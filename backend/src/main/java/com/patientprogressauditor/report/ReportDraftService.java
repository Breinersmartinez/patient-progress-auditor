package com.patientprogressauditor.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

// Guarda el borrador del agente: el informe en PENDIENTE_REVISION, su version 1 (la del modelo, que no se toca) y el
// indice de citas, todo en una transaccion. Es el UNICO camino de escritura del agente y no puede dejar un informe
// APROBADO: la aprobacion es de una persona de enfermeria (RN-02) y llega por otro camino (E12a).
@Service
public class ReportDraftService {

    private final ReportRepository reports;
    private final ReportVersionRepository versions;
    private final ReportCitationRepository citations;
    private final MonthlyReportWeekRepository monthlyWeeks;

    public ReportDraftService(ReportRepository reports, ReportVersionRepository versions, ReportCitationRepository citations,
            MonthlyReportWeekRepository monthlyWeeks) {
        this.reports = reports;
        this.versions = versions;
        this.citations = citations;
        this.monthlyWeeks = monthlyWeeks;
    }

    // `weekStart` es el primer lunes del periodo (la semana, o la primera de las cuatro del mensual). Un mensual trae los
    // cuatro informes semanales de los que sale (RN-10), en orden de semana.
    public record Origin(Report.Type type, UUID patientId, LocalDate weekStart, UUID requestedBy, UUID runId, String provider,
            String model, List<UUID> weeklyReportIds) {

        public Origin(UUID patientId, LocalDate weekStart, UUID requestedBy, UUID runId, String provider, String model) {
            this(Report.Type.SEMANAL, patientId, weekStart, requestedBy, runId, provider, model, List.of());
        }
    }

    // Un borrador valido: PENDIENTE_REVISION con contenido y citas.
    @Transactional
    public UUID saveDraft(Origin origin, JsonNode content, Map<String, List<UUID>> claimCitations) {
        Report report = newReport(origin, Report.Status.PENDIENTE_REVISION, null);
        reports.saveAndFlush(report);
        linkWeeks(report, origin);
        ReportVersion version = version(report, content, null);
        versions.saveAndFlush(version);
        List<ReportCitation> rows = new ArrayList<>();
        claimCitations.forEach((claim, entries) -> entries.forEach(entry -> rows.add(new ReportCitation(version.getId(), claim, entry))));
        citations.saveAllAndFlush(rows);
        return report.getId();
    }

    // Un limite se agoto o la salida no valido: INCOMPLETO con el motivo (RF-17). Es un estado final: no se aprueba. Lleva
    // el contenido que calculo el codigo (cifras y lo que falta) o, si el modelo escribio algo que no valido, su texto plano
    // (RF-18), siempre marcado como no validado.
    @Transactional
    public UUID saveIncomplete(Origin origin, String reason, JsonNode content, String plainText) {
        Report report = newReport(origin, Report.Status.INCOMPLETO, reason);
        reports.saveAndFlush(report);
        linkWeeks(report, origin);
        versions.saveAndFlush(version(report, content, plainText));
        return report.getId();
    }

    // De que informes semanales sale un mensual (monthly_report_weeks, RN-10).
    private void linkWeeks(Report report, Origin origin) {
        if (origin.type() != Report.Type.MENSUAL) {
            return;
        }
        List<MonthlyReportWeek> rows = new ArrayList<>();
        for (int i = 0; i < origin.weeklyReportIds().size(); i++) {
            rows.add(new MonthlyReportWeek(report.getId(), (short) (i + 1), origin.weeklyReportIds().get(i)));
        }
        monthlyWeeks.saveAllAndFlush(rows);
    }

    private Report newReport(Origin origin, Report.Status status, String reason) {
        Report report = new Report();
        report.setPatientId(origin.patientId());
        report.setReportType(origin.type());
        report.setPeriodStart(origin.weekStart());
        report.setPeriodEnd(origin.weekStart().plusDays(origin.type() == Report.Type.MENSUAL ? 27 : 6));
        report.setStatus(status);
        report.setStatusReason(reason);
        report.setRequestedBy(origin.requestedBy());
        report.setRunId(origin.runId());
        report.setProvider(origin.provider());
        report.setModel(origin.model());
        return report;
    }

    private ReportVersion version(Report report, JsonNode content, String plainText) {
        ReportVersion version = new ReportVersion();
        version.setReportId(report.getId());
        version.setVersionNo(1);
        version.setOrigin(ReportVersion.Origin.MODELO);
        version.setContent(content);
        version.setPlainText(plainText);
        return version;
    }
}
