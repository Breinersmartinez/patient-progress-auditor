package com.patientprogressauditor.session;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {

    // `id` es el del AppUser del correo del token (tambien para ADMIN).
    public record SessionView(UUID id, String displayName, String role, List<String> permissions) {
    }

    @GetMapping("/session")
    public SessionView session(@AuthenticationPrincipal AppPrincipal principal) {
        return new SessionView(principal.id(), principal.displayName(), principal.role(),
                principal.permissions().stream().sorted().toList());
    }
}
