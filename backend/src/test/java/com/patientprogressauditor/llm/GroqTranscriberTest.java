package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.patientprogressauditor.llm.FakeProvider.Reply;
import com.patientprogressauditor.llm.LlmException.Kind;

class GroqTranscriberTest {

    private static final byte[] AUDIO = "OggS-audio-sintetico-MARCADOR".getBytes(StandardCharsets.UTF_8);
    private static final String OK = """
            {"task":"transcribe","language":"spanish","duration":8.579312128,
             "text":" Paciente con náuseas leves por la mañana.","segments":[],"x_groq":{"id":"req_x"}}""";

    private FakeProvider server;
    private GroqTranscriber transcriber;

    @BeforeEach
    void start() throws Exception {
        server = new FakeProvider();
        transcriber = new GroqTranscriber(LlmFixtures.properties(server.baseUrl(), "gsk_prueba", "http://unused", ""), MAPPER);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private String sentBody() {
        return new String(server.requests.get(0).raw(), StandardCharsets.UTF_8);
    }

    @Test
    void sendsAMultipartWithTheModelSpanishAndTheAudioAndReadsTextAndDuration() {
        server.replyWith(Reply.ok(OK));

        Transcription result = transcriber.transcribe(AUDIO, AudioFormat.OGG, Duration.ofSeconds(5));

        FakeProvider.Received sent = server.requests.get(0);
        assertThat(sent.path()).isEqualTo("/audio/transcriptions");
        assertThat(sent.header("Authorization")).isEqualTo("Bearer gsk_prueba");
        assertThat(sent.header("Content-Type")).startsWith("multipart/form-data; boundary=");
        assertThat(sentBody()).contains("name=\"model\"\r\n\r\nwhisper-large-v3-turbo\r\n", "name=\"language\"\r\n\r\nes\r\n",
                "name=\"response_format\"\r\n\r\nverbose_json\r\n", "name=\"temperature\"\r\n\r\n0\r\n",
                "filename=\"dictado.ogg\"\r\nContent-Type: audio/ogg\r\n\r\nOggS-audio-sintetico-MARCADOR\r\n");
        assertThat(result.text()).isEqualTo("Paciente con náuseas leves por la mañana.");
        assertThat(result.seconds()).isEqualTo(8.579312128);
    }

    @Test
    void theModelComesFromConfigurationAndEachFormatKeepsItsExtension() {
        LlmProperties properties = LlmFixtures.properties(server.baseUrl(), "gsk_prueba", "http://unused", "");
        properties.getGroq().setTranscriptionModel("whisper-large-v3");
        server.replyWith(Reply.ok(OK));

        new GroqTranscriber(properties, MAPPER).transcribe(AUDIO, AudioFormat.MP4, Duration.ofSeconds(5));

        assertThat(sentBody()).contains("whisper-large-v3\r\n", "filename=\"dictado.mp4\"\r\nContent-Type: audio/mp4");
    }

    @Test
    void withoutDurationTheTextStillArrivesAndTheDurationIsUnknown() {
        server.replyWith(Reply.ok("{\"text\":\"Comió poco.\"}"));

        Transcription result = transcriber.transcribe(AUDIO, AudioFormat.WEBM, Duration.ofSeconds(5));

        assertThat(result.text()).isEqualTo("Comió poco.");
        assertThat(result.seconds()).isNull();
    }

    @Test
    void withoutAKeyItDoesNotCallGroq() {
        GroqTranscriber noKey = new GroqTranscriber(LlmFixtures.properties(server.baseUrl(), "", "http://unused", ""), MAPPER);

        assertThat(noKey.configured()).isFalse();
        assertThatThrownBy(() -> noKey.transcribe(AUDIO, AudioFormat.WEBM, Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.MISSING_KEY));
        assertThat(server.requests).isEmpty();
    }

    @Test
    void httpFailuresBecomeTheSameCategoriesAsTheChatAndNeverCarryTheAudio() {
        record Case(int status, Kind kind) {
        }
        for (Case c : new Case[] { new Case(429, Kind.RATE_LIMIT), new Case(401, Kind.AUTH), new Case(413, Kind.BAD_REQUEST),
                new Case(503, Kind.UNAVAILABLE) }) {
            server.replyWith(Reply.status(c.status(), "{\"error\":{\"message\":\"fallo\"}}", Map.of("Retry-After", "2")));

            assertThatThrownBy(() -> transcriber.transcribe(AUDIO, AudioFormat.WEBM, Duration.ofSeconds(5)))
                    .isInstanceOfSatisfying(LlmException.class, e -> {
                        assertThat(e.kind()).isEqualTo(c.kind());
                        assertThat(e.detail()).startsWith("HTTP " + c.status()).doesNotContain("MARCADOR");
                    });
        }
    }

    @Test
    void anAnswerWithoutTextIsAnInvalidOutput() {
        server.replyWith(Reply.ok("{\"duration\":3.0}"));

        assertThatThrownBy(() -> transcriber.transcribe(AUDIO, AudioFormat.WEBM, Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.INVALID_OUTPUT));
    }

    @Test
    void aSlowAnswerIsATimeout() {
        server.replyWith(Reply.slow(Duration.ofSeconds(2)));

        assertThatThrownBy(() -> transcriber.transcribe(AUDIO, AudioFormat.WEBM, Duration.ofMillis(300)))
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.kind()).isEqualTo(Kind.TIMEOUT));
    }

    @Test
    void theThreeBrowserFormatsAreRecognizedWithTheirCodecsAndNothingElse() {
        assertThat(AudioFormat.fromContentType("audio/webm;codecs=opus")).contains(AudioFormat.WEBM);
        assertThat(AudioFormat.fromContentType("audio/ogg; codecs=opus")).contains(AudioFormat.OGG);
        assertThat(AudioFormat.fromContentType("AUDIO/MP4")).contains(AudioFormat.MP4);
        assertThat(AudioFormat.fromContentType("audio/wav")).isEmpty();
        assertThat(AudioFormat.fromContentType("application/json")).isEmpty();
        assertThat(AudioFormat.fromContentType(null)).isEmpty();
    }
}
