package com.patientprogressauditor.entry;

// Catalogo cerrado de nueve sintomas (D18, propuesta); la base lo protege con un CHECK.
public enum Symptom {
    VOMITO("Vómito"),
    DIARREA("Diarrea"),
    NAUSEAS("Náuseas"),
    FIEBRE("Fiebre"),
    MAREO("Mareo"),
    DOLOR_CABEZA("Dolor de cabeza"),
    DOLOR_HUESO("Dolor de hueso"),
    APETITO_BAJO("Apetito bajo"),
    ANIMO_DECAIDO("Ánimo decaído");

    private final String label;

    Symptom(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
