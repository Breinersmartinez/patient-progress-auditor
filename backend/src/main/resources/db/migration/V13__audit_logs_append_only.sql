-- Auditoria de acciones (RF-35; docs/auditoria/especificacion.md y plan.md): la tabla audit_logs existe desde V3 y nadie
-- la usaba. Aqui se amplian sus valores y se vuelve de solo agregar.
--
-- Acciones nuevas: ENVIAR_INFORME (D29), CREAR_USUARIO y ACTUALIZAR_USUARIO (RF-33; el cambio de rol de un usuario
-- cumple la funcion de CAMBIAR_PERMISOS, porque RF-34 se descarto). Se conservan las cinco de V3, incluida
-- EDITAR_ENTRADA, reservada para cuando se construya RF-03. Tipo de objeto nuevo: USER.

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_action_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_action_check CHECK (action IN (
    'APROBAR_INFORME', 'RECHAZAR_INFORME', 'ENVIAR_INFORME', 'EDITAR_INFORME', 'EDITAR_ENTRADA',
    'REGISTRAR_ENTRADA_MEDICO', 'CREAR_USUARIO', 'ACTUALIZAR_USUARIO'));

ALTER TABLE audit_logs DROP CONSTRAINT audit_logs_entity_type_check;
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_entity_type_check CHECK (entity_type IN ('ENTRY', 'REPORT', 'USER'));

-- Solo agregar: la propia base rechaza UPDATE, DELETE y TRUNCATE, venga de la aplicacion o de una consola. Limite
-- declarado: el duenio de la tabla puede desactivar estos triggers con ALTER TABLE; no es una prueba forense.
CREATE FUNCTION audit_logs_append_only() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs es de solo agregar: % no esta permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_logs_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only();

CREATE TRIGGER audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_append_only();

-- La lista del administrador ordena por fecha y filtra por accion (paso 4 del plan); el indice por actor ya existe en V3.
CREATE INDEX idx_audit_logs_occurred ON audit_logs (occurred_at DESC, id DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action, occurred_at DESC);
