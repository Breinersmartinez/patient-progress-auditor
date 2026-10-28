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

- [x] E1 · [x] E2 · [ ] E3 · [ ] E4 · [ ] E5 · [ ] E6 · [ ] E7 · [ ] E8 · [ ] E9 · [ ] E10 · [ ] E11 · [ ] E12a · [ ] E12b · [ ] E14 · [ ] E15
- [ ] E12c (fuera por decisión del 28-oct) · [ ] E13 (al final, solo si sobra tiempo) · [ ] E16 (fuera)

## 5. Propuestas del §4.2 del prompt, cerradas para tu aprobación

| Tema | Propuesta | Alternativa descartada |
|---|---|---|
| Proveedor y modelo (D15) | Principal Google AI Studio y respaldo Groq, por HTTP con `RestClient`, sin SDK. **Verificado hoy en las páginas oficiales:** Gemini lista como estables `gemini-3.8-flash`, `gemini-3.6-flash`, `gemini-3.5-flash-lite` y `gemini-3.1-flash-lite`; Groq lista `llama-3.3-70b-versatile`, `openai/gpt-oss-120b` y `openai/gpt-oss-20b`, todos con 131 072 tokens de contexto. Candidatos: `gemini-3.8-flash` para el agente y `gemini-3.5-flash-lite` para estructurar; `openai/gpt-oss-120b` como respaldo. **No verificado:** los límites gratuitos (Google ya no los publica por modelo: se ven en la consola de AI Studio; las fuentes de terceros se contradicen, de 2 a 10 solicitudes por minuto), el soporte de herramientas y de JSON de cada modelo (las páginas no lo dicen) y el precio. Se decide en E8, paso 1, con la llave. Dato para gobernanza: Google puede usar los datos de la capa gratuita para mejorar sus productos | Un solo proveedor (no permitiría mostrar el cambio en vivo); un SDK (rompe «ningún SDK fuera de la capa») |
| Los cuatro límites | Valores iniciales, que se calibran con las primeras trazas y se justifican en `decisiones/semana06.md`: 8 iteraciones, 50 000 tokens por solicitud, 5 s por herramienta (son lecturas locales; la llamada al modelo tiene su propio tiempo máximo) y confirmación humana: la única escritura del agente es el borrador en `PENDIENTE_REVISION`; no tiene herramienta ni permiso para aprobar | Límites sin valor o sin razón: sin límite no hay entrega |
| Herramientas (siete como máximo, ninguna de salida) | `list_patient_weeks`, `get_week_entries`, `get_week_metrics`, `compare_weeks`, `get_symptom_days`, `save_draft` y, solo en el mensual, `get_approved_weekly_reports`. Se afinan y se registran dos versiones de cada descripción en E10 | Una herramienta que devuelva todo el historial: gasta tokens y quita decisiones al agente |
| Usuario elegido | Cabecera `X-Demo-User` con el id; parámetro `demoUser` solo en la ruta SSE; un filtro lo valida y deja rol e identidad; `/api/demo-users` y `/api/system/status` son públicos | `fetch` en streaming: más código sin ganancia en el Momento 2 (D24 prevé pasar a `fetch` con el token en el Momento 3) |
| Perfil de Spring | Perfil nuevo `docker` (`application-docker.yml`): conexión desde `DATABASE_URL`, `DB_USERNAME` y `DB_PASSWORD`, `validate`, sin SQL en el log, semilla permitida. `prod` rechaza `APP_SEED_DEMO=true` y no arranca | Usar `dev`: registra el SQL en DEBUG y hoy actualiza el esquema; usar `prod`: la semilla no corre |
| Validación del esquema | Jackson con registros tipados y Bean Validation, ambos ya presentes | Una biblioteca de JSON Schema: dependencia nueva sin necesidad |
| Pruebas con BD | Contenedor `postgres:16` desechable en un puerto libre (el 5433 está ocupado aquí), como el servicio de `ci.yml` | Testcontainers: dependencia nueva y no aprobada |
| Variables | `.env.example` lista `DATABASE_URL`, `DB_*`, `GEMINI_API_KEY`, `GROQ_API_KEY`, `LLM_PROVIDER`, `LLM_FALLBACK`, `GEMINI_MODEL`, `GROQ_MODEL`, `LLM_FAULT`, `APP_SEED_DEMO`, `SPRING_PROFILES_ACTIVE` y los puertos del host. Criterio: cada `${VAR}` de los yml y del compose figura en `.env.example` (lo compruebo con un `grep`) | — |
| Puertos | La base no se publica al host (no puede chocar); el frontend y el backend usan puertos configurables y `run.sh` avisa si están ocupados | Publicar el 5432: choca con cualquier Postgres local, como el de esta máquina |
| Fallos forzables | **Descartado por el equipo el 2026-10-28: no se construye `LLM_FAULT` ni fallos en vivo.** Propuesta original: `LLM_FAULT` = `none` (por defecto), `timeout`, `rate_limit` o `invalid_output`, leída solo dentro de la capa de proveedor y solo sobre el principal, de modo que el respaldo real entra en vivo. No es un modo simulado: la ruta normal siempre llama al proveedor real. **Lo construyo solo si lo confirmas** (pregunta 5) | Un adaptador de repetición de respuestas: es el modo simulado descartado en D16 |
| Cambio de proveedor en vivo | `LLM_PROVIDER=groq docker compose up -d backend`, escrito en el README y probado en E8 | — |
| Ejecuciones huérfanas | Al arrancar (`ApplicationReadyEvent`), toda ejecución `EN_COLA` o `EN_CURSO` pasa a `FALLIDA` con «Interrumpida por un reinicio» | Dejarlas en curso: la pantalla esperaría para siempre |
| Semana en curso | No se puede pedir; solo semanas completas con entradas (propuesta del prompt). «Hoy» se calcula en `America/Bogota` | — |
| Informes sembrados | Escritos a mano con las cifras que calcula el módulo de métricas y revisados uno por uno; no dependen de la llave. Se archivan como JSON en `backend/src/main/resources/seed/` | Generarlos con el agente: la semilla dependería de que E10 funcione |

**Contrato de API (borrador para acordar antes de separarnos).** `GET /api/system/status` · `GET /api/demo-users` · `GET|POST /api/patients` · `GET /api/patients/{id}/entries?from&to` · `GET /api/patients/{id}/weeks` · `POST /api/entries/propose` (no guarda) · `POST /api/entries/confirm` y `POST /api/entries` (solo texto) · `POST /api/reports/requests` (202 con `runId`) · `GET /api/runs/{id}` · `GET /api/runs/{id}/events` (SSE) · `GET /api/reports?status&patientId` · `GET /api/reports/{id}` · `PUT /api/reports/{id}/content` (nueva versión) · `POST /api/reports/{id}/approve` · `GET /api/reports/{id}/trace`.

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
