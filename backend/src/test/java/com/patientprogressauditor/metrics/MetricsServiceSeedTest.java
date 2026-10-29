package com.patientprogressauditor.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.web.NotFoundException;

// Compara cada semana de cada paciente sembrado con metrics/expected-metrics.json, que genera
// scripts/expected_metrics.py a partir del JSON de la semilla, con otro metodo y en otro lenguaje.
@SpringBootTest(properties = "app.seed.demo=true")
@Transactional
class MetricsServiceSeedTest {

    @Autowired private MetricsService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID patientId(String alias) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, alias);
    }

    private JsonNode expected() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/metrics/expected-metrics.json")) {
            return mapper.readTree(in);
        }
    }

    private static List<String> names(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static List<String> names(Iterator<String> it) {
        List<String> out = new ArrayList<>();
        it.forEachRemaining(out::add);
        return out;
    }

    private static List<String> names(List<Symptom> symptoms) {
        return symptoms.stream().map(Symptom::name).toList();
    }

    @Test
    void everyWeekOfEverySeededPatientMatchesTheIndependentCalculation() throws Exception {
        JsonNode expected = expected();
        int weeks = 0;
        for (Iterator<Map.Entry<String, JsonNode>> patients = expected.fields(); patients.hasNext();) {
            Map.Entry<String, JsonNode> patient = patients.next();
            for (Iterator<Map.Entry<String, JsonNode>> it = patient.getValue().fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> week = it.next();
                String where = patient.getKey() + " " + week.getKey();
                JsonNode want = week.getValue();
                WeeklyMetrics got = service.weekly(patientId(patient.getKey()), LocalDate.parse(week.getKey()));

                assertThat(got.entries()).as(where + " entradas").isEqualTo(want.get("entries").asInt());
                assertThat(got.daysWithEntry()).as(where + " días con entrada").isEqualTo(want.get("daysWithEntry").asInt());
                assertThat(got.daysWithoutEntry()).as(where + " días sin entrada").isEqualTo(want.get("daysWithoutEntry").asInt());
                assertThat(got.datesWithoutEntry().stream().map(LocalDate::toString).toList())
                        .as(where + " fechas sin entrada").isEqualTo(names(want.get("datesWithoutEntry")));
                assertThat(got.unstructuredEntries()).as(where + " sin estructurar").isEqualTo(want.get("unstructuredEntries").asInt());
                assertThat(got.totalEvents()).as(where + " eventos").isEqualTo(want.get("totalEvents").asInt());

                JsonNode symptoms = want.get("symptoms");
                assertThat(got.symptoms().stream().map(s -> s.symptom().name()).toList())
                        .as(where + " síntomas").containsExactlyInAnyOrderElementsOf(names(symptoms.fieldNames()));
                for (WeeklyMetrics.SymptomMetrics s : got.symptoms()) {
                    JsonNode w = symptoms.get(s.symptom().name());
                    String at = where + " " + s.symptom();
                    assertThat(s.days()).as(at + " días").isEqualTo(w.get("days").asInt());
                    assertThat(s.dates().stream().map(LocalDate::toString).toList()).as(at + " fechas").isEqualTo(names(w.get("dates")));
                    assertThat(s.events()).as(at + " eventos").isEqualTo(w.get("events").asInt());
                    assertThat(s.occurrences()).as(at + " veces").isEqualTo(w.get("occurrences").isNull() ? null : w.get("occurrences").asInt());
                    assertThat(s.maxIntensity() == null ? null : s.maxIntensity().name()).as(at + " intensidad")
                            .isEqualTo(w.get("maxIntensity").isNull() ? null : w.get("maxIntensity").asText());
                }

                WeeklyMetrics.Comparison c = got.comparison();
                assertThat(c.comparable()).as(where + " comparable").isEqualTo(want.get("comparable").asBoolean());
                assertThat(c.previousEntries()).as(where + " entradas previas").isEqualTo(want.get("previousEntries").asInt());
                assertThat(names(c.persistent())).as(where + " persistentes").isEqualTo(names(want.get("persistent")));
                assertThat(names(c.disappeared())).as(where + " desaparecidos").isEqualTo(names(want.get("disappeared")));
                assertThat(names(c.newSymptoms())).as(where + " nuevos").isEqualTo(names(want.get("new")));
                assertThat(names(c.reappeared())).as(where + " reaparecidos").isEqualTo(names(want.get("reappeared")));
                JsonNode change = want.get("daysChange");
                assertThat(c.daysChange()).as(where + " variación").hasSize(change.size());
                for (WeeklyMetrics.DaysChange d : c.daysChange()) {
                    JsonNode pair = change.get(d.symptom().name());
                    assertThat(d.previousDays()).as(where + " " + d.symptom() + " antes").isEqualTo(pair.get(0).asInt());
                    assertThat(d.days()).as(where + " " + d.symptom() + " ahora").isEqualTo(pair.get(1).asInt());
                    assertThat(d.change()).isEqualTo(pair.get(1).asInt() - pair.get(0).asInt());
                }
                weeks++;
            }
        }
        // D11: 18 semanas-paciente.
        assertThat(weeks).isEqualTo(18);
    }

    @Test
    void p014NauseaRecursInWeeksOneThreeAndFive() {
        UUID p014 = patientId("P-014");
        int[] days = new int[5];
        List<String> kinds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            WeeklyMetrics m = service.weekly(p014, LocalDate.of(2026, 9, 21).plusWeeks(i));
            days[i] = m.symptoms().stream().filter(s -> s.symptom() == Symptom.NAUSEAS).mapToInt(s -> s.days()).sum();
            kinds.add(m.comparison().reappeared().contains(Symptom.NAUSEAS) ? "reaparece"
                    : m.comparison().disappeared().contains(Symptom.NAUSEAS) ? "desaparece" : "-");
        }

        assertThat(days).containsExactly(2, 0, 3, 0, 4);
        assertThat(kinds).containsExactly("-", "desaparece", "reaparece", "desaparece", "reaparece");
    }

    @Test
    void aWeekAfterTheLastDataHasNothingAndIsNotComparable() {
        WeeklyMetrics m = service.weekly(patientId("P-014"), LocalDate.of(2026, 11, 2));

        assertThat(m.entries()).isZero();
        assertThat(m.daysWithoutEntry()).isEqualTo(7);
        assertThat(m.comparison().comparable()).isFalse();
    }

    @Test
    void anEntryWithOnlyTheTextIsCountedAsUnstructuredAndNotAsNoSymptoms() {
        UUID p033 = patientId("P-033");
        UUID author = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Marcela Rojas'", UUID.class);
        UUID entry = UUID.randomUUID();
        jdbc.update("INSERT INTO entries (id, patient_id, entry_date, body, author_id, structuring_status)"
                + " VALUES (?, ?, '2026-10-26', 'Solo texto', ?, 'SIN_ESTRUCTURAR')", entry, p033, author);

        WeeklyMetrics m = service.weekly(p033, LocalDate.of(2026, 10, 26));

        assertThat(m.entries()).isEqualTo(1);
        assertThat(m.unstructuredEntries()).isEqualTo(1);
        assertThat(m.unstructuredEntryIds()).containsExactly(entry);
        assertThat(m.symptoms()).isEmpty();
        assertThat(m.comparison().comparable()).isTrue();
        assertThat(m.comparison().previousEntries()).isGreaterThan(0);
    }

    @Test
    void unknownPatientsAndNonMondaysAreRejected() {
        assertThatThrownBy(() -> service.weekly(UUID.randomUUID(), LocalDate.of(2026, 10, 19)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.weekly(patientId("P-014"), LocalDate.of(2026, 10, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
