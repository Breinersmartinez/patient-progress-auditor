package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;
import com.patientprogressauditor.agent.tools.AgentTool;
import com.patientprogressauditor.agent.tools.ToolException;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.llm.LlmException;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.LlmGateway;
import com.patientprogressauditor.llm.LlmMessage;
import com.patientprogressauditor.llm.LlmProperties;
import com.patientprogressauditor.llm.LlmResponse;
import com.patientprogressauditor.llm.ScriptedClient;
import com.patientprogressauditor.llm.ToolCall;
import com.patientprogressauditor.llm.ToolSpec;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.metrics.MetricsService;
import com.patientprogressauditor.report.MonthlyEligibility;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportDraftService;
import com.patientprogressauditor.report.ReportRepository;

// El bucle con el modelo SIMULADO por un proveedor con guion (cada respuesta la decide la prueba) y los datos reales de
// P-014, semana 5 (19 al 25 de octubre). No es un modo de la aplicacion: es la forma de forzar cada limite en una prueba.
@SpringBootTest(properties = "app.seed.demo=true")
class AgentRunnerTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 19);

    @Autowired private List<AgentTool> realTools;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @Autowired private ReportDraftService drafts;
    @Autowired private ReportContentBuilder builder;
    @Autowired private ReportRepository reports;
    @Autowired private EntryRepository entries;
    @Autowired private MonthlyEligibility eligibility;
    @Autowired private ApprovedWeeklyReports approvedWeekly;
    @Autowired private MetricsService metricsService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private final AgentProperties properties = new AgentProperties();
    private final List<UUID> runIds = new ArrayList<>();
    private UUID patient;
    private UUID nurse;

    @BeforeEach
    void ids() {
        patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        nurse = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Camilo Torres'", UUID.class);
    }

    // Las pruebas confirman en la base (el agente guarda desde otro hilo): se limpia lo que crearon, nada mas.
    @AfterEach
    void clean() {
        for (UUID run : runIds) {
            jdbc.update("DELETE FROM monthly_report_weeks WHERE monthly_report_id IN (SELECT id FROM reports WHERE run_id = ?)", run);
            jdbc.update("DELETE FROM report_citations WHERE report_version_id IN (SELECT v.id FROM report_versions v JOIN reports r ON r.id = v.report_id WHERE r.run_id = ?)", run);
            jdbc.update("DELETE FROM report_versions WHERE report_id IN (SELECT id FROM reports WHERE run_id = ?)", run);
            jdbc.update("DELETE FROM reports WHERE run_id = ?", run);
            jdbc.update("DELETE FROM agent_steps WHERE run_id = ?", run);
            jdbc.update("DELETE FROM agent_runs WHERE id = ?", run);
        }
    }

    private AgentRunner runner(ScriptedClient groq, ScriptedClient gemini, AgentTool... extra) {
        LlmProperties llm = new LlmProperties();
        LlmGateway gateway = new LlmGateway(List.of(groq, gemini), llm, d -> { }, mapper);
        List<AgentTool> all = new ArrayList<>(realTools);
        all.addAll(List.of(extra));
        return new AgentRunner(gateway, all, properties, runs, steps, drafts, builder, reports, entries, eligibility,
                approvedWeekly, Clock.systemDefaultZone(), mapper);
    }

    private AgentRun newRun() {
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.INFORME);
        run.setPatientId(patient);
        run.setRequestedBy(nurse);
        run.setPeriodStart(WEEK);
        run.setPeriodEnd(WEEK.plusDays(6));
        runs.save(run);
        runIds.add(run.getId());
        return run;
    }

    private AgentRunner.Outcome execute(AgentRunner runner, AgentRun run) {
        return runner.run(run, new AgentRunner.Task(patient, WEEK, nurse, ""));
    }

    private static ToolCall call(String id, String name, String args) {
        try {
            return new ToolCall(id, name, new ObjectMapper().readTree(args), null);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static LlmResponse tools(int in, int out, ToolCall... calls) {
        return new LlmResponse(null, List.of(calls), LlmResponse.FinishReason.TOOL_CALLS, in, out);
    }

    private static final String WEEK_ARGS = "{\"week_start\":\"2026-10-19\"}";

    private static String goodDraft() {
        return """
                {"summary":"Las náuseas volvieron esta semana después de una semana sin ellas.",
                 "claims":[{"claim_key":"c1","text":"Las náuseas aparecieron 4 días esta semana y 0 la semana anterior.",
                   "criterion":"Frente a la semana anterior (12 al 18 de octubre)","entry_ids":["e1"],
                   "figures":[{"key":"NAUSEAS.days","value":4},{"key":"NAUSEAS.days@2026-10-12","value":0}]}],
                 "changes_vs_previous":["c1"],"recurring_patterns":["c1"]}""";
    }

    private static String alteredDraft() {
        return goodDraft().replace("\"value\":4", "\"value\":5");
    }

    private List<AgentStep.Type> stepTypes(AgentRun run) {
        return steps.findByRunIdOrderBySeqAsc(run.getId()).stream().map(AgentStep::getStepType).toList();
    }

    // --- éxito ---

    @Test
    void aFullRunReadsDecidesAndSavesAPendingDraftWithItsCitationsAndTrace() throws Exception {
        ScriptedClient groq = new ScriptedClient("groq", true)
                .then(tools(1500, 80, call("1", "get_week_metrics", WEEK_ARGS), call("2", "get_week_entries", WEEK_ARGS)))
                .then(tools(3200, 60, call("3", "get_symptom_history", "{\"symptom\":\"NAUSEAS\",\"weeks_back\":5}")))
                .then(tools(4000, 420, call("4", "save_draft", goodDraft())));
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.EXITO);
        assertThat(outcome.reportId()).isNotNull();
        assertThat(groq.calls).hasSize(3);

        String status = jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId());
        assertThat(status).isEqualTo("PENDIENTE_REVISION");
        assertThat(jdbc.queryForObject("SELECT run_id FROM reports WHERE id = ?", UUID.class, outcome.reportId())).isEqualTo(run.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_versions WHERE report_id = ? AND version_no = 1 AND origin = 'MODELO'",
                Integer.class, outcome.reportId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_citations WHERE report_version_id IN (SELECT id FROM report_versions WHERE report_id = ?)",
                Integer.class, outcome.reportId())).isGreaterThanOrEqualTo(1);
        String content = jdbc.queryForObject("SELECT content::text FROM report_versions WHERE report_id = ?", String.class, outcome.reportId());
        JsonNode json = mapper.readTree(content);
        assertThat(json.path("metrics").path("symptoms").path("NAUSEAS").path("days").asInt()).isEqualTo(4);
        assertThat(json.path("claims").get(0).path("entry_ids").get(0).asText()).matches("[0-9a-f-]{36}");

        AgentRun saved = runs.findById(run.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AgentRun.Status.COMPLETADA);
        assertThat(saved.getIterations()).isEqualTo(3);
        assertThat(saved.getTokensIn()).isEqualTo(8700);
        assertThat(saved.getTokensOut()).isEqualTo(560);
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(saved.getProvider()).isEqualTo("groq");
        assertThat(stepTypes(run)).contains(AgentStep.Type.ESTADO, AgentStep.Type.HERRAMIENTA, AgentStep.Type.LLAMADA_LLM);
        assertThat(steps.findByRunIdOrderBySeqAsc(run.getId()).stream().filter(s -> s.getStepType() == AgentStep.Type.HERRAMIENTA))
                .hasSize(4).extracting(AgentStep::getToolName).contains("save_draft", "get_symptom_history");
    }

    @Test
    void theModelNeverSeesThePatientAliasBedOrId() throws Exception {
        ScriptedClient groq = new ScriptedClient("groq", true).then(tools(10, 10, call("1", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("2", "save_draft", goodDraft())));
        execute(runner(groq, new ScriptedClient("gemini", true)), newRun());

        String everything = groq.calls.stream().flatMap(r -> r.messages().stream()).map(LlmMessage::content)
                .filter(c -> c != null).reduce("", String::concat);
        assertThat(everything).contains("get_week_metrics".substring(0, 0) + "2026-10-19").doesNotContain("P-014").doesNotContain("204-A")
                .doesNotContain(patient.toString());
    }

    @Test
    void theInventoryHasNoOutputToTheOutsideNoApprovalAndOnlyOneWrite() {
        AgentRunner runner = runner(new ScriptedClient("groq", true), new ScriptedClient("gemini", true));

        assertThat(runner.toolNames()).containsExactly("get_approved_weekly_reports", "get_symptom_history", "get_week_entries", "get_week_metrics", "list_patient_weeks", "save_draft");
        // Ninguna herramienta ACTUA fuera de lo permitido: el verbo aprobar/enviar/borrar no esta en su nombre. «approved» en
        // get_approved_weekly_reports describe el dato que LEE (informes ya aprobados por una persona), no una accion.
        assertThat(runner.toolNames()).noneMatch(n -> n.matches(".*(^|_)(approve|reject|aprobar|send|mail|delete|update)(_|$).*|.*(http|web|fetch).*"));
        assertThat(realTools.stream().filter(AgentTool::writes)).hasSize(1);
        assertThat(realTools.size()).isLessThanOrEqualTo(7);
    }

    // --- informe mensual (E13) ---

    private static final LocalDate MONTH = LocalDate.of(2026, 9, 21);

    private AgentRun newMonthlyRun(UUID forPatient, LocalDate first) {
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.MENSUAL);
        run.setPatientId(forPatient);
        run.setRequestedBy(nurse);
        run.setPeriodStart(first);
        run.setPeriodEnd(first.plusDays(27));
        runs.save(run);
        runIds.add(run.getId());
        return run;
    }

    private AgentRunner.Outcome executeMonthly(AgentRunner runner, AgentRun run, UUID forPatient, LocalDate first) {
        return runner.run(run, new AgentRunner.Task(Report.Type.MENSUAL, forPatient, first, nurse, ""));
    }

    // El sintoma de P-014 con mas dias en el mes y sus dias en la primera y la tercera semana (las cifras las pone el codigo).
    private record Pick(String symptom, int first, int third) {
    }

    private Pick pick() {
        Pick best = null;
        for (Symptom symptom : Symptom.values()) {
            int a = days(symptom, MONTH);
            int b = days(symptom, MONTH.plusWeeks(2));
            if (best == null || a + b > best.first() + best.third()) {
                best = new Pick(symptom.name(), a, b);
            }
        }
        return best;
    }

    private int days(Symptom symptom, LocalDate week) {
        return metricsService.weekly(patient, week).symptoms().stream().filter(m -> m.symptom() == symptom).mapToInt(m -> m.days()).findFirst().orElse(0);
    }

    private String monthlyDraft(Pick pick) {
        return """
                {"summary":"El síntoma se mantiene a lo largo del mes con cambios de una semana a otra.",
                 "claims":[{"claim_key":"c1","text":"El síntoma aparece %d días en la primera semana y %d días en la tercera.",
                   "criterion":"Primera semana frente a tercera semana","entry_ids":["e1"],
                   "figures":[{"key":"symptoms.%s.days@2026-09-21","value":%d},{"key":"symptoms.%s.days@2026-10-05","value":%d}]}],
                 "changes_vs_previous":["c1"],"recurring_patterns":["c1"]}""".formatted(pick.first(), pick.third(), pick.symptom(), pick.first(), pick.symptom(), pick.third());
    }

    @Test
    void aMonthlyRunReadsTheApprovedWeeklyReportsAndSavesAMonthlyDraftLinkedToThem() throws Exception {
        Pick pick = pick();
        ScriptedClient groq = new ScriptedClient("groq", true)
                .then(tools(2500, 60, call("1", "get_approved_weekly_reports", "{}")))
                .then(tools(4000, 420, call("2", "save_draft", monthlyDraft(pick))));
        AgentRun run = newMonthlyRun(patient, MONTH);

        AgentRunner.Outcome outcome = executeMonthly(runner(groq, new ScriptedClient("gemini", true)), run, patient, MONTH);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        assertThat(outcome.reportId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT report_type FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("MENSUAL");
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("PENDIENTE_REVISION");
        assertThat(jdbc.queryForObject("SELECT period_start FROM reports WHERE id = ?", java.sql.Date.class, outcome.reportId()).toLocalDate()).isEqualTo(MONTH);
        assertThat(jdbc.queryForObject("SELECT period_end FROM reports WHERE id = ?", java.sql.Date.class, outcome.reportId()).toLocalDate()).isEqualTo(MONTH.plusDays(27));
        // De cuales cuatro informes semanales salio (RN-10)
        List<UUID> linked = jdbc.queryForList("SELECT weekly_report_id FROM monthly_report_weeks WHERE monthly_report_id = ? ORDER BY week_no", UUID.class, outcome.reportId());
        assertThat(linked).hasSize(4);
        List<String> linkedStarts = jdbc.queryForList("SELECT period_start::text FROM reports WHERE id = ANY(?::uuid[]) ORDER BY period_start", String.class,
                (Object) linked.stream().map(UUID::toString).toArray(String[]::new));
        assertThat(linkedStarts).containsExactly("2026-09-21", "2026-09-28", "2026-10-05", "2026-10-12");
        assertThat(jdbc.queryForList("SELECT status FROM reports WHERE id = ANY(?::uuid[])", String.class,
                (Object) linked.stream().map(UUID::toString).toArray(String[]::new))).containsOnly("APROBADO");

        JsonNode content = mapper.readTree(jdbc.queryForObject("SELECT content::text FROM report_versions WHERE report_id = ?", String.class, outcome.reportId()));
        assertThat(content.path("period").path("weeks")).hasSize(4);
        assertThat(content.path("metrics").path("weeks")).hasSize(4);
        assertThat(content.path("weekly_reports")).hasSize(4);
        assertThat(content.path("weekly_reports").get(0).path("report_id").asText()).isEqualTo(linked.get(0).toString());
        assertThat(content.path("claims").get(0).path("figures")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_citations WHERE report_version_id IN (SELECT id FROM report_versions WHERE report_id = ?)",
                Integer.class, outcome.reportId())).isGreaterThanOrEqualTo(1);
        // La herramienta propia del mensual aparece en la traza, y el modelo la tenia disponible
        assertThat(steps.findByRunIdOrderBySeqAsc(run.getId()).stream().map(AgentStep::getToolName)).contains("get_approved_weekly_reports");
        assertThat(groq.calls.get(0).tools()).extracting(t -> t.name()).contains("get_approved_weekly_reports");
        assertThat(groq.calls.get(0).messages().get(0).content()).contains("MENSUAL");
        // Lo que el modelo lee no trae el alias, la cama ni el id del paciente
        String everything = groq.calls.stream().flatMap(r -> r.messages().stream()).map(LlmMessage::content).filter(c -> c != null).reduce("", String::concat);
        assertThat(everything).doesNotContain("P-014").doesNotContain("204-A").doesNotContain(patient.toString());
    }

    @Test
    void theWeeklyRunDoesNotSeeTheMonthlyTool() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(tools(10, 10, call("1", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("2", "save_draft", goodDraft())));
        execute(runner(groq, new ScriptedClient("gemini", true)), newRun());

        assertThat(groq.calls.get(0).tools()).extracting(t -> t.name())
                .containsExactlyInAnyOrder("get_symptom_history", "get_week_entries", "get_week_metrics", "list_patient_weeks", "save_draft");
    }

    @Test
    void aMonthlyRunWithAWeekNotApprovedIsBlockedWithoutCallingTheModel() {
        UUID p021 = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-021'", UUID.class);
        ScriptedClient groq = new ScriptedClient("groq", true);
        AgentRun run = newMonthlyRun(p021, LocalDate.of(2026, 9, 28));

        AgentRunner.Outcome outcome = executeMonthly(runner(groq, new ScriptedClient("gemini", true)), run, p021, LocalDate.of(2026, 9, 28));

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.FALLIDA);
        assertThat(outcome.message()).contains("falta el informe semanal aprobado").contains("28 de septiembre al 4 de octubre");
        assertThat(groq.calls).isEmpty();
        assertThat(outcome.reportId()).isNull();
    }

    @Test
    void theMonthlyIterationLimitLeavesAnIncompleteMonthlyReportWithTheCodeFiguresOfEachWeek() throws Exception {
        properties.setMaxIterations(1);
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(tools(500, 30, call("1", "get_approved_weekly_reports", "{}")), 1);
        AgentRun run = newMonthlyRun(patient, MONTH);

        AgentRunner.Outcome outcome = executeMonthly(runner(groq, new ScriptedClient("gemini", true)), run, patient, MONTH);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.INCOMPLETA);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.MAX_ITERACIONES);
        assertThat(jdbc.queryForObject("SELECT report_type FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("MENSUAL");
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("INCOMPLETO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monthly_report_weeks WHERE monthly_report_id = ?", Integer.class, outcome.reportId())).isEqualTo(4);
        JsonNode content = mapper.readTree(jdbc.queryForObject("SELECT content::text FROM report_versions WHERE report_id = ?", String.class, outcome.reportId()));
        assertThat(content.path("metrics").path("weeks")).hasSize(4);
        assertThat(content.path("summary").asText()).contains("El agente no terminó el borrador");
    }

    @Test
    void aMonthlyDraftThatCitesOrCountsOutsideTheMonthIsRejectedAndCorrected() {
        Pick pick = pick();
        String outside = monthlyDraft(pick).replace("2026-10-05", "2026-09-14");
        ScriptedClient groq = new ScriptedClient("groq", true)
                .then(tools(2500, 60, call("1", "get_approved_weekly_reports", "{}")))
                .then(tools(4000, 420, call("2", "save_draft", outside)))
                .then(tools(4200, 420, call("3", "save_draft", monthlyDraft(pick))));
        AgentRun run = newMonthlyRun(patient, MONTH);

        AgentRunner.Outcome outcome = executeMonthly(runner(groq, new ScriptedClient("gemini", true)), run, patient, MONTH);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        String observation = groq.calls.get(2).messages().stream().map(LlmMessage::content).filter(c -> c != null).reduce("", String::concat);
        assertThat(observation).contains("El informe mensual cubre desde la semana del 2026-09-21");
        assertThat(stepTypes(run)).contains(AgentStep.Type.VALIDACION);
    }

    // --- límites ---

    @Test
    void theIterationLimitStopsTheAgentAndLeavesTheReportIncompleteWithTheReason() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(tools(500, 30, call("1", "list_patient_weeks", "{}")), 1);
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(groq.calls).hasSize(5); // el límite por defecto: 5 llamadas, ni una más
        assertThat(outcome.status()).isEqualTo(AgentRun.Status.INCOMPLETA);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.MAX_ITERACIONES);
        assertThat(outcome.message()).contains("5 llamadas");
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("INCOMPLETO");
        assertThat(jdbc.queryForObject("SELECT status_reason FROM reports WHERE id = ?", String.class, outcome.reportId())).contains("5 llamadas");
        assertThat(stepTypes(run)).contains(AgentStep.Type.LIMITE);
        assertThat(runs.findById(run.getId()).orElseThrow().getStopReason()).isEqualTo(AgentRun.StopReason.MAX_ITERACIONES);
        // Aun incompleto, el informe trae lo que el codigo si sabe: las cifras.
        assertThat(jdbc.queryForObject("SELECT content::text FROM report_versions WHERE report_id = ?", String.class, outcome.reportId()))
                .contains("\"NAUSEAS\"").contains("El agente no terminó el borrador");
    }

    @Test
    void theIterationLimitCanBeLoweredByConfigurationToForceItInADemo() {
        properties.setMaxIterations(2);
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(tools(500, 30, call("1", "list_patient_weeks", "{}")), 1);

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), newRun());

        assertThat(groq.calls).hasSize(2);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.MAX_ITERACIONES);
    }

    @Test
    void theTokenLimitStopsBeforeSpendingACallThatWouldNotFit() {
        // Cada llamada gasta 16 000: la segunda llevaria el total a 32 000, mas que los 30 000 permitidos.
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(tools(14_000, 2_000, call("1", "list_patient_weeks", "{}")), 1);
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(groq.calls).hasSize(1);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.MAX_TOKENS);
        assertThat(outcome.status()).isEqualTo(AgentRun.Status.INCOMPLETA);
        assertThat(outcome.message()).contains("30000 tokens");
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("INCOMPLETO");
        assertThat(runs.findById(run.getId()).orElseThrow().getTokensIn()).isEqualTo(14_000);
    }

    // Una herramienta lenta de prueba: no es parte de la aplicacion; sirve para forzar el limite de tiempo por herramienta.
    private static class SlowTool implements AgentTool {
        @Override public String name() { return "slow_tool"; }
        @Override public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), "lenta", new ObjectMapper().createObjectNode().put("type", "object"));
        }
        @Override public String execute(AgentRunContext context, JsonNode arguments) throws ToolException {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "tarde";
        }
    }

    @Test
    void aSlowToolIsCutAtItsTimeoutReturnsAsAnObservationAndTheSecondTimeStopsTheRun() {
        properties.setToolTimeout(Duration.ofMillis(200));
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(tools(500, 30, call("1", "slow_tool", "{}")), 1);
        AgentRun run = newRun();

        long start = System.nanoTime();
        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true), new SlowTool()), run);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        assertThat(groq.calls).hasSize(2);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.TIMEOUT_HERRAMIENTA);
        assertThat(outcome.message()).contains("slow_tool").contains("2 ocasiones");
        // La primera vez no mata el bucle: el modelo recibe el error como observacion.
        String second = groq.calls.get(1).messages().stream().filter(m -> m.role() == LlmMessage.Role.TOOL)
                .map(LlmMessage::content).findFirst().orElseThrow();
        assertThat(second).startsWith("Error: la herramienta tardó más de 0 s").isNotBlank();
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("INCOMPLETO");
    }

    @Test
    void theAgentCannotApproveAnythingWhateverItAsksFor() {
        ScriptedClient groq = new ScriptedClient("groq", true)
                .then(tools(10, 10, call("1", "approve_report", "{\"report_id\":\"x\"}")))
                .then(tools(10, 10, call("2", "send_email", "{\"to\":\"x@example.test\"}")))
                .then(tools(10, 10, call("3", "save_draft", goodDraft().replace("e1", "e1"))));
        AgentRun run = newRun();
        // Primero lee, para tener alias que citar.
        groq = new ScriptedClient("groq", true).then(tools(10, 10, call("0", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("1", "approve_report", "{}"))).then(tools(10, 10, call("2", "send_email", "{}")))
                .then(tools(10, 10, call("3", "save_draft", goodDraft())));

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        List<String> observations = groq.calls.get(3).messages().stream().filter(m -> m.role() == LlmMessage.Role.TOOL).map(LlmMessage::content).toList();
        assertThat(observations).anyMatch(o -> o.contains("«approve_report» no existe")).anyMatch(o -> o.contains("«send_email» no existe"));
        // Lo unico que existe es el borrador pendiente de revision: nunca un informe aprobado.
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("PENDIENTE_REVISION");
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM reports WHERE id = ?", UUID.class, outcome.reportId())).isNull();
    }

    // --- errores que no matan el bucle ---

    @Test
    void aToolErrorGoesBackToTheModelAsAnObservationAndTheModelCorrects() {
        ScriptedClient groq = new ScriptedClient("groq", true)
                .then(tools(10, 10, call("1", "get_week_metrics", "{\"week_start\":\"2026-10-20\"}")))
                .then(tools(10, 10, call("2", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("3", "save_draft", goodDraft())));
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        String observation = groq.calls.get(1).messages().stream().filter(m -> m.role() == LlmMessage.Role.TOOL).map(LlmMessage::content).findFirst().orElseThrow();
        assertThat(observation).startsWith("Error: week_start debe ser un lunes").contains("2026-10-19");
        assertThat(steps.findByRunIdOrderBySeqAsc(run.getId()).stream().filter(AgentStep::isError)).isNotEmpty();
    }

    @Test
    void aRejectedDraftIsCorrectedOnceAndAccepted() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(tools(10, 10, call("0", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("1", "save_draft", alteredDraft()))).then(tools(10, 10, call("2", "save_draft", goodDraft())));
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        String problem = groq.calls.get(2).messages().stream().filter(m -> m.role() == LlmMessage.Role.TOOL).map(LlmMessage::content)
                .reduce((a, b) -> b).orElseThrow();
        assertThat(problem).contains("El borrador NO se guardó").contains("vale 4").contains("escribió 5");
        assertThat(stepTypes(run)).contains(AgentStep.Type.VALIDACION);
    }

    @Test
    void aDraftThatStaysInvalidIsDegradedToPlainTextAndMarkedIncomplete() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(tools(10, 10, call("0", "get_week_entries", WEEK_ARGS)))
                .thenAlways(tools(10, 10, call("1", "save_draft", alteredDraft())), 1);
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), run);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.INCOMPLETA);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.SALIDA_INVALIDA);
        assertThat(groq.calls).hasSize(3); // lectura + borrador inválido + borrador inválido: no sigue intentando
        assertThat(jdbc.queryForObject("SELECT status FROM reports WHERE id = ?", String.class, outcome.reportId())).isEqualTo("INCOMPLETO");
        assertThat(jdbc.queryForObject("SELECT plain_text FROM report_versions WHERE report_id = ?", String.class, outcome.reportId()))
                .startsWith("(Sin validar").contains("Las náuseas aparecieron 4 días");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_citations WHERE report_version_id IN (SELECT id FROM report_versions WHERE report_id = ?)",
                Integer.class, outcome.reportId())).isZero();
    }

    @Test
    void whenTheModelAnswersWithTextAndNoToolItIsToldHowToFinish() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(ScriptedClient.answer("Ya lo tengo.", 10, 10))
                .then(tools(10, 10, call("0", "get_week_entries", WEEK_ARGS))).then(tools(10, 10, call("1", "save_draft", goodDraft())));

        AgentRunner.Outcome outcome = execute(runner(groq, new ScriptedClient("gemini", true)), newRun());

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.COMPLETADA);
        assertThat(groq.calls.get(1).messages().get(groq.calls.get(1).messages().size() - 1).content()).contains("save_draft");
    }

    @Test
    void theModelIsReminderedWhenIterationsAreRunningOut() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(tools(10, 10, call("1", "list_patient_weeks", "{}")), 1);

        execute(runner(groq, new ScriptedClient("gemini", true)), newRun());

        assertThat(groq.calls.get(3).messages().stream().map(LlmMessage::content).filter(c -> c != null))
                .anyMatch(c -> c.contains("Te quedan 2 llamadas"));
        assertThat(groq.calls.get(4).messages().stream().map(LlmMessage::content).filter(c -> c != null))
                .anyMatch(c -> c.contains("Te quedan 1 llamadas"));
    }

    // --- proveedor y precondiciones ---

    @Test
    void whenNoProviderAnswersTheRunFailsWithAnHonestMessageAndNoReportIsCreated() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(new LlmException(Kind.UNAVAILABLE, "groq", "HTTP 503"), 1);
        ScriptedClient gemini = new ScriptedClient("gemini", true).thenAlways(new LlmException(Kind.UNAVAILABLE, "gemini", "HTTP 503"), 1);
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = execute(runner(groq, gemini), run);

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.FALLIDA);
        assertThat(outcome.stopReason()).isEqualTo(AgentRun.StopReason.PROVEEDOR_NO_DISPONIBLE);
        assertThat(outcome.message()).startsWith("No se pudo obtener respuesta de la IA.").doesNotContain("HTTP");
        assertThat(outcome.reportId()).isNull();
        AgentRun saved = runs.findById(run.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AgentRun.Status.FALLIDA);
        assertThat(saved.getErrorMessage()).startsWith("No se pudo obtener respuesta de la IA.");
        assertThat(stepTypes(run)).contains(AgentStep.Type.ERROR, AgentStep.Type.LLAMADA_LLM);
    }

    @Test
    void aWeekWithoutEntriesDoesNotCallTheModelAtAll() {
        ScriptedClient groq = new ScriptedClient("groq", true);
        AgentRun run = newRun();

        AgentRunner.Outcome outcome = runner(groq, new ScriptedClient("gemini", true))
                .run(run, new AgentRunner.Task(patient, LocalDate.of(2026, 11, 2), nurse, ""));

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.FALLIDA);
        assertThat(outcome.message()).contains("no tiene registros");
        assertThat(groq.calls).isEmpty();
    }

    @Test
    void aSecondDraftForTheSameWeekIsNotStartedWhileOneIsCurrent() {
        ScriptedClient first = new ScriptedClient("groq", true).then(tools(10, 10, call("0", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("1", "save_draft", goodDraft())));
        assertThat(execute(runner(first, new ScriptedClient("gemini", true)), newRun()).status()).isEqualTo(AgentRun.Status.COMPLETADA);

        ScriptedClient second = new ScriptedClient("groq", true);
        AgentRunner.Outcome outcome = execute(runner(second, new ScriptedClient("gemini", true)), newRun());

        assertThat(outcome.status()).isEqualTo(AgentRun.Status.FALLIDA);
        assertThat(outcome.message()).isEqualTo("Ya hay un informe vigente de esa semana.");
        assertThat(second.calls).isEmpty();
    }

    @Test
    void theFirstVersionOfTheDescriptionsIsUsedWhenConfigured() {
        properties.setToolDescriptions(DescriptionVersion.V1);
        ScriptedClient groq = new ScriptedClient("groq", true).then(tools(10, 10, call("0", "get_week_entries", WEEK_ARGS)))
                .then(tools(10, 10, call("1", "save_draft", goodDraft())));

        execute(runner(groq, new ScriptedClient("gemini", true)), newRun());

        assertThat(groq.calls.get(0).tools()).extracting(ToolSpec::description).contains("Devuelve las métricas de una semana.");
    }
}
