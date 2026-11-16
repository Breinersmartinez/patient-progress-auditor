package com.patientprogressauditor.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import com.patientprogressauditor.user.AppPermission;
import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppRoleRepository;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;

// Convierte el JWT ya validado de Keycloak en quien actua: el rol sale del claim realm_access.roles, los permisos del rol
// en la base (RF-34 seguira editandolos alli) y el autor, del AppUser con el mismo correo (D12).
@Component
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    // Orden de prioridad si un token trajera mas de un rol de la aplicacion.
    private static final List<String> APP_ROLES = List.of(AppRole.ADMIN, AppRole.NURSING, AppRole.DOCTOR);

    private final AppUserRepository users;
    private final AppRoleRepository roles;

    public JwtPrincipalConverter(AppUserRepository users, AppRoleRepository roles) {
        this.users = users;
        this.roles = roles;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String role = roleOf(jwt);
        if (role == null) {
            // Token valido pero sin rol de la aplicacion: queda autenticado sin autoridades y toda ruta responde 403.
            return UsernamePasswordAuthenticationToken.authenticated(
                    new AppPrincipal(null, nameOf(jwt, null), "", Set.of()), jwt, List.of());
        }
        AppUser user = AppRole.ADMIN.equals(role) ? null : users.findByEmailIgnoreCaseAndActiveTrue(jwt.getClaimAsString("email")).orElse(null);
        Set<String> permissions = roles.findByCode(role)
                .map(r -> r.getPermissions().stream().map(AppPermission::getCode).collect(Collectors.toSet()))
                .orElse(Set.of());
        AppPrincipal principal = new AppPrincipal(user == null ? null : user.getId(), nameOf(jwt, user), role, permissions);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        permissions.forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }

    private static String roleOf(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map) || !(map.get("roles") instanceof List<?> granted)) {
            return null;
        }
        return APP_ROLES.stream().filter(granted::contains).findFirst().orElse(null);
    }

    private static String nameOf(Jwt jwt, AppUser user) {
        if (user != null) {
            return user.getDisplayName();
        }
        String name = jwt.getClaimAsString("name");
        return name != null && !name.isBlank() ? name : String.valueOf(jwt.getClaimAsString("email"));
    }
}
