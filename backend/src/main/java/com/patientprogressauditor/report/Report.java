package com.patientprogressauditor.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Guarda estado, revision y envio; lo que redacto el modelo y lo que edito enfermeria vive en ReportVersion.
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    public enum Type { SEMANAL, MENSUAL }

    public enum Status {
        PENDIENTE_REVISION, INCOMPLETO, APROBADO, ENVIADO, RECHAZADO;

        // Un enviado cuenta como aprobado para el mensual, el PDF y el agente: enviar no cambia lo aprobado (D29).
        public boolean approved() {
            return this == APROBADO || this == ENVIADO;
        }

        // Los estados que ocupan el periodo (indice uq_reports_current_period): un rechazado o incompleto no lo bloquea.
        public boolean current() {
            return this == PENDIENTE_REVISION || approved();
        }
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 10)
    private Type reportType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDIENTE_REVISION;

    @Column(name = "status_reason", columnDefinition = "text")
    private String statusReason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "run_id")
    private UUID runId;

    @Column(length = 50)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "approved_version_no")
    private Integer approvedVersionNo;

    // Quien y cuando lo envio al medico (D29); solo existen en ENVIADO.
    @Column(name = "sent_by")
    private UUID sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
