package com.patientprogressauditor.keycloak;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.patientprogressauditor.user.AppRole;

// La unica puerta del backend a la API de administracion de Keycloak (docs/admin-usuarios): crea, edita, desactiva y
// manda la invitacion. Habla por la red de Compose con el cliente de servicio. El resto del codigo no sabe de Keycloak:
// lo que falla sale como KeycloakUnavailableException (502) o KeycloakUserExistsException (409).
public class KeycloakAdminClient {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final List<String> APP_ROLES = List.of(AppRole.ADMIN, AppRole.NURSING, AppRole.DOCTOR);
    private static final String WEB_CLIENT = "auditor-web";

    private final KeycloakAdminProperties properties;
    private final String publicUrl;
    private final RestClient http;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;

    // El constructor recibe el builder ya configurado (timeout en KeycloakConfig; las pruebas le ponen un servidor simulado).
    public KeycloakAdminClient(KeycloakAdminProperties properties, String publicUrl, RestClient.Builder builder) {
        this.properties = properties;
        this.publicUrl = publicUrl;
        this.http = builder.baseUrl(properties.url()).build();
    }

    // Crea el usuario ya habilitado y con su rol; devuelve el id de Keycloak. No manda la invitacion (otro paso, porque su
    // fallo no debe deshacer la cuenta).
    public String createUser(String email, String displayName, String role, boolean enabled) {
        return call(() -> {
            String[] names = split(displayName);
            var response = http.post().uri(admin("/users")).headers(this::bearer).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", email, "email", email, "firstName", names[0], "lastName", names[1],
                            "enabled", enabled, "emailVerified", true))
                    .retrieve().toBodilessEntity();
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new KeycloakUnavailableException("Keycloak no devolvio el id del usuario creado.");
            }
            String path = location.getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            try {
                setRole(id, role);
            } catch (RuntimeException e) {
                deleteQuietly(id);
                throw e;
            }
            return id;
        }, email);
    }

    public Optional<String> findIdByEmail(String email) {
        return call(() -> {
            List<Map<String, Object>> found = http.get()
                    .uri(uriBuilder -> uriBuilder.path(admin("/users")).queryParam("email", email).queryParam("exact", true).build())
                    .headers(this::bearer).retrieve().body(new ParameterizedTypeReference<>() { });
            return found == null || found.isEmpty() ? Optional.<String>empty() : Optional.of((String) found.get(0).get("id"));
        }, email);
    }

    // Deja nombre, rol y estado como los pide la aplicacion. Desactivar tambien cierra sus sesiones: el token ya emitido
    // deja de renovarse (el corte inmediato lo hace ademas el backend, al comprobar app_users.active).
    public void updateUser(String id, String displayName, String role, boolean enabled) {
        call(() -> {
            String[] names = split(displayName);
            http.put().uri(admin("/users/" + id)).headers(this::bearer).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("firstName", names[0], "lastName", names[1], "enabled", enabled))
                    .retrieve().toBodilessEntity();
            setRole(id, role);
            if (!enabled) {
                http.post().uri(admin("/users/" + id + "/logout")).headers(this::bearer).retrieve().toBodilessEntity();
            }
            return null;
        }, id);
    }

    // Correo de "crear contrasena" (UPDATE_PASSWORD), con el enlace de 12 h del realm. Reenviarlo deja sin efecto el anterior.
    public void sendInvitation(String id) {
        call(() -> {
            http.put().uri(uriBuilder -> uriBuilder.path(admin("/users/" + id + "/execute-actions-email"))
                            .queryParam("client_id", WEB_CLIENT).queryParam("redirect_uri", publicUrl).build())
                    .headers(this::bearer).contentType(MediaType.APPLICATION_JSON).body(List.of("UPDATE_PASSWORD"))
                    .retrieve().toBodilessEntity();
            return null;
        }, id);
    }

    // Solo para compensar un alta que no se pudo confirmar en la base.
    public void deleteUser(String id) {
        call(() -> {
            http.delete().uri(admin("/users/" + id)).headers(this::bearer).retrieve().toBodilessEntity();
            return null;
        }, id);
    }

    private void deleteQuietly(String id) {
        try {
            http.delete().uri(admin("/users/" + id)).headers(this::bearer).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            LOG.error("No se pudo deshacer el usuario {} de Keycloak tras un alta fallida.", id, e);
        }
    }

    // Un usuario tiene un solo rol de la aplicacion: se quitan los otros y se pone el pedido.
    private void setRole(String id, String role) {
        List<Map<String, Object>> current = http.get().uri(admin("/users/" + id + "/role-mappings/realm"))
                .headers(this::bearer).retrieve().body(new ParameterizedTypeReference<>() { });
        List<Map<String, Object>> toRemove = current == null ? List.of()
                : current.stream().filter(r -> r.get("name") instanceof String name && APP_ROLES.contains(name) && !role.equals(name)).toList();
        if (!toRemove.isEmpty()) {
            http.method(org.springframework.http.HttpMethod.DELETE).uri(admin("/users/" + id + "/role-mappings/realm"))
                    .headers(this::bearer).contentType(MediaType.APPLICATION_JSON).body(toRemove).retrieve().toBodilessEntity();
        }
        boolean has = current != null && current.stream().anyMatch(r -> role.equals(r.get("name")));
        if (!has) {
            Map<String, Object> representation = http.get().uri(admin("/roles/" + role)).headers(this::bearer)
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            if (representation == null) {
                throw new KeycloakUnavailableException("Keycloak no devolvio el rol " + role + ".");
            }
            http.post().uri(admin("/users/" + id + "/role-mappings/realm")).headers(this::bearer)
                    .contentType(MediaType.APPLICATION_JSON).body(List.of(representation)).retrieve().toBodilessEntity();
        }
    }

    private String admin(String path) {
        return "/admin/realms/" + properties.realm() + path;
    }

    private void bearer(HttpHeaders headers) {
        headers.setBearerAuth(accessToken());
    }

    private synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return token;
        }
        if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new KeycloakUnavailableException("Falta el secreto del cliente de servicio de Keycloak.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        Map<String, Object> response = http.post().uri("/realms/" + properties.realm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (response == null || !(response.get("access_token") instanceof String access)) {
            throw new KeycloakUnavailableException("Keycloak no entrego el token del cliente de servicio.");
        }
        token = access;
        tokenExpiresAt = Instant.now().plusSeconds(((Number) response.getOrDefault("expires_in", 60)).longValue());
        return token;
    }

    private <T> T call(java.util.function.Supplier<T> action, String subject) {
        try {
            return action.get();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 409) {
                throw new KeycloakUserExistsException(subject);
            }
            LOG.error("Keycloak respondio {} al gestionar {}", status.value(), subject);
            throw new KeycloakUnavailableException("Keycloak respondio " + status.value(), e);
        } catch (RestClientException e) {
            LOG.error("No se pudo contactar a Keycloak al gestionar {}", subject, e);
            throw new KeycloakUnavailableException("No se pudo contactar a Keycloak.", e);
        }
    }

    // Keycloak pide nombre y apellido: el primer termino y el resto; con una sola palabra, el apellido repite el nombre.
    static String[] split(String displayName) {
        String name = displayName.trim();
        int space = name.indexOf(' ');
        return space < 0 ? new String[] { name, name } : new String[] { name.substring(0, space), name.substring(space + 1).trim() };
    }
}
