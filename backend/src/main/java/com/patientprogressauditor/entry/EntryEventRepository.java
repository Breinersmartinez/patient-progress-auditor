package com.patientprogressauditor.entry;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryEventRepository extends JpaRepository<EntryEvent, UUID> {

    // Un evento con la fecha de su entrada (el evento toma la fecha de la entrada, DICCIONARIO_DATOS_M2).
    interface EventRow {
        UUID getEntryId();

        LocalDate getEntryDate();

        String getSymptomCode();

        String getSymptomLabel();

        default Symptom getSymptom() {
            return Symptom.of(getSymptomCode(), getSymptomLabel());
        }

        Intensity getIntensity();

        Short getOccurrences();
    }

    List<EntryEvent> findByEntryIdIn(Collection<UUID> entryIds);

    @Query("""
            SELECT ev.entryId AS entryId, e.entryDate AS entryDate, ev.symptomCode AS symptomCode, ev.symptomLabel AS symptomLabel,
                   ev.intensity AS intensity, ev.occurrences AS occurrences
            FROM Entry e, EntryEvent ev
            WHERE ev.entryId = e.id AND e.patientId = :patientId AND e.entryDate >= :from AND e.entryDate <= :to
            """)
    List<EventRow> findRows(@Param("patientId") UUID patientId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // ¿Alguna vez se registro ese sintoma a ese paciente? Sirve para decir «0 dias esa semana» solo de un sintoma que existe.
    @Query("""
            SELECT COUNT(ev) > 0 FROM Entry e, EntryEvent ev
            WHERE ev.entryId = e.id AND e.patientId = :patientId AND ev.symptomCode = :code
            """)
    boolean recordedFor(@Param("patientId") UUID patientId, @Param("code") String code);

    @Query("""
            SELECT DISTINCT ev.symptomCode FROM Entry e, EntryEvent ev
            WHERE ev.entryId = e.id AND e.patientId = :patientId AND e.entryDate < :before
            """)
    Set<String> findSymptomCodesBefore(@Param("patientId") UUID patientId, @Param("before") LocalDate before);
}
