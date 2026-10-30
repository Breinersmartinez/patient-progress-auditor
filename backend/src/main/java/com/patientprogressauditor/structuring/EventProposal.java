package com.patientprogressauditor.structuring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;

// Un evento que PROPONE el modelo. Todavia no es un evento: lo es cuando una persona lo confirma (RN-11).
public record EventProposal(@JsonIgnore Symptom symptom, Intensity intensity, Integer occurrences) {

    // La pantalla recibe el codigo y la etiqueta del sintoma (E18).
    @JsonProperty("symptom")
    public String symptomCode() {
        return symptom.code();
    }

    @JsonProperty("label")
    public String label() {
        return symptom.label();
    }
}
