package com.patientprogressauditor.structuring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;
import com.patientprogressauditor.entry.Intensity;
import com.patientprogressauditor.entry.Symptom;
import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.InvalidOutputException;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.LlmGateway;
import com.patientprogressauditor.llm.LlmMessage;
import com.patientprogressauditor.llm.LlmPurpose;
import com.patientprogressauditor.llm.LlmRequest;
import com.patientprogressauditor.llm.LlmResponse;
import com.patientprogressauditor.llm.LlmResult;
import com.patientprogressauditor.llm.LlmUnavailableException;
import com.patientprogressauditor.llm.StructuredResult;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.NotFoundException;

@SpringBootTest(properties = "app.seed.demo=true")
@Transactional
class StructuringServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EXAMPLE = "Dolor de cabeza intenso desde la mañana. Tuvo diarrea intensa dos veces. Comió poco.";
    private static final String ANSWER = """
            {"events":[{"symptom":"DOLOR_CABEZA","intensity":"INTENSO","occurrences":null},
                       {"symptom":"DIARREA","intensity":"INTENSO","occurrences":2},
                       {"symptom":"APETITO_BAJO","intensity":"SIN_INDICAR","occurrences":null}]}""";

    @MockitoBean private LlmGateway gateway;
    @Autowired private StructuringService service;
    @Autowired private AgentRunRepository runs;
    @Autowired private AgentStepRepository steps;
    @Autowired private JdbcTemplate jdbc;

    private UUID patient() {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
    }

    private DemoPrincipal nurse() {
        UUID id = jdbc.queryForObject("SELECT id FROM app_users WHERE display_name = 'Camilo Torres'", UUID.class);
        return new DemoPrincipal(id, "Camilo Torres", "ENFERMERIA", java.util.Set.of());
    }

    private static StructuredResult result(String json, boolean corrected, boolean fallback, List<Attempt> attempts) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        LlmResult llm = new LlmResult(new LlmResponse(json, List.of(), LlmResponse.FinishReason.STOP, 300, 80),
                fallback ? "gemini" : "groq", fallback ? "gemini-3.5-flash-lite" : "openai/gpt-oss-120b", fallback, 1200,
                new BigDecimal("0.000093"), attempts);
        return new StructuredResult(node, llm, 300, 80, new BigDecimal("0.000093"), corrected,
                corrected ? List.of("events[0].symptom debe ser uno de ...") : List.of(), attempts);
    }

    private List<AgentStep> stepsOf(UUID runId) {
        return steps.findByRunIdOrderBySeqAsc(runId);
    }

    @Test
    void theExampleNoteBecomesThreeProposalsAndNothingIsSaved() throws Exception {
        int entriesBefore = jdbc.queryForObject("SELECT count(*) FROM entries", Integer.class);
        int eventsBefore = jdbc.queryForObject("SELECT count(*) FROM entry_events", Integer.class);
        when(gateway.structured(any(), any())).thenReturn(result(ANSWER, false, false,
                List.of(new Attempt("groq", "openai/gpt-oss-120b", 1, null, 1100, null, 300, 80))));

        Proposal proposal = service.propose(patient(), EXAMPLE, nurse());

        assertThat(proposal.failure()).isNull();
        assertThat(proposal.events()).containsExactly(
                new EventProposal(Symptom.DOLOR_CABEZA, Intensity.INTENSO, null),
                new EventProposal(Symptom.DIARREA, Intensity.INTENSO, 2),
                new EventProposal(Symptom.APETITO_BAJO, Intensity.SIN_INDICAR, null));
        assertThat(proposal.provider()).isEqualTo("groq");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries", Integer.class)).isEqualTo(entriesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entry_events", Integer.class)).isEqualTo(eventsBefore);
    }

    @Test
    void theRequestToTheModelCarriesOnlyTheTextWithAShortBudgetAndNoTools() throws Exception {
        when(gateway.structured(any(), any())).thenReturn(result("{\"events\":[]}", false, false,
                List.of(new Attempt("groq", "m", 1, null, 10, null, 1, 1))));

        service.propose(patient(), EXAMPLE, nurse());

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(gateway).structured(captor.capture(), any());
        LlmRequest sent = captor.getValue();
        assertThat(sent.purpose()).isEqualTo(LlmPurpose.STRUCTURING);
        assertThat(sent.tools()).isEmpty();
        assertThat(sent.responseSchema()).isNotNull();
        assertThat(sent.budget()).isEqualTo(Duration.ofSeconds(30));
        String everything = sent.messages().stream().map(LlmMessage::content).reduce("", String::concat);
        assertThat(everything).contains(EXAMPLE);
        // RNF-02: ni el alias, ni la cama, ni la edad, ni el id interno del paciente salen al proveedor.
        assertThat(everything).doesNotContain("P-014").doesNotContain("204-A").doesNotContain(patient().toString());
    }

    @Test
    void aSuccessfulProposalLeavesItsTrace() throws Exception {
        when(gateway.structured(any(), any())).thenReturn(result(ANSWER, false, false,
                List.of(new Attempt("groq", "openai/gpt-oss-120b", 1, null, 1100, null, 300, 80))));

        Proposal proposal = service.propose(patient(), EXAMPLE, nurse());

        AgentRun run = runs.findById(proposal.runId()).orElseThrow();
        assertThat(run.getRunType()).isEqualTo(AgentRun.Type.ESTRUCTURACION);
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.COMPLETADA);
        assertThat(run.getStopReason()).isEqualTo(AgentRun.StopReason.EXITO);
        assertThat(run.getProvider()).isEqualTo("groq");
        assertThat(run.getModel()).isEqualTo("openai/gpt-oss-120b");
        assertThat(run.isFallbackUsed()).isFalse();
        assertThat(run.getTokensIn()).isEqualTo(300);
        assertThat(run.getTokensOut()).isEqualTo(80);
        assertThat(run.getCostUsd()).isEqualByComparingTo("0.000093");
        assertThat(run.getRequestedBy()).isEqualTo(nurse().id());
        assertThat(run.getFinishedAt()).isNotNull();
        List<AgentStep> trace = stepsOf(run.getId());
        assertThat(trace).extracting(AgentStep::getStepType).containsExactly(AgentStep.Type.LLAMADA_LLM, AgentStep.Type.VALIDACION);
        assertThat(trace.get(0).getTokensIn()).isEqualTo(300);
        assertThat(trace.get(0).isError()).isFalse();
    }

    @Test
    void aCorrectedOutputAndAFallbackAreBothVisibleInTheTrace() throws Exception {
        List<Attempt> attempts = List.of(
                new Attempt("groq", "openai/gpt-oss-120b", 1, Kind.UNAVAILABLE, 800, "HTTP 503"),
                new Attempt("gemini", "gemini-3.5-flash-lite", 1, null, 1500, null, 200, 50),
                new Attempt("gemini", "gemini-3.5-flash-lite", 1, null, 1400, null, 260, 60));
        when(gateway.structured(any(), any())).thenReturn(result(ANSWER, true, true, attempts));

        Proposal proposal = service.propose(patient(), EXAMPLE, nurse());

        AgentRun run = runs.findById(proposal.runId()).orElseThrow();
        assertThat(run.isFallbackUsed()).isTrue();
        assertThat(run.getIterations()).isEqualTo(2);
        assertThat(run.getTokensIn()).isEqualTo(460);
        assertThat(stepsOf(run.getId())).extracting(AgentStep::getStepType).containsExactly(AgentStep.Type.LLAMADA_LLM,
                AgentStep.Type.FALLBACK, AgentStep.Type.LLAMADA_LLM, AgentStep.Type.LLAMADA_LLM, AgentStep.Type.VALIDACION,
                AgentStep.Type.VALIDACION);
        assertThat(stepsOf(run.getId()).get(0).isError()).isTrue();
    }

    @Test
    void whenNoProviderAnswersThereIsAnHonestMessageNoEventsAndAFailedRun() {
        when(gateway.structured(any(), any())).thenThrow(new LlmUnavailableException(
                "No se pudo obtener respuesta de la IA. Groq: El servicio de IA recibió demasiadas solicitudes y pidió esperar. Intente de nuevo en unos minutos.",
                List.of(new Attempt("groq", "openai/gpt-oss-120b", 4, Kind.RATE_LIMIT, 200, "HTTP 429"))));

        Proposal proposal = service.propose(patient(), EXAMPLE, nurse());

        assertThat(proposal.events()).isEmpty();
        assertThat(proposal.failure()).startsWith("No se pudo obtener respuesta de la IA.")
                .endsWith("Puede guardar solo el texto o agregar los síntomas a mano.");
        AgentRun run = runs.findById(proposal.runId()).orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AgentRun.Status.FALLIDA);
        assertThat(run.getStopReason()).isEqualTo(AgentRun.StopReason.PROVEEDOR_NO_DISPONIBLE);
        assertThat(run.getErrorMessage()).isNotBlank();
        assertThat(stepsOf(run.getId())).extracting(AgentStep::getStepType).containsExactly(AgentStep.Type.LLAMADA_LLM, AgentStep.Type.ERROR);
    }

    @Test
    void anOutputThatNeverValidatesDegradesToFailureWithItsOwnStopReason() {
        when(gateway.structured(any(), any())).thenThrow(new InvalidOutputException(
                "La IA devolvió una respuesta que no cumple el formato esperado.", "esto no es json",
                List.of("no es JSON válido"), List.of(new Attempt("groq", "m", 1, null, 10, null, 5, 5),
                        new Attempt("groq", "m", 1, null, 10, null, 5, 5))));

        Proposal proposal = service.propose(patient(), EXAMPLE, nurse());

        assertThat(proposal.events()).isEmpty();
        assertThat(proposal.failure()).startsWith("La IA devolvió una respuesta que no cumple el formato esperado.");
        assertThat(runs.findById(proposal.runId()).orElseThrow().getStopReason()).isEqualTo(AgentRun.StopReason.SALIDA_INVALIDA);
    }

    @Test
    void anUnknownPatientIsNotFoundAndTheModelIsNotCalled() {
        assertThatThrownBy(() -> service.propose(UUID.randomUUID(), EXAMPLE, nurse())).isInstanceOf(NotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(gateway);
    }
}
