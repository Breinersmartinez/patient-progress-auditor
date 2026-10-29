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

## E6 — Pacientes y entradas (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| El alias del paciente es único sin distinguir mayúsculas, con `V5` (`uq_patients_alias`) y una comprobación previa en el servicio | El alias es el código con el que se busca y se cita (P-014): dos iguales confundirían las fichas. La comprobación da el mensaje claro y el índice gana la carrera si dos altas coinciden | Editar `V3`: ya se aplicó en las bases de desarrollo y Flyway rechazaría el cambio de suma de comprobación; ya se había dicho que `V3` evitaba una `V5`, pero este punto no estaba en el diccionario |
| `POST /api/patients/{id}/entries` guarda el texto **sin tocarlo** (ni recorte, ni normalización) y la entrada queda `SIN_ESTRUCTURAR`; la propuesta de eventos y su confirmación llegan en E9 | RF-02 y RN-07: el texto original es la fuente citable. Sin eventos confirmados es lo que RF-41 llama «sin estructurar» | Recortar espacios o normalizar saltos de línea: ya no sería «tal como se escribió» |
| Topes de la entrada: 4000 caracteres, sin el carácter nulo, fecha de hoy o anterior (la fecha la da el servidor, America/Bogota) | La fecha máxima «hoy» es la propuesta de la tabla 3.3. El nulo lo rechaza PostgreSQL con un error crudo; se explica antes. 4000 es una propuesta, sin dato del equipo | Sin tope de texto; fecha libre |
| Listado de un paciente: `from` y `to` incluyen sus dos extremos, `order=asc` (cronológico) por defecto o `desc`, `limit` de 1 a 500; el mismo día va por hora de creación (RN-09) y, si empatan, por id | RF-04 pide cronológico; las pantallas piden lo más reciente primero y los siete últimos. El `id` hace estable el orden | Ordenar en el navegador |
| `GET /api/entries` (historial) sale con el orden del mockup: fecha descendente y, el mismo día, por código de paciente; hasta 500 | El historial del mockup ordena así. Con 119 entradas sembradas cabe entera; la búsqueda sigue siendo del navegador, como en `listado()` | Paginar: no hace falta con este volumen |
| El «último informe» de la lista y la ficha lo decide el servidor según el rol: enfermería ve cualquier estado; el médico, solo `APROBADO` (`ReportVisibility`) | RN-01 y RNF-01: lo que ve el médico no puede depender de la pantalla. Aunque E12c no se construya, el endpoint ya lo respeta y se prueba | Filtrarlo en el frontend |
| Todo error de la API es `{ message, fields? }`, sin rastro de pila; una ruta que no existe responde 404 con «No encontramos lo que busca.» y un fallo no previsto, 500 con un mensaje genérico (se registra en el servidor) | RNF-04. `fields` permite mostrar el error junto al campo, como el mockup | Dejar la página de error de Spring: con `include-message: always` filtra texto del framework |
| Las pantallas usan el id interno (UUID) en la ruta: `#/enf/pacientes/ficha/<uuid>` | La API trabaja por id; el alias puede cambiar de forma o tener caracteres que escapar | Alias en la ruta como el mockup |
| El alta vuelve a la lista con «Paciente creado», como `vUsuForm` | Es el patrón del mockup (3.4) | Abrir la ficha recién creada |
| Microcopy nuevo (no sale del mockup): «Pacientes registrados en el servicio.», «Código del paciente», «Crear paciente», «Sin dato», «Sin estructurar: solo se guardó el texto.» y «Sin estructurar» | El mockup no tiene alta de paciente ni entradas sin estructurar. «Sin dato» evita mostrar «null años» y «Ninguno» afirmaría que no hubo síntomas cuando solo no se estructuró | Dejar la celda vacía |
| En el historial, la fecha de cada fila lleva `white-space: nowrap` dentro de la celda | Hallazgo de F8: con los textos sembrados, más largos que los del mockup, la tabla de ancho automático partía «dom 25» en dos líneas. No se toca el CSS copiado; es un estilo local de la celda | Cambiar `.tabla` en `styles.css` (rompe F1) |
| `PATIENTS_UPDATE` y `ENTRIES_UPDATE` no se usan todavía | No hay editar paciente ni corregir entrada en el núcleo (RF-03 es M2-S) | — |

## E7 — Métricas por semana (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Definiciones de las clases de síntoma, frente a la semana anterior W-1: **persistente** (en W y W-1), **desaparecido** (en W-1 y no en W), **nuevo** (en W, no en W-1 y nunca antes) y **reaparecido** (en W, no en W-1 y sí en alguna semana anterior). Un síntoma «está» en una semana si tiene un evento confirmado en algún día de ella | Ningún documento las definía (era el riesgo de E7). Separan lo que el equipo quiere ver, el síntoma que vuelve tras semanas sin él (OBJ-02), de uno que aparece por primera vez. **Propuesta: el equipo debe confirmarlas.** | Contar «reaparecido» por días sueltos sin síntoma: con un solo día libre casi todo sería «reaparecido» |
| Si la semana anterior no tiene entradas, la comparación es `comparable = false` y las cuatro listas quedan vacías | Sin entradas no se sabe si el síntoma «dejó de estar» o solo no se registró (RN-08). Inventar un «desaparecido» sería rellenar | Tratar la semana vacía como «sin síntomas» |
| Las entradas `SIN_ESTRUCTURAR` se cuentan aparte (también las de la semana anterior) y no cuentan como «sin síntomas» | RN-08 y RF-41: el informe debe declararlas. Sus síntomas son desconocidos, no ausentes | Ignorarlas |
| Por síntoma: `days` (días distintos), `events` (uno por entrada y síntoma), `occurrences` (suma de las veces indicadas; `null` si nadie las indicó) y `maxIntensity` (la mayor indicada; `null` si ninguna) | Dar al informe lo que puede afirmar sin inventar: un `null` es «no indicado», no cero | Rellenar con 0 o con `SIN_INDICAR` |
| El cálculo es una función pura (`MetricsCalculator`) y la lectura de datos va aparte (`MetricsService`) | Se prueba con casos hechos a mano sin base de datos y el agente y los informes comparten un único cálculo (RN-04) | Mezclar consultas y cálculo |
| No hay endpoint HTTP de métricas en E7 | Ningún requisito lo pide: lo usan el agente (E10) y los informes (E12). Menos superficie | Exponerlo «por si acaso» |
| El cálculo de referencia está hecho en Python (`scripts/expected_metrics.py`), con otro método, y su salida es el fixture de la prueba | Si la interpretación de una regla estuviera mal en Java, estaría igual de mal en una prueba escrita por la misma persona y el mismo método. La prueba compara las 18 semanas-paciente; se comprobó que falla si se rompe la regla de «reaparecido» | Escribir las cifras esperadas a mano en la prueba; calcular el esperado con el mismo código |
| Las fechas son `DATE` de la propia entrada: no hay conversión de zona horaria en las métricas | Evita el riesgo de límites de semana que el plan señalaba: la zona solo interviene al decidir «hoy» | Convertir `created_at` a fecha |

## E8 — Capa de proveedor del LLM (2026-10-28)

**Proveedor y modelo (D15).** Principal **Groq**, respaldo **Gemini** (el equipo invirtió el orden propuesto el 2026-10-28). Todo medido ese día con las llaves del equipo, salvo lo que se marca «no verificado».

| | Groq (principal) | Gemini (respaldo) |
|---|---|---|
| Modelo del agente | `openai/gpt-oss-120b` | `gemini-3.5-flash-lite` |
| Modelo de estructuración | `openai/gpt-oss-120b`, razonamiento medio | `gemini-3.5-flash-lite` |
| Ventana de contexto | 131 072 tokens (documentación) | 1 048 576 tokens (API de modelos) |
| Herramientas y JSON con esquema | Sí, con esquema estricto (medido) | Sí, con JSON Schema estándar (medido) |
| Latencia medida | 0,5 a 1,4 s por llamada corta | 0,7 s en una llamada trivial; 4 a 8 s con el ejemplo de estructuración |
| Precio de lista, USD por millón de tokens (entrada/salida) | 0,15 / 0,60 | 0,30 / 2,50 |
| Límites gratuitos | **30 solicitudes/min, 1 000/día, 8 000 tokens/min, 200 000 tokens/día**, por modelo (documentación y encabezados) | **No verificados**: Google no los publica por modelo; solo se ven en la consola de AI Studio |

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| Estructurar con `gpt-oss-120b` y razonamiento medio | Con el ejemplo del prompt acierta 14 de 14 llamadas (5 y 9); con razonamiento bajo falla 0 de 5 (omite «comió poco» o hasta el dolor de cabeza) y con el modelo de 20B falla 4 de 5 en medio y 3 de 9 en alto. Es una muestra chica, no la evaluación de E14, que la repetirá con los casos dorados | `gpt-oss-20b` (otra cuota y más barato, pero impreciso); razonamiento bajo (más rápido, pero omite síntomas) |
| El agente y la estructuración comparten la cuota de `gpt-oss-120b` (8 000 tokens por minuto) | No hay un modelo mejor y más barato que sirva. Riesgo declarado: un informe en curso y un registro a la vez compiten por los tokens; el límite de 8 000 obliga a que cada llamada del bucle del agente sea pequeña (E10), y un 429 largo conmuta a Gemini | Repartir en dos modelos de Groq |
| `gemini-3.5-flash-lite` como respaldo, no `gemini-3.8-flash` ni `gemini-3.5-flash` | Medido: `3.8-flash` dio un 503 y un tiempo agotado a los 40 s; `3.5-flash` tardó 22 s en responder «ok»; `3.5-flash-lite` responde en menos de un segundo y llama bien a las herramientas | El modelo más nuevo |
| Llamadas por HTTP con el cliente de la JDK (`java.net.http`), sin SDK ni `RestClient` | El prompt sugería `RestClient`; la JDK da un tiempo máximo por llamada y acceso a los encabezados (`Retry-After`) con menos código, sin dependencias nuevas. Ningún SDK de proveedor entra a la aplicación | SDK de Groq o de Google: dependencia nueva y acoplamiento |
| Un solo JSON Schema estándar para los dos proveedores (`response_format` estricto en Groq; `responseJsonSchema` y `parametersJsonSchema` en Gemini) | Los dos lo aceptan (medido) y el esquema se escribe una vez. Con `json_object` suelto Groq devolvió «dolor de cabeza» y «intensa», fuera del catálogo: no se usa | Un esquema por proveedor |
| Reintentos de 1, 2 y 4 s por proveedor ante 429, caída o tiempo agotado, y luego conmutación. Si el proveedor pide esperar más de 8 s (`Retry-After`), no se espera: se conmuta. Una llave rechazada, una solicitud inválida o una salida inválida del proveedor conmutan sin reintentar | Reintentar lo que no puede arreglarse esperando solo demora. El tope de 8 s evita que la persona espere un minuto por la cuota por minuto de Groq cuando Gemini está disponible | Esperar siempre lo que pida el proveedor |
| La conmutación es por llamada: un bucle puede empezar en Groq y seguir en Gemini | El historial es neutral al proveedor. Gemini rechaza con 400 una llamada a herramienta sin firma de pensamiento; para las que hizo Groq se usa el valor documentado `skip_thought_signature_validator` (medido: sin él, 400; con él, funciona) | Reiniciar el bucle al conmutar: pierde el trabajo hecho |
| La salida estructurada se valida con una función de quien llama; si no valida hay UN reintento devolviéndole el error al modelo y, si vuelve a fallar, `InvalidOutputException` con el último texto para degradar a texto plano (RF-18) | Nunca confiar en la forma. La degradación y su registro son de E9 y E10, que conocen la tarea | Reintentar sin límite |
| El mensaje a la persona es de una lista fija por clase de fallo y nunca lleva el cuerpo del proveedor, una llave ni una traza; el detalle técnico va al registro y a la traza (`Attempt.detail`) | RNF-04. Una prueba verifica que un cuerpo con «stack trace» y una llave no llegan al mensaje | Mostrar el mensaje del proveedor |
| El costo que se registra es el precio de lista de la llamada, aunque la capa gratuita no cobre; un modelo sin precio conocido da `null`, no cero | Permite comparar el costo por tarea contra la solución convencional (E14) con una cifra honesta | Registrar 0 |
| Sin llave la aplicación arranca, `GET /api/llm/status` lo dice y una llamada falla con «falta la llave» en vez de un error crudo | D22 y RNF-13. El endpoint no devuelve llaves | Fallar al arrancar |
| `GOOGLE_API_KEY` no se acepta como nombre alternativo | La variable documentada es `GEMINI_API_KEY`; aceptar otra sin documentarla sería una variable oculta. El `.env` del equipo todavía usa `GOOGLE_API_KEY` y hay que renombrarla | Leer las dos |

**Cómo cambiar de proveedor (RF-20).** Editar `LLM_PROVIDER` y `LLM_FALLBACK` en `.env` (por ejemplo `gemini` y `groq`) y ejecutar `docker compose up -d backend`. No hay que reconstruir. Comprobado reiniciando el backend con las dos combinaciones: `GET /api/llm/status` muestra el orden. Con `LLM_FALLBACK=none` no hay respaldo.

**Dato para gobernanza.** A los proveedores sale el texto de la entrada o las métricas del paciente y su id interno, nunca el alias, la cama, la edad ni el ingreso (RNF-02). La página de precios de Google dice que el contenido enviado en la capa gratuita puede usarse para mejorar sus productos; los datos son sintéticos. La política de retención de Groq: no verificada.

**Fallos.** Se prueban con pruebas automáticas, no en vivo (decisión del equipo): un servidor HTTP local reproduce un 429 con `Retry-After`, un 503, un tiempo agotado real, una llave rechazada y una respuesta ilegible; la pasarela se prueba con un reloj falso. Las llamadas reales (`LlmLiveTest`, solo con `RUN_LIVE_LLM=true`) comprobaron la conmutación con una llave de Groq inválida y que Gemini continúa una conversación que empezó Groq.

## E9 — Estructuración al registrar (2026-10-28)

**Decididas por el equipo el 28-oct (D27):** intensidades las cuatro del mockup (leve, moderado, intenso y sin indicar); botón «Aprobar y guardar», como el mockup, aunque se parezca a la aprobación del informe (RN-02); catálogo cerrado de nueve síntomas (D18); «Descartar» cierra la vista previa y no guarda nada; el «texto corregido» (RF-42) queda fuera.

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| **La estructuración es síncrona (una llamada corta en la misma petición) y el agente de informes es asíncrono con SSE (D24).** Respuesta para el profesor | La estructuración es una sola llamada, sin herramientas ni bucle, de 1 a 2 s con Groq (medido: 1,1 s y unos 1 100 tokens); la persona está mirando la pantalla «Así quedaría el registro» y espera el resultado para confirmarlo. El informe es un bucle de varias llamadas y herramientas que puede tardar decenas de segundos y gastar la cuota (8 000 tokens por minuto en Groq), así que corre en segundo plano y la pantalla recibe el progreso real. El estado visible es el botón «Enviando» y la burbuja del texto | Todo asíncrono: exige una cola y una pantalla de espera para algo que tarda un segundo. Todo síncrono: un informe bloquearía una petición HTTP el tiempo que dure el bucle (prohibido en la sustentación) |
| La petición síncrona tiene un **presupuesto total de 30 s** (`llm.structuring-budget`) y 15 s por llamada: ningún reintento, espera ni respaldo pasa de ahí, y cada llamada se acorta a lo que queda | Sin tope, 4 intentos de 15 s en cada proveedor y sus esperas podían pasar de dos minutos. Con él, la persona recibe a lo sumo un mensaje honesto a los 30 s | Solo el tiempo por llamada |
| Proponer no guarda nada; confirmar es el mismo `POST /api/patients/{id}/entries` con `events` opcionales. `events` ausente = «guardar solo el texto» (`SIN_ESTRUCTURAR`); una lista, aunque vacía, = lo que la persona confirmó (`ESTRUCTURADA`) | RN-11: ningún evento se guarda sin confirmación. La entrada y sus eventos van en una sola transacción (se comprobó que ante un evento inválido no queda ni la entrada). Una lista vacía confirmada es «sin síntomas», distinta de «no se pudo estructurar» | Dos endpoints de guardado; guardar lo propuesto y confirmar después |
| Si la IA falla, `POST .../entries/propose` responde **200** con `failure` y sin eventos | No es un error de la petición: el flujo sigue (guardar solo el texto o agregar a mano, RF-41). La pantalla no debe tratar un fallo de la IA como una excepción | 502 o 503: obligaría a la pantalla a distinguir errores |
| El origen de cada evento (`IA`, `EDITADO`, `MANUAL`) lo calcula la pantalla comparando con lo propuesto y lo envía; el servidor solo lo valida | Es informativo, no un control de seguridad (D26): lo que garantiza RN-11 es que el evento solo existe si llega por la confirmación, con `confirmed_by` del usuario elegido | Guardar la propuesta en el servidor y compararla: estado y expiración sin ganancia |
| Al proveedor sale **solo el texto de la nota**, entre etiquetas `<registro>`; no el alias, la cama, la edad ni el id del paciente (RNF-02). Una prueba lo comprueba | La estructuración no necesita saber de quién es la nota | Mandar el id interno: no sirve de nada al modelo |
| El texto de la nota es dato: el prompt lo dice, va entre etiquetas y se neutraliza una etiqueta de cierre escrita dentro de la nota | Defensa contra la inyección indirecta (la nota sembrada en P-040 trae una orden). Con el modelo real (30 llamadas, 2 proveedores) el síntoma real se propone y la orden no cambia nada | Confiar solo en la instrucción del prompt |
| Dos barreras sobre la salida: el esquema estricto del proveedor y un validador propio (catálogo, intensidad, veces de 1 a 20, sin repetidos) con un reintento corregido | Groq y Gemini no aplican el esquema igual. Si falla dos veces, no se inventa: se avisa y la persona guarda el texto o agrega a mano | Confiar en el esquema del proveedor |
| La traza `ESTRUCTURACION` se guarda en `agent_runs` y `agent_steps` (proveedor, modelo, tokens, costo, intentos, pasos `LLAMADA_LLM`, `FALLBACK`, `VALIDACION`, `ERROR`); un fallo al guardarla se registra pero no tumba la entrada. **Su lectura por API llega en E11** | RF-22. La traza es del equipo; una fila de traza rota no debe impedir que una enfermera registre | Mostrarla en una pantalla: opcional según el prompt |
| Sin «texto corregido»: la tarjeta no trae el campo y la ayuda dice «La IA detecta los síntomas; usted revisa y aprueba antes de guardar.» (el mockup decía «corrige la redacción y detecta…») | Lo que el mockup afirma ya no sería verdad. Cambia el microcopy de la tabla 3.3 | Dejar la frase del mockup |
| La caja de texto se vacía al enviar leyendo y limpiando el propio elemento (como el mockup) y la burbuja fija el texto que se guarda | Hallazgo de F8: con un enlace `[value]` el campo no se limpiaba si se escribía y se enviaba sin que Angular actualizara entre medias. Y fijar el texto en la burbuja evita guardar eventos de un texto distinto (riesgo de E9) | Releer la caja al guardar |
| En la vista previa, tras un fallo, «Aprobar y guardar» solo aparece si hay síntomas agregados; «Guardar solo el texto» envía sin eventos | Sin síntomas confirmados no hay nada que «aprobar»: guardaría «sin síntomas», que no es lo que la persona decidió | Mostrar siempre los dos |
| Microcopy nuevo: «Registro guardado solo con el texto; quedó sin estructurar.» y los mensajes de la tabla 3.4 | El mockup no tiene fallo de la IA | — |
| El campo del síntoma sigue siendo el de texto con lista de sugerencias del mockup, pero solo se acepta uno de los nueve («Elija un síntoma de la lista.») | Tabla 3.3 y D18: el catálogo es cerrado | Crear síntomas nuevos al vuelo, como el mockup |

## Decisiones del equipo (2026-10-28)

| Decisión | Razón | Alternativa descartada |
|---|---|---|
| D26: la app abre sin inicio de sesión y se elige con qué usuario de demostración sembrado se trabaja (confirmada) | Mantiene la separación de funciones (solo enfermería aprueba, el médico solo ve lo aprobado) y un autor por acción mientras la autenticación espera al Momento 3. No es un control de seguridad | Sin usuarios en el Momento 2; inicio de sesión mínimo con contraseña |
| No se construyen fallos forzables en vivo (`LLM_FAULT`) | Decisión del equipo: lo que se necesita ahora es el módulo de enfermería. Los tres fallos (tiempo agotado, salida inválida, 429 o caída) se prueban con pruebas automáticas y dobles de prueba. **Riesgo declarado:** `AGENTS.md` y OBJ-10 piden poder forzarlos en la demo; sin ellos solo se pueden mostrar las pruebas, no el fallo en vivo | Variables de entorno de fallo inyectado dentro de la capa de proveedor |
| Vista del médico (E12c) fuera por ahora | Decisión del equipo. Consecuencia: la puerta de la SPEC §6 no se cumple completa; el servidor ya niega al médico aprobar y ver informes que no sean `APROBADO`, y se prueba con pruebas automáticas | Promover E12c al núcleo |
| Una sola sesión, sin frentes paralelos | Decisión del equipo. Orden: E2 a E12b, E14 y E15; E13 al final si sobra tiempo; E16 y «texto corregido» fuera | Dos frentes (A y B) |
| Groq es el proveedor principal del LLM y Gemini (Google AI Studio) el de respaldo (D15) | Decisión del equipo el 2026-10-28, que invierte la propuesta inicial. La razón de fondo se escribe en E8 con latencia, costo, herramientas y ventana de contexto medidos. **Riesgo declarado:** los límites gratuitos de Groq por minuto son más estrechos que los de Gemini; el bucle del agente es lo que más tokens gasta, así que se mide en E8 y se ajustan las salidas de las herramientas | Gemini principal y Groq respaldo (propuesta original) |

**Pendiente de redactar en etapas posteriores:** por qué asíncrono con SSE y por qué la estructuración es síncrona (E9, E11), sin RAG (E15), Java 17 y por qué Spring Boot y Angular (E15), rutas con hash (E5).
