package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;
import com.patientprogressauditor.agent.tools.AgentTool;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.llm.GeminiClient;
import com.patientprogressauditor.llm.GroqClient;
import com.patientprogressauditor.llm.LlmGateway;
import com.patientprogressauditor.llm.LlmProperties;
import com.patientprogressauditor.report.ReportDraftService;
import com.patientprogressauditor.report.ReportRepository;

// Mide cada version de las descripciones de herramientas (docs/decisiones/semana06.md) con el modelo REAL. Usa Gemini como
// principal y sin respaldo para no gastar la cuota diaria de Groq (200 000 tokens) ni mezclar proveedores en la medicion.
// Solo con RUN_LIVE_LLM=true y MEASURE_TOOLS=true.
@EnabledIfEnvironmentVariable(named = "MEASURE_TOOLS", matches = "true")
@SpringBootTest(properties = "app.seed.demo=true")
class ToolDescriptionsMeasurementTest {

    private record Case(String alias, LocalDate week) {
    }

    private static final List<Case> CASES = List.of(new Case("P-014", LocalDate.of(2026, 10, 19)),
            new Case("P-014", LocalDate.of(2026, 10, 12)), new Case("P-040", LocalDate.of(2026, 10, 19)));
    private static final int REPEATS = 3;

    @Autowired private List<AgentTool> tools;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @Autowired private ReportDraftService drafts;
    @Autowired private ReportContentBuilder builder;
    @Autowired private ReportRepository reports;
    @Autowired private EntryRepository entries;
    @Autowired private com.patientprogressauditor.report.MonthlyEligibility eligibility;
    @Autowired private ApprovedWeeklyReports approvedWeekly;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private record Row(boolean providerFailed, boolean ok, int iterations, int tokens, int toolCalls, int toolErrors, int rejections, Map<String, int[]> perTool) {
    }

    private Row measure(DescriptionVersion version, Case c) {
        LlmProperties llm = new LlmProperties();
        llm.getGroq().setApiKey(System.getenv("GROQ_API_KEY"));
        llm.getGemini().setApiKey(System.getenv("GEMINI_API_KEY"));
        llm.setPrimary("gemini");
        llm.setFallback("none");
        // En la medicion se espera lo que el proveedor pida (la capa gratuita limita las llamadas por minuto).
        llm.setMaxRetryAfter(java.time.Duration.ofSeconds(70));
        llm.setBackoff(List.of(java.time.Duration.ofSeconds(15), java.time.Duration.ofSeconds(30), java.time.Duration.ofSeconds(45)));
        LlmGateway gateway = new LlmGateway(List.of(new GroqClient(llm, mapper), new GeminiClient(llm, mapper)), llm, d -> Thread.sleep(d.toMillis()), mapper);
        AgentProperties props = new AgentProperties();
        props.setToolDescriptions(version);
        AgentRunner runner = new AgentRunner(gateway, tools, props, runs, steps, drafts, builder, reports, entries, eligibility, approvedWeekly,
                Clock.systemDefaultZone(), mapper);

        UUID patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, c.alias());
        UUID nurse = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Camilo Torres'", UUID.class);
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.INFORME);
        run.setPatientId(patient);
        run.setRequestedBy(nurse);
        runs.save(run);
        try {
            AgentRunner.Outcome outcome = runner.run(run, new AgentRunner.Task(patient, c.week(), nurse, ""));
            AgentRun saved = runs.findById(run.getId()).orElseThrow();
            List<AgentStep> all = steps.findByRunIdOrderBySeqAsc(run.getId());
            Map<String, int[]> perTool = new TreeMap<>();
            int calls = 0;
            int errors = 0;
            for (AgentStep s : all) {
                if (s.getStepType() == AgentStep.Type.HERRAMIENTA) {
                    calls++;
                    int[] counts = perTool.computeIfAbsent(s.getToolName(), k -> new int[2]);
                    counts[0]++;
                    if (s.isError()) {
                        counts[1]++;
                        errors++;
                    }
                }
            }
            int rejections = (int) all.stream().filter(s -> s.getStepType() == AgentStep.Type.VALIDACION && s.isError()).count();
            return new Row(outcome.status() == AgentRun.Status.FALLIDA, outcome.status() == AgentRun.Status.COMPLETADA && rejections == 0, saved.getIterations(),
                    saved.getTokensIn() + saved.getTokensOut(), calls, errors, rejections, perTool);
        } finally {
            jdbc.update("DELETE FROM report_citations WHERE report_version_id IN (SELECT v.id FROM report_versions v JOIN reports r ON r.id = v.report_id WHERE r.run_id = ?)", run.getId());
            jdbc.update("DELETE FROM report_versions WHERE report_id IN (SELECT id FROM reports WHERE run_id = ?)", run.getId());
            jdbc.update("DELETE FROM reports WHERE run_id = ?", run.getId());
            jdbc.update("DELETE FROM agent_steps WHERE run_id = ?", run.getId());
            jdbc.update("DELETE FROM agent_runs WHERE id = ?", run.getId());
        }
    }

    @Test
    void measureBothVersionsOfEveryToolDescription() throws InterruptedException {
        Map<DescriptionVersion, List<Row>> results = new java.util.EnumMap<>(DescriptionVersion.class);
        int turn = 0;
        for (Case c : CASES) {
            for (int i = 0; i < REPEATS; i++, turn++) {
                // Se alternan las versiones para que ningun cambio del proveedor durante la medicion favorezca a una.
                List<DescriptionVersion> order = turn % 2 == 0 ? List.of(DescriptionVersion.V1, DescriptionVersion.V2) : List.of(DescriptionVersion.V2, DescriptionVersion.V1);
                for (DescriptionVersion version : order) {
                    Row row = measure(version, c);
                    System.out.printf("AG> %s %s %s: %s, %d llamadas al modelo, %d tokens, %d usos de herramienta (%d con error), %d rechazos del borrador, %s%n",
                            version, c.alias(), c.week(), row.providerFailed() ? "EXCLUIDA (falló el proveedor)" : row.ok() ? "válido a la primera" : "NO a la primera",
                            row.iterations(), row.tokens(), row.toolCalls(), row.toolErrors(), row.rejections(),
                            row.perTool().entrySet().stream().map(e -> e.getKey() + "×" + e.getValue()[0] + (e.getValue()[1] > 0 ? "(" + e.getValue()[1] + " err)" : "")).toList());
                    if (!row.providerFailed()) {
                        results.computeIfAbsent(version, v -> new ArrayList<>()).add(row);
                    }
                    Thread.sleep(12_000);
                }
            }
        }
        results.forEach((version, rows) -> {
            double n = rows.size();
            System.out.printf("AG> === %s: %d corridas válidas para medir, válidas a la primera %d/%d, rechazos %.2f, errores de herramienta %.2f, llamadas al modelo %.1f, tokens %.0f%n",
                    version, rows.size(), rows.stream().filter(Row::ok).count(), rows.size(), rows.stream().mapToInt(Row::rejections).sum() / n,
                    rows.stream().mapToInt(Row::toolErrors).sum() / n, rows.stream().mapToInt(Row::iterations).sum() / n, rows.stream().mapToInt(Row::tokens).sum() / n);
            assertThat(rows).isNotEmpty();
        });
    }
}
