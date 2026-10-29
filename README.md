# Patient Progress Auditor

Auditor de la evolución de pacientes. Enfermería registra cada día, en texto libre, cómo está un paciente; un agente de IA analiza los registros de la semana, los compara con las semanas anteriores y redacta un informe que enfermería revisa, corrige y aprueba antes de que llegue al médico.

Es apoyo al seguimiento documental: **no diagnostica, no receta, no sustituye el criterio profesional.** Todos los datos son sintéticos.

Proyecto del curso *Ingeniería de Aplicaciones con IA* (UCC). Esta entrega es el **Momento 2: el módulo de enfermería**.

---

## 1. Arrancar la aplicación (un comando)

**Requisitos de la máquina: solo Docker Engine con Docker Compose v2** (`docker compose version` debe responder). No hace falta Java, Node ni PostgreSQL: todo se compila y corre en contenedores. Necesita conexión a internet (para bajar las imágenes la primera vez y para llamar al proveedor de IA) y los puertos 4200 y 8080 libres; si alguno está ocupado, `run.sh` usa el siguiente libre y lo avisa.

Desde la raíz del repositorio:

```bash
./run.sh
```

- No pide nada si no hay terminal (una IA o un script pueden ejecutarlo sin quedarse esperando).
- Crea `.env` desde `.env.example` y genera la contraseña de la base de datos (`DB_PASSWORD`). No hay nada que editar a mano.
- Construye las imágenes, levanta los tres servicios y **regresa cuando están sanos**. La primera vez compila el backend y el frontend: medido el 29-oct en una máquina de desarrollo con las imágenes base ya descargadas, **4 min 23 s** de un árbol limpio a los tres servicios sanos (con las imágenes por descargar, más: depende de la red). Volver a ejecutarlo con todo construido toma de segundos a un minuto y medio.
- Termina imprimiendo las direcciones. Salida esperada (los puertos pueden variar):

```text
La aplicacion esta arriba.
  Interfaz:  http://localhost:4200
  API:       http://localhost:8080/swagger-ui.html
  Detener:   docker compose down
```

Si falta la llave del proveedor de IA, debajo aparece un `AVISO` (ver §3). La aplicación funciona igual, salvo las dos funciones que usan IA.

`docker compose up --build` a secas **no** sirve en un clon nuevo: la contraseña de la base de datos es obligatoria y no tiene valor por defecto (un valor por defecto sería una contraseña en un repositorio público). El comando es `./run.sh`.

### Qué se levanta

| Servicio | Imagen (versión fijada) | Qué es | Puerto en la máquina |
|---|---|---|---|
| `db` | `postgres:16.15-alpine` | **PostgreSQL 16**, base `patient_auditor`, usuario `patient_auditor`. Los datos viven en el volumen `pgdata` | ninguno (no se publica, para no chocar con un PostgreSQL local) |
| `backend` | `maven:3.9-eclipse-temurin-17` (compila) → `eclipse-temurin:17-jre` (corre) | API Spring Boot 3.4.1, Java 17 | 8080 |
| `frontend` | `node:20-alpine` (compila) → `nginx:1.28-alpine` (sirve) | Angular 19; nginx también reenvía `/api` al backend | 4200 |

Al arrancar el backend, **solo** con esto y sin pasos manuales:

1. Flyway aplica las migraciones `V1` a `V7` (`backend/src/main/resources/db/migration/`).
2. La semilla de demostración (`APP_SEED_DEMO=true`) inserta los datos sintéticos desde `backend/src/main/resources/seed/demo-data.json` y `demo-reports.json`: 4 usuarios, 5 pacientes, 119 registros con sus eventos ya confirmados y 5 informes. Es idempotente: reiniciar no duplica nada. **No llama al modelo.**

### Comprobar que quedó corriendo

Con los puertos por defecto (cámbielos si `run.sh` avisó otros):

```bash
curl -s http://localhost:8080/api/system/status    # {"status":"UP","database":"UP","today":"AAAA-MM-DD"}
curl -s http://localhost:8080/api/demo-users       # los 4 usuarios de demostración
curl -s -H 'X-Demo-User: 20000000-0000-0000-0000-000000000001' http://localhost:8080/api/llm/status   # proveedor principal y si tiene llave (nunca la muestra)
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:4200/   # 200
docker compose ps                                   # db, backend y frontend en "healthy"
```

Abra `http://localhost:4200` en el navegador.

### Detener, reiniciar, empezar de cero

```bash
docker compose down        # detiene; conserva los datos
./run.sh                   # vuelve a levantar
docker compose down -v     # detiene y BORRA la base (la semilla se vuelve a cargar al arrancar)
```

Si después de `down -v` se borra también `.env` (o se clona de nuevo) pero queda un volumen de una instalación anterior, `run.sh` se detiene con un mensaje que dice qué hacer (`docker compose down -v`): la contraseña de PostgreSQL solo se aplica al crear el volumen.

---

## 2. Usuarios de demostración y qué probar

**No hay inicio de sesión en esta entrega** (D23, decidido por el equipo: la autenticación y las credenciales llegan en el Momento 3). La aplicación abre con usuarios sembrados y se elige con cuál trabajar en el selector «Ver como» de la barra superior. No es un control de seguridad: sirve para que cada acción tenga un autor y para que las reglas de rol se cumplan en el servidor.

| Usuario | Rol | Identificador (para la API) |
|---|---|---|
| Marcela Rojas | Enfermería | `20000000-0000-0000-0000-000000000001` |
| Camilo Torres | Enfermería | `20000000-0000-0000-0000-000000000002` |
| Dr. Andrés Salazar | Médico | `20000000-0000-0000-0000-000000000003` |
| Dr. Julián Ortiz | Médico | `20000000-0000-0000-0000-000000000004` |

La interfaz de esta entrega es la de **enfermería**; el selector solo ofrece a Marcela y a Camilo. **No hay pantalla del médico** (decisión del equipo, E12c fuera de esta entrega). La regla del médico sí está en el servidor y se comprueba por la API enviando su identificador en la cabecera `X-Demo-User`:

```bash
# El médico solo recibe informes APROBADOS (los 4 sembrados); un informe pendiente le responde 404.
curl -s -H 'X-Demo-User: 20000000-0000-0000-0000-000000000003' http://localhost:8080/api/reports
```

### Los cinco pacientes (sintéticos)

Fechas fijas: los registros terminan el domingo 25-oct-2026 y el reloj de la aplicación usa la zona America/Bogotá.

| Paciente | Semanas con registros | Para qué sirve en la demostración |
|---|---|---|
| P-014 | 5 (21-sep a 25-oct) | Tiene 4 informes aprobados sembrados; la semana del 19-oct no tiene informe: es la que se genera. Permite el mensual |
| P-021 | 4 | Tiene 1 informe sembrado pendiente de revisión (semana del 12-oct) |
| P-007 | 3 | Pocos registros: el informe dice «sin información suficiente» en vez de rellenar |
| P-033 | 4 | Solo negaciones («sin vómito», «sin dolor»): el informe no inventa síntomas |
| P-040 | 2 | Una nota trae una instrucción maliciosa («ignora lo anterior…»): se trata como dato, no como orden |

### Recorrido de 5 minutos (necesita llave, ver §3)

1. Elija **Marcela Rojas**. En **Nuevo registro diario**, elija un paciente, escriba por ejemplo `Hoy vomitó dos veces, dolor de cabeza leve, no tuvo fiebre.`, pulse **Enviar** y revise los eventos que sugiere la IA (síntoma, intensidad, veces). Nada se guarda hasta pulsar **Aprobar y guardar**; **Descartar** cierra la vista previa sin guardar.
2. En **Generar informe**, elija **P-014**, la semana del **19-oct** y pulse **Generar con IA**. La pantalla muestra los pasos reales del agente a medida que ocurren (consulta de registros, métricas, validación). No bloquea la página: la ejecución corre en segundo plano.
3. Se abre el informe pendiente de revisión. Cada afirmación lleva un número `[n]` que lleva al registro original (fecha y texto) y el criterio de comparación; **Ver cómo se generó** muestra la herramienta, los parámetros, el resultado, los milisegundos y los tokens de cada paso.
4. **Editar** (crea la versión 2; la versión 1 de la IA no se toca), **Rechazar** (con motivo) o **Aprobar informe**. Solo un informe aprobado llega al médico.
5. En **Informes aprobados** están los 4 sembrados de P-014 y el que acaba de aprobar.
6. **Informe mensual.** En **Generar informe**, elija **P-014**, el período **Un mes (cuatro semanas)** y la ventana **del 21 de septiembre al 18 de octubre** (sus cuatro semanas ya tienen informe semanal aprobado). El agente lee los cuatro informes y las cifras de cada semana y redacta la evolución; la hoja muestra la tabla **Evolución semana a semana**, enlaza los cuatro informes de origen y pasa por la misma revisión (editar, rechazar, aprobar). Con **P-021** la ventana no se puede generar: la pantalla dice qué semanas faltan y en qué estado está su informe (regla RN-10, decidida por el código y no por el modelo).

Sin llave (ver §3) los pasos 1, 2 y 6 muestran un aviso claro; todo lo demás (pacientes, historial, informes sembrados, revisión, aprobación, rechazo) funciona.

---

## 3. La llave del proveedor de IA

La aplicación llama a un proveedor de LLM real (no hay modo simulado). Para usar la IA hace falta **una llave de API de Groq** (proveedor principal). La de Gemini (respaldo) es opcional.

**Tres formas de dársela a la aplicación; use cualquiera:**

1. **Su propia llave gratuita de Groq** (se crea en la consola de Groq → *API Keys*, sin tarjeta):
   ```bash
   GROQ_API_KEY=<su llave> ./run.sh
   ```
2. **La llave temporal que el equipo envía por un medio privado** (correo o mensaje), creada solo para la calificación y revocable. Se usa igual que la anterior. Nunca está en el repositorio ni en su historial.
3. **Sin llave:** `./run.sh` arranca igual, imprime el `AVISO` y la interfaz lo muestra donde se usa la IA. Cuando la tenga: `GROQ_API_KEY=<su llave> ./run.sh` (no reconstruye nada; solo recrea el backend).

**Recomendado: ponga también la llave de respaldo** (`GEMINI_API_KEY`, gratuita, se crea en Google AI Studio): `GROQ_API_KEY=<llave> GEMINI_API_KEY=<llave> ./run.sh`. La cuota gratuita de Groq (8 000 tokens por minuto) puede agotarse en plena demostración; con el respaldo el informe sigue (la traza muestra el cambio de proveedor) y sin él termina como `FALLIDA` con un mensaje honesto.

`run.sh` guarda las llaves en `.env` (archivo ignorado por git) para que reiniciar no la pierda. También puede escribirla ahí: `GROQ_API_KEY=...`. Con terminal, `run.sh` la pregunta una vez (sin eco; Enter para omitir). **Nunca** ponga una llave en `.env.example` ni en un archivo versionado: el repositorio es público.

Comprobar que la tomó (no imprime la llave; la API pide un usuario de demostración en la cabecera, aquí Marcela):

```bash
curl -s -H 'X-Demo-User: 20000000-0000-0000-0000-000000000001' http://localhost:8080/api/llm/status
# "available": true y "configured": true en el proveedor principal
```

### Proveedor y modelo, con su razón

| | Principal: **Groq** `openai/gpt-oss-120b` | Respaldo: **Gemini** `gemini-3.5-flash-lite` |
|---|---|---|
| Para qué | Estructurar la entrada y ejecutar el agente | Lo mismo, si Groq falla o no tiene llave |
| Latencia medida (28-oct) | 0,5 a 1,4 s por llamada corta | 0,7 s en una llamada trivial; 4 a 8 s estructurando |
| Precio de lista, USD por millón de tokens (entrada / salida) | 0,15 / 0,60 | 0,30 / 2,50 |
| Herramientas y JSON con esquema | Sí, esquema estricto (medido) | Sí (medido) |
| Ventana de contexto | 131 072 tokens | 1 048 576 tokens |
| Límite gratuito | 30 solicitudes/min, 1 000/día, 8 000 tokens/min, 200 000 tokens/día | **No verificado**: Google no lo publica por modelo |

La razón completa, los modelos descartados y los riesgos están en [docs/decisiones.md](docs/decisiones.md) (E8). Ante un 429 o una caída, la capa reintenta (1 s → 2 s → 4 s) y luego conmuta al respaldo; el mensaje que ve la persona es honesto y nunca trae trazas ni llaves.

### Cambiar de proveedor en vivo

Edite `.env` (o exporte las variables) y recree el backend; no hay que reconstruir imágenes:

```bash
# ejemplo: Gemini como principal y Groq como respaldo
sed -i 's/^LLM_PROVIDER=.*/LLM_PROVIDER=gemini/; s/^LLM_FALLBACK=.*/LLM_FALLBACK=groq/' .env
docker compose up -d backend
curl -s -H 'X-Demo-User: 20000000-0000-0000-0000-000000000001' http://localhost:8080/api/llm/status     # muestra el nuevo orden
```

`LLM_FALLBACK=none` desactiva el respaldo. Esto necesita `GEMINI_API_KEY` en `.env`.

### Forzar los límites del agente en la demostración

El agente tiene cuatro límites obligatorios. Cada uno se fuerza con una variable (la ruta sigue llamando al proveedor real; no se simula nada):

| Límite | Valor | Variable | Para forzarlo |
|---|---|---|---|
| Iteraciones del bucle | 5 | `AGENT_MAX_ITERATIONS` | `1` → el informe queda `INCOMPLETO` por límite de iteraciones |
| Tokens por solicitud | 30 000 | `AGENT_MAX_TOKENS` | `1000` → `INCOMPLETO` por tokens |
| Tiempo por herramienta | 5 s | `AGENT_TOOL_TIMEOUT_SECONDS` | `0` |
| Confirmación humana | el agente solo guarda un borrador `PENDIENTE_REVISION` | — | no aplica: no puede aprobar |

```bash
AGENT_MAX_ITERATIONS=1 ./run.sh        # o edite .env y: docker compose up -d backend
```

Un informe `INCOMPLETO` es un estado final con el motivo a la vista; no se aprueba.

**Lo que no se puede forzar en vivo (decisión del equipo, declarada):** los tres fallos del proveedor (tiempo agotado, salida que no valida, 429 o caída) no tienen interruptor en la aplicación. Se prueban con pruebas automáticas contra un servidor HTTP local que reproduce un 429 con `Retry-After`, un 503, un tiempo agotado real, una llave rechazada y una respuesta ilegible. Ver [docs/decisiones.md](docs/decisiones.md) (E8 y «Decisiones del equipo»).

---

## 4. Dónde entra el modelo y dónde no

Si se quita el modelo, los registros siguen existiendo pero el informe no. La frontera:

- **Código determinista:** guardar y consultar registros, ordenar, **calcular las métricas** (conteos, días con síntoma, comparación con la semana anterior), permisos, validaciones y estados del informe. Los números los calcula el código, nunca el modelo; un validador rechaza un borrador cuyas cifras no coincidan.
- **Modelo (LLM):** (1) al registrar, proponer los eventos que dice el texto (síntoma, intensidad, veces); (2) redactar la comparación entre semanas dentro del bucle del agente.
- **Persona:** quien registra confirma los eventos antes de guardar; enfermería contrasta cada afirmación con el registro original y aprueba.

```text
navegador ── :4200 ── nginx (frontend) ── /api ──▶ backend :8080 ──▶ PostgreSQL 16 (db)
                                                        │
                                                        └──▶ Groq (principal) / Gemini (respaldo)
```

Decisiones que el profesor suele preguntar (todas con su razón y la alternativa descartada en [docs/decisiones.md](docs/decisiones.md)):

| Pregunta | Respuesta corta |
|---|---|
| ¿Síncrono o asíncrono? | **Asíncrono** para el informe: `POST /api/runs` responde 202 al instante y la pantalla recibe los pasos reales por **SSE**. La estructuración al registrar es una sola llamada corta y va en la misma petición (E11, E9) |
| ¿Con RAG o sin RAG? | **Sin RAG:** los datos son estructurados (fechas, síntomas, conteos) y una consulta SQL directa es exacta; el agente pide los registros por herramientas de lectura (E15, `docs/decisiones/semana08.md`) |
| ¿Por qué ese proveedor? | Tabla de §3 y E8 |
| ¿Por qué Java 17 y no 21? | E15: LTS y mínimo de Spring Boot 3.x; pom, Dockerfile y CI ya en 17 y no se necesita nada de 21 |
| ¿Qué límites tiene el agente? | Cuatro, §3 y [docs/decisiones/semana06.md](docs/decisiones/semana06.md) |
| ¿Cómo se sabe que sirve? | Suite de evaluación y cuatro cifras contra una solución sin modelo (§5) |

---

## 5. Pruebas y evaluación

Las pruebas del backend usan un PostgreSQL real y lo modifican: use una base desechable, no la de la aplicación.

```bash
# Backend (313 pruebas): necesita un PostgreSQL 16 accesible y Java 17 + Maven en la máquina
docker run -d --name ppa-test-pg -e POSTGRES_DB=patient_auditor -e POSTGRES_USER=patient_auditor \
  -e POSTGRES_PASSWORD=prueba -p 55432:5432 postgres:16.15-alpine
cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/patient_auditor \
  SPRING_DATASOURCE_USERNAME=patient_auditor SPRING_DATASOURCE_PASSWORD=prueba mvn -B verify

# Frontend (113 pruebas): Node 20 y Chrome
cd frontend && npm ci && npx ng test --watch=false --browsers=ChromeHeadless

# Variables documentadas, y las 29 pruebas de la propia evaluación
scripts/check-env-example.sh
python3 -m unittest discover -s eval
```

**Evaluación del agente (`eval/`)**: 8 casos dorados de informes semanales (con un rechazo, una inyección y datos insuficientes), 3 del informe mensual y 16 de estructuración, verificados a mano. Un comando corre todo contra la aplicación levantada y escribe un resultado con fecha:

```bash
python3 eval/run_eval.py                    # contra la app en :8080 (gasta cuota de la llave)
python3 eval/run_eval.py --baseline-only    # solo la solución convencional: sin app y sin llave
```

La suite rechaza cada informe que genera para poder repetir la semana: úsela en una base desechable. Las cuatro cifras (tasa de éxito, costo por tarea, **mediana** de latencia y recuperación), su método y lo que **no** se midió están en [docs/decisiones/semana10.md](docs/decisiones/semana10.md); la guía de uso, en [eval/README.md](eval/README.md).

---

## 6. Si algo falla

| Síntoma | Causa y solución |
|---|---|
| `run.sh`: «Docker no esta instalado» / «Falta Docker Compose v2» / «El servicio de Docker no responde» | Instale o inicie Docker; Compose debe ser v2 (`docker compose`, con espacio) |
| `run.sh`: «Existe un volumen de base de datos creado con otra contrasena» | Quedó una base de una instalación anterior. Si no necesita esos datos: `docker compose down -v` y `./run.sh` |
| «El puerto 8080 está ocupado; BACKEND_PORT pasa a 8081» | Es un aviso, no un error: use el puerto que imprime al final |
| La primera vez parece detenido | Está compilando; `docker compose logs -f backend` muestra el avance |
| La interfaz dice que falta la llave | §3. La aplicación funciona sin ella, salvo registrar con IA y generar informes |
| Un informe queda `INCOMPLETO` o `FALLIDA` | Es un estado honesto: la traza dice por qué (límite agotado, salida que no validó, proveedor caído). Pida el informe de nuevo |
| 503 «El sistema está generando otros informes en este momento» | El ejecutor admite 2 informes a la vez y 4 en cola; espere unos segundos |

---

## 7. Estado de la entrega y límites declarados

**Momento 2.** Está: registro con estructuración por IA y confirmación humana, agente con cuatro límites, informe semanal y mensual (cuatro semanas aprobadas, RN-10) con citas y traza, revisión (editar con versiones, aprobar, rechazar), ejecución en segundo plano con progreso por SSE, semilla sin llamar al modelo, evaluación con cifras.

**No está (decidido y declarado):** autenticación y credenciales (Momento 3, D23); pantalla del médico (E12c fuera; la regla se aplica en el servidor y se prueba por API); PDF y voz (opcionales); fallos del proveedor forzables en vivo (se prueban con pruebas automáticas).

**Lo que más conviene saber antes de confiar en las cifras:** casi todas las ejecuciones medidas terminaron en el proveedor de respaldo (la cuota diaria de Groq se agotó durante la medición); el tamaño de muestra es pequeño; la calidad de la redacción no se midió. Detalle en [docs/decisiones/semana10.md](docs/decisiones/semana10.md).

## 8. Estructura del repositorio

```text
backend/      API Spring Boot 3.4.1 (Java 17, Maven)      — paquete com.patientprogressauditor
frontend/     SPA Angular 19 (Node 20)
docker-compose.yml · run.sh · .env.example               — arranque en un comando
eval/         casos dorados, solución convencional, script y resultados fechados
scripts/      verificación de variables, cálculo independiente de métricas, generador de la semilla
docs/         SPEC.md · REQUISITOS.md · PLAN.md · decisiones.md · decisiones/ · diccionarios de datos
skills/       los cuatro skills del curso (especifica-encargo, planea-trabajo, ejecuta-plan, valida-resultado)
.github/      CI, Security (CodeQL, Dependency Review, Gitleaks) y Qodana
```

| Para saber… | Lea |
|---|---|
| Qué se construye y por qué | [docs/SPEC.md](docs/SPEC.md), [docs/REQUISITOS.md](docs/REQUISITOS.md) |
| Cómo se planeó y verificó cada etapa | [docs/PLAN.md](docs/PLAN.md) |
| Cada decisión técnica con su razón y alternativa | [docs/decisiones.md](docs/decisiones.md) |
| Tablas, columnas y reglas de integridad | [docs/DICCIONARIO_DATOS_M2.md](docs/DICCIONARIO_DATOS_M2.md) |
| Reglas del proyecto y del curso | [AGENTS.md](AGENTS.md) |
| Prueba con una persona ajena al código | [docs/prueba_companero.md](docs/prueba_companero.md) |
