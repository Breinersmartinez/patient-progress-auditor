package com.patientprogressauditor.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

// Reemplaza al decodificador de Keycloak en las pruebas: acepta "test-" + base64url("<correo>|<ROL1,ROL2>") (el resolvedor de
// Spring solo deja pasar letras, digitos y unos pocos signos en el Bearer) y rechaza cualquier otra cosa.
// El resto de la cadena (filtro del token, conversor a AppPrincipal, filtro de usuario sin cuenta, permisos) es la real.
@Configuration
public class TestJwtConfig {

    public static final String PREFIX = "test-";

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() {
        return token -> {
            if (!token.startsWith(PREFIX)) {
                throw new BadJwtException("Token de prueba invalido");
            }
            String[] parts;
            try {
                parts = new String(Base64.getUrlDecoder().decode(token.substring(PREFIX.length())), StandardCharsets.UTF_8).split("\\|", 2);
            } catch (IllegalArgumentException e) {
                throw new BadJwtException("Token de prueba invalido");
            }
            if (parts.length != 2) {
                throw new BadJwtException("Token de prueba invalido");
            }
            List<String> roles = parts[1].isBlank() ? List.of() : Arrays.asList(parts[1].split(","));
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .subject(parts[0])
                    .issuer("http://localhost:7070/realms/auditor")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("email", parts[0])
                    .claim("name", parts[0])
                    .claim("realm_access", Map.of("roles", roles))
                    .build();
        };
    }
}
