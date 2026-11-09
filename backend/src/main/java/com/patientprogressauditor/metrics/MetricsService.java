package com.patientprogressauditor.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryEventRepository;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.metrics.MetricsCalculator.EntryFact;
import com.patientprogressauditor.metrics.MetricsCalculator.EventFact;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.web.NotFoundException;

// Lee lo que hace falta y deja el calculo a MetricsCalculator. Lo usan el agente (E10) y los informes (E12).
@Service
public class MetricsService {

    private final PatientRepository patients;
    private final EntryRepository entries;
    private final EntryEventRepository events;

    public MetricsService(PatientRepository patients, EntryRepository entries, EntryEventRepository events) {
        this.patients = patients;
        this.entries = entries;
        this.events = events;
    }

    // `weekStart` debe ser lunes (RN-06); si no, IllegalArgumentException.
    @Transactional(readOnly = true)
    public WeeklyMetrics weekly(UUID patientId, LocalDate weekStart) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos ese paciente.");
        }
        LocalDate previousStart = weekStart.minusDays(7);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<EntryFact> entryFacts = entries
                .findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patientId, previousStart, weekEnd).stream()
                .map(e -> new EntryFact(e.getId(), e.getEntryDate(),
                        e.getStructuringStatus() == Entry.StructuringStatus.SIN_ESTRUCTURAR))
                .toList();
        List<EventFact> eventFacts = events.findRows(patientId, previousStart, weekEnd).stream()
                .map(r -> new EventFact(r.getEntryId(), r.getEntryDate(), r.getSymptom(), r.getIntensity(), r.getOccurrences()))
                .toList();
        Set<Symptom> earlier = events.findSymptomCodesBefore(patientId, previousStart).stream()
                .map(code -> Symptom.of(code, null)).collect(java.util.stream.Collectors.toSet());

        return MetricsCalculator.compute(patientId, weekStart, entryFacts, eventFacts, earlier);
    }

    // La consulta del medico (D31, RF-46): un periodo cualquiera (no exige lunes) y su comparacion con OTRO periodo
    // cualquiera, o sin comparacion si `comparisonStart` es null. `comparisonStart`/`comparisonEnd` van las dos null o
    // las dos con valor (lo exige el servicio que arma la tarea, antes de llegar aqui).
    @Transactional(readOnly = true)
    public WeeklyMetrics period(UUID patientId, LocalDate periodStart, LocalDate periodEnd, LocalDate comparisonStart,
            LocalDate comparisonEnd) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos ese paciente.");
        }
        LocalDate readFrom = comparisonStart == null ? periodStart : comparisonStart.isBefore(periodStart) ? comparisonStart : periodStart;
        LocalDate readTo = comparisonEnd == null ? periodEnd : comparisonEnd.isAfter(periodEnd) ? comparisonEnd : periodEnd;

        List<EntryFact> entryFacts = entries
                .findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patientId, readFrom, readTo).stream()
                .map(e -> new EntryFact(e.getId(), e.getEntryDate(),
                        e.getStructuringStatus() == Entry.StructuringStatus.SIN_ESTRUCTURAR))
                .toList();
        List<EventFact> eventFacts = events.findRows(patientId, readFrom, readTo).stream()
                .map(r -> new EventFact(r.getEntryId(), r.getEntryDate(), r.getSymptom(), r.getIntensity(), r.getOccurrences()))
                .toList();
        Set<Symptom> earlier = events.findSymptomCodesBefore(patientId, readFrom).stream()
                .map(code -> Symptom.of(code, null)).collect(java.util.stream.Collectors.toSet());

        return MetricsCalculator.computePeriod(patientId, periodStart, periodEnd, comparisonStart, comparisonEnd, entryFacts, eventFacts, earlier);
    }
}
