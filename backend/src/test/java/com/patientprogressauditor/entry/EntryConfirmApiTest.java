package com.patientprogressauditor.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.LlmGateway;
import com.patientprogressauditor.llm.LlmResponse;
import com.patientprogressauditor.llm.LlmResult;
import com.patientprogressauditor.llm.LlmUnavailableException;
import com.patientprogressauditor.llm.StructuredResult;

// Proponer (con la IA simulada en la pasarela) y confirmar (de verdad, contra la base).
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class EntryConfirmApiTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    private static final String ANSWER = """
            {"events":[{"symptom":"DIARREA","intensity":"INTENSO","occurrences":2},
                       {"symptom":"APETITO_BAJO","intensity":"SIN_INDICAR","occurrences":null}]}""";

    @MockitoBean private LlmGateway gateway;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID patient;

    @BeforeEach
    void patient() {
        patient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, alias) VALUES (?, 'P-902')", patient);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private ResultActions propose(String user, String body) throws Exception {
        return mockMvc.perform(post("/api/patients/" + patient + "/entries/propose").header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("body", body))));
    }

    private ResultActions confirm(String user, String json) throws Exception {
        return mockMvc.perform(post("/api/patients/" + patient + "/entries").header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void modelAnswers(String json) throws Exception {
        List<Attempt> attempts = List.of(new Attempt("groq", "openai/gpt-oss-120b", 1, null, 900, null, 300, 80));
        LlmResult llm = new LlmResult(new LlmResponse(json, List.of(), LlmResponse.FinishReason.STOP, 300, 80), "groq",
                "openai/gpt-oss-120b", false, 900, new BigDecimal("0.000093"), attempts);
        when(gateway.structured(any(), any())).thenReturn(
                new StructuredResult(mapper.readTree(json), llm, 300, 80, new BigDecimal("0.000093"), false, List.of(), attempts));
    }

    // --- proponer ---

    @Test
    void proposingReturnsTheEventsAndSavesNoEntryAndNoEvent() throws Exception {
        modelAnswers(ANSWER);
        int entries = count("entries");
        int events = count("entry_events");

        propose(NURSE, "Tuvo diarrea intensa dos veces. Comió poco.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failure").doesNotExist())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].symptom").value("DIARREA"))
                .andExpect(jsonPath("$.events[0].intensity").value("INTENSO"))
                .andExpect(jsonPath("$.events[0].occurrences").value(2))
                .andExpect(jsonPath("$.events[1].symptom").value("APETITO_BAJO"))
                .andExpect(jsonPath("$.events[1].occurrences").doesNotExist())
                .andExpect(jsonPath("$.provider").value("groq"))
                .andExpect(jsonPath("$.runId").isNotEmpty());

        assertThat(count("entries")).isEqualTo(entries);
        assertThat(count("entry_events")).isEqualTo(events);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE run_type = 'ESTRUCTURACION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void whenTheAiFailsTheAnswerIs200WithAClearMessageAndNoStackTrace() throws Exception {
        when(gateway.structured(any(), any())).thenThrow(new LlmUnavailableException(
                "No se pudo obtener respuesta de la IA. Groq: El servicio de IA tardó demasiado en responder. Intente de nuevo en unos minutos.",
                List.of(new Attempt("groq", "m", 1, com.patientprogressauditor.llm.LlmException.Kind.TIMEOUT, 15000, "x"))));

        propose(NURSE, "Texto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(0))
                .andExpect(jsonPath("$.failure").value(org.hamcrest.Matchers.endsWith("Puede guardar solo el texto o agregar los síntomas a mano.")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void proposingValidatesTheTextAndThePermission() throws Exception {
        modelAnswers("{\"events\":[]}");
        propose(NURSE, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.body").value("El registro no puede quedar vacío."));
        propose(NURSE, "x".repeat(4001)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/patients/" + patient + "/entries/propose").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hola\"}")).andExpect(status().isUnauthorized());
        propose(DOCTOR, "Sin novedades.").andExpect(status().isOk());
        mockMvc.perform(post("/api/patients/" + UUID.randomUUID() + "/entries/propose").header("X-Demo-User", NURSE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hola\"}")).andExpect(status().isNotFound());
        // Solo la propuesta valida del medico llego al modelo: ni el texto en blanco ni el paciente inexistente.
        org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1)).structured(any(), any());
    }

    // --- confirmar ---

    @Test
    void confirmingSavesTheEntryAndItsEventsTogetherWithWhoConfirmedAndTheOrigin() throws Exception {
        confirm(NURSE, """
                {"entryDate":"2026-10-27","body":"Tuvo diarrea intensa dos veces. Comió poco. Mareo leve.","events":[
                  {"symptom":"DIARREA","intensity":"INTENSO","occurrences":2,"origin":"IA"},
                  {"symptom":"APETITO_BAJO","intensity":"LEVE","occurrences":null,"origin":"EDITADO"},
                  {"symptom":"MAREO","intensity":"LEVE","occurrences":null,"origin":"MANUAL"}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.structuringStatus").value("ESTRUCTURADA"))
                .andExpect(jsonPath("$.body").value("Tuvo diarrea intensa dos veces. Comió poco. Mareo leve."))
                .andExpect(jsonPath("$.symptoms.length()").value(3))
                .andExpect(jsonPath("$.symptoms[0].symptom").value("APETITO_BAJO"));

        assertThat(jdbc.queryForObject("SELECT structuring_status FROM entries WHERE patient_id = ?", String.class, patient))
                .isEqualTo("ESTRUCTURADA");
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
                SELECT symptom, intensity, occurrences, origin, confirmed_by, confirmed_at FROM entry_events
                WHERE entry_id = (SELECT id FROM entries WHERE patient_id = ?) ORDER BY symptom""", patient);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("origin")).containsExactly("EDITADO", "IA", "MANUAL");
        assertThat(((Number) rows.get(1).get("occurrences")).intValue()).isEqualTo(2);
        assertThat(rows.get(0).get("occurrences")).isNull();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("confirmed_by").toString()).isEqualTo(NURSE);
            assertThat(r.get("confirmed_at")).isNotNull();
        });
        mockMvc.perform(get("/api/patients/" + patient + "/entries").header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$[0].symptoms.length()").value(3));
    }

    @Test
    void confirmingAnEmptyListMeansNoSymptomsAndIsStillStructured() throws Exception {
        confirm(NURSE, "{\"entryDate\":\"2026-10-27\",\"body\":\"Sin novedades.\",\"events\":[]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.structuringStatus").value("ESTRUCTURADA"))
                .andExpect(jsonPath("$.symptoms.length()").value(0));
        assertThat(count("entry_events")).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM entry_events", Integer.class));
    }

    @Test
    void savingOnlyTheTextLeavesTheEntryUnstructuredAndWithoutEvents() throws Exception {
        confirm(NURSE, "{\"entryDate\":\"2026-10-27\",\"body\":\"La IA no respondió.\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.structuringStatus").value("SIN_ESTRUCTURAR"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entry_events WHERE entry_id = (SELECT id FROM entries WHERE patient_id = ?)",
                Integer.class, patient)).isZero();
    }

    // --- sintomas fuera del catalogo (E18) ---

    @Test
    void anOpenSymptomTypedByHandIsSavedWithItsLabelAndListedLikeTheOthers() throws Exception {
        confirm(NURSE, """
                {"entryDate":"2026-10-27","body":"Le duele la garganta y tiene picazón.","events":[
                  {"symptom":"dolor de garganta","intensity":"MODERADO","occurrences":null,"origin":"MANUAL"},
                  {"symptom":"OTRO","label":"picazón en los brazos","origin":"IA"},
                  {"symptom":"FIEBRE","label":"lo que sea","origin":"MANUAL"}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symptoms.length()").value(3))
                .andExpect(jsonPath("$.symptoms[0].symptom").value("DOLOR_DE_GARGANTA"))
                .andExpect(jsonPath("$.symptoms[0].label").value("Dolor de garganta"))
                .andExpect(jsonPath("$.symptoms[1].symptom").value("FIEBRE"))
                .andExpect(jsonPath("$.symptoms[1].label").value("Fiebre"))
                .andExpect(jsonPath("$.symptoms[2].symptom").value("PICAZON_EN_LOS_BRAZOS"));

        List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
                SELECT symptom, symptom_label, origin, confirmed_by FROM entry_events
                WHERE entry_id = (SELECT id FROM entries WHERE patient_id = ?) ORDER BY symptom""", patient);
        assertThat(rows).extracting(r -> r.get("symptom")).containsExactly("DOLOR_DE_GARGANTA", "FIEBRE", "PICAZON_EN_LOS_BRAZOS");
        assertThat(rows).extracting(r -> r.get("symptom_label")).containsExactly("Dolor de garganta", null, "Picazón en los brazos");
        assertThat(rows).allSatisfy(r -> assertThat(r.get("confirmed_by").toString()).isEqualTo(NURSE));
        mockMvc.perform(get("/api/patients/" + patient + "/entries").header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$[0].symptoms[0].label").value("Dolor de garganta"));
    }

    @Test
    void theSameOpenSymptomTwiceOrOneThatIsTheCatalogOneAreRejectedAsRepeated() throws Exception {
        String base = "{\"entryDate\":\"2026-10-27\",\"body\":\"Texto\",\"events\":[";

        confirm(NURSE, base + "{\"symptom\":\"tos seca\"},{\"symptom\":\"Tos  Seca\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.events").value("Hay un síntoma repetido. Déjelo una sola vez."));
        confirm(NURSE, base + "{\"symptom\":\"MAREO\"},{\"symptom\":\"mareo\"}]}").andExpect(status().isBadRequest());
        confirm(NURSE, base + "{\"symptom\":\"<script>\"}]}").andExpect(status().isBadRequest());
    }

    @Test
    void anOpenSymptomIsProposedWithItsLabelOnlyWhenTheNoteBacksIt() throws Exception {
        modelAnswers("""
                {"events":[{"symptom":"OTRO","label":"dolor de garganta","evidence":"le duele la garganta","intensity":"LEVE","occurrences":null},
                           {"symptom":"OTRO","label":"picazón","evidence":"se rasca todo el día","intensity":"SIN_INDICAR","occurrences":null},
                           {"symptom":"FIEBRE","label":null,"evidence":"fiebre","intensity":"SIN_INDICAR","occurrences":null}]}""");

        propose(NURSE, "Le duele la garganta, leve. Tuvo fiebre.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].symptom").value("DOLOR_DE_GARGANTA"))
                .andExpect(jsonPath("$.events[0].label").value("Dolor de garganta"))
                .andExpect(jsonPath("$.events[0].intensity").value("LEVE"))
                .andExpect(jsonPath("$.events[1].symptom").value("FIEBRE"));
        // la traza dice que se descarto y por que (no se esconde)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE message LIKE 'Se descartaron síntomas propuestos sin respaldo%'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void aMissingIntensityDefaultsToNotIndicated() throws Exception {
        confirm(NURSE, "{\"entryDate\":\"2026-10-27\",\"body\":\"Fiebre.\",\"events\":[{\"symptom\":\"FIEBRE\",\"origin\":\"MANUAL\"}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symptoms[0].intensity").value("SIN_INDICAR"));
    }

    @Test
    void nothingIsSavedWhenAnyEventIsInvalid() throws Exception {
        int entries = count("entries");
        String base = "{\"entryDate\":\"2026-10-27\",\"body\":\"Texto\",\"events\":[";

        confirm(NURSE, base + "{\"symptom\":\"DIARREA\",\"origin\":\"IA\"},{\"symptom\":\"12345\",\"origin\":\"MANUAL\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.events").value("Escriba el nombre del síntoma con letras (hasta 60 caracteres)."));
        confirm(NURSE, base + "{\"symptom\":\"DIARREA\"},{\"symptom\":\"DIARREA\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.events").value("Hay un síntoma repetido. Déjelo una sola vez."));
        confirm(NURSE, base + "{\"symptom\":\"DIARREA\",\"occurrences\":0}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.events").value("Las veces deben estar entre 1 y 20."));
        confirm(NURSE, base + "{\"symptom\":\"DIARREA\",\"occurrences\":21}]}").andExpect(status().isBadRequest());
        confirm(NURSE, base + "{\"symptom\":\"DIARREA\",\"intensity\":\"fuerte\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.events").value("La intensidad debe ser leve, moderado, intenso o sin indicar."));
        confirm(NURSE, base + "{\"symptom\":\"DIARREA\",\"origin\":\"MAGIA\"}]}").andExpect(status().isBadRequest());

        assertThat(count("entries")).isEqualTo(entries);
    }

    @Test
    void theTextIsStoredExactlyAsWrittenEvenWithEvents() throws Exception {
        String text = "  Vomitó 2 veces,\n\tmuy decaído… 😀  \n";
        confirm(NURSE, mapper.writeValueAsString(java.util.Map.of("entryDate", "2026-10-27", "body", text, "events",
                List.of(java.util.Map.of("symptom", "VOMITO", "intensity", "SIN_INDICAR", "occurrences", 2, "origin", "IA")))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT body FROM entries WHERE patient_id = ?", String.class, patient)).isEqualTo(text);
    }

    @Test
    void theDoctorCanConfirmToo() throws Exception {
        confirm(DOCTOR, "{\"entryDate\":\"2026-10-27\",\"body\":\"Consulta.\",\"events\":[{\"symptom\":\"FIEBRE\",\"origin\":\"MANUAL\"}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Dr. Andrés Salazar"));
        assertThat(jdbc.queryForObject("SELECT confirmed_by::text FROM entry_events WHERE entry_id = (SELECT id FROM entries WHERE patient_id = ?)",
                String.class, patient)).isEqualTo(DOCTOR);
    }

    // --- texto corregido (RF-42, E19) ---

    private static final String NOTE = "dolor de cabeza intenso desde la mañana tuvo diarea dos veces comio poko";
    private static final String FIXED = "Dolor de cabeza intenso desde la mañana. Tuvo diarrea dos veces. Comió poco.";

    @Test
    void theProposalCarriesTheCorrectedTextAndSavesNothing() throws Exception {
        modelAnswers("{\"events\":[],\"correctedText\":\"" + FIXED + "\"}");
        int versions = count("entry_versions");

        propose(NURSE, NOTE).andExpect(status().isOk()).andExpect(jsonPath("$.correctedText").value(FIXED));

        assertThat(count("entry_versions")).isEqualTo(versions);
    }

    @Test
    void aCorrectionThatChangesTheContentIsDroppedButTheSymptomsStillComeAndTheTraceSaysWhy() throws Exception {
        modelAnswers("{\"events\":[{\"symptom\":\"DIARREA\",\"intensity\":\"SIN_INDICAR\",\"occurrences\":3}],"
                + "\"correctedText\":\"Tuvo diarrea tres veces.\"}");

        propose(NURSE, "tuvo diarea dos veces").andExpect(status().isOk())
                .andExpect(jsonPath("$.correctedText").doesNotExist())
                .andExpect(jsonPath("$.failure").doesNotExist())
                .andExpect(jsonPath("$.events.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE message LIKE 'Se descartó el texto corregido%'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void confirmingWithACorrectionKeepsTheOriginalAndSavesTheVersionWithWhoAndHow() throws Exception {
        confirm(NURSE, mapper.writeValueAsString(java.util.Map.of("entryDate", "2026-10-27", "body", NOTE, "events", List.of(),
                "correction", java.util.Map.of("body", FIXED, "origin", "IA_ACEPTADA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(NOTE))
                .andExpect(jsonPath("$.correctedBody").value(FIXED));

        assertThat(jdbc.queryForObject("SELECT body FROM entries WHERE patient_id = ?", String.class, patient)).isEqualTo(NOTE);
        java.util.Map<String, Object> version = jdbc.queryForMap("""
                SELECT body, origin, version_no, created_by::text AS who FROM entry_versions
                WHERE entry_id = (SELECT id FROM entries WHERE patient_id = ?)""", patient);
        assertThat(version.get("body")).isEqualTo(FIXED);
        assertThat(version.get("origin")).isEqualTo("IA_ACEPTADA");
        assertThat(((Number) version.get("version_no")).intValue()).isEqualTo(1);
        assertThat(version.get("who")).isEqualTo(NURSE);
        mockMvc.perform(get("/api/patients/" + patient + "/entries").header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$[0].body").value(NOTE))
                .andExpect(jsonPath("$[0].correctedBody").value(FIXED));
        mockMvc.perform(get("/api/entries").header("X-Demo-User", DOCTOR))
                .andExpect(jsonPath("$[?(@.patientAlias=='P-902')].correctedBody").value(FIXED));
    }

    @Test
    void aCorrectionTheNurseEditedIsSavedAsEditedWithoutTheModelsRules() throws Exception {
        confirm(NURSE, mapper.writeValueAsString(java.util.Map.of("entryDate", "2026-10-27", "body", NOTE, "events", List.of(),
                "correction", java.util.Map.of("body", FIXED + " Sin fiebre.", "origin", "IA_EDITADA"))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT origin FROM entry_versions", String.class)).isEqualTo("IA_EDITADA");
    }

    @Test
    void noVersionIsSavedWhenThereIsNoCorrectionOrItEqualsTheOriginalOrIsBlank() throws Exception {
        int versions = count("entry_versions");
        confirm(NURSE, "{\"entryDate\":\"2026-10-27\",\"body\":\"Sin novedades.\",\"events\":[]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.correctedBody").doesNotExist());
        confirm(NURSE, "{\"entryDate\":\"2026-10-26\",\"body\":\"Sin novedades.\",\"events\":[],"
                + "\"correction\":{\"body\":\"Sin novedades.\",\"origin\":\"IA_ACEPTADA\"}}").andExpect(status().isCreated());
        confirm(NURSE, "{\"entryDate\":\"2026-10-25\",\"body\":\"Sin novedades.\",\"events\":[],"
                + "\"correction\":{\"body\":\"   \",\"origin\":\"IA_EDITADA\"}}").andExpect(status().isCreated());

        assertThat(count("entry_versions")).isEqualTo(versions);
    }

    @Test
    void aBadCorrectionIsRejectedAndNothingIsSaved() throws Exception {
        int entries = count("entries");
        String head = "{\"entryDate\":\"2026-10-27\",\"body\":\"Sin novedades.\",\"events\":[],\"correction\":";
        confirm(NURSE, head + "{\"body\":\"Todo bien.\",\"origin\":\"MAGIA\"}}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.correction").value("El origen del texto corregido no es válido."));
        confirm(NURSE, mapper.writeValueAsString(java.util.Map.of("entryDate", "2026-10-27", "body", "x", "correction",
                java.util.Map.of("body", "y".repeat(4001), "origin", "MANUAL")))).andExpect(status().isBadRequest());
        confirm(NURSE, head + "{\"body\":\"Todo\\u0000bien.\",\"origin\":\"MANUAL\"}}").andExpect(status().isBadRequest());

        assertThat(count("entries")).isEqualTo(entries);
    }
}
