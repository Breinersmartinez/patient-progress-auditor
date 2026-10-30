package com.patientprogressauditor.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.patientprogressauditor.user.AppPermission;
import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Toma el usuario de demostracion elegido de la peticion (D26). No es autenticacion: cualquiera puede mandar un id.
// Si no hay usuario valido no deja nada en el contexto y la regla de /api/** responde 401.
public class DemoUserFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Demo-User";
    public static final String QUERY_PARAM = "demoUser";

    // EventSource no permite cabeceras: solo en la ruta SSE de lectura se acepta el parametro de consulta.
    private static final String SSE_ROUTE = "/api/runs/*/events";
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final AppUserRepository users;

    public DemoUserFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        resolve(request).ifPresent(principal -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role()));
            principal.permissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
            SecurityContextHolder.getContext()
                    .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        });
        chain.doFilter(request, response);
    }

    private Optional<DemoPrincipal> resolve(HttpServletRequest request) {
        String raw = request.getHeader(HEADER);
        if (raw == null && isSseRoute(request)) {
            raw = request.getParameter(QUERY_PARAM);
        }
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return users.findById(id)
                .filter(user -> user.isActive() && user.isDemo() && !user.getRoles().isEmpty())
                .map(DemoUserFilter::toPrincipal);
    }

    // Se compara la ruta de la peticion y no el servletPath: este puede llegar vacio segun el despliegue.
    private static boolean isSseRoute(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return HttpMethod.GET.matches(request.getMethod()) && PATHS.match(SSE_ROUTE, path);
    }

    private static DemoPrincipal toPrincipal(AppUser user) {
        String role = user.getRoles().stream().map(AppRole::getCode).sorted().findFirst().orElseThrow();
        Set<String> permissions = user.getRoles().stream()
                .flatMap(userRole -> userRole.getPermissions().stream())
                .map(AppPermission::getCode)
                .collect(Collectors.toSet());
        return new DemoPrincipal(user.getId(), user.getDisplayName(), role, permissions);
    }
}
