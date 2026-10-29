# Decisiones técnicas — Patient Progress Auditor

> Cada decisión lleva su razón y la alternativa descartada: en la defensa se pregunta por todas (RNF-14, OBJ-16). Se completa etapa por etapa; el detalle va en `docs/decisiones/`.

## E1 — Arranque en frío (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Sin `JWT_SECRET`, sin filtro de JWT y cadena de seguridad abierta en el Momento 2 | D23: la autenticación es del Momento 3. El rol sale del usuario de demostración elegido (D26), que es separación de funciones y no un control de seguridad | Dejar el secreto con un valor por defecto: sería una credencial falsa en un repositorio público |
| CSRF desactivado y sesión sin estado | No hay cookies ni sesión: el usuario viaja en una cabecera, así que el ataque que CSRF previene no aplica. Al llegar el token (Momento 3) se revisa | Dejar CSRF activo: bloquearía todos los `POST` sin aportar protección |
| Perfil de Spring nuevo `docker` | Conexión desde `DATABASE_URL`, `ddl-auto: validate` y sin SQL en el log. `dev` registra SQL en DEBUG y hoy actualiza el esquema junto a Flyway; `prod` está reservado para rechazar la semilla | Usar `dev` en Compose; usar `prod` (la semilla no correría) |
| Estado de salud con un endpoint propio, `GET /api/system/status` (200, o 503 si la base no responde) | Sirve al `healthcheck` de Compose y, desde E8, al aviso de llave faltante, sin una dependencia nueva | `spring-boot-starter-actuator`: librería adicional para un solo uso |
| La base no se publica al host | El 5432 y el 5433 de esta máquina están ocupados y le pasará a cualquier evaluador con un Postgres local; sin publicar no puede chocar | Publicar el 5432 |
| Puertos del host configurables (`BACKEND_PORT`, `FRONTEND_PORT`) y `run.sh` elige el siguiente libre si el configurado está ocupado | Caso borde de la SPEC §5 («puerto ocupado en la máquina del evaluador») | Fallar con «puerto ocupado» |
| `run.sh` levanta con `docker compose up --build -d --wait` | Debe regresar cuando la app está sana: una IA que lo ejecuta no puede quedarse con un proceso en primer plano. Imprime URLs y cómo detener | `up` en primer plano: cuelga a quien lo ejecuta |
| `DB_PASSWORD` es obligatoria en Compose, sin valor por defecto | Un valor por defecto sería una contraseña en el repositorio público y se desincronizaría del volumen ya creado. Compose falla con un mensaje que manda a `./run.sh`. **El README (E15) debe decir `./run.sh` como el comando.** Pendiente de confirmar con el equipo: D22 dice que `docker compose up --build` directo se comporta igual | Contraseña por defecto en el compose |
| `run.sh` genera `DB_PASSWORD` y, si ya existe un volumen de base creado con otra, se detiene con instrucciones | La contraseña de Postgres solo se aplica al crear el volumen; seguir fallaría con un error de autenticación poco claro | Continuar y dejar que falle el backend |
| La llave se toma del entorno; con terminal se pide sin eco y se guarda en `.env`; sin terminal ni llave se arranca y se avisa | D22 y la prueba del profesor: nunca un `read` sin comprobar la terminal | Quedarse esperando la llave |
| Imágenes fijadas: `postgres:16.15-alpine`, `node:20-alpine`, `nginx:1.28-alpine`, y las ya presentes `maven:3.9-eclipse-temurin-17` y `eclipse-temurin:17-jre` | Mismo mayor de PostgreSQL que el servicio de `ci.yml` (`postgres:16`); Node 20 como el CI; Java 17 (D9) | `latest`: el arranque dejaría de ser reproducible |
| nginx con `proxy_buffering off`, lectura de 1 h y DNS dinámico de Docker | D24: los pasos del agente deben llegar a medida que ocurren; la resolución dinámica evita que nginx caiga si el backend aún no existe. El servidor además enviará `X-Accel-Buffering: no` (E11) | Dejar el búfer por defecto: el SSE llegaría de golpe |
| El frontend llama a `/api` en el mismo origen; `ng serve` usa `proxy.conf.json` | Sin CORS en Docker y sin variable de URL que mantener; se retira `API_URL`, que nada leía | Una variable `API_URL` compilada en el bundle |
| Se verifica `.env.example` con `scripts/check-env-example.sh` | La página de entrega trata una variable no documentada como falla crítica | Revisarlo a mano |

## E2 — Esquema y roles (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Las claves foráneas de las entidades son `UUID` simples, sin `@ManyToOne` | El ejecutor del agente corre en otro hilo y `open-in-view` está apagado: una relación perezosa fallaría fuera de la transacción. Las consultas son explícitas y se pueden explicar. Excepción: usuario → roles → permisos, eager y pequeño, porque se lee en cada petición | Relaciones JPA con carga perezosa |
| Entradas, eventos, versiones de informe, citas, semanas del mensual y pasos son `@Immutable` | RN-07 y RNF-10: el texto original no se actualiza ni se borra; la corrección va en una versión nueva. Hibernate ignora el cambio y una prueba lo comprueba | Confiar en que ningún servicio llame a `setBody` |
| Los permisos de base de datos (solo `INSERT` y `SELECT`) no se aplican aún | El diccionario lo deja para el endurecimiento del Momento 3 | Un usuario de base de datos distinto desde ya |
| Los estados y catálogos son `enum` de Java con `@Enumerated(STRING)` y los mismos códigos que los `CHECK` | Un valor nuevo exige tocar la migración y el código; la base es la última defensa | `ENUM` de PostgreSQL (más difícil de cambiar con una migración corriente) |
| `ddl-auto: validate` también en `dev` | Flyway es la única fuente del esquema; `update` dejaba a Hibernate modificarlo. Probado: si una columna no coincide, la app no arranca | Dejar `update` |
| IDs asignados por la aplicación y `Instant` para `TIMESTAMPTZ` | El diccionario los define sin valor por defecto, y la semilla usa UUID fijos | `@GeneratedValue`: impediría los UUID fijos de la semilla |
| `report_versions` exige `edited_by` cuando el origen es `ENFERMERIA` | El diccionario lo dice en prosa («obligatorio si origin = ENFERMERIA»); se pasó a un `CHECK` | Solo validarlo en el servidor |
| `V3` rellena `display_name` con el correo antes de volverla obligatoria | Si hubiera filas previas, la migración no fallaría. Hoy no hay usuarios | Columna con valor por defecto permanente |
| Las pruebas de esquema corren contra PostgreSQL real, no H2 | Los `CHECK`, `JSONB`, el índice parcial y `EXTRACT(ISODOW ...)` son de PostgreSQL | H2 en modo PostgreSQL: no garantiza lo mismo |
| Se agregan `bed`, `age` y `admitted_on` a `patients`, y no salen al proveedor | La lista y la ficha del mockup las muestran. Sintéticas y anulables | Quitarlas del mockup (no se rediseña) |

## E3 — Semilla (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Los datos viven en `seed/demo-data.json` y no en código Java | Se revisan a mano sin leer Java, y es la fuente de los casos dorados | Construirlos en una clase |
| Ids de entradas y eventos deterministas (`UUID.nameUUIDFromBytes`) | Idempotencia con `ON CONFLICT DO NOTHING` y los informes sembrados (E14) citan las mismas entradas sin una tabla de ids | UUID aleatorios: duplicarían datos al reiniciar |
| Inserción con `JdbcTemplate` en una transacción, no con las entidades | Es una carga masiva y las entidades son `@Immutable` con id asignado (`save` haría un `SELECT` por fila) | `save()` de Spring Data fila por fila |
| El perfil `prod` hace **fallar** el arranque si `APP_SEED_DEMO=true` | «Rechaza» (RF-05): ignorarlo en silencio ocultaría un error de configuración en producción | Ignorar la semilla en `prod` |
| Los eventos sembrados dicen solo lo que dice el texto | RN-11: intensidad solo si el texto la menciona, veces solo si las dice; una prueba automática lo revisa. La semilla es el patrón con el que se mide la estructuración | Intensidades «razonables» no escritas en el texto, como hace el mockup |
| Autor de cada entrada según el día de la semana (enfermería 5 de 7, médico 2 de 7); el evento lo confirma siempre una enfermera | D12 (el médico también registra) y el diccionario M2 §7.1 | Todas las entradas de una sola persona |
| P-033 conserva un único evento (`APETITO_BAJO`) | «Casi sin eventos» (D11): sirve para medir falsos positivos sin que la semana quede vacía de eventos | Cero eventos |
| P-040 lleva la instrucción en una nota que además trae un síntoma real (`VOMITO`) | Un agente que obedeciera la instrucción omitiría el vómito: el caso lo detecta | Una nota con solo la instrucción |

## E4 — Sesión y permisos (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| El usuario elegido viaja en la cabecera `X-Demo-User` (su UUID); en la ruta SSE `GET /api/runs/*/events` también vale el parámetro `demoUser` | `EventSource` no envía cabeceras (D24). El parámetro se limita a una ruta de solo lectura. No es un control de seguridad: cualquiera puede mandar el id de otra persona (D26) | `fetch` en streaming para poder mandar la cabecera: más código sin ganancia en el Momento 2; un parámetro en todo el API |
| Los permisos del rol se cargan como autoridades y los endpoints los exigen con `@PreAuthorize("hasAuthority('…')")` | La regla vive junto al endpoint y la matriz sale de la base, donde `V4` la fija | Preguntar el rol por nombre en cada servicio |
| 401 (no hay usuario válido) y 403 (hay usuario, falta permiso) son distintos, en JSON y en español | La interfaz debe distinguir «elija un usuario» de «su rol no puede» (RNF-04) | Una respuesta única de error |
| Solo cuenta como sesión un usuario activo, de demostración y con rol | Un usuario real que se agregue después no puede usarse por esta vía sin credenciales | Aceptar cualquier id existente |
| La matriz de la prueba está escrita a mano desde el diccionario y no se lee de la base | Si `V4` y el documento se desvían, la prueba falla; leerla de la base solo compararía la base consigo misma | Generar lo esperado desde la propia base |
| La ruta SSE se compara por `getRequestURI`, no por `servletPath` | `AntPathRequestMatcher` dependía de `servletPath`, que según el despliegue llega vacío; la prueba unitaria lo detectó | `AntPathRequestMatcher` de Spring Security |

## E5 — Frontend: sistema de diseño y shell (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Rutas con hash (`#/enf/pacientes`, `withHashLocation()`) | Son las del mockup (F4) y nginx sirve `index.html` sin configurar reglas para las rutas de la SPA | Rutas con historial del navegador: exigen reescrituras en nginx |
| El CSS se copia por rangos de líneas del mockup (8-194 y 317-481) y no se reescribe | F1: «se porta, no se rediseña»; cualquier diferencia visual sería un error de transcripción | Reescribirlo por componentes con estilos encapsulados |
| Los componentes que son un elemento del DOM (`svg`, `button`, `nav`) usan selector de atributo (`svg[appIcon]`, `button[appProcessButton]`, `nav[appSidebar]`) | El selector CSS del mockup depende de la estructura (`.btn > .icon`, `.sb`); un elemento envoltorio la rompería | Elementos personalizados `<app-icon>`, `<app-button>` |
| Los de contenido (`app-listing`, `app-empty-state`, `app-status-chip`) llevan `display: contents` | Los hijos siguen siendo hijos directos del contenedor para el CSS | Un `div` envoltorio |
| Sin Angular Material, Bootstrap, Tailwind, fuentes ni iconos externos | F2. Solo se usa lo que Angular ya trae (router, `HttpClient`) | — |
| La sesión se resuelve en `provideAppInitializer` antes de pintar | Evita parpadeo y peticiones sin usuario; si el servidor no responde, la pantalla lo dice con un aviso y no con un error | Resolverla en cada pantalla |
| El usuario elegido se guarda en `localStorage` (con `try/catch`) y viaja en `X-Demo-User` solo hacia `/api/` | D26. Sin almacenamiento, se pierde la preferencia y nada más | Cookie |
| «Ver como» ofrece solo las enfermeras y, al cambiar, vuelve a `#/enf/pacientes` | Tabla 3.3 (el médico aparece solo si se construye E12c); el mockup también vuelve al inicio del rol | Quedarse en la pantalla actual: los datos de la pantalla cambian de dueño |
| «Ver como» arranca en la primera enfermera por orden alfabético (Camilo Torres) | No hay otro criterio en la API; para arrancar como Marcela Rojas habría que ordenar de otra forma | Escribir un nombre en el código |
| La fecha de hoy la entrega el servidor (`today` en `/api/system/status`, America/Bogota) | F7 y RN-06: el navegador no decide en qué semana estamos | `new Date()` del navegador |
| `PendingComponent` marca las pantallas que aún no existen | Deja registradas las rutas del mockup y el menú navegable; cada etapa lo reemplaza. No es diseño final | Dejar las rutas sin registrar (todas darían «No encontramos lo que busca») |
| Sin `ChromeHeadless` con banderas especiales; `CHROME_BIN=/usr/bin/google-chrome` en esta máquina | Es lo que usa el CI sin cambios | Configurar un lanzador personalizado |

## Decisiones del equipo (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| D26: la app abre sin inicio de sesión y se elige con qué usuario de demostración sembrado se trabaja (confirmada) | Mantiene la separación de funciones (solo enfermería aprueba, el médico solo ve lo aprobado) y un autor por acción mientras la autenticación espera al Momento 3. No es un control de seguridad | Sin usuarios en el Momento 2; inicio de sesión mínimo con contraseña |
| No se construyen fallos forzables en vivo (`LLM_FAULT`) | Decisión del equipo: lo que se necesita ahora es el módulo de enfermería. Los tres fallos (tiempo agotado, salida inválida, 429 o caída) se prueban con pruebas automáticas y dobles de prueba. **Riesgo declarado:** `AGENTS.md` y OBJ-10 piden poder forzarlos en la demo; sin ellos solo se pueden mostrar las pruebas, no el fallo en vivo | Variables de entorno de fallo inyectado dentro de la capa de proveedor |
| Vista del médico (E12c) fuera por ahora | Decisión del equipo. Consecuencia: la puerta de la SPEC §6 no se cumple completa; el servidor ya niega al médico aprobar y ver informes que no sean `APROBADO`, y se prueba con pruebas automáticas | Promover E12c al núcleo |
| Una sola sesión, sin frentes paralelos | Decisión del equipo. Orden: E2 a E12b, E14 y E15; E13 al final si sobra tiempo; E16 y «texto corregido» fuera | Dos frentes (A y B) |

**Pendiente de redactar en etapas posteriores:** proveedor y modelo (E8), por qué asíncrono con SSE y por qué la estructuración es síncrona (E9, E11), sin RAG (E15), Java 17 y por qué Spring Boot y Angular (E15), rutas con hash (E5).
