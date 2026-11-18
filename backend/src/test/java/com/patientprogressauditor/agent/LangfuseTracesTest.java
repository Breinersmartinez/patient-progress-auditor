package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.LangfuseRun;
import com.patientprogressauditor.llm.LlmException.Kind;

class LangfuseTracesTest {

    private static final Instant START = Instant.parse("2026-11-04T10:00:00Z");
    private static final List<Attempt> ATTEMPTS = List.of(new Attempt("groq", "openai/gpt-oss-120b", 1, null, 900, null, 100, 20));

    private static AgentRun closedRun() {
        AgentRun run = new AgentRun();
        run.setRunType(AgentRun.Type.MENSUAL);
        run.setPatientId(UUID.randomUUID());
        run.setRequestedBy(UUID.randomUUID());
        run.setStatus(AgentRun.Status.INCOMPLETA);
        run.setStopReason(AgentRun.StopReason.MAX_TOKENS);
        run.setStartedAt(START);
        run.setFinishedAt(START.plusMillis(4200));
        return run;
    }

    @Test
    void itCarriesOnlyTheIdTheTypeTheStateTheTimesAndTheAttempts() {
        AgentRun row = closedRun();

        LangfuseRun run = LangfuseTraces.of(row, ATTEMPTS);

        assertThat(run.runId()).isEqualTo(row.getId());
        assertThat(run.type()).isEqualTo("MENSUAL");
        assertThat(run.status()).isEqualTo("INCOMPLETA");
        assertThat(run.stopReason()).isEqualTo("MAX_TOKENS");
        assertThat(run.startedAt()).isEqualTo(START);
        assertThat(run.millis()).isEqualTo(4200);
        assertThat(run.attempts()).isEqualTo(ATTEMPTS);
        // El paciente y quien lo pidio no tienen donde ir: el registro no trae esos campos.
        assertThat(run.toString()).doesNotContain(row.getPatientId().toString()).doesNotContain(row.getRequestedBy().toString());
    }

    @Test
    void aRunWithoutAStopReasonOrAFinishTimeStillMaps() {
        AgentRun row = closedRun();
        row.setStopReason(null);
        row.setFinishedAt(null);

        LangfuseRun run = LangfuseTraces.of(row, ATTEMPTS);

        assertThat(run.stopReason()).isNull();
        assertThat(run.millis()).isZero();
    }

    @Test
    void aRowWithoutAStartTimeIsNotSent() {
        AgentRun row = closedRun();
        row.setStartedAt(null);

        assertThat(LangfuseTraces.of(row, ATTEMPTS)).isNull();
    }

    @Test
    void aRowWithoutATypeOrAStateIsNotSentInsteadOfFailing() {
        AgentRun noType = closedRun();
        noType.setRunType(null);
        AgentRun noStatus = closedRun();
        noStatus.setStatus(null);

        assertThat(LangfuseTraces.of(noType, ATTEMPTS)).isNull();
        assertThat(LangfuseTraces.of(noStatus, ATTEMPTS)).isNull();
    }

    @Test
    void aFailedAttemptKeepsItsCategoryAndDropsNothingElseOfTheTrace() {
        List<Attempt> attempts = List.of(new Attempt("groq", "openai/gpt-oss-120b", 1, Kind.RATE_LIMIT, 100, "HTTP 429"));

        assertThat(LangfuseTraces.of(closedRun(), attempts).attempts()).extracting(Attempt::failure).containsExactly(Kind.RATE_LIMIT);
    }
}
