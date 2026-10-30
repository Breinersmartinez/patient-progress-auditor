package com.patientprogressauditor.report;

import static com.patientprogressauditor.report.ReportFixtures.DOCTOR;
import static com.patientprogressauditor.report.ReportFixtures.NURSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.entry.EntryRepository;

// La regla de las cuatro semanas (RN-10, RF-36) y la lista de meses. P-033 (semanas del 28-sep, 5, 12 y 19-oct, sin informes
// sembrados) permite armar cada estado; P-014 trae sus cuatro semanas aprobadas de la semilla.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
class MonthlyEligibilityTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 28);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntryRepository entries;
    @Autowired private MonthlyEligibility eligibility;

    private ReportFixtures fx;
    private UUID p033;

    @BeforeEach
    void setUp() {
        fx = new ReportFixtures(jdbc, entries, mapper);
        p033 = fx.patient("P-033");
    }

    private void approveAll() {
        for (int i = 0; i < 4; i++) {
            fx.report(p033, START.plusWeeks(i), "APROBADO");
        }
    }

    @Test
    void fourApprovedWeeksCanBeGenerated() {
        approveAll();
        MonthlyEligibility.Window window = eligibility.evaluate(p033, START);

        assertThat(window.problem()).isNull();
        assertThat(window.eligible()).isTrue();
        assertThat(window.end()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(window.weeks()).extracting(MonthlyEligibility.WeekState::weekNo).containsExactly(1, 2, 3, 4);
        assertThat(window.weeklyReportIds()).doesNotContainNull().hasSize(4);
    }

    @Test
    void aPendingWeekBlocksAndIsNamed() {
        fx.report(p033, START, "APROBADO");
        fx.report(p033, START.plusWeeks(1), "APROBADO");
        fx.report(p033, START.plusWeeks(2), "PENDIENTE_REVISION");
        fx.report(p033, START.plusWeeks(3), "APROBADO");

        String problem = eligibility.evaluate(p033, START).problem();

        assertThat(problem).contains("12 de octubre al 18 de octubre").contains("pendiente de revisión");
        assertThat(problem).doesNotContain("28 de septiembre").doesNotContain("5 de octubre");
    }

    @Test
    void everyMissingWeekIsListedWithItsOwnState() {
        fx.report(p033, START, "RECHAZADO");
        // la semana 2 no tiene informe
        fx.report(p033, START.plusWeeks(2), "INCOMPLETO");
        fx.report(p033, START.plusWeeks(3), "APROBADO");

        String problem = eligibility.evaluate(p033, START).problem();

        assertThat(problem).contains("28 de septiembre al 4 de octubre (su informe fue rechazado)")
                .contains("5 de octubre al 11 de octubre (no tiene informe semanal)")
                .contains("12 de octubre al 18 de octubre (su informe quedó incompleto)")
                .doesNotContain("19 de octubre al 25 de octubre (");
    }

    @Test
    void anApprovedReportBeatsARejectedOneOfTheSameWeek() {
        fx.report(p033, START, "RECHAZADO");
        fx.report(p033, START, "APROBADO");
        for (int i = 1; i < 4; i++) {
            fx.report(p033, START.plusWeeks(i), "APROBADO");
        }

        assertThat(eligibility.evaluate(p033, START).eligible()).isTrue();
    }

    @Test
    void aWindowThatHasNotEndedCannotBeGenerated() {
        // la ultima semana de esta ventana termina el 22-nov-2026, despues de hoy
        String problem = eligibility.evaluate(p033, LocalDate.of(2026, 10, 26)).problem();

        assertThat(problem).contains("todavía no termina");
    }

    @Test
    void aCurrentMonthlyReportBlocksARepeat() {
        approveAll();
        UUID monthly = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, requested_by)
                VALUES (?, ?, 'MENSUAL', ?, ?, 'PENDIENTE_REVISION', ?)""", monthly, p033, START, START.plusDays(27), UUID.fromString(NURSE));

        MonthlyEligibility.Window window = eligibility.evaluate(p033, START);

        assertThat(window.problem()).isEqualTo("Ya hay un informe mensual de ese período.");
        assertThat(window.currentMonthlyId()).isEqualTo(monthly);
    }

    @Test
    void monthsListsTheWindowsNewestFirstWithTheStateOfEachWeek() throws Exception {
        UUID p014 = fx.patient("P-014");
        mockMvc.perform(get("/api/patients/" + p014 + "/report-months").header("X-Demo-User", NURSE))
                .andExpect(status().isOk())
                // P-014 tiene cinco semanas (21-sep a 25-oct): dos ventanas
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].start").value("2026-09-28"))
                .andExpect(jsonPath("$[0].canGenerate").value(false))
                .andExpect(jsonPath("$[0].reason").value(org.hamcrest.Matchers.containsString("19 de octubre al 25 de octubre (no tiene informe semanal)")))
                .andExpect(jsonPath("$[1].start").value("2026-09-21"))
                .andExpect(jsonPath("$[1].end").value("2026-10-18"))
                .andExpect(jsonPath("$[1].canGenerate").value(true))
                .andExpect(jsonPath("$[1].weeks.length()").value(4))
                .andExpect(jsonPath("$[1].weeks[0].approved").value(true))
                .andExpect(jsonPath("$[1].weeks[3].approved").value(true));
    }

    @Test
    void aPatientWithoutFourWeeksHasNoWindows() throws Exception {
        mockMvc.perform(get("/api/patients/" + fx.patient("P-040") + "/report-months").header("X-Demo-User", NURSE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyNursingCanListMonthsAndTheyNeedAKnownPatient() throws Exception {
        mockMvc.perform(get("/api/patients/" + p033 + "/report-months").header("X-Demo-User", DOCTOR)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/patients/" + UUID.randomUUID() + "/report-months").header("X-Demo-User", NURSE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/patients/" + p033 + "/report-months")).andExpect(status().isUnauthorized());
    }
}
