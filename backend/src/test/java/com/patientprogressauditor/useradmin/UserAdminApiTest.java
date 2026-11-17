package com.patientprogressauditor.useradmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.patientprogressauditor.keycloak.KeycloakAdminClient;
import com.patientprogressauditor.keycloak.KeycloakUnavailableException;
import com.patientprogressauditor.keycloak.KeycloakUserExistsException;
import com.patientprogressauditor.session.AppPrincipal;
import com.patientprogressauditor.support.TestAuth;
import com.patientprogressauditor.web.FieldException;

// Gestion de usuarios (RF-33, A1 a A20, A24): la API completa con Keycloak simulado. Sin @Transactional a proposito: el
// servicio escribe base y Keycloak a la vez y aqui se miran los commits y los rollbacks de verdad.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
class UserAdminApiTest {

    private static final String SEEDED_ADMIN = "admin@example.test";
    private static final String DOMAIN = "@admintest.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserAdminService service;
    @MockitoBean private KeycloakAdminClient keycloak;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM app_users WHERE email LIKE ?", "%" + DOMAIN);
    }

    private static RequestPostProcessor admin() {
        return TestAuth.token(SEEDED_ADMIN, "ADMIN");
    }

    private UUID insertUser(String name, String role, boolean active) {
        UUID id = UUID.randomUUID();
        String email = name.toLowerCase().replace(' ', '.') + DOMAIN;
        jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo, active) VALUES (?, ?, ?, false, ?)", id, email, name, active);
        jdbc.update("INSERT INTO app_user_roles (user_id, role_id) SELECT ?, id FROM app_roles WHERE code = ?", id, role);
        return id;
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static String body(String name, String email, String role) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"role\":\"" + role + "\"}";
    }

    // ---- T5: listar (A1, A18, A19, A24)

    @Test
    void theAdminSeesTheSeededUsersIncludingHimself() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.email == 'admin@example.test')].role").value("ADMIN"))
                .andExpect(jsonPath("$[?(@.email == 'marcela.rojas@example.test')].name").value("Marcela Rojas"))
                .andExpect(jsonPath("$[?(@.email == 'andres.salazar@example.test')].active").value(true));
    }

    @Test
    void theListIsSortedByName() throws Exception {
        String json = mockMvc.perform(get("/api/admin/users").with(admin())).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json.indexOf("Administrador Auditor")).isLessThan(json.indexOf("Camilo Torres"));
        assertThat(json.indexOf("Camilo Torres")).isLessThan(json.indexOf("Marcela Rojas"));
    }

    @Test
    void onlyAdminsReachTheAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users").with(TestAuth.token("marcela.rojas@example.test", "ENFERMERIA"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").with(TestAuth.token("andres.salazar@example.test", "MEDICO"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users").with(TestAuth.token("marcela.rojas@example.test", "ENFERMERIA"))
                .contentType(MediaType.APPLICATION_JSON).content(body("Ana Prueba", "ana" + DOMAIN, "MEDICO"))).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/" + UUID.randomUUID()).with(TestAuth.token("andres.salazar@example.test", "MEDICO"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(keycloak);
    }

    @Test
    void theAdminStillReadsNoClinicalData() throws Exception {
        mockMvc.perform(get("/api/patients").with(admin())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/entries").with(admin())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports").with(admin())).andExpect(status().isForbidden());
    }

    // ---- T6: crear (A4, A7, A8, A16, A17)

    @Test
    void createsTheUserInTheDatabaseAndInKeycloakAndSendsTheInvitation() throws Exception {
        when(keycloak.createUser("ana.prueba" + DOMAIN, "Ana Prueba", "MEDICO", true)).thenReturn("kc-1");

        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("  Ana Prueba ", "Ana.Prueba" + DOMAIN, "MEDICO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.name").value("Ana Prueba"))
                .andExpect(jsonPath("$.user.email").value("ana.prueba" + DOMAIN))
                .andExpect(jsonPath("$.user.role").value("MEDICO"))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.invitationSent").value(true));

        assertThat(count("SELECT count(*) FROM app_users u JOIN app_user_roles ur ON ur.user_id = u.id"
                + " JOIN app_roles r ON r.id = ur.role_id WHERE u.email = ? AND r.code = 'MEDICO' AND u.active", "ana.prueba" + DOMAIN)).isEqualTo(1);
        verify(keycloak).sendInvitation("kc-1");
    }

    @Test
    void canCreateTheUserAlreadyDeactivatedAndNoInvitationIsSent() throws Exception {
        when(keycloak.createUser("ines.prueba" + DOMAIN, "Ines Prueba", "ENFERMERIA", false)).thenReturn("kc-9");

        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ines Prueba\",\"email\":\"ines.prueba" + DOMAIN + "\",\"role\":\"ENFERMERIA\",\"active\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.active").value(false))
                .andExpect(jsonPath("$.invitationSent").value(false));

        assertThat(count("SELECT count(*) FROM app_users WHERE email = ? AND NOT active", "ines.prueba" + DOMAIN)).isEqualTo(1);
        verify(keycloak, never()).sendInvitation(anyString());
    }

    @Test
    void aDuplicateEmailIsRejectedWithoutCallingKeycloak() throws Exception {
        insertUser("Ana Prueba", "MEDICO", true);

        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Otra Ana", "ANA.PRUEBA" + DOMAIN, "ENFERMERIA")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un usuario con ese correo."))
                .andExpect(jsonPath("$.fields.email").value("Ya existe un usuario con ese correo."));
        verifyNoInteractions(keycloak);
    }

    @Test
    void anEmailAlreadyInKeycloakIsAConflictAndNoRowIsLeft() throws Exception {
        when(keycloak.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenThrow(new KeycloakUserExistsException("x"));

        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana Prueba", "ana.prueba" + DOMAIN, "MEDICO")))
                .andExpect(status().isConflict());
        assertThat(count("SELECT count(*) FROM app_users WHERE email = ?", "ana.prueba" + DOMAIN)).isZero();
    }

    @Test
    void withKeycloakDownNothingIsLeftBehind() throws Exception {
        when(keycloak.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenThrow(new KeycloakUnavailableException("caido"));

        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana Prueba", "ana.prueba" + DOMAIN, "MEDICO")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("No se pudo contactar el servicio de inicio de sesión. Intente de nuevo."))
                .andExpect(jsonPath("$.trace").doesNotExist());
        assertThat(count("SELECT count(*) FROM app_users WHERE email = ?", "ana.prueba" + DOMAIN)).isZero();
        verify(keycloak, never()).sendInvitation(anyString());
    }

    @Test
    void ifOnlyTheInvitationFailsTheAccountExistsAndTheResponseSaysSo() throws Exception {
        when(keycloak.createUser(anyString(), anyString(), anyString(), anyBoolean())).thenReturn("kc-2");
        org.mockito.Mockito.doThrow(new KeycloakUnavailableException("correo")).when(keycloak).sendInvitation("kc-2");

        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Ana Prueba", "ana.prueba" + DOMAIN, "ENFERMERIA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationSent").value(false));
        assertThat(count("SELECT count(*) FROM app_users WHERE email = ?", "ana.prueba" + DOMAIN)).isEqualTo(1);
    }

    @Test
    void invalidDataIsRejectedBeforeTouchingAnything() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "sin-arroba", "SUPERUSUARIO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").value("Escriba el nombre."))
                .andExpect(jsonPath("$.fields.email").value("Escriba un correo válido."))
                .andExpect(jsonPath("$.fields.role").value("Elija un rol."));
        verifyNoInteractions(keycloak);
        assertThat(count("SELECT count(*) FROM app_users")).isEqualTo(5);
    }

    // ---- T7: editar y desactivar (A9 a A15, A20)

    @Test
    void editsNameRoleAndStateInBothPlacesAndIgnoresAnEmailChange() throws Exception {
        UUID id = insertUser("Ana Prueba", "ENFERMERIA", true);
        when(keycloak.findIdByEmail("ana.prueba" + DOMAIN)).thenReturn(Optional.of("kc-3"));

        mockMvc.perform(put("/api/admin/users/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana P. Gomez\",\"role\":\"MEDICO\",\"active\":true,\"email\":\"otro@admintest.local\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ana P. Gomez"))
                .andExpect(jsonPath("$.role").value("MEDICO"))
                .andExpect(jsonPath("$.email").value("ana.prueba" + DOMAIN));

        assertThat(jdbc.queryForObject("SELECT email FROM app_users WHERE id = ?", String.class, id)).isEqualTo("ana.prueba" + DOMAIN);
        assertThat(jdbc.queryForObject("SELECT r.code FROM app_user_roles ur JOIN app_roles r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class, id)).isEqualTo("MEDICO");
        verify(keycloak).updateUser("kc-3", "Ana P. Gomez", "MEDICO", true);
    }

    @Test
    void deactivatingCutsTheAccessAtTheNextRequestAndReactivatingGivesItBack() throws Exception {
        UUID id = insertUser("Ana Prueba", "ENFERMERIA", true);
        String email = "ana.prueba" + DOMAIN;
        when(keycloak.findIdByEmail(email)).thenReturn(Optional.of("kc-4"));
        mockMvc.perform(get("/api/session").with(TestAuth.token(email, "ENFERMERIA"))).andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/users/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Prueba\",\"role\":\"ENFERMERIA\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        verify(keycloak).updateUser("kc-4", "Ana Prueba", "ENFERMERIA", false);
        // Con el token todavia vigente, la siguiente peticion ya es 403 (A25).
        mockMvc.perform(get("/api/session").with(TestAuth.token(email, "ENFERMERIA"))).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/admin/users/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Prueba\",\"role\":\"ENFERMERIA\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/session").with(TestAuth.token(email, "ENFERMERIA"))).andExpect(status().isOk());
    }

    @Test
    void anAdminCannotDeactivateHimselfNorChangeHisOwnRole() throws Exception {
        UUID self = jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, SEEDED_ADMIN);

        mockMvc.perform(put("/api/admin/users/" + self).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Administrador Auditor\",\"role\":\"ADMIN\",\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puede desactivar su propia cuenta."));
        mockMvc.perform(put("/api/admin/users/" + self).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Administrador Auditor\",\"role\":\"MEDICO\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puede cambiar su propio rol."));
        assertThat(count("SELECT count(*) FROM app_users WHERE email = ? AND active", SEEDED_ADMIN)).isEqualTo(1);
        verify(keycloak, never()).updateUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void theLastActiveAdminCannotBeDeactivatedNorDemoted() {
        UUID onlyAdmin = jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, SEEDED_ADMIN);
        // Otro actor (no es el mismo): la regla de "siempre queda uno" tiene que sostenerse por si sola.
        AppPrincipal someoneElse = new AppPrincipal(UUID.randomUUID(), "Otro", "ADMIN", Set.of());

        assertThatThrownBy(() -> service.update(onlyAdmin, new UserAdminService.UserChanges("Administrador Auditor", "ADMIN", false), someoneElse))
                .isInstanceOfSatisfying(FieldException.class, e -> assertThat(e.getMessage()).isEqualTo("Debe quedar al menos un administrador activo."));
        assertThatThrownBy(() -> service.update(onlyAdmin, new UserAdminService.UserChanges("Administrador Auditor", "MEDICO", true), someoneElse))
                .isInstanceOf(FieldException.class);
        verify(keycloak, never()).updateUser(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void anotherAdminCanBeDeactivatedWhileOneStaysActive() throws Exception {
        UUID second = insertUser("Segundo Admin", "ADMIN", true);
        when(keycloak.findIdByEmail("segundo.admin" + DOMAIN)).thenReturn(Optional.of("kc-5"));

        mockMvc.perform(put("/api/admin/users/" + second).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Segundo Admin\",\"role\":\"ADMIN\",\"active\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void ifKeycloakFailsTheEditIsNotApplied() throws Exception {
        UUID id = insertUser("Ana Prueba", "ENFERMERIA", true);
        when(keycloak.findIdByEmail("ana.prueba" + DOMAIN)).thenReturn(Optional.of("kc-6"));
        org.mockito.Mockito.doThrow(new KeycloakUnavailableException("caido")).when(keycloak).updateUser(eq("kc-6"), anyString(), anyString(), anyBoolean());

        mockMvc.perform(put("/api/admin/users/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nombre Nuevo\",\"role\":\"MEDICO\",\"active\":false}"))
                .andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT display_name FROM app_users WHERE id = ?", String.class, id)).isEqualTo("Ana Prueba");
        assertThat(count("SELECT count(*) FROM app_users WHERE id = ? AND active", id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT r.code FROM app_user_roles ur JOIN app_roles r ON r.id = ur.role_id WHERE ur.user_id = ?", String.class, id)).isEqualTo("ENFERMERIA");
    }

    @Test
    void anUnknownUserIs404AndInvalidDataIs400() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + UUID.randomUUID()).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"role\":\"MEDICO\",\"active\":true}"))
                .andExpect(status().isNotFound());
        UUID id = insertUser("Ana Prueba", "ENFERMERIA", true);
        mockMvc.perform(put("/api/admin/users/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"role\":\"NADA\",\"active\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists()).andExpect(jsonPath("$.fields.role").exists());
    }

    @Test
    void usersCannotBeDeleted() throws Exception {
        UUID id = insertUser("Ana Prueba", "ENFERMERIA", true);
        int status = mockMvc.perform(delete("/api/admin/users/" + id).with(admin())).andReturn().getResponse().getStatus();
        assertThat(status).isIn(403, 404, 405);
        assertThat(count("SELECT count(*) FROM app_users WHERE id = ?", id)).isEqualTo(1);
    }

    // ---- T9: reenviar la invitacion

    @Test
    void resendsTheInvitationOfAnActiveUser() throws Exception {
        UUID id = insertUser("Ana Prueba", "ENFERMERIA", true);
        when(keycloak.findIdByEmail("ana.prueba" + DOMAIN)).thenReturn(Optional.of("kc-7"));

        mockMvc.perform(post("/api/admin/users/" + id + "/invitation").with(admin())).andExpect(status().isNoContent());
        verify(keycloak).sendInvitation("kc-7");
    }

    @Test
    void doesNotResendToADeactivatedOrUnknownUser() throws Exception {
        UUID inactive = insertUser("Ana Inactiva", "ENFERMERIA", false);
        mockMvc.perform(post("/api/admin/users/" + inactive + "/invitation").with(admin())).andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/users/" + UUID.randomUUID() + "/invitation").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/users/" + inactive + "/invitation").with(TestAuth.token("marcela.rojas@example.test", "ENFERMERIA")))
                .andExpect(status().isForbidden());
        verify(keycloak, never()).sendInvitation(any());
    }
}
