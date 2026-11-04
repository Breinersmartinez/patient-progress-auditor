package com.patientprogressauditor.llm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Una ejecucion ya cerrada (informe semanal, informe mensual o estructuracion), con lo unico que sale hacia Langfuse:
// su id, tipo, resultado y los intentos de llamada al modelo. No lleva texto de notas, prompts, respuestas ni el id del
// paciente: la forma del registro es la primera barrera (RNF-02). `type`, `status` y `stopReason` son los de agent_runs.
public record LangfuseRun(UUID runId, String type, String status, String stopReason, Instant startedAt, long millis,
        List<Attempt> attempts) {

    public LangfuseRun {
        attempts = List.copyOf(attempts);
    }
}
