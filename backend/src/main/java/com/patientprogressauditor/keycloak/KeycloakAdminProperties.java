package com.patientprogressauditor.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Cliente de servicio del realm (`auditor-admin`): solo gestiona usuarios. `publicUrl` es la interfaz a la que vuelve el
// enlace de la invitacion. Sin llaves por defecto: el secreto lo pone el entorno.
@ConfigurationProperties(prefix = "app.keycloak-admin")
public record KeycloakAdminProperties(String url, String realm, String clientId, String clientSecret) {
}
