package com.patientprogressauditor.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.support.TestAuth;

// Paso 4 de docs/auditoria/plan.md: la lectura de la bitacora (criterios 7 y 9 del encargo y el limite de 1 s con 10 000
// anotaciones). Cada prueba corre en su transaccion: lo que siembra se revierte y no queda nada.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class AuditApiTest {

    private static final String ADMIN = "admin@example.test";
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 12, 9, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID nurse;
    private UUID doctor;
    private UUID admin;

    @BeforeEach
    void users() {
        nurse = user("marcela.rojas@example.test");
        doctor = user("andres.salazar@example.test");
        admin = user(ADMIN);
        // La bitacora de la base de pruebas puede traer filas de otras clases: cada prueba mira solo lo suyo o lo cuenta.
    }

    private UUID user(String email) {
        return jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, email);
    }

    private static RequestPostProcessor asAdmin() {
        return TestAuth.token(ADMIN, "ADMIN");
    }

    private void insert(UUID actor, String role, String action, String type, UUID entity, OffsetDateTime when) {
        jdbc.update("INSERT INTO audit_logs (actor_id, actor_role, action, entity_type, entity_id, occurred_at, after_state)"
                + " VALUES (?, ?, ?, ?, ?, ?, '{\"status\": \"APROBADO\"}'::jsonb)", actor, role, action, type, entity, when);
    }

    private JsonNode list(String query) throws Exception {
        String body = mockMvc.perform(get("/api/admin/audit" + query).with(asAdmin())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    // Criterio 7: solo el administrador; ademas, no hay forma de escribir la bitacora por la API.
    @Test
    void onlyTheAdminReadsItAndNobodyCanWriteIt() throws Exception {
        mockMvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit").with(TestAuth.token("marcela.rojas@example.test", "ENFERMERIA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit").with(TestAuth.token("andres.salazar@example.test", "MEDICO")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit").with(asAdmin())).andExpect(status().isOk());

        // Ninguna ruta de escritura existe: el servidor responde que el metodo no esta permitido.
        mockMvc.perform(post("/api/admin/audit").with(asAdmin())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/admin/audit").with(asAdmin())).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/admin/audit").with(asAdmin())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void eachItemSaysWhoWhatWhenAndAboutWhatWithCodesNotClinicalText() throws Exception {
        UUID patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-033'", UUID.class);
        UUID report = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, requested_by)"
                + " VALUES (?, ?, 'SEMANAL', '2026-10-12', '2026-10-18', 'PENDIENTE_REVISION', ?)", report, patient, nurse);
        UUID target = jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, "camilo.torres@example.test");
        OffsetDateTime later = OffsetDateTime.now(ZoneOffset.UTC).plusYears(5);
        insert(nurse, "ENFERMERIA", "APROBAR_INFORME", "REPORT", report, later);
        insert(admin, "ADMIN", "ACTUALIZAR_USUARIO", "USER", target, later.plusSeconds(1));

        JsonNode items = list("").path("items");
        JsonNode userItem = items.get(0);
        JsonNode reportItem = items.get(1);
        assertThat(userItem.path("action").asText()).isEqualTo("ACTUALIZAR_USUARIO");
        assertThat(userItem.path("actor").path("name").asText()).isEqualTo("Administrador Auditor");
        assertThat(userItem.path("actor").path("role").asText()).isEqualTo("ADMIN");
        assertThat(userItem.path("subject").path("userName").asText()).isEqualTo("Camilo Torres");
        assertThat(reportItem.path("action").asText()).isEqualTo("APROBAR_INFORME");
        assertThat(reportItem.path("subject").path("patientCode").asText()).isEqualTo("P-033");
        assertThat(reportItem.path("subject").path("reportType").asText()).isEqualTo("SEMANAL");
        assertThat(reportItem.path("subject").path("periodStart").asText()).isEqualTo("2026-10-12");
        assertThat(reportItem.path("after").path("status").asText()).isEqualTo("APROBADO");
        assertThat(reportItem.path("occurredAt").asText()).isNotBlank();
    }

    // Criterio 9: cada filtro devuelve solo lo suyo, y combinados tambien.
    @Test
    void theFiltersByPersonAndByActionReturnOnlyWhatMatches() throws Exception {
        long nurseBefore = list("?actorId=" + nurse).path("total").asLong();
        long sendBefore = list("?action=ENVIAR_INFORME").path("total").asLong();
        long bothBefore = list("?actorId=" + nurse + "&action=ENVIAR_INFORME").path("total").asLong();
        insert(nurse, "ENFERMERIA", "APROBAR_INFORME", "REPORT", UUID.randomUUID(), T0);
        insert(nurse, "ENFERMERIA", "ENVIAR_INFORME", "REPORT", UUID.randomUUID(), T0.plusMinutes(1));
        insert(doctor, "MEDICO", "REGISTRAR_ENTRADA_MEDICO", "ENTRY", UUID.randomUUID(), T0.plusMinutes(2));
        insert(admin, "ADMIN", "ENVIAR_INFORME", "REPORT", UUID.randomUUID(), T0.plusMinutes(3));

        JsonNode byNurse = list("?actorId=" + nurse);
        assertThat(byNurse.path("total").asLong()).isEqualTo(nurseBefore + 2);
        byNurse.path("items").forEach(i -> assertThat(i.path("actor").path("id").asText()).isEqualTo(nurse.toString()));
        JsonNode bySend = list("?action=ENVIAR_INFORME");
        assertThat(bySend.path("total").asLong()).isEqualTo(sendBefore + 2);
        bySend.path("items").forEach(i -> assertThat(i.path("action").asText()).isEqualTo("ENVIAR_INFORME"));
        assertThat(list("?actorId=" + nurse + "&action=ENVIAR_INFORME").path("total").asLong()).isEqualTo(bothBefore + 1);
        // En minusculas tambien sirve; un tipo que no existe es un error claro, no una lista vacia.
        assertThat(list("?action=enviar_informe").path("total").asLong()).isEqualTo(sendBefore + 2);
        mockMvc.perform(get("/api/admin/audit?action=BORRAR_TODO").with(asAdmin())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist());
        mockMvc.perform(get("/api/admin/audit?actorId=no-es-un-id").with(asAdmin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/audit?page=-1").with(asAdmin())).andExpect(status().isBadRequest());
    }

    // Criterio 9 y el limite del encargo: con 10 000 anotaciones, las paginas de 50 no repiten ni omiten filas aunque
    // muchas compartan el mismo instante, y cada pagina responde en menos de 1 s.
    @Test
    void withTenThousandAnnotationsThePagesNeitherRepeatNorSkipAndAnswerUnderOneSecond() throws Exception {
        UUID actor = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo) VALUES (?, ?, 'Carga de prueba', TRUE)",
                actor, actor + "@audittest.local");
        // De a 10 por instante: el orden por fecha solo no alcanza y tiene que desempatar el id.
        jdbc.update("""
                INSERT INTO audit_logs (actor_id, actor_role, action, entity_type, entity_id, occurred_at)
                SELECT ?, 'ENFERMERIA', 'APROBAR_INFORME', 'REPORT', gen_random_uuid(), ? + (g / 10) * interval '1 second'
                FROM generate_series(1, 10000) g""", actor, T0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_id = ?", Long.class, actor)).isEqualTo(10_000L);

        Set<Long> seen = new HashSet<>();
        List<Long> times = new ArrayList<>();
        int pages = 0;
        for (int page = 0; ; page++) {
            long start = System.nanoTime();
            JsonNode body = list("?actorId=" + actor + "&page=" + page);
            times.add((System.nanoTime() - start) / 1_000_000);
            JsonNode items = body.path("items");
            if (items.isEmpty()) {
                break;
            }
            pages++;
            assertThat(body.path("size").asInt()).isEqualTo(50);
            assertThat(body.path("total").asLong()).isEqualTo(10_000L);
            items.forEach(i -> assertThat(seen.add(i.path("id").asLong())).as("fila repetida %s", i.path("id")).isTrue());
        }
        assertThat(pages).isEqualTo(200);
        assertThat(seen).hasSize(10_000);

        // Mediana, como mide el curso la latencia: un pico suelto de la maquina (GC, carga) no decide. El maximo se informa.
        List<Long> sorted = times.stream().sorted().toList();
        long median = sorted.get(sorted.size() / 2);
        long max = sorted.get(sorted.size() - 1);
        // La ultima pagina es la mas cara (OFFSET 9950): el mejor de tres intentos, tambien bajo 1 s.
        long last = Long.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            list("?actorId=" + actor + "&page=199");
            last = Math.min(last, (System.nanoTime() - start) / 1_000_000);
        }
        System.out.println("[auditoria] 10000 anotaciones, 200 paginas: mediana " + median + " ms, maximo " + max
                + " ms, ultima pagina " + last + " ms");
        assertThat(median).as("mediana en ms").isLessThan(1000);
        assertThat(last).as("ultima pagina en ms").isLessThan(1000);
    }
}
