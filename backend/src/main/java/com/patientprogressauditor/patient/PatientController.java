package com.patientprogressauditor.patient;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.patientprogressauditor.patient.PatientService.NewPatient;
import com.patientprogressauditor.patient.PatientService.PatientView;
import com.patientprogressauditor.session.DemoPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    // El alias vacio lo valida el servicio con su mensaje; aqui solo los topes de la base.
    public record CreatePatientRequest(
            @Size(max = 100, message = "El código no puede pasar de 100 caracteres.") String alias,
            @Size(max = 10, message = "La cama no puede pasar de 10 caracteres.") String bed,
            @Min(value = 0, message = "La edad debe estar entre 0 y 120 años.")
            @Max(value = 120, message = "La edad debe estar entre 0 y 120 años.") Integer age,
            LocalDate admittedOn) {
    }

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PATIENTS_READ')")
    public List<PatientView> list(@AuthenticationPrincipal DemoPrincipal principal) {
        return service.list(principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENTS_READ')")
    public PatientView get(@PathVariable UUID id, @AuthenticationPrincipal DemoPrincipal principal) {
        return service.get(id, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PATIENTS_CREATE')")
    public PatientView create(@Valid @RequestBody CreatePatientRequest request,
            @AuthenticationPrincipal DemoPrincipal principal) {
        return service.create(new NewPatient(request.alias(), request.bed(), request.age(), request.admittedOn()), principal);
    }
}
