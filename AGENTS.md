# AGENTS.md — Patient Progress Auditor

## Qué es esto
- Auditor de la evolución de pacientes: enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA analiza los registros de cada semana, los compara con semanas anteriores y redacta un informe semanal o mensual. Enfermería lo revisa y aprueba antes de que llegue al médico.
- Es apoyo al seguimiento documental: **no diagnostica, no receta, no sustituye el criterio profesional**.
- Proyecto del curso *Ingeniería de Aplicaciones con IA* (UCC, Ingeniería de Sistemas). Las reglas del curso mandan sobre cualquier otra convención de este archivo.
- Reemplaza al proyecto anterior (AgroCortex). Nada de AgroCortex es contexto válido. La limpieza se ejecutó (rama `chore/limpiar-agrocortex`, ya fusionada); si aparece algún residuo con ese nombre, es un error de limpieza y se corrige.

## Dónde va el LLM y dónde no
Si se quita el modelo, los registros siguen existiendo pero el informe no. La frontera es esta:
- **Código determinista:** guardar y consultar registros, ordenar por fecha, calcular métricas (conteos, frecuencias, días con síntoma), permisos, validaciones y estados del informe. Los números los calcula el código, nunca el LLM.
- **LLM:** interpretar texto libre ("vomitó el lunes, ánimo alto"), proponer al registrar una entrada los eventos que el texto dice (síntoma, intensidad y veces, D27) y redactar la comparación entre semanas.
- **Humano:** quien registra confirma los eventos que propone la IA antes de guardar; enfermería contrasta las afirmaciones del informe con los registros originales y aprueba.
- No poner el LLM en lo que el código resuelve exacto. La utilidad del LLM y del agente se mide contra una solución convencional (sin modelo).

## Estado actual
Al 2026-10-29 está construido el **módulo de enfermería del Momento 2** (etapas E1 a E12b y E14 del plan; E15 es la documentación y la entrega). Lo que existe:
- `backend/`: Spring Boot 3.4.1, Java 17, Maven, PostgreSQL 16, Flyway (`V1` a `V8`). Paquete `com.patientprogressauditor`: `session` (usuario de demostración elegido, D26), `patient`, `entry` (registro con estructuración por IA y confirmación, D27), `metrics` (cifras calculadas por el código), `llm` (capa de proveedor: Groq principal, Gemini de respaldo, reintentos y conmutación), `agent` (bucle con cuatro límites, seis herramientas (una solo del mensual), validadores de cifras y citas), `run` (ejecución en segundo plano con SSE, D24), `report` (versiones, ediciones, aprobar y rechazar con transiciones validadas; el informe mensual de cuatro semanas aprobadas, RN-10, E13; el PDF del informe aprobado con JasperReports, RF-37, D13, E17), `structuring` (la llamada corta al registrar), `system` (estado de salud), `seed` (4 usuarios, 5 pacientes, 119 entradas y 5 informes sin llamar al modelo). 350 pruebas.
- `frontend/`: Angular 19, Node 20, rutas con hash. Pantallas de enfermería: pacientes, registro con vista previa de eventos, historial, generar informe semanal o mensual con progreso, revisión, informe con citas y traza, edición, aprobación, rechazo y «Descargar PDF» del informe aprobado. 121 pruebas. **No hay pantalla del médico** (E12c fuera): la regla de que solo ve lo aprobado está en el servidor y se prueba por API.
- `eval/`: 8 casos dorados de informes semanales, 3 del mensual y 24 de estructuración (8 con síntomas fuera del catálogo), la solución convencional (sin modelo), `run_eval.py`, 31 pruebas de la propia evaluación y resultados fechados. Las cuatro cifras están en `docs/decisiones/semana10.md`.
- `docker-compose.yml`, `run.sh`, `.env.example`, los dos Dockerfile y `nginx.conf`: arranque en un comando (E1). `README.md` es el contrato ejecutable por una IA.
- `docs/`: `SPEC.md`, `REQUISITOS.md`, los dos diccionarios, `PLAN.md` (con el cierre de cada etapa), `decisiones.md`, `decisiones/semana06.md`, `semana08.md` y `semana10.md`, `prueba_companero.md` (plantilla por llenar). `seguridad.md`, `gobernanza.md` y `respuesta_auditoria.md` son del Momento 3. `docs/03` a `docs/06` son documentación del scaffold (CI, seguridad y commits).
- `.github/workflows/`: CI, Security (CodeQL, Dependency Review, Gitleaks) y Qodana.
- **Stack (D9, decidido):** Spring Boot y Angular; el agente vive en el backend. Java se queda en 17 (razón en `docs/decisiones.md`, E15).
- **Aún no existe:** autenticación y JWT (`jjwt` está declarada y sin uso hasta el Momento 3, D23), pantalla del médico, fallos del proveedor forzables en vivo (se prueban con pruebas automáticas), `seguridad.md` y `gobernanza.md`.

## Cómo se corre y se prueba
- **Todo en Docker (el comando de la entrega):** `./run.sh` (crea `.env`, genera `DB_PASSWORD`, toma la llave del entorno o la pide solo si hay terminal, y ejecuta `docker compose up --build -d --wait`). Sin terminal y sin llave arranca igual y avisa. Interfaz en `http://localhost:4200`, API en `http://localhost:8080`; si un puerto está ocupado, `run.sh` usa el siguiente libre. Con llave: `GROQ_API_KEY=<llave> ./run.sh`. Detalle, usuarios y recorrido en el `README.md`.
- Backend (necesita un PostgreSQL desechable; las pruebas lo modifican): `cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:<puerto>/patient_auditor SPRING_DATASOURCE_USERNAME=patient_auditor SPRING_DATASOURCE_PASSWORD=<clave> mvn -B verify`. Para correrlo sin Docker: `mvn spring-boot:run` (perfil `dev`, `DB_PASSWORD` sin valor por defecto). Swagger en `/swagger-ui.html`. No hay `mvnw`: usa el `mvn` del sistema.
- Frontend: `cd frontend && npm start` (el proxy de `ng serve` manda `/api` a `localhost:8080`). Pruebas: `npx ng test --watch=false --browsers=ChromeHeadless --code-coverage`.
- Evaluación: `python3 eval/run_eval.py` (contra la app levantada, gasta cuota de la llave; úsela en una base desechable) y `python3 eval/run_eval.py --baseline-only` (sin app ni llave). Pruebas de la evaluación: `python3 -m unittest discover -s eval`.
- Variables: `scripts/check-env-example.sh` falla si un yml o el compose usan una variable que `.env.example` no documenta.
- `npm run lint` está roto (falta `@angular-eslint`): no lo uses como verificación.

Objetivo de entrega: `./run.sh` levanta todo con un comando, con solo Docker Compose v2 en la máquina. El profesor entrega el README a una IA y ejecuta únicamente sus pasos; un compañero con un clon limpio debe tenerlo corriendo en menos de 5 minutos. Ver "Entrega y prueba del profesor". Esta sección es la que lleva los comandos exactos: si cambian, se actualiza aquí y en el README.

## Entrega y prueba del profesor (clase del 24-oct-2026)
Lo dicho en clase manda sobre la página del curso en lo logístico (plazo y formato); la rúbrica escrita sigue mandando en el puntaje.
- **Plazo:** el enlace al repositorio se entrega en el aula virtual hasta el **jueves 29 de octubre de 2026 a medianoche**, porque el profesor necesita un día para probarlo. La demo es el sábado 31. Poner el enlace solo cuenta como entregado; sin presentación es como no haber entregado.
- **Formato:** enlace al repositorio de GitHub, **público** (decisión del equipo; el profesor dijo que privado es opcional). No se entrega ZIP. Como el repo es público, ninguna llave ni secreto real entra al repo ni al historial.
- **Congelar:** la versión del jueves queda en un tag (`m2-entrega`) y la demo corre desde ese tag; no se integra nada nuevo hasta terminar la presentación.
- **El README es el contrato.** El profesor se lo da a una IA y ejecuta solo sus pasos; si la aplicación no levanta así, no la revisa. Debe decir el motor de BD, la imagen de Docker y el comando, traer los scripts de datos de prueba y no dejar labor manual. En lo posible, todo en Docker (builds multi-etapa: solo hace falta Docker Compose v2).
- **Llave del API:** `run.sh` la toma del entorno; si no está y hay terminal, la pide y la guarda en `.env` (fuera del repo). Si no hay terminal ni llave, no se queda esperando: arranca igual y avisa con claridad que falta la llave. Nunca un `read` sin comprobar la terminal, porque una IA ejecutándolo se quedaría colgada.
- **La IA debe estar integrada y funcionando** en esta entrega; el plan B ante un fallo del proveedor es el proveedor de respaldo (backoff y conmutación, semana 9). En el Momento 2 la app abre con datos y usuarios de demostración (enfermería y médico, nombres ficticios, sin credenciales) y se elige con cuál trabajar, sin autenticación (D23, D26); las credenciales en el README y el administrador llegan en el Momento 3.
- **Demo en vivo, sin videos:** 6 min del ciclo completo (dónde entra el modelo, arquitectura, patrones de interacción, retos y cómo se usó la IA) y 3 min de preguntas. Observabilidad y evaluaciones: en clase el profesor las trató como deseables en este momento; la rúbrica escrita las puntúa, así que se mantienen en versión mínima (D25).
- **Qué pregunta:** no pregunta por líneas de código sino por las decisiones técnicas: por qué ese proveedor, con o sin RAG, síncrono o asíncrono, por qué cada herramienta. Cada integrante responde por todas; no puede descubrir en plena presentación que el sistema salió asíncrono "porque la IA lo hizo así". Las decisiones se documentan en `docs/decisiones.md`.
- **Prueba en frío obligatoria:** antes de entregar, un clon limpio y una IA que solo lea el README deben dejar la app corriendo. Es la puerta de aceptación (SPEC §6).

## Cómo trabajar (método del curso)
- **Loop, en orden:** explorar (sin escribir código) → planear (por escrito, lo revisa un humano) → ejecutar (una tarea del plan a la vez) → verificar contra los criterios de aceptación. Nunca se ejecuta sin plan. Nunca se hace commit sin verificar. Si no puedes verificar, dilo.
- **Dudas vs decisiones:** si algo de producto no está decidido en `docs/SPEC.md`, para y pregunta; no lo resuelvas en silencio. Al terminar cada tarea, lista las decisiones que tomaste y que nadie te pidió.
- **Spec primero:** `docs/SPEC.md` con siete secciones: resumen, objetivos medibles, fuera de alcance (nunca vacío), diseño, casos borde, criterios de aceptación (cada línea se responde con sí o no) y decisiones. Spec, plan (`docs/PLAN.md`) y tareas son documentos distintos; ninguna tarea de más de medio día.
- **Revisión con contexto fresco:** quien revisa no es quien escribió. Sesión nueva para revisar; una tarea, una sesión.
- **Skills:** los cuatro del curso, en `skills/` en la raíz (copias en `.claude/skills/` y `.opencode/skills/`), forman la cadena `especifica-encargo` → `planea-trabajo` → `ejecuta-plan` → `valida-resultado`. Úsalos cuando la tarea coincida; si uno falla, reporta cuál y qué se corrigió.
- **Permisos.** Sin preguntar: leer, editar en rama, correr tests. Siempre preguntar: borrar, push, comandos destructivos, instalar dependencias, tocar `.env` o credenciales.
- **Código explicable:** se puede usar toda la IA, pero cada integrante responde por la aplicación: por cada decisión técnica (qué se hizo y por qué) y por cualquier función que se le señale. No generes lo que el equipo no pueda explicar ni decidas en silencio algo que el profesor preguntará (proveedor, RAG, síncrono o asíncrono): déjalo escrito en `docs/decisiones.md`.

## Reglas del producto (no negociables)
- **Supervisión humana exigida por el sistema.** Un informe solo llega al médico en estado `APROBADO`, y solo un usuario con rol de enfermería puede aprobarlo (en el Momento 2, el usuario de demostración de enfermería elegido, D26). La transición se valida en código; no basta con que alguien "mire la pantalla". Si se decide que el médico vea borradores, se muestran marcados "BORRADOR — sin aprobación de enfermería".
- **La IA propone, la persona confirma.** Al registrar una entrada, ningún evento estructurado se guarda sin confirmación de una persona, y el modelo no agrega lo que el texto no dice (RN-11). El texto original se guarda tal como se escribió y es la fuente citable; el «texto corregido» es una versión aparte (RN-07, RF-42, D27).
- **Sin diagnóstico.** El informe describe evolución documentada: qué cambió, qué reaparece, qué falta registrar. No diagnostica ni recomienda tratamiento o dosis.
- **Honestidad agéntica.** Si faltan registros, el informe lo dice ("sin información suficiente para la semana X"). Nunca rellena ni inventa.
- **Fuente y criterio visibles.** Cada afirmación del informe se rastrea al registro original (fecha + texto) y se muestra el criterio de comparación. Dos niveles: lenguaje claro primero, evidencia (registros citados, llamadas a herramientas) a un clic. Enfermería puede corregir antes de aprobar.
- **Datos de pacientes.** Solo datos sintéticos en el repo, `eval/`, trazas, prompts de ejemplo y capturas. Al proveedor del LLM sale lo mínimo: sin nombre ni documento (usar id interno). Lo que sale a terceros se declara en `docs/gobernanza.md`. El repositorio es público: las credenciales de los usuarios de demostración (desde el Momento 3) son sintéticas y públicas por diseño, y solo existen con la semilla activa (`APP_SEED_DEMO=true`), nunca en el perfil `prod`.

## El agente
- **Cuatro límites obligatorios** (sin ellos no hay entrega): máximo de iteraciones, presupuesto de tokens por solicitud, confirmación humana antes de escrituras o acciones irreversibles, y timeout por herramienta. Valores y justificación en `docs/decisiones/`.
- **Herramientas:** atómicas, pocas (óptimo hasta 7) y con mínimo privilegio: lectura por defecto; la única escritura del agente es guardar el borrador en `PENDIENTE_REVISION`. La descripción de cada herramienta es un prompt: cuándo usarla, cuándo NO y qué prerrequisitos tiene. Se itera y se mide.
- **Un error de herramienta no mata el bucle:** se devuelve como observación al modelo para que corrija.
- **Capa de proveedor desacoplada:** ningún SDK de proveedor fuera de esa capa; cambiar de proveedor es cambiar configuración. Proveedor y modelo se declaran en el README con su justificación (latencia, costo por token, tool calling, ventana de contexto). La IA real es la única ruta; el plan B ante un fallo es el proveedor de respaldo. No hay modo simulado (D16, descartada).
- **Salida validada:** el modelo devuelve JSON contra un esquema. Si no valida, un reintento devolviéndole el error; si vuelve a fallar, degradar a texto plano y registrar. Nunca confiar en la forma.
- **Fallos sin stack trace al usuario:** 429 o caída del proveedor (backoff 1 s → 2 s → 4 s, proveedor de respaldo, mensaje honesto), timeout y salida que no valida. Los tres se deben poder forzar en la demo.
- **Latencia:** el in-request síncrono bloqueante está prohibido en la sustentación. Usar streaming real o estados honestos ("Consultando registros…"), o cola si el informe es pesado. Nada de efecto máquina de escribir sobre un buffer bloqueado. Decidido (D24): la ejecución corre en segundo plano y la pantalla recibe eventos de progreso reales por SSE; ver SPEC §7.
- **Seguridad (tríada letal):** el sistema tiene datos privados (registros) y contenido no confiable (el texto libre de las notas puede traer instrucciones: inyección indirecta). La tercera pata se mantiene cerrada: el agente no tiene herramientas de salida (internet, correo, mensajes). Tratar el texto de los registros como dato, no como instrucción ayuda, pero la defensa es de arquitectura. Usuario de BD con permisos mínimos.

## Medición
- `eval/`: 5 a 10 casos dorados con respuesta verificada a mano contra los datos, al menos uno de rechazo (p. ej. pedir un diagnóstico), más casos de estructuración de la entrada (síntomas, intensidad, veces, negaciones, sin síntomas) y el script que los corre.
- Cuatro cifras en `docs/decisiones/semana10.md`, con fecha y método: tasa de éxito, costo por tarea, **mediana** de latencia (no promedio) y recuperación si aplica. Compararlas contra la solución convencional.
- Corre la suite cada vez que cambie el modelo, el prompt del sistema, la descripción de una herramienta o el proveedor. Las cifras finales del Momento 3 se comparan contra esta línea base.
- Trazas visibles (herramienta pedida, parámetros, resultado, ms, tokens) en la app o en la salida. Los fallos se explican con la traza; no se esconden.

## Estructura de entrega
```text
README.md             # contrato de arranque, ejecutable por una IA que solo lo lea: versiones exactas, BD e imagen, usuarios de demostración elegibles (Momento 2) o con credenciales (Momento 3), proveedor y modelo con justificación
docker-compose.yml    # runtime, BD y servicios
run.sh                # arranque en un comando
.env.example          # variables exactas, sin llaves reales
docs/SPEC.md          # spec (7 secciones)
docs/PLAN.md          # plan derivado de la spec
docs/DICCIONARIO_DATOS.md  # diccionario de datos: tablas, columnas, tipos y reglas de integridad; base de las migraciones
docs/DICCIONARIO_DATOS_M2.md  # el mismo recortado al Momento 2, con los puntos abiertos cerrados por propuesta; base de V3 y V4
docs/decisiones.md    # decisiones técnicas con su razón y la alternativa descartada; cifras de línea base
docs/decisiones/      # detalle: semana06.md (autonomía), semana08.md (datos), semana10.md (línea base)
docs/seguridad.md     # modelo de amenazas, tríada, ataque propio y hardening (M3)
docs/gobernanza.md    # ficha de 5 preguntas (M3)
docs/respuesta_auditoria.md   # respuesta a la auditoría cruzada (M3)
eval/                 # casos dorados + script
skills/               # los cuatro skills del curso: especifica-encargo, planea-trabajo, ejecuta-plan, valida-resultado
```
- El repositorio no versiona `node_modules/`, `.venv/`, `target/` ni `bin/`; usa rutas relativas y trae el código fuente completo. No se entrega ZIP: se envía el enlace.
- `.env.example` lista las variables exactas (la del proveedor elegido, p. ej. `GEMINI_API_KEY` o `GROQ_API_KEY`, y `DATABASE_URL`). Una variable no documentada es falla crítica. El repo es público: nunca una llave real, ni en el historial.
- Los nombres que exige el curso mandan sobre la convención anterior de docs (`docs/0X-*`).
- **Calendario:** enlace del repositorio en el aula virtual hasta el jueves 29 de octubre de 2026 a medianoche; Momento 2 el sábado 31 de octubre (demo en vivo de 6 min + 3 min de preguntas, orden sorteado; sin presentación no hay nota). Momento 3 en la semana 14 (aplicación completa, con autenticación).
- **Anulan la entrega:** app que no corre o entorno roto, incluido un README que una IA no puede ejecutar de principio a fin (el profesor no revisa lo demás); agente sin límites; sin trazas ni cifras; no poder justificar las decisiones técnicas ni explicar el propio código.

## Convenciones
- Commits: Conventional Commits `<tipo>[ámbito]: <descripción en minúsculas, imperativo>` (ver `docs/06-contribution/git-commit-prefixes.md`).
- `main` protegida: solo PRs, con los checks `CI`, `Security` y `Qodana`. Trabaja en ramas.
- Workflows: acciones ancladas a commit SHA, con `# vX` de referencia.
- Español en la UI, los mensajes al usuario y los docs.

## Qué NO hacer
- No hardcodear credenciales ni llaves de proveedor; `.env` y `*.env` están gitignored (solo `!*.env.example`). Tampoco en el historial.
- No commitear `backend/target/`, `frontend/dist|coverage|node_modules/`, `.idea/`, `uploads/`.
- No usar datos reales de pacientes en ningún archivo.
- No montar RAG o base vectorial sin justificarlo frente a una consulta directa a la BD.
- No reportar "funciona bien" sin cifras ni traza.
- No commitear llaves ni secretos reales: el repositorio es público. Si una llave se filtra, se revoca; borrarla del historial no basta.
- No bloquear `run.sh` esperando entrada si no hay terminal.
- No dejar decisiones técnicas sin justificar en `docs/decisiones.md`.

## PENDIENTE (preguntar al dueño)
- **Justificación de D9 (stack con Java 17):** escrita en `docs/decisiones.md` (E15, con la tabla de librerías). Falta que el equipo la lea: el profesor la va a preguntar.
- **Proveedor y modelo del LLM (D15):** decidido el 28-oct. Groq es el principal (`openai/gpt-oss-120b`) y Gemini el de respaldo (`gemini-3.5-flash-lite`); la capa y su justificación están en `docs/decisiones.md` (E8). Falta confirmar los límites gratuitos de Gemini, que Google no publica.
- **Estrategia de datos (D8):** decidida y escrita en `docs/decisiones/semana08.md` (consulta directa a la BD, sin RAG, con el tamaño del corpus medido y el criterio para volver a evaluarlo). No se midió una versión con RAG.
- **Visibilidad al médico (D6):** el supuesto es que solo ve informes aprobados (el deck lo dice; la descripción inicial decía que también podría revisarlos directamente). Confirmar; si ve borradores, se marcan (ver reglas del producto).
- **Patrón de integración (D24):** decidido: asíncrono, con eventos de progreso por SSE (SPEC §7). Su entrada está en `docs/decisiones.md` (E11): el profesor preguntará «¿síncrono o asíncrono, y por qué?».
- **Arranque en frío (D22 y D23):** hecho (E1, E3, E15): `docker-compose.yml`, `run.sh`, Dockerfiles, semilla sin llamar al LLM, usuarios de demostración y README ejecutable. Falta la prueba en frío con una persona ajena (`docs/prueba_companero.md`).
- **Conjunto sintético (D11):** hecho: 5 pacientes, 18 semanas-paciente (5, 4, 3, 4 y 2), 119 entradas con eventos confirmados, fechas fijas que terminan el domingo 25-oct-2026 y 5 informes sembrados (4 aprobados de P-014 y 1 pendiente de P-021). Los casos dorados de `eval/` salen de ahí. Los informes sembrados los escribe una plantilla, no el agente (E14).
- **Estructuración de la entrada (D27):** decidida el 28-oct. Intensidad: leve, moderado, intenso y «sin indicar»; nueve síntomas sugeridos (D18) y cualquier otro se acepta, con la frase de la nota que lo respalda (E18); botón «Aprobar y guardar» (como el mockup); «Descartar» cierra la vista previa sin guardar; el «texto corregido» queda fuera. Su entrada en `docs/decisiones.md` explica por qué esta llamada corta va en la misma petición y la del agente es asíncrona (D24).
- **Informe mensual en el Momento 2 (D4):** decidido por el equipo. Un mes son cuatro semanas completas consecutivas y el mensual exige el informe semanal aprobado de cada una (RN-10, RF-36); cuatro semanas y no mes calendario es propuesta por confirmar. **Implementado en E13** (decisiones en `docs/decisiones.md`, medición en `docs/decisiones/semana10.md`); la propuesta de «cuatro semanas y no mes calendario» sigue por confirmar.
- **Autenticación en el Momento 3 (D23):** decidido por el equipo. En el Momento 2 la app abre sin inicio de sesión y se elige con qué usuario de demostración sembrado se trabaja (D26, decidida el 28-oct): así solo Enfermería aprueba, el médico solo ve lo aprobado y cada acción, incluida la del médico que registra, tiene un autor (D12). No es un control de seguridad.
- **Llave del API para el profesor (parte pendiente de D22):** decidido el 29-oct: el evaluador usa su propia llave gratuita de Groq o el equipo le envía por un medio privado (correo o mensaje) una llave temporal creada solo para la calificación y revocable; el README lo dice. Falta que el equipo cree esa llave y la envíe; nunca va en el repo ni en el historial. Sin llave la app arranca con la semilla y avisa.
- **Entrega:** repositorio público; el enlace va al aula virtual antes del jueves 29 a medianoche y falta crear el tag `m2-entrega` (D21).
- **Archivo de decisiones:** resuelto. Es `docs/decisiones.md` (el profesor aceptó «otro documento» o el README), con el detalle en `docs/decisiones/`.
- **Evidencia de la aprobación de la necesidad de IA (D17):** guardar fecha y medio (clase o correo) en `docs/decisiones/`.
- **Diccionario de datos:** `docs/DICCIONARIO_DATOS.md` v0.1 resuelve el idioma de los identificadores (inglés en `snake_case`, con etiqueta en español) y propone las 16 tablas, sus columnas y los permisos del dominio. Quedan 13 puntos por confirmar (§8). `docs/DICCIONARIO_DATOS_M2.md` v0.1 es el recorte al Momento 2 y los cierra con una propuesta marcada: el evento toma la fecha de su entrada, los síntomas son una lista cerrada de nueve (la del mockup), la traza se lee desde el informe, `INCOMPLETO` es un estado final y `app_users.password_hash` admite `NULL` (hoy es `NOT NULL` en `V1`). Es el que se usa para escribir `V3` y `V4`. Falta que el equipo lo confirme, sobre todo el catálogo de síntomas (D18, con una persona de enfermería) y la matriz de trazas de la SPEC §4; si cambia algo que esté en los dos diccionarios, se actualizan los dos.

---
Reglas del curso verificadas contra ingenieria-aplicaciones-ia.netlify.app el 21-oct-2026 (rúbricas de los Momentos 1 a 3; slides de las semanas 2, 3, 4, 7, 8, 9, 10, 12 y 13). Si el sitio cambia, este archivo envejece. Entrega, plazo y prueba del profesor: transcripción de la clase del sábado 24-oct-2026.
