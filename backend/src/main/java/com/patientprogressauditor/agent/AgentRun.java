package com.patientprogressauditor.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El estado vive en la base de datos, no en la conexion: permite consultarlo y reanudar el SSE (D24).
@Entity
@Table(name = "agent_runs")
@Getter
@Setter
@NoArgsConstructor
public class AgentRun {

    public enum Type { INFORME, ESTRUCTURACION }

    public enum Status { EN_COLA, EN_CURSO, COMPLETADA, INCOMPLETA, FALLIDA }

    public enum StopReason {
        EXITO, MAX_ITERACIONES, MAX_TOKENS, TIMEOUT_HERRAMIENTA, SALIDA_INVALIDA, PROVEEDOR_NO_DISPONIBLE
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    private Type runType;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.EN_COLA;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 30)
    private StopReason stopReason;

    @Column(length = 50)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(nullable = false)
    private int iterations;

    @Column(name = "tokens_in", nullable = false)
    private int tokensIn;

    @Column(name = "tokens_out", nullable = false)
    private int tokensOut;

    @Column(name = "cost_usd", precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
