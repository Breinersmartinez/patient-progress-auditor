-- Enviar al medico (D29, RF-44; docs/DICCIONARIO_DATOS_M2.md 4.7 y 5.2): un informe aprobado pasa a ENVIADO cuando
-- enfermeria lo envia, y solo entonces lo ve el medico. Es solo esquema y permiso: no toca datos de demostracion, que
-- son de la semilla (que reconcilia los informes que declara enviados).

ALTER TABLE reports ADD COLUMN sent_by UUID REFERENCES app_users (id);
ALTER TABLE reports ADD COLUMN sent_at TIMESTAMPTZ;

ALTER TABLE reports DROP CONSTRAINT reports_status_check;
ALTER TABLE reports ADD CONSTRAINT reports_status_check
    CHECK (status IN ('PENDIENTE_REVISION', 'INCOMPLETO', 'APROBADO', 'ENVIADO', 'RECHAZADO'));

-- Un enviado conserva quien y cuando lo aprobo, y la version que se aprobo.
ALTER TABLE reports DROP CONSTRAINT reports_review_check;
ALTER TABLE reports ADD CONSTRAINT reports_review_check
    CHECK ((status IN ('APROBADO', 'ENVIADO', 'RECHAZADO')) = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL));
ALTER TABLE reports DROP CONSTRAINT reports_approved_version_check;
ALTER TABLE reports ADD CONSTRAINT reports_approved_version_check
    CHECK (status NOT IN ('APROBADO', 'ENVIADO') OR approved_version_no IS NOT NULL);

-- Quien y cuando envio existen exactamente en ENVIADO.
ALTER TABLE reports ADD CONSTRAINT reports_sent_check
    CHECK ((status = 'ENVIADO') = (sent_by IS NOT NULL AND sent_at IS NOT NULL));

-- Un enviado sigue siendo el informe vigente de su periodo: sin esto se podria pedir otro de una semana ya enviada.
DROP INDEX uq_reports_current_period;
CREATE UNIQUE INDEX uq_reports_current_period
    ON reports (patient_id, report_type, period_start)
    WHERE status IN ('PENDIENTE_REVISION', 'APROBADO', 'ENVIADO');

-- Permiso propio, solo de enfermeria, para que el tablero de permisos (RF-34) pueda separarlo de aprobar.
INSERT INTO app_permissions (id, code)
VALUES ('00000000-0000-0000-0000-000000000606', 'REPORTS_SEND')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM app_roles r
JOIN app_permissions p ON p.code = 'REPORTS_SEND'
WHERE r.code = 'ENFERMERIA'
ON CONFLICT DO NOTHING;
