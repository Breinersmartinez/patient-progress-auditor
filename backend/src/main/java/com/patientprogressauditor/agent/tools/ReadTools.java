package com.patientprogressauditor.agent.tools;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.AgentProperties;
import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;
import com.patientprogressauditor.agent.AgentRunContext;
import com.patientprogressauditor.agent.ApprovedWeeklyReports;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryEventRepository;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.entry.EntryService;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.llm.ToolSpec;
import com.patientprogressauditor.metrics.MetricsService;

// Las herramientas de LECTURA del agente. Ninguna recibe el id del paciente (la tarea ya lo fija) ni lee semanas
// posteriores a la del informe; ninguna sale a internet ni escribe. Las salidas son JSON compacto: la cuota gratuita de
// Groq es de 8 000 tokens por minuto.
public final class ReadTools {

    private static final LocalDate EPOCH = LocalDate.of(1900, 1, 1);

    private ReadTools() {
    }

    private static String json(ObjectMapper mapper, JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ToolException("No se pudo armar la respuesta.");
        }
    }

    private static ObjectNode weekParam(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties").putObject("week_start").put("type", "string")
                .put("description", "Lunes de la semana, AAAA-MM-DD");
        schema.putArray("required").add("week_start");
        schema.put("additionalProperties", false);
        return schema;
    }

    private static ObjectNode noParams(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties");
        schema.put("additionalProperties", false);
        return schema;
    }

    @Component
    public static class ListPatientWeeks implements AgentTool {

        private final EntryRepository entries;
        private final ObjectMapper mapper;

        public ListPatientWeeks(EntryRepository entries, ObjectMapper mapper) {
            this.entries = entries;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "list_patient_weeks";
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), noParams(mapper));
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            Map<LocalDate, Integer> perWeek = new TreeMap<>();
            for (Entry e : entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
                    context.patientId(), EPOCH, context.weekEnd())) {
                perWeek.merge(e.getEntryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), 1, Integer::sum);
            }
            ArrayNode weeks = mapper.createObjectNode().putArray("weeks");
            List<Map.Entry<LocalDate, Integer>> all = new ArrayList<>(perWeek.entrySet());
            // Las 12 semanas mas recientes: con mas, el modelo no gana nada y se gastan tokens.
            all.subList(Math.max(0, all.size() - 12), all.size())
                    .forEach(w -> weeks.addObject().put("week", w.getKey().toString()).put("entries", w.getValue()));
            return json(mapper, mapper.createObjectNode().set("weeks", weeks));
        }
    }

    @Component
    public static class GetWeekEntries implements AgentTool {

        private final EntryService entries;
        private final AgentProperties properties;
        private final ObjectMapper mapper;

        public GetWeekEntries(EntryService entries, AgentProperties properties, ObjectMapper mapper) {
            this.entries = entries;
            this.properties = properties;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "get_week_entries";
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), weekParam(mapper));
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            LocalDate week = context.requireWeek(arguments.path("week_start").asText(null));
            ArrayNode out = mapper.createArrayNode();
            for (EntryService.EntryView e : entries.listForPatient(context.patientId(), week, week.plusDays(6), false, 200)) {
                ObjectNode node = out.addObject();
                node.put("e", context.aliasFor(e.id(), e.entryDate()));
                node.put("date", e.entryDate().toString());
                String text = e.body();
                int max = properties.getEntryTextChars();
                node.put("text", text.length() > max ? text.substring(0, max) + "…" : text);
                ArrayNode symptoms = node.putArray("symptoms");
                e.symptoms().forEach(s -> symptoms.add(compact(s)));
                if ("SIN_ESTRUCTURAR".equals(e.structuringStatus())) {
                    node.put("unstructured", true);
                }
            }
            return json(mapper, mapper.createObjectNode().put("week", week.toString()).set("entries", out));
        }

        // «NAUSEAS:LEVE», «VOMITO:INTENSO:x2» o «APETITO_BAJO» (sin intensidad ni veces indicadas).
        static String compact(EntryService.SymptomView s) {
            StringBuilder sb = new StringBuilder(s.symptom());
            if (!"SIN_INDICAR".equals(s.intensity())) {
                sb.append(':').append(s.intensity());
            }
            if (s.occurrences() != null) {
                sb.append(":x").append(s.occurrences());
            }
            return sb.toString();
        }
    }

    @Component
    public static class GetWeekMetrics implements AgentTool {

        private final MetricsService metrics;
        private final ObjectMapper mapper;

        public GetWeekMetrics(MetricsService metrics, ObjectMapper mapper) {
            this.metrics = metrics;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "get_week_metrics";
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), weekParam(mapper));
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            LocalDate week = context.requireWeek(arguments.path("week_start").asText(null));
            return json(mapper, MetricsView.of(context.metrics(week, w -> metrics.weekly(context.patientId(), w)), mapper));
        }
    }

    // Solo de la consulta del medico (D31, RF-46): todas las entradas del periodo, de una vez (el tope de 31 dias,
    // docs/PLAN_M3.md Pieza 6 paso 1, las mantiene siempre pocas). Sin parametros: el periodo ya lo fija la tarea.
    @Component
    public static class GetPeriodEntries implements AgentTool {

        private final EntryService entries;
        private final AgentProperties properties;
        private final ObjectMapper mapper;

        public GetPeriodEntries(EntryService entries, AgentProperties properties, ObjectMapper mapper) {
            this.entries = entries;
            this.properties = properties;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "get_period_entries";
        }

        @Override
        public boolean consultationOnly() {
            return true;
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), noParams(mapper));
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            ArrayNode out = mapper.createArrayNode();
            for (EntryService.EntryView e : entries.listForPatient(context.patientId(), context.periodStart(), context.periodEnd(), false, 200)) {
                ObjectNode node = out.addObject();
                node.put("e", context.aliasFor(e.id(), e.entryDate()));
                node.put("date", e.entryDate().toString());
                String text = e.body();
                int max = properties.getEntryTextChars();
                node.put("text", text.length() > max ? text.substring(0, max) + "…" : text);
                ArrayNode symptoms = node.putArray("symptoms");
                e.symptoms().forEach(s -> symptoms.add(GetWeekEntries.compact(s)));
                if ("SIN_ESTRUCTURAR".equals(e.structuringStatus())) {
                    node.put("unstructured", true);
                }
            }
            ObjectNode root = mapper.createObjectNode();
            root.put("period_start", context.periodStart().toString()).put("period_end", context.periodEnd().toString());
            root.set("entries", out);
            return json(mapper, root);
        }
    }

    // Solo de la consulta del medico (D31, RF-46): las cifras del periodo completo y, si se eligio comparar, las del
    // periodo de comparacion (vs_previous). Mismo calculo que get_week_metrics (MetricsCalculator.computePeriod,
    // generalizacion probada contra compute() en el paso 3 del plan), con las claves del periodo en vez de "week".
    @Component
    public static class GetPeriodMetrics implements AgentTool {

        private final com.patientprogressauditor.metrics.MetricsService metrics;
        private final ObjectMapper mapper;

        public GetPeriodMetrics(com.patientprogressauditor.metrics.MetricsService metrics, ObjectMapper mapper) {
            this.metrics = metrics;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "get_period_metrics";
        }

        @Override
        public boolean consultationOnly() {
            return true;
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), noParams(mapper));
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            var data = context.metrics(context.periodEnd(), w -> metrics.period(context.patientId(), context.periodStart(),
                    context.periodEnd(), context.comparisonStart(), context.comparisonEnd()));
            ObjectNode view = MetricsView.of(data, mapper);
            // El "week" que deja MetricsView.of (pensado para una semana) no dice nada de un periodo libre: se
            // reemplaza por nombres claros, y lo mismo para el "week" de dentro de vs_previous.
            view.remove("week");
            view.put("period_start", context.periodStart().toString()).put("period_end", context.periodEnd().toString());
            ObjectNode vsPrevious = (ObjectNode) view.get("vs_previous");
            JsonNode comparisonWeek = vsPrevious.remove("week");
            if (context.comparisonStart() != null) {
                vsPrevious.put("comparison_start", context.comparisonStart().toString());
                vsPrevious.put("comparison_end", context.comparisonEnd().toString());
            } else if (comparisonWeek != null) {
                vsPrevious.putNull("comparison_start");
                vsPrevious.putNull("comparison_end");
            }
            return json(mapper, view);
        }
    }

    @Component
    public static class GetSymptomHistory implements AgentTool {

        static final int MAX_WEEKS_BACK = 8;

        private final EntryEventRepository events;
        private final ObjectMapper mapper;

        public GetSymptomHistory(EntryEventRepository events, ObjectMapper mapper) {
            this.events = events;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "get_symptom_history";
        }

        @Override
        public boolean sharedAcrossRoutes() {
            return true;
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            ObjectNode schema = mapper.createObjectNode().put("type", "object");
            ObjectNode props = schema.putObject("properties");
            props.putObject("symptom").put("type", "string").put("description",
                    "Código del síntoma tal como sale en get_week_metrics (p. ej. NAUSEAS, VOMITO, DOLOR_GARGANTA)");
            props.putObject("weeks_back").put("type", "integer").put("minimum", 1).put("maximum", MAX_WEEKS_BACK)
                    .put("description", "Cuántas semanas mirar, contando la del informe (1 a 8)");
            schema.putArray("required").add("symptom").add("weeks_back");
            schema.put("additionalProperties", false);
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), schema);
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            String raw = arguments.path("symptom").asText("");
            String code = Symptom.codeOf(raw);
            if (code.length() < 2 || code.length() > Symptom.MAX_CODE) {
                throw new ToolException("symptom debe ser el código de un síntoma, como sale en get_week_metrics (p. ej. NAUSEAS, DOLOR_GARGANTA).");
            }
            LocalDate catalogEnd = (context.isPeriod() ? context.periodEnd() : context.weekEnd()).plusDays(1);
            if (!Symptom.isCatalog(code) && !events.recordedFor(context.patientId(), code)) {
                throw new ToolException("«" + raw.trim() + "» no es un síntoma registrado para este paciente. Use el código tal como sale en get_week_metrics; "
                        + "este paciente tiene registrados: " + String.join(", ", new java.util.TreeSet<>(
                                events.findSymptomCodesBefore(context.patientId(), catalogEnd))) + ".");
            }
            int back = arguments.path("weeks_back").asInt(4);
            if (back < 1 || back > MAX_WEEKS_BACK) {
                throw new ToolException("weeks_back debe estar entre 1 y " + MAX_WEEKS_BACK + ".");
            }
            // El ancla es el lunes de la semana del informe, o, en una consulta (D31), el lunes de la semana que
            // contiene el fin del periodo: asi `perWeek` siempre queda alineado a semanas reales, sin importar que
            // el periodo mismo no empiece en lunes.
            LocalDate anchorWeek = context.isPeriod()
                    ? context.periodEnd().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : context.weekStart();
            LocalDate readEnd = context.isPeriod() ? context.periodEnd() : context.weekEnd();
            LocalDate first = anchorWeek.minusWeeks(back - 1L);
            Map<LocalDate, List<ObjectNode>> perWeek = new TreeMap<>();
            for (LocalDate w = first; !w.isAfter(anchorWeek); w = w.plusWeeks(1)) {
                perWeek.put(w, new ArrayList<>());
            }
            events.findRows(context.patientId(), first, readEnd).stream()
                    .filter(r -> r.getSymptomCode().equals(code))
                    .sorted(java.util.Comparator.comparing(r -> r.getEntryDate()))
                    .forEach(r -> {
                        ObjectNode node = mapper.createObjectNode();
                        node.put("e", context.aliasFor(r.getEntryId(), r.getEntryDate()));
                        node.put("date", r.getEntryDate().toString());
                        node.put("intensity", r.getIntensity().name());
                        if (r.getOccurrences() != null) {
                            node.put("times", r.getOccurrences());
                        }
                        perWeek.get(r.getEntryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))).add(node);
                    });
            ObjectNode out = mapper.createObjectNode().put("symptom", code);
            ArrayNode weeks = out.putArray("weeks");
            perWeek.forEach((week, nodes) -> {
                ObjectNode w = weeks.addObject().put("week", week.toString());
                w.put("days", nodes.stream().map(n -> n.path("date").asText()).distinct().count());
                ArrayNode list = w.putArray("entries");
                nodes.forEach(list::add);
            });
            return json(mapper, out);
        }
    }

    // Solo del mensual: los cuatro informes semanales aprobados (la version que enfermeria aprobo) con las cifras de cada
    // semana. Los alias de los registros que esos informes citan quedan emitidos y se pueden citar en el mensual.
    @Component
    public static class GetApprovedWeeklyReports implements AgentTool {

        private final EntryRepository entries;
        private final MetricsService metrics;
        private final ObjectMapper mapper;

        public GetApprovedWeeklyReports(EntryRepository entries, MetricsService metrics, ObjectMapper mapper) {
            this.entries = entries;
            this.metrics = metrics;
            this.mapper = mapper;
        }

        @Override
        public String name() {
            return "get_approved_weekly_reports";
        }

        @Override
        public boolean monthlyOnly() {
            return true;
        }

        @Override
        public ToolSpec spec(DescriptionVersion version) {
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), noParams(mapper));
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            if (!context.isMonthly()) {
                throw new ToolException("Esta herramienta es solo del informe mensual.");
            }
            ArrayNode weeks = mapper.createArrayNode();
            Map<java.util.UUID, LocalDate> dates = new java.util.HashMap<>();
            context.weeklyReports().stream().flatMap(w -> w.claims().stream()).flatMap(c -> c.entryIds().stream()).distinct()
                    .forEach(id -> dates.put(id, null));
            entries.findAllById(dates.keySet()).forEach(e -> dates.put(e.getId(), e.getEntryDate()));
            for (ApprovedWeeklyReports.Weekly weekly : context.weeklyReports()) {
                var data = context.metrics(weekly.weekStart(), w -> metrics.weekly(context.patientId(), w));
                ObjectNode node = weeks.addObject();
                node.put("n", weekly.weekNo());
                node.put("week", weekly.weekStart().toString());
                node.put("entries", data.entries());
                node.put("days_with_entry", data.daysWithEntry());
                node.put("total_events", data.totalEvents());
                ObjectNode days = node.putObject("days");
                data.symptoms().forEach(s -> days.put(s.symptom().name(), s.days()));
                node.put("summary", weekly.summary());
                ArrayNode claims = node.putArray("claims");
                for (ApprovedWeeklyReports.WeeklyClaim claim : weekly.claims()) {
                    ObjectNode c = claims.addObject().put("text", claim.text());
                    ArrayNode aliases = c.putArray("e");
                    claim.entryIds().forEach(id -> aliases.add(context.aliasFor(id, dates.get(id))));
                }
            }
            return json(mapper, mapper.createObjectNode().set("weeks", weeks));
        }
    }
}
