package com.patientprogressauditor.entry;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    interface LastEntry {
        UUID getPatientId();

        LocalDate getLastDate();
    }

    List<Entry> findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
            UUID patientId, LocalDate from, LocalDate to);

    // El orden lo trae el Pageable (fecha, hora de creacion y id para que sea estable).
    @Query("SELECT e FROM Entry e WHERE e.patientId = :patientId AND e.entryDate >= :from AND e.entryDate <= :to")
    List<Entry> findInRange(@Param("patientId") UUID patientId, @Param("from") LocalDate from,
            @Param("to") LocalDate to, Pageable pageable);

    // Historial de todos los pacientes: mas reciente primero y, el mismo dia, por codigo de paciente (como el mockup).
    @Query("""
            SELECT e FROM Entry e, Patient p WHERE p.id = e.patientId
            ORDER BY e.entryDate DESC, p.alias ASC, e.createdAt DESC, e.id ASC
            """)
    List<Entry> findHistory(Pageable pageable);

    @Query("SELECT e.patientId AS patientId, MAX(e.entryDate) AS lastDate FROM Entry e GROUP BY e.patientId")
    List<LastEntry> findLastEntryDates();

    @Query("SELECT e.patientId AS patientId, MAX(e.entryDate) AS lastDate FROM Entry e WHERE e.patientId IN :ids GROUP BY e.patientId")
    List<LastEntry> findLastEntryDates(@Param("ids") Collection<UUID> ids);
}
