package com.patientprogressauditor.llm;

import java.time.Duration;

// El puerto de voz a texto (D14): hoy lo implementa Groq con Whisper; un Whisper local se enchufa aqui sin tocar al resto.
// Recibe el audio en memoria y nunca lo guarda. Un fallo es una LlmException con su categoria, como en el chat.
public interface Transcriber {

    String id();

    String model();

    boolean configured();

    String keyVariable();

    Transcription transcribe(byte[] audio, AudioFormat format, Duration timeout);
}
