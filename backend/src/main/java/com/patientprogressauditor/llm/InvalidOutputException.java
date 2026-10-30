package com.patientprogressauditor.llm;

import java.util.List;

// La salida no valida contra el esquema ni tras un reintento corregido (RF-18). `lastText` permite degradar a texto
// plano y registrar; nunca se le muestra a la persona como si fuera valido.
public class InvalidOutputException extends RuntimeException {

    private final String lastText;
    private final transient List<String> problems;
    private final transient List<Attempt> attempts;

    public InvalidOutputException(String message, String lastText, List<String> problems, List<Attempt> attempts) {
        super(message);
        this.lastText = lastText;
        this.problems = List.copyOf(problems);
        this.attempts = List.copyOf(attempts);
    }

    public String lastText() {
        return lastText;
    }

    public List<String> problems() {
        return problems;
    }

    public List<Attempt> attempts() {
        return attempts;
    }
}
