package com.patientprogressauditor.llm;

import java.time.Duration;
import java.util.List;

// `tools` y `responseSchema` son excluyentes en la practica: una llamada o pide herramientas o pide JSON.
// `timeout` es el maximo de UNA llamada a un proveedor (null: el de la configuracion para el proposito).
// `budget` es el maximo de TODA la solicitud, con reintentos, esperas y respaldo (null: sin tope). Lo usan las
// llamadas sincronas, donde una persona espera la respuesta (D27).
public record LlmRequest(LlmPurpose purpose, List<LlmMessage> messages, List<ToolSpec> tools,
        ResponseSchema responseSchema, Duration timeout, Duration budget) {

    public static LlmRequest structured(LlmPurpose purpose, List<LlmMessage> messages, ResponseSchema schema) {
        return new LlmRequest(purpose, messages, List.of(), schema, null, null);
    }

    public static LlmRequest withTools(LlmPurpose purpose, List<LlmMessage> messages, List<ToolSpec> tools) {
        return new LlmRequest(purpose, messages, tools, null, null, null);
    }

    public LlmRequest withMessages(List<LlmMessage> newMessages) {
        return new LlmRequest(purpose, newMessages, tools, responseSchema, timeout, budget);
    }

    public LlmRequest withBudget(Duration newBudget) {
        return new LlmRequest(purpose, messages, tools, responseSchema, timeout, newBudget);
    }

    public LlmRequest withTimeout(Duration newTimeout) {
        return new LlmRequest(purpose, messages, tools, responseSchema, newTimeout, budget);
    }
}
