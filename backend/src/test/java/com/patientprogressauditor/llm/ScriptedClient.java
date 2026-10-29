package com.patientprogressauditor.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// Un proveedor con guion para probar la pasarela: devuelve en orden lo que se le programo (una respuesta o un fallo).
// Cuando se acaba el guion repite el ultimo resultado.
final class ScriptedClient implements LlmClient {

    private final String id;
    private final boolean configured;
    private final Deque<Object> script = new ArrayDeque<>();
    private Object last;
    final List<LlmRequest> calls = new ArrayList<>();

    ScriptedClient(String id, boolean configured) {
        this.id = id;
        this.configured = configured;
    }

    ScriptedClient then(Object outcome) {
        script.add(outcome);
        return this;
    }

    ScriptedClient thenAlways(Object outcome, int times) {
        for (int i = 0; i < times; i++) {
            script.add(outcome);
        }
        return this;
    }

    static LlmResponse answer(String text, int in, int out) {
        return new LlmResponse(text, List.of(), LlmResponse.FinishReason.STOP, in, out);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return id;
    }

    @Override
    public String keyVariable() {
        return id.toUpperCase() + "_API_KEY";
    }

    @Override
    public boolean configured() {
        return configured;
    }

    @Override
    public String model(LlmPurpose purpose) {
        return id.equals("groq") ? "openai/gpt-oss-120b" : "gemini-3.5-flash-lite";
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        calls.add(request);
        Object next = script.isEmpty() ? last : script.poll();
        last = next;
        if (next instanceof LlmException e) {
            throw e;
        }
        return (LlmResponse) next;
    }
}
