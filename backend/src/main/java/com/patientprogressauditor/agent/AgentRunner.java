package com.patientprogressauditor.agent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.tools.AgentTool;
import com.patientprogressauditor.agent.tools.ToolException;
import com.patientprogressauditor.entry.EntryRepository;
import com.patientprogressauditor.llm.Attempt;
import com.patientprogressauditor.llm.LlmGateway;
import com.patientprogressauditor.llm.LlmMessage;
import com.patientprogressauditor.llm.LlmPurpose;
import com.patientprogressauditor.llm.LlmRequest;
import com.patientprogressauditor.llm.LlmResponse;
import com.patientprogressauditor.llm.LlmResult;
import com.patientprogressauditor.llm.LlmUnavailableException;
import com.patientprogressauditor.llm.ToolCall;
import com.patientprogressauditor.llm.ToolSpec;
import com.patientprogressauditor.report.MonthlyEligibility;
import com.patientprogressauditor.report.Report;
import com.patientprogressauditor.report.ReportDraftService;
import com.patientprogressauditor.report.ReportRepository;

// El bucle del agente, escrito a mano (sin framework): pide al modelo, ejecuta las herramientas que pide, devuelve los
// resultados y repite hasta que se guarda un borrador valido o se agota un limite. Los CUATRO LIMITES son obligatorios:
//   1. iteraciones (llamadas al modelo)      2. tokens por solicitud      3. tiempo maximo por herramienta
//   4. confirmacion humana: la unica escritura es el borrador en PENDIENTE_REVISION; aprobar no esta a su alcance.
// Cada paso se guarda al ocurrir (agent_steps) para que la pantalla lo muestre en vivo (D24, E11).
@Service
public class AgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentRunner.class);

    // `weekStart` es el lunes de la semana (semanal) o de la PRIMERA de las cuatro semanas (mensual).
    public record Task(Report.Type type, UUID patientId, LocalDate weekStart, UUID requestedBy, String request) {

        public Task(UUID patientId, LocalDate weekStart, UUID requestedBy, String request) {
            this(Report.Type.SEMANAL, patientId, weekStart, requestedBy, request);
        }
    }

    public record Outcome(AgentRun.Status status, AgentRun.StopReason stopReason, UUID reportId, String message) {
    }

    private final LlmGateway gateway;
    private final Map<String, AgentTool> tools;
    private final AgentProperties properties;
    private final AgentRunRepository runs;
    private final AgentStepRepository steps;
    private final ReportDraftService drafts;
    private final ReportContentBuilder contentBuilder;
    private final ReportRepository reports;
    private final EntryRepository entries;
    private final MonthlyEligibility eligibility;
    private final ApprovedWeeklyReports approvedWeekly;
    private final Clock clock;
    private final ObjectMapper mapper;
    // Las herramientas corren aparte para poder cortarlas al pasar su tiempo maximo.
    private final ExecutorService toolPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "agent-tool");
        t.setDaemon(true);
        return t;
    });

    public AgentRunner(LlmGateway gateway, List<AgentTool> tools, AgentProperties properties, AgentRunRepository runs,
            AgentStepRepository steps, ReportDraftService drafts, ReportContentBuilder contentBuilder,
            ReportRepository reports, EntryRepository entries, MonthlyEligibility eligibility, ApprovedWeeklyReports approvedWeekly,
            Clock clock, ObjectMapper mapper) {
        this.gateway = gateway;
        this.tools = tools.stream().collect(Collectors.toMap(AgentTool::name, Function.identity()));
        this.properties = properties;
        this.runs = runs;
        this.steps = steps;
        this.drafts = drafts;
        this.contentBuilder = contentBuilder;
        this.reports = reports;
        this.entries = entries;
        this.eligibility = eligibility;
        this.approvedWeekly = approvedWeekly;
        this.clock = clock;
        this.mapper = mapper;
    }

    // Las herramientas que el modelo puede pedir (sin ninguna de salida ni de aprobacion).
    public List<String> toolNames() {
        return tools.keySet().stream().sorted().toList();
    }

    // Ejecuta la tarea. `run` es la fila de agent_runs que creo quien llama (E11 la crea EN_COLA antes de lanzar el hilo).
    public Outcome run(AgentRun run, Task task) {
        State state = new State(run, task);
        try {
            state.begin();
            String blocked = blockedReason(task);
            if (blocked != null) {
                state.step(AgentStep.Type.ERROR, blocked, true, null, null, null, null);
                return state.fail(AgentRun.Status.FALLIDA, null, blocked);
            }
            if (task.type() == Report.Type.MENSUAL) {
                state.loadMonthly();
            }
            return loop(state);
        } catch (RuntimeException e) {
            // Nada debe dejar una ejecucion «en curso» para siempre.
            log.error("La ejecución {} falló de forma no prevista", run.getId(), e);
            String message = "El agente falló de forma inesperada. Intente de nuevo en unos minutos.";
            try {
                state.step(AgentStep.Type.ERROR, message, true, null, null, null, null);
            } catch (RuntimeException traceFailure) {
                log.error("No se pudo registrar el fallo de la ejecución {} en la traza", run.getId(), traceFailure);
            }
            return state.fail(AgentRun.Status.FALLIDA, null, message);
        }
    }

    // Lo mismo para cualquier tipo de informe: en el mensual decide la regla de las cuatro semanas aprobadas (RN-10).
    public String blockedReason(Task task) {
        return task.type() == Report.Type.MENSUAL
                ? eligibility.evaluate(task.patientId(), task.weekStart()).problem()
                : blockedReason(task.patientId(), task.weekStart());
    }

    // Por que no tiene sentido generar este informe (o null si se puede). Lo comprueba quien pide la ejecucion, antes de
    // crearla, y el bucle otra vez al empezar: entre una cosa y otra puede haber pasado algo.
    public String blockedReason(UUID patientId, LocalDate weekStart) {
        if (entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patientId, weekStart,
                weekStart.plusDays(6)).isEmpty()) {
            return "La semana del " + weekStart + " no tiene registros: no hay nada que informar.";
        }
        boolean current = reports.findByPatientIdOrderByPeriodStartDesc(patientId).stream()
                .anyMatch(r -> r.getReportType() == Report.Type.SEMANAL && r.getPeriodStart().equals(weekStart)
                        && (r.getStatus() == Report.Status.PENDIENTE_REVISION || r.getStatus() == Report.Status.APROBADO));
        return current ? "Ya hay un informe vigente de esa semana." : null;
    }

    private Outcome loop(State s) {
        boolean monthly = s.context.isMonthly();
        // El semanal no ve las herramientas propias del mensual.
        List<ToolSpec> specs = tools.values().stream().filter(t -> monthly || !t.monthlyOnly())
                .map(t -> t.spec(properties.getToolDescriptions())).toList();
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(monthly ? AgentPrompt.systemMonthly(properties.getMaxIterations()) : AgentPrompt.system(properties.getMaxIterations())));
        messages.add(LlmMessage.user(monthly ? AgentPrompt.taskMonthly(s.task.weekStart(), s.task.request())
                : AgentPrompt.task(s.task.weekStart(), s.task.request())));

        for (int iteration = 1; iteration <= properties.getMaxIterations(); iteration++) {
            // Limite de tokens, antes de gastar otra llamada: la siguiente cuesta al menos lo que costo la anterior.
            if (s.tokens() >= properties.getMaxTokens() || (s.lastCallTokens > 0 && s.tokens() + s.lastCallTokens > properties.getMaxTokens())) {
                return s.limit(AgentRun.StopReason.MAX_TOKENS, "Se alcanzó el límite de " + properties.getMaxTokens()
                        + " tokens por solicitud (usados: " + s.tokens() + ") sin que el borrador quedara guardado.");
            }
            s.step(AgentStep.Type.ESTADO, "Consultando al modelo (llamada " + iteration + " de " + properties.getMaxIterations() + ")…",
                    false, null, null, null, null);
            LlmResult result;
            try {
                result = gateway.complete(LlmRequest.withTools(LlmPurpose.AGENT, List.copyOf(messages), specs));
            } catch (LlmUnavailableException e) {
                s.attempts(e.attempts());
                s.step(AgentStep.Type.ERROR, e.getMessage(), true, null, null, null, null);
                return s.fail(AgentRun.Status.FALLIDA, AgentRun.StopReason.PROVEEDOR_NO_DISPONIBLE, e.getMessage());
            }
            s.account(result);
            LlmResponse response = result.response();
            messages.add(LlmMessage.assistant(response.text(), response.toolCalls()));

            if (response.toolCalls().isEmpty()) {
                // Texto sin herramienta: no es un borrador. Se le recuerda como terminar.
                messages.add(LlmMessage.user("Todavía no guardaste el borrador. Termina llamando a save_draft con el informe."));
                continue;
            }
            for (ToolCall call : response.toolCalls()) {
                Outcome stop = s.runTool(call, messages);
                if (stop != null) {
                    return stop;
                }
            }
            if (s.context.savedReport() != null) {
                return s.success();
            }
            if (s.context.rejectedDrafts() >= 2) {
                // Salida que no valida tras un reintento con el error: se degrada a texto plano y se registra (RF-18).
                return s.degrade();
            }
            int left = properties.getMaxIterations() - iteration;
            if (left <= 2 && left > 0) {
                messages.add(LlmMessage.user("Te quedan " + left + " llamadas al modelo. Guarda el borrador con save_draft a más tardar en la última."));
            }
        }
        return s.limit(AgentRun.StopReason.MAX_ITERACIONES, "Se agotó el límite de " + properties.getMaxIterations()
                + " llamadas al modelo sin que el borrador quedara guardado.");
    }

    // El estado de UNA ejecucion: contadores, traza y cierre.
    private final class State {

        final AgentRun run;
        final Task task;
        final AgentRunContext context;
        int seq = 0;
        int calls = 0;
        int tokensIn = 0;
        int tokensOut = 0;
        int lastCallTokens = 0;
        int toolTimeouts = 0;
        String slowTool = "";
        BigDecimal cost = BigDecimal.ZERO;
        boolean costKnown = true;
        String provider;
        String model;
        boolean fallbackUsed;
        String lastDraft;
        List<UUID> weeklyReportIds = List.of();

        State(AgentRun run, Task task) {
            this.run = run;
            this.task = task;
            this.context = task.type() == Report.Type.MENSUAL
                    ? AgentRunContext.monthly(task.patientId(), task.weekStart())
                    : new AgentRunContext(task.patientId(), task.weekStart());
            this.context.setRequest(task.request());
            this.context.setSink((content, citations) -> drafts.saveDraft(origin(), content, citations));
        }

        int tokens() {
            return tokensIn + tokensOut;
        }

        ReportDraftService.Origin origin() {
            return new ReportDraftService.Origin(task.type(), task.patientId(), task.weekStart(), task.requestedBy(), run.getId(),
                    provider, model, weeklyReportIds);
        }

        // El mensual parte de los cuatro informes semanales aprobados: se leen una vez, al empezar, y alimentan la
        // herramienta, el contenido y el enlace monthly_report_weeks.
        void loadMonthly() {
            weeklyReportIds = eligibility.evaluate(task.patientId(), task.weekStart()).weeklyReportIds();
            context.setWeeklyReports(approvedWeekly.load(weeklyReportIds));
        }

        void begin() {
            run.setStatus(AgentRun.Status.EN_CURSO);
            run.setStartedAt(clock.instant());
            runs.save(run);
        }

        void step(AgentStep.Type type, String message, boolean error, String tool, JsonNode params, JsonNode result, Integer millis) {
            AgentStep step = new AgentStep();
            step.setRunId(run.getId());
            step.setSeq(++seq);
            step.setStepType(type);
            step.setToolName(tool);
            step.setMessage(message.length() > 300 ? message.substring(0, 300) : message);
            step.setParams(params);
            step.setResult(result);
            step.setError(error);
            step.setDurationMs(millis);
            steps.save(step);
        }

        void attempts(List<Attempt> attempts) {
            String previous = null;
            for (Attempt a : attempts) {
                if (previous != null && !previous.equals(a.provider())) {
                    step(AgentStep.Type.FALLBACK, "Se pasó del proveedor " + previous + " al de respaldo " + a.provider(), false, null, null, null, null);
                }
                previous = a.provider();
                AgentStep.Type type = AgentStep.Type.LLAMADA_LLM;
                String message = a.provider() + " " + a.model() + ", intento " + a.number() + (a.succeeded() ? "" : " falló: " + a.failure());
                AgentStep step = new AgentStep();
                step.setRunId(run.getId());
                step.setSeq(++seq);
                step.setStepType(type);
                step.setMessage(message.length() > 300 ? message.substring(0, 300) : message);
                step.setError(!a.succeeded());
                step.setDurationMs((int) a.millis());
                step.setTokensIn(a.tokensIn() == 0 ? null : a.tokensIn());
                step.setTokensOut(a.tokensOut() == 0 ? null : a.tokensOut());
                steps.save(step);
            }
        }

        void account(LlmResult result) {
            calls++;
            attempts(result.attempts());
            provider = result.provider();
            model = result.model();
            fallbackUsed = fallbackUsed || result.fallbackUsed();
            tokensIn += result.tokensIn();
            tokensOut += result.tokensOut();
            lastCallTokens = result.tokensIn() + result.tokensOut();
            if (result.costUsd() == null) {
                costKnown = false;
            } else {
                cost = cost.add(result.costUsd());
            }
        }

        // Ejecuta una herramienta con su tiempo maximo. Un uso invalido o un fallo vuelven al modelo como observacion; solo
        // el segundo tiempo agotado detiene la ejecucion. Devuelve el resultado final si hay que parar.
        Outcome runTool(ToolCall call, List<LlmMessage> messages) {
            AgentTool tool = tools.get(call.name());
            long start = System.nanoTime();
            String observation;
            boolean error = false;
            if (tool == null) {
                observation = "Error: la herramienta «" + call.name() + "» no existe. Herramientas disponibles: " + String.join(", ", toolNames()) + ".";
                error = true;
            } else {
                if (tool.writes()) {
                    lastDraft = call.arguments().toString();
                }
                Future<String> future = toolPool.submit(() -> tool.execute(context, call.arguments()));
                try {
                    observation = future.get(properties.getToolTimeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    toolTimeouts++;
                    slowTool = call.name();
                    observation = "Error: la herramienta tardó más de " + properties.getToolTimeout().toSeconds() + " s y se canceló. Pruebe con menos datos.";
                    error = true;
                } catch (ExecutionException e) {
                    error = true;
                    if (e.getCause() instanceof ToolException te) {
                        observation = "Error: " + te.getMessage();
                    } else {
                        log.error("La herramienta {} falló", call.name(), e.getCause());
                        observation = "Error: la herramienta falló por un problema interno; pruebe de otra forma.";
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    observation = "Error: la herramienta fue interrumpida.";
                    error = true;
                }
            }
            int millis = (int) ((System.nanoTime() - start) / 1_000_000);
            step(AgentStep.Type.HERRAMIENTA, describe(call), error, call.name(), call.arguments(), resultNode(observation), millis);
            if (error && "save_draft".equals(call.name()) && observation.startsWith("Error: El borrador NO se guardó")) {
                step(AgentStep.Type.VALIDACION, "El borrador no pasó la validación; se devolvieron los problemas al modelo para que los corrija", true, null, null, null, null);
            }
            messages.add(LlmMessage.tool(call.id(), call.name(), observation));
            if (toolTimeouts >= properties.getMaxToolTimeouts()) {
                return limit(AgentRun.StopReason.TIMEOUT_HERRAMIENTA, "La herramienta «" + slowTool + "» superó el tiempo máximo de "
                        + properties.getToolTimeout().toSeconds() + " s en " + toolTimeouts + " ocasiones.");
            }
            return null;
        }

        JsonNode resultNode(String observation) {
            ObjectNode node = mapper.createObjectNode();
            node.put("chars", observation.length());
            node.put("text", observation.length() > 1500 ? observation.substring(0, 1500) + "…" : observation);
            return node;
        }

        // Lo que ve la persona en el avance: lenguaje claro, no el nombre de la herramienta.
        String describe(ToolCall call) {
            JsonNode a = call.arguments();
            return switch (call.name()) {
                case "list_patient_weeks" -> "Revisando qué semanas tienen registros";
                case "get_week_entries" -> "Leyendo los registros de la semana del " + a.path("week_start").asText("?");
                case "get_week_metrics" -> "Calculando las cifras de la semana del " + a.path("week_start").asText("?");
                case "get_symptom_history" -> "Revisando el historial de " + a.path("symptom").asText("un síntoma") + " en las últimas "
                        + a.path("weeks_back").asText("?") + " semanas";
                case "get_approved_weekly_reports" -> "Leyendo los cuatro informes semanales aprobados del mes";
                case "save_draft" -> "Guardando el borrador del informe";
                default -> "Herramienta " + call.name();
            };
        }

        Outcome success() {
            // El paso va antes del estado final: quien sigue la traza en vivo cierra al ver el estado final (D24).
            step(AgentStep.Type.ESTADO, "Borrador guardado: queda pendiente de revisión por enfermería", false, null, null, null, null);
            finish(AgentRun.Status.COMPLETADA, AgentRun.StopReason.EXITO, null);
            return new Outcome(AgentRun.Status.COMPLETADA, AgentRun.StopReason.EXITO, context.savedReport(), null);
        }

        // Un limite se agoto: se detiene, se deja el informe INCOMPLETO con el motivo y se avisa (RF-16, RF-17).
        Outcome limit(AgentRun.StopReason reason, String message) {
            step(AgentStep.Type.LIMITE, message, true, null, null, null, null);
            UUID report = drafts.saveIncomplete(origin(), message, skeleton(message), null);
            finish(AgentRun.Status.INCOMPLETA, reason, message);
            return new Outcome(AgentRun.Status.INCOMPLETA, reason, report, message);
        }

        Outcome degrade() {
            String message = "El borrador no cumplió el formato tras " + context.rejectedDrafts()
                    + " intentos; se conserva su texto sin validar.";
            step(AgentStep.Type.ERROR, message, true, null, null, null, null);
            UUID report = drafts.saveIncomplete(origin(), message, skeleton(message), plainText());
            finish(AgentRun.Status.INCOMPLETA, AgentRun.StopReason.SALIDA_INVALIDA, message);
            return new Outcome(AgentRun.Status.INCOMPLETA, AgentRun.StopReason.SALIDA_INVALIDA, report, message);
        }

        Outcome fail(AgentRun.Status status, AgentRun.StopReason reason, String message) {
            finish(status, reason, message);
            return new Outcome(status, reason, null, message);
        }

        // Lo que el codigo si sabe aunque el agente no termine: las cifras y lo que falta. Sin afirmaciones del modelo.
        ObjectNode skeleton(String reason) {
            ObjectNode model = mapper.createObjectNode();
            model.put("summary", "El agente no terminó el borrador: " + reason);
            model.putArray("claims");
            model.putArray("changes_vs_previous");
            model.putArray("recurring_patterns");
            return contentBuilder.build(model, context);
        }

        // El borrador que no valido, como texto, marcado como no validado (nunca se presenta como informe).
        String plainText() {
            StringBuilder sb = new StringBuilder("(Sin validar: la salida del modelo no cumplió el formato y no pasó por la revisión del sistema.)\n");
            try {
                JsonNode draft = mapper.readTree(lastDraft == null ? "{}" : lastDraft);
                sb.append(draft.path("summary").asText("")).append('\n');
                draft.path("claims").forEach(c -> sb.append("- ").append(c.path("text").asText("")).append('\n'));
            } catch (JsonProcessingException e) {
                sb.append(lastDraft);
            }
            return sb.toString();
        }

        void finish(AgentRun.Status status, AgentRun.StopReason reason, String message) {
            run.setStatus(status);
            run.setStopReason(reason);
            run.setProvider(provider);
            run.setModel(model);
            run.setFallbackUsed(fallbackUsed);
            run.setIterations(calls);
            run.setTokensIn(tokensIn);
            run.setTokensOut(tokensOut);
            run.setCostUsd(costKnown && calls > 0 ? cost : null);
            run.setErrorMessage(status == AgentRun.Status.COMPLETADA || message == null ? null : message.substring(0, Math.min(500, message.length())));
            run.setFinishedAt(clock.instant());
            runs.save(run);
        }
    }
}
