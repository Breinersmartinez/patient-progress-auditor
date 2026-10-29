package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.tools.ReadTools;

// El validador con los datos reales de P-014, semana 5 (19 al 25 de octubre): náuseas 4 días, vómito 1, apetito bajo 2,
// dolor de hueso 1; la semana anterior (12 al 18) no tuvo náuseas.
@SpringBootTest(properties = "app.seed.demo=true")
@Transactional
class DraftValidatorTest {

    @Autowired private DraftValidator validator;
    @Autowired private ReportContentBuilder builder;
    @Autowired private ReadTools.GetSymptomHistory history;
    @Autowired private ReadTools.GetWeekEntries weekEntries;
    @Autowired private ReadTools.GetWeekMetrics metricsTool;
    @Autowired private ReadTools.GetApprovedWeeklyReports approvedTool;
    @Autowired private ApprovedWeeklyReports approvedWeekly;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private AgentRunContext ctx;
    private String nauseaAlias;

    @BeforeEach
    void context() throws Exception {
        UUID p014 = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        ctx = new AgentRunContext(p014, LocalDate.of(2026, 10, 19));
        JsonNode h = mapper.readTree(history.execute(ctx, mapper.readTree("{\"symptom\":\"NAUSEAS\",\"weeks_back\":2}")));
        nauseaAlias = h.path("weeks").get(1).path("entries").get(0).path("e").asText();
    }

    private JsonNode draft(String claimJson) throws Exception {
        return mapper.readTree("""
                {"summary":"Las náuseas volvieron esta semana después de una semana sin ellas.",
                 "claims":[%s],"changes_vs_previous":["c1"],"recurring_patterns":["c1"]}""".formatted(claimJson));
    }

    private String claim(String text, String entries, String figures) {
        return """
                {"claim_key":"c1","text":"%s","criterion":"Frente a la semana anterior (12 al 18 de octubre)",
                 "entry_ids":%s,"figures":%s}""".formatted(text, entries, figures);
    }

    private DraftValidator.Checked check(String text, String entries, String figures) throws Exception {
        return validator.check(draft(claim(text, entries, figures)), ctx);
    }

    @Test
    void aGoodDraftPassesAndItsCitationsBecomeRealEntryIds() throws Exception {
        DraftValidator.Checked c = check("Las náuseas aparecieron 4 días esta semana y 0 días la semana anterior.",
                "[\"" + nauseaAlias + "\"]",
                "[{\"key\":\"NAUSEAS.days\",\"value\":4},{\"key\":\"NAUSEAS.days@2026-10-12\",\"value\":0}]");

        assertThat(c.problems()).isEmpty();
        assertThat(c.citations().get("c1")).hasSize(1).containsExactly(ctx.entryOf(nauseaAlias));
        assertThat(c.model().path("claims").get(0).path("entry_ids").get(0).asText()).isEqualTo(ctx.entryOf(nauseaAlias).toString());
        assertThat(c.model().path("changes_vs_previous")).hasSize(1);
    }

    @Test
    void aDateNumberOfACitedEntryIsBackedByTheEntryItself() throws Exception {
        JsonNode e = mapper.readTree(history.execute(ctx, mapper.readTree("{\"symptom\":\"NAUSEAS\",\"weeks_back\":1}")))
                .path("weeks").get(0).path("entries").get(0);
        int day = LocalDate.parse(e.path("date").asText()).getDayOfMonth();

        DraftValidator.Checked c = check("Las náuseas se registraron el día " + day + ".", "[\"" + e.path("e").asText() + "\"]", "[]");

        assertThat(c.problems()).isEmpty();
    }

    @Test
    void keySyntaxSlipsAreForgivenButTheValueIsStillChecked() throws Exception {
        String cite = "[\"" + nauseaAlias + "\"]";
        // Corchetes, «:valor» pegado y la clave de una comparacion sin indice: se entiende de que cifra habla.
        DraftValidator.Checked c = check("Las náuseas aparecieron 4 días esta semana y 0 días la semana anterior.", cite,
                "[{\"key\":\"vs_previous.days.NAUSEAS[1]\",\"value\":4},{\"key\":\"symptoms.NAUSEAS.days:4\",\"value\":4},{\"key\":\"vs_previous.days.NAUSEAS\",\"value\":0}]");
        assertThat(c.problems()).isEmpty();
        // La clave que se guarda es la canonica: .1 y .0 elegidos por el valor.
        assertThat(c.model().path("claims").get(0).path("figures").findValuesAsText("key"))
                .containsExactly("vs_previous.days.NAUSEAS.1", "symptoms.NAUSEAS.days", "vs_previous.days.NAUSEAS.0");

        // Perdonar la sintaxis no perdona el valor.
        DraftValidator.Checked wrong = check("Las náuseas aparecieron 5 días.", cite, "[{\"key\":\"vs_previous.days.NAUSEAS[1]\",\"value\":5}]");
        assertThat(wrong.problems()).anyMatch(p -> p.contains("vale 4") && p.contains("escribió 5"));
        DraftValidator.Checked neither = check("Las náuseas aparecieron 9 días.", cite, "[{\"key\":\"vs_previous.days.NAUSEAS\",\"value\":9}]");
        assertThat(neither.problems()).anyMatch(p -> p.contains(".0» vale 0") && p.contains(".1» vale 4"));
    }

    @Test
    void theMessagesTellTheModelHowToFixTheThreeFailuresMeasuredInE14() throws Exception {
        // Una fecha escrita como numero: se le dice que use el dia de la semana.
        DraftValidator.Checked date = check("Las náuseas empezaron el 10 de octubre.", "[\"" + nauseaAlias + "\"]", "[]");
        assertThat(date.problems()).anyMatch(p -> p.contains("10") && p.contains("día de la semana"));
        // Sin afirmaciones (semana sin sintomas): se le dice como escribir la que corresponde.
        DraftValidator.Checked none = validator.check(mapper.readTree("{\"summary\":\"Sin síntomas.\",\"claims\":[],\"changes_vs_previous\":[],\"recurring_patterns\":[]}"), ctx);
        assertThat(none.problems()).anyMatch(p -> p.contains("total_events") && p.contains("No se registraron síntomas"));
        // Sin cita porque trata de algo ausente: se le dice que cite la semana anterior.
        DraftValidator.Checked uncited = check("Las náuseas ya no aparecen.", "[]", "[]");
        assertThat(uncited.problems()).anyMatch(p -> p.contains("semana anterior") && p.contains("get_week_entries"));
    }

    @Test
    void anAlteredFigureIsRejectedWithTheRealValue() throws Exception {
        DraftValidator.Checked c = check("Las náuseas aparecieron 5 días.", "[\"" + nauseaAlias + "\"]",
                "[{\"key\":\"NAUSEAS.days\",\"value\":5}]");

        assertThat(c.valid()).isFalse();
        assertThat(c.problems()).anyMatch(p -> p.contains("NAUSEAS.days") && p.contains("vale 4") && p.contains("escribió 5"));
    }

    @Test
    void aCitationThatDoesNotExistIsRejected() throws Exception {
        DraftValidator.Checked c = check("Las náuseas reaparecieron.", "[\"e99\", \"" + UUID.randomUUID() + "\"]", "[]");

        assertThat(c.problems()).anyMatch(p -> p.contains("«e99»") && p.contains("no existe"));
        assertThat(c.problems()).anyMatch(p -> p.contains("no existe") && p.contains(UUID.randomUUID().toString().substring(0, 0)));
        assertThat(c.citations().get("c1")).isEmpty();
    }

    @Test
    void aClaimWithoutCitationsIsRejected() throws Exception {
        assertThat(check("Las náuseas reaparecieron.", "[]", "[]").problems()).anyMatch(p -> p.contains("entry_ids"));
    }

    @Test
    void aNumberInTheTextWithoutBackingIsRejected() throws Exception {
        DraftValidator.Checked c = check("Hubo 7 episodios de náuseas.", "[\"" + nauseaAlias + "\"]", "[]");

        assertThat(c.problems()).anyMatch(p -> p.contains("el número 7") && p.contains("no tiene respaldo"));
    }

    @Test
    void figuresMustExistBeResolvableAndNotLookAhead() throws Exception {
        assertThat(check("x", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"FOO.days\",\"value\":1}]").problems())
                .anyMatch(p -> p.contains("FOO") && p.contains("catálogo"));
        assertThat(check("x", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"NAUSEAS.days@2026-10-26\",\"value\":1}]").problems())
                .anyMatch(p -> p.contains("posterior"));
        assertThat(check("x", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"entries\",\"value\":\"siete\"}]").problems())
                .anyMatch(p -> p.contains("número entero"));
        assertThat(check("x", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"APETITO_BAJO.occurrences\",\"value\":1}]").problems())
                .anyMatch(p -> p.contains("nadie indicó"));
        assertThat(check("x", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"mucho\",\"value\":1}]").problems())
                .anyMatch(p -> p.contains("no existe"));
        assertThat(check("x", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"Muy mucho!\",\"value\":1}]").problems())
                .anyMatch(p -> p.contains("formato"));
    }

    @Test
    void theSummaryMayNameOnlyNumbersThatExistAmongTheCodeMetrics() throws Exception {
        String claim = """
                {"claim_key":"c1","text":"Náuseas.","criterion":"semana anterior","entry_ids":["%s"],"figures":[]}""".formatted(nauseaAlias);
        JsonNode invented = mapper.readTree("""
                {"summary":"Hubo 13 días de náuseas.","claims":[%s],"changes_vs_previous":[],"recurring_patterns":[]}""".formatted(claim));
        JsonNode real = mapper.readTree("""
                {"summary":"Hubo 4 días de náuseas y 7 registros.","claims":[%s],"changes_vs_previous":[],"recurring_patterns":[]}""".formatted(claim));

        assertThat(validator.check(invented, ctx).problems()).anyMatch(p -> p.startsWith("summary: el número 13 no es ninguna de las cifras"));
        assertThat(validator.check(real, ctx).problems()).isEmpty();
    }

    @Test
    void aKeyIsThePathInTheMetricsJsonTheModelAlreadyHasAndTheShortFormWorksToo() throws Exception {
        String nausea = "[\"" + nauseaAlias + "\"]";
        assertThat(check("x", nausea, "[{\"key\":\"symptoms.NAUSEAS.days\",\"value\":4},{\"key\":\"NAUSEAS.days\",\"value\":4}]").problems()).isEmpty();
        assertThat(check("x", nausea, "[{\"key\":\"vs_previous.days.NAUSEAS.0\",\"value\":0},{\"key\":\"vs_previous.days.NAUSEAS.1\",\"value\":4}]").problems()).isEmpty();
        assertThat(check("x", nausea, "[{\"key\":\"entries\",\"value\":7},{\"key\":\"vs_previous.previous_entries\",\"value\":7}]").problems()).isEmpty();
        // Un sintoma que no aparece esa semana vale 0 dias.
        assertThat(check("x", nausea, "[{\"key\":\"symptoms.FIEBRE.days\",\"value\":0}]").problems()).isEmpty();
        assertThat(check("x", nausea, "[{\"key\":\"symptoms.NAUSEAS.days@2026-10-05\",\"value\":3}]").problems()).isEmpty();
        assertThat(check("x", nausea, "[{\"key\":\"symptoms.NAUSEAS.days\",\"value\":9}]").problems()).anyMatch(p -> p.contains("vale 4"));
    }

    @Test
    void anUnbackedNumberComesWithTheExactKeyToAddWhenSomeFigureHasThatValue() throws Exception {
        DraftValidator.Checked c = check("Hubo 7 registros.", "[\"" + nauseaAlias + "\"]", "[]");

        assertThat(c.problems()).anyMatch(p -> p.contains("el número 7") && p.contains("{\"key\":\"entries\",\"value\":7}"));
        assertThat(check("Hubo 13 episodios.", "[\"" + nauseaAlias + "\"]", "[]").problems())
                .anyMatch(p -> p.contains("Ninguna cifra de las métricas vale 13"));
    }

    @Test
    void diagnosisRecommendationsAndDosesAreRejectedAnywhere() throws Exception {
        assertThat(check("El diagnóstico probable es gastroenteritis.", "[\"" + nauseaAlias + "\"]", "[]").problems())
                .anyMatch(p -> p.contains("«diagnóstico»") && p.contains("no diagnostica"));
        assertThat(check("Se debe suspender el tratamiento.", "[\"" + nauseaAlias + "\"]", "[]").problems())
                .anyMatch(p -> p.contains("«suspender»") || p.contains("«tratamiento»"));
        JsonNode d = mapper.readTree("""
                {"summary":"Se recomienda reposo.","claims":[{"claim_key":"c1","text":"Náuseas.","criterion":"semana anterior",
                 "entry_ids":["%s"],"figures":[]}],"changes_vs_previous":[],"recurring_patterns":[]}""".formatted(nauseaAlias));
        assertThat(validator.check(d, ctx).problems()).anyMatch(p -> p.startsWith("summary contiene «Se recomienda") || p.startsWith("summary contiene «recomienda"));
    }

    @Test
    void theListsMustPointToExistingClaimsAndTheStructureMustBeComplete() throws Exception {
        JsonNode d = mapper.readTree("""
                {"summary":"ok","claims":[{"claim_key":"c1","text":"Náuseas.","criterion":"semana anterior",
                 "entry_ids":["%s"],"figures":[]}],"changes_vs_previous":["c9"],"recurring_patterns":"c1"}""".formatted(nauseaAlias));

        DraftValidator.Checked c = validator.check(d, ctx);

        assertThat(c.problems()).anyMatch(p -> p.contains("c9"));
        assertThat(c.problems()).anyMatch(p -> p.contains("recurring_patterns debe ser una lista"));
        assertThat(validator.check(mapper.readTree("{}"), ctx).problems()).hasSizeGreaterThanOrEqualTo(2);
        JsonNode dup = mapper.readTree("""
                {"summary":"ok","claims":[{"claim_key":"c1","text":"a","criterion":"b","entry_ids":["%1$s"],"figures":[]},
                 {"claim_key":"c1","text":"a","criterion":"b","entry_ids":["%1$s"],"figures":[]}],"changes_vs_previous":[],"recurring_patterns":[]}""".formatted(nauseaAlias));
        assertThat(validator.check(dup, ctx).problems()).anyMatch(p -> p.contains("repetido"));
    }

    // --- contenido que pone el codigo ---

    @Test
    void theContentCarriesTheCodeMetricsAndWhatIsMissingAndTheModelCannotChangeThem() throws Exception {
        DraftValidator.Checked c = check("Las náuseas aparecieron 4 días.", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"NAUSEAS.days\",\"value\":4}]");
        ObjectNode content = builder.build(c.model(), ctx);

        assertThat(content.path("period").path("start").asText()).isEqualTo("2026-10-19");
        assertThat(content.path("period").path("compared_with").asText()).isEqualTo("2026-10-12");
        assertThat(content.path("metrics").path("symptoms").path("NAUSEAS").path("days").asInt()).isEqualTo(4);
        assertThat(content.path("claims").get(0).path("figures").get(0).path("value").asInt()).isEqualTo(4);
        assertThat(content.path("out_of_scope").isNull()).isTrue();
        assertThat(content.path("insufficient_data")).isEmpty(); // 7 de 7 días con registro, semana anterior comparable
    }

    @Test
    void missingDaysAndUnstructuredEntriesAreDeclaredByTheCode() throws Exception {
        UUID p033 = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-033'", UUID.class);
        UUID author = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Camilo Torres'", UUID.class);
        // Una semana que no existe en la semilla: sin registros salvo uno que solo tiene texto.
        jdbc.update("INSERT INTO entries (id, patient_id, entry_date, body, author_id, structuring_status) VALUES (?, ?, '2026-11-03', 'Solo texto', ?, 'SIN_ESTRUCTURAR')",
                UUID.randomUUID(), p033, author);
        AgentRunContext c2 = new AgentRunContext(p033, LocalDate.of(2026, 11, 2));
        ObjectNode content = builder.build(mapper.createObjectNode(), c2);

        assertThat(content.path("insufficient_data")).extracting(n -> n.path("kind").asText())
                .containsExactly("DIAS_SIN_REGISTRO", "SEMANA_ANTERIOR_SIN_REGISTROS", "ENTRADAS_SIN_ESTRUCTURAR");
        assertThat(content.path("insufficient_data").get(0).path("dates")).hasSize(6);
        assertThat(content.path("insufficient_data").get(0).path("text").asText()).startsWith("No hay registros los días lunes 2 de noviembre");
        assertThat(content.path("insufficient_data").get(2).path("count").asInt()).isEqualTo(1);
    }

    @Test
    void aRequestForDiagnosisIsDeclaredOutOfScope() throws Exception {
        ctx.setRequest("Además dime qué enfermedad tiene y si hay que suspender el tratamiento");
        DraftValidator.Checked c = check("Las náuseas aparecieron 4 días.", "[\"" + nauseaAlias + "\"]", "[{\"key\":\"NAUSEAS.days\",\"value\":4}]");

        assertThat(builder.build(c.model(), ctx).path("out_of_scope").asText()).startsWith("Se pidió un diagnóstico");
    }

    // --- informe mensual (E13): cuatro semanas, del 21-sep al 18-oct ---

    private AgentRunContext monthlyContext() throws Exception {
        UUID p014 = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        AgentRunContext month = AgentRunContext.monthly(p014, LocalDate.of(2026, 9, 21));
        // El agente solo puede citar lo que leyo: se lee la primera semana para tener un alias
        JsonNode week = mapper.readTree(weekEntries.execute(month, mapper.readTree("{\"week_start\":\"2026-09-21\"}")));
        monthAlias = week.path("entries").get(0).path("e").asText();
        return month;
    }

    private String monthAlias;

    private JsonNode monthlyDraft(String text, String figures) throws Exception {
        return mapper.readTree("""
                {"summary":"El síntoma cambia a lo largo del mes.",
                 "claims":[{"claim_key":"c1","text":"%s","criterion":"Primera semana frente a cuarta semana","entry_ids":["%s"],"figures":%s}],
                 "changes_vs_previous":["c1"],"recurring_patterns":["c1"]}""".formatted(text, monthAlias, figures));
    }

    @Test
    void aMonthlyClaimCanBackItsNumbersWithTheFiguresOfEachWeek() throws Exception {
        AgentRunContext month = monthlyContext();
        // Las cifras salen del codigo: lo que vale cada una en su semana se lee de las metricas, no se supone
        int w1 = mapper.readTree(metricsOf(month, "2026-09-21")).path("total_events").asInt();
        int w3 = mapper.readTree(metricsOf(month, "2026-10-05")).path("total_events").asInt();

        DraftValidator.Checked c = validator.check(monthlyDraft("Los eventos pasan de %d en la primera semana a %d en la tercera.".formatted(w1, w3),
                "[{\"key\":\"total_events@2026-09-21\",\"value\":%d},{\"key\":\"total_events@2026-10-05\",\"value\":%d}]".formatted(w1, w3)), month);

        assertThat(c.problems()).isEmpty();
        assertThat(c.model().path("claims").get(0).path("figures")).hasSize(2);
    }

    @Test
    void aMonthlyFigureFromAWeekOutsideTheMonthIsRejectedAndExplained() throws Exception {
        AgentRunContext month = monthlyContext();

        DraftValidator.Checked before = validator.check(monthlyDraft("Hay eventos.", "[{\"key\":\"total_events@2026-09-14\",\"value\":0}]"), month);
        DraftValidator.Checked after = validator.check(monthlyDraft("Hay eventos.", "[{\"key\":\"total_events@2026-10-19\",\"value\":0}]"), month);

        assertThat(before.problems()).anyMatch(p -> p.contains("El informe mensual cubre desde la semana del 2026-09-21") && p.contains("2026-09-14 es anterior"));
        assertThat(after.problems()).anyMatch(p -> p.contains("última semana del informe") && p.contains("2026-10-19 es posterior"));
    }

    @Test
    void aMonthlyFigureWithTheWrongValueIsRejected() throws Exception {
        AgentRunContext month = monthlyContext();
        int real = mapper.readTree(metricsOf(month, "2026-09-21")).path("total_events").asInt();

        DraftValidator.Checked c = validator.check(monthlyDraft("Hay %d eventos.".formatted(real + 7), "[{\"key\":\"total_events@2026-09-21\",\"value\":%d}]".formatted(real + 7)), month);

        assertThat(c.problems()).anyMatch(p -> p.contains("vale " + real + " según el sistema"));
    }

    @Test
    void theHintForAnUnbackedMonthlyNumberNamesTheWeekOfTheFigure() throws Exception {
        AgentRunContext month = monthlyContext();

        // 7 son los dias de cada semana con registro: es una cifra del mes (entries), pero el texto no la respalda
        DraftValidator.Checked c = validator.check(monthlyDraft("Hay registros los 7 días de la primera semana.", "[]"), month);

        assertThat(c.problems()).anyMatch(p -> p.contains("el número 7 del texto no tiene respaldo") && p.contains("@2026-"));
    }

    private String metricsOf(AgentRunContext month, String week) {
        return metricsTool.execute(month, mapper.createObjectNode().put("week_start", week));
    }

    @Test
    void theMonthlyContentHasTheCodeFiguresOfEachWeekTheSourcesAndWhatIsMissingPerWeek() throws Exception {
        AgentRunContext month = monthlyContext();
        List<UUID> weekly = jdbc.queryForList("SELECT id FROM reports WHERE patient_id = ? AND report_type = 'SEMANAL' AND status = 'APROBADO' ORDER BY period_start", UUID.class, month.patientId());
        month.setWeeklyReports(approvedWeekly.load(weekly.subList(0, 4)));
        ObjectNode model = mapper.createObjectNode().put("summary", "Resumen");
        model.putArray("claims");

        ObjectNode content = builder.build(model, month);

        assertThat(content.path("period").path("start").asText()).isEqualTo("2026-09-21");
        assertThat(content.path("period").path("end").asText()).isEqualTo("2026-10-18");
        assertThat(content.path("period").path("weeks")).extracting(JsonNode::asText).containsExactly("2026-09-21", "2026-09-28", "2026-10-05", "2026-10-12");
        assertThat(content.path("metrics").path("weeks")).hasSize(4);
        assertThat(content.path("metrics").path("weeks").get(2).path("week").asText()).isEqualTo("2026-10-05");
        assertThat(content.path("weekly_reports")).hasSize(4);
        assertThat(content.path("weekly_reports").get(0).path("week_no").asInt()).isEqualTo(1);
        assertThat(content.path("weekly_reports").get(3).path("report_id").asText()).isEqualTo(weekly.get(3).toString());
        assertThat(content.path("weekly_reports").get(0).path("summary").asText()).isNotBlank();
        // La comparacion con "la semana anterior" no se repite en el mensual
        assertThat(content.path("insufficient_data")).noneMatch(n -> n.path("kind").asText().startsWith("SEMANA_ANTERIOR"));
        assertThat(content.path("out_of_scope").isNull()).isTrue();
    }

    @Test
    void theMonthlyToolReturnsTheApprovedReportsWithCitableAliasesAndRefusesToRunInAWeeklyTask() throws Exception {
        AgentRunContext month = monthlyContext();
        List<UUID> weekly = jdbc.queryForList("SELECT id FROM reports WHERE patient_id = ? AND report_type = 'SEMANAL' AND status = 'APROBADO' ORDER BY period_start", UUID.class, month.patientId());
        month.setWeeklyReports(approvedWeekly.load(weekly.subList(0, 4)));

        JsonNode out = mapper.readTree(approvedTool.execute(month, mapper.createObjectNode()));

        assertThat(out.path("weeks")).hasSize(4);
        assertThat(out.path("weeks").get(1).path("week").asText()).isEqualTo("2026-09-28");
        assertThat(out.path("weeks").get(0).path("summary").asText()).isNotBlank();
        assertThat(out.path("weeks").get(0).path("claims").get(0).path("e").get(0).asText()).matches("e\\d+");
        assertThat(month.entryOf(out.path("weeks").get(0).path("claims").get(0).path("e").get(0).asText())).isNotNull();
        assertThatThrownBy(() -> approvedTool.execute(ctx, mapper.createObjectNode())).hasMessageContaining("solo del informe mensual");
    }
}
