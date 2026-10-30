package com.patientprogressauditor.llm;

import java.util.List;

// Un mensaje de la conversacion, sin nada propio de un proveedor: sirve para cambiar de proveedor a mitad de un bucle.
public record LlmMessage(Role role, String content, List<ToolCall> toolCalls, String toolCallId, String toolName) {

    public enum Role { SYSTEM, USER, ASSISTANT, TOOL }

    public static LlmMessage system(String content) {
        return new LlmMessage(Role.SYSTEM, content, List.of(), null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content, List.of(), null, null);
    }

    // `content` puede ser null cuando el modelo solo pidio herramientas.
    public static LlmMessage assistant(String content, List<ToolCall> toolCalls) {
        return new LlmMessage(Role.ASSISTANT, content, toolCalls == null ? List.of() : toolCalls, null, null);
    }

    // El resultado de una herramienta, como texto (JSON o mensaje de error: un error vuelve al modelo como observacion).
    public static LlmMessage tool(String toolCallId, String toolName, String content) {
        return new LlmMessage(Role.TOOL, content, List.of(), toolCallId, toolName);
    }
}
