package com.patientprogressauditor.llm;

import java.math.BigDecimal;
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

    public int tokensIn() {
        return attempts.stream().filter(Attempt::succeeded).mapToInt(Attempt::tokensIn).sum();
    }

    public int tokensOut() {
        return attempts.stream().filter(Attempt::succeeded).mapToInt(Attempt::tokensOut).sum();
    }

    // La suma del costo de lista de las llamadas que respondieron (por tokens, o por tiempo si fue una transcripcion), la
    // misma que guarda la aplicacion en agent_runs.cost_usd. Es
    // null si ninguna respondio o si alguna usa un modelo sin precio conocido: vacio, no cero.
    public BigDecimal costUsd() {
        BigDecimal total = BigDecimal.ZERO;
        boolean any = false;
        for (Attempt attempt : attempts) {
            if (!attempt.succeeded()) {
                continue;
            }
            BigDecimal cost = LlmPricing.cost(attempt);
            if (cost == null) {
                return null;
            }
            total = total.add(cost);
            any = true;
        }
        return any ? total : null;
    }
}
