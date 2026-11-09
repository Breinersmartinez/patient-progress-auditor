package com.patientprogressauditor.consultation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorConsultationRepository extends JpaRepository<DoctorConsultation, UUID> {

    Optional<DoctorConsultation> findFirstByRunId(UUID runId);

    // Solo el autor la ve: el servicio compara `doctorId` antes de devolver cualquier otra cosa.
    Optional<DoctorConsultation> findByIdAndDoctorId(UUID id, UUID doctorId);
}
