# Patient Progress Auditor

Auditor de la evolución de pacientes: enfermería registra cada día, en texto libre, cómo está un paciente; un agente de IA analiza los registros de la semana, los compara con las semanas anteriores y redacta un informe que enfermería revisa y aprueba antes de que llegue al médico.

Es apoyo al seguimiento documental: **no diagnostica, no receta, no sustituye el criterio profesional**.

## Estado actual

**Scaffold.** El repositorio tiene los cimientos, no la funcionalidad del producto. Hoy no existe el agente, ni las herramientas, ni la capa de proveedor de LLM, ni el modelo de datos de pacientes.

Lo que sí existe:

- `backend/`: Spring Boot con dependencias de Spring Security / Flyway / JWT y migraciones RBAC (`V1` esquema, `V2` semilla genérica de roles y permisos). **Sin controllers, sin entidades de dominio, sin agente.**
- `frontend/`: SPA Angular standalone. **Sin rutas, sin servicios, sin pantallas.**
- `.github/workflows/`: CI (backend + frontend), Security (CodeQL, Dependency Review, Gitleaks), Qodana y Deploy (Heroku + Vercel).
- `docs/`: especificación y plan pendientes de redactar; documentación vigente de CI/CD, despliegue, seguridad y convenciones de commit.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.4.1 · Java 17 · Maven · PostgreSQL · Flyway · Spring Security + JWT |
| Frontend | Angular 19.2 · TypeScript · Node 20 |
| CI/CD | GitHub Actions · Heroku (backend) · Vercel (frontend) |

El proveedor y el modelo de LLM **están por decidir**: se documentarán aquí con su justificación (latencia, costo por token, tool calling, ventana de contexto) cuando se cierren en `docs/SPEC.md`.

## Estructura

```text
backend/     API Spring Boot (Maven)   — paquete com.patientprogressauditor
frontend/    SPA Angular                — proyecto patient-progress-auditor-frontend
docs/        Documentación (índice en docs/README.md)
eval/        Casos dorados + script de evaluación (pendiente)
skills/      Cadena escribir-spec → escribir-plan → ejecutar-plan (pendiente)
.github/     Workflows de GitHub Actions
```

## Dónde consultar

| Tema | Fuente |
|---|---|
| Reglas del proyecto y del curso | `AGENTS.md` |
| Especificación del incremento | `docs/SPEC.md` (pendiente) |
| Plan de trabajo | `docs/PLAN.md` (pendiente) |
| Índice de documentación | `docs/README.md` |
| CI/CD | `docs/04-operations/ci-cd.md` |
| Despliegue | (retirado) |
| Seguridad | `docs/05-security/README.md` |
| Convenciones de commit | `docs/06-contribution/git-commit-prefixes.md` |
| Esquema de la base de datos | migraciones Flyway en `backend/src/main/resources/db/migration/` |

## Cómo correr

Ver `AGENTS.md` (sección "Cómo se corre y se prueba") para los comandos exactos de backend, frontend, tests y Docker.

Requisitos previos del backend en local: un PostgreSQL accesible con la base `patient_auditor` y las variables `DB_PASSWORD` y `JWT_SECRET` definidas (ver `.env.example`; ninguna tiene valor por defecto).
