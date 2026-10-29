package com.patientprogressauditor.metrics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.metrics.WeeklyMetrics.Comparison;
import com.patientprogressauditor.metrics.WeeklyMetrics.DaysChange;
import com.patientprogressauditor.metrics.WeeklyMetrics.SymptomMetrics;

// Calculo puro: no lee la base, ni el reloj, ni el modelo. Recibe lo ya leido y devuelve cifras.
public final class MetricsCalculator {

    public record EntryFact(UUID id, LocalDate date, boolean unstructured) {
    }

    public record EventFact(UUID entryId, LocalDate date, Symptom symptom, Intensity intensity, Short occurrences) {
    }

    private MetricsCalculator() {
    }

    // `entries` y `events` deben cubrir al menos la semana y la anterior; `earlierSymptoms` son los sintomas
    // presentes en cualquier dia anterior a la semana anterior (sirven para distinguir nuevo de reaparecido).
    public static WeeklyMetrics compute(UUID patientId, LocalDate weekStart, List<EntryFact> entries,
            List<EventFact> events, Set<Symptom> earlierSymptoms) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Una semana empieza el lunes: " + weekStart);
        }
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate previousStart = weekStart.minusDays(7);
        LocalDate previousEnd = weekStart.minusDays(1);

        List<EntryFact> current = within(entries, weekStart, weekEnd, EntryFact::date);
        List<EntryFact> previous = within(entries, previousStart, previousEnd, EntryFact::date);
        List<EventFact> currentEvents = within(events, weekStart, weekEnd, EventFact::date);
        List<EventFact> previousEvents = within(events, previousStart, previousEnd, EventFact::date);

        Set<LocalDate> withEntry = new TreeSet<>();
        current.forEach(e -> withEntry.add(e.date()));
        List<LocalDate> without = new ArrayList<>();
        for (LocalDate day = weekStart; !day.isAfter(weekEnd); day = day.plusDays(1)) {
            if (!withEntry.contains(day)) {
                without.add(day);
            }
        }
        List<EntryFact> unstructured = current.stream().filter(EntryFact::unstructured)
                .sorted(Comparator.comparing(EntryFact::date).thenComparing(EntryFact::id)).toList();

        Map<Symptom, List<EventFact>> bySymptom = group(currentEvents);
        List<SymptomMetrics> symptoms = new ArrayList<>();
        bySymptom.forEach((symptom, list) -> symptoms.add(symptomMetrics(symptom, list)));

        Comparison comparison = compare(previousStart, previous, previousEvents, bySymptom, earlierSymptoms);
        return new WeeklyMetrics(patientId, weekStart, weekEnd, current.size(), withEntry.size(), without.size(), without,
                unstructured.size(), unstructured.stream().map(EntryFact::id).toList(), currentEvents.size(), symptoms,
                comparison);
    }

    private static Comparison compare(LocalDate previousStart, List<EntryFact> previous, List<EventFact> previousEvents,
            Map<Symptom, List<EventFact>> currentBySymptom, Set<Symptom> earlier) {
        int previousUnstructured = (int) previous.stream().filter(EntryFact::unstructured).count();
        if (previous.isEmpty()) {
            return new Comparison(previousStart, false, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        Map<Symptom, List<EventFact>> previousBySymptom = group(previousEvents);
        Set<Symptom> now = currentBySymptom.keySet();
        Set<Symptom> before = previousBySymptom.keySet();

        List<Symptom> persistent = ordered(now, s -> before.contains(s));
        List<Symptom> disappeared = ordered(before, s -> !now.contains(s));
        List<Symptom> fresh = ordered(now, s -> !before.contains(s) && !earlier.contains(s));
        List<Symptom> reappeared = ordered(now, s -> !before.contains(s) && earlier.contains(s));

        Set<Symptom> either = EnumSet.noneOf(Symptom.class);
        either.addAll(now);
        either.addAll(before);
        List<DaysChange> changes = new ArrayList<>();
        for (Symptom symptom : either) {
            int previousDays = days(previousBySymptom.get(symptom));
            int days = days(currentBySymptom.get(symptom));
            changes.add(new DaysChange(symptom, previousDays, days, days - previousDays));
        }
        return new Comparison(previousStart, true, previous.size(), previousUnstructured, previousEvents.size(),
                persistent, disappeared, fresh, reappeared, changes);
    }

    private static SymptomMetrics symptomMetrics(Symptom symptom, List<EventFact> list) {
        List<LocalDate> dates = list.stream().map(EventFact::date).distinct().sorted().toList();
        Integer occurrences = list.stream().filter(e -> e.occurrences() != null).map(e -> (int) e.occurrences())
                .reduce(Integer::sum).orElse(null);
        Intensity max = list.stream().map(EventFact::intensity).filter(i -> i != Intensity.SIN_INDICAR)
                .max(Comparator.naturalOrder()).orElse(null);
        return new SymptomMetrics(symptom, dates.size(), dates, list.size(), occurrences, max);
    }

    private static Map<Symptom, List<EventFact>> group(List<EventFact> events) {
        Map<Symptom, List<EventFact>> grouped = new EnumMap<>(Symptom.class);
        events.forEach(e -> grouped.computeIfAbsent(e.symptom(), s -> new ArrayList<>()).add(e));
        return grouped;
    }

    private static int days(List<EventFact> events) {
        return events == null ? 0 : (int) events.stream().map(EventFact::date).distinct().count();
    }

    private static List<Symptom> ordered(Set<Symptom> symptoms, Predicate<Symptom> keep) {
        return symptoms.stream().filter(keep).sorted().toList();
    }

    private static <T> List<T> within(List<T> items, LocalDate from, LocalDate to,
            Function<T, LocalDate> date) {
        return items.stream().filter(i -> !date.apply(i).isBefore(from) && !date.apply(i).isAfter(to)).toList();
    }
}
