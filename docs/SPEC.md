# SPEC — Patient Progress Auditor

> **Estado:** borrador v0.1 para aprobación · **Fase del ciclo de vida:** 1 — Planificación · **Fecha:** 2026-10-21
> **Equipo:** Breiner Martinez Muñoz y Andres Felipe Lopez Vivas · **Curso:** Ingeniería de Aplicaciones con IA (UCC)
>
> Este documento desarrolla las secciones §1 a §3 de la spec (resumen, objetivos, alcance) y deja sembradas §5 y §7. Las secciones §4 y §6 se completan en la fase de diseño.
>
> **Etiquetas de origen:** **[Curso]** lo exige el curso · **[Equipo]** ya lo definió el equipo (presentación del proyecto y `AGENTS.md`) · **[Propuesta]** lo propone este borrador y hay que confirmarlo. Ninguna meta numérica va sin etiqueta.

---

## §1. Resumen

**Qué se construye.** Un auditor de seguimiento de pacientes. Enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA consulta los registros de cada semana, los compara con semanas anteriores y redacta un borrador de informe semanal (y, más adelante, mensual). Enfermería lo revisa y aprueba antes de que llegue al médico.

**Qué problema resuelve.** Los registros diarios quedan dispersos. Al cerrar la semana, alguien debe releerlos todos, compararlos con la anterior y redactar el informe a mano. Eso consume tiempo y hace fácil que se pierdan patrones, como un síntoma que reaparece. Se buscan informes semanales y mensuales consistentes que muestren la evolución en el tiempo.

**Por qué necesita IA (prueba de necesidad).** Si se quita el modelo, los registros siguen existiendo pero el informe no. Las notas son texto libre ("vomitó el lunes, ánimo alto"), hay infinitas formas de escribirlas y no se pueden programar reglas para todas. El reparto es:

| Parte | Quién la resuelve | Por qué |
|---|---|---|
| Guardar, consultar, ordenar por fecha, calcular métricas, permisos, validaciones | Código determinista | Exigen exactitud |
| Interpretar el texto libre y redactar la comparación entre semanas | LLM | Espacio de entrada abierto, reglas infinitas |
| Contrastar las afirmaciones con los registros originales y aprobar | Humano (enfermería) | El error en salud es costoso |

**Qué no es.** Apoyo al seguimiento documental. No diagnostica, no receta y no sustituye el criterio profesional.

### Actores

| Actor | Qué hace con el sistema |
|---|---|
| Enfermería | Registra, dispara o consulta el informe, lo revisa, lo corrige y lo aprueba |
| Médico | Consulta los informes aprobados |
| Equipo de desarrollo | Construye, evalúa y sustenta |
| Evaluador del curso | Revisa la demo en vivo (6 min de demo + 3 de preguntas) |

---

## §2. Objetivos

### 2.1 Objetivo general

Reducir el trabajo manual de releer y redactar el seguimiento semanal de un paciente, entregando a enfermería un borrador comparativo con fuente verificable, sin que ningún informe llegue al médico sin aprobación humana. **[Equipo]**

### 2.2 Objetivos específicos y cómo se verifican

Cada objetivo se verifica con un sí o un no.

| ID | Objetivo | Verificación (sí/no) | Origen |
|---|---|---|---|
| **Producto** | | | |
| OBJ-01 | Generar un borrador de informe semanal que compare la semana con la(s) anterior(es), a partir de registros diarios en texto libre | Para cada caso dorado de la suite, el sistema produce un borrador que valida contra el esquema de salida y cubre ambas semanas | [Equipo] |
| OBJ-02 | Señalar patrones que reaparecen (p. ej. un síntoma que vuelve tras semanas sin él) | En los casos dorados con un patrón recurrente sembrado, el borrador lo menciona. La tasa de detección se registra como línea base | [Equipo] |
| OBJ-03 | Generar el informe mensual a partir de los registros del mes (hito del Momento 3) | Dado un mes con registros de cuatro semanas, el informe muestra la evolución semana a semana | [Equipo] · hito [Propuesta] |
| OBJ-04 | Que toda cifra del informe la calcule el código, no el LLM | En todos los casos dorados, las cifras del informe coinciden con las de la función de métricas y con el cálculo manual | [Equipo] |
| **Control humano y seguridad clínica** | | | |
| OBJ-05 | La revisión humana la exige el sistema, no es costumbre del operador | Prueba automatizada: un informe pendiente no es visible para el rol médico, y solo el rol enfermería puede aprobarlo | [Equipo] · [Curso] |
| OBJ-06 | Fuente y criterio visibles, y corregibles por enfermería | Cada afirmación enlaza a al menos un registro original (fecha + texto); el criterio de comparación se muestra; enfermería puede editar antes de aprobar | [Curso] |
| OBJ-07 | Honestidad ante datos insuficientes | En un caso con una semana sin registros, el informe lo declara y no rellena ni inventa | [Equipo] · [Curso] |
| OBJ-08 | No diagnosticar ni recomendar tratamiento | Al menos un caso de rechazo en la suite (pedir diagnóstico o dosis) donde el sistema se niega | [Curso] |
| **Agente y robustez** | | | |
| OBJ-09 | Agente con los cuatro límites activos: iteraciones, presupuesto de tokens, confirmación humana y timeout por herramienta | Los cuatro existen, tienen valor justificado y se pueden mostrar en la demo. Sin límite no hay entrega | [Curso] |
| OBJ-10 | Resiliencia y capa de proveedor desacoplada | Se fuerzan y se muestran tres fallos (timeout, salida que no valida, caída o 429) sin stack trace al usuario; el proveedor cambia por configuración | [Curso] |
| **Evidencia** | | | |
| OBJ-11 | Medir con método y mostrar trazas | `eval/` con 5 a 10 casos verificados a mano (al menos uno de rechazo); cuatro cifras con fecha y método en `docs/decisiones/semana10.md` (tasa de éxito, costo por tarea, mediana de latencia, recuperación si aplica); trazas visibles en vivo | [Curso] |
| OBJ-12 | Sustentar la necesidad del LLM y del agente frente a una solución convencional | Los seis ejes aplicados por componente, y la misma suite corrida contra una versión sin modelo (informe por plantilla con métricas por código) | [Equipo] · [Curso] |
| OBJ-13 | Reproducibilidad | `./run.sh` o `docker compose up --build` levanta todo; un compañero con el ZIP y el README lo corre en menos de 5 minutos | [Curso] |
| OBJ-14 | Seguridad y gobernanza (hito del Momento 3) | `docs/seguridad.md` (modelo de amenazas, tríada evaluada, ataque propio documentado, hardening con antes y después), `docs/gobernanza.md` (cinco preguntas) y `docs/respuesta_auditoria.md` | [Curso] |

### 2.3 Sobre las metas numéricas

No se fija un umbral de éxito del agente antes de medir la línea base. El curso califica el método (cifras con fecha, mediana, trazas) por encima del valor: un 6 de 10 sustentado con trazas puntúa más que un 10 de 10 sin evidencia. **[Curso]** El umbral, si se quiere, se define después de la primera medición.

---

## §3. Alcance

### 3.1 Dentro del alcance

| Capacidad | Momento 2 (31-oct-2026) | Momento 3 (semana 14) |
|---|---|---|
| Alta mínima de paciente con id interno y registros diarios en texto libre (formulario simple y carga de datos sintéticos) | Sí | |
| Consulta, orden por fechas y métricas semanales calculadas por código | Sí | |
| Agente con herramientas de lectura que consulta y compara semana vs anteriores | Sí | |
| Borrador de informe semanal con fuente y criterio visibles | Sí | |
| Flujo de revisión y aprobación por enfermería; vista del médico solo con informes aprobados | Sí | |
| Autenticación y roles mínimos (enfermería, médico), reutilizando el RBAC/JWT del scaffold | Sí | |
| Suite `eval/`, trazas visibles y línea base de las cuatro cifras | Sí | |
| Empaquetado reproducible (ZIP, `docker-compose.yml`, `run.sh`, `.env.example`, README) | Sí | |
| Informe mensual | | Sí |
| Seguridad, hardening, ficha de gobernanza y respuesta a la auditoría cruzada | | Sí |
| Cifras finales comparadas contra la línea base | | Sí |

Los hitos son **[Propuesta]**: separar semanal (M2) y mensual (M3) responde a que quedan 10 días hasta el Momento 2.

### 3.2 Fuera de alcance

- **Diagnóstico, pronóstico, recomendación de tratamiento o dosis.** El informe describe evolución documentada. **[Equipo]**
- **Monitoreo en tiempo real o alertas de urgencia.** El informe es retrospectivo (semanal o mensual); no sustituye la vigilancia clínica.
- **Envío automático de informes** al médico o por correo/mensajería. El canal de salida se mantiene cerrado a propósito (tríada letal). **[Curso]**
- **Edición o borrado de los registros originales por parte del agente.** Los registros son de solo lectura para él.
- **Integración con historia clínica electrónica o sistemas hospitalarios.** Los datos son sintéticos y de captura propia. **[Propuesta]**
- **Datos reales de pacientes y cumplimiento normativo de producción** sobre datos de salud. Un uso real exigiría revisión legal aparte. **[Propuesta]**
- **Entradas que no sean texto:** imágenes, voz, signos vitales de dispositivos.
- **Varias instituciones, gestión administrativa** (agendas, facturación) **y app móvil nativa.**
- **Entrenamiento o ajuste fino de modelos propios.**
- **Soporte de otros idiomas distintos del español.**
- **Despliegue en la nube para producción.** El entregable es un ZIP con docker compose. **[Propuesta]**

### 3.3 Supuestos y restricciones

- **Tiempo:** el Momento 2 es el sábado 31 de octubre de 2026, con demo en vivo de 6 min y 3 min de preguntas; sin presentación no hay nota. El Momento 3 es en la semana 14. **[Curso]**
- **Equipo:** dos personas, y ambas deben poder explicar cualquier función que se les señale. **[Curso]**
- **Costo cero:** el curso no exige pagar; se usan proveedores con capa gratuita. **[Curso]**
- **Datos:** solo sintéticos, en el repo, en `eval/`, en trazas y en capturas. **[Equipo]**
- **Privacidad hacia el proveedor:** al LLM sale lo mínimo, sin nombre ni documento (id interno). **[Propuesta]**

### 3.4 Dónde cae cada fase del ciclo de vida

| Fase | Qué produce | Entregable del curso | Estado |
|---|---|---|---|
| 1. Planificación | Objetivos, alcance, restricciones | `docs/SPEC.md` §1–§3 | **Este documento** |
| 2. Análisis y diseño | Diseño, casos borde, criterios de aceptación, decisiones (datos, proveedor, patrón de integración) | `docs/SPEC.md` §4–§7, `docs/PLAN.md`, `docs/decisiones/semana08.md` | Siguiente |
| 3. Implementación | Agente, herramientas, app, flujo de aprobación | Código; `skills/` (`escribir-spec`, `escribir-plan`, `ejecutar-plan`) | Pendiente |
| 4. Pruebas y evaluación | Suite dorada, cuatro cifras, trazas | `eval/`, `docs/decisiones/semana10.md` | Pendiente |
| 5. Entrega | ZIP reproducible y demo | README, `docker-compose.yml`, `run.sh`, `.env.example` | Momento 2 |
| 6. Seguridad, gobernanza y cierre | Amenazas, hardening, ficha, auditoría | `docs/seguridad.md`, `docs/gobernanza.md`, `docs/respuesta_auditoria.md` | Momento 3 |

---

## §4. Diseño

PENDIENTE — fase 2. Ya está decidido el reparto código / LLM / humano (ver §1). Falta: modelo de datos, herramientas del agente, flujo de estados del informe, patrón de integración y diagrama.

## §5. Casos borde (semillas)

Se identifican ahora y se resuelven en el diseño. Solo tienen comportamiento esperado los que ya decidió el equipo.

- Primera semana de un paciente (no hay semana anterior con qué comparar).
- Semana sin registros o con pocos días. *Esperado: el informe declara información insuficiente.*
- Notas ambiguas, con abreviaturas o contradictorias el mismo día ("ánimo alto" y "decaído").
- Dos registros el mismo día, fecha faltante o registros fuera de orden.
- Nota que contiene instrucciones dirigidas al agente (inyección indirecta). *Esperado: se trata como dato, no como instrucción.*
- Petición de diagnóstico o dosis. *Esperado: el sistema se niega.*
- Paciente dado de alta a mitad del período.
- Timeout, 429 o JSON inválido del proveedor. *Esperado: reintento, degradación y mensaje honesto, sin stack trace.*
- Enfermería edita el borrador: ¿se conserva la versión original generada por el modelo?

## §6. Criterios de aceptación

PENDIENTE — fase 2. Se derivan de la columna "Verificación" de §2.2, y cada línea debe responderse con sí o no.

## §7. Decisiones

| # | Decisión | Estado | Detalle |
|---|---|---|---|
| D1 | Revisión humana obligatoria antes de que el informe llegue al médico | Decidida **[Equipo]** | Alternativa descartada: envío automático |
| D2 | Reparto código / LLM / humano | Decidida **[Equipo]** | Ver §1 |
| D3 | El sistema no diagnostica | Decidida **[Equipo]** | Ver §3.2 |
| D4 | Informe semanal en el Momento 2, mensual en el Momento 3 | Propuesta | Por el tiempo disponible |
| D5 | Origen de los registros | Por confirmar | Captura mínima propia + semilla sintética (propuesto) vs. solo carga por script |
| D6 | ¿El médico puede ver borradores? | Por confirmar | La presentación dice que solo tras la aprobación; la descripción inicial dice que podría revisarlos directamente. Mientras se decide, solo ve `APROBADO` |
| D7 | Agente con herramientas vs. flujo fijo de pasos | Por justificar | Argumentar con los seis ejes de la semana 3 y medir contra la solución convencional (OBJ-12) |
| D8 | Estrategia de datos | Por decidir | Por defecto, consulta directa a la BD y el LLM lee las notas; RAG solo si se mide que hace falta. Se documenta en `docs/decisiones/semana08.md` |
| D9 | Stack y proveedor del LLM | Por decidir | Pendientes en `AGENTS.md` |
| D10 | Qué es "una semana" y contra cuántas semanas anteriores se compara | Por decidir | ¿Lunes a domingo o 7 días móviles? ¿La anterior o una tendencia de varias? |
| D11 | Tamaño del conjunto sintético | Por decidir | Cuántos pacientes y cuántas semanas; condiciona la suite de evaluación |
