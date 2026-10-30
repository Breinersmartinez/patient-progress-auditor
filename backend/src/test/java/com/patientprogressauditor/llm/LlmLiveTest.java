package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.llm.LlmResponse.FinishReason;

// Llamadas REALES a Groq y a Gemini. No corre con `mvn verify`: solo con RUN_LIVE_LLM=true y las llaves en el entorno
// (GROQ_API_KEY y GEMINI_API_KEY). Gasta unos cientos de tokens de la capa gratuita.
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_LLM", matches = "true")
class LlmLiveTest {

    private static final String NOTE = "Dolor de cabeza intenso desde la mañana. Tuvo diarrea intensa dos veces. Comió poco. Sin vómito.";

    private static final ResponseSchema SCHEMA = new ResponseSchema("propuesta", LlmFixtures.json("""
            {"type":"object","properties":{"events":{"type":"array","items":{"type":"object","properties":{
              "symptom":{"type":"string","enum":["VOMITO","DIARREA","NAUSEAS","FIEBRE","MAREO","DOLOR_CABEZA","DOLOR_HUESO","APETITO_BAJO","ANIMO_DECAIDO"]},
              "intensity":{"type":"string","enum":["LEVE","MODERADO","INTENSO","SIN_INDICAR"]},
              "occurrences":{"type":["integer","null"]}},
              "required":["symptom","intensity","occurrences"],"additionalProperties":false}}},
             "required":["events"],"additionalProperties":false}"""));

    private static final List<LlmMessage> STRUCTURE_PROMPT = List.of(
            LlmMessage.system("Extrae los síntomas que el texto dice, con su intensidad y las veces. No agregues nada que el texto no diga y no conviertas una negación en síntoma. Si la intensidad no se indica, usa SIN_INDICAR."),
            LlmMessage.user(NOTE));

    private static LlmProperties properties(String groqKey, String geminiKey) {
        LlmProperties p = new LlmProperties();
        p.getGroq().setApiKey(groqKey);
        p.getGemini().setApiKey(geminiKey);
        return p;
    }

    private static LlmProperties real() {
        return properties(System.getenv("GROQ_API_KEY"), System.getenv("GEMINI_API_KEY"));
    }

    private static LlmGateway gateway(LlmProperties p) {
        return new LlmGateway(List.of(new GroqClient(p, MAPPER), new GeminiClient(p, MAPPER)), p,
                d -> Thread.sleep(d.toMillis()), MAPPER);
    }

    private static List<String> check(JsonNode json) {
        List<String> found = new ArrayList<>();
        json.path("events").forEach(e -> found.add(e.path("symptom").asText() + "/" + e.path("intensity").asText() + "/"
                + (e.path("occurrences").isNull() ? "-" : e.path("occurrences").asText())));
        List<String> problems = new ArrayList<>();
        for (String want : List.of("DOLOR_CABEZA/INTENSO/-", "DIARREA/INTENSO/2", "APETITO_BAJO/SIN_INDICAR/-")) {
            if (!found.contains(want)) {
                problems.add("falta " + want + " en " + found);
            }
        }
        if (found.stream().anyMatch(f -> f.startsWith("VOMITO"))) {
            problems.add("una negación («sin vómito») se convirtió en síntoma: " + found);
        }
        return problems;
    }

    private void structures(LlmClient client) {
        LlmProperties p = real();
        LlmResponse response = client.chat(LlmRequest.structured(LlmPurpose.STRUCTURING, STRUCTURE_PROMPT, SCHEMA));
        System.out.printf("%s %s: %d ms-ish, %d/%d tokens: %s%n", client.id(), client.model(LlmPurpose.STRUCTURING),
                0, response.tokensIn(), response.tokensOut(), response.text().replaceAll("\\s+", ""));
        assertThat(response.tokensIn()).isPositive();
        try {
            assertThat(check(MAPPER.readTree(response.text()))).isEmpty();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(p).isNotNull();
    }

    @Test
    void groqStructuresTheExampleNoteWithoutInventingFromTheNegation() {
        structures(new GroqClient(real(), MAPPER));
    }

    @Test
    void geminiStructuresTheExampleNoteWithoutInventingFromTheNegation() {
        structures(new GeminiClient(real(), MAPPER));
    }

    private static final ToolSpec METRICS = LlmFixtures.METRICS_TOOL;

    private static List<LlmMessage> askForMetrics() {
        return List.of(LlmMessage.system("Eres un auditor de registros. Usa las herramientas para consultar datos; no inventes."),
                LlmMessage.user("Dame en una frase las métricas de la semana 2026-10-19 del paciente p1."));
    }

    // Ejecuta una vuelta completa: el modelo pide la herramienta, se le contesta y redacta.
    private LlmResponse toolRound(LlmClient client, List<LlmMessage> history) {
        LlmResponse first = client.chat(LlmRequest.withTools(LlmPurpose.AGENT, history, List.of(METRICS)));
        assertThat(first.finishReason()).isEqualTo(FinishReason.TOOL_CALLS);
        ToolCall call = first.toolCalls().get(0);
        assertThat(call.name()).isEqualTo("get_week_metrics");
        assertThat(call.arguments().path("week_start").asText()).isEqualTo("2026-10-19");
        List<LlmMessage> next = new ArrayList<>(history);
        next.add(LlmMessage.assistant(first.text(), first.toolCalls()));
        next.add(LlmMessage.tool(call.id(), call.name(), "{\"entries\":7,\"daysWithEntry\":7,\"nauseaDays\":4}"));
        LlmResponse last = client.chat(LlmRequest.withTools(LlmPurpose.AGENT, next, List.of(METRICS)));
        assertThat(last.text()).isNotBlank();
        assertThat(last.text()).contains("4");
        System.out.printf("%s %s: %s%n", client.id(), client.model(LlmPurpose.AGENT), last.text().replaceAll("\\s+", " "));
        return last;
    }

    @Test
    void groqCallsTheToolAndUsesItsResult() {
        toolRound(new GroqClient(real(), MAPPER), askForMetrics());
    }

    @Test
    void geminiCallsTheToolAndUsesItsResult() {
        toolRound(new GeminiClient(real(), MAPPER), askForMetrics());
    }

    @Test
    void geminiTakesOverAConversationThatGroqStarted() {
        // Groq pide la herramienta; Gemini (que no hizo esa llamada) recibe el historial y redacta.
        GroqClient groq = new GroqClient(real(), MAPPER);
        GeminiClient gemini = new GeminiClient(real(), MAPPER);
        List<LlmMessage> history = new ArrayList<>(askForMetrics());
        LlmResponse first = groq.chat(LlmRequest.withTools(LlmPurpose.AGENT, history, List.of(METRICS)));
        ToolCall call = first.toolCalls().get(0);
        assertThat(call.signature()).isNull();
        history.add(LlmMessage.assistant(first.text(), first.toolCalls()));
        history.add(LlmMessage.tool(call.id(), call.name(), "{\"entries\":7,\"daysWithEntry\":7,\"nauseaDays\":4}"));

        LlmResponse last = gemini.chat(LlmRequest.withTools(LlmPurpose.AGENT, history, List.of(METRICS)));

        System.out.printf("gemini continuó lo de groq: %s%n", last.text().replaceAll("\\s+", " "));
        assertThat(last.text()).contains("4");
    }

    @Test
    void aRejectedPrimaryKeyFallsOverToTheRealBackup() {
        LlmGateway gateway = gateway(properties("gsk_clave_invalida", System.getenv("GEMINI_API_KEY")));

        StructuredResult result = gateway.structured(LlmRequest.structured(LlmPurpose.STRUCTURING, STRUCTURE_PROMPT, SCHEMA),
                LlmLiveTest::check);

        assertThat(result.result().provider()).isEqualTo("gemini");
        assertThat(result.result().fallbackUsed()).isTrue();
        assertThat(result.result().attempts().get(0).failure()).isEqualTo(LlmException.Kind.AUTH);
        System.out.printf("conmutó: %s, costo de lista %s USD%n", result.result().attempts(), result.costUsd());
    }

    @Test
    void swappingTheOrderUsesGeminiFirst() {
        LlmProperties p = real();
        p.setPrimary("gemini");
        p.setFallback("groq");

        StructuredResult result = gateway(p).structured(LlmRequest.structured(LlmPurpose.STRUCTURING, STRUCTURE_PROMPT, SCHEMA),
                LlmLiveTest::check);

        assertThat(result.result().provider()).isEqualTo("gemini");
        assertThat(result.result().fallbackUsed()).isFalse();
    }
}
