package com.patientprogressauditor.structuring;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;
import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.InvalidOutputException;
import com.patientprogressauditor.llm.LlmGateway;
import com.patientprogressauditor.llm.LlmProperties;
import com.patientprogressauditor.llm.LlmPurpose;
import com.patientprogressauditor.llm.LlmRequest;
import com.patientprogressauditor.llm.LlmUnavailableException;
import com.patientprogressauditor.llm.ResponseSchema;
import com.patientprogressauditor.llm.StructuredResult;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.NotFoundException;

// Propone los eventos de una entrada (D27). Es UNA llamada corta, sin herramientas, en la misma peticion: la persona
// espera la pantalla «Así quedaría el registro». El agente de informes, en cambio, corre en segundo plano (D24).
// Proponer NO guarda la entrada ni ningun evento: eso lo hace EntryService.create cuando la persona confirma.
@Service
public class StructuringService {

    private static final Logger log = LoggerFactory.getLogger(StructuringService.class);

    static final String WHAT_NEXT = " Puede guardar solo el texto o agregar los síntomas a mano.";

    private final LlmGateway gateway;
    private final LlmProperties properties;
    private final PatientRepository patients;
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final Clock clock;
    private final ResponseSchema schema;

    public StructuringService(LlmGateway gateway, LlmProperties properties, PatientRepository patients,
            AgentRunRepository runs, AgentStepRepository steps, Clock clock, ObjectMapper mapper) {
        this.gateway = gateway;
        this.properties = properties;
        this.patients = patients;
        this.runs = runs;
        this.steps = steps;
        this.clock = clock;
        this.schema = StructuringPrompt.schema(mapper);
    }

    public Proposal propose(UUID patientId, String text, DemoPrincipal principal) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos ese paciente.");
        }
        Instant startedAt = clock.instant();
        LlmRequest request = new LlmRequest(LlmPurpose.STRUCTURING, StructuringPrompt.messages(text), List.of(), schema,
                null, properties.getStructuringBudget());
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.ESTRUCTURACION);
        run.setPatientId(patientId);
        run.setRequestedBy(principal.id());
        run.setStartedAt(startedAt);
        List<StepDraft> trace = new ArrayList<>();

        try {
            StructuredResult result = gateway.structured(request, StructuringPrompt::problems);
            StructuringPrompt.Screened screened = StructuringPrompt.screen(result.json(), text);
            List<EventProposal> events = screened.kept();
            JsonNode correctedNode = result.json().path("correctedText");
            StructuringPrompt.Corrected corrected = StructuringPrompt.acceptCorrection(text,
                    correctedNode.isTextual() ? correctedNode.asText() : null);
            addAttempts(trace, result.attempts());
            // Si el modelo no devolvio texto no hay nada que explicar; si devolvio uno que cambia el contenido, la traza lo dice.
            if (corrected.dropped() != null && correctedNode.isTextual() && !correctedNode.asText().isBlank()) {
                trace.add(new StepDraft(AgentStep.Type.VALIDACION, "Se descartó el texto corregido: " + corrected.dropped(),
                        false, null, 0, 0));
            }
            if (!screened.dropped().isEmpty()) {
                trace.add(new StepDraft(AgentStep.Type.VALIDACION, "Se descartaron síntomas propuestos sin respaldo en la nota: "
                        + String.join("; ", screened.dropped()), false, null, 0, 0));
            }
            if (result.corrected()) {
                trace.add(new StepDraft(AgentStep.Type.VALIDACION, "La primera salida no validó (" + String.join("; ", result.firstProblems())
                        + "); se reintentó una vez con el error", false, null, 0, 0));
            }
            trace.add(new StepDraft(AgentStep.Type.VALIDACION, "La salida validó contra el esquema: " + events.size() + " síntomas propuestos",
                    false, null, 0, 0));
            finish(run, AgentRun.Status.COMPLETADA, AgentRun.StopReason.EXITO, result.result().provider(), result.result().model(),
                    result.result().fallbackUsed(), result.attempts(), result.costUsd(), null);
            save(run, trace);
            return new Proposal(events, corrected.text(), null, run.getProvider(), run.getModel(), run.isFallbackUsed(), run.getId());
        } catch (LlmUnavailableException e) {
            addAttempts(trace, e.attempts());
            trace.add(new StepDraft(AgentStep.Type.ERROR, e.getMessage(), true, null, 0, 0));
            Attempt last = e.attempts().isEmpty() ? null : e.attempts().get(e.attempts().size() - 1);
            finish(run, AgentRun.Status.FALLIDA, AgentRun.StopReason.PROVEEDOR_NO_DISPONIBLE, last == null ? null : last.provider(),
                    last == null ? null : last.model(), false, e.attempts(), null, e.getMessage());
            save(run, trace);
            return failed(run, e.getMessage());
        } catch (InvalidOutputException e) {
            addAttempts(trace, e.attempts());
            trace.add(new StepDraft(AgentStep.Type.ERROR, "La salida no validó tras el reintento: " + String.join("; ", e.problems()),
                    true, null, 0, 0));
            Attempt last = e.attempts().get(e.attempts().size() - 1);
            finish(run, AgentRun.Status.FALLIDA, AgentRun.StopReason.SALIDA_INVALIDA, last.provider(), last.model(),
                    last.provider() != null && !last.provider().equals(e.attempts().get(0).provider()), e.attempts(), null,
                    e.getMessage());
            save(run, trace);
            return failed(run, e.getMessage());
        }
    }

    private Proposal failed(AgentRun run, String message) {
        return new Proposal(List.of(), null, message + WHAT_NEXT, run.getProvider(), run.getModel(), run.isFallbackUsed(), run.getId());
    }

    private record StepDraft(AgentStep.Type type, String message, boolean error, Integer millis, int tokensIn, int tokensOut) {
    }

    // Un paso por intento; si el proveedor cambia entre intentos, un paso FALLBACK antes del primero del nuevo.
    private static void addAttempts(List<StepDraft> trace, List<Attempt> attempts) {
        String previous = null;
        for (Attempt a : attempts) {
            if (previous != null && !previous.equals(a.provider())) {
                trace.add(new StepDraft(AgentStep.Type.FALLBACK, "Se pasó del proveedor " + previous + " al de respaldo " + a.provider(),
                        false, null, 0, 0));
            }
            previous = a.provider();
            String message = a.provider() + " " + a.model() + ", intento " + a.number()
                    + (a.succeeded() ? "" : " falló: " + a.failure());
            trace.add(new StepDraft(AgentStep.Type.LLAMADA_LLM, message, !a.succeeded(), (int) a.millis(), a.tokensIn(), a.tokensOut()));
        }
    }

    private void finish(AgentRun run, AgentRun.Status status, AgentRun.StopReason reason, String provider, String model,
            boolean fallbackUsed, List<Attempt> attempts, BigDecimal cost, String error) {
        run.setStatus(status);
        run.setStopReason(reason);
        run.setProvider(provider);
        run.setModel(model);
        run.setFallbackUsed(fallbackUsed);
        run.setIterations((int) attempts.stream().filter(Attempt::succeeded).count());
        run.setTokensIn(attempts.stream().mapToInt(Attempt::tokensIn).sum());
        run.setTokensOut(attempts.stream().mapToInt(Attempt::tokensOut).sum());
        run.setCostUsd(cost);
        run.setErrorMessage(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        run.setFinishedAt(clock.instant());
    }

    // La traza es parte del requisito, pero un fallo al guardarla no debe tumbar la entrada de la persona.
    private void save(AgentRun run, List<StepDraft> trace) {
        try {
            runs.save(run);
            List<AgentStep> rows = new ArrayList<>();
            int seq = 1;
            for (StepDraft draft : trace) {
                AgentStep step = new AgentStep();
                step.setRunId(run.getId());
                step.setSeq(seq++);
                step.setStepType(draft.type());
                step.setMessage(draft.message().substring(0, Math.min(draft.message().length(), 300)));
                step.setError(draft.error());
                step.setDurationMs(draft.millis());
                step.setTokensIn(draft.tokensIn() == 0 ? null : draft.tokensIn());
                step.setTokensOut(draft.tokensOut() == 0 ? null : draft.tokensOut());
                rows.add(step);
            }
            steps.saveAll(rows);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la traza de la estructuración {}", run.getId(), e);
        }
    }
}
