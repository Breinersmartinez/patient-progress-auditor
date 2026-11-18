package com.patientprogressauditor.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.patient.Patient;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportRepository;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;
import com.patientprogressauditor.web.NotFoundException;

// Avisa al medico de una entrada guardada o un informe enviado (D30, RF-45). No guarda texto clinico: la frase la
// arma quien lea esto (hoy, el frontend) con el tipo y la fecha. Las dos escrituras (notifyEntryCreated,
// notifyReportSent) las llama, dentro de su propia transaccion, quien guarda la entrada o envia el informe: si
// fallan, fallan junto con ese guardado.
@Service
public class NotificationService {

    public record NotificationView(UUID id, String type, UUID patientId, String patientAlias, UUID entryId,
            LocalDate entryDate, UUID reportId, String reportType, String createdAt, boolean read) {
    }

    private static final String DOCTOR_ROLE = "MEDICO";

    private final NotificationRepository notifications;
    private final AppUserRepository users;
    private final PatientRepository patients;
    private final EntryRepository entries;
    private final ReportRepository reports;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, AppUserRepository users, PatientRepository patients,
            EntryRepository entries, ReportRepository reports, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.patients = patients;
        this.entries = entries;
        this.reports = reports;
        this.clock = clock;
    }

    // Todo medico activo menos el autor (RF-45, D30): si no queda ninguno, no se crea nada y el guardado no falla por eso.
    public void notifyEntryCreated(UUID patientId, UUID entryId, UUID authorId) {
        for (AppUser doctor : users.findActiveByRoleExcluding(DOCTOR_ROLE, authorId)) {
            Notification n = new Notification();
            n.setRecipientId(doctor.getId());
            n.setPatientId(patientId);
            n.setNotificationType(Notification.Type.ENTRY);
            n.setEntryId(entryId);
            n.setCreatedAt(clock.instant());
            notifications.saveAndFlush(n);
        }
    }

    // Todo medico activo (RF-45, D30): enfermeria envia, nunca un medico, asi que no hay autor que excluir.
    public void notifyReportSent(UUID patientId, UUID reportId) {
        for (AppUser doctor : users.findActiveByRoleExcluding(DOCTOR_ROLE, null)) {
            Notification n = new Notification();
            n.setRecipientId(doctor.getId());
            n.setPatientId(patientId);
            n.setNotificationType(Notification.Type.REPORT);
            n.setReportId(reportId);
            n.setCreatedAt(clock.instant());
            notifications.saveAndFlush(n);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(DemoPrincipal principal) {
        List<Notification> all = notifications.findByRecipientIdOrderByCreatedAtDesc(principal.id());
        Lookups lookups = lookups(all);
        return all.stream().map(n -> view(n, lookups)).toList();
    }

    // Los pacientes con alguna sin leer de este medico: el punto de "Mis pacientes".
    @Transactional(readOnly = true)
    public List<UUID> patientsWithUnread(DemoPrincipal principal) {
        return notifications.findPatientIdsWithUnread(principal.id());
    }

    @Transactional
    public void markRead(UUID id, DemoPrincipal principal) {
        Notification n = notifications.findByIdAndRecipientId(id, principal.id())
                .orElseThrow(() -> new NotFoundException("No encontramos esa notificación."));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
            notifications.saveAndFlush(n);
        }
    }

    // Al abrir la ficha de un paciente: solo las de registro (ENTRY) de ese paciente, sin leer, quedan leidas (como
    // el mockup, auditor.fuente.html:421, n.tipo === 'registro'). Las de informe se marcan al abrir el informe o la
    // notificacion misma (markReportRead/markRead), no por abrir la ficha.
    @Transactional
    public void markPatientRead(UUID patientId, DemoPrincipal principal) {
        List<Notification> unread = notifications.findByRecipientIdAndPatientIdAndNotificationTypeAndReadAtIsNull(
                principal.id(), patientId, Notification.Type.ENTRY);
        unread.forEach(n -> n.setReadAt(clock.instant()));
        notifications.saveAllAndFlush(unread);
    }

    private Lookups lookups(List<Notification> all) {
        Map<UUID, String> patientAliases = patients.findAllById(all.stream().map(Notification::getPatientId).distinct().toList())
                .stream().collect(Collectors.toMap(Patient::getId, Patient::getAlias));
        Map<UUID, LocalDate> entryDates = entries
                .findAllById(all.stream().map(Notification::getEntryId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Entry::getId, Entry::getEntryDate));
        Map<UUID, Report.Type> reportTypes = reports
                .findAllById(all.stream().map(Notification::getReportId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Report::getId, Report::getReportType));
        return new Lookups(patientAliases, entryDates, reportTypes);
    }

    private record Lookups(Map<UUID, String> patientAliases, Map<UUID, LocalDate> entryDates, Map<UUID, Report.Type> reportTypes) {
    }

    private static NotificationView view(Notification n, Lookups l) {
        return new NotificationView(n.getId(), n.getNotificationType().name(), n.getPatientId(),
                l.patientAliases().getOrDefault(n.getPatientId(), "—"), n.getEntryId(), l.entryDates().get(n.getEntryId()),
                n.getReportId(), n.getReportId() == null ? null : l.reportTypes().get(n.getReportId()).name(),
                n.getCreatedAt().toString(), n.getReadAt() != null);
    }
}
