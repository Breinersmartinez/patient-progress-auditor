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

// Solo existen eventos confirmados por una persona (RN-11): confirmedBy y confirmedAt son obligatorios.
@Entity
@Immutable
@Table(name = "entry_events")
@Getter
@Setter
@NoArgsConstructor
public class EntryEvent {

    public enum Origin { IA, EDITADO, MANUAL }

    public static final String TYPE_SYMPTOM = "SINTOMA";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType = TYPE_SYMPTOM;

    // El codigo y la etiqueta del sintoma (E18); `getSymptom`/`setSymptom` los juntan en un `Symptom`.
    @Column(name = "symptom", nullable = false, length = Symptom.MAX_CODE)
    private String symptomCode;

    @Column(name = "symptom_label", length = Symptom.MAX_LABEL)
    private String symptomLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Intensity intensity = Intensity.SIN_INDICAR;

    private Short occurrences;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Origin origin;

    @Column(name = "confirmed_by", nullable = false)
    private UUID confirmedBy;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Symptom getSymptom() {
        return Symptom.of(symptomCode, symptomLabel);
    }

    // La etiqueta solo se guarda para un sintoma fuera del catalogo: los nueve la tienen en el codigo.
    public void setSymptom(Symptom symptom) {
        this.symptomCode = symptom.code();
        this.symptomLabel = symptom.inCatalog() ? null : symptom.label();
    }
}
