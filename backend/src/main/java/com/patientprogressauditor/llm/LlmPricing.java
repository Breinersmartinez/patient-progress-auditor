package com.patientprogressauditor.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

// Precio de lista en USD por millon de tokens, verificado el 2026-10-28 en las paginas de los proveedores. La capa
// gratuita no cobra: el costo que se registra es lo que costaria la misma llamada con el plan de pago, para poder
// comparar el costo por tarea (docs/decisiones.md). Un modelo sin precio conocido da costo null, no cero.
final class LlmPricing {

    private record Price(String inputPerMillion, String outputPerMillion) {
    }

    private static final Map<String, Price> PRICES = Map.of(
            "groq/openai/gpt-oss-120b", new Price("0.15", "0.60"),
            "groq/openai/gpt-oss-20b", new Price("0.075", "0.30"),
            "gemini/gemini-3.5-flash-lite", new Price("0.30", "2.50"));

    // Voz a texto: USD por hora de audio, verificado el 2026-11-05 en console.groq.com/docs/speech-to-text. Groq cobra al
    // menos 10 s por peticion.
    private static final Map<String, String> AUDIO_PER_HOUR = Map.of(
            "groq/whisper-large-v3-turbo", "0.04",
            "groq/whisper-large-v3", "0.111");
    static final int MIN_BILLED_AUDIO_SECONDS = 10;

    private LlmPricing() {
    }

    // El costo de un intento que respondio: por tiempo si fue una transcripcion, por tokens si fue texto. null si no se sabe.
    static BigDecimal cost(Attempt attempt) {
        return attempt.audioSeconds() > 0 ? audioCost(attempt.provider(), attempt.model(), attempt.audioSeconds())
                : cost(attempt.provider(), attempt.model(), attempt.tokensIn(), attempt.tokensOut());
    }

    // Lo que cuesta transcribir `seconds` de audio a precio de lista, con el minimo que cobra el proveedor. null si el modelo
    // no tiene precio conocido o si no se sabe la duracion.
    static BigDecimal audioCost(String provider, String model, double seconds) {
        String perHour = AUDIO_PER_HOUR.get(provider + "/" + model);
        if (perHour == null || !(seconds > 0) || Double.isInfinite(seconds)) {
            return null;
        }
        BigDecimal billed = BigDecimal.valueOf(Math.max(seconds, MIN_BILLED_AUDIO_SECONDS));
        return new BigDecimal(perHour).multiply(billed).divide(BigDecimal.valueOf(3600), 6, RoundingMode.HALF_UP);
    }

    static BigDecimal cost(String provider, String model, int tokensIn, int tokensOut) {
        Price price = PRICES.get(provider + "/" + model);
        if (price == null) {
            return null;
        }
        BigDecimal in = new BigDecimal(price.inputPerMillion()).multiply(BigDecimal.valueOf(tokensIn));
        BigDecimal out = new BigDecimal(price.outputPerMillion()).multiply(BigDecimal.valueOf(tokensOut));
        return in.add(out).divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }

    // La parte del costo que corresponde a los tokens de entrada, a 6 decimales, o null si el modelo no tiene precio. La de
    // salida es el resto: asi las dos partes suman exactamente cost(...) y Langfuse no puede mostrar otro total.
    static BigDecimal inputCost(String provider, String model, int tokensIn) {
        Price price = PRICES.get(provider + "/" + model);
        if (price == null) {
            return null;
        }
        return new BigDecimal(price.inputPerMillion()).multiply(BigDecimal.valueOf(tokensIn))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }
}
