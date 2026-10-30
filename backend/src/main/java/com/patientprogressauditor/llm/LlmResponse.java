package com.patientprogressauditor.llm;

import java.util.List;

// Lo que devuelve un proveedor en una llamada que salio bien.
public record LlmResponse(String text, List<ToolCall> toolCalls, FinishReason finishReason, int tokensIn, int tokensOut) {

    public enum FinishReason { STOP, TOOL_CALLS, LENGTH, OTHER }
}
