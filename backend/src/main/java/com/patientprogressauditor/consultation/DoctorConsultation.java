package com.patientprogressauditor.consultation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.fasterxml.jackson.databind.JsonNode;

// La consulta del medico, "Armar informe" (D31, RF-46): no es un Report. No tiene estado, no pasa por enfermeria y
// solo la lee su autor (doctor_id). El contenido tiene la misma forma que ReportVersion.content (DICCIONARIO_DATOS_M2
// 4.15): es lo que ya valido DraftValidator, armado por ConsultationContentBuilder.
@Entity
@Table(name = "doctor_consultations")
@Getter
@Setter
@NoArgsConstructor
public class DoctorConsultation {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "comparison_start")
    private LocalDate comparisonStart;

    @Column(name = "comparison_end")
    private LocalDate comparisonEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode content;

    @Column(name = "plain_text")
    private String plainText;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
