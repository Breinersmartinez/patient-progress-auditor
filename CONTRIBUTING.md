# Cómo contribuir — Patient Progress Auditor

Dos personas trabajan en este repositorio, cada una con su propia forma de trabajo y sus propias herramientas de IA. Este documento fija lo que es común y lo que es de cada quien. Las reglas del producto y del curso están en [AGENTS.md](AGENTS.md), y esa es la fuente de verdad: aquí no se repiten.

## Antes de empezar

1. [README.md](README.md): cómo se levanta todo (`./run.sh`) y cómo se recorre la aplicación.
2. [AGENTS.md](AGENTS.md): qué es el producto, las reglas no negociables, cómo se prueba y la entrega.
3. [docs/SPEC.md](docs/SPEC.md) y [docs/REQUISITOS.md](docs/REQUISITOS.md): qué se construye y con qué criterio de aceptación.

## Ramas y pull requests

- `main` está protegida: solo entra por pull request, con los checks `CI`, `Security` y `Qodana` en verde.
- Cada integrante trabaja en ramas propias (por ejemplo `feat/…`, `fix/…`, `chore/…` o `develop_<iniciales>`).
- El pull request se abre con la plantilla de [.github/pull_request_template.md](.github/pull_request_template.md).
- Quien revisa no es quien escribió el cambio.

## Commits

- Conventional Commits, en minúsculas e imperativo: `<tipo>[ámbito]: <descripción>`. La guía está en [docs/06-contribution/git-commit-prefixes.md](docs/06-contribution/git-commit-prefixes.md).
- No se hace commit sin haber verificado. Si algo no se pudo verificar, se dice en el pull request.

## Cómo se prueba

| Qué | Comando |
|---|---|
| Backend (necesita un PostgreSQL desechable: las pruebas lo modifican) | `cd backend && mvn -B verify` con las variables `SPRING_DATASOURCE_*` de [AGENTS.md](AGENTS.md) |
| Frontend | `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless` |
| Variables de entorno documentadas | `scripts/check-env-example.sh` |
| Evaluación del agente (gasta cuota de la llave) | `python3 eval/run_eval.py`, en una base desechable |

`npm run lint` está roto y no cuenta como verificación.

## Reglas que no se negocian

- **El repositorio es público.** Ninguna llave, ningún `.env` y ningún dato real de pacientes, ni en los archivos ni en el historial. Solo datos sintéticos.
- **Nada de decisiones en silencio.** Lo que el profesor preguntará (proveedor, RAG, síncrono o asíncrono, stack) se escribe en [docs/decisiones.md](docs/decisiones.md), con su razón y la alternativa descartada. Al terminar una tarea se listan las decisiones que nadie pidió.
- **Documentos coherentes.** Cuando cambia una decisión se actualiza en este orden: `docs/SPEC.md` §7, `docs/REQUISITOS.md`, los dos diccionarios de datos si cambian los datos, `AGENTS.md` y el resumen ejecutivo. No se renumeran RF, RNF, RN, OBJ ni D, porque se citan entre documentos.
- **Mockups.** El diseño de la interfaz es el de los mockups del equipo y no se modifica sin que lo pidan.
- **Idioma.** Documentos, interfaz y mensajes al usuario en español.

## Herramientas de IA: qué se comparte y qué es de cada quien

| Se versiona (es del equipo) | Para qué |
|---|---|
| `AGENTS.md` | Reglas que valen para cualquier herramienta |
| `skills/` | Los cuatro skills del curso: `especifica-encargo`, `planea-trabajo`, `ejecuta-plan`, `valida-resultado`. La entrega los exige en la raíz |
| `CLAUDE.md` y `.claude/` | Configuración de Claude Code del equipo (incluye la copia de los skills que Claude Code carga) |

| No se versiona (es de cada integrante, está en `.gitignore`) | Ejemplos |
|---|---|
| Configuración de otras herramientas | `.opencode/`, `.codex/`, `.cursor/`, `.windsurf/`, `.aider*`, `.continue/`, `.gemini/`, `.amazonq/`, `.roo/`, `opencode.json`, `GEMINI.md` |
| Prompts de trabajo personales | `docs/prompts/` |

- **Instalar los skills en su herramienta:** copie `skills/*` a la carpeta de skills que su herramienta lea, por ejemplo `mkdir -p .opencode/skills && cp -R skills/* .opencode/skills/`.
- **Si su herramienta crea una carpeta de configuración nueva,** agréguela al bloque «Harness de IA» de [.gitignore](.gitignore) en el mismo pull request, para que no llegue al repositorio.
- **Las llaves de los proveedores** van en el entorno o en `.env`, nunca en un archivo de configuración de la herramienta.

## Entrega

`./run.sh` debe seguir levantando todo con solo Docker Compose v2, y el README debe seguir siendo ejecutable por una IA que no tenga más contexto que ese archivo. La puerta de aceptación es un clon limpio (SPEC §6).
