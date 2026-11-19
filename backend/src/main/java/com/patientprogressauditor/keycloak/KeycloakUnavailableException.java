package com.patientprogressauditor.keycloak;

// Keycloak no respondio, rechazo al cliente de servicio o contesto con un error del servidor: la gestion de usuarios no
// puede completarse y el backend responde 502 sin dejar nada a medias.
public class KeycloakUnavailableException extends RuntimeException {

    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public KeycloakUnavailableException(String message) {
        super(message);
    }
}
