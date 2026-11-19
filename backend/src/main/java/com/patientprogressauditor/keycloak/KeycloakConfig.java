package com.patientprogressauditor.keycloak;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KeycloakAdminProperties.class)
public class KeycloakConfig {

    // Diez segundos por llamada: la pantalla del admin no se queda colgada si Keycloak no contesta (cae en 502).
    @Bean
    KeycloakAdminClient keycloakAdminClient(KeycloakAdminProperties properties, @Value("${app.public-url}") String publicUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new KeycloakAdminClient(properties, publicUrl, RestClient.builder().requestFactory(factory));
    }
}
