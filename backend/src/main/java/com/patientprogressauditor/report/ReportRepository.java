package com.patientprogressauditor.report;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByPatientIdOrderByPeriodStartDesc(UUID patientId);

    List<Report> findByStatusInOrderByPeriodStartDescCreatedAtDesc(Collection<Report.Status> statuses);

    List<Report> findByStatusInAndPatientIdOrderByPeriodStartDescCreatedAtDesc(Collection<Report.Status> statuses, UUID patientId);

    // Bloquea la fila hasta el final de la transaccion: aprobar, rechazar y editar no se cruzan.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id = :id")
    Optional<Report> findForUpdate(@Param("id") UUID id);

    // El informe que dejo una ejecucion (borrador o INCOMPLETO), si dejo alguno.
    Optional<Report> findFirstByRunId(UUID runId);

    interface LatestReport {
        UUID getId();

        UUID getPatientId();

        String getReportType();

        LocalDate getPeriodStart();

        LocalDate getPeriodEnd();

        String getStatus();
    }

    // Ultimo informe de cada paciente (periodo mas reciente; a igual periodo, el creado despues) entre los estados dados.
    @Query(value = """
            SELECT DISTINCT ON (patient_id) id, patient_id AS "patientId", report_type AS "reportType",
                   period_start AS "periodStart", period_end AS "periodEnd", status
            FROM reports
            WHERE status IN (:statuses)
            ORDER BY patient_id, period_start DESC, created_at DESC
            """, nativeQuery = true)
    List<LatestReport> findLatestPerPatient(@Param("statuses") Collection<String> statuses);
}
