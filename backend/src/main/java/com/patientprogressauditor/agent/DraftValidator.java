package com.patientprogressauditor.agent;

import java.time.LocalDate;
import java.util.ArrayList;
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

// Valida el borrador que propone el modelo ANTES de guardarlo (RF-11, RF-12, RF-13, RF-15, RN-03, RN-04, RN-05). Cada
// problema se dice en espanol y de forma que el modelo pueda corregirlo: vuelve a el como observacion de la herramienta.
@Component
public class DraftValidator {

    public static final int MAX_CLAIMS = 8;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    public record Checked(List<String> problems, ObjectNode model, Map<String, List<UUID>> citations) {

        public boolean valid() {
            return problems.isEmpty();
        }
    }

    private final FigureResolver figures;
    private final ObjectMapper mapper;

    public DraftValidator(FigureResolver figures, ObjectMapper mapper) {
        this.figures = figures;
        this.mapper = mapper;
    }

    public Checked check(JsonNode draft, AgentRunContext context) {
        List<String> problems = new ArrayList<>();
        Map<String, List<UUID>> citations = new LinkedHashMap<>();
        Set<Integer> backed = new HashSet<>();
        ObjectNode model = mapper.createObjectNode();

        String summary = draft.path("summary").asText("");
        if (summary.isBlank() || summary.length() > 600) {
            problems.add("summary debe ser un texto de 1 a 600 caracteres.");
        }
        forbidden("summary", summary, problems);
        model.put("summary", summary);

        JsonNode claims = draft.path("claims");
        ArrayNode out = model.putArray("claims");
        if (!claims.isArray() || claims.isEmpty() || claims.size() > MAX_CLAIMS) {
            problems.add("claims debe ser una lista de 1 a " + MAX_CLAIMS + " afirmaciones. Si no hubo síntomas, escriba UNA que lo diga («No se registraron síntomas esta semana»), "
                    + "cite 2 o 3 registros de la semana y respáldela con figures [{\"key\":\"total_events\",\"value\":0}].");
        } else {
            Set<String> seen = new HashSet<>();
            for (JsonNode claim : claims) {
                checkClaim(claim, context, seen, problems, citations, out, backed);
            }
        }
        checkSummaryNumbers(summary, context, backed, problems);
        model.set("changes_vs_previous", keys(draft.path("changes_vs_previous"), "changes_vs_previous", citations.keySet(), problems));
        model.set("recurring_patterns", keys(draft.path("recurring_patterns"), "recurring_patterns", citations.keySet(), problems));
        return new Checked(problems, model, citations);
    }

    private void checkClaim(JsonNode claim, AgentRunContext context, Set<String> seen, List<String> problems,
            Map<String, List<UUID>> citations, ArrayNode out, Set<Integer> backed) {
        String key = claim.path("claim_key").asText("");
        String at = key.isBlank() ? "una afirmación" : "la afirmación «" + key + "»";
        if (!KEY.matcher(key).matches()) {
            problems.add("claim_key debe ser un identificador corto (letras, números, guion) y todas las afirmaciones deben traerlo.");
            return;
        }
        if (!seen.add(key)) {
            problems.add("claim_key «" + key + "» está repetido.");
            return;
        }
        String text = claim.path("text").asText("");
        String criterion = claim.path("criterion").asText("");
        if (text.isBlank() || text.length() > 320) {
            problems.add(at + ": text debe tener de 1 a 320 caracteres.");
        }
        if (criterion.isBlank() || criterion.length() > 220) {
            problems.add(at + ": criterion (con qué se comparó) debe tener de 1 a 220 caracteres.");
        }
        forbidden(at + " (text)", text, problems);
        forbidden(at + " (criterion)", criterion, problems);

        // Citas: solo alias que el agente leyo en esta ejecucion (RN-05, RF-12).
        List<UUID> cited = new ArrayList<>();
        ObjectNode node = out.addObject().put("claim_key", key).put("text", text).put("criterion", criterion);
        ArrayNode ids = node.putArray("entry_ids");
        JsonNode refs = claim.path("entry_ids");
        if (!refs.isArray() || refs.isEmpty() || refs.size() > 6) {
            problems.add(at + ": entry_ids debe citar de 1 a 6 registros (use los alias e1, e2… que le devolvieron las herramientas). "
                    + "Si trata de algo que NO ocurrió esta semana (un síntoma que desapareció), cite los registros de la semana anterior donde ocurrió: léalos con get_week_entries.");
        } else {
            for (JsonNode ref : refs) {
                UUID entry = context.entryOf(ref.asText(""));
                if (entry == null) {
                    problems.add(at + ": la cita «" + ref.asText() + "» no existe. Solo puede citar alias que le devolvieron get_week_entries o get_symptom_history en esta tarea.");
                } else if (cited.add(entry)) {
                    ids.add(entry.toString());
                }
            }
        }
        citations.put(key, cited);

        // Cifras: cada una debe existir y coincidir con el calculo del codigo (RF-13).
        Set<Integer> allowed = baseNumbers(context);
        cited.forEach(e -> allowed.add(context.dateOf(e).getDayOfMonth()));
        ArrayNode figureOut = node.putArray("figures");
        JsonNode figs = claim.path("figures");
        if (figs.isArray()) {
            for (JsonNode fig : figs) {
                String figKey = fig.path("key").asText("");
                if (!fig.path("value").isInt()) {
                    problems.add(at + ": la cifra «" + figKey + "» debe traer value como número entero.");
                    continue;
                }
                FigureResolver.Resolution r = figures.resolveOrExplain(context, figKey, fig.path("value").asInt());
                if (r.figure().isEmpty()) {
                    problems.add(at + ": " + r.problem() + ".");
                    continue;
                }
                int actual = r.figure().get().value();
                if (actual != fig.path("value").asInt()) {
                    problems.add(at + ": la cifra «" + figKey + "» vale " + actual + " según el sistema y usted escribió "
                            + fig.path("value").asInt() + ". Use el valor que devolvió get_week_metrics.");
                    continue;
                }
                allowed.add(actual);
                backed.add(actual);
                allowed.add(r.figure().get().week().getDayOfMonth());
                allowed.add(r.figure().get().week().plusDays(6).getDayOfMonth());
                figureOut.addObject().put("key", r.figure().get().key()).put("value", actual);
            }
        }
        // Todo numero que el texto nombra debe tener respaldo: una cifra, o el dia de una fecha citada o de la semana.
        for (String field : List.of(text, criterion)) {
            Matcher m = NUMBER.matcher(field);
            while (m.find()) {
                int n = Integer.parseInt(m.group().length() > 9 ? "0" : m.group());
                if (!allowed.contains(n) && n != context.weekStart().getYear()) {
                    problems.add(at + ": el número " + m.group() + " del texto no tiene respaldo. " + hint(context, n));
                    break;
                }
            }
        }
    }

    // Como respaldar un numero: la clave exacta si hay una cifra con ese valor, o quitarlo.
    private String hint(AgentRunContext context, int n) {
        List<String> candidates = figures.candidates(context, n);
        String date = " Si es una fecha, escriba el día de la semana (lunes, martes…) y no el número.";
        return candidates.isEmpty()
                ? "Ninguna cifra de las métricas vale " + n + ": quite el número o reescriba sin él." + date
                : "Agregue a figures {\"key\":\"" + candidates.get(0) + "\",\"value\":" + n + "}"
                        + (candidates.size() > 1 ? " (otras claves con ese valor: " + String.join(", ", candidates.subList(1, candidates.size())) + ")" : "")
                        + " si esa es la cifra que quiso decir, o quite el número." + date;
    }

    // El resumen es lenguaje claro y no lleva `figures`: un numero suyo debe existir entre las cifras que calculo el codigo
    // (o ser el dia de una fecha). Las afirmaciones, que son las que se citan, si llevan cada cifra con su clave.
    private void checkSummaryNumbers(String summary, AgentRunContext context, Set<Integer> backed, List<String> problems) {
        Set<Integer> allowed = baseNumbers(context);
        allowed.addAll(backed);
        allowed.addAll(figures.allValues(context));
        Matcher m = NUMBER.matcher(summary);
        while (m.find()) {
            int n = Integer.parseInt(m.group().length() > 9 ? "0" : m.group());
            if (!allowed.contains(n) && n != context.weekStart().getYear()) {
                problems.add("summary: el número " + m.group() + " no es ninguna de las cifras de las métricas de la semana. Quítelo o use el valor correcto. "
                        + "Si es una fecha, escriba el día de la semana (lunes, martes…) y no el número.");
                return;
            }
        }
    }

    // Dias de la semana del informe y de la anterior: se pueden nombrar en el criterio («semana del 12 al 18»).
    private static Set<Integer> baseNumbers(AgentRunContext c) {
        Set<Integer> days = new LinkedHashSet<>();
        for (LocalDate d : List.of(c.weekStart(), c.weekEnd(), c.weekStart().minusDays(7), c.weekStart().minusDays(1))) {
            days.add(d.getDayOfMonth());
        }
        return days;
    }

    private ArrayNode keys(JsonNode node, String field, Set<String> claimKeys, List<String> problems) {
        ArrayNode out = mapper.createArrayNode();
        if (node.isMissingNode() || node.isNull()) {
            return out;
        }
        if (!node.isArray() || node.size() > MAX_CLAIMS) {
            problems.add(field + " debe ser una lista de claim_key.");
            return out;
        }
        for (JsonNode key : node) {
            if (!claimKeys.contains(key.asText())) {
                problems.add(field + " menciona «" + key.asText() + "», que no es una afirmación de claims.");
            } else {
                out.add(key.asText());
            }
        }
        return out;
    }

    private static void forbidden(String where, String text, List<String> problems) {
        DiagnosisGuard.forbiddenIn(text).ifPresent(word -> problems.add(where + " contiene «" + word
                + "»: el informe no diagnostica ni recomienda tratamiento ni dosis. Describa solo lo que los registros documentan."));
    }
}
