-- El alias es el codigo legible del paciente (P-014): no puede repetirse, sin distinguir mayusculas (RF-01).
-- Va en una migracion nueva y no en V3 porque V3 ya se aplico en las bases de desarrollo.
CREATE UNIQUE INDEX uq_patients_alias ON patients (lower(alias));
