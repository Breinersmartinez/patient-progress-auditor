package com.patientprogressauditor.llm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.llm.LlmException.Kind;

// La unica puerta al modelo para el resto de la aplicacion. Aplica, en este orden:
//  1. el proveedor principal, con reintentos de 1 s, 2 s y 4 s ante 429, caida o tiempo agotado (si el proveedor pide
//     esperar mas que el tope, no se espera);
//  2. el proveedor de respaldo, con lo mismo;
//  3. un mensaje honesto si ninguno responde.
// Cambiar de proveedor es cambiar LLM_PROVIDER y LLM_FALLBACK; nada mas en la aplicacion conoce a Groq ni a Gemini.
@Service
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final List<LlmClient> order;
    private final LlmClient primary;
    private final LlmProperties properties;
    private final Sleeper sleeper;
    private final ObjectMapper mapper;

    public LlmGateway(List<LlmClient> clients, LlmProperties properties, Sleeper sleeper, ObjectMapper mapper) {
        this.properties = properties;
        this.sleeper = sleeper;
        this.mapper = mapper;
        this.primary = find(clients, properties.getPrimary(), "LLM_PROVIDER");
        List<LlmClient> providers = new ArrayList<>(List.of(primary));
        String fallbackId = properties.getFallback();
        if (fallbackId != null && !fallbackId.isBlank() && !fallbackId.equalsIgnoreCase("none")
                && !fallbackId.equalsIgnoreCase(primary.id())) {
            providers.add(find(clients, fallbackId, "LLM_FALLBACK"));
        }
        this.order = List.copyOf(providers);
        log.info("Proveedores de IA: principal {}{}", primary.id(),
                order.size() > 1 ? ", respaldo " + order.get(1).id() : ", sin respaldo");
    }

    private static LlmClient find(List<LlmClient> clients, String id, String variable) {
        return clients.stream().filter(c -> c.id().equalsIgnoreCase(id == null ? "" : id.trim())).findFirst()
                .orElseThrow(() -> new IllegalStateException("%s=%s no es un proveedor conocido (%s)".formatted(variable, id,
                        clients.stream().map(LlmClient::id).collect(Collectors.joining(", ")))));
    }

    // Los proveedores en el orden en que se intentan: sirve al endpoint de estado.
    public List<LlmClient> providers() {
        return order;
    }

    public LlmResult complete(LlmRequest request) {
        List<Attempt> attempts = new ArrayList<>();
        long start = System.nanoTime();
        for (LlmClient client : order) {
            if (outOfTime(request, start)) {
                break;
            }
            String model = client.model(request.purpose());
            if (!client.configured()) {
                attempts.add(new Attempt(client.id(), model, 0, Kind.MISSING_KEY, 0, client.keyVariable() + " no esta definida"));
                continue;
            }
            for (int number = 1;; number++) {
                Duration left = remaining(request, start);
                if (left != null && exhausted(left)) {
                    attempts.add(new Attempt(client.id(), model, number, Kind.TIMEOUT, 0, "Presupuesto de tiempo agotado"));
                    break;
                }
                long t = System.nanoTime();
                try {
                    LlmResponse response = client.chat(shortened(request, left));
                    attempts.add(new Attempt(client.id(), model, number, null, millisSince(t), null, response.tokensIn(), response.tokensOut()));
                    BigDecimal cost = LlmPricing.cost(client.id(), model, response.tokensIn(), response.tokensOut());
                    log.info("IA {} {} {}: {} tokens de entrada, {} de salida, {} ms, intento {}", client.id(), model,
                            request.purpose(), response.tokensIn(), response.tokensOut(), millisSince(t), number);
                    return new LlmResult(response, client.id(), model, client != primary, millisSince(start), cost, attempts);
                } catch (LlmException e) {
                    attempts.add(new Attempt(client.id(), model, number, e.kind(), millisSince(t), e.detail()));
                    log.warn("IA {} {} intento {} fallo ({}): {}", client.id(), model, number, e.kind(), e.detail());
                    Duration wait = waitBeforeRetry(e, number);
                    if (wait == null) {
                        break;
                    }
                    Duration afterWait = remaining(request, start);
                    if (afterWait != null && afterWait.compareTo(wait) <= 0) {
                        // Esperar y reintentar ya no cabe en el tiempo que la persona esta dispuesta a esperar.
                        attempts.add(new Attempt(client.id(), model, number + 1, Kind.TIMEOUT, 0, "Presupuesto de tiempo agotado"));
                        break;
                    }
                    pause(wait);
                }
            }
        }
        throw new LlmUnavailableException(honestMessage(attempts), attempts);
    }

    // Lo que queda del presupuesto total, o null si la solicitud no tiene tope.
    private static Duration remaining(LlmRequest request, long startNanos) {
        return request.budget() == null ? null : request.budget().minus(Duration.ofNanos(System.nanoTime() - startNanos));
    }

    private static boolean exhausted(Duration left) {
        return left.isNegative() || left.isZero();
    }

    private static boolean outOfTime(LlmRequest request, long startNanos) {
        Duration left = remaining(request, startNanos);
        return left != null && exhausted(left);
    }

    // Una llamada nunca dura mas de lo que queda del presupuesto.
    private LlmRequest shortened(LlmRequest request, Duration left) {
        if (left == null) {
            return request;
        }
        Duration own = request.timeout() != null ? request.timeout() : properties.timeoutFor(request.purpose());
        return left.compareTo(own) < 0 ? request.withTimeout(left) : request;
    }

    // null = no se reintenta con este proveedor.
    private Duration waitBeforeRetry(LlmException e, int failedAttempt) {
        List<Duration> backoff = properties.getBackoff();
        if (!e.kind().retryable() || failedAttempt > backoff.size()) {
            return null;
        }
        Duration wait = backoff.get(failedAttempt - 1);
        if (e.retryAfter() != null) {
            if (e.retryAfter().compareTo(properties.getMaxRetryAfter()) > 0) {
                return null;
            }
            wait = e.retryAfter().compareTo(wait) > 0 ? e.retryAfter() : wait;
        }
        return wait;
    }

    private void pause(Duration wait) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Pide JSON y lo valida con `validator` (devuelve la lista de problemas; vacia si esta bien). Si no valida, UN reintento
    // devolviendole al modelo el error; si vuelve a fallar, InvalidOutputException para que quien llama degrade a texto plano.
    public StructuredResult structured(LlmRequest request, Function<JsonNode, List<String>> validator) {
        LlmResult first = complete(request);
        Parsed parsed = parse(first.response().text(), validator);
        if (parsed.problems().isEmpty()) {
            return new StructuredResult(parsed.json(), first, first.tokensIn(), first.tokensOut(), first.costUsd(), false, List.of(),
                    first.attempts());
        }
        log.warn("IA {}: la salida no valida ({}); se reintenta una vez", first.provider(), parsed.problems());
        List<LlmMessage> retry = new ArrayList<>(request.messages());
        retry.add(LlmMessage.assistant(first.response().text(), List.of()));
        retry.add(LlmMessage.user("Tu respuesta no cumple el formato: " + String.join("; ", parsed.problems())
                + ". Devuelve únicamente el JSON corregido."));
        LlmResult second = complete(request.withMessages(retry));
        Parsed again = parse(second.response().text(), validator);
        List<Attempt> all = new ArrayList<>(first.attempts());
        all.addAll(second.attempts());
        if (!again.problems().isEmpty()) {
            log.warn("IA {}: la salida sigue sin validar tras el reintento ({})", second.provider(), again.problems());
            throw new InvalidOutputException("La IA devolvió una respuesta que no cumple el formato esperado.",
                    second.response().text(), again.problems(), all);
        }
        BigDecimal cost = first.costUsd() == null || second.costUsd() == null ? null : first.costUsd().add(second.costUsd());
        return new StructuredResult(again.json(), second, first.tokensIn() + second.tokensIn(),
                first.tokensOut() + second.tokensOut(), cost, true, parsed.problems(), all);
    }

    private record Parsed(JsonNode json, List<String> problems) {
    }

    private Parsed parse(String text, Function<JsonNode, List<String>> validator) {
        if (text == null || text.isBlank()) {
            return new Parsed(null, List.of("la respuesta vino vacía"));
        }
        try {
            JsonNode json = mapper.readTree(text);
            return new Parsed(json, validator.apply(json));
        } catch (JsonProcessingException e) {
            return new Parsed(null, List.of("no es JSON válido"));
        }
    }

    // Que paso y que hacer, en lenguaje claro. Nunca el cuerpo del proveedor ni una llave.
    static String honestMessage(List<Attempt> attempts) {
        List<Attempt> failed = attempts.stream().filter(a -> !a.succeeded()).toList();
        if (!failed.isEmpty() && failed.stream().allMatch(a -> a.failure() == Kind.MISSING_KEY)) {
            return "La IA no está configurada: falta la llave del proveedor. Defina GROQ_API_KEY (o GEMINI_API_KEY) y reinicie la aplicación.";
        }
        List<String> reasons = new ArrayList<>();
        failed.stream().map(Attempt::provider).distinct().forEach(provider -> {
            Attempt last = failed.stream().filter(a -> a.provider().equals(provider)).reduce((a, b) -> b).orElseThrow();
            reasons.add(display(provider) + ": " + last.failure().message());
        });
        return "No se pudo obtener respuesta de la IA. " + String.join(" ", reasons) + " Intente de nuevo en unos minutos.";
    }

    private static String display(String provider) {
        return provider.equals("groq") ? "Groq" : provider.equals("gemini") ? "Gemini" : provider;
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
