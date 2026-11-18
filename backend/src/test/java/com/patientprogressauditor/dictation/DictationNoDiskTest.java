package com.patientprogressauditor.dictation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.llm.LangfuseExporter;
import com.patientprogressauditor.llm.Transcriber;
import com.patientprogressauditor.llm.Transcription;

// La condicion del equipo, contra el Tomcat de verdad (MockMvc no pasa por el): un dictado de 2 MB entra, se transcribe y
// ningun archivo del directorio temporal (donde Tomcat escribiria un cuerpo o un multipart) contiene el audio. Y uno mas
// grande que el tope se rechaza por su Content-Length sin leerlo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.seed.demo=true")
class DictationNoDiskTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String MARK = "AUDIO-EN-DISCO-5d2e";

    @MockitoBean private Transcriber transcriber;
    @MockitoBean private LangfuseExporter langfuse;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @LocalServerPort private int port;

    private final List<UUID> runIds = new ArrayList<>();
    private UUID patient;

    @BeforeEach
    void setUp() {
        patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        when(transcriber.id()).thenReturn("groq");
        when(transcriber.model()).thenReturn("whisper-large-v3-turbo");
        when(transcriber.configured()).thenReturn(true);
        when(transcriber.transcribe(any(), any(), any())).thenReturn(new Transcription("Durmió bien.", 30.0));
    }

    @AfterEach
    void clean() {
        for (UUID run : runIds) {
            jdbc.update("DELETE FROM agent_steps WHERE run_id = ?", run);
            jdbc.update("DELETE FROM agent_runs WHERE id = ?", run);
        }
    }

    private HttpResponse<String> post(byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/patients/" + patient + "/entries/transcribe"))
                .header("X-Demo-User", NURSE).header("Content-Type", "audio/webm;codecs=opus")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    // Un audio de `size` bytes con la marca repetida: si quedara en un archivo, la marca aparece.
    private static byte[] audio(int size) {
        byte[] mark = MARK.getBytes(StandardCharsets.US_ASCII);
        byte[] audio = new byte[size];
        for (int i = 0; i < size; i++) {
            audio[i] = mark[i % mark.length];
        }
        return audio;
    }

    // Archivos del directorio temporal modificados desde `since` que contienen la marca. Lo que no se puede leer (directorios
    // ajenos sin permiso) se salta UNO por UNO: un recorrido que se cortara en el primero no veria nada y la prueba pasaria
    // en falso (paso en la primera version: se detecto rompiendo el codigo a proposito).
    private static List<Path> filesWithTheMark(long since) throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(tmp, java.util.Set.of(), 6, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && attrs.lastModifiedTime().toMillis() >= since && attrs.size() >= MARK.length()
                        && attrs.size() < 50_000_000) {
                    try {
                        if (new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains(MARK)) {
                            found.add(file);
                        }
                    } catch (IOException | SecurityException e) {
                        // Un archivo ajeno que no se puede leer no es nuestro.
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException e) {
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        return found;
    }

    @Test
    void aTwoMegabyteDictationIsTranscribedWithoutTouchingTheDisk() throws Exception {
        long since = System.currentTimeMillis() - 1000;

        HttpResponse<String> response = post(audio(2 * 1024 * 1024));

        assertThat(response.statusCode()).isEqualTo(200);
        runIds.add(UUID.fromString(mapper.readTree(response.body()).path("runId").asText()));
        assertThat(mapper.readTree(response.body()).path("text").asText()).isEqualTo("Durmió bien.");
        assertThat(filesWithTheMark(since)).isEmpty();
    }

    @Test
    void anAudioOverTheLimitIsRejectedByItsDeclaredLength() throws Exception {
        HttpResponse<String> response = post(audio(DictationService.MAX_AUDIO_BYTES + 1024));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(mapper.readTree(response.body()).path("message").asText()).startsWith("El audio es demasiado grande");
    }
}
