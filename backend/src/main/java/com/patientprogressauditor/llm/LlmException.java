package com.patientprogressauditor.llm;

import java.time.Duration;

// Un fallo de UN proveedor en UNA llamada. `getMessage()` es lo que puede ver la persona: en espanol y sin cuerpo crudo
// ni rastro de pila. `detail` es para el registro del servidor y nunca sale a la pantalla.
public class LlmException extends RuntimeException {

    public enum Kind {
        RATE_LIMIT(true, "El servicio de IA recibió demasiadas solicitudes y pidió esperar."),
        UNAVAILABLE(true, "El servicio de IA no está disponible en este momento."),
        TIMEOUT(true, "El servicio de IA tardó demasiado en responder."),
        INVALID_OUTPUT(false, "El servicio de IA devolvió una respuesta que no se pudo usar."),
        AUTH(false, "El servicio de IA no aceptó la llave configurada."),
        MISSING_KEY(false, "Falta la llave del servicio de IA."),
        BAD_REQUEST(false, "El servicio de IA rechazó la solicitud.");

        private final boolean retryable;
        private final String message;

        Kind(boolean retryable, String message) {
            this.retryable = retryable;
            this.message = message;
        }

        public boolean retryable() {
            return retryable;
        }

        public String message() {
            return message;
        }
    }

    private final Kind kind;
    private final String provider;
    private final Duration retryAfter;
    private final String detail;

    public LlmException(Kind kind, String provider, Duration retryAfter, String detail) {
        super(kind.message());
        this.kind = kind;
        this.provider = provider;
        this.retryAfter = retryAfter;
        this.detail = detail;
    }

    public LlmException(Kind kind, String provider, String detail) {
        this(kind, provider, null, detail);
    }

    public Kind kind() {
        return kind;
    }

    public String provider() {
        return provider;
    }

    // Lo que el proveedor pidio esperar (429), o null.
    public Duration retryAfter() {
        return retryAfter;
    }

    public String detail() {
        return detail;
    }
}
