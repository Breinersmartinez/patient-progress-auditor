package com.patientprogressauditor.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.patientprogressauditor.report.Report.Status;
import com.patientprogressauditor.web.FieldException;

// RF-23: las dieciseis combinaciones de estado, una por una. Solo existen dos transiciones.
class ReportTransitionsTest {

    @Test
    void onlyAPendingReportCanBeApprovedOrRejected() {
        for (Status from : Status.values()) {
            for (Status to : Status.values()) {
                boolean expected = from == Status.PENDIENTE_REVISION && (to == Status.APROBADO || to == Status.RECHAZADO);
                assertThat(ReportTransitions.allowed(from, to)).as("%s -> %s", from, to).isEqualTo(expected);
            }
        }
    }

    @Test
    void onlyAPendingReportIsEditable() {
        for (Status status : Status.values()) {
            assertThat(ReportTransitions.editable(status)).isEqualTo(status == Status.PENDIENTE_REVISION);
        }
    }

    @Test
    void anUndefinedTransitionIsRefusedWithAHumanSentence() {
        Report report = new Report();
        report.setStatus(Status.INCOMPLETO);
        report.setStatusReason("Se agotó el límite de 5 llamadas al modelo.");
        assertThatThrownBy(() -> ReportTransitions.require(report, Status.APROBADO))
                .isInstanceOf(FieldException.class)
                .hasMessageContaining("No se puede aprobar este informe")
                .hasMessageContaining("Se agotó el límite");
        report.setStatus(Status.APROBADO);
        assertThatThrownBy(() -> ReportTransitions.requireEditable(report)).hasMessageContaining("ya fue aprobado");
    }
}
