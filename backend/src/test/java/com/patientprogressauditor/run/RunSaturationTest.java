package com.patientprogressauditor.run;

import static com.patientprogressauditor.run.RunTestSupport.NURSE;
import static com.patientprogressauditor.run.RunTestSupport.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentRunner;
import com.patientprogressauditor.agent.AgentStepRepository;

// Un hilo y sin cola: con una ejecucion viva, otra distinta no entra. Responde 503 y no deja la ejecucion colgada.
@SpringBootTest(properties = { "app.seed.demo=true", "agent.executor-threads=1", "agent.queue-capacity=0" })
@AutoConfigureMockMvc
class RunSaturationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @MockitoSpyBean private AgentRunner runner;

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private UUID first;
    private UUID other;

    @AfterEach
    void tearDown() throws InterruptedException {
        release.countDown();
        for (UUID patient : new UUID[] { first, other }) {
            if (patient != null) {
                RunTestSupport.awaitNoOpenRuns(jdbc, patient);
                RunTestSupport.clean(jdbc, patient);
            }
        }
    }

    private MvcResult request(UUID patient, String week) throws Exception {
        return mockMvc.perform(post("/api/runs").header("X-Demo-User", NURSE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + patient + "\",\"weekStart\":\"" + week + "\"}")).andReturn();
    }

    @Test
    void withAllThreadsBusyTheAnswerIs503AndTheRunIsNotLeftHanging() throws Exception {
        first = RunTestSupport.patient(jdbc, "P-014");
        // Otro paciente con registros en alguna semana y sin informe vigente en ella.
        var row = jdbc.queryForMap("""
                SELECT e.patient_id, date_trunc('week', e.entry_date)::date AS week FROM entries e
                WHERE e.patient_id <> ? AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.patient_id = e.patient_id
                    AND r.period_start = date_trunc('week', e.entry_date)::date AND r.status IN ('PENDIENTE_REVISION', 'APROBADO'))
                LIMIT 1""", first);
        other = (UUID) row.get("patient_id");
        String otherWeek = row.get("week").toString();
        RunTestSupport.clean(jdbc, first);
        RunTestSupport.clean(jdbc, other);

        doAnswer(call -> {
            com.patientprogressauditor.agent.AgentRun run = call.getArgument(0);
            RunTestSupport.start(runs, run);
            RunTestSupport.step(steps, run.getId(), 1, "Consultando al modelo");
            started.countDown();
            release.await(20, TimeUnit.SECONDS);
            RunTestSupport.complete(runs, run);
            return null;
        }).when(runner).run(any(), any());

        assertThat(request(first, WEEK.toString()).getResponse().getStatus()).isEqualTo(202);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        MvcResult busy = request(other, otherWeek);
        assertThat(busy.getResponse().getStatus()).isEqualTo(503);
        assertThat(busy.getResponse().getHeader("Retry-After")).isEqualTo("30");
        assertThat(busy.getResponse().getContentAsString()).contains("otros informes").doesNotContain("Exception");
        assertThat(jdbc.queryForObject("SELECT status FROM agent_runs WHERE patient_id = ?", String.class, other)).isEqualTo("FALLIDA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_steps s JOIN agent_runs r ON r.id = s.run_id WHERE r.patient_id = ? AND s.is_error",
                Integer.class, other)).isEqualTo(1);
    }
}
