package com.patientprogressauditor.consultation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// El unico camino de escritura de una consulta (D31, RF-46, Pieza 6 paso 3): nunca un Report.
@SpringBootTest(properties = "app.seed.demo=true")
@Transactional
class ConsultationDraftServiceTest {

    @Autowired private ConsultationDraftService drafts;
    @Autowired private DoctorConsultationRepository consultations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID doctor;
    private UUID patient;
    private UUID run;

    private ConsultationDraftService.Origin origin(LocalDate start, LocalDate end, LocalDate cmpStart, LocalDate cmpEnd) {
        doctor = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Dr. Andrés Salazar'", UUID.class);
        patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        run = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status)"
                + " VALUES (?, 'CONSULTA', ?, ?, ?, ?, 'EN_CURSO')", run, patient, doctor, start, end);
        return new ConsultationDraftService.Origin(doctor, patient, start, end, cmpStart, cmpEnd, run);
    }

    @Test
    void savesAConsultationWithItsPeriodAndComparison() {
        ObjectNode content = mapper.createObjectNode().put("summary", "Sin novedades.");
        ConsultationDraftService.Origin origin = origin(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));

        UUID id = drafts.save(origin, content);

        DoctorConsultation saved = consultations.findById(id).orElseThrow();
        assertThat(saved.getDoctorId()).isEqualTo(doctor);
        assertThat(saved.getPatientId()).isEqualTo(patient);
        assertThat(saved.getPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(saved.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(saved.getComparisonStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(saved.getContent().path("summary").asText()).isEqualTo("Sin novedades.");
        assertThat(saved.getPlainText()).isNull();
        assertThat(saved.getRunId()).isEqualTo(run);
    }

    @Test
    void savesAConsultationWithoutComparisonAsNullDates() {
        ObjectNode content = mapper.createObjectNode().put("summary", "Sin comparación.");
        ConsultationDraftService.Origin origin = origin(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), null, null);

        UUID id = drafts.save(origin, content);

        DoctorConsultation saved = consultations.findById(id).orElseThrow();
        assertThat(saved.getComparisonStart()).isNull();
        assertThat(saved.getComparisonEnd()).isNull();
    }

    @Test
    void anIncompleteConsultationCanKeepOnlyThePlainText() {
        ConsultationDraftService.Origin origin = origin(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), null, null);

        UUID id = drafts.saveIncomplete(origin, null, "(Sin validar: el modelo no terminó.)");

        DoctorConsultation saved = consultations.findById(id).orElseThrow();
        assertThat(saved.getContent()).isNull();
        assertThat(saved.getPlainText()).contains("Sin validar");
    }

    @Test
    void onlyItsOwnDoctorCanFindIt() {
        ConsultationDraftService.Origin origin = origin(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), null, null);
        UUID id = drafts.save(origin, mapper.createObjectNode().put("summary", "x"));
        UUID other = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Dr. Julián Ortiz'", UUID.class);

        assertThat(consultations.findByIdAndDoctorId(id, doctor)).isPresent();
        assertThat(consultations.findByIdAndDoctorId(id, other)).isEmpty();
    }
}
