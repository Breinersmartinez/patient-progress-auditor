package com.patientprogressauditor.agent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportRepository;
import com.patientprogressauditor.report.ReportVersion;
import com.patientprogressauditor.report.ReportVersionRepository;

// Lee los informes semanales APROBADOS de los que sale un mensual (RN-10): la version que enfermeria aprobo, no la del modelo.
// Es solo lectura. Lo usan la herramienta del agente y quien arma el contenido mensual.
@Component
public class ApprovedWeeklyReports {

    public record WeeklyClaim(String claimKey, String text, String criterion, List<UUID> entryIds) {
    }

    public record Weekly(int weekNo, LocalDate weekStart, UUID reportId, String summary, List<WeeklyClaim> claims) {
    }

    private final ReportRepository reports;
    private final ReportVersionRepository versions;

    public ApprovedWeeklyReports(ReportRepository reports, ReportVersionRepository versions) {
        this.reports = reports;
        this.versions = versions;
    }

    // `reportIds` en orden de semana (de la primera a la cuarta).
    public List<Weekly> load(List<UUID> reportIds) {
        List<Weekly> out = new ArrayList<>();
        int weekNo = 1;
        for (UUID id : reportIds) {
            Report report = reports.findById(id).orElseThrow(() -> new IllegalStateException("No existe el informe semanal " + id));
            if (report.getStatus() != Report.Status.APROBADO || report.getApprovedVersionNo() == null) {
                throw new IllegalStateException("El informe semanal " + id + " no está aprobado");
            }
            ReportVersion version = versions.findByReportIdOrderByVersionNoAsc(id).stream()
                    .filter(v -> v.getVersionNo() == report.getApprovedVersionNo()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("El informe semanal " + id + " no tiene su versión aprobada"));
            JsonNode content = version.getContent();
            List<WeeklyClaim> claims = new ArrayList<>();
            if (content != null) {
                for (JsonNode claim : content.path("claims")) {
                    List<UUID> entries = new ArrayList<>();
                    claim.path("entry_ids").forEach(e -> entries.add(UUID.fromString(e.asText())));
                    claims.add(new WeeklyClaim(claim.path("claim_key").asText(), claim.path("text").asText(),
                            claim.path("criterion").asText(), entries));
                }
            }
            out.add(new Weekly(weekNo++, report.getPeriodStart(), id, content == null ? "" : content.path("summary").asText(""), claims));
        }
        return out;
    }
}
