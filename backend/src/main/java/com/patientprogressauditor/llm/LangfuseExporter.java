package com.patientprogressauditor.llm;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

// Manda a Langfuse Cloud la traza de cada ejecucion cerrada (D28, RF-43). Tres garantias:
//  1. Opcional: sin LANGFUSE_PUBLIC_KEY y LANGFUSE_SECRET_KEY no hace nada ni abre un hilo.
//  2. Nunca bloquea: el envio corre en un hilo aparte con cola acotada; si se llena o Langfuse no responde, la traza se
//     descarta con un aviso. Enfermeria no espera ni ve nada de esto.
//  3. Nunca lanza: cualquier fallo se registra (categoria o codigo HTTP, nunca el cuerpo ni una llave) y se sigue.
// Solo salen metadatos (ver LangfusePayload). Es un complemento de la traza propia (RF-22), no un reemplazo.
@Component
public class LangfuseExporter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LangfuseExporter.class);
    private static final String OTLP_PATH = "/api/public/otel/v1/traces";
    private static final int QUEUE_CAPACITY = 50;

    private final ObjectMapper mapper;
    private final String url;
    private final String authorization;
    private final String environment;
    private final Duration timeout;
    private final ThreadPoolExecutor executor;

    public LangfuseExporter(LangfuseProperties properties, ObjectMapper mapper) {
        this.mapper = mapper;
        this.timeout = properties.getTimeout();
        this.environment = LangfusePayload.environment(properties.getEnvironment());
        if (properties.enabled()) {
            String base = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank() ? LangfuseProperties.DEFAULT_BASE_URL
                    : properties.getBaseUrl().trim().replaceAll("/+$", "");
            this.url = base + OTLP_PATH;
            this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                    (properties.getPublicKey().trim() + ":" + properties.getSecretKey().trim()).getBytes(StandardCharsets.UTF_8));
            this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
                Thread thread = new Thread(task, "langfuse-export");
                thread.setDaemon(true);
                return thread;
            });
            log.info("Langfuse: trazas activas hacia {} (entorno {}), solo metadatos", base, environment);
        } else {
            this.url = null;
            this.authorization = null;
            this.executor = null;
            log.info("Langfuse: apagado (faltan LANGFUSE_PUBLIC_KEY y LANGFUSE_SECRET_KEY); no se envia nada");
        }
    }

    public boolean enabled() {
        return executor != null;
    }

    // Encola la traza y vuelve de inmediato.
    public void export(LangfuseRun run) {
        if (!enabled() || run == null) {
            return;
        }
        try {
            executor.execute(() -> send(run));
        } catch (RejectedExecutionException e) {
            log.warn("Langfuse: cola llena o cerrada, se descarta la traza de la ejecucion {}", run.runId());
        } catch (RuntimeException e) {
            log.warn("Langfuse: no se pudo encolar la traza de la ejecucion {} ({})", run.runId(), e.getClass().getSimpleName());
        }
    }

    private void send(LangfuseRun run) {
        try {
            JsonHttp.Result result = JsonHttp.post("langfuse", url,
                    Map.of("Authorization", authorization, "x-langfuse-ingestion-version", "4"),
                    LangfusePayload.build(run, environment, mapper), timeout, mapper);
            if (result.status() / 100 != 2) {
                log.warn("Langfuse rechazo la traza de la ejecucion {}: HTTP {}", run.runId(), result.status());
            }
        } catch (LlmException e) {
            log.warn("Langfuse no recibio la traza de la ejecucion {}: {}", run.runId(), e.kind());
        } catch (RuntimeException e) {
            log.warn("Langfuse: fallo al enviar la traza de la ejecucion {} ({})", run.runId(), e.getClass().getSimpleName());
        }
    }

    // Al apagar la aplicacion se da un momento a lo que ya esta en cola; lo que no alcance se pierde (es observabilidad).
    @Override
    public void close() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
