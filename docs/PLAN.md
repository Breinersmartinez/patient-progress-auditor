# Plan 0 (histórico)

> Se conserva tal cual lo escribió el equipo el 27-oct. Su tarea 1 sigue sin marcar: `frontend/package.json` aún trae `angular-cli-ghpages`. El plan maestro del Momento 2 está al final de este archivo.

# PLAN — Retiro de despliegue heredado

> **Estado:** en ejecución · **Fecha:** 2026-10-27

## Alcance del plan

Retirar la configuración de publicación heredada y las secciones de documentación que la describen. Cubre el arranque autosuficiente exigido por `docs/SPEC.md` (OBJ-13, RNF-08 y RNF-13) al conservar Docker Compose como única vía de ejecución. No modifica la SPEC ni los requisitos.

## Tareas

- [ ] 1. Retirar la publicación heredada y sus referencias directas
  - **Resultado:** no existe configuración de publicación heredada ni documentación que la describa; CI, seguridad y análisis de calidad continúan definidos.
  - **Responsable:** Breiner Martinez Muñoz.
  - **Dependencias:** ninguna.
  - **Estimación:** menos de media jornada.
  - **Criterio de aceptación (sí/no):** ¿La búsqueda de las referencias a los dos proveedores retirados devuelve cero resultados, los workflows restantes son YAML válido y las verificaciones de backend y frontend siguen pasando?

## Orden y dependencias

Solo hay una tarea: primero se retiran el manifiesto y el workflow, junto con sus referencias documentales directas; después se validan las configuraciones y compilaciones que permanecen.

## Primera tarea

**Tarea 1 — Retirar la publicación heredada y sus referencias directas.** Está lista para ejecutarse.

## Fuera de este ciclo

- Crear o modificar Docker Compose, `run.sh`, el arranque en frío o infraestructura nueva: no forman parte del retiro solicitado.
- Cambiar `docs/SPEC.md` o `docs/REQUISITOS.md`: no contienen referencias directas a los proveedores retirados.

---

# Plan maestro — Momento 2 hasta el módulo de enfermería (ETAPA 1)

> **Estado:** borrador para aprobación; sin el «adelante» no se escribe código · **Fecha:** 2026-10-28, 17:36 (Bogotá) · **Rama:** `develop_BSMartinez` · **Fuente:** `docs/prompts/m2-enfermeria.md`
>
> **Cómo leerlo.** No es un plan de siete pasos: son 16 encargos (E1 a E16). Cada uno tendrá su propio encargo y un plan de siete pasos como máximo, en su sesión (`planea-trabajo`, regla 2). Aquí se ordenan, se dicen sus dependencias, se cierran las propuestas del §4.2 del prompt y se señala lo que puede romper la entrega. Cada afirmación sobre el repo o el entorno la comprobé hoy; lo que no pude comprobar dice «no verificado».

## 1. Encargo

**En una frase.** Construir en la rama el módulo de enfermería completo (registrar con estructuración por IA, generar con el agente, revisar, corregir y aprobar), con arranque en frío por Docker Compose, y dejarlo listo para congelarlo en el tag `m2-entrega` antes de la medianoche del jueves 29-oct-2026.

**Contexto.** El profesor da el README a una IA y, si la app no levanta sola, no revisa nada. El Momento 2 vale el 30 % de la nota: agente 25 puntos, datos 20, aplicación 20, medición y trazas 25, sustentación 10. Hoy el repo es un scaffold sin lógica de dominio.

**Qué se produce.**
1. `docker compose up --build` y `./run.sh` dejan base, backend y frontend sanos, sin `JWT_SECRET` y sin quedarse esperando entrada.
2. Esquema Flyway `V3` y `V4` (16 tablas del diccionario M2 y las columnas `bed`, `age`, `admitted_on` en `patients`) y semilla idempotente: 4 usuarios, 5 pacientes, unas 120 entradas con eventos confirmados.
3. API y pantallas de enfermería (rutas con hash del mockup A): pacientes, entradas, estructuración al registrar, informes, revisión y aprobación.
4. Capa de proveedor (principal y respaldo) y agente con los cuatro límites, validadores de cifras y citas, ejecución en segundo plano y progreso por SSE.
5. `eval/` con 5 a 10 casos dorados (uno de rechazo) y las cuatro cifras con fecha y método; `docs/decisiones.md` y el README ejecutable.

**Insumos.**

| Insumo | Para qué | Estado hoy |
|---|---|---|
| `GEMINI_API_KEY` y `GROQ_API_KEY` en el shell | E8, E9, E10, E14: toda verificación con IA real | **No definidas en esta sesión.** Sin ellas las pruebas con dobles corren, pero «una llamada real devuelve JSON» no se puede verificar |
| Docker 29.3.1 y Compose v5.0.2 | E1 y la prueba en frío | Presentes |
| Chrome `/usr/bin/google-chrome` | Karma y F8 | Presente; `CHROME_BIN` sin definir (lo paso en el comando) |
| PostgreSQL para `mvn verify` | E2 en adelante | El 5432 local lo ocupa un Postgres del equipo cuyas credenciales no conozco; no lo toco. Uso un contenedor desechable `postgres:16` en un puerto libre (el 5433 también estaba ocupado; usé el 55432), como el servicio de `ci.yml` |
| Mockup A, SPEC, REQUISITOS, diccionarios, EDT | Todo | Leídos. De las dos EDT vale `EDT_M2_Patient_Progress_Auditor.xlsx` (modificada 15:23; la otra, 12:59) |
| `.env` local | — | Existe y no está versionado. No lo leo ni lo modifico; `run.sh` se prueba en una copia temporal del repo |

**Criterio de aceptación.**
- Forma: las 16 etapas cerradas con el reporte del §7 del prompt, o recortadas por decisión escrita tuya.
- Se sabrá porque: (a) las cuatro preguntas de la puerta de la SPEC §6 se responden sí, con la prueba en frío (un clon limpio y una IA que solo lee el README); (b) la columna «Cierre» de cada etapa se responde con evidencia; (c) `mvn -B verify` y `ng test` pasan; (d) las capturas de las etapas de pantallas no difieren del mockup salvo la tabla 3.3.
- Límite razonable: ningún secreto en el árbol ni en el historial (E15 lo escanea).

**Queda fuera.** Todo lo del lado derecho de la tabla del §0 del prompt: pantallas del médico y del administrador (salvo E12c si la apruebas), Momento 3, rechazo con motivo, corregir una entrada, PDF, voz, notificaciones, «Enviar al médico», RAG, multiagente, modo simulado.

**Riesgos del resultado.** Una demo limpia con datos sintéticos puede leerse como validación clínica: el catálogo de nueve síntomas no se validó con enfermería (D18). La «sesión de demostración» separa funciones, no es seguridad (D26). Las cuatro cifras saldrán de pocas ejecuciones con modelos de capa gratuita: son línea base, no tasa de acierto general.

## 2. Contradicciones y aclaraciones (antes de asumir nada)

| # | Qué choca | Cómo lo trato |
|---|---|---|
| C1 | RF-28 (el médico consulta informes aprobados) es M2-N en REQUISITOS y la puerta de la SPEC §6 pide un usuario de médico, pero el prompt lo saca a la etapa opcional E12c | Lo dice el propio prompt. **Sin E12c la puerta no se cumple.** Recomiendo promoverla (pregunta 2). Estimación de la EDT: 3,5 h (10.1 y 10.2), más F8 |
| C2 | El cierre de E3 dice «los conteos de D11», y D11 incluye 5 informes sembrados que el prompt asigna a E14 (EDT 2.13) | E3 se cierra con 5 pacientes, 18 semanas-paciente y unas 120 entradas con eventos. Los informes llegan en E14 |
| C3 | El prompt propone la cabecera `X-Demo-User`; `EventSource` no envía cabeceras. SPEC D24 dice que en el Momento 2 basta `EventSource` | Cabecera en todo el API y parámetro `demoUser` solo en la ruta SSE (solo lectura). Resuelve las dos |
| C4 | El prompt exige `DATABASE_URL`; `application-dev.yml` usa `DB_HOST`, `DB_PORT`, `DB_NAME` | El perfil nuevo `docker` usa `DATABASE_URL`; `dev` conserva los `DB_*`. Las dos familias van en `.env.example` |
| C5 | `application-dev.yml` tiene `ddl-auto: update` junto a Flyway: Hibernate podría tocar el esquema | En E2 pasa a `validate`. Cambia un comportamiento existente: lo anoto en `docs/decisiones.md` |
| C6 | La EDT no tiene tarea para portar el sistema de diseño del mockup (F1 a F3) ni para F8; E5 solo suma la 3.4 (2 h) | E5 está subestimada. Lo digo ahora para no descubrirlo el jueves |
| C7 | El Plan 0 sigue con su tarea sin marcar: `angular-cli-ghpages` está en `frontend/package.json` | Fuera de este plan. Quitarlo es una desinstalación: te pregunto si lo quieres |

## 3. Capacidad frente a carga

- Quedan **30 h 24 min** de calendario hasta la medianoche del jueves.
- La EDT suma 185 h; las tareas que mapean a E1 a E16 suman 159 h. Sin E12c, E13 ni E16 (la parte que el prompt manda recortar primero) son **146 h**: E1 8 · E2 8,5 · E3 11 · E4 4 · E5 2 · E6 6,5 · E7 6 · E8 12,5 · E9 13,5 · E10 22,5 · E11 5 · E12a 8,5 · E12b 10 · E14 15,5 · E15 12,5.
- La EDT dice que sus horas son una propuesta de Claude para una persona con ayuda de IA. **No sé cuánto menos tarda esto ejecutado con Claude Code** y no lo voy a adivinar. Si esas horas fueran ciertas, 146 h no caben en 30 h de calendario ni con dos personas.
- Lo que propongo: medir. Al cerrar E1 y E2 comparo el tiempo real contra la EDT (8 h y 8,5 h) y con ese factor recalibro el corte. Lo reporto antes de empezar E3.
- Una cosa ya se puede decir sin medir: el orden de recorte del prompt (E16, E13, «texto corregido») libera unas 12,5 h, que no cierran la brecha si la EDT es cierta. Habrá que decidir un segundo escalón de cortes (pregunta 3).

## 4. Plan maestro

**Supuestos (si alguno es falso, el plan cambia).**
- Habrá llave de Gemini y de Groq en el shell antes de empezar E8.
- Spring Boot 3.4.1 y Angular 19 alcanzan para todo sin librerías nuevas (ver §6).
- Los modelos y sus límites gratuitos son los que se verifiquen en E8, paso 1, con la llave real.
- Los dos integrantes pueden trabajar en paralelo, cada uno en su rama y su sesión.

### Etapas, dependencias y horas de la EDT

| Etapa | Frente | Depende de | EDT (h) | Qué la cierra, en corto (el criterio completo está en el prompt §5) |
|---|---|---|---|---|
| E1 Arranque en frío | A | — | 8 | Compose deja los tres servicios sanos sin `JWT_SECRET`; `run.sh` sin terminal y sin llave arranca |
| E2 Esquema y roles | A | E1 | 8,5 | Flyway aplica V3 y V4 en base vacía; el `CHECK` rechaza lo que debe; solo ENFERMERIA aprueba |
| E3 Semilla | A | E2 | 11 | Conteos de D11; reiniciar no duplica; `prod` rechaza la semilla |
| E4 Sesión y permisos | A | E3 | 4 | Sin usuario elegido, 401; cada rol hace lo que dice la matriz |
| E5 Sistema de diseño y shell | A | E4 | 2 (subestimada, C6) | Capturas del shell sin diferencias a 1280 y 390; «Ver como» cambia de enfermera |
| E6 Pacientes y entradas | A | E4, E5 | 6,5 | Texto guardado idéntico al enviado; historial filtra y ordena |
| E7 Métricas | B | E3 | 6 | Cifras iguales al cálculo a mano, incluido el reaparecido de P1 |
| E8 Capa de proveedor | B | E1 (E2 para registrar tokens) | 12,5 | Llamada real devuelve JSON; cambiar la variable cambia de proveedor; los tres fallos se fuerzan |
| E10 Agente | B | E7, E8 | 22,5 | Cada límite se fuerza y detiene al agente; cifra alterada o cita inexistente se rechazan; la nota de P5 no lo cambia |
| E11 Segundo plano y SSE | B | E10 | 5 | Primer evento antes de terminar; reanuda; el proxy no acumula; huérfanas aparecen interrumpidas |
| E9 Estructuración al registrar | A | E6, E8 | 13,5 | El ejemplo del prompt valida; «sin vómito» no crea evento; nada se guarda hasta confirmar |
| E12a Informes: backend | B | E11, E4 | 8,5 | Transición no definida rechazada; el médico no aprueba ni ve pendientes |
| E12b Informes: pantallas | A | E12a, E5 | 10 | Se recorre generar, revisar, editar y aprobar; cada cita a un clic |
| E14 Informes sembrados y evaluación | B | E3, E7, E12a | 15,5 | Informes con citas y cifras válidas; un comando corre la suite; cuatro cifras medidas o «no medido» |
| E15 Documentación y entrega | A y B | todas | 12,5 | Una IA que solo lee el README llega a la app corriendo; escaneo sin secretos |
| E12c Vista del médico (opcional, puerta §6) | A | E12a, E5 | 3,5 | El pendiente no aparece al médico, ni en pantalla ni por API |
| E13 Informe mensual | B | E12a, E14 | 7 | Con las cuatro semanas aprobadas de P1 muestra la evolución; con una sin aprobar no se genera |
| E16 Auditoría | A | E12a | 2,5 | Cada acción sensible deja quién, cuándo, antes y después |

**Orden.** Una sola sesión: el orden numérico del prompt, con E12c, E13 y E16 al final. Dos sesiones: el frente A (E3 a E6, luego E9, E12b) y el frente B (E7, E8, E10, E11, luego E12a, E14). Pongo E7 en B porque E10 la necesita y así B no espera a A. Antes de separarnos acordamos el contrato de API (§5). Si trabajo solo, propongo subir E7 y E8 antes de E5 y E6: la rúbrica da 50 puntos a agente y medición, y la interfaz se puede aligerar.

### El paso más riesgoso del proyecto: E10, el agente contra un proveedor de capa gratuita

- **Por qué.** Hay que lograr que un modelo gratuito complete un bucle con herramientas y devuelva un informe que pase el esquema, las cifras y las citas, dentro de los límites. Cada rechazo del validador consume iteraciones, tokens y cuota. Y es lo que más puntúa.
- **Sub-riesgo del respaldo (no verificado).** Fuentes de terceros dan a Groq en capa gratuita límites de tokens por minuto de entre 6 000 y 12 000 según el modelo. Si es cierto, el respaldo podría no sostener un bucle con contexto grande. Mitigación: salidas de herramientas compactas (métricas ya calculadas y solo las entradas necesarias) y probar el respaldo con una ejecución completa en E8, no solo con un «hola».
- **Plan B.** Menos herramientas y una que devuelva la comparación ya calculada, para que el bucle tenga pocas iteraciones, conservando las decisiones del agente (cuántas semanas retroceder, si profundiza en un síntoma, si declara insuficiencia). Si aun así no funciona, se documenta con la tasa medida. No hay modo simulado (D16).
- **Segundo más riesgoso:** E1, porque un arranque roto anula la entrega. Por eso va primero y se vuelve a probar en E15.

### Dónde se puede caer

| Etapa | Qué puede salir mal | Cómo se nota |
|---|---|---|
| E1 | El Dockerfile del backend no tiene `CMD`; `run.sh` con un `read` sin terminal se cuelga; el 80 y el 5432 de esta máquina están ocupados | `docker compose up` termina con el backend reiniciándose; `run.sh < /dev/null` no regresa; «puerto ocupado» |
| E2 | `validate` de Hibernate contra tipos `SMALLINT`, `JSONB` y `NUMERIC` mal mapeados; `password_hash NOT NULL` de V1 | La app no arranca con un error de esquema; la semilla falla al insertar usuarios |
| E3 | Escribir unas 120 entradas coherentes; semilla que se duplica; conteos que nadie contó a mano | Conteos distintos tras reiniciar; un caso dorado que no coincide con los datos |
| E4 | Orden del filtro frente a Spring Security; confundir 401 con 403 | Un endpoint responde sin usuario elegido |
| E5 | Portar unas 1 000 líneas de CSS y JS sin rediseñar | Diferencias en las capturas a 390 px |
| E7 | «Reaparecido» sin definición exacta; límites de semana con la zona horaria | Cifras que difieren del cálculo a mano en P1 |
| E8 | Los modelos cambian; 429 durante el desarrollo; el JSON difiere entre proveedores | Fallos en el paso 1 con la llave real |
| E9 | El modelo inventa eventos o convierte una negación en síntoma; la persona cambia el texto tras la propuesta | «Sin vómito» crea `VOMITO`; se guardan eventos de un texto distinto |
| E10 | Ver riesgo principal; inyección en la nota de P5 | Informe `INCOMPLETO` en la mayoría de ejecuciones |
| E11 | nginx acumula la respuesta; carga perezosa fuera de la transacción (`open-in-view: false`); ejecución duplicada | Los pasos llegan todos al final; `LazyInitializationException` |
| E14 | Informes sembrados que no coinciden con las métricas; pocas ejecuciones reales para la mediana | Validador rechaza la semilla; cifras con «n» muy bajo, que se declara |
| E15 | El README supone algo que solo existe en esta máquina | Falla la prueba en frío con un clon limpio |

### Seguimiento

- [x] E1 · [x] E2 · [x] E3 · [x] E4 · [x] E5 · [x] E6 · [x] E7 · [x] E8 · [x] E9 · [x] E10 · [x] E11 · [x] E12a · [x] E12b · [x] E14 · [x] E15 (falta la prueba del compañero y el tag)
- [ ] E12c (fuera por decisión del 28-oct) · [x] E13 (pedido por el equipo el 29-oct) · [ ] E16 (fuera)

## 5. Propuestas del §4.2 del prompt, cerradas para tu aprobación

| Tema | Propuesta | Alternativa descartada |
|---|---|---|
| Proveedor y modelo (D15) | **[Invertido por el equipo el 2026-10-28: principal Groq y respaldo Gemini; lo que sigue es la propuesta original]** Principal Google AI Studio y respaldo Groq, por HTTP con `RestClient`, sin SDK. **Verificado hoy en las páginas oficiales:** Gemini lista como estables `gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-3.5-flash-lite` y `gemini-3.1-flash-lite`; Groq lista `llama-3.3-70b-versatile`, `openai/gpt-oss-120b` y `openai/gpt-oss-20b`, todos con 131 072 tokens de contexto. Candidatos: `gemini-3.8-flash` para el agente y `gemini-3.5-flash-lite` para estructurar; `openai/gpt-oss-120b` como respaldo. **No verificado:** los límites gratuitos (Google ya no los publica por modelo: se ven en la consola de AI Studio; las fuentes de terceros se contradicen, de 2 a 10 solicitudes por minuto), el soporte de herramientas y de JSON de cada modelo (las páginas no lo dicen) y el precio. Se decide en E8, paso 1, con la llave. Dato para gobernanza: Google puede usar los datos de la capa gratuita para mejorar sus productos | Un solo proveedor (no permitiría mostrar el cambio en vivo); un SDK (rompe «ningún SDK fuera de la capa») |
| Los cuatro límites | Valores iniciales, que se calibran con las primeras trazas y se justifican en `decisiones/semana06.md`: 8 iteraciones, 50 000 tokens por solicitud, 5 s por herramienta (son lecturas locales; la llamada al modelo tiene su propio tiempo máximo) y confirmación humana: la única escritura del agente es el borrador en `PENDIENTE_REVISION`; no tiene herramienta ni permiso para aprobar | Límites sin valor o sin razón: sin límite no hay entrega |
| Herramientas (siete como máximo, ninguna de salida) | `list_patient_weeks`, `get_week_entries`, `get_week_metrics`, `compare_weeks`, `get_symptom_days`, `save_draft` y, solo en el mensual, `get_approved_weekly_reports`. Se afinan y se registran dos versiones de cada descripción en E10 | Una herramienta que devuelva todo el historial: gasta tokens y quita decisiones al agente |
| Usuario elegido | Cabecera `X-Demo-User` con el id; parámetro `demoUser` solo en la ruta SSE; un filtro lo valida y deja rol e identidad; `/api/demo-users` y `/api/system/status` son públicos | `fetch` en streaming: más código sin ganancia en el Momento 2 (D24 prevé pasar a `fetch` con el token en el Momento 3) |
| Perfil de Spring | Perfil nuevo `docker` (`application-docker.yml`): conexión desde `DATABASE_URL`, `DB_USERNAME` y `DB_PASSWORD`, `validate`, sin SQL en el log, semilla permitida. `prod` rechaza `APP_SEED_DEMO=true` y no arranca | Usar `dev`: registra el SQL en DEBUG y hoy actualiza el esquema; usar `prod`: la semilla no corre |
| Validación del esquema | Jackson con registros tipados y Bean Validation, ambos ya presentes | Una biblioteca de JSON Schema: dependencia nueva sin necesidad |
| Pruebas con BD | Contenedor `postgres:16` desechable en un puerto libre (el 5433 está ocupado aquí), como el servicio de `ci.yml` | Testcontainers: dependencia nueva y no aprobada |
| Variables | `.env.example` lista `DATABASE_URL`, `DB_*`, `GEMINI_API_KEY`, `GROQ_API_KEY`, `LLM_PROVIDER`, `LLM_FALLBACK`, `GEMINI_MODEL`, `GROQ_MODEL`, `LLM_FAULT`, `APP_SEED_DEMO`, `SPRING_PROFILES_ACTIVE` y los puertos del host. Criterio: cada `${VAR}` de los yml y del compose figura en `.env.example` (lo compruebo con un `grep`) | — |
| Puertos | La base no se publica al host (no puede chocar); el frontend y el backend usan puertos configurables y `run.sh` avisa si están ocupados | Publicar el 5432: choca con cualquier Postgres local, como el de esta máquina |
| Fallos forzables | **Descartado por el equipo el 2026-10-28: no se construye `LLM_FAULT` ni fallos en vivo.** Propuesta original: `LLM_FAULT` = `none` (por defecto), `timeout`, `rate_limit` o `invalid_output`, leída solo dentro de la capa de proveedor y solo sobre el principal, de modo que el respaldo real entra en vivo. No es un modo simulado: la ruta normal siempre llama al proveedor real. **Lo construyo solo si lo confirmas** (pregunta 5) | Un adaptador de repetición de respuestas: es el modo simulado descartado en D16 |
| Cambio de proveedor en vivo | `LLM_PROVIDER=gemini docker compose up -d backend` (el principal por defecto es Groq), escrito en el README y probado en E8 | — |
| Ejecuciones huérfanas | Al arrancar (`ApplicationReadyEvent`), toda ejecución `EN_COLA` o `EN_CURSO` pasa a `FALLIDA` con «Interrumpida por un reinicio» | Dejarlas en curso: la pantalla esperaría para siempre |
| Semana en curso | No se puede pedir; solo semanas completas con entradas (propuesta del prompt). «Hoy» se calcula en `America/Bogota` | — |
| Informes sembrados | Escritos a mano con las cifras que calcula el módulo de métricas y revisados uno por uno; no dependen de la llave. Se archivan como JSON en `backend/src/main/resources/seed/` | Generarlos con el agente: la semilla dependería de que E10 funcione |

**Contrato de API (borrador para acordar antes de separarnos).** `GET /api/system/status` · `GET /api/demo-users` · `GET|POST /api/patients` · `GET /api/patients/{id}/entries?from&to` · `GET /api/patients/{id}/weeks` · `POST /api/entries/propose` (no guarda) · `POST /api/entries/confirm` y `POST /api/entries` (solo texto) · `POST /api/runs` (202 con el id; ver E11 y E12a) · `GET /api/runs/{id}` · `GET /api/runs/{id}/events` (SSE) · `GET /api/reports?status&patientId` · `GET /api/reports/{id}` · `PUT /api/reports/{id}/content` (nueva versión) · `POST /api/reports/{id}/approve` · `GET /api/reports/{id}/trace`.

## 6. Dependencias nuevas que prevés (para aprobar juntas)

- **Backend: ninguna.** Se usan `RestClient` y `SseEmitter` (de `spring-boot-starter-web`), `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6), `ThreadPoolTaskExecutor` (Spring), Jackson, Bean Validation, Mockito y `MockRestServiceServer` (de `spring-boot-starter-test`). El estado de salud para Compose es un endpoint propio (`/api/system/status`), sin `spring-boot-starter-actuator`.
- **Frontend: ninguna.** Router, formularios y `HttpClient` ya vienen con Angular 19; Karma y Jasmine ya están. Sin Angular Material, Bootstrap, Tailwind, fuentes ni iconos externos (F2). El proxy de `ng serve` es un archivo `proxy.conf.json`, no un paquete.
- **Imágenes de Docker a descargar** (versiones exactas se fijan en E1 tras comprobar que existen): `postgres:16`, `maven:3.9-eclipse-temurin-17` y `eclipse-temurin:17-jre` (ya en el Dockerfile), `node:20` y `nginx`.
- **Escaneo de secretos (E15):** primero un `grep` con patrones, sin instalar nada. Opcional: la imagen de `gitleaks`; el workflow `Security` ya incluye `gitleaks-action`. Solo con tu aprobación.

## 7. Lo que necesito de ti para decir «adelante»

1. **Llaves.** ¿Cuándo tendré `GEMINI_API_KEY` y `GROQ_API_KEY` en el shell de esta sesión? Sin ellas, E8, E9, E10 y E14 se construyen con dobles y quedan «no verificados» en lo esencial.
2. **E12c.** ¿La promuevo al núcleo? Recomiendo que sí: cuesta 3,5 h según la EDT y es lo que cumple la puerta de la SPEC §6 y RF-28.
3. **Línea de corte y reparto.** ¿Una sesión o dos frentes (A y B)? Y, si la medición de E1 y E2 confirma que falta tiempo, ¿qué se recorta después de E16, E13 y «texto corregido»? Mi propuesta de orden: la pantalla de alta de paciente (RF-01 queda por API y semilla), luego los controles de edición de eventos de E9 (se conservan confirmar, quitar y descartar).
4. **D26 y D27.** La EDT (13.1) los quiere «Decididas». D26 la necesito ya, para E4. Los puntos del §4.4 del prompt (valores de intensidad, nombre del botón, catálogo, «Descartar») te los pregunto en E9.
5. **Fallos inyectados (`LLM_FAULT`).** ¿Confirmas que los construya? La rúbrica pide mostrar los tres fallos en vivo.
6. **Dependencias.** ¿Apruebas el §6 tal cual (cero librerías nuevas y las imágenes de Docker)? ¿Y que `run.sh` se pruebe en una copia temporal para no tocar tu `.env`?

### Respuestas del equipo (2026-10-28)

| # | Respuesta | Efecto en el plan |
|---|---|---|
| 1 | Las llaves ya están en el shell | **Verificado: en esta sesión `GEMINI_API_KEY` y `GROQ_API_KEY` no están definidas.** Se resuelve antes de E8 |
| 2 | E12c: por ahora solo se necesita el módulo de enfermería | E12c queda fuera. La puerta de la SPEC §6 («un usuario de enfermería y uno de médico») no se cumple con lo construido: el médico existe en la semilla y en el servidor, sin pantalla |
| 3 | Una sesión; lo necesario para el módulo de enfermería | Una sola sesión, orden numérico del prompt: E2 a E12b, E14 y E15. E13 solo si sobra tiempo después de E15; E16 y «texto corregido» fuera |
| 4 | D26 confirmada | `docs/SPEC.md` D26 pasa a «Decidida»; D27 y el §4.4 se preguntan en E9 |
| 5 | Sin fallos en vivo | No se construye `LLM_FAULT`. Los tres fallos (tiempo agotado, salida inválida, 429 o caída) se verifican con pruebas automáticas con dobles de prueba, no en la demo |
| 6 | Dependencias aprobadas (cero librerías, imágenes de Docker) y `run.sh` en copia temporal | Sin cambios |
| 7 (28-oct, por la noche) | **Groq es el proveedor principal y Gemini el de respaldo** | Se invierte la propuesta de D15. Cambian: `SPEC.md` D15, `run.sh` (pide y exige `GROQ_API_KEY`), `.env.example` y el compose (orden), y el diseño de E8: el principal por defecto es Groq y el límite de tokens por minuto de Groq pasa a ser el riesgo del bucle del agente, no del respaldo |

## 8. Qué se entrega al final

- Al cerrar el Momento 2: la rama con las etapas aprobadas, cada una con su reporte del §7 del prompt y su propuesta de commit (no hago commit ni push).
- Lo que dejo listo para ti, sin hacerlo: la prueba en frío con un clon limpio, el tag `m2-entrega`, el push, el enlace en el aula virtual, la llave temporal del profesor y el ensayo de la demo (EDT 14.4 a 14.9), con los pasos y el comando exacto.

---

# E1 — Arranque en frío

> **Estado:** en ejecución (aprobada con el «adelante» del 2026-10-28) · **EDT:** 1.1 a 1.6 (8 h) · **Depende de:** nada

## Encargo

**En una frase.** Que `docker compose up --build` y `./run.sh`, en una máquina con solo Docker Compose v2, dejen la base, el backend y el frontend sanos, sin `JWT_SECRET`, sin llave del proveedor obligatoria y sin quedarse esperando entrada.

**Contexto.** Un arranque roto anula la entrega (SPEC §6, D22, RNF-13). El Dockerfile del backend no tiene `CMD`, `docker-compose.yml` y `run.sh` están vacíos y `application.yml` exige `JWT_SECRET`.

**Qué se produce.**
1. Backend que arranca sin `JWT_SECRET`: sin bloque `security.jwt`, con cadena de seguridad abierta y CORS por configuración, perfil `docker` y `GET /api/system/status` (público; 503 si la base no responde).
2. Dockerfile del backend con `CMD`; Dockerfile del frontend en dos etapas (Node 20 y nginx) con proxy `/api` sin acumular la respuesta (`proxy_buffering off`, `X-Accel-Buffering: no`).
3. `docker-compose.yml` (PostgreSQL con versión fijada, base no publicada al host, comprobaciones de salud, puertos del host configurables) y `.env.example` con toda variable que usa el código.
4. `run.sh`: verifica Docker, crea `.env` si falta, genera `DB_PASSWORD`, toma la llave del entorno, la pide solo si hay terminal y, si no hay ninguna, arranca igual y avisa.

**Insumos.** `backend/Dockerfile`, `application*.yml`, `.env.example`, `frontend/` (Angular 19, sin servicios), `docker-compose.yml` y `run.sh` vacíos. Docker 29.3.1 y Compose v5.0.2. Imágenes locales `postgres:16` y `postgres:16-alpine`. Si `docker compose` o una descarga fallan: «no verificado» y qué sí corrió.

**Criterio de aceptación.**
- ¿`docker compose up --build` deja base, backend y frontend sanos sin `JWT_SECRET` definido?
- ¿`run.sh < /dev/null` sin llave termina con la app arriba y un aviso claro, en vez de quedarse esperando?
- ¿Con terminal (simulada) y sin llave, `run.sh` la pide sin eco y la guarda en `.env`, y una segunda corrida no la pide ni cambia `DB_PASSWORD`?
- ¿`GET /api/system/status` responde 200 sin token a través del nginx del frontend, y 503 con la base caída?
- ¿Cada `${VAR}` de los yml y del compose figura en `.env.example`, y ningún valor es una llave real?
- ¿`mvn -B verify` pasa contra un Postgres desechable?

**Queda fuera.** Estado del proveedor y su aviso en pantalla (E8, EDT 1.7), el servicio de API del frontend, Flyway `V3`/`V4`, la semilla, el README (E15).

**Riesgos.** Que `run.sh` pise un `.env` ajeno: se prueba en una copia temporal del repo. Que el nginx acumule el SSE: se deja la configuración desde ya y se prueba en E11.

**Decisiones tomadas.** Perfil `docker` nuevo (el `dev` registra SQL y hoy actualiza el esquema; `prod` rechaza la semilla). Base sin publicar al host (el 5432 de esta máquina está ocupado). Healthcheck con un endpoint propio, sin Actuator. Frontend por hash sin configuración de rutas en nginx.

## Plan

- [x] 1. Mirar el estado actual: compilar el backend, construir el frontend y comprobar que las imágenes base existen
      Produce: lista de lo que falla hoy
      Se comprueba: `mvn -q -DskipTests package` y `npm run build` terminan; se anotan los errores que haya
  > `mvn package` y `npm run build` pasan (216,77 kB iniciales). `frontend/public` no existe y el glob de assets no estorba. Las imágenes base existen en el registro: `node:20-alpine`, `nginx:1.28-alpine`, `postgres:16.15-alpine`.
  > OJO: en esta máquina están ocupados el 80, el 5432 y el 5433.
- [x] 2. Quitar `JWT_SECRET`, abrir la cadena de seguridad, agregar el perfil `docker` y `/api/system/status`
      Produce: `application.yml`, `application-docker.yml`, `SecurityConfig`, `SystemStatusController` y su prueba
      Se comprueba: `mvn -B verify` contra `postgres:16` en un puerto libre pasa; el arranque sin `JWT_SECRET` responde 200 sin token
  > `mvn -B verify` con `JWT_SECRET` sin definir: 3 pruebas, 0 fallos, contra `postgres:16` en el 55432. Archivos: `SecurityConfig`, `SystemStatusController`, `application-docker.yml`; `application.yml` sin el bloque `security.jwt`.
  > OJO: `dev` sigue con `ddl-auto: update` hasta E2 (C5).
- [x] 3. Dockerfile del backend con `CMD`
      Produce: imagen que arranca sola
      Se comprueba: `docker run` con la base responde 200 en `/api/system/status`
  > Imagen del backend construida (2 min 15 s en frío) y con `CMD`: arranca en unos 23 s, `/api/system/status` responde 200 sin token y 503 con la base detenida. `curl` existe en la imagen.
- [x] 4. Dockerfile del frontend, `nginx.conf` y `proxy.conf.json`
      Produce: imagen nginx con Angular y proxy `/api`
      Se comprueba: la imagen sirve `index.html` y `/api/system/status` llega al backend
  > Imagen del frontend construida (3 min 28 s en frío). Por nginx: `/` 200, `/enf/pacientes` 200 (respaldo a `index.html`) y `/api/system/status` 200 con la base arriba. `proxy_buffering off` queda puesto; el SSE se prueba en E11.
- [x] 5. `docker-compose.yml` y `.env.example`
      Produce: tres servicios con salud
      Se comprueba: en una copia temporal, `docker compose up --build` deja los tres `healthy`; el `grep` de variables no encuentra faltantes
  > `docker compose up` en una copia temporal deja `db`, `backend` y `frontend` en `healthy`; la base no se publica (solo 5432 interno). `scripts/check-env-example.sh`: 19 variables revisadas, y falla si se quita una (probado). `docker compose config` sin `DB_PASSWORD` falla con mensaje claro.
- [x] 6. `run.sh`
      Produce: arranque de un comando
      Se comprueba: sin terminal y sin llave arranca y avisa; con terminal simulada la pide y la guarda; la segunda corrida conserva `DB_PASSWORD`
  > `./run.sh < /dev/null` sin llave: arriba en 1 min 34 s (con capas en caché) y aviso de la llave. Con terminal simulada (`script`) pide Gemini y Groq sin eco y guarda solo la que se escribió; la segunda corrida solo pide Groq y conserva `DB_PASSWORD`. Con el 8080 ocupado pasa al 8081. Con `.env` borrado y volumen existente falla con mensaje, sin colgarse.
  > OJO: cada corrida reconstruye y recrea el backend (el jar cambia de capa).
- [x] 7. Comprobar el criterio de aceptación y escribir `docs/decisiones.md`
      Produce: reporte del §7 del prompt y las decisiones de la etapa
      Se comprueba: cada pregunta del criterio contestada con evidencia
  > Criterio contestado abajo. `docs/decisiones.md` creado con las decisiones de la etapa; `AGENTS.md` actualizado en comandos y en la mención de `JWT_SECRET`/`API_URL`.

## El paso más riesgoso

El 5: Compose junta tres imágenes y dos esperas de salud. Si falla, se aísla por servicio (pasos 3 y 4 ya los probaron solos).

## Dónde se puede caer

| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 1 | El build del frontend falla por `assets` apuntando a `public/`, que no existe | `ng build` reporta la ruta |
| 2 | Spring Security sigue exigiendo autenticación; la prueba necesita base | 401 en `/api/system/status`; `contextLoads` falla sin Postgres |
| 3 | La imagen no trae `curl` o el jar no se llama como el glob | El healthcheck nunca pasa |
| 5 | El backend arranca antes que la base | Reinicios del backend; se resuelve con `depends_on` y `condition: service_healthy` |
| 6 | `read` sin terminal; `.env` existente pisado | `run.sh < /dev/null` no regresa |

---

# E2 — Esquema y roles

> **Estado:** en ejecución · **EDT:** 2.1 a 2.6 (8,5 h) · **Depende de:** E1 (hecha)

## Encargo

**En una frase.** Que Flyway cree en una base vacía las 16 tablas del diccionario M2 (con `bed`, `age` y `admitted_on` en `patients`) y los roles `ENFERMERIA`, `MEDICO` y `AGENTE` con sus permisos, y que Hibernate valide el mapeo de las entidades del núcleo contra ese esquema.

**Contexto.** E3 (semilla), E4 (permisos) y todo lo demás escriben y leen estas tablas. Una columna mal tipada aparece como error de `validate` al arrancar; un `CHECK` ausente deja pasar datos que la regla de negocio prohíbe.

**Qué se produce.**
1. Los dos diccionarios con las tres columnas nuevas (hecho).
2. `V3__domain_schema.sql` y `V4__seed_domain_rbac.sql`; `V1` y `V2` no se editan.
3. Entidades JPA y repositorios del núcleo (usuarios y roles, pacientes, entradas, eventos, informes, versiones, citas, semanas del mensual, ejecuciones y pasos); las entradas, eventos, versiones, citas y pasos son `@Immutable` (RN-07, RNF-10).
4. `ddl-auto: validate` también en `dev` (C5).
5. Pruebas de esquema y de repositorios.

**Criterio de aceptación.**
- ¿Flyway aplica `V3` y `V4` en una base vacía y `mvn -B verify` pasa con `ddl-auto: validate`?
- ¿La base rechaza un síntoma fuera del catálogo, un informe que no empieza en lunes, un mensual de 7 días, un `APROBADO` sin revisor y un `INCOMPLETO` sin motivo?
- ¿Solo `ENFERMERIA` tiene `REPORTS_APPROVE` y `AGENTE` solo guarda borrador (`REPORTS_CREATE_DRAFT`)?
- ¿Los repositorios escriben y leen una cadena paciente, entrada, evento, ejecución, pasos e informe con sus versiones y citas?
- ¿`entries.body` no se actualiza aunque el código lo intente?
- ¿Los dos diccionarios dicen lo mismo de `patients`?

**Queda fuera.** Semilla (E3), filtro de usuario y permisos en endpoints (E4), entidades de `entry_versions` y `audit_logs` (M2-S: las tablas sí se crean), Momento 3.

**Riesgos.** Tipos que `validate` no acepta (`SMALLINT`, `JSONB`, `NUMERIC`, `TIMESTAMPTZ`); `password_hash NOT NULL` de `V1`.

**Decisiones tomadas.** Claves foráneas como `UUID` simples y no `@ManyToOne` (sin carga perezosa fuera de transacción: el ejecutor del agente corre en otro hilo y `open-in-view` está apagado); excepción: usuario → roles → permisos, eager y pequeño. Enums de Java con `@Enumerated(STRING)` y los mismos códigos que los `CHECK`. IDs asignados por la aplicación.

## Plan

- [x] 1. Escribir `V3` (tablas y restricciones del diccionario M2 §4)
      Produce: `V3__domain_schema.sql`
      Se comprueba: Flyway la aplica sobre una base vacía sin error y `\dt` muestra 16 tablas de aplicación
  > `V3`: 11 tablas nuevas y 3 cambios a `app_users`; aplicada sobre base vacía junto con V1 y V2 (16 tablas en `public`). OJO: `reports` referencia `agent_runs`, por eso este se crea antes; `entry_versions` y `audit_logs` se crean aunque no tengan entidad.
- [x] 2. Escribir `V4` (roles y permisos del dominio con UUID fijos)
      Produce: `V4__seed_domain_rbac.sql`
      Se comprueba: 4 roles, 20 permisos; solo `ENFERMERIA` tiene `REPORTS_APPROVE`; reaplicar no duplica
  > `V4`: 3 roles y 11 permisos nuevos (20 permisos y 4 roles en total). Permisos por rol: ENFERMERIA 10, MEDICO 4, AGENTE 3 (ADMIN conserva sus 9). Solo ENFERMERIA tiene `REPORTS_APPROVE`; reaplicar `V4` no duplica (20 permisos, 26 asignaciones).
- [x] 3. Probar el esquema desde SQL
      Produce: `DomainSchemaTest`
      Se comprueba: las 8 restricciones del criterio rechazan lo que deben y aceptan el caso válido
  > `DomainSchemaTest`: 15 pruebas verdes con Flyway sobre base vacía. La base rechaza síntoma fuera del catálogo, mismo síntoma dos veces, informe que no empieza en lunes, mensual de 7 días, `APROBADO` sin revisor, `INCOMPLETO` sin motivo, segundo informe vigente del periodo y `seq` repetido; un `INCOMPLETO` no bloquea pedir otro.
- [x] 4. Escribir entidades y repositorios; `dev` pasa a `validate`
      Produce: paquetes `user`, `patient`, `entry`, `report`, `agent`
      Se comprueba: el contexto arranca con `validate` contra el esquema de Flyway
  > 7 entidades de dominio + 3 de RBAC y 10 repositorios en los paquetes `user`, `patient`, `entry`, `report`, `agent`. `dev` pasa a `validate`. El contexto arranca contra el esquema de Flyway. Comprobé que `validate` valida: al renombrar una columna a propósito el arranque falla con `SchemaManagementException`.
- [x] 5. Probar repositorios y la inmutabilidad
      Produce: `RepositoriesTest`
      Se comprueba: la cadena completa se escribe y se lee; un cambio a `entries.body` no llega a la base
  > `RepositoriesTest`: 5 pruebas verdes. Entradas del rango salen por fecha y luego por hora; un cambio a `entries.body` no llega a la base (`@Immutable`); eventos, traza JSONB, versiones, citas y semanas del mensual se escriben y se leen.
- [x] 6. Correr la verificación completa y cerrar
      Produce: reporte del §7 del prompt
      Se comprueba: `mvn -B verify` verde, cada pregunta del criterio contestada con evidencia
  > `mvn -B verify` sobre una base nueva: 23 pruebas, 0 fallos, `JWT_SECRET` sin definir; `flyway_schema_history` muestra las 4 versiones con éxito. No verificado: el arranque por Compose con V3/V4 (lo ejercita E3).

## El paso más riesgoso

El 4: `validate` es estricto con los tipos. Si un campo no coincide, se corrige el mapeo y no la migración, salvo que el diccionario esté mal (entonces se avisa).

## Dónde se puede caer

| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 1 | Orden de creación (`reports` referencia `agent_runs`); sintaxis del índice parcial | Flyway aborta con el número de línea |
| 3 | Una excepción en Postgres deja abortada la transacción de la prueba | Fallos en cascada: una prueba por restricción fallida |
| 4 | `Instant`, `JsonNode` o `Short` no coinciden con `timestamptz`, `jsonb` o `smallint` | `Schema-validation: wrong column type` al arrancar |
| 5 | El contenedor de prueba conserva datos de otra corrida | Violaciones de unicidad; se limpia con `@Transactional` |

---

# E3 — Semilla

> **Estado:** en ejecución · **EDT:** 2.7 a 2.12 y 2.14 (11 h) · **Depende de:** E2 (hecha)

## Encargo

**En una frase.** Que al arrancar con `APP_SEED_DEMO=true` (y nunca con el perfil `prod`) existan 4 usuarios de demostración sin contraseña, 5 pacientes con 18 semanas-paciente y unas 120 entradas con sus eventos ya confirmados, con fechas fijas que terminan el domingo 25-oct-2026, sin llamar al LLM y sin duplicar nada al reiniciar.

**Contexto.** Todos los casos dorados, las métricas (E7) y los informes sembrados (E14) salen de estos datos; si cambian, se invalida lo verificado a mano. La app abre con ellos en la demo (D26).

**Qué se produce.**
1. `backend/src/main/resources/seed/demo-data.json`: los datos, legibles y revisables a mano (sintéticos).
2. Un `ApplicationRunner` idempotente que los inserta con UUID deterministas y `ON CONFLICT DO NOTHING`, en una sola transacción.
3. Un guardia que hace fallar el arranque si `prod` y la semilla coinciden.
4. Pruebas de conteos (D11), de idempotencia y de `prod`.

**Reparto (D11).** P-014 (cinco semanas; el síntoma que reaparece: náuseas en las semanas 1, 3 y 5) · P-021 (cuatro; fiebre en la semana 3) · P-007 (tres; días sin registro y una semana con tres entradas) · P-033 (cuatro; sin síntomas y con negaciones, un solo evento) · P-040 (dos; una nota con una instrucción dentro del texto).

**Criterio de aceptación.**
- ¿Existen 4 usuarios (2 `ENFERMERIA`, 2 `MEDICO`), 5 pacientes, 18 semanas-paciente y entre 115 y 125 entradas?
- ¿P-014 tiene náuseas en las semanas 1, 3 y 5 y en ninguna de la 2 y la 4? ¿P-007 tiene tres semanas con 5, 3 y 6 entradas? ¿P-033 tiene un solo evento y textos con negaciones? ¿P-040 tiene la nota con la instrucción?
- ¿Cada evento es del catálogo de nueve, está confirmado por una enfermera y ninguna entrada trae datos reales?
- ¿Correr la semilla dos veces deja los mismos conteos por tabla?
- ¿Con el perfil `prod` y `APP_SEED_DEMO=true` el arranque falla con un mensaje claro, y sin `APP_SEED_DEMO` no se siembra nada?
- ¿Por Compose (perfil `docker`) quedan los datos sembrados y reiniciar no los duplica?

**Queda fuera.** Los 5 informes sembrados (E14), credenciales (Momento 3), llamar al LLM.

**Riesgos.** Que un texto sembrado diga algo que su evento no recoge (o al revés) y falsee los casos dorados; que los datos parezcan reales. Se revisa la tabla de síntomas por semana contra los textos.

**Decisiones tomadas.** Datos en JSON y no en código: se revisan sin leer Java. IDs de entradas y eventos derivados del contenido (`UUID.nameUUIDFromBytes`), para que E14 pueda citarlas sin tablas de ids. Inserción con `JdbcTemplate` y `ON CONFLICT DO NOTHING`: es una carga masiva, no pasa por las entidades inmutables. El autor de cada entrada depende del día de la semana (enfermería cinco de siete días, un médico los otros dos); los eventos los confirma siempre una enfermera, como pide el diccionario.

## Plan

- [x] 1. Generar `demo-data.json` y comprobar sus conteos contra D11
      Produce: el JSON y una tabla de síntomas por paciente y semana
      Se comprueba: 5 pacientes, 18 semanas-paciente, 115 a 125 entradas, náuseas de P-014 en 2-0-3-0-4 días por semana
  > `demo-data.json`: 5 pacientes, 18 semanas-paciente, 119 entradas y 41 eventos. Entradas por semana: P-014 7·7·7·7·7, P-021 7·7·7·7, P-007 5·3·6, P-033 7·7·7·7, P-040 7·7. Días con náuseas de P-014 por semana: 2·0·3·0·4. P-033 tiene 1 evento y 20 de 28 textos con negación. La nota con la instrucción está en P-040, el 21-oct.
  > OJO: la primera versión tenía intensidades («moderado», «leve») que el texto no decía (RN-11); se corrigieron los textos y un chequeo automático ya no encuentra inconsistencias entre texto, intensidad y veces.
- [x] 2. Escribir el sembrador y su guardia de `prod`
      Produce: `DemoDataSeeder`, registros de lectura del JSON, `app.seed.demo` en `application.yml`
      Se comprueba: compila y el contexto arranca con la semilla activa contra `postgres:16`
  > `DemoDataSeeder` (`seed/`), `app.seed.demo` en `application.yml`. Los ids de entradas y eventos son deterministas (`entryId(alias, fecha)`), públicos para que E14 cite las mismas entradas. Corre en una sola transacción y falla con un mensaje claro si el perfil es `prod`.
- [x] 3. Probar conteos, idempotencia y `prod`
      Produce: `DemoDataSeederTest`
      Se comprueba: los conteos del criterio; dos corridas iguales; `prod` falla; sin la propiedad no hay filas nuevas
  > 10 pruebas verdes (9 de la semilla y 1 sin bandera): 2 enfermeras y 2 médicos sin contraseña; 5 pacientes; 18 semanas; 119 entradas; última fecha 2026-10-25; todas a las 18:00 de Bogotá; náuseas en las semanas 1, 3 y 5; P-007 con 5·3·6; P-033 con 1 evento; nota de P-040 con su evento real (`VOMITO`); eventos confirmados solo por enfermería; dos corridas más no cambian ningún conteo; `prod` lanza `IllegalStateException`; sin la bandera no existe el sembrador.
- [x] 4. Arrancar por Compose en una copia temporal y reiniciar
      Produce: evidencia con `psql` del contenedor
      Se comprueba: mismos conteos tras `docker compose restart backend`
  > Por Compose (perfil `docker`): usuarios 4, pacientes 5, entradas 119, eventos 41. Tras `docker compose restart backend` los mismos conteos (el sembrador corrió dos veces, según el log) y `/api/system/status` 200. Con esto queda verificado que `validate` y las migraciones V3/V4 también funcionan bajo `docker`.
- [x] 5. Cerrar la etapa
      Produce: reporte del §7, decisiones en `docs/decisiones.md`
      Se comprueba: cada pregunta del criterio contestada con evidencia
  > Reporte abajo; decisiones en `docs/decisiones.md`.

## El paso más riesgoso

El 1: son unas 120 entradas escritas a mano; un error aquí se arrastra a E7, E10 y E14. Por eso el paso termina con una tabla que se compara contra el reparto de D11, y cada texto con síntoma lleva su evento.

## Dónde se puede caer

| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 1 | Una semana que no empieza en lunes; una negación sembrada como evento | La tabla de semanas no cuadra; P-033 con más de un evento |
| 2 | La zona horaria cambia el día de `created_at`; el JSON no se lee con UTF-8 | Fechas corridas; «Ã³» en los textos |
| 3 | Las pruebas dejan filas en la base compartida | Conteos distintos entre corridas |
| 4 | El perfil `docker` falla por `validate` con las columnas nuevas | El backend no queda `healthy` |

---

# E4 — Sesión y permisos

> **Estado:** en ejecución · **EDT:** 3.1 y 3.2 (4 h) · **Depende de:** E3 (hecha)

## Encargo

**En una frase.** Que cada petición a `/api/**` (salvo el estado y la lista de usuarios de demostración) lleve el usuario elegido, que el servidor tome de él su rol y sus permisos, y que cada permiso del §3.4 del diccionario M2 se pueda exigir en un endpoint y se niegue a quien no lo tiene.

**Contexto.** D26: sin autenticación en el Momento 2, pero solo enfermería aprueba y el médico solo ve lo aprobado; eso lo decide el servidor, no la pantalla (RNF-01). E5 necesita la lista de usuarios y la sesión actual; E6 en adelante necesita los permisos en los endpoints.

**Qué se produce.**
1. `GET /api/demo-users` (público): id, nombre y rol de los usuarios de demostración activos.
2. `GET /api/session`: usuario, rol y permisos de quien hace la petición.
3. Un filtro que lee la cabecera `X-Demo-User` (y el parámetro `demoUser` solo en la ruta SSE `/api/runs/*/events`, donde `EventSource` no envía cabeceras) y deja en el contexto el usuario con sus permisos como autoridades.
4. `/api/**` exige usuario; `@PreAuthorize("hasAuthority('…')")` queda habilitado para los endpoints de las etapas siguientes; errores 401 y 403 en JSON, en español, sin rastro de pila.
5. Pruebas: la matriz de permisos escrita a mano contra los usuarios sembrados, y los casos sin usuario, con uno inválido o desconocido.

**Criterio de aceptación.**
- ¿Una petición a `/api/**` sin usuario elegido, con un id mal formado o con uno desconocido recibe 401 con mensaje claro, y `status` y `demo-users` siguen abiertos?
- ¿Cada usuario sembrado puede ejercer exactamente los permisos que dice el diccionario M2 §3.4 para su rol y ninguno más (probado permiso por permiso con una matriz escrita en la prueba, no leída de la base)?
- ¿Falta de permiso da 403, distinto de 401?
- ¿El parámetro `demoUser` se acepta solo en la ruta SSE?
- ¿Un usuario inactivo o que no es de demostración no puede hacerse pasar por sesión?

**Queda fuera.** Endpoints de pacientes, entradas e informes (E6 en adelante; allí se aplica `@PreAuthorize` y se prueban sus reglas, incluida «el médico no aprueba»), el rol `AGENTE` como sesión (no inicia sesión), JWT y contraseñas (Momento 3).

**Riesgos.** Que parezca seguridad: cualquiera puede mandar el id de una enfermera. Está declarado en D26 y se repite en el README y en la defensa.

**Decisiones tomadas.** Cabecera `X-Demo-User` con el UUID. El filtro no es un bean (si lo fuera, el contenedor lo registraría dos veces). La sonda de la matriz vive solo en las pruebas.

## Plan

- [x] 1. Escribir el filtro, la sesión, los manejadores de 401 y 403 y los endpoints de usuarios y sesión
      Produce: paquete `session` y `SecurityConfig` actualizada
      Se comprueba: compila y el contexto arranca
  > Paquete `session` (`DemoUserFilter`, `DemoPrincipal`, `SessionController`, `ApiError`) y `SecurityConfig` con `@EnableMethodSecurity`: `/api/system/status` y `/api/demo-users` públicos, el resto de `/api/**` exige usuario; 401 y 403 en JSON. El filtro no es un bean.
- [x] 2. Probar la matriz de permisos con una sonda
      Produce: `PermissionMatrixTest`
      Se comprueba: 2 usuarios × 11 permisos coinciden con la matriz escrita; faltante da 403
  > `PermissionMatrixTest`: 3 pruebas; 4 usuarios sembrados × 11 permisos coinciden con la matriz escrita a mano (enfermería 10, médico 4; `REPORTS_CREATE_DRAFT` no es de nadie que elija sesión).
- [x] 3. Probar los casos de identidad y la ruta SSE
      Produce: `SessionEndpointsTest` y `DemoUserFilterTest`
      Se comprueba: 401 sin usuario, mal formado, desconocido o inactivo; `demoUser` solo en la ruta SSE
  > `SessionEndpointsTest` (9) y `DemoUserFilterTest` (3): 401 sin usuario, mal formado, desconocido, no demo e inactivo; 403 distinto de 401, con mensaje y sin traza; la lista pública no trae correo ni hash; `OPTIONS` de CORS pasa sin usuario; `demoUser` solo vale en `GET /api/runs/*/events`.
  > OJO: la prueba unitaria destapó que `AntPathRequestMatcher` depende de `servletPath`, que puede llegar vacío; se compara `getRequestURI`. La ruta SSE real se prueba otra vez en E11.
- [x] 4. Correr la verificación completa y cerrar
      Produce: reporte del §7
      Se comprueba: `mvn -B verify` verde y cada pregunta del criterio contestada
  > `mvn -B verify`: 48 pruebas, 0 fallos, `JWT_SECRET` sin definir. La sonda de permisos no está en el jar (0 coincidencias).

## El paso más riesgoso

El 1: un orden equivocado de filtros deja pasar sin usuario o bloquea la petición previa de CORS.

## Dónde se puede caer

| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 1 | El filtro corre antes del de CORS; el 401 sale en HTML | Prueba de `OPTIONS` falla; el cuerpo no es JSON |
| 2 | La sonda de pruebas se registra también en producción | Falla `mvn package`; no debe estar en `src/main` |
| 3 | Un usuario no demo con id válido entra | La prueba de usuario inactivo lo detecta |

---

# E5 — Frontend: sistema de diseño y shell

> **Estado:** cerrada · **EDT:** 3.4 (2 h; subestimada, C6) · **Depende de:** E4 (hecha) · **Nota:** el encargo y el plan de esta etapa los escribí al cerrarla, no antes de empezar; el criterio de aceptación sí sale de la columna «Cierre» del prompt.

## Encargo

**En una frase.** Portar del mockup A el sistema de diseño y el armazón de la aplicación a Angular 19, sin rediseñar nada, con rutas con hash, sesión de demostración e interceptor, de modo que las capturas del armazón a 1280×800 y a 390×844 no difieran del mockup salvo en lo de la tabla 3.3.

**Criterio de aceptación.**
- ¿Las capturas del armazón (menú, encabezado, tarjeta de usuario) a 1280 y a 390 no muestran diferencias de maquetación, tipografía, color o espaciado con el mockup, salvo contenido y tabla 3.3?
- ¿Se cambia de enfermera con «Ver como» y el servidor recibe el nuevo usuario en cada petición?
- ¿El CSS es el del mockup, copiado y no reescrito, y no hay librerías de interfaz, fuentes ni iconos externos?
- ¿Están las rutas con hash del mockup, la ruta por defecto `#/enf/pacientes` y «No encontramos lo que busca» para lo desconocido?
- ¿Existen, con la estructura de DOM del mockup, `icono`, `btnProceso`/`conProceso`, `menuLateral`, `listado`, `estado`, `toast` y `vacioHTML`?

**Queda fuera.** Las pantallas (E6 en adelante); `hojaHTML`, `filaSintoma` y `vistaPrevia`, que se portan con su pantalla (E9, E12b); campana, administrador y médico.

## Plan

- [x] 1. Copiar el CSS del mockup y su catálogo de iconos
      Produce: `styles.css` (líneas 8-194 y 317-481 del mockup, sin tocar) y `core/icons.ts` (23 iconos)
      Se comprueba: el CSS se genera con `sed` por rangos, no se retipea
  > 355 líneas de CSS idénticas a las del mockup; los 15 iconos base y los 8 de módulo salen del mismo archivo por script.
- [x] 2. Portar los componentes de interfaz
      Produce: `IconComponent` (`svg[appIcon]`), `ProcessButtonComponent` (`button[appProcessButton]`), `EmptyStateComponent`, `StatusChipComponent`, `ListingComponent` con `appCell`, `ToastService`, `date-es.ts`
      Se comprueba: pruebas unitarias de estructura y comportamiento
  > Los componentes de atributo (`svg[…]`, `button[…]`, `nav[…]`) conservan el DOM del mockup: el elemento es el propio `<svg class="icon">`, `<button class="btn">` y `<nav class="sb">`. Los de contenido llevan `display: contents`.
- [x] 3. Portar el menú lateral
      Produce: `SidebarComponent`
      Se comprueba: `aria-current`, grupo desplegado, contraer, grupo contraído, tarjeta y Escape
  > Sin «Mi perfil», «Preferencias» ni «Cerrar sesión» (3.3); las filas Área, Cargo y Último acceso solo aparecen con dato; el correo, solo si existe.
- [x] 4. Sesión, interceptor, armazón y rutas con hash
      Produce: `SessionService`, `demoUserInterceptor`, `AppComponent`, `app.routes.ts`, `app.config.ts`
      Se comprueba: el servidor recibe `X-Demo-User`; ruta por defecto y no encontrada
  > Las 8 rutas del mockup más `#/enf/pacientes/nuevo` (alta, 3.4), todas con `PendingComponent` hasta su etapa. «Ver como» solo ofrece enfermeras.
- [x] 5. La fecha de hoy la da el servidor
      Produce: `today` en `GET /api/system/status` (America/Bogota)
      Se comprueba: prueba del backend; F7 (el navegador no decide qué día es)
  > `mvn -B verify`: 48 pruebas, 0 fallos.
- [x] 6. Pruebas unitarias
      Produce: 8 archivos `.spec.ts`
      Se comprueba: `ng test` con cobertura
  > 31 pruebas, 0 fallos; cobertura de líneas 96,79 %, de ramas 75,51 %.
- [x] 7. Verificación visual F8 y cierre
      Produce: capturas del mockup y de la app a 1280×800 y a 390×844, fuera del repositorio
      Se comprueba: diferencias listadas y contenido distinto aparte
  > Con backend real y `ng serve` (proxy a 8080). Único cambio fuera del contenido: «Ver como» arranca en la primera enfermera por orden alfabético (Camilo Torres), no en Marcela Rojas. `npm run build` de producción: 300,10 kB iniciales, sin advertencias de presupuesto.

---

# E6 — Pacientes y entradas

> **Estado:** cerrada · **EDT:** 4.1 a 4.4 (6,5 h) · **Depende de:** E4 y E5 (hechas) · **Nota:** esta vez el encargo y el plan se escriben antes de empezar.

## Encargo

**En una frase.** Que enfermería pueda crear pacientes, ver la lista y la ficha, y consultar el historial de entradas, con una API que guarda el texto de la entrada tal como se escribió y lista por paciente, por rango y en orden, y con las cuatro pantallas fieles al mockup A.

**Criterio de aceptación.**
- ¿El texto guardado de una entrada es idéntico byte a byte al enviado (espacios, saltos de línea, tildes y emojis incluidos)?
- ¿Con entradas cargadas desordenadas, el listado de un paciente sale en orden cronológico, el filtro por rango devuelve solo las del rango y, el mismo día, se ordenan por hora de creación (RF-04, RN-09)?
- ¿Se crea un paciente desde la pantalla de alta, aparece en la lista y su ficha abre? ¿Un alias repetido o vacío se rechaza con un mensaje claro y sin stack trace?
- ¿Cada endpoint exige su permiso (el médico no crea pacientes; sin usuario elegido es 401) y el médico nunca recibe el estado de un informe que no esté `APROBADO` (RN-01)?
- F8: ¿las capturas de lista, ficha, alta e historial a 1280×800 y a 390×844 no difieren del mockup salvo contenido y tabla 3.3?

**Queda fuera.**
- La pantalla «Nuevo registro diario» y la estructuración con IA (E9); en E6 el botón «Nuevo registro» lleva a la ruta, que sigue siendo el marcador.
- Editar entradas y sus versiones (RF-03, M2-S).
- Auditoría (RF-35, M2-S).
- Editar o archivar pacientes.
- Que la entrada se guarde con eventos: la API de E6 guarda solo el texto (`SIN_ESTRUCTURAR`); E9 la extiende para confirmar eventos.

## Plan

- [x] 1. Base común del backend
      Produce: `V5` (alias único sin distinguir mayúsculas), bean `Clock` en `America/Bogota`, `ApiExceptionHandler` (validación → 400 con mensaje por campo; no encontrado → 404; cuerpo ilegible → 400; sin stack trace) y `ApiError` con campos
      Se comprueba: `DomainSchemaTest` rechaza un alias repetido; prueba del manejador
  > `ApiError` pasó a `{ message, fields? }`. `SystemStatusController` usa el mismo reloj. Un fallo no previsto responde 500 con mensaje genérico; AccessDenied y Authentication se relanzan para que los resuelva la cadena de seguridad (401 y 403 siguen igual).
- [x] 2. API de pacientes
      Produce: `GET /api/patients`, `GET /api/patients/{id}`, `POST /api/patients` con `@PreAuthorize`; último registro y último informe, este último filtrado por rol (el médico solo `APROBADO`)
      Se comprueba: pruebas de MockMvc con la semilla: conteo, alta, duplicado, permisos, regla del médico con un informe pendiente insertado
  > 10 pruebas en `PatientsApiTest`. Un informe `PENDIENTE_REVISION` y otro `APROBADO` insertados a mano muestran que enfermería ve el más reciente y el médico solo el aprobado.
- [x] 3. API de entradas
      Produce: `POST /api/patients/{id}/entries` (guarda `body` sin tocarlo, `SIN_ESTRUCTURAR`, autor = usuario elegido), `GET /api/patients/{id}/entries?from&to&order&limit` y `GET /api/entries` (historial)
      Se comprueba: pruebas de texto idéntico, orden, rango, varias el mismo día, fecha futura, paciente inexistente, permisos
  > 10 pruebas en `EntriesApiTest`. El texto de prueba lleva espacios al inicio y al final, tabulador, saltos de línea, tildes y emoji; vuelve idéntico de la respuesta, de la base y del listado. Repetido por HTTP real a través del proxy de `ng serve`.
- [x] 4. Servicios Angular, ayudantes y tarjeta de registro
      Produce: `PatientsService`, `EntriesService`, `periodoTxt` en `date-es.ts`, `EntryCardComponent` (`regCard`)
      Se comprueba: pruebas unitarias
  > Sin `FormsModule`: el alta usa señales y eventos. Las respuestas del servidor pasan por `apiFailure`, que nunca muestra texto crudo.
- [x] 5. Pantallas lista y ficha
      Produce: `PatientsListComponent`, `PatientDetailComponent` con rutas reales
      Se comprueba: pruebas unitarias con respuestas simuladas del servidor
  > Un paciente inexistente o un id mal formado muestra «No encontramos lo que busca». Sin cama, edad ni ingreso no se inventa la línea de datos.
- [x] 6. Pantallas alta e historial
      Produce: `PatientFormComponent` (patrón `vUsuForm`, errores por campo), `EntryHistoryComponent`
      Se comprueba: pruebas unitarias; `ng test` y `npm run build` limpios
  > `ng test`: 57 pruebas, 0 fallos (31 de E5 + 26 nuevas), líneas 96,84 %, ramas 77,87 %. `npm run build`: 330,47 kB iniciales, sin advertencias.
- [x] 7. F8 y cierre
      Produce: capturas del mockup y de la app a 1280×800 y a 390×844 de las cuatro pantallas, fuera del repositorio; documentos al día
      Se comprueba: diferencias listadas; `mvn -B verify` y `ng test` verdes
  > Con backend real, datos sembrados y `ng serve`. Para ver la tarjeta «Último informe» inserté dos informes a mano en la base desechable y los borré al terminar (la semilla de informes llega en E14). Una diferencia real, la fecha partida en dos líneas en el historial, se corrigió con `nowrap` en la celda. `mvn -B verify`: 69 pruebas, 0 fallos.

---

# E7 — Métricas por semana

> **Estado:** cerrada · **EDT:** 6.1 a 6.3 (6 h) · **Depende de:** E3 (hecha) · **Riesgo del plan maestro:** «reaparecido» sin definición exacta.

## Encargo

**En una frase.** Calcular en código, por paciente y por semana (lunes a domingo), las cifras que el informe va a citar: días con y sin entrada, eventos por síntoma, días con cada síntoma, variación frente a la semana anterior y síntomas nuevos, persistentes, desaparecidos y reaparecidos (RF-07, RN-04).

**Criterio de aceptación.**
- ¿Las cifras de cada semana de los cinco pacientes sembrados coinciden con las de un cálculo independiente hecho a partir del JSON de la semilla, incluido el síntoma que reaparece en P-014 (náuseas: 2·0·3·0·4 días)?
- ¿Una semana sin entradas, o con la anterior sin entradas, declara que no se puede comparar en vez de inventar «desaparecidos»?
- ¿Las entradas `SIN_ESTRUCTURAR` se cuentan aparte, para que el informe las declare (RN-08)?
- ¿Ninguna cifra la calcula el modelo ni sale de un texto (RN-04), y una fecha que no es lunes se rechaza?

**Definiciones (propuesta, no estaban en ningún documento).** Un síntoma está *presente* en una semana si tiene al menos un evento confirmado en algún día de ella. Frente a la semana anterior W-1:
- *persistente*: presente en W y en W-1;
- *desaparecido*: presente en W-1 y no en W;
- *nuevo*: presente en W, ausente en W-1 y nunca antes en el historial del paciente;
- *reaparecido*: presente en W, ausente en W-1 y presente en alguna semana anterior a W-1.
Si W-1 no tiene ninguna entrada, no se puede decir que un síntoma «dejó de estar»: la comparación queda `comparable = false` y las cuatro listas vacías.

**Queda fuera.** Endpoint HTTP (las usan el agente en E10 y los informes en E12; no hay requisito de exponerlas), la lista de semanas con entradas (E10/E12a), el informe mensual (suma semanas ya calculadas) y la intensidad más allá del máximo de la semana.

## Plan

- [x] 1. Cálculo puro
      Produce: paquete `metrics` con `WeeklyMetrics` (registros) y `MetricsCalculator`, sin base de datos ni reloj
      Se comprueba: pruebas unitarias con casos pequeños hechos a mano (nuevo, persistente, desaparecido, reaparecido, hueco, no comparable, sin estructurar, lunes)
  > 12 pruebas, 0 fallos. Un caso extra: dos eventos del mismo síntoma el mismo día son 1 día y 2 eventos.
- [x] 2. Lectura de datos y servicio
      Produce: consultas en `EntryEventRepository` y `MetricsService.weekly(patientId, weekStart)`
      Se comprueba: paciente inexistente y fecha que no es lunes se rechazan
  > Dos consultas por llamada más una de historial; `weekly` lee la semana y la anterior y los síntomas de antes de ella.
- [x] 3. Cálculo independiente de referencia
      Produce: `scripts/expected_metrics.py` (otro lenguaje, lee solo el JSON de la semilla) y su salida como fixture de pruebas
      Se comprueba: la tabla de P-014 se revisa a ojo contra 2·0·3·0·4
  > P-014: 2, 0, 3, 0, 4 días con náuseas; «reaparecido» en las semanas 3 y 5 y «desaparecido» en la 2 y la 4. Son 18 semanas-paciente (5, 4, 3, 4 y 2). La primera semana de cada paciente no es comparable.
- [x] 4. Prueba contra los cinco pacientes
      Produce: prueba de integración que compara cada semana de cada paciente sembrado con el fixture
      Se comprueba: 18 semanas-paciente iguales; `mvn -B verify` verde
  > 5 pruebas de integración, 0 fallos. Comprobé que fallan si se rompe la regla de «reaparecido» (4 fallos) y restauré el código.
- [x] 5. Documentos y cierre
      Produce: definiciones en `docs/decisiones.md`; estructura de `metrics` en el diccionario si cambia
      Se comprueba: sin contradicciones con RF-07 y RN-04
  > Las definiciones son propuesta: quedan marcadas así en `docs/decisiones.md` para que el equipo las confirme.

---

# E8 — Capa de proveedor del LLM

> **Estado:** cerrada · **EDT:** 7.1 a 7.6 y 1.7 (12,5 h) · **Depende de:** E1 · **Cambio de entrada:** el equipo invirtió el orden el 2026-10-28: **Groq es el principal y Gemini el respaldo**.

## Lo medido con las llaves reales (2026-10-28)

| Dato | Resultado |
|---|---|
| Límites de Groq en esta cuenta (encabezados y documentación) | `openai/gpt-oss-120b` y `openai/gpt-oss-20b`: **30 solicitudes/min, 1 000/día, 8 000 tokens/min, 200 000 tokens/día**, cada modelo con su propia cuota. En un 429 llega `retry-after` en segundos |
| Límites de Gemini | Google no los publica por modelo (solo se ven en la consola de AI Studio). **No verificados**; se tratará el 429 como en Groq |
| Groq, salida estructurada | Con `json_schema` estricto devuelve el catálogo y los valores correctos; con `json_object` suelto devolvió «dolor de cabeza» y «intensa», fuera del catálogo. Se usa el esquema estricto |
| Groq, herramientas y razonamiento | Llama a la herramienta con los argumentos correctos. `gpt-oss` gasta tokens en razonar (330 a 536 en una tarea corta); con `reasoning_effort: low` bajó a 7 a 20 |
| Gemini | `gemini-3.5-flash-lite`: 0,7 s, herramientas y esquema correctos. `gemini-3.8-flash`: un 503 y un tiempo agotado a los 40 s; `gemini-3.5-flash`: 22 s para «ok». Se usa `gemini-3.5-flash-lite` |
| Respaldo a mitad de un bucle | Gemini responde 400 si una llamada a herramienta del historial no trae firma de pensamiento (porque la hizo otro proveedor); con el valor comodín documentado `skip_thought_signature_validator` funciona |
| Esquemas | Los dos proveedores aceptan JSON Schema estándar (`response_format` en Groq; `responseJsonSchema` y `parametersJsonSchema` en Gemini): un solo esquema para ambos |
| Precios de referencia (USD por millón de tokens, entrada/salida) | Groq `gpt-oss-120b` 0,15/0,60; `gpt-oss-20b` 0,075/0,30; Gemini 3.5 Flash-Lite 0,30/2,50 (de pago; la capa gratuita no cobra). El «costo» que se registra es el equivalente a precio de lista |

**Consecuencia para el agente (E10).** El tope de 8 000 tokens por minuto de Groq obliga a que cada llamada del bucle sea pequeña y a que la pasarela espere o conmute en un 429; se vigila en E10.

## Encargo

**En una frase.** Una capa que desacopla el modelo del resto de la aplicación: un puerto, un adaptador de Groq (principal) y uno de Gemini (respaldo) por HTTP sin SDK, selección por configuración, reintentos con espera de 1, 2 y 4 s, conmutación al respaldo, mensajes honestos, validación de la salida con un reintento y registro de proveedor, modelo, tokens y costo.

**Criterio de aceptación.**
- ¿Una llamada real a Groq y otra a Gemini devuelven JSON que valida contra el esquema, y una llamada con herramientas devuelve la herramienta pedida?
- ¿Cambiar `LLM_PROVIDER` y `LLM_FALLBACK` cambia el orden de los proveedores sin tocar código, con el procedimiento escrito?
- ¿Cada uno de los tres fallos se prueba con una prueba automática y se comporta así: 429 o caída reintenta con 1, 2 y 4 s y conmuta; tiempo agotado reintenta y conmuta; salida inválida se corrige una vez y, si vuelve a fallar, avisa para degradar a texto plano?
- ¿Un fallo llega a quien llama con un mensaje honesto en español, sin rastro de pila ni cuerpo crudo del proveedor?
- ¿Sin llave de ningún proveedor la aplicación arranca, un endpoint lo dice y una llamada falla con un mensaje claro en vez de un error crudo?
- ¿Cada llamada deja proveedor, modelo, tokens de entrada y salida, costo, latencia y los intentos (para `agent_runs` y `agent_steps`)?

**Queda fuera.** Fallos forzables en vivo y `LLM_FAULT` (decisión del equipo), las instrucciones y el esquema de cada tarea (E9 y E10), el bucle del agente, voz (Whisper) y cualquier SDK de proveedor.

## Plan

- [x] 1. Contrato de la capa
      Produce: paquete `llm` con el puerto `LlmClient`, mensajes, herramientas, esquema, respuesta, `LlmException` con sus clases de fallo y mensajes en español, y `LlmProperties`
      Se comprueba: compila y la configuración enlaza con valores por defecto sin llave
  > Mensajes, historial y herramientas no llevan nada de un proveedor. `Attempt` y `LlmResult` traen lo que piden `agent_runs` y `agent_steps`.
- [x] 2. Adaptador de Groq
      Produce: `GroqClient` (compatible con OpenAI, `json_schema` estricto, herramientas, `reasoning_effort`)
      Se comprueba: pruebas contra un servidor HTTP local de la JDK: éxito, herramientas, 429 con `retry-after`, 5xx, tiempo agotado real, salida inválida (`json_validate_failed`), llave rechazada
  > 12 pruebas. El tiempo agotado es real: el servidor tarda 800 ms y el cliente espera 150. Un 413 por exceso de tokens por minuto no se reintenta.
- [x] 3. Adaptador de Gemini
      Produce: `GeminiClient` (`generateContent`, firma comodín para historiales de otro proveedor)
      Se comprueba: las mismas pruebas, más el historial con herramientas sin firma
  > 11 pruebas. Varias respuestas de herramienta seguidas se agrupan en un turno del usuario, como pide Gemini.
- [x] 4. Pasarela
      Produce: `LlmGateway` con reintentos 1, 2 y 4 s (respeta `retry-after` hasta un tope), conmutación, intentos registrados, costo y salida estructurada con un reintento corregido
      Se comprueba: pruebas con adaptadores de prueba y un reloj falso: cada uno de los tres fallos, sin llave, y los dos proveedores caídos
  > 23 pruebas. Con 429, caída o tiempo agotado el principal se intenta 4 veces con esperas de 1, 2 y 4 s y luego pasa el respaldo; con `retry-after` de 30 s conmuta sin esperar; con 3 s espera 3 s.
- [x] 5. Estado y variables
      Produce: `GET /api/llm/status`, `LLM_PROVIDER`, `LLM_FALLBACK` y los modelos en `application.yml`, `.env.example` y el compose
      Se comprueba: `scripts/check-env-example.sh`; el endpoint no devuelve llaves; sin llave dice qué falta
  > 7 pruebas. El `.env.example`, el compose y el `application.yml` quedaron con las mismas variables (24 revisadas por el script).
- [x] 6. Verificación con llaves reales
      Produce: una prueba opcional (solo con llave) y la corrida de las llamadas reales por la propia capa
      Se comprueba: JSON válido de Groq y de Gemini, herramientas, y el cambio de orden por variable
  > `LlmLiveTest`, 7 de 7 con llamadas reales. Una primera corrida falló: con `gpt-oss-20b` la estructuración omitía «comió poco». Medí 14 de 14 con `gpt-oss-120b` en razonamiento medio y cambié el modelo por defecto. Reinicié el backend con las dos órdenes de proveedor y sin llaves: el estado lo refleja y las llaves no aparecen en los registros.
- [x] 7. Documentos y cierre
      Produce: `docs/decisiones.md` (proveedor, modelos, razón, límites, alternativas) y procedimiento de cambio de proveedor
      Se comprueba: cada cifra con su origen y fecha; lo no verificado, dicho
  > Quedan escritos el procedimiento de cambio, el riesgo de la cuota compartida y que los límites de Gemini no están verificados.

---

# E9 — Estructuración al registrar

> **Estado:** cerrada · **EDT:** 5.1 a 5.7 (13,5 h) · **Depende de:** E6 y E8 (hechas) · **Decisiones del equipo (2026-10-28):** intensidades las del mockup, botón «Aprobar y guardar», catálogo de nueve síntomas y «Descartar» cierra la vista previa sin guardar; el «texto corregido» queda fuera.

## Encargo

**En una frase.** Al registrar una entrada, una sola llamada corta al modelo (sin herramientas, en la misma petición, D27) propone los síntomas que el texto dice, con intensidad y veces; la persona los confirma, edita, quita, agrega o descarta en la pantalla «Así quedaría el registro», y solo lo confirmado se guarda, junto con el texto tal como se escribió.

**Criterio de aceptación.**
- Con «Dolor de cabeza intenso desde la mañana. Tuvo diarrea intensa dos veces. Comió poco.», ¿la propuesta trae dolor de cabeza (intenso), diarrea (intensa, 2 veces) y apetito bajo (sin indicar) y valida contra el esquema?
- ¿«Sin vómito» no crea evento, y una nota que trae una instrucción se trata como dato?
- ¿Proponer no guarda nada, y confirmar guarda la entrada y los eventos en una sola transacción, con quién los confirmó y su origen (`IA`, `EDITADO`, `MANUAL`)?
- ¿Si la IA falla (caída, 429, tiempo agotado o salida que no valida tras un reintento) la persona ve un mensaje claro, no pierde lo escrito y puede guardar solo el texto (la entrada queda `SIN_ESTRUCTURAR`) o agregar los síntomas a mano?
- ¿Cada propuesta deja su traza `ESTRUCTURACION` (proveedor, modelo, tokens, costo, intentos) y nada del paciente salvo el texto sale al proveedor?
- F8: ¿la pantalla, con la vista previa y con el fallo, a 1280×800 y a 390×844, no difiere del mockup salvo contenido y tabla 3.3?

**Queda fuera.** El «texto corregido» (RF-42) y su campo, mostrar la traza en una pantalla (la API de ejecuciones llega en E11), el dictado por voz, editar una entrada ya guardada (RF-03) y la auditoría (RF-35).

## Plan

- [x] 1. Presupuesto total de una llamada síncrona
      Produce: `budget` en `LlmRequest` y en la pasarela: ningún reintento ni espera pasa del presupuesto, y cada llamada se acorta a lo que queda
      Se comprueba: pruebas de la pasarela con reloj falso: con presupuesto agotado no reintenta y falla con mensaje honesto
  > Presupuesto de 30 s en `LlmRequest` y en la pasarela. Una espera que ya no cabe corta el reintento y no se prueba el respaldo con tiempo agotado. 4 pruebas nuevas.
- [x] 2. Propuesta de eventos
      Produce: esquema y prompt de la estructuración (catálogo, negaciones, texto como dato), validador, `StructuringService.propose` y la traza `ESTRUCTURACION` en `agent_runs` y `agent_steps`
      Se comprueba: pruebas con la pasarela simulada: la petición al modelo no lleva alias, cama ni id del paciente; cada fallo termina con mensaje; la traza queda
  > 13 pruebas (6 del prompt y 7 del servicio). La petición al modelo lleva el texto entre etiquetas y no lleva alias, cama, edad ni id.
- [x] 3. API de proponer y confirmar
      Produce: `POST /api/patients/{id}/entries/propose` (no guarda) y `POST /api/patients/{id}/entries` con eventos opcionales (entrada y eventos en una transacción; sin eventos, `SIN_ESTRUCTURAR`)
      Se comprueba: pruebas de MockMvc: proponer no crea entradas; confirmar guarda todo o nada; síntoma fuera del catálogo y repetido se rechazan; permisos
  > 10 pruebas. Un evento inválido deja la base como estaba (ni la entrada). Los eventos se envían con `saveAllAndFlush` para que una restricción de la base falle dentro del servicio.
- [x] 4. Prueba con el modelo real
      Produce: pruebas opcionales (`RUN_LIVE_LLM=true`) con el ejemplo, la negación y una nota con instrucción
      Se comprueba: ejemplo exacto, sin vómito, y la instrucción no cambia la salida
  > `StructuringLiveTest`: 5 casos × 2 proveedores × 3 repeticiones = 30 llamadas reales, todas exactas, también con la nota sembrada de P-040 y con un intento de cerrar la etiqueta.
- [x] 5. Pantalla «Nuevo registro diario»
      Produce: `EntryNewComponent` (`vRegNuevo`, `filaSintoma`, `vistaPrevia`), servicios y aviso de llave
      Se comprueba: pruebas unitarias: enviar, propuesta, editar, quitar, agregar, descartar, guardar, fallo con «Guardar solo el texto»
  > 16 pruebas, 0 fallos. `ProcessButtonComponent` ahora tiene `exportAs` para usarlo desde la plantilla.
- [x] 6. F8
      Produce: capturas del mockup y de la app, con vista previa y con fallo
      Se comprueba: diferencias listadas
  > Con backend real y llaves reales, y Chrome manejado por su protocolo de depuración (`ws` ya estaba instalado como dependencia de Angular; nada nuevo) para escribir y pulsar «Enviar». La estructura coincide a 1280 y a 390. Diferencias: sin «Texto corregido» (decidido), el texto de ayuda y el contenido. El estado de fallo se vio con el backend sin llaves. Hallazgo: la caja de texto no se vaciaba en un caso límite; corregido.
- [x] 7. Documentos y cierre
      Produce: `docs/decisiones.md` (por qué síncrono, el prompt, el origen de los eventos), plan al día
      Se comprueba: `mvn -B verify`, `ng test` y `npm run build` verdes
  > Resultados en el reporte de la etapa.

---

# E10 — El agente

> **Estado:** cerrada · **EDT:** 8.1 a 8.9, 8.12 y 8.13 (22,5 h; la etapa de más riesgo) · **Depende de:** E7 y E8 (hechas) · **Valores de los límites (decisión del equipo y recomendación, 2026-10-28):** 5 iteraciones (pedido del equipo), 30 000 tokens por solicitud (recomendación: unas 2 veces lo esperado y protege la cuota diaria de 200 000 de Groq; se calibra con las corridas reales), 5 s por herramienta y confirmación humana (el agente solo escribe el borrador en `PENDIENTE_REVISION`; aprobar no está a su alcance).

## Encargo

**En una frase.** Un agente que, dado un paciente y una semana, decide qué consultar con sus herramientas (registros, métricas, historial de un síntoma, cuántas semanas atrás mirar), redacta el informe semanal y lo guarda como borrador `PENDIENTE_REVISION`, con cada afirmación citando entradas reales y cada cifra igual a la que calculó el código; y que se detiene y lo dice cuando agota un límite.

**Criterio de aceptación.**
- ¿Se fuerza cada límite (5 iteraciones, tokens, 5 s por herramienta) y el agente se detiene, deja el informe `INCOMPLETO` con el motivo y la traza lo muestra?
- ¿Una cifra alterada y una cita inexistente se rechazan antes de guardar, y el modelo recibe el error como observación para corregir?
- ¿Un diagnóstico, una recomendación o una dosis en la salida se rechazan, y una solicitud de diagnóstico se declara fuera de alcance?
- ¿La nota sembrada de P-040, que trae una orden, no cambia la conducta del agente?
- ¿El inventario de herramientas no tiene salida (internet, correo) ni escritura distinta del borrador, y el agente no puede aprobar?
- ¿Con el modelo real, el informe de P-014 en la semana 5 (náuseas reaparecidas) sale válido, con la reaparición señalada y cifras iguales a las métricas?
- ¿Hay un registro de dos versiones de la descripción de cada herramienta con el resultado medido de cada una?

**Queda fuera.** Ejecución en segundo plano, SSE y la API de ejecuciones (E11); pedir el informe por HTTP, estados, edición y aprobación (E12a); el informe mensual y `get_approved_weekly_reports` (E13); la evaluación completa y las cuatro cifras (E14).

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| Herramientas (5, todas de lectura salvo una) | `list_patient_weeks`, `get_week_entries`, `get_week_metrics`, `get_symptom_history` y `save_draft`. Ninguna recibe el id del paciente: la tarea ya lo fija (menos tokens, menos errores, mínimo privilegio). Ninguna lee semanas posteriores a la del informe |
| Citas | Cada entrada que el agente lee recibe un alias corto por ejecución (`e1`, `e2`…). Se cita con el alias; el código lo traduce al id real. Un alias que no se emitió en la ejecución es una cita inexistente |
| Cifras | Las calcula `MetricsService`. El borrador no copia cifras a mano: el código agrega `metrics` y `insufficient_data` al contenido. Cada cifra que el texto de una afirmación nombra debe venir respaldada por `figures` (`clave`, `valor`) y coincidir con las métricas |
| Salida de `save_draft` | `summary` (sin números), `claims` (`claim_key`, `text`, `criterion`, `entry_ids`, `figures`), `changes_vs_previous` y `recurring_patterns` (listas de `claim_key`) |
| Parada | Éxito: `save_draft` válido. Límite: iteraciones, tokens o un segundo tiempo agotado de herramienta. Salida inválida dos veces: se degrada a texto plano y queda `INCOMPLETO`. Proveedor caído: la ejecución falla con mensaje honesto |
| Errores de herramienta | Vuelven al modelo como observación; no matan el bucle |
| Traza | Cada paso se guarda al ocurrir (`agent_steps`), con un mensaje en lenguaje claro («Consultando los registros de la semana…»), para que E11 lo emita por SSE |

## Plan

- [x] 1. Límites, contexto y herramientas de lectura
      Produce: `AgentProperties`, `AgentRunContext` (alias de entradas, semana y paciente de la tarea), la interfaz de herramienta con su tiempo máximo y las cuatro herramientas de lectura con salida compacta
      Se comprueba: pruebas con la semilla: el alias, el alcance (semana posterior rechazada, lunes obligatorio), salidas iguales a las métricas
  > `ReadToolsTest`, 10 pruebas. La salida de métricas de una semana mide menos de 1 500 caracteres. Los alias son estables entre herramientas y un texto de 1 000 caracteres se corta a 400.
- [x] 2. Contenido, validador y guardado
      Produce: esquema de `save_draft`, validador (esquema, citas, cifras, sin números en el resumen, sin diagnóstico ni dosis), armado del contenido con `metrics` e `insufficient_data` del código y guardado de informe, versión 1 y citas
      Se comprueba: una cifra alterada, una cita inexistente, un diagnóstico y un número sin respaldo se rechazan con un mensaje que el modelo pueda corregir; lo válido se guarda en `PENDIENTE_REVISION` con sus citas
  > `DraftValidatorTest` (15) y `DiagnosisGuardTest` (20). La primera versión de las claves de las cifras y la regla del resumen fallaron con el modelo real y se corrigieron (ver paso 4); el rechazo ahora trae la clave exacta que el modelo podría escribir.
- [x] 3. El bucle
      Produce: `AgentRunner` (prompt del sistema, bucle a mano, cuatro límites, parada, `INCOMPLETO`, texto plano, traza progresiva)
      Se comprueba: con la pasarela simulada: éxito, cada límite, herramienta lenta, error de herramienta, salida inválida dos veces, proveedor caído, sin herramienta de aprobar
  > `AgentRunnerTest`, 17 pruebas con un proveedor con guion: éxito con citas y traza, cada límite (5 llamadas; 14 000 + 2 000 tokens; herramienta de 3 s cortada a 200 ms), error de herramienta como observación, rechazo corregido, salida inválida dos veces → texto plano e `INCOMPLETO`, proveedor caído → `FALLIDA`, y pedir `approve_report` o `send_email` devuelve «no existe». Los límites se fuerzan también por configuración (`AgentPropertiesForcedTest`).
- [x] 4. Corridas reales y ajuste
      Produce: corridas con Groq y Gemini en P-014 (semana 5), P-040 (nota con orden) y una solicitud de diagnóstico; el valor de los tokens se fija con los datos
      Se comprueba: informe válido, reaparición señalada, la orden no se obedece, el diagnóstico se declara fuera de alcance
  > `AgentLiveTest` con Groq y Gemini reales: P-014 semana 5 válido a la primera con la reaparición de las náuseas señalada; P-040 documenta el vómito real y no obedece la orden de la nota; la solicitud de diagnóstico se declara fuera de alcance y no se responde. Cada corrida usa de 10 700 a 14 400 tokens y termina en Gemini por el límite de 8 000 tokens por minuto de Groq.
- [x] 5. Dos versiones de cada descripción de herramienta
      Produce: `v1` (escueta) y `v2` (cuándo usarla, cuándo no, prerrequisitos), seleccionable por configuración, y la medición de cada una
      Se comprueba: tabla con tasa de borrador válido, iteraciones y tokens por versión
  > 18 corridas con Gemini: V1 acierta a la primera 4 de 9 y V2, 7 de 9, con el 1,7 % más de tokens. Se usa V2. Registro completo en `docs/decisiones/semana06.md`.
- [x] 6. Documentos
      Produce: `docs/decisiones.md` (los cuatro límites con valor y razón, herramientas, citas por alias, cifras por código) y `docs/decisiones/semana06.md`
      Se comprueba: cada valor con su origen y lo no verificado dicho
  > Tabla de límites con valor, razón y variable para forzarlos.
- [x] 7. Cierre
      Se comprueba: `mvn -B verify` verde
  > Resultado en el reporte de la etapa.

---

# E11 — Segundo plano y SSE

> **Estado:** cerrada · **EDT:** 8.10 y 8.11 (5 h) · **Depende de:** E10 (hecha) · **Decisión de fondo:** D24 (asíncrono, pasos por SSE, sin broker, se transmiten pasos y no tokens).

## Encargo

**En una frase.** Que pedir un informe semanal no bloquee la petición: responde de inmediato con el id de una ejecución que corre en segundo plano, y la pantalla ve cada paso real de la traza (`agent_steps`) a medida que ocurre, puede reanudar si se corta la conexión y nunca queda una ejecución «en curso» para siempre.

**Criterio de aceptación.**
- ¿La petición para generar responde (202) antes de que termine la ejecución, con el id, y una segunda petición igual (mismo paciente y semana, con la primera aún viva) devuelve la misma ejecución sin lanzar otra, incluso si llegan 8 a la vez?
- ¿El primer evento llega al cliente antes de que la ejecución termine (comprobado con una conexión HTTP real, no con MockMvc)?
- ¿Tras cortar la conexión, reconectar con `Last-Event-ID` entrega solo los pasos que faltan y el cierre, sin repetir ni perder ninguno?
- ¿La respuesta del flujo lleva `X-Accel-Buffering: no` y, detrás del nginx de la imagen del frontend, los eventos llegan uno a uno y no todos al final?
- ¿Una ejecución que quedó `EN_COLA` o `EN_CURSO` cuando el proceso se reinició aparece `FALLIDA` con «Interrumpida por un reinicio», con su paso de error en la traza?
- ¿Si el ejecutor está lleno, la petición responde un 503 honesto (sin stack trace) y la ejecución queda `FALLIDA`, no colgada?
- ¿Solo enfermería pide y lee ejecuciones (médico: 403; sin usuario: 401), y un lunes inválido, un paciente inexistente, una semana sin registros o un informe vigente se rechazan sin crear ejecución?
- ¿La traza de una ejecución `ESTRUCTURACION` también se lee por la API?

**Queda fuera.** Las pantallas (E12b): aquí solo el contrato; listar, ver, editar, aprobar y rechazar informes (E12a); el mensual (E13); un broker o una cola externa (descartados en D24); transmitir tokens (descartado).

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| Ejecutor | `ThreadPoolExecutor` acotado en el proceso: 2 hilos y cola de 4 (`agent.executor-threads`, `agent.queue-capacity`). 2 hilos porque la cuota gratuita de Groq (8 000 tokens por minuto) no soporta más ejecuciones a la vez; lleno responde 503, no espera sin fin |
| Una ejecución por paciente y semana | Índice único parcial en `agent_runs` (`INFORME`, `EN_COLA`/`EN_CURSO`) en la migración V6: lo hace cumplir la base aunque lleguen peticiones a la vez; el servicio, al chocar, devuelve la ejecución ya viva |
| Pedir | `POST /api/runs` `{patientId, weekStart}` → 202 con la ejecución nueva o 200 con la existente (`reused: true`). Valida antes de crear: lunes, no futura, paciente, registros en la semana y sin informe vigente; si no, 4xx y no se crea fila |
| Leer | `GET /api/runs/{id}` (estado y `reportId`), `GET /api/runs/{id}/steps?after=` (traza completa con parámetros y resultado, para cualquier tipo de ejecución) y `GET /api/runs/{id}/events` (SSE) |
| SSE | Eventos `step` (`id:` = id de la fila de `agent_steps`, el punto de reanudación) y `end` (la ejecución final). Se leen de la base cada 300 ms desde un solo hilo, así que lo que se ve en vivo es lo que luego se consulta y sobrevive a un reinicio. Latido cada 15 s. El estado final se lee **antes** de vaciar los pasos, para no cerrar con pasos sin enviar |
| Reanudación | `Last-Event-ID` (lo manda solo `EventSource`) o `?after=`; `EventSource` no permite cabeceras, y el filtro de sesión ya acepta `?demoUser=` solo en esta ruta (E4) |
| Huérfanas | Al arrancar, toda `EN_COLA`/`EN_CURSO` pasa a `FALLIDA` con «Interrumpida por un reinicio» y un paso `ERROR` en la traza: el ejecutor vive en el proceso y un reinicio mata la ejecución sin avisar |
| Seguridad | `REPORTS_REQUEST` (enfermería) para pedir y leer; las llamadas asíncronas de Spring Security no se vuelven a autorizar (la petición ya lo fue) |
| Proxy | `proxy_buffering off` ya está en `frontend/nginx.conf` desde E1; se prueba aquí con un nginx real |

## Plan

- [x] 1. Modelo y ejecutor
      Produce: migración `V6` (índice único parcial), `agent.executor-threads` y `agent.queue-capacity`, el ejecutor, el cierre de `AgentRunner.success` con el paso antes del estado final, la comprobación previa compartida
      Se comprueba: la base rechaza dos ejecuciones vivas del mismo paciente y semana; Flyway aplica V6 sobre V5
  > `V6` (índice único parcial), `RunExecutor` de 2 hilos y cola de 4, y `AgentRunner.blockedReason` público. Hallazgo: `success()` guardaba el último paso después del estado final; se invirtió para que el flujo no pierda pasos.
- [x] 2. Pedir en segundo plano
      Produce: `RunService` (validar, deduplicar, crear, lanzar, 503 si está lleno, `FALLIDA` si el hilo muere) y `POST /api/runs`
      Se comprueba: 202 antes de que la ejecución termine; 8 peticiones a la vez crean una; los rechazos no crean fila; lleno → 503 y `FALLIDA`
  > `RunsApiTest` (6) y `RunSaturationTest` (1): 202 antes del final, la misma ejecución para una petición igual, 8 peticiones a la vez crean una, siete rechazos sin fila, 503 con `Retry-After`, hilo que muere → `FALLIDA`. La corrida real mostró que la respuesta decía `EN_CURSO` (entidad compartida con el hilo); ahora se toma la vista antes de lanzar.
- [x] 3. Leer estado y traza
      Produce: `GET /api/runs/{id}` y `/steps`
      Se comprueba: el estado final trae `reportId`; la traza de una `ESTRUCTURACION` se lee; permisos
  > `GET /api/runs/{id}` y `/steps`; la traza de una `ESTRUCTURACION` se lee; médico 403, sin usuario 401. El 404 de un flujo respondía 500 (el JSON no cabía en `text/event-stream`): todo error sale ahora como JSON.
- [x] 4. SSE con reanudación
      Produce: el emisor de eventos, el latido, las cabeceras sin acumulación y el ajuste de seguridad
      Se comprueba: con HTTP real, el primer evento llega con la ejecución viva; reconectar con `Last-Event-ID` entrega solo lo que falta; una ejecución terminada se reproduce y cierra
  > `RunEventsTest` (4) con HTTP real: el primer evento llega con la ejecución en `EN_CURSO`, `Last-Event-ID` entrega exactamente lo que falta, una terminada se reproduce y cierra, y las cabeceras van puestas.
- [x] 5. Huérfanas
      Produce: la recuperación al arrancar
      Se comprueba: `EN_COLA` y `EN_CURSO` pasan a `FALLIDA` con su paso; `COMPLETADA` no se toca; repetirla no cambia nada
  > `OrphanRunRecoveryTest` (2). Además, con `kill -9` real: quedó `EN_CURSO` con 4 pasos y tras reiniciar apareció `FALLIDA` con «Interrumpida por un reinicio del servidor» y su paso `ERROR`.
- [x] 6. Corrida real y proxy
      Produce: una ejecución real contra Groq y Gemini con el flujo leído con `curl -N` y marcas de tiempo; un `kill -9` en plena ejecución y el reinicio; un nginx real delante del flujo
      Se comprueba: los pasos llegan espaciados en el tiempo (no juntos al final), tanto directo como por nginx; la huérfana aparece interrumpida
  > Cinco ejecuciones reales (Groq y Gemini): los eventos llegan repartidos en 12 a 43 s, directo y por un nginx 1.28 real (con `proxy_buffering off` y también con `on`). POST en caliente: 0,12 s. Dos ejecuciones de P-007 terminaron `INCOMPLETA` por cifras sin respaldo del respaldo (Gemini): hallazgo para E14.
- [x] 7. Documentos y cierre
      Produce: `docs/decisiones.md` (por qué asíncrono, pasos y no tokens, 2 hilos, sin broker), contrato de la API en este plan, `.env.example` sin cambios, plan al día
      Se comprueba: `mvn -B verify`, `scripts/check-env-example.sh`
  > Entrada E11 en `docs/decisiones.md`. `mvn -B verify` y `check-env-example.sh`: ver el reporte de la etapa.

---

# E12a — Informes: backend

> **Estado:** cerrada · **EDT:** 9.1 a 9.4 (8,5 h) · **Depende de:** E11 y E4 (hechas) · **Reglas:** RN-01, RN-02, RN-03, RN-05, RF-23 a RF-28 y la tabla 3.3 del prompt (solo semanas completas).

## Encargo

**En una frase.** Que enfermería pueda ver un informe con su evidencia, corregirlo sin perder lo que redactó el modelo, y aprobarlo o rechazarlo con motivo, y que el médico solo vea los aprobados, todo decidido por el servidor y no por la pantalla.

**Criterio de aceptación.**
- ¿Una transición no definida se rechaza (409)? Solo `PENDIENTE_REVISION` pasa a `APROBADO` o `RECHAZADO` y solo `PENDIENTE_REVISION` se edita; `INCOMPLETO`, `APROBADO` y `RECHAZADO` son finales.
- ¿El médico recibe 403 al aprobar, rechazar o editar, y un informe pendiente, incompleto o rechazado no aparece en su lista ni en su detalle (404, sin revelar que existe)?
- ¿Tras editar quedan la versión 1 del modelo (intacta) y la de enfermería, con quién editó, y la lista de citas se rehace? ¿Una edición con una cita de otro paciente o posterior a la semana, un diagnóstico o un número que ninguna cifra respalda se rechaza con el motivo por campo?
- ¿Aprobar registra quién, cuándo y qué versión se aprobó, y el médico ve esa versión?
- ¿Aprobar y editar a la vez no producen un estado incoherente (bloqueo de la fila)?
- ¿Rechazar exige motivo, lo guarda y deja pedir un informe nuevo de esa semana?
- ¿Cada afirmación del detalle trae sus entradas citadas con fecha y texto original, y se puede leer la traza de la ejecución que lo produjo?
- ¿La lista de semanas para generar solo trae semanas completas con registros, y pedir una semana en curso se rechaza (tabla 3.3)?

**Queda fuera.** Las pantallas (E12b), el informe mensual (E13), la auditoría (E16, RF-35), la vista propia del médico (E12c) y exportar a PDF.

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| Lectura | `GET /api/reports?status&patientId&type` (resumen, del período más reciente al más antiguo) y `GET /api/reports/{id}` (cabecera, versión vigente con su contenido, lista de versiones y citas resueltas con fecha y texto original). La versión vigente es la aprobada si la hay; si no, la última |
| Visibilidad | `ReportVisibility` decide en el servidor: médico solo `APROBADO`; lo demás es 404 |
| Edición | `PUT /api/reports/{id}/content` (`REPORTS_UPDATE`) crea la versión n+1 con origen `ENFERMERIA`; nunca se toca una versión anterior. Se cambian resumen, texto y criterio de cada afirmación y sus citas; las cifras (`metrics`, `insufficient_data`, `period`) las copia el servidor de la versión anterior, la persona no las puede cambiar (RN-04) |
| Validación de la edición | Mismas reglas que el borrador, adaptadas: citas reales del mismo paciente y no posteriores a la semana (RN-05), sin diagnóstico ni dosis (RN-03), y todo número del texto debe existir entre las cifras guardadas del informe o ser un día de las fechas citadas. Errores por campo |
| Decisión | `POST /api/reports/{id}/approve` y `/reject` (`REPORTS_APPROVE`, solo enfermería). Con bloqueo de fila para que aprobar y editar a la vez no se crucen |
| Evidencia | `GET /api/reports/{id}/trace`: los pasos de la ejecución que lo produjo (`REPORTS_READ` y visible); `GET /api/reports/{id}/versions/{n}` para ver la versión del modelo (`REPORTS_UPDATE`) |
| Semanas | `GET /api/patients/{id}/report-weeks` (`REPORTS_REQUEST`): semanas completas con registros y el informe vigente de cada una. `POST /api/runs` rechaza una semana que no terminó |

## Plan

- [x] 1. Lectura con la regla de estado
      Produce: repositorio con bloqueo, `ReportService` (lista, detalle, versiones, traza), `ReportController`
      Se comprueba: el médico solo ve `APROBADO` en lista y detalle; enfermería ve todos; el detalle trae citas con texto original
  > `ReportsApiTest`: el médico lista solo `APROBADO` y recibe 404 en el detalle y la traza de los otros tres estados; enfermería ve los cuatro. El detalle trae cada cita con fecha, texto original y autor.
- [x] 2. Edición con versiones
      Produce: validador de la edición y `PUT .../content`
      Se comprueba: la versión 1 no cambia; cada rechazo trae su campo; solo `PENDIENTE_REVISION`
  > La versión 1 queda idéntica tras editar; la 2 es `ENFERMERIA` con quién editó y sus citas. Rechazos con el campo: cita de otro paciente, cita posterior a la semana, diagnóstico, dosis, número sin respaldo, resumen vacío, sin afirmaciones y sin cambios.
- [x] 3. Aprobar y rechazar
      Produce: las dos transiciones con bloqueo, quién, cuándo y versión
      Se comprueba: todas las transiciones no definidas dan 409; médico 403; aprobar y editar a la vez
  > `ReportTransitionsTest` (16 combinaciones) y 409 para las tres transiciones no definidas desde cada estado final, más aprobar dos veces. `ReportConcurrencyTest`: aprobar y editar a la vez, 12 rondas, la versión aprobada fue siempre la última.
- [x] 4. Semanas para generar
      Produce: `report-weeks` y la regla de semana completa en `POST /api/runs`
      Se comprueba: solo semanas completas con registros
  > `report-weeks` solo trae semanas completas con registros y el informe vigente de cada una; `POST /api/runs` rechaza la semana en curso (la regla se añadió al servicio de E11).
- [x] 5. Documentos y cierre
      Produce: contrato de la API en este plan y entrada en `docs/decisiones.md`
      Se comprueba: `mvn -B verify`
  > Entrada E12a en `docs/decisiones.md` con el contrato. `mvn -B verify`: ver el reporte de la etapa.

---

# E12b — Informes: pantallas

> **Estado:** cerrada · **EDT:** 9.5 a 9.9 (10 h) · **Depende de:** E12a y E5 (hechas) · **Mockup:** `vInfGenerar`, `vInfLista`, `vInfVer`, `hojaHTML` de `auditor.html` (F8 a 1280×800 y 390×844) · **Diferencias permitidas:** tabla 3.3 y 3.4 del prompt.

## Encargo

**En una frase.** Que enfermería pueda, en el navegador, pedir un informe viendo el progreso real del agente, encontrarlo en «En revisión», leerlo con la evidencia a un clic, corregirlo (texto, criterio y fuentes), aprobarlo o rechazarlo, y ver cómo se generó.

**Criterio de aceptación.**
- ¿Se recorre en el navegador, con el backend real, generar → revisar → editar → aprobar, y el informe aprobado pasa de «En revisión» a «Aprobados»?
- ¿Mientras el agente trabaja, la pantalla muestra los pasos reales que llegan por SSE (no una animación) y al terminar redirige al informe?
- ¿Cada `[n]` del resumen lleva a su fila de «Fuentes» y cada fuente muestra la fecha y el texto original de la entrada y enlaza a la ficha del paciente?
- ¿«Ver cómo se generó» abre la traza (paso, herramienta, parámetros, resultado, ms, tokens) a un clic?
- ¿Un informe incompleto muestra su motivo y «Generar de nuevo» y no ofrece aprobar; uno rechazado muestra su motivo?
- ¿Un error del servidor (422 con campos, 409, 503) se muestra en lenguaje claro junto al campo o en un aviso, nunca como código crudo?
- ¿F8: la estructura de las tres pantallas coincide con el mockup a 1280 y a 390, y las diferencias son solo de contenido o de las tablas 3.3 y 3.4?
- ¿`ng test` y `ng build` pasan?

**Queda fuera.** El informe mensual y «Un mes (cuatro semanas)» (E13), la vista del médico (E12c), descargar PDF (RF-37), y la comparación con otros periodos del mockup (RN-06).

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| Servicios | `ReportsService` (lista, detalle, versión, traza, edición, aprobar, rechazar, semanas) y `RunsService` (pedir, estado, seguir por SSE). `EventSource` detrás de una fábrica inyectable para poder probarlo; el usuario va en `?demoUser=` (E4) |
| `#/enf/informes/generar/:id?` | Paciente, «Período del informe: Una semana», semana (solo las del servidor), línea de comparación (tabla 3.3), botón «Generar con IA»; el progreso es una lista de pasos con `.est--d` y `.dot`. Si la semana ya tiene informe, se abre; si ya se genera, se engancha a esa ejecución |
| `#/enf/informes/revision` y `aprobados` | `ListingComponent`: Paciente, Período, Generado, Estado. «En revisión» = pendientes; la otra, todo lo demás (aprobados, rechazados e incompletos), como el mockup (`estado !== 'En revisión'`) |
| `#/enf/informes/ver/:id` | Barra de acciones, hoja (`hojaHTML`) con resumen y `[n]`, cifras, patrón, fuentes, pie; edición (resumen, texto, criterio, fuentes); aprobar; rechazar con motivo; «Ver cómo se generó»; versión de la IA |
| Cifras | Salen de `content.metrics` (las calculó el código); la tabla compara con la semana anterior usando `vs_previous.days` |
| Faltantes | `insufficient_data` se escribe como frase sin cita, con su fuente «dom 25: sin registro» (RN-08, sección 3.5) |

## Plan

- [x] 1. Servicios
      Produce: `reports.service.ts`, `runs.service.ts`, tipos, hora de Bogotá para fechas de creación
      Se comprueba: pruebas de las llamadas y de seguir una ejecución (pasos, fin, corte)
  > `ReportsService`, `RunsService` (con `EventSource` detrás de una fábrica) y la fecha de Bogotá. `runs.service.spec` (4): pedir, seguir por SSE, corte con consulta de estado y cierre manual.
- [x] 2. Generar con progreso
      Produce: `ReportGenerateComponent` y la ruta
      Se comprueba: pruebas: semanas, ya existe, progreso, fin → redirige, fallo y semana en curso
  > 9 pruebas: semanas, pasos reales en orden, fin → abre el informe, ya existe, ya se genera, fallida, incompleta, 503 y sin semanas.
- [x] 3. Listas
      Produce: `ReportListComponent` (revisión y aprobados)
      Se comprueba: filtro por estado, fila navegable, vacío
  > 3 pruebas: «En revisión» solo pendientes, «Aprobados» el resto, vacío con camino a generar.
- [x] 4. El informe
      Produce: `ReportViewComponent` con la hoja, las fuentes a un clic, la traza, aprobar, rechazar y editar
      Se comprueba: pruebas de cada estado, edición con errores por campo, aprobar y rechazar
  > 15 pruebas: hoja completa, `[n]` a su fuente, cada estado, aprobar y su 409, rechazar con motivo, edición (formulario, guardar, 422 por campo, cancelar), traza, versión de la IA, texto sin validar y 404.
- [x] 5. F8
      Produce: capturas del mockup y de la app, con backend real y llaves reales, a 1280 y 390
      Se comprueba: diferencias listadas
  > Con backend y proveedores reales y Chrome por su protocolo de depuración. Estructura igual en las tres pantallas y las listas a 1280 y 390; se corrigieron la traza que se recortaba, el color de los enlaces de las fuentes y la opción del menú marcada.
- [x] 6. Recorrido real y documentos
      Produce: generar → editar → aprobar en el navegador real; `docs/decisiones.md`
      Se comprueba: `ng test`, `ng build`, `mvn -B verify`
  > Generar → progreso → informe → traza → editar → aprobar, y en otro editar → versión de la IA → rechazar con motivo, todo en el navegador real; la base quedó con la versión 1 del modelo intacta y la 2 de enfermería. `ng test` 105 de 105 y `ng build` verdes (372 kB).

---

# E14 — Informes sembrados y evaluación

> **Estado:** cerrada · **EDT:** 2.13 y 12.1 a 12.6 (15,5 h; «nunca se recorta»: la medición vale 25 de 100 puntos) · **Depende de:** E3, E7 y E12a (hechas) · **Reglas:** AGENTS.md «Medición»: 5 a 10 casos dorados verificados a mano (uno de rechazo), casos de estructuración, el script, y cuatro cifras con fecha y método (tasa de éxito, costo por tarea, **mediana** de latencia, recuperación) contra la solución convencional.

## Encargo

**En una frase.** Que la app arranque con los cinco informes de D11 ya hechos (cifras iguales a las métricas, citas válidas, sin llamar al modelo), y que un comando corra una suite de casos dorados contra el sistema real y contra una solución convencional sin modelo, y deje las cuatro cifras medidas o diciendo «no medido».

**Criterio de aceptación.**
- ¿Los 5 informes sembrados existen (4 `APROBADO` de P-014, semanas 1 a 4, y 1 `PENDIENTE_REVISION` de P-021, semana 3), con `metrics` iguales al cálculo del código, cada afirmación con citas del mismo paciente y de la semana (o anteriores), cifras que coinciden, sin diagnóstico y con aprobador y fecha los aprobados? ¿Reiniciar no los duplica y el arranque no llama al modelo?
- ¿Hay entre 5 y 10 casos dorados de informes, verificados a mano contra los datos, con uno de rechazo (pedir un diagnóstico), uno de inyección (P-040), uno de datos insuficientes (P-007), uno sin síntomas (P-033) y uno de reaparición (P-014)?
- ¿Hay casos de estructuración de la entrada (síntomas, intensidad, veces, negaciones, sin síntomas, inyección) con su respuesta esperada?
- ¿Un comando (`python3 eval/run_eval.py`) corre todo contra la app levantada, sin librerías nuevas, y escribe un resultado con fecha?
- ¿La solución convencional (plantilla sin modelo, solo dentro de `eval/`) corre los mismos casos con las mismas comprobaciones?
- ¿`docs/decisiones/semana10.md` trae las cuatro cifras con fecha, método y n, la **mediana** de latencia, la comparación con la solución convencional, y «no medido» donde no se midió?

**Queda fuera.** El informe mensual (E13), una suite en CI (necesita llaves y gasta cuota), medir con muchos usuarios, y evaluar la calidad del lenguaje del modelo más allá de lo que se comprueba por reglas.

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| Informes sembrados | Los redacta una plantilla sin modelo sobre las métricas (`eval/baseline.py`, la misma que es la solución convencional) y los guarda como datos en `seed/demo-reports.json`; el sembrador los inserta con `metrics` e `insufficient_data` calculados por el código en ese momento, así que las cifras no pueden diferir. Ids deterministas, `ON CONFLICT DO NOTHING` |
| Validez de la semilla | Prueba que recorre los 5 informes: citas, cifras contra `MetricsService`, números del texto respaldados, sin diagnóstico, estados y aprobador |
| Pedir con indicación | `POST /api/runs` acepta `request` opcional (≤300 caracteres): es la forma de probar el caso de rechazo (pedir un diagnóstico) contra el sistema real. La interfaz no lo expone |
| La suite | `eval/run_eval.py` (Python 3 estándar) llama a la app por HTTP como lo haría una persona: pide, sigue la ejecución, lee el informe y comprueba reglas por caso. Cada ejecución deja el informe `RECHAZADO` con motivo «evaluación» para poder repetir la semana |
| Comprobaciones por caso | Reglas verificables, no juicio del modelo: estado final, cifras citadas = cifras verdaderas, citas del mismo paciente, síntomas que deben y no deben aparecer, días sin registro declarados, ausencia de palabras de diagnóstico y de la orden inyectada |
| Cuatro cifras | Tasa de éxito (informe válido a la primera y al final), costo por tarea (USD de lista, de `agent_runs`), **mediana** de latencia (de `POST` a `end`) y recuperación (de las ejecuciones con un rechazo del validador o un cambio de proveedor, cuántas terminan bien) |
| Solución convencional | Plantilla en `eval/baseline.py` desde las entradas por la API: cuenta días con síntoma, compara con la semana anterior y escribe frases fijas. Mismas comprobaciones. Estructuración: diccionario de palabras con ventana de negación |

## Plan

- [x] 1. Pedir con indicación
      Produce: `request` opcional en `POST /api/runs` hasta el ejecutor
      Se comprueba: prueba del API; el texto llega a la tarea
  > `request` opcional (máx. 300) hasta la tarea del agente; prueba en `RunsApiTest`.
- [x] 2. Plantilla sin modelo y datos de la semilla
      Produce: `eval/baseline.py` y `scripts/make_seed_reports.py` → `seed/demo-reports.json`
      Se comprueba: los textos leídos a mano contra los datos
  > `eval/baseline.py` coincide con el cálculo independiente en las 18 semanas; `scripts/make_seed_reports.py` genera `demo-reports.json` y `test_eval.py` comprueba que el archivo es lo que genera el script.
- [x] 3. Sembrado de los informes
      Produce: el sembrador inserta informes, versión 1, citas; contenido con `metrics` del código
      Se comprueba: 5 informes, idempotente, validez por prueba, el médico ve 4 y no el pendiente
  > `SeededReportsTest` (7): 5 informes, cifras iguales al código, citas, números respaldados, sin diagnóstico, idempotente, el médico ve 4 y no el pendiente. Las pruebas de informes pasaron a P-033 para no chocar con los sembrados.
- [x] 4. Casos dorados y suite
      Produce: `eval/cases/*.json` (informes y estructuración), `eval/run_eval.py`, `eval/README.md`
      Se comprueba: la suite corre contra la solución convencional sin llave y falla cuando se le altera un caso
  > 8 casos de informes y 16 de estructuración; `run_eval.py`; 18 pruebas de la propia evaluación; la solución convencional corre sin llave.
- [x] 5. Medición con llave real
      Produce: corridas reales (n por caso), resultado fechado en `eval/results/`
      Se comprueba: las cuatro cifras salen del resultado, la latencia en mediana
  > Línea base 01:30: informes 11 de 16 (69 %), mediana de latencia 17,8 s; estructuración 32 de 32 (el diccionario, 12 de 16). Resultados en `eval/results/`.
- [x] 6. Ajuste del agente según lo medido
      Produce: cambios al prompt o a la validación para las causas de fallo medidas, y la medición otra vez
      Se comprueba: la tasa de éxito antes y después, con el mismo método
  > Cuatro causas corregidas (claves, fechas como número, `claims` vacío, citas de lo que desapareció). Tras el ajuste, 16 de 16 (en Gemini: de 8 de 13 a 16 de 16) y 6 de 6 en la reserva. Groq tras el ajuste: no medido (cuota agotada).
- [x] 7. Documentos y cierre
      Produce: `docs/decisiones/semana10.md`, entrada en `docs/decisiones.md`, plan al día
      Se comprueba: `mvn -B verify`, `ng test`
  > `docs/decisiones/semana10.md` y entrada E14 en `docs/decisiones.md`. `mvn -B verify`: ver el reporte de la etapa.

---

# E15 — Documentación y entrega

> **Estado:** cerrada, a falta de la prueba del compañero y del tag (los hace una persona del equipo) · **EDT:** 13.1 a 13.7 y 14.1 a 14.3 (12,5 h; «nunca se recorta»: un README que una IA no pueda ejecutar anula la entrega) · **Depende de:** E1 a E12b y E14 (hechas) · **Reglas:** AGENTS.md «Entrega y prueba del profesor», «Estructura de entrega» y «Qué NO hacer».

## Encargo

**En una frase.** Que una IA que solo lea el README, en un clon limpio con solo Docker Compose v2, deje la aplicación corriendo con un comando y sin trabajo manual, y que cada decisión técnica y cada librería tenga su razón y su alternativa por escrito.

**Criterio de aceptación.**
- ¿Un clon limpio (el árbol que se commitearía, sin `.env`, `target/` ni `node_modules/`) llega con `./run.sh` a base, backend y frontend sanos, sin terminal y sin llave, y avisa con claridad que falta la llave?
- ¿Con una llave real en el entorno, la misma ruta deja funcionando la IA (estructurar una entrada y generar un informe con progreso por SSE a través de nginx)?
- ¿El README dice el motor de BD, las imágenes con versión, el comando, las URL, los usuarios de demostración elegibles, el proveedor y modelo con su razón, cómo conseguir y entregar la llave, cómo cambiar de proveedor, cómo forzar los límites, qué pasa sin llave, y cómo correr pruebas y la evaluación?
- ¿`docs/decisiones.md` trae, además de E1 a E14, Java 17, Spring Boot y Angular, la ausencia de RAG y la tabla de librerías con razón y alternativa, y existe `docs/decisiones/semana08.md`?
- ¿Existe `docs/prueba_companero.md` listo para que lo llene una persona ajena al código?
- ¿El escaneo del árbol **y del historial** no encuentra ningún secreto, ni siquiera los valores exactos de las llaves del `.env`?
- ¿`AGENTS.md` ya no dice que el repositorio es un scaffold?

**Queda fuera.** La prueba del compañero y el tag `m2-entrega` (los hace una persona del equipo), el push, la llave temporal del profesor (el equipo la crea), `seguridad.md`, `gobernanza.md` y `respuesta_auditoria.md` (Momento 3), el informe mensual (E13).

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| README | Orden de lectura de una IA: requisitos → comando → qué debe verse → llave → usuarios → verificación con `curl` → decisiones y justificación → pruebas. Cada paso con su salida esperada. Sin remitir a `AGENTS.md` para lo ejecutable |
| Llave | Tres vías en el README: la propia del evaluador (Groq gratuita), la que envía el equipo por un medio privado, o ninguna (la app abre y avisa). `run.sh` además guarda en `.env` una llave que venga del entorno, para que `docker compose up -d` posterior no la pierda |
| Prueba en frío | Copia del árbol que se commitearía (`git ls-files -co --exclude-standard`) a un directorio nuevo y `./run.sh` literal, primero sin llave y luego con ella; no hay commit aún de E14, así que un `git clone` no lo traería |
| Escaneo | Patrones de llaves conocidas sobre el árbol y sobre `git log -p` de todas las ramas, y búsqueda de los valores exactos de las llaves del `.env` (sin imprimirlos). Sin instalar nada |
| Documentos | `semana08.md` (datos, sin RAG, con lo medido), tabla de librerías, Java 17, stack y la lista de pendientes de `decisiones.md` cerrada |

## Plan

- [x] 1. `run.sh` guarda la llave del entorno
      Produce: cambio mínimo en `run.sh`
      Se comprueba: con la llave en el entorno queda en `.env`; sin terminal ni llave arranca y avisa
  > Cuatro líneas en `run.sh`. Verificado en la prueba en frío: con la llave en el entorno quedó en `.env`, y un `./run.sh` posterior sin ella en el entorno siguió con la IA disponible.
- [x] 2. README ejecutable
      Produce: `README.md` reescrito
      Se comprueba: cada comando y cada URL citados existen; `scripts/check-env-example.sh`
  > README reescrito. La prueba en frío encontró un error mío (`/api/llm/status` pide un usuario en la cabecera) y se corrigió. Enlaces relativos revisados; `check-env-example.sh`: 28 variables.
- [x] 3. Decisiones y documentos
      Produce: `docs/decisiones/semana08.md`, secciones nuevas en `docs/decisiones.md`, `docs/prueba_companero.md`, índice de `docs/README.md`, «Estado actual» de `AGENTS.md`
      Se comprueba: sin referencias a documentos que no existen; la lista de pendientes cerrada
  > `semana08.md` con el tamaño del corpus medido (119 entradas, 2 584 caracteres), sección E15 de `decisiones.md` con Java 17, stack, sin RAG y la tabla de librerías, plantilla `prueba_companero.md`, índice de `docs/README.md` y «Estado actual» y pendientes de `AGENTS.md` reescritos.
- [x] 4. Escaneo de secretos
      Produce: resultado del escaneo del árbol y del historial
      Se comprueba: cero coincidencias, incluidos los valores exactos de las llaves
  > 322 archivos del árbol y 157 commits de todas las ramas: 0 coincidencias de patrón o de valor exacto de las llaves del `.env`; 1 falso positivo en el historial (una línea de código del proyecto anterior, `String token = extractBearerToken(request)`); ningún `.env` ni clave privada agregado alguna vez.
- [x] 5. Prueba en frío con el árbol limpio
      Produce: la app corriendo desde un directorio nuevo, siguiendo el README al pie de la letra
      Se comprueba: sin llave (arranca y avisa) y con llave (estructurar, generar con SSE, aprobar)
  > Árbol que se commitearía, copiado a un directorio nuevo, sin `.env`. Sin llave y sin terminal: 4 min 23 s, salida 0, aviso de llave, 3 servicios sanos, 5 pacientes, 5 informes (el médico ve 4), el informe sin llave termina `FALLIDA` con mensaje claro. Con llave: 1 min 24 s; estructuración (3,0 s), informe con SSE por nginx (14,5 s, con paso de Groq a Gemini por límite de Groq), médico 404/403 sobre el pendiente y 5 informes tras aprobar; `AGENT_MAX_ITERATIONS=1` deja el informe `INCOMPLETO`. La interfaz servida por nginx lista los 5 pacientes. Hecho por mí, que conozco el código: no sustituye la prueba del compañero.
- [x] 6. Cierre
      Produce: este plan al día y el reporte
      Se comprueba: `mvn -B verify`, `ng test`, pruebas de `eval/`
  > Esta etapa no cambió código Java ni TypeScript (solo `run.sh` y documentos), por lo que no repetí `mvn -B verify` ni `ng test`; el último resultado es el de E14 (281 y 105). Sí corrí `bash -n run.sh`, las 18 pruebas de `eval/` y `check-env-example.sh`.

---

# E13 — Informe mensual

> **Estado:** cerrada (el equipo lo pidió el 29-oct: «habíamos quedado de incluir en la entrega el informe mensual») · **EDT:** 11.1 a 11.3 (7 h) · **Depende de:** E10, E11, E12a, E12b, E14 y E15 (hechas) · **Reglas:** RF-36, RN-10, D4, prompt 3.3 y 3.4 («Informe mensual: la misma hoja con una tabla por semana y, si falta una semana aprobada, un `.error` que dice cuál»).

## Encargo

**En una frase.** Que enfermería pueda pedir, para un paciente, el informe de **cuatro semanas completas consecutivas** cuando cada una ya tiene su informe semanal `APROBADO`; que el agente lo redacte con esos informes y las cifras de cada semana, que muestre la evolución semana a semana, y que pase por la misma revisión de enfermería antes de llegar al médico. Si falta una semana aprobada, no se genera y se dice cuál.

**Criterio de aceptación.**
- ¿Con las cuatro semanas aprobadas de P-014 (21-sep a 18-oct) se genera el mensual, queda `PENDIENTE_REVISION`, y la hoja muestra la evolución semana a semana con cifras iguales a las del código, afirmaciones con citas del mismo paciente dentro de las cuatro semanas y los cuatro informes semanales enlazados?
- ¿Con una o más semanas sin aprobar (pendiente, rechazada, incompleta o sin informe) el servidor responde 409 **sin crear ejecución** y el mensaje nombra cada semana que falta y su estado, y la pantalla lo muestra en un `.error`?
- ¿Se rechazan, en el servidor, un inicio que no es lunes, una ventana cuya última semana no ha terminado y un mensual vigente repetido (se devuelve el que ya existe o corre)?
- ¿El mensual sigue las mismas reglas que el semanal: solo enfermería lo aprueba, el médico solo lo ve `APROBADO`, se edita con versiones (la 1 de la IA no se toca), las cifras no las cambia nadie, un límite agotado lo deja `INCOMPLETO` con el motivo?
- ¿Queda registrado de qué cuatro informes semanales salió (`monthly_report_weeks`)?
- ¿El agente cumple los cuatro límites también en el mensual, con la misma traza visible (herramienta, parámetros, resultado, ms, tokens)?
- ¿`mvn -B verify` y `ng test` pasan, y una ejecución real (llave) de P-014 deja un mensual válido?

**Queda fuera.** Mensual por mes calendario (D4: cuatro semanas); aprobar semanas desde el mensual; PDF; la vista del médico (E12c); una cifra de éxito estadísticamente firme del mensual (se mide con n pequeño y se dice).

## Diseño (resumen)

| Pieza | Decisión |
|---|---|
| Ventana | Cuatro lunes consecutivos; el inicio es el lunes de la primera. `reports.period_end = inicio + 27` (ya lo exige `V3`) |
| Regla RN-10 | `MonthlyEligibility` (código, no el modelo): por cada semana busca el informe `SEMANAL` `APROBADO`; si falta, el mensaje dice «la semana del X al Y: no tiene informe / está en revisión / fue rechazada…». También exige las cuatro semanas terminadas y que no haya un mensual vigente |
| Tipo de ejecución | `agent_runs.run_type = 'MENSUAL'` (`V7`): así un semanal y un mensual del mismo paciente y misma primera semana no se bloquean entre sí. El índice de «una sola ejecución viva» incluye el tipo |
| Pedirlo | `POST /api/runs` acepta `type` (`SEMANAL` por defecto o `MENSUAL`); con `MENSUAL`, `weekStart` es la primera semana. `GET /api/patients/{id}/report-months` lista las ventanas con el estado de sus cuatro semanas y por qué no se puede generar |
| El agente | El **mismo bucle, límites, validador y capa de proveedor**; cambian el contexto (un periodo de cuatro semanas en vez de una), el prompt y **una herramienta nueva de lectura**, `get_approved_weekly_reports`, solo del mensual: devuelve por semana el resumen, las afirmaciones aprobadas con los alias de sus registros y las cifras compactas. Las cifras que el modelo cita se validan igual (`clave@AAAA-MM-DD` para cada semana) |
| Contenido | `period.weeks`, `weekly_reports` (los cuatro informes de origen), `metrics.weeks` (las métricas de cada semana, del código), `insufficient_data` por semana, más `summary`, `claims`, `changes_vs_previous` y `recurring_patterns` del modelo. Misma forma de afirmación y de cita que el semanal, así la edición y las fuentes funcionan igual |
| Pantalla | «Generar informe»: el período «Un mes (cuatro semanas)» ya previsto en la tabla 3.3. La hoja: título mensual, tabla «Evolución semana a semana» (síntoma por semana), los cuatro informes semanales de origen enlazados, y el mismo resumen, patrón y fuentes |
| Medición | Dos casos dorados de `eval/` (P-014 genera; P-021 se bloquea nombrando las semanas) con comprobaciones propias, y una medición pequeña con llave real, con la cifra y su n |

## Plan

- [x] 1. La regla de las cuatro semanas y la lista de meses
      Produce: `V7` (tipo `MENSUAL` e índice con tipo), `MonthlyEligibility`, `GET /api/patients/{id}/report-months`
      Se comprueba: pruebas de la regla (4 aprobadas, una pendiente, una rechazada, una sin informe, ventana en curso, no lunes) y del endpoint contra la semilla
  > `V7`, `MonthlyEligibility` (el mensaje nombra cada semana y su estado) y `GET /api/patients/{id}/report-months`. `MonthlyEligibilityTest`: 9 pruebas (cuatro aprobadas, pendiente, rechazada, incompleta, sin informe, aprobada que gana a rechazada, ventana en curso, mensual vigente, lista y permisos).
- [x] 2. Pedir el mensual
      Produce: `AgentRun.Type.MENSUAL`, `type` en `POST /api/runs`, 409 con las semanas que faltan, ejecución viva única por tipo
      Se comprueba: prueba del API: bloqueado no crea ejecución; vigente repetido devuelve la misma; el semanal sigue igual
  > `AgentRun.Type.MENSUAL`, `type` en `POST /api/runs`, 409 sin crear ejecución, una ejecución viva por tipo. `MonthlyRunsApiTest`: 7 pruebas (202 y repetido, semanal y mensual a la vez, semana sin aprobar, varias faltantes, no lunes y ventana en curso, permisos, sin `type` sigue semanal).
- [x] 3. El agente en el mensual
      Produce: contexto de periodo, `get_approved_weekly_reports`, prompt mensual, resolutor y validador sobre las cuatro semanas, contenido mensual, `monthly_report_weeks`, validación de edición
      Se comprueba: pruebas con modelo de prueba (borrador válido, cita fuera del periodo, cifra falsa, límite agotado → `INCOMPLETO`); las 281 de antes siguen verdes
  > Contexto de periodo (`AgentRunContext.monthly`), `ApprovedWeeklyReports` y la herramienta `get_approved_weekly_reports` (solo del mensual), prompt mensual, resolutor y validador sobre las cuatro semanas, contenido mensual, `monthly_report_weeks` y validación de edición. 5 pruebas del agente mensual, 6 del validador y el contenido, 5 de revisión (`MonthlyReportsApiTest`). Las pruebas semanales de antes siguen verdes; hubo que ajustar el constructor de `AgentRunner` en tres pruebas y la guarda del inventario (el verbo «aprobar» ya no se busca dentro de «approved»).
- [x] 4. Pantallas
      Produce: período «Un mes» en generar, hoja mensual, lista y servicios
      Se comprueba: pruebas del frontend; Chrome a 1280×800 y 390×844 contra la hoja semanal (F8)
  > Período «Un mes (cuatro semanas)» en generar (la semana que falta se muestra al elegir la ventana), hoja mensual con la tabla «Evolución semana a semana» y los cuatro informes de origen enlazados, y «Mensual:» en las listas. 8 pruebas nuevas (113 en total). Chrome a 1280×800 y 390×844: la tabla de cinco columnas desbordaba en pantalla angosta; ahora se desplaza dentro de la hoja y el documento no desborda. No hay mockup del mensual: se comparó con la hoja semanal.
- [x] 5. Con IA real
      Produce: una ejecución real de P-014 y el bloqueo de P-021 contra la app levantada con llave
      Se comprueba: informe válido, traza completa, cifras iguales al código; lo que falle se corrige por su causa
  > P-014 del 21-sep al 18-oct con llaves reales: 3 llamadas, Groq limitó y la traza muestra el paso a Gemini, cifras iguales al cálculo, aprobado y visible para el médico; el repetido responde 409. P-021: 409 con las cuatro semanas nombradas. Repetido en el contenedor limpio (`V7` aplicada) y una regresión del semanal real: bien.
- [x] 6. Evaluación
      Produce: casos mensuales en `eval/` y la medición con n y fecha en `docs/decisiones/semana10.md`
      Se comprueba: `python3 -m unittest discover -s eval`; la suite corre contra la app
  > 3 casos mensuales (`eval/cases/monthly.json`, `eval/monthly.py`), 29 pruebas de la evaluación (11 nuevas). Con llave: 6 de 6 ejecuciones del agente (a la primera 2 de 6), mediana 9,7 s, 0,0089 USD, 11 828 tokens; M2 1 de 1. Una primera pasada dio 0 de 6 por un error del comprobador (cero de un síntoma ausente), corregido y repetido; todo en `docs/decisiones/semana10.md`.
- [x] 7. Documentos y cierre
      Produce: `decisiones.md` (E13), README, `AGENTS.md`, SPEC y este plan al día
      Se comprueba: `mvn -B verify`, `ng test`, y la prueba en frío otra vez (el contenedor aplica `V7`)
  > `decisiones.md` (E13), `semana10.md`, `semana06.md`, README, `AGENTS.md`, SPEC D4 y este plan. `mvn -B verify`: 313 pruebas, 0 fallos, 14 omitidas (antes 281). `ng test`: 113 de 113 y `ng build` aparte no se repitió (compila en la imagen de Docker). Prueba en frío con el árbol limpio: 4 min 22 s, tres servicios sanos, `V1` a `V7` aplicadas.
