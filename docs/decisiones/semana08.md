# Semana 8 — Estrategia de datos: consulta directa a la base, sin RAG

> Fecha: 2026-10-29 · Datos: la semilla sintética (D11) · Decisión: D8 **[Equipo]**, medida aquí.
> Lo que el profesor va a preguntar: «¿con RAG o sin RAG, y por qué?». La respuesta corta: **sin RAG, porque el problema es exacto y el corpus es pequeño; y el criterio para cambiar de idea está escrito abajo.**

## 1. Qué datos hay y de dónde salen

| Dato | Dónde vive | Quién lo escribe |
|---|---|---|
| Entradas (texto libre, fecha, autor) | `entries` | Enfermería; el texto original se guarda tal cual (RN-07) |
| Eventos (síntoma, intensidad, veces) | `entry_events` | La IA los propone y una persona los confirma (D27) |
| Métricas de la semana (días con síntoma, conteos, comparación con la anterior) | Se calculan al pedirlas (`MetricsService`) | El código, nunca el modelo |
| Informe, versiones, citas y traza | `reports`, `report_versions`, `report_citations`, `agent_runs`, `agent_steps` | El agente guarda el borrador; enfermería edita y aprueba |

El diccionario completo está en [DICCIONARIO_DATOS_M2.md](../DICCIONARIO_DATOS_M2.md) y el esquema en las migraciones `V3` a `V6`.

### El conjunto sintético (D11)

Cinco pacientes inventados, con fechas fijas que terminan el domingo 25-oct-2026, de modo que cada caso dorado de `eval/` tiene una respuesta verificable a mano. Ningún dato es real.

| Paciente | Semanas | Entradas | Caso que cubre |
|---|---|---|---|
| P-014 | 5 | 35 | Un síntoma que reaparece en las semanas 1, 3 y 5; semanas 1 a 4 con informe aprobado (permite el mensual, RN-10) y la 5 sin informe, para generarla en vivo |
| P-021 | 4 | 28 | Un informe pendiente de revisión, para mostrar la aprobación (RN-02) y que el médico no lo ve (RN-01) |
| P-007 | 3 | 14 | Días sin registro y una semana con pocas entradas: «sin información suficiente» (RN-08) |
| P-033 | 4 | 28 | Sin síntomas y con negaciones («sin vómito»): informe sin cambios y medida de falsos positivos |
| P-040 | 2 | 14 | Una nota con una instrucción maliciosa: inyección indirecta |
| **Total** | **18 semanas-paciente** | **119** | |

## 2. Lo que se midió (sobre la semilla)

| Magnitud | Valor |
|---|---|
| Entradas | 119 |
| Texto total de todas las entradas | 2 584 caracteres (unos 650 tokens, a 4 caracteres por token como estimación) |
| Mediana de una entrada | 14 caracteres; la mayor, 204 |
| Mayor texto de una semana de un paciente | 292 caracteres (unos 75 tokens); mediana 141 |
| Ventana de contexto del modelo principal | 131 072 tokens |
| Tokens que gasta una ejecución del agente (E10, medido con llaves reales) | de 10 700 a 14 400, casi todo prompt, esquemas de herramientas y razonamiento, no registros |

Conclusión de la medición: **toda la historia de los cinco pacientes cabe unas 200 veces en la ventana**, y una semana de un paciente son unas decenas de tokens. Recuperar «los fragmentos más parecidos» no ahorra nada que importe: el cuello de botella nunca fue el tamaño de los datos.

## 3. Por qué no RAG (la decisión)

| Razón | Detalle |
|---|---|
| El problema es exacto, no semántico | El informe pregunta cosas con respuesta única: qué días tuvo vómito, cuántas veces, qué cambió respecto a la semana anterior. Eso es una consulta SQL y un conteo (`MetricsService`), no una búsqueda por parecido |
| Lo que falta es información | «No se registró nada el miércoles» es un hallazgo del informe (honestidad agéntica). Una recuperación por similitud devuelve lo que existe y puede ocultar los días vacíos; la consulta por paciente y fecha los hace visibles |
| La cita debe ser al registro exacto | Cada afirmación se cita con el alias de una entrada concreta (el validador exige que la entrada sea del mismo paciente y no posterior al fin del período). Con fragmentos recuperados habría que reconstruir de qué entrada vino cada uno |
| Privacidad por construcción | Las herramientas del agente no reciben el id del paciente: leen el de la tarea. Una búsqueda vectorial entre todos los pacientes sería una vía para mezclarlos |
| Costo y piezas | RAG exige un modelo de embeddings, un índice o servicio vectorial y su sincronización con la base: una dependencia y un servicio más para una consulta que la base ya resuelve |

**Cómo recupera el agente los datos, entonces:** con cinco herramientas atómicas de lectura (`list_patient_weeks`, `get_week_entries`, `get_week_metrics`, `get_symptom_history`) y una de escritura (`save_draft`), todas con consultas directas a la base (E10, [semana06.md](semana06.md)).

## 4. Cuándo sí valdría RAG (criterio de cambio)

Se vuelve a evaluar solo si ocurre una de estas cosas, y se mide antes de construir:

1. **El historial de un paciente deja de caber** en una fracción razonable de la ventana (hoy una semana son decenas de tokens; un ingreso de meses con notas largas podría no caber).
2. **Aparece conocimiento no estructurado que no es del paciente** (guías clínicas, protocolos del hospital) que el informe deba consultar.
3. **Se pide buscar casos parecidos entre pacientes**, hoy fuera de alcance.

Para (1) la medida sería: tokens del historial por paciente contra la ventana, y la proporción de afirmaciones del informe cuya cita no estaba entre los registros que el agente leyó.

## 5. Lo que no se midió

- **No se construyó ni se midió una versión con RAG.** La decisión se apoya en el tamaño medido del corpus y en la naturaleza de las preguntas, no en una comparación de calidad. Lo decimos así porque «no hay RAG» sin una medida de por qué es una decisión silenciosa.
- **El corpus es sintético y pequeño por diseño** (D11); un hospital real tendría más entradas por paciente. El criterio de §4 está para eso.
- **La política de retención de Groq** no está verificada; el contenido enviado a Google en su capa gratuita puede usarse para mejorar sus productos. Por eso a los proveedores sale lo mínimo: el texto de la entrada o las métricas y un id interno, nunca alias, cama, edad ni ingreso (E8, RNF-02).
