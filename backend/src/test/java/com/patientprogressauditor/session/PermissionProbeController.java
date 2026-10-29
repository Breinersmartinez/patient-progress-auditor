package com.patientprogressauditor.session;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Solo en pruebas: un endpoint por permiso, con la misma anotacion que usaran los endpoints reales.
@RestController
class PermissionProbeController {

    @GetMapping("/api/test-probe/{permission}")
    @PreAuthorize("hasAuthority(#permission)")
    String probe(@PathVariable String permission) {
        return "ok";
    }
}
