package com.patientprogressauditor.llm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.llm.LlmException.Kind;

// Arma el cuerpo OTLP/JSON que acepta Langfuse (probado en el paso 2 de docs/PLAN_M3.md): un tramo raiz por ejecucion y
// una generacion por intento de llamada. Es una funcion pura: no abre red ni lee configuracion.
//
// Solo salen metadatos. Del intento se lee proveedor, modelo, numero, categoria del fallo, duracion y tokens; NUNCA
// `Attempt.detail`, que es texto tecnico del proveedor y puede traer un fragmento de su respuesta.
final class LangfusePayload {

    private static final String META = "langfuse.observation.metadata.";
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    // Codigos de estado de OTLP.
    private static final int STATUS_OK = 1;
    private static final int STATUS_ERROR = 2;

    private LangfusePayload() {
    }

    static ObjectNode build(LangfuseRun run, String environment, ObjectMapper mapper) {
        // El id de la traza es el de la ejecucion sin guiones (32 hexadecimales): se busca en Langfuse por agent_runs.id.
        String traceId = run.runId().toString().replace("-", "");
        long seed = Long.parseUnsignedLong(traceId.substring(0, 16), 16);
        String rootId = spanId(seed, 0);

        ArrayNode spans = mapper.createArrayNode();
        ObjectNode root = mapper.createObjectNode();
        spans.add(root);

        // Attempt guarda la duracion pero no la hora de inicio: los intentos se dibujan uno tras otro desde el inicio de la
        // ejecucion. Las esperas entre reintentos y las herramientas del agente no ocupan lugar en esa linea de tiempo.
        long start = nanos(run.startedAt());
        long cursor = start;
        int index = 1;
        for (Attempt attempt : run.attempts()) {
            long end = cursor + attempt.millis() * NANOS_PER_MILLI;
            spans.add(generation(mapper, traceId, spanId(seed, index++), rootId, attempt, cursor, end));
            cursor = end;
        }

        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("langfuse.trace.name", traceName(run.type()));
        attrs.put("langfuse.observation.type", "span");
        attrs.put(META + "run_id", run.runId().toString());
        attrs.put(META + "run_type", run.type());
        attrs.put(META + "run_status", run.status());
        if (run.stopReason() != null) {
            attrs.put(META + "stop_reason", run.stopReason());
        }
        attrs.put(META + "attempts", String.valueOf(run.attempts().size()));
        attrs.put(META + "tokens_in", String.valueOf(run.tokensIn()));
        attrs.put(META + "tokens_out", String.valueOf(run.tokensOut()));
        BigDecimal cost = run.costUsd();
        if (cost != null) {
            attrs.put(META + "cost_usd", cost.toPlainString());
        }
        String level = "FALLIDA".equals(run.status()) ? "ERROR" : "INCOMPLETA".equals(run.status()) ? "WARNING" : null;
        if (level != null) {
            attrs.put("langfuse.observation.level", level);
            attrs.put("langfuse.observation.status_message", run.stopReason() != null ? run.stopReason() : run.status());
        }
        long rootEnd = Math.max(start + run.millis() * NANOS_PER_MILLI, cursor);
        String error = "FALLIDA".equals(run.status()) ? attrs.get("langfuse.observation.status_message") : null;
        fill(mapper, root, traceId, rootId, null, traceName(run.type()), start, rootEnd, attrs, error);

        ObjectNode body = mapper.createObjectNode();
        ObjectNode resourceSpan = body.putArray("resourceSpans").addObject();
        ArrayNode resourceAttributes = resourceSpan.putObject("resource").putArray("attributes");
        resourceAttributes.add(attribute(mapper, "service.name", "patient-progress-auditor"));
        resourceAttributes.add(attribute(mapper, "deployment.environment.name", environment));
        ObjectNode scopeSpan = resourceSpan.putArray("scopeSpans").addObject();
        scopeSpan.putObject("scope").put("name", "ppa.llm");
        scopeSpan.set("spans", spans);
        return body;
    }

    private static ObjectNode generation(ObjectMapper mapper, String traceId, String spanId, String parentId, Attempt attempt,
            long start, long end) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("langfuse.observation.type", "generation");
        if (attempt.model() != null) {
            attrs.put("langfuse.observation.model.name", attempt.model());
        }
        attrs.put(META + "provider", attempt.provider());
        attrs.put(META + "attempt", String.valueOf(attempt.number()));
        Kind failure = attempt.failure();
        if (failure == null && attempt.audioSeconds() > 0) {
            // Transcripcion (RF-38): el uso es tiempo de audio, no tokens. Solo se manda el total del costo, que Langfuse respeta.
            attrs.put(META + "audio_seconds", String.valueOf(attempt.audioSeconds()));
            attrs.put("langfuse.observation.usage_details", "{\"audio_seconds\":%d}".formatted((long) Math.ceil(attempt.audioSeconds())));
            BigDecimal total = LlmPricing.cost(attempt);
            if (total != null) {
                attrs.put("langfuse.observation.cost_details", "{\"total\":%s}".formatted(total.toPlainString()));
            }
        } else if (failure == null) {
            attrs.put("langfuse.observation.usage_details", "{\"input\":%d,\"output\":%d}".formatted(attempt.tokensIn(), attempt.tokensOut()));
            BigDecimal total = LlmPricing.cost(attempt.provider(), attempt.model(), attempt.tokensIn(), attempt.tokensOut());
            // Sin precio conocido no se manda costo: Langfuse lo muestra vacio, no en cero.
            if (total != null) {
                BigDecimal input = LlmPricing.inputCost(attempt.provider(), attempt.model(), attempt.tokensIn());
                attrs.put("langfuse.observation.cost_details", "{\"input\":%s,\"output\":%s,\"total\":%s}"
                        .formatted(input.toPlainString(), total.subtract(input).toPlainString(), total.toPlainString()));
            }
        } else {
            attrs.put(META + "failure", failure.name());
            attrs.put("langfuse.observation.level", "ERROR");
            attrs.put("langfuse.observation.status_message", failure.name());
        }
        ObjectNode span = mapper.createObjectNode();
        fill(mapper, span, traceId, spanId, parentId, "llm-" + attempt.provider() + "-intento-" + attempt.number(), start, end,
                attrs, failure == null ? null : failure.name());
        return span;
    }

    private static void fill(ObjectMapper mapper, ObjectNode span, String traceId, String spanId, String parentId, String name,
            long start, long end, Map<String, String> attrs, String error) {
        span.put("traceId", traceId);
        span.put("spanId", spanId);
        if (parentId != null) {
            span.put("parentSpanId", parentId);
        }
        span.put("name", name);
        span.put("kind", 1);
        span.put("startTimeUnixNano", Long.toString(start));
        span.put("endTimeUnixNano", Long.toString(end));
        ArrayNode attributes = span.putArray("attributes");
        attrs.forEach((key, value) -> attributes.add(attribute(mapper, key, value)));
        ObjectNode status = span.putObject("status");
        status.put("code", error == null ? STATUS_OK : STATUS_ERROR);
        if (error != null) {
            status.put("message", error);
        }
    }

    private static ObjectNode attribute(ObjectMapper mapper, String key, String value) {
        ObjectNode node = mapper.createObjectNode();
        node.put("key", key);
        node.putObject("value").put("stringValue", value);
        return node;
    }

    // Identificadores de tramo deterministas (16 hexadecimales, nunca todo ceros): reenviar la misma ejecucion no duplica.
    private static String spanId(long seed, int index) {
        long id = seed + index * GOLDEN;
        return "%016x".formatted(id == 0 ? 1 : id);
    }

    private static long nanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }

    private static String traceName(String type) {
        return switch (type) {
            case "INFORME" -> "informe-semanal";
            case "MENSUAL" -> "informe-mensual";
            case "ESTRUCTURACION" -> "estructuracion";
            case "TRANSCRIPCION" -> "transcripcion";
            default -> type.toLowerCase();
        };
    }

    // El entorno de Langfuse admite solo minusculas, digitos, guion y guion bajo, hasta 40 caracteres, y no puede empezar por
    // "langfuse". Un nombre invalido se normaliza en lugar de perder las trazas.
    static String environment(String configured) {
        String clean = configured == null ? "" : configured.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        if (clean.length() > 40) {
            clean = clean.substring(0, 40);
        }
        return clean.isEmpty() || clean.startsWith("langfuse") ? LangfuseProperties.DEFAULT_ENVIRONMENT : clean;
    }
}
