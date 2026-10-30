package com.patientprogressauditor.llm;

import java.math.BigDecimal;
import java.util.List;

// Una llamada resuelta, con lo que pide el registro de cada ejecucion (agent_runs): proveedor, modelo, si se uso el
// respaldo, tokens, costo y latencia total (incluye las esperas entre intentos).
public record LlmResult(LlmResponse response, String provider, String model, boolean fallbackUsed, long millis,
        BigDecimal costUsd, List<Attempt> attempts) {

    public int tokensIn() {
        return response.tokensIn();
    }

    public int tokensOut() {
        return response.tokensOut();
    }
}
