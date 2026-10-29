package com.patientprogressauditor.entry;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientprogressauditor.patient.Patient;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

@Service
public class EntryService {

    public static final int MAX_LIMIT = 500;
    public static final int MAX_BODY_LENGTH = 4000;

    private static final LocalDate FIRST_DAY = LocalDate.of(1900, 1, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(9999, 12, 31);

    public record SymptomView(String symptom, String intensity, Short occurrences) {
    }

    // `body` es el texto tal como se escribio: la fuente que citan los informes (RN-05, RN-07).
    public record EntryView(UUID id, UUID patientId, String patientAlias, LocalDate entryDate, String body,
            String authorName, String structuringStatus, Instant createdAt, List<SymptomView> symptoms) {
    }

    private final EntryRepository entries;
    private final EntryEventRepository events;
    private final PatientRepository patients;
    private final AppUserRepository users;
    private final Clock clock;

    public EntryService(EntryRepository entries, EntryEventRepository events, PatientRepository patients,
            AppUserRepository users, Clock clock) {
        this.entries = entries;
        this.events = events;
        this.patients = patients;
        this.users = users;
        this.clock = clock;
    }

    // Guarda el texto sin tocarlo: ni recorte, ni normalizacion, ni correccion. Sin eventos, la entrada queda
    // SIN_ESTRUCTURAR; la propuesta de la IA y su confirmacion llegan en E9 (D27).
    @Transactional
    public EntryView create(UUID patientId, LocalDate entryDate, String body, DemoPrincipal principal) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new NotFoundException("No encontramos ese paciente."));
        if (entryDate.isAfter(LocalDate.now(clock))) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "entryDate", "La fecha del registro no puede ser futura.");
        }
        if (body.indexOf('\u0000') >= 0) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "body", "El registro tiene un carácter que no se puede guardar.");
        }
        Entry entry = new Entry();
        entry.setPatientId(patientId);
        entry.setEntryDate(entryDate);
        entry.setBody(body);
        entry.setAuthorId(principal.id());
        entry.setStructuringStatus(Entry.StructuringStatus.SIN_ESTRUCTURAR);
        entries.saveAndFlush(entry);
        return new EntryView(entry.getId(), patientId, patient.getAlias(), entryDate, body, principal.displayName(),
                entry.getStructuringStatus().name(), entry.getCreatedAt(), List.of());
    }

    // Entradas de un paciente por fecha (RF-04): cronologico por defecto y, el mismo dia, por hora de creacion (RN-09).
    @Transactional(readOnly = true)
    public List<EntryView> listForPatient(UUID patientId, LocalDate from, LocalDate to, boolean newestFirst, int limit) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new NotFoundException("No encontramos ese paciente."));
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "from", "La fecha inicial no puede ser posterior a la final.");
        }
        Sort.Direction direction = newestFirst ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, "entryDate", "createdAt", "id");
        List<Entry> found = entries.findInRange(patientId, from == null ? FIRST_DAY : from, to == null ? LAST_DAY : to,
                PageRequest.of(0, limit, sort));
        return views(found, Map.of(patientId, patient));
    }

    // Historial de todos los pacientes, del mas reciente al mas antiguo.
    @Transactional(readOnly = true)
    public List<EntryView> history(int limit) {
        List<Entry> found = entries.findHistory(PageRequest.of(0, limit));
        Map<UUID, Patient> byId = patients.findAllById(found.stream().map(Entry::getPatientId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Patient::getId, Function.identity()));
        return views(found, byId);
    }

    private List<EntryView> views(List<Entry> found, Map<UUID, Patient> patientsById) {
        Collection<UUID> ids = found.stream().map(Entry::getId).toList();
        Map<UUID, List<SymptomView>> symptoms = ids.isEmpty() ? Map.of() : events.findByEntryIdIn(ids).stream()
                .sorted(Comparator.comparing(e -> e.getSymptom().name()))
                .collect(Collectors.groupingBy(EntryEvent::getEntryId, Collectors.mapping(
                        e -> new SymptomView(e.getSymptom().name(), e.getIntensity().name(), e.getOccurrences()),
                        Collectors.toList())));
        Map<UUID, String> authors = users.findAllById(found.stream().map(Entry::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return found.stream().map(e -> new EntryView(e.getId(), e.getPatientId(), patientsById.get(e.getPatientId()).getAlias(),
                e.getEntryDate(), e.getBody(), authors.getOrDefault(e.getAuthorId(), ""), e.getStructuringStatus().name(),
                e.getCreatedAt(), symptoms.getOrDefault(e.getId(), List.of()))).toList();
    }
}
