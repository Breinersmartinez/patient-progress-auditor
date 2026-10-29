package com.patientprogressauditor.web;

import java.util.Map;

import org.springframework.http.HttpStatus;

// Un dato del formulario no es valido para el servicio (p. ej. un codigo repetido o una fecha futura).
// Lleva el campo y el mensaje para que la pantalla lo muestre junto al campo, como el mockup.
public class FieldException extends RuntimeException {

    private final HttpStatus status;
    private final transient Map<String, String> fields;

    public FieldException(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.fields = Map.of(field, message);
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, String> fields() {
        return fields;
    }
}
