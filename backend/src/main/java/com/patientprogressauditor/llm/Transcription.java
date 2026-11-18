package com.patientprogressauditor.llm;

// Lo que devuelve el transcriptor: el texto y la duracion del audio en segundos (null si el proveedor no la dio; sin ella
// el costo queda vacio, no en cero).
public record Transcription(String text, Double seconds) {
}
