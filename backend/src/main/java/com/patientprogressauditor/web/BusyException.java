package com.patientprogressauditor.web;

// El sistema no puede atender la peticion ahora (p. ej. el ejecutor de informes esta lleno): 503 con un mensaje claro.
public class BusyException extends RuntimeException {

    public BusyException(String message) {
        super(message);
    }
}
