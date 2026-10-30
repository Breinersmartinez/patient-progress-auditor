package com.patientprogressauditor.metrics;

import static com.patientprogressauditor.entry.Symptom.DIARREA;
import static com.patientprogressauditor.entry.Symptom.FIEBRE;
import static com.patientprogressauditor.entry.Symptom.NAUSEAS;
import static com.patientprogressauditor.entry.Symptom.VOMITO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.metrics.MetricsCalculator.EntryFact;
import com.patientprogressauditor.metrics.MetricsCalculator.EventFact;

// Casos pequeños con la cuenta hecha a mano. Semana evaluada: lunes 19 al domingo 25 de octubre de 2026;
// semana anterior: 12 al 18 de octubre.
class MetricsCalculatorTest {

    private static final UUID PATIENT = UUID.randomUUID();
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);

    private static LocalDate oct(int day) {
        return LocalDate.of(2026, 10, day);
    }

    // Arma entradas y eventos: cada evento crea su propia entrada, salvo que se pase una ya existente.
    private static class Data {
        final List<EntryFact> entries = new ArrayList<>();
        final List<EventFact> events = new ArrayList<>();

        UUID entry(LocalDate date) {
            return entry(date, false);
        }

        UUID entry(LocalDate date, boolean unstructured) {
            UUID id = UUID.randomUUID();
            entries.add(new EntryFact(id, date, unstructured));
            return id;
        }

        Data event(UUID entry, LocalDate date, Symptom symptom, Intensity intensity, Integer occurrences) {
            events.add(new EventFact(entry, date, symptom, intensity, occurrences == null ? null : occurrences.shortValue()));
            return this;
        }

        Data symptom(LocalDate date, Symptom symptom) {
            return event(entry(date), date, symptom, Intensity.SIN_INDICAR, null);
        }

        WeeklyMetrics compute(Set<Symptom> earlier) {
            return MetricsCalculator.compute(PATIENT, WEEK, entries, events, earlier);
        }
    }

    @Test
    void aSymptomNeverSeenBeforeIsNewAndOneSeenInAnOlderWeekReappears() {
        Data data = new Data();
        data.entry(oct(13));
        data.symptom(oct(20), NAUSEAS);
        data.symptom(oct(21), VOMITO);

        WeeklyMetrics m = data.compute(Set.of(VOMITO));

        assertThat(m.comparison().comparable()).isTrue();
        assertThat(m.comparison().newSymptoms()).containsExactly(NAUSEAS);
        assertThat(m.comparison().reappeared()).containsExactly(VOMITO);
        assertThat(m.comparison().persistent()).isEmpty();
        assertThat(m.comparison().disappeared()).isEmpty();
    }

    @Test
    void persistentAndDisappearedComeWithTheChangeInDays() {
        Data data = new Data();
        data.symptom(oct(12), VOMITO);
        data.symptom(oct(13), VOMITO);
        data.symptom(oct(14), FIEBRE);
        data.symptom(oct(20), VOMITO);

        WeeklyMetrics m = data.compute(Set.of());

        assertThat(m.comparison().persistent()).containsExactly(VOMITO);
        assertThat(m.comparison().disappeared()).containsExactly(FIEBRE);
        assertThat(m.comparison().newSymptoms()).isEmpty();
        assertThat(m.comparison().daysChange()).extracting("symptom", "previousDays", "days", "change")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(VOMITO, 2, 1, -1),
                        org.assertj.core.groups.Tuple.tuple(FIEBRE, 1, 0, -1));
        assertThat(m.comparison().previousEntries()).isEqualTo(3);
        assertThat(m.comparison().previousTotalEvents()).isEqualTo(3);
    }

    @Test
    void aSymptomThatWasAbsentInThePreviousWeekAndHadTheSameStateTwoWeeksAgoIsNotNew() {
        // Hueco intermedio: ausente en W-1 (con entradas), presente en W-2: reaparece.
        Data data = new Data();
        data.entry(oct(14));
        data.symptom(oct(23), DIARREA);

        WeeklyMetrics m = data.compute(Set.of(DIARREA));

        assertThat(m.comparison().reappeared()).containsExactly(DIARREA);
        assertThat(m.comparison().newSymptoms()).isEmpty();
    }

    @Test
    void whenThePreviousWeekHasNoEntriesItIsNotComparableAndNothingIsInvented() {
        Data data = new Data();
        data.symptom(oct(20), NAUSEAS);

        WeeklyMetrics m = data.compute(Set.of(NAUSEAS));

        assertThat(m.comparison().comparable()).isFalse();
        assertThat(m.comparison().previousWeekStart()).isEqualTo(oct(12));
        assertThat(m.comparison().previousEntries()).isZero();
        assertThat(m.comparison().newSymptoms()).isEmpty();
        assertThat(m.comparison().reappeared()).isEmpty();
        assertThat(m.comparison().persistent()).isEmpty();
        assertThat(m.comparison().disappeared()).isEmpty();
        assertThat(m.comparison().daysChange()).isEmpty();
        assertThat(m.symptoms()).hasSize(1);
    }

    @Test
    void countsDaysWithAndWithoutEntriesAndListsTheMissingOnes() {
        Data data = new Data();
        data.entry(oct(19));
        data.entry(oct(19));
        data.entry(oct(21));

        WeeklyMetrics m = data.compute(Set.of());

        assertThat(m.entries()).isEqualTo(3);
        assertThat(m.daysWithEntry()).isEqualTo(2);
        assertThat(m.daysWithoutEntry()).isEqualTo(5);
        assertThat(m.datesWithoutEntry()).containsExactly(oct(20), oct(22), oct(23), oct(24), oct(25));
        assertThat(m.weekStart()).isEqualTo(oct(19));
        assertThat(m.weekEnd()).isEqualTo(oct(25));
        assertThat(m.symptoms()).isEmpty();
        assertThat(m.totalEvents()).isZero();
    }

    @Test
    void anEmptyWeekHasSevenDaysWithoutEntry() {
        WeeklyMetrics m = new Data().compute(Set.of());

        assertThat(m.entries()).isZero();
        assertThat(m.daysWithEntry()).isZero();
        assertThat(m.daysWithoutEntry()).isEqualTo(7);
    }

    @Test
    void unstructuredEntriesAreCountedApartAndNotAsNoSymptoms() {
        Data data = new Data();
        UUID previousUnstructured = data.entry(oct(14), true);
        UUID a = data.entry(oct(22), true);
        data.entry(oct(21), false);

        WeeklyMetrics m = data.compute(Set.of());

        assertThat(m.unstructuredEntries()).isEqualTo(1);
        assertThat(m.unstructuredEntryIds()).containsExactly(a);
        assertThat(m.comparison().previousUnstructuredEntries()).isEqualTo(1);
        assertThat(previousUnstructured).isNotEqualTo(a);
    }

    @Test
    void twoEventsOfTheSameSymptomOnTheSameDayAreOneDayAndTwoEvents() {
        Data data = new Data();
        data.event(data.entry(oct(20)), oct(20), VOMITO, Intensity.LEVE, 1);
        data.event(data.entry(oct(20)), oct(20), VOMITO, Intensity.INTENSO, 2);
        data.event(data.entry(oct(22)), oct(22), VOMITO, Intensity.SIN_INDICAR, null);

        WeeklyMetrics.SymptomMetrics vomit = data.compute(Set.of()).symptoms().get(0);

        assertThat(vomit.symptom()).isEqualTo(VOMITO);
        assertThat(vomit.days()).isEqualTo(2);
        assertThat(vomit.dates()).containsExactly(oct(20), oct(22));
        assertThat(vomit.events()).isEqualTo(3);
        assertThat(vomit.occurrences()).isEqualTo(3);
        assertThat(vomit.maxIntensity()).isEqualTo(Intensity.INTENSO);
    }

    @Test
    void occurrencesAndIntensityStayNullWhenNobodyIndicatedThem() {
        Data data = new Data();
        data.symptom(oct(20), FIEBRE);

        WeeklyMetrics.SymptomMetrics fever = data.compute(Set.of()).symptoms().get(0);

        assertThat(fever.occurrences()).isNull();
        assertThat(fever.maxIntensity()).isNull();
    }

    @Test
    void eventsOutsideTheWeekAndThePreviousOneAreIgnored() {
        Data data = new Data();
        data.symptom(oct(5), DIARREA);
        data.symptom(oct(26), DIARREA);
        data.symptom(oct(19), VOMITO);
        data.symptom(oct(25), VOMITO);

        WeeklyMetrics m = data.compute(Set.of());

        assertThat(m.symptoms()).extracting("symptom").containsExactly(VOMITO);
        assertThat(m.entries()).isEqualTo(2);
        assertThat(m.totalEvents()).isEqualTo(2);
    }

    @Test
    void symptomsComeInCatalogOrder() {
        Data data = new Data();
        data.symptom(oct(20), NAUSEAS);
        data.symptom(oct(20), VOMITO);
        data.symptom(oct(20), DIARREA);

        assertThat(data.compute(Set.of()).symptoms()).extracting("symptom").containsExactly(VOMITO, DIARREA, NAUSEAS);
    }

    @Test
    void aWeekThatDoesNotStartOnMondayIsRejected() {
        assertThatThrownBy(() -> MetricsCalculator.compute(PATIENT, oct(20), List.of(), List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
