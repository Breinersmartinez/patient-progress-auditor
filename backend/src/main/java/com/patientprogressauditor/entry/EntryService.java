package com.patientprogressauditor.entry;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.patientprogressauditor.structuring.StructuringPrompt;
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

    public record SymptomView(String symptom, String intensity, Short occurrences, String label) {
    }

    // `body` es el texto tal como se escribio: la fuente que citan los informes (RN-05, RN-07).
    // Un evento que la persona confirmo al guardar. `origin`: IA (tal como lo propuso el modelo), EDITADO (lo cambio) o
    // MANUAL (lo agrego ella). Lo informa la pantalla; no es un control de seguridad (D26).
    public record ConfirmedEvent(String symptom, String intensity, Integer occurrences, String origin, String label) {
    }

    // El texto corregido que la persona confirmo junto al original (RF-42). `origin`: IA_ACEPTADA, IA_EDITADA o MANUAL; lo informa
    // la pantalla, como el de los eventos (D26). Un texto vacio, o igual al original, no genera version.
    public record Correction(String body, String origin) {
    }

    // `correctedBody` es la version vigente del texto (null si no hay): la pantalla la muestra, pero `body` sigue siendo el original.
    public record EntryView(UUID id, UUID patientId, String patientAlias, LocalDate entryDate, String body,
            String authorName, String structuringStatus, Instant createdAt, List<SymptomView> symptoms, String correctedBody) {
    }

    private final EntryRepository entries;
    private final EntryEventRepository events;
    private final EntryVersionRepository versions;
    private final PatientRepository patients;
    private final AppUserRepository users;
    private final Clock clock;

    public EntryService(EntryRepository entries, EntryEventRepository events, EntryVersionRepository versions,
            PatientRepository patients, AppUserRepository users, Clock clock) {
        this.entries = entries;
        this.events = events;
        this.versions = versions;
        this.patients = patients;
        this.users = users;
        this.clock = clock;
    }

    // Guarda el texto sin tocarlo: ni recorte, ni normalizacion, ni correccion. `requestedEvents` null es «guardar solo el texto»
    // y la entrada queda SIN_ESTRUCTURAR (RF-41); una lista (aunque vacia) es lo que la persona confirmo y la entrada
    // queda ESTRUCTURADA. La entrada y sus eventos se guardan en una sola transaccion: todo o nada (RN-11, D27).
    @Transactional
    public EntryView create(UUID patientId, LocalDate entryDate, String body, List<ConfirmedEvent> requestedEvents,
            Correction correction, DemoPrincipal principal) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> new NotFoundException("No encontramos ese paciente."));
        if (entryDate.isAfter(LocalDate.now(clock))) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "entryDate", "La fecha del registro no puede ser futura.");
        }
        if (body.indexOf('\u0000') >= 0) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "body", "El registro tiene un carácter que no se puede guardar.");
        }
        List<EntryEvent> confirmed = requestedEvents == null ? List.of() : toEvents(requestedEvents, principal);
        EntryVersion version = toVersion(correction, body, principal);
        Entry entry = new Entry();
        entry.setPatientId(patientId);
        entry.setEntryDate(entryDate);
        entry.setBody(body);
        entry.setAuthorId(principal.id());
        entry.setStructuringStatus(requestedEvents == null ? Entry.StructuringStatus.SIN_ESTRUCTURAR : Entry.StructuringStatus.ESTRUCTURADA);
        entries.saveAndFlush(entry);
        confirmed.forEach(event -> event.setEntryId(entry.getId()));
        events.saveAllAndFlush(confirmed);
        if (version != null) {
            version.setEntryId(entry.getId());
            versions.saveAndFlush(version);
        }
        return new EntryView(entry.getId(), patientId, patient.getAlias(), entryDate, body, principal.displayName(),
                entry.getStructuringStatus().name(), entry.getCreatedAt(),
                confirmed.stream().sorted(Comparator.comparing(EntryService::byCode)).map(EntryService::view).toList(),
                version == null ? null : version.getBody());
    }

    // La version corregida (RF-42), o null si no hay nada que guardar: sin texto, o igual al original. No se recorta ni se
    // reescribe: es lo que la persona dejo en la vista previa. El original no se toca (RN-07).
    private EntryVersion toVersion(Correction correction, String body, DemoPrincipal principal) {
        if (correction == null || correction.body() == null || correction.body().isBlank() || correction.body().equals(body)) {
            return null;
        }
        if (correction.body().length() > MAX_BODY_LENGTH) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "correction", "El texto corregido no puede pasar de 4000 caracteres.");
        }
        if (correction.body().indexOf('\u0000') >= 0) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "correction", "El texto corregido tiene un carácter que no se puede guardar.");
        }
        EntryVersion.Origin origin = correction.origin() == null ? EntryVersion.Origin.MANUAL
                : parse(EntryVersion.Origin.class, correction.origin());
        if (origin == null) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "correction", "El origen del texto corregido no es válido.");
        }
        EntryVersion version = new EntryVersion();
        version.setVersionNo(1);
        version.setBody(correction.body());
        version.setOrigin(origin);
        version.setCreatedBy(principal.id());
        version.setCreatedAt(clock.instant());
        return version;
    }

    // El orden de siempre: alfabetico por codigo.
    private static String byCode(EntryEvent e) {
        return e.getSymptom().code();
    }

    private static SymptomView view(EntryEvent e) {
        return new SymptomView(e.getSymptom().name(), e.getIntensity().name(), e.getOccurrences(), e.getSymptom().label());
    }

    // El sintoma que la persona confirma: uno del catalogo por su codigo, o cualquier otro por su nombre escrito (E18). El
    // codigo de uno nuevo lo calcula el servidor del nombre, no se confia en el que mande el cliente.
    private static Symptom symptomOf(ConfirmedEvent requested) {
        String code = requested.symptom() == null ? "" : requested.symptom().trim();
        if (Symptom.isCatalog(code)) {
            return Symptom.of(code, null);
        }
        String typed = requested.label() != null && !requested.label().isBlank() ? requested.label() : code;
        return Symptom.fromTyped(typed).orElseThrow(() -> new FieldException(HttpStatus.BAD_REQUEST, "events",
                "Escriba el nombre del síntoma con letras (hasta " + Symptom.MAX_LABEL + " caracteres)."));
    }

    // Valida lo que la persona confirma y deja quien lo confirmo y cuando. Un sintoma puede ser cualquiera con forma de sintoma (E18).
    
    private List<EntryEvent> toEvents(List<ConfirmedEvent> requested, DemoPrincipal principal) {
        List<EntryEvent> out = new ArrayList<>();
        Set<Symptom> seen = new java.util.HashSet<>();
        Instant now = clock.instant();
        for (ConfirmedEvent requestedEvent : requested) {
            Symptom symptom = symptomOf(requestedEvent);
            if (!seen.add(symptom)) {
                throw new FieldException(HttpStatus.BAD_REQUEST, "events", "Hay un síntoma repetido. Déjelo una sola vez.");
            }
            Intensity intensity = requestedEvent.intensity() == null || requestedEvent.intensity().isBlank()
                    ? Intensity.SIN_INDICAR : parse(Intensity.class, requestedEvent.intensity());
            if (intensity == null) {
                throw new FieldException(HttpStatus.BAD_REQUEST, "events", "La intensidad debe ser leve, moderado, intenso o sin indicar.");
            }
            Integer times = requestedEvent.occurrences();
            if (times != null && (times < 1 || times > StructuringPrompt.MAX_OCCURRENCES)) {
                throw new FieldException(HttpStatus.BAD_REQUEST, "events",
                        "Las veces deben estar entre 1 y " + StructuringPrompt.MAX_OCCURRENCES + ".");
            }
            EntryEvent.Origin origin = requestedEvent.origin() == null ? EntryEvent.Origin.MANUAL
                    : parse(EntryEvent.Origin.class, requestedEvent.origin());
            if (origin == null) {
                throw new FieldException(HttpStatus.BAD_REQUEST, "events", "El origen del síntoma no es válido.");
            }
            EntryEvent event = new EntryEvent();
            event.setSymptom(symptom);
            event.setIntensity(intensity);
            event.setOccurrences(times == null ? null : times.shortValue());
            event.setOrigin(origin);
            event.setConfirmedBy(principal.id());
            event.setConfirmedAt(now);
            out.add(event);
        }
        return out;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        return null;
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
                .sorted(Comparator.comparing(EntryService::byCode))
                .collect(Collectors.groupingBy(EntryEvent::getEntryId, Collectors.mapping(EntryService::view, Collectors.toList())));
        // La version vigente es la de mayor numero: se ordena por numero y la ultima de cada entrada gana.
        Map<UUID, String> corrected = ids.isEmpty() ? Map.of() : versions.findByEntryIdIn(ids).stream()
                .sorted(Comparator.comparingInt(EntryVersion::getVersionNo))
                .collect(Collectors.toMap(EntryVersion::getEntryId, EntryVersion::getBody, (older, newer) -> newer));
        Map<UUID, String> authors = users.findAllById(found.stream().map(Entry::getAuthorId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return found.stream().map(e -> new EntryView(e.getId(), e.getPatientId(), patientsById.get(e.getPatientId()).getAlias(),
                e.getEntryDate(), e.getBody(), authors.getOrDefault(e.getAuthorId(), ""), e.getStructuringStatus().name(),
                e.getCreatedAt(), symptoms.getOrDefault(e.getId(), List.of()), corrected.get(e.getId()))).toList();
    }
}
