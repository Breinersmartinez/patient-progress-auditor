package com.patientprogressauditor.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.LlmResponse.FinishReason;

// Gemini (Google AI Studio), por generateContent. Es el proveedor de respaldo (decision del equipo, 28-oct).
@Component
public class GeminiClient implements LlmClient {

    static final String ID = "gemini";

    // Valor documentado por Google para una llamada a herramienta del historial que no hizo Gemini (por ejemplo, la hizo
    // el proveedor principal antes de caer): sin una firma, Gemini 3 responde 400. Medido el 2026-10-28.
    static final String FOREIGN_SIGNATURE = "skip_thought_signature_validator";

    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"");

    private final LlmProperties.Gemini config;
    private final LlmProperties properties;
    private final ObjectMapper mapper;

    public GeminiClient(LlmProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.config = properties.getGemini();
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Gemini";
    }

    @Override
    public String keyVariable() {
        return "GEMINI_API_KEY";
    }

    @Override
    public boolean configured() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public String model(LlmPurpose purpose) {
        return config.getModel();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (!configured()) {
            throw new LlmException(Kind.MISSING_KEY, ID, "GEMINI_API_KEY no esta definida");
        }
        ObjectNode body = mapper.createObjectNode();
        StringBuilder system = new StringBuilder();
        ArrayNode contents = body.putArray("contents");
        ArrayNode pendingToolResults = null;
        for (LlmMessage m : request.messages()) {
            if (m.role() != LlmMessage.Role.TOOL) {
                pendingToolResults = null;
            }
            switch (m.role()) {
                case SYSTEM -> system.append(system.length() == 0 ? "" : "\n\n").append(m.content());
                case USER -> contents.addObject().put("role", "user").putArray("parts").addObject().put("text", m.content());
                case ASSISTANT -> {
                    ArrayNode parts = contents.addObject().put("role", "model").putArray("parts");
                    if (m.content() != null && !m.content().isBlank()) {
                        parts.addObject().put("text", m.content());
                    }
                    for (ToolCall call : m.toolCalls()) {
                        ObjectNode part = parts.addObject();
                        ObjectNode functionCall = part.putObject("functionCall");
                        functionCall.put("name", call.name());
                        functionCall.set("args", call.arguments());
                        part.put("thoughtSignature", call.signature() != null ? call.signature() : FOREIGN_SIGNATURE);
                    }
                }
                case TOOL -> {
                    // Varias respuestas de herramientas seguidas van juntas en un solo turno del usuario.
                    if (pendingToolResults == null) {
                        pendingToolResults = contents.addObject().put("role", "user").putArray("parts");
                    }
                    ObjectNode response = pendingToolResults.addObject().putObject("functionResponse");
                    response.put("name", m.toolName());
                    response.putObject("response").set("result", asJsonValue(m.content()));
                }
            }
        }
        if (system.length() > 0) {
            body.putObject("systemInstruction").putArray("parts").addObject().put("text", system.toString());
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (ToolSpec tool : request.tools()) {
                ObjectNode declaration = declarations.addObject();
                declaration.put("name", tool.name()).put("description", tool.description());
                declaration.set("parametersJsonSchema", tool.parameters());
            }
        }
        ObjectNode generation = body.putObject("generationConfig");
        generation.put("temperature", properties.getTemperature());
        if (request.responseSchema() != null) {
            generation.put("responseMimeType", "application/json");
            generation.set("responseJsonSchema", request.responseSchema().schema());
        }

        Duration timeout = request.timeout() != null ? request.timeout() : properties.timeoutFor(request.purpose());
        JsonHttp.Result result = JsonHttp.post(ID,
                config.getBaseUrl() + "/models/" + model(request.purpose()) + ":generateContent",
                Map.of("x-goog-api-key", config.getApiKey()), body, timeout, mapper);
        if (result.status() != 200) {
            throw failure(result);
        }
        return parse(result.body());
    }

    // El resultado de una herramienta es JSON casi siempre; si no lo es (un mensaje de error), viaja como texto.
    private JsonNode asJsonValue(String content) {
        try {
            return mapper.readTree(content);
        } catch (JsonProcessingException | RuntimeException e) {
            return mapper.getNodeFactory().textNode(content == null ? "" : content);
        }
    }

    private LlmException failure(JsonHttp.Result result) {
        int status = result.status();
        String body = result.body() == null ? "" : result.body();
        String detail = "HTTP " + status + " " + JsonHttp.snippet(body);
        if (status == 429) {
            return new LlmException(Kind.RATE_LIMIT, ID, retryDelay(result), detail);
        }
        if (status == 401 || status == 403 || (status == 400 && body.contains("API key"))) {
            return new LlmException(Kind.AUTH, ID, detail);
        }
        if (status == 408 || status == 504) {
            return new LlmException(Kind.TIMEOUT, ID, detail);
        }
        if (status >= 400 && status < 500) {
            return new LlmException(Kind.BAD_REQUEST, ID, detail);
        }
        return new LlmException(Kind.UNAVAILABLE, ID, detail);
    }

    // Google puede pedir la espera en el encabezado o en `details[].retryDelay` ("34s").
    private Duration retryDelay(JsonHttp.Result result) {
        Duration header = JsonHttp.retryAfter(result);
        if (header != null) {
            return header;
        }
        Matcher matcher = RETRY_DELAY.matcher(result.body() == null ? "" : result.body());
        return matcher.find() ? Duration.ofMillis((long) (Double.parseDouble(matcher.group(1)) * 1000)) : null;
    }

    private LlmResponse parse(String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode candidate = root.path("candidates").path(0);
            if (candidate.isMissingNode()) {
                throw new LlmException(Kind.INVALID_OUTPUT, ID,
                        "Sin candidatos (bloqueo: " + root.path("promptFeedback").path("blockReason").asText("-") + ")");
            }
            StringBuilder text = new StringBuilder();
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                if (part.has("text")) {
                    text.append(part.path("text").asText());
                }
                if (part.has("functionCall")) {
                    JsonNode call = part.path("functionCall");
                    String id = call.path("id").asText("call_" + calls.size());
                    calls.add(new ToolCall(id, call.path("name").asText(), call.path("args"),
                            part.has("thoughtSignature") ? part.path("thoughtSignature").asText() : null));
                }
            }
            FinishReason finish = !calls.isEmpty() ? FinishReason.TOOL_CALLS : switch (candidate.path("finishReason").asText("")) {
                case "STOP" -> FinishReason.STOP;
                case "MAX_TOKENS" -> FinishReason.LENGTH;
                default -> FinishReason.OTHER;
            };
            JsonNode usage = root.path("usageMetadata");
            int out = usage.path("candidatesTokenCount").asInt(0) + usage.path("thoughtsTokenCount").asInt(0);
            return new LlmResponse(text.length() == 0 ? null : text.toString(), calls, finish,
                    usage.path("promptTokenCount").asInt(0), out);
        } catch (JsonProcessingException e) {
            throw new LlmException(Kind.INVALID_OUTPUT, ID, "Respuesta ilegible: " + JsonHttp.snippet(raw));
        }
    }
}
