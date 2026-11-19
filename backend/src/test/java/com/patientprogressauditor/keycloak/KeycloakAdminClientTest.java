package com.patientprogressauditor.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// El cliente de la API de administracion de Keycloak contra un servidor simulado: cada llamada, su orden y sus fallos.
// La prueba contra el Keycloak real esta en el reporte (docs/admin-usuarios/reporte.md).
class KeycloakAdminClientTest {

    private static final String KC = "http://kc.test";
    private static final String ADMIN = KC + "/admin/realms/auditor";

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(new KeycloakAdminProperties(KC, "auditor", "auditor-admin", "secreto"),
                "http://localhost:4200", builder);
    }

    private void expectToken() {
        server.expect(requestTo(KC + "/realms/auditor/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=auditor-admin")))
                .andRespond(withSuccess("{\"access_token\":\"tok\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    }

    private void expectCreate(String id) {
        server.expect(requestTo(ADMIN + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(content().json("{\"username\":\"ana@example.test\",\"email\":\"ana@example.test\","
                        + "\"firstName\":\"Ana\",\"lastName\":\"Gomez Ruiz\",\"enabled\":true}"))
                .andRespond(withStatus(HttpStatus.CREATED).location(URI.create(ADMIN + "/users/" + id)));
    }

    private void expectEmptyRoles(String id) {
        server.expect(requestTo(ADMIN + "/users/" + id + "/role-mappings/realm")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"name\":\"default-roles-auditor\"}]", MediaType.APPLICATION_JSON));
    }

    private void expectRoleRepresentationAndMapping(String id, String role) {
        server.expect(requestTo(ADMIN + "/roles/" + role)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"r1\",\"name\":\"" + role + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/" + id + "/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":\"r1\",\"name\":\"" + role + "\"}]"))
                .andRespond(withNoContent());
    }

    @Test
    void createsTheUserEnabledWithItsRoleAndReturnsTheKeycloakId() {
        expectToken();
        expectCreate("kc-1");
        expectEmptyRoles("kc-1");
        expectRoleRepresentationAndMapping("kc-1", "ENFERMERIA");

        assertThat(client.createUser("ana@example.test", "Ana Gomez Ruiz", "ENFERMERIA", true)).isEqualTo("kc-1");
        server.verify();
    }

    @Test
    void theTokenIsRequestedOnceAndReused() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/kc-1/execute-actions-email?client_id=auditor-web&redirect_uri=http://localhost:4200"))
                .andExpect(method(HttpMethod.PUT)).andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-1/execute-actions-email?client_id=auditor-web&redirect_uri=http://localhost:4200"))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("Authorization", "Bearer tok")).andRespond(withNoContent());

        client.sendInvitation("kc-1");
        client.sendInvitation("kc-1");
        server.verify();
    }

    @Test
    void anExistingEmailIsAConflictAndNothingElseIsCalled() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.createUser("ana@example.test", "Ana Gomez Ruiz", "ENFERMERIA", true))
                .isInstanceOf(KeycloakUserExistsException.class);
        server.verify();
    }

    @Test
    void ifTheRoleCannotBeSetTheNewKeycloakUserIsDeleted() {
        expectToken();
        expectCreate("kc-2");
        server.expect(requestTo(ADMIN + "/users/kc-2/role-mappings/realm")).andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo(ADMIN + "/users/kc-2")).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());

        assertThatThrownBy(() -> client.createUser("ana@example.test", "Ana Gomez Ruiz", "ENFERMERIA", true))
                .isInstanceOf(KeycloakUnavailableException.class);
        server.verify();
    }

    @Test
    void aServerErrorIsReportedAsUnavailable() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users")).andRespond(withServerError());

        assertThatThrownBy(() -> client.createUser("ana@example.test", "Ana Gomez Ruiz", "ENFERMERIA", true))
                .isInstanceOf(KeycloakUnavailableException.class).hasMessageContaining("500");
    }

    @Test
    void aNetworkFailureIsReportedAsUnavailable() {
        server.expect(requestTo(KC + "/realms/auditor/protocol/openid-connect/token"))
                .andRespond(withException(new IOException("conexion rechazada")));

        assertThatThrownBy(() -> client.sendInvitation("kc-1")).isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    void aRejectedServiceClientIsReportedAsUnavailable() {
        server.expect(requestTo(KC + "/realms/auditor/protocol/openid-connect/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.sendInvitation("kc-1")).isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    void withoutTheServiceSecretNothingIsCalled() {
        KeycloakAdminClient noSecret = new KeycloakAdminClient(new KeycloakAdminProperties(KC, "auditor", "auditor-admin", ""),
                "http://localhost:4200", RestClient.builder());
        assertThatThrownBy(() -> noSecret.sendInvitation("kc-1")).isInstanceOf(KeycloakUnavailableException.class)
                .hasMessageContaining("secreto");
    }

    @Test
    void updatingToAnotherRoleRemovesTheOldOneAndSetsTheNewOne() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/kc-1")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"firstName\":\"Ana\",\"lastName\":\"Gomez Ruiz\",\"enabled\":true}"))
                .andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-1/role-mappings/realm")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"r0\",\"name\":\"ENFERMERIA\"},{\"id\":\"rx\",\"name\":\"default-roles-auditor\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/kc-1/role-mappings/realm")).andExpect(method(HttpMethod.DELETE))
                .andExpect(content().json("[{\"id\":\"r0\",\"name\":\"ENFERMERIA\"}]")).andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/roles/MEDICO")).andRespond(withSuccess("{\"id\":\"r1\",\"name\":\"MEDICO\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/kc-1/role-mappings/realm")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":\"r1\",\"name\":\"MEDICO\"}]")).andRespond(withNoContent());

        client.updateUser("kc-1", "Ana Gomez Ruiz", "MEDICO", true);
        server.verify();
    }

    @Test
    void disablingAlsoClosesTheUserSessions() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/kc-1")).andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"enabled\":false}")).andRespond(withNoContent());
        server.expect(requestTo(ADMIN + "/users/kc-1/role-mappings/realm"))
                .andRespond(withSuccess("[{\"id\":\"r0\",\"name\":\"ENFERMERIA\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ADMIN + "/users/kc-1/logout")).andExpect(method(HttpMethod.POST)).andRespond(withNoContent());

        client.updateUser("kc-1", "Ana Gomez Ruiz", "ENFERMERIA", false);
        server.verify();
    }

    @Test
    void theInvitationAsksForANewPasswordAndReturnsToTheApp() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users/kc-1/execute-actions-email?client_id=auditor-web&redirect_uri=http://localhost:4200"))
                .andExpect(method(HttpMethod.PUT)).andExpect(queryParam("client_id", "auditor-web"))
                .andExpect(content().json("[\"UPDATE_PASSWORD\"]")).andRespond(withNoContent());

        client.sendInvitation("kc-1");
        server.verify();
    }

    @Test
    void findsAUserByExactEmail() {
        expectToken();
        server.expect(requestTo(ADMIN + "/users?email=ana@example.test&exact=true")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"kc-9\"}]", MediaType.APPLICATION_JSON));
        assertThat(client.findIdByEmail("ana@example.test")).contains("kc-9");
    }

    @Test
    void namesAreSplitIntoFirstAndLastName() {
        assertThat(KeycloakAdminClient.split("Ana Gomez Ruiz")).containsExactly("Ana", "Gomez Ruiz");
        assertThat(KeycloakAdminClient.split("  Madonna ")).containsExactly("Madonna", "Madonna");
    }
}
