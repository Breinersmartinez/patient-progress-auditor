# Documentación — Patient Progress Auditor

Portal de documentación técnica del proyecto. Cada documento describe un problema, una decisión o un procedimiento, y referencia a sus dependientes.

## Índice

### Especificación y plan (curso)

| Documento | Qué es | Estado |
|---|---|---|
| [SPEC.md](SPEC.md) | Especificación del incremento: resumen, objetivos medibles, fuera de alcance, diseño, casos borde, criterios de aceptación y decisiones | `PENDIENTE` |
| [PLAN.md](PLAN.md) | Plan derivado de la spec, con tareas verificables | `PENDIENTE` |
| [decisiones/](decisiones/) | Decisiones con fecha y método (semana08: datos; semana10: línea base de medición) | Esqueleto vacío |
| [seguridad.md](seguridad.md) | Modelo de amenazas, tríada, ataque propio y hardening (M3) | Pendiente de crear |
| [gobernanza.md](gobernanza.md) | Ficha de 5 preguntas sobre datos que salen a terceros (M3) | Pendiente de crear |
| [respuesta_auditoria.md](respuesta_auditoria.md) | Respuesta a la auditoría cruzada (M3) | Pendiente de crear |

### 03 — Engineering

| Documento | Qué es | Relacionado con |
|---|---|---|
| [github-actions-workflows.md](03-engineering/github-actions-workflows.md) | Cómo se construyeron los workflows de GitHub Actions: decisiones, alternativas descartadas, verificaciones | CI/CD (04) |

### 04 — Operations

| Documento | Qué es | Relacionado con |
|---|---|---|
| [ci-cd.md](04-operations/ci-cd.md) | Pipeline automatizado CI/CD: workflows, secretos, environments, branch protection | Workflows (03) · Deployment (04) |
| [deployment.md](04-operations/deployment.md) | Despliegue manual: Heroku (`heroku.yml` + git push) y Vercel, con troubleshooting | CI/CD (04) |

### 05 — Security

| Documento | Qué es | Relacionado con |
|---|---|---|
| [README.md](05-security/README.md) | Postura de seguridad: qué está automatizado, qué hay que configurar | CI/CD (04) · Workflows (03) |

### 06 — Contribution

| Documento | Qué es | Relacionado con |
|---|---|---|
| [git-commit-prefixes.md](06-contribution/git-commit-prefixes.md) | Convención de mensajes de commit (Conventional Commits) del monorepo | AGENTS.md |

## Convenciones del portal

- **Nombres de archivo**: inglés, `kebab-case`, una palabra de dominio por documento.
- **Prefijo numérico** por categoría: establece el orden de lectura y evita que GitHub ordene alfabéticamente rompiendo la cronología conceptual.
- **Cada documento referencia a sus dependientes** (tabla "Relacionado con") y no duplica contenido que ya vive en otro.
- **Los nombres que exige el curso mandan** sobre la convención anterior: `docs/SPEC.md`, `docs/PLAN.md` y `docs/decisiones/` son los nombres oficiales (ver la sección "Estructura de entrega" de `AGENTS.md`).
