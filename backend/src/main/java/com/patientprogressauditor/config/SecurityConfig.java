package com.patientprogressauditor.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.session.ApiError;
import com.patientprogressauditor.session.JwtPrincipalConverter;
import com.patientprogressauditor.session.LinkedUserFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtPrincipalConverter converter, ObjectMapper mapper) throws Exception {
        // Autenticacion con el JWT de Keycloak (docs/login-keycloak). Sin sesion ni cookies: el token viaja en la cabecera
        // Authorization, asi que no hay nada que otro sitio pueda reutilizar y CSRF solo se exime en /api/** (docs/decisiones.md).
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El SSE termina con una segunda pasada (ASYNC) por la cadena: la peticion ya se autorizo al llegar
                        // y el filtro del token no corre en esa pasada, asi que no se vuelve a pedir.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/system/status").permitAll()
                        // Gestion de usuarios (docs/admin-usuarios): solo el administrador. Los demas roles reciben 403.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, e) -> write(response, mapper,
                                HttpStatus.UNAUTHORIZED, "Inicie sesion para continuar."))
                        .accessDeniedHandler((request, response, e) -> write(response, mapper,
                                HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta accion.")))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> write(response, mapper,
                                HttpStatus.UNAUTHORIZED, "Inicie sesion para continuar."))
                        .accessDeniedHandler((request, response, e) -> write(response, mapper,
                                HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta accion.")))
                // El filtro no es un bean: como bean, el contenedor de servlets lo registraria una segunda vez.
                .addFilterAfter(new LinkedUserFilter(mapper), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    // Las claves publicas se piden a Keycloak la primera vez que llega un token (no al arrancar): el backend levanta
    // aunque Keycloak todavia no este listo. El emisor es la URL publica del realm, que no es la que ve el backend.
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.oidc.issuer}") String issuer, @Value("${app.oidc.jwks-uri}") String jwksUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), new ApiError(message));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "Last-Event-ID"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
