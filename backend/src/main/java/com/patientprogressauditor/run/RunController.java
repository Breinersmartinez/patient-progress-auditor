package com.patientprogressauditor.run;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.patientprogressauditor.run.RunService.RunView;
import com.patientprogressauditor.run.RunService.StepView;
import com.patientprogressauditor.session.DemoPrincipal;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Ejecuciones del agente (D24): se piden, se consultan y se siguen en vivo. Solo enfermeria (REPORTS_REQUEST); la traza
// muestra el texto de los registros del paciente.
@RestController
@RequestMapping("/api/runs")
@PreAuthorize("hasAuthority('REPORTS_REQUEST')")
public class RunController {

    public record RequestRun(@NotNull(message = "Elija el paciente.") UUID patientId,
            @NotNull(message = "Elija la semana.") LocalDate weekStart,
            // Indicacion libre para el agente. La interfaz no la expone: sirve para probar contra el sistema real que una
            // peticion de diagnostico se declara fuera de alcance (eval/). Se trata como dato, nunca como orden.
            @Size(max = 300, message = "La indicación no puede pasar de 300 caracteres.") String request) {
    }

    private final RunService service;
    private final RunEventStreamer streamer;

    public RunController(RunService service, RunEventStreamer streamer) {
        this.service = service;
        this.streamer = streamer;
    }

    // Responde 202 al instante con el id; 200 si ya habia una ejecucion viva de ese paciente y semana (la misma, sin lanzar otra).
    @PostMapping
    public ResponseEntity<RunView> request(@Valid @RequestBody RequestRun request, @AuthenticationPrincipal DemoPrincipal principal) {
        RunService.Requested requested = service.request(request.patientId(), request.weekStart(), request.request(), principal);
        return ResponseEntity.status(requested.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(requested.run());
    }

    @GetMapping("/{id}")
    public RunView get(@PathVariable UUID id) {
        return service.get(id);
    }

    // La traza completa (cualquier tipo de ejecucion), con parametros y resultado de cada paso. `after`: solo los de id mayor.
    @GetMapping("/{id}/steps")
    public List<StepView> steps(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after) {
        return service.steps(id, after);
    }

    // SSE. Reanuda con la cabecera Last-Event-ID (la manda EventSource al reconectar) o con ?after=.
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(required = false) Long after, HttpServletResponse response) {
        service.get(id);
        long from = after != null ? after : parse(lastEventId);
        // nginx acumula la respuesta salvo que se le diga que no (D24); no-transform evita que otro intermediario la reescriba.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        return streamer.open(id, Math.max(from, 0));
    }

    private static long parse(String lastEventId) {
        try {
            return lastEventId == null ? 0 : Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
