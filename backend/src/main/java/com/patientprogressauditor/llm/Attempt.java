package com.patientprogressauditor.llm;

import com.patientprogressauditor.llm.LlmException.Kind;

// Un intento de llamada a un proveedor, para la traza (agent_steps: LLAMADA_LLM y FALLBACK). `failure` es null si salio bien.
// `detail` es tecnico (para el servidor y la traza del equipo); no se muestra a la persona.
public record Attempt(String provider, String model, int number, Kind failure, long millis, String detail) {

    public boolean succeeded() {
        return failure == null;
    }
}
