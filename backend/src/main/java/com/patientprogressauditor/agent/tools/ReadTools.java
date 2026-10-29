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
        public ToolSpec spec(DescriptionVersion version) {
            ObjectNode schema = mapper.createObjectNode().put("type", "object");
            ObjectNode props = schema.putObject("properties");
            ArrayNode names = props.putObject("symptom").put("type", "string").putArray("enum");
            Stream.of(Symptom.values()).forEach(s -> names.add(s.name()));
            props.putObject("weeks_back").put("type", "integer").put("minimum", 1).put("maximum", MAX_WEEKS_BACK)
                    .put("description", "Cuántas semanas mirar, contando la del informe (1 a 8)");
            schema.putArray("required").add("symptom").add("weeks_back");
            schema.put("additionalProperties", false);
            return new ToolSpec(name(), ToolDescriptions.of(name(), version), schema);
        }

        @Override
        public String execute(AgentRunContext context, JsonNode arguments) {
            String raw = arguments.path("symptom").asText("");
            Symptom symptom = Stream.of(Symptom.values()).filter(s -> s.name().equalsIgnoreCase(raw.trim())).findFirst()
                    .orElseThrow(() -> new ToolException("symptom debe ser uno de: "
                            + Stream.of(Symptom.values()).map(Enum::name).collect(Collectors.joining(", ")) + "."));
            int back = arguments.path("weeks_back").asInt(4);
            if (back < 1 || back > MAX_WEEKS_BACK) {
                throw new ToolException("weeks_back debe estar entre 1 y " + MAX_WEEKS_BACK + ".");
            }
            LocalDate first = context.weekStart().minusWeeks(back - 1L);
            Map<LocalDate, List<ObjectNode>> perWeek = new TreeMap<>();
            for (LocalDate w = first; !w.isAfter(context.weekStart()); w = w.plusWeeks(1)) {
                perWeek.put(w, new ArrayList<>());
            }
            events.findRows(context.patientId(), first, context.weekEnd()).stream()
                    .filter(r -> r.getSymptom() == symptom)
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
            ObjectNode out = mapper.createObjectNode().put("symptom", symptom.name());
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
}
