package com.patientprogressauditor.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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

@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class SessionEndpointsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private UUID insertUser(boolean demo, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo, active) VALUES (?, ?, 'Prueba', ?, ?)",
                id, id + "@example.test", demo, active);
        jdbc.update("INSERT INTO app_user_roles (user_id, role_id) SELECT ?, id FROM app_roles WHERE code = 'ENFERMERIA'", id);
        return id;
    }

    @Test
    void demoUsersListIsPublicAndHidesEverythingButNameAndRole() throws Exception {
        mockMvc.perform(get("/api/demo-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.displayName == 'Marcela Rojas')].role").value("ENFERMERIA"))
                .andExpect(jsonPath("$[?(@.displayName == 'Dr. Andrés Salazar')].role").value("MEDICO"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void statusStaysOpen() throws Exception {
        mockMvc.perform(get("/api/system/status")).andExpect(status().isOk());
    }

    @Test
    void sessionReturnsTheChosenUserWithRoleAndPermissions() throws Exception {
        mockMvc.perform(get("/api/session").header("X-Demo-User", PermissionMatrixTest.ANDRES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEDICO"))
                .andExpect(jsonPath("$.displayName").value("Dr. Andrés Salazar"))
                .andExpect(jsonPath("$.permissions[?(@ == 'REPORTS_APPROVE')]").isEmpty())
                .andExpect(jsonPath("$.permissions[?(@ == 'REPORTS_READ')]").isNotEmpty());
    }

    @Test
    void noUserIsRejectedWithAClearMessageAndNoStackTrace() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Elija un usuario de demostracion para continuar."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void malformedAndUnknownIdsAreRejected() throws Exception {
        mockMvc.perform(get("/api/session").header("X-Demo-User", "no-es-un-uuid")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/session").header("X-Demo-User", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserThatIsNotDemoOrIsInactiveCannotBeUsedAsASession() throws Exception {
        mockMvc.perform(get("/api/session").header("X-Demo-User", insertUser(false, true).toString()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/session").header("X-Demo-User", insertUser(true, false).toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingPermissionIs403WithAMessageAndNotTheSameAs401() throws Exception {
        mockMvc.perform(get("/api/test-probe/REPORTS_APPROVE").header("X-Demo-User", PermissionMatrixTest.JULIAN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Su rol no tiene permiso para esta accion."));
    }

    @Test
    void queryParameterDoesNotReplaceTheHeaderOutsideTheSseRoute() throws Exception {
        mockMvc.perform(get("/api/session").param("demoUser", PermissionMatrixTest.MARCELA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightIsNotBlockedByTheMissingUser() throws Exception {
        mockMvc.perform(options("/api/session")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "x-demo-user"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
