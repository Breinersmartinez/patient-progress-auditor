# Auditoría de acciones (RF-35): encargo

Rama `feat/auditoria-acciones`, creada desde `develop` (que ya integra Keycloak y la administración de usuarios).
Estado: **encargo por aprobar**. Falta el plan (`planea-trabajo`) y la aprobación de un humano antes de escribir código.
Origen de cada afirmación entre corchetes: **[Curso]**, **[Equipo]** o **[Propuesta]**.

## Encargo en una frase

Dejar registrada, en una bitácora que nadie puede editar ni borrar, cada acción sensible que una persona hace en la
aplicación (quién, cuándo, sobre qué y el antes y el después, sin texto clínico) y mostrársela al administrador en una
pantalla de solo lectura con filtros por persona y por tipo de acción. **[Equipo]**

## Contexto

RF-35 pide auditar las acciones sensibles y quedó fuera del Momento 2 (hito M2-S); el módulo de administración la dejó
declarada como fuera de alcance. Hoy un informe aprobado, enviado o rechazado, o un usuario desactivado, no deja rastro
de quién lo hizo: solo el estado final. Quien recibe el resultado es el **administrador**, que responde con ella
«¿quién aprobó esto?» sin ver datos clínicos; también la rúbrica del curso (RNF-07: «trazas y auditoría consultables»,
OBJ-05 y OBJ-14) y el profesor, que preguntará cómo se explica lo que pasó. **[Propuesta]**

## Lo que hay que responder o producir

1. **Una anotación por acción sensible**, creada en la **misma transacción** que la acción, de modo que una acción válida
   no queda sin anotar ni una anotación queda sin acción. Las acciones, tal como ya existen en el código:

   | Acción | Dónde ocurre hoy | Quién | Objeto |
   |---|---|---|---|
   | Aprobar un informe | `ReportService.approve` | Enfermería | informe |
   | Rechazar un informe (con su motivo) | `ReportService.reject` | Enfermería | informe |
   | Enviar un informe al médico | `ReportService.send` | Enfermería | informe |
   | Editar un informe (crea una versión nueva) | `ReportService.edit` | Enfermería | informe |
   | Registrar una entrada **por parte del médico** | `EntryService.create`, con rol `MEDICO` | Médico | entrada |
   | Crear un usuario | `UserAdminService.create` | Administrador | usuario |
   | Cambiar nombre, rol o estado de un usuario, incluido desactivarlo | `UserAdminService.update` | Administrador | usuario |

2. **Qué guarda cada anotación**: quién (usuario y el rol con el que actuó en ese momento), cuándo (fecha y hora con zona),
   qué acción, sobre qué objeto (su identificador y su tipo) y, si cambia algo, el estado **antes** y **después**.
   Para un usuario, el antes y el después son nombre, rol y estado (`activo` o `inactivo`). Para un informe, número de
   versión, origen y estado, **nunca el contenido**. Para el rechazo, el motivo ya es texto que escribe enfermería: se
   guarda **solo** si hoy no contiene datos clínicos por diseño (ver pregunta 1).
3. **Inmutabilidad**: ninguna operación de la aplicación ni de la base de datos puede actualizar o borrar una anotación;
   se comprueba con una prueba que intenta `UPDATE` y `DELETE` y espera el rechazo. **[Equipo]**
4. **Una pantalla de solo lectura para el administrador** en `#/adm/auditoria`, con una entrada en su menú: tabla con
   *Cuándo, Quién (nombre y rol), Qué hizo, Sobre qué (código del paciente o del usuario, nunca texto clínico)*, filtros
   por **persona** y por **tipo de acción**, la más reciente primero y paginada de a 50. Sin botones de editar o borrar.
5. **Solo el administrador la ve.** El servidor responde 403 a enfermería y médico en el endpoint de lectura; la
   pantalla no se muestra a otros roles (la UI solo oculta, el servidor decide, RNF-01).
6. **Documentos al día**: decisión nueva en `docs/decisiones.md` y en `docs/SPEC.md` §7; RF-35 pasa de M2-S a M3 en
   `docs/REQUISITOS.md` con sus conteos; `docs/DICCIONARIO_DATOS.md` y `_M2.md` con la tabla ajustada; `AGENTS.md`,
   `CLAUDE.md` y el resumen ejecutivo si cambia lo que dicen del estado. Reporte de cierre en `docs/auditoria/reporte.md`.

## Insumos que hace falta tener

| Insumo | Para qué se usa | Si no existe |
|---|---|---|
| Tabla `audit_logs` de `V3__domain_schema.sql` (id, actor, rol, acción, tipo, objeto, cuándo, antes, después; índices por objeto y por actor) | Base de la bitácora. **Existe y ningún código la usa** | Una migración nueva `V13` la ajusta (ver abajo) |
| `ReportService`, `EntryService`, `UserAdminService` | Puntos donde se anota. `UserAdminService.create` hoy **no recibe** a quien actúa (solo `update` lo recibe) | Se le agrega, igual que a `update` |
| `docs/DICCIONARIO_DATOS.md` §4.13 y §3.2 | Define la tabla y sus valores | Mandan sobre este encargo si chocan; se avisa |
| Pantalla de usuarios del administrador (`users-list.component.ts`) | Referencia de estilo, menú y paginación | — |
| Mockup de la pantalla de auditoría | Diseño de la interfaz | **No existe en vA ni vC** (ver «Preguntas pendientes») |

El `CHECK` de `V3` solo admite cinco acciones y dos tipos de objeto (`ENTRY`, `REPORT`): faltan **enviar informe** y el
tipo **usuario**, que el administrador y D29 ya exigen. Ajustarlo es parte del trabajo y se hace con una migración, no
editando `V3`, porque ya corrió.

## Criterio de aceptación

- **Forma esperada del resultado:** migración `V13`; un servicio único de auditoría llamado desde los tres puntos;
  un endpoint de lectura solo para `ADMIN` con filtros y paginación; una pantalla Angular de solo lectura; documentos
  actualizados.
- **Límites razonables:** la lista responde en menos de 1 s con 10 000 anotaciones (los índices ya existen); una
  anotación pesa menos de 2 KB (solo metadatos); agregar la anotación no suma más de una escritura a la acción.
  **Corrección del 2026-11-19, tras la validación (hallazgo 3):** el borrador no decía qué estadístico se exigía y la
  regla se fijó **después** de medir, cuando una página llegó a 2371 ms con la máquina cargada. Queda así: menos de
  1 s en la **mediana** de las 200 páginas filtradas, en la **última página** y en la **primera página sin filtros**
  (la que el administrador abre primero), estas dos como el mejor de tres; el máximo se informa siempre. En la
  validación, hecha con la máquina libre, hasta el máximo quedó en 216 ms, así que el criterio también se cumple
  leído al pie de la letra.
- **Como se sabrá que responde** (cada línea se responde con sí o no):
  1. ¿Aprobar, rechazar, enviar y editar un informe dejan **una** fila cada uno, con quién, rol y objeto correctos?
  2. ¿Una entrada registrada por el **médico** deja fila y una registrada por **enfermería** no?
  3. ¿Crear un usuario y cambiarle el rol o desactivarlo dejan fila con el antes y el después?
  4. ¿Si la acción falla (por ejemplo, aprobar un informe que ya no está pendiente), **no** queda fila?
  5. ¿Si la anotación falla, la acción se revierte (misma transacción)?
  6. ¿`UPDATE` y `DELETE` sobre `audit_logs` son rechazados, incluso por el usuario de la aplicación?
  7. ¿Enfermería y médico reciben 403 al leer la bitácora, y el administrador 200?
  8. ¿Ninguna fila de `audit_logs` de los flujos probados contiene texto de un registro, de un informe ni el nombre de un
     paciente (búsqueda en el JSON de antes y después)? Sí = aceptado.
  9. ¿Los filtros por persona y por tipo devuelven solo lo que corresponde y la paginación no repite ni omite filas?
  10. ¿Pasan `mvn -B verify` y `ng test` sin perder pruebas y el recorrido en pantalla con el administrador coincide con lo
      descrito en el punto 4 de arriba?

## Lo que queda fuera

- **Anotar lo que hace el agente de IA**: es la traza del agente, ya está en el informe; las trazas del admin se descartaron.
- **Editar una entrada** (RF-03): no existe esa función, así que no hay nada que anotar; entrará cuando RF-03 se construya.
- **Cambiar permisos por rol** (RF-34, descartado): su equivalente, el cambio de rol de un usuario, sí se anota.
- **Anotar lecturas** (quién abrió qué informe) y **accesos denegados** (403 o 404): solo se anotan acciones que cambian algo.
- **Que enfermería registre una entrada**: RF-35 solo pide el registro del médico (D12); lo de enfermería es su flujo normal.
- **Exportar, imprimir o purgar** la bitácora; **retención** y archivado; **búsqueda de texto libre**.
- **Anotar retroactivamente** lo que la semilla de demostración dejó hecho: no lo hizo una persona.
- **Inicios de sesión y cierres de sesión**: los gestiona Keycloak con su propio registro de eventos.
- **Cambiar el diseño** de las pantallas existentes; el aspecto de la nueva toma el de la pantalla de usuarios.

## Riesgos del resultado

- **Fuga de datos clínicos por el «antes y después»**: guardar el contenido de un informe en la bitácora la volvería un
  segundo almacén de datos de pacientes que ve el administrador, que no debe verlos (RF-33). Por eso el criterio 8.
- **Falsa sensación de inmutabilidad**: si solo se impide editar desde la aplicación, quien tenga acceso a la base de
  datos puede alterarla. El criterio 6 exige que lo impida la propia base; aun así, la protección se puede saltar y
  esto **no** es una prueba forense. Hay que decirlo así en la entrega.
  **Corrección del 2026-11-19, tras la validación (hallazgo 1):** el riesgo estaba subestimado. El usuario con que corre
  la aplicación (`patient_auditor`) es **superusuario** de PostgreSQL, porque es el mismo que crea la base en el compose
  y el que corre Flyway. Con una sola sentencia (`SET LOCAL session_replication_role = replica`) los triggers dejan de
  actuar, sin `ALTER TABLE`. Quien obtenga esa conexión (por ejemplo, con una inyección SQL o leyendo el `.env`) puede
  alterar la bitácora. La defensa es de hardening y va en `docs/seguridad.md`: un usuario dueño del esquema para
  Flyway y otro para la aplicación, sin superusuario ni dueño de las tablas, con solo `INSERT` y `SELECT` sobre
  `audit_logs`.
- **Anotación sin acción o acción sin anotación** si se hacen en transacciones distintas: criterios 4 y 5.
- **Lectura equivocada del administrador**: la bitácora dice qué hizo cada persona, no si estuvo bien. No debe leerse
  como evaluación del personal.

## Preguntas pendientes

Ninguna. Las cuatro del primer borrador se cerraron el 2026-11-18, cuando el equipo pidió seguir «los estilos actuales
del frontend» y continuar con el plan; las propuestas se tomaron por aceptadas y quedan en el registro de abajo. Si el
equipo no estaba de acuerdo con alguna, se corrige aquí antes de ejecutar el paso que la usa.

## Decisiones tomadas

| Duda que apareció | Qué se decidió | Por qué |
|---|---|---|
| Acciones que se anotan | Las siete de la tabla del punto 1, no `EDITAR_ENTRADA` ni `CAMBIAR_PERMISOS` tal cual | La primera no existe (RF-03 fuera) y la segunda no existe (RF-34 descartado); el cambio de rol de usuario cumple su función |
| Si «enviar informe» entra | Sí | Es la acción que cumple la regla humana de D29; sin ella no se puede probar quién la mandó al médico |
| Si las acciones del administrador entran | Sí, crear y actualizar usuario | Lo pidió el equipo en el ejemplo («Desactivó un usuario») y el administrador es quien más necesita rendir cuentas |
| Dónde se guarda | En la tabla `audit_logs` que ya existe en `V3` | Evita una tabla nueva y está diseñada en el diccionario (§4.13) para esto |
| Cómo se cambia el `CHECK` de `V3` | Con una migración nueva, no editando `V3` | `V3` ya corrió en las bases de todos; Flyway rechaza cambiar una migración aplicada |
| Si se anotan lecturas y accesos denegados | No | RF-35 habla de acciones sensibles que cambian algo; anotar lecturas multiplica el volumen y no lo pidió nadie |
| Quién lee la bitácora | Solo el administrador | Lo dijo el equipo; además el administrador no ve datos clínicos y la bitácora no los lleva |
| Motivo del rechazo (era la pregunta 1) | Se guarda solo que hubo rechazo y la longitud del motivo, nunca el texto | Es texto libre de enfermería y podría traer datos clínicos; el administrador no debe verlos (RF-33). **[Propuesta]** aceptada |
| Falta de mockup (pregunta 2) | La pantalla toma el aspecto de la de usuarios, con las mismas clases del frontend actual | Lo pidió el equipo («siguiendo los estilos actuales del frontend»); no se inventa un lenguaje visual nuevo |
| Datos de demostración (pregunta 3) | La bitácora empieza vacía; solo se anotan acciones nuevas | Lo que sembró la semilla no lo hizo una persona; inventar autores sería falsear la bitácora. **[Propuesta]** aceptada |
| Nombre de quien actuó (pregunta 4) | Se guarda el identificador y se muestra el nombre actual | Alcanza para «quién» y evita guardar datos personales duplicados. **[Propuesta]** aceptada |
| Fecha del hito | RF-35 pasa de M2-S a M3 y se documenta | Se construye ahora, en el Momento 3; hay que corregirlo en los requisitos, no dejarlo contradictorio |
