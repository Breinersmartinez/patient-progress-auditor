package com.patientprogressauditor.agent;

import java.time.Duration;
import java.util.List;

import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.LangfuseRun;

// De una fila cerrada de agent_runs y sus intentos a lo que sale hacia Langfuse. Lo comparten el agente y la estructuracion.
// Aqui se decide que sale: el id, el tipo, el estado, el motivo de parada y los intentos. El paciente, quien lo pidio y los
// mensajes de error se quedan en la base.
public final class LangfuseTraces {

    private LangfuseTraces() {
    }

    // null si a la fila le falta algo sin lo cual no hay traza (hora de inicio, tipo o estado): el exportador ignora un null,
    // y la observabilidad no puede tumbar el cierre de una ejecucion.
    public static LangfuseRun of(AgentRun run, List<Attempt> attempts) {
        if (run.getId() == null || run.getStartedAt() == null || run.getRunType() == null || run.getStatus() == null) {
            return null;
        }
        long millis = run.getFinishedAt() == null ? 0 : Math.max(0, Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());
        return new LangfuseRun(run.getId(), run.getRunType().name(), run.getStatus().name(),
                run.getStopReason() == null ? null : run.getStopReason().name(), run.getStartedAt(), millis, attempts);
    }
}
