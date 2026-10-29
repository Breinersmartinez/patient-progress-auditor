package com.patientprogressauditor.metrics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;

// Las cifras de una semana (lunes a domingo) de un paciente. Las calcula el codigo, nunca el modelo (RN-04);
// el informe las copia y el validador rechaza el informe si una cifra no coincide (RF-13).
public record WeeklyMetrics(
        UUID patientId,
        LocalDate weekStart,
        LocalDate weekEnd,
        int entries,
        int daysWithEntry,
        int daysWithoutEntry,
        List<LocalDate> datesWithoutEntry,
        // Entradas con solo el texto guardado: el informe las declara y no las cuenta como «sin sintomas» (RN-08).
        int unstructuredEntries,
        List<UUID> unstructuredEntryIds,
        int totalEvents,
        List<SymptomMetrics> symptoms,
        Comparison comparison) {

    // `days`: dias distintos con el sintoma; `events`: eventos confirmados (uno por entrada y sintoma);
    // `occurrences`: suma de las veces que se indicaron (null si ninguna entrada las indico);
    // `maxIntensity`: la mayor intensidad indicada (null si ninguna se indico).
    public record SymptomMetrics(Symptom symptom, int days, List<LocalDate> dates, int events, Integer occurrences,
            Intensity maxIntensity) {
    }

    public record DaysChange(Symptom symptom, int previousDays, int days, int change) {
    }

    // Frente a la semana anterior. Si esa semana no tiene entradas no se puede decir que un sintoma «dejo de estar»:
    // `comparable` es false y las listas quedan vacias.
    public record Comparison(
            LocalDate previousWeekStart,
            boolean comparable,
            int previousEntries,
            int previousUnstructuredEntries,
            int previousTotalEvents,
            List<Symptom> persistent,
            List<Symptom> disappeared,
            List<Symptom> newSymptoms,
            List<Symptom> reappeared,
            List<DaysChange> daysChange) {
    }
}
