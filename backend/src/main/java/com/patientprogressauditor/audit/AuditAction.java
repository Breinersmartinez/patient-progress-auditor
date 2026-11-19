package com.patientprogressauditor.audit;

// Acciones sensibles que se anotan (RF-35). Los nombres son los valores de audit_logs.action (V3 y V13).
// EDITAR_ENTRADA existe en la base pero nadie la usa todavia: editar una entrada es RF-03, que no esta construido.
public enum AuditAction {
    APROBAR_INFORME,
    RECHAZAR_INFORME,
    ENVIAR_INFORME,
    EDITAR_INFORME,
    REGISTRAR_ENTRADA_MEDICO,
    CREAR_USUARIO,
    ACTUALIZAR_USUARIO
}
