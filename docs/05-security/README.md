# Seguridad — Patient Progress Auditor

Postura de seguridad del proyecto. Este documento no repite contenido de `04-operations/ci-cd.md` ni de `03-engineering/github-actions-workflows.md`: resume qué está automatizado, qué depende de configuración humana, y qué aún no se cubre.

## Qué está automatizado

| Control | Cómo | Cuándo | Falla el pipeline si |
|---|---|---|---|
| **SAST** | CodeQL (Java/Kotlin + JavaScript/TypeScript) | Push a `main`, PR a `main`, semanal (lunes 02:00) | La analysis de GitHub reporta algo que el equipo decide bloquear |
| **SCA** | Dependency Review de GitHub | PRs a `main` | Un PR introduce una dependencia con vulnerabilidad de severidad alta |
| **Secretos** | Gitleaks sobre el historial (`fetch-depth: 0`) | Push | Un secreto está commiteado |

## Qué depende de configuración humana (por hacer)

- **Environments** `staging` y `production` en GitHub con sus secrets (ver `04-operations/ci-cd.md` → "Secretos requeridos").
- **Branch protection** en `main`: revisores requeridos, status checks de CI requeridos, y administradores sujetos a las mismas reglas.
- **Protección del environment `production`**: activar *environment protection rules* para exigir aprobación antes de liberar con tags.

## Principios aplicados

- **Pinning por SHA** en todas las actions de los workflows (supply-chain): una action se referencia por commit inmutable, con el tag de versión solo como comentario.
- **Mínimo privilegio**: permisos explícitos por job; ninguno pide más que lo necesario (solo CodeQL requiere `security-events: write`).
- **Secretos por environment**, nunca en el código ni en variables del repositorio.

## Brechas conocidas y no cubiertas (consciente)

- **OIDC**: Heroku y Vercel no soportan federación OIDC; los tokens de API (`HEROKU_API_KEY`, `VERCEL_TOKEN`) son la única vía y se administran como secrets de environment, con rotación manual.
- **Contenedor**: el backend se despliega como imagen Docker a Heroku, pero todavía no hay escaneo de imagen (Trivy/Grype) ni firma. El acabado entra junto con el pipeline de imágenes.
- **Dependencias backend SCA fuera del PR**: dependabot no está configurado; los CVEs en dependencias ya mergeadas se detectan con el run semanal de CodeQL.
- **Trailing**: no hay `SECURITY.md` público con procedimiento de divulgación responsable. Prioridad media si el repo sale público.