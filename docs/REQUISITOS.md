# REQUISITOS — Patient Progress Auditor

> **Estado:** borrador v0.1 para aprobación · **Fase del ciclo de vida:** 2 — Análisis de requisitos · **Fecha:** 2026-10-23
> **Base:** `docs/SPEC.md` v0.4 (§1–§3, objetivos OBJ-01 a OBJ-15), `AGENTS.md` y las rúbricas del curso *Ingeniería de Aplicaciones con IA*.
>
> **Convenciones:** RF = requisito funcional · RNF = no funcional · RN = regla de negocio · CU = caso de uso. Cada requisito tiene una traza hacia los objetivos de la SPEC y un criterio de aceptación que se responde con sí o no.
>
> **Hito / prioridad:** **M2-N** núcleo del Momento 2 (lo exige la rúbrica o el control humano; sin esto no hay demo) · **M2-S** Momento 2 si hay tiempo · **M3** Momento 3 · **Opc** opcional.
>
> **Origen:** **[Curso]** · **[Equipo]** (presentación del proyecto y `AGENTS.md`) · **[Propuesta]** (este borrador, por confirmar).
>
> Este documento describe **qué** debe hacer el sistema, no **cómo**. Stack, arquitectura, modelo de datos y herramientas concretas del agente son de la fase de diseño.

---

## 1. Necesidades de los interesados

| Actor | Necesidad | Origen | Requisitos |
|---|---|---|---|
| Enfermería | No releer todos los registros ni redactar el informe a mano cada semana | [Equipo] | RF-08 a RF-13 |
| Enfermería | Ver la evolución frente a semanas anteriores sin que se pierda un síntoma que reaparece | [Equipo] | RF-07, RF-10 |
| Enfermería | Revisar rápido con la evidencia a la mano, corregir y aprobar | [Propuesta] | RF-24 a RF-27 |
| Enfermería | Registrar cada día sin fricción: texto libre y, opcionalmente, voz | [Equipo] | RF-02, RF-38 |
| Médico | Recibir informes consistentes y aprobados que muestren la evolución en el tiempo | [Equipo] | RF-28, RF-29, RF-36 |
| Médico | Registrar entradas diarias cuando le corresponda | [Equipo] | RF-30 |
| Administrador | Gestionar usuarios y permisos sin acceder a datos clínicos | [Propuesta] | RF-31 a RF-34 |
| Evaluador del curso | Ver en 6 minutos el agente funcionando con límites, trazas y fallos forzados | [Curso] | RF-16 a RF-22, RNF-08 |

---

## 2. Casos de uso

| CU | Actor | Qué hace | Requisitos |
|---|---|---|---|
| CU-01 Registrar entrada diaria | Enfermería, Médico | Escribe (o dicta) cómo está el paciente hoy | RF-02, RF-03, RF-30, RF-38 |
| CU-02 Generar borrador semanal | Enfermería | Pide el informe de un paciente y una semana; el agente lo redacta | RF-06 a RF-22 |
| CU-03 Revisar y corregir | Enfermería | Lee el borrador, abre la evidencia y lo corrige | RF-24, RF-25 |
| CU-04 Aprobar o rechazar | Enfermería | Decide; el sistema registra quién y cuándo | RF-23, RF-26, RF-27, RF-35 |
| CU-05 Consultar informes aprobados | Médico | Ve la evolución del paciente por período | RF-28, RF-29 |
| CU-06 Gestionar usuarios y permisos | Administrador | Crea usuarios, ve y (M3) edita permisos | RF-31 a RF-34 |
| CU-07 Ver la traza de una ejecución | Enfermería; equipo en la demo | Ve qué consultó el agente y con qué resultado | RF-22 |
| CU-08 Generar informe mensual | Enfermería | Consolida el mes | RF-36 |
| CU-09 Exportar a PDF | Médico, Enfermería | Descarga un informe aprobado | RF-37 |

### Flujo crítico: generar → revisar → aprobar → consultar

1. Enfermería elige paciente y semana y pide el borrador (RF-08).
2. El agente consulta con sus herramientas, decide cuántas semanas anteriores revisar y redacta. El código valida el esquema, las citas y las cifras (RF-09 a RF-13).
3. El informe queda `PENDIENTE_REVISION`, o `INCOMPLETO` si se agotó un límite (RF-17).
4. Enfermería lee, abre la evidencia y corrige si hace falta (RF-24, RF-25).
5. Enfermería aprueba (RF-26) o rechaza con motivo (RF-27).
6. El médico ve el informe aprobado (RF-28).

**Excepciones:** semana sin datos suficientes, el informe lo declara (RF-14) · fallo del proveedor, mensaje honesto y respaldo (RF-19) · entrada con instrucciones, se trata como dato (RF-21).

---

## 3. Requisitos funcionales

### 3.1 Pacientes y entradas

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-01 | Crear pacientes con un id interno y un alias legible; el LLM solo recibe el id interno | M2-N | OBJ-05, RNF-02 | ¿Existe el paciente creado y las trazas del agente muestran solo el id interno, sin alias? |
| RF-02 | Registrar una entrada diaria en texto libre asociada a paciente, fecha y autor, sin plantilla obligatoria | M2-N | OBJ-01 | ¿La entrada queda guardada con paciente, fecha y autor al enviarla en texto libre? |
| RF-03 | Permitir corregir una entrada conservando la versión anterior y quién la cambió | M2-S | OBJ-05 | Tras editar, ¿se puede ver la versión previa y el autor del cambio? |
| RF-04 | Listar las entradas de un paciente por fecha y filtrar por rango | M2-N | OBJ-04 | Con 10 entradas cargadas desordenadas, ¿salen en orden cronológico y el filtro devuelve solo las del rango? |
| RF-05 | Cargar el conjunto sintético de demostración con un comando | M2-N | OBJ-13 | Tras `./run.sh`, ¿existen los pacientes y semanas del conjunto sintético? |

### 3.2 Eventos y métricas (el código cuenta, el LLM interpreta)

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-06 | Derivar de cada entrada eventos estructurados (tipo, descripción normalizada, fecha) mediante el LLM, validados contra un esquema, guardados aparte del texto original y enlazados a su entrada | M2-N | OBJ-02, OBJ-04 | ¿Cada evento valida contra el esquema y enlaza a su entrada original? |
| RF-07 | Calcular en código, a partir de entradas y eventos, las métricas semanales por paciente: días con entrada, días sin entrada, eventos por tipo, días con cada síntoma, variación frente a la semana anterior y síntomas nuevos, persistentes, desaparecidos y reaparecidos | M2-N | OBJ-02, OBJ-04 | En cada caso dorado, ¿las métricas coinciden con el cálculo manual? |

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
| RF-20 | Permitir cambiar el proveedor del LLM por configuración y contar con un adaptador simulado (replay) | M2-N | OBJ-10, OBJ-13 | ¿Cambiar la variable de configuración cambia de proveedor sin tocar código, y la demo corre sin internet con el simulado? |
| RF-21 | Tratar el texto de las entradas como dato y no como instrucción, y no dar al agente herramientas de salida (internet, correo, mensajes) | M2-N | OBJ-14, RNF-03 | ¿Una entrada con instrucciones maliciosas no altera el comportamiento del agente, y su inventario no incluye ninguna salida? |
| RF-22 | Registrar la traza de cada ejecución (herramienta, parámetros, resultado, ms, tokens) y mostrarla en la app | M2-N | OBJ-11 | ¿Se ve en vivo la traza de una ejecución con esos cinco datos? |

### 3.4 Revisión y aprobación

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-23 | Manejar los estados del informe `PENDIENTE_REVISION`, `INCOMPLETO`, `APROBADO` y `RECHAZADO`, con transiciones validadas en el servidor | M2-N | OBJ-05 | ¿Una transición no definida es rechazada por el servidor? |
| RF-24 | Mostrar a enfermería el informe en lenguaje claro y, a un clic, la evidencia: entradas citadas, criterio de comparación y llamadas de herramientas | M2-N | OBJ-06 | ¿Desde el informe se llega a cada entrada citada en un clic? |
| RF-25 | Permitir a enfermería editar el borrador antes de aprobar, conservando la versión original del modelo y las ediciones | M2-S | OBJ-06 | Tras editar, ¿existen la versión del modelo y la editada? |
| RF-26 | Permitir aprobar un informe solo al rol enfermería y registrar quién y cuándo | M2-N | OBJ-05 | ¿Un usuario médico recibe rechazo al aprobar, y el informe aprobado muestra aprobador y fecha? |
| RF-27 | Permitir a enfermería rechazar un informe con motivo y pedir uno nuevo | M2-S | OBJ-05 | ¿El rechazo guarda el motivo y permite generar un nuevo borrador? |

### 3.5 Consulta del médico

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-28 | Permitir al médico listar pacientes y consultar los informes `APROBADO` por paciente y período, del más reciente al más antiguo | M2-N | OBJ-05 | ¿Un informe pendiente, incompleto o rechazado NO aparece para el médico? |
| RF-29 | Mostrar al médico, en un informe aprobado, la evidencia que lo respalda | M2-S | OBJ-06 | ¿Desde un informe aprobado el médico llega a las entradas citadas? |
| RF-30 | Permitir al médico registrar entradas diarias (supuesto D12) | M2-S | OBJ-15 | ¿Un usuario médico crea una entrada y queda con su autoría? |

### 3.6 Usuarios, roles y auditoría

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-31 | Autenticar con usuario y contraseña y usar un token con expiración | M2-N | OBJ-15, RNF-01 | Sin token válido, ¿el servidor rechaza cualquier operación? |
| RF-32 | Manejar los roles ENFERMERIA, MEDICO y ADMIN, más un rol técnico AGENTE, con permisos por módulo y acción según la matriz de la SPEC §4 | M2-N | OBJ-15 | ¿Cada rol puede hacer exactamente lo que dice la matriz y nada más? |
| RF-33 | Permitir al administrador crear, actualizar y desactivar usuarios, sin leer datos clínicos | M2-S | OBJ-15 | ¿Un administrador recibe rechazo al consultar entradas o informes? |
| RF-34 | Mostrar el tablero de permisos por rol: lectura en M2, edición en M3, con el permiso Aprobar fijo para enfermería | M2-S (lectura) · M3 (edición) | OBJ-15 | ¿El tablero muestra la matriz real y, en M3, rechaza asignar Aprobar a otro rol? |
| RF-35 | Registrar en auditoría las acciones sensibles: aprobar, rechazar, editar entradas o informes y cambiar permisos (quién, cuándo, antes y después) | M2-S | OBJ-05, OBJ-14 | ¿Cada acción sensible deja una fila de auditoría con esos cuatro datos? |

### 3.7 Momento 3 y opcionales

| ID | El sistema debe… | Hito | Traza | Criterio de aceptación (sí/no) |
|---|---|---|---|---|
| RF-36 | Generar el informe mensual a partir de las entradas y eventos del mes, mostrando la evolución semana a semana | M3 | OBJ-03 | Con un mes de cuatro semanas, ¿el informe muestra la evolución por semana? |
| RF-37 | Exportar a PDF un informe aprobado, con aprobador, fecha, id del informe y nota de asistencia de IA (D13) | Opc | — | ¿El texto extraído del PDF contiene la aprobación? |
| RF-38 | Dictar una entrada por voz con transcripción editable antes de guardar, origen `VOZ` y sin guardar el audio (D14) | Opc | — | ¿La entrada dictada se puede corregir antes de guardar y queda con origen `VOZ` sin audio almacenado? |

---

## 4. Requisitos no funcionales

| ID | Categoría | Requisito | Criterio de verificación (sí/no) |
|---|---|---|---|
| RNF-01 | Seguridad | Autorización en el servidor para toda operación; la UI solo oculta. Mínimo privilegio; credenciales fuera del repo y del historial; usuario de BD con permisos mínimos | ¿Una petición directa al API sin permiso es rechazada aunque la UI no muestre el botón? ¿No hay secretos en el repo ni en el historial? |
| RNF-02 | Privacidad | Solo datos sintéticos. Al LLM solo salen el id interno y el texto de las entradas, nunca nombre ni documento. Sin audio guardado | ¿Una revisión de trazas y repo no encuentra datos reales ni identificadores personales? |
| RNF-03 | Seguridad del agente | Hay datos privados y contenido no confiable; la tercera pata (salida hacia afuera) se mantiene cerrada | ¿El inventario de herramientas del agente no incluye ninguna que envíe datos fuera? |
| RNF-04 | Robustez | Sin stack trace al usuario; los tres fallos forzables (timeout, salida que no valida, caída o 429) se manejan | ¿Los tres fallos forzados se manejan con mensaje claro? |
| RNF-05 | Rendimiento | La generación no bloquea la interfaz: estados honestos o streaming. La latencia se mide como mediana y se reporta, sin umbral previo | ¿La UI muestra estados durante la generación y la mediana está en `docs/decisiones/semana10.md`? |
| RNF-06 | Costo | Costo cero: capa gratuita de los proveedores. El costo por tarea se mide | ¿La demo corre sin pagos y se reporta el costo por tarea? |
| RNF-07 | Observabilidad | Trazas y auditoría consultables; los fallos se explican con la traza | ¿Un fallo forzado se explica con su traza? |
| RNF-08 | Reproducibilidad | `./run.sh` o `docker compose --profile monitor up -d`; ZIP limpio; variables en `.env.example` | ¿Otra persona lo levanta en menos de 5 minutos siguiendo el README? |
| RNF-09 | Usabilidad | UI en español; lenguaje claro primero y evidencia a un clic; estados honestos | ¿Una persona de enfermería llega del informe a la evidencia en un clic? |
| RNF-10 | Integridad de datos | Las entradas y los informes no se borran, solo se corrigen con versión; las entradas son de solo lectura para el agente | ¿Existe alguna operación de borrado sobre entradas o informes? (debe ser no) |
| RNF-11 | Calidad | Pruebas automatizadas de las reglas críticas: aprobación, permisos, validador de cifras y citas, esquema. Commits convencionales. Cada integrante explica el código | ¿La verificación del proyecto pasa y cubre esas reglas? |
| RNF-12 | Cumplimiento del curso | Estructura de entrega, límites del agente, `eval/` y cifras con fecha, según `AGENTS.md` | ¿Existe cada entregable? |

---

## 5. Reglas de negocio

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Un informe solo llega al médico en estado `APROBADO` | [Equipo] |
| RN-02 | Solo enfermería aprueba; la transición se valida en el servidor | [Equipo] |
| RN-03 | El informe describe evolución documentada; no diagnostica ni recomienda tratamiento o dosis | [Equipo] |
| RN-04 | Toda cifra del informe la calcula el código, no el LLM | [Equipo] |
| RN-05 | Toda afirmación del informe cita al menos una entrada | [Curso] |
| RN-06 | Una semana va de lunes a domingo. El informe se compara con la semana anterior y el agente decide cuántas más atrás revisar, con tope | [Propuesta] (D10) |
| RN-07 | El agente nunca modifica las entradas originales | [Equipo] |
| RN-08 | Si faltan datos, el informe lo declara y no los rellena | [Equipo] |
| RN-09 | Se permiten varias entradas el mismo día; dentro del día se ordenan por hora de creación | [Propuesta] |

---

## 6. Modelo conceptual preliminar

Solo entidades y relaciones. Atributos, tipos y tablas se definen en el diseño.

| Entidad | Qué es | Relaciones clave |
|---|---|---|
| Usuario, Rol, Permiso | Quién entra y qué puede hacer | Un usuario tiene un rol; un rol agrupa permisos por módulo y acción |
| Paciente | Persona seguida, identificada por id interno | Tiene muchas entradas e informes |
| Entrada | Registro diario en texto libre (fecha, autor, versiones) | Pertenece a un paciente; tiene eventos derivados |
| Evento | Dato estructurado derivado por el LLM de una entrada (tipo, descripción normalizada, fecha) | Enlaza a su entrada original |
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
| OBJ-04 Cifras por código | RF-04, RF-06, RF-07, RF-13 |
| OBJ-05 Revisión humana exigida | RF-01, RF-03, RF-09, RF-23, RF-26 a RF-28, RF-35 |
| OBJ-06 Fuente y criterio visibles | RF-12, RF-24, RF-25, RF-29 |
| OBJ-07 Datos insuficientes | RF-14 |
| OBJ-08 No diagnosticar | RF-15 |
| OBJ-09 Límites y parada | RF-09, RF-16, RF-17 |
| OBJ-10 Resiliencia y proveedor | RF-18, RF-19, RF-20 |
| OBJ-11 Medición y trazas | RF-22, RNF-05 a RNF-07; entregables `eval/` y `docs/decisiones/semana10.md` |
| OBJ-12 Necesidad del LLM y del agente | RF-10; entregable: decisiones del agente y comparación contra la plantilla sin LLM |
| OBJ-13 Reproducibilidad y plan B | RF-05, RF-20, RNF-08 |
| OBJ-14 Seguridad y gobernanza | RF-21, RF-35, RNF-01 a RNF-03; entregables del Momento 3 |
| OBJ-15 Permisos por rol | RF-30 a RF-34 |

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

**Para el diseño (no bloquean el análisis):** stack (D9), estrategia de datos (D8), proveedor del LLM (D15), tamaño del conjunto sintético (D11), motor de PDF (D13), voz (D14) y dónde se derivan los eventos (al guardar la entrada o al generar el informe).

---

## 9. Criterios de salida de la fase de análisis

- [ ] Cada objetivo de la SPEC tiene al menos un requisito o entregable (§7).
- [ ] Cada requisito funcional tiene un criterio que se responde con sí o no.
- [ ] Los dos integrantes revisaron los supuestos de trabajo de §8.
- [ ] El catálogo de métricas (RF-07) y la estructura del informe (RF-11) se validaron con una persona de enfermería, o quedó registrado que no se pudo.
- [ ] La SPEC se actualizó con las decisiones D18 a D20 y los supuestos confirmados.
