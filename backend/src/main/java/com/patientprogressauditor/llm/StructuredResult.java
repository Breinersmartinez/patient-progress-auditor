package com.patientprogressauditor.llm;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

// Una salida JSON que ya valido. Los tokens y el costo suman la llamada corregida si hubo que repetirla.
// `firstProblems` son los de la primera salida (vacia si valido al primer intento); `attempts` son todos los intentos
// a proveedores, de las dos llamadas si hubo correccion.
public record StructuredResult(JsonNode json, LlmResult result, int tokensIn, int tokensOut, BigDecimal costUsd,
        boolean corrected, List<String> firstProblems, List<Attempt> attempts) {
}
