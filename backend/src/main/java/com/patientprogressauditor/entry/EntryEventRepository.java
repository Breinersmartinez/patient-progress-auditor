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

        Symptom getSymptom();

        Intensity getIntensity();

        Short getOccurrences();
    }

    List<EntryEvent> findByEntryIdIn(Collection<UUID> entryIds);

    @Query("""
            SELECT ev.entryId AS entryId, e.entryDate AS entryDate, ev.symptom AS symptom,
                   ev.intensity AS intensity, ev.occurrences AS occurrences
            FROM Entry e, EntryEvent ev
            WHERE ev.entryId = e.id AND e.patientId = :patientId AND e.entryDate >= :from AND e.entryDate <= :to
            """)
    List<EventRow> findRows(@Param("patientId") UUID patientId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT DISTINCT ev.symptom FROM Entry e, EntryEvent ev
            WHERE ev.entryId = e.id AND e.patientId = :patientId AND e.entryDate < :before
            """)
    Set<Symptom> findSymptomsBefore(@Param("patientId") UUID patientId, @Param("before") LocalDate before);
}
