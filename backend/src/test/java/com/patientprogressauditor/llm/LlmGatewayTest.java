package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.ASK;
import static com.patientprogressauditor.llm.LlmFixtures.EVENTS_SCHEMA;
import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static com.patientprogressauditor.llm.ScriptedClient.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.llm.LlmException.Kind;

class LlmGatewayTest {

    private final List<Duration> slept = new ArrayList<>();
    private final LlmProperties properties = new LlmProperties();
    private final LlmRequest request = LlmRequest.structured(LlmPurpose.AGENT, ASK, EVENTS_SCHEMA);

    private LlmGateway gateway(ScriptedClient... clients) {
        return new LlmGateway(List.of(clients), properties, slept::add, MAPPER);
    }

    private static LlmException fail(String provider, Kind kind) {
        return new LlmException(kind, provider, "HTTP 500 detalle-tecnico-secreto");
    }

    private static LlmException rateLimited(String provider, Duration retryAfter) {
        return new LlmException(Kind.RATE_LIMIT, provider, retryAfter, "429");
    }

    private static List<String> noProblems(JsonNode json) {
        return List.of();
    }

    @Test
    void thePrimaryAnswersAndTheCallIsRegistered() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(answer("{}", 1000, 500));
        ScriptedClient gemini = new ScriptedClient("gemini", true);

        LlmResult result = gateway(groq, gemini).complete(request);

        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.model()).isEqualTo("openai/gpt-oss-120b");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.tokensIn()).isEqualTo(1000);
        assertThat(result.tokensOut()).isEqualTo(500);
        // (0,15 x 1000 + 0,60 x 500) / 1 000 000
        assertThat(result.costUsd()).isEqualByComparingTo(new BigDecimal("0.000450"));
        assertThat(result.attempts()).hasSize(1);
        assertThat(result.attempts().get(0).succeeded()).isTrue();
        assertThat(gemini.calls).isEmpty();
        assertThat(slept).isEmpty();
    }

    @Test
    void aRateLimitRetriesWithOneTwoAndFourSecondsAndRecovers() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(fail("groq", Kind.RATE_LIMIT), 3).then(answer("{}", 10, 5));

        LlmResult result = gateway(groq, new ScriptedClient("gemini", true)).complete(request);

        assertThat(slept).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(groq.calls).hasSize(4);
        assertThat(result.provider()).isEqualTo("groq");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.attempts()).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = { "RATE_LIMIT", "UNAVAILABLE", "TIMEOUT" })
    void eachTransientFailureRetriesFourTimesThenSwitchesToTheBackup(Kind kind) {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(fail("groq", kind), 1);
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 8, 4));

        LlmResult result = gateway(groq, gemini).complete(request);

        assertThat(groq.calls).hasSize(4);
        assertThat(slept).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.model()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.attempts()).hasSize(5);
        assertThat(result.attempts().get(3).failure()).isEqualTo(kind);
        assertThat(result.attempts().get(4).provider()).isEqualTo("gemini");
    }

    @Test
    void theBackupGetsTheSameConversationSoItCanTakeOverMidLoop() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(fail("groq", Kind.UNAVAILABLE), 1);
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 1, 1));

        gateway(groq, gemini).complete(request);

        assertThat(gemini.calls.get(0)).isSameAs(request);
    }

    @Test
    void aLongWaitRequestedByTheProviderSwitchesAtOnceWithoutSleeping() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(rateLimited("groq", Duration.ofSeconds(30)));
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 1, 1));

        LlmResult result = gateway(groq, gemini).complete(request);

        assertThat(slept).isEmpty();
        assertThat(groq.calls).hasSize(1);
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void aShortWaitRequestedByTheProviderIsRespectedWhenItIsLongerThanTheBackoff() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(rateLimited("groq", Duration.ofSeconds(3)))
                .then(rateLimited("groq", Duration.ofMillis(200))).then(answer("{}", 1, 1));

        gateway(groq, new ScriptedClient("gemini", true)).complete(request);

        // Primera espera: max(1 s, 3 s) = 3 s. Segunda: max(2 s, 0,2 s) = 2 s.
        assertThat(slept).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(2));
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, names = { "AUTH", "BAD_REQUEST", "INVALID_OUTPUT" })
    void aFailureThatRetryingCannotFixGoesStraightToTheBackup(Kind kind) {
        ScriptedClient groq = new ScriptedClient("groq", true).then(fail("groq", kind));
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 1, 1));

        LlmResult result = gateway(groq, gemini).complete(request);

        assertThat(groq.calls).hasSize(1);
        assertThat(slept).isEmpty();
        assertThat(result.provider()).isEqualTo("gemini");
    }

    @Test
    void aPrimaryWithoutAKeyIsSkippedWithoutBeingCalled() {
        ScriptedClient groq = new ScriptedClient("groq", false);
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 1, 1));

        LlmResult result = gateway(groq, gemini).complete(request);

        assertThat(groq.calls).isEmpty();
        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.attempts().get(0).failure()).isEqualTo(Kind.MISSING_KEY);
    }

    @Test
    void whenNobodyAnswersTheMessageIsHonestAndHasNoTechnicalDetail() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(fail("groq", Kind.RATE_LIMIT), 1);
        ScriptedClient gemini = new ScriptedClient("gemini", true).thenAlways(fail("gemini", Kind.TIMEOUT), 1);

        assertThatThrownBy(() -> gateway(groq, gemini).complete(request))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("No se pudo obtener respuesta de la IA. "
                            + "Groq: El servicio de IA recibió demasiadas solicitudes y pidió esperar. "
                            + "Gemini: El servicio de IA tardó demasiado en responder. Intente de nuevo en unos minutos.");
                    assertThat(e.getMessage()).doesNotContain("HTTP").doesNotContain("secreto");
                    assertThat(e.attempts()).hasSize(8);
                });
        assertThat(slept).hasSize(6);
    }

    @Test
    void withoutAnyKeyItSaysWhatToDefineInsteadOfFailingRaw() {
        assertThatThrownBy(() -> gateway(new ScriptedClient("groq", false), new ScriptedClient("gemini", false)).complete(request))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> {
                    assertThat(e.getMessage()).contains("falta la llave").contains("GROQ_API_KEY");
                    assertThat(e.attempts()).extracting(Attempt::failure).containsOnly(Kind.MISSING_KEY);
                });
    }

    @Test
    void withNoBackupConfiguredItFailsAfterThePrimary() {
        properties.setFallback("none");
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(fail("groq", Kind.UNAVAILABLE), 1);
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 1, 1));

        assertThatThrownBy(() -> gateway(groq, gemini).complete(request)).isInstanceOf(LlmUnavailableException.class);
        assertThat(gemini.calls).isEmpty();
    }

    @Test
    void changingTheVariablesSwapsTheOrderWithoutTouchingCode() {
        properties.setPrimary("gemini");
        properties.setFallback("groq");
        ScriptedClient groq = new ScriptedClient("groq", true).then(answer("{}", 1, 1));
        ScriptedClient gemini = new ScriptedClient("gemini", true).then(answer("{}", 1, 1));

        LlmResult result = gateway(groq, gemini).complete(request);

        assertThat(result.provider()).isEqualTo("gemini");
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(groq.calls).isEmpty();
    }

    @Test
    void anUnknownProviderNameStopsTheStartWithAClearMessage() {
        properties.setPrimary("openai");

        assertThatThrownBy(() -> gateway(new ScriptedClient("groq", true), new ScriptedClient("gemini", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_PROVIDER=openai")
                .hasMessageContaining("groq, gemini");
    }

    @Test
    void theSameProviderAsPrimaryAndBackupIsUsedOnce() {
        properties.setFallback("groq");

        assertThat(gateway(new ScriptedClient("groq", true), new ScriptedClient("gemini", true)).providers())
                .extracting(LlmClient::id).containsExactly("groq");
    }

    @Test
    void anUnknownModelCostsNullAndNotZero() {
        assertThat(LlmPricing.cost("groq", "modelo-nuevo", 100, 100)).isNull();
    }

    // --- salida estructurada ---

    private static List<String> needsEvents(JsonNode json) {
        return json.has("events") ? List.of() : List.of("falta el campo events");
    }

    @Test
    void aValidAnswerPassesWithoutRetry() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(answer("{\"events\":[]}", 100, 20));

        StructuredResult result = gateway(groq, new ScriptedClient("gemini", true)).structured(request, LlmGatewayTest::needsEvents);

        assertThat(result.json().has("events")).isTrue();
        assertThat(result.corrected()).isFalse();
        assertThat(groq.calls).hasSize(1);
    }

    @Test
    void anInvalidAnswerIsRetriedOnceWithTheErrorAndThenAccepted() {
        ScriptedClient groq = new ScriptedClient("groq", true).then(answer("{\"otra\":1}", 100, 20))
                .then(answer("{\"events\":[]}", 130, 25));

        StructuredResult result = gateway(groq, new ScriptedClient("gemini", true)).structured(request, LlmGatewayTest::needsEvents);

        assertThat(result.corrected()).isTrue();
        assertThat(result.tokensIn()).isEqualTo(230);
        assertThat(result.tokensOut()).isEqualTo(45);
        assertThat(groq.calls).hasSize(2);
        List<LlmMessage> sent = groq.calls.get(1).messages();
        assertThat(sent).hasSize(ASK.size() + 2);
        assertThat(sent.get(sent.size() - 2).content()).isEqualTo("{\"otra\":1}");
        assertThat(sent.get(sent.size() - 1).content()).contains("falta el campo events").contains("JSON corregido");
        assertThat(request.messages()).hasSize(ASK.size());
    }

    @Test
    void anAnswerThatStaysInvalidDegradesInsteadOfLoopingOrShowingAnError() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(answer("esto no es json", 100, 20), 1);

        assertThatThrownBy(() -> gateway(groq, new ScriptedClient("gemini", true)).structured(request, LlmGatewayTest::needsEvents))
                .isInstanceOfSatisfying(InvalidOutputException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo("La IA devolvió una respuesta que no cumple el formato esperado.");
                    assertThat(e.lastText()).isEqualTo("esto no es json");
                    assertThat(e.problems()).containsExactly("no es JSON válido");
                    assertThat(e.attempts()).hasSize(2);
                });
        assertThat(groq.calls).hasSize(2);
    }

    @Test
    void anEmptyAnswerCountsAsInvalid() {
        ScriptedClient groq = new ScriptedClient("groq", true).thenAlways(answer("  ", 10, 0), 1);

        assertThatThrownBy(() -> gateway(groq, new ScriptedClient("gemini", true)).structured(request, LlmGatewayTest::noProblems))
                .isInstanceOfSatisfying(InvalidOutputException.class,
                        e -> assertThat(e.problems()).containsExactly("la respuesta vino vacía"));
    }
}
