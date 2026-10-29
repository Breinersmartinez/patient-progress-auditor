package com.patientprogressauditor.run;

import static com.patientprogressauditor.run.RunTestSupport.DOCTOR;
import static com.patientprogressauditor.run.RunTestSupport.NURSE;
import static com.patientprogressauditor.run.RunTestSupport.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentRunner;
import com.patientprogressauditor.agent.AgentStepRepository;

// El SSE contra un servidor HTTP real (no MockMvc, que no ve el flujo mientras ocurre): el primer evento llega con la
// ejecucion todavia viva, reanudar con Last-Event-ID no pierde ni repite pasos y una ejecucion terminada se reproduce.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "app.seed.demo=true")
class RunEventsTest {

    private record Event(String name, String id, String data) {
    }

    // Lee un flujo SSE en otro hilo y deja cada evento en una cola, para que la prueba espere eventos reales.
    private static final class Stream implements AutoCloseable {
        final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        final HttpResponse<java.io.InputStream> response;
        final Thread reader;
        volatile boolean ended;

        Stream(HttpClient client, HttpRequest request) throws Exception {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String name = null;
                    String id = null;
                    StringBuilder data = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.isEmpty()) {
                            if (name != null || data.length() > 0) {
                                events.add(new Event(name, id, data.toString()));
                            }
                            name = null;
                            id = null;
                            data = new StringBuilder();
                        } else if (line.startsWith("event:")) {
                            name = line.substring(6).trim();
                        } else if (line.startsWith("id:")) {
                            id = line.substring(3).trim();
                        } else if (line.startsWith("data:")) {
                            data.append(line.substring(5).trim());
                        }
                    }
                } catch (IOException e) {
                    // cerrado por la prueba
                }
                ended = true;
            }, "test-sse-reader");
            reader.setDaemon(true);
            reader.start();
        }

        Event next() throws InterruptedException {
            Event event = events.poll(10, TimeUnit.SECONDS);
            assertThat(event).as("llego un evento antes de 10 s").isNotNull();
            return event;
        }

        @Override
        public void close() throws IOException {
            response.body().close();
        }
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @MockitoSpyBean private AgentRunner runner;

    private final HttpClient client = HttpClient.newHttpClient();
    private final CountDownLatch gate1 = new CountDownLatch(1);
    private final CountDownLatch gate2 = new CountDownLatch(1);
    private UUID patient;

    @BeforeEach
    void setUp() {
        patient = RunTestSupport.patient(jdbc, "P-014");
        RunTestSupport.clean(jdbc, patient);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        gate1.countDown();
        gate2.countDown();
        RunTestSupport.awaitNoOpenRuns(jdbc, patient);
        RunTestSupport.clean(jdbc, patient);
    }

    // Un «agente» de cuatro pasos que se detiene en dos puertas que abre la prueba: no hay tiempos que adivinar.
    private void agentWithTwoGates() {
        doAnswer(call -> {
            AgentRun run = call.getArgument(0);
            RunTestSupport.start(runs, run);
            RunTestSupport.step(steps, run.getId(), 1, "Revisando qué semanas tienen registros");
            gate1.await(20, TimeUnit.SECONDS);
            RunTestSupport.step(steps, run.getId(), 2, "Leyendo los registros de la semana");
            RunTestSupport.step(steps, run.getId(), 3, "Calculando las cifras de la semana");
            gate2.await(20, TimeUnit.SECONDS);
            RunTestSupport.step(steps, run.getId(), 4, "Borrador guardado");
            RunTestSupport.complete(runs, run);
            return null;
        }).when(runner).run(any(), any());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String requestRun() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(url("/api/runs")))
                .header("X-Demo-User", NURSE).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"patientId\":\"" + patient + "\",\"weekStart\":\"" + WEEK + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(202);
        return mapper.readTree(response.body()).path("id").asText();
    }

    private HttpRequest.Builder events(String runId, String user) {
        return HttpRequest.newBuilder(URI.create(url("/api/runs/" + runId + "/events?demoUser=" + user))).header("Accept", "text/event-stream");
    }

    private String status(String runId) {
        return jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?::uuid", String.class, runId);
    }

    @Test
    void theFirstEventArrivesWhileTheRunIsStillAliveAndEndClosesTheStream() throws Exception {
        agentWithTwoGates();
        String run = requestRun();

        try (Stream stream = new Stream(client, events(run, NURSE).build())) {
            assertThat(stream.response.statusCode()).isEqualTo(200);
            assertThat(stream.response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
            assertThat(stream.response.headers().firstValue("X-Accel-Buffering")).hasValue("no");
            assertThat(stream.response.headers().firstValue("Cache-Control").orElse("")).contains("no-cache");

            Event first = stream.next();
            // El primer evento esta aqui y la ejecucion NO ha terminado: lo que se ve en vivo es lo que esta pasando.
            assertThat(first.name()).isEqualTo("step");
            assertThat(mapper.readTree(first.data()).path("message").asText()).isEqualTo("Revisando qué semanas tienen registros");
            assertThat(status(run)).isEqualTo("EN_CURSO");
            assertThat(first.id()).isEqualTo(mapper.readTree(first.data()).path("id").asText());

            gate1.countDown();
            assertThat(mapper.readTree(stream.next().data()).path("seq").asInt()).isEqualTo(2);
            assertThat(mapper.readTree(stream.next().data()).path("seq").asInt()).isEqualTo(3);
            assertThat(status(run)).isEqualTo("EN_CURSO");

            gate2.countDown();
            assertThat(mapper.readTree(stream.next().data()).path("seq").asInt()).isEqualTo(4);
            Event end = stream.next();
            assertThat(end.name()).isEqualTo("end");
            JsonNode finalState = mapper.readTree(end.data());
            assertThat(finalState.path("status").asText()).isEqualTo("COMPLETADA");
            assertThat(finalState.path("id").asText()).isEqualTo(run);
            stream.reader.join(5000);
            assertThat(stream.ended).as("el servidor cierra el flujo tras el fin").isTrue();
        }
    }

    @Test
    void reconnectingWithLastEventIdDeliversOnlyWhatIsMissing() throws Exception {
        agentWithTwoGates();
        String run = requestRun();
        List<String> seen = new ArrayList<>();
        String lastId;

        try (Stream first = new Stream(client, events(run, NURSE).build())) {
            Event one = first.next();
            seen.add(one.id());
            gate1.countDown();
            Event two = first.next();
            seen.add(two.id());
            lastId = two.id();
            // Se corta la conexion (cierre de pestana, red caida) con la ejecucion todavia viva.
        }
        assertThat(status(run)).isEqualTo("EN_CURSO");

        gate2.countDown();
        try (Stream second = new Stream(client, events(run, NURSE).header("Last-Event-ID", lastId).build())) {
            Event event;
            while (!"end".equals((event = second.next()).name())) {
                seen.add(event.id());
            }
        }
        // Ni se perdio ni se repitio ningun paso: es exactamente la traza guardada.
        List<String> stored = jdbc.queryForList("SELECT id::text FROM agent_steps WHERE run_id = ?::uuid ORDER BY id", String.class, run);
        assertThat(stored).hasSize(4);
        assertThat(seen).containsExactlyElementsOf(stored);
    }

    @Test
    void aFinishedRunIsReplayedAndTheStreamCloses() throws Exception {
        UUID run = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status, stop_reason) VALUES (?, 'INFORME', ?, ?, ?, ?, 'COMPLETADA', 'EXITO')",
                run, patient, UUID.fromString(NURSE), WEEK, WEEK.plusDays(6));
        RunTestSupport.step(steps, run, 1, "Uno");
        RunTestSupport.step(steps, run, 2, "Dos");

        try (Stream stream = new Stream(client, events(run.toString(), NURSE).build())) {
            assertThat(stream.next().name()).isEqualTo("step");
            assertThat(stream.next().name()).isEqualTo("step");
            assertThat(stream.next().name()).isEqualTo("end");
            stream.reader.join(5000);
            assertThat(stream.ended).isTrue();
        }
    }

    @Test
    void theStreamNeedsANurseAndAnExistingRun() throws Exception {
        UUID run = UUID.randomUUID();
        jdbc.update("INSERT INTO agent_runs (id, run_type, patient_id, requested_by, period_start, period_end, status) VALUES (?, 'INFORME', ?, ?, ?, ?, 'FALLIDA')",
                run, patient, UUID.fromString(NURSE), WEEK, WEEK.plusDays(6));

        assertThat(client.send(events(run.toString(), DOCTOR).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        HttpRequest anonymous = HttpRequest.newBuilder(URI.create(url("/api/runs/" + run + "/events"))).build();
        assertThat(client.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(client.send(events(UUID.randomUUID().toString(), NURSE).build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
        // El parametro demoUser solo vale en la ruta del flujo, no en las demas.
        HttpRequest other = HttpRequest.newBuilder(URI.create(url("/api/runs/" + run + "?demoUser=" + NURSE))).build();
        assertThat(client.send(other, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }
}
