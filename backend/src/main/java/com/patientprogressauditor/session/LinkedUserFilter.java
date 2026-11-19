package com.patientprogressauditor.session;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Un usuario de Keycloak con rol clinico y sin AppUser con su correo no puede actuar: no habria autor para sus acciones
// (D12). Responde 403 y no crea nada. Corre despues de validar el token.
public class LinkedUserFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(LinkedUserFilter.class);

    private final ObjectMapper mapper;

    public LinkedUserFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppPrincipal principal
                && principal.unlinked() && !principal.role().isEmpty()) {
            LOG.warn("Usuario con rol {} sin AppUser con su correo: {}", principal.role(), principal.displayName());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(),
                    new ApiError("Su usuario no esta registrado en el sistema. Pida a administracion que lo registre."));
            return;
        }
        chain.doFilter(request, response);
    }
}
