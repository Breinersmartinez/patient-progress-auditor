package com.patientprogressauditor.entry;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByPatientIdAndEntryDateBetweenOrderByEntryDateAscCreatedAtAsc(
            UUID patientId, LocalDate from, LocalDate to);
}
