package com.patientprogressauditor.consultation;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentRunner;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.patient.Patient;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.run.RunExecutor;
import com.patientprogressauditor.run.RunService;
import com.patientprogressauditor.run.RunTrace;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.BusyException;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

// La consulta del medico, «Armar informe» (D31, RF-46): la pide, la valida y la deja leer solo a su autor. El camino de
// cola/SSE/traza es el mismo de un informe (RunExecutor, RunTrace, RunService): lo unico propio de aqui es la validacion
// del periodo (tope de 31 dias, sin cruce con la comparacion) y donde se guarda el resultado (doctor_consultations, nunca reports).
@Service
public class ConsultationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationService.class);

    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);

    // El tope real, medido con el modelo (docs/PLAN_M3.md, Pieza 6, paso 1): cubre cualquier mes calendario.
    public static final int MAX_DAYS = 31;

    static final String UNEXPECTED = "El agente falló de forma inesperada. Intente de nuevo en unos minutos.";

    public record CitedEntry(UUID id, LocalDate date, String body) {
    }

    public record ConsultationDetail(UUID id, UUID patientId, String patientAlias, LocalDate periodStart, LocalDate periodEnd,
            LocalDate comparisonStart, LocalDate comparisonEnd, JsonNode content, String plainText, UUID runId,
            Instant createdAt, Map<String, List<CitedEntry>> citations) {
    }

    // Semanas reales con registros, para el selector "Semana" del formulario (como el mockup, vInfGenerar): a
    // diferencia de ReportService.WeekOption, sin reportId/runId/canGenerate, porque una consulta no tiene ese estado
    // (no hay "ya existe", se puede pedir otra vez la misma semana) y no depende de REPORTS_REQUEST (paso 5 de la
    // Pieza 6: la consulta no reusa la infraestructura de informes).
    public record WeekOption(LocalDate weekStart, LocalDate weekEnd, int entries) {
    }

    private final PatientRepository patients;
    private final EntryRepository entries;
    private final DoctorConsultationRepository consultations;
    private final AgentRunner runner;
    private final AgentRunRepository runs;
    private final RunService runService;
    private final RunExecutor executor;
    private final RunTrace trace;
    private final Clock clock;

    public ConsultationService(PatientRepository patients, EntryRepository entries, DoctorConsultationRepository consultations,
            AgentRunner runner, AgentRunRepository runs, RunService runService, RunExecutor executor, RunTrace trace, Clock clock) {
        this.patients = patients;
        this.entries = entries;
        this.consultations = consultations;
        this.runner = runner;
        this.runs = runs;
        this.runService = runService;
        this.executor = executor;
        this.trace = trace;
        this.clock = clock;
    }

    // Valida en el servidor (criterio 4 de la Pieza 6: nada de esto crea una ejecucion si falla) y lanza en segundo plano.
    public RunService.RunView request(UUID patientId, LocalDate periodStart, LocalDate periodEnd, LocalDate comparisonStart,
            LocalDate comparisonEnd, DemoPrincipal principal) {
        validateRange(periodStart, periodEnd, "period");
        if (periodEnd.isAfter(LocalDate.now(clock))) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "periodEnd", "El período no puede llegar más allá de hoy.");
        }
        if ((comparisonStart == null) != (comparisonEnd == null)) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "comparisonStart", "Indique las dos fechas del período de comparación, o ninguna.");
        }
        if (comparisonStart != null) {
            validateRange(comparisonStart, comparisonEnd, "comparison");
            boolean overlaps = !comparisonEnd.isBefore(periodStart) && !periodEnd.isBefore(comparisonStart);
            if (overlaps) {
                throw new FieldException(HttpStatus.BAD_REQUEST, "comparisonStart",
                        "El período de comparación no puede cruzarse con el período de la consulta.");
            }
        }
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos al paciente.");
        }
        AgentRunner.ConsultationPeriod period = new AgentRunner.ConsultationPeriod(periodStart, periodEnd, comparisonStart, comparisonEnd);
        AgentRunner.Task task = AgentRunner.Task.consultation(patientId, principal.id(), period);
        String blocked = runner.blockedReason(task);
        if (blocked != null) {
            throw new FieldException(HttpStatus.CONFLICT, "periodStart", blocked);
        }
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.CONSULTA);
        run.setPatientId(patientId);
        run.setRequestedBy(principal.id());
        run.setPeriodStart(periodStart);
        run.setPeriodEnd(periodEnd);
        runs.save(run);
        // La vista se toma ANTES de lanzar: el hilo del ejecutor cambia esa misma entidad al empezar.
        RunService.RunView created = runService.view(run);
        launch(run, task);
        return created;
    }

    // ---- semanas para el formulario ----

    // Semanas completas (de lunes a domingo, sin llegar más allá de hoy) con al menos un registro, de más reciente a
    // más antigua, igual orden que ReportService.weeks().
    public List<WeekOption> weeks(UUID patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos al paciente.");
        }
        LocalDate today = LocalDate.now(clock);
        Map<LocalDate, Integer> perWeek = new TreeMap<>(Comparator.reverseOrder());
        for (Entry e : entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patientId, EPOCH, today)) {
            LocalDate monday = e.getEntryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (!monday.plusDays(6).isAfter(today)) {
                perWeek.merge(monday, 1, Integer::sum);
            }
        }
        List<WeekOption> out = new ArrayList<>();
        perWeek.forEach((monday, count) -> out.add(new WeekOption(monday, monday.plusDays(6), count)));
        return out;
    }

    private void validateRange(LocalDate start, LocalDate end, String field) {
        if (end.isBefore(start)) {
            throw new FieldException(HttpStatus.BAD_REQUEST, field + "End", "La fecha final no puede ser anterior a la inicial.");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAYS) {
            throw new FieldException(HttpStatus.BAD_REQUEST, field + "End", "El período no puede pasar de " + MAX_DAYS + " días.");
        }
    }

    private void launch(AgentRun run, AgentRunner.Task task) {
        try {
            executor.submit(() -> execute(run, task));
        } catch (RejectedExecutionException e) {
            String message = "El sistema está generando otras consultas en este momento. Intente de nuevo en un minuto.";
            trace.failIfOpen(run.getId(), message);
            throw new BusyException(message);
        }
    }

    private void execute(AgentRun run, AgentRunner.Task task) {
        try {
            runner.run(run, task);
        } catch (Throwable t) {
            log.error("La consulta {} terminó con un error no atrapado", run.getId(), t);
            trace.failIfOpen(run.getId(), UNEXPECTED);
        }
    }

    // Solo el autor la ve (criterio 3 de la Pieza 6): a cualquier otro, 404, como un informe que el rol no puede ver (RN-01).
    public ConsultationDetail detail(UUID id, DemoPrincipal principal) {
        return view(findVisible(id, principal));
    }

    DoctorConsultation findVisible(UUID id, DemoPrincipal principal) {
        return consultations.findByIdAndDoctorId(id, principal.id())
                .orElseThrow(() -> new NotFoundException("No encontramos esa consulta."));
    }

    private ConsultationDetail view(DoctorConsultation c) {
        String alias = patients.findById(c.getPatientId()).map(Patient::getAlias).orElse("");
        return new ConsultationDetail(c.getId(), c.getPatientId(), alias, c.getPeriodStart(), c.getPeriodEnd(),
                c.getComparisonStart(), c.getComparisonEnd(), c.getContent(), c.getPlainText(), c.getRunId(), c.getCreatedAt(),
                citedEntries(c.getContent()));
    }

    // Cada afirmacion cita entry_ids reales (DraftValidator ya los resolvio de alias a UUID): no hay una tabla de
    // citas aparte para la consulta (a diferencia de report_citations), asi que se resuelven aqui, por id.
    private Map<String, List<CitedEntry>> citedEntries(JsonNode content) {
        Map<String, List<CitedEntry>> out = new LinkedHashMap<>();
        if (content == null) {
            return out;
        }
        Set<UUID> ids = new HashSet<>();
        for (JsonNode claim : content.path("claims")) {
            for (JsonNode ref : claim.path("entry_ids")) {
                try {
                    ids.add(UUID.fromString(ref.asText()));
                } catch (IllegalArgumentException ignored) {
                    // defensivo: el contenido ya validado siempre trae UUID
                }
            }
        }
        Map<UUID, Entry> byId = entries.findAllById(ids).stream().collect(Collectors.toMap(Entry::getId, Function.identity()));
        for (JsonNode claim : content.path("claims")) {
            List<CitedEntry> list = new ArrayList<>();
            for (JsonNode ref : claim.path("entry_ids")) {
                Entry entry = byId.get(parseOrNull(ref.asText()));
                if (entry != null) {
                    list.add(new CitedEntry(entry.getId(), entry.getEntryDate(), entry.getBody()));
                }
            }
            out.put(claim.path("claim_key").asText(), list);
        }
        return out;
    }

    private static UUID parseOrNull(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
