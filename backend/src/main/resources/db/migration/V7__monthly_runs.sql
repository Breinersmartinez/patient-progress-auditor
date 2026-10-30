-- Informe mensual (E13, RF-36): la ejecucion que lo produce es de tipo MENSUAL, distinta de la del semanal. El indice de «una
-- sola ejecucion viva» (V6) pasa a incluir el tipo: un semanal y un mensual del mismo paciente que empiezan el mismo lunes no
-- deben bloquearse entre si.
ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_type_check;
ALTER TABLE agent_runs ADD CONSTRAINT agent_runs_type_check CHECK (run_type IN ('INFORME', 'MENSUAL', 'ESTRUCTURACION'));

DROP INDEX uq_agent_runs_live_report;
CREATE UNIQUE INDEX uq_agent_runs_live_report
    ON agent_runs (patient_id, run_type, period_start)
    WHERE run_type IN ('INFORME', 'MENSUAL') AND status IN ('EN_COLA', 'EN_CURSO');
