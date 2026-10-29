package com.patientprogressauditor.llm;

import com.fasterxml.jackson.databind.JsonNode;

// Esquema JSON (estandar) al que debe ajustarse la respuesta. Para el modo estricto de los proveedores, todas las
// propiedades van en `required` y `additionalProperties` es false.
public record ResponseSchema(String name, JsonNode schema) {
}
