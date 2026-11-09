package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.tools.ReadTools;

// Las herramientas de lectura propias de la consulta del medico (D31, RF-46, Pieza 6 paso 3): un periodo libre (no
// necesariamente una semana), con su comparacion opcional. Sobre la semilla real de P-014 (2026-09-21 a 2026-10-25).
@SpringBootTest(properties = "app.seed.demo=true")
@Transactional
class PeriodToolsTest {

    @Autowired private ReadTools.GetPeriodEntries periodEntries;
    @Autowired private ReadTools.GetPeriodMetrics periodMetrics;
    @Autowired private ReadTools.GetWeekMetrics weekMetrics;
    @Autowired private ReadTools.GetSymptomHistory history;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID p014;

    @BeforeEach
    void context() {
        p014 = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
    }

    private JsonNode args() throws Exception {
        return mapper.readTree("{}");
    }

    @Test
    void periodEntriesCoversTheWholeRangeEvenIfItDoesNotStartOnAMonday() throws Exception {
        // Un rango que no empieza en lunes (martes 22 a viernes 25 de septiembre): nada en el agente lo exige.
        AgentRunContext ctx = AgentRunContext.period(p014, LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 25), null, null);

        JsonNode out = mapper.readTree(periodEntries.execute(ctx, args()));

        assertThat(out.path("period_start").asText()).isEqualTo("2026-09-22");
        assertThat(out.path("period_end").asText()).isEqualTo("2026-09-25");
        JsonNode rows = out.path("entries");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).path("date").asText()).isEqualTo("2026-09-22");
        assertThat(rows.get(0).path("e").asText()).isEqualTo("e1");
        assertThat(ctx.knownEntries()).isEqualTo(4);
        // Nada del paciente salvo lo clinico.
        assertThat(out.toString()).doesNotContain("P-014").doesNotContain(p014.toString());
    }

    @Test
    void periodMetricsWithoutComparisonHasNoComparisonDatesAndIsNotComparable() throws Exception {
        AgentRunContext ctx = AgentRunContext.period(p014, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), null, null);

        JsonNode out = mapper.readTree(periodMetrics.execute(ctx, args()));

        assertThat(out.path("period_start").asText()).isEqualTo("2026-10-19");
        assertThat(out.path("period_end").asText()).isEqualTo("2026-10-25");
        assertThat(out.has("week")).isFalse();
        assertThat(out.path("entries").asInt()).isEqualTo(7);
        JsonNode vsPrevious = out.path("vs_previous");
        assertThat(vsPrevious.path("comparable").asBoolean()).isFalse();
        assertThat(vsPrevious.path("comparison_start").isNull()).isTrue();
        assertThat(vsPrevious.has("week")).isFalse();
    }

    @Test
    void periodMetricsWithComparisonMatchesAnArbitraryOtherPeriod() throws Exception {
        // La semana del 19 al 25 de octubre, comparada con la primera semana del paciente (21 al 27 de septiembre),
        // no "la semana anterior": la consulta puede comparar con cualquier periodo que el medico elija.
        AgentRunContext ctx = AgentRunContext.period(p014, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 27));

        JsonNode out = mapper.readTree(periodMetrics.execute(ctx, args()));

        JsonNode vsPrevious = out.path("vs_previous");
        assertThat(vsPrevious.path("comparable").asBoolean()).isTrue();
        assertThat(vsPrevious.path("comparison_start").asText()).isEqualTo("2026-09-21");
        assertThat(vsPrevious.path("comparison_end").asText()).isEqualTo("2026-09-27");
    }

    @Test
    void aWeekRequestedAsAPeriodGivesTheSameFiguresAsTheWeeklyTool() throws Exception {
        AgentRunContext weekly = new AgentRunContext(p014, LocalDate.of(2026, 10, 19));
        AgentRunContext asPeriod = AgentRunContext.period(p014, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));

        JsonNode fromWeekly = mapper.readTree(weekMetrics.execute(weekly, mapper.readTree("{\"week_start\":\"2026-10-19\"}")));
        JsonNode fromPeriod = mapper.readTree(periodMetrics.execute(asPeriod, args()));

        assertThat(fromPeriod.path("entries").asInt()).isEqualTo(fromWeekly.path("entries").asInt());
        assertThat(fromPeriod.path("days_with_entry").asInt()).isEqualTo(fromWeekly.path("days_with_entry").asInt());
        assertThat(fromPeriod.path("symptoms")).isEqualTo(fromWeekly.path("symptoms"));
        assertThat(fromPeriod.path("vs_previous").path("comparable").asBoolean())
                .isEqualTo(fromWeekly.path("vs_previous").path("comparable").asBoolean());
        assertThat(fromPeriod.path("vs_previous").path("days")).isEqualTo(fromWeekly.path("vs_previous").path("days"));
    }

    @Test
    void symptomHistoryStillGroupsByRealCalendarWeeksWhenThePeriodDoesNotStartOnAMonday() throws Exception {
        // El periodo (jueves 22 a sabado 24 de octubre) no empieza en lunes: el historial debe seguir agrupando por
        // semanas reales (lunes a domingo), sin perder ni duplicar un evento.
        AgentRunContext ctx = AgentRunContext.period(p014, LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 24), null, null);

        JsonNode out = mapper.readTree(history.execute(ctx, mapper.readTree("{\"symptom\":\"NAUSEAS\",\"weeks_back\":2}")));

        JsonNode weeks = out.path("weeks");
        assertThat(weeks).hasSize(2);
        assertThat(weeks.get(0).path("week").asText()).isEqualTo("2026-10-12");
        assertThat(weeks.get(1).path("week").asText()).isEqualTo("2026-10-19");
        // El 22 de octubre (dentro del periodo) tiene nauseas moderadas y vomito, segun la semilla: debe aparecer en
        // la semana del 19, no perderse ni caer en una semana que no existe en el mapa.
        long totalDays = 0;
        for (JsonNode w : weeks) {
            totalDays += w.path("days").asLong();
        }
        assertThat(totalDays).isGreaterThan(0);
    }
}
