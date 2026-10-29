package com.patientprogressauditor.agent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.tools.MetricsView;
import com.patientprogressauditor.metrics.MetricsService;
import com.patientprogressauditor.metrics.WeeklyMetrics;

// Arma el contenido definitivo del informe (DICCIONARIO_DATOS_M2 §4.8): lo que redacto el modelo (ya validado) MAS lo que
// pone el codigo y el modelo no puede tocar: las cifras (`metrics`) y lo que falta (`insufficient_data`, RF-14, RN-08).
@Component
public class ReportContentBuilder {

    private static final DateTimeFormatter LONG_DAY = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "CO"));

    private final MetricsService metrics;
    private final ObjectMapper mapper;

    public ReportContentBuilder(MetricsService metrics, ObjectMapper mapper) {
        this.metrics = metrics;
        this.mapper = mapper;
    }

    public ObjectNode build(ObjectNode model, AgentRunContext context) {
        WeeklyMetrics week = context.metrics(context.weekStart(), w -> metrics.weekly(context.patientId(), w));
        ObjectNode content = mapper.createObjectNode();
        ObjectNode period = content.putObject("period");
        period.put("start", context.weekStart().toString()).put("end", context.weekEnd().toString());
        period.put("compared_with", week.comparison().previousWeekStart().toString());
        content.setAll(model);
        content.set("metrics", MetricsView.of(week, mapper));
        content.set("insufficient_data", insufficient(week));
        if (DiagnosisGuard.asksForDiagnosisOrTreatment(context.request())) {
            content.put("out_of_scope", DiagnosisGuard.OUT_OF_SCOPE);
        } else {
            content.putNull("out_of_scope");
        }
        return content;
    }

    // Lo que falta, declarado sin rellenar ni inventar: lo calcula el codigo, no el modelo.
    ArrayNode insufficient(WeeklyMetrics w) {
        ArrayNode out = mapper.createArrayNode();
        if (!w.datesWithoutEntry().isEmpty()) {
            ObjectNode node = out.addObject().put("kind", "DIAS_SIN_REGISTRO");
            ArrayNode dates = node.putArray("dates");
            w.datesWithoutEntry().forEach(d -> dates.add(d.toString()));
            node.put("text", "No hay registros " + (w.datesWithoutEntry().size() == 1 ? "el " : "los días ") + days(w.datesWithoutEntry()) + ".");
        }
        if (!w.comparison().comparable()) {
            out.addObject().put("kind", "SEMANA_ANTERIOR_SIN_REGISTROS").put("text",
                    "La semana anterior (" + MetricsView.period(w.comparison().previousWeekStart())
                            + ") no tiene registros: no se puede comparar y no se afirma qué síntomas aparecieron o desaparecieron.");
        } else if (w.comparison().previousUnstructuredEntries() > 0) {
            out.addObject().put("kind", "SEMANA_ANTERIOR_ENTRADAS_SIN_ESTRUCTURAR").put("count", w.comparison().previousUnstructuredEntries())
                    .put("text", count(w.comparison().previousUnstructuredEntries()) + " de la semana anterior solo tienen el texto guardado: sus síntomas no se contaron en la comparación.");
        }
        if (w.unstructuredEntries() > 0) {
            ObjectNode node = out.addObject().put("kind", "ENTRADAS_SIN_ESTRUCTURAR").put("count", w.unstructuredEntries());
            ArrayNode ids = node.putArray("entry_ids");
            w.unstructuredEntryIds().forEach(id -> ids.add(id.toString()));
            node.put("text", count(w.unstructuredEntries()) + " de esta semana solo tienen el texto guardado: sus síntomas no se contaron en las cifras.");
        }
        return out;
    }

    private static String count(int n) {
        return n == 1 ? "1 registro" : n + " registros";
    }

    private static String days(List<LocalDate> dates) {
        List<String> names = dates.stream().map(LONG_DAY::format).collect(Collectors.toList());
        return names.size() < 2 ? String.join("", names) : String.join(", ", names.subList(0, names.size() - 1)) + " y " + names.get(names.size() - 1);
    }
}
