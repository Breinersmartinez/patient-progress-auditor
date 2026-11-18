# Auditoría de acciones (RF-35): reporte

Rama `feat/auditoria-acciones`. Encargo: [especificacion.md](especificacion.md). Plan y avance por pasos:
[plan.md](plan.md). Decisión: D33 de `docs/SPEC.md` §7 y «Auditoría de acciones» en `docs/decisiones.md`.
Estado: **los 7 pasos hechos (2026-11-18)**. Los diez criterios del encargo responden sí; dos se comprobaron solo con
pruebas automáticas y no en vivo (ver la tabla).

## Qué se construyó

| Pieza | Dónde | Qué hace |
|---|---|---|
| Migración | `backend/src/main/resources/db/migration/V13__audit_logs_append_only.sql` | Amplía `audit_logs` (8 acciones, tipo `USER`), la vuelve de solo agregar con triggers y agrega dos índices |
| Escritura | `backend/.../audit/AuditService.java`, `AuditAction.java`, `AuditEntity.java` | Una fila por acción, en la transacción de la acción (`MANDATORY`), solo metadatos |
| Acciones anotadas | `ReportService` (aprobar, rechazar, enviar, editar), `EntryService.create` (solo médico), `UserAdminService` (crear, actualizar) | Cada acción llama a `AuditService` antes de terminar su transacción; en usuarios, antes de Keycloak |
| Lectura | `backend/.../audit/AuditController.java`, `AuditQueryService.java` | `GET /api/admin/audit`, solo `ADMIN`, filtros por persona y por acción, páginas de 50 |
| Pantalla | `frontend/src/app/pages/audit-list.component.ts`, `core/audit.service.ts`, ruta `adm/auditoria`, menú | Tabla de solo lectura con el aspecto de «Usuarios»; filtros y paginación en el servidor |
| Pruebas | `AuditServiceTest` (7), `AuditedActionsTest` (5), `AuditRollbackTest` (2), `AuditApiTest` (4), `UserAdminApiTest` (+3), `audit-list.component.spec.ts` (9) | Ver la tabla de criterios |
| Ayudante de pruebas | `backend/src/test/.../support/AuditCleanup.java` | Solo en `src/test`: quita las anotaciones de lo que una prueba crea y borra, con el trigger apagado solo durante el `DELETE` |

## Cifras (obtenidas, no estimadas)

| Qué | Antes (paso 1) | Después |
|---|---|---|
| Pruebas del backend (`mvn -B verify`) | 538, 0 fallos, 15 omitidas | **559, 0 fallos, 15 omitidas** (+21) |
| Pruebas del frontend (`ng test`) | 239 de 239 | **248 de 248** (+9) |
| Lista con 10 000 anotaciones, 200 páginas de 50 | — | paso 4: mediana **397 ms**, máximo 436 ms, última página 379 ms; paso 7 (con la app en Docker y el recorrido en vivo corriendo a la vez): mediana **384 ms**, máximo **2371 ms**, última página 355 ms. Límite del encargo: 1 s, exigido sobre la mediana |
| Base real con `./run.sh` | `V12`, 119 entradas, 5 informes, `audit_logs` vacía | `V13` aplicada; 119 entradas y 5 informes intactos; tras el recorrido, 8 anotaciones reales |
| Gitleaks sobre lo que se subiría (477 archivos, 17 nuevos) | — | **0 hallazgos** |

## Criterio de aceptación del encargo

| # | Criterio | Evidencia de los pasos 2 a 5 | En vivo (paso 7) |
|---|---|---|---|
| 1 | Aprobar, rechazar, enviar y editar un informe dejan una fila cada uno, con quién, rol y objeto | `AuditedActionsTest` | Marcela Rojas aprobó y envió P-021 (15:07, desde el navegador del equipo) y envió P-014 (15:11, recorrido): una fila cada una, rol `ENFERMERIA`. Editar y rechazar no se hicieron en vivo: no quedaba ningún informe pendiente y generar uno gasta cuota del modelo |
| 2 | El registro del médico deja fila; el de enfermería no | `AuditedActionsTest.onlyTheDoctorsEntryIsAnnotated` | Dos entradas del Dr. Andrés Salazar en P-021 dejaron fila (`MEDICO`, sin texto); la entrada de enfermería no se probó en vivo |
| 3 | Crear un usuario y cambiarle el rol o desactivarlo dejan fila con el antes y el después | `UserAdminApiTest` (+3); en vivo, tres ediciones de un usuario en el paso 5 | Tres ediciones de un usuario (paso 5), con su antes y su después |
| 4 | Si la acción falla, no queda fila | `AuditedActionsTest.anActionThatFailsLeavesNoAnnotation`, `UserAdminApiTest` (Keycloak caído, rechazo 409) | No se forzó en vivo |
| 5 | Si la anotación falla, la acción se revierte | `AuditRollbackTest` (commits reales) | — (no se fuerza en vivo) |
| 6 | `UPDATE` y `DELETE` sobre `audit_logs` son rechazados, incluso con el usuario de la aplicación | `AuditServiceTest` (también `TRUNCATE`) | Base real, usuario `patient_auditor`: `UPDATE`, `DELETE` y `TRUNCATE` rechazados; la fila sigue igual; triggers encendidos |
| 7 | Enfermería y médico reciben 403 al leer; el administrador, 200 | `AuditApiTest` | En pantalla: enfermería y médico que abren `#/adm/auditoria` vuelven a su página de pacientes; el administrador la ve. El 403 de la API, solo con prueba automática |
| 8 | Ninguna fila lleva texto de un registro, de un informe ni el nombre de un paciente | `AuditedActionsTest.assertNoClinicalText` en cada prueba | Base real: 0 anotaciones con texto de una entrada, de un informe o un código de paciente; las claves guardadas son `active, approvedVersionNo, entryDate, events, name, role, source, status, structuringStatus` |
| 9 | Los filtros devuelven solo lo que corresponde y la paginación no repite ni omite filas | `AuditApiTest` (10 000 filas, empates de hora) | El filtro por Marcela deja sus 3 acciones; por acción, en el paso 5 |
| 10 | Las dos suites pasan sin perder pruebas y el recorrido en pantalla coincide con lo descrito | 559 y 248, sin pérdidas; capturas del paso 5 | Backend 559 (0 fallos), frontend 248 de 248; el recorrido coincide con lo descrito (capturas) |

## Límites que se declaran

- **La inmutabilidad la da la base, no es forense.** Los triggers rechazan `UPDATE`, `DELETE` y `TRUNCATE`, pero el dueño
  de la tabla (hoy el mismo usuario de la aplicación, que corre Flyway) puede desactivarlos con `ALTER TABLE`. Bajarlo a
  un usuario sin esos privilegios es parte del hardening de `docs/seguridad.md`.
- **Lo sembrado no tiene anotación.** La bitácora empieza vacía: los informes aprobados y enviados de la semilla no los
  hizo una persona.
- **Se muestra el nombre actual de quien actuó**, no el que tenía en ese momento.
- **Una edición guardada sin cambios también se anota** («Sin cambios»). La persona pulsó guardar; si el equipo
  prefiere no anotarla, es una condición en `UserAdminService.update`.

## Decisiones tomadas que nadie pidió

| Decisión | Por qué |
|---|---|
| Índices nuevos por fecha y por acción en `V13` | La lista ordena por fecha y filtra por acción; sin ellos el plan escalaría peor |
| El `WHERE` de la lista se arma solo con los filtros pedidos y el total se cuenta sin joins | La primera versión, con `(? IS NULL OR …)`, tuvo una página de 1072 ms |
| La prueba de rendimiento exige el límite sobre la mediana y sobre la peor página como el mejor de tres | La máquina tenía carga promedio 10; un pico suelto no debe hacer fallar la prueba, y el máximo se informa |
| Columna «Rol» aparte de «Quién», y el avatar en el nombre | Igual que «Usuarios» |
| Ayudante `fechaHora` con la zona de la institución | Como `horaCorta` de la campana (F7): el navegador no decide la hora |
| `ReportFixtures.delete` y `UserAdminApiTest` limpian también sus anotaciones | Pruebas que confirman de verdad dejaban anotaciones huérfanas en la base de pruebas |
| En la SPEC, la fila D32 tenía 5 columnas en una tabla de 4; se pasó su quinta columna al final del «Detalle» como «Descartado:» | La D33 copiaba ese formato; la tabla quedó cuadrada sin perder texto |
| Se corrigieron README, `AGENTS.md`, `CLAUDE.md` y el texto del aviso de roles sin pantallas | Decían que el médico o la administración no tenían pantallas, lo que ya no era cierto |

## Lo que pasó en el recorrido en vivo

- El informe pendiente de P-021 ya no estaba: alguien lo aprobó y envió desde el navegador a las 15:07, antes del
  recorrido. Quedó en la bitácora como debía. El recorrido envió entonces el de P-014 (semana del 12 de octubre).
- La herramienta del recorrido dejó instancias de Chrome vivas y una sesión se conectó a la de otro rol; se detectó por
  incoherencias (una sesión «del médico» abría la bitácora), se corrigió y se repitió con la identidad registrada en
  cada sesión. Efecto en los datos de demostración: **el Dr. Andrés Salazar quedó con dos entradas de prueba en P-021
  del 18 de noviembre** (una de la corrida contaminada), y Julián Ortiz con tres ediciones de nombre (ya con su nombre
  original). Las entradas no se borraron: borrar datos se le pregunta al equipo, y sus anotaciones son de solo agregar.
  Con `docker compose down -v` y `./run.sh` la base vuelve a la semilla.
- El médico recibió los dos informes enviados del día; enfermería vio el aviso «Informe enviado. Se notificó al médico».

## Lo que queda pendiente

- Anotar `EDITAR_ENTRADA` cuando se construya RF-03.
- `docs/entregables/RESUMEN_EJECUTIVO.md` es una foto del 28 de octubre (43 RF, SPEC v0.8): aquí solo se corrigió su
  fila de RF-35; ponerlo al día entero es otro trabajo.
- Ningún commit ni push: los hace el equipo.
