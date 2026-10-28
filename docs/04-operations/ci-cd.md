# CI/CD — Patient Progress Auditor (GitHub Actions)

Pipeline de integración y despliegue continuo del monorepo. Complementa `deployment.md` (que describe el despliegue manual) con la versión automatizada.

## Workflows

| Workflow | Archivo | Cuándo corre | Qué hace |
|---|---|---|---|
| **CI** | `.github/workflows/ci.yml` | PR a `main` y push a `main` | Lint de workflows, compila y testea backend (Maven) y frontend (Angular/Karma headless) con coverage |
| **Deploy** | `.github/workflows/deploy.yml` | Merge (push) a `main` · `workflow_dispatch` manual | Despliegue desactivado / no aplicable en esta configuración. |
| **Security** | `.github/workflows/security.yml` | Push/PR a `main` + semanal (lunes 02:00) | CodeQL (SAST), Dependency Review (SCA) y Gitleaks (secretos) |

## Estándares aplicados

- **Pinning por SHA**: toda action referencia commit completo `@<sha>` con el tag de versión como comentario. Mitiga supply-chain (verifica integridad del código ejecutado).
- **Mínimo privilegio**: cada job declara `permissions:` explícitas (p. ej. `contents: read`); CodeQL agrega `security-events: write` solo donde lo necesita.
- **Concurrency**: cancela runs obsoletos del mismo ref en CI; bloquea desplegues paralelos (`cancel-in-progress: false`) para no liberar releases fuera de orden.
- **Auditoría de flujo**: job `lint-workflows` valida la sintaxis de los YAML con actionlint antes de correr el resto.
- **Secrets de repositorio**: un solo ambiente de despliegue; los secretos viven a nivel de repositorio, no en `environments` staging/production.
- **Rama protegida**: `main` exige PR con los checks `CI`, `Security` y `Qodana` verdes (ver *Branch protection* abajo); nada llega a `main` sin pasar la puerta.

## Secretos requeridos

Configurar en Settings → Secrets and variables → Actions (nivel repositorio):

| Secreto | Uso |
|---|---|

## Flujo de liberación (liberación continua)

```
── feature → PR → CI (build+test) + Security + Qodana → merge a main
```

1. **PR**: corren `CI`, `Security` (CodeQL) y `Qodana`; son *required status checks* en la protección de `main`. Sin los tres verdes no se puede mergear ni pushear directo.
2. **Merge a `main`** → sin despliegue automatizado configurado.
3. No hay ambientes ni tags de liberación: lo mergeado a `main` es lo que corre. Para rollback, revertir el merge en un PR nuevo.

Despliegue manual alternativo: Actions → *Deploy* → *Run workflow* (despliega el estado actual de `main`).

## Branch protection aplicada a `main` (Settings → Branches → `main`)

- [x] Require a pull request before merging
- [x] Require status checks: `Backend (Java 17 / Maven)`, `Frontend (Node 20 / Angular)`, `Lint GitHub Actions`, `CodeQL (SAST)`, `Qodana for JVM`
- [x] Require conversation resolution
- [x] Delete head branches after merge
- [x] Enforce admins
- [x] Force pushes y borrados deshabilitados

> `Gitleaks (secretos)` no se exige como check porque corre solo en `push`; de ser requerido, bloquearía los PRs.

## Notas

- **Gitleaks** falla el run si detecta secretos commiteados; corre solo en push (los PRs se cubren con Dependency Review).
