# SPEC — Patient Progress Auditor

> **Estado:** borrador v0.4 para aprobación · **Fase del ciclo de vida:** 1 — Planificación (cerrada, línea base) · en curso: 2 — Análisis de requisitos · **Fecha:** 2026-10-23
> **Equipo:** Breiner Martinez Muñoz y Andres Felipe Lopez Vivas · **Curso:** Ingeniería de Aplicaciones con IA (UCC)
>
> **Cambios de la v0.4:** se cierra la planificación; las fases se separan en análisis de requisitos (`docs/REQUISITOS.md`) y diseño; se registra la aprobación del profesor sobre la necesidad de IA (D17); los supuestos de trabajo del análisis quedan reflejados en D5, D6, D10 y D12, y se abren D18 a D20. La v0.3 fijó que el sistema es una aplicación con un agente integrado (D7) y sembró las decisiones que toma el agente. La v0.2 incorporó la semana 6 (autonomía por acción, criterio de parada, plan B sin internet) y las decisiones sobre permisos por rol, PDF y voz.
>
> Este documento desarrolla las secciones §1 a §3 de la spec (resumen, objetivos, alcance) y deja sembradas §4, §5 y §7. Los criterios de aceptación por requisito están en `docs/REQUISITOS.md`; la §6 los consolida en la fase de diseño.
>
> **Etiquetas de origen:** **[Curso]** lo exige o lo recomienda el curso · **[Equipo]** ya lo definió el equipo (presentación del proyecto y `AGENTS.md`) · **[Propuesta]** lo propone este borrador y hay que confirmarlo. Ninguna meta numérica va sin etiqueta.

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

**Forma del sistema: aplicación con un agente integrado [Equipo, D7].** El agente es el núcleo del producto: recibe la tarea (informe de un paciente y un período), decide qué registros e historial consultar, cuántas semanas mirar hacia atrás, si profundiza cuando un síntoma reaparece o si declara información insuficiente, y entrega un borrador validado. El código no le dicta el orden: le ofrece herramientas deterministas (consulta, métricas exactas, guardar borrador) y le impone barreras (cuatro límites, validación del esquema de salida, aprobación humana). Un solo agente con herramientas y límites; no multiagente. **[Curso]** La pregunta de la semana 6 (¿el paso siguiente depende de lo que se descubra en el anterior?) se responde con decisiones reales del agente, listadas en §4, y se mide contra la solución convencional (OBJ-12).

**Qué no es.** Apoyo al seguimiento documental. No diagnostica, no receta y no sustituye el criterio profesional.

### Actores

| Actor | Qué hace con el sistema |
|---|---|
| Enfermería | Registra, dispara o consulta el informe, lo revisa, lo corrige y lo aprueba |
| Médico | Consulta los informes aprobados y, según D12, también puede registrar |
| Administrador | Gestiona usuarios y roles; no lee datos clínicos **[Propuesta]** |
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
| OBJ-15 | Permisos por rol con tablero | Seed de permisos propio del proyecto; vista del tablero (solo lectura en M2, edición en M3); tres pruebas: el médico no puede aprobar, un informe pendiente no lo ve el médico, el tablero no puede asignar Aprobar a otro rol que enfermería | [Equipo] · hito [Propuesta] |
| **Agente y robustez** | | | |
| OBJ-09 | Agente con los cuatro límites activos (iteraciones, presupuesto de tokens, confirmación humana, timeout por herramienta), **criterio de parada explícito** (condición de éxito) y mapa de autonomía por acción | Los cuatro existen, tienen valor justificado y se pueden mostrar en la demo; al agotarse un límite el agente se detiene y avisa en vez de seguir intentando; el mapa está en `docs/decisiones/semana06.md`. Sin límite no hay entrega | [Curso] |
| OBJ-10 | Resiliencia y capa de proveedor desacoplada | Se fuerzan y se muestran tres fallos (timeout, salida que no valida, caída o 429) sin stack trace al usuario; el proveedor cambia por configuración | [Curso] |
| **Evidencia** | | | |
| OBJ-11 | Medir con método y mostrar trazas | `eval/` con 5 a 10 casos verificados a mano (al menos uno de rechazo); cuatro cifras con fecha y método en `docs/decisiones/semana10.md` (tasa de éxito, costo por tarea, mediana de latencia, recuperación si aplica); trazas visibles en vivo | [Curso] |
| OBJ-12 | Sustentar la necesidad del LLM y del agente frente a alternativas más simples | Se listan las decisiones que toma el agente (qué consultar, cuánto retroceder, cuándo declarar información insuficiente) y lo que va deliberadamente en código; los seis ejes aplicados por componente; la misma suite corrida contra una versión sin modelo (informe por plantilla con métricas por código) | [Equipo] · [Curso] |
| OBJ-13 | Reproducibilidad y plan B | `./run.sh` o `docker compose up --build` levanta todo; un compañero con el ZIP y el README lo corre en menos de 5 minutos; existe un modo simulado del proveedor (replay) para correr la demo sin internet | [Curso] |
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
| Agente integrado que genera el borrador: decide qué consultar con herramientas de lectura y de métricas, compara semana vs anteriores y opera con los cuatro límites | Sí | |
| Borrador de informe semanal con fuente y criterio visibles | Sí | |
| Flujo de revisión y aprobación por enfermería; vista del médico solo con informes aprobados | Sí | |
| Autenticación y roles mínimos, reutilizando el RBAC/JWT del scaffold con seed propio | Sí | |
| Tablero de permisos por rol | Solo lectura | Edición con invariantes |
| Mapa de autonomía por acción (`docs/decisiones/semana06.md`) | Sí | |
| Suite `eval/`, trazas visibles y línea base de las cuatro cifras | Sí | |
| Modo simulado del proveedor (plan B sin internet y pruebas) | Sí | |
| Empaquetado reproducible (ZIP, `docker-compose.yml`, `run.sh`, `.env.example`, README) | Sí | |
| Informe mensual | | Sí |
| Seguridad, hardening, ficha de gobernanza y respuesta a la auditoría cruzada | | Sí |
| Cifras finales comparadas contra la línea base | | Sí |
| Exportación a PDF de informes aprobados | | Opcional |
| Dictado por voz con transcripción editable | | Opcional |

Los hitos son **[Propuesta]**: separar semanal (M2) y mensual (M3) responde a que quedan 8 días hasta el Momento 2 (a 2026-10-23). PDF y voz solo entran si todo lo demás está cerrado.

### 3.2 Fuera de alcance

- **Diagnóstico, pronóstico, recomendación de tratamiento o dosis.** El informe describe evolución documentada. **[Equipo]**
- **Monitoreo en tiempo real o alertas de urgencia.** El informe es retrospectivo (semanal o mensual); no sustituye la vigilancia clínica.
- **Envío automático de informes** al médico o por correo/mensajería. El canal de salida se mantiene cerrado a propósito (tríada letal). **[Curso]**
- **Arquitectura multiagente o sub-agentes.** Un solo agente acotado; la semana 6 advierte contra construir "el diagrama que vio en internet". **[Curso]**
- **Edición o borrado de los registros originales por parte del agente.** Los registros son de solo lectura para él.
- **Asignar el permiso Aprobar desde el tablero a un rol distinto de enfermería.** Es un invariante del sistema, no una opción de configuración. **[Propuesta]**
- **Guardar el audio del dictado por voz.** Solo se conserva el texto transcrito y revisado. **[Propuesta]**
- **Integración con historia clínica electrónica o sistemas hospitalarios.** Los datos son sintéticos y de captura propia. **[Propuesta]**
- **Datos reales de pacientes y cumplimiento normativo de producción** sobre datos de salud. Un uso real exigiría revisión legal aparte. **[Propuesta]**
- **Entradas que no sean texto o dictado:** imágenes, signos vitales de dispositivos.
- **Varias instituciones, gestión administrativa** (agendas, facturación) **y app móvil nativa.**
- **Entrenamiento o ajuste fino de modelos propios.**
- **Soporte de otros idiomas distintos del español.**
- **Despliegue en la nube para producción.** El entregable es un ZIP con docker compose. **[Propuesta]**

### 3.3 Supuestos y restricciones

- **Tiempo:** el Momento 2 es el sábado 31 de octubre de 2026, con demo en vivo de 6 min y 3 min de preguntas; sin presentación no hay nota. El Momento 3 es en la semana 14. **[Curso]**
- **Equipo:** dos personas, y ambas deben poder explicar cualquier función que se les señale. **[Curso]**
- **Costo cero:** el curso no exige pagar. El taller de la semana 6 sugiere Google AI Studio (sin tarjeta) y Groq como alternativa; ambos con capa gratuita. **[Curso]**
- **Sin dependencia de internet en la demo:** se lleva plan B (modo simulado). **[Curso]**
- **Datos:** solo sintéticos, en el repo, en `eval/`, en trazas y en capturas. **[Equipo]**
- **Privacidad hacia el proveedor:** al LLM sale lo mínimo, sin nombre ni documento (id interno). **[Propuesta]**

### 3.4 Dónde cae cada fase del ciclo de vida

| Fase | Qué produce | Entregable del curso | Estado |
|---|---|---|---|
| 1. Planificación | Objetivos, alcance, restricciones | `docs/SPEC.md` §1–§3 | **Cerrada** (línea base v0.4) |
| 2. Análisis de requisitos | Necesidades por actor, casos de uso, requisitos funcionales y no funcionales, reglas de negocio, criterios de aceptación | `docs/REQUISITOS.md` | **En curso** |
| 3. Diseño | Arquitectura, modelo de datos, herramientas del agente, flujo de estados y decisiones (forma del sistema, datos, proveedor, patrón de integración) | `docs/SPEC.md` §4–§7, `docs/PLAN.md`, `docs/decisiones/semana06.md` (autonomía), `docs/decisiones/semana08.md` (datos) | Siguiente |
| 4. Implementación | Agente, herramientas, app, flujo de aprobación, permisos | Código; `skills/` (`escribir-spec`, `escribir-plan`, `ejecutar-plan`) | Pendiente |
| 5. Pruebas y evaluación | Suite dorada, cuatro cifras, trazas | `eval/`, `docs/decisiones/semana10.md` | Pendiente |
| 6. Entrega | ZIP reproducible y demo | README, `docker-compose.yml`, `run.sh`, `.env.example` | Momento 2 |
| 7. Seguridad, gobernanza y cierre | Amenazas, hardening, ficha, auditoría | `docs/seguridad.md`, `docs/gobernanza.md`, `docs/respuesta_auditoria.md` | Momento 3 |

---

## §4. Diseño (semillas)

El diseño completo es de la fase 3. Estas semillas ya salen de la semana 6 y de lo acordado; todas son **[Propuesta]** salvo lo que dice el curso.

### Las cinco piezas del agente

| Pieza | Aquí significa | Estado |
|---|---|---|
| Contexto / prompt | Rol de apoyo documental, reglas (no diagnosticar, citar fuente, declarar insuficiencia), inventario de herramientas y protocolo de salida (JSON) | Pendiente |
| Modelo | Proveedor y modelo del LLM | Pendiente (D15) |
| Herramientas | Leer registros por paciente y semana, obtener métricas, guardar borrador. Descripciones escritas como prompt: cuándo usarla, cuándo NO, prerrequisitos | Pendiente |
| Memoria | Las semanas anteriores entran por herramienta (BD), no por la memoria del modelo; historial acotado por sesión | Propuesta |
| Parada | Condición de éxito (informe válido contra el esquema y con fuente por afirmación) más los cuatro límites. Si un límite se agota, se detiene y avisa | Propuesta |

### Qué decide el agente

El agente se justifica por las decisiones que toma, no por seguir una receta. Estas son las candidatas; el diseño las confirma y la suite las mide.

| Decisión del agente | Depende de lo que va encontrando |
|---|---|
| Qué herramientas usar y en qué orden | Cuántos registros tiene la semana, si hay notas ambiguas |
| Cuántas semanas atrás consultar | Si un síntoma reaparece y hay que ver desde cuándo |
| Si profundiza en un síntoma (consulta días concretos) | Si una métrica cambia de forma llamativa |
| Si los datos bastan o declara información insuficiente | Si faltan días o registros |
| Cuándo dar por terminado el borrador | Si ya hay un informe válido con fuente por afirmación |

Lo que el agente **no** decide y va en código: el cálculo de las métricas, los permisos, los estados del informe, la aprobación, la validación del esquema de salida y los límites duros.

### Autonomía por acción (base de `docs/decisiones/semana06.md`)

La pregunta de la semana 6: ¿qué es lo peor que pasa si se equivoca en esta acción concreta?

| Acción | Nivel | Por qué |
|---|---|---|
| Leer registros e historial | Ejecuta y reporta | Solo lectura; queda traza |
| Calcular métricas | Código, sin modelo | Exige exactitud |
| Guardar el borrador (`PENDIENTE_REVISION`) | Ejecuta y reporta | Reversible; no sale a nadie |
| Aprobar el informe | **Sugiere**: lo ejecuta una persona | Es lo que lo hace llegar al médico |
| Compartir con el médico | Solo como consecuencia de la aprobación | Visible por terceros |
| Editar o borrar registros originales | Sin acceso para el agente | Irreversible |
| Cualquier acción sin traza | Nunca | Si no hay traza, no se puede responder por ello |

### Permisos por rol (V ver, C crear, A actualizar, E eliminar)

| Módulo | ENFERMERIA | MEDICO | ADMIN | AGENTE (rol técnico) |
|---|---|---|---|---|
| Pacientes | V C A | V | — | V |
| Registros diarios | V C A | V C (D12) | — | V |
| Informes | V A + **Aprobar** | V (solo aprobados) | — | C (solo borrador) |
| Usuarios | — | — | V C A E | — |
| Roles/permisos | — | — | V A | — |
| Trazas | — | — | V | — |

"El médico solo ve lo aprobado" es una regla de estado: va en la consulta, no en un permiso. Cada cambio en el tablero queda registrado (quién, cuándo, antes y después).

## §5. Casos borde (semillas)

Se identifican ahora y se resuelven en el diseño. Solo tienen comportamiento esperado los que ya decidió el equipo o el curso.

- Primera semana de un paciente (no hay semana anterior con qué comparar).
- Semana sin registros o con pocos días. *Esperado: el informe declara información insuficiente.*
- Notas ambiguas, con abreviaturas o contradictorias el mismo día ("ánimo alto" y "decaído").
- Dos registros el mismo día, fecha faltante o registros fuera de orden.
- Nota que contiene instrucciones dirigidas al agente (inyección indirecta). *Esperado: se trata como dato, no como instrucción.*
- Petición de diagnóstico o dosis. *Esperado: el sistema se niega.*
- Paciente dado de alta a mitad del período.
- Timeout, 429 o JSON inválido del proveedor. *Esperado: reintento, degradación y mensaje honesto, sin stack trace.*
- Se agota el límite de iteraciones o de tokens sin un informe válido. *Esperado: se detiene, avisa y deja el informe marcado como incompleto; no sigue intentando.*
- Sin internet en la demo. *Esperado: el modo simulado del proveedor mantiene el flujo.*
- Enfermería edita el borrador: ¿se conserva la versión original generada por el modelo?
- Dictado por voz con errores de transcripción (opcional M3). *Esperado: el texto es editable antes de guardar y queda con origen `VOZ`.*

## §6. Criterios de aceptación

Los criterios por requisito están en `docs/REQUISITOS.md` (columna "Criterio de aceptación"), cada uno con respuesta sí o no. Esta sección los consolida en la fase de diseño.

## §7. Decisiones

| # | Decisión | Estado | Detalle |
|---|---|---|---|
| D1 | Revisión humana obligatoria antes de que el informe llegue al médico | Decidida **[Equipo]** | Alternativa descartada: envío automático |
| D2 | Reparto código / LLM / humano | Decidida **[Equipo]** | Ver §1 |
| D3 | El sistema no diagnostica | Decidida **[Equipo]** | Ver §3.2 |
| D4 | Informe semanal en el Momento 2, mensual en el Momento 3 | Propuesta | Por el tiempo disponible |
| D5 | Origen de los registros | Supuesto de trabajo | Captura mínima propia + semilla sintética (propuesto) vs. solo carga por script |
| D6 | ¿El médico puede ver borradores? | Supuesto de trabajo: no | La presentación dice que solo tras la aprobación; la descripción inicial dice que podría revisarlos directamente. Mientras se decide, solo ve `APROBADO` |
| D7 | Forma del sistema | Decidida **[Equipo]** | Aplicación con un agente integrado (ver §1), como exige la rúbrica del Momento 2. La defensa frente a "¿por qué agente y no workflow?" se hace con las decisiones reales del agente (§4) y se mide: la misma suite contra una plantilla sin LLM (obligatoria, OBJ-12) y, opcionalmente, contra un workflow con LLM sin bucle |
| D8 | Estrategia de datos | Por decidir | Por defecto, consulta directa a la BD y el LLM lee las notas; RAG solo si se mide que hace falta. Se documenta en `docs/decisiones/semana08.md` |
| D9 | Stack | Por decidir | El código de los talleres (bucle, FastMCP, FastAPI) es Python; el scaffold es Java con RBAC/JWT. En Java hay que escribir el bucle a mano (el taller lo resuelve en unas 80 líneas); en Python se reescribe lo existente. Pendiente en `AGENTS.md` |
| D10 | Qué es "una semana" y contra cuántas semanas anteriores se compara | Supuesto de trabajo | Lunes a domingo; se compara con la anterior y el agente decide cuántas más atrás revisar, con tope (RN-06) |
| D11 | Tamaño del conjunto sintético | Por decidir | Cuántos pacientes y cuántas semanas; condiciona la suite de evaluación |
| D12 | ¿El médico también registra? | Supuesto de trabajo: sí | La matriz de §4 lo asume sí; amplía el papel del médico |
| D13 | Motor de PDF (opcional M3) | Propuesta | Thymeleaf + HTML→PDF (una sola plantilla para pantalla y PDF). Jasper solo si ya existen plantillas `.jrxml`; si se usa, versión 7.0.7 o superior. Solo informes `APROBADO` |
| D14 | Dictado por voz (opcional M3) | Propuesta | Whisper detrás de un puerto, con Groq como adaptador y uno local por perfil de compose. Transcripción siempre editable, sin guardar audio, origen `VOZ`. Es una comodidad de entrada, no parte de la justificación de IA |
| D15 | Proveedor del LLM | Propuesta | Google AI Studio como principal y Groq como respaldo o alternativa, los dos del taller de la semana 6. Tener ambos permite demostrar la capa del proveedor cambiando en vivo. Verificar los modelos vigentes antes de fijarlos |
| D16 | Modo simulado del proveedor | Propuesta | Adaptador de replay con respuestas grabadas: plan B sin internet y base de las pruebas |
| D17 | La aplicación necesita IA (prueba de necesidad) | Decidida: aprobada por el profesor | Registrar la fecha y el medio (clase o correo) en `docs/decisiones/` como evidencia |
| D18 | Catálogo de métricas semanales | Por confirmar | El de RF-07 es una propuesta; validar con una persona de enfermería |
| D19 | Disparo de la generación del informe | Supuesto de trabajo | Manual (botón) en M2; programado al cierre de semana, M3 |
| D20 | Campo de consulta libre al pedir el informe | Supuesto de trabajo: no en M2 | Sin campo, el caso de rechazo (OBJ-08) se construye con una entrada que pide un diagnóstico |
