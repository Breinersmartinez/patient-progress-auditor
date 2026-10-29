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
        Set<Symptom> earlier = events.findSymptomsBefore(patientId, previousStart);

        return MetricsCalculator.compute(patientId, weekStart, entryFacts, eventFacts, earlier);
    }
}
