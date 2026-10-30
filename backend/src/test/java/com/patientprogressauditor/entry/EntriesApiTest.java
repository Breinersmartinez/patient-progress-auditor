package com.patientprogressauditor.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class EntriesApiTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID newPatient;

    @BeforeEach
    void patient() {
        newPatient = UUID.randomUUID();
        jdbc.update("INSERT INTO patients (id, alias) VALUES (?, 'P-900')", newPatient);
    }

    private ResultActions create(String user, UUID patient, String date, String body) throws Exception {
        String json = mapper.writeValueAsString(java.util.Map.of("entryDate", date, "body", body));
        return mockMvc.perform(post("/api/patients/" + patient + "/entries").header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions list(UUID patient, String query) throws Exception {
        return mockMvc.perform(get("/api/patients/" + patient + "/entries" + query).header("X-Demo-User", NURSE));
    }

    private UUID seeded(String alias) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, alias);
    }

    @Test
    void theTextIsStoredExactlyAsWrittenAndTheEntryIsNotStructured() throws Exception {
        String text = "  vomito 2 veces\n\tmuy decaido,   sin apetito... áéíóú ñ ¿qué? 😀  \n\n";

        create(NURSE, newPatient, "2026-10-20", text)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(text))
                .andExpect(jsonPath("$.entryDate").value("2026-10-20"))
                .andExpect(jsonPath("$.structuringStatus").value("SIN_ESTRUCTURAR"))
                .andExpect(jsonPath("$.authorName").value("Camilo Torres"))
                .andExpect(jsonPath("$.symptoms.length()").value(0));

        String stored = jdbc.queryForObject("SELECT body FROM entries WHERE patient_id = ?", String.class, newPatient);
        assertThat(stored).isEqualTo(text);
        UUID author = jdbc.queryForObject("SELECT author_id FROM entries WHERE patient_id = ?", UUID.class, newPatient);
        assertThat(author).isEqualTo(UUID.fromString(NURSE));
        list(newPatient, "").andExpect(jsonPath("$[0].body").value(text));
    }

    @Test
    void theDoctorCanAlsoRegisterAndIsTheAuthor() throws Exception {
        create(DOCTOR, newPatient, "2026-10-20", "Sin novedades.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Dr. Andrés Salazar"));
    }

    @Test
    void invalidEntriesAreRejectedWithAMessagePerFieldAndNothingIsSaved() throws Exception {
        create(NURSE, newPatient, "2026-10-20", "   \n ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.body").value("El registro no puede quedar vacío."));
        create(NURSE, newPatient, LocalDate.now().plusDays(1).toString(), "Mañana")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.entryDate").value("La fecha del registro no puede ser futura."));
        create(NURSE, newPatient, "2026-10-20", "con nulo \u0000 adentro")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.body").value("El registro tiene un carácter que no se puede guardar."));
        create(NURSE, newPatient, "2026-10-20", "x".repeat(4001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.body").value("El registro no puede pasar de 4000 caracteres."));
        mockMvc.perform(post("/api/patients/" + newPatient + "/entries").header("X-Demo-User", NURSE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.entryDate").value("Elija la fecha del registro."));
        create(NURSE, UUID.randomUUID(), "2026-10-20", "Paciente que no existe")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontramos ese paciente."));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries WHERE patient_id = ?", Integer.class, newPatient)).isZero();
    }

    @Test
    void an4000CharacterTextIsAccepted() throws Exception {
        create(NURSE, newPatient, "2026-10-20", "x".repeat(4000)).andExpect(status().isCreated());
    }

    @Test
    void listIsChronologicalEvenWhenTheyWereSavedOutOfOrderAndSameDayGoesByCreationTime() throws Exception {
        create(NURSE, newPatient, "2026-10-22", "jueves").andExpect(status().isCreated());
        create(NURSE, newPatient, "2026-10-20", "martes, primera").andExpect(status().isCreated());
        create(NURSE, newPatient, "2026-10-21", "miercoles").andExpect(status().isCreated());
        create(NURSE, newPatient, "2026-10-20", "martes, segunda").andExpect(status().isCreated());

        list(newPatient, "")
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].body").value("martes, primera"))
                .andExpect(jsonPath("$[1].body").value("martes, segunda"))
                .andExpect(jsonPath("$[2].body").value("miercoles"))
                .andExpect(jsonPath("$[3].body").value("jueves"));
        list(newPatient, "?order=desc")
                .andExpect(jsonPath("$[0].body").value("jueves"))
                .andExpect(jsonPath("$[2].body").value("martes, segunda"))
                .andExpect(jsonPath("$[3].body").value("martes, primera"));
    }

    @Test
    void theRangeIncludesBothEndsAndLimitKeepsTheNewest() throws Exception {
        for (int day = 19; day <= 25; day++) {
            create(NURSE, newPatient, "2026-10-" + day, "dia " + day).andExpect(status().isCreated());
        }

        list(newPatient, "?from=2026-10-21&to=2026-10-23")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].body").value("dia 21"))
                .andExpect(jsonPath("$[2].body").value("dia 23"));
        list(newPatient, "?from=2026-10-24")
                .andExpect(jsonPath("$.length()").value(2));
        list(newPatient, "?to=2026-10-19")
                .andExpect(jsonPath("$.length()").value(1));
        list(newPatient, "?order=desc&limit=3")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].body").value("dia 25"))
                .andExpect(jsonPath("$[2].body").value("dia 23"));
    }

    @Test
    void badParametersFailWithAClearMessage() throws Exception {
        list(newPatient, "?from=2026-10-25&to=2026-10-20").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.from").value("La fecha inicial no puede ser posterior a la final."));
        list(newPatient, "?order=sideways").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.order").value("El orden debe ser asc o desc."));
        list(newPatient, "?limit=0").andExpect(status().isBadRequest());
        list(newPatient, "?limit=501").andExpect(status().isBadRequest());
        list(newPatient, "?from=ayer").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se pudo leer lo enviado. Revise los datos e intente de nuevo."));
        list(UUID.randomUUID(), "").andExpect(status().isNotFound());
    }

    @Test
    void seededEntriesComeWithTheirConfirmedSymptoms() throws Exception {
        UUID p014 = seeded("P-014");
        int expected = jdbc.queryForObject("SELECT count(*) FROM entries WHERE patient_id = ?", Integer.class, p014);

        list(p014, "").andExpect(jsonPath("$.length()").value(expected))
                .andExpect(jsonPath("$[0].patientAlias").value("P-014"))
                .andExpect(jsonPath("$[?(@.symptoms.length() > 0)]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.symptoms[0].symptom == 'NAUSEAS')].symptoms[0].intensity").isNotEmpty());
    }

    @Test
    void historyIsNewestFirstThenByPatientCodeAndCoversEveryPatient() throws Exception {
        create(NURSE, newPatient, "2026-10-26", "Del lunes siguiente").andExpect(status().isCreated());
        int total = jdbc.queryForObject("SELECT count(*) FROM entries", Integer.class);

        mockMvc.perform(get("/api/entries").header("X-Demo-User", NURSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(total))
                .andExpect(jsonPath("$[0].patientAlias").value("P-900"))
                .andExpect(jsonPath("$[0].entryDate").value("2026-10-26"));
        // El 25 de octubre hay entradas de varios pacientes: salen por codigo.
        mockMvc.perform(get("/api/entries?limit=4").header("X-Demo-User", NURSE))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].entryDate").value("2026-10-25"))
                .andExpect(jsonPath("$[1].patientAlias").value("P-007"));
    }

    @Test
    void permissionsAreEnforcedByTheServer() throws Exception {
        mockMvc.perform(get("/api/entries")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/entries").header("X-Demo-User", DOCTOR)).andExpect(status().isOk());
        mockMvc.perform(post("/api/patients/" + newPatient + "/entries")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"entryDate\":\"2026-10-20\",\"body\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
    }
}
