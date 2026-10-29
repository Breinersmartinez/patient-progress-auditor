package com.patientprogressauditor.agent.tools;

import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.metrics.WeeklyMetrics;

// Las metricas de una semana en forma compacta (pocos tokens). Es la UNICA forma en que las cifras llegan al modelo y la
// que se guarda en el informe como `metrics`: asi lo que el modelo lee, lo que cita y lo que se valida es lo mismo.
public final class MetricsView {

    private MetricsView() {
    }

    public static ObjectNode of(WeeklyMetrics m, ObjectMapper mapper) {
        ObjectNode out = mapper.createObjectNode();
        out.put("week", m.weekStart().toString());
        out.put("entries", m.entries());
        out.put("days_with_entry", m.daysWithEntry());
        out.put("days_without_entry", m.daysWithoutEntry());
        ArrayNode missing = out.putArray("missing_dates");
        m.datesWithoutEntry().forEach(d -> missing.add(d.toString()));
        out.put("unstructured_entries", m.unstructuredEntries());
        out.put("total_events", m.totalEvents());
        ObjectNode symptoms = out.putObject("symptoms");
        for (WeeklyMetrics.SymptomMetrics s : m.symptoms()) {
            ObjectNode node = symptoms.putObject(s.symptom().name());
            node.put("days", s.days());
            ArrayNode dates = node.putArray("dates");
            s.dates().forEach(d -> dates.add(d.toString()));
            node.put("events", s.events());
            if (s.occurrences() == null) {
                node.putNull("occurrences");
            } else {
                node.put("occurrences", s.occurrences());
            }
            node.put("max_intensity", s.maxIntensity() == null ? null : s.maxIntensity().name());
        }
        WeeklyMetrics.Comparison c = m.comparison();
        ObjectNode cmp = out.putObject("vs_previous");
        cmp.put("week", c.previousWeekStart().toString());
        cmp.put("comparable", c.comparable());
        cmp.put("previous_entries", c.previousEntries());
        cmp.put("previous_unstructured_entries", c.previousUnstructuredEntries());
        cmp.put("previous_total_events", c.previousTotalEvents());
        names(cmp.putArray("persistent"), c.persistent());
        names(cmp.putArray("disappeared"), c.disappeared());
        names(cmp.putArray("new"), c.newSymptoms());
        names(cmp.putArray("reappeared"), c.reappeared());
        ObjectNode days = cmp.putObject("days");
        c.daysChange().forEach(d -> days.putArray(d.symptom().name()).add(d.previousDays()).add(d.days()));
        return out;
    }

    private static void names(ArrayNode array, java.util.List<Symptom> symptoms) {
        symptoms.forEach(s -> array.add(s.name()));
    }

    public static String period(LocalDate weekStart) {
        return weekStart + " al " + weekStart.plusDays(6);
    }
}
