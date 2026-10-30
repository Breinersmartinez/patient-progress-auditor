package com.patientprogressauditor.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.llm.LlmException.Kind;
import com.patientprogressauditor.llm.LlmResponse.FinishReason;

// Groq, por su API compatible con OpenAI (POST /chat/completions). Es el proveedor principal (decision del equipo, 28-oct).
@Component
public class GroqClient implements LlmClient {

    static final String ID = "groq";

    private final LlmProperties.Groq config;
    private final LlmProperties properties;
    private final ObjectMapper mapper;

    public GroqClient(LlmProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.config = properties.getGroq();
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Groq";
    }

    @Override
    public String keyVariable() {
        return "GROQ_API_KEY";
    }

    @Override
    public boolean configured() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public String model(LlmPurpose purpose) {
        return purpose == LlmPurpose.STRUCTURING ? config.getStructuringModel() : config.getAgentModel();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (!configured()) {
            throw new LlmException(Kind.MISSING_KEY, ID, "GROQ_API_KEY no esta definida");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model(request.purpose()));
        body.put("temperature", properties.getTemperature());
        body.put("reasoning_effort", request.purpose() == LlmPurpose.STRUCTURING
                ? config.getStructuringReasoningEffort() : config.getAgentReasoningEffort());
        body.set("messages", messages(request.messages()));
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec tool : request.tools()) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", tool.name()).put("description", tool.description());
                function.set("parameters", tool.parameters());
            }
            body.put("tool_choice", "auto");
        }
        if (request.responseSchema() != null) {
            ObjectNode format = body.putObject("response_format").put("type", "json_schema");
            ObjectNode schema = format.putObject("json_schema");
            schema.put("name", request.responseSchema().name()).put("strict", true);
            schema.set("schema", request.responseSchema().schema());
        }

        Duration timeout = request.timeout() != null ? request.timeout() : properties.timeoutFor(request.purpose());
        JsonHttp.Result result = JsonHttp.post(ID, config.getBaseUrl() + "/chat/completions",
                Map.of("Authorization", "Bearer " + config.getApiKey()), body, timeout, mapper);
        if (result.status() != 200) {
            throw failure(result);
        }
        return parse(result.body());
    }

    private ArrayNode messages(List<LlmMessage> messages) {
        ArrayNode out = mapper.createArrayNode();
        for (LlmMessage m : messages) {
            ObjectNode node = out.addObject();
            switch (m.role()) {
                case SYSTEM -> node.put("role", "system").put("content", m.content());
                case USER -> node.put("role", "user").put("content", m.content());
                case TOOL -> node.put("role", "tool").put("tool_call_id", m.toolCallId()).put("content", m.content());
                case ASSISTANT -> {
                    node.put("role", "assistant");
                    if (m.content() != null) {
                        node.put("content", m.content());
                    }
                    if (!m.toolCalls().isEmpty()) {
                        ArrayNode calls = node.putArray("tool_calls");
                        for (ToolCall call : m.toolCalls()) {
                            ObjectNode function = calls.addObject().put("id", call.id()).put("type", "function")
                                    .putObject("function");
                            function.put("name", call.name()).put("arguments", call.arguments().toString());
                        }
                    }
                }
            }
        }
        return out;
    }

    private LlmException failure(JsonHttp.Result result) {
        int status = result.status();
        String code = errorCode(result.body());
        String detail = "HTTP " + status + " " + JsonHttp.snippet(result.body());
        if (status == 429) {
            return new LlmException(Kind.RATE_LIMIT, ID, JsonHttp.retryAfter(result), detail);
        }
        if (status == 401 || status == 403) {
            return new LlmException(Kind.AUTH, ID, detail);
        }
        if (status == 408 || status == 504) {
            return new LlmException(Kind.TIMEOUT, ID, detail);
        }
        if (status == 400 && ("json_validate_failed".equals(code) || result.body().contains("failed_generation"))) {
            return new LlmException(Kind.INVALID_OUTPUT, ID, detail);
        }
        if (status >= 400 && status < 500) {
            return new LlmException(Kind.BAD_REQUEST, ID, detail);
        }
        return new LlmException(Kind.UNAVAILABLE, ID, detail);
    }

    private String errorCode(String body) {
        try {
            return mapper.readTree(body).path("error").path("code").asText("");
        } catch (JsonProcessingException | RuntimeException e) {
            return "";
        }
    }

    private LlmResponse parse(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode()) {
                throw new LlmException(Kind.INVALID_OUTPUT, ID, "Sin choices: " + JsonHttp.snippet(body));
            }
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                JsonNode function = call.path("function");
                calls.add(new ToolCall(call.path("id").asText(), function.path("name").asText(),
                        mapper.readTree(function.path("arguments").asText("{}")), null));
            }
            String text = message.path("content").isNull() ? null : message.path("content").asText(null);
            FinishReason finish = switch (root.path("choices").path(0).path("finish_reason").asText("")) {
                case "stop" -> FinishReason.STOP;
                case "tool_calls" -> FinishReason.TOOL_CALLS;
                case "length" -> FinishReason.LENGTH;
                default -> FinishReason.OTHER;
            };
            JsonNode usage = root.path("usage");
            return new LlmResponse(text, calls, finish, usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0));
        } catch (JsonProcessingException e) {
            throw new LlmException(Kind.INVALID_OUTPUT, ID, "Respuesta ilegible: " + JsonHttp.snippet(body));
        }
    }
}
