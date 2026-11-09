package com.patientprogressauditor.notification;

import java.time.Instant;
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

// Un aviso al medico de una entrada guardada o un informe enviado (D30, RF-45). No guarda texto clinico: la frase
// se arma al leerla, con el tipo y la fecha de lo que avisa.
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    public enum Type { ENTRY, REPORT }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 20)
    private Type notificationType;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;
}
