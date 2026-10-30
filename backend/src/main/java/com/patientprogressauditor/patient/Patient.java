package com.patientprogressauditor.patient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Solo el id sale al proveedor del LLM (RNF-02): alias, cama, edad e ingreso nunca.
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 100)
    private String alias;

    @Column(length = 10)
    private String bed;

    private Short age;

    @Column(name = "admitted_on")
    private LocalDate admittedOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
