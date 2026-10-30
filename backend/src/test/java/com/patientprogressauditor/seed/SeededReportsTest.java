package com.patientprogressauditor.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.DiagnosisGuard;
import com.patientprogressauditor.agent.tools.MetricsView;
import com.patientprogressauditor.metrics.MetricsService;

// Los cinco informes sembrados (D11) son datos que el equipo muestra en la demo: tienen que cumplir las mismas reglas que
// los del agente. Cifras contra el codigo, citas reales, texto sin diagnostico y la visibilidad del medico.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class SeededReportsTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final String SEEDED = "provider = 'semilla'";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private MetricsService metrics;
    @Autowired private DemoDataSeeder seeder;

    private record Seeded(UUID id, UUID patientId, String alias, LocalDate start, LocalDate end, String status, UUID versionId, JsonNode content) {
    }

    private List<Seeded> seeded() {
        return jdbc.query("SELECT r.id, r.patient_id, p.alias, r.period_start, r.period_end, r.status, v.id AS version_id, v.content::text AS content"
                + " FROM reports r JOIN patients p ON p.id = r.patient_id JOIN report_versions v ON v.report_id = r.id AND v.version_no = 1"
                + " WHERE r." + SEEDED + " ORDER BY p.alias, r.period_start", (rs, i) -> {
                    try {
                        return new Seeded(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class), rs.getString("alias"),
                                rs.getDate("period_start").toLocalDate(), rs.getDate("period_end").toLocalDate(), rs.getString("status"),
                                rs.getObject("version_id", UUID.class), mapper.readTree(rs.getString("content")));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    @Test
    void thereAreFiveSeededReportsAsD11Says() {
        List<Seeded> reports = seeded();
        assertThat(reports).extracting(r -> r.alias() + " " + r.start() + " " + r.status()).containsExactly(
                "P-014 2026-09-21 APROBADO", "P-014 2026-09-28 APROBADO", "P-014 2026-10-05 APROBADO", "P-014 2026-10-12 APROBADO",
                "P-021 2026-10-12 PENDIENTE_REVISION");
    }

    @Test
    void theApprovedOnesNameWhoApprovedAndWhenAndTheVersionIsTheModelsOne() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.status, r.reviewed_by, r.reviewed_at, r.approved_version_no, r.created_at, v.origin, u.display_name"
                + " FROM reports r JOIN report_versions v ON v.report_id = r.id LEFT JOIN app_users u ON u.id = r.reviewed_by WHERE r." + SEEDED);
        assertThat(rows).hasSize(5);
        for (Map<String, Object> row : rows) {
            assertThat(row.get("origin")).isEqualTo("MODELO");
            if ("APROBADO".equals(row.get("status"))) {
                assertThat(row.get("display_name")).isEqualTo("Marcela Rojas");
                assertThat(row.get("reviewed_at")).isNotNull();
                assertThat(row.get("approved_version_no")).isEqualTo(1);
            } else {
                assertThat(row.get("reviewed_by")).isNull();
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE " + SEEDED + " AND model = 'plantilla sin modelo'", Integer.class))
                .as("la base dice que los escribio una plantilla, no el modelo").isEqualTo(5);
    }

    @Test
    void theFiguresAreTheOnesTheCodeCalculatesAndEachCitedFigureMatches() {
        for (Seeded r : seeded()) {
            JsonNode expected = MetricsView.of(metrics.weekly(r.patientId(), r.start()), mapper);
            assertThat(r.content().path("metrics")).as("metrics de %s %s", r.alias(), r.start()).isEqualTo(expected);
            assertThat(r.content().path("period").path("start").asText()).isEqualTo(r.start().toString());
            for (JsonNode claim : r.content().path("claims")) {
                for (JsonNode figure : claim.path("figures")) {
                    JsonNode actual = resolve(expected, figure.path("key").asText());
                    assertThat(actual.isMissingNode()).as("la clave %s existe", figure.path("key").asText()).isFalse();
                    assertThat(actual.asInt()).as("%s %s %s", r.alias(), r.start(), figure.path("key").asText()).isEqualTo(figure.path("value").asInt());
                }
            }
        }
    }

    private static JsonNode resolve(JsonNode root, String path) {
        JsonNode node = root;
        for (String part : path.split("\\.")) {
            node = node.isArray() ? node.path(Integer.parseInt(part)) : node.path(part);
        }
        return node;
    }

    @Test
    void everyClaimCitesRealEntriesOfThePatientNotAfterTheWeekAndTheCitationIndexMatches() {
        for (Seeded r : seeded()) {
            Set<String> indexed = new HashSet<>(jdbc.queryForList("SELECT claim_key || '|' || entry_id FROM report_citations WHERE report_version_id = ?", String.class, r.versionId()));
            Set<String> fromContent = new HashSet<>();
            assertThat(r.content().path("claims")).isNotEmpty();
            for (JsonNode claim : r.content().path("claims")) {
                assertThat(claim.path("entry_ids")).as("RN-05: %s %s %s", r.alias(), r.start(), claim.path("claim_key").asText()).isNotEmpty();
                for (JsonNode id : claim.path("entry_ids")) {
                    Map<String, Object> entry = jdbc.queryForMap("SELECT patient_id, entry_date FROM entries WHERE id = ?::uuid", id.asText());
                    assertThat(entry.get("patient_id")).isEqualTo(r.patientId());
                    assertThat(((java.sql.Date) entry.get("entry_date")).toLocalDate()).isBeforeOrEqualTo(r.end());
                    fromContent.add(claim.path("claim_key").asText() + "|" + id.asText());
                }
            }
            assertThat(indexed).isEqualTo(fromContent);
        }
    }

    @Test
    void noNumberInTheTextIsWithoutSupportAndNoTextDiagnoses() {
        for (Seeded r : seeded()) {
            Set<Integer> allowed = new HashSet<>();
            collect(r.content().path("metrics"), allowed);
            for (LocalDate d : List.of(r.start(), r.end(), r.start().minusDays(7), r.start().minusDays(1))) {
                allowed.add(d.getDayOfMonth());
            }
            allowed.add(r.start().getYear());
            List<String> texts = new java.util.ArrayList<>(List.of(r.content().path("summary").asText()));
            r.content().path("claims").forEach(c -> {
                texts.add(c.path("text").asText());
                texts.add(c.path("criterion").asText());
            });
            for (String text : texts) {
                assertThat(DiagnosisGuard.forbiddenIn(text)).as(text).isEmpty();
                Matcher m = NUMBER.matcher(text);
                while (m.find()) {
                    assertThat(allowed).as("%s %s: «%s»", r.alias(), r.start(), text).contains(Integer.parseInt(m.group()));
                }
            }
        }
    }

    private static void collect(JsonNode node, Set<Integer> out) {
        if (node.isInt()) {
            out.add(node.intValue());
        } else if (node.isContainerNode()) {
            node.forEach(child -> collect(child, out));
        }
    }

    @Test
    void restartingDoesNotDuplicateAndNothingCallsTheModel() {
        int before = jdbc.queryForObject("SELECT count(*) FROM reports WHERE " + SEEDED, Integer.class);
        int citations = jdbc.queryForObject("SELECT count(*) FROM report_citations", Integer.class);
        seeder.seed();
        seeder.seed();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE " + SEEDED, Integer.class)).isEqualTo(before).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_citations", Integer.class)).isEqualTo(citations);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_versions v JOIN reports r ON r.id = v.report_id WHERE r." + SEEDED, Integer.class)).isEqualTo(5);
        // La semilla no llama al proveedor: no deja ninguna ejecucion del agente.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs r JOIN reports rep ON rep.run_id = r.id WHERE rep." + SEEDED, Integer.class)).isZero();
    }

    @Test
    void theDoctorSeesTheFourApprovedAndNotThePendingOne() throws Exception {
        String list = mockMvc.perform(get("/api/reports").header("X-Demo-User", DOCTOR)).andReturn().getResponse().getContentAsString();
        JsonNode reports = mapper.readTree(list);
        List<String> seededIds = jdbc.queryForList("SELECT id::text FROM reports WHERE " + SEEDED, String.class);
        long visibleSeeded = 0;
        for (JsonNode r : reports) {
            assertThat(r.path("status").asText()).isEqualTo("APROBADO");
            if (seededIds.contains(r.path("id").asText())) {
                visibleSeeded++;
            }
        }
        assertThat(visibleSeeded).isEqualTo(4);
        UUID pending = DemoDataSeeder.reportId("P-021", LocalDate.of(2026, 10, 12));
        assertThat(mockMvc.perform(get("/api/reports/" + pending).header("X-Demo-User", DOCTOR)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/reports/" + pending).header("X-Demo-User", NURSE)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
