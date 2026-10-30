package com.patientprogressauditor.agent.tools;

// Un uso invalido de una herramienta (argumento mal formado, semana fuera de alcance...). No mata el bucle: vuelve al
// modelo como observacion para que corrija (AGENTS.md).
public class ToolException extends RuntimeException {

    public ToolException(String message) {
        super(message);
    }
}
