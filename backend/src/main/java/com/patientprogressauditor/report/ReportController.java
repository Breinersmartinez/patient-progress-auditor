package com.patientprogressauditor.report;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.report.ReportEditValidator.ClaimEdit;
import com.patientprogressauditor.report.pdf.ReportPdfService;
import com.patientprogressauditor.report.ReportEditValidator.Edit;
import com.patientprogressauditor.report.ReportService.Detail;
import com.patientprogressauditor.report.ReportService.Summary;
import com.patientprogressauditor.report.ReportService.VersionView;
import com.patientprogressauditor.report.ReportService.WeekOption;
import com.patientprogressauditor.run.RunService.StepView;
import com.patientprogressauditor.session.DemoPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Informes: ver, corregir, aprobar y rechazar. Pedirlos es POST /api/runs (RunController). Los permisos aqui son la
// primera barrera y la regla de estado (ReportVisibility, ReportTransitions) la segunda: ninguna depende de la pantalla.
@RestController
@RequestMapping("/api")
public class ReportController {

    public record RejectRequest(
            @NotBlank(message = "Escriba el motivo del rechazo.")
            @Size(max = 500, message = "El motivo no puede pasar de 500 caracteres.") String reason) {
    }

    private final ReportService service;
    private final MonthlyReportService monthly;
    private final ReportPdfService pdf;

    public ReportController(ReportService service, MonthlyReportService monthly, ReportPdfService pdf) {
        this.service = service;
        this.monthly = monthly;
        this.pdf = pdf;
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    public List<Summary> list(@RequestParam(required = false) Report.Status status, @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Report.Type type, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.list(principal, status, patientId, type);
    }

    @GetMapping("/reports/{id}")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    public Detail get(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.detail(id, principal);
    }

    @GetMapping("/reports/{id}/versions/{versionNo}")
    @PreAuthorize("hasAuthority('REPORTS_UPDATE')")
    public VersionView version(@PathVariable UUID id, @PathVariable int versionNo) {
        return service.version(id, versionNo);
    }

    @GetMapping("/reports/{id}/trace")
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    public List<StepView> trace(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.trace(id, principal);
    }

    // Crea una version nueva de enfermeria; la del modelo no se toca (RF-25).
    @PutMapping("/reports/{id}/content")
    @PreAuthorize("hasAuthority('REPORTS_UPDATE')")
    public Detail edit(@PathVariable UUID id, @Valid @RequestBody EditRequest request, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.edit(id, new Edit(request.summary(), request.claims()), principal);
    }

    public record EditRequest(String summary, List<ClaimEdit> claims) {
    }

    @PostMapping("/reports/{id}/approve")
    @PreAuthorize("hasAuthority('REPORTS_APPROVE')")
    public Detail approve(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.approve(id, principal);
    }

    @PostMapping("/reports/{id}/reject")
    @PreAuthorize("hasAuthority('REPORTS_APPROVE')")
    public Detail reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest request, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.reject(id, request.reason(), principal);
    }

    // Semanas que se pueden pedir: completas y con registros (tabla 3.3 del prompt).
    @GetMapping("/patients/{patientId}/report-weeks")
    @PreAuthorize("hasAuthority('REPORTS_REQUEST')")
    public List<WeekOption> weeks(@PathVariable UUID patientId) {
        return service.weeks(patientId);
    }

    // El PDF de un informe aprobado (RF-37). Lo piden enfermeria y medico con los mismos permisos que ver el informe; lo que
    // el rol no puede ver responde 404 y lo que no esta aprobado, 409.
    @GetMapping(value = "/reports/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('REPORTS_READ')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        ReportPdfService.Pdf file = pdf.render(id, principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF).body(file.bytes());
    }

    // Ventanas de cuatro semanas con el estado de sus informes semanales (RN-10): cuales se pueden generar y por que no.
    @GetMapping("/patients/{patientId}/report-months")
    @PreAuthorize("hasAuthority('REPORTS_REQUEST')")
    public List<MonthlyReportService.MonthOption> months(@PathVariable UUID patientId) {
        return monthly.months(patientId);
    }
}
