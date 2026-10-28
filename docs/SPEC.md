# SPEC — Patient Progress Auditor

> **Estado:** borrador v0.6 para aprobación · **Fase del ciclo de vida:** 1 — Planificación (cerrada, línea base) · en curso: 2 — Análisis de requisitos · siguiente: 3 — Diseño · **Fecha:** 2026-10-27
> **Equipo:** Breiner Martinez Muñoz y Andres Felipe Lopez Vivas · **Curso:** Ingeniería de Aplicaciones con IA (UCC)
>
> **Cambios de la v0.6 (decisiones del equipo del 27 de octubre de 2026):** el informe mensual pasa al Momento 2 junto con el semanal (D4), porque ese momento presenta lo principal de la aplicación: el agente y los informes. La autenticación y lo que depende de ella (inicio de sesión, credenciales, cuentas, administración de usuarios y tablero de permisos) pasa al Momento 3, cuando se presenta la aplicación completa (D23). El arranque siembra desde el Momento 2 el conjunto sintético y los usuarios de demostración, sin credenciales, y desde el Momento 3 las credenciales y el administrador. Para no perder la revisión humana que exige el curso ni saber quién hizo cada acción (D12), se propone una sesión de demostración sin autenticación en el Momento 2: se elige con qué usuario sembrado se trabaja (D26, propuesta). D24 queda escrita: ejecución asíncrona con eventos de progreso por SSE. Se define el mes: cuatro semanas completas consecutivas, cada una con informe semanal aprobado (D4). D9 pasa a decidida sin límite de momento: el stack aplica desde el Momento 2 y solo el RBAC/JWT del scaffold espera al Momento 3.
>
> **Cambios de la v0.5 (a partir de la clase del 24 de octubre de 2026):** la entrega pasa a ser el enlace al repositorio público de GitHub (decisión del equipo; el profesor dijo que privado es opcional), sin ZIP, con plazo el jueves 29 de octubre a medianoche y demo el sábado 31. El README es el contrato de arranque y lo ejecutará una IA del profesor que solo lo lee: debe levantar todo sin pasos manuales, con Docker, y la llave del API se toma del entorno o se pide por consola. La IA tiene que estar integrada y funcionando; el plan B es el proveedor de respaldo y el modo simulado se descarta (D16). El profesor preguntará por las decisiones técnicas y no por líneas de código. Se agregan D21 a D25 y el objetivo OBJ-16, se ajustan D9 y D13 a D16 y OBJ-13, y se siembra el arranque en frío en §4. Lo dicho en clase manda sobre la página del curso en lo logístico (plazo y formato); la rúbrica escrita se sigue usando para el puntaje.
>
> **Cambios de la v0.4:** se cierra la planificación; las fases se separan en análisis de requisitos (`docs/REQUISITOS.md`) y diseño; se registra la aprobación del profesor sobre la necesidad de IA (D17); los supuestos de trabajo del análisis quedan reflejados en D5, D6, D10 y D12, y se abren D18 a D20. La v0.3 fijó que el sistema es una aplicación con un agente integrado (D7) y sembró las decisiones que toma el agente. La v0.2 incorporó la semana 6 (autonomía por acción, criterio de parada, plan B sin internet) y las decisiones sobre permisos por rol, PDF y voz.
>
> Este documento desarrolla las secciones §1 a §3 de la spec (resumen, objetivos, alcance) y deja sembradas §4, §5 y §7. Los criterios de aceptación por requisito están en `docs/REQUISITOS.md`; la §6 los consolida en la fase de diseño.
>
> **Etiquetas de origen:** **[Curso]** lo exige o lo recomienda el curso (la página o lo dicho en clase) · **[Equipo]** ya lo definió el equipo (presentación del proyecto y `AGENTS.md`) · **[Propuesta]** lo propone este borrador y hay que confirmarlo. Ninguna meta numérica va sin etiqueta.

---

## §1. Resumen

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

### Actores

| Actor | Qué hace con el sistema |
|---|---|
| Enfermería | Registra, dispara o consulta el informe, lo revisa, lo corrige y lo aprueba |
| Médico | Consulta los informes aprobados y, según D12, también puede registrar |
| Administrador | Gestiona usuarios y roles desde el Momento 3; no lee datos clínicos **[Propuesta]** |
| Equipo de desarrollo | Construye, evalúa y sustenta |
| Evaluador del curso | Levanta el repositorio con una IA que solo lee el README (el día antes de la demo) y revisa la demo en vivo (6 min de demo + 3 de preguntas), donde pregunta por las decisiones técnicas |

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
- **Despliegue en la nube para producción.** El entregable es el enlace al repositorio público de GitHub, que se levanta con Docker Compose; no se entrega ZIP. **[Equipo]**

### 3.3 Supuestos y restricciones

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

### 3.4 Dónde cae cada fase del ciclo de vida

| Fase | Qué produce | Entregable del curso | Estado |
|---|---|---|---|
| 1. Planificación | Objetivos, alcance, restricciones | `docs/SPEC.md` §1–§3 | **Cerrada** (línea base v0.4) |
| 2. Análisis de requisitos | Necesidades por actor, casos de uso, requisitos funcionales y no funcionales, reglas de negocio, criterios de aceptación | `docs/REQUISITOS.md` | **En curso** |
| 3. Diseño | Arquitectura, modelo de datos, herramientas del agente, flujo de estados, mockups de las pantallas clave y decisiones (forma del sistema, stack, datos, proveedor, patrón de integración, arranque en frío) | `docs/SPEC.md` §4–§7, `docs/PLAN.md`, `docs/decisiones.md` (con el detalle en `docs/decisiones/`: `semana06.md` autonomía, `semana08.md` datos) | Siguiente (medio día: D9 y D24 se deciden primero) |
| 4. Implementación | Agente, herramientas, app, flujo de aprobación, permisos | Código; `skills/` (`especifica-encargo`, `planea-trabajo`, `ejecuta-plan`, `valida-resultado`) | Pendiente |
| 5. Pruebas y evaluación | Suite dorada, cuatro cifras, trazas | `eval/`, `docs/decisiones.md` (cifras; detalle en `docs/decisiones/semana10.md`) | Pendiente |
| 6. Entrega | Repositorio público reproducible y demo | README, `docker-compose.yml`, `run.sh`, `.env.example`, `docs/decisiones.md` | Momento 2: enlace el jueves 29-oct, demo el sábado 31-oct |
| 7. Seguridad, gobernanza y cierre | Autenticación y cuentas, amenazas, hardening, ficha, auditoría | `docs/seguridad.md`, `docs/gobernanza.md`, `docs/respuesta_auditoria.md` | Momento 3 |

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

"El médico solo ve lo aprobado" es una regla de estado: va en la consulta, no en un permiso. Cada cambio en el tablero queda registrado (quién, cuándo, antes y después). En el Momento 2 los roles ENFERMERIA y MEDICO se aplican con el usuario de demostración elegido (D26); la administración, las cuentas y el tablero llegan en el Momento 3 (D23).

### Arranque en frío (semilla de D22 y D23)

El profesor da el README a una IA y ejecuta solo sus pasos, así que este camino se construye primero y se congela al final. El rol AGENTE es técnico y no tiene usuario con contraseña.

| Pieza | Qué hace | Estado |
|---|---|---|
| `docker-compose.yml` | Levanta la base de datos (PostgreSQL, imagen y versión fijadas), el backend y el frontend; los builds son multi-etapa, así que no hace falta Java ni Node en la máquina | Pendiente (hoy no existe, y el Dockerfile del backend no tiene `CMD`) |
| `run.sh` | Verifica Docker, crea `.env` desde `.env.example`, genera `JWT_SECRET` y `DB_PASSWORD`, resuelve la llave del proveedor (entorno o consola; sin ninguna de las dos, arranca igual y avisa que falta) y ejecuta `docker compose up --build` | Pendiente |
| Migraciones Flyway | Esquema y los roles y permisos del dominio (ENFERMERIA, MEDICO, ADMIN) | Parcial: existe el RBAC genérico, solo con el rol ADMIN |
| Semilla de demostración | El conjunto sintético y los usuarios de demostración (enfermería y médico, sin credenciales) desde el Momento 2, y las credenciales y el administrador desde el Momento 3 (D23); idempotente, solo con `APP_SEED_DEMO=true` y nunca en `prod` | Pendiente |
| README | Pasos lineales que una IA pueda ejecutar: prerrequisito (Docker Compose v2), motor de BD, imagen, comando, URL, usuarios de demostración elegibles (Momento 2) o con credenciales (Momento 3), cómo verificar que está arriba y cómo cambiar de proveedor | Pendiente |

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
- Sin internet en la demo. *Esperado: aviso claro, sin stack trace. No hay modo simulado (D16, descartada): el plan B es una conexión alternativa.*
- Enfermería edita el borrador: ¿se conserva la versión original generada por el modelo?
- Dictado por voz con errores de transcripción (opcional M3). *Esperado: el texto es editable antes de guardar y queda con origen `VOZ`.*
- Arranque en una máquina limpia, sin llave del API y sin terminal (por ejemplo, ejecutado por una IA). *Esperado: no se queda esperando; toma la variable de entorno o arranca igual y avisa con claridad que falta la llave.*
- Llave del API inválida, vencida o sin cuota (se cubre con RF-19).
- Reinicio con la semilla activa, o puerto ocupado en la máquina del evaluador.

## §6. Criterios de aceptación

Los criterios por requisito están en `docs/REQUISITOS.md` (columna "Criterio de aceptación"), cada uno con respuesta sí o no. Esta sección los consolida en la fase de diseño.

**Puerta de aceptación de la entrega** (si falla, el profesor no revisa lo demás):

- ¿Un clon limpio del repositorio público, con una IA que recibe solo el README, termina con la aplicación corriendo y sin pasos manuales?
- ¿La app abre con los datos de demostración y se puede trabajar con un usuario de demostración de enfermería y uno de médico, sin pasos manuales? Desde el Momento 3: ¿se puede iniciar sesión con cada usuario de demostración del README?
- ¿Una generación de informe llama a una IA real, y si falta la llave la app avisa con claridad en vez de fallar con un error crudo?
- ¿El repositorio y su historial están libres de llaves y secretos reales?

## §7. Decisiones

| # | Decisión | Estado | Detalle |
|---|---|---|---|
| D1 | Revisión humana obligatoria antes de que el informe llegue al médico | Decidida **[Equipo]** | Alternativa descartada: envío automático |
| D2 | Reparto código / LLM / humano | Decidida **[Equipo]** | Ver §1 |
| D3 | El sistema no diagnostica | Decidida **[Equipo]** | Ver §3.2 |
| D4 | Informe semanal y mensual en el Momento 2 | Decidida **[Equipo]** | El Momento 2 presenta lo principal de la aplicación, el agente y los informes, y por eso incluye el semanal y el mensual (RF-36, OBJ-03). Reemplaza la propuesta de dejar el mensual para el Momento 3; el tiempo se compensa moviendo la autenticación al Momento 3 (D23). Un mes son cuatro semanas completas consecutivas (lunes a domingo) y el mensual solo se genera si las cuatro tienen informe semanal aprobado (RN-10), esto último decidido por el equipo. Se propone cuatro semanas y no el mes calendario porque las semanas de lunes a domingo cruzan los límites de mes y un mes calendario tiene entre cuatro y cinco: sería ambiguo qué semanas deben estar aprobadas. Es la ejecución más pesada del agente (D24) |
| D5 | Origen de los registros | Decidida **[Equipo]** | Captura mínima propia + semilla sintética (propuesto) vs. solo carga por script |
| D6 | ¿El médico puede ver borradores? | Supuesto de trabajo: no | La presentación dice que solo tras la aprobación; la descripción inicial dice que podría revisarlos directamente. Mientras se decide, solo ve `APROBADO` |
| D7 | Forma del sistema | Decidida **[Equipo]** | Aplicación con un agente integrado (ver §1), como exige la rúbrica del Momento 2. La defensa frente a "¿por qué agente y no workflow?" se hace con las decisiones reales del agente (§4) y se mide: la misma suite contra una plantilla sin LLM (obligatoria, OBJ-12) y, opcionalmente, contra un workflow con LLM sin bucle |
| D8 | Estrategia de datos | Decidida **[Equipo]** | Por defecto, consulta directa a la BD y el LLM lee las notas; RAG solo si se mide que hace falta. Se documenta en `docs/decisiones/semana08.md` |
| D9 | Stack | Decidida **[Equipo]** | Se mantiene Java con Spring Boot y Angular, el stack del scaffold, desde el Momento 2. Alternativa descartada: Python, como los talleres (bucle, FastMCP, FastAPI), que obliga a reescribir lo existente; en Java el bucle del agente se escribe a mano (el taller lo resuelve en unas 80 líneas) y el agente vive en el backend Spring. Con builds multi-etapa en Docker el stack no condiciona al profesor, que solo necesita Docker, pero él lo va a preguntar: la razón es la velocidad de desarrollo con Flyway, el CI y la seguridad del scaffold ya montados. Del scaffold, solo el RBAC y el JWT esperan al Momento 3, con la autenticación (D23). Pendiente dentro de D9: subir a Java 21 (la página de entrega lo cita; hoy pom, Dockerfile y CI usan 17) |
| D10 | Qué es "una semana" y contra cuántas semanas anteriores se compara | Decidida **[Equipo]** | Lunes a domingo; se compara con la anterior y el agente decide cuántas más atrás revisar, con tope (RN-06) |
| D11 | Tamaño del conjunto sintético | Decidida **[Equipo]** | Cuántos pacientes y cuántas semanas; condiciona la suite de evaluación |
| D12 | ¿El médico también registra? | Decidida **[Equipo]** | La matriz de §4 lo asume sí; amplía el papel del médico, puede registrar pero se debe auditar que medico hizo el registro |
| D13 | Motor de PDF (opcional M3) | Decidida **[Equipo]** | Thymeleaf + HTML→PDF (una sola plantilla para pantalla y PDF). Jasper solo si ya existen plantillas `.jrxml`; si se usa, versión 7.0.7 o superior. Solo informes `APROBADO`. Si entra, el profesor preguntará por qué ese motor; no entra en el Momento 2 salvo que sobre tiempo |
| D14 | Dictado por voz (opcional M3) | Decidida **[Equipo]** | Whisper detrás de un puerto, con Groq como adaptador y uno local por perfil de compose. Transcripción siempre editable, sin guardar audio, origen `VOZ`. Es una comodidad de entrada, no parte de la justificación de IA. El profesor usó Whisper como ejemplo de lo que preguntará («por qué Whisper, cómo lo procesaron»): si entra, hay que defenderlo, y no entra en el Momento 2 salvo que sobre tiempo |
| D15 | Proveedor del LLM | Propuesta | Google AI Studio como principal y Groq como respaldo o alternativa, los dos del taller de la semana 6. Tener ambos permite demostrar la capa del proveedor cambiando en vivo. Verificar los modelos vigentes antes de fijarlos. La ruta principal de la entrega es el proveedor real: la IA tiene que estar integrada y funcionando. La llave la aporta quien ejecuta (se toma del entorno o se pide por consola, D22) y nunca va en el repo público. El profesor preguntará por qué ese proveedor y modelo: registrar latencia, costo por token, tool calling y ventana de contexto en `docs/decisiones.md` |
| D16 | Modo simulado del proveedor | Descartada **[Equipo]** | Adaptador de replay con respuestas grabadas. Se descarta: el curso solo pide un modo simulado en el taller guiado y el reto de la semana 6, no en el proyecto del Momento 2, y en clase el profesor pidió la demo en vivo con la IA integrada y funcionando. El plan B ante un fallo del proveedor es el proveedor de respaldo (semana 9, RF-19). Consecuencias: las pruebas de las reglas críticas (aprobación, permisos, validadores) no necesitan modelo; los casos dorados corren con llave real; si falla la red en la demo, el plan B es una conexión alternativa |
| D17 | La aplicación necesita IA (prueba de necesidad) | Decidida: aprobada por el profesor | Registrar la fecha y el medio (clase o correo) en `docs/decisiones/` como evidencia |
| D18 | Catálogo de métricas semanales | Por confirmar | El de RF-07 es una propuesta; validar con una persona de enfermería |
| D19 | Disparo de la generación del informe | Supuesto de trabajo | Manual (botón) en M2; programado al cierre de semana, M3 |
| D20 | Campo de consulta libre al pedir el informe | Supuesto de trabajo: no en M2 | Sin campo, el caso de rechazo (OBJ-08) se construye con una entrada que pide un diagnóstico |
| D21 | Forma de entrega | Decidida **[Equipo]** | Enlace al repositorio público de GitHub, sin ZIP: el profesor dijo en clase (24-oct) que el privado es opcional y que solo se envía el enlace, en el aula virtual, hasta el jueves 29 a medianoche. Por ser público, ninguna llave ni secreto real entra al repo ni al historial. El registro de decisiones es `docs/decisiones.md` (el profesor aceptó «otro documento» o el README), con el detalle en `docs/decisiones/` |
| D22 | Arranque en frío autosuficiente y llave del API | Decidida **[Equipo]** | El profesor da el README a una IA y ejecuta solo sus pasos; si no levanta, no revisa. Todo en Docker Compose (BD, backend y frontend, con builds multi-etapa: el único prerrequisito es Docker Compose v2). `run.sh` verifica Docker, crea `.env` desde `.env.example`, genera `JWT_SECRET` y `DB_PASSWORD` y resuelve la llave: variable de entorno si existe; si no y hay terminal, la pide sin eco y la guarda en `.env` (el profesor lo recomendó así); si no hay terminal ni llave, no se queda esperando: arranca igual y avisa con claridad que falta la llave y cómo pasarla por la variable de entorno del proveedor. `docker compose up --build` directo se comporta igual. Alternativa descartada: quedarse esperando la llave, que dejaría colgada a una IA que ejecuta el script |
| D23 | Usuarios de demostración | Decidida **[Equipo]**: usuarios en el Momento 2, credenciales en el Momento 3 | El equipo decidió el 27-oct que la autenticación y lo que depende de ella pasan al Momento 3, cuando se presenta la aplicación completa. Los usuarios de demostración siguen sembrándose desde el Momento 2, sin credenciales, para que cada acción tenga un autor identificable (RF-02, RF-26, RF-35, y qué médico registra, D12) y se pueda elegir con cuál trabajar (D26): enfermería y médico con nombres ficticios. Un `ApplicationRunner` idempotente crea los usuarios y el conjunto sintético (RF-05) al arrancar y, desde el Momento 3, además el usuario de administración y las credenciales; solo corre con `APP_SEED_DEMO=true` y nunca en el perfil `prod`. Desde el Momento 3, la contraseña viene de una variable de entorno con valor documentado en `.env.example` y en el README, y se cifra con el mismo `PasswordEncoder` del registro normal: no hay hash en migraciones. Los roles y permisos del dominio sí van en una migración Flyway. Riesgo aceptado: credenciales de demostración públicas, de datos sintéticos |
| D24 | Patrón de integración | Decidida **[Equipo]** | El profesor preguntará «¿síncrono o asíncrono, y por qué?» y no quiere enterarse en plena presentación de que salió asíncrono porque la IA lo hizo. **Es asíncrono: el agente corre fuera de la petición HTTP y la pantalla recibe eventos de progreso reales por SSE, sin broker.** La petición de generación (semanal o mensual) valida el usuario de demostración elegido (D26), crea una ejecución en curso y responde de inmediato con su id; si ya hay una para el mismo paciente y período devuelve esa. Un ejecutor acotado en el mismo proceso corre el bucle. Cada paso (herramienta, validación, reintento, proveedor de respaldo, límite agotado) se escribe en la traza (RF-22) y se emite como evento desde esa misma fila, así que lo que se ve en vivo es lo que luego se consulta. El estado vive en la base de datos, no en la conexión: si el flujo se corta o se recarga la página, se reanuda desde el último id o se consulta el estado. «En curso» y «Generando…» no son estados del informe (RF-23); la ejecución termina en `PENDIENTE_REVISION` o `INCOMPLETO` (RF-17), o en un error honesto (RF-19). **Se transmiten pasos, no tokens:** la salida se valida completa (esquema, cifras y citas) y mostrar texto parcial sería mostrar texto sin validar. Descartadas: in-request síncrono (prohibido en la sustentación), streaming de tokens, cola con workers y broker (otro servicio en el arranque en frío) y polling como única vía. A probar el primer día: el proxy no debe acumular la respuesta (`X-Accel-Buffering: no`). En el Momento 2 no hay token y basta `EventSource`; con la autenticación (Momento 3, RF-31) el cliente pasa a `fetch` para enviar el `Authorization` sin poner el token en la URL. Verificación: ¿llega el primer evento antes de terminar la ejecución? Detalle en `docs/decisiones.md`. |
| D25 | Evaluaciones y observabilidad en el Momento 2 | Supuesto de trabajo: versión mínima | En clase el profesor las trató como deseables (observabilidad «si alcanzan», evaluaciones como automatización a tener); la rúbrica escrita las puntúa. Mínimo: 3 a 5 casos dorados (incluido el de rechazo) con su script y la traza de herramientas visible en la app; las cuatro cifras con fecha si alcanza el tiempo. Se amplía para el Momento 3 |
| D26 | Sesión de demostración en el Momento 2, sin autenticación | Propuesta | Como la autenticación pasa al Momento 3 (D23), en el Momento 2 la app abre sin inicio de sesión y la persona elige en pantalla, entre los usuarios de demostración sembrados, con cuál trabaja: una enfermera o un médico con nombres ficticios y sin contraseña. El usuario elegido viaja en cada petición y el servidor toma de él su rol y su identidad: solo Enfermería aprueba o rechaza (RN-02, RF-26), el Médico solo ve informes `APROBADO` (RN-01, RF-28) y el «quién» de RF-02, RF-26 y RF-35 es una persona, lo que permite auditar qué médico registró una entrada (D12). Es una separación de funciones para la demostración, no un control de seguridad: cualquiera puede cambiar de usuario, así que RNF-01 se cumple en el Momento 2 solo en esa medida y por completo desde el Momento 3, cuando la selección se reemplaza por el inicio de sesión (RF-31) con las credenciales de D23, sin cambiar las reglas, y entra el rol Administración. Alternativas: sin usuarios en el Momento 2, solo un rol (no recomendada: no se podría auditar qué médico registra ni habría cómo mostrar que el médico no ve borradores ni aprueba, OBJ-05) e inicio de sesión mínimo con contraseña (contradice la decisión de dejar la autenticación para el Momento 3). Por confirmar con el equipo |
