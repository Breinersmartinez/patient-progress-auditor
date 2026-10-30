package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.ASK;
import static com.patientprogressauditor.llm.LlmFixtures.EVENTS_SCHEMA;
import static com.patientprogressauditor.llm.LlmFixtures.METRICS_TOOL;
import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static com.patientprogressauditor.llm.LlmFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.llm.FakeProvider.Reply;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.LlmResponse.FinishReason;

class GeminiClientTest {

    private static final String OK = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"{\\"events\\":[]}"}]},"finishReason":"STOP"}],
             "usageMetadata":{"promptTokenCount":38,"candidatesTokenCount":125,"totalTokenCount":163}}""";

    private FakeProvider server;
    private GeminiClient client;

    @BeforeEach
    void start() throws Exception {
        server = new FakeProvider();
        client = new GeminiClient(LlmFixtures.properties("http://unused", "", server.baseUrl(), "AIza-prueba"), MAPPER);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static LlmException failure(GeminiClient client, LlmRequest request) {
        try {
            client.chat(request);
        } catch (LlmException e) {
            return e;
        }
        throw new AssertionError("Debia fallar");
    }

    private static final LlmRequest STRUCTURE = LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA);

    @Test
    void sendsTheKeyInTheHeaderTheSystemApartAndTheStandardJsonSchema() {
        server.replyWith(Reply.ok(OK));

        LlmResponse response = client.chat(STRUCTURE);

        FakeProvider.Received sent = server.requests.get(0);
        assertThat(sent.path()).isEqualTo("/models/gemini-3.5-flash-lite:generateContent");
        assertThat(sent.header("x-goog-api-key")).isEqualTo("AIza-prueba");
        assertThat(sent.path()).doesNotContain("AIza");
        assertThat(sent.body().path("systemInstruction").path("parts").get(0).path("text").asText()).isEqualTo("Extrae sintomas.");
        assertThat(sent.body().path("contents")).hasSize(1);
        assertThat(sent.body().path("contents").get(0).path("role").asText()).isEqualTo("user");
        assertThat(sent.body().path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(sent.body().path("generationConfig").path("responseJsonSchema").path("required").get(0).asText()).isEqualTo("events");
        assertThat(sent.body().path("generationConfig").path("temperature").asDouble()).isZero();
        assertThat(response.text()).isEqualTo("{\"events\":[]}");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokensIn()).isEqualTo(38);
        assertThat(response.tokensOut()).isEqualTo(125);
    }

    @Test
    void thinkingTokensCountAsOutputAndThoughtPartsAreNotTheAnswer() {
        server.replyWith(Reply.ok("""
                {"candidates":[{"content":{"parts":[{"thought":true,"text":"pensando..."},{"text":"hola"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"thoughtsTokenCount":40}}"""));

        LlmResponse response = client.chat(STRUCTURE);

        assertThat(response.text()).isEqualTo("hola");
        assertThat(response.tokensOut()).isEqualTo(45);
    }

    @Test
    void readsAToolCallWithItsSignature() {
        server.replyWith(Reply.ok("""
                {"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"get_week_metrics",
                  "args":{"patient_id":"p1","week_start":"2026-10-19"},"id":"call_9"},"thoughtSignature":"FIRMA-GEMINI"}]},"finishReason":"STOP"}],
                 "usageMetadata":{"promptTokenCount":146,"candidatesTokenCount":20}}"""));

        LlmResponse response = client.chat(LlmRequest.withTools(LlmPurpose.AGENT, ASK, List.of(METRICS_TOOL)));

        JsonNode declaration = server.requests.get(0).body().path("tools").get(0).path("functionDeclarations").get(0);
        assertThat(declaration.path("name").asText()).isEqualTo("get_week_metrics");
        assertThat(declaration.path("parametersJsonSchema").path("type").asText()).isEqualTo("object");
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_CALLS);
        assertThat(response.toolCalls().get(0).id()).isEqualTo("call_9");
        assertThat(response.toolCalls().get(0).signature()).isEqualTo("FIRMA-GEMINI");
        assertThat(response.toolCalls().get(0).arguments().path("patient_id").asText()).isEqualTo("p1");
    }

    @Test
    void aHistoryWithToolCallsFromAnotherProviderCarriesTheForeignSignatureAndGroupsTheResults() {
        server.replyWith(Reply.ok(OK));
        ToolCall fromGroq = new ToolCall("fc_1", "get_week_metrics", json("{\"patient_id\":\"p1\"}"), null);
        ToolCall own = new ToolCall("call_2", "get_week_metrics", json("{\"patient_id\":\"p2\"}"), "FIRMA-GEMINI");

        client.chat(LlmRequest.withTools(LlmPurpose.AGENT, List.of(LlmMessage.system("s"), LlmMessage.user("metricas"),
                LlmMessage.assistant(null, List.of(fromGroq, own)),
                LlmMessage.tool("fc_1", "get_week_metrics", "{\"entries\":7}"),
                LlmMessage.tool("call_2", "get_week_metrics", "No se pudo calcular: semana invalida")), List.of(METRICS_TOOL)));

        JsonNode contents = server.requests.get(0).body().path("contents");
        assertThat(contents).hasSize(3);
        JsonNode model = contents.get(1);
        assertThat(model.path("role").asText()).isEqualTo("model");
        assertThat(model.path("parts").get(0).path("thoughtSignature").asText()).isEqualTo("skip_thought_signature_validator");
        assertThat(model.path("parts").get(1).path("thoughtSignature").asText()).isEqualTo("FIRMA-GEMINI");
        JsonNode results = contents.get(2).path("parts");
        assertThat(contents.get(2).path("role").asText()).isEqualTo("user");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).path("functionResponse").path("response").path("result").path("entries").asInt()).isEqualTo(7);
        // Un error de herramienta no es JSON: viaja como texto, como observacion para el modelo.
        assertThat(results.get(1).path("functionResponse").path("response").path("result").asText())
                .isEqualTo("No se pudo calcular: semana invalida");
    }

    @Test
    void aRateLimitTakesTheWaitFromTheBodyOrTheHeader() {
        server.replyWith(Reply.status(429, """
                {"error":{"code":429,"status":"RESOURCE_EXHAUSTED","details":[{"retryDelay":"34s"}]}}"""));
        assertThat(failure(client, STRUCTURE).kind()).isEqualTo(Kind.RATE_LIMIT);
        assertThat(failure(client, STRUCTURE).retryAfter()).isEqualTo(Duration.ofSeconds(34));

        server.replyWith(Reply.status(429, "{\"error\":{}}", Map.of("Retry-After", "3")));
        assertThat(failure(client, STRUCTURE).retryAfter()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void anOverloadedModelIsUnavailableAndRetryable() {
        server.replyWith(Reply.status(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}"));

        LlmException e = failure(client, STRUCTURE);

        assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(e.kind().retryable()).isTrue();
        assertThat(e.provider()).isEqualTo("gemini");
    }

    @Test
    void aBadKeyAnswers400ButIsAuth() {
        server.replyWith(Reply.status(400, "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\",\"status\":\"INVALID_ARGUMENT\"}}"));
        assertThat(failure(client, STRUCTURE).kind()).isEqualTo(Kind.AUTH);

        server.replyWith(Reply.status(403, "{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));
        assertThat(failure(client, STRUCTURE).kind()).isEqualTo(Kind.AUTH);

        server.replyWith(Reply.status(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"bad schema\"}}"));
        assertThat(failure(client, STRUCTURE).kind()).isEqualTo(Kind.BAD_REQUEST);
    }

    @Test
    void aSlowProviderTimesOutForReal() {
        server.replyWith(Reply.slow(Duration.ofMillis(800)));

        LlmException e = failure(client,
                new LlmRequest(LlmPurpose.STRUCTURING, ASK, List.of(), EVENTS_SCHEMA, Duration.ofMillis(150), null));

        assertThat(e.kind()).isEqualTo(Kind.TIMEOUT);
    }

    @Test
    void aBlockedOrEmptyAnswerIsInvalidOutput() {
        server.replyWith(Reply.ok("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}"));
        assertThat(failure(client, STRUCTURE).kind()).isEqualTo(Kind.INVALID_OUTPUT);

        server.replyWith(Reply.ok("no es json"));
        assertThat(failure(client, STRUCTURE).kind()).isEqualTo(Kind.INVALID_OUTPUT);
    }

    @Test
    void withoutAKeyItFailsBeforeTouchingTheNetwork() {
        GeminiClient noKey = new GeminiClient(LlmFixtures.properties("http://unused", "", server.baseUrl(), ""), MAPPER);

        assertThat(noKey.configured()).isFalse();
        assertThatThrownBy(() -> noKey.chat(STRUCTURE))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.MISSING_KEY));
        assertThat(server.requests).isEmpty();
    }

    @Test
    void theErrorTheUserSeesNeverCarriesTheBodyOrTheKey() {
        server.replyWith(Reply.status(500, "{\"error\":{\"message\":\"trace AIza-prueba at com.google.Y\"}}"));

        LlmException e = failure(client, STRUCTURE);

        assertThat(e.getMessage()).doesNotContain("AIza").doesNotContain("trace");
    }
}
