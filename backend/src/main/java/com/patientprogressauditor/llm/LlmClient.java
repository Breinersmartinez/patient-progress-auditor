package com.patientprogressauditor.llm;

// El puerto del modelo: un proveedor. Ningun SDK de proveedor vive fuera de esta capa; cambiar de proveedor es
// cambiar configuracion. Un fallo se informa con LlmException, nunca con la excepcion cruda de HTTP.
public interface LlmClient {

    // "groq" o "gemini": el valor de LLM_PROVIDER y LLM_FALLBACK.
    String id();

    // Nombre para mostrar en los mensajes.
    String displayName();

    // Nombre de la variable de entorno con la llave, para decirle a la persona que falta.
    String keyVariable();

    // Hay llave configurada. Sin ella la aplicacion arranca igual y las llamadas fallan con MISSING_KEY.
    boolean configured();

    String model(LlmPurpose purpose);

    LlmResponse chat(LlmRequest request) throws LlmException;
}
