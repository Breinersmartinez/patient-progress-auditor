package com.patientprogressauditor.useradmin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.session.AppPrincipal;
import com.patientprogressauditor.useradmin.UserAdminService.CreatedUser;
import com.patientprogressauditor.useradmin.UserAdminService.NewUser;
import com.patientprogressauditor.useradmin.UserAdminService.UserChanges;
import com.patientprogressauditor.useradmin.UserAdminService.UserView;

// Gestion de usuarios del administrador (RF-33). Solo ADMIN (SecurityConfig lo exige por ruta y aqui por metodo). No hay
// DELETE: un usuario se desactiva, nunca se borra, porque su nombre firma el historial.
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    // Los mensajes de campo los pone el servicio; aqui solo la forma.
    public record CreateUserRequest(String name, String email, String role, Boolean active) {
    }

    // Sin correo, a proposito: si llega, se ignora (no hay donde guardarlo).
    public record UpdateUserRequest(String name, String role, boolean active) {
    }

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUser create(@RequestBody CreateUserRequest request) {
        return service.create(new NewUser(request.name(), request.email(), request.role(), request.active()));
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable UUID id, @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal AppPrincipal actor) {
        return service.update(id, new UserChanges(request.name(), request.role(), request.active()), actor);
    }

    @PostMapping("/{id}/invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendInvitation(@PathVariable UUID id) {
        service.resendInvitation(id);
    }
}
