package com.patientprogressauditor.web;

// Lo que se pidio no existe o no se puede ver: 404 con un mensaje claro.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
