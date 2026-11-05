package com.patientprogressauditor.llm;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

// Configuracion de la capa (application.yml, prefijo `llm`). Las llaves vienen del entorno y nunca tienen valor por defecto.
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmProperties {

    // Orden de los proveedores: el principal y el de respaldo (LLM_PROVIDER, LLM_FALLBACK).
    private String primary = "groq";
    private String fallback = "gemini";

    // Espera entre intentos de un mismo proveedor ante 429, caida o tiempo agotado: 1 s, 2 s y 4 s.
    private List<Duration> backoff = List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    // Si el proveedor pide esperar mas que esto, no se espera: se pasa al respaldo.
    private Duration maxRetryAfter = Duration.ofSeconds(8);

    private Duration structuringTimeout = Duration.ofSeconds(15);
    // Tope de TODA una estructuracion (reintentos, esperas y respaldo): la persona espera la respuesta en pantalla.
    private Duration structuringBudget = Duration.ofSeconds(30);
    private Duration agentTimeout = Duration.ofSeconds(60);
    // Dictado por voz (RF-38): tope de cada llamada y de toda la transcripcion con sus reintentos; la persona espera en pantalla.
    private Duration transcriptionTimeout = Duration.ofSeconds(20);
    private Duration transcriptionBudget = Duration.ofSeconds(30);
    private double temperature = 0;

    private Groq groq = new Groq();
    private Gemini gemini = new Gemini();

    @Getter
    @Setter
    public static class Groq {
        private String apiKey = "";
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String agentModel = "openai/gpt-oss-120b";
        private String structuringModel = "openai/gpt-oss-120b";
        // Medido el 2026-10-28 con el ejemplo del prompt (docs/decisiones.md): con "medium" acierta 14 de 14; con "low",
        // y con el modelo de 20B, falla de forma sistematica (omite «comio poco» o hasta el dolor de cabeza).
        private String structuringReasoningEffort = "medium";
        private String agentReasoningEffort = "medium";
        // Medido el 2026-11-05 (docs/decisiones.md): el mismo texto que whisper-large-v3, 2,8 veces mas barato.
        private String transcriptionModel = "whisper-large-v3-turbo";
    }

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String model = "gemini-3.5-flash-lite";
    }

    public Duration timeoutFor(LlmPurpose purpose) {
        return purpose == LlmPurpose.STRUCTURING ? structuringTimeout : agentTimeout;
    }
}
