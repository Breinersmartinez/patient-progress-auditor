package com.patientprogressauditor.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class SymptomTest {

    @Test
    void theNineCatalogSymptomsKeepTheirCodesAndLabels() {
        assertThat(Symptom.values()).hasSize(9);
        assertThat(Symptom.values()).extracting(Symptom::code).containsExactly("VOMITO", "DIARREA", "NAUSEAS", "FIEBRE", "MAREO",
                "DOLOR_CABEZA", "DOLOR_HUESO", "APETITO_BAJO", "ANIMO_DECAIDO");
        assertThat(Symptom.NAUSEAS.label()).isEqualTo("Náuseas");
    }

    @Test
    void aTypedNameBecomesAStableCodeWithoutAccentsOrSpaces() {
        Symptom symptom = Symptom.fromTyped("  picazón   en los brazos ").orElseThrow();

        assertThat(symptom.code()).isEqualTo("PICAZON_EN_LOS_BRAZOS");
        assertThat(symptom.label()).isEqualTo("Picazón en los brazos");
        assertThat(symptom.inCatalog()).isFalse();
    }

    @Test
    void aTypedNameThatIsACatalogSymptomIsThatSymptom() {
        assertThat(Symptom.fromTyped("dolor de cabeza")).contains(Symptom.DOLOR_CABEZA);
        assertThat(Symptom.fromTyped("NAUSEAS")).contains(Symptom.NAUSEAS);
        assertThat(Symptom.fromTyped("Animo decaido")).contains(Symptom.ANIMO_DECAIDO);
        assertThat(Symptom.fromTyped("vómito").orElseThrow().inCatalog()).isTrue();
    }

    @Test
    void whatDoesNotLookLikeASymptomIsRefused() {
        assertThat(Symptom.fromTyped(null)).isEmpty();
        assertThat(Symptom.fromTyped("   ")).isEmpty();
        assertThat(Symptom.fromTyped("1")).isEmpty();
        assertThat(Symptom.fromTyped("12345")).isEmpty();
        assertThat(Symptom.fromTyped("<script>alert(1)</script>")).isEmpty();
        assertThat(Symptom.fromTyped("a".repeat(61))).isEmpty();
        assertThat(Symptom.fromTyped("x")).isEmpty();
    }

    @Test
    void twoSymptomsWithTheSameCodeAreTheSameWhateverTheLabel() {
        assertThat(Symptom.of("DOLOR_DE_GARGANTA", "Dolor de garganta")).isEqualTo(Symptom.of("DOLOR_DE_GARGANTA", "dolor de garganta"));
        assertThat(Symptom.of("FIEBRE", "otra cosa")).isSameAs(Symptom.FIEBRE);
        assertThat(Symptom.of("TOS_SECA", null).label()).isEqualTo("Tos seca");
        assertThatThrownBy(() -> Symptom.of("tos seca", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalogSymptomsComeFirstInCatalogOrderAndThenTheOthersByCode() {
        TreeSet<Symptom> sorted = new TreeSet<>();
        sorted.add(Symptom.of("TOS_SECA", null));
        sorted.add(Symptom.MAREO);
        sorted.add(Symptom.of("DOLOR_DE_GARGANTA", null));
        sorted.add(Symptom.VOMITO);

        assertThat(sorted).extracting(Symptom::code).containsExactly("VOMITO", "MAREO", "DOLOR_DE_GARGANTA", "TOS_SECA");
    }
}
