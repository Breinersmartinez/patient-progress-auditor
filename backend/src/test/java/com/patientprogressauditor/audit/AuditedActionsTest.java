package com.patientprogressauditor.audit;

import static com.patientprogressauditor.report.ReportFixtures.DOCTOR;
import static com.patientprogressauditor.report.ReportFixtures.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.report.ReportFixtures;
import com.patientprogressauditor.support.TestAuth;

// Paso 3 de docs/auditoria/plan.md, criterios 1, 2, 4 y 8 del encargo: cada accion sensible de informes y el registro del
// medico dejan UNA anotacion, con quien, rol y objeto; una accion que falla no deja ninguna; y ninguna lleva texto clinico.
// Cada prueba corre en su transaccion y no deja nada (las anotaciones tambien se revierten). El criterio 5 (la accion se
// revierte si la anotacion falla) necesita commits de verdad y esta en AuditRollbackTest.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class AuditedActionsTest {

    private static final LocalDate W3 = LocalDate.of(2026, 10, 5);
    private static final LocalDate W4 = LocalDate.of(2026, 10, 12);
    private static final String REASON = "La paciente refiere nauseas intensas que el informe no recoge.";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;

    private ReportFixtures fx;
    private UUID patient;
    private final List<String> clinicalTexts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        patient = fx.patient("P-033");
        clinicalTexts.add("P-033");
        clinicalTexts.add(REASON);
        clinicalTexts.add("náuseas");
        clinicalTexts.add("ánimo");
        for (LocalDate week : List.of(W3, W4)) {
            fx.weekEntries(patient, week).forEach(e -> clinicalTexts.add(e.getBody()));
        }
    }

    private List<Map<String, Object>> rows(UUID entityId) {
        return jdbc.queryForList("SELECT actor_id, actor_role, action, entity_type, before_state::text AS before,"
                + " after_state::text AS after FROM audit_logs WHERE entity_id = ? ORDER BY id", entityId);
    }

    private ResultActions action(String user, UUID report, String what, String json) throws Exception {
        return mockMvc.perform(post("/api/reports/" + report + "/" + what).with(TestAuth.as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // Criterio 8: el antes y el despues de TODA anotacion de la prueba, sin texto de registros ni de informes, sin el
    // codigo del paciente ni el motivo escrito por enfermeria.
    private void assertNoClinicalText() {
        List<String> states = jdbc.queryForList("SELECT coalesce(before_state::text, '') || ' ' || coalesce(after_state::text, '')"
                + " FROM audit_logs", String.class);
        assertThat(states).isNotEmpty();
        for (String state : states) {
            for (String text : clinicalTexts) {
                assertThat(state).doesNotContainIgnoringCase(text);
            }
        }
    }

    @Test
    void approvingAndSendingLeaveOneAnnotationEachWithWhoRoleAndObject() throws Exception {
        UUID report = fx.report(patient, W4, "PENDIENTE_REVISION");

        action(NURSE, report, "approve", "").andExpect(status().isOk());
        action(NURSE, report, "send", "").andExpect(status().isOk());

        List<Map<String, Object>> rows = rows(report);
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("APROBAR_INFORME", "ENVIAR_INFORME");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("actor_id")).isEqualTo(UUID.fromString(NURSE));
            assertThat(r.get("actor_role")).isEqualTo("ENFERMERIA");
            assertThat(r.get("entity_type")).isEqualTo("REPORT");
        });
        JsonNode approved = mapper.readTree((String) rows.get(0).get("after"));
        assertThat(mapper.readTree((String) rows.get(0).get("before")).path("status").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(approved.path("status").asText()).isEqualTo("APROBADO");
        assertThat(approved.path("approvedVersionNo").asInt()).isEqualTo(1);
        assertThat(mapper.readTree((String) rows.get(1).get("after")).path("status").asText()).isEqualTo("ENVIADO");
        assertNoClinicalText();
    }

    @Test
    void rejectingKeepsOnlyTheLengthOfTheReasonNeverItsText() throws Exception {
        UUID report = fx.report(patient, W4, "PENDIENTE_REVISION");

        action(NURSE, report, "reject", mapper.writeValueAsString(Map.of("reason", REASON))).andExpect(status().isOk());

        List<Map<String, Object>> rows = rows(report);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("action")).isEqualTo("RECHAZAR_INFORME");
        JsonNode after = mapper.readTree((String) rows.get(0).get("after"));
        assertThat(after.path("status").asText()).isEqualTo("RECHAZADO");
        assertThat(after.path("reasonLength").asInt()).isEqualTo(REASON.length());
        assertNoClinicalText();
    }

    @Test
    void editingKeepsTheVersionNumbersNotTheContent() throws Exception {
        UUID report = fx.report(patient, W4, "PENDIENTE_REVISION");
        List<Entry> week = fx.weekEntries(patient, W4);
        ObjectNode edit = mapper.createObjectNode().put("summary", "La semana tiene registros todos los días y las náuseas aparecen en 3 días.");
        var claims = edit.putArray("claims");
        claims.addObject().put("claimKey", "c1").put("text", "Las náuseas se registraron en 3 días; el lunes con más detalle.")
                .put("criterion", "Texto de los registros de la semana").putArray("entryIds").add(week.get(0).getId().toString());
        claims.addObject().put("claimKey", "c2").put("text", "El ánimo se describe estable en los registros citados.")
                .put("criterion", "Texto de los registros de la semana").putArray("entryIds").add(week.get(1).getId().toString());
        clinicalTexts.add("el lunes con más detalle");

        mockMvc.perform(put("/api/reports/" + report + "/content").with(TestAuth.as(NURSE))
                .contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isOk());

        List<Map<String, Object>> rows = rows(report);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("action")).isEqualTo("EDITAR_INFORME");
        assertThat(mapper.readTree((String) rows.get(0).get("before")).path("versionNo").asInt()).isEqualTo(1);
        assertThat(mapper.readTree((String) rows.get(0).get("after")).path("versionNo").asInt()).isEqualTo(2);
        assertNoClinicalText();
    }

    // Criterio 4: una accion que el servidor rechaza no deja anotacion.
    @Test
    void anActionThatFailsLeavesNoAnnotation() throws Exception {
        UUID report = fx.report(patient, W3, "APROBADO");

        action(NURSE, report, "approve", "").andExpect(status().isConflict());
        action(NURSE, report, "reject", "{\"reason\":\"Tarde\"}").andExpect(status().isConflict());
        // El medico no puede aprobar (permiso): tampoco deja rastro.
        action(DOCTOR, report, "approve", "").andExpect(status().isForbidden());

        assertThat(rows(report)).isEmpty();
    }

    // Criterio 2: el registro del medico se anota; el de enfermeria no (RF-35 y D12).
    @Test
    void onlyTheDoctorsEntryIsAnnotated() throws Exception {
        String doctorText = "Refiere cefalea leve por la tarde, sin vomitos.";
        String nurseText = "Durmio bien, come la mitad del almuerzo.";
        clinicalTexts.add(doctorText);
        clinicalTexts.add(nurseText);

        UUID byDoctor = createEntry(DOCTOR, doctorText);
        UUID byNurse = createEntry(NURSE, nurseText);

        List<Map<String, Object>> rows = rows(byDoctor);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("action", "REGISTRAR_ENTRADA_MEDICO").containsEntry("actor_role", "MEDICO")
                .containsEntry("entity_type", "ENTRY").containsEntry("actor_id", UUID.fromString(DOCTOR));
        assertThat(mapper.readTree((String) rows.get(0).get("after")).path("source").asText()).isEqualTo("TEXTO");
        assertThat(rows(byNurse)).isEmpty();
        assertNoClinicalText();
    }

    private UUID createEntry(String user, String body) throws Exception {
        String json = mapper.writeValueAsString(Map.of("entryDate", "2026-10-20", "body", body));
        String response = mockMvc.perform(post("/api/patients/" + patient + "/entries").with(TestAuth.as(user))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).path("id").asText());
    }
}
