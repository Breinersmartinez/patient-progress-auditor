package com.patientprogressauditor.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.patientprogressauditor.session.AppPrincipal;
import com.patientprogressauditor.support.AuditCleanup;

// Paso 2 de docs/auditoria/plan.md: la bitacora (V13 y AuditService). Sin @Transactional en la clase a proposito: se
// miran los commits y los rollbacks de verdad, y cada prueba limpia lo suyo con AuditCleanup.
@SpringBootTest
class AuditServiceTest {

    @Autowired private AuditService audit;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;

    private final List<UUID> created = new ArrayList<>();
    private AppPrincipal actor;

    @BeforeEach
    void actor() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_users (id, email, display_name, is_demo) VALUES (?, ?, 'Auditora de prueba', TRUE)",
                id, id + "@audittest.local");
        created.add(id);
        actor = new AppPrincipal(id, "Auditora de prueba", "ENFERMERIA", Set.of());
    }

    @AfterEach
    void cleanUp() {
        AuditCleanup.deleteFor(jdbc, created);
        jdbc.update("DELETE FROM app_users WHERE email LIKE '%@audittest.local'");
    }

    private UUID object() {
        UUID id = UUID.randomUUID();
        created.add(id);
        return id;
    }

    private int rowsFor(UUID entityId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Integer.class, entityId);
    }

    @Test
    void recordsWhoWhatWhenAndTheStatesInsideATransaction() {
        UUID report = object();

        transaction.executeWithoutResult(s -> audit.record(actor, AuditAction.APROBAR_INFORME, AuditEntity.REPORT, report,
                Map.of("status", "PENDIENTE_REVISION"), Map.of("status", "APROBADO", "versionNo", 2)));

        Map<String, Object> row = jdbc.queryForMap("SELECT actor_id, actor_role, action, entity_type, occurred_at,"
                + " before_state->>'status' AS before_status, after_state->>'status' AS after_status,"
                + " (after_state->>'versionNo')::int AS version FROM audit_logs WHERE entity_id = ?", report);
        assertThat(row).containsEntry("actor_id", actor.id()).containsEntry("actor_role", "ENFERMERIA")
                .containsEntry("action", "APROBAR_INFORME").containsEntry("entity_type", "REPORT")
                .containsEntry("before_status", "PENDIENTE_REVISION").containsEntry("after_status", "APROBADO")
                .containsEntry("version", 2);
        assertThat(row.get("occurred_at")).isNotNull();
    }

    @Test
    void withoutATransactionItRefusesSoNoAnnotationIsEverLeftAlone() {
        assertThatThrownBy(() -> audit.record(actor, AuditAction.APROBAR_INFORME, AuditEntity.REPORT, object(), null, null))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void ifTheActionRollsBackTheAnnotationGoesWithIt() {
        UUID report = object();

        assertThatThrownBy(() -> transaction.executeWithoutResult(s -> {
            audit.record(actor, AuditAction.ENVIAR_INFORME, AuditEntity.REPORT, report, null, Map.of("status", "ENVIADO"));
            throw new IllegalStateException("la accion fallo despues de anotar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowsFor(report)).isZero();
    }

    @Test
    void theNewActionsAndTheUserTypeAreAccepted() {
        UUID user = object();

        assertThatCode(() -> transaction.executeWithoutResult(s -> {
            audit.record(actor, AuditAction.CREAR_USUARIO, AuditEntity.USER, user, null, Map.of("role", "MEDICO"));
            audit.record(actor, AuditAction.ACTUALIZAR_USUARIO, AuditEntity.USER, user,
                    Map.of("active", true), Map.of("active", false));
            audit.record(actor, AuditAction.ENVIAR_INFORME, AuditEntity.REPORT, object(), null, null);
            audit.record(actor, AuditAction.REGISTRAR_ENTRADA_MEDICO, AuditEntity.ENTRY, object(), null, null);
        })).doesNotThrowAnyException();
        assertThat(rowsFor(user)).isEqualTo(2);
    }

    @Test
    void anActionOutsideTheCatalogIsRejectedByTheDatabase() {
        UUID report = object();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_logs (actor_id, actor_role, action, entity_type, entity_id)"
                + " VALUES (?, 'ENFERMERIA', 'BORRAR_TODO', 'REPORT', ?)", actor.id(), report))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Criterio 6 del encargo: ni la aplicacion ni una consola con el usuario de la aplicacion pueden cambiar o borrar.
    // Spring traduce el SQLSTATE 42501 del trigger a BadSqlGrammarException: el texto de PostgreSQL esta en la causa raiz.
    @Test
    void updateDeleteAndTruncateAreRejectedByTheDatabase() {
        UUID report = object();
        transaction.executeWithoutResult(s -> audit.record(actor, AuditAction.RECHAZAR_INFORME, AuditEntity.REPORT, report,
                Map.of("status", "PENDIENTE_REVISION"), Map.of("status", "RECHAZADO")));

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET action = 'APROBAR_INFORME' WHERE entity_id = ?", report))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("solo agregar");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_logs WHERE entity_id = ?", report))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("solo agregar");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_logs"))
                .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("solo agregar");

        assertThat(jdbc.queryForObject("SELECT action FROM audit_logs WHERE entity_id = ?", String.class, report))
                .isEqualTo("RECHAZAR_INFORME");
    }

    // La limpieza de las pruebas deja el trigger encendido aunque haya borrado.
    @Test
    void theTestCleanupLeavesTheProtectionOn() {
        UUID report = object();
        transaction.executeWithoutResult(s -> audit.record(actor, AuditAction.EDITAR_INFORME, AuditEntity.REPORT, report,
                null, null));

        AuditCleanup.deleteFor(jdbc, List.of(report));

        assertThat(rowsFor(report)).isZero();
        assertThat(jdbc.queryForObject("SELECT tgenabled FROM pg_trigger WHERE tgname = 'audit_logs_no_update_delete'",
                String.class)).isEqualTo("O");
    }
}
