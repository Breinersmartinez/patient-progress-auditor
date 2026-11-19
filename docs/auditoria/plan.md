# Auditoría de acciones (RF-35): plan

Rama `feat/auditoria-acciones`. Encargo: [especificacion.md](especificacion.md). Estado: **ejecutado completo el 2026-11-18** (aprobado ese día con la condición del equipo de no romper ninguna funcionalidad actual).
Se ejecuta **un paso por vez** con `ejecuta-plan`: cada paso se comprueba y se cierra por escrito (en esta tabla) antes
del siguiente. No se hace commit ni push sin que el equipo lo pida.

## Supuestos que este plan da por ciertos

- La tabla `audit_logs` de `V3` está vacía en todas las bases y nada la lee (comprobado: solo la nombra `DomainSchemaTest`).
- Las tres clases donde se anota ya corren sus acciones dentro de una transacción de base de datos
  (`ReportService`: `@Transactional` en `edit`, `approve`, `send` y `reject`; `EntryService.create`: `@Transactional`;
  `UserAdminService`: transacción programática). Si un paso descubre lo contrario, se detiene y se avisa.
- `UserAdminService.create` hoy no recibe a quien actúa; hay que pasárselo desde el controlador. No cambia lo que ve el
  cliente de la API.
- Las respuestas del equipo a las cuatro preguntas del encargo quedaron como las propuestas (registradas en el encargo).
- No hay criterio de rendimiento del curso para esta pantalla: se usa el del encargo (1 s con 10 000 filas) y se reporta
  la cifra medida; no se inventa otra.

## Pasos

| # | Paso | Qué produce | Qué se mira al terminarlo |
|---|------|-------------|---------------------------|
| 1 | **Medir la línea base** de lo que ya hay: correr `mvn -B verify` (base desechable) y `ng test`, y listar qué pruebas tocan `app_users` o `audit_logs` | Cifras de partida de las dos suites y la lista de pruebas que limpian usuarios | Las dos suites pasan **antes** de tocar nada; las cifras salen del comando, no se estiman |
| 2 | **Preparar la bitácora**: migración `V13` (amplía acciones y tipos de objeto con `ENVIAR_INFORME`, `CREAR_USUARIO`, `ACTUALIZAR_USUARIO` y `USER`; hace la tabla de solo agregar) y un servicio único `AuditService` que anota en la transacción en curso. Decidir y aplicar cómo limpian las pruebas que hoy borran usuarios | `V13`, `AuditService` y su prueba; el arreglo de limpieza de `UserAdminApiTest` | `UPDATE` y `DELETE` sobre `audit_logs` son rechazados y `INSERT` con las acciones nuevas pasa (criterio 6); `V13` aplica sobre una base con `V12` y datos; `DomainSchemaTest` sigue pasando |
| 3 | **Anotar las siete acciones** en `ReportService` (aprobar, rechazar, enviar, editar), `EntryService.create` (solo si quien registra es médico) y `UserAdminService` (crear, actualizar), guardando solo metadatos | Una anotación por acción, en la misma transacción, sin contenido clínico | Criterios 1 a 5 y 8: una fila por acción correcta; ninguna si la acción falla; la acción se revierte si la anotación falla; búsqueda en el JSON sin texto de registros, informes ni nombre de paciente |
| 4 | **Exponer la lectura**: `GET /api/admin/audit` con filtros por persona y tipo de acción y paginación de 50, solo `ADMIN` | Endpoint, su contrato y sus pruebas | Criterios 7 y 9: 403 a enfermería y médico, 200 al administrador; los filtros devuelven solo lo que corresponde y las páginas no repiten ni omiten filas; con 10 000 filas sembradas por la prueba, tiempo medido contra el límite de 1 s |
| 5 | **Construir la interfaz** `#/adm/auditoria`: entrada «Auditoría» en el menú del administrador (icono `trazas`, que ya existe), servicio Angular y una pantalla de solo lectura con las mismas clases de la pantalla de usuarios (`.cab`, `.barra`, `.cont`, `.tabla-wrap`, `.tabla`, `.inp`, `.btn`, `.aviso`, estado vacío), dos filtros, «N resultados» y «Anterior/Siguiente» | Ruta con `roleGuard('ADMIN')`, `AuditService`, `AuditListComponent` y sus pruebas | `ng test` pasa con pruebas nuevas (carga, vacío, error, filtros, paginación, que enfermería y médico no la ven); captura en escritorio y a 390 px junto a «Usuarios»: mismo encabezado, tabla, botones y tarjetas en móvil |
| 6 | **Dejar los documentos al día**: decisión nueva en `docs/decisiones.md` y SPEC §7; RF-35 de M2-S a M3 en `docs/REQUISITOS.md` con conteos; diccionarios (`DICCIONARIO_DATOS.md` y `_M2.md`); `AGENTS.md`, `CLAUDE.md` y resumen ejecutivo; `docs/auditoria/reporte.md` | Documentos coherentes y el reporte de cierre | Sin referencias a IDs que no existen, sin tablas descuadradas, conteos por hito al día; lo que se afirma del estado coincide con lo construido |
| 7 | **Comprobar el criterio de aceptación** del encargo: `mvn -B verify`, `ng test`, `gitleaks` sobre lo versionado y el recorrido en vivo con el administrador, enfermería y médico (con Keycloak) | Tabla de los 10 criterios, cada uno con sí o no y la evidencia | Las diez líneas contestan sí; las dos suites no pierden pruebas respecto del paso 1; Gitleaks queda en cero |

## Avance

- [x] 1. Medir la línea base (2026-11-18)
  > Backend `mvn -B verify` (base desechable): **538 pruebas, 0 fallos, 0 errores, 15 omitidas**. Frontend `ng test`:
  > **239 de 239**. Medidas hoy sobre `2f1de33`; hasta `6121110` (HEAD de esta rama) solo cambió `.gitleaks.toml`, sin
  > archivos de `backend/` ni `frontend/`, así que la línea base vale sin volver a correrlas.
  > Pruebas que escriben usuarios: `UserAdminApiTest` (crea por API y **borra** con `DELETE FROM app_users` al limpiar),
  > `SessionEndpointsTest` (inserta por SQL, no borra) y `DomainSchemaTest` (solo nombra `audit_logs`). Pruebas que
  > ejercen las acciones a anotar: 11 clases (`ReportsApiTest`, `MonthlyReportsApiTest`, `ReportConcurrencyTest`,
  > `ReportPdfApiTest`, `EntriesApiTest`, `EntryConfirmApiTest`, `DictationApiTest`, `DictationNoDiskTest`,
  > `NotificationsApiTest`, `SessionEndpointsTest`, `UserAdminApiTest`).
  > OJO para el paso 2: solo `UserAdminApiTest` choca con una tabla de solo agregar (borra usuarios que tendrán anotaciones).
- [x] 2. Preparar la bitácora (2026-11-18)
  > `V13__audit_logs_append_only.sql`: amplía las acciones (8, con `ENVIAR_INFORME`, `CREAR_USUARIO` y
  > `ACTUALIZAR_USUARIO`; se conservan las 5 de V3) y los tipos (`USER`); triggers que rechazan `UPDATE`, `DELETE` y
  > `TRUNCATE`; dos índices para la lista (fecha y acción). `audit/AuditService` (con `AuditAction` y `AuditEntity`)
  > escribe con `Propagation.MANDATORY`: sin transacción, falla. Limpieza de pruebas: `support/AuditCleanup` (solo en
  > `src/test`) apaga el trigger durante el `DELETE` y lo vuelve a encender; `UserAdminApiTest` la usa antes de borrar
  > sus usuarios. 7 pruebas nuevas en `AuditServiceTest`.
  > Comprobación: base desechable llevada a V12 con la semilla (119 entradas, 5 informes, `audit_logs` vacía) y luego
  > `mvn -B verify`, que aplicó V13 encima: **545 pruebas, 0 fallos, 0 errores, 15 omitidas** (538 de la línea base + 7).
  > Después: semilla intacta (119 y 5), `audit_logs` en 0 (las pruebas limpian lo suyo), los dos triggers encendidos.
  > OJO: Spring traduce el error del trigger (SQLSTATE 42501) a `BadSqlGrammarException`; el texto de PostgreSQL está en
  > la causa raíz. La primera corrida falló solo por eso, en una aserción de la prueba nueva.
- [x] 3. Anotar las siete acciones (2026-11-18)
  > `ReportService` (aprobar, rechazar, enviar, editar), `EntryService.create` (solo si el rol es `MEDICO`) y
  > `UserAdminService` (crear, actualizar) llaman a `AuditService` dentro de su transacción. Antes y después: estados,
  > números de versión, longitud del motivo del rechazo; de la entrada, fecha, origen, estado y número de eventos; del
  > usuario, nombre, rol y estado (sin correo). En usuarios la anotación va **antes** de llamar a Keycloak. El
  > controlador pasa a `create` quién actúa (para el cliente de la API no cambia nada).
  > Comprobación: 10 pruebas nuevas (`AuditedActionsTest` 5, `AuditRollbackTest` 2, `UserAdminApiTest` +3), criterios
  > 1 a 5 y 8 en sí. Suite completa: **555 pruebas, 0 fallos, 0 errores, 15 omitidas** (545 del paso 2 + 10).
  > Después de la suite quedaban 17 anotaciones de `ReportConcurrencyTest` (sin transacción, borra sus informes):
  > `ReportFixtures.delete` ahora quita también sus anotaciones; repetido, quedan 0.
  > OJO: un `@MockitoSpyBean` sobre `AuditService` envuelve el proxy transaccional y no se puede configurar fuera de
  > una transacción (choca con `MANDATORY`); para simular el fallo se usa `@MockitoBean`.
- [x] 4. Exponer la lectura (2026-11-18)
  > `GET /api/admin/audit?actorId=&action=&page=` (`AuditController` y `AuditQueryService`, solo lectura, `ADMIN`):
  > páginas de 50 con `total`; cada fila con cuándo, quién (nombre y rol), acción, objeto y «sobre qué» resuelto al leer
  > con códigos y fechas (código del paciente, tipo y semana del informe, fecha de la entrada, nombre del usuario), más el
  > antes y el después. Acción desconocida, id mal formado o página negativa: 400 sin traza.
  > Comprobación: `AuditApiTest` (4 pruebas): 401 sin sesión, 403 a enfermería y médico, 200 al administrador; 405 a
  > `POST`, `PUT` y `DELETE`; filtros solos y combinados; 10 000 anotaciones en 200 páginas sin repetir ni omitir filas.
  > Tiempo con 10 000: **mediana 397 ms, máximo 436 ms, última página 379 ms** (límite 1 s). Suite completa:
  > **559 pruebas, 0 fallos, 0 errores, 15 omitidas** (555 + 4); `audit_logs` en 0 al terminar.
  > OJO: la primera versión, con `(? IS NULL OR …)` en el `WHERE` y el total contado con los joins, tuvo una página de
  > 1072 ms aunque el SQL medía unos 20 ms en la base. Se arma el `WHERE` solo con los filtros pedidos y se cuenta sobre
  > `audit_logs` sola. La máquina tenía carga promedio 10: por eso el límite se exige sobre la mediana (como mide el curso
  > la latencia) y sobre la peor página como el mejor de tres; el máximo se informa.
- [x] 5. Construir la interfaz (2026-11-18)
  > `#/adm/auditoria` con `roleGuard('ADMIN')`; «Auditoría» en el menú del administrador (icono `trazas`);
  > `core/audit.service.ts`; `pages/audit-list.component.ts` con las clases de «Usuarios» (`.cab`, `.barra`, `.cont`,
  > `.tabla-wrap`, `.tabla`, `.total`, `.aviso`, estado vacío). Columnas: Cuándo, Quién, Rol, Qué hizo, Sobre qué, Cambio;
  > dos filtros (persona y tipo de acción) y «Anterior/Siguiente» resueltos en el servidor; sin botones ni filas clicables.
  > Estilos nuevos, mínimos: los `select` de la barra usan la flecha de los formularios (no la lupa del buscador),
  > `.paginas`, y «Cuándo» en la lista de columnas que no se parten. Ayudante `fechaHora` (zona de la institución).
  > Comprobación: `ng test` **248 de 248** (239 + 9). En vivo con `./run.sh` (la base real pasó de V12 a V13 con la
  > semilla intacta), sesión de Keycloak como administrador: el menú muestra «Usuarios» y «Auditoría», tres ediciones
  > reales de un usuario aparecen con su antes y después, el filtro por acción funciona y a 390 px no hay desborde.
  > Capturas en escritorio y a 390 px junto a «Usuarios»: mismo encabezado, tarjeta, barra, tabla y tarjetas en móvil.
  > OJO: el menú lateral decide al cargar si va contraído; para una captura de teléfono hay que abrir la página ya en
  > 390 px. Y una edición guardada sin cambios también se anota («Sin cambios»): la persona pulsó guardar.
- [x] 6. Dejar los documentos al día (2026-11-18)
  > SPEC: fila nueva en §3.1 (alcance), en §4 (matriz: «Auditoría», solo `ADMIN` con V) y D33 en §7. REQUISITOS v0.11:
  > RF-35 de M2-S a M3, texto ajustado, nota de cambios con conteos (M3 puro 6 → 7, M2-S 7 → 6; 46 RF; verificado
  > contando la tabla: 29 M2-N + 2 mixtos, 6 M2-S, 7 M3, 2 Opc). Diccionario completo: lista de tablas, §3.2, §4.13 y
  > la fila de `V13`; el del Momento 2, una nota que remite al vigente. `decisiones.md`: «Auditoría de acciones».
  > `AGENTS.md` (migraciones `V1` a `V13`, bullet nuevo), `CLAUDE.md`, README (qué ve el administrador y el médico),
  > resumen ejecutivo (solo la fila de RF-35) y `docs/auditoria/reporte.md`.
  > Comprobación: cada fila tocada tiene el mismo número de columnas que su cabecera; los IDs citados (D12, D26, D29, D32,
  > D33, RF-03, RF-34, RF-35, RN-12) existen en la SPEC o en los requisitos.
  > OJO: la tabla de decisiones de la SPEC tiene 4 columnas y la D32 (rama de administración) traía 5; se corrigió junto
  > con la D33. Y el README, `AGENTS.md` y `CLAUDE.md` decían que el médico o la administración veían «Su rol aún no tiene
  > pantallas», lo que ya no era cierto: el aviso queda solo para un rol desconocido.
- [x] 7. Comprobar el criterio de aceptación (2026-11-18)
  > Suites finales: backend **559, 0 fallos, 15 omitidas**; frontend **248 de 248** (línea base 538 y 239: sin pérdidas).
  > Gitleaks sobre lo que se subiría (477 archivos): **0 hallazgos**. Recorrido en vivo con `./run.sh` y Keycloak:
  > enfermería envió un informe, el médico registró dos entradas y recibió los informes, el administrador vio las 8
  > acciones del día y filtró por persona; enfermería y médico no llegan a la bitácora. En la base real, `UPDATE`,
  > `DELETE` y `TRUNCATE` rechazados y 0 anotaciones con texto clínico. Los 10 criterios en sí (tabla en `reporte.md`);
  > editar y rechazar un informe, la entrada de enfermería, el 403 de la API y la reversión se comprobaron solo con
  > pruebas automáticas.
  > OJO: con la máquina cargada, una página de la prueba de 10 000 llegó a 2371 ms (mediana 384 ms). Y la herramienta del
  > recorrido dejó Chrome vivos que cruzaron sesiones: dos entradas de prueba del médico en P-021 quedaron en la base de
  > demostración (ver `reporte.md`).

## El paso más riesgoso

**El paso 3**, porque toca tres servicios que ya funcionan y la anotación debe ir **dentro** de la misma transacción.
Dos puntos concretos: (a) en `UserAdminService` la transacción llama también a Keycloak, que no es transaccional, así
que si la base confirma y Keycloak falla (o al revés) no debe quedar una anotación de algo que no pasó; hoy el código ya
deshace el alta en Keycloak si el commit falla, y la anotación debe seguir esa misma regla; (b) si se anota con una
transacción propia en vez de la de la acción, se rompe el criterio 5.

Si sale mal: se detiene el paso, no se sigue al 4, y se vuelve al paso 2 a ajustar el servicio (por ejemplo, que
`AuditService` exija una transacción activa y falle si no la hay, para que el error aparezca en pruebas y no en
producción).

## Donde se puede caer

| Paso | Qué puede salir mal | Como se nota |
|------|---------------------|--------------|
| 1 | La base desechable no coincide con la de CI o hay pruebas inestables | Cifras distintas entre dos corridas seguidas |
| 2 | Hacer la tabla de solo agregar rompe la limpieza de `UserAdminApiTest` (borra usuarios que ahora tienen anotaciones, y las anotaciones no se pueden borrar) | `DELETE FROM app_users` falla con violación de clave foránea en esa clase de prueba |
| 2 | Quien es dueño de la tabla puede quitar la protección con `ALTER TABLE` (el usuario de la aplicación hoy lo es) | La prueba pasa pero el riesgo sigue: se declara en `reporte.md` como límite, no se oculta |
| 3 | Se anota algo clínico en el antes y después (contenido del informe, texto de la entrada, nombre del paciente) | La búsqueda del criterio 8 encuentra texto en `before_state` o `after_state` |
| 3 | Se anota una acción que luego se revierte (por ejemplo, aprobar un informe que ya no estaba pendiente) | Fila en `audit_logs` sin cambio de estado en el informe |
| 3 | `EntryService.create` anota también las entradas de enfermería | Fila con rol `ENFERMERIA` tras registrar como enfermería |
| 4 | Lista lenta con muchas filas o paginación inestable cuando dos anotaciones tienen la misma hora | Tiempo sobre 1 s, o una fila repetida u omitida entre páginas |
| 5 | La pantalla se ve distinta a las demás (otro encabezado, botones o tarjetas en móvil) | Captura lado a lado con «Usuarios» en el paso 5 |
| 5 | Se reutiliza `app-listing`, que filtra y pagina en el navegador, con miles de filas | La pantalla trae todo o se vuelve lenta: por eso la paginación y los filtros van en el servidor y la pantalla usa solo sus clases |
| 6 | Un documento queda diciendo que RF-35 es M2-S o que no existe | `grep` de `RF-35` y de «M2-S» en los documentos del paso 6 |
| 7 | El recorrido en vivo muestra una acción sin anotar porque la prueba automática usó otra ruta | Una acción hecha en pantalla que no aparece en la bitácora |

## Qué se entrega al final

- Migración `V13` y `AuditService`; las siete acciones anotadas en la misma transacción; endpoint de lectura solo para el
  administrador con filtros y paginación; pantalla `#/adm/auditoria` con el aspecto de «Usuarios».
- Pruebas nuevas en backend y frontend, sin pérdida respecto de la línea base del paso 1.
- Documentos al día y `docs/auditoria/reporte.md` con la tabla de los 10 criterios, las cifras medidas y los límites
  declarados (la inmutabilidad la garantiza la base de datos pero no es una prueba forense; la bitácora no lleva texto
  clínico).
- Sin commits ni push hechos por mí: el equipo los hace.
