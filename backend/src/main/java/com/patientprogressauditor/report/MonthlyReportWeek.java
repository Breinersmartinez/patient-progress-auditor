package com.patientprogressauditor.report;

import java.io.Serializable;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// De que cuatro informes semanales aprobados sale un mensual (RN-10).
@Entity
@Immutable
@Table(name = "monthly_report_weeks")
@IdClass(MonthlyReportWeek.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportWeek {

    @Id
    @Column(name = "monthly_report_id")
    private UUID monthlyReportId;

    @Id
    @Column(name = "week_no")
    private Short weekNo;

    @Column(name = "weekly_report_id", nullable = false)
    private UUID weeklyReportId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID monthlyReportId;
        private Short weekNo;
    }
}
