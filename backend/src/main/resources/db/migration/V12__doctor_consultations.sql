-- Consulta del medico, "Armar informe" (D31, RF-46; docs/DICCIONARIO_DATOS_M2.md 4.15): el medico arma, de sus propios
-- registros, una consulta de un periodo (no necesariamente una semana) que no es un informe: no tiene estado, no pasa
-- por enfermeria y solo la ve su autor. Vive aparte de `reports` para que ninguna consulta a informes la devuelva.

ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_type_check;
ALTER TABLE agent_runs ADD CONSTRAINT agent_runs_type_check
    CHECK (run_type IN ('INFORME', 'MENSUAL', 'ESTRUCTURACION', 'TRANSCRIPCION', 'CONSULTA'));

CREATE TABLE doctor_consultations (
    id                UUID PRIMARY KEY,
    doctor_id         UUID NOT NULL REFERENCES app_users (id),
    patient_id        UUID NOT NULL REFERENCES patients (id),
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    comparison_start  DATE,
    comparison_end    DATE,
    content           JSONB,
    plain_text        TEXT,
    run_id            UUID NOT NULL REFERENCES agent_runs (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT doctor_consultations_period_check CHECK (period_end >= period_start),
    CONSTRAINT doctor_consultations_comparison_pair_check
        CHECK ((comparison_start IS NULL) = (comparison_end IS NULL)),
    CONSTRAINT doctor_consultations_comparison_order_check
        CHECK (comparison_end IS NULL OR comparison_end >= comparison_start),
    CONSTRAINT doctor_consultations_body_check CHECK (content IS NOT NULL OR plain_text IS NOT NULL)
);

-- Cada medico, lo suyo, lo mas reciente primero.
CREATE INDEX idx_doctor_consultations_doctor ON doctor_consultations (doctor_id, created_at DESC);

INSERT INTO app_permissions (id, code)
VALUES ('00000000-0000-0000-0000-000000000608', 'CONSULTATIONS_REQUEST')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM app_roles r
JOIN app_permissions p ON p.code = 'CONSULTATIONS_REQUEST'
WHERE r.code = 'MEDICO'
ON CONFLICT DO NOTHING;
