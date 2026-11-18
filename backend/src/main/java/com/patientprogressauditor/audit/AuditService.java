package com.patientprogressauditor.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.session.AppPrincipal;

// Bitacora de acciones sensibles (RF-35, docs/auditoria/). Una fila por accion, escrita en la MISMA transaccion que la
// accion: si la accion se revierte, la fila tambien; si la fila falla, la accion se revierte. Por eso MANDATORY: llamarla
// sin transaccion es un error de programacion y salta en las pruebas, no queda una anotacion suelta.
//
// Solo metadatos. Quien llama pasa en `before` y `after` estados, versiones, roles o longitudes, nunca el texto de un
// registro o de un informe ni el nombre de un paciente: el administrador lee esta tabla y no debe ver datos clinicos.
@Service
public class AuditService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AppPrincipal actor, AuditAction action, AuditEntity entity, UUID entityId,
            Map<String, ?> before, Map<String, ?> after) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(entityId, "entityId");
        jdbc.update("INSERT INTO audit_logs (actor_id, actor_role, action, entity_type, entity_id, before_state, after_state)"
                + " VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB))",
                actor.id(), actor.role(), action.name(), entity.name(), entityId, toJson(before), toJson(after));
    }

    private String toJson(Map<String, ?> state) {
        if (state == null) {
            return null;
        }
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Estado de auditoria no serializable", e);
        }
    }
}
