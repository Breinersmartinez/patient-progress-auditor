# Cómo se construyeron los workflows de GitHub Actions

En este documento se explica **cómo** se construyeron y por qué se tomaron las decisiones técnicas que se ven en `.github/workflows/`. Es un complemento de `docs/04-operations/ci-cd.md`, que describe qué hace cada workflow y qué hay que configurar; aquí está la lógica de construcción.

---

## El problema de fondo

El repo es un monorepo: una API Spring Boot en `backend/` y una SPA Angular en `frontend/`, cada una con su propio destino (`Heroku` — app `patient-progress-auditor` — y `Vercel` — proyecto `patient-progress-auditor-frontend`). El flujo manual está documentado en `docs/04-operations/deployment.md`, pero depende de comandos a mano, y no hay una puerta de calidad entre "se ve bien en mi máquina" y "está en producción".

La construcción buscó tres cosas concretas:
1. **Un CI que falle antes de llegar a `main`**: compilar y correr tests de las dos aplicaciones en cada PR.
2. **Un CD que repita el flujo manual documentado** (Heroku vía `heroku.yml` + Vercel), pero automatizado y con un solo ambiente (sin separación staging/producción).
3. **Seguridad por defecto**: análisis de código, dependencias y secretos sin que nadie tenga que acordarse de correrlos.

---

## Los tres workflows

### 1. `ci.yml` — la puerta de entrada

**Trigger**: `pull_request` y `push` a `main`. Nada más. Los branches de features no corren CI porque no aportan información: lo que importa es el estado del PR.

**Estructura en tres jobs**:
- `lint-workflows`: valida el propio YAML de los workflows con `actionlint`. Es la primera línea de defensa: si un workflow está mal escrito, el error aparece aquí y no a mitad de un deploy.
- `backend`: `checkout` → `setup-java` (temurin 17, con `cache: maven`) → `mvn -B verify`. Se usa `verify` y no `package` porque `verify` corre la fase de tests y falla si estos no pasan.
- `frontend`: `setup-node` (Node 20, con cache de npm apuntando a `package-lock.json`) → `npm ci` → `ng test --watch=false --code-coverage` → `npm run build`.

**Detalle que costó encontrarlo**: el proyecto **no tenía `karma.conf.js`**. En Angular, `ng test` necesita esa configuración y sin ella el CI habría fallado en el primer run. Se creó `frontend/karma.conf.js` con `browsers: ['ChromeHeadless']`, que es lo que permite que los tests corran sin abrir un navegador en un runner de CI (y también funciona local). Ese archivo no es un extra: es un requisito para que el job de frontend exista.

**Decisiones de construcción**:
- Los artefactos (reports de tests, coverage) se suben con `upload-artifact` solo si el run no fue cancelado, y con `retention-days: 14`: están para diagnosticar un fallo, no para guardarse para siempre.
- El coverage lcov se genera en Karma (en `coverageReporter`, directorio `frontend/coverage/patient-progress-auditor-frontend/`) para que más adelante se pueda conectar a un servicio de reportes o al propio PR sin tocar el workflow.

### 2. `deploy.yml` — el flujo de liberación

**Trigger**: dos vías — `push` a `main` y `workflow_dispatch` manual. El repo tiene **un solo ambiente**, sin separación staging/producción: decidimos no multiplicar destinos (otra app Heroku, otro proyecto Vercel, más secrets) mientras no haya usuarios reales ni equipo. Cuando eso cambie, se reintroduce la separación con `environments` y tags `v*`.

**Dos jobs, uno por aplicación**: `deploy-backend` y `deploy-frontend`, con `deploy-frontend` `needs: deploy-backend` para que el deploy quede ordenado (el frontend puede apuntar a la API recién desplegada).

**Cómo se despliega el backend** (y por qué así): el flujo manual de `docs/04-operations/deployment.md` usa `heroku.yml` + `git push`. El workflow hace exactamente eso — un único paso sincroniza la URL de git de Heroku con el API key como password:

1. `git push https://heroku:${HEROKU_API_KEY}@git.heroku.com/${HEROKU_APP_NAME}.git HEAD:main`, con `HEROKU_APP_NAME` = `patient-progress-auditor` (app configurada como remoto en GitHub → Settings → Secrets).
2. Heroku construye la imagen con `heroku.yml` (`build.docker.web: backend/Dockerfile`) y libera el release.

Se eligió git push (y no container registry) porque la imagen la construye Heroku y el `run.web` de `heroku.yml` define el proceso; el runner no necesita Docker ni el CLI de Heroku.

**Smoke test**: tras el push, un `curl --retry 5` (sin `--fail`) contra la URL pública. No usa `--fail` a propósito: la API responde `401` por defecto (Spring Security sin `SecurityConfig` todavía) y el chequeo busca que el servidor responda, no un status concreto. Detecta el caso típico "la app arrancó y se cayó" (el célebre H10 de Heroku).

**Frontend**: se construye en el runner (`npm run build`) y se sube con `vercel deploy --prebuilt --prod`. Usar `--prebuilt` es intencional: el build ya ocurrió en CI, así Vercel no re-compila (y no puede fallar por razones distintas a las del build local). `--prod` porque, en el ambiente único, lo mergeado a `main` es lo que ven los usuarios.

**Concurrency**: grupo `deploy` con `cancel-in-progress: false`. Para CD no queremos cancelar un deploy en marcha: si llega un segundo disparo mientras uno corre, el segundo espera. Liberar dos releases "al mismo tiempo" en Heroku es exactamente la clase de cosa que corrompe releases.

### 3. `security.yml` — seguridad sin fricción

No depende de que nadie la ejecute: corre en `push`/`pull_request` a `main` y en un `schedule` semanal (lunes 02:00). El horario existe para barrer CVEs nuevos de dependencias ya mergeadas, cosa que el trigger por eventos no cubre.

Tres frentes:
- **CodeQL (SAST)**: `languages: java-kotlin, javascript-typescript` cubre Spring Boot y Angular. `autobuild` deduce solo cómo compilar cada lenguaje. Necesita `security-events: write`, el único job del repo que lo pide.
- **Dependency Review (SCA)**: solo en PRs, con `fail-on-severity: high`. Si un PR introduce una dependencia con vulnerabilidad alta, el PR se bloquea y el resumen aparece como comentario `comment-summary-in-pr`.
- **Gitleaks (secretos)**: en `push`, con `fetch-depth: 0` (escanea el historial, no solo el diff). Si un secreto está commiteado, el push falla. Solo se desbloquea `GITHUB_TOKEN` mínimo.

---

## Decisiones transversales (aplica a los tres)

### Pinning por SHA completo
Todas las actions se referencian con el SHA completo del commit y el tag de versión como comentario:

```yaml
uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
```

La razón es supply-chain: un tag (`@v7`) es una referencia mutable; alguien podría re-apuntarlo. Un SHA inmuta lo que se ejecuta. El comentario con el tag es lo que permite leerlo sin abrir el repo de la action. Los SHAs se obtuvieron de la API de GitHub al momento de escribir los workflows y no son inventados.

### Permisos mínimos
La regla es: cada job declara lo que usa y nada más. Por defecto `permissions: contents: read` a nivel de workflow; cada job que necesita más (como `security-events: write` en CodeQL) lo declara explícitamente. Así, si una action se ve comprometida o un paso falla, no tiene más poder que el mínimo para hacer su trabajo.

### Concurrency consciente
- **CI**: `cancel-in-progress: true` — si haces push de dos commits seguidos al mismo PR, el run del commit viejo es basura; cancelarlo ahorra minutos y no pierde información.
- **Deploy**: `cancel-in-progress: false` — al revés, por la razón explicada arriba.

### Lo que probamos y descartamos
**Path filters por job.** En un primer borrador se pusieron `paths` en los jobs de backend y frontend del CI, pensando "si cambia solo el backend, no corre el frontend". `actionlint` lo rechazó: `paths` no es una clave válida de job (solo existe a nivel de workflow en `on:`). Se evaluó partir el CI en dos archivos, uno por servicio, pero se descartó: con checks requeridos en branch protection, un workflow que no corre por path-filter se marca como *skipped* y complica el "todo debe pasar antes de mergear". Para un monorepo de este tamaño, correr ambos jobs siempre es más simple y más determinista.

**`mvn package` en vez de `verify`.** Descartado: `verify` es el que ejecuta los tests. La diferencia se notó al verificar localmente el workflow — por eso backend corre `mvn -B verify --no-transfer-progress`.

---

## Cómo se verificó la construcción

1. **actionlint local**: se descargó el binario y se corrieron los tres workflows. Es el mismo chequeo que hace el job `lint-workflows` en CI.
2. **Ejecución real de los comandos**: `mvn -B verify` en `backend/` (1 test; **requiere un PostgreSQL alcanzable** con las credenciales del perfil `dev` — en CI lo provee el servicio `postgres` del workflow) y `ng test --watch=false --code-coverage --browsers=ChromeHeadless` en `frontend/` (3 tests SUCCESS, coverage 100% sobre el código actual). Ambos son exactamente los comandos que corren en los jobs; verificar localmente elimina la clase de errores que solo aparecen cuando el workflow ya está pusheado.

---

## Lo que no se construyó (y por qué)

- **Gate CI → CD por `needs`**: un workflow no puede depender de otro workflow. La forma estándar es que CI sea un *required status check* en branch protection de `main`; así, nada llega a `main` (y por lo tanto a deploy) sin CI verde. Eso se configura en Settings de GitHub, no en código.
- **Reusable workflows**: para dos aplicaciones, la duplicación controlada de 2 jobs en `deploy.yml` es más legible que la indirección de `workflow_call`. Si el repo crece a 5+ servicios, ese es el refactor indicado.
- **Secrets en OIDC**: Heroku y Vercel no soportan OIDC federation, así que los tokens de API son la vía posible. Todos viven como secrets de repositorio (un solo ambiente de despliegue).