# CLAUDE.md — Patient Progress Auditor

@AGENTS.md

`AGENTS.md` es la fuente de verdad del producto, las reglas, la entrega y los comandos: se importa arriba y no se repite aquí. Este archivo solo agrega lo propio de Claude Code. Si algo contradice a `AGENTS.md`, manda `AGENTS.md`; si contradice a `docs/SPEC.md` o `docs/REQUISITOS.md` en el alcance del producto, manda la SPEC y, después, los requisitos.

## Antes de tocar algo
Lee, en este orden, solo lo que la tarea necesita:
1. `AGENTS.md` — qué es el producto, reglas no negociables, entrega y prueba del profesor.
2. `docs/SPEC.md` — objetivos (OBJ), alcance (§3), diseño (§4), puerta de aceptación (§6) y decisiones D1 a D27 con su estado (§7).
3. `docs/REQUISITOS.md` — RF, RNF, RN y CU con su hito (M2-N, M2-S, M3, Opc) y su criterio de aceptación.
4. `docs/DICCIONARIO_DATOS.md` — tablas, columnas, tipos, catálogos y reglas de integridad; base de las migraciones Flyway. Identificadores en inglés y `snake_case`.
5. `docs/DICCIONARIO_DATOS_M2.md` — lo mismo recortado al Momento 2, con los puntos abiertos cerrados por propuesta; es el que se usa para escribir `V3` y `V4`. Si choca con el completo, avisa.
6. `docs/PLAN.md` — el plan de trabajo. Cubre el Momento 2 (E1 a E19); el del Momento 3 está en construcción en `docs/PLAN_M3.md` (hoy solo con la pieza de Langfuse, aprobada); lo que no esté en un plan aprobado no se ejecuta.
7. `CONTRIBUTING.md` — ramas, commits, pull requests y qué del harness de IA se versiona.

## Qué se construye ahora
- **Momento 3 (semana 14, aplicación completa, con autenticación):** autenticación y JWT (RF-31), credenciales y administrador (RF-33), tablero de permisos (RF-34), seguridad (`docs/seguridad.md`), gobernanza (`docs/gobernanza.md`) y respuesta a la auditoría cruzada (`docs/respuesta_auditoria.md`). **`docs/PLAN.md` solo cubre el Momento 2:** antes de escribir código, especifica y planea con los skills y espera la aprobación.
- **Momento 2 (hecho; las fechas del enlace, 29-oct, y de la demo, 31-oct, ya pasaron):**
  - Núcleo (M2-N): RF-01, RF-02, RF-04 a RF-26, RF-28, RF-32, RF-36, RF-40, RF-41 y RF-39 en su parte del Momento 2, más los requisitos no funcionales (RNF-01 se cumple solo en parte hasta el Momento 3). Es el registro con estructuración por IA (D27), el agente, el informe semanal y el mensual, y la revisión de enfermería.
  - Opcionales (M2-S) que entraron: RF-27 (rechazar un informe), RF-37 (PDF con JasperReports, D13, desde el 29-oct por pedido del equipo) y RF-42 (texto corregido). Quedaron fuera RF-03 (corregir una entrada) y RF-35 (auditoría); la pantalla del médico (E12c) quedó fuera del Momento 2 y se construyó en el Momento 3 (Pieza 4 de `docs/PLAN_M3.md`, 2026-11-07).
  - Sin inicio de sesión: la app abre con datos y usuarios de demostración, sin credenciales, y se elige con cuál trabajar (D26). Eso cambia con el Momento 3.
- **Opcionales:** voz (RF-38, D14) y Langfuse (RF-43, D28): trazas y costo por consulta en Langfuse Cloud, solo metadatos, con llaves opcionales. El plan aprobado y el avance por pasos están en `docs/PLAN_M3.md`; se ejecuta un paso por vez con `ejecuta-plan`.
- No agregues funcionalidades que no estén en `docs/REQUISITOS.md`. Si algo del Momento 3 choca con lo ya construido, indícalo antes de asumir una solución.

## Cómo trabajar con Claude Code
- **Una tarea, una sesión.** Sigue el bucle del curso con los skills de `.claude/skills/`: `especifica-encargo` → `planea-trabajo` → `ejecuta-plan` → `valida-resultado`. La revisión la hace una sesión nueva, no quien escribió.
- **Contradicciones primero:** si dos documentos o dos decisiones chocan, indícalo antes de asumir una solución.
- **Decisiones silenciosas, no.** Lo que el profesor preguntará (proveedor, RAG, síncrono o asíncrono, motor de PDF) se deja escrito en `docs/decisiones.md`. Al terminar cada tarea, lista las decisiones que tomaste y que nadie te pidió.
- **Permisos:** sin preguntar, leer, editar en rama y correr tests. Preguntar siempre antes de borrar, hacer push, ejecutar comandos destructivos, instalar dependencias o tocar `.env` y credenciales. Commits solo si te lo piden y después de verificar.
- **Repositorio público:** ninguna llave ni dato real de pacientes, ni en archivos ni en el historial. Solo datos sintéticos.
- **Harness:** tu configuración de Claude Code (`CLAUDE.md` y `.claude/`) se versiona; la de otras herramientas (`.opencode/`, `.codex/`, `.cursor/`…) y `docs/prompts/` son de cada integrante y están en `.gitignore`. Los skills de `.claude/skills/` son copias de `skills/`, la fuente del curso: si falta alguno, cópialo de ahí (`cp -R skills/<nombre> .claude/skills/`) y no edites la copia a mano. Detalle en `CONTRIBUTING.md`.
- **Mockups:** el diseño de la interfaz es el de los mockups del equipo; no se modifican sin que lo pidan. Pueden mostrar la autenticación en el hito anterior a la decisión del 27-oct: en alcance mandan la SPEC y los requisitos; en interfaz, los mockups.

## Mantener los documentos coherentes
- Cuando cambia una decisión, actualiza en este orden: `docs/SPEC.md` §7, luego `docs/REQUISITOS.md` (hito del RF, §8, conteos), `docs/DICCIONARIO_DATOS.md` y `docs/DICCIONARIO_DATOS_M2.md` si cambian los datos, `AGENTS.md` y el resumen ejecutivo (`docs/entregables/RESUMEN_EJECUTIVO.md`). La copia de `AGENTS.md` en el Project de claude.ai se sincroniza aparte.
- No renumeres RF, RNF, RN, OBJ ni D: se citan entre documentos.
- Cada afirmación lleva su origen: **[Curso]**, **[Equipo]** o **[Propuesta]**. Lo que no está decidido se marca como propuesta o supuesto, no como hecho.
- Tablas de requisitos: ID, requisito, hito, traza y criterio que se responde con sí o no. Revisa que cada fila conserve el mismo número de columnas.
- Docs y UI en español; commits en Conventional Commits, en minúsculas e imperativo.
- Las reglas de este apartado también están resumidas en `CONTRIBUTING.md`, para quien no use Claude Code: si cambian aquí, actualízalo.

## Antes de dar algo por terminado
- Código: los comandos exactos están en "Cómo se corre y se prueba" de `AGENTS.md` (`mvn -B verify` en el backend y `ng test` en el frontend; `npm run lint` está roto y no cuenta).
- Documentos: sin referencias a IDs que no existen, sin tablas descuadradas y con los conteos por hito de `docs/REQUISITOS.md` al día.
- Entrega: la puerta de aceptación es un clon limpio y una IA que solo lea el README dejando la app corriendo (SPEC §6). Si no pudiste verificarlo, dilo.
