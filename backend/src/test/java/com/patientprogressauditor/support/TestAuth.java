package com.patientprogressauditor.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;

// Ayudante de las pruebas: manda un token de prueba del usuario sembrado con ese id (su correo y su rol), como lo haria el
// navegador con el token de Keycloak. Un id que no existe manda un token valido de un correo sin cuenta (403, no 401).
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor as(Object userId) {
        return request -> {
            AppUserRepository users = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext())
                    .getBean(AppUserRepository.class);
            AppUser user = parse(userId) == null ? null : users.findById(parse(userId)).orElse(null);
            if (user == null) {
                return withToken(request, "nadie@example.test", AppRole.NURSING);
            }
            String role = user.getRoles().stream().map(AppRole::getCode).sorted().findFirst().orElse("");
            return withToken(request, user.getEmail(), role);
        };
    }

    // Token sin pasar por la base: para probar roles sueltos, correos sin cuenta y administracion.
    public static RequestPostProcessor token(String email, String... roles) {
        return request -> withToken(request, email, String.join(",", roles));
    }

    public static String bearer(String email, String roles) {
        return "Bearer " + TestJwtConfig.PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString((email + "|" + roles).getBytes(StandardCharsets.UTF_8));
    }

    private static org.springframework.mock.web.MockHttpServletRequest withToken(
            org.springframework.mock.web.MockHttpServletRequest request, String email, String roles) {
        request.removeHeader("Authorization");
        request.addHeader("Authorization", bearer(email, roles));
        return request;
    }

    private static UUID parse(Object id) {
        try {
            return UUID.fromString(String.valueOf(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
