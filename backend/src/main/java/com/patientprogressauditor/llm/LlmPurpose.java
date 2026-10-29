package com.patientprogressauditor.llm;

// Para que sirve la llamada: cada proveedor usa un modelo distinto segun el proposito (estructurar es corto y barato;
// el agente razona y usa herramientas) y cada modelo tiene su propia cuota.
public enum LlmPurpose {
    STRUCTURING,
    AGENT
}
