package com.patientprogressauditor.llm;

import java.util.Locale;
import java.util.Optional;

// Los formatos en que graban los navegadores actuales (RF-38): Chrome, Edge y Brave en webm/opus, Firefox en ogg/opus (o
// webm) y Safari en mp4/aac. Probados contra Whisper en Groq el 2026-11-05. Cualquier otro se rechaza antes de enviarlo.
public enum AudioFormat {
    WEBM("webm", "audio/webm"),
    OGG("ogg", "audio/ogg"),
    MP4("mp4", "audio/mp4");

    private final String extension;
    private final String mediaType;

    AudioFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    // Del Content-Type que manda el navegador ("audio/webm;codecs=opus") al formato; vacio si no es uno de los tres.
    public static Optional<AudioFormat> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String base = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        for (AudioFormat format : values()) {
            if (format.mediaType.equals(base)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
