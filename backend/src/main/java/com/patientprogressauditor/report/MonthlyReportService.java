package com.patientprogressauditor.report;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.report.MonthlyEligibility.WeekState;
import com.patientprogressauditor.web.NotFoundException;

// Las ventanas de cuatro semanas que se pueden pedir para un paciente, cada una con el estado de sus cuatro informes
// semanales (RN-10): la pantalla las muestra y dice cuales faltan. La regla la decide MonthlyEligibility, no esta lista.
@Service
public class MonthlyReportService {

    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);

    public record MonthOption(LocalDate start, LocalDate end, List<WeekState> weeks, UUID reportId, Report.Status reportStatus,
            UUID runId, boolean canGenerate, String reason) {
    }

    private final MonthlyEligibility eligibility;
    private final PatientRepository patients;
    private final EntryRepository entries;
    private final AgentRunRepository runs;
    private final Clock clock;

    public MonthlyReportService(MonthlyEligibility eligibility, PatientRepository patients, EntryRepository entries,
            AgentRunRepository runs, Clock clock) {
        this.eligibility = eligibility;
        this.patients = patients;
        this.entries = entries;
        this.runs = runs;
        this.clock = clock;
    }

    // Desde la primera semana con registros hasta donde caben cuatro semanas completas; la mas reciente primero.
    public List<MonthOption> months(UUID patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos al paciente.");
        }
        LocalDate today = LocalDate.now(clock);
        TreeSet<LocalDate> weeks = new TreeSet<>();
        for (Entry e : entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patientId, EPOCH, today)) {
            LocalDate monday = e.getEntryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (monday.plusDays(6).isBefore(today)) {
                weeks.add(monday);
            }
        }
        List<MonthOption> out = new ArrayList<>();
        if (weeks.isEmpty()) {
            return out;
        }
        LocalDate lastMonday = weeks.last();
        for (LocalDate start = weeks.first(); !start.plusWeeks(MonthlyEligibility.WEEKS - 1).isAfter(lastMonday); start = start.plusWeeks(1)) {
            MonthlyEligibility.Window window = eligibility.evaluate(patientId, start);
            Optional<AgentRun> live = runs.findFirstByPatientIdAndPeriodStartAndRunTypeAndStatusIn(patientId, start,
                    AgentRun.Type.MENSUAL, List.of(AgentRun.Status.EN_COLA, AgentRun.Status.EN_CURSO));
            String reason = window.problem() != null ? window.problem() : live.isPresent() ? "Ya se está generando." : null;
            out.add(new MonthOption(window.start(), window.end(), window.weeks(), window.currentMonthlyId(), window.currentMonthlyStatus(),
                    live.map(AgentRun::getId).orElse(null), window.eligible() && live.isEmpty(), reason));
        }
        out.sort((a, b) -> b.start().compareTo(a.start()));
        return out;
    }
}
