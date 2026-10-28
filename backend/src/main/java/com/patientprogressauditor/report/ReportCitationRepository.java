package com.patientprogressauditor.report;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCitationRepository extends JpaRepository<ReportCitation, ReportCitation.Key> {

    List<ReportCitation> findByReportVersionId(UUID reportVersionId);
}
