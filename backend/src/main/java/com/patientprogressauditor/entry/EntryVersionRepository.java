package com.patientprogressauditor.entry;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryVersionRepository extends JpaRepository<EntryVersion, UUID> {

    List<EntryVersion> findByEntryIdIn(Collection<UUID> entryIds);
}
