# Seguridad — Patient Progress Auditor

| Campo | Valor |
|---|---|
| Versión | 1.0 |
| Fecha | 2026-11-10 |
| Alcance | Backend Spring Boot, frontend Angular, Docker Compose, agente/LLM y CI |
| Estado | Momento 2 cerrado; piezas 1 a 5 del Momento 3 construidas y pieza 6 en curso |
| Clasificación | Documento público; no contiene llaves ni datos reales |

> **Advertencia.** Este documento describe una aplicación docente con datos sintéticos. No es un dispositivo médico, no diagnostica, no prescribe y no sustituye el criterio profesional. **No debe usarse con datos reales de pacientes ni exponerse a Internet en su estado actual.** Las limitaciones de la sección 10 son requisitos para un uso real, no advertencias menores.

El repositorio es público por diseño. Los identificadores de los usuarios de demostración y sus roles son públicos; ninguna llave real, contraseña real ni dato real de paciente debe entrar al repositorio ni a su historial.

## 1. Alcance

Cubre:

- Autenticación de demostración, autorización por permisos y aislamiento de informes, notificaciones y consultas.
- Flujo de aprobación y envío de informes, incluido el mensual.
- Agente de IA: herramientas, límites, validaciones, tratamiento de inyección de instrucciones y proveedores externos.
- Entradas, dictado por voz, salida a PDF y errores de la API.
- Secretos, configuración, cadena de suministro y despliegue con Docker Compose.
- Pruebas automatizadas y controles de CI relacionados con seguridad.

No cubre:

- La infraestructura de Groq, Google Gemini, Langfuse, Docker, GitHub ni el proveedor de nube.
- El sistema operativo, el navegador, la red local, el host de Docker ni el respaldo físico.
- Cumplimiento normativo de datos de salud (por ejemplo, habeas data, HIPAA o equivalentes). Ese análisis es previo a cualquier uso real.
- Seguridad clínica del contenido de los informes. El producto es apoyo documental, no un dispositivo médico.

## 2. Arquitectura y fronteras de confianza

```text
Navegador
   │  HTTP (sin TLS en este compose)
   ▼
Frontend nginx :4200  ──────────────┐
   │  HTTP por red interna de Compose│
   ▼                                 │
Backend Spring Boot :8080            │  HTTPS saliente y fijo
   │                                 ├──► Groq (LLM y Whisper)
   ├──► PostgreSQL 16 (red interna)  ├──► Google Gemini (respaldo)
   │                                 └──► Langfuse Cloud (opcional, solo metadatos)
   ▼
Volumen pgdata
```

Fronteras y datos que las cruzan:

| Frontera | Qué cruza | Protección actual |
|---|---|---|
| Navegador ↔ nginx | Texto de registros, informes, cabecera `X-Demo-User`, audio de dictado | HTTP sin TLS; sin cabeceras de seguridad; `X-Demo-User` no es autenticación |
| nginx ↔ backend | Las mismas peticiones; el audio se reenvía sin búfer en disco | Red interna de Compose; límite de 5 MB en la ruta de dictado |
| Backend ↔ PostgreSQL | Datos clínicos sintéticos, usuarios, permisos, trazas | La base no publica puertos; la cadena JDBC no exige TLS explícito |
| Backend ↔ Groq/Gemini | Texto de la nota o de los registros; audio en el dictado; llaves en cabecera | HTTPS; URLs fijas en código; minimización de identificadores |
| Backend ↔ Langfuse | Solo metadatos de ejecución, tokens y costo | HTTPS y opcional por llaves; no salen prompts ni respuestas |
| Operador ↔ `.env`/Docker | Llaves, contraseña de base, variables de límites | `.env` fuera de git; el acceso al daemon de Docker equivale a acceso a esos secretos |

## 3. Activos

| Activo | Por qué importa | Dónde vive |
|---|---|---|
| Registros de evolución | Contienen texto libre que puede describir salud; son la fuente citable | PostgreSQL, `entries` y `entry_versions` |
| Eventos confirmados | Síntomas, intensidad y veces; base de las métricas | PostgreSQL, `entry_events` |
| Informes y versiones | Contienen afirmaciones, citas y decisiones de enfermería | PostgreSQL, `reports` y `report_versions` |
| Estado de aprobación y envío | Determina qué ve el médico y quién respondió | PostgreSQL, campos de estado, `reviewed_by`, `sent_by` |
| Trazas del agente | Muestran herramientas, parámetros, resultados y errores; pueden incluir texto clínico | PostgreSQL, `agent_runs` y `agent_steps` |
| Notificaciones | Revelan actividad por paciente aunque no lleven texto clínico | PostgreSQL, `notifications` |
| Consultas del médico | Contenido derivado, solo para su autor | PostgreSQL, `doctor_consultations` |
| Identidad y permisos de demostración | Determinan qué acciones permite el servidor | PostgreSQL, tablas RBAC; cabecera `X-Demo-User` |
| Llaves de proveedores y base | Permiten gastar cuota, leer la base o enviar datos a terceros | Variables de entorno y `.env` |
| Disponibilidad del servicio | Una ejecución costosa o un cuerpo enorme puede agotar recursos | Backend, cola de ejecuciones y proveedor |

## 4. Actores y capacidades

| Actor | Capacidades actuales | Límite |
|---|---|---|
| Enfermería (usuario demo) | Ver y crear pacientes y registros; pedir, editar, aprobar y enviar informes; ver todos los estados | No lee notificaciones ni pide consultas; no administra usuarios |
| Médico (usuario demo) | Ver pacientes y registros; crear registros; ver solo informes `ENVIADO`; leer sus notificaciones; pedir consultas | No aprueba, no envía, no ve borradores ni el historial de versiones |
| Agente de IA | Leer registros y métricas; guardar un borrador | No aprueba, no envía y no inicia conexiones arbitrarias; no tiene correo ni mensajería |
| Operador | Configurar variables, llaves, base y despliegue | Acceso al daemon de Docker y a `.env` |
| Atacante externo | Alcanza los puertos publicados; puede intentar suplantar usuarios demo | No hay autenticación real que lo detenga en el Momento 2 |
| Proveedores externos | Reciben el texto o el audio enviado | Su retención y tratamiento se rigen por sus términos |

Supuestos del Momento 2:

- Despliegue local o en una red de confianza, con datos sintéticos.
- Todos los usuarios de enfermería comparten acceso a todos los pacientes; todos los médicos ven los registros de todos los pacientes y los informes enviados. No hay asignación paciente-profesional.
- Un solo inquilino y una sola instancia.
- No hay requisito de disponibilidad ni de recuperación ante desastres.
- Si cualquiera de estos supuestos deja de cumplirse, se aplican las medidas de la sección 11 antes de continuar.

## 5. Modelo de amenazas

Severidad: **Crítica** bloquea uso real o compromete directamente; **Alta** es explotable con impacto probable; **Media** requiere condiciones o afecta defensa en profundidad; **Baja** es higiene.

| ID | Amenaza | Impacto | Control actual | Residual |
|---|---|---|---|---|
| S-1 | Suplantar a enfermería o al médico enviando el UUID de otro usuario en `X-Demo-User` | Acceso total al rol suplantado | El filtro exige usuario activo, demo y con rol; no es autenticación | **Crítica y aceptada solo para la demo.** Cualquiera con acceso a la red puede hacerlo |
| S-2 | Robar llaves de `.env` o del entorno de Docker | Gasto de cuota, acceso a la base o envío de datos a terceros | `.env` ignorado por git; Gitleaks; sin llaves en imágenes | **Alta.** El acceso al host o a Docker equivale a las llaves |
| T-1 | Aprobar o enviar un informe sin ser enfermería | Un borrador llega al médico sin supervisión | Permisos `REPORTS_APPROVE` y `REPORTS_SEND`; transiciones en servidor; el agente no puede aprobar | Baja |
| T-2 | Cambiar directamente filas de la base | Alterar estados, versiones o registros sin rastro | Credenciales de base; la base no se publica | **Alta** si se compromete el host o la contraseña |
| T-3 | Inyectar instrucciones en una nota para alterar el borrador | Contenido no deseado en el informe | Prompt trata la nota como dato; etiquetas neutralizadas; validadores; confirmación humana; pruebas de inyección | **Media.** La defensa principal es la revisión humana y la validación, no la obediencia del modelo |
| R-1 | Negar una acción: quién aprobó, editó o registró | Sin evidencia completa para auditar | Campos de autor, `reviewed_by`/`sent_by`, versiones y `agent_steps` | **Alta.** `audit_logs` existe pero ningún código la escribe; no hay registro append-only |
| I-1 | Que el médico vea un borrador o un informe no enviado | Rompe la regla de supervisión | `ReportVisibility` limita al médico a `ENVIADO`; lo no visible responde 404 | Baja |
| I-2 | Acceder a un informe, notificación o consulta de otro | Divulgación de datos | Permisos de rol; notificaciones filtradas por destinatario; consultas por `doctor_id`; 404 en lugar de 403 | **Media.** Enfermería ve todos los informes; no hay separación por paciente o autor |
| I-3 | Leer trazas de una ejecución con texto clínico | Divulgación por la traza | La ruta exige `REPORTS_REQUEST`; la traza se lee desde el informe | **Media.** Cualquier enfermería puede leer cualquier ejecución; no hay control por autor |
| I-4 | Filtrar detalles internos en un error | Divulgación de rutas, SQL, respuestas del proveedor | `ApiExceptionHandler` devuelve mensajes propios; `LlmException.getMessage()` está sanitizado; `detail` no sale al cliente | **Baja.** `server.error.include-message: always` sigue activo; revisar antes de producción |
| I-5 | Enviar más datos de los necesarios al LLM | Divulgación a terceros | El agente no recibe nombre, cama ni id; la estructuración envía solo la nota; Langfuse solo metadatos | **Media.** El texto clínico sí sale a Groq/Gemini; la retención depende del proveedor |
| I-6 | Ver llaves en logs, historial o imágenes | Compromiso de credenciales | Sin llaves en git; Gitleaks; `.env` ignorado; logs no imprimen la llave | **Media.** `.env` puede quedar legible por otros usuarios del host; los logs técnicos deben tratarse como sensibles |
| D-1 | Agotar la cuota del proveedor con ejecuciones repetidas | Informes fallidos y costo | Límites por ejecución; una ejecución viva por paciente y periodo; respaldo Gemini | **Alta.** No hay límite global por usuario, IP ni ventana de tiempo |
| D-2 | Enviar cuerpos enormes o audio gigante | Consumo de memoria o denegación | Texto limitado a 4000 caracteres; audio a 5 MB y en memoria; nginx con el mismo tope | **Media.** No hay límite global explícito para JSON; Tomcat y la JVM son el último tope |
| D-3 | Pedir informes costosos en bucle | Gasto y bloqueo de la cola | `BusyException` con `Retry-After` para una ejecución viva del mismo paciente y periodo | **Alta.** No hay cuota por usuario ni rate limiting |
| E-1 | Escalar privilegios por un endpoint sin `@PreAuthorize` | Acción no autorizada | Autorización a nivel de método en los controladores; regla general `authenticated()` para `/api/**` | **Baja.** Requiere revisión continua al agregar endpoints |
| E-2 | Que el agente escriba algo distinto de un borrador | Alterar el flujo o aprobar | Solo `save_draft` escribe; crea `PENDIENTE_REVISION`; no hay herramienta de aprobación ni de envío | Baja |
| E-3 | Saltar la interfaz para llamar la API | Acciones no previstas | La autorización está en el servidor; el guard de Angular no es una barrera | Baja |
| SC-1 | Dependencia o imagen comprometida | Ejecución de código o fuga | CodeQL, Dependency Review, Gitleaks, acciones fijadas por SHA, `npm ci` con lockfile | **Media.** Sin escaneo de imágenes, SBOM, firma ni Dependabot; imágenes base por etiqueta, no por digest |

Riesgos específicos de IA, además de T-3:

- **Agencia excesiva:** mitigada por herramientas atómicas, sin salida y con una sola escritura.
- **Salida no confiable:** esquema, segunda validación en código, reintento con el error y degradación a texto plano.
- **Cifras inventadas:** las métricas las calcula el código y el validador exige que cada número esté respaldado.
- **Citas inventadas:** el validador exige alias de registros reales del mismo paciente y no posteriores al periodo.
- **Diagnóstico o tratamiento:** `DiagnosisGuard` bloquea el vocabulario prohibido y la solicitud se declara fuera de alcance.
- **Exfiltración por la salida:** el texto se muestra con interpolación de Angular, no como HTML ni Markdown; no hay enlaces ni imágenes generadas por el modelo.

## 6. Tríada letal

| Pata | ¿Está presente? | Detalle |
|---|---|---|
| Datos privados | **Sí** | Registros e informes. Hoy son sintéticos; con datos reales la exposición sería crítica |
| Contenido no confiable | **Sí** | El texto libre de las notas puede contener instrucciones dirigidas al modelo |
| Herramientas de salida | **No** | El agente no tiene correo, HTTP arbitrario ni mensajería. Solo lee la base y guarda un borrador; las únicas conexiones salientes son las llamadas fijas al proveedor del LLM |

La tercera pata se mantiene cerrada por arquitectura. El modelo tampoco elige la URL del proveedor: las direcciones de Groq y Gemini son fijas en código. Si en el futuro se agrega una herramienta de salida, la tríada queda completa y el diseño debe revisarse antes de implementarla.

La inyección indirecta sigue siendo posible, pero su efecto esperado se limita a proponer contenido incorrecto o ruido en el borrador. No puede ejecutar acciones, aprobar, enviar ni consultar recursos fuera del contexto del paciente. La persona que registra confirma los eventos y enfermería revisa el informe antes de aprobarlo.

## 7. Controles implementados

### 7.1 Identidad de demostración

- `DemoUserFilter` toma `X-Demo-User` (UUID) y solo acepta un usuario **activo**, **demo** y con al menos un rol.
- En la ruta SSE `GET /api/runs/*/events` acepta `?demoUser=` porque `EventSource` no permite cabeceras. En cualquier otra ruta o método, el parámetro se ignora.
- No hay sesión ni cookie: la cadena es `STATELESS`. Sin usuario válido, `/api/**` responde 401; un permiso faltante responde 403 con un mensaje propio.
- Los usuarios demo se siembran solo con `APP_SEED_DEMO=true`; `DemoDataSeeder` falla si el perfil es `prod`. No hay registro público ni recuperación de contraseña.
- La columna `password_hash` existe en el modelo, pero hoy no hay inicio de sesión con contraseña. `jjwt` está declarado y sin uso hasta el Momento 3.

**Limitación:** el UUID es público y la cabecera es falsificable. En el Momento 2 esto permite que cualquiera con acceso a la red actúe como enfermería o médico. No es un control de seguridad y no debe presentarse como tal.

### 7.2 Autorización por permisos

La autorización se aplica en el servidor con `@EnableMethodSecurity` y `@PreAuthorize`. El guard de Angular solo mejora la navegación; no protege datos.

| Permiso | Enfermería | Médico | Agente |
|---|---:|---:|---:|
| `PATIENTS_READ` | Sí | Sí | Sí |
| `PATIENTS_CREATE` | Sí | No | No |
| `PATIENTS_UPDATE` | Sí | No | No |
| `ENTRIES_READ` | Sí | Sí | Sí |
| `ENTRIES_CREATE` | Sí | Sí | No |
| `ENTRIES_UPDATE` | Sí | No | No |
| `REPORTS_READ` | Sí | Sí | No |
| `REPORTS_REQUEST` | Sí | No | No |
| `REPORTS_CREATE_DRAFT` | No | No | Sí |
| `REPORTS_UPDATE` | Sí | No | No |
| `REPORTS_APPROVE` | Sí | No | No |
| `REPORTS_SEND` | Sí | No | No |
| `NOTIFICATIONS_READ` | No | Sí | No |
| `CONSULTATIONS_REQUEST` | No | Sí | No |

`AGENTE` es un rol técnico del agente, no un usuario que inicie sesión. El rol `ADMIN` existe en el esquema con permisos genéricos, pero no tiene usuario sembrado ni superficies de administración en esta entrega.

Reglas de aislamiento:

- **Informes:** enfermería ve todos los estados; el médico solo `ENVIADO`. Un informe que el rol no puede ver responde 404, no 403, para no revelar su existencia. El PDF solo se genera para `APROBADO` o `ENVIADO` y con `Cache-Control: no-store`.
- **Historial de versiones:** el médico no lo recibe; solo ve la versión aprobada del informe enviado.
- **Mensual:** el enlace a una semana que compone un mensual enviado se oculta al médico si esa semana no está `ENVIADO`. La decisión se toma en vivo en el servidor, no desde el contenido congelado.
- **Notificaciones:** el servicio busca por `recipient_id`; marcar la de otro médico responde 404. No se guarda texto clínico: solo tipo, paciente, entrada o informe, fecha y estado de lectura.
- **Consultas del médico:** la tabla es independiente de `reports` y el repositorio busca por `doctor_id`. En esta entrega existen el modelo, el servicio de borrador y las herramientas; la superficie HTTP y la pantalla están en construcción, por lo que aún no hay un endpoint de consulta que probar de extremo a extremo.
- **Trazas:** se leen desde el informe con `REPORTS_READ` y la visibilidad del informe. La ruta directa de ejecuciones exige `REPORTS_REQUEST`; cualquier enfermería puede leer cualquier ejecución del período, porque en el Momento 2 no hay asignación por paciente ni por autor.

### 7.3 Integridad del flujo de informes

- Transiciones validadas en código: un informe solo se aprueba desde `PENDIENTE_REVISION`; solo se envía desde `APROBADO`; rechazar exige un motivo; un enviado no vuelve atrás.
- Aprobar y enviar son permisos separados, ambos de enfermería. El agente no tiene ninguno.
- El mensual exige cuatro semanas completas consecutivas y el informe semanal aprobado de cada una (RN-10). No se puede pedir otro informe vigente para un periodo ya tomado.
- La persona confirma los eventos propuestos antes de guardar la entrada. El texto original se conserva; el «texto corregido» es una versión aparte y no reemplaza la fuente citable.
- La edición de un informe la hace enfermería y genera una versión nueva; el texto del modelo no se sobrescribe.
- El PDF imprime la versión aprobada, no la que redactó el modelo.

### 7.4 Agente y LLM

Herramientas por tipo de ejecución:

| Tipo | Herramientas |
|---|---|
| Semanal | `list_patient_weeks`, `get_week_entries`, `get_week_metrics`, `get_symptom_history`, `save_draft` |
| Mensual | Las anteriores más `get_approved_weekly_reports` |
| Consulta | `get_period_entries`, `get_period_metrics`, `save_draft` |

`save_draft` es la única escritura y solo crea un borrador `PENDIENTE_REVISION`. No existe herramienta para aprobar, enviar, borrar, ejecutar código, abrir URLs, enviar correo o mensajes.

Los cuatro límites obligatorios están implementados:

1. **Iteraciones:** máximo 5 llamadas al modelo por solicitud (`AGENT_MAX_ITERATIONS`).
2. **Tokens:** máximo 30 000 tokens por solicitud (`AGENT_MAX_TOKENS`), comprobado antes de gastar otra llamada.
3. **Tiempo por herramienta:** 5 s (`AGENT_TOOL_TIMEOUT_SECONDS`), con cancelación. El primer tiempo agotado vuelve al modelo como observación; el segundo detiene la ejecución (`maxToolTimeouts = 2`).
4. **Confirmación humana:** el agente solo deja un borrador; enfermería lo revisa, lo aprueba y después lo envía.

Otras defensas:

- Un error de herramienta vuelve al modelo como observación; no mata el bucle.
- La salida se valida contra un esquema. Si falla, se reintenta devolviendo el error; tras dos borradores rechazados se degrada a texto plano y se registra.
- `DraftValidator`, `FigureResolver`, `DiagnosisGuard` y `ReportEditValidator` comprueban citas, cifras, longitud, diagnóstico, tratamiento y dosis. En el borrador del agente, cada número debe estar respaldado por las cifras calculadas por el código; en la edición humana se permiten además días del periodo o de las entradas citadas y el año.
- `AgentPrompt` trata los registros como dato, no como instrucción. `StructuringPrompt` neutraliza las etiquetas `<registro>` y `</registro>` y aplica una segunda barrera a los síntomas fuera del catálogo.
- La tarea del agente no lleva nombre, cama ni id del paciente. La estructuración envía solo el texto de la nota. Las herramientas devuelven alias (`e1`, `e2`…), no identificadores de paciente. La indicación libre (`request`) no se expone en la interfaz; si se usara desde la API, su texto también se enviaría al modelo.
- Hay reintentos con espera exponencial (1 s, 2 s, 4 s), timeout y conmutación a Gemini si Groq falla. Los mensajes al usuario están en español y sin detalle técnico.
- `LangfuseTraces` y `LangfuseExporter` envían solo metadatos; `LangfusePayloadTest` verifica que no salgan prompts, respuestas ni el `detail` técnico del proveedor.

### 7.5 Datos y terceros

| Tercero | Qué recibe | Cuándo | Observaciones |
|---|---|---|---|
| Groq | Texto de la nota, texto de registros para el informe y audio del dictado | Al estructurar, generar un informe o dictar | HTTPS; la aplicación no guarda el audio; la retención del proveedor se rige por sus términos |
| Google Gemini | El mismo texto de estructuración o de informe | Solo como respaldo si Groq falla | HTTPS; mismas reglas de minimización |
| Langfuse Cloud | Identificador de ejecución, tipo, estado, proveedor, modelo, tokens, duración y costo | Solo si `LANGFUSE_PUBLIC_KEY` y `LANGFUSE_SECRET_KEY` están definidas | No recibe prompts, respuestas ni texto clínico; es opcional |

Medidas de minimización:

- El agente no recibe nombre, documento, cama ni id interno del paciente.
- La estructuración recibe únicamente la nota, entre etiquetas.
- Langfuse no recibe contenido; el costo se calcula en código.
- Las notificaciones no llevan texto clínico.
- Los datos de la semilla son sintéticos y los informes sembrados los escribe una plantilla, no el agente.
- No hay CDN, fuentes ni scripts de terceros en el frontend; los recursos se sirven desde el propio nginx.

Retención y gobierno:

- La aplicación no controla cuánto conservan Groq, Google o Langfuse. Antes de usar datos reales se necesitan términos, retención, región y, si aplica, un acuerdo de tratamiento de datos.
- Está pendiente activar *Zero Data Retention* en Groq si se quiere que el audio y el texto no queden en sus registros de abuso. La aplicación no lo puede garantizar por sí sola.
- `docs/gobernanza.md` declara qué sale a terceros. Este documento no sustituye esa ficha.

### 7.6 Entradas y salidas

- Validación con Bean Validation y reglas de servicio: texto máximo de 4000 caracteres, fecha no futura, ausencia de `\u0000`, enums por lista blanca y límites en veces e intensidad.
- Audio: cuerpo crudo, no multipart, máximo 5 MB, lectura en memoria y borrado del arreglo al terminar. nginx usa `client_body_in_file_only off` y `proxy_request_buffering off`.
- El acceso a datos es por Spring Data JPA. La única consulta nativa usa parámetros vinculados; no se concatena SQL de la persona.
- Los síntomas fuera del catálogo requieren una frase literal de la nota y un nombre relacionado; una orden dentro del texto no puede crear un síntoma.
- Los informes se muestran con interpolación de Angular, no con HTML ni Markdown. El único `bypassSecurityTrustHtml` usa el catálogo estático `ICONOS`, no entrada de la persona.
- El PDF se genera en el servidor con JasperReports a partir de datos estructurados; no interpreta HTML de la persona.
- `ApiExceptionHandler` devuelve `{ message, fields? }` sin stack trace ni texto del framework. `LlmException` separa el mensaje público del detalle técnico.

### 7.7 Secretos y cadena de suministro

- Las llaves y contraseñas llegan por variables de entorno. `.env` está ignorado por git (`.gitignore:*.env`) y no se versiona.
- `run.sh` genera `DB_PASSWORD` con `od` y no bloquea si no hay terminal. No imprime las llaves.
- Gitleaks corre en cada push a `main`; CodeQL analiza Java y TypeScript en push, pull request y semanalmente; Dependency Review falla con vulnerabilidades altas en pull requests.
- Las acciones de GitHub están fijadas por SHA, con la versión en comentario, y los workflows declaran permisos mínimos.
- El frontend instala con `npm ci` y lockfile; el backend compila con Maven y versiones declaradas.
- No hay llaves dentro de las imágenes: los Dockerfile copian código y artefactos, no `.env`.

Limitaciones:

- No hay escaneo de imágenes, SBOM, firma de artefactos ni Dependabot.
- Las imágenes base usan etiquetas (`postgres:16.15-alpine`, `eclipse-temurin:17-jre`, `nginx:1.28-alpine`, `node:20-alpine`) y no digests.
- Gitleaks corre en push, no en cada pull request; un secreto podría entrar a una rama antes de la fusión.
- En el entorno revisado, `.env` quedó con modo `664`. `run.sh` lo crea con el umask del usuario. Se recomienda `umask 077` o `chmod 600 .env`.

### 7.8 Transporte y despliegue

- El compose publica `8080` (backend) y `4200` (frontend). PostgreSQL no publica puertos.
- No hay TLS: nginx escucha en HTTP y el navegador habla HTTP. Las llamadas del backend a los proveedores sí usan HTTPS.
- La cadena JDBC no exige TLS explícito. En este compose la base solo es alcanzable por la red interna, pero un despliegue separado debe configurar `sslmode`.
- No hay cabeceras de seguridad en nginx: faltan `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` y HSTS.
- El backend corre como `root` dentro del contenedor: el Dockerfile no declara `USER`. nginx usa su imagen oficial, que también arranca como root para abrir el puerto 80.
- No hay límite de tasa en nginx ni en el backend; solo el bloqueo de una ejecución viva por paciente y periodo.
- Swagger UI y `/api-docs` quedan fuera de `/api/**` y, por la regla `anyRequest().permitAll()`, son públicos. En una demo es aceptable; en producción deben deshabilitarse o protegerse.
- `GET /api/system/status` y `GET /api/demo-users` son públicos por diseño. El segundo revela los usuarios demo, que ya son públicos.
- CSRF está deshabilitado solo para `/api/**` porque no hay cookie ni sesión; la identidad viaja en una cabecera. Si el Momento 3 adopta cookies, esta decisión debe revisarse.
- CORS permite por defecto `http://localhost:4200` y las cabeceras `Content-Type`, `X-Demo-User` y `Last-Event-ID`. En Docker, el navegador habla solo con nginx y la petición es del mismo origen.

### 7.9 Auditoría y trazabilidad

- `reports` conserva estado, `reviewed_by`, `reviewed_at`, `approved_version_no`, `sent_by` y `sent_at`.
- `report_versions` conserva cada versión con su autor y fecha.
- `agent_steps` conserva herramienta, parámetros, resultado y duración de cada paso; es la traza visible.
- `notifications` conserva destinatario, tipo, paciente y lectura, sin texto clínico.

**Brecha:** la tabla `audit_logs` existe en `V3` con acciones y tipos permitidos, pero **ningún código Java la escribe**. No hay un registro de auditoría inmutable ni consultable para aprobaciones, rechazos, ediciones o registros del médico. RF-35 sigue pendiente. Los campos y las versiones permiten reconstruir parte del historial, pero no sustituyen una auditoría.

## 8. Pruebas de seguridad

| Prueba | Qué comprueba |
|---|---|
| `PermissionMatrixTest` | La matriz exacta de permisos de enfermería y médico; que solo enfermería aprueba y envía; que enfermería no lee notificaciones |
| `DemoUserFilterTest` | Cabecera válida; usuario inactivo o inexistente; `demoUser` solo en la ruta SSE y solo con GET |
| `ReportsApiTest` | El médico solo lista y abre informes `ENVIADO`; los demás responden 404; transiciones inválidas con 409; solo enfermería envía; el PDF y la traza respetan la visibilidad |
| `ReportPdfApiTest` | Solo se imprime un informe aprobado; lo que el rol no ve no existe; el PDF usa la versión aprobada |
| `MonthlyReportsApiTest` | Reglas del mensual y visibilidad de la semana no enviada |
| `NotificationsApiTest` | Un médico no marca la notificación de otro; enfermería recibe 403; no se notifica al autor; aprobar no notifica y enviar sí |
| `ConsultationDraftServiceTest` | Solo el médico autor encuentra su consulta |
| `DictationNoDiskTest` | El audio no se escribe en disco |
| `StructuringPromptTest` | La nota no puede cerrar las etiquetas; una orden dentro de la nota no crea un síntoma; el texto corregido no cambia números ni negaciones |
| `LangfusePayloadTest` | No salen prompts, respuestas ni detalle técnico a Langfuse |
| `DemoDataSeederDisabledTest` / `DemoDataSeederTest` | Sin la bandera no hay semilla; el seeder rechaza el perfil `prod` |
| `ReportTransitionsTest` / `ReportConcurrencyTest` | Transiciones y concurrencia del estado del informe |

Controles de CI: CodeQL, Dependency Review, Gitleaks, compilación y pruebas de backend y frontend, y Qodana. No hay DAST, análisis de composición de imágenes, pruebas de carga ni auditoría independiente.

Cómo reproducir lo principal:

```bash
cd backend && mvn -B verify
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage
python3 eval/run_eval.py --baseline-only
```

El backend necesita un PostgreSQL desechable; las pruebas lo modifican. La evaluación contra la app levantada gasta cuota de la llave.

## 9. Riesgos aceptados del Momento 2

Estos riesgos son conscientes y están acotados a la demo con datos sintéticos:

1. **Identidad falsificable:** `X-Demo-User` no es autenticación. Cualquiera con acceso a la red puede actuar como cualquier usuario demo.
2. **Sin TLS:** el tráfico entre navegador, nginx y backend viaja en claro.
3. **Acceso por rol, no por relación:** todos los profesionales del mismo rol ven el mismo conjunto de pacientes. No hay asignación paciente-profesional.
4. **Swagger público:** la superficie de la API es visible sin autenticación.
5. **Sin rate limiting:** la cuota del proveedor puede agotarse con solicitudes repetidas.
6. **Sin auditoría completa:** no se escribe `audit_logs`.
7. **Contenedores como root y base con superusuario:** es aceptable en local, no en producción.

Ninguno de estos riesgos debe trasladarse a un despliegue con datos reales.

## 10. Deuda de seguridad priorizada

| Prioridad | Medida | Motivo |
|---|---|---|
| P0 | Autenticación real y JWT/sesión (RF-31), con credenciales y administrador (RF-33) | Elimina la suplantación por cabecera |
| P0 | TLS en el borde y HSTS; `sslmode` para la base | Protege credenciales, texto clínico y cookies/tokens |
| P0 | Escribir `audit_logs` en la misma transacción que aprobar, rechazar, editar, enviar y registrar; hacerlo append-only | Hoy no hay evidencia completa de quién hizo qué |
| P0 | Rate limiting y cuotas por usuario, IP y proveedor | Evita agotar cuota y bloquear el servicio |
| P1 | Contenedores sin root, con `read_only`, `no-new-privileges`, `cap_drop: ALL` y usuario sin privilegios | Reduce el impacto de una ejecución remota |
| P1 | Usuario de base con permisos mínimos, separado del de migraciones; respaldos cifrados y probados | Limita el daño de una credencial comprometida |
| P1 | Cabeceras de seguridad y CSP en nginx | Mitiga XSS, clickjacking y filtrado de referer |
| P1 | Deshabilitar Swagger y `/api-docs` fuera de demo | Reduce la superficie y la divulgación |
| P1 | Asignación paciente-profesional y control por autor en trazas e informes | El acceso por rol es demasiado amplio para datos reales |
| P1 | Escaneo de imágenes, SBOM, firma y Dependabot; imágenes por digest | Cadena de suministro verificable |
| P2 | Revisar `server.error.include-message: always` y los logs técnicos | Evitar filtraciones por errores y trazas |
| P2 | Política de retención, borrado y consentimiento; términos con proveedores; ZDR en Groq | Obligaciones de datos de salud |
| P2 | Monitoreo, alertas, respuesta a incidentes y pruebas de restauración | Operación segura |
| P2 | Pentest independiente antes de producción | Validar el modelo de amenazas con un tercero |
| P3 | `chmod 600 .env`, `umask 077` y rotación de llaves | Higiene de secretos en el host |

## 11. Requisitos para uso real

Antes de tratar datos reales, como mínimo:

1. Sustituir la identidad demo por autenticación fuerte, con contraseñas hasheadas (BCrypt/Argon2), bloqueo por intentos, sesión o JWT de corta vida y revocación. Agregar MFA para roles clínicos.
2. Implementar el administrador y el tablero de permisos (RF-33, RF-34) sin permitir que un usuario se otorgue permisos a sí mismo.
3. Aplicar mínimo privilegio por paciente y por relación profesional-paciente, además del rol. Revisar cada endpoint y cada traza.
4. Terminar TLS de extremo a extremo, activar HSTS, CSP y cabeceras de seguridad, y deshabilitar documentación pública.
5. Escribir auditoría transaccional e inmutable, con retención y acceso restringido.
6. Endurecer contenedores y base: non-root, solo lectura, capacidades mínimas, usuario de BD sin superusuario, red segmentada y respaldos cifrados con restauración probada.
7. Poner rate limiting, cuotas y alertas de costo; presupuesto máximo por período.
8. Formalizar gobernanza de datos: base legal, consentimiento, retención, borrado, minimización, región, términos y acuerdos con Groq, Google y Langfuse; activar ZDR si corresponde.
9. Revisar la retención y los límites del plan gratuito de Langfuse; borrar trazas de prueba del proyecto compartido.
10. Establecer monitoreo, alertas, respuesta a incidentes, contacto de seguridad y un proceso de parcheo.
11. Hacer una revisión independiente y un pentest antes de la puesta en producción.
12. Verificar que el contenido generado no diagnostique, no recomiende tratamiento ni dosis y que siempre haya revisión humana antes de que llegue al médico.

## 12. Divulgación responsable

Si encuentra una vulnerabilidad:

- Use el **reporte privado de vulnerabilidades de GitHub** en la pestaña *Security* del repositorio, si está habilitado. Si no lo está, contacte a los mantenedores por el canal privado del curso.
- **No abra un issue público** con detalles explotables.
- Incluya una descripción, pasos para reproducir, impacto, versión o commit y una prueba de concepto mínima.
- **No incluya datos reales de pacientes, llaves ni información de terceros.** Use datos sintéticos.
- No realice pruebas de denegación de servicio, ingeniería social, acceso físico ni ataques a la infraestructura de Groq, Google, Langfuse, GitHub o Docker.
- El bypass de la identidad demo (`X-Demo-User`) es un riesgo conocido y aceptado del Momento 2; no hace falta reportarlo como hallazgo nuevo.
- Los resultados de un escáner sin impacto demostrado no se consideran una vulnerabilidad por sí solos.

La respuesta es de mejor esfuerzo; este proyecto no ofrece un SLA. Se pide divulgación coordinada: dar tiempo a corregir antes de publicar detalles. No se tomarán represalias por pruebas de buena fe que respeten este alcance y no accedan a datos de terceros.

## 13. Referencias

- `AGENTS.md`: reglas del producto, del agente y de la entrega.
- `docs/SPEC.md`: requisitos, permisos, estados y criterios de aceptación.
- `docs/REQUISITOS.md`: requisitos funcionales y no funcionales, incluidos RF-31, RF-33, RF-34, RF-35, RF-44 y RF-45.
- `docs/decisiones.md`: D23 (autenticación), D24 (asíncrono/SSE), D26 (usuario demo), D28 (Langfuse), D29 (envío y visibilidad), D30 (notificaciones), D31 (consultas).
- `docs/PLAN_M3.md`: piezas del Momento 3 y estado de ejecución.
- `docs/gobernanza.md`: ficha de tratamiento y terceros (Momento 3).
- `docs/05-security/README.md`: controles automatizados y brechas de seguridad del repositorio.
- `backend/src/main/java/com/patientprogressauditor/config/SecurityConfig.java`: cadena de seguridad.
- `backend/src/main/java/com/patientprogressauditor/session/DemoUserFilter.java`: identidad demo.
- `backend/src/main/java/com/patientprogressauditor/report/ReportVisibility.java`: visibilidad de informes.
- `backend/src/main/java/com/patientprogressauditor/agent/AgentPrompt.java`: reglas del agente.
- `backend/src/main/java/com/patientprogressauditor/agent/tools/`: herramientas y límites.
- `backend/src/main/java/com/patientprogressauditor/llm/LangfusePayload.java`: metadatos enviados a Langfuse.
- `frontend/nginx.conf`: proxy, límites de audio y ausencia de cabeceras de seguridad.
- `docker-compose.yml`: servicios, puertos y variables.
- `.github/workflows/security.yml`: CodeQL, Dependency Review y Gitleaks.

## 14. Registro de cambios

| Fecha | Versión | Cambio |
|---|---|---|
| 2026-11-10 | 1.0 | Documento inicial: arquitectura, modelo de amenazas, controles, pruebas, deuda y divulgación |
