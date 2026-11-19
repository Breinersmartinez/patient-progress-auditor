package com.patientprogressauditor.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.patientprogressauditor.support.TestAuth;

// Autenticacion con el JWT de Keycloak (K5, K6, K10, K11): el decodificador es el de pruebas; lo demas es la cadena real.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class SessionEndpointsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private UUID insertUser(boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo, active) VALUES (?, ?, 'Prueba', false, ?)",
                id, id + "@example.test", active);
        jdbc.update("INSERT INTO app_user_roles (user_id, role_id) SELECT ?, id FROM app_roles WHERE code = 'ENFERMERIA'", id);
        return id;
    }

    @Test
    void statusStaysOpen() throws Exception {
        mockMvc.perform(get("/api/system/status")).andExpect(status().isOk());
    }

    @Test
    void theDemoUserListIsGone() throws Exception {
        mockMvc.perform(get("/api/demo-users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/demo-users").with(TestAuth.as(PermissionMatrixTest.MARCELA))).andExpect(status().isNotFound());
    }

    @Test
    void sessionReturnsTheTokenUserWithRoleAndPermissions() throws Exception {
        mockMvc.perform(get("/api/session").with(TestAuth.as(PermissionMatrixTest.ANDRES)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PermissionMatrixTest.ANDRES))
                .andExpect(jsonPath("$.role").value("MEDICO"))
                .andExpect(jsonPath("$.displayName").value("Dr. Andrés Salazar"))
                .andExpect(jsonPath("$.permissions[?(@ == 'REPORTS_APPROVE')]").isEmpty())
                .andExpect(jsonPath("$.permissions[?(@ == 'REPORTS_READ')]").isNotEmpty());
    }

    @Test
    void noTokenIsRejectedWithAClearMessageAndNoStackTrace() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Inicie sesion para continuar."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void aBadTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/session").header("Authorization", "Bearer no-es-un-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/session").header("Authorization", "Basic abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void theOldDemoHeaderAndQueryDoNotAuthenticateAnymore() throws Exception {
        mockMvc.perform(get("/api/session").header("X-Demo-User", PermissionMatrixTest.MARCELA)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/session").param("demoUser", PermissionMatrixTest.MARCELA)).andExpect(status().isUnauthorized());
    }

    @Test
    void aClinicalRoleWithoutAnAccountIs403AndNothingIsCreated() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class);
        mockMvc.perform(get("/api/patients").with(TestAuth.token("sin.cuenta@example.test", "ENFERMERIA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Su usuario no esta registrado en el sistema. Pida a administracion que lo registre."));
        mockMvc.perform(get("/api/session").with(TestAuth.token("sin.cuenta@example.test", "MEDICO"))).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM app_users", Integer.class)).isEqualTo(before);
    }

    @Test
    void aDeactivatedAccountCannotActEvenWithAValidToken() throws Exception {
        mockMvc.perform(get("/api/session").with(TestAuth.as(insertUser(false)))).andExpect(status().isForbidden());
    }

    @Test
    void aDeactivatedAdminIsCutOffImmediatelyToo() throws Exception {
        jdbc.update("UPDATE app_users SET active = false WHERE email = 'admin@example.test'");
        mockMvc.perform(get("/api/session").with(TestAuth.token("admin@example.test", "ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void anAdminTokenWithoutAnAccountIs403() throws Exception {
        mockMvc.perform(get("/api/session").with(TestAuth.token("otro.admin@example.test", "ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void theEmailMatchIgnoresCase() throws Exception {
        mockMvc.perform(get("/api/session").with(TestAuth.token("MARCELA.ROJAS@EXAMPLE.TEST", "ENFERMERIA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PermissionMatrixTest.MARCELA));
    }

    @Test
    void anAdminHasAnAccountButReadsNoClinicalData() throws Exception {
        mockMvc.perform(get("/api/session").with(TestAuth.token("admin@example.test", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.displayName").value("Administrador Auditor"))
                .andExpect(jsonPath("$.id").isNotEmpty());
        mockMvc.perform(get("/api/patients").with(TestAuth.token("admin@example.test", "ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/entries").with(TestAuth.token("admin@example.test", "ADMIN"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports").with(TestAuth.token("admin@example.test", "ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void aValidTokenWithoutAnApplicationRoleCanDoNothing() throws Exception {
        mockMvc.perform(get("/api/patients").with(TestAuth.token("marcela.rojas@example.test", "offline_access")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients").with(TestAuth.token("marcela.rojas@example.test"))).andExpect(status().isForbidden());
    }

    @Test
    void theRoleComesFromTheTokenAndNotFromTheDatabase() throws Exception {
        // Marcela es enfermeria en la base; con un token de MEDICO actua como medico (no aprueba).
        mockMvc.perform(get("/api/session").with(TestAuth.token("marcela.rojas@example.test", "MEDICO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEDICO"))
                .andExpect(jsonPath("$.permissions[?(@ == 'REPORTS_APPROVE')]").isEmpty());
    }

    @Test
    void missingPermissionIs403WithAMessageAndNotTheSameAs401() throws Exception {
        mockMvc.perform(get("/api/test-probe/REPORTS_APPROVE").with(TestAuth.as(PermissionMatrixTest.JULIAN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Su rol no tiene permiso para esta accion."));
    }

    @Test
    void aPostToTheApiNeedsNoCsrfTokenBecauseTheTokenTravelsInAHeader() throws Exception {
        // 401 (falta el token) y no 403 (CSRF): el filtro CSRF no actua sobre /api/**.
        mockMvc.perform(post("/api/entries")).andExpect(status().isUnauthorized());
    }

    @Test
    void csrfProtectionStaysOnOutsideTheApi() throws Exception {
        mockMvc.perform(post("/fuera-de-la-api")).andExpect(status().isForbidden());
    }

    @Test
    void corsPreflightAllowsTheAuthorizationHeaderAndNotTheOldOne() throws Exception {
        mockMvc.perform(options("/api/session")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mockMvc.perform(options("/api/session")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "x-demo-user"))
                .andExpect(status().isForbidden());
    }
}
