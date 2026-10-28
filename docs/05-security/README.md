# Seguridad — Patient Progress Auditor

Postura de seguridad del proyecto. Este documento no repite contenido de `04-operations/ci-cd.md` ni de `03-engineering/github-actions-workflows.md`: resume qué está automatizado, qué depende de configuración humana, y qué aún no se cubre.

## Qué está automatizado

| Control | Cómo | Cuándo | Falla el pipeline si |
|---|---|---|---|
| **SAST** | CodeQL (Java/Kotlin + JavaScript/TypeScript) | Push a `main`, PR a `main`, semanal (lunes 02:00) | La analysis de GitHub reporta algo que el equipo decide bloquear |
| **SCA** | Dependency Review de GitHub | PRs a `main` | Un PR introduce una dependencia con vulnerabilidad de severidad alta |
| **Secretos** | Gitleaks sobre el historial (`fetch-depth: 0`) | Push | Un secreto está commiteado |

## Qué depende de configuración humana (por hacer)

- **Branch protection** en `main`: revisores requeridos, status checks de CI requeridos, y administradores sujetos a las mismas reglas.

## Principios aplicados

- **Pinning por SHA** en todas las actions de los workflows (supply-chain): una action se referencia por commit inmutable, con el tag de versión solo como comentario.
- **Mínimo privilegio**: permisos explícitos por job; ninguno pide más que lo necesario (solo CodeQL requiere `security-events: write`).
- **Secretos fuera del código**, nunca en archivos versionados.

## Brechas conocidas y no cubiertas (consciente)

- **Contenedor**: todavía no hay escaneo de imagen (Trivy/Grype) ni firma de las imágenes Docker.
- **Dependencias backend SCA fuera del PR**: dependabot no está configurado; los CVEs en dependencias ya mergeadas se detectan con el run semanal de CodeQL.
- **Trailing**: no hay `SECURITY.md` público con procedimiento de divulgación responsable. Prioridad media si el repo sale público.
