package com.patientprogressauditor.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryEvent;
import com.patientprogressauditor.entry.EntryEventRepository;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.patient.Patient;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.report.MonthlyReportWeek;
import com.patientprogressauditor.report.MonthlyReportWeekRepository;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportCitation;
import com.patientprogressauditor.report.ReportCitationRepository;
import com.patientprogressauditor.report.ReportRepository;
import com.patientprogressauditor.report.ReportVersion;
import com.patientprogressauditor.report.ReportVersionRepository;
import com.patientprogressauditor.user.AppRole;
import com.patientprogressauditor.user.AppRoleRepository;
import com.patientprogressauditor.user.AppUser;
import com.patientprogressauditor.user.AppUserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class RepositoriesTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Autowired private AppUserRepository users;
    @Autowired private AppRoleRepository roles;
    @Autowired private PatientRepository patients;
    @Autowired private EntryRepository entries;
    @Autowired private EntryEventRepository events;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @Autowired private ReportRepository reports;
    @Autowired private ReportVersionRepository versions;
    @Autowired private ReportCitationRepository citations;
    @Autowired private MonthlyReportWeekRepository monthlyWeeks;
    @Autowired private EntityManager em;
    @Autowired private ObjectMapper json;

    private AppUser nurse;
    private Patient patient;

    @BeforeEach
    void fixtures() {
        nurse = new AppUser();
        nurse.setEmail(nurse.getId() + "@example.test");
        nurse.setDisplayName("Enfermera de prueba");
        nurse.setDemo(true);
        nurse.getRoles().add(roles.findByCode(AppRole.NURSING).orElseThrow());
        users.save(nurse);

        patient = new Patient();
        patient.setAlias("P-999");
        patient.setBed("204-A");
        patient.setAge((short) 71);
        patient.setAdmittedOn(MONDAY);
        patients.save(patient);
    }

    private Entry entry(LocalDate date, String body, Instant createdAt) {
        Entry entry = new Entry();
        entry.setPatientId(patient.getId());
        entry.setEntryDate(date);
        entry.setBody(body);
        entry.setAuthorId(nurse.getId());
        entry.setStructuringStatus(Entry.StructuringStatus.ESTRUCTURADA);
        entry.setCreatedAt(createdAt);
        return entries.saveAndFlush(entry);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void roleAndPermissionsAreReadThroughTheUser() {
        flushAndClear();

        AppUser loaded = users.findById(nurse.getId()).orElseThrow();
        Set<String> permissions = loaded.getRoles().iterator().next().getPermissions().stream()
                .map(permission -> permission.getCode()).collect(java.util.stream.Collectors.toSet());

        assertThat(loaded.getRoles()).extracting(AppRole::getCode).containsExactly("ENFERMERIA");
        assertThat(permissions).contains("REPORTS_APPROVE", "ENTRIES_CREATE");
        assertThat(users.findByDemoTrueAndActiveTrueOrderByDisplayName()).extracting(AppUser::getId).contains(nurse.getId());
    }

    @Test
    void entriesComeBackInDateOrderThenCreationOrderAndWithinTheRange() {
        Instant morning = Instant.parse("2026-10-20T13:00:00Z");
        entry(MONDAY.plusDays(2), "miercoles", morning);
        entry(MONDAY, "lunes tarde", morning.plusSeconds(3600));
        entry(MONDAY, "lunes temprano", morning);
        entry(MONDAY.plusDays(20), "fuera de rango", morning);
        flushAndClear();

        List<Entry> found = entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
                patient.getId(), MONDAY, MONDAY.plusDays(6));

        assertThat(found).extracting(Entry::getBody).containsExactly("lunes temprano", "lunes tarde", "miercoles");
    }

    @Test
    void originalTextCannotBeUpdated() {
        Entry saved = entry(MONDAY, "texto tal como se escribio", Instant.now());
        flushAndClear();

        Entry loaded = entries.findById(saved.getId()).orElseThrow();
        loaded.setBody("texto alterado");
        flushAndClear();

        assertThat(entries.findById(saved.getId()).orElseThrow().getBody()).isEqualTo("texto tal como se escribio");
    }

    @Test
    void eventsAreSavedAndReadWithTheirConfirmation() {
        Entry saved = entry(MONDAY, "Tuvo diarrea intensa dos veces", Instant.now());
        EntryEvent event = new EntryEvent();
        event.setEntryId(saved.getId());
        event.setSymptom(Symptom.DIARREA);
        event.setIntensity(Intensity.INTENSO);
        event.setOccurrences((short) 2);
        event.setOrigin(EntryEvent.Origin.IA);
        event.setConfirmedBy(nurse.getId());
        event.setConfirmedAt(Instant.now());
        events.saveAndFlush(event);
        flushAndClear();

        List<EntryEvent> found = events.findByEntryIdIn(List.of(saved.getId()));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getSymptom()).isEqualTo(Symptom.DIARREA);
        assertThat(found.get(0).getIntensity()).isEqualTo(Intensity.INTENSO);
        assertThat(found.get(0).getOccurrences()).isEqualTo((short) 2);
    }

    @Test
    void runStepsReportVersionsAndCitationsRoundTrip() throws Exception {
        Entry cited = entry(MONDAY, "registro citado", Instant.now());

        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.INFORME);
        run.setPatientId(patient.getId());
        run.setRequestedBy(nurse.getId());
        run.setPeriodStart(MONDAY);
        run.setPeriodEnd(MONDAY.plusDays(6));
        runs.save(run);

        AgentStep step = new AgentStep();
        step.setRunId(run.getId());
        step.setSeq(1);
        step.setStepType(AgentStep.Type.HERRAMIENTA);
        step.setToolName("get_week_entries");
        step.setParams(json.readTree("{\"week_start\":\"2026-10-19\"}"));
        steps.saveAndFlush(step);

        Report report = new Report();
        report.setPatientId(patient.getId());
        report.setReportType(Report.Type.SEMANAL);
        report.setPeriodStart(MONDAY);
        report.setPeriodEnd(MONDAY.plusDays(6));
        report.setRequestedBy(nurse.getId());
        report.setRunId(run.getId());
        reports.save(report);

        ReportVersion version = new ReportVersion();
        version.setReportId(report.getId());
        version.setVersionNo(1);
        version.setOrigin(ReportVersion.Origin.MODELO);
        version.setContent(json.readTree("{\"summary\":\"resumen\",\"claims\":[{\"claim_key\":\"c1\"}]}"));
        versions.save(version);
        citations.saveAndFlush(new ReportCitation(version.getId(), "c1", cited.getId()));

        Report weekly = new Report();
        weekly.setPatientId(patient.getId());
        weekly.setReportType(Report.Type.SEMANAL);
        weekly.setPeriodStart(MONDAY.minusDays(28));
        weekly.setPeriodEnd(MONDAY.minusDays(22));
        weekly.setRequestedBy(nurse.getId());
        reports.save(weekly);
        Report monthly = new Report();
        monthly.setPatientId(patient.getId());
        monthly.setReportType(Report.Type.MENSUAL);
        monthly.setPeriodStart(MONDAY.minusDays(28));
        monthly.setPeriodEnd(MONDAY.minusDays(1));
        monthly.setRequestedBy(nurse.getId());
        reports.save(monthly);
        monthlyWeeks.saveAndFlush(new MonthlyReportWeek(monthly.getId(), (short) 1, weekly.getId()));
        flushAndClear();

        assertThat(steps.findByRunIdOrderBySeqAsc(run.getId())).singleElement()
                .satisfies(found -> assertThat(found.getParams().get("week_start").asText()).isEqualTo("2026-10-19"));
        JsonNode content = versions.findByReportIdOrderByVersionNoAsc(report.getId()).get(0).getContent();
        assertThat(content.get("claims").get(0).get("claim_key").asText()).isEqualTo("c1");
        assertThat(citations.findByReportVersionId(version.getId())).singleElement()
                .satisfies(found -> assertThat(found.getEntryId()).isEqualTo(cited.getId()));
        assertThat(reports.findByPatientIdOrderByPeriodStartDesc(patient.getId())).hasSize(3);
        assertThat(monthlyWeeks.findByMonthlyReportIdOrderByWeekNo(monthly.getId())).singleElement()
                .satisfies(found -> assertThat(found.getWeeklyReportId()).isEqualTo(weekly.getId()));
        assertThat(runs.findByStatusIn(List.of(AgentRun.Status.EN_COLA))).extracting(AgentRun::getId).contains(run.getId());
    }
}
