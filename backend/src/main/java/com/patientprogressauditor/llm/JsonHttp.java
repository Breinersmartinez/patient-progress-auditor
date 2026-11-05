package com.patientprogressauditor.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.llm.LlmException.Kind;

// POST con el cliente HTTP de la JDK (sin SDK): permite un tiempo maximo por llamada y leer los encabezados. El cuerpo va
// siempre en memoria (`ofString` u `ofByteArray`): nada se escribe a disco, tampoco el audio de un dictado.
final class JsonHttp {

    record Result(int status, Map<String, java.util.List<String>> headers, String body) {

        String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .flatMap(e -> e.getValue().stream()).findFirst().orElse(null);
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private JsonHttp() {
    }

    static Result post(String provider, String url, Map<String, String> headers, JsonNode body, Duration timeout,
            ObjectMapper mapper) {
        try {
            return postBytes(provider, url, headers, mapper.writeValueAsBytes(body), "application/json", timeout);
        } catch (JsonProcessingException e) {
            throw new LlmException(Kind.BAD_REQUEST, provider, "No se pudo armar la solicitud: " + e.getOriginalMessage());
        }
    }

    static Result postBytes(String provider, String url, Map<String, String> headers, byte[] body, String contentType,
            Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Content-Type", contentType);
        headers.forEach(request::header);
        try {
            request.POST(HttpRequest.BodyPublishers.ofByteArray(body));
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), response.headers().map(), response.body());
        } catch (HttpTimeoutException e) {
            throw new LlmException(Kind.TIMEOUT, provider, "Sin respuesta en " + timeout.toSeconds() + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(Kind.UNAVAILABLE, provider, "Llamada interrumpida");
        } catch (IOException e) {
            throw new LlmException(Kind.UNAVAILABLE, provider, "Fallo de red: " + e.getClass().getSimpleName());
        }
    }

    // Lo que el proveedor pidio esperar, en segundos (encabezado Retry-After), o null.
    static Duration retryAfter(Result result) {
        String value = result.header("Retry-After");
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofMillis((long) (Double.parseDouble(value.trim()) * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Un estado HTTP de error como fallo de la capa: 429 pide esperar, 401/403 es la llave, 408/504 es tiempo, otro 4xx es la
    // solicitud y el resto es caida. El detalle (estado y un trozo del cuerpo del proveedor) es solo para el servidor.
    static LlmException httpFailure(String provider, Result result) {
        int status = result.status();
        String detail = "HTTP " + status + " " + snippet(result.body());
        if (status == 429) {
            return new LlmException(Kind.RATE_LIMIT, provider, retryAfter(result), detail);
        }
        if (status == 401 || status == 403) {
            return new LlmException(Kind.AUTH, provider, detail);
        }
        if (status == 408 || status == 504) {
            return new LlmException(Kind.TIMEOUT, provider, detail);
        }
        if (status >= 400 && status < 500) {
            return new LlmException(Kind.BAD_REQUEST, provider, detail);
        }
        return new LlmException(Kind.UNAVAILABLE, provider, detail);
    }

    static String snippet(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ");
        return flat.length() > 300 ? flat.substring(0, 300) + "…" : flat;
    }
}
