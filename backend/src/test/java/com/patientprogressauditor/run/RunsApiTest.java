package com.patientprogressauditor.run;

import static com.patientprogressauditor.run.RunTestSupport.DOCTOR;
import static com.patientprogressauditor.run.RunTestSupport.NURSE;
import static com.patientprogressauditor.run.RunTestSupport.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentRunner;
import com.patientprogressauditor.agent.AgentStepRepository;

// Pedir un informe en segundo plano (D24). El agente real se cambia por uno que espera a que la prueba lo suelte: asi se
// comprueba que la peticion NO espera a la ejecucion. Lo que se prueba es el segundo plano, no el bucle (AgentRunnerTest).
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class RunsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @MockitoSpyBean private AgentRunner runner;

    private UUID patient;
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        patient = RunTestSupport.patient(jdbc, "P-014");
        RunTestSupport.clean(jdbc, patient);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        release.countDown();
        RunTestSupport.awaitNoOpenRuns(jdbc, patient);
        RunTestSupport.clean(jdbc, patient);
    }

    private void agentWaitsForTheTest() {
        doAnswer(call -> {
            AgentRun run = call.getArgument(0);
            RunTestSupport.start(runs, run);
            RunTestSupport.step(steps, run.getId(), 1, "Consultando al modelo");
            started.countDown();
            release.await(20, TimeUnit.SECONDS);
            RunTestSupport.step(steps, run.getId(), 2, "Borrador guardado");
            RunTestSupport.complete(runs, run);
            return null;
        }).when(runner).run(any(), any());
    }

    private MvcResultHolder request(String user, UUID patientId, String week) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/runs").header("X-Demo-User", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + patientId + "\",\"weekStart\":\"" + week + "\"}")).andReturn();
        return new MvcResultHolder(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private record MvcResultHolder(int status, String body) {
        JsonNode json(ObjectMapper mapper) throws Exception {
            return mapper.readTree(body);
        }
    }

    private AgentRun.Status statusOf(String runId) {
        return jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?::uuid", String.class, runId) == null ? null
                : AgentRun.Status.valueOf(jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?::uuid", String.class, runId));
    }

    private void awaitStatus(String runId, AgentRun.Status expected) throws InterruptedException {
        for (int i = 0; i < 100 && statusOf(runId) != expected; i++) {
            Thread.sleep(50);
        }
        assertThat(statusOf(runId)).isEqualTo(expected);
    }

    @Test
    void answersWithTheIdBeforeTheRunEndsAndReturnsTheLiveRunForAnEqualRequest() throws Exception {
        agentWaitsForTheTest();

        MvcResultHolder first = request(NURSE, patient, WEEK.toString());
        assertThat(first.status()).isEqualTo(202);
        assertThat(first.json(mapper).path("status").asText()).isEqualTo("EN_COLA");
        String id = first.json(mapper).path("id").asText();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        // La ejecucion sigue viva y la peticion ya respondio.
        assertThat(statusOf(id)).isEqualTo(AgentRun.Status.EN_CURSO);

        MvcResultHolder second = request(NURSE, patient, WEEK.toString());
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.json(mapper).path("id").asText()).isEqualTo(id);
        verify(runner, times(1)).run(any(), any());

        release.countDown();
        awaitStatus(id, AgentRun.Status.COMPLETADA);

        // Una ejecucion terminada no bloquea: otra peticion crea una nueva.
        agentWaitsForTheTest();
        MvcResultHolder third = request(NURSE, patient, WEEK.toString());
        assertThat(third.status()).isEqualTo(202);
        String thirdId = third.json(mapper).path("id").asText();
        assertThat(thirdId).isNotEqualTo(id);
        // Se espera su final para no limpiar la base mientras su hilo todavia escribe.
        awaitStatus(thirdId, AgentRun.Status.COMPLETADA);
    }

    @Test
    void eightSimultaneousRequestsCreateOneRun() throws Exception {
        agentWaitsForTheTest();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResultHolder>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return request(NURSE, patient, WEEK.toString());
            }));
        }
        go.countDown();
        List<MvcResultHolder> answers = new ArrayList<>();
        for (Future<MvcResultHolder> future : futures) {
            answers.add(future.get(20, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(answers).extracting(MvcResultHolder::status).containsOnly(200, 202);
        assertThat(answers.stream().filter(a -> a.status() == 202)).hasSize(1);
        assertThat(answers.stream().map(a -> {
            try {
                return a.json(mapper).path("id").asText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE patient_id = ?", Integer.class, patient)).isEqualTo(1);
        verify(runner, times(1)).run(any(), any());
    }

    @Test
    void theDatabaseItselfRefusesTwoLiveRunsOfTheSamePatientAndWeek() {
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status) VALUES (?, 'INFORME', ?, ?, ?, ?, 'EN_COLA')",
                UUID.randomUUID(), patient, UUID.fromString(NURSE), WEEK, WEEK.plusDays(6));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status) VALUES (?, 'INFORME', ?, ?, ?, ?, 'EN_CURSO')",
                UUID.randomUUID(), patient, UUID.fromString(NURSE), WEEK, WEEK.plusDays(6)))
                .hasMessageContaining("uq_agent_runs_live_report");
        // Una terminada no bloquea.
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status) VALUES (?, 'INFORME', ?, ?, ?, ?, 'FALLIDA')",
                UUID.randomUUID(), patient, UUID.fromString(NURSE), WEEK, WEEK.plusDays(6));
    }

    @Test
    void rejectsWhatCannotBeRequestedWithoutCreatingARun() throws Exception {
        // Medico: 403. Sin usuario: 401.
        assertThat(request(DOCTOR, patient, WEEK.toString()).status()).isEqualTo(403);
        assertThat(mockMvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + patient + "\",\"weekStart\":\"" + WEEK + "\"}")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        // Un martes, una semana futura, un paciente que no existe, una semana sin registros y un informe vigente.
        MvcResultHolder tuesday = request(NURSE, patient, "2026-10-20");
        assertThat(tuesday.status()).isEqualTo(400);
        assertThat(tuesday.json(mapper).path("fields").path("weekStart").asText()).contains("lunes");
        assertThat(request(NURSE, patient, "2030-01-07").status()).isEqualTo(400);
        assertThat(request(NURSE, UUID.randomUUID(), WEEK.toString()).status()).isEqualTo(404);
        MvcResultHolder empty = request(NURSE, patient, "2026-01-05");
        assertThat(empty.status()).isEqualTo(409);
        assertThat(empty.json(mapper).path("message").asText()).contains("no tiene registros");
        // La semilla aun no trae informes (E14): la prueba deja uno vigente de la semana 4 y lo borra al terminar.
        UUID report = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, report_type, patient_id, period_start, period_end, status, requested_by) VALUES (?, 'SEMANAL', ?, '2026-10-12', '2026-10-18', 'PENDIENTE_REVISION', ?)",
                report, patient, UUID.fromString(NURSE));
        MvcResultHolder current;
        try {
            current = request(NURSE, patient, "2026-10-12");
        } finally {
            jdbc.update("DELETE FROM reports WHERE id = ?", report);
        }
        assertThat(current.status()).isEqualTo(409);
        assertThat(current.json(mapper).path("message").asText()).contains("informe vigente");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs", Integer.class)).isZero();
        verify(runner, times(0)).run(any(), any());
    }

    @Test
    void aRunnerThatDiesLeavesTheRunFailedWithAnHonestMessage() throws Exception {
        doAnswer(call -> {
            throw new StackOverflowError("simulado");
        }).when(runner).run(any(), any());

        String id = request(NURSE, patient, WEEK.toString()).json(mapper).path("id").asText();
        awaitStatus(id, AgentRun.Status.FALLIDA);

        mockMvc.perform(get("/api/runs/" + id).header("X-Demo-User", NURSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALLIDA"))
                .andExpect(jsonPath("$.message").value("El agente falló de forma inesperada. Intente de nuevo en unos minutos."));
        mockMvc.perform(get("/api/runs/" + id + "/steps").header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$[0].type").value("ERROR"))
                .andExpect(jsonPath("$[0].error").value(true));
    }

    @Test
    void readsStateAndTraceOnlyForNursingAndAnyRunType() throws Exception {
        UUID run = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, status, stop_reason) VALUES (?, 'ESTRUCTURACION', ?, ?, 'COMPLETADA', 'EXITO')",
                run, patient, UUID.fromString(NURSE));
        RunTestSupport.step(steps, run, 1, "Estructurando el registro");
        RunTestSupport.step(steps, run, 2, "Propuesta validada");

        mockMvc.perform(get("/api/runs/" + run + "/steps").header("X-Demo-User", NURSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].message").value("Propuesta validada"));
        long firstId = jdbc.queryForObject("SELECT min(id) FROM agent_steps WHERE run_id = ?", Long.class, run);
        mockMvc.perform(get("/api/runs/" + run + "/steps?after=" + firstId).header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/runs/" + run).header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$.runType").value("ESTRUCTURACION"));
        mockMvc.perform(get("/api/runs/" + run + "/steps").header("X-Demo-User", DOCTOR)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/runs/" + run)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/runs/" + UUID.randomUUID()).header("X-Demo-User", NURSE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontramos esa ejecución."));
    }
}
