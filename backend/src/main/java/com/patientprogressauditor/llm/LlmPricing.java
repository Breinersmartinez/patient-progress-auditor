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

    private LlmPricing() {
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
