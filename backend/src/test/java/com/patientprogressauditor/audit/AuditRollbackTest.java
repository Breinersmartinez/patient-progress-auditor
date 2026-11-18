package com.patientprogressauditor.audit;

import static com.patientprogressauditor.report.ReportFixtures.DOCTOR;
import static com.patientprogressauditor.report.ReportFixtures.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.report.ReportFixtures;
import com.patientprogressauditor.support.AuditCleanup;
import com.patientprogressauditor.support.TestAuth;

// Criterio 5 del encargo (paso 3 del plan): si la anotacion falla, la accion se revierte entera, porque las dos van en
// la misma transaccion. Sin @Transactional a proposito: dentro de una transaccion de prueba el UPDATE del informe se vería
// hasta el final y la prueba no probaria nada. Cada prueba borra lo que confirmo.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class AuditRollbackTest {

    private static final LocalDate W4 = LocalDate.of(2026, 10, 12);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;
    // Un doble del servicio, no un espia: el espia envuelve el proxy transaccional (MANDATORY) y no se puede configurar
    // fuera de una transaccion. Aqui solo importa que la anotacion falle dentro de la transaccion de la accion.
    @MockitoBean private AuditService audit;

    private ReportFixtures fx;
    private UUID patient;
    private final List<UUID> reports = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        patient = fx.patient("P-033");
    }

    @AfterEach
    void cleanUp() {
        AuditCleanup.deleteFor(jdbc, reports);
        for (UUID report : reports) {
            jdbc.update("DELETE FROM report_citations WHERE report_version_id IN (SELECT id FROM report_versions WHERE report_id = ?)", report);
            jdbc.update("DELETE FROM report_versions WHERE report_id = ?", report);
            jdbc.update("DELETE FROM reports WHERE id = ?", report);
        }
    }

    @Test
    void ifTheAnnotationFailsTheApprovalIsNotApplied() throws Exception {
        UUID report = fx.report(patient, W4, "PENDIENTE_REVISION");
        reports.add(report);
        doThrow(new IllegalStateException("la bitacora no respondio"))
                .when(audit).record(any(), eq(AuditAction.APROBAR_INFORME), any(), any(), any(), any());

        mockMvc.perform(post("/api/reports/" + report + "/approve").with(TestAuth.as(NURSE)))
                .andExpect(status().isInternalServerError());

        Map<String, Object> row = jdbc.queryForMap("SELECT status, reviewed_by, approved_version_no FROM reports WHERE id = ?", report);
        assertThat(row.get("status")).isEqualTo("PENDIENTE_REVISION");
        assertThat(row.get("reviewed_by")).isNull();
        assertThat(row.get("approved_version_no")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, report)).isZero();
    }

    @Test
    void ifTheAnnotationFailsTheDoctorsEntryIsNotSaved() throws Exception {
        String body = "Registro que no debe quedar " + UUID.randomUUID();
        doThrow(new IllegalStateException("la bitacora no respondio"))
                .when(audit).record(any(), eq(AuditAction.REGISTRAR_ENTRADA_MEDICO), any(), any(), any(), any());

        mockMvc.perform(post("/api/patients/" + patient + "/entries").with(TestAuth.as(DOCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("entryDate", "2026-10-20", "body", body))))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries WHERE body = ?", Integer.class, body)).isZero();
    }
}
