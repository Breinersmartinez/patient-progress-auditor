package com.patientprogressauditor.structuring;

import java.util.List;
import java.util.UUID;

// Lo que ve la pantalla al proponer. Si la IA fallo, `failure` trae un mensaje claro y `events` va vacio: la persona
// puede guardar solo el texto o agregar los sintomas a mano (RF-41). Nada se guardo todavia.
public record Proposal(List<EventProposal> events, String failure, String provider, String model, boolean fallbackUsed,
        UUID runId) {
}
