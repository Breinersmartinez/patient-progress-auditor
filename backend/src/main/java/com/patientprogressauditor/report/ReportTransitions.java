package com.patientprogressauditor.report;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.patientprogressauditor.report.Report.Status;
import com.patientprogressauditor.web.FieldException;

// Las transiciones del estado de un informe (DICCIONARIO_DATOS_M2 §5.2, RF-23), decididas aqui y no en la pantalla.
// Solo un borrador pendiente de revision se edita, se aprueba o se rechaza; los demas estados son finales: un cambio se
// hace con un informe nuevo.
public final class ReportTransitions {

    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            Status.PENDIENTE_REVISION, Set.of(Status.APROBADO, Status.RECHAZADO),
            Status.INCOMPLETO, Set.of(),
            Status.APROBADO, Set.of(),
            Status.RECHAZADO, Set.of());

    private ReportTransitions() {
    }

    public static boolean allowed(Status from, Status to) {
        return ALLOWED.get(from).contains(to);
    }

    public static boolean editable(Status status) {
        return status == Status.PENDIENTE_REVISION;
    }

    // 409 con una frase que la persona entienda, no el nombre tecnico del estado.
    public static void require(Report report, Status to) {
        if (!allowed(report.getStatus(), to)) {
            throw conflict(report, to == Status.APROBADO ? "aprobar" : "rechazar");
        }
    }

    public static void requireEditable(Report report) {
        if (!editable(report.getStatus())) {
            throw conflict(report, "editar");
        }
    }

    private static FieldException conflict(Report report, String action) {
        String why = switch (report.getStatus()) {
            case INCOMPLETO -> "está incompleto (" + report.getStatusReason() + "). Pida un informe nuevo.";
            case APROBADO -> "ya fue aprobado y no se modifica. Para un cambio, pida un informe nuevo.";
            case RECHAZADO -> "ya fue rechazado. Pida un informe nuevo.";
            default -> "no está pendiente de revisión.";
        };
        return new FieldException(HttpStatus.CONFLICT, "status", "No se puede " + action + " este informe: " + why);
    }
}
