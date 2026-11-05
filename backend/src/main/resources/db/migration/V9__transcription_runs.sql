-- Dictado por voz (RF-38, D14): cada transcripcion es una ejecucion de tipo TRANSCRIPCION, con su traza y su costo, como la
-- estructuracion. El audio NO se guarda: esta tabla solo recibe la fila de la ejecucion (sin texto ni audio).
ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_type_check;
ALTER TABLE agent_runs ADD CONSTRAINT agent_runs_type_check CHECK (run_type IN ('INFORME', 'MENSUAL', 'ESTRUCTURACION', 'TRANSCRIPCION'));
