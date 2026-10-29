package com.patientprogressauditor.report;

import static com.patientprogressauditor.report.ReportFixtures.DOCTOR;
import static com.patientprogressauditor.report.ReportFixtures.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
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
import com.patientprogressauditor.agent.AgentRunner;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;

// Informes (E12a): la regla de estado para el medico, la edicion con versiones, aprobar y rechazar. Cada prueba corre en
// su transaccion y no deja nada. Los informes citan registros reales de P-033 de la semilla (P-014 y P-021 ya traen sus informes sembrados, E14).
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class ReportsApiTest {

    private static final LocalDate W1 = LocalDate.of(2026, 10, 19);
    private static final LocalDate W2 = LocalDate.of(2026, 9, 28);
    private static final LocalDate W3 = LocalDate.of(2026, 10, 5);
    private static final LocalDate W4 = LocalDate.of(2026, 10, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;
    @Autowired private AgentRunner runner;

    private ReportFixtures fx;
    private UUID p014;

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        p014 = fx.patient("P-033");
    }

    private JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String edit(UUID report, String summary, String text, String criterion, UUID... cited) throws Exception {
        var claim = mapper.createObjectNode().put("claimKey", "c1").put("text", text).put("criterion", criterion);
        var ids = claim.putArray("entryIds");
        for (UUID id : cited) {
            ids.add(id.toString());
        }
        var second = mapper.createObjectNode().put("claimKey", "c2").put("text", "El ánimo se describe estable en los registros citados.")
                .put("criterion", "Texto de los registros de la semana");
        second.putArray("entryIds").add(fx.weekEntries(p014, W4).get(1).getId().toString());
        return mapper.createObjectNode().put("summary", summary).<com.fasterxml.jackson.databind.node.ObjectNode>set("claims",
                mapper.createArrayNode().add(claim).add(second)).toString();
    }

    private ResultActions putContent(String user, UUID report, String json) throws Exception {
        return mockMvc.perform(put("/api/reports/" + report + "/content").header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions action(String user, UUID report, String what, String json) throws Exception {
        return mockMvc.perform(post("/api/reports/" + report + "/" + what).header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---- lectura: la regla de estado la pone el servidor ----

    @Test
    void theDoctorListsOnlyApprovedReportsAndNursingListsAllOfThem() throws Exception {
        UUID pending = fx.report(p014, W1, "PENDIENTE_REVISION");
        UUID incomplete = fx.report(p014, W2, "INCOMPLETO");
        UUID approved = fx.report(p014, W3, "APROBADO");
        UUID rejected = fx.report(p014, W4, "RECHAZADO");

        List<String> nurse = ids(body(mockMvc.perform(get("/api/reports?patientId=" + p014).header("X-Demo-User", NURSE))));
        assertThat(nurse).containsExactly(pending.toString(), rejected.toString(), approved.toString(), incomplete.toString());

        List<String> doctor = ids(body(mockMvc.perform(get("/api/reports?patientId=" + p014).header("X-Demo-User", DOCTOR))));
        assertThat(doctor).containsExactly(approved.toString());
        // Pedir un estado que no ve no es un error ni lo revela: lista vacia.
        assertThat(body(mockMvc.perform(get("/api/reports?status=PENDIENTE_REVISION").header("X-Demo-User", DOCTOR)))).isEmpty();
        assertThat(ids(body(mockMvc.perform(get("/api/reports?status=INCOMPLETO&patientId=" + p014).header("X-Demo-User", NURSE)))))
                .containsExactly(incomplete.toString());
        mockMvc.perform(get("/api/reports")).andExpect(status().isUnauthorized());
    }

    private List<String> ids(JsonNode list) {
        return list.findValuesAsText("id");
    }

    @Test
    void theDoctorCannotOpenAReportThatIsNotApprovedAndItLooksLikeItDoesNotExist() throws Exception {
        for (String state : List.of("PENDIENTE_REVISION", "INCOMPLETO", "RECHAZADO")) {
            UUID report = fx.report(p014, state.equals("INCOMPLETO") ? W1 : state.equals("RECHAZADO") ? W2 : W3, state);
            mockMvc.perform(get("/api/reports/" + report).header("X-Demo-User", DOCTOR))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("No encontramos ese informe."));
            mockMvc.perform(get("/api/reports/" + report + "/trace").header("X-Demo-User", DOCTOR)).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/reports/" + report).header("X-Demo-User", NURSE)).andExpect(status().isOk());
        }
    }

    @Test
    void theDetailBringsEachCitedEntryWithItsDateAndOriginalText() throws Exception {
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        List<Entry> week = fx.weekEntries(p014, W4);

        JsonNode detail = body(mockMvc.perform(get("/api/reports/" + report).header("X-Demo-User", NURSE)).andExpect(status().isOk()));
        assertThat(detail.path("report").path("patientAlias").asText()).isEqualTo("P-033");
        assertThat(detail.path("report").path("status").asText()).isEqualTo("PENDIENTE_REVISION");
        assertThat(detail.path("version").path("versionNo").asInt()).isEqualTo(1);
        assertThat(detail.path("version").path("origin").asText()).isEqualTo("MODELO");
        assertThat(detail.path("version").path("content").path("claims")).hasSize(2);
        JsonNode cited = detail.path("citations").path("c1").get(0);
        assertThat(cited.path("id").asText()).isEqualTo(week.get(0).getId().toString());
        assertThat(cited.path("date").asText()).isEqualTo(week.get(0).getEntryDate().toString());
        assertThat(cited.path("body").asText()).isEqualTo(week.get(0).getBody());
        assertThat(detail.path("versions")).hasSize(1);
    }

    // ---- edicion: versiones ----

    @Test
    void editingKeepsTheModelsVersionAndAddsTheNursesWithItsCitations() throws Exception {
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        List<Entry> week = fx.weekEntries(p014, W4);
        JsonNode original = body(mockMvc.perform(get("/api/reports/" + report + "/versions/1").header("X-Demo-User", NURSE)));

        JsonNode edited = body(putContent(NURSE, report, edit(report, "La semana tiene registros todos los días y las náuseas aparecen en 3 días.",
                "Las náuseas se registraron en 3 días; el lunes con más detalle.", "Texto de los registros de la semana",
                week.get(0).getId(), week.get(2).getId())).andExpect(status().isOk()));

        assertThat(edited.path("version").path("versionNo").asInt()).isEqualTo(2);
        assertThat(edited.path("version").path("origin").asText()).isEqualTo("ENFERMERIA");
        assertThat(edited.path("version").path("editedBy").asText()).isEqualTo("Camilo Torres");
        assertThat(edited.path("versions")).hasSize(2);
        assertThat(edited.path("citations").path("c1")).hasSize(2);
        // La cifra y lo que falta siguen siendo los del codigo; la afirmacion reescrita ya no arrastra cifras del agente.
        assertThat(edited.path("version").path("content").path("metrics")).isEqualTo(original.path("content").path("metrics"));
        assertThat(edited.path("version").path("content").path("claims").get(0).path("figures")).isEmpty();
        // La afirmacion que no se toco las conserva.
        assertThat(edited.path("version").path("content").path("claims").get(1).path("text").asText())
                .isEqualTo(original.path("content").path("claims").get(1).path("text").asText());
        // La version 1 sigue siendo exactamente la del modelo (RF-25).
        JsonNode stillOriginal = body(mockMvc.perform(get("/api/reports/" + report + "/versions/1").header("X-Demo-User", NURSE)));
        assertThat(stillOriginal).isEqualTo(original);
        assertThat(stillOriginal.path("origin").asText()).isEqualTo("MODELO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_citations c JOIN report_versions v ON v.id = c.report_version_id WHERE v.report_id = ? AND v.version_no = 2",
                Integer.class, report)).isEqualTo(3);
        mockMvc.perform(get("/api/reports/" + report + "/versions/9").header("X-Demo-User", NURSE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/" + report + "/versions/1").header("X-Demo-User", DOCTOR)).andExpect(status().isForbidden());
    }

    @Test
    void anEditThatBreaksARuleIsRefusedWithTheReasonPerField() throws Exception {
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        List<Entry> week = fx.weekEntries(p014, W4);
        UUID otherPatient = entries.findAll().stream().filter(e -> !e.getPatientId().equals(p014)).findFirst().orElseThrow().getId();
        UUID later = fx.weekEntries(p014, LocalDate.of(2026, 10, 19)).get(0).getId();
        String ok = "Las náuseas se registraron en 3 días de la semana.";
        String crit = "Texto de los registros de la semana";
        String summary = "La semana tiene registros todos los días.";

        // Cita de otro paciente.
        putContent(NURSE, report, edit(report, summary, ok, crit, otherPatient)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields['claims[0].entryIds']").value("Una de las citas no es un registro de este paciente."));
        // Cita posterior a la semana del informe.
        putContent(NURSE, report, edit(report, summary, ok, crit, later)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields['claims[0].entryIds']").value("No se puede citar un registro posterior al período del informe."));
        // Un diagnostico, una dosis.
        putContent(NURSE, report, edit(report, summary, "Parece una infección, conviene suspender el tratamiento.", crit, week.get(0).getId()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fields['claims[0].text']").exists());
        putContent(NURSE, report, edit(report, summary, "Se recomienda 500 mg cada ocho horas.", crit, week.get(0).getId()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fields['claims[0].text']").exists());
        // Una cifra que el sistema no calculo.
        putContent(NURSE, report, edit(report, summary, "Las náuseas se registraron en 9 días de la semana.", crit, week.get(0).getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields['claims[0].text']").value(org.hamcrest.Matchers.containsString("El número 9")));
        // Sin cita, resumen vacio, sin afirmaciones.
        putContent(NURSE, report, edit(report, " ", ok, crit)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields.summary").exists()).andExpect(jsonPath("$.fields['claims[0].entryIds']").exists());
        putContent(NURSE, report, "{\"summary\":\"Resumen\",\"claims\":[]}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fields.claims").exists());
        // Sin cambios no se crea una version.
        JsonNode v1 = body(mockMvc.perform(get("/api/reports/" + report + "/versions/1").header("X-Demo-User", NURSE)));
        String same = mapper.createObjectNode().put("summary", v1.path("content").path("summary").asText())
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("claims", sameClaims(v1)).toString();
        putContent(NURSE, report, same).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("No hay cambios que guardar."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_versions WHERE report_id = ?", Integer.class, report)).isEqualTo(1);
        putContent(DOCTOR, report, same).andExpect(status().isForbidden());
    }

    private com.fasterxml.jackson.databind.node.ArrayNode sameClaims(JsonNode version) {
        var out = mapper.createArrayNode();
        version.path("content").path("claims").forEach(c -> {
            var node = out.addObject().put("claimKey", c.path("claim_key").asText()).put("text", c.path("text").asText())
                    .put("criterion", c.path("criterion").asText());
            c.path("entry_ids").forEach(id -> node.withArray("entryIds").add(id.asText()));
        });
        return out;
    }

    // ---- decisiones ----

    @Test
    void approvingRecordsWhoWhenAndWhichVersionAndTheDoctorSeesThatVersion() throws Exception {
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        List<Entry> week = fx.weekEntries(p014, W4);
        putContent(NURSE, report, edit(report, "La semana tiene registros todos los días.", "Las náuseas se registraron en 3 días de la semana.",
                "Texto de los registros de la semana", week.get(0).getId())).andExpect(status().isOk());

        action(DOCTOR, report, "approve", "").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, report)).isEqualTo("PENDIENTE_REVISION");

        JsonNode approved = body(action(NURSE, report, "approve", "").andExpect(status().isOk()));
        assertThat(approved.path("report").path("status").asText()).isEqualTo("APROBADO");
        assertThat(approved.path("report").path("reviewedBy").asText()).isEqualTo("Camilo Torres");
        assertThat(approved.path("report").path("reviewedAt").asText()).isNotBlank();
        assertThat(approved.path("approvedVersionNo").asInt()).isEqualTo(2);

        JsonNode forDoctor = body(mockMvc.perform(get("/api/reports/" + report).header("X-Demo-User", DOCTOR)).andExpect(status().isOk()));
        assertThat(forDoctor.path("version").path("versionNo").asInt()).isEqualTo(2);
        assertThat(forDoctor.path("version").path("content").path("claims").get(0).path("text").asText())
                .isEqualTo("Las náuseas se registraron en 3 días de la semana.");
        assertThat(forDoctor.path("versions")).as("el historial de versiones es de enfermería").isEmpty();
        assertThat(forDoctor.path("citations").path("c1")).isNotEmpty();
        mockMvc.perform(get("/api/reports/" + report + "/trace").header("X-Demo-User", DOCTOR)).andExpect(status().isOk());
    }

    @Test
    void everyTransitionThatIsNotDefinedIsRefusedWith409AndChangesNothing() throws Exception {
        LocalDate[] weeks = { W1, W2, W3 };
        int i = 0;
        for (String state : List.of("INCOMPLETO", "APROBADO", "RECHAZADO")) {
            UUID report = fx.report(p014, weeks[i++], state);
            for (String what : List.of("approve", "reject")) {
                action(NURSE, report, what, "{\"reason\":\"Motivo\"}").andExpect(status().isConflict())
                        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("No se puede")));
            }
            putContent(NURSE, report, "{\"summary\":\"Resumen\",\"claims\":[]}").andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, report)).isEqualTo(state);
        }
        // Aprobar dos veces: la segunda es una transicion que no existe.
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        action(NURSE, report, "approve", "").andExpect(status().isOk());
        action(NURSE, report, "approve", "").andExpect(status().isConflict());
        action(NURSE, report, "reject", "{\"reason\":\"Tarde\"}").andExpect(status().isConflict());
        action(NURSE, UUID.randomUUID(), "approve", "").andExpect(status().isNotFound());
    }

    @Test
    void rejectingNeedsAReasonSavesItAndLetsTheWeekBeRequestedAgain() throws Exception {
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        assertThat(runner.blockedReason(p014, W4)).as("mientras hay un informe vigente no se pide otro").contains("informe vigente");

        action(NURSE, report, "reject", "{\"reason\":\"  \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").value("Escriba el motivo del rechazo."));
        action(DOCTOR, report, "reject", "{\"reason\":\"No me gusta\"}").andExpect(status().isForbidden());

        JsonNode rejected = body(action(NURSE, report, "reject", "{\"reason\":\"No concuerda con el registro del martes.\"}")
                .andExpect(status().isOk()));
        assertThat(rejected.path("report").path("status").asText()).isEqualTo("RECHAZADO");
        assertThat(rejected.path("report").path("statusReason").asText()).isEqualTo("No concuerda con el registro del martes.");
        assertThat(rejected.path("report").path("reviewedBy").asText()).isEqualTo("Camilo Torres");
        assertThat(runner.blockedReason(p014, W4)).as("tras rechazar se puede pedir uno nuevo").isNull();
    }

    // ---- evidencia y semanas ----

    @Test
    void theTraceOfTheRunThatProducedTheReportIsReadFromTheReport() throws Exception {
        UUID report = fx.report(p014, W4, "APROBADO");
        // Un informe sin ejecucion (p. ej. el sembrado) tiene la traza vacia.
        UUID seeded = fx.report(p014, W3, "PENDIENTE_REVISION");
        mockMvc.perform(get("/api/reports/" + seeded + "/trace").header("X-Demo-User", NURSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        UUID run = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status, stop_reason) VALUES (?, 'INFORME', ?, ?, ?, ?, 'COMPLETADA', 'EXITO')",
                run, p014, UUID.fromString(NURSE), W4, W4.plusDays(6));
        jdbc.update("INSERT INTO agent_steps (run_id, seq, step_type, tool_name, message) VALUES (?, 1, 'HERRAMIENTA', 'get_week_entries', 'Leyendo los registros de la semana'), (?, 2, 'ESTADO', NULL, 'Borrador guardado')", run, run);
        jdbc.update("UPDATE reports SET run_id = ? WHERE id = ?", run, report);

        for (String user : List.of(NURSE, DOCTOR)) {
            mockMvc.perform(get("/api/reports/" + report + "/trace").header("X-Demo-User", user))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].tool").value("get_week_entries"));
        }
    }

    @Test
    void onlyCompleteWeeksWithEntriesCanBeRequestedAndEachShowsItsReport() throws Exception {
        UUID report = fx.report(p014, W4, "PENDIENTE_REVISION");
        // Un registro de la semana en curso: esa semana no aparece (tabla 3.3).
        UUID author = UUID.fromString(NURSE);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("America/Bogota"));
        jdbc.update("INSERT INTO entries (id, patient_id, entry_date, body, author_id, source, structuring_status) VALUES (?, ?, ?, 'Hoy sin novedad.', ?, 'TEXTO', 'SIN_ESTRUCTURAR')",
                UUID.randomUUID(), p014, today, author);

        JsonNode weeks = body(mockMvc.perform(get("/api/patients/" + p014 + "/report-weeks").header("X-Demo-User", NURSE)).andExpect(status().isOk()));
        List<String> starts = weeks.findValuesAsText("weekStart");
        assertThat(starts).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(starts).contains("2026-10-19", "2026-10-12").doesNotContain(today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString());
        JsonNode w4 = null;
        for (JsonNode w : weeks) {
            if (w.path("weekStart").asText().equals("2026-10-12")) {
                w4 = w;
            }
        }
        assertThat(w4.path("reportId").asText()).isEqualTo(report.toString());
        assertThat(w4.path("canGenerate").asBoolean()).isFalse();
        assertThat(w4.path("reason").asText()).isEqualTo("Ya hay un informe de esta semana.");
        mockMvc.perform(get("/api/patients/" + p014 + "/report-weeks").header("X-Demo-User", DOCTOR)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID() + "/report-weeks").header("X-Demo-User", NURSE)).andExpect(status().isNotFound());

        // Pedir la semana en curso se rechaza antes de crear ninguna ejecucion.
        mockMvc.perform(post("/api/runs").header("X-Demo-User", NURSE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + p014 + "\",\"weekStart\":\"" + today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.weekStart").value(org.hamcrest.Matchers.containsString("completa")));
    }
}
