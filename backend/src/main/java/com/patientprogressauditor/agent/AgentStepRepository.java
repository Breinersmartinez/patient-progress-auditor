package com.patientprogressauditor.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStepRepository extends JpaRepository<AgentStep, Long> {

    List<AgentStep> findByRunIdOrderBySeqAsc(UUID runId);

    // Los pasos posteriores a uno ya visto: el id crece con cada fila, asi que es el punto de reanudacion del SSE (D24).
    List<AgentStep> findByRunIdAndIdGreaterThanOrderByIdAsc(UUID runId, long afterId);

    Optional<AgentStep> findTopByRunIdOrderBySeqDesc(UUID runId);
}
