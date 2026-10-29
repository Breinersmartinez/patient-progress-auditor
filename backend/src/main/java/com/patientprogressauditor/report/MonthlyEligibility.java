package com.patientprogressauditor.report;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

// La regla del informe mensual (RN-10, D4), decidida por el CODIGO y no por el modelo: un mes son cuatro semanas completas
// consecutivas (lunes a domingo) y solo se genera si cada una tiene su informe semanal APROBADO. Si falta alguna, dice cual y
// en que estado esta.
@Component
public class MonthlyEligibility {

    public static final int WEEKS = 4;

    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "CO"));

    // Una de las cuatro semanas: el informe semanal que la cubre (el aprobado si hay; si no, el vigente o el ultimo).
    public record WeekState(int weekNo, LocalDate weekStart, LocalDate weekEnd, UUID reportId, Report.Status status, boolean approved) {
    }

    // `problem` es null si se puede generar. `currentMonthly`: el mensual vigente de esta ventana, si ya hay uno.
    public record Window(LocalDate start, LocalDate end, List<WeekState> weeks, UUID currentMonthlyId,
            Report.Status currentMonthlyStatus, String problem) {

        public boolean eligible() {
            return problem == null;
        }

        // Los informes semanales de los que sale el mensual, en orden de semana.
        public List<UUID> weeklyReportIds() {
            return weeks.stream().map(WeekState::reportId).toList();
        }
    }

    private final ReportRepository reports;
    private final Clock clock;

    public MonthlyEligibility(ReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    public Window evaluate(UUID patientId, LocalDate start) {
        List<Report> all = reports.findByPatientIdOrderByPeriodStartDesc(patientId);
        List<WeekState> weeks = new ArrayList<>();
        for (int i = 0; i < WEEKS; i++) {
            LocalDate week = start.plusWeeks(i);
            weeks.add(weekState(all, i + 1, week));
        }
        LocalDate end = start.plusDays(7L * WEEKS - 1);
        Report current = all.stream().filter(r -> r.getReportType() == Report.Type.MENSUAL && r.getPeriodStart().equals(start))
                .filter(r -> r.getStatus() == Report.Status.PENDIENTE_REVISION || r.getStatus() == Report.Status.APROBADO)
                .findFirst().orElse(null);
        return new Window(start, end, weeks, current == null ? null : current.getId(), current == null ? null : current.getStatus(),
                problem(start, end, weeks, current));
    }

    private String problem(LocalDate start, LocalDate end, List<WeekState> weeks, Report current) {
        if (!end.isBefore(LocalDate.now(clock))) {
            return "Solo se puede informar un mes completo: la semana del " + LONG.format(start.plusWeeks(WEEKS - 1)) + " al "
                    + LONG.format(end) + " todavía no termina.";
        }
        if (current != null) {
            return "Ya hay un informe mensual de ese período.";
        }
        List<String> missing = new ArrayList<>();
        for (WeekState w : weeks) {
            if (!w.approved()) {
                missing.add("la semana del " + LONG.format(w.weekStart()) + " al " + LONG.format(w.weekEnd()) + " (" + why(w) + ")");
            }
        }
        if (missing.isEmpty()) {
            return null;
        }
        return "No se puede generar el informe mensual: falta el informe semanal aprobado de " + String.join("; ", missing)
                + ". Apruebe esos informes y vuelva a pedirlo.";
    }

    private static String why(WeekState w) {
        if (w.status() == null) {
            return "no tiene informe semanal";
        }
        return switch (w.status()) {
            case PENDIENTE_REVISION -> "su informe está pendiente de revisión";
            case RECHAZADO -> "su informe fue rechazado";
            case INCOMPLETO -> "su informe quedó incompleto";
            case APROBADO -> "aprobado";
        };
    }

    // El aprobado si hay; si no, el vigente (pendiente); si no, el mas reciente de los demas (rechazado o incompleto).
    private static WeekState weekState(List<Report> all, int weekNo, LocalDate week) {
        List<Report> weekly = all.stream().filter(r -> r.getReportType() == Report.Type.SEMANAL && r.getPeriodStart().equals(week)).toList();
        Report best = weekly.stream().filter(r -> r.getStatus() == Report.Status.APROBADO).findFirst()
                .or(() -> weekly.stream().filter(r -> r.getStatus() == Report.Status.PENDIENTE_REVISION).findFirst())
                .or(() -> weekly.stream().sorted(Comparator.comparing(Report::getCreatedAt, Comparator.nullsFirst(Comparator.<java.time.Instant>naturalOrder())).reversed()).findFirst()).orElse(null);
        return new WeekState(weekNo, week, week.plusDays(6), best == null ? null : best.getId(), best == null ? null : best.getStatus(),
                best != null && best.getStatus() == Report.Status.APROBADO);
    }
}
