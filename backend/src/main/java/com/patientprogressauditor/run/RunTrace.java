package com.patientprogressauditor.run;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;

// Cierra con honestidad una ejecucion que quedo abierta: el hilo murio, el ejecutor estaba lleno o el proceso se reinicio.
// El paso va ANTES del estado final: quien sigue la traza en vivo cierra al ver el estado final (D24).
@Component
public class RunTrace {

    static final List<AgentRun.Status> OPEN = List.of(AgentRun.Status.EN_COLA, AgentRun.Status.EN_CURSO);

    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final Clock clock;

    public RunTrace(AgentRunRepository runs, AgentStepRepository steps, Clock clock) {
        this.runs = runs;
        this.steps = steps;
        this.clock = clock;
    }

    // Devuelve true si la ejecucion estaba abierta y la cerro; false si ya habia terminado (no se pisa un resultado).
    public boolean failIfOpen(UUID runId, String message) {
        AgentRun run = runs.findById(runId).orElse(null);
        if (run == null || !OPEN.contains(run.getStatus())) {
            return false;
        }
        AgentStep step = new AgentStep();
        step.setRunId(runId);
        step.setSeq(steps.findTopByRunIdOrderBySeqDesc(runId).map(last -> last.getSeq() + 1).orElse(1));
        step.setStepType(AgentStep.Type.ERROR);
        step.setMessage(message.length() > 300 ? message.substring(0, 300) : message);
        step.setError(true);
        steps.save(step);
        run.setStatus(AgentRun.Status.FALLIDA);
        run.setErrorMessage(message.length() > 500 ? message.substring(0, 500) : message);
        run.setFinishedAt(clock.instant());
        runs.save(run);
        return true;
    }
}
