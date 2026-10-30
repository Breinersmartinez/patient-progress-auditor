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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.session.ApiError;
import com.patientprogressauditor.session.DemoUserFilter;
import com.patientprogressauditor.user.AppUserRepository;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AppUserRepository users, ObjectMapper mapper) throws Exception {
        // Momento 2 (D23, D26): sin token ni sesion; el rol sale del usuario de demostracion elegido.
        http
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // El SSE termina con una segunda pasada (ASYNC) por la cadena: la peticion ya se autorizo al llegar
                        // y el filtro de sesion no corre en esa pasada, asi que no se vuelve a pedir usuario.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/system/status", "/api/demo-users").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> write(response, mapper,
                                HttpStatus.UNAUTHORIZED, "Elija un usuario de demostracion para continuar."))
                        .accessDeniedHandler((request, response, e) -> write(response, mapper,
                                HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta accion.")))
                // El filtro no es un bean: como bean, el contenedor de servlets lo registraria una segunda vez.
                .addFilterBefore(new DemoUserFilter(users), AuthorizationFilter.class);
        return http.build();
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
        config.setAllowedHeaders(List.of("Content-Type", "X-Demo-User", "Last-Event-ID"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
