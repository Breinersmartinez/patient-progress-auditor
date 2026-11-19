package com.patientprogressauditor.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.web.FieldException;

// Lectura de la bitacora para el administrador (RF-35, paso 4 de docs/auditoria/plan.md). Solo lectura: esta clase no
// tiene ningun metodo que escriba. "Sobre que" se arma al leer con codigos y fechas (codigo del paciente, tipo y semana
// del informe, fecha de la entrada, nombre del usuario), nunca con el texto de un registro o de un informe.
@Service
public class AuditQueryService {

    public static final int PAGE_SIZE = 50;

    public record Actor(UUID id, String name, String role) {
    }

    // Lo que identifica al objeto sin datos clinicos. Los campos que no aplican al tipo van en null; si el objeto ya no
    // existe (por ejemplo, un informe borrado), todos van en null y la pantalla muestra solo el tipo.
    public record Subject(String patientCode, String reportType, LocalDate periodStart, LocalDate entryDate, String userName) {
    }

    public record Item(long id, Instant occurredAt, Actor actor, String action, String entityType, UUID entityId,
            Subject subject, JsonNode before, JsonNode after) {
    }

    public record Page(List<Item> items, int page, int size, long total) {
    }

    // Los joins solo resuelven "sobre que" y el nombre de quien actuo; no cambian cuantas filas hay (actor_id tiene clave
    // foranea a app_users), asi que el total se cuenta sobre audit_logs sola.
    private static final String JOINS = """
             FROM audit_logs a
             JOIN app_users u ON u.id = a.actor_id
             LEFT JOIN reports r ON a.entity_type = 'REPORT' AND r.id = a.entity_id
             LEFT JOIN patients rp ON rp.id = r.patient_id
             LEFT JOIN entries e ON a.entity_type = 'ENTRY' AND e.id = a.entity_id
             LEFT JOIN patients ep ON ep.id = e.patient_id
             LEFT JOIN app_users tu ON a.entity_type = 'USER' AND tu.id = a.entity_id
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditQueryService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // `actorId` y `action` son opcionales; `page` empieza en 0. La mas reciente primero; el id desempata dos anotaciones
    // del mismo instante para que las paginas no repitan ni omitan filas.
    @Transactional(readOnly = true)
    public Page list(UUID actorId, String action, int page) {
        String code = action == null || action.isBlank() ? null : action.trim().toUpperCase(Locale.ROOT);
        if (code != null && !isAction(code)) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "action", "Ese tipo de acción no existe.");
        }
        if (page < 0) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "page", "La página no puede ser negativa.");
        }
        // El WHERE lleva solo los filtros pedidos (sin "? IS NULL OR ..."), para que PostgreSQL use los indices por actor
        // (V3) y por accion y fecha (V13). Los valores van siempre como parametros, nunca concatenados.
        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> args = new ArrayList<>();
        if (actorId != null) {
            where.append(" AND a.actor_id = ?");
            args.add(actorId);
        }
        if (code != null) {
            where.append(" AND a.action = ?");
            args.add(code);
        }
        Long counted = jdbc.queryForObject("SELECT count(*) FROM audit_logs a" + where, Long.class, args.toArray());
        long total = counted == null ? 0 : counted;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(PAGE_SIZE);
        pageArgs.add((long) page * PAGE_SIZE);
        List<Item> items = jdbc.query("""
                SELECT a.id, a.occurred_at, a.actor_id, u.display_name AS actor_name, a.actor_role, a.action,
                       a.entity_type, a.entity_id, a.before_state::text AS before_state, a.after_state::text AS after_state,
                       coalesce(rp.alias, ep.alias) AS patient_code, r.report_type, r.period_start, e.entry_date,
                       tu.display_name AS user_name
                """ + JOINS + where + " ORDER BY a.occurred_at DESC, a.id DESC LIMIT ? OFFSET ?", this::item,
                pageArgs.toArray());
        return new Page(new ArrayList<>(items), page, PAGE_SIZE, total);
    }

    private static boolean isAction(String code) {
        for (AuditAction action : AuditAction.values()) {
            if (action.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private Item item(ResultSet rs, int row) throws SQLException {
        Subject subject = new Subject(rs.getString("patient_code"), rs.getString("report_type"),
                rs.getObject("period_start", LocalDate.class), rs.getObject("entry_date", LocalDate.class),
                rs.getString("user_name"));
        return new Item(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
                new Actor(rs.getObject("actor_id", UUID.class), rs.getString("actor_name"), rs.getString("actor_role")),
                rs.getString("action"), rs.getString("entity_type"), rs.getObject("entity_id", UUID.class), subject,
                parse(rs.getString("before_state")), parse(rs.getString("after_state")));
    }

    private JsonNode parse(String state) {
        if (state == null) {
            return null;
        }
        try {
            return json.readTree(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Estado de auditoria ilegible", e);
        }
    }
}
