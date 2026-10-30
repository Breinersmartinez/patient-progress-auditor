package com.patientprogressauditor.report;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {

    List<ReportVersion> findByReportIdOrderByVersionNoAsc(UUID reportId);
}
