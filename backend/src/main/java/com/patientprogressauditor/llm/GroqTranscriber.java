package com.patientprogressauditor.llm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientprogressauditor.llm.LlmException.Kind;

// Whisper en Groq (D14, RF-38): POST multipart a /audio/transcriptions con el idioma fijado en espanol y la respuesta con
// duracion (`verbose_json`). El multipart se arma en memoria y se manda con JsonHttp: el audio no toca el disco ni el
// registro. El detalle de un fallo lleva el estado y un trozo de la respuesta de Groq, nunca el audio.
@Component
public class GroqTranscriber implements Transcriber {

    static final String ID = "groq";

    private final LlmProperties.Groq config;
    private final ObjectMapper mapper;

    public GroqTranscriber(LlmProperties properties, ObjectMapper mapper) {
        this.config = properties.getGroq();
        this.mapper = mapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String model() {
        return config.getTranscriptionModel();
    }

    @Override
    public boolean configured() {
        return config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public String keyVariable() {
        return "GROQ_API_KEY";
    }

    @Override
    public Transcription transcribe(byte[] audio, AudioFormat format, Duration timeout) {
        if (!configured()) {
            throw new LlmException(Kind.MISSING_KEY, ID, "GROQ_API_KEY no esta definida");
        }
        String boundary = "ppa-" + UUID.randomUUID();
        byte[] body = multipart(boundary, audio, format);
        JsonHttp.Result result = JsonHttp.postBytes(ID, config.getBaseUrl() + "/audio/transcriptions",
                Map.of("Authorization", "Bearer " + config.getApiKey()), body, "multipart/form-data; boundary=" + boundary, timeout);
        if (result.status() != 200) {
            throw JsonHttp.httpFailure(ID, result);
        }
        try {
            JsonNode root = mapper.readTree(result.body());
            JsonNode text = root.path("text");
            if (!text.isTextual()) {
                throw new LlmException(Kind.INVALID_OUTPUT, ID, "Sin texto en la respuesta");
            }
            JsonNode duration = root.path("duration");
            return new Transcription(text.asText().strip(), duration.isNumber() ? duration.asDouble() : null);
        } catch (JsonProcessingException e) {
            throw new LlmException(Kind.INVALID_OUTPUT, ID, "La respuesta no es JSON");
        }
    }

    private byte[] multipart(String boundary, byte[] audio, AudioFormat format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.length + 1024);
        field(out, boundary, "model", model());
        field(out, boundary, "language", "es");
        field(out, boundary, "response_format", "verbose_json");
        field(out, boundary, "temperature", "0");
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"dictado." + format.extension()
                + "\"\r\nContent-Type: " + format.mediaType() + "\r\n\r\n");
        out.writeBytes(audio);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
