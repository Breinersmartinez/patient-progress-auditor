package com.patientprogressauditor.patient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.report.ReportRepository;
import com.patientprogressauditor.report.ReportVisibility;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

@Service
public class PatientService {

    private static final String ALIAS_TAKEN = "Ya existe un paciente con ese código.";

    public record LastReport(UUID id, String type, LocalDate periodStart, LocalDate periodEnd, String status) {
    }

    // Lo que ve la pantalla: nunca viaja al proveedor del LLM (RNF-02). El ultimo informe respeta el rol (RN-01).
    public record PatientView(UUID id, String alias, String bed, Short age, LocalDate admittedOn,
            LocalDate lastEntryDate, LastReport lastReport) {
    }

    public record NewPatient(String alias, String bed, Integer age, LocalDate admittedOn) {
    }

    private final PatientRepository patients;
    private final EntryRepository entries;
    private final ReportRepository reports;
    private final Clock clock;

    public PatientService(PatientRepository patients, EntryRepository entries, ReportRepository reports, Clock clock) {
        this.patients = patients;
        this.entries = entries;
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PatientView> list(DemoPrincipal principal) {
        List<Patient> all = patients.findAll().stream()
                .sorted(Comparator.comparing(Patient::getAlias, String.CASE_INSENSITIVE_ORDER)).toList();
        Map<UUID, LocalDate> lastEntries = entries.findLastEntryDates().stream()
                .collect(Collectors.toMap(EntryRepository.LastEntry::getPatientId, EntryRepository.LastEntry::getLastDate));
        Map<UUID, LastReport> lastReports = lastReports(principal);
        return all.stream().map(p -> view(p, lastEntries.get(p.getId()), lastReports.get(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public PatientView get(UUID id, DemoPrincipal principal) {
        Patient patient = patients.findById(id).orElseThrow(() -> new NotFoundException("No encontramos ese paciente."));
        LocalDate lastEntry = entries.findLastEntryDates(List.of(id)).stream()
                .map(EntryRepository.LastEntry::getLastDate).findFirst().orElse(null);
        return view(patient, lastEntry, lastReports(principal).get(id));
    }

    @Transactional
    public PatientView create(NewPatient request, DemoPrincipal principal) {
        String alias = request.alias() == null ? "" : request.alias().trim();
        if (alias.isEmpty()) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "alias", "Escriba el código del paciente.");
        }
        if (request.admittedOn() != null && request.admittedOn().isAfter(LocalDate.now(clock))) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "admittedOn", "La fecha de ingreso no puede ser futura.");
        }
        if (patients.existsByAliasIgnoreCase(alias)) {
            throw new FieldException(HttpStatus.CONFLICT, "alias", ALIAS_TAKEN);
        }
        Patient patient = new Patient();
        patient.setAlias(alias);
        String bed = request.bed() == null ? "" : request.bed().trim();
        patient.setBed(bed.isEmpty() ? null : bed);
        patient.setAge(request.age() == null ? null : request.age().shortValue());
        patient.setAdmittedOn(request.admittedOn());
        try {
            patients.saveAndFlush(patient);
        } catch (DataIntegrityViolationException e) {
            // Dos altas a la vez con el mismo codigo: la gana uq_patients_alias.
            throw new FieldException(HttpStatus.CONFLICT, "alias", ALIAS_TAKEN);
        }
        return view(patient, null, null);
    }

    private Map<UUID, LastReport> lastReports(DemoPrincipal principal) {
        Map<UUID, LastReport> result = new HashMap<>();
        for (ReportRepository.LatestReport r : reports.findLatestPerPatient(ReportVisibility.statusesFor(principal))) {
            result.put(r.getPatientId(),
                    new LastReport(r.getId(), r.getReportType(), r.getPeriodStart(), r.getPeriodEnd(), r.getStatus()));
        }
        return result;
    }

    private static PatientView view(Patient p, LocalDate lastEntry, LastReport lastReport) {
        return new PatientView(p.getId(), p.getAlias(), p.getBed(), p.getAge(), p.getAdmittedOn(), lastEntry, lastReport);
    }
}
