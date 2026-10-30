package com.patientprogressauditor.agent;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La traza: una fila por cosa que paso. Solo se agregan; el id creciente es el punto de reanudacion del SSE.
@Entity
@Immutable
@Table(name = "agent_steps")
@Getter
@Setter
@NoArgsConstructor
public class AgentStep {

    public enum Type { ESTADO, LLAMADA_LLM, HERRAMIENTA, VALIDACION, LIMITE, FALLBACK, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 20)
    private Type stepType;

    @Column(name = "tool_name", length = 60)
    private String toolName;

    @Column(length = 300)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode result;

    @Column(name = "is_error", nullable = false)
    private boolean error;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
