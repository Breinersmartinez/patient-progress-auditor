package com.patientprogressauditor.session;

import java.util.Set;
import java.util.UUID;

// Quien actua en la peticion: sale del JWT de Keycloak. El rol viene del token y el autor (D12) es el AppUser de ese correo.
// Todo rol (tambien ADMIN, docs/admin-usuarios) tiene su AppUser; la fila de ADMIN solo lo identifica, no le da datos
// clinicos. Un `id` nulo con rol es un usuario de Keycloak sin cuenta activa en la aplicacion (o desactivado):
// LinkedUserFilter lo rechaza con 403 en la siguiente peticion, aunque su token siga vigente.
public record AppPrincipal(UUID id, String displayName, String role, Set<String> permissions) {

    public boolean unlinked() {
        return id == null;
    }
}
