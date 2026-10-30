package com.patientprogressauditor.llm;

import com.fasterxml.jackson.databind.JsonNode;

// Una herramienta ofrecida al modelo. `parameters` es JSON Schema estandar, igual para todos los proveedores.
// La descripcion es un prompt: cuando usarla, cuando no y que prerrequisitos tiene.
public record ToolSpec(String name, String description, JsonNode parameters) {
}
