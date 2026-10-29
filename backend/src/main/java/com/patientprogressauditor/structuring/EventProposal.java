package com.patientprogressauditor.structuring;

import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;

// Un evento que PROPONE el modelo. Todavia no es un evento: lo es cuando una persona lo confirma (RN-11).
public record EventProposal(Symptom symptom, Intensity intensity, Integer occurrences) {
}
