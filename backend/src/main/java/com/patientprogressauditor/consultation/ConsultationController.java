package com.patientprogressauditor.consultation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.consultation.ConsultationService.ConsultationDetail;
import com.patientprogressauditor.consultation.ConsultationService.WeekOption;
import com.patientprogressauditor.consultation.pdf.ConsultationPdfService;
import com.patientprogressauditor.run.RunService.RunView;
import com.patientprogressauditor.session.DemoPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// La consulta del medico, «Armar informe» (D31, RF-46): pedirla, leerla y su PDF. Seguir su progreso (segundo plano, D24)
// es /api/runs/{id}[/steps|/events], igual que un informe (RunController, abierto al medico para sus propias consultas).
// Pedirla (POST) es solo de MEDICO (CONSULTATIONS_REQUEST): hoy solo enfermeria pide informes, y esta es la unica
// ejecucion que pide el medico. Leerla (GET) no lleva permiso de clase a proposito: la autoridad "puede pedir consultas"
// no es la misma pregunta que "puede ver ESTA consulta", y poner el permiso aqui le daria 403 a enfermeria en vez del
// 404 que el criterio de aceptacion pide ("a cualquier otro, 404"); ConsultationService.findVisible ya resuelve ese 404
// por dueno (ninguna enfermera es nunca la doctorId de una fila), sin depender de que el principal tenga o no el permiso.
@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    public record RequestConsultation(@NotNull(message = "Elija el paciente.") UUID patientId,
            @NotNull(message = "Elija el inicio del período.") LocalDate periodStart,
            @NotNull(message = "Elija el fin del período.") LocalDate periodEnd,
            // Las dos con valor, o ninguna (sin comparar).
            LocalDate comparisonStart, LocalDate comparisonEnd) {
    }

    private final ConsultationService service;
    private final ConsultationPdfService pdf;

    public ConsultationController(ConsultationService service, ConsultationPdfService pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    // 202 al instante con el id de la ejecucion (AgentRun.Type.CONSULTA); el contenido se lee despues con GET /{id}.
    @PostMapping
    @PreAuthorize("hasAuthority('CONSULTATIONS_REQUEST')")
    public ResponseEntity<RunView> request(@Valid @RequestBody RequestConsultation request, @AuthenticationPrincipal DemoPrincipal principal) {
        RunView created = service.request(request.patientId(), request.periodStart(), request.periodEnd(),
                request.comparisonStart(), request.comparisonEnd(), principal);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(created);
    }

    // El selector "Semana" del formulario (como el mockup): semanas completas con registros. Mismo permiso que pedir,
    // porque es un dato para armar el formulario de pedir, no la consulta en si (esa la filtra findVisible).
    @GetMapping("/weeks/{patientId}")
    @PreAuthorize("hasAuthority('CONSULTATIONS_REQUEST')")
    public List<WeekOption> weeks(@PathVariable UUID patientId) {
        return service.weeks(patientId);
    }

    // Solo quien la pidio (criterio 3 de la Pieza 6): a cualquier otro, 404 (ConsultationService.findVisible).
    @GetMapping("/{id}")
    public ConsultationDetail get(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.detail(id, principal);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        ConsultationPdfService.Pdf file = pdf.render(id, principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF).body(file.bytes());
    }
}
