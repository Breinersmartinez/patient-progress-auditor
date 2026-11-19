package com.patientprogressauditor.keycloak;

// Keycloak ya tiene un usuario con ese correo (409).
public class KeycloakUserExistsException extends RuntimeException {

    public KeycloakUserExistsException(String email) {
        super("Keycloak ya tiene un usuario con el correo " + email);
    }
}
