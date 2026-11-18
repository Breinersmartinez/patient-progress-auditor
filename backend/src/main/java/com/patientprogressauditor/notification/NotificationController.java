package com.patientprogressauditor.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.notification.NotificationService.NotificationView;
import com.patientprogressauditor.session.DemoPrincipal;

// Notificaciones al medico (RF-45, D30): solo lee y marca las suyas; de otro medico, 404 (no 403, E12a). El permiso
// NOTIFICATIONS_READ es la primera barrera y recipient_id = sesion la segunda, en el servicio.
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping("/notifications")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_READ')")
    public List<NotificationView> list(@AuthenticationPrincipal DemoPrincipal principal) {
        return service.list(principal);
    }

    @PostMapping("/notifications/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_READ')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        service.markRead(id, principal);
    }

    // Al abrir la ficha de un paciente: marca leidas todas las suyas sin leer (como el mockup).
    @PostMapping("/patients/{patientId}/notifications/read")
    @PreAuthorize("hasAuthority('NOTIFICATIONS_READ')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markPatientRead(@PathVariable UUID patientId, @AuthenticationPrincipal DemoPrincipal principal) {
        service.markPatientRead(patientId, principal);
    }
}
