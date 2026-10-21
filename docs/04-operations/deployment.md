# Despliegue — Patient Progress Auditor

Repositorio monorepo con dos aplicaciones independientes:

| Aplicación | Ubicación | Tecnología | Destino |
|---|---|---|---|
| API (backend) | `backend/` | Spring Boot 3.4.1 · Java 17 · Maven | Heroku (stack container) |
| SPA (frontend) | `frontend/` | Angular 19.2 · TypeScript | Vercel |

Cada una se despliega por su cuenta, con su propio pipeline y sus propias variables de entorno. No existe despliegue acoplado.

---

## 1. Backend — Heroku

### 1.1. Modelo de despliegue

Se usa **`heroku.yml` + `git push heroku main`**, no el `container:push`.

**Contexto / por qué:** Heroku auto-detecta el buildpack buscando `pom.xml` en la raíz del repositorio. En un monorepo el `pom.xml` vive en `backend/`, así que la detección falla (`No default language could be detected`). `heroku.yml` resuelve esto declarando el Dockerfile del backend:

```yaml
build:
  docker:
    web: backend/Dockerfile   # el contexto del build es la carpeta del Dockerfile (backend/)
run:
  web: java -jar app.jar       # entra en reemplazo del Procfile (que por eso se eliminó)
```

La app debe estar en stack `container` (ver requisitos). Alternativas descartadas y documentadas:

1. **Container Registry (`heroku container:push`)** — era el modelo previo; quedó obsoleto al adoptar `heroku.yml` (su presencia rompía el CLI con `No images to push`).
2. **Buildpacks** — no aplican al monorepo: requieren `pom.xml` en la raíz.

### 1.2. Requisitos (se configuran una sola vez)

```bash
# Stack container (Docker, no buildpacks)
heroku stack:set container -a patient-progress-auditor

# `heroku.yml` commiteado en la raíz (definido en §1.1)

# No hay buildpacks configurados (verificable con `heroku buildpacks`)

# Profile de Spring en producción
heroku config:set SPRING_PROFILES_ACTIVE=prod -a patient-progress-auditor

# PostgreSQL (el addon setea PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD)
heroku addons:create heroku-postgresql:essential-0 -a patient-progress-auditor
```

Verificación de stack: `heroku apps:info -a patient-progress-auditor` debe mostrar `Stack: container`.

### 1.3. Desplegar

El repo tiene **un solo ambiente**: no hay separación staging/producción. Despliegue automático:

1. Abrir un PR a `main` (la rama está protegida contra push directos).
2. Cuando `CI`, `Security` y `Qodana` pasan, se mergea → el workflow `Deploy` hace `git push` a Heroku y el build ocurre ahí.

Fallback manual (solo emergencias; el push a Heroku no pasa por la protección de GitHub):

```bash
# 1. Verificar el build local (contexto = backend/)
docker build -f backend/Dockerfile -t patient-progress-auditor:test .

# 2. Preparar el remote (una sola vez)
heroku git:remote -a patient-progress-auditor

# 3. Desplegar (el build de Docker ocurre en Heroku)
git push heroku main
```

> El proceso `web` lo define `heroku.yml` (`java -jar app.jar`); el Procfile ya no existe y no hace falta.

### 1.4. Verificación

```bash
heroku ps -a patient-progress-auditor        # → web.1: up
heroku logs --num 50 -a patient-progress-auditor
curl -s -o /dev/null -w "%{http_code}\n" https://patient-progress-auditor.herokuapp.com/
```

**Nota sobre el `401`:** con el scaffold actual (sin `SecurityConfig` todavía) la API responde `401` en `/` y `/swagger-ui.html` porque Spring Security genera una password aleatoria por defecto. Es esperado hasta que exista configuración de seguridad real.

---

## 2. Frontend — Vercel

### 2.1. Modelo de despliegue

El job `Frontend → Vercel` del workflow `Deploy` construye en el runner (`npm run build`) y sube con `npx vercel deploy --prebuilt --prod`. Vercel no re-compila; sirve `dist/`.

### 2.2. Configuración

- **Root directory:** `frontend`
- **Build command:** `npm run build`
- **Output directory:** `dist/patient-progress-auditor-frontend/browser`
- **Base framework preset:** Angular
- **Proyecto de Vercel:** `patient-progress-auditor-frontend` (el id viaja en el secreto `VERCEL_PROJECT_ID`)

`frontend/vercel.json` aplica los rewrites SPA (toda ruta → `index.html`) y el cacheo de assets:

```json
{
  "buildCommand": "npm run build",
  "outputDirectory": "dist/patient-progress-auditor-frontend/browser",
  "framework": "angular",
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
}
```

### 2.3. Variables de entorno

| Variable | Uso |
|---|---|
| `API_URL` | URL base de la API (ej: `https://patient-progress-auditor.herokuapp.com`) |

> A diferencia del backend, en Angular las variables de entorno no se inyectan en tiempo de construcción desde `process.env`: se definen en `src/environments/` (aún no creados) o se leen en runtime. `API_URL` en Vercel se usa para generar los artefactos en el build.

---

## 3. Monorepo — reglas de oro

- **`heroku.yml` en la raíz** define el proceso web (`java -jar app.jar`); deja de existir el `Procfile`.
- **El contexto del build Docker es la carpeta del `Dockerfile`** (`backend/`), no la raíz: el `COPY pom.xml` del Dockerfile funciona aunque el `pom.xml` no esté arriba.
- **No mover `pom.xml` a la raíz:** el buildpack Java no entiende subdirectorios; el backend va por Docker vía `heroku.yml`, no por buildpack.
- **`frontend/` está excluida del contexto Docker** vía `.dockerignore` de la raíz (aunque con contexto `backend/` no viaja igualmente).
- **Cada app tiene su propio `.env`** documentado en `.env.example` (raíz).

---

## 4. Errores comunes y su solución

| Error | Causa | Solución |
|---|---|---|
| Build del Dockerfile falla con `COPY failed: ... pom.xml: not found` | Se corrió `docker build` con contexto = raíz | Usar `docker build -f backend/Dockerfile` desde la raíz (contexto correcto) o `cd backend && docker build .` |
| `Your app does not include a heroku.yml build manifest` | Se intentó `git push` sin `heroku.yml` commiteado | Commitear `heroku.yml`; verificar stack con `heroku apps:info` (debe ser `container`) |
| `you have triggered a build ... at least twice` | Misma SHA ya registrada en builds de Heroku | Esperar a un commit nuevo (o `git commit --allow-empty`) y volver a push |
| App arranca y muere (H10) | Perfil `dev` sin PostgreSQL local | `heroku config:set SPRING_PROFILES_ACTIVE=prod` |
| Push de CI rechazado con `[rejected] ... (fetch first)` | Checkout shallow (`fetch-depth: 1`): git no puede probar fast-forward contra `main` del remote de Heroku | `actions/checkout` con `fetch-depth: 0` (ya en `deploy.yml`) |