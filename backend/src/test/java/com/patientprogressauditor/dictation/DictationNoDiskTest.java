package com.patientprogressauditor.dictation;

import com.patientprogressauditor.support.TestAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
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
                .header("Authorization", TestAuth.bearer("camilo.torres@example.test", "ENFERMERIA")).header("Content-Type", "audio/webm;codecs=opus")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    // La respuesta HTTP hasta el final de su cuerpo JSON (el servidor no la cierra: ver arriba).
    private static String readResponse(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream read = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (true) {
            String text = read.toString(StandardCharsets.UTF_8);
            int headerEnd = text.indexOf("\r\n\r\n");
            if (headerEnd >= 0 && text.indexOf('}', headerEnd) >= 0) {
                return text;
            }
            int n = in.read(buffer);
            if (n < 0) {
                return text;
            }
            read.write(buffer, 0, n);
        }
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

    // Se declara un Content-Length mayor que el tope y solo se envia el primer kilobyte: si el servidor respondiera despues
    // de leer el cuerpo, esta prueba se quedaria esperando. Antes se subian los 5 MB enteros con HttpClient, y cuando el
    // servidor respondia 413 y cerraba, el cliente seguia escribiendo: en CI fallaba con «Broken pipe» y en local pasaba,
    // segun cuanto aceptara la red de golpe. Asi se prueba justo lo que dice el nombre, sin depender de la red.
    @Test
    void anAudioOverTheLimitIsRejectedByItsDeclaredLength() throws Exception {
        String head = "POST /api/patients/" + patient + "/entries/transcribe HTTP/1.1\r\n"
                + "Host: localhost:" + port + "\r\n"
                + "Authorization: " + TestAuth.bearer("camilo.torres@example.test", "ENFERMERIA") + "\r\n"
                + "Content-Type: audio/webm;codecs=opus\r\n"
                + "Content-Length: " + (DictationService.MAX_AUDIO_BYTES + 1024) + "\r\n"
                + "Connection: close\r\n\r\n";
        String response;
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(15_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(audio(1024));
            out.flush();
            // Se lee hasta tener la respuesta entera, sin esperar el cierre: tras responder, Tomcat intenta descartar el
            // resto del cuerpo declarado, que nunca llega, y la conexion queda abierta.
            response = readResponse(socket.getInputStream());
        }

        assertThat(response).startsWith("HTTP/1.1 413");
        String json = response.substring(response.indexOf('{'), response.lastIndexOf('}') + 1);
        assertThat(mapper.readTree(json).path("message").asText()).startsWith("El audio es demasiado grande");
    }
}
