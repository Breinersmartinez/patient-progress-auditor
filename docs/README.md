# Documentación — Patient Progress Auditor

Portal de documentación técnica del proyecto. Cada documento describe un problema, una decisión o un procedimiento, y referencia a sus dependientes.

## Índice

### Especificación, plan y decisiones (curso)

| Documento | Qué es | Estado |
|---|---|---|
| [SPEC.md](SPEC.md) | Especificación del incremento: resumen, objetivos medibles, fuera de alcance, diseño, casos borde, criterios de aceptación y decisiones (D1 a D27) | Vigente |
| [REQUISITOS.md](REQUISITOS.md) | RF, RNF, RN y CU con su hito y su criterio de aceptación | Vigente |
| [DICCIONARIO_DATOS.md](DICCIONARIO_DATOS.md) · [DICCIONARIO_DATOS_M2.md](DICCIONARIO_DATOS_M2.md) | Tablas, columnas, tipos y reglas de integridad (completo y recortado al Momento 2) | Vigente |
| [PLAN.md](PLAN.md) | Plan maestro y el encargo, plan y cierre de cada etapa (E1 a E15) | Vigente |
| [decisiones.md](decisiones.md) | Cada decisión técnica con su razón y la alternativa descartada; tabla de librerías | Vigente |
| [decisiones/semana06.md](decisiones/semana06.md) | Autonomía del agente: límites, herramientas y descripciones | Vigente |
| [decisiones/semana08.md](decisiones/semana08.md) | Datos: consulta directa a la base, sin RAG, con lo medido | Vigente |
| [decisiones/semana10.md](decisiones/semana10.md) | Línea base de medición: las cuatro cifras contra la solución convencional | Vigente |
| [prueba_companero.md](prueba_companero.md) | Plantilla de la prueba en frío con una persona ajena al código | Por llenar antes del tag |
| seguridad.md · gobernanza.md · respuesta_auditoria.md | Modelo de amenazas, ficha de gobernanza y respuesta a la auditoría cruzada | Momento 3 (por crear) |

### 03 — Engineering

| Documento | Qué es | Relacionado con |
|---|---|---|
| [github-actions-workflows.md](03-engineering/github-actions-workflows.md) | Cómo se construyeron los workflows de GitHub Actions: decisiones, alternativas descartadas, verificaciones | CI (04) |

### 04 — Operations

| Documento | Qué es | Relacionado con |
|---|---|---|
| [ci-cd.md](04-operations/ci-cd.md) | Pipeline de integración continua: workflows y protección de rama | Workflows (03) |

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
