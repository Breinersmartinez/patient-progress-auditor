package com.patientprogressauditor.dictation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.llm.AudioFormat;
import com.patientprogressauditor.llm.LangfuseExporter;
import com.patientprogressauditor.llm.LangfuseRun;
import com.patientprogressauditor.llm.LlmException;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.Transcriber;
import com.patientprogressauditor.llm.Transcription;

// El dictado por voz (RF-38, D14) por la API, con el transcriptor SIMULADO (la prueba decide lo que «oye» Whisper). Lo que se
// comprueba es la condicion del equipo: el audio no queda en ninguna parte (registro, traza, Langfuse ni el propio arreglo),
// y tampoco el texto transcrito, que solo vuelve a la pantalla para revisarlo.
@SpringBootTest(properties = "app.seed.demo=true")
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class DictationApiTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";
    private static final String AUDIO_MARK = "AUDIO-MARCADOR-7f3c";
    private static final String TEXT_MARK = "TEXTO-MARCADOR-91ab";
    private static final String HEARD = "Paciente con náuseas leves por la mañana. " + TEXT_MARK;

    @MockitoBean private Transcriber transcriber;
    @MockitoBean private LangfuseExporter langfuse;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private UUID patient;

    @BeforeEach
    void setUp() {
        patient = jdbc.queryForObject("SELECT id FROM patients WHERE alias = 'P-014'", UUID.class);
        when(transcriber.id()).thenReturn("groq");
        when(transcriber.model()).thenReturn("whisper-large-v3-turbo");
        when(transcriber.keyVariable()).thenReturn("GROQ_API_KEY");
        when(transcriber.configured()).thenReturn(true);
    }

    private static byte[] audio() {
        return ("webm-sintetico-" + AUDIO_MARK).getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions dictate(String user, UUID patientId, String contentType, byte[] body) throws Exception {
        var request = post("/api/patients/" + patientId + "/entries/transcribe").contentType(contentType).content(body);
        if (user != null) {
            request.header("X-Demo-User", user);
        }
        return mockMvc.perform(request);
    }

    private LangfuseRun sent() {
        ArgumentCaptor<LangfuseRun> captor = ArgumentCaptor.forClass(LangfuseRun.class);
        verify(langfuse).export(captor.capture());
        return captor.getValue();
    }

    private List<String> stepMessages(UUID runId) {
        return jdbc.queryForList("SELECT message FROM agent_steps WHERE run_id = ? ORDER BY seq", String.class, runId);
    }

    @Test
    void aDictationReturnsTheTextAndLeavesARunWithItsCostButNeitherTheAudioNorTheText(CapturedOutput output) throws Exception {
        AtomicReference<byte[]> heardAudio = new AtomicReference<>();
        when(transcriber.transcribe(any(), any(), any())).thenAnswer(call -> {
            heardAudio.set(((byte[]) call.getArgument(0)).clone());
            return new Transcription(HEARD, 8.579);
        });
        int entriesBefore = jdbc.queryForObject("SELECT count(*) FROM entries", Integer.class);

        MvcResult result = dictate(NURSE, patient, "audio/webm;codecs=opus", audio())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(HEARD))
                .andExpect(jsonPath("$.failure").doesNotExist())
                .andExpect(jsonPath("$.model").value("whisper-large-v3-turbo"))
                .andReturn();
        UUID runId = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).path("runId").asText());

        // Whisper recibio el audio completo, en el formato que dijo el navegador...
        assertThat(heardAudio.get()).isEqualTo(audio());
        ArgumentCaptor<byte[]> passed = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<AudioFormat> format = ArgumentCaptor.forClass(AudioFormat.class);
        verify(transcriber).transcribe(passed.capture(), format.capture(), any(Duration.class));
        assertThat(format.getValue()).isEqualTo(AudioFormat.WEBM);
        // ...y al terminar la peticion el arreglo quedo borrado: el audio no se conserva ni en memoria.
        assertThat(passed.getValue()).containsOnly((byte) 0);

        JsonNode run = mapper.valueToTree(jdbc.queryForMap(
                "SELECT run_type, status, stop_reason, provider, model, iterations, tokens_in, tokens_out, cost_usd::text AS cost FROM agent_runs WHERE id = ?", runId));
        assertThat(run.path("run_type").asText()).isEqualTo("TRANSCRIPCION");
        assertThat(run.path("status").asText()).isEqualTo("COMPLETADA");
        assertThat(run.path("stop_reason").asText()).isEqualTo("EXITO");
        assertThat(run.path("model").asText()).isEqualTo("whisper-large-v3-turbo");
        assertThat(run.path("iterations").asInt()).isEqualTo(1);
        assertThat(run.path("tokens_in").asInt()).isZero();
        // 0,04 USD por hora × 10 s (el minimo que cobra Groq)
        assertThat(run.path("cost").asText()).isEqualTo("0.000111");
        assertThat(jdbc.queryForObject("SELECT error_message FROM agent_runs WHERE id = ?", String.class, runId)).isNull();

        List<String> trace = stepMessages(runId);
        assertThat(trace).hasSize(2);
        assertThat(trace.get(0)).isEqualTo("groq whisper-large-v3-turbo, intento 1");
        assertThat(trace.get(1)).startsWith("Transcripción: 8,6 s de audio (34 bytes)").contains("caracteres");
        assertThat(String.join("\n", trace)).doesNotContain(AUDIO_MARK, TEXT_MARK, "náuseas");

        LangfuseRun traced = sent();
        assertThat(traced.type()).isEqualTo("TRANSCRIPCION");
        assertThat(traced.costUsd()).isEqualByComparingTo("0.000111");
        assertThat(traced.toString()).doesNotContain(AUDIO_MARK, TEXT_MARK);

        assertThat(output.getAll()).doesNotContain(AUDIO_MARK, TEXT_MARK);
        // Dictar no guarda la entrada: eso pasa al enviarla y confirmarla, como siempre.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entries", Integer.class)).isEqualTo(entriesBefore);
    }

    @Test
    void eachBrowserFormatReachesWhisperAsItself() throws Exception {
        when(transcriber.transcribe(any(), any(), any())).thenReturn(new Transcription("Comió poco.", 3.0));
        for (String type : List.of("audio/webm;codecs=opus", "audio/ogg;codecs=opus", "audio/mp4")) {
            dictate(NURSE, patient, type, audio()).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Comió poco."));
        }
        ArgumentCaptor<AudioFormat> formats = ArgumentCaptor.forClass(AudioFormat.class);
        verify(transcriber, org.mockito.Mockito.times(3)).transcribe(any(), formats.capture(), any());
        assertThat(formats.getAllValues()).containsExactly(AudioFormat.WEBM, AudioFormat.OGG, AudioFormat.MP4);
    }

    @Test
    void whenGroqRejectsTheCallThereIsAnHonestMessageAndTheNoteCanBeWritten(CapturedOutput output) throws Exception {
        when(transcriber.transcribe(any(), any(), any()))
                .thenThrow(new LlmException(Kind.AUTH, "groq", "HTTP 401 {\"error\":\"Invalid API Key\"}"));

        MvcResult result = dictate(NURSE, patient, "audio/webm", audio())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").doesNotExist())
                .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());

        assertThat(body.path("failure").asText()).startsWith("No se pudo transcribir el audio.").endsWith("Puede escribir la nota.")
                .doesNotContain("HTTP", "Invalid");
        UUID runId = UUID.fromString(body.path("runId").asText());
        assertThat(jdbc.queryForObject("SELECT status FROM agent_runs WHERE id = ?", String.class, runId)).isEqualTo("FALLIDA");
        assertThat(jdbc.queryForObject("SELECT stop_reason FROM agent_runs WHERE id = ?", String.class, runId)).isEqualTo("PROVEEDOR_NO_DISPONIBLE");
        assertThat(jdbc.queryForObject("SELECT cost_usd FROM agent_runs WHERE id = ?", java.math.BigDecimal.class, runId)).isNull();
        assertThat(sent().status()).isEqualTo("FALLIDA");
        assertThat(output.getAll()).doesNotContain(AUDIO_MARK);
    }

    @Test
    void withoutTheGroqKeyItSaysSoWithoutCallingAnything() throws Exception {
        when(transcriber.configured()).thenReturn(false);

        dictate(NURSE, patient, "audio/webm", audio())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failure").value("El dictado no está disponible: falta la llave de Groq (GROQ_API_KEY). Puede escribir la nota."));
        verify(transcriber, never()).transcribe(any(), any(), any());
    }

    @Test
    void anAudioWithoutVoiceIsReportedInsteadOfReturningAnEmptyNote() throws Exception {
        when(transcriber.transcribe(any(), any(), any())).thenReturn(new Transcription("  ", 6.0));

        dictate(NURSE, patient, "audio/ogg", audio())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.failure").value("No se reconoció ninguna voz en el audio. Intente de nuevo o escriba la nota."));
    }

    @Test
    void anUnsupportedFormatIsRejectedWithAClearMessageBeforeCallingWhisper() throws Exception {
        dictate(NURSE, patient, "audio/wav", audio())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Este navegador grabó el audio en un formato que no se puede transcribir. Escriba la nota."));
        verify(transcriber, never()).transcribe(any(), any(), any());
    }

    @Test
    void anEmptyAudioIsRejected() throws Exception {
        dictate(NURSE, patient, "audio/webm", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No llegó audio. Intente dictar de nuevo."));
        verify(transcriber, never()).transcribe(any(), any(), any());
    }

    @Test
    void anAudioOverTheLimitIsRejectedWithoutCallingWhisper() throws Exception {
        byte[] tooBig = new byte[DictationService.MAX_AUDIO_BYTES + 1];
        Arrays.fill(tooBig, (byte) 7);

        dictate(NURSE, patient, "audio/webm", tooBig)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("El audio es demasiado grande para transcribirlo. Dicte una nota más corta o escríbala."));
        verify(transcriber, never()).transcribe(any(), any(), any());
    }

    @Test
    void anUnknownPatientIsNotFound() throws Exception {
        dictate(NURSE, UUID.randomUUID(), "audio/webm", audio()).andExpect(status().isNotFound());
        verify(transcriber, never()).transcribe(any(), any(), any());
    }

    @Test
    void whoeverCanRegisterCanDictateAndNobodyElse() throws Exception {
        when(transcriber.transcribe(any(), any(), any())).thenReturn(new Transcription("Durmió bien.", 2.0));

        dictate(DOCTOR, patient, "audio/webm", audio()).andExpect(status().isOk());
        dictate(null, patient, "audio/webm", audio()).andExpect(status().isUnauthorized());
    }
}
