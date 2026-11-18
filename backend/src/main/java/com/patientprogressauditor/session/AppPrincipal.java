package com.patientprogressauditor.session;

import java.util.Set;
import java.util.UUID;

import com.patientprogressauditor.user.AppRole;

// Quien actua en la peticion: sale del JWT de Keycloak. El rol viene del token y el autor (D12) es el AppUser de ese correo.
// `id` es nulo solo para ADMIN, que no tiene AppUser (no lee datos clinicos). Un rol clinico con `id` nulo es un usuario
// de Keycloak sin cuenta en la aplicacion: LinkedUserFilter lo rechaza con 403.
public record AppPrincipal(UUID id, String displayName, String role, Set<String> permissions) {

    public boolean unlinked() {
        return id == null && !AppRole.ADMIN.equals(role);
    }
}
