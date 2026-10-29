package com.patientprogressauditor.llm;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;

// Una salida JSON que ya valido. Los tokens y el costo suman la llamada corregida si hubo que repetirla.
public record StructuredResult(JsonNode json, LlmResult result, int tokensIn, int tokensOut, BigDecimal costUsd,
        boolean corrected) {
}
