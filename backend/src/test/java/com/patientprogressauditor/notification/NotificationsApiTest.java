package com.patientprogressauditor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

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
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.report.ReportFixtures;

// Notificaciones al medico (RF-45, D30): quien recibe cada aviso, que no lo recibe el autor de su propia entrada,
// que solo enviar notifica (no aprobar), que estructurar o dictar no notifican, y que un medico no lee ni marca las
// de otro. Cada prueba corre en su transaccion y no deja nada (P-033 de la semilla, como ReportsApiTest).
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class NotificationsApiTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    private static final String OTHER_DOCTOR = "20000000-0000-0000-0000-000000000004";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;

    private ReportFixtures fx;
    private UUID p033;
    private UUID newPatient;

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        p033 = fx.patient("P-033");
        newPatient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, alias) VALUES (?, 'P-901')", newPatient);
    }

    private ResultActions createEntry(String user) throws Exception {
        String json = mapper.writeValueAsString(Map.of("entryDate", "2026-10-20", "body", "Tranquilo, sin novedades."));
        return mockMvc.perform(post("/api/patients/" + newPatient + "/entries").header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions listAs(String user) throws Exception {
        return mockMvc.perform(get("/api/notifications").header("X-Demo-User", user));
    }

    private List<Map<String, Object>> rowsFor(String recipient) {
        return jdbc.queryForList("SELECT notification_type, patient_id, entry_id, report_id, read_at FROM notifications"
                + " WHERE recipient_id = ?", UUID.fromString(recipient));
    }

    @Test
    void anEntryByTheNurseNotifiesBothActiveDoctors() throws Exception {
        createEntry(NURSE).andExpect(status().isCreated());

        assertThat(rowsFor(DOCTOR)).anySatisfy(r -> assertThat(r).containsEntry("notification_type", "ENTRY")
                .containsEntry("patient_id", newPatient));
        assertThat(rowsFor(OTHER_DOCTOR)).anySatisfy(r -> assertThat(r).containsEntry("notification_type", "ENTRY")
                .containsEntry("patient_id", newPatient));
    }

    @Test
    void anEntryByADoctorNotifiesTheOtherDoctorButNotHimself() throws Exception {
        int before = rowsFor(DOCTOR).size();
        createEntry(DOCTOR).andExpect(status().isCreated());

        assertThat(rowsFor(DOCTOR)).hasSize(before);
        assertThat(rowsFor(OTHER_DOCTOR)).anySatisfy(r -> assertThat(r).containsEntry("notification_type", "ENTRY")
                .containsEntry("patient_id", newPatient));
    }

    @Test
    void nursingCannotListOrMarkNotificationsForbidden() throws Exception {
        listAs(NURSE).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/" + UUID.randomUUID() + "/read").header("X-Demo-User", NURSE))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/patients/" + newPatient + "/notifications/read").header("X-Demo-User", NURSE))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvingDoesNotNotifyButSendingDoes() throws Exception {
        UUID report = fx.report(p033, LocalDate.of(2026, 10, 19), "APROBADO");
        int beforeSend = rowsFor(DOCTOR).stream().filter(r -> report.equals(r.get("report_id"))).toList().size();
        assertThat(beforeSend).isZero();

        mockMvc.perform(post("/api/reports/" + report + "/send").header("X-Demo-User", NURSE)).andExpect(status().isOk());

        assertThat(rowsFor(DOCTOR)).anySatisfy(r -> assertThat(r).containsEntry("notification_type", "REPORT")
                .containsEntry("report_id", report));
        assertThat(rowsFor(OTHER_DOCTOR)).anySatisfy(r -> assertThat(r).containsEntry("notification_type", "REPORT")
                .containsEntry("report_id", report));
    }

    // La campana no distingue el tipo de informe al guardar la fila: probar solo el semanal dejaba sin cubrir que
    // `reportType` llegue correcto para el mensual (hallazgo de la validación de la Pieza 5, 2026-11-08).
    @Test
    void sendingAMonthlyReportNotifiesWithReportTypeMensual() throws Exception {
        UUID report = fx.monthlyReport(p033, LocalDate.of(2026, 10, 19), "APROBADO");

        mockMvc.perform(post("/api/reports/" + report + "/send").header("X-Demo-User", NURSE)).andExpect(status().isOk());

        assertThat(rowsFor(DOCTOR)).anySatisfy(r -> assertThat(r).containsEntry("notification_type", "REPORT")
                .containsEntry("report_id", report));
        String body = listAs(DOCTOR).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode mine = StreamSupport.stream(mapper.readTree(body).spliterator(), false)
                .filter(n -> report.toString().equals(n.path("reportId").asText())).findFirst().orElseThrow();
        assertThat(mine.path("reportType").asText()).isEqualTo("MENSUAL");
    }

    @Test
    void structuringAProposalDoesNotNotifyAnyone() throws Exception {
        int before = rowsFor(DOCTOR).size() + rowsFor(OTHER_DOCTOR).size();
        String json = mapper.writeValueAsString(Map.of("body", "Náuseas leves."));
        mockMvc.perform(post("/api/patients/" + newPatient + "/entries/propose").header("X-Demo-User", NURSE)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk());

        assertThat(rowsFor(DOCTOR).size() + rowsFor(OTHER_DOCTOR).size()).isEqualTo(before);
    }

    @Test
    void aDoctorCannotMarkAnotherDoctorsNotificationRead() throws Exception {
        createEntry(NURSE).andExpect(status().isCreated());
        UUID theirs = jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_id = ? AND patient_id = ?",
                UUID.class, UUID.fromString(OTHER_DOCTOR), newPatient);

        mockMvc.perform(post("/api/notifications/" + theirs + "/read").header("X-Demo-User", DOCTOR))
                .andExpect(status().isNotFound());

        Boolean stillUnread = jdbc.queryForObject("SELECT read_at IS NULL FROM notifications WHERE id = ?", Boolean.class, theirs);
        assertThat(stillUnread).isTrue();
    }

    @Test
    void openingAPatientsFileMarksOnlyTheirUnreadEntryNotificationsRead() throws Exception {
        createEntry(NURSE).andExpect(status().isCreated());

        mockMvc.perform(post("/api/patients/" + newPatient + "/notifications/read").header("X-Demo-User", DOCTOR))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> mine = jdbc.queryForList(
                "SELECT read_at FROM notifications WHERE recipient_id = ? AND patient_id = ?",
                UUID.fromString(DOCTOR), newPatient);
        assertThat(mine).isNotEmpty().allSatisfy(r -> assertThat(r.get("read_at")).isNotNull());
    }

    // El plan («Cuándo se marca leída») y el mockup (auditor.fuente.html:421, n.tipo === 'registro') dicen que
    // abrir la ficha marca leidas solo las de registro: un informe recien enviado no debe perder su punto sin que
    // el medico lo haya abierto (hallazgo de la validacion de la Pieza 5, 2026-11-08).
    @Test
    void openingAPatientsFileDoesNotMarkTheirReportNotificationsRead() throws Exception {
        String json = mapper.writeValueAsString(Map.of("entryDate", "2026-10-26", "body", "Tranquilo, sin novedades."));
        mockMvc.perform(post("/api/patients/" + p033 + "/entries").header("X-Demo-User", NURSE)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());
        UUID report = fx.report(p033, LocalDate.of(2026, 10, 19), "APROBADO");
        mockMvc.perform(post("/api/reports/" + report + "/send").header("X-Demo-User", NURSE)).andExpect(status().isOk());

        mockMvc.perform(post("/api/patients/" + p033 + "/notifications/read").header("X-Demo-User", DOCTOR))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> mine = rowsFor(DOCTOR).stream().filter(r -> p033.equals(r.get("patient_id"))).toList();
        assertThat(mine).anySatisfy(r -> {
            assertThat(r).containsEntry("notification_type", "ENTRY");
            assertThat(r.get("read_at")).isNotNull();
        });
        assertThat(mine).anySatisfy(r -> {
            assertThat(r).containsEntry("notification_type", "REPORT");
            assertThat(r.get("read_at")).isNull();
        });
    }
}
