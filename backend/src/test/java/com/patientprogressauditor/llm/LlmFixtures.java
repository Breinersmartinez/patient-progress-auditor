package com.patientprogressauditor.llm;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// El mismo esquema y las mismas herramientas para las pruebas de los dos adaptadores y de la pasarela.
final class LlmFixtures {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static final ResponseSchema EVENTS_SCHEMA = new ResponseSchema("propuesta", json("""
            {"type":"object","properties":{"events":{"type":"array","items":{"type":"object","properties":{
              "symptom":{"type":"string","enum":["VOMITO","DIARREA","NAUSEAS"]},
              "intensity":{"type":"string","enum":["LEVE","MODERADO","INTENSO","SIN_INDICAR"]},
              "occurrences":{"type":["integer","null"]}},
              "required":["symptom","intensity","occurrences"],"additionalProperties":false}}},
             "required":["events"],"additionalProperties":false}"""));

    static final ToolSpec METRICS_TOOL = new ToolSpec("get_week_metrics",
            "Cifras de una semana (lunes a domingo) de un paciente. Usela antes de redactar.", json("""
            {"type":"object","properties":{"patient_id":{"type":"string"},"week_start":{"type":"string"}},
             "required":["patient_id","week_start"],"additionalProperties":false}"""));

    static final List<LlmMessage> ASK = List.of(LlmMessage.system("Extrae sintomas."),
            LlmMessage.user("Tuvo diarrea intensa dos veces."));

    private LlmFixtures() {
    }

    static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    static LlmProperties properties(String groqUrl, String groqKey, String geminiUrl, String geminiKey) {
        LlmProperties p = new LlmProperties();
        p.getGroq().setBaseUrl(groqUrl);
        p.getGroq().setApiKey(groqKey);
        p.getGemini().setBaseUrl(geminiUrl);
        p.getGemini().setApiKey(geminiKey);
        p.setStructuringTimeout(Duration.ofSeconds(5));
        p.setAgentTimeout(Duration.ofSeconds(5));
        return p;
    }
}
