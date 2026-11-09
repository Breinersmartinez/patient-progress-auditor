package com.patientprogressauditor.consultation;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

// Guarda el resultado del agente para una consulta del medico (D31, RF-46): nunca un Report. Es el unico camino de
// escritura del agente para una consulta, igual que ReportDraftService lo es para un informe; no hay aprobacion ni
// revision porque la consulta no tiene flujo.
@Service
public class ConsultationDraftService {

    private final DoctorConsultationRepository consultations;

    public ConsultationDraftService(DoctorConsultationRepository consultations) {
        this.consultations = consultations;
    }

    // `periodStart`/`periodEnd` no tienen que ser una semana; `comparisonStart`/`comparisonEnd` van las dos null o
    // las dos con valor.
    public record Origin(UUID doctorId, UUID patientId, LocalDate periodStart, LocalDate periodEnd,
            LocalDate comparisonStart, LocalDate comparisonEnd, UUID runId) {
    }

    @Transactional
    public UUID save(Origin origin, JsonNode content) {
        return save(origin, content, null);
    }

    // Igual que `ReportDraftService.saveIncomplete`: si el agente no termino, el `content` trae solo lo que el codigo
    // pudo calcular (cifras, lo que falta) o, si el modelo escribio algo que no valido, su texto plano sin validar.
    @Transactional
    public UUID saveIncomplete(Origin origin, JsonNode content, String plainText) {
        return save(origin, content, plainText);
    }

    private UUID save(Origin origin, JsonNode content, String plainText) {
        DoctorConsultation consultation = new DoctorConsultation();
        consultation.setDoctorId(origin.doctorId());
        consultation.setPatientId(origin.patientId());
        consultation.setPeriodStart(origin.periodStart());
        consultation.setPeriodEnd(origin.periodEnd());
        consultation.setComparisonStart(origin.comparisonStart());
        consultation.setComparisonEnd(origin.comparisonEnd());
        consultation.setContent(content);
        consultation.setPlainText(plainText);
        consultation.setRunId(origin.runId());
        consultations.saveAndFlush(consultation);
        return consultation.getId();
    }
}
