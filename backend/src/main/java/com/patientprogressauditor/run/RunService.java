package com.patientprogressauditor.run;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentRunner;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportRepository;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.BusyException;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

// Pide un informe sin bloquear la peticion (D24): valida, crea la ejecucion en cola, la entrega al ejecutor y responde con
// su id. El bucle del agente corre fuera de la peticion; el estado vive en la base de datos y no en la conexion.
@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    static final String UNEXPECTED = "El agente falló de forma inesperada. Intente de nuevo en unos minutos.";

    public record RunView(UUID id, AgentRun.Type runType, UUID patientId, LocalDate periodStart, LocalDate periodEnd,
            AgentRun.Status status, AgentRun.StopReason stopReason, UUID reportId, String message, String provider,
            String model, boolean fallbackUsed, int iterations, int tokensIn, int tokensOut, BigDecimal costUsd,
            Instant createdAt, Instant startedAt, Instant finishedAt) {
    }

    public record StepView(long id, int seq, AgentStep.Type type, String message, String tool, JsonNode params,
            JsonNode result, boolean error, Integer durationMs, Integer tokensIn, Integer tokensOut, Instant at) {
    }

    // `created` es falso si ya habia una ejecucion viva del mismo paciente y semana y se devuelve esa.
    public record Requested(RunView run, boolean created) {
    }

    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final ReportRepository reports;
    private final PatientRepository patients;
    private final AgentRunner runner;
    private final RunExecutor executor;
    private final RunTrace trace;
    private final Clock clock;

    public RunService(AgentRunRepository runs, AgentStepRepository steps, ReportRepository reports, PatientRepository patients,
            AgentRunner runner, RunExecutor executor, RunTrace trace, Clock clock) {
        this.runs = runs;
        this.steps = steps;
        this.reports = reports;
        this.patients = patients;
        this.runner = runner;
        this.executor = executor;
        this.trace = trace;
        this.clock = clock;
    }

    public Requested request(UUID patientId, LocalDate weekStart, String note, DemoPrincipal principal) {
        return request(patientId, weekStart, Report.Type.SEMANAL, note, principal);
    }

    // `weekStart` es el lunes de la semana o, en el mensual, de la primera de las cuatro. Cada tipo tiene su regla.
    public Requested request(UUID patientId, LocalDate weekStart, Report.Type type, String note, DemoPrincipal principal) {
        if (type == Report.Type.MENSUAL) {
            return requestMonthly(patientId, weekStart, note, principal);
        }
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "weekStart", "La semana debe empezar un lunes.");
        }
        // Solo semanas completas (tabla 3.3 del prompt): una semana en curso daria un informe con la mitad de los registros.
        if (!weekStart.plusDays(6).isBefore(LocalDate.now(clock))) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "weekStart", "Solo se puede informar una semana completa: esa todavía no termina.");
        }
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos al paciente.");
        }
        Optional<AgentRun> live = live(patientId, weekStart);
        if (live.isPresent()) {
            return new Requested(view(live.get()), false);
        }
        String blocked = runner.blockedReason(patientId, weekStart);
        if (blocked != null) {
            throw new FieldException(HttpStatus.CONFLICT, "weekStart", blocked);
        }
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.INFORME);
        run.setPatientId(patientId);
        run.setRequestedBy(principal.id());
        run.setPeriodStart(weekStart);
        run.setPeriodEnd(weekStart.plusDays(6));
        try {
            // El indice unico parcial de V6 decide si dos peticiones llegan a la vez: la segunda recibe la primera.
            runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException e) {
            return live(patientId, weekStart).map(existing -> new Requested(view(existing), false)).orElseThrow(() -> e);
        }
        // La vista se toma ANTES de lanzar: el hilo del ejecutor cambia esa misma entidad al empezar.
        RunView created = view(run);
        launch(run, new AgentRunner.Task(patientId, weekStart, principal.id(), note));
        return new Requested(created, true);
    }

    // El informe mensual (RF-36, RN-10): cuatro semanas completas, cada una con su informe semanal aprobado. Si falta alguna no se
    // crea la ejecucion y el 409 dice cual (y en que estado esta).
    private Requested requestMonthly(UUID patientId, LocalDate firstWeek, String note, DemoPrincipal principal) {
        if (firstWeek.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "weekStart", "El mes debe empezar un lunes.");
        }
        if (!firstWeek.plusDays(27).isBefore(LocalDate.now(clock))) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "weekStart", "Solo se puede informar un mes completo: las cuatro semanas deben haber terminado.");
        }
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos al paciente.");
        }
        Optional<AgentRun> live = live(patientId, firstWeek, AgentRun.Type.MENSUAL);
        if (live.isPresent()) {
            return new Requested(view(live.get()), false);
        }
        AgentRunner.Task task = new AgentRunner.Task(Report.Type.MENSUAL, patientId, firstWeek, principal.id(), note);
        String blocked = runner.blockedReason(task);
        if (blocked != null) {
            throw new FieldException(HttpStatus.CONFLICT, "weekStart", blocked);
        }
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.MENSUAL);
        run.setPatientId(patientId);
        run.setRequestedBy(principal.id());
        run.setPeriodStart(firstWeek);
        run.setPeriodEnd(firstWeek.plusDays(27));
        try {
            runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException e) {
            return live(patientId, firstWeek, AgentRun.Type.MENSUAL).map(existing -> new Requested(view(existing), false)).orElseThrow(() -> e);
        }
        RunView created = view(run);
        launch(run, task);
        return new Requested(created, true);
    }

    private void launch(AgentRun run, AgentRunner.Task task) {
        try {
            executor.submit(() -> execute(run, task));
        } catch (RejectedExecutionException e) {
            String message = "El sistema está generando otros informes en este momento. Intente de nuevo en un minuto.";
            trace.failIfOpen(run.getId(), message);
            throw new BusyException(message);
        }
    }

    // Corre en el hilo del ejecutor. El bucle ya atrapa sus errores; esto es la red de seguridad para que nada deje una
    // ejecucion «en curso» para siempre.
    private void execute(AgentRun run, AgentRunner.Task task) {
        try {
            runner.run(run, task);
        } catch (Throwable t) {
            log.error("La ejecución {} terminó con un error no atrapado", run.getId(), t);
            trace.failIfOpen(run.getId(), UNEXPECTED);
        }
    }

    private Optional<AgentRun> live(UUID patientId, LocalDate weekStart) {
        return live(patientId, weekStart, AgentRun.Type.INFORME);
    }

    private Optional<AgentRun> live(UUID patientId, LocalDate weekStart, AgentRun.Type type) {
        return runs.findFirstByPatientIdAndPeriodStartAndRunTypeAndStatusIn(patientId, weekStart, type, RunTrace.OPEN);
    }

    public RunView get(UUID runId) {
        return view(find(runId));
    }

    public List<StepView> steps(UUID runId, long afterId) {
        find(runId);
        return stepsAfter(runId, afterId);
    }

    List<StepView> stepsAfter(UUID runId, long afterId) {
        return steps.findByRunIdAndIdGreaterThanOrderByIdAsc(runId, afterId).stream().map(RunService::stepView).toList();
    }

    AgentRun find(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("No encontramos esa ejecución."));
    }

    RunView view(AgentRun run) {
        UUID reportId = reports.findFirstByRunId(run.getId()).map(Report::getId).orElse(null);
        return new RunView(run.getId(), run.getRunType(), run.getPatientId(), run.getPeriodStart(), run.getPeriodEnd(),
                run.getStatus(), run.getStopReason(), reportId, run.getErrorMessage(), run.getProvider(), run.getModel(),
                run.isFallbackUsed(), run.getIterations(), run.getTokensIn(), run.getTokensOut(), run.getCostUsd(),
                run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt());
    }

    public static StepView stepView(AgentStep s) {
        return new StepView(s.getId(), s.getSeq(), s.getStepType(), s.getMessage(), s.getToolName(), s.getParams(),
                s.getResult(), s.isError(), s.getDurationMs(), s.getTokensIn(), s.getTokensOut(), s.getCreatedAt());
    }

    static boolean isFinal(AgentRun run) {
        return !RunTrace.OPEN.contains(run.getStatus());
    }
}
