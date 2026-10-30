-- Una sola ejecucion viva de informe por paciente y semana (D24): si llegan dos peticiones a la vez, la base rechaza la
-- segunda y el servicio devuelve la que ya corre. Una ejecucion terminada (COMPLETADA, INCOMPLETA, FALLIDA) no bloquea.
CREATE UNIQUE INDEX uq_agent_runs_live_report
    ON agent_runs (patient_id, period_start)
    WHERE run_type = 'INFORME' AND status IN ('EN_COLA', 'EN_CURSO');
