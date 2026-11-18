package com.patientprogressauditor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.patientprogressauditor.llm.LlmException.Kind;

class TranscriptionGatewayTest {

    // Un transcriptor con guion: devuelve en orden lo programado (un resultado o un fallo) y anota las esperas pedidas.
    private static final class Scripted implements Transcriber {
        final Deque<Object> script = new ArrayDeque<>();
        boolean configured = true;
        int calls;

        Scripted then(Object outcome) {
            script.add(outcome);
            return this;
        }

        @Override
        public String id() {
            return "groq";
        }

        @Override
        public String model() {
            return "whisper-large-v3-turbo";
        }

        @Override
        public boolean configured() {
            return configured;
        }

        @Override
        public String keyVariable() {
            return "GROQ_API_KEY";
        }

        @Override
        public Transcription transcribe(byte[] audio, AudioFormat format, Duration timeout) {
            calls++;
            Object next = script.poll();
            if (next instanceof LlmException e) {
                throw e;
            }
            return (Transcription) next;
        }
    }

    private final List<Duration> waits = new ArrayList<>();

    private TranscriptionGateway gateway(Scripted transcriber) {
        return new TranscriptionGateway(transcriber, new LlmProperties(), waits::add);
    }

    private static final byte[] AUDIO = { 1, 2, 3 };

    @Test
    void aSuccessfulTranscriptionIsChargedByAudioTimeWithTheTenSecondMinimum() {
        TranscriptionGateway.Result result = gateway(new Scripted().then(new Transcription("Comió poco.", 8.579)))
                .transcribe(AUDIO, AudioFormat.WEBM);

        assertThat(result.text()).isEqualTo("Comió poco.");
        assertThat(result.model()).isEqualTo("whisper-large-v3-turbo");
        // 0,04 USD por hora × 10 s (Groq cobra al menos 10 s) = 0,000111
        assertThat(result.costUsd()).isEqualByComparingTo("0.000111");
        assertThat(result.attempts()).singleElement().satisfies(a -> {
            assertThat(a.succeeded()).isTrue();
            assertThat(a.audioSeconds()).isEqualTo(8.579);
        });
    }

    @Test
    void aFallenProviderIsRetriedWithTheSameWaitsAsTheChat() {
        Scripted transcriber = new Scripted().then(new LlmException(Kind.UNAVAILABLE, "groq", "HTTP 503 x"))
                .then(new Transcription("Durmió bien.", 120.0));

        TranscriptionGateway.Result result = gateway(transcriber).transcribe(AUDIO, AudioFormat.OGG);

        assertThat(transcriber.calls).isEqualTo(2);
        assertThat(waits).containsExactly(Duration.ofSeconds(1));
        assertThat(result.attempts()).extracting(Attempt::failure).containsExactly(Kind.UNAVAILABLE, null);
        // 0,04 × 120 / 3 600 = 0,001333
        assertThat(result.costUsd()).isEqualByComparingTo("0.001333");
    }

    @Test
    void aRejectedKeyIsNotRetriedAndTheMessageIsHonestWithoutTechnicalDetail() {
        Scripted transcriber = new Scripted().then(new LlmException(Kind.AUTH, "groq", "HTTP 401 {\"error\":\"Invalid API Key\"}"));

        assertThatThrownBy(() -> gateway(transcriber).transcribe(AUDIO, AudioFormat.WEBM))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> {
                    assertThat(e.getMessage()).startsWith("No se pudo transcribir el audio.").doesNotContain("HTTP", "Invalid");
                    assertThat(e.attempts()).singleElement().satisfies(a -> assertThat(a.failure()).isEqualTo(Kind.AUTH));
                });
        assertThat(transcriber.calls).isEqualTo(1);
    }

    @Test
    void withoutAKeyItSaysSoAndDoesNotCall() {
        Scripted transcriber = new Scripted();
        transcriber.configured = false;

        assertThatThrownBy(() -> gateway(transcriber).transcribe(AUDIO, AudioFormat.WEBM))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> {
                    assertThat(e.getMessage()).contains("falta la llave de Groq (GROQ_API_KEY)");
                    assertThat(e.attempts()).singleElement().satisfies(a -> assertThat(a.failure()).isEqualTo(Kind.MISSING_KEY));
                });
        assertThat(transcriber.calls).isZero();
    }

    @Test
    void retriesStopAfterTheBackoffListAndTheCostStaysEmpty() {
        Scripted transcriber = new Scripted();
        for (int i = 0; i < 5; i++) {
            transcriber.then(new LlmException(Kind.RATE_LIMIT, "groq", "HTTP 429"));
        }

        assertThatThrownBy(() -> gateway(transcriber).transcribe(AUDIO, AudioFormat.WEBM))
                .isInstanceOfSatisfying(LlmUnavailableException.class, e -> assertThat(e.attempts()).hasSize(4).noneMatch(Attempt::succeeded));
        assertThat(transcriber.calls).isEqualTo(4);
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void anUnknownDurationLeavesTheCostEmptyNotZero() {
        TranscriptionGateway.Result result = gateway(new Scripted().then(new Transcription("Sin fiebre.", null)))
                .transcribe(AUDIO, AudioFormat.MP4);

        assertThat(result.costUsd()).isNull();
    }

    @Test
    void audioPricesComeFromTheListAndUnknownModelsHaveNoPrice() {
        assertThat(LlmPricing.audioCost("groq", "whisper-large-v3-turbo", 8.579)).isEqualByComparingTo("0.000111");
        assertThat(LlmPricing.audioCost("groq", "whisper-large-v3-turbo", 120)).isEqualByComparingTo("0.001333");
        assertThat(LlmPricing.audioCost("groq", "whisper-large-v3", 30)).isEqualByComparingTo("0.000925");
        assertThat(LlmPricing.audioCost("groq", "otro-modelo", 30)).isNull();
        assertThat(LlmPricing.audioCost("groq", "whisper-large-v3-turbo", 0)).isNull();
        assertThat(LlmPricing.audioCost("groq", "whisper-large-v3-turbo", Double.NaN)).isNull();
    }
}
