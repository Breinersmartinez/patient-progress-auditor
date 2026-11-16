package com.patientprogressauditor.consultation.pdf;

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
import com.patientprogressauditor.consultation.ConsultationService.CitedEntry;
import com.patientprogressauditor.consultation.ConsultationService.ConsultationDetail;
import com.patientprogressauditor.entry.Symptom;

// Convierte una consulta del medico en lo que se imprime (D31, RF-46, RN-12): los mismos bloques del informe semanal
// (ReportPdfBlocks), sin aprobacion ni mensual, porque la consulta no tiene ninguna de las dos.
final class ConsultationPdfBlocks {

    static final String AI_NOTE = "Generado con IA a partir de los registros del período. No pasó por la revisión de enfermería "
            + "ni forma parte del historial oficial. No contiene diagnóstico ni recomendación de tratamiento.";

    private static final Locale ES = new Locale("es", "CO");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter LONG_NO_YEAR = DateTimeFormatter.ofPattern("d 'de' MMMM", ES);
    private static final DateTimeFormatter WITH_TIME = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", ES);

    record Document(Map<String, Object> parameters, List<Map<String, Object>> blocks, String fileName) {
    }

    private final List<Map<String, Object>> blocks = new ArrayList<>();

    private ConsultationPdfBlocks() {
    }

    static Document of(ConsultationDetail d, String doctorName) {
        return new ConsultationPdfBlocks().build(d, doctorName);
    }

    private Document build(ConsultationDetail d, String doctorName) {
        JsonNode c = d.content();
        LocalDate start = d.periodStart();
        LocalDate end = d.periodEnd();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("titulo", "Informe de consulta");
        params.put("subtitulo", "Paciente " + d.patientAlias() + ", período del " + period(start, end));
        params.put("armadaPor", doctorName == null ? "—" : doctorName);
        params.put("armadaEn", WITH_TIME.format(d.createdAt().atZone(BOGOTA)) + " (hora de Bogotá)");
        params.put("consultaId", d.id().toString());
        params.put("notaIa", AI_NOTE);

        if (c.path("period").hasNonNull("compared_with")) {
            LocalDate before = d.comparisonStart();
            LocalDate beforeEnd = d.comparisonEnd();
            meta("Comparado con: " + period(before, beforeEnd)
                    + (c.path("metrics").path("vs_previous").path("comparable").asBoolean(true) ? "" : " (sin registros)") + ".");
        } else {
            meta("Sin período de comparación.");
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

        periodTable(c.path("metrics"));

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

        return new Document(params, blocks, "consulta-" + d.patientAlias() + "-" + start + ".pdf");
    }

    private void periodTable(JsonNode m) {
        heading("Cifras del período");
        JsonNode versus = m.path("vs_previous");
        boolean comparable = versus.path("comparable").asBoolean(false);
        row("TH3", "Síntoma", "Período de comparación", "Este período");
        row("T3", "Registros", comparable ? String.valueOf(versus.path("previous_entries").asInt()) : "Sin datos", String.valueOf(m.path("entries").asInt()));
        Set<String> names = new LinkedHashSet<>();
        m.path("symptoms").fieldNames().forEachRemaining(names::add);
        versus.path("days").fieldNames().forEachRemaining(names::add);
        for (String code : names) {
            JsonNode now = m.path("symptoms").path(code);
            int days = now.path("days").asInt(0);
            int times = now.path("occurrences").asInt(0);
            int before = versus.path("days").path(code).path(0).asInt(0);
            row("T3", label(code, now), comparable ? days(before) : "Sin datos",
                    days > 0 ? days(days) + (times > days ? ", " + times + " veces" : "") : "No registrado");
        }
        note("Cifras calculadas por el sistema a partir de los registros, no por la IA. Solo se listan los síntomas que aparecieron en alguno de los dos períodos.");
    }

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

    private static String label(String code, JsonNode metrics) {
        try {
            return Symptom.of(code, metrics == null ? null : metrics.path("label").asText(null)).label();
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
