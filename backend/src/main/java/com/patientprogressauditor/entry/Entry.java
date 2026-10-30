package com.patientprogressauditor.entry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Inmutable: el texto original nunca se actualiza (RN-07, RNF-10). Las correcciones van en entry_versions.
@Entity
@Immutable
@Table(name = "entries")
@Getter
@Setter
@NoArgsConstructor
public class Entry {

    public enum Source { TEXTO, VOZ }

    public enum StructuringStatus { ESTRUCTURADA, SIN_ESTRUCTURAR }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source = Source.TEXTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "structuring_status", nullable = false, length = 20)
    private StructuringStatus structuringStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
