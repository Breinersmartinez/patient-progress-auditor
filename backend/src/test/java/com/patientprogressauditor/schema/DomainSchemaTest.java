package com.patientprogressauditor.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DomainSchemaTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Autowired
    private JdbcTemplate jdbc;

    private UUID nurse;
    private UUID patient;
    private UUID entry;

    @BeforeEach
    void fixtures() {
        nurse = UUID.randomUUID();
        patient = UUID.randomUUID();
        entry = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo) VALUES (?, ?, 'Enfermera de prueba', TRUE)",
                nurse, nurse + "@example.test");
        jdbc.update("INSERT INTO patients (id, alias, bed, age, admitted_on) VALUES (?, 'P-999', '204-A', 71, ?)",
                patient, MONDAY);
        jdbc.update("INSERT INTO entries (id, patient_id, entry_date, body, author_id, structuring_status)"
                + " VALUES (?, ?, ?, 'texto original', ?, 'ESTRUCTURADA')", entry, patient, MONDAY, nurse);
    }

    private void insertEvent(String symptom) {
        jdbc.update("INSERT INTO entry_events (id, entry_id, event_type, symptom, origin, confirmed_by, confirmed_at)"
                + " VALUES (?, ?, 'SINTOMA', ?, 'IA', ?, ?)",
                UUID.randomUUID(), entry, symptom, nurse, OffsetDateTime.now());
    }

    private void insertReport(String type, LocalDate start, LocalDate end, String status, String reason,
            UUID reviewer, Integer approvedVersion) {
        jdbc.update("INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, status_reason,"
                + " requested_by, reviewed_by, reviewed_at, approved_version_no) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), patient, type, start, end, status, reason, nurse, reviewer,
                reviewer == null ? null : OffsetDateTime.now(), approvedVersion);
    }

    @Test
    void flywayCreatesTheSixteenTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("app_users", "app_roles", "app_permissions", "app_user_roles",
                "app_role_permissions", "patients", "entries", "entry_events", "entry_versions", "reports",
                "report_versions", "report_citations", "monthly_report_weeks", "agent_runs", "agent_steps",
                "audit_logs");
    }

    @Test
    void validEventIsAccepted() {
        assertThatCode(() -> insertEvent("VOMITO")).doesNotThrowAnyException();
    }

    @Test
    void symptomOutsideTheCatalogIsRejected() {
        assertThatThrownBy(() -> insertEvent("SARPULLIDO")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSymptomTwiceInOneEntryIsRejected() {
        insertEvent("DIARREA");

        assertThatThrownBy(() -> insertEvent("DIARREA")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void weeklyReportMustStartOnMondayAndEndOnSunday() {
        assertThatCode(() -> insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "PENDIENTE_REVISION", null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void reportNotStartingOnMondayIsRejected() {
        LocalDate tuesday = MONDAY.plusDays(1);

        assertThatThrownBy(() -> insertReport("SEMANAL", tuesday, tuesday.plusDays(6), "PENDIENTE_REVISION", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void monthlyReportMustCoverFourWeeks() {
        assertThatThrownBy(() -> insertReport("MENSUAL", MONDAY, MONDAY.plusDays(6), "PENDIENTE_REVISION", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvedReportNeedsReviewer() {
        assertThatThrownBy(() -> insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "APROBADO", null, null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incompleteReportNeedsReason() {
        assertThatThrownBy(() -> insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "INCOMPLETO", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyOneCurrentReportPerPatientAndPeriod() {
        insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "PENDIENTE_REVISION", null, null, null);

        assertThatThrownBy(() -> insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "PENDIENTE_REVISION", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incompleteReportDoesNotBlockANewOne() {
        insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "INCOMPLETO", "Se agoto el limite de iteraciones", null, null);

        assertThatCode(() -> insertReport("SEMANAL", MONDAY, MONDAY.plusDays(6), "PENDIENTE_REVISION", null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void agentStepSequenceIsUniquePerRun() {
        UUID run = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by) VALUES (?, 'INFORME', ?, ?)",
                run, patient, nurse);
        jdbc.update("INSERT INTO agent_steps (run_id, seq, step_type) VALUES (?, 1, 'ESTADO')", run);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_steps (run_id, seq, step_type) VALUES (?, 1, 'ESTADO')", run))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyNursingHasApprovePermission() {
        List<String> roles = jdbc.queryForList(
                "SELECT r.code FROM app_role_permissions rp JOIN app_roles r ON r.id = rp.role_id"
                        + " JOIN app_permissions p ON p.id = rp.permission_id WHERE p.code = 'REPORTS_APPROVE'",
                String.class);

        assertThat(roles).containsExactly("ENFERMERIA");
    }

    @Test
    void agentRoleCanOnlyReadAndSaveTheDraft() {
        List<String> permissions = jdbc.queryForList(
                "SELECT p.code FROM app_role_permissions rp JOIN app_roles r ON r.id = rp.role_id"
                        + " JOIN app_permissions p ON p.id = rp.permission_id WHERE r.code = 'AGENTE'",
                String.class);

        assertThat(permissions).containsExactlyInAnyOrder("PATIENTS_READ", "ENTRIES_READ", "REPORTS_CREATE_DRAFT");
    }

    @Test
    void doctorCannotCreateOrApprove() {
        List<String> permissions = jdbc.queryForList(
                "SELECT p.code FROM app_role_permissions rp JOIN app_roles r ON r.id = rp.role_id"
                        + " JOIN app_permissions p ON p.id = rp.permission_id WHERE r.code = 'MEDICO'",
                String.class);

        assertThat(permissions).containsExactlyInAnyOrder("PATIENTS_READ", "ENTRIES_READ", "ENTRIES_CREATE", "REPORTS_READ");
    }

    @Test
    void aliasIsUniqueIgnoringCase() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO patients (id, alias) VALUES (?, 'p-999')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
