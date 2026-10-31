package com.patientprogressauditor.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.entry.EntryService.EntryView;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.structuring.Proposal;
import com.patientprogressauditor.structuring.StructuringService;
import com.patientprogressauditor.web.FieldException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api")
public class EntryController {

    // `body` no se recorta ni se normaliza: se valida que no este en blanco y se guarda tal cual.
    // `events` ausente: «guardar solo el texto». Presente (aunque vacio): lo que la persona confirmo.
    public record CreateEntryRequest(
            @NotNull(message = "Elija la fecha del registro.") LocalDate entryDate,
            @NotBlank(message = "El registro no puede quedar vacío.")
            @Size(max = EntryService.MAX_BODY_LENGTH, message = "El registro no puede pasar de 4000 caracteres.") String body,
            @Valid List<EntryService.ConfirmedEvent> events,
            @Valid CorrectionRequest correction) {
    }

    // El texto corregido de la vista previa (RF-42), tal como la persona lo dejo. Sin `correction`, o igual al original, no se guarda version.
    public record CorrectionRequest(
            @Size(max = EntryService.MAX_BODY_LENGTH, message = "El texto corregido no puede pasar de 4000 caracteres.") String body,
            String origin) {
    }

    public record ProposeRequest(
            @NotBlank(message = "El registro no puede quedar vacío.")
            @Size(max = EntryService.MAX_BODY_LENGTH, message = "El registro no puede pasar de 4000 caracteres.") String body) {
    }

    private final EntryService service;
    private final StructuringService structuring;

    public EntryController(EntryService service, StructuringService structuring) {
        this.service = service;
        this.structuring = structuring;
    }

    // Propone los sintomas con IA. NO guarda nada: la entrada y los eventos se guardan al confirmar (POST de abajo, RN-11).
    // Si la IA falla responde 200 con `failure` y sin eventos, para que la persona guarde solo el texto o agregue a mano.
    @PostMapping("/patients/{patientId}/entries/propose")
    @PreAuthorize("hasAuthority('ENTRIES_CREATE')")
    public Proposal propose(@PathVariable UUID patientId, @Valid @RequestBody ProposeRequest request,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return structuring.propose(patientId, request.body(), principal);
    }

    @PostMapping("/patients/{patientId}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ENTRIES_CREATE')")
    public EntryView create(@PathVariable UUID patientId, @Valid @RequestBody CreateEntryRequest request,
            @AuthenticationPrincipal DemoPrincipal principal) {
        EntryService.Correction correction = request.correction() == null ? null
                : new EntryService.Correction(request.correction().body(), request.correction().origin());
        return service.create(patientId, request.entryDate(), request.body(), request.events(), correction, principal);
    }

    // order=asc (cronologico, por defecto) o desc (mas reciente primero); from y to incluyen sus dos extremos.
    @GetMapping("/patients/{patientId}/entries")
    @PreAuthorize("hasAuthority('ENTRIES_READ')")
    public List<EntryView> listForPatient(@PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(defaultValue = "" + EntryService.MAX_LIMIT) int limit) {
        if (!order.equals("asc") && !order.equals("desc")) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "order", "El orden debe ser asc o desc.");
        }
        return service.listForPatient(patientId, from, to, order.equals("desc"), checkedLimit(limit));
    }

    @GetMapping("/entries")
    @PreAuthorize("hasAuthority('ENTRIES_READ')")
    public List<EntryView> history(@RequestParam(defaultValue = "" + EntryService.MAX_LIMIT) int limit) {
        return service.history(checkedLimit(limit));
    }

    private static int checkedLimit(int limit) {
        if (limit < 1 || limit > EntryService.MAX_LIMIT) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "limit", "El límite debe estar entre 1 y " + EntryService.MAX_LIMIT + ".");
        }
        return limit;
    }
}
