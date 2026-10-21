# AGENTS.md — Patient Progress Auditor

## Qué es esto
- Auditor de la evolución de pacientes: enfermería registra cada día, en texto libre, cómo está un paciente. Un agente de IA analiza los registros de cada semana, los compara con semanas anteriores y redacta un informe semanal o mensual. Enfermería lo revisa y aprueba antes de que llegue al médico.
- Es apoyo al seguimiento documental: **no diagnostica, no receta, no sustituye el criterio profesional**.
- Proyecto del curso *Ingeniería de Aplicaciones con IA* (UCC, Ingeniería de Sistemas). Las reglas del curso mandan sobre cualquier otra convención de este archivo.
- Reemplaza al proyecto anterior (AgroCortex). Nada de AgroCortex es contexto válido; lo que quede en el repo con ese nombre es residuo (ver PENDIENTE).

## Dónde va el LLM y dónde no
Si se quita el modelo, los registros siguen existiendo pero el informe no. La frontera es esta:
- **Código determinista:** guardar y consultar registros, ordenar por fecha, calcular métricas (conteos, frecuencias, días con síntoma), permisos, validaciones y estados del informe. Los números los calcula el código, nunca el LLM.
- **LLM:** interpretar texto libre ("vomitó el lunes, ánimo alto") y redactar la comparación entre semanas.
- **Humano:** enfermería contrasta las afirmaciones con los registros originales y aprueba.
- No poner el LLM en lo que el código resuelve exacto. La utilidad del LLM y del agente se mide contra una solución convencional (sin modelo).

## Estado actual
Scaffold heredado de AgroCortex, sin lógica de dominio de pacientes:
- `backend/`: Spring Boot 3.4.1, Java 17, Maven, PostgreSQL, Flyway, Spring Security + JWT (migraciones RBAC `V1`/`V2`). Solo existe la clase `*Application` y su test; sin controllers ni entidades.
- `frontend/`: Angular 19, Node 20, sin rutas ni servicios.
- `.github/workflows/`: CI, Security, Qodana y Deploy (Heroku + Vercel).
- **No existen todavía:** agente, herramientas, capa de proveedor, `eval/`, `docker-compose.yml`, `run.sh`, `docs/SPEC.md`, `docs/PLAN.md`, `skills/`.

## Cómo se corre y se prueba
Hoy (heredado del scaffold):
- Backend: `cd backend && mvn spring-boot:run`. Perfil `dev`; Postgres en `localhost:5432`, DB/user/pass `agrocortex` (según `application-dev.yml`). Exige `JWT_SECRET` (sin valor por defecto). Swagger en `/swagger-ui.html`. No hay `mvnw`: usa el `mvn` del sistema.
- Tests backend: `cd backend && mvn -B verify`.
- Frontend: `cd frontend && npm start` (URL del API por `API_URL`).
- Tests frontend: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage`.
- `npm run lint` está roto (falta `@angular-eslint`): no lo uses como verificación.

Objetivo de entrega (por crear): `./run.sh` o `docker compose up --build` levanta todo con un comando. Un compañero con el ZIP y el README debe tenerlo corriendo en menos de 5 minutos. Cuando existan, actualiza esta sección: es la única que lleva los comandos exactos.

## Cómo trabajar (método del curso)
- **Loop, en orden:** explorar (sin escribir código) → planear (por escrito, lo revisa un humano) → ejecutar (una tarea del plan a la vez) → verificar contra los criterios de aceptación. Nunca se ejecuta sin plan. Nunca se hace commit sin verificar. Si no puedes verificar, dilo.
- **Dudas vs decisiones:** si algo de producto no está decidido en `docs/SPEC.md`, para y pregunta; no lo resuelvas en silencio. Al terminar cada tarea, lista las decisiones que tomaste y que nadie te pidió.
- **Spec primero:** `docs/SPEC.md` con siete secciones: resumen, objetivos medibles, fuera de alcance (nunca vacío), diseño, casos borde, criterios de aceptación (cada línea se responde con sí o no) y decisiones. Spec, plan (`docs/PLAN.md`) y tareas son documentos distintos; ninguna tarea de más de medio día.
- **Revisión con contexto fresco:** quien revisa no es quien escribió. Sesión nueva para revisar; una tarea, una sesión.
- **Skills:** `skills/` en la raíz con la cadena `escribir-spec` → `escribir-plan` → `ejecutar-plan`. Úsalos cuando la tarea coincida; si uno falla, reporta cuál y qué se corrigió.
- **Permisos.** Sin preguntar: leer, editar en rama, correr tests. Siempre preguntar: borrar, push, comandos destructivos, instalar dependencias, tocar `.env` o credenciales.
- **Código explicable:** se puede usar toda la IA, pero cada integrante responde por cada línea y debe poder explicar cualquier función que se le señale. No generes lo que el equipo no pueda explicar.

## Reglas del producto (no negociables)
- **Supervisión humana exigida por el sistema.** Un informe solo llega al médico en estado `APROBADO`, y solo un usuario con rol de enfermería puede aprobarlo. La transición se valida en código; no basta con que alguien "mire la pantalla". Si se decide que el médico vea borradores, se muestran marcados "BORRADOR — sin aprobación de enfermería".
- **Sin diagnóstico.** El informe describe evolución documentada: qué cambió, qué reaparece, qué falta registrar. No diagnostica ni recomienda tratamiento o dosis.
- **Honestidad agéntica.** Si faltan registros, el informe lo dice ("sin información suficiente para la semana X"). Nunca rellena ni inventa.
- **Fuente y criterio visibles.** Cada afirmación del informe se rastrea al registro original (fecha + texto) y se muestra el criterio de comparación. Dos niveles: lenguaje claro primero, evidencia (registros citados, llamadas a herramientas) a un clic. Enfermería puede corregir antes de aprobar.
- **Datos de pacientes.** Solo datos sintéticos en el repo, `eval/`, trazas, prompts de ejemplo y capturas. Al proveedor del LLM sale lo mínimo: sin nombre ni documento (usar id interno). Lo que sale a terceros se declara en `docs/gobernanza.md`.

## El agente
- **Cuatro límites obligatorios** (sin ellos no hay entrega): máximo de iteraciones, presupuesto de tokens por solicitud, confirmación humana antes de escrituras o acciones irreversibles, y timeout por herramienta. Valores y justificación en `docs/decisiones/`.
- **Herramientas:** atómicas, pocas (óptimo hasta 7) y con mínimo privilegio: lectura por defecto; la única escritura del agente es guardar el borrador en `PENDIENTE_REVISION`. La descripción de cada herramienta es un prompt: cuándo usarla, cuándo NO y qué prerrequisitos tiene. Se itera y se mide.
- **Un error de herramienta no mata el bucle:** se devuelve como observación al modelo para que corrija.
- **Capa de proveedor desacoplada:** ningún SDK de proveedor fuera de esa capa; cambiar de proveedor es cambiar configuración. Proveedor y modelo se declaran en el README con su justificación (latencia, costo por token, tool calling, ventana de contexto).
- **Salida validada:** el modelo devuelve JSON contra un esquema. Si no valida, un reintento devolviéndole el error; si vuelve a fallar, degradar a texto plano y registrar. Nunca confiar en la forma.
- **Fallos sin stack trace al usuario:** 429 o caída del proveedor (backoff 1 s → 2 s → 4 s, proveedor de respaldo, mensaje honesto), timeout y salida que no valida. Los tres se deben poder forzar en la demo.
- **Latencia:** el in-request síncrono bloqueante está prohibido en la sustentación. Usar streaming real o estados honestos ("Consultando registros…"), o cola si el informe es pesado. Nada de efecto máquina de escribir sobre un buffer bloqueado.
- **Seguridad (tríada letal):** el sistema tiene datos privados (registros) y contenido no confiable (el texto libre de las notas puede traer instrucciones: inyección indirecta). La tercera pata se mantiene cerrada: el agente no tiene herramientas de salida (internet, correo, mensajes). Tratar el texto de los registros como dato, no como instrucción ayuda, pero la defensa es de arquitectura. Usuario de BD con permisos mínimos.

## Medición
- `eval/`: 5 a 10 casos dorados con respuesta verificada a mano contra los datos, al menos uno de rechazo (p. ej. pedir un diagnóstico), más el script que los corre.
- Cuatro cifras en `docs/decisiones/semana10.md`, con fecha y método: tasa de éxito, costo por tarea, **mediana** de latencia (no promedio) y recuperación si aplica. Compararlas contra la solución convencional.
- Corre la suite cada vez que cambie el modelo, el prompt del sistema, la descripción de una herramienta o el proveedor. Las cifras finales del Momento 3 se comparan contra esta línea base.
- Trazas visibles (herramienta pedida, parámetros, resultado, ms, tokens) en la app o en la salida. Los fallos se explican con la traza; no se esconden.

## Estructura de entrega
```text
README.md             # contrato de arranque: versiones exactas, proveedor y modelo con justificación
docker-compose.yml    # runtime, BD y servicios
run.sh                # arranque en un comando
.env.example          # variables exactas, sin llaves reales
docs/SPEC.md          # spec (7 secciones)
docs/PLAN.md          # plan derivado de la spec
docs/decisiones/      # semana08.md (datos), semana10.md (línea base)
docs/seguridad.md     # modelo de amenazas, tríada, ataque propio y hardening (M3)
docs/gobernanza.md    # ficha de 5 preguntas (M3)
docs/respuesta_auditoria.md   # respuesta a la auditoría cruzada (M3)
eval/                 # casos dorados + script
skills/               # escribir-spec, escribir-plan, ejecutar-plan
```
- El ZIP no lleva `node_modules/`, `.venv/`, `target/`, `bin/` ni `.git/`; usa rutas relativas y trae el código fuente completo.
- `.env.example` lista las variables exactas (la del proveedor elegido, p. ej. `GEMINI_API_KEY` o `GROQ_API_KEY`, y `DATABASE_URL`). Una variable no documentada es falla crítica.
- Los nombres que exige el curso mandan sobre la convención anterior de docs (`docs/0X-*`).
- **Calendario:** Momento 2 el sábado 31 de octubre de 2026 (demo en vivo de 6 min + 3 min de preguntas, orden sorteado; sin presentación no hay nota). Momento 3 en la semana 14.
- **Anulan la entrega:** app que no corre o entorno roto; agente sin límites; sin trazas ni cifras; no saber explicar el propio código.

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

## PENDIENTE (preguntar al dueño)
- **Stack:** ¿se mantiene Spring Boot 3.4 / Java 17 + Angular 19? La lámina de entrega del curso pide runtime con versiones exactas (cita Java 21 LTS) y `docker-compose.yml` + `run.sh`, que hoy no existen. Definir también dónde vive el agente.
- **Proveedor y modelo del LLM** y su capa de abstracción: aún no hay ninguno.
- **Estrategia de datos:** consulta directa a la BD o RAG (`docs/decisiones/semana08.md`). Por defecto, consulta directa; RAG solo si se justifica y se mide.
- **Visibilidad al médico:** el deck dice que solo ve informes aprobados; la descripción inicial dice que también puede revisarlos directamente. Decidir si ve borradores (ver reglas del producto).
- **Patrón de integración** para generar informes: streaming SSE o cola con workers.
- **Residuos de AgroCortex por limpiar:** paquete `com.agrocortex`, artifact `agrocortex-api`, DB `agrocortex` (`.env.example`, CI, `application-dev.yml`), `README.md`, `docs/01`–`docs/06` (análisis agrícola, modelo de datos MVP, etc.) y el deploy a Heroku/Vercel, que el curso no exige (entregable: ZIP + docker compose).
- **Nombre del archivo de decisiones:** la lámina de entrega dice `docs/decisiones.md`; las rúbricas dicen `docs/decisiones/semana08.md` y `semana10.md`. Confirmar con el profesor antes del 31 de octubre.
- **Idioma de los identificadores del dominio** (Paciente/Patient, Registro/Entry, Informe/Report) y nombre canónico de cada entidad.

---
Reglas del curso verificadas contra ingenieria-aplicaciones-ia.netlify.app el 21-oct-2026 (rúbricas de los Momentos 1 a 3; slides de las semanas 2, 3, 4, 7, 8, 9, 10, 12 y 13). Si el sitio cambia, este archivo envejece.