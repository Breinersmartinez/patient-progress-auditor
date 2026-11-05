package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.ASK;
import static com.patientprogressauditor.llm.LlmFixtures.EVENTS_SCHEMA;
import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.llm.LlmException.Kind;

class LangfusePayloadTest {

    private static final UUID RUN_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final Instant START = Instant.parse("2026-11-03T15:00:00Z");
    private static final String SECRET_DETAIL = "Groq respondio: {\"error\":\"vomito de Maria Perez, cama 4\"}";

    private static Attempt ok(String provider, String model, int number, long millis, int tokensIn, int tokensOut) {
        return new Attempt(provider, model, number, null, millis, null, tokensIn, tokensOut);
    }

    private static Attempt failed(String provider, String model, int number, long millis, Kind kind) {
        return new Attempt(provider, model, number, kind, millis, SECRET_DETAIL);
    }

    private static LangfuseRun run(String type, String status, String stopReason, long millis, Attempt... attempts) {
        return new LangfuseRun(RUN_ID, type, status, stopReason, START, millis, List.of(attempts));
    }

    private static JsonNode body(LangfuseRun run) {
        return LangfusePayload.build(run, "demo", MAPPER);
    }

    private static List<JsonNode> spans(JsonNode body) {
        List<JsonNode> result = new ArrayList<>();
        body.path("resourceSpans").path(0).path("scopeSpans").path(0).path("spans").forEach(result::add);
        return result;
    }

    private static Map<String, String> attributes(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        node.path("attributes").forEach(a -> result.put(a.path("key").asText(), a.path("value").path("stringValue").asText()));
        return result;
    }

    @Test
    void aRunIsOneTraceWithARootAndOneGenerationPerAttempt() {
        JsonNode body = body(run("INFORME", "COMPLETADA", "EXITO", 5200, failed("groq", "openai/gpt-oss-120b", 1, 300, Kind.RATE_LIMIT),
                ok("gemini", "gemini-3.5-flash-lite", 1, 2000, 1000, 200)));

        List<JsonNode> spans = spans(body);
        assertThat(spans).hasSize(3);
        JsonNode root = spans.get(0);
        assertThat(root.path("traceId").asText()).isEqualTo("0f8fad5bd9cb469fa16570867728950e");
        assertThat(root.has("parentSpanId")).isFalse();
        assertThat(root.path("name").asText()).isEqualTo("informe-semanal");
        Map<String, String> rootAttrs = attributes(root);
        assertThat(rootAttrs).containsEntry("langfuse.trace.name", "informe-semanal")
                .containsEntry("langfuse.observation.metadata.run_id", RUN_ID.toString())
                .containsEntry("langfuse.observation.metadata.run_type", "INFORME")
                .containsEntry("langfuse.observation.metadata.run_status", "COMPLETADA")
                .containsEntry("langfuse.observation.metadata.stop_reason", "EXITO")
                .containsEntry("langfuse.observation.metadata.attempts", "2");
        for (JsonNode generation : spans.subList(1, 3)) {
            assertThat(generation.path("traceId").asText()).isEqualTo(root.path("traceId").asText());
            assertThat(generation.path("parentSpanId").asText()).isEqualTo(root.path("spanId").asText());
            assertThat(attributes(generation)).containsEntry("langfuse.observation.type", "generation");
        }
        assertThat(spans.get(1).path("name").asText()).isEqualTo("llm-groq-intento-1");
        assertThat(spans.get(2).path("name").asText()).isEqualTo("llm-gemini-intento-1");
        JsonNode resource = body.path("resourceSpans").path(0).path("resource").path("attributes");
        assertThat(attributes(body.path("resourceSpans").path(0).path("resource"))).containsEntry("deployment.environment.name", "demo")
                .containsEntry("service.name", "patient-progress-auditor");
        assertThat(resource.size()).isEqualTo(2);
    }

    @Test
    void theTechnicalDetailAndAnyTextNeverLeave() {
        String json = body(run("ESTRUCTURACION", "FALLIDA", "PROVEEDOR_NO_DISPONIBLE", 900,
                failed("groq", "openai/gpt-oss-120b", 1, 300, Kind.UNAVAILABLE), failed("gemini", "gemini-3.5-flash-lite", 1, 200, Kind.AUTH)))
                .toString();

        assertThat(json).doesNotContain("Maria", "Perez", "vomito", "cama", "respondio", "detail");
        // Ni entrada ni salida de la generacion: solo metadatos.
        assertThat(json).doesNotContain("langfuse.observation.input", "langfuse.observation.output", "gen_ai.prompt", "gen_ai.completion");
    }

    @Test
    void theCostIsTheOneTheGatewayComputesAndItsPartsAddUpToIt() {
        JsonNode generation = spans(body(run("INFORME", "COMPLETADA", "EXITO", 1000, ok("groq", "openai/gpt-oss-120b", 1, 1000, 1001, 1)))).get(1);

        Map<String, String> attrs = attributes(generation);
        assertThat(json(attrs.get("langfuse.observation.usage_details")).path("input").asInt()).isEqualTo(1001);
        assertThat(json(attrs.get("langfuse.observation.usage_details")).path("output").asInt()).isEqualTo(1);
        JsonNode cost = json(attrs.get("langfuse.observation.cost_details"));
        BigDecimal expected = LlmPricing.cost("groq", "openai/gpt-oss-120b", 1001, 1);
        assertThat(cost.path("total").decimalValue()).isEqualByComparingTo(expected);
        assertThat(cost.path("input").decimalValue().add(cost.path("output").decimalValue())).isEqualByComparingTo(expected);
        assertThat(cost.path("input").decimalValue()).isEqualByComparingTo("0.000150");
        assertThat(cost.path("output").decimalValue()).isEqualByComparingTo("0.000001");
    }

    // La comprobacion del paso 5 con la pasarela real y proveedores con guion: un 429 que pide esperar mucho pasa directo al
    // respaldo, y lo que llega a Langfuse suma lo mismo que el costo que la pasarela entrega a quien guarda agent_runs.
    @Test
    void aRateLimitFollowedByTheFallbackGivesTwoGenerationsWhoseCostsAddUpToTheCostOfTheResult() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(new LlmException(Kind.RATE_LIMIT, "groq", Duration.ofSeconds(30), "HTTP 429"), 1);
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(ScriptedClient.answer("{}", 1234, 321));
        LlmResult result = new LlmGateway(List.of(groq, gemini), new LlmProperties(), d -> { }, MAPPER)
                .complete(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA));

        List<JsonNode> spans = spans(body(new LangfuseRun(RUN_ID, "ESTRUCTURACION", "COMPLETADA", "EXITO", START, result.millis(), result.attempts())));

        assertThat(spans).hasSize(3);
        Map<String, String> failed = attributes(spans.get(1));
        assertThat(failed).containsEntry("langfuse.observation.level", "ERROR").containsEntry("langfuse.observation.status_message", "RATE_LIMIT")
                .doesNotContainKey("langfuse.observation.cost_details");
        Map<String, String> succeeded = attributes(spans.get(2));
        assertThat(succeeded).containsEntry("langfuse.observation.model.name", "gemini-3.5-flash-lite");
        // Gemini: 1234 * 0,30 + 321 * 2,50 por millon de tokens; el 429 de Groq no suma nada.
        assertThat(result.costUsd()).isEqualByComparingTo("0.001173");
        assertThat(json(succeeded.get("langfuse.observation.cost_details")).path("total").decimalValue()).isEqualByComparingTo(result.costUsd());
        assertThat(attributes(spans.get(0)).get("langfuse.observation.metadata.cost_usd")).isEqualTo(result.costUsd().toPlainString());
    }

    @Test
    void aTranscriptionIsChargedByAudioTimeAndSendsNeitherTextNorAudio() {
        Attempt whisper = new Attempt("groq", "whisper-large-v3-turbo", 1, null, 450, null, 0, 0, 8.579);
        LangfuseRun run = run("TRANSCRIPCION", "COMPLETADA", "EXITO", 500, whisper);

        List<JsonNode> spans = spans(body(run));

        assertThat(spans.get(0).path("name").asText()).isEqualTo("transcripcion");
        Map<String, String> attrs = attributes(spans.get(1));
        assertThat(attrs).containsEntry("langfuse.observation.model.name", "whisper-large-v3-turbo")
                .containsEntry("langfuse.observation.metadata.audio_seconds", "8.579");
        assertThat(json(attrs.get("langfuse.observation.usage_details")).path("audio_seconds").asInt()).isEqualTo(9);
        assertThat(json(attrs.get("langfuse.observation.cost_details")).path("total").decimalValue()).isEqualByComparingTo("0.000111");
        assertThat(run.costUsd()).isEqualByComparingTo("0.000111");
        assertThat(attributes(spans.get(0))).containsEntry("langfuse.observation.metadata.cost_usd", "0.000111");
    }

    @Test
    void aFailedAttemptHasNoCostNorTokensAndIsMarkedAsError() {
        JsonNode generation = spans(body(run("INFORME", "COMPLETADA", "EXITO", 400, failed("groq", "openai/gpt-oss-120b", 1, 300, Kind.RATE_LIMIT)))).get(1);

        Map<String, String> attrs = attributes(generation);
        assertThat(attrs).doesNotContainKeys("langfuse.observation.cost_details", "langfuse.observation.usage_details")
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "RATE_LIMIT")
                .containsEntry("langfuse.observation.metadata.failure", "RATE_LIMIT");
        assertThat(generation.path("status").path("code").asInt()).isEqualTo(2);
        assertThat(generation.path("status").path("message").asText()).isEqualTo("RATE_LIMIT");
    }

    @Test
    void aModelWithoutAKnownPriceSendsNoCostInsteadOfZero() {
        List<JsonNode> spans = spans(body(run("INFORME", "COMPLETADA", "EXITO", 500, ok("groq", "otro/modelo-nuevo", 1, 500, 100, 50))));

        Map<String, String> generation = attributes(spans.get(1));
        assertThat(generation).containsKey("langfuse.observation.usage_details").doesNotContainKey("langfuse.observation.cost_details");
        assertThat(attributes(spans.get(0))).doesNotContainKey("langfuse.observation.metadata.cost_usd");
    }

    @Test
    void theRootCarriesTheTotalsOfTheRun() {
        JsonNode root = spans(body(run("MENSUAL", "COMPLETADA", "EXITO", 9000, ok("groq", "openai/gpt-oss-120b", 1, 3000, 1200, 300),
                ok("groq", "openai/gpt-oss-120b", 1, 3000, 800, 100)))).get(0);

        Map<String, String> attrs = attributes(root);
        BigDecimal expected = LlmPricing.cost("groq", "openai/gpt-oss-120b", 1200, 300).add(LlmPricing.cost("groq", "openai/gpt-oss-120b", 800, 100));
        assertThat(attrs).containsEntry("langfuse.trace.name", "informe-mensual").containsEntry("langfuse.observation.metadata.tokens_in", "2000")
                .containsEntry("langfuse.observation.metadata.tokens_out", "400");
        assertThat(new BigDecimal(attrs.get("langfuse.observation.metadata.cost_usd"))).isEqualByComparingTo(expected);
    }

    @Test
    void aRunWithNoSuccessfulCallHasNoCostAndAFailedRunIsAnError() {
        JsonNode root = spans(body(run("ESTRUCTURACION", "FALLIDA", "PROVEEDOR_NO_DISPONIBLE", 100, failed("groq", "openai/gpt-oss-120b", 1, 50, Kind.TIMEOUT)))).get(0);

        assertThat(attributes(root)).doesNotContainKey("langfuse.observation.metadata.cost_usd")
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "PROVEEDOR_NO_DISPONIBLE");
        assertThat(root.path("status").path("code").asInt()).isEqualTo(2);
    }

    @Test
    void anIncompleteRunIsAWarningNotAnError() {
        JsonNode root = spans(body(run("INFORME", "INCOMPLETA", "MAX_ITERACIONES", 100, ok("groq", "openai/gpt-oss-120b", 1, 50, 10, 10)))).get(0);

        assertThat(attributes(root)).containsEntry("langfuse.observation.level", "WARNING")
                .containsEntry("langfuse.observation.status_message", "MAX_ITERACIONES");
        assertThat(root.path("status").path("code").asInt()).isEqualTo(1);
    }

    @Test
    void attemptsAreDrawnOneAfterAnotherAndTheRootCoversThemAll() {
        List<JsonNode> spans = spans(body(run("INFORME", "COMPLETADA", "EXITO", 100, failed("groq", "openai/gpt-oss-120b", 1, 300, Kind.RATE_LIMIT),
                ok("groq", "openai/gpt-oss-120b", 2, 700, 10, 10))));

        long start = START.getEpochSecond() * 1_000_000_000L;
        assertThat(spans.get(0).path("startTimeUnixNano").asText()).isEqualTo(Long.toString(start));
        assertThat(spans.get(1).path("startTimeUnixNano").asText()).isEqualTo(Long.toString(start));
        assertThat(spans.get(1).path("endTimeUnixNano").asText()).isEqualTo(Long.toString(start + 300_000_000L));
        assertThat(spans.get(2).path("startTimeUnixNano").asText()).isEqualTo(Long.toString(start + 300_000_000L));
        assertThat(spans.get(2).path("endTimeUnixNano").asText()).isEqualTo(Long.toString(start + 1_000_000_000L));
        // La duracion declarada de la ejecucion (100 ms) es menor que la suma de los intentos: la raiz los cubre igual.
        assertThat(spans.get(0).path("endTimeUnixNano").asText()).isEqualTo(Long.toString(start + 1_000_000_000L));
    }

    @Test
    void spanIdsAreSixteenHexDistinctNonZeroAndStable() {
        LangfuseRun run = run("INFORME", "COMPLETADA", "EXITO", 100, ok("groq", "openai/gpt-oss-120b", 1, 10, 1, 1),
                ok("groq", "openai/gpt-oss-120b", 2, 10, 1, 1));

        List<String> ids = spans(body(run)).stream().map(s -> s.path("spanId").asText()).toList();
        assertThat(ids).hasSize(3).doesNotHaveDuplicates().allMatch(id -> id.matches("[0-9a-f]{16}") && !id.equals("0000000000000000"));
        assertThat(spans(body(run)).stream().map(s -> s.path("spanId").asText()).toList()).isEqualTo(ids);
    }

    @Test
    void theEnvironmentIsNormalizedToWhatLangfuseAccepts() {
        assertThat(LangfusePayload.environment("eval")).isEqualTo("eval");
        assertThat(LangfusePayload.environment("  Demo Equipo ")).isEqualTo("demo-equipo");
        assertThat(LangfusePayload.environment("")).isEqualTo("demo");
        assertThat(LangfusePayload.environment(null)).isEqualTo("demo");
        assertThat(LangfusePayload.environment("langfuse-prueba")).isEqualTo("demo");
        assertThat(LangfusePayload.environment("a".repeat(60))).hasSize(40);
    }

    private static JsonNode json(String text) {
        return LlmFixtures.json(text);
    }
}
