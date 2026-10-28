# REQUISITOS — Patient Progress Auditor

> **Estado:** borrador v0.4 para aprobación · **Fase del ciclo de vida:** 2 — Análisis de requisitos · **Fecha:** 2026-10-28
> **Base:** `docs/SPEC.md` v0.7 (§1–§3, objetivos OBJ-01 a OBJ-16), `AGENTS.md`, las rúbricas del curso *Ingeniería de Aplicaciones con IA* y lo dicho por el profesor en la clase del 24 de octubre de 2026.
>
> **Cambios de la v0.4 (pantalla de registro del equipo, 28 de octubre de 2026):** RF-06 pasa de derivar eventos «de cada entrada» a proponerlos al registrarla, con intensidad y veces, y se resuelve dónde se derivan los eventos (D27, propuesta). Se agregan RF-40 (confirmar, editar, quitar, agregar o descartar antes de guardar) y RF-41 (si la estructuración falla, guardar solo el texto o agregar a mano) al núcleo del Momento 2, RF-42 (texto corregido como versión, con el original intacto) como M2-S, y la regla RN-11 (la IA solo propone). RF-02 aclara que el texto se guarda tal como se escribió y RF-05 que la semilla trae los eventos ya confirmados. Se ajustan RN-07, CU-01, el modelo conceptual y §8. El núcleo del Momento 2 queda en 31 RF, los M2-S en 6, el Momento 3 en 3 y los opcionales en 2; en total son 42 RF.
>
> **Cambios de la v0.3 (decisiones del equipo del 27 de octubre de 2026):** RF-36 (informe mensual) pasa de M3 a núcleo del Momento 2 (D4); un mes son cuatro semanas completas consecutivas y exige el informe semanal aprobado de cada una (nueva RN-10). La autenticación y lo que depende de ella pasan al Momento 3 (D23): RF-31, RF-33 y RF-34 pasan a M3, y RF-32 y RF-39 se reparten entre los dos momentos: los usuarios de demostración, sin credenciales, se siembran desde el Momento 2 y se elige con cuál trabajar (D26, propuesta); las credenciales, el administrador y las cuentas llegan en el Momento 3. RF-05 recoge de RF-39 la carga idempotente del conjunto sintético, condicionada a `APP_SEED_DEMO`. RF-35 (auditoría) sigue en M2-S porque el «quién» es ahora una persona, también para el médico que registra (D12). Se ajustan CU-06, CU-10, RNF-01, RNF-13 y los supuestos de §8. El núcleo del Momento 2 queda en 29 RF, los M2-S en 5, el Momento 3 en 3 y los opcionales en 2; en total siguen siendo 39 RF (RF-32 y RF-39 cuentan en el núcleo por su parte del Momento 2).
>
> **Cambios de la v0.2:** RF-25 (editar el borrador) sube a núcleo, porque la rúbrica del Momento 2 pide que el usuario pueda corregir la fuente y el criterio. Se agregan RF-39 (usuarios de demostración), RNF-13 (arranque en frío sin pasos manuales), RNF-14 (decisiones técnicas justificadas), CU-10 y OBJ-16. Se endurecen RNF-01 (el repositorio es público), RNF-08 (el README lo ejecuta una IA), RNF-11 y RNF-12, y RF-20 se limita al cambio de proveedor por configuración, porque el curso no pide un modo simulado en el proyecto (D16, descartada). El núcleo del Momento 2 pasa de 27 a 29 RF (RF-25 y RF-39); los M2-S bajan de 8 a 7; en total son 39 RF.
>
> **Convenciones:** RF = requisito funcional · RNF = no funcional · RN = regla de negocio · CU = caso de uso. Cada requisito tiene una traza hacia los objetivos de la SPEC y un criterio de aceptación que se responde con sí o no.
>
> **Hito / prioridad:** **M2-N** núcleo del Momento 2 (lo exige la rúbrica o el control humano; sin esto no hay demo) · **M2-S** Momento 2 si hay tiempo · **M3** Momento 3 · **Opc** opcional.
>
> **Origen:** **[Curso]** (la página o lo dicho en clase) · **[Equipo]** (presentación del proyecto y `AGENTS.md`) · **[Propuesta]** (este borrador, por confirmar).
>
> Este documento describe **qué** debe hacer el sistema, no **cómo**. Stack, arquitectura, modelo de datos y herramientas concretas del agente son de la fase de diseño.

---

## 1. Necesidades de los interesados

| Actor | Necesidad | Origen | Requisitos |
|---|---|---|---|
| Enfermería | No releer todos los registros ni redactar el informe a mano cada semana | [Equipo] | RF-08 a RF-13 |
| Enfermería | Ver la evolución frente a semanas anteriores sin que se pierda un síntoma que reaparece | [Equipo] | RF-07, RF-10 |
| Enfermería | Revisar rápido con la evidencia a la mano, corregir y aprobar | [Propuesta] | RF-24 a RF-27 |
| Enfermería | Registrar cada día sin fricción: texto libre, con los síntomas propuestos por la IA para confirmar en un paso y, opcionalmente, voz | [Equipo] | RF-02, RF-06, RF-40, RF-38 |
| Médico | Recibir informes consistentes y aprobados que muestren la evolución en el tiempo | [Equipo] | RF-28, RF-29, RF-36 |
| Médico | Registrar entradas diarias cuando le corresponda | [Equipo] | RF-30 |
| Administrador | Gestionar usuarios y permisos sin acceder a datos clínicos (Momento 3) | [Propuesta] | RF-31 a RF-34 |
| Evaluador del curso | Levantar el repositorio con una IA que solo lee el README, sin pasos manuales; ver en 6 minutos el ciclo completo con el agente funcionando, límites, trazas y fallos forzados; y que el equipo justifique cada decisión técnica | [Curso] | RF-05, RF-16 a RF-22, RNF-08, RNF-13, RNF-14 |

---

## 2. Casos de uso

| CU | Actor | Qué hace | Requisitos |
|---|---|---|---|
| CU-01 Registrar entrada diaria | Enfermería, Médico | Escribe (o dicta) cómo está el paciente hoy, revisa los síntomas que propone la IA y confirma | RF-02, RF-03, RF-06, RF-30, RF-38, RF-40, RF-41, RF-42 |
| CU-02 Generar borrador semanal | Enfermería | Pide el informe de un paciente y una semana; el agente lo redacta | RF-06 a RF-22 |
| CU-03 Revisar y corregir | Enfermería | Lee el borrador, abre la evidencia y lo corrige | RF-24, RF-25 |
| CU-04 Aprobar o rechazar | Enfermería | Decide; el sistema registra quién y cuándo | RF-23, RF-26, RF-27, RF-35 |
| CU-05 Consultar informes aprobados | Médico | Ve la evolución del paciente por período | RF-28, RF-29 |
| CU-06 Gestionar usuarios y permisos (Momento 3) | Administrador | Crea usuarios, ve y edita permisos | RF-31 a RF-34 |
| CU-07 Ver la traza de una ejecución | Enfermería; equipo en la demo | Ve qué consultó el agente y con qué resultado | RF-22 |
| CU-08 Generar informe mensual | Enfermería | Consolida cuatro semanas completas con informe semanal aprobado (RN-10) | RF-36 |
| CU-09 Exportar a PDF | Médico, Enfermería | Descarga un informe aprobado | RF-37 |
| CU-10 Levantar la aplicación en frío | Evaluador del curso, compañero | Clona el repositorio público, sigue el README, ejecuta `./run.sh`, abre la aplicación y elige un usuario de demostración (D26); desde el Momento 3 inicia sesión con sus credenciales | RF-05, RF-39, RNF-08, RNF-13 |

### Flujo crítico: generar → revisar → aprobar → consultar

1. Enfermería elige paciente y período (una semana, o un mes de cuatro semanas aprobadas con RF-36 y RN-10) y pide el borrador (RF-08).
2. El agente consulta con sus herramientas, decide cuántas semanas anteriores revisar y redacta. El código valida el esquema, las citas y las cifras (RF-09 a RF-13).
3. El informe queda `PENDIENTE_REVISION`, o `INCOMPLETO` si se agotó un límite (RF-17).
4. Enfermería lee, abre la evidencia y corrige si hace falta (RF-24, RF-25).
5. Enfermería aprueba (RF-26) o rechaza con motivo (RF-27).
6. El médico ve el informe aprobado (RF-28).

**Excepciones:** semana sin datos suficientes, el informe lo declara (RF-14) · fallo del proveedor, mensaje honesto y respaldo (RF-19) · entrada con instrucciones, se trata como dato (RF-21).

### Flujo de registro de una entrada (CU-01)

1. La persona (enfermería o médico, D12) elige el paciente y escribe el texto del día (RF-02).
2. El servidor manda al LLM el id interno y el texto; el modelo propone los eventos con intensidad y veces y el código valida el esquema (RF-06).
3. La pantalla muestra el texto y los eventos propuestos con un estado visible mientras espera (RF-40).
4. La persona confirma, cambia la intensidad o las veces, quita un evento, agrega uno a mano o descarta (RF-40).
5. Al confirmar se guardan la entrada, con el texto tal como se escribió, y los eventos que quedaron, con quién y cuándo los confirmó (RF-02, RF-40). Descartar no guarda nada.

**Excepciones:** el proveedor falla, se agota el tiempo o la salida no valida tras un reintento: se guarda solo el texto o se agregan los eventos a mano, y la entrada queda «sin estructurar» (RF-41) · el texto trae instrucciones: se trata como dato (RF-21) · el texto no menciona síntomas o los niega: no se proponen eventos (RN-11).

### Flujo de arranque en frío (CU-10)

1. El evaluador clona el repositorio público y entrega el README a su IA.
2. La IA ejecuta los pasos del README: `./run.sh` o `docker compose up --build` (RNF-08, RNF-13).
3. `run.sh` crea `.env`, genera los secretos locales y resuelve la llave del proveedor: entorno o consola.
4. Docker Compose levanta la base de datos, el backend y el frontend; las migraciones y la semilla se aplican solas (RF-05 y los usuarios de demostración, RF-39; desde el Momento 3, también las credenciales).
5. El evaluador abre la URL del README y elige un usuario de demostración (D26); desde el Momento 3 inicia sesión con las credenciales del README (RF-31, RF-39).
6. Genera un informe con la IA real; si falta la llave, la app avisa con claridad (RNF-13).

**Excepciones:** sin terminal y sin llave, no se queda esperando y arranca avisando que falta la llave · llave inválida, mensaje honesto y respaldo (RF-19) · reinicio con la semilla, sin duplicados (RF-05).

---

## 3. Requisitos funcionales

### 3.1 Pacientes y entradas

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-01 | Crear pacientes con un id interno y un alias legible; el LLM solo recibe el id interno | M2-N | OBJ-05, RNF-02 | ¿Existe el paciente creado y las trazas del agente muestran solo el id interno, sin alias? |
| RF-02 | Registrar una entrada diaria en texto libre asociada a paciente, fecha y autor, sin plantilla obligatoria; el texto se guarda tal como se escribió, se estructure o no | M2-N | OBJ-01 | ¿La entrada queda guardada con paciente, fecha, autor y el texto exacto que se escribió? |
| RF-03 | Permitir corregir una entrada conservando la versión anterior y quién la cambió | M2-S | OBJ-05 | Tras editar, ¿se puede ver la versión previa y el autor del cambio? |
| RF-04 | Listar las entradas de un paciente por fecha y filtrar por rango | M2-N | OBJ-04 | Con 10 entradas cargadas desordenadas, ¿salen en orden cronológico y el filtro devuelve solo las del rango? |
| RF-05 | Cargar el conjunto sintético de demostración, con sus eventos ya confirmados y sin llamar al LLM, al primer arranque, de forma idempotente, solo con la semilla activa (`APP_SEED_DEMO=true`) y nunca en el perfil `prod` | M2-N | OBJ-13, RNF-13 | Tras `./run.sh` en una máquina limpia, ¿existen los pacientes, las semanas y los eventos ya confirmados del conjunto sintético, y reiniciar no duplica datos? |

### 3.2 Eventos y métricas (el código cuenta, el LLM interpreta)

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-06 | Proponer, mediante el LLM y al registrar una entrada, los eventos estructurados que el texto dice (tipo, descripción normalizada, intensidad, veces y fecha), validados contra un esquema; lo que el texto no dice queda «sin indicar» o vacío y nunca se inventa. Los eventos se guardan aparte del texto original y enlazados a su entrada (D27) | M2-N | OBJ-02, OBJ-04 | Con «Dolor de cabeza intenso desde la mañana. Tuvo diarrea intensa dos veces. Comió poco.», ¿la propuesta trae dolor de cabeza (intenso), diarrea (intensa, 2 veces) y apetito bajo (sin indicar), valida contra el esquema y no agrega nada que el texto no diga? |
| RF-07 | Calcular en código, a partir de entradas y eventos, las métricas semanales por paciente: días con entrada, días sin entrada, eventos por tipo, días con cada síntoma, variación frente a la semana anterior y síntomas nuevos, persistentes, desaparecidos y reaparecidos | M2-N | OBJ-02, OBJ-04 | En cada caso dorado, ¿las métricas coinciden con el cálculo manual? |
| RF-40 | Mostrar a la persona que registra, antes de guardar, el texto y los eventos propuestos para que los confirme, cambie la intensidad o las veces, quite uno, agregue uno a mano o descarte. Solo lo confirmado se guarda como evento, con quién lo confirmó y cuándo; descartar no guarda nada | M2-N | OBJ-04, OBJ-05 | ¿No se guarda ningún evento hasta que la persona confirma, y tras confirmar quedan solo los que ella dejó, con quién y cuándo los confirmó? |
| RF-41 | Si la estructuración falla (caída o 429 del proveedor, tiempo agotado o salida que no valida tras un reintento), avisar con claridad y permitir guardar la entrada solo como texto o agregar los eventos a mano, sin perder lo escrito; la entrada sin eventos confirmados queda marcada «sin estructurar» y el informe lo declara (RN-08) | M2-N | OBJ-07, OBJ-10 | Con el proveedor caído o la llave inválida, ¿se puede guardar la entrada, queda marcada «sin estructurar» y el usuario no ve un stack trace ni pierde el texto? |
| RF-42 | Proponer, junto a los eventos, una versión corregida del texto (ortografía y puntuación, sin cambiar el contenido) que la persona acepta, edita o ignora; se guarda como versión de la entrada con quién la confirmó, reutilizando el versionado de RF-03, y el original queda intacto y es el que citan los informes (RN-05, RN-07) | M2-S | OBJ-05, OBJ-06 | Tras confirmar, ¿existen el texto original tal como se escribió y la versión corregida con su autor, y el informe cita el original? |

### 3.3 Agente e informe

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-08 | Generar un borrador de informe semanal para un paciente y una semana, a solicitud de enfermería | M2-N | OBJ-01 | Al pedirlo, ¿se crea un informe en `PENDIENTE_REVISION` para ese paciente y semana? |
| RF-09 | Dar al agente acceso a los datos solo mediante herramientas autorizadas de lectura y métricas | M2-N | OBJ-05, OBJ-09 | ¿Todas las llamadas de una traza pertenecen al inventario de herramientas y no hay acceso directo a la BD? |
| RF-10 | Dejar que el agente decida qué consultar y cuántas semanas anteriores revisar (dentro de un tope), y que profundice en un síntoma | M2-N | OBJ-01, OBJ-02, OBJ-12 | En los casos con patrón recurrente, ¿la traza muestra consultas a semanas más antiguas que en los casos sin patrón? |
| RF-11 | Producir el informe con estructura fija validada por esquema: resumen, métricas, cambios frente a la semana anterior, patrones recurrentes, datos insuficientes y afirmaciones con fuente | M2-N | OBJ-01 | ¿Cada informe generado valida contra el esquema? |
| RF-12 | Hacer que cada afirmación cite al menos una entrada (id y fecha) y el criterio de comparación | M2-N | OBJ-06 | ¿Un validador automático confirma que cada cita existe y pertenece al paciente y al período? |
| RF-13 | Tomar las cifras del informe del módulo de métricas; si una cifra no coincide, rechazar el informe y reintentar | M2-N | OBJ-04 | Si se altera a propósito una cifra en la salida del modelo, ¿se detecta y se rechaza? |
| RF-14 | Declarar en el informe cuando faltan registros, sin rellenar ni inventar | M2-N | OBJ-07 | En el caso con una semana sin entradas, ¿el informe declara información insuficiente? |
| RF-15 | Omitir diagnósticos y recomendaciones de tratamiento o dosis aunque la solicitud o las entradas los pidan, e indicar que está fuera de su alcance | M2-N | OBJ-08 | En el caso de rechazo de la suite, ¿el informe omite el diagnóstico y lo indica? |
| RF-16 | Operar con los cuatro límites del agente: iteraciones, tokens por solicitud, confirmación humana ante escrituras irreversibles y timeout por herramienta | M2-N | OBJ-09 | ¿Se puede forzar cada límite en la demo y el agente se detiene y avisa? |
| RF-17 | Aplicar un criterio de parada explícito (informe válido con fuente por afirmación); si un límite se agota, dejar el informe `INCOMPLETO` con el motivo | M2-N | OBJ-09 | Un caso forzado a agotar iteraciones, ¿termina en `INCOMPLETO` con motivo en vez de seguir intentando? |
| RF-18 | Validar la salida del modelo contra el esquema; si falla, reintentar una vez con el error; si vuelve a fallar, degradar a texto plano y registrar | M2-N | OBJ-10 | Una salida inválida forzada, ¿produce reintento y luego degradación sin error visible al usuario? |
| RF-19 | Ante timeout, 429 o caída del proveedor, reintentar con espera creciente, usar el proveedor de respaldo y mostrar un mensaje honesto, sin stack trace | M2-N | OBJ-10 | Cada uno de los tres fallos forzados, ¿muestra un mensaje claro y no un stack trace? |
| RF-20 | Permitir cambiar el proveedor del LLM por configuración y mostrarlo en vivo | M2-N | OBJ-10 | ¿Cambiar la variable de configuración cambia de proveedor sin tocar código y se puede mostrar en vivo? |
| RF-21 | Tratar el texto de las entradas como dato y no como instrucción, y no dar al agente herramientas de salida (internet, correo, mensajes) | M2-N | OBJ-14, RNF-03 | ¿Una entrada con instrucciones maliciosas no altera el comportamiento del agente, y su inventario no incluye ninguna salida? |
| RF-22 | Registrar la traza de cada ejecución (herramienta, parámetros, resultado, ms, tokens) y mostrarla en la app | M2-N | OBJ-11 | ¿Se ve en vivo la traza de una ejecución con esos cinco datos? |

### 3.4 Revisión y aprobación

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-23 | Manejar los estados del informe `PENDIENTE_REVISION`, `INCOMPLETO`, `APROBADO` y `RECHAZADO`, con transiciones validadas en el servidor | M2-N | OBJ-05 | ¿Una transición no definida es rechazada por el servidor? |
| RF-24 | Mostrar a enfermería el informe en lenguaje claro y, a un clic, la evidencia: entradas citadas, criterio de comparación y llamadas de herramientas | M2-N | OBJ-06 | ¿Desde el informe se llega a cada entrada citada en un clic? |
| RF-25 | Permitir a enfermería editar el borrador antes de aprobar, conservando la versión original del modelo y las ediciones | M2-N | OBJ-06 | Tras editar, ¿existen la versión del modelo y la editada? |
| RF-26 | Permitir aprobar un informe solo al rol enfermería y registrar quién y cuándo | M2-N | OBJ-05 | ¿Un usuario médico recibe rechazo al aprobar, y el informe aprobado muestra aprobador y fecha? |
| RF-27 | Permitir a enfermería rechazar un informe con motivo y pedir uno nuevo | M2-S | OBJ-05 | ¿El rechazo guarda el motivo y permite generar un nuevo borrador? |

### 3.5 Consulta del médico

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-28 | Permitir al médico listar pacientes y consultar los informes `APROBADO` por paciente y período, del más reciente al más antiguo | M2-N | OBJ-05 | ¿Un informe pendiente, incompleto o rechazado NO aparece para el médico? |
| RF-29 | Mostrar al médico, en un informe aprobado, la evidencia que lo respalda | M2-S | OBJ-06 | ¿Desde un informe aprobado el médico llega a las entradas citadas? |
| RF-30 | Permitir al médico registrar entradas diarias (supuesto D12) | M2-S | OBJ-15 | ¿Un usuario médico crea una entrada y queda con su autoría? |

### 3.6 Usuarios, roles y auditoría

> En el Momento 2 no hay autenticación (D23): los usuarios de demostración existen desde el arranque, sin credenciales, y la persona elige con cuál trabaja; el servidor usa su rol y su identidad para separar funciones y para registrar quién hizo cada acción (D26, propuesta). Las credenciales, el inicio de sesión, la administración de usuarios y el tablero de permisos son del Momento 3.

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-31 | Autenticar con usuario y contraseña y usar un token con expiración | M3 | OBJ-15, RNF-01 | Sin token válido, ¿el servidor rechaza cualquier operación? |
| RF-32 | Manejar los roles ENFERMERIA, MEDICO y ADMIN, más un rol técnico AGENTE, con permisos por módulo y acción según la matriz de la SPEC §4. En M2 se aplican con el rol del usuario de demostración elegido (D26), solo con ENFERMERIA, MEDICO y AGENTE; ADMIN y las cuentas con credenciales llegan en M3 | M2-N (usuario elegido) · M3 (cuentas) | OBJ-15 | ¿Cada rol puede hacer exactamente lo que dice la matriz y nada más? |
| RF-33 | Permitir al administrador crear, actualizar y desactivar usuarios, sin leer datos clínicos | M3 | OBJ-15 | ¿Un administrador recibe rechazo al consultar entradas o informes? |
| RF-34 | Mostrar el tablero de permisos por rol (primero lectura, luego edición), con el permiso Aprobar fijo para enfermería | M3 | OBJ-15 | ¿El tablero muestra la matriz real y rechaza asignar Aprobar a otro rol? |
| RF-35 | Registrar en auditoría las acciones sensibles: aprobar, rechazar, editar entradas o informes, registrar entradas por parte del médico (D12) y, en el Momento 3, cambiar permisos (quién, cuándo, antes y después). El «quién» es el usuario de demostración elegido en el Momento 2 y el usuario autenticado en el Momento 3 | M2-S | OBJ-05, OBJ-14 | ¿Cada acción sensible deja una fila de auditoría con esos cuatro datos, y se distingue qué médico registró cada entrada? |
| RF-39 | Crear al primer arranque, de forma idempotente, los usuarios de demostración, solo con la semilla activa (`APP_SEED_DEMO=true`) y nunca en el perfil `prod`: desde el Momento 2, enfermería y médico con nombres ficticios y sin credenciales; desde el Momento 3, además el administrador y las credenciales, documentadas en el README | M2-N (usuarios) · M3 (credenciales) | OBJ-13, OBJ-15, RNF-13 | Tras `./run.sh` en una máquina limpia, ¿hay usuarios de demostración para elegir (Momento 2), se puede iniciar sesión con cada usuario del README (Momento 3), y reiniciar no duplica usuarios? |

### 3.7 Informe mensual y opcionales

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-36 | Generar el informe mensual de cuatro semanas completas consecutivas (RN-10), a partir de las entradas y eventos de esas semanas, mostrando la evolución semana a semana. Solo se genera si las cuatro semanas tienen informe semanal aprobado; si falta alguna, no se genera y se indica cuál. Pasa por la misma revisión de enfermería antes de llegar al médico (RN-01, RN-02) | M2-N | OBJ-03 | Con cuatro semanas con informe semanal aprobado, ¿el informe muestra la evolución por semana, y con una semana sin aprobar el sistema no lo genera e indica cuál falta? |
| RF-37 | Exportar a PDF un informe aprobado, con aprobador, fecha, id del informe y nota de asistencia de IA (D13) | Opc | — | ¿El texto extraído del PDF contiene la aprobación? |
| RF-38 | Dictar una entrada por voz con transcripción editable antes de guardar, origen `VOZ` y sin guardar el audio (D14) | Opc | — | ¿La entrada dictada se puede corregir antes de guardar y queda con origen `VOZ` sin audio almacenado? |

---

## 4. Requisitos no funcionales

| ID | Categoría | Requisito | Criterio de verificación (sí/no) |
|---|---|---|---|
| RNF-01 | Seguridad | Autorización en el servidor para toda operación; la UI solo oculta. En M2 la autorización usa el rol del usuario de demostración elegido (D26): separa funciones pero no es un control de seguridad; la autenticación llega en M3. Mínimo privilegio; ninguna llave ni secreto real en el repo ni en el historial (el repositorio es público); las credenciales de demostración (desde M3) son sintéticas, están en el README y solo existen con la semilla activa; usuario de BD con permisos mínimos | ¿Una petición directa al API sin el usuario o el permiso requerido es rechazada aunque la UI no muestre el botón? ¿El escaneo de secretos no encuentra ninguno en el repo ni en el historial, y el perfil `prod` rechaza la semilla de demostración? |
| RNF-02 | Privacidad | Solo datos sintéticos. Al LLM solo salen el id interno y el texto de las entradas, nunca nombre ni documento. Sin audio guardado | ¿Una revisión de trazas y repo no encuentra datos reales ni identificadores personales? |
| RNF-03 | Seguridad del agente | Hay datos privados y contenido no confiable; la tercera pata (salida hacia afuera) se mantiene cerrada | ¿El inventario de herramientas del agente no incluye ninguna que envíe datos fuera? |
| RNF-04 | Robustez | Sin stack trace al usuario; los tres fallos forzables (timeout, salida que no valida, caída o 429) se manejan | ¿Los tres fallos forzados se manejan con mensaje claro? |
| RNF-05 | Rendimiento | La generación no bloquea la interfaz: estados honestos o streaming; el patrón elegido (D24) se justifica en `docs/decisiones.md`. La latencia se mide como mediana y se reporta, sin umbral previo | ¿La UI muestra estados durante la generación y la mediana está en `docs/decisiones/semana10.md`? |
| RNF-06 | Costo | Costo cero: capa gratuita de los proveedores. El costo por tarea se mide | ¿La demo corre sin pagos y se reporta el costo por tarea? |
| RNF-07 | Observabilidad | Trazas y auditoría consultables; los fallos se explican con la traza | ¿Un fallo forzado se explica con su traza? |
| RNF-08 | Reproducibilidad | Repositorio público autosuficiente: `./run.sh` o `docker compose up --build`; README como contrato ejecutable por una IA que solo lo lea (pasos lineales, motor de BD, imagen, comando y URL); Docker Compose v2 como único prerrequisito; variables en `.env.example`; la prueba del compañero queda documentada con lo que falló y lo que se corrigió | ¿Una IA con solo el README, en un clon limpio, deja la app corriendo sin intervención manual, y otra persona lo hace en menos de 5 minutos? |
| RNF-09 | Usabilidad | UI en español; lenguaje claro primero y evidencia a un clic; estados honestos | ¿Una persona de enfermería llega del informe a la evidencia en un clic? |
| RNF-10 | Integridad de datos | Las entradas y los informes no se borran, solo se corrigen con versión; las entradas son de solo lectura para el agente | ¿Existe alguna operación de borrado sobre entradas o informes? (debe ser no) |
| RNF-11 | Calidad | Pruebas automatizadas de las reglas críticas: aprobación, permisos, validador de cifras y citas, esquema. Commits convencionales. Cada integrante responde por las decisiones técnicas (qué se hizo y por qué) y puede explicar cualquier bloque que se le señale: la rúbrica escrita lo exige, aunque el profesor dijo en clase que preguntará sobre todo por decisiones | ¿La verificación del proyecto pasa y cubre esas reglas? |
| RNF-12 | Cumplimiento del curso | Estructura de entrega, límites del agente, `eval/` y cifras con fecha, según `AGENTS.md`; enlace al repositorio entregado en el aula virtual hasta el jueves 29-oct a medianoche, con la versión congelada en un tag hasta la presentación | ¿Existe cada entregable, y el enlace se entregó a tiempo con la versión congelada? |
| RNF-13 | Arranque en frío | La aplicación completa (BD, backend, frontend, migraciones y semilla) levanta con un comando y sin labor manual en una máquina limpia con solo Docker Compose v2; la llave del proveedor se toma del entorno o se pide por consola si hay terminal, se guarda en `.env` (fuera del repo) y, si no hay llave ni terminal, el arranque no se bloquea y la app avisa con claridad que falta la llave | En un clon limpio, ¿un solo comando deja corriendo la app con datos de demostración (y, desde el Momento 3, con login), sin quedarse esperando entrada? |
| RNF-14 | Decisiones justificadas | `docs/decisiones.md` registra cada decisión técnica (forma del sistema, stack, proveedor y modelo, estrategia de datos con o sin RAG, patrón síncrono, streaming o cola, base de datos y cada herramienta opcional que entre) con su razón técnica y la alternativa descartada; incluye el registro de iteración de las descripciones de herramientas y la prueba del compañero | ¿Para cada decisión técnica del sistema hay una entrada con razón y alternativa, y cada integrante sabe explicarla? |

---

## 5. Reglas de negocio

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Un informe solo llega al médico en estado `APROBADO` | [Equipo] |
| RN-02 | Solo enfermería aprueba; la transición se valida en el servidor | [Equipo] |
| RN-03 | El informe describe evolución documentada; no diagnostica ni recomienda tratamiento o dosis | [Equipo] |
| RN-04 | Toda cifra del informe la calcula el código, no el LLM | [Equipo] |
| RN-05 | Toda afirmación del informe cita al menos una entrada | [Curso] |
| RN-06 | Una semana va de lunes a domingo. El informe se compara con la semana anterior y el agente decide cuántas más atrás revisar, con tope | [Equipo] (D10) |
| RN-07 | El agente nunca modifica las entradas originales; el texto corregido (RF-42) es una versión aparte y el original sigue siendo la fuente citable | [Equipo] |
| RN-08 | Si faltan datos, el informe lo declara y no los rellena | [Equipo] |
| RN-09 | Se permiten varias entradas el mismo día; dentro del día se ordenan por hora de creación | [Propuesta] |
| RN-10 | Un mes son cuatro semanas completas consecutivas (lunes a domingo, RN-06). El informe mensual solo se genera si cada una tiene informe semanal `APROBADO`; si falta alguna, no se genera y se indica cuál | [Equipo] (semanas aprobadas) · [Propuesta] (cuatro semanas y no mes calendario) (D4) |
| RN-11 | La IA solo propone: ningún evento queda guardado sin confirmación de una persona, y el modelo no agrega lo que el texto no dice (intensidad «sin indicar» y veces vacías si no se indican, sin negaciones convertidas en síntomas y sin diagnóstico) | [Equipo] (confirmación humana) · [Propuesta] (no agregar lo que el texto no dice) (D27) |

---

## 6. Modelo conceptual preliminar

Solo entidades y relaciones. Atributos, tipos y tablas se definen en el diseño.

| Entidad | Qué es | Relaciones clave |
|---|---|---|
| Usuario, Rol, Permiso | Quién entra y qué puede hacer (en el Momento 2, usuarios de demostración sin credenciales cuyo rol aplica el servidor, D26; credenciales y administración de cuentas desde el Momento 3) | Un usuario tiene un rol; un rol agrupa permisos por módulo y acción |
| Paciente | Persona seguida, identificada por id interno | Tiene muchas entradas e informes |
| Entrada | Registro diario en texto libre (fecha, autor, texto original, versión corregida si la hay y marca «sin estructurar») | Pertenece a un paciente; tiene eventos derivados |
| Evento | Dato estructurado propuesto por el LLM y confirmado por una persona (tipo, descripción normalizada, intensidad, veces, fecha, origen y quién lo confirmó) | Enlaza a su entrada original |
| Informe | Documento por paciente y período, con estado, contenido estructurado, versión del modelo, versión editada, aprobador y fecha | Tiene afirmaciones; tiene una ejecución del agente |
| Afirmación | Frase del informe con su cita | Referencia una o más entradas |
| Ejecución del agente | Traza de pasos: herramienta, parámetros, resultado, ms, tokens | Asociada a un informe |
| Auditoría | Acción sensible: quién, cuándo, antes y después | Referencia al objeto afectado |

---

## 7. Trazabilidad objetivos → requisitos

| Objetivo (SPEC §2.2) | Requisitos que lo cubren |
|---|---|
| OBJ-01 Borrador semanal | RF-02, RF-08, RF-10, RF-11 |
| OBJ-02 Patrones que reaparecen | RF-06, RF-07, RF-10 |
| OBJ-03 Informe mensual | RF-36 |
| OBJ-04 Cifras por código | RF-04, RF-06, RF-07, RF-13, RF-40 |
| OBJ-05 Revisión humana exigida | RF-01, RF-03, RF-09, RF-23, RF-26 a RF-28, RF-35, RF-40, RF-42 |
| OBJ-06 Fuente y criterio visibles | RF-12, RF-24, RF-25, RF-29, RF-42 |
| OBJ-07 Datos insuficientes | RF-14, RF-41 |
| OBJ-08 No diagnosticar | RF-15 |
| OBJ-09 Límites y parada | RF-09, RF-16, RF-17 |
| OBJ-10 Resiliencia y proveedor | RF-18, RF-19, RF-20, RF-41 |
| OBJ-11 Medición y trazas | RF-22, RNF-05 a RNF-07; entregables `eval/` y `docs/decisiones.md` (cifras; detalle en `docs/decisiones/semana10.md`) |
| OBJ-12 Necesidad del LLM y del agente | RF-10; entregable: decisiones del agente y comparación contra la plantilla sin LLM |
| OBJ-13 Entrega autosuficiente y plan B | RF-05, RF-19, RF-39, RNF-08, RNF-13 |
| OBJ-14 Seguridad y gobernanza | RF-21, RF-35, RNF-01 a RNF-03; entregables del Momento 3 |
| OBJ-15 Permisos por rol | RF-30 a RF-34, RF-39 |
| OBJ-16 Decisiones técnicas justificadas | RNF-11, RNF-14; entregable `docs/decisiones.md` |

Todos los objetivos tienen al menos un requisito o un entregable asociado.

---

## 8. Supuestos de trabajo y preguntas abiertas

Para poder avanzar, el análisis usa estos supuestos. Si cambian, se actualiza este documento y la SPEC.

| Tema | Supuesto de trabajo | Afecta | Decisión en SPEC §7 |
|---|---|---|---|
| Origen de los registros | Captura mínima propia más datos sintéticos cargados por script | RF-02, RF-05 | D5 |
| Visibilidad al médico | Solo ve informes `APROBADO` | RF-28, RN-01 | D6 |
| Qué es una semana | Lunes a domingo; compara con la anterior y el agente decide cuántas más atrás, con tope | RN-06, RF-10 | D10 |
| El médico registra | Sí | RF-30 | D12 |
| Catálogo de métricas | El de RF-07 es una propuesta; conviene validarlo con una persona de enfermería | RF-07, RF-11 | D18 |
| Disparo de la generación | Manual (botón) en M2; programado al cierre de semana queda para M3 | RF-08 | D19 |
| Consulta libre al pedir el informe | Sin campo libre en M2; el caso de rechazo se construye con una entrada que pide un diagnóstico | RF-15 | D20 |
| Forma de entrega | Enlace al repositorio público de GitHub, sin ZIP; el privado es opcional según el profesor | RNF-01, RNF-08, RNF-12 | D21 |
| Arranque y llave del API | Una IA del profesor ejecuta solo el README; la llave se toma del entorno o se pide por consola; sin llave ni terminal, arranca igual y avisa que falta la llave | RNF-13 | D22 |
| Usuarios de demostración | Sembrados al arrancar desde el Momento 2 (enfermería y médico, nombres ficticios, sin credenciales) para que cada acción tenga un autor identificable; credenciales sintéticas en el README y administrador desde el Momento 3 | RF-05, RF-35, RF-39 | D23 |
| Informe mensual | En el Momento 2 (decidido). Un mes son cuatro semanas completas consecutivas (lunes a domingo) y exige el informe semanal aprobado de cada una; cuatro semanas y no mes calendario es propuesta por confirmar | RF-36, RN-10 | D4 |
| Estructuración de la entrada | La IA propone los eventos al registrar y la persona los confirma antes de guardar; el original se guarda intacto y el texto corregido es una versión aparte (M2-S). Propuesta por confirmar | RF-06, RF-40 a RF-42, RN-07, RN-11 | D27 |
| Identidad en el Momento 2 | Sin autenticación; el usuario de demostración elegido (enfermería o médico, sembrado sin credenciales) viaja en cada petición y el servidor lo usa para aprobar, para filtrar lo que ve el médico y para registrar quién hizo cada acción. Propuesta por confirmar | RF-02, RF-26, RF-28, RF-32, RF-35, RNF-01 | D26 |
| Patrón de integración | Ejecución asíncrona del agente con eventos de progreso por SSE | RF-22, RNF-05 | D24 |
| Evaluaciones y observabilidad | En clase el profesor las trató como deseables; la rúbrica escrita las puntúa; versión mínima: 3 a 5 casos dorados con script y traza visible en la app | RF-22, OBJ-11 | D25 |

**Estado de las decisiones de la SPEC §7:** decididas D1, D2, D3, D4, D5, D7, D8, D9, D10, D11, D12, D13, D14, D17, D21, D22, D23, D24; propuestas D15, D26, D27; supuestos D6, D19, D20, D25; por confirmar D18; descartada D16.

**Para el diseño (no bloquean el análisis):** proveedor del LLM (D15, propuesta), subir la versión de Java de 17 a 21 (parte pendiente de D9), cifras del conjunto sintético (D11 está decidida, sin valores; debe incluir al menos un paciente con cuatro semanas aprobadas para el informe mensual), valores de intensidad y tipos de evento de la estructuración (D27, D18) y qué hace «Descartar». Ya decididas: stack (D9), estrategia de datos (D8), motor de PDF (D13), voz (D14), patrón de integración (D24) y dónde se derivan los eventos (al registrar la entrada, D27 propuesta).

---

## 9. Criterios de salida de la fase de análisis

- [x] Cada objetivo de la SPEC tiene al menos un requisito o entregable (§7). Verificado el 2026-10-25, incluido OBJ-16.
- [x] Cada requisito funcional tiene un criterio que se responde con sí o no. Verificado el 2026-10-25 en los 39 RF y el 2026-10-28 en RF-40 a RF-42 (42 RF).
- [ ] Los dos integrantes revisaron los supuestos de trabajo de §8.
- [ ] El catálogo de métricas (RF-07) y la estructura del informe (RF-11) se validaron con una persona de enfermería, o quedó registrado que no se pudo.
- [ ] La SPEC se actualizó con las decisiones D18 a D20 y los supuestos confirmados (D18 a D20 ya están en la SPEC; faltan los supuestos confirmados, incluidos D21 a D27).

Las casillas abiertas no bloquean el diseño; quedan como deuda con fecha límite propuesta: revisión de supuestos por los dos integrantes el lunes 26 y validación con enfermería (o constancia de que no se pudo) el miércoles 28 **[Propuesta]**.
