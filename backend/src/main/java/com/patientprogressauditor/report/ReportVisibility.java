package com.patientprogressauditor.report;

import java.util.List;

import com.patientprogressauditor.session.DemoPrincipal;

// Que estados de informe puede ver cada rol. El medico solo ve APROBADO (RN-01, RF-28); lo decide el servidor,
// no la pantalla. Enfermeria ve todos los estados.
public final class ReportVisibility {

    private static final String DOCTOR = "MEDICO";

    private ReportVisibility() {
    }

    public static List<String> statusesFor(DemoPrincipal principal) {
        if (DOCTOR.equals(principal.role())) {
            return List.of(Report.Status.APROBADO.name());
        }
        return List.of(Report.Status.values()).stream().map(Enum::name).toList();
    }
}
