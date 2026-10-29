package com.patientprogressauditor.llm;

import java.time.Duration;
import java.util.List;

// `tools` y `responseSchema` son excluyentes en la practica: una llamada o pide herramientas o pide JSON.
// `timeout` null usa el de la configuracion para el proposito.
public record LlmRequest(LlmPurpose purpose, List<LlmMessage> messages, List<ToolSpec> tools,
        ResponseSchema responseSchema, Duration timeout) {

    public static LlmRequest structured(LlmPurpose purpose, List<LlmMessage> messages, ResponseSchema schema) {
        return new LlmRequest(purpose, messages, List.of(), schema, null);
    }

    public static LlmRequest withTools(LlmPurpose purpose, List<LlmMessage> messages, List<ToolSpec> tools) {
        return new LlmRequest(purpose, messages, tools, null, null);
    }

    public LlmRequest withMessages(List<LlmMessage> newMessages) {
        return new LlmRequest(purpose, newMessages, tools, responseSchema, timeout);
    }
}
