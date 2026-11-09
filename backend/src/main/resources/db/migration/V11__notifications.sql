-- Notificaciones al medico (D30, RF-45; docs/DICCIONARIO_DATOS_M2.md 4.14): un aviso por entrada guardada o informe
-- enviado, sin texto clinico. Se crea en la misma transaccion que guarda la entrada o envia el informe.

CREATE TABLE notifications (
    id                 UUID PRIMARY KEY,
    recipient_id       UUID NOT NULL REFERENCES app_users (id),
    patient_id         UUID NOT NULL REFERENCES patients (id),
    notification_type  VARCHAR(20) NOT NULL CHECK (notification_type IN ('ENTRY', 'REPORT')),
    entry_id           UUID REFERENCES entries (id),
    report_id          UUID REFERENCES reports (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at            TIMESTAMPTZ,
    CONSTRAINT notifications_entry_check
        CHECK ((notification_type = 'ENTRY') = (entry_id IS NOT NULL AND report_id IS NULL)),
    CONSTRAINT notifications_report_check
        CHECK ((notification_type = 'REPORT') = (report_id IS NOT NULL AND entry_id IS NULL))
);

-- Listar las propias y contar las sin leer; marcar leidas las de un paciente al abrir su ficha.
CREATE INDEX idx_notifications_recipient_read ON notifications (recipient_id, read_at);
CREATE INDEX idx_notifications_recipient_patient_read ON notifications (recipient_id, patient_id, read_at);

INSERT INTO app_permissions (id, code)
VALUES ('00000000-0000-0000-0000-000000000607', 'NOTIFICATIONS_READ')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM app_roles r
JOIN app_permissions p ON p.code = 'NOTIFICATIONS_READ'
WHERE r.code = 'MEDICO'
ON CONFLICT DO NOTHING;
