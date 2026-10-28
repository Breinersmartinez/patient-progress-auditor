package com.patientprogressauditor.report;

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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Inmutable: la version 1 es la del modelo y no se toca; cada edicion de enfermeria agrega una version (RF-25).
@Entity
@Immutable
@Table(name = "report_versions")
@Getter
@Setter
@NoArgsConstructor
public class ReportVersion {

    public enum Origin { MODELO, ENFERMERIA }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Origin origin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode content;

    @Column(name = "plain_text", columnDefinition = "text")
    private String plainText;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
