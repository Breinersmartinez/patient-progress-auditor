package com.patientprogressauditor.report;

import static com.patientprogressauditor.report.ReportFixtures.DOCTOR;
import static com.patientprogressauditor.report.ReportFixtures.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;

// El PDF de un informe aprobado (RF-37, CU-09, D13: JasperReports). El criterio del requisito: el texto extraido del PDF
// contiene la aprobacion. Aqui se extrae el texto de un PDF REAL (con OpenPDF, que ya viene con Jasper) y se comprueba lo que
// el documento dice, no solo que tenga bytes.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class ReportPdfApiTest {

    private static final LocalDate W = LocalDate.of(2026, 10, 19);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;

    private ReportFixtures fx;
    private UUID p033;

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        p033 = fx.patient("P-033");
    }

    private byte[] pdf(String user, UUID report) throws Exception {
        return mockMvc.perform(get("/api/reports/" + report + "/pdf").header("X-Demo-User", user)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn().getResponse().getContentAsByteArray();
    }

    // Todo el texto del PDF, con los saltos de linea y los espacios multiples reducidos a uno.
    private static String text(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder all = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                all.append(extractor.getTextFromPage(page)).append(' ');
            }
            return all.toString().replaceAll("\\s+", " ");
        } finally {
            reader.close();
        }
    }

    // El extractor une las celdas de una tabla sin espacio («comparaciónEsta semana»): para las tablas se compara sin espacios.
    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static int pages(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }

    @Test
    void anApprovedWeeklyReportBecomesAPdfThatSaysWhoApprovedItWhenAndWhichReportItIs() throws Exception {
        UUID report = fx.report(p033, W, "APROBADO");
        List<Entry> week = fx.weekEntries(p033, W);

        var response = mockMvc.perform(get("/api/reports/" + report + "/pdf").header("X-Demo-User", NURSE)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("informe-semanal-P-033-2026-10-19.pdf")))
                .andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();
        String text = text(bytes);

        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        assertThat(text).contains("Informe de seguimiento semanal").contains("Paciente P-033, semana del 19 al 25 de octubre de 2026");
        // RF-37: aprobador, fecha, id del informe y la nota de asistencia de IA
        assertThat(text).contains("Aprobado por: Camilo Torres").contains("Fecha de aprobación:").contains("(hora de Bogotá)")
                .contains(report.toString()).contains("Redactado con asistencia de IA y revisado y aprobado por enfermería")
                .contains("no es un diagnóstico ni una recomendación de tratamiento");
        // El contenido de la hoja: resumen, afirmaciones con su [n] y su criterio, y las fuentes con el texto ORIGINAL
        assertThat(text).contains("La semana tiene registros todos los días y las náuseas aparecen en 3 días.")
                .contains("[1] Las náuseas se registraron en 3 días de la semana.")
                .contains("Criterio de comparación: Días con el síntoma esta semana frente a la semana anterior")
                .contains("[2] El ánimo se describe estable en los registros citados.")
                .contains("Fuentes (registros originales)").contains("“" + week.get(0).getBody() + "”").contains("“" + week.get(1).getBody() + "”");
        // Las cifras que calculo el codigo
        assertThat(text).contains("Cifras de la semana").contains("Náuseas");
        assertThat(pages(bytes)).isEqualTo(1);
    }

    @Test
    void aSeededApprovedReportPrintsWithItsRealFiguresAndSources() throws Exception {
        UUID p014 = fx.patient("P-014");
        UUID report = jdbc.queryForObject("SELECT id FROM reports WHERE patient_id = ? AND period_start = '2026-10-12' AND status = 'APROBADO'", UUID.class, p014);

        String text = text(pdf(DOCTOR, report));

        assertThat(text).contains("Paciente P-014, semana del 12 al 18 de octubre de 2026").contains("Aprobado por: Marcela Rojas")
                .contains("Dolor de hueso persiste: 1 día esta semana y 1 día la semana anterior.")
                .contains("Patrón a destacar");
        assertThat(squash(text)).contains(squash("Síntoma Período de comparación Esta semana Registros 7 7 Dolor de hueso 1 día 1 día Vómito 1 día No registrado"));
    }

    @Test
    void onlyAnApprovedReportIsPrintedAndWhatTheRoleCannotSeeDoesNotExist() throws Exception {
        for (String state : List.of("PENDIENTE_REVISION", "INCOMPLETO", "RECHAZADO")) {
            UUID report = fx.report(p033, state.equals("INCOMPLETO") ? W.minusWeeks(1) : state.equals("RECHAZADO") ? W.minusWeeks(2) : W, state);
            mockMvc.perform(get("/api/reports/" + report + "/pdf").header("X-Demo-User", NURSE)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Solo se puede imprimir un informe aprobado. Este está "
                            + (state.equals("PENDIENTE_REVISION") ? "pendiente de revisión" : state.toLowerCase()) + "."));
            // El medico no sabe que existe
            mockMvc.perform(get("/api/reports/" + report + "/pdf").header("X-Demo-User", DOCTOR)).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/reports/" + UUID.randomUUID() + "/pdf").header("X-Demo-User", NURSE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/reports/" + UUID.randomUUID() + "/pdf")).andExpect(status().isUnauthorized());
    }

    @Test
    void theDoctorGetsTheSamePdfOnceApproved() throws Exception {
        UUID report = fx.report(p033, W, "PENDIENTE_REVISION");
        mockMvc.perform(get("/api/reports/" + report + "/pdf").header("X-Demo-User", DOCTOR)).andExpect(status().isNotFound());

        mockMvc.perform(post("/api/reports/" + report + "/approve").header("X-Demo-User", NURSE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(text(pdf(DOCTOR, report))).contains("Aprobado por: Camilo Torres").contains(report.toString());
    }

    @Test
    void thePdfPrintsTheVersionThatWasApprovedNotTheOneTheModelWrote() throws Exception {
        UUID report = fx.report(p033, W, "PENDIENTE_REVISION");
        List<Entry> week = fx.weekEntries(p033, W);
        ObjectNode claim = mapper.createObjectNode().put("claimKey", "c1").put("text", "Las náuseas se registraron en 3 días, según enfermería.")
                .put("criterion", "Días con el síntoma esta semana frente a la semana anterior");
        claim.putArray("entryIds").add(week.get(0).getId().toString());
        String edit = mapper.createObjectNode().put("summary", "Resumen corregido por enfermería.")
                .<ObjectNode>set("claims", mapper.createArrayNode().add(claim)).toString();
        mockMvc.perform(put("/api/reports/" + report + "/content").header("X-Demo-User", NURSE).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/reports/" + report + "/approve").header("X-Demo-User", NURSE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        String text = text(pdf(NURSE, report));

        assertThat(text).contains("Resumen corregido por enfermería.").contains("según enfermería").contains("Versión aprobada: 2 (corregida por enfermería: Camilo Torres)")
                .doesNotContain("La semana tiene registros todos los días");
    }

    @Test
    void aMonthlyReportPrintsTheEvolutionTableAndItsFourSourceReports() throws Exception {
        UUID p014 = fx.patient("P-014");
        LocalDate start = LocalDate.of(2026, 9, 21);
        List<UUID> weekly = jdbc.queryForList("SELECT id FROM reports WHERE patient_id = ? AND report_type = 'SEMANAL' AND status = 'APROBADO' ORDER BY period_start", UUID.class, p014);
        UUID monthly = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        Entry cited = fx.weekEntries(p014, start).get(0);
        jdbc.update("""
                INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, requested_by, reviewed_by, reviewed_at, approved_version_no)
                VALUES (?, ?, 'MENSUAL', ?, ?, 'APROBADO', ?, ?, now(), 1)""", monthly, p014, start, start.plusDays(27), UUID.fromString(NURSE), UUID.fromString(NURSE));
        jdbc.update("INSERT INTO report_versions (id, report_id, version_no, origin, content) VALUES (?, ?, 1, 'MODELO', ?::jsonb)", version, monthly, monthlyContent(start, weekly, cited).toString());
        jdbc.update("INSERT INTO report_citations (report_version_id, claim_key, entry_id) VALUES (?, 'c1', ?)", version, cited.getId());

        byte[] bytes = pdf(DOCTOR, monthly);
        String text = text(bytes);

        assertThat(text).contains("Informe de seguimiento mensual").contains("Paciente P-014, del 21 de septiembre al 18 de octubre de 2026 (cuatro semanas)")
                .contains("Semanas incluidas: 4 informes semanales aprobados");
        assertThat(text).contains("Evolución semana a semana").doesNotContain("Cifras de la semana");
        assertThat(squash(text)).contains(squash("Síntoma Semana 1 (21 sep) Semana 2 (28 sep) Semana 3 (5 oct) Semana 4 (12 oct) Registros 7 7 6 7 Náuseas 2 días No registrado 3 días No registrado"));
        assertThat(text).contains("Informes semanales de origen").contains("Semana 1 (21 al 27 de septiembre de 2026): Náuseas en dos días.")
                .contains("Semana 4 (12 al 18 de octubre de 2026): Sin síntomas.");
        assertThat(text).contains("[1] Las náuseas pasan de 2 días en la primera semana a 0 en la segunda.").contains("“" + cited.getBody() + "”");
        assertThat(bytes).isNotEmpty();
    }

    private ObjectNode monthlyContent(LocalDate start, List<UUID> weekly, Entry cited) {
        ObjectNode content = mapper.createObjectNode();
        ArrayNode starts = content.putObject("period").put("start", start.toString()).put("end", start.plusDays(27).toString()).putArray("weeks");
        for (int i = 0; i < 4; i++) {
            starts.add(start.plusWeeks(i).toString());
        }
        content.put("summary", "Las náuseas aparecen y desaparecen a lo largo del mes.");
        content.putArray("claims").addObject().put("claim_key", "c1").put("text", "Las náuseas pasan de 2 días en la primera semana a 0 en la segunda.")
                .put("criterion", "Primera semana frente a segunda semana")
                .<ObjectNode>set("entry_ids", mapper.createArrayNode().add(cited.getId().toString())).set("figures", mapper.createArrayNode());
        content.putArray("changes_vs_previous");
        content.putArray("recurring_patterns").add("c1");
        ArrayNode weeks = content.putObject("metrics").putArray("weeks");
        int[] entriesPerWeek = {7, 7, 6, 7};
        int[] nausea = {2, 0, 3, 0};
        for (int i = 0; i < 4; i++) {
            ObjectNode w = weeks.addObject().put("week", start.plusWeeks(i).toString()).put("entries", entriesPerWeek[i]);
            ObjectNode symptoms = w.putObject("symptoms");
            if (nausea[i] > 0) {
                symptoms.putObject("NAUSEAS").put("days", nausea[i]);
            }
        }
        String[] summaries = {"Náuseas en dos días.", "Sin síntomas.", "Náuseas vuelven.", "Sin síntomas."};
        ArrayNode sources = content.putArray("weekly_reports");
        for (int i = 0; i < 4; i++) {
            sources.addObject().put("week_no", i + 1).put("week_start", start.plusWeeks(i).toString()).put("report_id", weekly.get(i).toString()).put("summary", summaries[i]);
        }
        content.putArray("insufficient_data");
        content.putNull("out_of_scope");
        return content;
    }

    @Test
    void aLongReportFlowsOntoASecondPageWithoutCuttingTextAndKeepsAccentsAndSpecialCharacters() throws Exception {
        UUID report = fx.report(p033, W, "APROBADO");
        List<Entry> week = fx.weekEntries(p033, W);
        String filler = "Muchas palabras para ocupar espacio, con acentos y eñes: ñandú, canción, ¿cómo sigue? “comillas” <etiqueta> & $P{titulo} [x] ";
        ObjectNode content = mapper.createObjectNode();
        content.putObject("period").put("start", W.toString()).put("end", W.plusDays(6).toString()).put("compared_with", W.minusDays(7).toString());
        content.put("summary", "Resumen largo. " + filler.repeat(5).substring(0, 540));
        ArrayNode claims = content.putArray("claims");
        ArrayNode ids = mapper.createArrayNode();
        for (int i = 0; i < 6; i++) {
            ids.add(week.get(i).getId().toString());
        }
        for (int i = 1; i <= 8; i++) {
            claims.addObject().put("claim_key", "c" + i).put("text", ("Afirmación " + i + ". " + filler.repeat(3)).substring(0, 318) + "FIN" + i)
                    .put("criterion", "Criterio de la afirmación " + i).<ObjectNode>set("entry_ids", ids.deepCopy()).set("figures", mapper.createArrayNode());
        }
        content.putArray("changes_vs_previous");
        content.putArray("recurring_patterns");
        content.putObject("metrics").put("week", W.toString()).put("entries", 7).putObject("symptoms");
        content.putArray("insufficient_data");
        content.putNull("out_of_scope");
        jdbc.update("UPDATE report_versions SET content = ?::jsonb WHERE report_id = ?", content.toString(), report);
        for (int i = 1; i <= 8; i++) {
            for (int k = 0; k < 6; k++) {
                jdbc.update("INSERT INTO report_citations (report_version_id, claim_key, entry_id) SELECT id, ?, ? FROM report_versions WHERE report_id = ? ON CONFLICT DO NOTHING",
                        "c" + i, week.get(k).getId(), report);
            }
        }

        byte[] bytes = pdf(NURSE, report);
        String text = text(bytes);

        assertThat(pages(bytes)).isGreaterThanOrEqualTo(2);
        for (int i = 1; i <= 8; i++) {
            assertThat(text).contains("FIN" + i);
        }
        assertThat(text).contains("ñandú, canción, ¿cómo sigue?").contains("“comillas”").contains("<etiqueta> & $P{titulo} [x]");
        assertThat(squash(text)).contains(squash("Página 1 de 2")).contains(squash("Página 2 de 2"));
    }
}
