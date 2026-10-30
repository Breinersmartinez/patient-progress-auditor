package com.patientprogressauditor.report;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyReportWeekRepository extends JpaRepository<MonthlyReportWeek, MonthlyReportWeek.Key> {

    List<MonthlyReportWeek> findByMonthlyReportIdOrderByWeekNo(UUID monthlyReportId);
}
