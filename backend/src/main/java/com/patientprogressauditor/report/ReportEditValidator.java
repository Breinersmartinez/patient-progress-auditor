package com.patientprogressauditor.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.DiagnosisGuard;
import com.patientprogressauditor.entry.Entry;

// Valida lo que enfermeria edita y arma la version nueva. Reglas del borrador del agente (DraftValidator) adaptadas a una
// persona: las citas son registros reales del mismo paciente y no posteriores a la semana (RN-05); el texto no diagnostica
// ni receta (RN-03); y las CIFRAS no las cambia nadie: un numero del texto debe existir entre las cifras que el codigo
// guardo en el informe (RN-04) o ser un dia de las fechas del periodo o de las entradas citadas.
@Component
public class ReportEditValidator {

    public static final int MAX_CLAIMS = 8;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    public record ClaimEdit(String claimKey, String text, String criterion, List<UUID> entryIds) {
    }

    public record Edit(String summary, List<ClaimEdit> claims) {
    }

    public record Result(Map<String, String> problems, ObjectNode content, Map<String, List<UUID>> citations) {

        public boolean valid() {
            return problems.isEmpty();
        }
    }

    private final ObjectMapper mapper;

    public ReportEditValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // `previous`: el contenido de la version vigente; `entries`: los registros citados que existen, por id.
    public Result check(Report report, JsonNode previous, Edit edit, Map<UUID, Entry> entries) {
        Map<String, String> problems = new LinkedHashMap<>();
        Map<String, List<UUID>> citations = new LinkedHashMap<>();
        ObjectNode content = previous.deepCopy();

        String summary = edit.summary() == null ? "" : edit.summary().strip();
        if (summary.isEmpty() || summary.length() > 600) {
            problems.put("summary", "El resumen debe tener de 1 a 600 caracteres.");
        }
        forbidden("summary", summary, problems);

        List<ClaimEdit> claims = edit.claims() == null ? List.of() : edit.claims();
        if (claims.isEmpty() || claims.size() > MAX_CLAIMS) {
            problems.put("claims", "El informe debe tener de 1 a " + MAX_CLAIMS + " afirmaciones.");
        }
        Map<String, JsonNode> before = new HashMap<>();
        previous.path("claims").forEach(c -> before.put(c.path("claim_key").asText(), c));

        Set<Integer> allowed = allowedNumbers(report, previous);
        Set<String> used = new HashSet<>();
        claims.forEach(c -> {
            if (c.claimKey() != null && !c.claimKey().isBlank()) {
                used.add(c.claimKey());
            }
        });
        ArrayNode out = mapper.createArrayNode();
        int index = 0;
        int next = 1;
        for (ClaimEdit claim : claims) {
            String at = "claims[" + index + "]";
            String key = claim.claimKey() == null ? "" : claim.claimKey().strip();
            if (key.isEmpty()) {
                while (used.contains("c" + next)) {
                    next++;
                }
                key = "c" + next;
                used.add(key);
            } else if (!KEY.matcher(key).matches()) {
                problems.put(at + ".claimKey", "El identificador de la afirmación no es válido.");
            }
            if (citations.containsKey(key)) {
                problems.put(at + ".claimKey", "Hay dos afirmaciones con el mismo identificador.");
            }
            String text = claim.text() == null ? "" : claim.text().strip();
            String criterion = claim.criterion() == null ? "" : claim.criterion().strip();
            if (text.isEmpty() || text.length() > 320) {
                problems.put(at + ".text", "El texto de la afirmación debe tener de 1 a 320 caracteres.");
            }
            if (criterion.isEmpty() || criterion.length() > 220) {
                problems.put(at + ".criterion", "El criterio de comparación debe tener de 1 a 220 caracteres.");
            }
            forbidden(at + ".text", text, problems);
            forbidden(at + ".criterion", criterion, problems);

            List<UUID> cited = new ArrayList<>();
            List<UUID> refs = claim.entryIds() == null ? List.of() : claim.entryIds();
            if (refs.isEmpty() || refs.size() > 6) {
                problems.put(at + ".entryIds", "Cada afirmación debe citar de 1 a 6 registros.");
            }
            Set<Integer> numbers = new HashSet<>(allowed);
            for (UUID id : new LinkedHashSet<>(refs)) {
                Entry entry = entries.get(id);
                if (entry == null || !entry.getPatientId().equals(report.getPatientId())) {
                    problems.put(at + ".entryIds", "Una de las citas no es un registro de este paciente.");
                } else if (entry.getEntryDate().isAfter(report.getPeriodEnd())) {
                    problems.put(at + ".entryIds", "No se puede citar un registro posterior al período del informe.");
                } else {
                    cited.add(id);
                    numbers.add(entry.getEntryDate().getDayOfMonth());
                }
            }
            Map<String, String> texts = new LinkedHashMap<>();
            texts.put(at + ".text", text);
            texts.put(at + ".criterion", criterion);
            texts.forEach((field, value) -> {
                Matcher m = NUMBER.matcher(value);
                while (m.find()) {
                    if (!backed(m.group(), numbers)) {
                        problems.putIfAbsent(field, "El número " + m.group()
                                + " no es ninguna de las cifras que calculó el sistema para este informe. Quítelo o use el valor correcto.");
                        break;
                    }
                }
            });
            citations.put(key, cited);

            ObjectNode node = out.addObject().put("claim_key", key).put("text", text).put("criterion", criterion);
            ArrayNode ids = node.putArray("entry_ids");
            cited.forEach(id -> ids.add(id.toString()));
            // Las cifras que respaldo el agente valen para el texto que escribio; si la persona lo reescribe ya no se sabe
            // a que afirmacion corresponden, y no se arrastran.
            JsonNode old = before.get(key);
            boolean unchanged = old != null && old.path("text").asText().equals(text) && old.path("criterion").asText().equals(criterion);
            node.set("figures", unchanged && old.path("figures").isArray() ? old.path("figures").deepCopy() : mapper.createArrayNode());
            index++;
        }
        if (!problems.isEmpty()) {
            return new Result(problems, content, citations);
        }
        content.put("summary", summary);
        content.set("claims", out);
        content.set("changes_vs_previous", keep(previous.path("changes_vs_previous"), citations.keySet()));
        content.set("recurring_patterns", keep(previous.path("recurring_patterns"), citations.keySet()));
        return new Result(problems, content, citations);
    }

    private ArrayNode keep(JsonNode keys, Set<String> existing) {
        ArrayNode out = mapper.createArrayNode();
        keys.forEach(k -> {
            if (existing.contains(k.asText())) {
                out.add(k.asText());
            }
        });
        return out;
    }

    private static boolean backed(String digits, Set<Integer> allowed) {
        return digits.length() > 9 || allowed.contains(Integer.parseInt(digits));
    }

    // Las cifras del informe (las que guardo el codigo), los dias de las dos semanas y el anio.
    private static Set<Integer> allowedNumbers(Report report, JsonNode content) {
        Set<Integer> numbers = new HashSet<>();
        collect(content.path("metrics"), numbers);
        LocalDate start = report.getPeriodStart();
        for (LocalDate d : List.of(start, report.getPeriodEnd(), start.minusDays(7), start.minusDays(1))) {
            numbers.add(d.getDayOfMonth());
        }
        numbers.add(start.getYear());
        // En el mensual tambien se pueden nombrar los limites de cada una de las cuatro semanas.
        if (report.getReportType() == Report.Type.MENSUAL) {
            for (int i = 0; i < 4; i++) {
                numbers.add(start.plusWeeks(i).getDayOfMonth());
                numbers.add(start.plusWeeks(i).plusDays(6).getDayOfMonth());
            }
        }
        return numbers;
    }

    private static void collect(JsonNode node, Set<Integer> out) {
        if (node.isInt() || node.isLong()) {
            out.add(node.intValue());
        } else if (node.isContainerNode()) {
            node.forEach(child -> collect(child, out));
        }
    }

    private static void forbidden(String where, String text, Map<String, String> problems) {
        DiagnosisGuard.forbiddenIn(text).ifPresent(word -> problems.putIfAbsent(where, "Contiene «" + word
                + "»: el informe no diagnostica ni recomienda tratamiento ni dosis. Describa solo lo que los registros documentan."));
    }
}
