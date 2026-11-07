package com.patientprogressauditor.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.patientprogressauditor.report.Report.Status;
import com.patientprogressauditor.web.FieldException;

// RF-23: las veinticinco combinaciones de estado, una por una. Solo existen tres transiciones (D29 sumo APROBADO -> ENVIADO).
class ReportTransitionsTest {

    @Test
    void onlyThreeTransitionsExist() {
        for (Status from : Status.values()) {
            for (Status to : Status.values()) {
                boolean expected = (from == Status.PENDIENTE_REVISION && (to == Status.APROBADO || to == Status.RECHAZADO))
                        || (from == Status.APROBADO && to == Status.ENVIADO);
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

    @Test
    void aSentReportIsFinalAndSaysSo() {
        Report report = new Report();
        report.setStatus(Status.ENVIADO);
        for (Status to : Status.values()) {
            assertThat(ReportTransitions.allowed(Status.ENVIADO, to)).isFalse();
        }
        assertThatThrownBy(() -> ReportTransitions.require(report, Status.ENVIADO)).hasMessageContaining("No se puede enviar este informe")
                .hasMessageContaining("ya fue enviado al médico");
        assertThatThrownBy(() -> ReportTransitions.require(report, Status.APROBADO)).hasMessageContaining("ya fue enviado al médico");
        assertThatThrownBy(() -> ReportTransitions.requireEditable(report)).hasMessageContaining("ya fue enviado al médico");
    }

    @Test
    void aPendingReportCannotBeSentAndTheSentenceTellsToApproveFirst() {
        Report report = new Report();
        report.setStatus(Status.PENDIENTE_REVISION);
        assertThatThrownBy(() -> ReportTransitions.require(report, Status.ENVIADO)).hasMessageContaining("todavía no fue aprobado");
        // Un aprobado solo se envia: no se rechaza.
        assertThat(ReportTransitions.allowed(Status.APROBADO, Status.ENVIADO)).isTrue();
        assertThat(ReportTransitions.allowed(Status.APROBADO, Status.RECHAZADO)).isFalse();
    }

    @Test
    void sentCountsAsApprovedAndOccupiesThePeriodButARejectedOneDoesNot() {
        assertThat(Status.ENVIADO.approved()).isTrue();
        assertThat(Status.APROBADO.approved()).isTrue();
        assertThat(Status.PENDIENTE_REVISION.approved()).isFalse();
        assertThat(Status.ENVIADO.current()).isTrue();
        assertThat(Status.RECHAZADO.current()).isFalse();
        assertThat(Status.INCOMPLETO.current()).isFalse();
    }
}
