# CI/CD — Patient Progress Auditor (GitHub Actions)

Pipeline de integración y despliegue continuo del monorepo. Complementa `deployment.md` (que describe el despliegue manual) con la versión automatizada.

## Workflows

| Workflow | Archivo | Cuándo corre | Qué hace |
|---|---|---|---|
| **CI** | `.github/workflows/ci.yml` | PR a `main` y push a `main` | Lint de workflows, compila y testea backend (Maven) y frontend (Angular/Karma headless) con coverage |
| **Deploy** | `.github/workflows/deploy.yml` | Merge (push) a `main` · `workflow_dispatch` manual | Despliega el backend en Heroku (`git push` + `heroku.yml`) y el frontend en Vercel (`vercel deploy --prebuilt --prod`). Ambiente único: sin staging/producción |
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
| `HEROKU_API_KEY` | Autenticación del `git push` a Heroku (usuario `heroku`, password = API key) |
| `HEROKU_APP_NAME` | Nombre de la app Heroku (ej. `patient-progress-auditor`) |
| `APP_URL` | Smoke test del backend |
| `API_URL` | Variable de build del frontend (URL base de la API) |
| `VERCEL_TOKEN` | Deploy CLI de Vercel |
| `VERCEL_ORG_ID` | Org de Vercel |
| `VERCEL_PROJECT_ID` | Proyecto Vercel del frontend |

## Flujo de liberación (liberación continua)

```
── feature → PR → CI (build+test) + Security + Qodana → merge a main
                                                        │
                                                        └── merge → Deploy (Heroku + Vercel)
```

1. **PR**: corren `CI`, `Security` (CodeQL) y `Qodana`; son *required status checks* en la protección de `main`. Sin los tres verdes no se puede mergear ni pushear directo.
2. **Merge a `main`** → despliegue automático al ambiente único (Heroku + Vercel).
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

- **Backend** se despliega con `git push` de `HEAD:main` a Heroku, que construye la imagen desde `heroku.yml` (`build.docker.web: backend/Dockerfile`). Igual que el flujo manual de `deployment.md`.
- **Frontend**: el build ocurre en el runner (`npm run build`) y `vercel deploy --prebuilt` sube `dist/`. Vercel no necesita re-build.
- **Gitleaks** falla el run si detecta secretos commiteados; corre solo en push (los PRs se cubren con Dependency Review).
- El archivo `deployment.md` sigue siendo la referencia operativa de troubleshooting (H10, `No images to push` histórico, etc.).