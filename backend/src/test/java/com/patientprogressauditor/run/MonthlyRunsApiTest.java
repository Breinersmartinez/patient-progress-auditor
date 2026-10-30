package com.patientprogressauditor.run;

import static com.patientprogressauditor.run.RunTestSupport.DOCTOR;
import static com.patientprogressauditor.run.RunTestSupport.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

// Pedir el informe MENSUAL (E13, RF-36, RN-10). Como en RunsApiTest, el agente se cambia por uno que espera a la prueba: aqui
// se prueba la regla de las cuatro semanas y el segundo plano, no el bucle (AgentRunnerTest). P-014 trae sus semanas del
// 21-sep al 12-oct aprobadas de la semilla; P-021 solo tiene una pendiente.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class MonthlyRunsApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @MockitoSpyBean private AgentRunner runner;

    private UUID p014;
    private UUID p021;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        p014 = RunTestSupport.patient(jdbc, "P-014");
        p021 = RunTestSupport.patient(jdbc, "P-021");
        RunTestSupport.clean(jdbc, p014);
        RunTestSupport.clean(jdbc, p021);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        release.countDown();
        RunTestSupport.awaitNoOpenRuns(jdbc, p014);
        RunTestSupport.clean(jdbc, p014);
        RunTestSupport.clean(jdbc, p021);
    }

    private void agentWaitsForTheTest() {
        doAnswer(call -> {
            AgentRun run = call.getArgument(0);
            RunTestSupport.start(runs, run);
            RunTestSupport.step(steps, run.getId(), 1, "Consultando al modelo");
            release.await(20, TimeUnit.SECONDS);
            RunTestSupport.complete(runs, run);
            return null;
        }).when(runner).run(any(), any());
    }

    private MvcResult request(String user, UUID patient, String start, String type) throws Exception {
        String typePart = type == null ? "" : ",\"type\":\"" + type + "\"";
        return mockMvc.perform(post("/api/runs").header("X-Demo-User", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + patient + "\",\"weekStart\":\"" + start + "\"" + typePart + "}")).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void fourApprovedWeeksStartAMonthlyRunInTheBackgroundAndARepeatReturnsTheSameOne() throws Exception {
        agentWaitsForTheTest();

        MvcResult first = request(NURSE, p014, "2026-09-21", "MENSUAL");
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        JsonNode run = json(first);
        assertThat(run.path("runType").asText()).isEqualTo("MENSUAL");
        assertThat(run.path("periodStart").asText()).isEqualTo("2026-09-21");
        assertThat(run.path("periodEnd").asText()).isEqualTo("2026-10-18");
        assertThat(run.path("status").asText()).isEqualTo("EN_COLA");

        MvcResult again = request(NURSE, p014, "2026-09-21", "MENSUAL");
        assertThat(again.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(again).path("id").asText()).isEqualTo(run.path("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE patient_id = ? AND run_type = 'MENSUAL'", Integer.class, p014)).isEqualTo(1);
    }

    @Test
    void aWeeklyRunOfTheSameFirstWeekDoesNotCollideWithTheMonthlyOne() throws Exception {
        agentWaitsForTheTest();
        // 21-sep tiene informe semanal vigente: se pide el de otra semana que no lo tiene, el 19-oct, y el mensual a la vez
        assertThat(request(NURSE, p014, "2026-09-21", "MENSUAL").getResponse().getStatus()).isEqualTo(202);
        MvcResult weekly = request(NURSE, p014, "2026-10-19", null);

        assertThat(weekly.getResponse().getStatus()).isEqualTo(202);
        assertThat(json(weekly).path("runType").asText()).isEqualTo("INFORME");
    }

    @Test
    void aWeekWithoutApprovalBlocksTheMonthlyNamesTheWeekAndCreatesNoRun() throws Exception {
        agentWaitsForTheTest();

        // P-014 del 28-sep al 25-oct: la ultima semana (19-oct) no tiene informe semanal
        MvcResult blocked = request(NURSE, p014, "2026-09-28", "MENSUAL");

        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        JsonNode body = json(blocked);
        assertThat(body.toString()).contains("19 de octubre al 25 de octubre (no tiene informe semanal)");
        assertThat(body.toString()).doesNotContain("28 de septiembre");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE patient_id = ?", Integer.class, p014)).isZero();
    }

    @Test
    void severalMissingWeeksAreAllNamedWithTheirState() throws Exception {
        MvcResult blocked = request(NURSE, p021, "2026-09-28", "MENSUAL");

        assertThat(blocked.getResponse().getStatus()).isEqualTo(409);
        String text = blocked.getResponse().getContentAsString();
        // P-021 solo tiene el informe de la semana 3 (12-oct) pendiente de revision
        assertThat(text).contains("28 de septiembre al 4 de octubre (no tiene informe semanal)")
                .contains("5 de octubre al 11 de octubre (no tiene informe semanal)")
                .contains("12 de octubre al 18 de octubre (su informe está pendiente de revisión)")
                .contains("19 de octubre al 25 de octubre (no tiene informe semanal)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE patient_id = ?", Integer.class, p021)).isZero();
    }

    @Test
    void theWindowMustStartOnAMondayAndBeFinished() throws Exception {
        assertThat(request(NURSE, p014, "2026-09-22", "MENSUAL").getResponse().getStatus()).isEqualTo(400);
        // la ventana que empieza el 26-oct termina el 22-nov, despues de hoy
        assertThat(request(NURSE, p014, "2026-10-26", "MENSUAL").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void onlyNursingCanAskAndThePatientMustExist() throws Exception {
        assertThat(request(DOCTOR, p014, "2026-09-21", "MENSUAL").getResponse().getStatus()).isEqualTo(403);
        assertThat(request(NURSE, UUID.randomUUID(), "2026-09-21", "MENSUAL").getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void aRequestWithoutATypeIsWeeklyAsBefore() throws Exception {
        agentWaitsForTheTest();

        MvcResult weekly = request(NURSE, p014, "2026-10-19", null);

        assertThat(weekly.getResponse().getStatus()).isEqualTo(202);
        assertThat(json(weekly).path("runType").asText()).isEqualTo("INFORME");
        assertThat(json(weekly).path("periodEnd").asText()).isEqualTo("2026-10-25");
    }
}
