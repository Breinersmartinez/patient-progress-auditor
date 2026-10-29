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
import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;
import com.patientprogressauditor.agent.tools.AgentTool;
import com.patientprogressauditor.agent.tools.ReadTools;
import com.patientprogressauditor.agent.tools.ToolException;

@SpringBootTest(properties = "app.seed.demo=true")
@Transactional
class ReadToolsTest {

    private static final LocalDate WEEK5 = LocalDate.of(2026, 10, 19);

    @Autowired private ReadTools.ListPatientWeeks listWeeks;
    @Autowired private ReadTools.GetWeekEntries weekEntries;
    @Autowired private ReadTools.GetWeekMetrics weekMetrics;
    @Autowired private ReadTools.GetSymptomHistory history;
    @Autowired private List<AgentTool> allTools;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID p014;
    private AgentRunContext ctx;

    @BeforeEach
    void context() {
        p014 = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        ctx = new AgentRunContext(p014, WEEK5);
    }

    private JsonNode args(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void listsTheWeeksWithEntriesUpToTheReportWeek() throws Exception {
        JsonNode weeks = mapper.readTree(listWeeks.execute(ctx, args("{}"))).path("weeks");

        assertThat(weeks).hasSize(5);
        assertThat(weeks.get(0).path("week").asText()).isEqualTo("2026-09-21");
        assertThat(weeks.get(4).path("week").asText()).isEqualTo("2026-10-19");
        assertThat(weeks.get(4).path("entries").asInt()).isEqualTo(7);

        AgentRunContext earlier = new AgentRunContext(p014, LocalDate.of(2026, 10, 5));
        assertThat(mapper.readTree(listWeeks.execute(earlier, args("{}"))).path("weeks")).hasSize(3);
    }

    @Test
    void entriesComeWithShortAliasesTheOriginalTextAndTheConfirmedSymptoms() throws Exception {
        JsonNode out = mapper.readTree(weekEntries.execute(ctx, args("{\"week_start\":\"2026-10-19\"}")));
        JsonNode entries = out.path("entries");

        assertThat(entries).hasSize(7);
        assertThat(entries.get(0).path("e").asText()).isEqualTo("e1");
        assertThat(entries.get(6).path("e").asText()).isEqualTo("e7");
        assertThat(entries.get(0).path("date").asText()).isEqualTo("2026-10-19");
        assertThat(entries.get(0).path("text").asText()).isNotBlank();
        assertThat(entries.toString()).contains("NAUSEAS");
        assertThat(ctx.knownEntries()).isEqualTo(7);
        assertThat(ctx.entryOf("e1")).isNotNull();
        assertThat(ctx.entryOf("e99")).isNull();
        // Nada del paciente salvo lo clinico: ni alias, ni cama, ni id.
        assertThat(out.toString()).doesNotContain("P-014").doesNotContain("204-A").doesNotContain(p014.toString());
    }

    @Test
    void theSameEntryKeepsItsAliasAcrossTools() throws Exception {
        weekEntries.execute(ctx, args("{\"week_start\":\"2026-10-19\"}"));
        int known = ctx.knownEntries();

        weekEntries.execute(ctx, args("{\"week_start\":\"2026-10-19\"}"));
        history.execute(ctx, args("{\"symptom\":\"NAUSEAS\",\"weeks_back\":1}"));

        assertThat(ctx.knownEntries()).isEqualTo(known);
    }

    @Test
    void aLongTextIsCutToSaveTokens() throws Exception {
        UUID author = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Camilo Torres'", UUID.class);
        jdbc.update("INSERT INTO entries (id, patient_id, entry_date, body, author_id, structuring_status) VALUES (?, ?, '2026-10-19', ?, ?, 'SIN_ESTRUCTURAR')",
                UUID.randomUUID(), p014, "x".repeat(1000), author);

        JsonNode entries = mapper.readTree(weekEntries.execute(ctx, args("{\"week_start\":\"2026-10-19\"}"))).path("entries");

        JsonNode long1 = null;
        for (JsonNode e : entries) {
            if (e.path("text").asText().startsWith("xxx")) {
                long1 = e;
            }
        }
        assertThat(long1).isNotNull();
        assertThat(long1.path("text").asText()).hasSize(401).endsWith("…");
        assertThat(long1.path("unstructured").asBoolean()).isTrue();
    }

    @Test
    void metricsAreTheCodeCalculationInCompactForm() throws Exception {
        JsonNode m = mapper.readTree(weekMetrics.execute(ctx, args("{\"week_start\":\"2026-10-19\"}")));

        assertThat(m.path("week").asText()).isEqualTo("2026-10-19");
        assertThat(m.path("entries").asInt()).isEqualTo(7);
        assertThat(m.path("symptoms").path("NAUSEAS").path("days").asInt()).isEqualTo(4);
        assertThat(m.path("vs_previous").path("week").asText()).isEqualTo("2026-10-12");
        assertThat(m.path("vs_previous").path("reappeared")).extracting(JsonNode::asText).contains("NAUSEAS");
        assertThat(m.path("vs_previous").path("days").path("NAUSEAS")).extracting(JsonNode::asInt).containsExactly(0, 4);
        assertThat(m.toString().length()).isLessThan(1500);
    }

    @Test
    void symptomHistoryShowsTheDaysPerWeekAndTheCitableEntries() throws Exception {
        JsonNode h = mapper.readTree(history.execute(ctx, args("{\"symptom\":\"nauseas\",\"weeks_back\":5}")));

        assertThat(h.path("symptom").asText()).isEqualTo("NAUSEAS");
        assertThat(h.path("weeks")).extracting(w -> w.path("days").asInt()).containsExactly(2, 0, 3, 0, 4);
        assertThat(h.path("weeks").get(0).path("week").asText()).isEqualTo("2026-09-21");
        JsonNode first = h.path("weeks").get(4).path("entries").get(0);
        assertThat(first.path("e").asText()).startsWith("e");
        assertThat(ctx.entryOf(first.path("e").asText())).isNotNull();
        assertThat(ctx.dateOf(ctx.entryOf(first.path("e").asText()))).isBetween(WEEK5, WEEK5.plusDays(6));
    }

    @Test
    void usesOutsideTheScopeAreRejectedWithAMessageTheModelCanFix() {
        assertThatThrownBy(() -> weekEntries.execute(ctx, args("{\"week_start\":\"2026-10-26\"}")))
                .isInstanceOf(ToolException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> weekMetrics.execute(ctx, args("{\"week_start\":\"2026-10-20\"}")))
                .isInstanceOf(ToolException.class).hasMessageContaining("lunes").hasMessageContaining("2026-10-19");
        assertThatThrownBy(() -> weekMetrics.execute(ctx, args("{\"week_start\":\"ayer\"}")))
                .isInstanceOf(ToolException.class).hasMessageContaining("AAAA-MM-DD");
        assertThatThrownBy(() -> weekMetrics.execute(ctx, args("{}"))).isInstanceOf(ToolException.class);
        assertThatThrownBy(() -> history.execute(ctx, args("{\"symptom\":\"dolor de pecho\",\"weeks_back\":2}")))
                .isInstanceOf(ToolException.class).hasMessageContaining("DOLOR_CABEZA");
        assertThatThrownBy(() -> history.execute(ctx, args("{\"symptom\":\"VOMITO\",\"weeks_back\":9}")))
                .isInstanceOf(ToolException.class).hasMessageContaining("entre 1 y 8");
    }

    @Test
    void theContextRejectsAWeekThatIsNotMonday() {
        assertThatThrownBy(() -> new AgentRunContext(p014, LocalDate.of(2026, 10, 20))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noToolReceivesAPatientIdAndNoReadToolWrites() {
        for (AgentTool tool : allTools) {
            assertThat(tool.spec(DescriptionVersion.V2).parameters().toString()).as(tool.name()).doesNotContain("patient");
            if (!tool.name().equals("save_draft")) {
                assertThat(tool.writes()).as(tool.name()).isFalse();
            }
        }
    }

    @Test
    void bothDescriptionVersionsExistAndTheSecondOneSaysWhenNotToUseIt() {
        for (AgentTool tool : allTools) {
            String v1 = tool.spec(DescriptionVersion.V1).description();
            String v2 = tool.spec(DescriptionVersion.V2).description();
            assertThat(v1).as(tool.name()).isNotBlank();
            assertThat(v2.length()).as(tool.name()).isGreaterThan(v1.length() * 3);
            assertThat(v2).as(tool.name()).containsPattern("No (la )?(use|devuelve|necesita)");
        }
    }
}
