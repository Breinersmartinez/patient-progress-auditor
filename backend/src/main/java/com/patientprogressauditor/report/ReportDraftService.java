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

    public ReportDraftService(ReportRepository reports, ReportVersionRepository versions, ReportCitationRepository citations) {
        this.reports = reports;
        this.versions = versions;
        this.citations = citations;
    }

    public record Origin(UUID patientId, LocalDate weekStart, UUID requestedBy, UUID runId, String provider, String model) {
    }

    // Un borrador valido: PENDIENTE_REVISION con contenido y citas.
    @Transactional
    public UUID saveDraft(Origin origin, JsonNode content, Map<String, List<UUID>> claimCitations) {
        Report report = newReport(origin, Report.Status.PENDIENTE_REVISION, null);
        reports.saveAndFlush(report);
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
        versions.saveAndFlush(version(report, content, plainText));
        return report.getId();
    }

    private Report newReport(Origin origin, Report.Status status, String reason) {
        Report report = new Report();
        report.setPatientId(origin.patientId());
        report.setReportType(Report.Type.SEMANAL);
        report.setPeriodStart(origin.weekStart());
        report.setPeriodEnd(origin.weekStart().plusDays(6));
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
