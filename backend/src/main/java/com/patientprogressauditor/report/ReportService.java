package com.patientprogressauditor.report;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStepRepository;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.patient.Patient;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.report.ReportEditValidator.Edit;
import com.patientprogressauditor.run.RunService;
import com.patientprogressauditor.run.RunService.StepView;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

// Lo que se hace con un informe despues de que el agente lo deja: verlo con su evidencia, corregirlo, aprobarlo o
// rechazarlo. Las reglas (quien ve que, que transiciones existen, que se puede editar) se deciden AQUI, en el servidor.
@Service
public class ReportService {

    private static final LocalDate EPOCH = LocalDate.of(2000, 1, 1);

    public record Summary(UUID id, UUID patientId, String patientAlias, Report.Type reportType, LocalDate periodStart,
            LocalDate periodEnd, Report.Status status, String statusReason, Instant generatedAt, String reviewedBy,
            Instant reviewedAt, String provider, String model) {
    }

    public record VersionMeta(int versionNo, ReportVersion.Origin origin, String editedBy, Instant createdAt) {
    }

    public record VersionView(int versionNo, ReportVersion.Origin origin, String editedBy, Instant createdAt, JsonNode content,
            String plainText) {
    }

    public record CitedEntry(UUID id, LocalDate date, String body, String author) {
    }

    public record Detail(Summary report, String requestedBy, UUID runId, Integer approvedVersionNo, VersionView version,
            List<VersionMeta> versions, Map<String, List<CitedEntry>> citations) {
    }

    public record WeekOption(LocalDate weekStart, LocalDate weekEnd, int entries, UUID reportId, Report.Status reportStatus,
            UUID runId, boolean canGenerate, String reason) {
    }

    private final ReportRepository reports;
    private final ReportVersionRepository versions;
    private final ReportCitationRepository citations;
    private final PatientRepository patients;
    private final EntryRepository entries;
    private final AppUserRepository users;
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final ReportEditValidator editValidator;
    private final Clock clock;

    public ReportService(ReportRepository reports, ReportVersionRepository versions, ReportCitationRepository citations,
            PatientRepository patients, EntryRepository entries, AppUserRepository users, AgentRunRepository runs,
            AgentStepRepository steps, ReportEditValidator editValidator, Clock clock) {
        this.reports = reports;
        this.versions = versions;
        this.citations = citations;
        this.patients = patients;
        this.entries = entries;
        this.users = users;
        this.runs = runs;
        this.steps = steps;
        this.editValidator = editValidator;
        this.clock = clock;
    }

    // ---- lectura ----

    public List<Summary> list(DemoPrincipal principal, Report.Status status, UUID patientId, Report.Type type) {
        List<Report.Status> visible = ReportVisibility.statusesFor(principal).stream().map(Report.Status::valueOf).toList();
        // Pedir un estado que el rol no ve no es un error: simplemente no hay nada.
        List<Report.Status> wanted = status == null ? visible : visible.stream().filter(s -> s == status).toList();
        List<Report> found = patientId == null
                ? reports.findByStatusInOrderByPeriodStartDescCreatedAtDesc(wanted)
                : reports.findByStatusInAndPatientIdOrderByPeriodStartDescCreatedAtDesc(wanted, patientId);
        if (type != null) {
            found = found.stream().filter(r -> r.getReportType() == type).toList();
        }
        return summaries(found);
    }

    public Detail detail(UUID id, DemoPrincipal principal) {
        Report report = visible(id, principal);
        List<ReportVersion> all = versions.findByReportIdOrderByVersionNoAsc(id);
        ReportVersion current = current(report, all);
        Map<UUID, String> names = names(users(report, all));
        Map<String, List<CitedEntry>> cited = citedEntries(current);
        return new Detail(summaries(List.of(report)).get(0), name(names, report.getRequestedBy()), report.getRunId(),
                report.getApprovedVersionNo(), view(current, names),
                // El historial de versiones es de enfermeria: el medico solo ve la aprobada.
                isDoctor(principal) ? List.of() : all.stream().map(v -> meta(v, names)).toList(), cited);
    }

    // Una version concreta, p. ej. la 1 que redacto el modelo, para compararla con la editada (RF-25).
    public VersionView version(UUID id, int versionNo) {
        reports.findById(id).orElseThrow(() -> new NotFoundException("No encontramos ese informe."));
        List<ReportVersion> all = versions.findByReportIdOrderByVersionNoAsc(id);
        ReportVersion version = all.stream().filter(v -> v.getVersionNo() == versionNo).findFirst()
                .orElseThrow(() -> new NotFoundException("Ese informe no tiene una versión " + versionNo + "."));
        return view(version, names(users(reports.findById(id).orElseThrow(), all)));
    }

    // Los pasos de la ejecucion que produjo el informe: la evidencia de que consulto el agente (RF-24).
    public List<StepView> trace(UUID id, DemoPrincipal principal) {
        Report report = visible(id, principal);
        if (report.getRunId() == null) {
            return List.of();
        }
        return steps.findByRunIdOrderBySeqAsc(report.getRunId()).stream().map(RunService::stepView).toList();
    }

    // ---- edicion y decision ----

    @Transactional
    public Detail edit(UUID id, Edit edit, DemoPrincipal principal) {
        Report report = locked(id);
        ReportTransitions.requireEditable(report);
        List<ReportVersion> all = versions.findByReportIdOrderByVersionNoAsc(id);
        ReportVersion latest = all.get(all.size() - 1);
        if (latest.getContent() == null) {
            throw new FieldException(HttpStatus.CONFLICT, "status", "Este informe no tiene un contenido estructurado que se pueda editar.");
        }
        Set<UUID> ids = new HashSet<>();
        if (edit.claims() != null) {
            edit.claims().forEach(c -> {
                if (c.entryIds() != null) {
                    ids.addAll(c.entryIds());
                }
            });
        }
        Map<UUID, Entry> found = entries.findAllById(ids).stream().collect(Collectors.toMap(Entry::getId, Function.identity()));
        ReportEditValidator.Result result = editValidator.check(report, latest.getContent(), edit, found);
        if (!result.valid()) {
            throw new FieldException(HttpStatus.UNPROCESSABLE_ENTITY, "Revise lo que cambió: el informe no se guardó.", result.problems());
        }
        if (result.content().equals(latest.getContent())) {
            throw new FieldException(HttpStatus.BAD_REQUEST, "content", "No hay cambios que guardar.");
        }
        ReportVersion version = new ReportVersion();
        version.setReportId(id);
        version.setVersionNo(latest.getVersionNo() + 1);
        version.setOrigin(ReportVersion.Origin.ENFERMERIA);
        version.setContent(result.content());
        version.setEditedBy(principal.id());
        versions.saveAndFlush(version);
        List<ReportCitation> rows = new ArrayList<>();
        result.citations().forEach((claim, cited) -> cited.forEach(entry -> rows.add(new ReportCitation(version.getId(), claim, entry))));
        citations.saveAllAndFlush(rows);
        report.setUpdatedAt(clock.instant());
        reports.saveAndFlush(report);
        return detail(id, principal);
    }

    @Transactional
    public Detail approve(UUID id, DemoPrincipal principal) {
        Report report = locked(id);
        ReportTransitions.require(report, Report.Status.APROBADO);
        List<ReportVersion> all = versions.findByReportIdOrderByVersionNoAsc(id);
        report.setStatus(Report.Status.APROBADO);
        report.setReviewedBy(principal.id());
        report.setReviewedAt(clock.instant());
        report.setApprovedVersionNo(all.get(all.size() - 1).getVersionNo());
        report.setUpdatedAt(clock.instant());
        reports.saveAndFlush(report);
        return detail(id, principal);
    }

    @Transactional
    public Detail reject(UUID id, String reason, DemoPrincipal principal) {
        Report report = locked(id);
        ReportTransitions.require(report, Report.Status.RECHAZADO);
        report.setStatus(Report.Status.RECHAZADO);
        report.setStatusReason(reason.strip());
        report.setReviewedBy(principal.id());
        report.setReviewedAt(clock.instant());
        report.setUpdatedAt(clock.instant());
        reports.saveAndFlush(report);
        return detail(id, principal);
    }

    // ---- semanas para generar ----

    // Solo semanas completas con registros (tabla 3.3): por cada una, el informe vigente o la ejecucion en marcha.
    public List<WeekOption> weeks(UUID patientId) {
        if (!patients.existsById(patientId)) {
            throw new NotFoundException("No encontramos al paciente.");
        }
        LocalDate today = LocalDate.now(clock);
        Map<LocalDate, Integer> perWeek = new TreeMap<>(Comparator.reverseOrder());
        for (Entry e : entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patientId, EPOCH, today)) {
            LocalDate monday = e.getEntryDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (monday.plusDays(6).isBefore(today)) {
                perWeek.merge(monday, 1, Integer::sum);
            }
        }
        List<Report> weekly = reports.findByPatientIdOrderByPeriodStartDesc(patientId).stream()
                .filter(r -> r.getReportType() == Report.Type.SEMANAL).toList();
        List<WeekOption> out = new ArrayList<>();
        perWeek.forEach((monday, count) -> {
            Optional<Report> current = weekly.stream().filter(r -> r.getPeriodStart().equals(monday))
                    .filter(r -> r.getStatus() == Report.Status.PENDIENTE_REVISION || r.getStatus() == Report.Status.APROBADO).findFirst();
            Optional<AgentRun> live = runs.findFirstByPatientIdAndPeriodStartAndRunTypeAndStatusIn(patientId, monday,
                    AgentRun.Type.INFORME, List.of(AgentRun.Status.EN_COLA, AgentRun.Status.EN_CURSO));
            String reason = current.isPresent() ? "Ya hay un informe de esta semana." : live.isPresent() ? "Ya se está generando." : null;
            out.add(new WeekOption(monday, monday.plusDays(6), count, current.map(Report::getId).orElse(null),
                    current.map(Report::getStatus).orElse(null), live.map(AgentRun::getId).orElse(null),
                    current.isEmpty(), reason));
        });
        return out;
    }

    // ---- ayudas ----

    private Report locked(UUID id) {
        return reports.findForUpdate(id).orElseThrow(() -> new NotFoundException("No encontramos ese informe."));
    }

    // Un informe que el rol no puede ver se trata como inexistente: no se revela que esta.
    private Report visible(UUID id, DemoPrincipal principal) {
        return reports.findById(id)
                .filter(r -> ReportVisibility.statusesFor(principal).contains(r.getStatus().name()))
                .orElseThrow(() -> new NotFoundException("No encontramos ese informe."));
    }

    private static boolean isDoctor(DemoPrincipal principal) {
        return !ReportVisibility.statusesFor(principal).contains(Report.Status.PENDIENTE_REVISION.name());
    }

    // La aprobada si hay; si no, la ultima.
    private static ReportVersion current(Report report, List<ReportVersion> all) {
        Integer approved = report.getApprovedVersionNo();
        return all.stream().filter(v -> approved == null ? v == all.get(all.size() - 1) : v.getVersionNo() == approved).findFirst()
                .orElse(all.get(all.size() - 1));
    }

    private List<Summary> summaries(List<Report> found) {
        Map<UUID, String> aliases = patients.findAllById(found.stream().map(Report::getPatientId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getAlias));
        Map<UUID, String> reviewers = names(found.stream().map(Report::getReviewedBy).filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return found.stream().map(r -> new Summary(r.getId(), r.getPatientId(), aliases.get(r.getPatientId()), r.getReportType(),
                r.getPeriodStart(), r.getPeriodEnd(), r.getStatus(), r.getStatusReason(), r.getCreatedAt(),
                r.getReviewedBy() == null ? null : name(reviewers, r.getReviewedBy()), r.getReviewedAt(), r.getProvider(), r.getModel())).toList();
    }

    private Set<UUID> users(Report report, List<ReportVersion> all) {
        Set<UUID> ids = new HashSet<>();
        ids.add(report.getRequestedBy());
        all.stream().map(ReportVersion::getEditedBy).filter(java.util.Objects::nonNull).forEach(ids::add);
        return ids;
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }

    private static String name(Map<UUID, String> names, UUID id) {
        return id == null ? null : names.getOrDefault(id, "—");
    }

    private static VersionMeta meta(ReportVersion v, Map<UUID, String> names) {
        return new VersionMeta(v.getVersionNo(), v.getOrigin(), name(names, v.getEditedBy()), v.getCreatedAt());
    }

    private static VersionView view(ReportVersion v, Map<UUID, String> names) {
        return new VersionView(v.getVersionNo(), v.getOrigin(), name(names, v.getEditedBy()), v.getCreatedAt(), v.getContent(), v.getPlainText());
    }

    // Por afirmacion, los registros que cita con su fecha y su texto ORIGINAL (la fuente citable, RN-07).
    private Map<String, List<CitedEntry>> citedEntries(ReportVersion version) {
        List<ReportCitation> rows = citations.findByReportVersionId(version.getId());
        Map<UUID, Entry> byId = entries.findAllById(rows.stream().map(ReportCitation::getEntryId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Entry::getId, Function.identity()));
        Map<UUID, String> authors = names(byId.values().stream().map(Entry::getAuthorId).collect(Collectors.toSet()));
        Map<String, List<CitedEntry>> out = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparing((ReportCitation c) -> byId.get(c.getEntryId()).getEntryDate())
                .thenComparing(c -> byId.get(c.getEntryId()).getCreatedAt())).forEach(c -> {
                    Entry e = byId.get(c.getEntryId());
                    out.computeIfAbsent(c.getClaimKey(), k -> new ArrayList<>())
                            .add(new CitedEntry(e.getId(), e.getEntryDate(), e.getBody(), name(authors, e.getAuthorId())));
                });
        return out;
    }
}
