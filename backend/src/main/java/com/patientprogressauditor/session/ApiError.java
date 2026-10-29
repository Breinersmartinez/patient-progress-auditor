package com.patientprogressauditor.session;

import java.util.Map;

// Cuerpo de los errores que ve la persona: un mensaje claro, nunca un stack trace (RNF-04).
// `fields` solo viaja cuando el error es de un campo del formulario: nombre del campo -> mensaje.
public record ApiError(String message, Map<String, String> fields) {

    public ApiError(String message) {
        this(message, null);
    }
}
