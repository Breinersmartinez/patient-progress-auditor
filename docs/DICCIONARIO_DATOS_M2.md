# DICCIONARIO DE DATOS — Momento 2 — Patient Progress Auditor

> **Estado:** borrador v0.1 para aprobación · **Fase del ciclo de vida:** 3 — Diseño (modelo de datos) · **Alcance:** Momento 2 · **Fecha:** 2026-10-28
> **Base:** `docs/SPEC.md` v0.7, `docs/REQUISITOS.md` v0.4, las migraciones existentes `V1__create_rbac.sql` y `V2__seed_rbac.sql` y el diccionario completo `docs/DICCIONARIO_DATOS.md` v0.1, del que deriva.
>
> **Qué es:** el modelo de datos de lo que se construye y se entrega en el Momento 2 (demo del sábado 31 de octubre; versión congelada el jueves 29 en el tag `m2-entrega`). Se lee solo: para escribir `V3` y `V4` no hace falta abrir el diccionario completo.
>
> **Qué deja fuera:** lo que llega en el Momento 3: las credenciales y las contraseñas, el usuario y el rol `ADMIN` en uso, el tablero de permisos (RF-31, RF-33, RF-34), el módulo global de trazas y el cambio de permisos en la auditoría. El rol `ADMIN` y los permisos `USERS_*`, `ROLES_*` y `PERMISSIONS_READ` que ya sembró `V2` se quedan como están, sin uso.
>
> **Relación con el completo:** el completo sigue siendo la referencia de todo el proyecto. Este documento cierra con una propuesta marcada **[Propuesta]** los 13 puntos que el completo dejó abiertos (§8) y lista cada diferencia entre los dos (§9). Cuando se confirme o cambie algo que esté en los dos, se actualizan los dos.
>
> **Origen:** cada columna cita el requisito (RF, RN, RNF) o la decisión (D) que la pide. Lo que se agrega para poder construir y no viene de un requisito se marca **[Propuesta]**.
>
> **Hito:** **M2-N** núcleo del Momento 2 · **M2-S** Momento 2 si hay tiempo, lo primero que se recorta el jueves · **Opc** opcional, igual que en `REQUISITOS.md`.

---

## 1. Convenciones

| Tema | Convención | Origen |
|---|---|---|
| Idioma de los identificadores | Tablas y columnas en inglés y `snake_case`, como las tablas `app_*` existentes y el paquete `com.patientprogressauditor`. Cada columna lleva su etiqueta en español en este diccionario. La interfaz y los documentos siguen en español | [Equipo] |
| Idioma de los valores | Los códigos de dominio ya fijados en los documentos se conservan en español y en mayúsculas: `ENFERMERIA`, `MEDICO`, `PENDIENTE_REVISION`, `APROBADO`, `VOZ` | RF-23, RF-32, RF-38 |
| Nombres de tabla | Plural. Las del RBAC llevan el prefijo `app_` (ya existen); las del dominio no | V1 |
| Clave primaria | `UUID` generado por la aplicación y sin valor por defecto, como en V1. Los datos sembrados usan UUID fijos para que la semilla sea idempotente (`ON CONFLICT DO NOTHING`, como V2). Excepción: `agent_steps` y `audit_logs` usan `BIGINT` autoincremental porque su orden es la fuente de eventos | V1, V2, RF-05, D24 |
| Fechas y horas | `DATE` para los días del dominio (fecha de la entrada, límites del período) y `TIMESTAMPTZ` para los instantes (`created_at`). Semana de lunes a domingo; mes de cuatro semanas. La aplicación calcula «hoy» y los límites de la semana en `America/Bogota` **[Propuesta]**; la BD guarda los instantes en UTC, como cualquier `TIMESTAMPTZ` | RN-06, RN-10 |
| Auditoría de fila | Toda tabla con filas editables lleva `created_at` y `updated_at` (`TIMESTAMPTZ`, por defecto `CURRENT_TIMESTAMP`), como V1. Las tablas que solo se agregan llevan `created_at` | V1 |
| Borrado | Ninguna fila de dominio se borra: no hay `ON DELETE CASCADE` en las claves foráneas del dominio y las entradas e informes solo se corrigen con versión. Los usuarios, roles y permisos tampoco se borran; en el Momento 2 no hay pantalla para desactivarlos (RF-33 es del Momento 3) | RNF-10 |
| Texto de la entrada | `entries.body` guarda lo que la persona escribió y nunca se actualiza; el texto corregido va en `entry_versions` (M2-S). Las citas de los informes apuntan al original | RN-05, RN-07, RF-42 |
| Cifras | No se guardan métricas calculadas: el código las calcula cada vez a partir de entradas y eventos. El informe guarda una copia de las cifras que usó | RN-04, RF-07, RF-13 |
| Datos personales | Solo datos sintéticos. Al proveedor del LLM salen únicamente el `id` interno del paciente y el texto (ver §6) | RNF-02, RF-01 |
| Nulos y valores por defecto | Una columna es `NOT NULL` salvo que este diccionario diga lo contrario | — |

## 2. Mapa de entidades

16 tablas: 5 del RBAC que ya existen y 11 del dominio que crea `V3`. En el Momento 2 son 14 de núcleo y 2 «si hay tiempo».

| Tabla | Etiqueta | Qué guarda | Hito | Requisitos |
|---|---|---|---|---|
| `app_users` | Usuarios | Los usuarios de demostración entre los que se elige (D26), sin credenciales. Existe; requiere cambios (§4.1) | M2-N | RF-32, RF-39 |
| `app_roles` | Roles | `ENFERMERIA`, `MEDICO` y el rol técnico `AGENTE`. Existe; solo tiene `ADMIN` | M2-N | RF-32 |
| `app_permissions` | Permisos | Qué se puede hacer por módulo y acción. Existe; faltan los del dominio | M2-N | RF-32 |
| `app_user_roles` | Usuario–rol | Rol de cada usuario. Existe | M2-N | RF-32 |
| `app_role_permissions` | Rol–permiso | Permisos de cada rol. Existe | M2-N | RF-32 |
| `patients` | Pacientes | Persona seguida, con id interno y alias legible | M2-N | RF-01 |
| `entries` | Entradas | Registro diario en texto libre, tal como se escribió | M2-N | RF-02, RF-04, RN-07, RN-09 |
| `entry_events` | Eventos de la entrada | Síntomas con intensidad y veces, propuestos por la IA y confirmados por una persona | M2-N | RF-06, RF-40, RF-41, RN-11 |
| `entry_versions` | Versiones de la entrada | Texto corregido o editado, con quién y cuándo | M2-S | RF-03, RF-42 |
| `reports` | Informes | Informe semanal o mensual de un paciente, con su estado y su revisión | M2-N | RF-08, RF-23, RF-26, RF-27, RF-36 |
| `report_versions` | Versiones del informe | Contenido del modelo y cada edición de enfermería | M2-N | RF-11, RF-18, RF-25 |
| `report_citations` | Citas del informe | Qué entradas respalda cada afirmación | M2-N | RF-12, RN-05 |
| `monthly_report_weeks` | Semanas del informe mensual | Las cuatro semanas aprobadas que componen un mensual | M2-N | RF-36, RN-10 |
| `agent_runs` | Ejecuciones | Estado, límites, proveedor y costo de cada ejecución del agente o de cada estructuración | M2-N | RF-16, RF-17, RF-19, RF-22, D24 |
| `agent_steps` | Pasos de la ejecución | La traza: cada llamada, herramienta, validación y fallo, en orden | M2-N | RF-22, RNF-07, D24 |
| `audit_logs` | Auditoría | Quién hizo una acción sensible, cuándo, y el antes y el después | M2-S | RF-35, D12 |

```mermaid
erDiagram
    app_users ||--o{ app_user_roles : tiene
    app_roles ||--o{ app_user_roles : agrupa
    app_roles ||--o{ app_role_permissions : otorga
    app_permissions ||--o{ app_role_permissions : incluye
    patients ||--o{ entries : tiene
    app_users ||--o{ entries : escribe
    entries ||--o{ entry_events : contiene
    entries ||--o{ entry_versions : versiona
    patients ||--o{ reports : tiene
    reports ||--|{ report_versions : versiona
    report_versions ||--o{ report_citations : cita
    entries ||--o{ report_citations : respalda
    reports ||--o{ monthly_report_weeks : agrupa
    agent_runs ||--o{ agent_steps : traza
    agent_runs ||--o{ reports : produce
    app_users ||--o{ audit_logs : actua
```

## 3. Catálogos (valores permitidos)

Se guardan como `VARCHAR` con `CHECK`, no como `ENUM` de PostgreSQL, para poder cambiarlos con una migración corriente.

### 3.1 Roles

| Código | Qué es | Hito | Origen |
|---|---|---|---|
| `ENFERMERIA` | Registra, genera, revisa, corrige y aprueba o rechaza | M2-N | RF-32, RN-02 |
| `MEDICO` | Consulta informes `APROBADO` y registra entradas | M2-N | RF-28, RF-30, D12 |
| `AGENTE` | Rol técnico del agente: lectura y guardar el borrador; sin usuario ni contraseña | M2-N | RF-09, RF-32 |

El rol `ADMIN` ya existe por `V2` y en el Momento 2 no se asigna a ningún usuario ni se usa; su contenido y el de sus permisos se definen en el Momento 3.

### 3.2 Estados y tipos

| Campo | Valores | Notas | Origen |
|---|---|---|---|
| `reports.status` | `PENDIENTE_REVISION`, `INCOMPLETO`, `APROBADO`, `RECHAZADO` | Valor inicial `PENDIENTE_REVISION`, o `INCOMPLETO` si se agotó un límite. `RECHAZADO` es M2-S. Transiciones en §5.2 | RF-17, RF-23, RF-27 |
| `reports.report_type` | `SEMANAL`, `MENSUAL` | | RF-08, RF-36 |
| `report_versions.origin` | `MODELO`, `ENFERMERIA` | La versión 1 es siempre `MODELO` | RF-25 |
| `entries.source` | `TEXTO`, `VOZ` | `VOZ` solo si entra el dictado (Opc) | RF-38 |
| `entries.structuring_status` | `ESTRUCTURADA`, `SIN_ESTRUCTURAR` | Ver la definición en §5 y el punto 7 de §8 | RF-41 |
| `entry_events.event_type` | `SINTOMA` | El único tipo en el Momento 2: la pantalla de registro solo muestra síntomas | RF-06, D27 |
| `entry_events.symptom` | Los 9 códigos de §3.3 | Lista cerrada **[Propuesta]** | RF-06, D18 |
| `entry_events.intensity` | `LEVE`, `MODERADO`, `INTENSO`, `SIN_INDICAR` | Son las cuatro opciones de la pantalla de registro de los mockups | RF-06, D27 |
| `entry_events.origin` | `IA`, `EDITADO`, `MANUAL` | Propuesto por la IA y confirmado igual, propuesto y cambiado por la persona, o agregado a mano | RF-40, D27 |
| `entry_versions.origin` | `IA_ACEPTADA`, `IA_EDITADA`, `MANUAL` | Texto corregido propuesto por la IA y aceptado o editado, o edición manual (M2-S) | RF-03, RF-42 |
| `agent_runs.run_type` | `INFORME`, `ESTRUCTURACION` | **[Propuesta]**: la estructuración al registrar también deja traza | RF-22, RNF-07 |
| `agent_runs.status` | `EN_COLA`, `EN_CURSO`, `COMPLETADA`, `INCOMPLETA`, `FALLIDA` | **[Propuesta]**: el estado de la ejecución vive en la BD para poder consultarlo y reanudar | D24 |
| `agent_runs.stop_reason` | `EXITO`, `MAX_ITERACIONES`, `MAX_TOKENS`, `TIMEOUT_HERRAMIENTA`, `SALIDA_INVALIDA`, `PROVEEDOR_NO_DISPONIBLE` | **[Propuesta]**: el motivo con el que termina | RF-16, RF-17, RF-19 |
| `agent_steps.step_type` | `ESTADO`, `LLAMADA_LLM`, `HERRAMIENTA`, `VALIDACION`, `LIMITE`, `FALLBACK`, `ERROR` | **[Propuesta]** | RF-22, D24 |
| `audit_logs.action` | `APROBAR_INFORME`, `RECHAZAR_INFORME`, `EDITAR_INFORME`, `EDITAR_ENTRADA`, `REGISTRAR_ENTRADA_MEDICO` | M2-S | RF-35 |

### 3.3 Catálogo de síntomas

Lista cerrada **[Propuesta]**: son los nueve síntomas del catálogo de la pantalla de registro de los mockups, a la espera de que D18 la valide con una persona de enfermería. El modelo solo puede proponer estos códigos (el esquema JSON los fija como enumeración), «Agregar» ofrece esta misma lista y la BD la protege con un `CHECK` en `entry_events.symptom`. Cambiar la lista es una migración.

| Código | Etiqueta | Origen |
|---|---|---|
| `VOMITO` | Vómito | Mockups, D27 |
| `DIARREA` | Diarrea | Mockups, D27 |
| `NAUSEAS` | Náuseas | Mockups |
| `FIEBRE` | Fiebre | Mockups |
| `MAREO` | Mareo | Mockups |
| `DOLOR_CABEZA` | Dolor de cabeza | Mockups, D27 |
| `DOLOR_HUESO` | Dolor de hueso | Mockups |
| `APETITO_BAJO` | Apetito bajo | Mockups, D27 |
| `ANIMO_DECAIDO` | Ánimo decaído | Mockups |

### 3.4 Catálogo de permisos del dominio

V2 dejó los permisos del dominio «hasta que la especificación cierre sus nombres canónicos». Estos son los nombres propuestos, derivados de la matriz de `SPEC.md` §4. En el Momento 2 se aplican con el rol del usuario de demostración elegido (D26).

| Código | Qué permite | ENFERMERIA | MEDICO | AGENTE | Hito | Origen |
|---|---|---|---|---|---|---|
| `PATIENTS_READ` | Ver pacientes | Sí | Sí | Sí | M2-N | SPEC §4 |
| `PATIENTS_CREATE` | Crear pacientes | Sí | — | — | M2-N | RF-01 |
| `PATIENTS_UPDATE` | Actualizar pacientes | Sí | — | — | M2-N | SPEC §4 |
| `ENTRIES_READ` | Ver entradas y eventos | Sí | Sí | Sí | M2-N | SPEC §4 |
| `ENTRIES_CREATE` | Registrar entradas y confirmar sus eventos | Sí | Sí | — | M2-N | RF-02, RF-40, D12 |
| `ENTRIES_UPDATE` | Corregir una entrada con versión | Sí | — | — | M2-S | RF-03 |
| `REPORTS_READ` | Ver informes y la traza de su ejecución; para el médico solo `APROBADO`, regla de estado en la consulta | Sí | Sí | — | M2-N | RN-01, RF-24, RF-28 |
| `REPORTS_REQUEST` | Pedir la generación de un informe | Sí | — | — | M2-N | RF-08, **[Propuesta]** |
| `REPORTS_CREATE_DRAFT` | Guardar el borrador en `PENDIENTE_REVISION` | — | — | Sí | M2-N | RF-09, SPEC §4 |
| `REPORTS_UPDATE` | Editar el borrador antes de aprobar | Sí | — | — | M2-N | RF-25 |
| `REPORTS_APPROVE` | Aprobar o rechazar un informe; fijo para enfermería | Sí | — | — | M2-N | RN-02, RF-26, RF-27 |

`V2` ya sembró para `ADMIN` los permisos `USERS_*`, `ROLES_*` y `PERMISSIONS_READ`. En el Momento 2 no se usan y `V4` no los toca. No hay permiso `TRACES_READ` en el Momento 2: la traza se lee desde el informe (punto 1 de §8).

## 4. Tablas

Columnas: **Clave** indica `PK`, `FK → tabla` o `UQ` (único). **Nulo** dice si la columna admite `NULL`. Las columnas marcadas «nueva» o «cambia» no están en V1.

### 4.1 `app_users` — Usuarios (M2-N)

Quién actúa. En el Momento 2 son los usuarios de demostración sembrados, sin credenciales, entre los que se elige en pantalla (D26): así cada acción tiene un autor identificable. El inicio de sesión llega en el Momento 3 (D23).

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | Generado por la aplicación. UUID fijo en los usuarios de demostración | V1, RF-05 |
| `email` | Correo | `VARCHAR(320)` | No | UQ | Correo ficticio de un dominio reservado (por ejemplo `enfermera.demo@example.test`). En el Momento 2 solo identifica al usuario; no se usa para iniciar sesión | V1, RF-39 |
| `display_name` | Nombre visible | `VARCHAR(100)` | No | — | Nueva. Nombre ficticio que se muestra al elegir usuario y como autor en entradas, informes y auditoría | RF-39, D26, **[Propuesta]** |
| `password_hash` | Hash de la contraseña | `VARCHAR(100)` | Sí | — | Cambia: en V1 es `NOT NULL`. En el Momento 2 queda siempre vacío porque los usuarios de demostración no tienen credenciales; no se guarda un hash de relleno, que sería una credencial falsa. El Momento 3 lo vuelve obligatorio con su propia migración | D23 |
| `is_demo` | Usuario de demostración | `BOOLEAN` | No | — | Nueva. `TRUE` en los sembrados con `APP_SEED_DEMO=true`; lista los que se pueden elegir y permite excluirlos en `prod`. Por defecto `FALSE` | RF-39, D26, **[Propuesta]** |
| `active` | Activo | `BOOLEAN` | No | — | Por defecto `TRUE`. En el Momento 2 ninguna pantalla lo cambia | V1 |
| `created_at` | Creado | `TIMESTAMPTZ` | No | — | Por defecto `CURRENT_TIMESTAMP` | V1 |
| `updated_at` | Actualizado | `TIMESTAMPTZ` | No | — | Por defecto `CURRENT_TIMESTAMP` | V1 |

### 4.2 `app_roles`, `app_permissions`, `app_user_roles`, `app_role_permissions` — RBAC (M2-N)

Existen en V1 y no cambian de estructura; lo que cambia es su contenido (§3.1 y §3.4).

| Tabla | Columnas | Reglas |
|---|---|---|
| `app_roles` | `id UUID PK` · `code VARCHAR(50) UQ` · `active BOOLEAN` · `created_at` · `updated_at` | V2 solo siembra `ADMIN`, que en el Momento 2 no se usa; faltan `ENFERMERIA`, `MEDICO` y `AGENTE` (§3.1) |
| `app_permissions` | `id UUID PK` · `code VARCHAR(100) UQ` · `active BOOLEAN` · `created_at` · `updated_at` | Faltan los permisos del dominio (§3.4) |
| `app_user_roles` | `user_id UUID FK → app_users` · `role_id UUID FK → app_roles` · `PK (user_id, role_id)` | Un usuario de demostración tiene un rol. Las tablas de unión del RBAC conservan su `ON DELETE CASCADE` de V1; es la única excepción a la regla de no borrar |
| `app_role_permissions` | `role_id UUID FK → app_roles` · `permission_id UUID FK → app_permissions` · `PK (role_id, permission_id)` | El permiso `REPORTS_APPROVE` solo se asigna a `ENFERMERIA` (RN-02) |

El rol `AGENTE` no tiene fila en `app_users`: es técnico y no inicia sesión (SPEC §4, arranque en frío).

### 4.3 `patients` — Pacientes (M2-N)

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id interno | `UUID` | No | PK | Único identificador del paciente que sale al proveedor del LLM | RF-01, RNF-02 |
| `alias` | Alias | `VARCHAR(100)` | No | — | Nombre sintético o iniciales para mostrar. Nunca se envía al LLM ni a la traza. Único sin distinguir mayúsculas (índice `uq_patients_alias`, `V5`) | RF-01 |
| `bed` | Cama | `VARCHAR(10)` | Sí | — | Nueva. Cama del paciente (por ejemplo `204-A`), sintética; la muestran la lista y la ficha. Nunca se envía al LLM ni a la traza | RF-01, RNF-02, **[Propuesta]** |
| `age` | Edad | `SMALLINT` | Sí | — | Nueva. Años cumplidos, sintéticos. Nunca se envía al LLM ni a la traza | RF-01, RNF-02, **[Propuesta]** |
| `admitted_on` | Fecha de ingreso | `DATE` | Sí | — | Nueva. Día de ingreso, sintético. Nunca se envía al LLM ni a la traza | RF-01, RNF-02, **[Propuesta]** |
| `created_at` | Creado | `TIMESTAMPTZ` | No | — | | V1 |
| `updated_at` | Actualizado | `TIMESTAMPTZ` | No | — | | V1 |

### 4.4 `entries` — Entradas diarias (M2-N)

Una fila por registro. Se permiten varias entradas el mismo día (RN-09).

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | | RF-02 |
| `patient_id` | Paciente | `UUID` | No | FK → `patients` | | RF-02 |
| `entry_date` | Fecha de la entrada | `DATE` | No | — | Día que describe el texto; puede ser anterior al día en que se registró. Define a qué semana pertenece (lunes a domingo) | RF-02, RN-06 |
| `body` | Texto original | `TEXT` | No | — | Lo que la persona escribió, sin cambios. Ninguna operación lo actualiza ni lo borra; es la fuente que citan los informes. El texto no es instrucción para el agente | RF-02, RN-05, RN-07, RF-21 |
| `author_id` | Autor | `UUID` | No | FK → `app_users` | Usuario de demostración elegido (D26). Puede ser enfermería o médico | RF-02, RF-30, D12 |
| `source` | Origen del texto | `VARCHAR(10)` | No | — | `TEXTO` por defecto o `VOZ`; sin audio guardado | RF-38, RNF-02 |
| `structuring_status` | Estado de estructuración | `VARCHAR(20)` | No | — | `ESTRUCTURADA` si una persona confirmó la propuesta de eventos, aunque sea vacía; `SIN_ESTRUCTURAR` si se guardó sin confirmarla (falló la IA o se guardó solo el texto). El informe declara las entradas sin estructurar | RF-40, RF-41, RN-08 |
| `created_at` | Registrada | `TIMESTAMPTZ` | No | — | Instante en que se guardó; ordena las entradas dentro del día. No hay `updated_at`: la fila no cambia | RN-09 |

**Índice:** `(patient_id, entry_date, created_at)` para listar por fecha y filtrar por rango (RF-04).

### 4.5 `entry_events` — Eventos de la entrada (M2-N)

Un síntoma que el texto dice, propuesto por el LLM y confirmado por una persona. Solo existen eventos confirmados: lo que se descarta no se guarda (RN-11). El evento no tiene fecha propia: vale la de su entrada (`entries.entry_date`), como dice D27.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | | RF-06 |
| `entry_id` | Entrada | `UUID` | No | FK → `entries` | Enlaza al texto original | RF-06 |
| `event_type` | Tipo | `VARCHAR(30)` | No | — | `SINTOMA` en el Momento 2. `CHECK (event_type = 'SINTOMA')` | RF-06, D27 |
| `symptom` | Síntoma (descripción normalizada) | `VARCHAR(20)` | No | — | Código del catálogo cerrado de §3.3 (`VOMITO`, `DIARREA`, `APETITO_BAJO`…). Es la clave con la que el código cuenta los días con cada síntoma: «comió poco» se normaliza a `APETITO_BAJO`. `CHECK` con los nueve códigos **[Propuesta]** | RF-06, RF-07, D18, D27 |
| `intensity` | Intensidad | `VARCHAR(15)` | No | — | `SIN_INDICAR` por defecto; el modelo no la infiere si el texto no la dice | RF-06, RN-11, D27 |
| `occurrences` | Veces | `SMALLINT` | Sí | — | Entero mayor que 0 si el texto lo dice («dos veces» pasa a 2); vacío si no. Son las veces en el día de la entrada | RF-06, RN-11 |
| `origin` | Origen del evento | `VARCHAR(10)` | No | — | `IA`, `EDITADO` o `MANUAL` (§3.2) | RF-40 |
| `confirmed_by` | Confirmó | `UUID` | No | FK → `app_users` | Quién dejó el evento; no se guarda un evento sin confirmación | RF-40, RN-11 |
| `confirmed_at` | Confirmado | `TIMESTAMPTZ` | No | — | | RF-40 |
| `created_at` | Creado | `TIMESTAMPTZ` | No | — | | V1 |

**Restricciones:** `CHECK (occurrences IS NULL OR occurrences > 0)` y `UNIQUE (entry_id, symptom)`: un síntoma aparece una sola vez por entrada, con sus veces **[Propuesta]**. **Índices:** `(entry_id)` y `(symptom)`.

**Cómo se cuentan.** Las métricas semanales (RF-07) unen `entry_events` con `entries` por `entry_id` y filtran por `patient_id` y `entry_date`. Los días con un síntoma son los `entry_date` distintos, de modo que varias entradas el mismo día cuentan un día (RN-09); las veces son la suma de `occurrences` donde está indicado.

### 4.6 `entry_versions` — Versiones de la entrada (M2-S)

El texto corregido por la IA (ortografía y puntuación, sin cambiar el contenido) o editado por una persona. La versión vigente es la de mayor `version_no`; si no hay ninguna, vale el original.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | | RF-03 |
| `entry_id` | Entrada | `UUID` | No | FK → `entries` | | RF-03 |
| `version_no` | Número de versión | `INTEGER` | No | — | Desde 1; el original es la versión 0 implícita y no se repite aquí. `UNIQUE (entry_id, version_no)` | RF-03 |
| `body` | Texto de la versión | `TEXT` | No | — | | RF-03, RF-42 |
| `origin` | Origen de la versión | `VARCHAR(15)` | No | — | `IA_ACEPTADA`, `IA_EDITADA` o `MANUAL` | RF-42 |
| `created_by` | Quién | `UUID` | No | FK → `app_users` | Quién confirmó o cambió el texto | RF-03, RF-42 |
| `created_at` | Cuándo | `TIMESTAMPTZ` | No | — | | RF-03 |

### 4.7 `reports` — Informes (M2-N)

Un informe de un paciente y un período. Lo que el modelo redactó y lo que enfermería editó vive en `report_versions`; esta tabla guarda el estado y quién lo revisó.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | | RF-08 |
| `patient_id` | Paciente | `UUID` | No | FK → `patients` | | RF-08 |
| `report_type` | Tipo | `VARCHAR(10)` | No | — | `SEMANAL` o `MENSUAL` | RF-08, RF-36 |
| `period_start` | Inicio del período | `DATE` | No | — | Siempre un lunes: `CHECK (EXTRACT(ISODOW FROM period_start) = 1)`. En el mensual, el lunes de la primera de las cuatro semanas | RN-06, RN-10 |
| `period_end` | Fin del período | `DATE` | No | — | Siempre un domingo: `period_start + 6` en el semanal y `period_start + 27` en el mensual | RN-06, RN-10 |
| `status` | Estado | `VARCHAR(20)` | No | — | Por defecto `PENDIENTE_REVISION`. Valores y transiciones en §3.2 y §5 | RF-17, RF-23 |
| `status_reason` | Motivo | `TEXT` | Sí | — | Obligatorio en `INCOMPLETO` (qué límite se agotó) y en `RECHAZADO` (motivo que escribe enfermería) | RF-17, RF-27 |
| `requested_by` | Pidió | `UUID` | No | FK → `app_users` | Quién solicitó la generación | RF-08 |
| `run_id` | Ejecución | `UUID` | Sí | FK → `agent_runs` | La ejecución que lo produjo. Vacío en los informes del conjunto sintético, que se siembran ya aprobados o, en uno de ellos, pendiente de revisión | RF-22, RF-05 |
| `provider` | Proveedor | `VARCHAR(50)` | Sí | — | Proveedor que respondió; alimenta la nota de asistencia de IA del PDF | RF-19, RF-37 |
| `model` | Modelo | `VARCHAR(100)` | Sí | — | Modelo que redactó el borrador | RF-37, D15 |
| `reviewed_by` | Revisó | `UUID` | Sí | FK → `app_users` | Quién aprobó o rechazó. Debe tener el rol `ENFERMERIA`: lo valida el servidor, no la BD | RN-02, RF-26 |
| `reviewed_at` | Revisado | `TIMESTAMPTZ` | Sí | — | Cuándo se aprobó o rechazó | RF-26 |
| `approved_version_no` | Versión aprobada | `INTEGER` | Sí | — | Qué versión de `report_versions` se aprobó; es la que ve el médico | RF-25, RF-28 |
| `created_at` | Creado | `TIMESTAMPTZ` | No | — | | V1 |
| `updated_at` | Actualizado | `TIMESTAMPTZ` | No | — | | V1 |

**Restricciones:**
- `CHECK ((status IN ('APROBADO','RECHAZADO')) = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))`.
- `CHECK (status <> 'APROBADO' OR approved_version_no IS NOT NULL)`.
- `CHECK (status NOT IN ('INCOMPLETO','RECHAZADO') OR status_reason IS NOT NULL)`.
- Índice único parcial `(patient_id, report_type, period_start) WHERE status IN ('PENDIENTE_REVISION','APROBADO')`: a lo sumo un informe vigente por paciente y período; uno `RECHAZADO` o `INCOMPLETO` no bloquea pedir uno nuevo **[Propuesta]**.
- Índices: `(patient_id, period_start DESC)` para la consulta del médico y `(status)`.

### 4.8 `report_versions` — Versiones del informe (M2-N)

La versión 1 es la que escribió el modelo y no se toca; cada edición de enfermería agrega una versión. Así se conserva el original del modelo y las ediciones.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | | RF-25 |
| `report_id` | Informe | `UUID` | No | FK → `reports` | | RF-25 |
| `version_no` | Número de versión | `INTEGER` | No | — | Desde 1. `UNIQUE (report_id, version_no)` | RF-25 |
| `origin` | Origen | `VARCHAR(12)` | No | — | `MODELO` (versión 1) o `ENFERMERIA` | RF-25 |
| `content` | Contenido | `JSONB` | Sí | — | El informe con estructura fija validada por esquema (ver la estructura abajo). Vacío solo si se degradó a texto plano | RF-11, RF-18 |
| `plain_text` | Texto plano | `TEXT` | Sí | — | Respaldo cuando la salida no valida tras un reintento: se degrada a texto plano y se registra. `CHECK (content IS NOT NULL OR plain_text IS NOT NULL)` | RF-18 |
| `edited_by` | Editó | `UUID` | Sí | FK → `app_users` | Obligatorio si `origin = 'ENFERMERIA'`; vacío en la versión del modelo | RF-25, RF-35 |
| `created_at` | Creado | `TIMESTAMPTZ` | No | — | | RF-25 |

**Estructura de `content`** (JSON con claves en inglés, validado por esquema en el servidor):

| Clave | Qué contiene | Origen |
|---|---|---|
| `summary` | Resumen en lenguaje claro | RF-11, RNF-09 |
| `metrics` | Las cifras de la semana o del mes, copiadas del módulo de métricas (`WeeklyMetrics`, E7; definiciones en `docs/decisiones.md`): el validador rechaza el informe si una cifra no coincide | RF-07, RF-13, RN-04 |
| `changes_vs_previous` | Cambios frente a la semana anterior; en el mensual, la evolución semana a semana | RF-11, RF-36 |
| `recurring_patterns` | Síntomas que reaparecen o persisten | RF-11, OBJ-02 |
| `insufficient_data` | Semanas, días o entradas sin información suficiente, declarados sin rellenar; incluye las entradas `SIN_ESTRUCTURAR` | RF-14, RF-41, RN-08 |
| `claims` | Lista de afirmaciones: `claim_key`, `text`, `criterion` (criterio de comparación) y `entry_ids` (al menos una entrada citada) | RF-12, RN-05 |

### 4.9 `report_citations` — Citas del informe (M2-N)

Índice normalizado de `content.claims`: una fila por afirmación y entrada citada. Da integridad referencial a las citas y permite abrir la evidencia «a un clic».

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `report_version_id` | Versión del informe | `UUID` | No | PK, FK → `report_versions` | | RF-12 |
| `claim_key` | Afirmación | `VARCHAR(40)` | No | PK | Clave de la afirmación dentro de `content.claims` | RF-12 |
| `entry_id` | Entrada citada | `UUID` | No | PK, FK → `entries` | Siempre el texto original de la entrada, no una versión | RF-12, RN-05, RF-42 |

La clave primaria es `(report_version_id, claim_key, entry_id)`. Cada `claim_key` del contenido debe tener al menos una fila: lo comprueba el validador antes de guardar el borrador (RN-05).

### 4.10 `monthly_report_weeks` — Semanas del informe mensual (M2-N)

Deja a la vista de qué cuatro informes semanales sale un mensual.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `monthly_report_id` | Informe mensual | `UUID` | No | PK, FK → `reports` | Un informe con `report_type = 'MENSUAL'` | RF-36 |
| `week_no` | Semana | `SMALLINT` | No | PK | De 1 a 4: `CHECK (week_no BETWEEN 1 AND 4)` | RN-10 |
| `weekly_report_id` | Informe semanal | `UUID` | No | FK → `reports` | Un informe `SEMANAL` en estado `APROBADO`, de las cuatro semanas consecutivas. `UNIQUE (monthly_report_id, weekly_report_id)` | RN-10 |

Que los cuatro semanales estén `APROBADO` y sean consecutivos lo valida el servidor antes de generar el mensual; la BD no lo puede expresar (§5).

### 4.11 `agent_runs` — Ejecuciones (M2-N)

Una fila por ejecución del agente de informes y por cada estructuración de una entrada. El estado vive aquí para poder consultarlo si se corta la conexión de progreso.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `UUID` | No | PK | | D24 |
| `run_type` | Tipo de ejecución | `VARCHAR(20)` | No | — | `INFORME` o `ESTRUCTURACION` | RF-22, **[Propuesta]** |
| `patient_id` | Paciente | `UUID` | No | FK → `patients` | | RF-22 |
| `requested_by` | Pidió | `UUID` | No | FK → `app_users` | | RF-08 |
| `period_start` | Inicio solicitado | `DATE` | Sí | — | Solo en `INFORME` | RF-08 |
| `period_end` | Fin solicitado | `DATE` | Sí | — | Solo en `INFORME` | RF-08 |
| `status` | Estado | `VARCHAR(15)` | No | — | `EN_COLA` por defecto; valores en §3.2 | D24 |
| `stop_reason` | Motivo de parada | `VARCHAR(30)` | Sí | — | Vacío mientras corre; al terminar, `EXITO` o el límite o fallo que la detuvo | RF-16, RF-17 |
| `provider` | Proveedor | `VARCHAR(50)` | Sí | — | El que respondió; cambia si se usó el de respaldo | RF-19, RF-20 |
| `model` | Modelo | `VARCHAR(100)` | Sí | — | | D15 |
| `fallback_used` | Usó respaldo | `BOOLEAN` | No | — | Por defecto `FALSE` | RF-19 |
| `iterations` | Iteraciones | `INTEGER` | No | — | Por defecto 0; se compara con el tope de iteraciones | RF-16 |
| `tokens_in` | Tokens de entrada | `INTEGER` | No | — | Por defecto 0; suma de la ejecución, contra el presupuesto de tokens | RF-16, RNF-06 |
| `tokens_out` | Tokens de salida | `INTEGER` | No | — | Por defecto 0 | RF-16, RNF-06 |
| `cost_usd` | Costo estimado | `NUMERIC(10,6)` | Sí | — | Costo por tarea, para la línea base de cifras; 0 en la capa gratuita | RNF-06, OBJ-11 |
| `error_message` | Mensaje de error | `VARCHAR(500)` | Sí | — | Mensaje honesto para el usuario; nunca un stack trace | RF-19, RNF-04 |
| `started_at` | Inicio | `TIMESTAMPTZ` | Sí | — | | RF-22 |
| `finished_at` | Fin | `TIMESTAMPTZ` | Sí | — | La latencia se mide como mediana sobre `finished_at - started_at` | OBJ-11 |
| `created_at` | Creado | `TIMESTAMPTZ` | No | — | | V1 |

**Índices:** `(patient_id, created_at DESC)` y `(status)`. El número de ejecuciones simultáneas lo acota el ejecutor en proceso (D24), no la BD.

### 4.12 `agent_steps` — Pasos de la ejecución, la traza (M2-N)

Una fila por cosa que pasó: cada llamada al modelo, cada herramienta, cada validación, límite o fallo. Su orden es la fuente de eventos del progreso por SSE y el punto desde donde se reanuda (D24).

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `BIGINT` | No | PK | Autoincremental y creciente: la conexión de progreso se reanuda desde el último `id` visto | D24 |
| `run_id` | Ejecución | `UUID` | No | FK → `agent_runs` | | RF-22 |
| `seq` | Orden | `INTEGER` | No | — | Posición dentro de la ejecución. `UNIQUE (run_id, seq)` | RF-22 |
| `step_type` | Tipo de paso | `VARCHAR(20)` | No | — | Valores en §3.2 | RF-22 |
| `tool_name` | Herramienta | `VARCHAR(60)` | Sí | — | Solo en `HERRAMIENTA`. Una de las herramientas autorizadas, pocas y de mínimo privilegio | RF-09, RF-22 |
| `message` | Mensaje de estado | `VARCHAR(300)` | Sí | — | Texto honesto que ve la persona («Consultando registros…»); sin stack trace | D24, RNF-05 |
| `params` | Parámetros | `JSONB` | Sí | — | Qué se pidió a la herramienta o al modelo | RF-22 |
| `result` | Resultado | `JSONB` | Sí | — | Qué devolvió. Datos sintéticos; no incluye el alias del paciente | RF-22, RNF-02 |
| `is_error` | Es un error | `BOOLEAN` | No | — | Por defecto `FALSE`. Un error de herramienta no mata el bucle: se devuelve al modelo como observación | RF-16, RNF-04 |
| `duration_ms` | Duración (ms) | `INTEGER` | Sí | — | | RF-22 |
| `tokens_in` | Tokens de entrada | `INTEGER` | Sí | — | | RF-22 |
| `tokens_out` | Tokens de salida | `INTEGER` | Sí | — | | RF-22 |
| `created_at` | Cuándo | `TIMESTAMPTZ` | No | — | | RF-22 |

### 4.13 `audit_logs` — Auditoría (M2-S)

Una fila por acción sensible. Solo se agregan filas; nunca se actualizan ni se borran.

| Columna | Etiqueta | Tipo | Nulo | Clave | Descripción y reglas | Origen |
|---|---|---|---|---|---|---|
| `id` | Id | `BIGINT` | No | PK | Autoincremental | RF-35 |
| `actor_id` | Quién | `UUID` | No | FK → `app_users` | Usuario de demostración elegido (D26). Para un registro del médico identifica qué médico lo hizo | RF-35, D12, D26 |
| `actor_role` | Rol de quien actúa | `VARCHAR(20)` | No | — | Rol con el que actuó en ese momento | RF-35 |
| `action` | Acción | `VARCHAR(40)` | No | — | Valores en §3.2 | RF-35 |
| `entity_type` | Tipo de objeto | `VARCHAR(30)` | No | — | `ENTRY` o `REPORT` | RF-35 |
| `entity_id` | Objeto afectado | `UUID` | No | — | Id de la entrada o del informe; sin clave foránea porque apunta a varias tablas | RF-35 |
| `occurred_at` | Cuándo | `TIMESTAMPTZ` | No | — | Por defecto `CURRENT_TIMESTAMP` | RF-35 |
| `before_state` | Antes | `JSONB` | Sí | — | Estado previo; vacío si la acción crea el objeto | RF-35 |
| `after_state` | Después | `JSONB` | Sí | — | Estado resultante | RF-35 |

**Índices:** `(entity_type, entity_id)` y `(actor_id, occurred_at DESC)`.

## 5. Reglas de integridad

### 5.1 Qué garantiza la base de datos y qué el servidor

| Regla | Dónde se garantiza | Cómo | Origen |
|---|---|---|---|
| Solo enfermería aprueba o rechaza | Servidor y BD | El servidor comprueba el rol de `reviewed_by` y valida la transición; la BD exige `reviewed_by` y `reviewed_at` en `APROBADO` y `RECHAZADO` | RN-02, RF-26 |
| El médico solo ve informes `APROBADO` | Servidor | Es una regla de estado: va en la consulta (`status = 'APROBADO'` y la versión de `approved_version_no`), no en un permiso | RN-01, RF-28 |
| Una semana va de lunes a domingo; el mensual son 28 días | BD | `CHECK` sobre `period_start` y `period_end` en `reports` | RN-06, RN-10 |
| El mensual solo se genera con cuatro semanas consecutivas y aprobadas | Servidor y `monthly_report_weeks` | El servidor verifica las cuatro semanas antes de pedir el informe y, si falta una, no lo genera e indica cuál | RN-10, RF-36 |
| Toda afirmación cita al menos una entrada | Servidor | El validador exige una fila en `report_citations` por cada `claim_key` y que las entradas sean del mismo paciente | RN-05, RF-12 |
| Toda cifra del informe la calcula el código | Servidor | Se compara `content.metrics` con el módulo de métricas; si una cifra no coincide se rechaza y se reintenta | RN-04, RF-13 |
| El texto original no cambia | Servidor | No hay operación de actualización de `entries`, que ni siquiera tiene `updated_at`. Endurecerlo con permisos de BD (solo `INSERT` y `SELECT` sobre `entries`, `entry_versions`, `report_versions`, `agent_steps` y `audit_logs`) queda para el hardening del Momento 3 | RN-07, RNF-10 |
| Ningún evento se guarda sin confirmación | BD y servidor | `confirmed_by` y `confirmed_at` son `NOT NULL`; la entrada y sus eventos confirmados se guardan en una sola transacción, y descartar no guarda nada | RN-11, RF-40 |
| Un síntoma aparece una vez por entrada y es del catálogo | BD | `UNIQUE (entry_id, symptom)` y `CHECK` sobre `entry_events.symptom` (§3.3) **[Propuesta]** | RF-06, D18 |
| La traza de un informe se lee desde el informe | Servidor | Enfermería lee los `agent_steps` de la ejecución de cualquier informe de sus pacientes; el médico, solo los del informe `APROBADO` que ve. No hay módulo global de trazas en el Momento 2 **[Propuesta]** | RF-24, RF-29, RN-01 |
| Las entradas y los informes no se borran | BD | Ninguna clave foránea del dominio usa `ON DELETE CASCADE` y no hay operación de borrado | RNF-10 |
| Orden de las entradas del mismo día | BD | `created_at`, con el índice `(patient_id, entry_date, created_at)` | RN-09 |
| La semilla es idempotente y nunca corre en producción | Servidor | UUID fijos y `ON CONFLICT DO NOTHING`; solo con `APP_SEED_DEMO=true`; el perfil `prod` la rechaza | RF-05, RF-39, D23 |

### 5.2 Transiciones del estado del informe

| Desde | Hacia | Quién o qué | Hito | Origen |
|---|---|---|---|---|
| (generación) | `PENDIENTE_REVISION` | El agente termina con un informe válido y con fuente por afirmación | M2-N | RF-17, RF-23 |
| (generación) | `INCOMPLETO` | Se agotó un límite; el motivo queda en `status_reason` | M2-N | RF-17 |
| `PENDIENTE_REVISION` | `APROBADO` | Enfermería | M2-N | RN-02, RF-26 |
| `PENDIENTE_REVISION` | `RECHAZADO` | Enfermería, con motivo; después se pide un informe nuevo | M2-S | RF-27 |
| `INCOMPLETO` | ninguna | Estado final: enfermería lo ve con su motivo y pide un informe nuevo. No se aprueba ni se rechaza, y el médico no lo ve **[Propuesta]** | M2-N | RF-17; punto 8 de §8 |
| `APROBADO`, `RECHAZADO` | ninguna | Estados finales: un cambio se hace con un informe nuevo **[Propuesta]** | — | RF-23 |

## 6. Qué sale al proveedor del LLM y qué no se guarda

| Dato | ¿Sale al proveedor? | Nota | Origen |
|---|---|---|---|
| `patients.id` | Sí | Único identificador del paciente que se envía | RF-01, RNF-02 |
| `entries.body` y los eventos de las entradas | Sí | Texto sintético; al estructurar una entrada y como resultado de las herramientas del agente. Se trata como dato, no como instrucción | RNF-02, RF-21 |
| `patients.alias`, `bed`, `age`, `admitted_on`, `app_users.display_name` y `email` | No | Nunca van al proveedor ni a la traza | RNF-02 |
| `app_users.password_hash` | No | | RNF-01 |
| Audio de dictado | No se guarda | Solo queda el texto transcrito, con `source = 'VOZ'` | RF-38, RNF-02 |
| Métricas calculadas | No se guardan | Se calculan al pedirlas; el informe guarda una copia de las que usó | RF-07 |
| PDF exportado | No se guarda | Se genera al vuelo desde un informe `APROBADO` | RF-37 |
| Propuesta descartada de eventos | Solo en la traza | Lo que no se confirma no es un evento; queda en `agent_steps.result` de la estructuración | RN-11, RF-22 |

Lo que sale a terceros se declara en `docs/gobernanza.md` (Momento 3).

## 7. Migraciones y semilla del Momento 2

V1 y V2 existen y no se editan: se agregan migraciones nuevas. Los roles y permisos van en migración; los usuarios de demostración y el conjunto sintético los crea el `ApplicationRunner` idempotente (D23, RF-05, RF-39).

| Migración | Qué hace | Hito |
|---|---|---|
| `V3__domain_schema.sql` | En `app_users`: agrega `display_name` e `is_demo` y permite `NULL` en `password_hash`. Crea `patients`, `entries`, `entry_events`, `entry_versions`, `reports`, `report_versions`, `report_citations`, `monthly_report_weeks`, `agent_runs`, `agent_steps` y `audit_logs`, con sus `CHECK`, claves e índices | M2-N (`entry_versions` y `audit_logs` M2-S) |
| `V4__seed_domain_rbac.sql` | Siembra los roles `ENFERMERIA`, `MEDICO` y `AGENTE` y los permisos de §3.4 con sus asignaciones, con UUID fijos y `ON CONFLICT DO NOTHING` como V2. No toca `ADMIN` ni sus permisos | M2-N |
| `ApplicationRunner` de la semilla | Con `APP_SEED_DEMO=true` y nunca en `prod`: crea lo de la tabla siguiente. No llama al LLM | M2-N |

### 7.1 Contenido de la semilla (D11)

Fechas fijas: las semanas van de lunes a domingo y la última termina el domingo 25 de octubre de 2026. Cada paciente prueba una cosa; el reparto está en `SPEC.md` D11.

| Tabla | Filas | Qué lleva |
|---|---|---|
| `app_users` y `app_user_roles` | 4 | Dos de enfermería y dos médicos con nombres ficticios, `is_demo = TRUE` y sin contraseña **[Propuesta]**. Dos médicos para poder mostrar qué médico registró cada entrada (D12) |
| `patients` | 5 | Alias ficticios. Semanas por paciente: 5, 4, 3, 4 y 2 (18 semanas-paciente) |
| `entries` | unas 120 | Una por día con registro, salvo los huecos de P3. `source = 'TEXTO'`, `structuring_status = 'ESTRUCTURADA'`, autor alternado entre los usuarios de demostración. `created_at` fijo (propuesta: las 18:00 de `America/Bogota` del día de la entrada) para que el orden dentro del día sea estable |
| `entry_events` | uno o dos por entrada con síntomas | `origin = 'IA'`, `confirmed_by` un usuario de demostración de enfermería y `confirmed_at` el día de la entrada. P4 casi no tiene: sus negaciones («sin vómito») no crean eventos (RN-11) |
| `reports` y `report_versions` | 5 y 5 | `SEMANAL`. De P1, las semanas 1 a 4 en `APROBADO` (permiten el mensual, RN-10), con `approved_version_no = 1`, `reviewed_by` una enfermera y `reviewed_at`. De P2, la semana 3 en `PENDIENTE_REVISION`. `requested_by` una enfermera. Versión 1 con `origin = 'MODELO'`; `run_id` vacío |
| `report_citations` | al menos una por afirmación | Entradas sembradas del mismo paciente y de la semana del informe (RN-05) |
| `monthly_report_weeks`, `entry_versions`, `agent_runs`, `agent_steps`, `audit_logs` | 0 | El mensual de P1 se genera en vivo y deja sus filas entonces; las ejecuciones, las versiones de texto y la auditoría las crea el uso |

Los informes sembrados se pueden generar una vez con el agente real, revisar a mano y guardar como semilla, para que el arranque no llame al LLM. Mientras el agente no exista, se escriben a mano con las cifras que calcula el código.

## 8. Los 13 puntos abiertos del diccionario completo, cerrados para el Momento 2

Cada fila es una **[Propuesta]** para poder escribir `V3` y `V4` ya. Lo que cambia en las tablas está en la tercera columna; la cuarta dice qué documento hay que ajustar si se confirma.

| # | Punto | Propuesta para el Momento 2 | Efecto en este diccionario | Si se confirma, ajustar |
|---|---|---|---|---|
| 1 | Trazas | La traza de una ejecución es parte de la evidencia de su informe: se lee con `REPORTS_READ` desde el informe. Enfermería ve la de cualquier informe de sus pacientes y el médico la del informe aprobado que ve. No hay módulo global de trazas ni permiso `TRACES_READ` | §3.4 y una fila en §5.1 | Matriz de `SPEC.md` §4 (módulo Trazas) |
| 2 | Permisos `ROLES_CREATE` y `ROLES_DELETE` de V2 | No aplica al Momento 2: son de `ADMIN`, que llega en el Momento 3. V2 queda como está | Ninguno | Se resuelve en el Momento 3 |
| 3 | `password_hash` en el Momento 2 | `NULL` en todos los usuarios de demostración; sin hash de relleno. El Momento 3 lo vuelve obligatorio | §4.1 | — |
| 4 | Fecha del evento | El evento vale la fecha de su entrada, como dice D27 («La fecha de la entrada»). No hay `event_date`. Si el texto nombra otro día, la persona registra la entrada con esa fecha | Se quita `entry_events.event_date`; las métricas unen con `entries.entry_date` (§4.5) | Confirmar que el modelo no propone fechas |
| 5 | Descripción normalizada | Lista cerrada de nueve síntomas, la del catálogo de la pantalla de registro de los mockups | `description` pasa a `symptom` con `CHECK` (§3.3, §4.5) | D18: validarla con una persona de enfermería |
| 6 | Intensidad y veces | Intensidad: `LEVE`, `MODERADO`, `INTENSO`, `SIN_INDICAR`, las cuatro opciones de la pantalla. Veces: las del día de la entrada, por evento. Las métricas cuentan días con el síntoma (`entry_date` distintos) y suman las veces indicadas | §3.2 y §4.5 | RF-07, que hoy no las usa; si no entran en las métricas, quedan como dato de la entrada |
| 7 | Qué es «sin estructurar» | `SIN_ESTRUCTURAR` si se guardó sin confirmar la propuesta; `ESTRUCTURADA` si una persona la confirmó, aunque sea con cero eventos (un texto sin síntomas no es un fallo) | §3.2 y §4.4 | Redacción de RF-41 («sin eventos confirmados») |
| 8 | Informe `INCOMPLETO` | Estado final: enfermería lo ve con su motivo y pide uno nuevo; no se aprueba ni se rechaza y el médico no lo ve | §5.2 | RF-17 y RF-23 |
| 9 | Paciente dado de alta | No se modela en el Momento 2: los pacientes sintéticos no se dan de baja y `patients` no tiene fecha de alta ni estado | Ninguno | El caso borde de la SPEC sigue sin comportamiento esperado |
| 10 | Zona horaria | `America/Bogota` para decidir «hoy» y los límites de la semana; la BD no cambia | §1 | — |
| 11 | Informe nuevo tras un rechazo | No se agrega `replaces_report_id`: un rechazado y su reemplazo se relacionan por paciente, tipo y período | Ninguno | — |
| 12 | Quién pide la generación | Se mantiene `REPORTS_REQUEST`, solo para enfermería | §3.4 | Matriz de `SPEC.md` §4 (Informes: «crear» pasa a «solicitar») |
| 13 | Editar los eventos de una entrada ya guardada | No en el Momento 2: limitación conocida. Corregir el texto (RF-42, M2-S) no cambia los eventos | Ninguno | Decidir si se amplía cuando haya tiempo |

## 9. Diferencias con el diccionario completo

| Elemento | En el completo | En el Momento 2 | Motivo |
|---|---|---|---|
| `entry_events.event_date` | Columna con la fecha del evento | No existe; vale `entries.entry_date` | Punto 4 de §8 |
| `entry_events.description` | Texto de hasta 100 caracteres, lista cerrada o libre por decidir | `symptom`, código de una lista cerrada de nueve, con `CHECK` | Punto 5 de §8 |
| `entry_events`, restricciones | `CHECK` de veces | Además `UNIQUE (entry_id, symptom)` | Contar días con síntoma sin duplicados dentro de la entrada |
| Índices de `entry_events` | `(entry_id)` y `(event_type, description, event_date)` | `(entry_id)` y `(symptom)` | Se quita la fecha |
| Rol `ADMIN` | Fila del catálogo, M3 | Solo una nota: existe por V2 y no se usa | Alcance |
| Permisos `USERS_*`, `ROLES_*`, `PERMISSIONS_READ`, `TRACES_READ` | En el catálogo de permisos, M3 | Fuera del catálogo; los de V2 siguen sembrados y sin uso | Alcance, punto 1 de §8 |
| Columna `ADMIN` de la matriz de permisos | Presente | Ausente | Alcance |
| `audit_logs.action` y `entity_type` | Incluyen `CAMBIAR_PERMISOS` y `ROLE_PERMISSION` | No los incluyen | Cambiar permisos es RF-34, del Momento 3 |
| `reports.status = 'INCOMPLETO'` | Transiciones por definir | Estado final | Punto 8 de §8 |
| `app_users.email`, `password_hash`, `active` | Descritos para el inicio de sesión del Momento 3 | Solo identificación; hash siempre vacío; `active` sin pantalla | Alcance |
| Texto original inmutable en la BD | «Servidor, y BD desde el Momento 3» | Servidor; los permisos de BD quedan para el hardening del Momento 3 | Alcance |
| Semilla | Describe 5 informes y la credencial de M3 | Detalla filas por tabla y deja fuera credenciales y administrador | Alcance |
| Trazabilidad | RF-01 a RF-42 | Sin RF-31, RF-33 ni RF-34 | Alcance |

## 10. Trazabilidad requisito → tablas

| Requisito | Tablas y columnas |
|---|---|
| RF-01 | `patients` |
| RF-02, RF-04 | `entries` |
| RF-03, RF-42 | `entry_versions`, `entries.body` (original) |
| RF-05, RF-39 | `app_users.is_demo`, semilla de todas las tablas |
| RF-06, RF-40 | `entry_events` |
| RF-41 | `entries.structuring_status`, `agent_runs`, `agent_steps` |
| RF-07 | Ninguna propia: se calcula desde `entries` y `entry_events` |
| RF-08, RF-36 | `reports`, `monthly_report_weeks`, `agent_runs` |
| RF-09, RF-32 | `app_roles`, `app_permissions`, `app_user_roles`, `app_role_permissions` |
| RF-10, RF-16, RF-17, RF-19, RF-20, RF-22 | `agent_runs`, `agent_steps` |
| RF-11, RF-13, RF-14 | `report_versions.content` |
| RF-12, RF-24, RF-29 | `report_citations`, `report_versions.content.claims`, `agent_steps` |
| RF-15, RF-21 | Ninguna: son reglas del prompt, de la validación y de las herramientas disponibles |
| RF-18 | `report_versions.plain_text` |
| RF-23, RF-26, RF-27, RF-28 | `reports` (`status`, `status_reason`, `reviewed_by`, `reviewed_at`, `approved_version_no`) |
| RF-25 | `report_versions` |
| RF-30 | `entries.author_id` y el permiso `ENTRIES_CREATE` del médico |
| RF-35 | `audit_logs` |
| RF-37 (Opc) | `reports.provider` y `reports.model`; el PDF no se guarda |
| RF-38 (Opc) | `entries.source` |
