package com.patientprogressauditor.consultation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

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
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunner;

// La consulta del medico, «Armar informe» (D31, RF-46): pedirla valida en el servidor (tope de 31 dias, comparacion que
// no se cruza, periodo con registros) sin crear ejecucion si falla; se sigue por /api/runs/{id} (abierto al medico solo
// para sus propias ejecuciones); solo su autor la lee y la imprime, con la marca «Consulta del médico» (RN-12).
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class ConsultationApiTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    private static final String OTHER_DOCTOR = "20000000-0000-0000-0000-000000000004";
    private static final LocalDate START = LocalDate.of(2026, 10, 19);
    private static final LocalDate END = LocalDate.of(2026, 10, 25);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private AgentRunner runner;

    private UUID patient;

    @BeforeEach
    void setUp() {
        patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        clean();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        jdbc.update("DELETE FROM doctor_consultations WHERE patient_id = ?", patient);
        jdbc.update("DELETE FROM agent_steps WHERE run_id IN (SELECT id FROM agent_runs WHERE patient_id = ? AND run_type = 'CONSULTA')", patient);
        jdbc.update("DELETE FROM agent_runs WHERE patient_id = ? AND run_type = 'CONSULTA'", patient);
    }

    // El «agente» de la prueba: guarda una consulta real (con una afirmacion que cita un registro real de la semana y
    // una cifra real de NAUSEAS, igual que AgentRunnerTest), sin pasar por el modelo.
    private void agentSavesAConsultation() {
        doAnswer(call -> {
            AgentRun run = call.getArgument(0);
            UUID entry = jdbc.queryForObject("SELECT id FROM entries WHERE patient_id = ? AND entry_date = ? LIMIT 1", UUID.class, patient, START);
            String content = """
                    {"period":{"start":"%s","end":"%s","compared_with":null},
                     "summary":"Las náuseas aparecieron en el período.",
                     "claims":[{"claim_key":"c1","text":"Las náuseas aparecieron 4 días en el período.",
                       "criterion":"Días del período","entry_ids":["%s"],"figures":[{"key":"NAUSEAS.days","value":4}]}],
                     "changes_vs_previous":[],"recurring_patterns":[],
                     "metrics":{"entries":7,"symptoms":{"NAUSEAS":{"days":4}},"vs_previous":{"comparable":false,"days":{}}},
                     "insufficient_data":[],"out_of_scope":null}""".formatted(START, END, entry);
            jdbc.update("UPDATE agent_runs SET status = 'EN_CURSO', started_at = now() WHERE id = ?", run.getId());
            jdbc.update("INSERT INTO doctor_consultations (id, doctor_id, patient_id, period_start, period_end, content, run_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)", UUID.randomUUID(), run.getRequestedBy(), run.getPatientId(), START, END, content, run.getId());
            jdbc.update("UPDATE agent_runs SET status = 'COMPLETADA', stop_reason = 'EXITO', finished_at = now() WHERE id = ?", run.getId());
            return new AgentRunner.Outcome(AgentRun.Status.COMPLETADA, AgentRun.StopReason.EXITO, null, null);
        }).when(runner).run(any(), any());
    }

    private MvcResult request(String user, String body) throws Exception {
        return mockMvc.perform(post("/api/consultations").header("X-Demo-User", user).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    // El ejecutor corre en otro hilo (D24): se espera a que termine antes de leer lo que escribio.
    private void awaitCompletion(String runId) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            String status = jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?::uuid", String.class, runId);
            if ("COMPLETADA".equals(status) || "FALLIDA".equals(status) || "INCOMPLETA".equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("La ejecución " + runId + " no terminó a tiempo.");
    }

    private String body(UUID patientId, LocalDate start, LocalDate end, LocalDate cmpStart, LocalDate cmpEnd) {
        StringBuilder b = new StringBuilder("{\"patientId\":\"" + patientId + "\",\"periodStart\":\"" + start + "\",\"periodEnd\":\"" + end + "\"");
        if (cmpStart != null) {
            b.append(",\"comparisonStart\":\"").append(cmpStart).append("\",\"comparisonEnd\":\"").append(cmpEnd).append('"');
        }
        return b.append('}').toString();
    }

    @Test
    void aDoctorArmsAConsultationReadsItAndPrintsItWithTheMark() throws Exception {
        agentSavesAConsultation();

        MvcResult created = request(DOCTOR, body(patient, START, END, null, null));
        assertThat(created.getResponse().getStatus()).isEqualTo(202);
        JsonNode runView = mapper.readTree(created.getResponse().getContentAsString());
        String runId = runView.path("id").asText();
        assertThat(runView.path("runType").asText()).isEqualTo("CONSULTA");
        awaitCompletion(runId);
        verify(runner, times(1)).run(any(), any());

        // Se sigue por /api/runs/{id}, abierto al medico para su propia consulta (D31). consultationId es como
        // reportId pero de doctor_consultations: asi la pantalla sabe a donde navegar al terminar.
        UUID consultationId = jdbc.queryForObject("SELECT id FROM doctor_consultations WHERE run_id = ?::uuid", UUID.class, runId);
        mockMvc.perform(get("/api/runs/" + runId).header("X-Demo-User", DOCTOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETADA"))
                .andExpect(jsonPath("$.consultationId").value(consultationId.toString()))
                .andExpect(jsonPath("$.reportId").doesNotExist());

        mockMvc.perform(get("/api/consultations/" + consultationId).header("X-Demo-User", DOCTOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientAlias").value("P-014"))
                .andExpect(jsonPath("$.periodStart").value(START.toString()))
                .andExpect(jsonPath("$.content.summary").value("Las náuseas aparecieron en el período."));

        byte[] pdf = mockMvc.perform(get("/api/consultations/" + consultationId + "/pdf").header("X-Demo-User", DOCTOR))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        String text = text(pdf);
        assertThat(text).contains("CONSULTA DEL MÉDICO").contains("Consulta del médico.")
                .contains("No pasó por la revisión de enfermería").contains("Informe de consulta")
                .contains("Las náuseas aparecieron 4 días en el período.");

        // No entra al historial: ningun Report quedo ligado a esta ejecucion.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE run_id = ?::uuid", Integer.class, runId)).isZero();
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder all = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                all.append(extractor.getTextFromPage(page)).append(' ');
            }
            return all.toString().replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }

    @Test
    void onlyItsOwnDoctorSeesItAnyoneElseGets404() throws Exception {
        agentSavesAConsultation();
        String runId = mapper.readTree(request(DOCTOR, body(patient, START, END, null, null)).getResponse().getContentAsString()).path("id").asText();
        awaitCompletion(runId);
        UUID consultationId = jdbc.queryForObject("SELECT id FROM doctor_consultations WHERE run_id = ?::uuid", UUID.class, runId);

        mockMvc.perform(get("/api/consultations/" + consultationId).header("X-Demo-User", OTHER_DOCTOR)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/consultations/" + consultationId).header("X-Demo-User", NURSE)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/runs/" + runId).header("X-Demo-User", OTHER_DOCTOR)).andExpect(status().isNotFound());
        // Enfermeria SI puede seguir el progreso de cualquier ejecucion (igual que con un informe), pero la consulta en
        // si (doctor_consultations) no es suya: GET /api/runs/{id} no es un informe, no bloquea a enfermeria.
        mockMvc.perform(get("/api/runs/" + runId).header("X-Demo-User", NURSE)).andExpect(status().isOk());
    }

    @Test
    void rejectsWhatCannotBeRequestedWithoutCreatingAnExecution() throws Exception {
        agentSavesAConsultation();
        // Enfermeria no pide consultas (CONSULTATIONS_REQUEST es solo del medico); anonimo, 401.
        assertThat(request(NURSE, body(patient, START, END, null, null)).getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post("/api/consultations").contentType(MediaType.APPLICATION_JSON)
                .content(body(patient, START, END, null, null))).andReturn().getResponse().getStatus()).isEqualTo(401);

        // Fin antes de inicio.
        assertThat(request(DOCTOR, body(patient, END, START, null, null)).getResponse().getStatus()).isEqualTo(400);
        // Mas de 31 dias.
        assertThat(request(DOCTOR, body(patient, START, START.plusDays(32), null, null)).getResponse().getStatus()).isEqualTo(400);
        // El periodo llega mas alla de hoy (la semilla termina el 25-oct-2026; cualquier fecha futura sirve).
        assertThat(request(DOCTOR, body(patient, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 7), null, null)).getResponse().getStatus()).isEqualTo(400);
        // Solo una fecha de comparacion.
        String onlyOne = "{\"patientId\":\"" + patient + "\",\"periodStart\":\"" + START + "\",\"periodEnd\":\"" + END + "\",\"comparisonStart\":\"" + START.minusDays(7) + "\"}";
        assertThat(request(DOCTOR, onlyOne).getResponse().getStatus()).isEqualTo(400);
        // Comparacion que se cruza con el periodo.
        MvcResult overlap = request(DOCTOR, body(patient, START, END, START.minusDays(3), END.minusDays(3)));
        assertThat(overlap.getResponse().getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(overlap.getResponse().getContentAsString()).path("fields").path("comparisonStart").asText()).contains("cruzarse");
        // Paciente que no existe.
        assertThat(request(DOCTOR, body(UUID.randomUUID(), START, END, null, null)).getResponse().getStatus()).isEqualTo(404);
        // Periodo sin registros (fuera de la semilla, que termina el 25-oct-2026).
        MvcResult empty = request(DOCTOR, body(patient, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 7), null, null));
        assertThat(empty.getResponse().getStatus()).isEqualTo(409);
        assertThat(empty.getResponse().getContentAsString()).contains("no tiene registros");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE patient_id = ? AND run_type = 'CONSULTA'", Integer.class, patient)).isZero();
        verify(runner, times(0)).run(any(), any());
    }

    @Test
    void acceptsAValidComparisonThatDoesNotOverlap() throws Exception {
        agentSavesAConsultation();
        // El periodo de comparacion anterior, pegado pero sin cruzarse (termina un dia antes de que empiece el periodo).
        MvcResult ok = request(DOCTOR, body(patient, START, END, START.minusDays(7), START.minusDays(1)));
        assertThat(ok.getResponse().getStatus()).isEqualTo(202);
        String runId = mapper.readTree(ok.getResponse().getContentAsString()).path("id").asText();
        awaitCompletion(runId);
        verify(runner, times(1)).run(any(), any());
    }
}
