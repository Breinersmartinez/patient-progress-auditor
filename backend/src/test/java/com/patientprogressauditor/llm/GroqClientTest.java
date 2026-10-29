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

import com.patientprogressauditor.llm.FakeProvider.Reply;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.LlmResponse.FinishReason;

class GroqClientTest {

    private static final String OK = """
            {"choices":[{"message":{"content":"{\\"events\\":[]}"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":120,"completion_tokens":30,"total_tokens":150}}""";

    private FakeProvider server;
    private GroqClient client;

    @BeforeEach
    void start() throws Exception {
        server = new FakeProvider();
        client = new GroqClient(LlmFixtures.properties(server.baseUrl(), "gsk_prueba", "http://unused", ""), MAPPER);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void sendsAStrictJsonSchemaWithTheStructuringModelAndReadsTextAndTokens() {
        server.replyWith(Reply.ok(OK));

        LlmResponse response = client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA));

        FakeProvider.Received sent = server.requests.get(0);
        assertThat(sent.path()).isEqualTo("/chat/completions");
        assertThat(sent.header("Authorization")).isEqualTo("Bearer gsk_prueba");
        assertThat(sent.body().path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        assertThat(sent.body().path("reasoning_effort").asText()).isEqualTo("medium");
        assertThat(sent.body().path("temperature").asDouble()).isZero();
        assertThat(sent.body().path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(sent.body().path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
        assertThat(sent.body().path("response_format").path("json_schema").path("schema").path("required").get(0).asText())
                .isEqualTo("events");
        assertThat(sent.body().path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(sent.body().has("tools")).isFalse();
        assertThat(response.text()).isEqualTo("{\"events\":[]}");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokensIn()).isEqualTo(120);
        assertThat(response.tokensOut()).isEqualTo(30);
    }

    @Test
    void theAgentUsesTheBiggerModelAndAsksForTools() {
        server.replyWith(Reply.ok("""
                {"choices":[{"message":{"content":null,"tool_calls":[{"id":"fc_1","type":"function",
                  "function":{"name":"get_week_metrics","arguments":"{\\"patient_id\\":\\"p1\\",\\"week_start\\":\\"2026-10-19\\"}"}}]},
                  "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":191,"completion_tokens":50}}"""));

        LlmResponse response = client.chat(LlmRequest.withTools(LlmPurpose.AGENT, ASK, List.of(METRICS_TOOL)));

        FakeProvider.Received sent = server.requests.get(0);
        assertThat(sent.body().path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        assertThat(sent.body().path("reasoning_effort").asText()).isEqualTo("medium");
        assertThat(sent.body().path("tools").get(0).path("function").path("name").asText()).isEqualTo("get_week_metrics");
        assertThat(sent.body().path("tools").get(0).path("function").path("parameters").path("required").size()).isEqualTo(2);
        assertThat(response.finishReason()).isEqualTo(FinishReason.TOOL_CALLS);
        assertThat(response.text()).isNull();
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).id()).isEqualTo("fc_1");
        assertThat(response.toolCalls().get(0).arguments().path("week_start").asText()).isEqualTo("2026-10-19");
    }

    @Test
    void aToolResultTravelsBackAsAToolMessageWithItsCallId() {
        server.replyWith(Reply.ok(OK));
        ToolCall call = new ToolCall("fc_1", "get_week_metrics", json("{\"patient_id\":\"p1\"}"), null);

        client.chat(LlmRequest.withTools(LlmPurpose.AGENT, List.of(LlmMessage.user("metricas"),
                LlmMessage.assistant(null, List.of(call)), LlmMessage.tool("fc_1", "get_week_metrics", "{\"entries\":7}")),
                List.of(METRICS_TOOL)));

        var messages = server.requests.get(0).body().path("messages");
        assertThat(messages.get(1).path("tool_calls").get(0).path("id").asText()).isEqualTo("fc_1");
        assertThat(messages.get(1).path("tool_calls").get(0).path("function").path("arguments").asText())
                .isEqualTo("{\"patient_id\":\"p1\"}");
        assertThat(messages.get(2).path("role").asText()).isEqualTo("tool");
        assertThat(messages.get(2).path("tool_call_id").asText()).isEqualTo("fc_1");
        assertThat(messages.get(2).path("content").asText()).isEqualTo("{\"entries\":7}");
    }

    @Test
    void aRateLimitCarriesWhatTheProviderAskedToWait() {
        server.replyWith(Reply.status(429, "{\"error\":{\"message\":\"Rate limit reached for model ... TPM\"}}",
                Map.of("retry-after", "2")));

        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.RATE_LIMIT);
                    assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(e.provider()).isEqualTo("groq");
                });
    }

    @Test
    void serverErrorsAreUnavailableAndRetryable() {
        server.replyWith(Reply.status(503, "{\"error\":{\"message\":\"Service Unavailable\"}}"));

        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
                    assertThat(e.kind().retryable()).isTrue();
                });
    }

    @Test
    void aSlowProviderTimesOutForReal() {
        server.replyWith(Reply.slow(Duration.ofMillis(800)));
        LlmRequest request = new LlmRequest(LlmPurpose.STRUCTURING, ASK, List.of(), EVENTS_SCHEMA, Duration.ofMillis(150));

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.chat(request))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.TIMEOUT));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(700));
    }

    @Test
    void anOutputThatBreaksTheSchemaIsInvalidOutputAndOtherBadRequestsAreNot() {
        server.replyWith(Reply.status(400, "{\"error\":{\"message\":\"Failed to generate JSON\",\"code\":\"json_validate_failed\",\"failed_generation\":\"{}\"}}"));
        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_OUTPUT));

        server.replyWith(Reply.status(400, "{\"error\":{\"message\":\"messages must not be empty\"}}"));
        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.BAD_REQUEST));

        server.replyWith(Reply.status(413, "{\"error\":{\"message\":\"Request too large for model on tokens per minute\"}}"));
        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind().retryable()).isFalse());
    }

    @Test
    void aRejectedKeyIsAuthAndIsNotRetryable() {
        server.replyWith(Reply.status(401, "{\"error\":{\"message\":\"Invalid API Key\"}}"));

        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.AUTH);
                    assertThat(e.kind().retryable()).isFalse();
                });
    }

    @Test
    void withoutAKeyItFailsBeforeTouchingTheNetwork() {
        GroqClient noKey = new GroqClient(LlmFixtures.properties(server.baseUrl(), "  ", "http://unused", ""), MAPPER);

        assertThat(noKey.configured()).isFalse();
        assertThatThrownBy(() -> noKey.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.MISSING_KEY));
        assertThat(server.requests).isEmpty();
    }

    @Test
    void anUnreadableAnswerIsInvalidOutput() {
        server.replyWith(Reply.ok("esto no es json"));

        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_OUTPUT));
    }

    @Test
    void aDownServerIsUnavailable() {
        server.close();

        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE));
    }

    @Test
    void whatThePersonSeesNeverCarriesTheProviderBodyOrTheKey() {
        server.replyWith(Reply.status(500, "{\"error\":{\"message\":\"internal stack trace gsk_prueba at com.x.Y\"}}"));

        assertThatThrownBy(() -> client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, ASK, EVENTS_SCHEMA)))
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("El servicio de IA no está disponible en este momento.");
                    assertThat(e.getMessage()).doesNotContain("stack").doesNotContain("gsk_");
                    assertThat(e.getCause()).isNull();
                });
    }
}
