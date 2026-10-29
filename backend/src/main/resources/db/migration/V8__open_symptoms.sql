-- E18: el sintoma deja de ser una lista cerrada de nueve. Un sintoma es un codigo (MAYUSCULAS, sin acentos) y una etiqueta
-- legible; los nueve del catalogo conservan su codigo. La base protege la forma, ya no la lista.
ALTER TABLE entry_events DROP CONSTRAINT entry_events_symptom_check;
ALTER TABLE entry_events ALTER COLUMN symptom TYPE VARCHAR(40);
ALTER TABLE entry_events ADD COLUMN symptom_label VARCHAR(60);
ALTER TABLE entry_events ADD CONSTRAINT entry_events_symptom_format_check CHECK (symptom ~ '^[A-Z][A-Z0-9_]{1,39}$');
