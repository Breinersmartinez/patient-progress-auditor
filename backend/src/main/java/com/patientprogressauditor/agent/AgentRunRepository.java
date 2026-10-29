package com.patientprogressauditor.agent;

import java.util.Collection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByStatusIn(Collection<AgentRun.Status> statuses);

    Optional<AgentRun> findFirstByPatientIdAndPeriodStartAndRunTypeAndStatusIn(UUID patientId, LocalDate periodStart,
            AgentRun.Type runType, Collection<AgentRun.Status> statuses);
}
