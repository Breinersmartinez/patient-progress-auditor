package com.patientprogressauditor.run;

import static com.patientprogressauditor.run.RunTestSupport.NURSE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.patientprogressauditor.agent.AgentStepRepository;

// El ejecutor vive en el proceso: lo que quedo abierto tras un reinicio se marca como interrumpido (D24).
@SpringBootTest(properties = "app.seed.demo=true")
class OrphanRunRecoveryTest {

    @Autowired private OrphanRunRecovery recovery;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentStepRepository steps;

    private UUID patient;

    @BeforeEach
    void setUp() {
        patient = RunTestSupport.patient(jdbc, "P-014");
        RunTestSupport.clean(jdbc, patient);
    }

    @AfterEach
    void tearDown() {
        RunTestSupport.clean(jdbc, patient);
    }

    private UUID insert(String status, String week) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status, stop_reason) VALUES (?, 'INFORME', ?, ?, ?::date, ?::date + 6, ?, ?)",
                id, patient, UUID.fromString(NURSE), week, week, status, "COMPLETADA".equals(status) ? "EXITO" : null);
        return id;
    }

    @Test
    void anOpenRunAfterARestartAppearsInterruptedWithItsStepAndAFinishedOneIsUntouched() {
        UUID queued = insert("EN_COLA", "2026-09-07");
        UUID running = insert("EN_CURSO", "2026-09-14");
        UUID done = insert("COMPLETADA", "2026-09-21");
        RunTestSupport.step(steps, running, 1, "Consultando al modelo");

        assertThat(recovery.recover()).isEqualTo(2);

        for (UUID id : new UUID[] { queued, running }) {
            assertThat(jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?", String.class, id)).isEqualTo("FALLIDA");
            assertThat(jdbc.queryForObject("SELECT error_message FROM agent_runs WHERE id = ?", String.class, id))
                    .startsWith("Interrumpida por un reinicio");
            assertThat(jdbc.queryForObject("SELECT finished_at IS NOT NULL FROM agent_runs WHERE id = ?", Boolean.class, id)).isTrue();
        }
        // El paso de error va despues del ultimo que ya tenia (la traza sigue en orden).
        assertThat(jdbc.queryForList("SELECT seq FROM agent_steps WHERE run_id = ? ORDER BY seq", Integer.class, running)).containsExactly(1, 2);
        assertThat(jdbc.queryForObject("SELECT is_error FROM agent_steps WHERE run_id = ? AND seq = 2", Boolean.class, running)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?", String.class, done)).isEqualTo("COMPLETADA");

        // Repetirlo no cambia nada.
        assertThat(recovery.recover()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE run_id = ?", Integer.class, running)).isEqualTo(2);
    }

    @Test
    void afterRecoveryTheSameWeekCanBeRequestedAgain() {
        insert("EN_CURSO", "2026-09-07");
        recovery.recover();
        // El indice de una sola ejecucion viva ya no la cuenta.
        insert("EN_COLA", "2026-09-07");
    }
}
