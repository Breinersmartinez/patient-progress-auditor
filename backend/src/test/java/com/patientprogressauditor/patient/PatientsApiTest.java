package com.patientprogressauditor.patient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class PatientsApiTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private static MockHttpServletRequestBuilder asNurse(MockHttpServletRequestBuilder request) {
        return request.header("X-Demo-User", NURSE);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private UUID patientId(String alias) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, alias);
    }

    @Test
    void listReturnsTheFivePatientsByAliasWithTheirLastEntry() throws Exception {
        mockMvc.perform(asNurse(get("/api/patients")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].alias").value("P-007"))
                .andExpect(jsonPath("$[1].alias").value("P-014"))
                .andExpect(jsonPath("$[4].alias").value("P-040"))
                .andExpect(jsonPath("$[1].bed").isNotEmpty())
                .andExpect(jsonPath("$[1].age").isNumber())
                .andExpect(jsonPath("$[1].admittedOn").isNotEmpty())
                .andExpect(jsonPath("$[1].lastEntryDate").value("2026-10-25"))
                // P-014 trae sembrado el informe aprobado de la semana del 12 de octubre (E14).
                .andExpect(jsonPath("$[1].lastReport.status").value("APROBADO"))
                .andExpect(jsonPath("$[1].lastReport.periodStart").value("2026-10-12"))
                .andExpect(jsonPath("$[2].lastReport.status").value("PENDIENTE_REVISION"));
    }

    @Test
    void getReturnsOnePatientAndUnknownOrMalformedIdsFailCleanly() throws Exception {
        mockMvc.perform(asNurse(get("/api/patients/" + patientId("P-014"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alias").value("P-014"));
        mockMvc.perform(asNurse(get("/api/patients/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontramos ese paciente."))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mockMvc.perform(asNurse(get("/api/patients/no-es-un-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void createStoresTheTrimmedCodeAndTheNewPatientShowsUpWithoutEntries() throws Exception {
        mockMvc.perform(json(asNurse(post("/api/patients")),
                        "{\"alias\":\"  P-045 \",\"bed\":\"  \",\"age\":64,\"admittedOn\":\"2026-10-20\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alias").value("P-045"))
                .andExpect(jsonPath("$.bed").doesNotExist())
                .andExpect(jsonPath("$.age").value(64))
                .andExpect(jsonPath("$.admittedOn").value("2026-10-20"))
                .andExpect(jsonPath("$.lastEntryDate").doesNotExist());
        mockMvc.perform(asNurse(get("/api/patients")))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[?(@.alias == 'P-045')]").isNotEmpty());
    }

    @Test
    void onlyTheCodeIsRequired() throws Exception {
        mockMvc.perform(json(asNurse(post("/api/patients")), "{\"alias\":\"P-046\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.age").doesNotExist());
    }

    @Test
    void aRepeatedCodeIsRejectedIgnoringCase() throws Exception {
        mockMvc.perform(json(asNurse(post("/api/patients")), "{\"alias\":\"p-014\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fields.alias").value("Ya existe un paciente con ese código."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void invalidDataIsRejectedWithAMessagePerField() throws Exception {
        mockMvc.perform(json(asNurse(post("/api/patients")), "{\"alias\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.alias").value("Escriba el código del paciente."));
        mockMvc.perform(json(asNurse(post("/api/patients")), "{\"alias\":\"P-1\",\"age\":121}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.age").value("La edad debe estar entre 0 y 120 años."));
        mockMvc.perform(json(asNurse(post("/api/patients")), "{\"alias\":\"P-1\",\"bed\":\"12345678901\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.bed").value("La cama no puede pasar de 10 caracteres."));
        mockMvc.perform(json(asNurse(post("/api/patients")),
                        "{\"alias\":\"P-1\",\"admittedOn\":\"" + LocalDate.now().plusDays(3) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.admittedOn").value("La fecha de ingreso no puede ser futura."));
        mockMvc.perform(json(asNurse(post("/api/patients")), "{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se pudo leer lo enviado. Revise los datos e intente de nuevo."));
    }

    @Test
    void permissionsAreEnforcedByTheServer() throws Exception {
        mockMvc.perform(get("/api/patients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/patients").header("X-Demo-User", DOCTOR)).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/patients").header("X-Demo-User", DOCTOR), "{\"alias\":\"P-047\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Su rol no tiene permiso para esta accion."));
    }

    @Test
    void anUnknownRouteIsAJsonNotFoundNotAFrameworkPage() throws Exception {
        mockMvc.perform(asNurse(get("/api/no-existe")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No encontramos lo que busca."));
    }

    private void insertReport(String alias, String periodStart, String status) {
        boolean approved = status.equals("APROBADO");
        jdbc.update("""
                INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, requested_by,
                                     reviewed_by, reviewed_at, approved_version_no)
                VALUES (?, ?, 'SEMANAL', ?::date, ?::date + 6, ?, ?::uuid, ?::uuid, ?::timestamptz, ?)
                """, UUID.randomUUID(), patientId(alias), periodStart, periodStart, status, NURSE,
                approved ? NURSE : null, approved ? "2026-10-26T10:00:00-05:00" : null, approved ? 1 : null);
    }

    @Test
    void theLastReportIsTheMostRecentPeriodAndTheDoctorOnlySeesApprovedOnes() throws Exception {
        // El aprobado del 12 de octubre ya viene sembrado (E14): solo se agrega el pendiente de la semana siguiente.
        insertReport("P-014", "2026-10-19", "PENDIENTE_REVISION");

        mockMvc.perform(asNurse(get("/api/patients/" + patientId("P-014"))))
                .andExpect(jsonPath("$.lastReport.status").value("PENDIENTE_REVISION"))
                .andExpect(jsonPath("$.lastReport.periodStart").value("2026-10-19"))
                .andExpect(jsonPath("$.lastReport.periodEnd").value("2026-10-25"));
        mockMvc.perform(get("/api/patients/" + patientId("P-014")).header("X-Demo-User", DOCTOR))
                .andExpect(jsonPath("$.lastReport.status").value("APROBADO"))
                .andExpect(jsonPath("$.lastReport.periodStart").value("2026-10-12"));
    }

    @Test
    void theDoctorSeesNoReportWhenTheOnlyOneIsPending() throws Exception {
        insertReport("P-021", "2026-10-19", "PENDIENTE_REVISION");

        mockMvc.perform(get("/api/patients").header("X-Demo-User", DOCTOR))
                .andExpect(jsonPath("$[?(@.alias == 'P-021')].lastReport").isEmpty());
        mockMvc.perform(asNurse(get("/api/patients")))
                .andExpect(jsonPath("$[?(@.alias == 'P-021')].lastReport.status").value("PENDIENTE_REVISION"));
    }
}
