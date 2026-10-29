package com.patientprogressauditor.llm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

// Un proveedor falso de verdad: un servidor HTTP local (de la JDK) al que los adaptadores le hablan por la red.
// Asi un 429, un 503 o un tiempo agotado son respuestas HTTP reales y no un objeto simulado dentro del adaptador.
final class FakeProvider implements AutoCloseable {

    record Received(String path, Map<String, List<String>> headers, JsonNode body) {

        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }
    }

    record Reply(int status, String body, Map<String, String> headers, Duration delay) {

        static Reply ok(String body) {
            return new Reply(200, body, Map.of(), Duration.ZERO);
        }

        static Reply status(int status, String body) {
            return new Reply(status, body, Map.of(), Duration.ZERO);
        }

        static Reply status(int status, String body, Map<String, String> headers) {
            return new Reply(status, body, headers, Duration.ZERO);
        }

        static Reply slow(Duration delay) {
            return new Reply(200, "{}", Map.of(), delay);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    final List<Received> requests = new CopyOnWriteArrayList<>();
    volatile Function<Received, Reply> handler = r -> Reply.ok("{}");

    FakeProvider() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            JsonNode body = MAPPER.readTree(exchange.getRequestBody());
            Received received = new Received(exchange.getRequestURI().getPath(), exchange.getRequestHeaders(), body);
            requests.add(received);
            Reply reply = handler.apply(received);
            try {
                Thread.sleep(reply.delay().toMillis());
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException | InterruptedException e) {
                // El cliente ya se fue (tiempo agotado): no hay a quien responder.
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    FakeProvider replyWith(Function<Received, Reply> handler) {
        this.handler = handler;
        return this;
    }

    FakeProvider replyWith(Reply reply) {
        return replyWith(r -> reply);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
