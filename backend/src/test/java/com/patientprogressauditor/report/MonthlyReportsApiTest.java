package com.patientprogressauditor.report;

import static com.patientprogressauditor.report.ReportFixtures.DOCTOR;
import static com.patientprogressauditor.report.ReportFixtures.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;

// El informe mensual pasa por la MISMA revision que el semanal (RF-36, RN-01, RN-02): el medico solo lo ve aprobado, solo
// enfermeria lo aprueba, se edita con versiones y las cifras no las cambia nadie. P-014 (21-sep al 18-oct) trae sus cuatro
// semanas aprobadas de la semilla; el mensual se inserta por JDBC para fijar el estado sin pasar por el agente.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class MonthlyReportsApiTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 21);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;

    private ReportFixtures fx;
    private UUID p014;
    private UUID monthly;
    private List<Entry> first;

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        p014 = fx.patient("P-014");
        first = fx.weekEntries(p014, START);
        monthly = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, requested_by)
                VALUES (?, ?, 'MENSUAL', ?, ?, 'PENDIENTE_REVISION', ?)""", monthly, p014, START, START.plusDays(27), UUID.fromString(NURSE));
        jdbc.update("INSERT INTO report_versions (id, report_id, version_no, origin, content) VALUES (?, ?, 1, 'MODELO', ?::jsonb)",
                version, monthly, content().toString());
        jdbc.update("INSERT INTO report_citations (report_version_id, claim_key, entry_id) VALUES (?, 'c1', ?)", version, first.get(0).getId());
        List<UUID> weekly = jdbc.queryForList("SELECT id FROM reports WHERE patient_id = ? AND report_type = 'SEMANAL' AND status = 'APROBADO' ORDER BY period_start", UUID.class, p014);
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO monthly_report_weeks (monthly_report_id, week_no, weekly_report_id) VALUES (?, ?, ?)", monthly, i + 1, weekly.get(i));
        }
    }

    private ObjectNode content() {
        ObjectNode content = mapper.createObjectNode();
        content.putObject("period").put("start", START.toString()).put("end", START.plusDays(27).toString());
        content.put("summary", "El síntoma se mantiene durante el mes.");
        content.putArray("claims").addObject().put("claim_key", "c1").put("text", "Las náuseas aparecen 3 días en la primera semana.")
                .put("criterion", "Primera semana frente a cuarta semana")
                .<ObjectNode>set("entry_ids", mapper.createArrayNode().add(first.get(0).getId().toString()))
                .set("figures", mapper.createArrayNode().add(mapper.createObjectNode().put("key", "symptoms.NAUSEAS.days@2026-09-21").put("value", 3)));
        content.putArray("changes_vs_previous").add("c1");
        content.putArray("recurring_patterns").add("c1");
        var weeks = content.putObject("metrics").putArray("weeks");
        for (int i = 0; i < 4; i++) {
            weeks.addObject().put("week", START.plusWeeks(i).toString()).put("entries", 7).putObject("symptoms").putObject("NAUSEAS").put("days", i == 0 ? 3 : 1);
        }
        content.putArray("weekly_reports");
        content.putArray("insufficient_data");
        content.putNull("out_of_scope");
        return content;
    }

    private String edit(String text, UUID cited) throws Exception {
        ObjectNode claim = mapper.createObjectNode().put("claimKey", "c1").put("text", text).put("criterion", "Primera semana frente a cuarta semana");
        claim.putArray("entryIds").add(cited.toString());
        return mapper.createObjectNode().put("summary", "El síntoma se mantiene durante el mes.")
                .<ObjectNode>set("claims", mapper.createArrayNode().add(claim)).toString();
    }

    private ResultActions putContent(String user, String json) throws Exception {
        return mockMvc.perform(put("/api/reports/" + monthly + "/content").header("X-Demo-User", user).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void theMonthlyIsVisibleToNursingFilterableByTypeAndHiddenFromTheDoctorUntilApproved() throws Exception {
        mockMvc.perform(get("/api/reports?type=MENSUAL&patientId=" + p014).header("X-Demo-User", NURSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].reportType").value("MENSUAL"))
                .andExpect(jsonPath("$[0].periodEnd").value("2026-10-18"));
        mockMvc.perform(get("/api/reports/" + monthly).header("X-Demo-User", DOCTOR)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports?type=MENSUAL").header("X-Demo-User", DOCTOR)).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/reports/" + monthly + "/approve").header("X-Demo-User", DOCTOR).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reports/" + monthly + "/approve").header("X-Demo-User", NURSE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.report.status").value("APROBADO"))
                .andExpect(jsonPath("$.report.reviewedBy").value("Camilo Torres"));

        mockMvc.perform(get("/api/reports/" + monthly).header("X-Demo-User", DOCTOR)).andExpect(status().isOk());
        putContent(NURSE, edit("Las náuseas aparecen 3 días en la primera semana.", first.get(0).getId())).andExpect(status().isConflict());
    }

    @Test
    void anEditCreatesVersionTwoKeepsTheModelVersionAndAcceptsTheFiguresOfAnyWeek() throws Exception {
        // 1 es la cifra de las semanas 2 a 4; 3, la de la primera; 28 y 5, dias de las semanas del mes
        putContent(NURSE, edit("Las náuseas pasan de 3 días en la primera semana a 1 día en las demás.", first.get(0).getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version.versionNo").value(2)).andExpect(jsonPath("$.version.origin").value("ENFERMERIA"));

        mockMvc.perform(get("/api/reports/" + monthly + "/versions/1").header("X-Demo-User", NURSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.claims[0].text").value("Las náuseas aparecen 3 días en la primera semana."));
    }

    @Test
    void aNumberThatIsNotAFigureOfTheMonthIsRejected() throws Exception {
        putContent(NURSE, edit("Las náuseas aparecen 17 días en la primera semana.", first.get(0).getId()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fields['claims[0].text']").exists());
    }

    @Test
    void aCitationAfterTheEndOfTheMonthIsRejected() throws Exception {
        // P-014 tiene registros de la semana del 19-oct, posterior al fin de este mes (18-oct)
        List<Entry> later = fx.weekEntries(p014, LocalDate.of(2026, 10, 19));
        putContent(NURSE, edit("Las náuseas aparecen 3 días en la primera semana.", later.get(0).getId()))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fields['claims[0].entryIds']").exists());
    }

    @Test
    void theSourcesOfTheMonthlyAreRecordedInTheLinkTable() {
        List<String> starts = jdbc.queryForList("""
                SELECT w.period_start::text FROM monthly_report_weeks m JOIN reports w ON w.id = m.weekly_report_id
                WHERE m.monthly_report_id = ? ORDER BY m.week_no""", String.class, monthly);
        assertThat(starts).containsExactly("2026-09-21", "2026-09-28", "2026-10-05", "2026-10-12");
    }
}
