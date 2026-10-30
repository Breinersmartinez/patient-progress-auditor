package com.patientprogressauditor.llm;

import com.fasterxml.jackson.databind.JsonNode;

// Una herramienta que el modelo pide usar. `signature` es un dato opaco del proveedor que hay que devolverle tal cual
// en el historial (Gemini lo exige); si la llamada la hizo otro proveedor, es null.
public record ToolCall(String id, String name, JsonNode arguments, String signature) {
}
