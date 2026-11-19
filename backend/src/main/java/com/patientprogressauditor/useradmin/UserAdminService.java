package com.patientprogressauditor.useradmin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.patientprogressauditor.audit.AuditAction;
import com.patientprogressauditor.audit.AuditEntity;
import com.patientprogressauditor.audit.AuditService;
import com.patientprogressauditor.keycloak.KeycloakAdminClient;
import com.patientprogressauditor.keycloak.KeycloakUnavailableException;
import com.patientprogressauditor.keycloak.KeycloakUserExistsException;
import com.patientprogressauditor.session.AppPrincipal;
import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppRoleRepository;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

// Gestion de usuarios del administrador (RF-33, docs/admin-usuarios). Cada cambio vive en dos sitios, la fila de app_users y
// el usuario de Keycloak, que no comparten transaccion: la base se escribe primero SIN confirmar, luego Keycloak, y solo si
// Keycloak responde se confirma. Si Keycloak falla, la base se revierte; si el commit falla despues de que Keycloak ya
// cambio, el alta se deshace en Keycloak. El administrador gestiona cuentas: nada de esto lee datos clinicos.
@Service
public class UserAdminService {

    private static final Logger LOG = LoggerFactory.getLogger(UserAdminService.class);
    private static final Set<String> ROLES = Set.of(AppRole.ADMIN, AppRole.NURSING, AppRole.DOCTOR);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public record UserView(UUID id, String name, String email, String role, boolean active) {
    }

    // `invitationSent` es falso si la cuenta se creo pero el correo no salio: la pantalla avisa y ofrece reenviarlo.
    public record CreatedUser(UserView user, boolean invitationSent) {
    }

    // `active` nulo = activo (como antes); inactivo crea la cuenta ya desactivada y sin invitacion: no puede entrar.
    public record NewUser(String name, String email, String role, Boolean active) {
    }

    // Sin correo: es el enlace con Keycloak y con el autor de las acciones historicas (D12), no se edita.
    public record UserChanges(String name, String role, boolean active) {
    }

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final KeycloakAdminClient keycloak;
    private final TransactionTemplate transaction;
    private final AuditService audit;

    public UserAdminService(AppUserRepository users, AppRoleRepository roles, KeycloakAdminClient keycloak,
            TransactionTemplate transaction, AuditService audit) {
        this.audit = audit;
        this.users = users;
        this.roles = roles;
        this.keycloak = keycloak;
        this.transaction = transaction;
    }

    public List<UserView> list() {
        return users.findAllByOrderByDisplayNameAsc().stream().map(UserAdminService::view).toList();
    }

    public CreatedUser create(NewUser request, AppPrincipal actor) {
        String name = request.name() == null ? "" : request.name().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        validate(name, request.role(), email);
        boolean active = request.active() == null || request.active();
        if (users.existsByEmailIgnoreCase(email)) {
            throw emailTaken();
        }
        String[] keycloakId = new String[1];
        UserView created;
        try {
            created = transaction.execute(status -> {
                AppUser user = new AppUser();
                user.setEmail(email);
                user.setDisplayName(name);
                user.setActive(active);
                user.getRoles().add(roles.findByCode(request.role()).orElseThrow());
                users.saveAndFlush(user);
                // RF-35: antes de Keycloak, que no es transaccional. Si la anotacion falla, Keycloak no se toca.
                audit.record(actor, AuditAction.CREAR_USUARIO, AuditEntity.USER, user.getId(), null, state(name, request.role(), active));
                keycloakId[0] = keycloak.createUser(email, name, request.role(), active);
                return view(user);
            });
        } catch (KeycloakUserExistsException e) {
            throw emailTaken();
        } catch (DataIntegrityViolationException e) {
            undoKeycloak(keycloakId[0]);
            throw emailTaken();
        } catch (RuntimeException e) {
            // El commit pudo fallar despues de crear en Keycloak: no queda un usuario sin su fila.
            undoKeycloak(keycloakId[0]);
            throw e;
        }
        return new CreatedUser(created, active && sendInvitation(keycloakId[0], email));
    }

    public UserView update(UUID id, UserChanges changes, AppPrincipal actor) {
        AppUser user = find(id);
        String name = changes.name() == null ? "" : changes.name().trim();
        validate(name, changes.role(), null);
        String currentRole = roleOf(user);
        boolean sameRole = currentRole.equals(changes.role());
        if (id.equals(actor.id())) {
            if (!sameRole) {
                throw new FieldException(HttpStatus.CONFLICT, "role", "No puede cambiar su propio rol.");
            }
            if (!changes.active()) {
                throw new FieldException(HttpStatus.CONFLICT, "active", "No puede desactivar su propia cuenta.");
            }
        }
        boolean losesAdmin = AppRole.ADMIN.equals(currentRole) && user.isActive() && (!sameRole || !changes.active());
        if (losesAdmin && users.countActiveAdmins() <= 1) {
            throw new FieldException(HttpStatus.CONFLICT, sameRole ? "active" : "role",
                    "Debe quedar al menos un administrador activo.");
        }
        String keycloakId = keycloak.findIdByEmail(user.getEmail()).orElseThrow(
                () -> new KeycloakUnavailableException("El usuario " + user.getEmail() + " no existe en Keycloak."));
        Map<String, Object> before = state(user.getDisplayName(), currentRole, user.isActive());
        return transaction.execute(status -> {
            user.setDisplayName(name);
            user.setActive(changes.active());
            user.getRoles().clear();
            user.getRoles().add(roles.findByCode(changes.role()).orElseThrow());
            user.setUpdatedAt(Instant.now());
            users.saveAndFlush(user);
            // RF-35: antes de Keycloak, por la misma razon que al crear. Incluye el cambio de rol y la desactivacion.
            audit.record(actor, AuditAction.ACTUALIZAR_USUARIO, AuditEntity.USER, user.getId(), before,
                    state(name, changes.role(), changes.active()));
            keycloak.updateUser(keycloakId, name, changes.role(), changes.active());
            return view(user);
        });
    }

    // Reenviar deja sin efecto la invitacion anterior; un usuario desactivado primero se reactiva.
    // Lo que la bitacora guarda de un usuario: nombre, rol y estado. Nunca el correo ni credenciales.
    private static Map<String, Object> state(String name, String role, boolean active) {
        return Map.of("name", name, "role", role, "active", active);
    }

    public void resendInvitation(UUID id) {
        AppUser user = find(id);
        if (!user.isActive()) {
            throw new FieldException(HttpStatus.CONFLICT, "active", "Reactive al usuario antes de reenviar la invitación.");
        }
        String keycloakId = keycloak.findIdByEmail(user.getEmail()).orElseThrow(
                () -> new KeycloakUnavailableException("El usuario " + user.getEmail() + " no existe en Keycloak."));
        keycloak.sendInvitation(keycloakId);
    }

    private boolean sendInvitation(String keycloakId, String email) {
        try {
            keycloak.sendInvitation(keycloakId);
            return true;
        } catch (KeycloakUnavailableException e) {
            LOG.error("El usuario {} se creo pero no se pudo enviar su invitacion.", email, e);
            return false;
        }
    }

    private void undoKeycloak(String keycloakId) {
        if (keycloakId == null) {
            return;
        }
        try {
            keycloak.deleteUser(keycloakId);
        } catch (RuntimeException e) {
            LOG.error("Quedo un usuario huerfano en Keycloak (id {}): se creo y la base no lo confirmo.", keycloakId, e);
        }
    }

    private AppUser find(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("No encontramos a ese usuario."));
    }

    private static void validate(String name, String role, String email) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (name.isEmpty()) {
            errors.put("name", "Escriba el nombre.");
        } else if (name.length() > 100) {
            errors.put("name", "El nombre no puede pasar de 100 caracteres.");
        }
        if (email != null && (!EMAIL.matcher(email).matches() || email.length() > 320)) {
            errors.put("email", "Escriba un correo válido.");
        }
        if (role == null || !ROLES.contains(role)) {
            errors.put("role", "Elija un rol.");
        }
        if (!errors.isEmpty()) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "Revise los datos indicados.", errors);
        }
    }

    private static FieldException emailTaken() {
        return new FieldException(HttpStatus.CONFLICT, "email", "Ya existe un usuario con ese correo.");
    }

    private static String roleOf(AppUser user) {
        return user.getRoles().stream().map(AppRole::getCode).sorted().findFirst().orElse("");
    }

    private static UserView view(AppUser user) {
        return new UserView(user.getId(), user.getDisplayName(), user.getEmail(), roleOf(user), user.isActive());
    }
}
