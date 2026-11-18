package com.patientprogressauditor.llm;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.patientprogressauditor.llm.LlmException.Kind;

// La puerta a la voz a texto para el resto de la aplicacion (RF-38, D14), como LlmGateway lo es para el chat: reintenta con
// las mismas esperas ante 429, caida o tiempo agotado, dentro de un presupuesto de tiempo, calcula el costo a precio de
// lista y, si no se pudo, da un mensaje honesto. Sin respaldo: D14 solo nombra a Groq y al Whisper local.
// El audio se recibe en memoria y nunca se escribe en el registro.
@Service
public class TranscriptionGateway {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionGateway.class);

    public record Result(String text, Double seconds, String provider, String model, BigDecimal costUsd, List<Attempt> attempts) {
    }

    private final Transcriber transcriber;
    private final LlmProperties properties;
    private final Sleeper sleeper;

    public TranscriptionGateway(Transcriber transcriber, LlmProperties properties, Sleeper sleeper) {
        this.transcriber = transcriber;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    public Transcriber transcriber() {
        return transcriber;
    }

    public Result transcribe(byte[] audio, AudioFormat format) {
        List<Attempt> attempts = new ArrayList<>();
        String provider = transcriber.id();
        String model = transcriber.model();
        if (!transcriber.configured()) {
            attempts.add(new Attempt(provider, model, 0, Kind.MISSING_KEY, 0, transcriber.keyVariable() + " no esta definida"));
            throw new LlmUnavailableException(honestMessage(Kind.MISSING_KEY), attempts);
        }
        long start = System.nanoTime();
        for (int number = 1;; number++) {
            Duration left = properties.getTranscriptionBudget().minus(Duration.ofNanos(System.nanoTime() - start));
            if (left.isNegative() || left.isZero()) {
                attempts.add(new Attempt(provider, model, number, Kind.TIMEOUT, 0, "Presupuesto de tiempo agotado"));
                throw new LlmUnavailableException(honestMessage(Kind.TIMEOUT), attempts);
            }
            Duration timeout = left.compareTo(properties.getTranscriptionTimeout()) < 0 ? left : properties.getTranscriptionTimeout();
            long t = System.nanoTime();
            try {
                Transcription result = transcriber.transcribe(audio, format, timeout);
                double seconds = result.seconds() == null ? 0 : result.seconds();
                Attempt ok = new Attempt(provider, model, number, null, millisSince(t), null, 0, 0, seconds);
                attempts.add(ok);
                log.info("IA {} {} TRANSCRIPCION: {} s de audio, {} ms, intento {}", provider, model, result.seconds(), millisSince(t), number);
                return new Result(result.text(), result.seconds(), provider, model, LlmPricing.cost(ok), attempts);
            } catch (LlmException e) {
                attempts.add(new Attempt(provider, model, number, e.kind(), millisSince(t), e.detail()));
                log.warn("IA {} {} transcripcion intento {} fallo ({}): {}", provider, model, number, e.kind(), e.detail());
                Duration wait = waitBeforeRetry(e, number);
                Duration afterCall = properties.getTranscriptionBudget().minus(Duration.ofNanos(System.nanoTime() - start));
                if (wait == null || afterCall.compareTo(wait) <= 0) {
                    throw new LlmUnavailableException(honestMessage(e.kind()), attempts);
                }
                pause(wait);
            }
        }
    }

    // Las mismas reglas que LlmGateway: solo lo reintentable, con las esperas de la configuracion, y nunca mas de lo que el
    // proveedor pide si pasa del tope. null = no se reintenta.
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

    // Que paso, en lenguaje claro. Nunca el cuerpo del proveedor ni una llave.
    static String honestMessage(Kind kind) {
        if (kind == Kind.MISSING_KEY) {
            return "El dictado no está disponible: falta la llave de Groq (GROQ_API_KEY).";
        }
        return "No se pudo transcribir el audio. " + kind.message();
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
