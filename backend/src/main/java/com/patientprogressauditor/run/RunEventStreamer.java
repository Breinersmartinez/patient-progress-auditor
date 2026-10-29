package com.patientprogressauditor.run;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.run.RunService.RunView;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

// Lleva la traza de una ejecucion al navegador por SSE (D24). Los eventos NO se inventan ni se guardan aparte: salen de las
// filas de agent_steps, de modo que lo que se ve en vivo es lo mismo que luego se consulta, sobrevive a un reinicio y se
// puede reanudar desde el ultimo id. Se transmiten pasos, no tokens: la salida se valida completa antes de mostrarse.
//   event: step  (id = id de la fila)  ·  event: end (la ejecucion final, tras el ultimo paso)  ·  comentario de latido
@Component
public class RunEventStreamer {

    private static final Logger log = LoggerFactory.getLogger(RunEventStreamer.class);

    static final long POLL_MILLIS = 300;
    static final long HEARTBEAT_MILLIS = 15_000;
    // Mas que cualquier ejecucion posible; si el cliente sigue ahi, EventSource reconecta solo y reanuda.
    static final long STREAM_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final class Stream {
        final UUID runId;
        final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        long lastId;
        long lastSent = System.currentTimeMillis();
        boolean done;

        Stream(UUID runId, long lastId) {
            this.runId = runId;
            this.lastId = lastId;
        }
    }

    private final RunService service;
    private final ObjectMapper mapper;
    private final Set<Stream> streams = ConcurrentHashMap.newKeySet();
    // Un solo hilo reparte los eventos de todos los flujos abiertos; cada vuelta es una lectura corta de la base.
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "run-events");
        thread.setDaemon(true);
        return thread;
    });

    public RunEventStreamer(RunService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostConstruct
    void start() {
        ticker.scheduleWithFixedDelay(this::pumpAll, POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        ticker.shutdownNow();
    }

    // Abre el flujo y envia ya lo que haya despues de `afterId` (0 = desde el principio).
    public SseEmitter open(UUID runId, long afterId) {
        Stream stream = new Stream(runId, afterId);
        streams.add(stream);
        stream.emitter.onCompletion(() -> streams.remove(stream));
        stream.emitter.onTimeout(() -> close(stream));
        stream.emitter.onError(e -> streams.remove(stream));
        ticker.execute(() -> pump(stream));
        return stream.emitter;
    }

    int openStreams() {
        return streams.size();
    }

    private void pumpAll() {
        for (Stream stream : streams) {
            pump(stream);
        }
    }

    private void pump(Stream stream) {
        if (stream.done) {
            return;
        }
        try {
            // El estado final se lee ANTES de vaciar los pasos: lo que se escribio hasta entonces ya esta en la base, y asi no
            // se cierra el flujo con un paso sin enviar.
            AgentRun run = service.find(stream.runId);
            boolean finished = RunService.isFinal(run);
            for (RunService.StepView step : service.stepsAfter(stream.runId, stream.lastId)) {
                stream.emitter.send(SseEmitter.event().id(Long.toString(step.id())).name("step").data(json(step)));
                stream.lastId = step.id();
                stream.lastSent = System.currentTimeMillis();
            }
            if (finished) {
                RunView end = service.view(run);
                stream.emitter.send(SseEmitter.event().name("end").data(json(end)));
                close(stream);
            } else if (System.currentTimeMillis() - stream.lastSent >= HEARTBEAT_MILLIS) {
                stream.emitter.send(SseEmitter.event().comment("latido"));
                stream.lastSent = System.currentTimeMillis();
            }
        } catch (IOException | IllegalStateException e) {
            // El cliente se fue (o el flujo ya estaba cerrado): no es un error, y reanuda con Last-Event-ID.
            stream.done = true;
            streams.remove(stream);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar la traza de la ejecución {}", stream.runId, e);
            stream.done = true;
            streams.remove(stream);
            stream.emitter.completeWithError(e);
        }
    }

    private void close(Stream stream) {
        stream.done = true;
        streams.remove(stream);
        stream.emitter.complete();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento", e);
        }
    }
}
