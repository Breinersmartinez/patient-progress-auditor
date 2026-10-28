# Resumen ejecutivo — Patient Progress Auditor (fases 1 y 2)

Oct 27, 2026 · @brei

## Resumen en una página

La planificación está cerrada (SPEC v0.6) y el análisis de requisitos está en su tercer borrador (REQUISITOS v0.3): 39 requisitos funcionales, 14 no funcionales, 10 reglas de negocio y 10 casos de uso. El enlace al repositorio se entrega el jueves 29 de octubre de 2026 a medianoche y la demo del Momento 2 es el sábado 31. El 27 de octubre el equipo movió el alcance: el informe mensual entra al Momento 2 y la autenticación pasa al Momento 3. Lo que más condiciona ahora es el arranque en frío, que todavía no existe: `docker-compose.yml` y `run.sh` están vacíos y el README describe el scaffold.

**Qué es.** Patient Progress Auditor: enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA lee los registros de la semana, los compara con las anteriores y redacta un borrador de informe semanal o mensual. Enfermería lo revisa y lo aprueba antes de que llegue al médico. El sistema no diagnostica ni receta.

**Por qué necesita IA.** Las notas son texto libre («vomitó el lunes, ánimo alto») y no se pueden programar reglas para todas las formas de escribirlas. El código guarda, ordena y calcula; el modelo interpreta y redacta; la persona aprueba. El profesor ya aprobó que la aplicación necesita IA.

**Forma del sistema.** Una aplicación con un agente integrado, con herramientas, cuatro límites y criterio de parada. No es un workflow ni un sistema multiagente. La generación corre en segundo plano y la pantalla recibe eventos de progreso reales por SSE (D24).

**Qué cambió el 27 de octubre.** Tres decisiones del equipo, con sus consecuencias en los documentos:

- **Informe mensual en el Momento 2 (D4).** Un mes son cuatro semanas completas consecutivas y el mensual solo se genera si cada una tiene informe semanal aprobado (RN-10). Se propuso cuatro semanas y no mes calendario porque las semanas de lunes a domingo cruzan los límites de mes; falta confirmarlo.
- **Autenticación en el Momento 3 (D23).** En el Momento 2 los usuarios de demostración (enfermería y médico, sin credenciales) se siembran al arrancar y se elige con cuál trabajar (D26, propuesta). Así sigue siendo cierto que solo enfermería aprueba y que el médico solo ve lo aprobado, y cada acción, incluida la del médico que registra (D12), tiene un autor. Es separación de funciones para la demostración, no un control de seguridad.
- **Stack sin límite de momento (D9).** Spring Boot y Angular aplican desde el Momento 2; solo el RBAC y el JWT esperan al Momento 3.

| En una mirada | Estado |
| --- | --- |
| Fase 1, Planificación | Cerrada: SPEC v0.6 con 16 objetivos verificables y 26 decisiones |
| Fase 2, Análisis de requisitos | Borrador v0.3: 39 RF, 14 RNF, 10 reglas de negocio, 10 casos de uso |
| Fase 3, Diseño | Siguiente; el stack ya no la bloquea (D9) |
| Entrega | Enlace al repositorio público en el aula virtual hasta el jueves 29 de octubre a medianoche; versión congelada en el tag `m2-entrega` |
| Momento 2 | Sábado 31 de octubre de 2026: demo de 6 min y 3 min de preguntas |
| Núcleo para el Momento 2 | 29 de los 39 RF; otros 5 se recortan primero si falta tiempo |
| Momento 3 | 3 RF (inicio de sesión, administración de usuarios, tablero de permisos), más seguridad y gobernanza; 2 RF opcionales (PDF y voz) |

## Qué se trabajó

Se cubrieron trece pasos, del nombre del proyecto a los cambios del 27 de octubre; los entregables para el repo son `AGENTS.md`, `CLAUDE.md`, `SPEC.md`, `REQUISITOS.md` y este resumen.

| # | Paso | Qué se hizo | Entregable |
| --- | --- | --- | --- |
| 1 | Nombre y repo | Se recomendó el nombre «Auditor de la evolución de pacientes» y reglas para nombrar el repo. El repositorio existente se llama `patient-progress-auditor` | Anexo A |
| 2 | AGENTS.md | Reescrito desde cero con las reglas del curso (rúbricas de los Momentos 1 a 3 y diapositivas de las semanas 2, 3, 4, 7, 8, 9, 10, 12 y 13) | `AGENTS.md` |
| 3 | Limpieza de AgroCortex | Prompt para OpenCode con el método del curso (explorar, planear, esperar aprobación, ejecutar, verificar); la limpieza se ejecutó en la rama `chore/limpiar-agrocortex`, ya fusionada | Repo |
| 4 | Planificación | Objetivos, alcance y restricciones; cada meta se verifica con sí o no y lleva su origen (curso, equipo o propuesta) | `SPEC.md` §1 a §3 |
| 5 | Evaluación de herramientas | Tablero de permisos: sí. JasperReports: no, mejor HTML a PDF. Voz a texto: Whisper detrás de un puerto, solo como opcional | SPEC: D13, D14 y matriz de permisos |
| 6 | Semana 6 | Se incorporaron la forma del sistema, la autonomía por acción y el criterio de parada; el modo simulado se descartó después (D16) | SPEC v0.2 |
| 7 | Corrección de rumbo | Se fijó que es una aplicación con agente integrado y no un workflow; D7 pasó a decidida | SPEC v0.3 |
| 8 | Análisis de requisitos | Necesidades por actor, casos de uso, requisitos funcionales y no funcionales, reglas de negocio, modelo conceptual y trazabilidad de los objetivos | `REQUISITOS.md` v0.1 y SPEC v0.4 |
| 9 | Clase del 24 de octubre | La entrega pasa a ser el enlace al repositorio público, sin ZIP, con plazo el jueves 29; el README lo ejecuta una IA; se incorporan arranque en frío, preguntas del profesor y D21 a D25 | SPEC v0.5, REQUISITOS v0.2, `AGENTS.md` |
| 10 | Mockups | Lienzo con 31 tableros de la interfaz; es el diseño con el que trabaja el equipo | Lienzo en claude.ai |
| 11 | Reparto del trabajo | Recomendación de dos frentes: plataforma y datos, y agente e informe | Chat |
| 12 | Patrón de integración | D24 escrita: ejecución asíncrona con eventos de progreso por SSE, estado en la BD y reanudación | SPEC §7, D24 |
| 13 | Decisiones del 27 de octubre | Informe mensual al Momento 2 con su definición de mes, autenticación al Momento 3 con usuarios de demostración desde el Momento 2, D9 y D8 decididas; se actualizan los cuatro documentos y se crea `CLAUDE.md` | SPEC v0.6, REQUISITOS v0.3, `AGENTS.md`, `CLAUDE.md` |

## Decisiones

De las 26 decisiones registradas en la SPEC, 18 están tomadas, 4 son supuestos de trabajo por confirmar, 2 son propuestas, 1 sigue por confirmar y 1 quedó descartada.

| ID | Decisión | Estado |
| --- | --- | --- |
| D1 | Un informe llega al médico solo tras la aprobación de enfermería; se descartó el envío automático | Decidida |
| D2 | El código guarda y calcula, el LLM interpreta y redacta, el humano aprueba | Decidida |
| D3 | El sistema no diagnostica ni recomienda tratamiento | Decidida |
| D4 | Informe semanal y mensual en el Momento 2; un mes son cuatro semanas completas con informe semanal aprobado (RN-10) | Decidida |
| D5 | Registros: captura mínima propia más datos sintéticos sembrados | Decidida |
| D7 | Aplicación con un agente integrado, no un workflow | Decidida |
| D8 | Consulta directa a la BD; RAG solo si se mide que hace falta | Decidida |
| D9 | Spring Boot y Angular desde el Momento 2; solo el RBAC y el JWT esperan al Momento 3; pendiente subir a Java 21 | Decidida |
| D10 | Semana de lunes a domingo; el agente decide cuántas semanas atrás revisar, con tope | Decidida |
| D11 | Tamaño del conjunto sintético: decidido, aún sin cifras | Decidida |
| D12 | El médico también registra y se audita qué médico hizo cada registro | Decidida |
| D13 | PDF con Thymeleaf y HTML a PDF; opcional | Decidida |
| D14 | Voz con Whisper detrás de un puerto, sin guardar el audio; opcional | Decidida |
| D17 | La necesidad de IA quedó aprobada por el profesor | Decidida |
| D21 | Entrega: enlace al repositorio público de GitHub, sin ZIP | Decidida |
| D22 | Arranque en frío con un comando; la llave del API se toma del entorno o se pide por consola | Decidida |
| D23 | Usuarios de demostración sembrados desde el Momento 2, sin credenciales; autenticación, credenciales y administrador en el Momento 3 | Decidida |
| D24 | Ejecución asíncrona del agente con eventos de progreso por SSE | Decidida |
| D6 | El médico solo ve informes aprobados; queda abierto si ve borradores | Supuesto de trabajo |
| D19 | La generación del informe es manual (botón) en el Momento 2 | Supuesto de trabajo |
| D20 | Sin campo de consulta libre en el Momento 2 | Supuesto de trabajo |
| D25 | Evaluaciones y observabilidad en versión mínima | Supuesto de trabajo |
| D15 | Proveedor: Google AI Studio como principal y Groq como respaldo | Propuesta |
| D26 | Sesión de demostración sin autenticación en el Momento 2: se elige el usuario sembrado | Propuesta |
| D18 | Catálogo de métricas semanales, a validar con enfermería | Por confirmar |
| D16 | Modo simulado del proveedor (replay): descartado | Descartada |

## Conclusiones

Siete conclusiones sostienen lo que viene; las dos primeras definen qué se construye antes del jueves.

- **El Momento 2 es el agente y los informes.** Núcleo de 29 requisitos, con el informe semanal y el mensual. El mensual (RF-36) es la ejecución más pesada del agente y la que más exige al patrón asíncrono (D4, D24).
- **El control humano sigue viviendo en el código aunque no haya autenticación.** Con usuarios de demostración elegibles, el servidor aplica el rol: solo enfermería aprueba, el médico solo ve informes aprobados y cada acción tiene un autor (D26, RF-35). Es separación de funciones, no seguridad; RNF-01 se cumple por completo en el Momento 3.
- **Un mes son cuatro semanas aprobadas.** El mensual no se genera con una semana sin aprobar y dice cuál falta (RN-10). El conjunto sintético debe incluir al menos un paciente con cuatro semanas aprobadas para poder mostrarlo.
- **El agente se defiende por las decisiones que toma.** La pregunta de la semana 6 es si el paso siguiente depende de lo encontrado. Hay cinco decisiones candidatas y se miden contra una plantilla sin LLM (OBJ-12).
- **El código no puede contar sobre texto libre.** El LLM convierte cada entrada en eventos estructurados y el código los cuenta (RF-06 y RF-07). Así las cifras siguen siendo exactas (OBJ-04) y es el mejor argumento de necesidad de IA.
- **El stack ya no es una incógnita.** Spring Boot y Angular se quedan (D9); el bucle del agente se escribe a mano en Java (unas 80 líneas en el taller) y vive en el backend. Falta subir de Java 17 a 21 y justificarlo en `docs/decisiones.md`.
- **El alcance es pesado para tres días de construcción.** La versión del jueves queda congelada en el tag `m2-entrega`; el viernes no se integra nada nuevo. Si no alcanza, lo primero que se recorta son los 5 requisitos M2-S.

## Riesgos y pendientes

Diez pendientes pueden afectar el Momento 2; los dos primeros son los que impiden que el profesor pueda probar la aplicación.

| Pendiente | Por qué importa | Siguiente paso |
| --- | --- | --- |
| Arranque en frío sin construir (D22, D23) | `docker-compose.yml` y `run.sh` están vacíos, el `Dockerfile` del backend no tiene `CMD` y el README describe el scaffold; si una IA no puede levantar la app con el README, el profesor no la revisa | Construirlo primero: compose, `run.sh`, `CMD`, semilla de datos y de usuarios de demostración, README ejecutable |
| Backend con `JWT_SECRET` obligatorio | Sin autenticación en el Momento 2, el backend no puede exigir `JWT_SECRET` ni proteger rutas con JWT al arrancar; hoy `application.yml` lo exige sin valor por defecto | Relajar la seguridad del scaffold en el Momento 2 y dejar RBAC y JWT para el Momento 3 |
| Sesión de demostración sin confirmar (D26) y mes de cuatro semanas (D4) | Sostienen RF-26, RF-28, RF-35, RF-36 y RN-10 | Los dos integrantes los confirman y cambian el estado en la tabla de decisiones |
| Proveedor y modelo del LLM sin decidir (D15) | Los modelos citados en las fuentes no están verificados y difieren de la lámina de entrega | Verificar los vigentes y registrar la justificación en `docs/decisiones.md` |
| Java 17 frente a Java 21 (D9) | La página de entrega cita Java 21; hoy pom, Dockerfile y CI usan 17 | Subir la versión o dejar escrito por qué no |
| Conjunto sintético sin cifras (D11) | Está decidido, pero no cuántos pacientes ni semanas; condiciona las evaluaciones y la demo del mensual | Fijarlo con al menos un paciente con cuatro semanas aprobadas |
| Supuestos sin confirmar | D6, D19, D20 y D25 sostienen requisitos | Los dos integrantes los revisan |
| Catálogo de métricas sin validar (D18) | Lo inventó el equipo, no un usuario | 15 minutos con una persona de enfermería, o dejar constancia de que no se pudo |
| Defensa de las decisiones ante el profesor | `docs/decisiones.md` y `docs/decisiones/` no existen todavía; el profesor pregunta por proveedor, RAG, síncrono o asíncrono y por qué cada herramienta | Escribir al menos D8, D9, D15 y D24 antes de la demo |

Los mockups no se actualizaron con las decisiones del 27 de octubre y pueden mostrar la autenticación en el hito anterior; por decisión del equipo se mantienen tal cual, y en alcance mandan la SPEC y los requisitos.

## Ruta hasta el Momento 2

Quedan cuatro días hasta la demo y tres de construcción: el jueves se congela la versión, así que todo lo del núcleo debe estar integrado antes.

| Día | Qué se hace | Requisitos |
| --- | --- | --- |
| Martes 27 | Confirmar D26 y el mes de cuatro semanas; construir el arranque en frío y los datos | RF-01, RF-02, RF-04, RF-05, RF-39, RNF-13 |
| Miércoles 28 | Eventos y métricas, agente con herramientas y límites, proveedor y trazas | RF-06 a RF-22 |
| Jueves 29 | Revisión y aprobación, vista del médico, informe mensual, prueba en frío con una IA que solo lea el README, tag `m2-entrega` y enlace en el aula virtual antes de medianoche | RF-23 a RF-26, RF-28, RF-32, RF-36 |
| Viernes 30 | Ensayo de la demo y de las respuestas sobre decisiones; no se integra nada nuevo | Fallos forzados, trazas, `docs/decisiones.md` |
| Sábado 31 | Demo de 6 min y 3 min de preguntas | — |

Es una propuesta mía, no un acuerdo, y es muy ajustada. Agrupa lo que el análisis marca como núcleo (29 requisitos); si el tiempo no alcanza, se recortan primero los 5 requisitos M2-S (RF-03, RF-27, RF-29, RF-30 y RF-35).

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

## Anexo B. Planificación (SPEC v0.6, §1 a §3)

Es el texto final de las tres primeras secciones de la SPEC. Las etiquetas indican el origen de cada punto: **[Curso]** lo exige o lo recomienda el curso, **[Equipo]** ya lo definió el equipo y **[Propuesta]** lo propone el borrador y falta confirmarlo. Las secciones §4 a §7 (diseño, casos borde, criterios de aceptación y decisiones) están en `docs/SPEC.md`.

### §1. Resumen

**Qué se construye.** Un auditor de seguimiento de pacientes. Enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA consulta los registros de cada semana, los compara con semanas anteriores y redacta un borrador de informe semanal o mensual. Enfermería lo revisa y aprueba antes de que llegue al médico.

**Qué problema resuelve.** Los registros diarios quedan dispersos. Al cerrar la semana, alguien debe releerlos todos, compararlos con la anterior y redactar el informe a mano. Eso consume tiempo y hace fácil que se pierdan patrones, como un síntoma que reaparece. Se buscan informes semanales y mensuales consistentes que muestren la evolución en el tiempo.

**Por qué necesita IA (prueba de necesidad).** Si se quita el modelo, los registros siguen existiendo pero el informe no. Las notas son texto libre ("vomitó el lunes, ánimo alto"), hay infinitas formas de escribirlas y no se pueden programar reglas para todas. El reparto es:

| Parte | Quién la resuelve | Por qué |
|---|---|---|
| Guardar, consultar, ordenar por fecha, calcular métricas, permisos, validaciones | Código determinista | Exigen exactitud |
| Interpretar el texto libre y redactar la comparación entre semanas | LLM | Espacio de entrada abierto, reglas infinitas |
| Contrastar las afirmaciones con los registros originales y aprobar | Humano (enfermería) | El error en salud es costoso |

**Forma del sistema: aplicación con un agente integrado [Equipo, D7].** El agente es el núcleo del producto: recibe la tarea (informe de un paciente y un período), decide qué registros e historial consultar, cuántas semanas mirar hacia atrás, si profundiza cuando un síntoma reaparece o si declara información insuficiente, y entrega un borrador validado. El código no le dicta el orden: le ofrece herramientas deterministas (consulta, métricas exactas, guardar borrador) y le impone barreras (cuatro límites, validación del esquema de salida, aprobación humana). Un solo agente con herramientas y límites; no multiagente. **[Curso]** La pregunta de la semana 6 (¿el paso siguiente depende de lo que se descubra en el anterior?) se responde con decisiones reales del agente, listadas en §4, y se mide contra la solución convencional (OBJ-12).

**Qué no es.** Apoyo al seguimiento documental. No diagnostica, no receta y no sustituye el criterio profesional.

#### Actores

| Actor | Qué hace con el sistema |
|---|---|
| Enfermería | Registra, dispara o consulta el informe, lo revisa, lo corrige y lo aprueba |
| Médico | Consulta los informes aprobados y, según D12, también puede registrar |
| Administrador | Gestiona usuarios y roles desde el Momento 3; no lee datos clínicos **[Propuesta]** |
| Equipo de desarrollo | Construye, evalúa y sustenta |
| Evaluador del curso | Levanta el repositorio con una IA que solo lee el README (el día antes de la demo) y revisa la demo en vivo (6 min de demo + 3 de preguntas), donde pregunta por las decisiones técnicas |

---

### §2. Objetivos

#### 2.1 Objetivo general

Reducir el trabajo manual de releer y redactar el seguimiento semanal de un paciente, entregando a enfermería un borrador comparativo con fuente verificable, sin que ningún informe llegue al médico sin aprobación humana. **[Equipo]**

#### 2.2 Objetivos específicos y cómo se verifican

Cada objetivo se verifica con un sí o un no.

| ID | Objetivo | Verificación (sí/no) | Origen |
|---|---|---|---|
| **Producto** | | | |
| OBJ-01 | Generar un borrador de informe semanal que compare la semana con la(s) anterior(es), a partir de registros diarios en texto libre | Para cada caso dorado de la suite, el sistema produce un borrador que valida contra el esquema de salida y cubre ambas semanas | [Equipo] |
| OBJ-02 | Señalar patrones que reaparecen (p. ej. un síntoma que vuelve tras semanas sin él) | En los casos dorados con un patrón recurrente sembrado, el borrador lo menciona. La tasa de detección se registra como línea base | [Equipo] |
| OBJ-03 | Generar el informe mensual a partir de los registros del mes (hito del Momento 2, D4) | Dado un mes de cuatro semanas completas con informe semanal aprobado, el informe muestra la evolución semana a semana, y si falta una semana aprobada no se genera | [Equipo] |
| OBJ-04 | Que toda cifra del informe la calcule el código, no el LLM | En todos los casos dorados, las cifras del informe coinciden con las de la función de métricas y con el cálculo manual | [Equipo] |
| **Control humano y seguridad clínica** | | | |
| OBJ-05 | La revisión humana la exige el sistema, no es costumbre del operador | Prueba automatizada: un informe pendiente no es visible para el rol médico, y solo el rol enfermería puede aprobarlo (en el Momento 2, con el usuario de demostración elegido, D26) | [Equipo] · [Curso] |
| OBJ-06 | Fuente y criterio visibles, y corregibles por enfermería | Cada afirmación enlaza a al menos un registro original (fecha + texto); el criterio de comparación se muestra; enfermería puede editar antes de aprobar | [Curso] |
| OBJ-07 | Honestidad ante datos insuficientes | En un caso con una semana sin registros, el informe lo declara y no rellena ni inventa | [Equipo] · [Curso] |
| OBJ-08 | No diagnosticar ni recomendar tratamiento | Al menos un caso de rechazo en la suite (pedir diagnóstico o dosis) donde el sistema se niega | [Curso] |
| OBJ-15 | Permisos por rol con tablero | Seed de permisos propio del proyecto; tablero de permisos con edición e invariantes (Momento 3); tres pruebas: el médico no puede aprobar y un informe pendiente no lo ve el médico (desde el Momento 2, con el usuario de demostración elegido, D26), y el tablero no puede asignar Aprobar a otro rol que enfermería (Momento 3) | [Equipo] · hito [Propuesta] |
| **Agente y robustez** | | | |
| OBJ-09 | Agente con los cuatro límites activos (iteraciones, presupuesto de tokens, confirmación humana, timeout por herramienta), **criterio de parada explícito** (condición de éxito) y mapa de autonomía por acción | Los cuatro existen, tienen valor justificado y se pueden mostrar en la demo; al agotarse un límite el agente se detiene y avisa en vez de seguir intentando; el mapa está en `docs/decisiones/semana06.md`. Sin límite no hay entrega | [Curso] |
| OBJ-10 | Resiliencia y capa de proveedor desacoplada | Se fuerzan y se muestran tres fallos (timeout, salida que no valida, caída o 429) sin stack trace al usuario; el proveedor cambia por configuración | [Curso] |
| **Evidencia** | | | |
| OBJ-11 | Medir con método y mostrar trazas | `eval/` con 5 a 10 casos verificados a mano (al menos uno de rechazo); cuatro cifras con fecha y método en `docs/decisiones/semana10.md` (tasa de éxito, costo por tarea, mediana de latencia, recuperación si aplica); trazas visibles en vivo | [Curso] |
| OBJ-12 | Sustentar la necesidad del LLM y del agente frente a alternativas más simples | Se listan las decisiones que toma el agente (qué consultar, cuánto retroceder, cuándo declarar información insuficiente) y lo que va deliberadamente en código; los seis ejes aplicados por componente; la misma suite corrida contra una versión sin modelo (informe por plantilla con métricas por código) | [Equipo] · [Curso] |
| OBJ-13 | Entrega autosuficiente, reproducible y con plan B | Una IA que recibe solo el README, en un clon limpio del repositorio público, levanta todo sin pasos manuales (`./run.sh` o `docker compose up --build`; Docker Compose v2 como único prerrequisito); un compañero lo corre en menos de 5 minutos y la prueba queda documentada con lo que falló y lo que se corrigió; la llave del API se toma del entorno o se pide por consola y nunca va en el repo, y si falta la app arranca igual y avisa con claridad; ante un fallo del proveedor, entra el proveedor de respaldo (RF-19) | [Curso] |
| OBJ-14 | Seguridad y gobernanza (hito del Momento 3) | `docs/seguridad.md` (modelo de amenazas, tríada evaluada, ataque propio documentado, hardening con antes y después), `docs/gobernanza.md` (cinco preguntas) y `docs/respuesta_auditoria.md` | [Curso] |
| OBJ-16 | Decisiones técnicas justificadas y defendibles | `docs/decisiones.md` registra cada decisión técnica (forma del sistema, stack, proveedor y modelo, estrategia de datos con o sin RAG, patrón síncrono, streaming o cola, base de datos y cada herramienta opcional que entre) con su razón técnica y la alternativa descartada, más el registro de iteración de las descripciones de herramientas; cada integrante responde por cualquiera de ellas | [Curso] · [Equipo] |

#### 2.3 Sobre las metas numéricas

No se fija un umbral de éxito del agente antes de medir la línea base. El curso califica el método (cifras con fecha, mediana, trazas) por encima del valor: un 6 de 10 sustentado con trazas puntúa más que un 10 de 10 sin evidencia. **[Curso]** El umbral, si se quiere, se define después de la primera medición.

---

### §3. Alcance

#### 3.1 Dentro del alcance

| Capacidad | Momento 2 (31-oct-2026) | Momento 3 (semana 14) |
|---|---|---|
| Alta mínima de paciente con id interno y registros diarios en texto libre (formulario simple y carga de datos sintéticos) | Sí | |
| Consulta, orden por fechas y métricas semanales calculadas por código | Sí | |
| Agente integrado que genera el borrador: decide qué consultar con herramientas de lectura y de métricas, compara semana vs anteriores y opera con los cuatro límites | Sí | |
| Borrador de informe semanal con fuente y criterio visibles | Sí | |
| Flujo de revisión y aprobación por enfermería; vista del médico solo con informes aprobados | Sí | |
| Sesión de demostración sin autenticación: se elige un usuario de demostración (enfermería o médico) y el servidor aplica con su rol la aprobación solo por enfermería y la vista del médico solo con informes aprobados (D26) | Sí | |
| Autenticación con usuario, contraseña y token, roles y cuentas, y gestión de usuarios por el administrador, reutilizando el RBAC/JWT del scaffold con seed propio | | Sí |
| Tablero de permisos por rol, con edición e invariantes | | Sí |
| Mapa de autonomía por acción (`docs/decisiones/semana06.md`) | Sí | |
| Suite `eval/`, trazas visibles y línea base de las cuatro cifras | Sí | |
| Repositorio público autosuficiente: `docker-compose.yml`, `run.sh`, `.env.example` y README como contrato de arranque, probado con una IA que solo lee el README | Sí | |
| Datos sintéticos y usuarios de demostración (enfermería y médico, sin credenciales) sembrados al primer arranque | Sí | |
| Credenciales de los usuarios de demostración en el README y usuario de administración | | Sí |
| Informe mensual de cuatro semanas con informe semanal aprobado, con la evolución semana a semana | Sí | |
| Seguridad, hardening, ficha de gobernanza y respuesta a la auditoría cruzada | | Sí |
| Cifras finales comparadas contra la línea base | | Sí |
| Exportación a PDF de informes aprobados | | Opcional |
| Dictado por voz con transcripción editable | | Opcional |

Los hitos son **[Propuesta]** salvo lo que el equipo decidió (D4 y D23): el Momento 2 presenta lo principal, el agente y los informes semanal y mensual, y el Momento 3 presenta la aplicación completa, con la autenticación y lo que depende de ella. La entrega es el jueves 29 de octubre a medianoche, así que desde el 26 quedan cuatro días de construcción. PDF y voz solo entran si todo lo demás está cerrado; si entran, hay que poder justificar la tecnología elegida (D13 y D14).

#### 3.2 Fuera de alcance

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
- **Despliegue en la nube para producción.** El entregable es el enlace al repositorio público de GitHub, que se levanta con Docker Compose; no se entrega ZIP. **[Equipo]**

#### 3.3 Supuestos y restricciones

- **Tiempo:** el enlace al repositorio se entrega en el aula virtual hasta el jueves 29 de octubre de 2026 a medianoche, porque el profesor necesita un día para probarlo. La demo es el sábado 31, en vivo, de 6 min más 3 min de preguntas; poner el enlace solo cuenta como entregado y, sin presentación, es como no haber entregado. El profesor aconsejó congelar la versión del jueves hasta terminar la presentación: se hace con un tag, p. ej. `m2-entrega` **[Propuesta]**. El Momento 3 es en la semana 14. **[Curso]** (clase del 24-oct)
- **Sin autenticación en el Momento 2:** el equipo decidió dejarla, con las credenciales, las cuentas y la administración, para el Momento 3 (D23). Hasta entonces la app abre sin inicio de sesión y se trabaja con un usuario de demostración elegido en pantalla, sin contraseña (D26, propuesta). **[Equipo]**
- **Equipo:** dos personas, y ambas deben poder explicar cualquier función que se les señale. **[Curso]**
- **Costo cero:** el curso no exige pagar. El taller de la semana 6 sugiere Google AI Studio (sin tarjeta) y Groq como alternativa; ambos con capa gratuita. **[Curso]**
- **Plan B en la demo:** ante un fallo del proveedor, backoff y proveedor de respaldo (semana 9, RF-19) **[Curso]**. No hay modo simulado: el curso solo lo pide en el taller guiado y el reto de la semana 6, y la demo exige la IA en vivo (D16, descartada). Si falla la red, el plan B es una conexión alternativa, p. ej. los datos del celular **[Propuesta]**.
- **Datos:** solo sintéticos, en el repo, en `eval/`, en trazas y en capturas. **[Equipo]**
- **Privacidad hacia el proveedor:** al LLM sale lo mínimo, sin nombre ni documento (id interno). **[Propuesta]**
- **Entrega autosuficiente:** el profesor da el README a una IA y ejecuta solo sus pasos; si la aplicación no levanta así, no la revisa. Todo corre en Docker; el README dice el motor de BD, la imagen y el comando; no hay labor manual. **[Curso]** (clase del 24-oct)
- **Repositorio público:** ninguna llave ni secreto real en el repo ni en el historial; las credenciales de demostración (desde el Momento 3, D23) son sintéticas, están en el README y solo existen con la semilla activa. **[Equipo]**
- **La IA debe estar integrada y funcionando en esta entrega,** y en vivo en la demo. **[Curso]** (clase del 24-oct)
- **Qué pregunta el profesor:** no pregunta por líneas de código sino por las decisiones técnicas (por qué ese proveedor, con o sin RAG, síncrono o asíncrono, por qué cada herramienta); cada integrante responde por todas. La rúbrica escrita sigue pidiendo dominio individual del código. **[Curso]**
- **Evaluaciones y observabilidad:** en clase el profesor las trató como deseables en este momento («si alcanzan»); la rúbrica escrita las puntúa (25 puntos), así que se mantienen en versión mínima (D25). **[Curso]**

#### 3.4 Dónde cae cada fase del ciclo de vida

| Fase | Qué produce | Entregable del curso | Estado |
|---|---|---|---|
| 1. Planificación | Objetivos, alcance, restricciones | `docs/SPEC.md` §1–§3 | **Cerrada** (línea base v0.4) |
| 2. Análisis de requisitos | Necesidades por actor, casos de uso, requisitos funcionales y no funcionales, reglas de negocio, criterios de aceptación | `docs/REQUISITOS.md` | **En curso** |
| 3. Diseño | Arquitectura, modelo de datos, herramientas del agente, flujo de estados, mockups de las pantallas clave y decisiones (forma del sistema, stack, datos, proveedor, patrón de integración, arranque en frío) | `docs/SPEC.md` §4–§7, `docs/PLAN.md`, `docs/decisiones.md` (con el detalle en `docs/decisiones/`: `semana06.md` autonomía, `semana08.md` datos) | Siguiente (medio día: D9 y D24 se deciden primero) |
| 4. Implementación | Agente, herramientas, app, flujo de aprobación, permisos | Código; `skills/` (`especifica-encargo`, `planea-trabajo`, `ejecuta-plan`, `valida-resultado`) | Pendiente |
| 5. Pruebas y evaluación | Suite dorada, cuatro cifras, trazas | `eval/`, `docs/decisiones.md` (cifras; detalle en `docs/decisiones/semana10.md`) | Pendiente |
| 6. Entrega | Repositorio público reproducible y demo | README, `docker-compose.yml`, `run.sh`, `.env.example`, `docs/decisiones.md` | Momento 2: enlace el jueves 29-oct, demo el sábado 31-oct |
| 7. Seguridad, gobernanza y cierre | Autenticación y cuentas, amenazas, hardening, ficha, auditoría | `docs/seguridad.md`, `docs/gobernanza.md`, `docs/respuesta_auditoria.md` | Momento 3 |

## Anexo C. Análisis de requisitos (REQUISITOS v0.3, versión compacta)

Es el texto final de los requisitos sin la columna de criterios de aceptación ni la trazabilidad de objetivos; ambas están en `docs/REQUISITOS.md`. Hitos: **M2-N** núcleo del Momento 2, **M2-S** Momento 2 si hay tiempo, **M3** Momento 3 y **Opc** opcional.

### Necesidades de los interesados

| Actor | Necesidad | Requisitos |
| --- | --- | --- |
| Enfermería | No releer todos los registros ni redactar el informe a mano cada semana | RF-08 a RF-13 |
| Enfermería | Ver la evolución frente a semanas anteriores sin que se pierda un síntoma que reaparece | RF-07, RF-10 |
| Enfermería | Revisar rápido con la evidencia a la mano, corregir y aprobar | RF-24 a RF-27 |
| Enfermería | Registrar cada día sin fricción: texto libre y, opcionalmente, voz | RF-02, RF-38 |
| Médico | Recibir informes consistentes y aprobados que muestren la evolución en el tiempo | RF-28, RF-29, RF-36 |
| Médico | Registrar entradas diarias cuando le corresponda | RF-30 |
| Administrador | Gestionar usuarios y permisos sin acceder a datos clínicos (Momento 3) | RF-31 a RF-34 |
| Evaluador del curso | Levantar el repositorio con una IA que solo lee el README, sin pasos manuales; ver en 6 minutos el ciclo completo con el agente funcionando, límites, trazas y fallos forzados; y que el equipo justifique cada decisión técnica | RF-05, RF-16 a RF-22, RNF-08, RNF-13, RNF-14 |

### Casos de uso

| CU | Actor | Qué hace |
| --- | --- | --- |
| CU-01 Registrar entrada diaria | Enfermería, Médico | Escribe (o dicta) cómo está el paciente hoy |
| CU-02 Generar borrador semanal | Enfermería | Pide el informe de un paciente y una semana; el agente lo redacta |
| CU-03 Revisar y corregir | Enfermería | Lee el borrador, abre la evidencia y lo corrige |
| CU-04 Aprobar o rechazar | Enfermería | Decide; el sistema registra quién y cuándo |
| CU-05 Consultar informes aprobados | Médico | Ve la evolución del paciente por período |
| CU-06 Gestionar usuarios y permisos (Momento 3) | Administrador | Crea usuarios, ve y edita permisos |
| CU-07 Ver la traza de una ejecución | Enfermería; equipo en la demo | Ve qué consultó el agente y con qué resultado |
| CU-08 Generar informe mensual | Enfermería | Consolida cuatro semanas completas con informe semanal aprobado (RN-10) |
| CU-09 Exportar a PDF | Médico, Enfermería | Descarga un informe aprobado |
| CU-10 Levantar la aplicación en frío | Evaluador del curso, compañero | Clona el repositorio público, sigue el README, ejecuta `./run.sh`, abre la aplicación y elige un usuario de demostración (D26); desde el Momento 3 inicia sesión con sus credenciales |


### Flujo crítico: generar → revisar → aprobar → consultar

1. Enfermería elige paciente y período (una semana, o un mes de cuatro semanas aprobadas con RF-36 y RN-10) y pide el borrador (RF-08).
2. El agente consulta con sus herramientas, decide cuántas semanas anteriores revisar y redacta. El código valida el esquema, las citas y las cifras (RF-09 a RF-13).
3. El informe queda `PENDIENTE_REVISION`, o `INCOMPLETO` si se agotó un límite (RF-17).
4. Enfermería lee, abre la evidencia y corrige si hace falta (RF-24, RF-25).
5. Enfermería aprueba (RF-26) o rechaza con motivo (RF-27).
6. El médico ve el informe aprobado (RF-28).

**Excepciones:** semana sin datos suficientes, el informe lo declara (RF-14) · fallo del proveedor, mensaje honesto y respaldo (RF-19) · entrada con instrucciones, se trata como dato (RF-21).

### Flujo de arranque en frío (CU-10)

1. El evaluador clona el repositorio público y entrega el README a su IA.
2. La IA ejecuta los pasos del README: `./run.sh` o `docker compose up --build` (RNF-08, RNF-13).
3. `run.sh` crea `.env`, genera los secretos locales y resuelve la llave del proveedor: entorno o consola.
4. Docker Compose levanta la base de datos, el backend y el frontend; las migraciones y la semilla se aplican solas (RF-05 y los usuarios de demostración, RF-39; desde el Momento 3, también las credenciales).
5. El evaluador abre la URL del README y elige un usuario de demostración (D26); desde el Momento 3 inicia sesión con las credenciales del README (RF-31, RF-39).
6. Genera un informe con la IA real; si falta la llave, la app avisa con claridad (RNF-13).

**Excepciones:** sin terminal y sin llave, no se queda esperando y arranca avisando que falta la llave · llave inválida, mensaje honesto y respaldo (RF-19) · reinicio con la semilla, sin duplicados (RF-05).

### Requisitos funcionales (39)

En el Momento 2 no hay autenticación (D23): los usuarios de demostración existen desde el arranque, sin credenciales, y la persona elige con cuál trabaja; el servidor usa su rol y su identidad para separar funciones y para registrar quién hizo cada acción (D26, propuesta). Las credenciales, el inicio de sesión, la administración de usuarios y el tablero de permisos son del Momento 3.

| ID | El sistema debe… | Hito |
| --- | --- | --- |
| **Pacientes y entradas** |  |  |
| RF-01 | Crear pacientes con un id interno y un alias legible; el LLM solo recibe el id interno | M2-N |
| RF-02 | Registrar una entrada diaria en texto libre asociada a paciente, fecha y autor, sin plantilla obligatoria | M2-N |
| RF-03 | Permitir corregir una entrada conservando la versión anterior y quién la cambió | M2-S |
| RF-04 | Listar las entradas de un paciente por fecha y filtrar por rango | M2-N |
| RF-05 | Cargar el conjunto sintético de demostración al primer arranque, de forma idempotente, solo con la semilla activa (`APP_SEED_DEMO=true`) y nunca en el perfil `prod` | M2-N |
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
| RF-20 | Permitir cambiar el proveedor del LLM por configuración y mostrarlo en vivo | M2-N |
| RF-21 | Tratar el texto de las entradas como dato y no como instrucción, y no dar al agente herramientas de salida (internet, correo, mensajes) | M2-N |
| RF-22 | Registrar la traza de cada ejecución (herramienta, parámetros, resultado, ms, tokens) y mostrarla en la app | M2-N |
| **Revisión y aprobación** |  |  |
| RF-23 | Manejar los estados del informe `PENDIENTE_REVISION`, `INCOMPLETO`, `APROBADO` y `RECHAZADO`, con transiciones validadas en el servidor | M2-N |
| RF-24 | Mostrar a enfermería el informe en lenguaje claro y, a un clic, la evidencia: entradas citadas, criterio de comparación y llamadas de herramientas | M2-N |
| RF-25 | Permitir a enfermería editar el borrador antes de aprobar, conservando la versión original del modelo y las ediciones | M2-N |
| RF-26 | Permitir aprobar un informe solo al rol enfermería y registrar quién y cuándo | M2-N |
| RF-27 | Permitir a enfermería rechazar un informe con motivo y pedir uno nuevo | M2-S |
| **Consulta del médico** |  |  |
| RF-28 | Permitir al médico listar pacientes y consultar los informes `APROBADO` por paciente y período, del más reciente al más antiguo | M2-N |
| RF-29 | Mostrar al médico, en un informe aprobado, la evidencia que lo respalda | M2-S |
| RF-30 | Permitir al médico registrar entradas diarias (supuesto D12) | M2-S |
| **Usuarios, roles y auditoría** |  |  |
| RF-31 | Autenticar con usuario y contraseña y usar un token con expiración | M3 |
| RF-32 | Manejar los roles ENFERMERIA, MEDICO y ADMIN, más un rol técnico AGENTE, con permisos por módulo y acción según la matriz de la SPEC §4. En M2 se aplican con el rol del usuario de demostración elegido (D26), solo con ENFERMERIA, MEDICO y AGENTE; ADMIN y las cuentas con credenciales llegan en M3 | M2-N (usuario elegido) · M3 (cuentas) |
| RF-33 | Permitir al administrador crear, actualizar y desactivar usuarios, sin leer datos clínicos | M3 |
| RF-34 | Mostrar el tablero de permisos por rol (primero lectura, luego edición), con el permiso Aprobar fijo para enfermería | M3 |
| RF-35 | Registrar en auditoría las acciones sensibles: aprobar, rechazar, editar entradas o informes, registrar entradas por parte del médico (D12) y, en el Momento 3, cambiar permisos (quién, cuándo, antes y después). El «quién» es el usuario de demostración elegido en el Momento 2 y el usuario autenticado en el Momento 3 | M2-S |
| RF-39 | Crear al primer arranque, de forma idempotente, los usuarios de demostración, solo con la semilla activa (`APP_SEED_DEMO=true`) y nunca en el perfil `prod`: desde el Momento 2, enfermería y médico con nombres ficticios y sin credenciales; desde el Momento 3, además el administrador y las credenciales, documentadas en el README | M2-N (usuarios) · M3 (credenciales) |
| **Informe mensual y opcionales** |  |  |
| RF-36 | Generar el informe mensual de cuatro semanas completas consecutivas (RN-10), a partir de las entradas y eventos de esas semanas, mostrando la evolución semana a semana. Solo se genera si las cuatro semanas tienen informe semanal aprobado; si falta alguna, no se genera y se indica cuál. Pasa por la misma revisión de enfermería antes de llegar al médico (RN-01, RN-02) | M2-N |
| RF-37 | Exportar a PDF un informe aprobado, con aprobador, fecha, id del informe y nota de asistencia de IA (D13) | Opc |
| RF-38 | Dictar una entrada por voz con transcripción editable antes de guardar, origen `VOZ` y sin guardar el audio (D14) | Opc |

### Requisitos no funcionales (14)

| ID | Categoría | Requisito |
| --- | --- | --- |
| RNF-01 | Seguridad | Autorización en el servidor para toda operación; la UI solo oculta. En M2 la autorización usa el rol del usuario de demostración elegido (D26): separa funciones pero no es un control de seguridad; la autenticación llega en M3. Mínimo privilegio; ninguna llave ni secreto real en el repo ni en el historial (el repositorio es público); las credenciales de demostración (desde M3) son sintéticas, están en el README y solo existen con la semilla activa; usuario de BD con permisos mínimos |
| RNF-02 | Privacidad | Solo datos sintéticos. Al LLM solo salen el id interno y el texto de las entradas, nunca nombre ni documento. Sin audio guardado |
| RNF-03 | Seguridad del agente | Hay datos privados y contenido no confiable; la tercera pata (salida hacia afuera) se mantiene cerrada |
| RNF-04 | Robustez | Sin stack trace al usuario; los tres fallos forzables (timeout, salida que no valida, caída o 429) se manejan |
| RNF-05 | Rendimiento | La generación no bloquea la interfaz: estados honestos o streaming; el patrón elegido (D24) se justifica en `docs/decisiones.md`. La latencia se mide como mediana y se reporta, sin umbral previo |
| RNF-06 | Costo | Costo cero: capa gratuita de los proveedores. El costo por tarea se mide |
| RNF-07 | Observabilidad | Trazas y auditoría consultables; los fallos se explican con la traza |
| RNF-08 | Reproducibilidad | Repositorio público autosuficiente: `./run.sh` o `docker compose up --build`; README como contrato ejecutable por una IA que solo lo lea (pasos lineales, motor de BD, imagen, comando y URL); Docker Compose v2 como único prerrequisito; variables en `.env.example`; la prueba del compañero queda documentada con lo que falló y lo que se corrigió |
| RNF-09 | Usabilidad | UI en español; lenguaje claro primero y evidencia a un clic; estados honestos |
| RNF-10 | Integridad de datos | Las entradas y los informes no se borran, solo se corrigen con versión; las entradas son de solo lectura para el agente |
| RNF-11 | Calidad | Pruebas automatizadas de las reglas críticas: aprobación, permisos, validador de cifras y citas, esquema. Commits convencionales. Cada integrante responde por las decisiones técnicas (qué se hizo y por qué) y puede explicar cualquier bloque que se le señale: la rúbrica escrita lo exige, aunque el profesor dijo en clase que preguntará sobre todo por decisiones |
| RNF-12 | Cumplimiento del curso | Estructura de entrega, límites del agente, `eval/` y cifras con fecha, según `AGENTS.md`; enlace al repositorio entregado en el aula virtual hasta el jueves 29-oct a medianoche, con la versión congelada en un tag hasta la presentación |
| RNF-13 | Arranque en frío | La aplicación completa (BD, backend, frontend, migraciones y semilla) levanta con un comando y sin labor manual en una máquina limpia con solo Docker Compose v2; la llave del proveedor se toma del entorno o se pide por consola si hay terminal, se guarda en `.env` (fuera del repo) y, si no hay llave ni terminal, el arranque no se bloquea y la app avisa con claridad que falta la llave |
| RNF-14 | Decisiones justificadas | `docs/decisiones.md` registra cada decisión técnica (forma del sistema, stack, proveedor y modelo, estrategia de datos con o sin RAG, patrón síncrono, streaming o cola, base de datos y cada herramienta opcional que entre) con su razón técnica y la alternativa descartada; incluye el registro de iteración de las descripciones de herramientas y la prueba del compañero |

### Reglas de negocio (10)

| ID | Regla | Origen |
|---|---|---|
| RN-01 | Un informe solo llega al médico en estado `APROBADO` | [Equipo] |
| RN-02 | Solo enfermería aprueba; la transición se valida en el servidor | [Equipo] |
| RN-03 | El informe describe evolución documentada; no diagnostica ni recomienda tratamiento o dosis | [Equipo] |
| RN-04 | Toda cifra del informe la calcula el código, no el LLM | [Equipo] |
| RN-05 | Toda afirmación del informe cita al menos una entrada | [Curso] |
| RN-06 | Una semana va de lunes a domingo. El informe se compara con la semana anterior y el agente decide cuántas más atrás revisar, con tope | [Equipo] (D10) |
| RN-07 | El agente nunca modifica las entradas originales | [Equipo] |
| RN-08 | Si faltan datos, el informe lo declara y no los rellena | [Equipo] |
| RN-09 | Se permiten varias entradas el mismo día; dentro del día se ordenan por hora de creación | [Propuesta] |
| RN-10 | Un mes son cuatro semanas completas consecutivas (lunes a domingo, RN-06). El informe mensual solo se genera si cada una tiene informe semanal `APROBADO`; si falta alguna, no se genera y se indica cuál | [Equipo] (semanas aprobadas) · [Propuesta] (cuatro semanas y no mes calendario) (D4) |

### Modelo conceptual preliminar

Solo entidades y relaciones. Atributos, tipos y tablas se definen en el diseño.

| Entidad | Qué es | Relaciones clave |
|---|---|---|
| Usuario, Rol, Permiso | Quién entra y qué puede hacer (en el Momento 2, usuarios de demostración sin credenciales cuyo rol aplica el servidor, D26; credenciales y administración de cuentas desde el Momento 3) | Un usuario tiene un rol; un rol agrupa permisos por módulo y acción |
| Paciente | Persona seguida, identificada por id interno | Tiene muchas entradas e informes |
| Entrada | Registro diario en texto libre (fecha, autor, versiones) | Pertenece a un paciente; tiene eventos derivados |
| Evento | Dato estructurado derivado por el LLM de una entrada (tipo, descripción normalizada, fecha) | Enlaza a su entrada original |
| Informe | Documento por paciente y período, con estado, contenido estructurado, versión del modelo, versión editada, aprobador y fecha | Tiene afirmaciones; tiene una ejecución del agente |
| Afirmación | Frase del informe con su cita | Referencia una o más entradas |
| Ejecución del agente | Traza de pasos: herramienta, parámetros, resultado, ms, tokens | Asociada a un informe |
| Auditoría | Acción sensible: quién, cuándo, antes y después | Referencia al objeto afectado |

### Supuestos de trabajo y preguntas abiertas

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
| Identidad en el Momento 2 | Sin autenticación; el usuario de demostración elegido (enfermería o médico, sembrado sin credenciales) viaja en cada petición y el servidor lo usa para aprobar, para filtrar lo que ve el médico y para registrar quién hizo cada acción. Propuesta por confirmar | RF-02, RF-26, RF-28, RF-32, RF-35, RNF-01 | D26 |
| Patrón de integración | Ejecución asíncrona del agente con eventos de progreso por SSE | RF-22, RNF-05 | D24 |
| Evaluaciones y observabilidad | En clase el profesor las trató como deseables; la rúbrica escrita las puntúa; versión mínima: 3 a 5 casos dorados con script y traza visible en la app | RF-22, OBJ-11 | D25 |

**Estado de las decisiones de la SPEC §7:** decididas D1, D2, D3, D4, D5, D7, D8, D9, D10, D11, D12, D13, D14, D17, D21, D22, D23, D24; propuestas D15, D26; supuestos D6, D19, D20, D25; por confirmar D18; descartada D16.

**Para el diseño (no bloquean el análisis):** proveedor del LLM (D15, propuesta), subir la versión de Java de 17 a 21 (parte pendiente de D9), cifras del conjunto sintético (D11 está decidida, sin valores; debe incluir al menos un paciente con cuatro semanas aprobadas para el informe mensual) y dónde se derivan los eventos (al guardar la entrada o al generar el informe). Ya decididas: stack (D9), estrategia de datos (D8), motor de PDF (D13), voz (D14) y patrón de integración (D24).

### Criterios de salida de la fase de análisis

- [x] Cada objetivo de la SPEC tiene al menos un requisito o entregable (§7). Verificado el 2026-10-25, incluido OBJ-16.
- [x] Cada requisito funcional tiene un criterio que se responde con sí o no. Verificado el 2026-10-25: los 39 RF.
- [ ] Los dos integrantes revisaron los supuestos de trabajo de §8.
- [ ] El catálogo de métricas (RF-07) y la estructura del informe (RF-11) se validaron con una persona de enfermería, o quedó registrado que no se pudo.
- [ ] La SPEC se actualizó con las decisiones D18 a D20 y los supuestos confirmados (D18 a D20 ya están en la SPEC; faltan los supuestos confirmados, incluidos D21 a D26).

Las casillas abiertas no bloquean el diseño; quedan como deuda con fecha límite propuesta: revisión de supuestos por los dos integrantes el lunes 26 y validación con enfermería (o constancia de que no se pudo) el miércoles 28 **[Propuesta]**.
