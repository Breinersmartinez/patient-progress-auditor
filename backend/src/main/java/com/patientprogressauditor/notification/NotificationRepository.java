package com.patientprogressauditor.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    List<Notification> findByRecipientIdAndPatientIdAndNotificationTypeAndReadAtIsNull(UUID recipientId, UUID patientId,
            Notification.Type notificationType);

    // Los pacientes con alguna notificacion sin leer de este destinatario: el punto de "Mis pacientes".
    @Query("SELECT DISTINCT n.patientId FROM Notification n WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    List<UUID> findPatientIdsWithUnread(@Param("recipientId") UUID recipientId);
}
