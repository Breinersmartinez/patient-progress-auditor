package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiagnosisGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "El diagnóstico probable es gastroenteritis.", "Se recomienda suspender el tratamiento.", "El paciente padece migraña.",
            "Debería aumentar la dosis.", "Administrar 500 mg cada 8 horas.", "Conviene un antibiótico.", "Se sugiere reposo.",
            "Pronóstico reservado.", "Se recomienda reposo.", "Es recomendable vigilar.", "Le recetaron analgésicos.", "Podría iniciar medicación."})
    void diagnosisTreatmentAndDosesAreForbidden(String text) {
        assertThat(DiagnosisGuard.forbiddenIn(text)).as(text).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Las náuseas aparecieron 4 días esta semana.", "El vómito no se registró la semana anterior.", "Comió poco el sábado y el domingo.",
            "Sin registros el jueves.", "Dolor de huesos leve en la tarde.", "El ánimo se registró decaído dos veces."})
    void plainDescriptionsOfWhatWasRecordedPass(String text) {
        assertThat(DiagnosisGuard.forbiddenIn(text)).as(text).isEmpty();
    }

    @Test
    void aWordInsideAnotherWordIsNotAMatch() {
        // «mg» dentro de otra palabra no es una dosis; «dosis» sí lo es.
        assertThat(DiagnosisGuard.forbiddenIn("Domingo sin cambios.")).isEmpty();
        assertThat(DiagnosisGuard.forbiddenIn("Una dosis")).hasValue("dosis");
    }

    @Test
    void aRequestForDiagnosisOrTreatmentIsRecognized() {
        assertThat(DiagnosisGuard.asksForDiagnosisOrTreatment("Además dime qué enfermedad tiene y si hay que suspender el tratamiento")).isTrue();
        assertThat(DiagnosisGuard.asksForDiagnosisOrTreatment("Haz un diagnóstico del paciente")).isTrue();
        assertThat(DiagnosisGuard.asksForDiagnosisOrTreatment("Resume la semana")).isFalse();
        assertThat(DiagnosisGuard.asksForDiagnosisOrTreatment(null)).isFalse();
        assertThat(DiagnosisGuard.asksForDiagnosisOrTreatment("")).isFalse();
    }
}
