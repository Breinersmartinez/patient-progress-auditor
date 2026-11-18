package com.patientprogressauditor.audit;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.audit.AuditQueryService.Page;

// Bitacora de acciones (RF-35): solo lectura y solo el administrador. Ademas de esta anotacion, SecurityConfig exige el rol
// ADMIN para todo /api/admin/**: enfermeria y medico reciben 403. No hay POST, PUT ni DELETE: nadie edita la bitacora.
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public Page list(@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page) {
        return service.list(actorId, action, page);
    }
}
