package com.patientprogressauditor.llm;

import java.util.List;

// Ningun proveedor pudo responder (tras reintentar y probar el respaldo). `getMessage()` es honesto, en espanol y listo
// para mostrar: dice que paso y que hacer, sin rastro de pila ni cuerpo crudo.
public class LlmUnavailableException extends RuntimeException {

    private final transient List<Attempt> attempts;

    public LlmUnavailableException(String message, List<Attempt> attempts) {
        super(message);
        this.attempts = List.copyOf(attempts);
    }

    public List<Attempt> attempts() {
        return attempts;
    }
}
