package com.patientprogressauditor.support;

import java.util.Collection;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

// Solo para pruebas. audit_logs es de solo agregar (V13): un trigger rechaza UPDATE y DELETE. Las pruebas que crean y
// borran sus propios usuarios u objetos necesitan quitar antes sus anotaciones, o la clave foranea actor_id impide
// borrar al usuario. Esto apaga el trigger solo durante el DELETE y lo vuelve a encender siempre.
//
// Funciona porque en las pruebas el usuario de la base es el duenio de la tabla (el mismo que corre Flyway): es el
// limite que declara docs/auditoria/plan.md, y por eso este ayudante no existe en src/main.
public final class AuditCleanup {

    private AuditCleanup() {
    }

    // Borra las anotaciones hechas por esos usuarios o sobre esos objetos.
    public static void deleteFor(JdbcTemplate jdbc, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        UUID[] array = ids.toArray(UUID[]::new);
        jdbc.execute("ALTER TABLE audit_logs DISABLE TRIGGER audit_logs_no_update_delete");
        try {
            jdbc.update("DELETE FROM audit_logs WHERE actor_id = ANY (?) OR entity_id = ANY (?)", array, array);
        } finally {
            jdbc.execute("ALTER TABLE audit_logs ENABLE TRIGGER audit_logs_no_update_delete");
        }
    }

    // Lo mismo para los usuarios cuyo correo termina en `domain` (las pruebas de administracion usan un dominio propio).
    public static void deleteForUsersOf(JdbcTemplate jdbc, String domain) {
        deleteFor(jdbc, jdbc.queryForList("SELECT id FROM app_users WHERE email LIKE ?", UUID.class, "%" + domain));
    }
}
