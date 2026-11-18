package com.patientprogressauditor.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.entry.Entry;
import com.patientprogressauditor.entry.EntryRepository;

// Informes de prueba con contenido real: citan registros del paciente de esa semana y traen las cifras (`metrics`) que el
// codigo guardaria. Se insertan por JDBC para fijar el estado sin pasar por el agente.
// Publica desde la Pieza 5 (notificaciones): NotificationsApiTest tambien necesita un informe APROBADO real para
// probar que enviarlo notifica, y repetir esta logica en otro paquete habria duplicado la validacion de contenido.
public final class ReportFixtures {

    public static final String NURSE = "20000000-0000-0000-0000-000000000002";
    public static final String DOCTOR = "20000000-0000-0000-0000-000000000003";

    private final JdbcTemplate jdbc;
    private final EntryRepository entries;
    private final ObjectMapper mapper;

    public ReportFixtures(JdbcTemplate jdbc, EntryRepository entries, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.entries = entries;
        this.mapper = mapper;
    }

    public UUID patient(String alias) {
        return jdbc.queryForObject("SELECT id FROM patients WHERE alias = ?", UUID.class, alias);
    }

    public List<Entry> weekEntries(UUID patient, LocalDate monday) {
        return entries.findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(patient, monday, monday.plusDays(6));
    }

    // Un informe SEMANAL de la semana `monday` en el estado dado, con su version 1 (MODELO) y sus citas.
    public UUID report(UUID patient, LocalDate monday, String status) {
        return insert(patient, monday, monday.plusDays(6), "SEMANAL", status);
    }

    // Un informe MENSUAL que empieza el `monday` (cuatro semanas), en el estado dado. El contenido es el mismo
    // minimo valido de `report()`: alcanza para probar que enviarlo notifica con reportType MENSUAL (Pieza 5),
    // no el contenido real de un mensual (RF-36), que no hace falta para eso.
    public UUID monthlyReport(UUID patient, LocalDate monday, String status) {
        return insert(patient, monday, monday.plusDays(27), "MENSUAL", status);
    }

    private UUID insert(UUID patient, LocalDate periodStart, LocalDate periodEnd, String type, String status) {
        List<Entry> week = weekEntries(patient, periodStart);
        UUID id = UUID.randomUUID();
        UUID version = UUID.randomUUID();
        boolean sent = status.equals("ENVIADO");
        boolean approved = status.equals("APROBADO") || sent;
        boolean reviewed = approved || status.equals("RECHAZADO");
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO reports (id, patient_id, report_type, period_start, period_end, status, status_reason, requested_by,
                    reviewed_by, reviewed_at, approved_version_no, sent_by, sent_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, patient, type, periodStart, periodEnd, status,
                status.equals("INCOMPLETO") ? "Se agotó el límite de 5 llamadas al modelo." : status.equals("RECHAZADO") ? "No concuerda con los registros." : null,
                UUID.fromString(NURSE), reviewed ? UUID.fromString(NURSE) : null, reviewed ? java.sql.Timestamp.from(now) : null,
                approved ? 1 : null, sent ? UUID.fromString(NURSE) : null, sent ? java.sql.Timestamp.from(now) : null);
        jdbc.update("INSERT INTO report_versions (id, report_id, version_no, origin, content) VALUES (?, ?, 1, 'MODELO', ?::jsonb)",
                version, id, content(periodStart, week.get(0).getId(), week.get(1).getId()).toString());
        jdbc.update("INSERT INTO report_citations (report_version_id, claim_key, entry_id) VALUES (?, 'c1', ?), (?, 'c2', ?)",
                version, week.get(0).getId(), version, week.get(1).getId());
        return id;
    }

    ObjectNode content(LocalDate monday, UUID first, UUID second) {
        ObjectNode content = mapper.createObjectNode();
        content.putObject("period").put("start", monday.toString()).put("end", monday.plusDays(6).toString())
                .put("compared_with", monday.minusDays(7).toString());
        content.put("summary", "La semana tiene registros todos los días y las náuseas aparecen en 3 días.");
        var claims = content.putArray("claims");
        claims.addObject().put("claim_key", "c1").put("text", "Las náuseas se registraron en 3 días de la semana.")
                .put("criterion", "Días con el síntoma esta semana frente a la semana anterior")
                .<ObjectNode>set("entry_ids", mapper.createArrayNode().add(first.toString()))
                .set("figures", mapper.createArrayNode().add(mapper.createObjectNode().put("key", "symptoms.NAUSEAS.days").put("value", 3)));
        claims.addObject().put("claim_key", "c2").put("text", "El ánimo se describe estable en los registros citados.")
                .put("criterion", "Texto de los registros de la semana")
                .<ObjectNode>set("entry_ids", mapper.createArrayNode().add(second.toString()))
                .set("figures", mapper.createArrayNode());
        content.putArray("changes_vs_previous").add("c1");
        content.putArray("recurring_patterns").add("c2");
        content.putObject("metrics").put("week", monday.toString()).put("entries", 7).putObject("symptoms")
                .putObject("NAUSEAS").put("days", 3).put("events", 3);
        content.putArray("insufficient_data");
        content.putNull("out_of_scope");
        return content;
    }

    void delete(UUID patient) {
        jdbc.update("DELETE FROM report_citations WHERE report_version_id IN (SELECT v.id FROM report_versions v JOIN reports r ON r.id = v.report_id WHERE r.patient_id = ?)", patient);
        jdbc.update("DELETE FROM report_versions WHERE report_id IN (SELECT id FROM reports WHERE patient_id = ?)", patient);
        jdbc.update("DELETE FROM reports WHERE patient_id = ?", patient);
    }
}
