package com.patientprogressauditor.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.tools.MetricsView;
import com.patientprogressauditor.agent.tools.ToolException;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.metrics.MetricsService;
import com.patientprogressauditor.metrics.WeeklyMetrics;

// Resuelve una cifra que el modelo cita (`key`) contra el calculo del codigo (RN-04, RF-13). La clave es la RUTA en el JSON
// que devuelve get_week_metrics (lo que el modelo ya tiene delante), con «@AAAA-MM-DD» para otra semana:
//   entries, total_events, symptoms.NAUSEAS.days, vs_previous.previous_entries, vs_previous.days.NAUSEAS.0 ...
// Atajo: NAUSEAS.days equivale a symptoms.NAUSEAS.days.
@Component
public class FigureResolver {

    private static final Pattern KEY = Pattern.compile("^([A-Za-z0-9_.]+?)(?:@(\\d{4}-\\d{2}-\\d{2}))?$");
    private static final Pattern SHORT = Pattern.compile("^([A-Z_]+)\\.(days|events|occurrences)$");

    public static final String VALID_KEYS = "la ruta de la cifra en get_week_metrics, p. ej. entries, total_events, days_without_entry, "
            + "symptoms.NAUSEAS.days, symptoms.NAUSEAS.events, vs_previous.previous_entries, vs_previous.days.NAUSEAS.0 "
            + "(días en la semana anterior; .1 es esta semana); con @AAAA-MM-DD para otra semana";

    public record Figure(String key, int value, LocalDate week) {
    }

    public record Resolution(Optional<Figure> figure, String problem) {
    }

    private final MetricsService metrics;
    private final ObjectMapper mapper;

    public FigureResolver(MetricsService metrics, ObjectMapper mapper) {
        this.metrics = metrics;
        this.mapper = mapper;
    }

    public Resolution resolveOrExplain(AgentRunContext context, String key) {
        return resolveOrExplain(context, key, null);
    }

    // `claimed` es el valor que el modelo escribio: sirve SOLO para elegir entre «.0» y «.1» cuando la clave de una
    // comparacion llega sin indice. El valor nunca se acepta por venir del modelo: se compara con el calculo.
    public Resolution resolveOrExplain(AgentRunContext context, String key, Integer claimed) {
        Matcher m = KEY.matcher(normalize(key));
        if (!m.matches()) {
            return no("la clave «" + key + "» no tiene el formato esperado (" + VALID_KEYS + ")");
        }
        LocalDate week;
        try {
            week = m.group(2) == null ? context.weekStart() : context.requireWeek(m.group(2));
        } catch (ToolException e) {
            return no("la clave «" + key + "»: " + e.getMessage());
        }
        String path = m.group(1);
        Matcher shortForm = SHORT.matcher(path);
        if (shortForm.matches()) {
            path = "symptoms." + path;
        }
        JsonNode root = metricsOf(context, week);
        JsonNode node = root;
        for (String segment : path.split("\\.")) {
            node = node.isArray() && segment.matches("\\d+") ? node.path(Integer.parseInt(segment)) : node.path(segment);
        }
        if (node.isMissingNode()) {
            return missing(path, key, week);
        }
        // «vs_previous.days.FIEBRE» sin indice: [semana anterior, esta semana]. Se elige el que coincide con lo que escribio.
        if (node.isArray() && node.size() == 2 && node.get(0).isInt() && node.get(1).isInt()) {
            for (int i = 0; i < 2; i++) {
                if (claimed != null && node.get(i).asInt() == claimed) {
                    return new Resolution(Optional.of(new Figure(path + "." + i + (m.group(2) == null ? "" : "@" + m.group(2)), claimed, week)), null);
                }
            }
            return no("«" + key + "» son dos cifras: «" + path + ".0» vale " + node.get(0).asInt() + " (semana anterior) y «" + path + ".1» vale "
                    + node.get(1).asInt() + " (esta semana). Escriba la clave con .0 o .1");
        }
        if (node.isNull()) {
            return no("«" + key + "» no tiene valor: nadie indicó cuántas veces");
        }
        if (!node.isInt()) {
            return no("«" + key + "» no es una cifra entera (" + VALID_KEYS + ")");
        }
        return new Resolution(Optional.of(new Figure(normalize(key), node.asInt(), week)), null);
    }

    // Errores de sintaxis que no cambian de que cifra se habla: «X[1]» es «X.1», y un «:0» o «=0» pegado a la clave sobra.
    static String normalize(String key) {
        String k = key == null ? "" : key.trim();
        k = k.replaceAll("\\[(\\d+)\\]", ".$1").replaceAll("[:=]\\s*-?\\d+$", "").replaceAll("[\\s.]+$", "");
        return k;
    }

    // Un sintoma que no aparece esa semana tiene 0 dias y 0 eventos; una ruta que no existe se explica.
    private Resolution missing(String path, String key, LocalDate week) {
        String[] parts = path.split("\\.");
        if (parts.length == 3 && parts[0].equals("symptoms") && (parts[2].equals("days") || parts[2].equals("events"))) {
            if (Stream.of(Symptom.values()).anyMatch(s -> s.name().equals(parts[1]))) {
                return new Resolution(Optional.of(new Figure(key.trim(), 0, week)), null);
            }
            return no("«" + parts[1] + "» no es un síntoma del catálogo ("
                    + Stream.of(Symptom.values()).map(Enum::name).collect(Collectors.joining(", ")) + ")");
        }
        if (parts.length == 3 && parts[0].equals("symptoms") && parts[2].equals("occurrences")) {
            return no("«" + key + "» no tiene valor: ese síntoma no aparece esa semana");
        }
        return no("la clave «" + key + "» no existe (" + VALID_KEYS + ")");
    }

    // Claves de la semana del informe cuyo valor es `n`: para decirle al modelo cual escribir cuando nombra un numero sin
    // respaldo («el 1 puede ser symptoms.DOLOR_HUESO.days»).
    // En el mensual se buscan las cuatro semanas y la clave lleva «@» con la semana (salvo la ultima, que es la de por defecto).
    public List<String> candidates(AgentRunContext context, int n) {
        List<String> found = new ArrayList<>();
        for (LocalDate week : context.weeks()) {
            List<String> inWeek = new ArrayList<>();
            collect(metricsOf(context, week), "", n, inWeek);
            inWeek.forEach(path -> found.add(week.equals(context.weekStart()) ? path : path + "@" + week));
        }
        return found.size() > 3 ? found.subList(0, 3) : found;
    }

    // Todos los enteros que calculo el codigo para las semanas del informe (y la anterior a cada una, dentro de vs_previous).
    public java.util.Set<Integer> allValues(AgentRunContext context) {
        java.util.Set<Integer> values = new java.util.HashSet<>();
        context.weeks().forEach(week -> collectValues(metricsOf(context, week), values));
        return values;
    }

    private void collectValues(JsonNode node, java.util.Set<Integer> values) {
        if (node.isInt()) {
            values.add(node.asInt());
        }
        node.forEach(child -> collectValues(child, values));
    }

    private void collect(JsonNode node, String path, int n, List<String> found) {
        if (node.isInt() && node.asInt() == n && !path.isEmpty()) {
            found.add(path);
        } else if (node.isObject()) {
            node.fields().forEachRemaining(e -> collect(e.getValue(), path.isEmpty() ? e.getKey() : path + "." + e.getKey(), n, found));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collect(node.get(i), path + "." + i, n, found);
            }
        }
    }

    private JsonNode metricsOf(AgentRunContext context, LocalDate week) {
        WeeklyMetrics data = context.metrics(week, w -> metrics.weekly(context.patientId(), w));
        return MetricsView.of(data, mapper);
    }

    private static Resolution no(String problem) {
        return new Resolution(Optional.empty(), problem);
    }
}
