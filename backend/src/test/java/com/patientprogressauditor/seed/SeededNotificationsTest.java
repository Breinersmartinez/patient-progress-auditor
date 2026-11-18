package com.patientprogressauditor.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// La campana del medico (D30, RF-45) abre la demo con algo que mostrar: dos registros recientes sin leer (P-014 y
// P-021) y el ultimo informe enviado de P-014 ya leido, para cada medico activo.
@SpringBootTest(properties = "app.seed.demo=true")
class SeededNotificationsTest {

    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    private static final String OTHER_DOCTOR = "20000000-0000-0000-0000-000000000004";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DemoDataSeeder seeder;

    private UUID patient(String alias) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, alias);
    }

    @Test
    void eachActiveDoctorHasExactlyThreeNotificationsTwoUnreadEntriesAndOneReadReport() {
        for (String doctor : List.of(DOCTOR, OTHER_DOCTOR)) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT notification_type, patient_id, entry_id, report_id, read_at FROM notifications WHERE recipient_id = ?",
                    UUID.fromString(doctor));
            assertThat(rows).hasSize(3);
            assertThat(rows.stream().filter(r -> "ENTRY".equals(r.get("notification_type")))).hasSize(2)
                    .allSatisfy(r -> assertThat(r.get("read_at")).isNull());
            assertThat(rows.stream().filter(r -> "REPORT".equals(r.get("notification_type")))).hasSize(1)
                    .allSatisfy(r -> assertThat(r.get("read_at")).isNotNull());
        }
    }

    @Test
    void theTwoUnreadEntriesAreTheMostRecentOfP014AndP021() {
        UUID p014 = patient("P-014");
        UUID p021 = patient("P-021");
        LocalDate lastP014 = jdbc.queryForObject("SELECT max(entry_date) FROM entries WHERE patient_id = ?", LocalDate.class, p014);
        LocalDate lastP021 = jdbc.queryForObject("SELECT max(entry_date) FROM entries WHERE patient_id = ?", LocalDate.class, p021);
        UUID lastEntryP014 = jdbc.queryForObject("SELECT id FROM entries WHERE patient_id = ? AND entry_date = ?", UUID.class, p014, lastP014);
        UUID lastEntryP021 = jdbc.queryForObject("SELECT id FROM entries WHERE patient_id = ? AND entry_date = ?", UUID.class, p021, lastP021);

        List<UUID> entryIds = jdbc.queryForList(
                "SELECT entry_id FROM notifications WHERE recipient_id = ? AND notification_type = 'ENTRY'", UUID.class,
                UUID.fromString(DOCTOR));
        assertThat(entryIds).containsExactlyInAnyOrder(lastEntryP014, lastEntryP021);
    }

    @Test
    void theReadReportIsTheLastSentOneOfP014() {
        UUID p014 = patient("P-014");
        UUID lastSent = jdbc.queryForObject(
                "SELECT id FROM reports WHERE patient_id = ? AND status = 'ENVIADO' ORDER BY period_start DESC LIMIT 1",
                UUID.class, p014);

        UUID reportId = jdbc.queryForObject(
                "SELECT report_id FROM notifications WHERE recipient_id = ? AND notification_type = 'REPORT'", UUID.class,
                UUID.fromString(DOCTOR));
        assertThat(reportId).isEqualTo(lastSent);
    }

    @Test
    void restartingDoesNotDuplicateNotifications() {
        int before = jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class);
        seeder.seed();
        seeder.seed();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isEqualTo(before).isEqualTo(6);
    }
}
