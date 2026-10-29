package com.patientprogressauditor.report.pdf;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportService.CitedEntry;
import com.patientprogressauditor.report.ReportService.Detail;

// Convierte un informe aprobado en lo que se imprime: unos parametros (titulo, aprobador, fecha, id, nota de IA) y una lista
// de bloques que la plantilla informe.jrxml dibuja. Aqui esta el CONTENIDO (el mismo de la hoja de pantalla, RF-24: afirmacion
// con su [n], cifras del codigo, fuentes con el texto original); la plantilla solo pone el formato.
final class ReportPdfBlocks {

    static final String AI_NOTE = "Redactado con asistencia de IA y revisado y aprobado por enfermería. Describe la evolución documentada; "
            + "no es un diagnóstico ni una recomendación de tratamiento.";

    private static final Locale ES = new Locale("es", "CO");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter LONG_NO_YEAR = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);
    private static final DateTimeFormatter WITH_TIME = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ES);

    record Document(Map<String, Object> parameters, List<Map<String, Object>> blocks, String fileName) {
    }

    private final List<Map<String, Object>> blocks = new ArrayList<>();

    private ReportPdfBlocks() {
    }

    static Document of(Detail detail) {
        return new ReportPdfBlocks().build(detail);
    }

    private Document build(Detail d) {
        Report.Type type = d.report().reportType();
        boolean monthly = type == Report.Type.MENSUAL;
        JsonNode c = d.version().content();
        LocalDate start = d.report().periodStart();
        LocalDate end = d.report().periodEnd();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("titulo", "Informe de seguimiento " + (monthly ? "mensual" : "semanal"));
        params.put("subtitulo", "Paciente " + d.report().patientAlias() + ", " + (monthly ? "del " + period(start, end) + " (cuatro semanas)"
                : "semana del " + period(start, end)));
        params.put("aprobadoPor", d.report().reviewedBy() == null ? "—" : d.report().reviewedBy());
        params.put("aprobadoEn", d.report().reviewedAt() == null ? "—" : WITH_TIME.format(d.report().reviewedAt().atZone(BOGOTA)) + " (hora de Bogotá)");
        params.put("informeId", d.report().id().toString());
        params.put("notaIa", AI_NOTE);

        meta("Generado: " + LONG.format(d.report().generatedAt().atZone(BOGOTA)) + ". Versión aprobada: " + d.version().versionNo()
                + (d.version().origin().name().equals("ENFERMERIA") ? " (corregida por enfermería" + (d.version().editedBy() == null ? "" : ": " + d.version().editedBy()) + ")"
                : " (redactada por la IA, sin cambios)") + ".");
        if (monthly) {
            meta("Semanas incluidas: " + c.path("weekly_reports").size() + " informes semanales aprobados.");
        } else if (c.path("period").hasNonNull("compared_with")) {
            LocalDate before = LocalDate.parse(c.path("period").path("compared_with").asText());
            meta("Comparado con: " + period(before, before.plusDays(6)) + (c.path("metrics").path("vs_previous").path("comparable").asBoolean(true) ? "" : " (sin registros)") + ".");
        }

        List<JsonNode> claims = new ArrayList<>();
        c.path("claims").forEach(claims::add);
        List<JsonNode> missing = new ArrayList<>();
        c.path("insufficient_data").forEach(missing::add);

        heading("Resumen de la evolución");
        paragraph(c.path("summary").asText(""));
        for (int i = 0; i < claims.size(); i++) {
            JsonNode claim = claims.get(i);
            paragraph("[" + (i + 1) + "] " + claim.path("text").asText(""));
            note("Criterio de comparación: " + claim.path("criterion").asText(""));
        }
        for (int j = 0; j < missing.size(); j++) {
            paragraph("[" + (claims.size() + j + 1) + "] " + missing.get(j).path("text").asText(""));
        }

        if (monthly) {
            monthlyTable(c.path("metrics").path("weeks"));
            sourcesOfMonthly(c.path("weekly_reports"));
        } else {
            weeklyTable(c.path("metrics"));
        }

        int pattern = patternIndex(c, claims);
        if (pattern >= 0) {
            heading("Patrón a destacar");
            paragraph(claims.get(pattern).path("text").asText("") + " [" + (pattern + 1) + "]");
        }

        heading("Fuentes (registros originales)");
        for (int i = 0; i < claims.size(); i++) {
            List<CitedEntry> cited = d.citations().getOrDefault(claims.get(i).path("claim_key").asText(), List.of());
            StringBuilder line = new StringBuilder("[" + (i + 1) + "] ");
            for (int k = 0; k < cited.size(); k++) {
                line.append(k == 0 ? "" : "  /  ").append(shortDay(cited.get(k).date())).append(": “").append(cited.get(k).body()).append("”");
            }
            block("S", line.toString());
        }
        for (int j = 0; j < missing.size(); j++) {
            block("S", "[" + (claims.size() + j + 1) + "] " + missingSource(missing.get(j)));
        }

        String kind = monthly ? "mensual" : "semanal";
        return new Document(params, blocks, "informe-" + kind + "-" + d.report().patientAlias() + "-" + start + ".pdf");
    }

    // ---- tablas ----

    private void weeklyTable(JsonNode m) {
        heading("Cifras de la semana");
        JsonNode versus = m.path("vs_previous");
        boolean comparable = versus.path("comparable").asBoolean(false);
        row("TH3", "Síntoma", "Período de comparación", "Esta semana");
        row("T3", "Registros", comparable ? String.valueOf(versus.path("previous_entries").asInt()) : "Sin datos", String.valueOf(m.path("entries").asInt()));
        Set<String> names = new LinkedHashSet<>();
        m.path("symptoms").fieldNames().forEachRemaining(names::add);
        versus.path("days").fieldNames().forEachRemaining(names::add);
        for (String code : names) {
            JsonNode now = m.path("symptoms").path(code);
            int days = now.path("days").asInt(0);
            int times = now.path("occurrences").asInt(0);
            int before = versus.path("days").path(code).path(0).asInt(0);
            row("T3", label(code), comparable ? days(before) : "Sin datos",
                    days > 0 ? days(days) + (times > days ? ", " + times + " veces" : "") : "No registrado");
        }
        note("Cifras calculadas por el sistema a partir de los registros, no por la IA. Solo se listan los síntomas que aparecieron en alguno de los dos períodos.");
    }

    private void monthlyTable(JsonNode weeks) {
        heading("Evolución semana a semana");
        List<JsonNode> list = new ArrayList<>();
        weeks.forEach(list::add);
        String[] head = new String[5];
        head[0] = "Síntoma";
        for (int i = 0; i < 4; i++) {
            head[i + 1] = i < list.size() ? "Semana " + (i + 1) + " (" + shortDate(LocalDate.parse(list.get(i).path("week").asText())) + ")" : "";
        }
        row("TH5", head);
        String[] entries = new String[5];
        entries[0] = "Registros";
        for (int i = 0; i < 4; i++) {
            entries[i + 1] = i < list.size() ? String.valueOf(list.get(i).path("entries").asInt()) : "";
        }
        row("T5", entries);
        Set<String> names = new LinkedHashSet<>();
        list.forEach(w -> w.path("symptoms").fieldNames().forEachRemaining(names::add));
        for (String code : names) {
            String[] cells = new String[5];
            cells[0] = label(code);
            for (int i = 0; i < 4; i++) {
                int d = i < list.size() ? list.get(i).path("symptoms").path(code).path("days").asInt(0) : 0;
                cells[i + 1] = days(d);
            }
            row("T5", cells);
        }
        note("Cifras calculadas por el sistema a partir de los registros, no por la IA. Solo se listan los síntomas que aparecieron en alguna de las cuatro semanas.");
    }

    private void sourcesOfMonthly(JsonNode sources) {
        if (sources.isEmpty()) {
            return;
        }
        heading("Informes semanales de origen");
        for (JsonNode s : sources) {
            LocalDate week = LocalDate.parse(s.path("week_start").asText());
            paragraph("Semana " + s.path("week_no").asInt() + " (" + period(week, week.plusDays(6)) + "): " + s.path("summary").asText(""));
        }
    }

    // ---- bloques ----

    private void heading(String text) {
        block("H", text);
    }

    private void paragraph(String text) {
        block("P", text);
    }

    private void note(String text) {
        block("N", text);
    }

    private void meta(String text) {
        block("M", text);
    }

    private void block(String kind, String a) {
        row(kind, a);
    }

    private void row(String kind, String... cells) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        String[] names = {"a", "b", "c", "d", "e"};
        for (int i = 0; i < names.length; i++) {
            row.put(names[i], i < cells.length && cells[i] != null ? cells[i] : "");
        }
        blocks.add(row);
    }

    // ---- ayudas ----

    private static int patternIndex(JsonNode c, List<JsonNode> claims) {
        String key = c.path("recurring_patterns").path(0).asText("");
        for (int i = 0; i < claims.size(); i++) {
            if (!key.isEmpty() && claims.get(i).path("claim_key").asText().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static String missingSource(JsonNode item) {
        if ("DIAS_SIN_REGISTRO".equals(item.path("kind").asText()) && item.path("dates").size() > 0) {
            List<String> days = new ArrayList<>();
            item.path("dates").forEach(d -> days.add(shortDay(LocalDate.parse(d.asText())) + ": sin registro"));
            return String.join("  /  ", days);
        }
        return "Registros guardados solo con el texto: sus síntomas no se contaron";
    }

    private static String label(String code) {
        try {
            return Symptom.valueOf(code).label();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private static String days(int n) {
        return n > 0 ? n + (n == 1 ? " día" : " días") : "No registrado";
    }

    static String period(LocalDate from, LocalDate to) {
        if (from.getMonth() == to.getMonth() && from.getYear() == to.getYear()) {
            return from.getDayOfMonth() + " al " + LONG.format(to);
        }
        return LONG_NO_YEAR.format(from) + " al " + LONG.format(to);
    }

    // «mar 20 oct»: el dia de la semana abreviado, el numero y el mes abreviado.
    private static String shortDay(LocalDate d) {
        return dow(d.getDayOfWeek()) + " " + shortDate(d);
    }

    private static String shortDate(LocalDate d) {
        return d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, ES).replace(".", "");
    }

    private static String dow(DayOfWeek d) {
        return d.getDisplayName(TextStyle.SHORT, ES).replace(".", "");
    }
}
