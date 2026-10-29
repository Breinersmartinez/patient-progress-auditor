package com.patientprogressauditor.run;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;

// Lo comun de las pruebas de ejecuciones: ids de la semilla y un «agente» que escribe pasos reales en la base a la vez que
// la prueba decide cuando avanza. El bucle del modelo ya se prueba en AgentRunnerTest; aqui se prueba el segundo plano.
final class RunTestSupport {

    static final String NURSE = "20000000-0000-0000-0000-000000000002";
    static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    static final LocalDate WEEK = LocalDate.of(2026, 10, 19);

    private RunTestSupport() {
    }

    static UUID patient(JdbcTemplate jdbc, String alias) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, alias);
    }

    static void step(AgentStepRepository steps, UUID runId, int seq, String message) {
        AgentStep step = new AgentStep();
        step.setRunId(runId);
        step.setSeq(seq);
        step.setStepType(AgentStep.Type.ESTADO);
        step.setMessage(message);
        steps.save(step);
    }

    static void start(AgentRunRepository runs, AgentRun run) {
        run.setStatus(AgentRun.Status.EN_CURSO);
        runs.save(run);
    }

    static void complete(AgentRunRepository runs, AgentRun run) {
        run.setStatus(AgentRun.Status.COMPLETADA);
        run.setStopReason(AgentRun.StopReason.EXITO);
        runs.save(run);
    }

    // Tras soltar al «agente» de la prueba, espera a que su hilo termine de escribir antes de limpiar la base.
    static void awaitNoOpenRuns(JdbcTemplate jdbc, UUID patient) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Integer open = jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE patient_id = ? AND status IN ('EN_COLA', 'EN_CURSO')",
                    Integer.class, patient);
            if (open == 0) {
                return;
            }
            Thread.sleep(50);
        }
    }

    // Borra lo que dejaron las ejecuciones de la prueba (las escribe otro hilo, fuera de su transaccion).
    static void clean(JdbcTemplate jdbc, UUID patient) {
        jdbc.update("DELETE FROM agent_steps WHERE run_id IN (SELECT id FROM agent_runs WHERE patient_id = ?)", patient);
        jdbc.update("DELETE FROM agent_runs WHERE patient_id = ?", patient);
    }
}
