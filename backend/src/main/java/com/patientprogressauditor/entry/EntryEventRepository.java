package com.patientprogressauditor.entry;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryEventRepository extends JpaRepository<EntryEvent, UUID> {

    List<EntryEvent> findByEntryIdIn(Collection<UUID> entryIds);
}
