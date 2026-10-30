package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

// El prompt del mensual no debe ensenar lo que el validador rechaza (E13b): un ordinal de semana en digitos es un numero sin respaldo.
class AgentPromptTest {

    private static final Pattern WEEK_IN_DIGITS = Pattern.compile("(?i)semana\\s+\\d");

    @Test
    void theMonthlyPromptNamesTheWeeksInWordsAndNeverInDigits() {
        String prompt = AgentPrompt.systemMonthly(5);

        assertThat(prompt).contains("la primera semana", "la cuarta");
        assertThat(WEEK_IN_DIGITS.matcher(prompt).find()).isFalse();
    }

    @Test
    void theMonthlyPromptTellsToKeepDigitsOutOfTheSummaryAndToWriteWithoutAnumberWhenThereIsNoFigure() {
        String prompt = AgentPrompt.systemMonthly(5);

        assertThat(prompt).contains("El summary también", "escribe la frase sin número", "SIEMPRE con @");
    }

    @Test
    void theMonthlyTaskGivesTheMondayOfEachWeekForTheFigureKeys() {
        String task = AgentPrompt.taskMonthly(LocalDate.of(2026, 9, 21), null);

        assertThat(task).contains("2026-09-21", "2026-09-28", "2026-10-05", "2026-10-12");
    }
}
