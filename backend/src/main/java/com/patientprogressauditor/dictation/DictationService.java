package com.patientprogressauditor.dictation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.patientprogressauditor.agent.AgentRun;
import com.patientprogressauditor.agent.AgentRunRepository;
import com.patientprogressauditor.agent.AgentStep;
import com.patientprogressauditor.agent.AgentStepRepository;
import com.patientprogressauditor.agent.LangfuseTraces;
import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.AudioFormat;
import com.patientprogressauditor.llm.LangfuseExporter;
import com.patientprogressauditor.llm.LlmUnavailableException;
import com.patientprogressauditor.llm.TranscriptionGateway;
import com.patientprogressauditor.patient.PatientRepository;
import com.patientprogressauditor.session.DemoPrincipal;
import com.patientprogressauditor.web.FieldException;
import com.patientprogressauditor.web.NotFoundException;

// Dictado de una nota (RF-38, D14): transcribe el audio y devuelve el texto para que la persona lo revise en la caja del
// chat. NO guarda la entrada (eso lo hace EntryService.create, con origen VOZ) y NO guarda el audio en ninguna parte:
// se lee del cuerpo de la peticion a memoria con tope, se reenvia a Groq y se borra del arreglo al terminar. Ni el audio
// ni el texto transcrito van al registro, a la traza (agent_steps) ni a Langfuse: alli solo quedan duracion, tamano y costo.
@Service
public class DictationService {

    private static final Logger log = LoggerFactory.getLogger(DictationService.class);

    // 120 s de voz grabados por el navegador a baja tasa ocupan menos de 1 MB; el tope deja margen y queda muy por debajo de
    // los 25 MB que acepta Groq. nginx tiene el mismo tope en frontend/nginx.conf.
    public static final int MAX_AUDIO_BYTES = 5 * 1024 * 1024;

    static final String WHAT_NEXT = " Puede escribir la nota.";
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    public record Dictation(String text, Double seconds, String failure, String provider, String model, UUID runId) {
    }

    private final TranscriptionGateway gateway;
    private final PatientRepository patients;
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final LangfuseExporter langfuse;
    private final Clock clock;

    public DictationService(TranscriptionGateway gateway, PatientRepository patients, AgentRunRepository runs,
            AgentStepRepository steps, LangfuseExporter langfuse, Clock clock) {
        this.gateway = gateway;
        this.patients = patients;
        this.runs = runs;
        this.steps = steps;
        this.langfuse = langfuse;
        this.clock = clock;
    }

    // Lee el audio del cuerpo de la peticion, solo en memoria y con tope: nunca mas de MAX_AUDIO_BYTES + 1 bytes.
    public static byte[] readAudio(InputStream body, long declaredLength) throws IOException {
        if (declaredLength > MAX_AUDIO_BYTES) {
            throw tooLarge();
        }
        byte[] audio = body.readNBytes(MAX_AUDIO_BYTES + 1);
        if (audio.length > MAX_AUDIO_BYTES) {
            Arrays.fill(audio, (byte) 0);
            throw tooLarge();
        }
        return audio;
    }

    private static FieldException tooLarge() {
        return new FieldException(HttpStatus.PAYLOAD_TOO_LARGE, "audio",
                "El audio es demasiado grande para transcribirlo. Dicte una nota más corta o escríbala.");
    }

    public Dictation transcribe(UUID patientId, byte[] audio, String contentType, DemoPrincipal principal) {
        try {
            if (!patients.existsById(patientId)) {
                throw new NotFoundException("No encontramos ese paciente.");
            }
            AudioFormat format = AudioFormat.fromContentType(contentType).orElseThrow(() -> new FieldException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "audio",
                    "Este navegador grabó el audio en un formato que no se puede transcribir. Escriba la nota."));
            if (audio.length == 0) {
                throw new FieldException(HttpStatus.BAD_REQUEST, "audio", "No llegó audio. Intente dictar de nuevo.");
            }
            AgentRun run = new AgentRun();
            run.setRunType(AgentRun.Type.TRANSCRIPCION);
            run.setPatientId(patientId);
            run.setRequestedBy(principal.id());
            run.setStartedAt(clock.instant());
            try {
                TranscriptionGateway.Result result = gateway.transcribe(audio, format);
                String text = result.text() == null ? "" : result.text().strip();
                List<String> trace = new ArrayList<>();
                trace.add("Transcripción: " + seconds(result.seconds()) + " de audio (" + audio.length + " bytes), "
                        + text.length() + " caracteres");
                String failure = text.isEmpty() ? "No se reconoció ninguna voz en el audio. Intente de nuevo o escriba la nota." : null;
                finish(run, AgentRun.Status.COMPLETADA, AgentRun.StopReason.EXITO, result.provider(), result.model(),
                        result.attempts(), result.costUsd(), null);
                save(run, result.attempts(), trace, null);
                return new Dictation(failure == null ? text : null, result.seconds(), failure, result.provider(), result.model(), run.getId());
            } catch (LlmUnavailableException e) {
                Attempt last = e.attempts().isEmpty() ? null : e.attempts().get(e.attempts().size() - 1);
                finish(run, AgentRun.Status.FALLIDA, AgentRun.StopReason.PROVEEDOR_NO_DISPONIBLE, last == null ? null : last.provider(),
                        last == null ? null : last.model(), e.attempts(), null, e.getMessage());
                save(run, e.attempts(), List.of(), e.getMessage());
                return new Dictation(null, null, e.getMessage() + WHAT_NEXT, run.getProvider(), run.getModel(), run.getId());
            }
        } finally {
            // El audio no se conserva: ni en disco ni, pasada la peticion, en memoria.
            Arrays.fill(audio, (byte) 0);
        }
    }

    private static String seconds(Double seconds) {
        // En espanol, como la interfaz, y sin depender del idioma de la maquina donde corre el servidor.
        return seconds == null ? "duración desconocida" : String.format(SPANISH, "%.1f s", seconds);
    }

    private void finish(AgentRun run, AgentRun.Status status, AgentRun.StopReason reason, String provider, String model,
            List<Attempt> attempts, BigDecimal cost, String error) {
        run.setStatus(status);
        run.setStopReason(reason);
        run.setProvider(provider);
        run.setModel(model);
        run.setFallbackUsed(false);
        run.setIterations((int) attempts.stream().filter(Attempt::succeeded).count());
        run.setCostUsd(cost);
        run.setErrorMessage(error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        run.setFinishedAt(clock.instant());
    }

    // La traza es parte del requisito, pero un fallo al guardarla no debe impedir que la persona reciba su texto.
    private void save(AgentRun run, List<Attempt> attempts, List<String> notes, String error) {
        try {
            runs.save(run);
            List<AgentStep> rows = new ArrayList<>();
            int seq = 1;
            for (Attempt a : attempts) {
                rows.add(step(run, seq++, AgentStep.Type.LLAMADA_LLM, a.provider() + " " + a.model() + ", intento " + a.number()
                        + (a.succeeded() ? "" : " falló: " + a.failure()), !a.succeeded(), (int) a.millis()));
            }
            for (String note : notes) {
                rows.add(step(run, seq++, AgentStep.Type.ESTADO, note, false, null));
            }
            if (error != null) {
                rows.add(step(run, seq++, AgentStep.Type.ERROR, error, true, null));
            }
            steps.saveAll(rows);
        } catch (RuntimeException e) {
            log.error("No se pudo guardar la traza de la transcripción {}", run.getId(), e);
        }
        // Aunque la traza propia no se haya guardado, la de Langfuse sale: no bloquea ni lanza.
        langfuse.export(LangfuseTraces.of(run, attempts));
    }

    private static AgentStep step(AgentRun run, int seq, AgentStep.Type type, String message, boolean error, Integer millis) {
        AgentStep step = new AgentStep();
        step.setRunId(run.getId());
        step.setSeq(seq);
        step.setStepType(type);
        step.setMessage(message.substring(0, Math.min(message.length(), 300)));
        step.setError(error);
        step.setDurationMs(millis);
        return step;
    }
}
