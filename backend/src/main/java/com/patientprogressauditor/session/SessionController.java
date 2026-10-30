package com.patientprogressauditor.session;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppUserRepository;

@RestController
@RequestMapping("/api")
public class SessionController {

    public record DemoUserView(UUID id, String displayName, String role) {
    }

    public record SessionView(UUID id, String displayName, String role, List<String> permissions) {
    }

    private final AppUserRepository users;

    public SessionController(AppUserRepository users) {
        this.users = users;
    }

    // Publico: es lo que se muestra para elegir con quien trabajar (D26). Nunca expone correo ni credenciales.
    @GetMapping("/demo-users")
    public List<DemoUserView> demoUsers() {
        return users.findByDemoTrueAndActiveTrueOrderByDisplayName().stream()
                .filter(user -> !user.getRoles().isEmpty())
                .map(user -> new DemoUserView(user.getId(), user.getDisplayName(),
                        user.getRoles().stream().map(AppRole::getCode).sorted().findFirst().orElseThrow()))
                .toList();
    }

    @GetMapping("/session")
    public SessionView session(@AuthenticationPrincipal DemoPrincipal principal) {
        return new SessionView(principal.id(), principal.displayName(), principal.role(),
                principal.permissions().stream().sorted().toList());
    }
}
