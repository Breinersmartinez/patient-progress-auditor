package com.patientprogressauditor.entry;

import java.time.Instant;
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

// El texto corregido de una entrada (RF-42): una version APARTE, con quien la confirmo. El original (`entries.body`) no se
// toca y es el que citan los informes (RN-05, RN-07). La version vigente es la de mayor `version_no`.
@Entity
@Immutable
@Table(name = "entry_versions")
@Getter
@Setter
@NoArgsConstructor
public class EntryVersion {

    // IA_ACEPTADA: la persona dejo el texto que propuso el modelo. IA_EDITADA: lo cambio. MANUAL: lo escribio ella.
    public enum Origin { IA_ACEPTADA, IA_EDITADA, MANUAL }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Origin origin;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
