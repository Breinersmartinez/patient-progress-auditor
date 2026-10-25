# Resumen ejecutivo — Patient Progress Auditor (fases 1 y 2)

Oct 25, 2026 · @brei

## Resumen en una página

La planificación está cerrada (SPEC v0.4) y el análisis de requisitos tiene su primer borrador completo: 38 requisitos funcionales y 12 no funcionales. Faltan 6 días para el Momento 2 (31 de octubre de 2026) y la decisión que más condiciona el diseño sigue abierta: el stack.

**Qué es.** Patient Progress Auditor: enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA lee los registros de la semana, los compara con las anteriores y redacta un borrador de informe. Enfermería lo revisa y lo aprueba antes de que llegue al médico. El sistema no diagnostica ni receta.

**Por qué necesita IA.** Las notas son texto libre ("vomitó el lunes, ánimo alto") y no se pueden programar reglas para todas las formas de escribirlas. El código guarda, ordena y calcula; el modelo interpreta y redacta; la persona aprueba. El profesor ya aprobó que la aplicación necesita IA.

**Forma del sistema.** Una aplicación con un agente integrado, con herramientas, cuatro límites y criterio de parada. No es un workflow ni un sistema multiagente.

| En una mirada | Estado |
| --- | --- |
| Fase 1, Planificación | Cerrada: SPEC v0.4 con 15 objetivos verificables |
| Fase 2, Análisis de requisitos | Primer borrador: 38 RF, 12 RNF, 9 reglas de negocio, 9 casos de uso |
| Fase 3, Diseño | Siguiente; bloqueada por la decisión del stack (D9) |
| Momento 2 | Sábado 31 de octubre de 2026: demo de 6 min y 3 min de preguntas |
| Núcleo para el Momento 2 | 27 de los 38 RF; otros 8 se recortan primero si falta tiempo |

## Qué se trabajó

Se cubrieron ocho pasos, del nombre del proyecto al análisis de requisitos; tres terminaron en archivos para el repo: `AGENTS.md`, `SPEC.md` y `REQUISITOS.md`.

| # | Paso | Qué se hizo | Entregable |
| --- | --- | --- | --- |
| 1 | Nombre y repo | Se recomendó el nombre «Auditor de la evolución de pacientes» y reglas para nombrar el repo. El repositorio existente se llama `patient-progress-auditor` | Anexo A |
| 2 | AGENTS.md | Reescrito desde cero con las reglas del curso (rúbricas de los Momentos 1 a 3 y diapositivas de las semanas 2, 3, 4, 7, 8, 9, 10, 12 y 13); marca lo que aún no existe en el repo | `AGENTS.md` |
| 3 | Prompt de limpieza | Prompt para OpenCode que elimina los restos de AgroCortex con el método del curso: explorar, planear, esperar aprobación, ejecutar y verificar; sin push | Entregado en el chat; no he comprobado si ya se ejecutó |
| 4 | Planificación | Objetivos, alcance y restricciones; cada meta se verifica con sí o no y lleva su origen (curso, equipo o propuesta) | `SPEC.md` §1 a §3 |
| 5 | Evaluación de herramientas | Tablero de permisos: sí. JasperReports: no, mejor HTML a PDF. Voz a texto: Whisper detrás de un puerto, solo como opcional | SPEC: D13, D14 y matriz de permisos |
| 6 | Semana 6 | Se incorporaron la forma del sistema, la autonomía por acción, el criterio de parada y el modo simulado sin internet | SPEC v0.2 |
| 7 | Corrección de rumbo | Se fijó que es una aplicación con agente integrado y no un workflow; D7 pasó a decidida | SPEC v0.3 |
| 8 | Análisis de requisitos | Necesidades por actor, 9 casos de uso, 38 RF, 12 RNF, 9 reglas de negocio, modelo conceptual y trazabilidad de los 15 objetivos | `REQUISITOS.md` v0.1 y SPEC v0.4 |

## Decisiones

De las 20 decisiones registradas en la SPEC, 5 están tomadas, 6 son supuestos de trabajo por confirmar, 5 son propuestas y 4 siguen abiertas.

| ID | Decisión | Estado |
| --- | --- | --- |
| D1 | Un informe llega al médico solo tras la aprobación de enfermería | Decidida |
| D2 | El código guarda y calcula, el LLM interpreta y redacta, el humano aprueba | Decidida |
| D3 | El sistema no diagnostica ni recomienda tratamiento | Decidida |
| D7 | Aplicación con un agente integrado, no un workflow | Decidida |
| D17 | La necesidad de IA quedó aprobada por el profesor | Decidida |
| D5 | Registros: captura mínima propia más datos sintéticos | Supuesto de trabajo |
| D6 | El médico solo ve informes aprobados; queda abierto si ve borradores | Supuesto de trabajo |
| D10 | Una semana va de lunes a domingo; el agente decide cuántas semanas atrás revisar, con tope | Supuesto de trabajo |
| D12 | El médico también registra entradas | Supuesto de trabajo |
| D19 | La generación del informe es manual en el Momento 2 | Supuesto de trabajo |
| D20 | Sin campo de consulta libre en el Momento 2 | Supuesto de trabajo |
| D4 | Informe semanal en el Momento 2 y mensual en el Momento 3 | Propuesta |
| D13 | PDF con HTML a PDF y no con Jasper; opcional | Propuesta |
| D14 | Voz con Whisper detrás de un puerto, sin guardar el audio; opcional | Propuesta |
| D15 | Proveedor: Google AI Studio como principal y Groq como respaldo | Propuesta |
| D16 | Modo simulado del proveedor (replay) como plan B sin internet | Propuesta |
| D8 | Estrategia de datos: consulta directa a la BD o RAG | Abierta |
| D9 | Stack: Java con Spring y Angular (el scaffold) o Python (los talleres) | Abierta |
| D11 | Tamaño del conjunto sintético: pacientes y semanas | Abierta |
| D18 | Catálogo de métricas semanales, a validar con enfermería | Abierta |

## Conclusiones

Seis conclusiones sostienen lo que viene; la primera libera el paso a la fase de diseño.

- **La planificación se puede dar por cerrada.** El visto bueno del profesor sobre la necesidad de IA era lo único que faltaba; no hace falta otra aprobación para empezar el diseño.
- **El agente se defiende por las decisiones que toma.** La pregunta de la semana 6 es si el paso siguiente depende de lo encontrado. Hay cinco decisiones candidatas y se miden contra una plantilla sin LLM (OBJ-12).
- **El control humano tiene que vivir en el código.** Solo enfermería aprueba, el médico solo ve informes aprobados y tres pruebas automáticas lo verifican (OBJ-05 y OBJ-15).
- **El código no puede contar sobre texto libre.** El LLM convierte cada entrada en eventos estructurados y el código los cuenta (RF-06 y RF-07). Así las cifras siguen siendo exactas (OBJ-04) y es el mejor argumento de necesidad de IA.
- **De las tres herramientas evaluadas, solo el tablero de permisos toca la rúbrica.** PDF y voz quedan como opcionales del Momento 3: Jasper no se recomienda y la voz trae riesgos de privacidad y de reproducibilidad.
- **El alcance es pesado para 6 días.** 27 de los 38 requisitos son núcleo; el tablero, la auditoría completa, editar o rechazar informes y el registro del médico se recortan primero.

## Riesgos y pendientes

Nueve pendientes pueden afectar el Momento 2; el stack es el único que bloquea el diseño.

| Pendiente | Por qué importa | Siguiente paso |
| --- | --- | --- |
| Stack sin decidir (D9) | El scaffold es Java con Spring y Angular; los talleres del curso están en Python. Condiciona el diseño y la limpieza del repo | Decidir hoy; en Java el bucle del agente se escribe a mano (unas 80 líneas en el taller) |
| Supuestos sin confirmar | D5, D6, D10, D12, D19 y D20 sostienen los requisitos | Los dos integrantes los revisan y cambian el estado en la tabla de decisiones |
| Catálogo de métricas sin validar (D18) | Lo inventó el equipo, no un usuario | 15 minutos con una persona de enfermería, o dejar constancia de que no se pudo |
| Defensa de «¿por qué agente y no workflow?» | Es la pregunta de riesgo de la semana 6 | Preparar las cinco decisiones del agente y la comparación contra la plantilla sin LLM |
| AGENTS.md del repo desactualizado | No incluye lo de la semana 6 | Cuatro ajustes a mano: `semana06.md` en la estructura, criterio de parada, adaptador simulado y un solo agente |
| Evidencia de la aprobación del profesor (D17) | Respalda la prueba de necesidad de IA | Guardar fecha y medio (clase o correo) en `docs/decisiones/` |
| Nombre del archivo de decisiones | La lámina de entrega dice `docs/decisiones.md`; las rúbricas y la semana 6 usan `docs/decisiones/semanaNN.md` | Confirmar con el profesor antes del 31 de octubre |
| Modelos del proveedor sin verificar (D15) | Los de la semana 6 (Gemini 3.5 Flash Lite y Qwen 3.8 27B) no están verificados y difieren de la lámina de entrega | Verificar los vigentes antes de fijarlos |
| Estado del repo | No he comprobado si la limpieza de AgroCortex se ejecutó | Revisar el diff en una sesión nueva, con contexto fresco |

## Ruta hasta el Momento 2

Quedan 6 días: el stack se decide hoy y el ZIP se prueba en frío el 30 de octubre, un día antes de la demo.

&#91;embedded content: ruta al Momento 2 · 6 hitos en 6 días\]

Es una propuesta mía, no un acuerdo, y es ajustada. Agrupa lo que el análisis marca como núcleo (27 requisitos); si el tiempo no alcanza, se recortan primero los 8 requisitos M2-S.

## Anexo A. Nombre, repo y descripción

El proyecto se presenta como «Auditor de la evolución de pacientes»; en el repositorio existente (`patient-progress-auditor`) lleva el nombre en inglés.

| Elemento | Texto final |
| --- | --- |
| Nombre del proyecto | Auditor de la evolución de pacientes |
| Repositorio actual | `patient-progress-auditor` |
| Alternativa recomendada en español | `auditor-evolucion-pacientes` |
| Otras alternativas | `seguimiento-pacientes-ia`, `informes-evolucion-paciente`, `evolia-pacientes`, `patient-evolution-auditor` |
| Temas (topics) de GitHub | `llm`, `agentes-ia`, `salud`, `seguimiento-pacientes`, `human-in-the-loop` |

**Descripción corta del repo (una línea):**

> Agente de IA que analiza registros diarios en texto libre, compara semanas y meses, y redacta informes de evolución del paciente para revisión y aprobación de enfermería.

**Reglas para nombrar el repo:** minúsculas, guiones en lugar de espacios, sin tildes ni «ñ», y entre dos y cuatro palabras que digan qué hace. Si el repo va en inglés, conviene mantener también el código y el README en inglés.

**Palabras que conviene evitar:** «diagnóstico», «médico» y «clínico asistente». La propuesta dice que el sistema no sustituye el criterio profesional ni diagnostica, y el nombre debe ser coherente con eso.

## Anexo B. Planificación (SPEC v0.4, §1 a §3)

Es el texto final de las tres primeras secciones de la SPEC. Las etiquetas indican el origen de cada punto: **\[Curso\]** lo exige o lo recomienda el curso, **\[Equipo\]** ya lo definió el equipo y **\[Propuesta\]** lo propone el borrador y falta confirmarlo. Las secciones §4 a §7 (diseño, casos borde, criterios de aceptación y decisiones) están en `SPEC.md`.

### §1. Resumen

**Qué se construye.** Un auditor de seguimiento de pacientes. Enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA consulta los registros de cada semana, los compara con semanas anteriores y redacta un borrador de informe semanal (y, más adelante, mensual). Enfermería lo revisa y aprueba antes de que llegue al médico.

**Qué problema resuelve.** Los registros diarios quedan dispersos. Al cerrar la semana, alguien debe releerlos todos, compararlos con la anterior y redactar el informe a mano. Eso consume tiempo y hace fácil que se pierdan patrones, como un síntoma que reaparece. Se buscan informes semanales y mensuales consistentes que muestren la evolución en el tiempo.

**Por qué necesita IA (prueba de necesidad).** Si se quita el modelo, los registros siguen existiendo pero el informe no. Las notas son texto libre («vomitó el lunes, ánimo alto»), hay infinitas formas de escribirlas y no se pueden programar reglas para todas. El reparto es:

| Parte | Quién la resuelve | Por qué |
| --- | --- | --- |
| Guardar, consultar, ordenar por fecha, calcular métricas, permisos, validaciones | Código determinista | Exigen exactitud |
| Interpretar el texto libre y redactar la comparación entre semanas | LLM | Espacio de entrada abierto, reglas infinitas |
| Contrastar las afirmaciones con los registros originales y aprobar | Humano (enfermería) | El error en salud es costoso |

**Forma del sistema: aplicación con un agente integrado \[Equipo, D7\].** El agente es el núcleo del producto: recibe la tarea (informe de un paciente y un período), decide qué registros e historial consultar, cuántas semanas mirar hacia atrás, si profundiza cuando un síntoma reaparece o si declara información insuficiente, y entrega un borrador validado. El código no le dicta el orden: le ofrece herramientas deterministas (consulta, métricas exactas, guardar borrador) y le impone barreras (cuatro límites, validación del esquema de salida, aprobación humana). Un solo agente con herramientas y límites; no multiagente. **\[Curso\]** La pregunta de la semana 6 (¿el paso siguiente depende de lo que se descubra en el anterior?) se responde con decisiones reales del agente, listadas en §4 de la SPEC, y se mide contra la solución convencional (OBJ-12).

**Qué no es.** Apoyo al seguimiento documental. No diagnostica, no receta y no sustituye el criterio profesional.

#### Actores

| Actor | Qué hace con el sistema |
| --- | --- |
| Enfermería | Registra, dispara o consulta el informe, lo revisa, lo corrige y lo aprueba |
| Médico | Consulta los informes aprobados y, según D12, también puede registrar |
| Administrador | Gestiona usuarios y roles; no lee datos clínicos **\[Propuesta\]** |
| Equipo de desarrollo | Construye, evalúa y sustenta |
| Evaluador del curso | Revisa la demo en vivo (6 min de demo y 3 de preguntas) |

### §2. Objetivos

#### 2.1 Objetivo general

Reducir el trabajo manual de releer y redactar el seguimiento semanal de un paciente, entregando a enfermería un borrador comparativo con fuente verificable, sin que ningún informe llegue al médico sin aprobación humana. **\[Equipo\]**

#### 2.2 Objetivos específicos y cómo se verifican

Cada objetivo se verifica con un sí o un no.

| ID | Objetivo | Verificación (sí/no) | Origen |
| --- | --- | --- | --- |
| **Producto** |  |  |  |
| OBJ-01 | Generar un borrador de informe semanal que compare la semana con la(s) anterior(es), a partir de registros diarios en texto libre | Para cada caso dorado de la suite, el sistema produce un borrador que valida contra el esquema de salida y cubre ambas semanas | \[Equipo\] |
| OBJ-02 | Señalar patrones que reaparecen (p. ej. un síntoma que vuelve tras semanas sin él) | En los casos dorados con un patrón recurrente sembrado, el borrador lo menciona. La tasa de detección se registra como línea base | \[Equipo\] |
| OBJ-03 | Generar el informe mensual a partir de los registros del mes (hito del Momento 3) | Dado un mes con registros de cuatro semanas, el informe muestra la evolución semana a semana | \[Equipo\] · hito \[Propuesta\] |
| OBJ-04 | Que toda cifra del informe la calcule el código, no el LLM | En todos los casos dorados, las cifras del informe coinciden con las de la función de métricas y con el cálculo manual | \[Equipo\] |
| **Control humano y seguridad clínica** |  |  |  |
| OBJ-05 | La revisión humana la exige el sistema, no es costumbre del operador | Prueba automatizada: un informe pendiente no es visible para el rol médico, y solo el rol enfermería puede aprobarlo | \[Equipo\] · \[Curso\] |
| OBJ-06 | Fuente y criterio visibles, y corregibles por enfermería | Cada afirmación enlaza a al menos un registro original (fecha + texto); el criterio de comparación se muestra; enfermería puede editar antes de aprobar | \[Curso\] |
| OBJ-07 | Honestidad ante datos insuficientes | En un caso con una semana sin registros, el informe lo declara y no rellena ni inventa | \[Equipo\] · \[Curso\] |
| OBJ-08 | No diagnosticar ni recomendar tratamiento | Al menos un caso de rechazo en la suite (pedir diagnóstico o dosis) donde el sistema se niega | \[Curso\] |
| OBJ-15 | Permisos por rol con tablero | Seed de permisos propio del proyecto; vista del tablero (solo lectura en M2, edición en M3); tres pruebas: el médico no puede aprobar, un informe pendiente no lo ve el médico, el tablero no puede asignar Aprobar a otro rol que enfermería | \[Equipo\] · hito \[Propuesta\] |
| **Agente y robustez** |  |  |  |
| OBJ-09 | Agente con los cuatro límites activos (iteraciones, presupuesto de tokens, confirmación humana, timeout por herramienta), **criterio de parada explícito** (condición de éxito) y mapa de autonomía por acción | Los cuatro existen, tienen valor justificado y se pueden mostrar en la demo; al agotarse un límite el agente se detiene y avisa en vez de seguir intentando; el mapa está en `docs/decisiones/semana06.md`. Sin límite no hay entrega | \[Curso\] |
| OBJ-10 | Resiliencia y capa de proveedor desacoplada | Se fuerzan y se muestran tres fallos (timeout, salida que no valida, caída o 429) sin stack trace al usuario; el proveedor cambia por configuración | \[Curso\] |
| **Evidencia** |  |  |  |
| OBJ-11 | Medir con método y mostrar trazas | `eval/` con 5 a 10 casos verificados a mano (al menos uno de rechazo); cuatro cifras con fecha y método en `docs/decisiones/semana10.md` (tasa de éxito, costo por tarea, mediana de latencia, recuperación si aplica); trazas visibles en vivo | \[Curso\] |
| OBJ-12 | Sustentar la necesidad del LLM y del agente frente a alternativas más simples | Se listan las decisiones que toma el agente (qué consultar, cuánto retroceder, cuándo declarar información insuficiente) y lo que va deliberadamente en código; los seis ejes aplicados por componente; la misma suite corrida contra una versión sin modelo (informe por plantilla con métricas por código) | \[Equipo\] · \[Curso\] |
| OBJ-13 | Reproducibilidad y plan B | `./run.sh` o `docker compose up --build` levanta todo; un compañero con el ZIP y el README lo corre en menos de 5 minutos; existe un modo simulado del proveedor (replay) para correr la demo sin internet | \[Curso\] |
| OBJ-14 | Seguridad y gobernanza (hito del Momento 3) | `docs/seguridad.md` (modelo de amenazas, tríada evaluada, ataque propio documentado, hardening con antes y después), `docs/gobernanza.md` (cinco preguntas) y `docs/respuesta_auditoria.md` | \[Curso\] |

#### 2.3 Sobre las metas numéricas

No se fija un umbral de éxito del agente antes de medir la línea base. El curso califica el método (cifras con fecha, mediana, trazas) por encima del valor: un 6 de 10 sustentado con trazas puntúa más que un 10 de 10 sin evidencia. **\[Curso\]** El umbral, si se quiere, se define después de la primera medición.

### §3. Alcance

#### 3.1 Dentro del alcance

| Capacidad | Momento 2 (31-oct-2026) | Momento 3 (semana 14) |
| --- | --- | --- |
| Alta mínima de paciente con id interno y registros diarios en texto libre (formulario simple y carga de datos sintéticos) | Sí |  |
| Consulta, orden por fechas y métricas semanales calculadas por código | Sí |  |
| Agente integrado que genera el borrador: decide qué consultar con herramientas de lectura y de métricas, compara semana vs anteriores y opera con los cuatro límites | Sí |  |
| Borrador de informe semanal con fuente y criterio visibles | Sí |  |
| Flujo de revisión y aprobación por enfermería; vista del médico solo con informes aprobados | Sí |  |
| Autenticación y roles mínimos, reutilizando el RBAC/JWT del scaffold con seed propio | Sí |  |
| Tablero de permisos por rol | Solo lectura | Edición con invariantes |
| Mapa de autonomía por acción (`docs/decisiones/semana06.md`) | Sí |  |
| Suite `eval/`, trazas visibles y línea base de las cuatro cifras | Sí |  |
| Modo simulado del proveedor (plan B sin internet y pruebas) | Sí |  |
| Empaquetado reproducible (ZIP, `docker-compose.yml`, `run.sh`, `.env.example`, README) | Sí |  |
| Informe mensual |  | Sí |
| Seguridad, hardening, ficha de gobernanza y respuesta a la auditoría cruzada |  | Sí |
| Cifras finales comparadas contra la línea base |  | Sí |
| Exportación a PDF de informes aprobados |  | Opcional |
| Dictado por voz con transcripción editable |  | Opcional |

Los hitos son **\[Propuesta\]**: separar semanal (M2) y mensual (M3) responde al poco tiempo que queda hasta el Momento 2. PDF y voz solo entran si todo lo demás está cerrado.

#### 3.2 Fuera de alcance

- **Diagnóstico, pronóstico, recomendación de tratamiento o dosis.** El informe describe evolución documentada. **\[Equipo\]**
- **Monitoreo en tiempo real o alertas de urgencia.** El informe es retrospectivo (semanal o mensual); no sustituye la vigilancia clínica.
- **Envío automático de informes** al médico o por correo/mensajería. El canal de salida se mantiene cerrado a propósito (tríada letal). **\[Curso\]**
- **Arquitectura multiagente o sub-agentes.** Un solo agente acotado; la semana 6 advierte contra construir «el diagrama que vio en internet». **\[Curso\]**
- **Edición o borrado de los registros originales por parte del agente.** Los registros son de solo lectura para él.
- **Asignar el permiso Aprobar desde el tablero a un rol distinto de enfermería.** Es un invariante del sistema, no una opción de configuración. **\[Propuesta\]**
- **Guardar el audio del dictado por voz.** Solo se conserva el texto transcrito y revisado. **\[Propuesta\]**
- **Integración con historia clínica electrónica o sistemas hospitalarios.** Los datos son sintéticos y de captura propia. **\[Propuesta\]**
- **Datos reales de pacientes y cumplimiento normativo de producción** sobre datos de salud. Un uso real exigiría revisión legal aparte. **\[Propuesta\]**
- **Entradas que no sean texto o dictado:** imágenes, signos vitales de dispositivos.
- **Varias instituciones, gestión administrativa** (agendas, facturación) **y app móvil nativa.**
- **Entrenamiento o ajuste fino de modelos propios.**
- **Soporte de otros idiomas distintos del español.**
- **Despliegue en la nube para producción.** El entregable es un ZIP con docker compose. **\[Propuesta\]**

#### 3.3 Supuestos y restricciones

- **Tiempo:** el Momento 2 es el sábado 31 de octubre de 2026, con demo en vivo de 6 min y 3 min de preguntas; sin presentación no hay nota. El Momento 3 es en la semana 14. **\[Curso\]**
- **Equipo:** dos personas, y ambas deben poder explicar cualquier función que se les señale. **\[Curso\]**
- **Costo cero:** el curso no exige pagar. El taller de la semana 6 sugiere Google AI Studio (sin tarjeta) y Groq como alternativa; ambos con capa gratuita. **\[Curso\]**
- **Sin dependencia de internet en la demo:** se lleva plan B (modo simulado). **\[Curso\]**
- **Datos:** solo sintéticos, en el repo, en `eval/`, en trazas y en capturas. **\[Equipo\]**
- **Privacidad hacia el proveedor:** al LLM sale lo mínimo, sin nombre ni documento (id interno). **\[Propuesta\]**

#### 3.4 Dónde cae cada fase del ciclo de vida

| Fase | Qué produce | Entregable del curso | Estado |
| --- | --- | --- | --- |
| 1. Planificación | Objetivos, alcance, restricciones | `docs/SPEC.md` §1–§3 | **Cerrada** (línea base v0.4) |
| 2. Análisis de requisitos | Necesidades por actor, casos de uso, requisitos funcionales y no funcionales, reglas de negocio, criterios de aceptación | `docs/REQUISITOS.md` | **En curso** |
| 3. Diseño | Arquitectura, modelo de datos, herramientas del agente, flujo de estados y decisiones (forma del sistema, datos, proveedor, patrón de integración) | `docs/SPEC.md` §4–§7, `docs/PLAN.md`, `docs/decisiones/semana06.md` (autonomía), `docs/decisiones/semana08.md` (datos) | Siguiente |
| 4. Implementación | Agente, herramientas, app, flujo de aprobación, permisos | Código; `skills/` (`escribir-spec`, `escribir-plan`, `ejecutar-plan`) | Pendiente |
| 5. Pruebas y evaluación | Suite dorada, cuatro cifras, trazas | `eval/`, `docs/decisiones/semana10.md` | Pendiente |
| 6. Entrega | ZIP reproducible y demo | README, `docker-compose.yml`, `run.sh`, `.env.example` | Momento 2 |
| 7. Seguridad, gobernanza y cierre | Amenazas, hardening, ficha, auditoría | `docs/seguridad.md`, `docs/gobernanza.md`, `docs/respuesta_auditoria.md` | Momento 3 |

## Anexo C. Análisis de requisitos (REQUISITOS v0.1, versión compacta)

Es el texto final de los requisitos sin la columna de criterios de aceptación ni la trazabilidad de objetivos; ambas están en `REQUISITOS.md`. Hitos: **M2-N** núcleo del Momento 2, **M2-S** Momento 2 si hay tiempo, **M3** Momento 3 y **Opc** opcional.

### Necesidades de los interesados

| Actor | Necesidad | Requisitos |
| --- | --- | --- |
| Enfermería | No releer todos los registros ni redactar el informe a mano cada semana | RF-08 a RF-13 |
| Enfermería | Ver la evolución frente a semanas anteriores sin que se pierda un síntoma que reaparece | RF-07, RF-10 |
| Enfermería | Revisar rápido con la evidencia a la mano, corregir y aprobar | RF-24 a RF-27 |
| Enfermería | Registrar cada día sin fricción: texto libre y, opcionalmente, voz | RF-02, RF-38 |
| Médico | Recibir informes consistentes y aprobados que muestren la evolución en el tiempo | RF-28, RF-29, RF-36 |
| Médico | Registrar entradas diarias cuando le corresponda | RF-30 |
| Administrador | Gestionar usuarios y permisos sin acceder a datos clínicos | RF-31 a RF-34 |
| Evaluador del curso | Ver en 6 minutos el agente funcionando con límites, trazas y fallos forzados | RF-16 a RF-22, RNF-08 |

### Casos de uso

| Caso de uso | Actor | Qué hace |
| --- | --- | --- |
| CU-01 Registrar entrada diaria | Enfermería, Médico | Escribe (o dicta) cómo está el paciente hoy |
| CU-02 Generar borrador semanal | Enfermería | Pide el informe de un paciente y una semana; el agente lo redacta |
| CU-03 Revisar y corregir | Enfermería | Lee el borrador, abre la evidencia y lo corrige |
| CU-04 Aprobar o rechazar | Enfermería | Decide; el sistema registra quién y cuándo |
| CU-05 Consultar informes aprobados | Médico | Ve la evolución del paciente por período |
| CU-06 Gestionar usuarios y permisos | Administrador | Crea usuarios, ve y (M3) edita permisos |
| CU-07 Ver la traza de una ejecución | Enfermería; equipo en la demo | Ve qué consultó el agente y con qué resultado |
| CU-08 Generar informe mensual | Enfermería | Consolida el mes |
| CU-09 Exportar a PDF | Médico, Enfermería | Descarga un informe aprobado |

### Flujo crítico: generar, revisar, aprobar, consultar

1. Enfermería elige paciente y semana y pide el borrador (RF-08).
2. El agente consulta con sus herramientas, decide cuántas semanas anteriores revisar y redacta. El código valida el esquema, las citas y las cifras (RF-09 a RF-13).
3. El informe queda `PENDIENTE_REVISION`, o `INCOMPLETO` si se agotó un límite (RF-17).
4. Enfermería lee, abre la evidencia y corrige si hace falta (RF-24, RF-25).
5. Enfermería aprueba (RF-26) o rechaza con motivo (RF-27).
6. El médico ve el informe aprobado (RF-28).

**Excepciones:** una semana sin datos suficientes, que el informe declara (RF-14); un fallo del proveedor, con mensaje honesto y respaldo (RF-19); una entrada con instrucciones, que se trata como dato (RF-21).

### Requisitos funcionales (38)

| ID | El sistema debe… | Hito |
| --- | --- | --- |
| **Pacientes y entradas** |  |  |
| RF-01 | Crear pacientes con un id interno y un alias legible; el LLM solo recibe el id interno | M2-N |
| RF-02 | Registrar una entrada diaria en texto libre asociada a paciente, fecha y autor, sin plantilla obligatoria | M2-N |
| RF-03 | Permitir corregir una entrada conservando la versión anterior y quién la cambió | M2-S |
| RF-04 | Listar las entradas de un paciente por fecha y filtrar por rango | M2-N |
| RF-05 | Cargar el conjunto sintético de demostración con un comando | M2-N |
| **Eventos y métricas (el código cuenta, el LLM interpreta)** |  |  |
| RF-06 | Derivar de cada entrada eventos estructurados (tipo, descripción normalizada, fecha) mediante el LLM, validados contra un esquema, guardados aparte del texto original y enlazados a su entrada | M2-N |
| RF-07 | Calcular en código, a partir de entradas y eventos, las métricas semanales por paciente: días con entrada, días sin entrada, eventos por tipo, días con cada síntoma, variación frente a la semana anterior y síntomas nuevos, persistentes, desaparecidos y reaparecidos | M2-N |
| **Agente e informe** |  |  |
| RF-08 | Generar un borrador de informe semanal para un paciente y una semana, a solicitud de enfermería | M2-N |
| RF-09 | Dar al agente acceso a los datos solo mediante herramientas autorizadas de lectura y métricas | M2-N |
| RF-10 | Dejar que el agente decida qué consultar y cuántas semanas anteriores revisar (dentro de un tope), y que profundice en un síntoma | M2-N |
| RF-11 | Producir el informe con estructura fija validada por esquema: resumen, métricas, cambios frente a la semana anterior, patrones recurrentes, datos insuficientes y afirmaciones con fuente | M2-N |
| RF-12 | Hacer que cada afirmación cite al menos una entrada (id y fecha) y el criterio de comparación | M2-N |
| RF-13 | Tomar las cifras del informe del módulo de métricas; si una cifra no coincide, rechazar el informe y reintentar | M2-N |
| RF-14 | Declarar en el informe cuando faltan registros, sin rellenar ni inventar | M2-N |
| RF-15 | Omitir diagnósticos y recomendaciones de tratamiento o dosis aunque la solicitud o las entradas los pidan, e indicar que está fuera de su alcance | M2-N |
| RF-16 | Operar con los cuatro límites del agente: iteraciones, tokens por solicitud, confirmación humana ante escrituras irreversibles y timeout por herramienta | M2-N |
| RF-17 | Aplicar un criterio de parada explícito (informe válido con fuente por afirmación); si un límite se agota, dejar el informe `INCOMPLETO` con el motivo | M2-N |
| RF-18 | Validar la salida del modelo contra el esquema; si falla, reintentar una vez con el error; si vuelve a fallar, degradar a texto plano y registrar | M2-N |
| RF-19 | Ante timeout, 429 o caída del proveedor, reintentar con espera creciente, usar el proveedor de respaldo y mostrar un mensaje honesto, sin stack trace | M2-N |
| RF-20 | Permitir cambiar el proveedor del LLM por configuración y contar con un adaptador simulado (replay) | M2-N |
| RF-21 | Tratar el texto de las entradas como dato y no como instrucción, y no dar al agente herramientas de salida (internet, correo, mensajes) | M2-N |
| RF-22 | Registrar la traza de cada ejecución (herramienta, parámetros, resultado, ms, tokens) y mostrarla en la app | M2-N |
| **Revisión y aprobación** |  |  |
| RF-23 | Manejar los estados del informe `PENDIENTE_REVISION`, `INCOMPLETO`, `APROBADO` y `RECHAZADO`, con transiciones validadas en el servidor | M2-N |
| RF-24 | Mostrar a enfermería el informe en lenguaje claro y, a un clic, la evidencia: entradas citadas, criterio de comparación y llamadas de herramientas | M2-N |
| RF-25 | Permitir a enfermería editar el borrador antes de aprobar, conservando la versión original del modelo y las ediciones | M2-S |
| RF-26 | Permitir aprobar un informe solo al rol enfermería y registrar quién y cuándo | M2-N |
| RF-27 | Permitir a enfermería rechazar un informe con motivo y pedir uno nuevo | M2-S |
| **Consulta del médico** |  |  |
| RF-28 | Permitir al médico listar pacientes y consultar los informes `APROBADO` por paciente y período, del más reciente al más antiguo | M2-N |
| RF-29 | Mostrar al médico, en un informe aprobado, la evidencia que lo respalda | M2-S |
| RF-30 | Permitir al médico registrar entradas diarias (supuesto D12) | M2-S |
| **Usuarios, roles y auditoría** |  |  |
| RF-31 | Autenticar con usuario y contraseña y usar un token con expiración | M2-N |
| RF-32 | Manejar los roles ENFERMERIA, MEDICO y ADMIN, más un rol técnico AGENTE, con permisos por módulo y acción según la matriz de la SPEC §4 | M2-N |
| RF-33 | Permitir al administrador crear, actualizar y desactivar usuarios, sin leer datos clínicos | M2-S |
| RF-34 | Mostrar el tablero de permisos por rol: lectura en M2, edición en M3, con el permiso Aprobar fijo para enfermería | M2-S (lectura) · M3 (edición) |
| RF-35 | Registrar en auditoría las acciones sensibles: aprobar, rechazar, editar entradas o informes y cambiar permisos (quién, cuándo, antes y después) | M2-S |
| **Momento 3 y opcionales** |  |  |
| RF-36 | Generar el informe mensual a partir de las entradas y eventos del mes, mostrando la evolución semana a semana | M3 |
| RF-37 | Exportar a PDF un informe aprobado, con aprobador, fecha, id del informe y nota de asistencia de IA (D13) | Opc |
| RF-38 | Dictar una entrada por voz con transcripción editable antes de guardar, origen `VOZ` y sin guardar el audio (D14) | Opc |

### Requisitos no funcionales (12)

| ID | Categoría | Requisito |
| --- | --- | --- |
| RNF-01 | Seguridad | Autorización en el servidor para toda operación; la UI solo oculta. Mínimo privilegio; credenciales fuera del repo y del historial; usuario de BD con permisos mínimos |
| RNF-02 | Privacidad | Solo datos sintéticos. Al LLM solo salen el id interno y el texto de las entradas, nunca nombre ni documento. Sin audio guardado |
| RNF-03 | Seguridad del agente | Hay datos privados y contenido no confiable; la tercera pata (salida hacia afuera) se mantiene cerrada |
| RNF-04 | Robustez | Sin stack trace al usuario; los tres fallos forzables (timeout, salida que no valida, caída o 429) se manejan |
| RNF-05 | Rendimiento | La generación no bloquea la interfaz: estados honestos o streaming. La latencia se mide como mediana y se reporta, sin umbral previo |
| RNF-06 | Costo | Costo cero: capa gratuita de los proveedores. El costo por tarea se mide |
| RNF-07 | Observabilidad | Trazas y auditoría consultables; los fallos se explican con la traza |
| RNF-08 | Reproducibilidad | `./run.sh` o `docker compose up --build`; ZIP limpio; variables en `.env.example` |
| RNF-09 | Usabilidad | UI en español; lenguaje claro primero y evidencia a un clic; estados honestos |
| RNF-10 | Integridad de datos | Las entradas y los informes no se borran, solo se corrigen con versión; las entradas son de solo lectura para el agente |
| RNF-11 | Calidad | Pruebas automatizadas de las reglas críticas: aprobación, permisos, validador de cifras y citas, esquema. Commits convencionales. Cada integrante explica el código |
| RNF-12 | Cumplimiento del curso | Estructura de entrega, límites del agente, `eval/` y cifras con fecha, según `AGENTS.md` |

### Reglas de negocio (9)

| ID | Regla | Origen |
| --- | --- | --- |
| RN-01 | Un informe solo llega al médico en estado `APROBADO` | \[Equipo\] |
| RN-02 | Solo enfermería aprueba; la transición se valida en el servidor | \[Equipo\] |
| RN-03 | El informe describe evolución documentada; no diagnostica ni recomienda tratamiento o dosis | \[Equipo\] |
| RN-04 | Toda cifra del informe la calcula el código, no el LLM | \[Equipo\] |
| RN-05 | Toda afirmación del informe cita al menos una entrada | \[Curso\] |
| RN-06 | Una semana va de lunes a domingo. El informe se compara con la semana anterior y el agente decide cuántas más atrás revisar, con tope | \[Propuesta\] (D10) |
| RN-07 | El agente nunca modifica las entradas originales | \[Equipo\] |
| RN-08 | Si faltan datos, el informe lo declara y no los rellena | \[Equipo\] |
| RN-09 | Se permiten varias entradas el mismo día; dentro del día se ordenan por hora de creación | \[Propuesta\] |

### Modelo conceptual preliminar

Solo entidades y relaciones; los atributos, tipos y tablas se definen en el diseño.

| Entidad | Qué es | Relaciones clave |
| --- | --- | --- |
| Usuario, Rol, Permiso | Quién entra y qué puede hacer | Un usuario tiene un rol; un rol agrupa permisos por módulo y acción |
| Paciente | Persona seguida, identificada por id interno | Tiene muchas entradas e informes |
| Entrada | Registro diario en texto libre (fecha, autor, versiones) | Pertenece a un paciente; tiene eventos derivados |
| Evento | Dato estructurado derivado por el LLM de una entrada (tipo, descripción normalizada, fecha) | Enlaza a su entrada original |
| Informe | Documento por paciente y período, con estado, contenido estructurado, versión del modelo, versión editada, aprobador y fecha | Tiene afirmaciones; tiene una ejecución del agente |
| Afirmación | Frase del informe con su cita | Referencia una o más entradas |
| Ejecución del agente | Traza de pasos: herramienta, parámetros, resultado, ms, tokens | Asociada a un informe |
| Auditoría | Acción sensible: quién, cuándo, antes y después | Referencia al objeto afectado |

### Supuestos de trabajo y preguntas abiertas

Para poder avanzar, el análisis usa estos supuestos. Si cambian, se actualizan los requisitos y la SPEC.

| Tema | Supuesto de trabajo | Afecta | Decisión en la SPEC |
| --- | --- | --- | --- |
| Origen de los registros | Captura mínima propia más datos sintéticos cargados por script | RF-02, RF-05 | D5 |
| Visibilidad al médico | Solo ve informes `APROBADO` | RF-28, RN-01 | D6 |
| Qué es una semana | Lunes a domingo; compara con la anterior y el agente decide cuántas más atrás, con tope | RN-06, RF-10 | D10 |
| El médico registra | Sí | RF-30 | D12 |
| Catálogo de métricas | El de RF-07 es una propuesta; conviene validarlo con una persona de enfermería | RF-07, RF-11 | D18 |
| Disparo de la generación | Manual (botón) en M2; programado al cierre de semana queda para M3 | RF-08 | D19 |
| Consulta libre al pedir el informe | Sin campo libre en M2; el caso de rechazo se construye con una entrada que pide un diagnóstico | RF-15 | D20 |

**Para el diseño (no bloquean el análisis):** stack (D9), estrategia de datos (D8), proveedor del LLM (D15), tamaño del conjunto sintético (D11), motor de PDF (D13), voz (D14) y dónde se derivan los eventos (al guardar la entrada o al generar el informe).

### Criterios de salida de la fase de análisis

- [ ] Cada objetivo de la SPEC tiene al menos un requisito o entregable.
- [ ] Cada requisito funcional tiene un criterio que se responde con sí o no.
- [ ] Los dos integrantes revisaron los supuestos de trabajo.
- [ ] El catálogo de métricas (RF-07) y la estructura del informe (RF-11) se validaron con una persona de enfermería, o quedó registrado que no se pudo.
- [ ] La SPEC se actualizó con las decisiones D18 a D20 y los supuestos confirmados.
