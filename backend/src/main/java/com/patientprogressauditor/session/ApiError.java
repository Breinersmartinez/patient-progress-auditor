package com.patientprogressauditor.session;

// Cuerpo de los errores que ve la persona: un mensaje claro, nunca un stack trace (RNF-04).
public record ApiError(String message) {
}
