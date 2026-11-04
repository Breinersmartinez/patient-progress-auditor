package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.patientprogressauditor.llm.FakeProvider.Reply;
import com.patientprogressauditor.llm.LlmException.Kind;

class LangfuseExporterTest {

    private static final String SECRET_DETAIL = "Groq respondio: {\"error\":\"vomito de Maria Perez, cama 4\"}";

    private FakeProvider server;
    private LangfuseExporter exporter;

    @BeforeEach
    void start() throws Exception {
        server = new FakeProvider();
    }

    @AfterEach
    void stop() {
        if (exporter != null) {
            exporter.close();
        }
        server.close();
    }

    private static LangfuseProperties properties(String baseUrl, String publicKey, String secretKey) {
        LangfuseProperties properties = new LangfuseProperties();
        properties.setBaseUrl(baseUrl);
        properties.setPublicKey(publicKey);
        properties.setSecretKey(secretKey);
        properties.setEnvironment("pruebas");
        properties.setTimeout(Duration.ofMillis(500));
        return properties;
    }

    private LangfuseExporter exporter(String publicKey, String secretKey) {
        exporter = new LangfuseExporter(properties(server.baseUrl(), publicKey, secretKey), MAPPER);
        return exporter;
    }

    private static LangfuseRun run() {
        return new LangfuseRun(UUID.randomUUID(), "INFORME", "COMPLETADA", "EXITO", Instant.parse("2026-11-03T15:00:00Z"), 2000,
                List.of(new Attempt("groq", "openai/gpt-oss-120b", 1, Kind.RATE_LIMIT, 300, SECRET_DETAIL),
                        new Attempt("gemini", "gemini-3.5-flash-lite", 1, null, 1500, null, 1000, 200)));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long limit = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < limit) {
            Thread.sleep(10);
        }
    }

    @Test
    void sendsTheTraceToTheOtlpEndpointWithBasicAuthAndRealTimeIngestion() throws Exception {
        exporter("pk-lf-prueba", "sk-lf-prueba").export(run());
        await(() -> !server.requests.isEmpty());

        assertThat(server.requests).hasSize(1);
        FakeProvider.Received sent = server.requests.get(0);
        assertThat(sent.path()).isEqualTo("/api/public/otel/v1/traces");
        assertThat(sent.header("Authorization"))
                .isEqualTo("Basic " + Base64.getEncoder().encodeToString("pk-lf-prueba:sk-lf-prueba".getBytes(StandardCharsets.UTF_8)));
        assertThat(sent.header("x-langfuse-ingestion-version")).isEqualTo("4");
        assertThat(sent.header("Content-Type")).startsWith("application/json");
        assertThat(sent.body().path("resourceSpans").path(0).path("scopeSpans").path(0).path("spans")).hasSize(3);
    }

    @Test
    void whatIsSentCarriesNeitherTheTechnicalDetailNorTheKeys() throws Exception {
        exporter("pk-lf-prueba", "sk-lf-prueba").export(run());
        await(() -> !server.requests.isEmpty());

        String body = server.requests.get(0).body().toString();
        assertThat(body).doesNotContain("Maria", "vomito", "cama", "respondio", "pk-lf-prueba", "sk-lf-prueba");
        assertThat(body).contains("RATE_LIMIT").contains("pruebas");
    }

    @Test
    void aTrailingSlashInTheBaseUrlDoesNotBreakThePath() throws Exception {
        exporter = new LangfuseExporter(properties(server.baseUrl() + "/", "pk", "sk"), MAPPER);
        exporter.export(run());
        await(() -> !server.requests.isEmpty());

        assertThat(server.requests.get(0).path()).isEqualTo("/api/public/otel/v1/traces");
    }

    @Test
    void withoutBothKeysItDoesNothing() throws Exception {
        for (String[] keys : new String[][] { { "", "" }, { "pk-lf-prueba", "" }, { "", "sk-lf-prueba" }, { "  ", "  " } }) {
            LangfuseExporter off = new LangfuseExporter(properties(server.baseUrl(), keys[0], keys[1]), MAPPER);
            assertThat(off.enabled()).isFalse();
            off.export(run());
            off.close();
        }
        Thread.sleep(300);

        assertThat(server.requests).isEmpty();
    }

    @Test
    void aNullRunIsIgnored() {
        assertThatCode(() -> exporter("pk", "sk").export(null)).doesNotThrowAnyException();
    }

    // La peor llamada a export(), con las corridas ya construidas. Con el envio en el hilo de quien llama cada llamada tardaria
    // lo que tarde el host (aqui, el tiempo maximo de 500 ms); en segundo plano tarda lo de encolar (milisegundos).
    private long worstExportMillis(int runs) {
        List<LangfuseRun> prepared = IntStream.range(0, runs).mapToObj(i -> run()).toList();
        long worst = 0;
        for (LangfuseRun run : prepared) {
            long start = System.nanoTime();
            exporter.export(run);
            worst = Math.max(worst, (System.nanoTime() - start) / 1_000_000);
        }
        return worst;
    }

    @Test
    void aHostThatDoesNotAnswerNeverDelaysTheCaller() {
        server.replyWith(Reply.slow(Duration.ofSeconds(2)));
        exporter("pk", "sk");

        // 200 envios contra un host que tarda 2 s, con cola de 50: ninguna llamada espera ni se desborda.
        assertThat(worstExportMillis(200)).isLessThan(250);
    }

    @Test
    void anUnreachableHostNeitherThrowsNorDelaysTheCaller() {
        exporter = new LangfuseExporter(properties("http://127.0.0.1:1", "pk", "sk"), MAPPER);

        assertThat(worstExportMillis(20)).isLessThan(250);
    }

    @Test
    void aMalformedBaseUrlNeitherThrowsNorStopsTheExporter() throws Exception {
        exporter = new LangfuseExporter(properties("esto no es una url", "pk", "sk"), MAPPER);

        assertThatCode(() -> exporter.export(run())).doesNotThrowAnyException();
        Thread.sleep(100);
        assertThatCode(() -> exporter.export(run())).doesNotThrowAnyException();
    }

    @Test
    void aRejectedTraceIsDroppedAndTheNextOneStillGoesOut() throws Exception {
        server.replyWith(Reply.status(401, "{\"message\":\"Invalid credentials\"}"));
        exporter("pk", "sk");

        exporter.export(run());
        exporter.export(run());
        await(() -> server.requests.size() >= 2);

        assertThat(server.requests).hasSize(2);
    }

    @Test
    void closingGivesWhatIsQueuedAMomentToLeave() {
        exporter("pk", "sk");
        for (int i = 0; i < 3; i++) {
            exporter.export(run());
        }

        exporter.close();

        assertThat(server.requests).hasSize(3);
    }

    @Test
    void afterClosingItDropsNewTracesWithoutThrowing() {
        exporter("pk", "sk").close();

        assertThatCode(() -> exporter.export(run())).doesNotThrowAnyException();
        assertThat(server.requests).isEmpty();
    }
}
