package com.patientprogressauditor.session;

import java.util.Set;
import java.util.UUID;

// Quien actua en la peticion (D26): el usuario de demostracion elegido, con el rol del que toma sus permisos.
public record DemoPrincipal(UUID id, String displayName, String role, Set<String> permissions) {
}
