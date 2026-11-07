# PLAN — Momento 3 (semana 14, aplicación completa con autenticación)

> **Estado:** en construcción. Trae dos piezas opcionales: Langfuse (aprobada el 2026-11-03) y el dictado por voz (aprobada el 2026-11-05), y las cuatro del módulo del médico: «Enviar al médico» (Pieza 3), pantallas y registro (Pieza 4), notificaciones (Pieza 5) y «Armar informe» (Pieza 6), escritas el 2026-11-05 y revisadas el 2026-11-07 (estado `ENVIADO` y fuente de la consulta del médico), **aprobadas el 2026-11-07**. El resto del Momento 3 (autenticación y JWT RF-31, credenciales y administrador RF-33, tablero de permisos RF-34, `docs/seguridad.md` y `docs/respuesta_auditoria.md`) todavía no está especificado ni planeado; sin su plan aprobado no se escribe código de esas piezas. **[Equipo]**
> **Fecha:** 2026-11-03 · **Rama:** `develop_BSMartinez` · **Plan del Momento 2:** `docs/PLAN.md` (E1 a E19, cerrado).

## Pieza 1 — Langfuse: trazas de las consultas al modelo y costo por consulta

**Origen:** el profesor recomendó Langfuse para trazas y costos. **[Curso]** El equipo lo integra como adicional, no obligatorio. **[Equipo]**

### Encargo
Que cada llamada al modelo de la aplicación (informe semanal, informe mensual y estructuración de una entrada) aparezca en Langfuse Cloud, de forma opcional y sin texto de pacientes, con su modelo, tokens, latencia y costo, para comparar el costo por consulta según el modelo usado.

### Decisiones del encargo
| Duda | Decisión | Por qué |
|---|---|---|
| Dónde corre Langfuse | Langfuse Cloud, región de Estados Unidos (`https://us.cloud.langfuse.com`), una cuenta y un proyecto compartidos por los dos integrantes, con el profesor invitado más adelante si el equipo lo decide **[Equipo]** | Autoalojado suma seis contenedores y pone en riesgo el arranque en menos de 5 minutos y la prueba en frío (RNF-13) |
| Es obligatorio | No. Sin llaves la aplicación arranca igual y avisa **[Equipo]** | La prueba del profesor es un clon limpio y una IA que solo lee el README: no puede depender de una cuenta externa |
| Qué se envía | Solo metadatos: proveedor, modelo, tokens, costo, latencia, categoría del fallo, tipo y resultado de la ejecución, id de la ejecución. Sin texto de notas, prompts ni respuestas, sin id del paciente, sin el detalle técnico del proveedor **[Equipo]** | RNF-02: a terceros sale lo mínimo. El texto sigue en la traza propia (RF-22) |
| Dónde se ve el costo | Solo en Langfuse; la interfaz y los mockups no cambian **[Equipo]** | Los mockups no se modifican sin que el equipo lo pida |
| Qué es una «consulta» | Cada llamada al modelo, más el total por ejecución, incluida la estructuración **[Equipo]** | Cubre todo lo que pasa por `LlmGateway` |
| Quién calcula el costo | El código (`LlmPricing`, precio de lista); Langfuse lo muestra **[Propuesta]** | `AGENTS.md`: los números los calcula el código. Con el respaldo, un informe puede mezclar dos modelos |
| Qué pasa si Langfuse falla | Nunca afecta al flujo de enfermería **[Propuesta]** | La observabilidad no puede tumbar el producto |
| Relación con la traza propia | Complementa, no reemplaza, a RF-22 **[Propuesta]** | RF-22 es lo que revisa enfermería; Langfuse sirve al equipo y al evaluador |

### Fuera del alcance
Mostrar el costo en la interfaz · autoalojar Langfuse · enviar texto de notas, prompts o respuestas · gestión de prompts, evaluaciones, puntuaciones y alertas de Langfuse · trazas de las herramientas del agente (nombre y duración; se pueden añadir después del paso 5) · reemplazar la traza propia (RF-22) · cambiar modelos, precios o proveedores · cobro real (la capa gratuita no cobra y el costo es de lista).

### Supuestos
- Langfuse Cloud acepta por HTTP, en JSON, las trazas OpenTelemetry (OTLP) armadas por la aplicación, toma el costo enviado sin recalcularlo y deja vacía la entrada y la salida si no se mandan. **Probado en el paso 2 (2026-11-03).**
- Las llaves entran por el entorno o por el `.env` local, que git ignora. Nunca van a un archivo versionado.
- La API de ingesta antigua (`/api/public/ingestion`) está en desuso según Langfuse y no se usa.
- Para `mvn -B verify` hay un PostgreSQL desechable.

### Pasos
- [x] 1. **Mirar** el estado de partida: correr las pruebas del backend y del frontend antes de tocar nada y confirmar los puntos de enganche (`LlmGateway`, el cierre de ejecución en `AgentRunner` y `StructuringService.finish`). Anotar la D y el RF o RNF libres.
  - **Produce:** la línea base de pruebas y la lista de enganches con archivo y línea.
  - **Se mira:** `mvn -B verify` y `ng test` en verde, con el número de pruebas anotado; cada enganche confirmado leyendo el código.
  > Hecho el 2026-11-03. Backend: 366 pruebas, 0 fallos, 14 omitidas (las pruebas en vivo, que exigen `RUN_LIVE_LLM=true` y llaves), `BUILD SUCCESS` en 3 min 29 s contra una base desechable en el puerto 5439. Frontend: 129 de 129. Ambas cifras coinciden con `AGENTS.md`. Libres: **D28** y **RF-43**, que se usaron en el paso 3; no hizo falta un requisito no funcional nuevo.
  > Enganches: `LlmGateway.complete` (`LlmGateway.java:63`, costo por llamada exitosa en la línea 85); en el agente, `State.attempts` (`AgentRunner.java:281`), `State.account` (303) y `State.finish` (443, costo en la 452); en la estructuración, `addAttempts` (`StructuringService.java:132`) y `finish` (146, costo en la 156).
  > **Hallazgo 1:** en la estructuración, si la salida no valida tras el reintento (`SALIDA_INVALIDA`), `finish` recibe costo `null` aunque ya se gastaron tokens. En `agent_runs.cost_usd` ese costo queda vacío, y sumando los intentos en Langfuse no lo estaría. El criterio 1 compara solo las ejecuciones con `cost_usd` no vacío; las demás se documentan aparte.
  > **Hallazgo 2:** `Attempt` guarda la duración pero no la hora de inicio, como se temía (riesgo del paso 5).
  > **Hallazgo 3:** la base `ppa-test-db` (PostgreSQL 16.15, `127.0.0.1:5439`) sigue levantada para las pruebas de los pasos 4 a 7. En los puertos 5432 y 5433 hay otras bases locales que no son de este proyecto y no se usaron.
- [x] 2. **Probar** el envío a mano: mandar con `curl` a `us.cloud.langfuse.com` una generación de prueba, sin texto, con modelo, tokens, costo y metadatos.
  - **Produce:** el cuerpo mínimo que Langfuse acepta y la decisión entre JSON armado por la aplicación o la librería de OpenTelemetry.
  - **Se mira:** en el panel aparece como generación, con el costo igual al enviado y sin entrada ni salida.
  > Hecho el 2026-11-03. Se envió una traza OTLP/JSON (HTTP 200) con un tramo raíz y tres generaciones, y se leyó de vuelta con la API v2. **A** (costo coherente): `totalCost` 0,00036, igual al enviado. **B** (costo enviado 0,000777 a propósito, distinto de los 0,0008 que da `LlmPricing` para esos tokens): Langfuse conservó 0,000777, así que respeta el costo recibido y no recalcula con su tabla. **C** (intento fallido sin tokens ni costo): `usageDetails` y `costDetails` vacíos y `totalCost` vacío, no cero; el nivel `ERROR` y el mensaje `RATE_LIMIT` se conservan. `input` y `output` vacíos en las cuatro observaciones.
  > **Decisión (plan paso 2):** la aplicación arma el JSON OTLP ella misma y lo manda con `JsonHttp` (el cliente HTTP de la JDK que ya usan los de Groq y Gemini; escribí «`RestClient`» por error y se corrigió en el paso 4). **No hace falta ninguna dependencia nueva**, así que no se pide permiso para instalar la librería de OpenTelemetry. **[Propuesta]**
  > Cuerpo mínimo aceptado: `POST {LANGFUSE_BASE_URL}/api/public/otel/v1/traces`, `Authorization: Basic base64(pública:secreta)`, `Content-Type: application/json` y `x-langfuse-ingestion-version: 4`. `traceId` de 32 y `spanId` de 16 caracteres hexadecimales, tiempos en nanosegundos como texto. Atributos usados: `langfuse.observation.type` (`generation`), `langfuse.observation.model.name`, `langfuse.observation.usage_details` y `langfuse.observation.cost_details` (JSON como texto, claves `input`, `output`, `total`), `langfuse.observation.level` y `langfuse.observation.status_message`, y los metadatos con el prefijo `langfuse.observation.metadata.`. El atributo de recurso `deployment.environment.name` llega como el entorno de Langfuse.
  > **Hallazgo 1:** el endpoint antiguo de lectura (`GET /api/public/traces/{id}`) responde **410**: Langfuse lo retira en la nube el 2026-11-16. Para verificar (pasos 6 y 7) se usa `GET /api/public/v2/observations` con `traceId`, `fromStartTime`, `toStartTime` y `fields=core,basic,usage,io,metadata`.
  > **Hallazgo 2:** la lectura tiene un límite de ritmo (429 al consultar cada 5 s). Se consulta con pausas de 15 a 20 s.
  > **Hallazgo 3:** el entorno de Langfuse sirve para separar las trazas de la demo, de `eval/` y de pruebas (`deployment.environment.name`); con eso se mitiga el riesgo de que `eval/` llene el proyecto compartido.
  > **Hallazgo 4:** los metadatos guardados son solo los míos, más `scope.name`, `resourceAttributes.*` y un eco de los atributos `langfuse.*`; no hay datos ajenos.
  > **No verificado:** que el nombre de la traza (`langfuse.trace.name` en el tramo raíz) quede como nombre de la traza; la API v2 de observaciones no lo devuelve. Se mira en el panel en el paso 6. Tampoco medí cuánto tarda en aparecer: lo leí unos minutos después.
  > **Queda en el proyecto compartido** una traza de prueba (entorno `spike`, nombre `spike-ppa-ejecucion`, id `d8144a2d534fc64557d4afccdc839793`, cuatro observaciones). Se puede borrar desde el panel.
- [x] 3. **Registrar** la decisión antes de programar, en el orden de `CLAUDE.md`: D28 en `docs/SPEC.md` §7, el requisito con hito Opc y los conteos en `docs/REQUISITOS.md`, `docs/decisiones.md`, `docs/gobernanza.md` (completo, con lo construido hasta ahora más la integración), `AGENTS.md` y el resumen ejecutivo.
  - **Produce:** los documentos coherentes entre sí.
  - **Se mira:** sin IDs inexistentes, sin tablas descuadradas, conteos por hito sumados y lo que sale a Langfuse escrito con su región.
  > Hecho el 2026-11-03. **D28** en la SPEC (v0.8: nota de versión, fila de alcance y referencia desde D25) y **RF-43** en los requisitos (v0.5), con hito **Opc**: el equipo dijo que es un plus del Momento 3 y que no hace falta mostrarlo en la demo, así que no se suma a lo obligatorio. Total: **43 RF y 2 opcionales** (RF-38 y RF-43); los demás conteos por hito no cambian (calculado de la tabla: 29 M2-N, 2 repartidos entre M2 y M3, 7 M2-S, 3 M3). También quedaron al día `docs/decisiones.md` (sección nueva con razón y alternativa de cada decisión), `AGENTS.md`, `CLAUDE.md`, `docs/README.md` y el resumen ejecutivo. Los diccionarios de datos no cambian: no hay tablas ni columnas nuevas. **`docs/gobernanza.md` v0.1** responde las cinco preguntas del curso y cita el código o la prueba de cada hecho.
  > Comprobación: un script revisó los IDs citados, que las filas de cada tabla tengan las columnas de su encabezado, los conteos, la región (Estados Unidos aparece en la ficha, `decisiones.md`, la SPEC y este plan) y que ninguna llave aparezca fuera del `.env`. Todo cuadra; el único aviso fue una mención mía de un ID «libre», ya redactada de otro modo.
  > **Hallazgos de la ficha** (el curso pide describir lo que el sistema hace, no lo deseado; **no se corrigió nada**, quedan para el plan del Momento 3 y para `docs/seguridad.md`): (1) el servidor exige el rol de enfermería para aprobar, pero **no verifica la identidad**: el usuario viaja en `X-Demo-User` y el propio filtro dice que no es autenticación (se cierra con RF-31); (2) **no hay cómo cancelar** una ejecución en curso; (3) **no hay política de retención** ni rotación de registros; (4) un informe aprobado **no se puede retirar**, solo reemplazar con uno nuevo; (5) el texto libre de las notas **no se depura** antes de salir al proveedor; (6) el motivo técnico de un fallo del proveedor, que se escribe en el registro del servidor, **puede traer un fragmento de su respuesta**.
  > **Decisiones que nadie pidió:** añadí a `CLAUDE.md` la referencia a `docs/PLAN_M3.md` (decía que el plan del Momento 3 no existía); en el resumen ejecutivo corregí la línea que ponía al PDF como opcional del Momento 3 (pasó al Momento 2, D13), pero dejé lo demás de esa foto de las fases 1 y 2 como estaba (por ejemplo «31 de los 43 RF» en el núcleo, que no coincide con los 29 + 2 de la tabla).
- [x] 4. **Construir** la pieza de envío dentro de `llm`: recibe cada llamada ya resuelta, la manda en segundo plano y nunca bloquea ni lanza error; se apaga sola si faltan las llaves. Sumar las variables a `application.yml`, `.env.example` y `docker-compose.yml`.
  - **Produce:** la clase de envío, la configuración y sus pruebas.
  - **Se mira:** pruebas verdes que cubren que el cuerpo no lleva texto de notas ni el `detail` técnico, que el costo vacío se manda vacío, que un host caído no lanza ni retrasa y que sin llaves no hace nada; `scripts/check-env-example.sh` en verde.
  > Hecho el 2026-11-03. Backend: **389 pruebas, 0 fallos, 14 omitidas**, `BUILD SUCCESS` en 6 min 27 s (la línea base eran 366; las 23 nuevas son 11 del cuerpo, 11 del envío y 1 de guarda). `scripts/check-env-example.sh` en verde con 32 variables, y en rojo si se quita `LANGFUSE_ENVIRONMENT` de `.env.example` (control negativo). El frontend no se tocó y no se volvió a correr.
  > **Qué quedó:** en el paquete `llm`, `LangfuseProperties` (prefijo `langfuse`), `LangfuseRun` (lo único que sale: id, tipo, estado, motivo de parada, inicio, duración e intentos; no tiene campo para texto ni para el paciente), `LangfusePayload` (función pura que arma el OTLP/JSON), `LangfuseExporter` (hilo aparte, cola de 50, tope de 5 s por envío, se apaga sola sin las dos llaves, nunca lanza; al cerrar da hasta 2 s a lo que esté en cola). Se reutiliza `JsonHttp`, así que no hay dependencia nueva ni código HTTP nuevo. `LlmPricing` ganó `inputCost`. Variables en `application.yml`, `.env.example` y `docker-compose.yml`; `docker compose config` resuelve los valores por defecto cuando faltan.
  > **Las pruebas detectan lo que dicen detectar:** rompí el código a propósito de cuatro formas y cada una hizo fallar la prueba esperada: colar `detail` en la generación (fallan 2), enviar en el hilo de quien llama (la prueba del host lento mide 2 902 ms por llamada contra un tope de 250), enviar sin llaves (falla la de «sin llaves») y mandar costo en cero en vez de vacío (falla la del modelo sin precio). Código restaurado y comparado con la copia.
  > **Hallazgo 1:** mi primera prueba de latencia midió la suma de 200 llamadas, incluida la construcción de las corridas y ~150 líneas de aviso por cola llena, y falló con 509 ms contra 500 en la suite completa. Medido por separado, la peor llamada individual es de 4 a 19 ms. La reescribí para medir la peor llamada (tope 250 ms), que es lo que importa: que quien llama no espere al host.
  > **Hallazgo 2:** las pruebas con contexto de Spring recorrerían la ruta real, y quien las corra con las llaves en el entorno llenaría de trazas falsas la cuenta compartida. `pom.xml` vacía las dos llaves en surefire y `PatientProgressAuditorApplicationTests` lo comprueba. Control negativo: sin ese ajuste y con llaves en el entorno, la prueba falla y el registro dice «trazas activas».
  > **Hallazgo 3 (corrige mi paso 2):** escribí que el JSON se manda «con `RestClient`, como Groq y Gemini». Es falso: usan `JsonHttp`, el cliente de la JDK (`docs/decisiones.md:129` ya lo decía). Corregido en este plan y en `docs/decisiones.md`.
  > **Decisiones del cuerpo:** el id de la traza es el de la ejecución sin guiones (se busca por `agent_runs.id`) y los ids de tramo son deterministas, así que reenviar una ejecución no la duplica; el costo de la generación es `LlmPricing.cost` y se parte en entrada (redondeada a 6 decimales) y salida (el resto), para que sumen exactamente el total y Langfuse no pueda mostrar otro; un intento fallido va como generación en `ERROR` con la categoría del fallo, sin uso ni costo; un modelo sin precio no manda costo; la ejecución `FALLIDA` va en `ERROR` y la `INCOMPLETA` en `WARNING`. Un intento por falta de llave (`MISSING_KEY`) también se manda como error, igual que en la traza propia (RF-22).
  > **Para el paso 5:** `Attempt` sigue sin hora de inicio, así que las generaciones se dibujan una tras otra desde el inicio de la ejecución; las esperas del reintento y las herramientas del agente no ocupan lugar en la línea de tiempo (la duración de cada generación sí es la real). Si en el panel se ve engañoso, se agrega la hora de inicio a `Attempt`. El cierre de la ejecución debe llamar a `LangfuseExporter.export` después de guardar `agent_runs`, nunca antes. La suma de costos de la traza debe salir igual a `costUsd`: el agente (`State.account`) y la estructuración (`LlmGateway.structured`) suman el `costUsd` de cada llamada exitosa, que es el mismo `LlmPricing.cost` que usa el exportador por intento (comprobado leyendo el código). Las llamadas que fallan del todo no suman costo en la aplicación ni en la traza.
  > **No verificado:** que Langfuse acepte el cuerpo armado por Java (el formato es el probado en el paso 2 con Python, pero este código no ha hablado todavía con la nube; es lo primero que mira el paso 6) y que el nombre de la traza (`langfuse.trace.name`) quede como se envía.
- [x] 5. **Conectar** las dos rutas, el informe (semanal y mensual) y la estructuración: cada ejecución es una traza con el id de la ejecución, cada intento una generación, y el cierre lleva tipo, resultado y totales.
  - **Produce:** las rutas enviando, sin cambiar lo que ve enfermería.
  - **Se mira:** con el cliente guionado, un 429 seguido del respaldo da dos generaciones, la fallida con error y costo vacío, y la suma de costos es igual al `costUsd` de la ejecución; las pruebas anteriores siguen en verde.
  > Hecho el 2026-11-04. Backend: **404 pruebas, 0 fallos, 14 omitidas**, `BUILD SUCCESS` en 7 min 32 s (389 del paso 4 más 15: 5 de `LangfuseTracesTest`, 4 del agente, 4 de la estructuración, 1 del cuerpo y 1 del exportador). Las pruebas anteriores siguen en verde. El frontend no se tocó y no se volvió a correr.
  > **Qué quedó:** `AgentRunner.State.finish` y `StructuringService.save` entregan la traza al exportador **después de guardar `agent_runs`**; no bloquea ni lanza. `State` acumula todos los intentos (`allAttempts`) y la estructuración pasa a `save` los de cada rama (éxito, proveedor caído y salida inválida). El mapeo de la fila a lo que sale está en un solo lugar, `agent/LangfuseTraces` (id, tipo, estado, motivo, inicio, duración, intentos; devuelve null, y el exportador lo ignora, si falta id, inicio, tipo o estado). `LangfuseRun` pasó a calcular `tokensIn`, `tokensOut` y `costUsd` desde los intentos: es la única fuente de esa suma, y la usan el cuerpo y las pruebas. Una ejecución **sin intentos** (semana sin registros, informe bloqueado) no se manda: no llamó al modelo, no es una consulta.
  > **Lo que dice «se mira» del plan, comprobado:** con la pasarela real y proveedores con guion, un 429 que pide esperar 30 s pasa directo al respaldo y da **dos generaciones** (`LangfusePayloadTest`): la fallida en `ERROR`, motivo `RATE_LIMIT` y sin costo; la buena con el precio de Gemini, y el costo del cuerpo igual al `costUsd` de la pasarela (0,001173). En el agente (`AgentRunnerTest`) la suma de lo entregado es igual a `agent_runs.cost_usd`: 0,002900 con el respaldo, y igual en el éxito y en el límite de iteraciones; con el proveedor caído (8 intentos, ninguno con respuesta) el costo es vacío en los dos lados. En la estructuración, igual en el éxito y vacío en el fallo.
  > **Las pruebas detectan lo que dicen:** rompí el código a propósito y fallaron las esperadas: quitar el envío del agente y de la estructuración (5 de 5 pruebas) y dejar de acumular los intentos del agente (3 de 3; la estructuración, intacta, siguió en verde).
  > **Hallazgo 1 (prueba frágil, corregida):** la suite completa falló una vez en `LangfuseExporterTest`: la primera petición de la JVM agotó el tope de 500 ms que yo había puesto solo para las pruebas (el cliente HTTP de la JDK arranca en frío en una máquina cargada; el registro marca `TIMEOUT` a los 2,4 s). No era el código: en producción el tope es de 5 s y el cliente ya está caliente por las llamadas al proveedor. Corregí la prueba (tope de 3 s, 500 ms solo en la del host lento, y precalentar `JsonHttp`) y volví a correr **la suite completa**, que pasó. Un fallo que solo aparece en la suite completa no se da por resuelto con una corrida aislada.
  > **Hallazgo 2 (ya existía; sin cambiar):** si en la estructuración la primera llamada responde pero su salida no valida y el reintento falla **del todo** (`LlmUnavailableException`), los intentos de la primera llamada se pierden: `LlmGateway.structured` lanza solo los del reintento. Ni `agent_runs` ni Langfuse los ven, y se gastaron tokens. Es el mismo tipo de hueco que el hallazgo 1 del paso 1 (`SALIDA_INVALIDA` deja el costo vacío): hay que sumarlo al criterio 1 como ejecución excluida.
  > **Hallazgo 3:** la prueba `anOutputThatNeverValidatesIsTheOneRunWhereLangfuseKnowsACostTheRunRowDoesNot` fija a propósito la diferencia del hallazgo 1 del paso 1 (fila con costo vacío, Langfuse con 0,000195). Si algún día `agent_runs` guarda ese costo, esa prueba debe cambiar.
  > **Decisiones que nadie pidió:** (1) los constructores de `AgentRunner` y `StructuringService` reciben el exportador (se actualizaron los dos sitios de prueba que construyen `AgentRunner` a mano); (2) la clase `LangfuseTraces` en el paquete del agente para no duplicar el mapeo; (3) en las pruebas del agente y de la estructuración el exportador es un simulado que captura lo entregado, así que ninguna prueba habla con Langfuse; (4) la hora de inicio de `Attempt` **no** se agregó: los intentos siguen dibujándose uno tras otro (ver paso 6).
  > **No verificado:** nada contra Langfuse Cloud todavía. La línea de tiempo del panel (generaciones una tras otra, sin las esperas del reintento ni las herramientas) se mira en el paso 6; si engaña, se agrega la hora de inicio a `Attempt`. La latencia de la estructuración con Langfuse activo se mide en el paso 6 contra la línea base de 1,40 s.
- [ ] 6. **Verificar en vivo** con las llaves del equipo: un informe semanal con Groq, uno con `LLM_PROVIDER=gemini`, una estructuración, una corrida sin llaves y una con el host inalcanzable.
  - **Produce:** de tres a cinco ejecuciones reales en el panel y la tabla de comparación.
  - **Se mira:** el costo del panel es igual, a 6 decimales, al `cost_usd` de `agent_runs`; cada modelo usa su precio; una frase de nota, un alias y un prompt no aparecen en el buscador; sin llaves o sin host el flujo termina igual.
- [ ] 7. **Comprobar** el criterio de aceptación del encargo, punto por punto, y dejar la evidencia en `docs/decisiones/semana14.md`.
  - **Produce:** el veredicto sí o no por criterio, con lo que no se pudo verificar.
  - **Se mira:** los seis criterios contestados; `./run.sh` en un clon limpio sin llaves arranca igual (si no se puede correr, se dice); backend y frontend en verde.

### Criterio de aceptación
1. El costo de cada ejecución en Langfuse es igual, a los 6 decimales, al `cost_usd` de su fila en `agent_runs`.
2. Con una ejecución normal y otra con `LLM_PROVIDER=gemini`, cada una muestra su costo con el precio de su modelo (tokens × precio de `LlmPricing`).
3. Se busca en Langfuse una frase conocida de una nota sintética, el alias de un paciente y un prompt, y no aparece ninguna.
4. Sin llaves, o con un host de Langfuse inalcanzable, el informe y la estructuración funcionan igual, y la mediana de estructuración no se aleja de la línea base de 1,40 s (`docs/decisiones/semana10.md`).
5. `scripts/check-env-example.sh` y Gitleaks pasan, y `./run.sh` en un clon limpio sigue levantando todo sin mencionar Langfuse.
6. Las pruebas existentes siguen en verde, más las nuevas.

Orden de magnitud a precio de lista (línea base de `docs/decisiones/semana10.md`): informe semanal cerca de 0,003 USD, mensual cerca de 0,009 y estructuración cerca de 0,0004. Un valor diez veces fuera de eso es una alarma.

### El paso más riesgoso
El **paso 2**. Si el JSON armado a mano no se registra como generación, o Langfuse recalcula el costo por su cuenta, cambia el diseño de los pasos 4 y 5.
- Si el JSON falla: se usa la librería de OpenTelemetry, que es una dependencia nueva y se pide permiso antes de instalarla.
- Si Langfuse recalcula el costo: se envía por el campo que sí respeta y, si no hay otro, se define el modelo en el proyecto con los precios de `LlmPricing`.
- Si nada funciona: se para y se informa antes de gastar más. Langfuse es un adicional, no una condición de entrega.

### Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 2 | El formato de los identificadores o de los números de OTLP en JSON no es el esperado | Respuesta 4xx, o 200 y el panel vacío tras un minuto |
| 2, 6 | Falta el encabezado `x-langfuse-ingestion-version=4` | Los datos tardan hasta 10 minutos en aparecer |
| 4, 5 | El `detail` del intento (texto técnico del proveedor) se cuela en las trazas | El buscador del panel encuentra texto de error; se manda solo la categoría del fallo |
| 5 | `Attempt` guarda la duración pero no la hora de inicio | Las generaciones salen superpuestas o desordenadas en la línea de tiempo |
| 4 | El envío se hace en el hilo de la petición | La mediana de estructuración sube frente a 1,40 s con el host inalcanzable |
| 5, 6 | Una ejecución mezcla dos modelos por el respaldo y la suma no coincide | El costo del panel difiere de `agent_runs.cost_usd` en el último decimal |
| 6 | La suite de `eval/` llena el proyecto compartido de trazas | Decenas de trazas de evaluación mezcladas con las de la demo |

### Qué se entrega al final
- Una pieza nueva en el paquete `llm`, con su configuración y pruebas; cambios pequeños en el agente y la estructuración.
- `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`, `LANGFUSE_BASE_URL` y `LANGFUSE_ENVIRONMENT` en `.env.example` y `docker-compose.yml`, sin valores reales.
- D28, el requisito nuevo, la entrada en `docs/decisiones.md`, `docs/gobernanza.md` y `AGENTS.md` y el resumen ejecutivo actualizados.
- `docs/decisiones/semana14.md` con la comparación de costos y el veredicto del criterio de aceptación.
- Cambios sin commit: el equipo los verifica y los sube.

## Pieza 2 — Dictado por voz de la nota (RF-38, D14)

**Origen:** RF-38 (hito Opc) y D14 (decidida) ya estaban especificados; el equipo pidió construirlo el 2026-11-05. **[Equipo]** Aprobado con dos condiciones explícitas: **el audio no se guarda en ninguna parte** y se respetan las decisiones de la SPEC y de los requisitos. **[Equipo]**

### Encargo
Que quien puede registrar entradas (`ENTRIES_CREATE`) dicte la nota desde la pantalla de registro: Whisper en Groq la transcribe, el texto cae en la caja del chat para corregirlo y sigue el flujo de siempre (estructuración, vista previa, confirmación). La entrada queda con origen `VOZ`, el audio no se guarda en ninguna parte y cada transcripción es una ejecución con traza, costo y Langfuse.

### Decisiones del encargo
| Duda | Decisión | Por qué |
|---|---|---|
| Transcriptor | Whisper en Groq detrás de un puerto (`Transcriber`), con la misma `GROQ_API_KEY`. El adaptador local que prevé D14 queda para después **[Equipo]** | Sin contenedor nuevo: no pesa en el arranque en frío. El puerto deja enchufar el local sin tocar el resto |
| Registro | Ejecución nueva `TRANSCRIPCION` en `agent_runs` (migración `V9`), con traza propia y traza en Langfuse **[Equipo]** | Es una llamada a un modelo: se mide como las demás (RF-22, RF-43) |
| Botón | Micrófono a la izquierda de «Enviar», con íconos SVG del sistema y sin emojis. Estados: Dictar → Detener (con contador) → Transcribiendo **[Equipo]** | Los mockups no tienen dictado; el equipo eligió el lugar |
| Tope de duración | 120 s por dictado **[Propuesta]** | Una nota diaria cabe de sobra; acota tamaño y costo |
| Origen `VOZ` | Si cualquier parte del texto enviado vino de un dictado, aunque luego se edite **[Propuesta]** | RF-38 pide la transcripción editable; editarla no cambia de dónde salió |
| Si Groq falla | Sin respaldo para transcribir: mensaje honesto y el registro por texto sigue igual **[Propuesta]** | D14 solo nombra a Groq y al local; la entrada nunca depende de la voz (RF-41) |
| Costo | Lo calcula el código: segundos de audio × precio de lista del modelo **[Propuesta]** | `AGENTS.md`: los números los calcula el código |
| Navegadores | La aplicación se probará en distintos navegadores **[Equipo, 2026-11-05]**. Objetivo: Chrome, Edge, Firefox y Safari en sus versiones actuales **[Propuesta]**. Cada uno graba en su formato (los de motor Chromium en webm/opus, Firefox en ogg u webm con opus, Safari en mp4/aac): la pantalla elige uno que el navegador soporte (`MediaRecorder.isTypeSupported`) y lo declara al enviarlo; el backend acepta solo los formatos que Groq transcribe | Un formato fijo dejaría fuera a Firefox o a Safari |
| Navegador que no puede grabar | Sin `MediaRecorder`, sin un formato aceptado o fuera de un contexto seguro, el micrófono se muestra deshabilitado con el motivo y escribir la nota funciona igual **[Propuesta]** | RF-41: la entrada nunca depende de la IA, tampoco de la voz |
| Audio | Ni en la base, ni en el disco del backend o de nginx (ni temporal), ni en registros ni en trazas **[Equipo]** | Condición de la aprobación; SPEC §3.2, RNF-02 y el diccionario («Audio de dictado: no se guarda») |

### Fuera del alcance
El Whisper local en contenedor · medir la calidad de la transcripción en `eval/` · transcribir en vivo mientras se habla · dictar en otros campos (texto corregido, informe) · mostrar el origen `VOZ` en el historial · guardar el audio · un respaldo con otro proveedor para transcribir.

### Supuestos
- Groq transcribe con Whisper en la capa gratuita, en español, y acepta los tres formatos que graban los navegadores: webm/opus, ogg/opus y mp4/aac. **Se prueba en el paso 2.**
- La demo se abre en `http://localhost`: todos los navegadores dan el micrófono solo en `localhost` o con HTTPS.
- En esta máquina hay Chrome 145, Firefox 159 y Brave 151 (motor Chromium, como Edge). **Safari no se puede probar aquí** (Linux): queda para un integrante con Mac o iPhone, y mientras tanto se reporta como no verificado.

### Pasos
- [x] 1. **Mirar** el punto de partida: backend y frontend en verde como línea base, y los enganches confirmados (`CreateEntryRequest`, `send()` de `entry-new`, `GroqClient`, la ruta del costo de `LangfuseRun`, los límites de tamaño de nginx y de Spring).
  - **Produce:** la línea base y la lista de enganches con archivo y línea.
  - **Se mira:** `mvn -B verify` y `ng test` en verde con el número anotado.
  > Hecho el 2026-11-05. Backend: **404 pruebas, 0 fallos, 14 omitidas** (las en vivo), `BUILD SUCCESS` en 4 min 14 s contra `ppa-test-db` (127.0.0.1:5439, encendida de nuevo; estaba detenida). Frontend: **129 de 129**.
  > Enganches: la creación es `CreateEntryRequest` (`EntryController.java:37`, sin campo de origen) y `EntryService.create` (`EntryService.java:80`; `source` queda en su valor por defecto `TEXTO`, `Entry.java:49`); la columna y su `CHECK (TEXTO, VOZ)` ya existen en `V3`. En la pantalla, la caja del chat es un `<input>` de una línea (`#rn-txt`) y `send()` lo lee y lo vacía (`entry-new.component.ts:196`). `GroqClient` llama con `JsonHttp`, que solo manda JSON: la transcripción de Groq pide `multipart/form-data`, que hay que armar en memoria. El costo hacia Langfuse sale de tokens (`LangfuseRun.costUsd`, `LangfusePayload`): el audio no tiene tokens, hay que extender esa ruta. No hay ícono de micrófono ni de detener en `core/icons.ts`.
  > **Hallazgo (condición del audio):** `frontend/nginx.conf` no fija `client_max_body_size` (1 MB por defecto) y nginx escribe a un archivo temporal los cuerpos de más de `client_body_buffer_size` (16 KB por defecto en 64 bits): sin configurarlo, **un dictado pasaría por el disco de nginx**. Spring tampoco fija límites de multipart; por eso el backend recibirá el audio como cuerpo crudo (`byte[]`), que Tomcat lee en memoria.
- [x] 2. **Probar** a mano la transcripción de Groq con un audio sintético en español, sin datos reales.
  - **Produce:** el modelo elegido, qué formatos acepta (webm/opus, ogg/opus y mp4/aac, los que graban Chromium, Firefox y Safari), si devuelve la duración, el tope de tamaño, el mínimo que cobra y el precio de lista verificado en la página de Groq.
  - **Se mira:** HTTP 200 con el texto esperado para cada uno de los tres formatos; las cifras salen de la respuesta o de la página, no de memoria.
  > Hecho el 2026-11-05. Audio sintético: la frase «Paciente con náuseas leves por la mañana. Vomitó dos veces después del almuerzo. Comió poco y durmió bien.» generada con `espeak-ng` dentro de un contenedor desechable (nada instalado en la máquina) y convertida con `ffmpeg` a webm/opus (35 KB), ogg/opus (33 KB), m4a/aac y mp4/aac (48 KB); 8,58 s. Sin datos reales.
  > **Resultado:** `POST https://api.groq.com/openai/v1/audio/transcriptions` (multipart: `file`, `model`, `language=es`, `response_format=verbose_json`, `temperature=0`) respondió **HTTP 200 con la transcripción exacta en los cuatro archivos** y con los dos modelos, en 382 a 518 ms. La respuesta trae `duration` (8,579 s), `text`, `segments`, `language` y `x_groq.id`; la duración no está en la documentación, así que el código no puede darla por segura (sin ella, costo vacío, no cero).
  > **Documentación de Groq** (`console.groq.com/docs/speech-to-text`, leída el 2026-11-05): modelos `whisper-large-v3-turbo` (**0,04 USD por hora** de audio) y `whisper-large-v3` (0,111 USD por hora); **se cobra un mínimo de 10 s** por petición; hasta **25 MB** por archivo en la capa gratuita; formatos flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav y webm; el audio se reduce a 16 kHz mono. En `docs/rate-limits`: 20 peticiones por minuto, 2 000 por día, 7 200 s de audio por hora y 28 800 por día (la página no deja claro si es la tabla del plan gratuito; la cabecera `x-ratelimit-limit-requests: 2000` coincide con las 2 000 por día). La página de precios de Groq no mostró las cifras (contenido dinámico): el precio sale solo de la de speech-to-text. La página no dice qué hace Groq con el audio; `docs/your-data` no se leyó todavía (paso 3, gobernanza).
  > **Decisión (propuesta):** `whisper-large-v3-turbo`. En la prueba acertó igual que `whisper-large-v3`, con la misma latencia, y cuesta 2,8 veces menos. Costo: un dictado de 8,6 s se cobra como 10 s (0,000111 USD); uno de 120 s, 0,001333 USD.
  > **Hallazgo 1 (honestidad, importante):** con **6 s de silencio** y con **6 s de ruido**, Whisper devolvió «Gracias.», con `no_speech_prob` = 0. Inventa texto y el servidor no lo puede detectar con los datos de la respuesta. Mitigación propuesta para el paso 5: la pantalla mide el nivel de la voz mientras graba y, si no oyó voz, no manda el audio a Groq («No se oyó ninguna voz; intente de nuevo o escriba la nota»). Además el texto nunca se envía solo: cae en la caja para revisarlo (RF-38).
  > **Hallazgo 2:** Groq (Cloudflare) rechaza con 403 «error code: 1010» el agente de usuario por defecto de Python. La aplicación usa el cliente de la JDK, que ya funciona con el chat de Groq; se confirma con la transcripción en el paso 6.
  > **Hallazgo 3 (tamaño):** a 32 kb/s, 120 s son unos 480 KB, pero los navegadores graban por defecto con más tasa (puede pasar de 1 MB). En el paso 5 se fija `audioBitsPerSecond` y el tope del servidor se pone con margen (propuesta: 5 MB), muy por debajo de los 25 MB de Groq.
- [x] 3. **Registrar** antes de programar: D14 y RF-38 al día en la SPEC y los requisitos, `TRANSCRIPCION` en los dos diccionarios, `docs/decisiones.md` («por qué Whisper», que el profesor preguntará), `docs/gobernanza.md` (el audio sale a Groq) y `AGENTS.md`.
  - **Se mira:** sin IDs inexistentes, tablas cuadradas y conteos por hito sin cambio (RF-38 sigue Opc).
  > Hecho el 2026-11-05. **SPEC v0.9:** nota de versión, D14 precisada (solo el adaptador Groq por ahora, `whisper-large-v3-turbo` configurable, ejecución `TRANSCRIPCION`, audio sin guardar, formatos de los navegadores) y dos casos borde nuevos en §5 (silencio y navegador que no puede grabar). **Requisitos v0.6:** nota de versión y el supuesto del dictado en §8; RF-38 no cambia de texto ni de hito. **Diccionarios:** `agent_runs.run_type` con `TRANSCRIPCION` (`V9`) en los dos, y de paso con `MENSUAL`, que existía desde `V7` y los diccionarios no listaban. **`docs/decisiones.md`:** sección «Dictado por voz: Whisper en Groq», con el «por qué Whisper» y la alternativa descartada de cada decisión. **`docs/gobernanza.md`:** Groq-Whisper como tercero que recibe el audio, el párrafo del audio sin guardar y lo que dice Groq de sus datos. **`AGENTS.md`** y el resumen ejecutivo (fila D14), al día.
  > Comprobación (script): ningún ID citado inexistente, ninguna fila descuadrada, conteos por hito sin cambio (43 RF: 29 M2-N, 2 repartidos, 7 M2-S, 3 M3, 2 Opc) y ninguna llave en los archivos cambiados.
  > **Condición del equipo sobre el modelo (2026-11-05):** relación precio-calidad. Se eligió `whisper-large-v3-turbo` (mismo resultado que `whisper-large-v3` en la prueba, 0,04 frente a 0,111 USD por hora) y queda configurable para cambiarlo sin código si con voces reales falla.
  > **Hallazgo (gobernanza):** según `console.groq.com/docs/your-data`, Groq no retiene por defecto, pero **puede guardar entradas y salidas hasta 30 días** para fiabilidad o abuso, salvo que se active *Zero Data Retention* en la consola; no dice si entrena con los datos. La aplicación cumple la condición del audio por su lado, pero para que Groq tampoco lo conserve el equipo debe activar ZDR (acción en la consola de Groq, no en el código).
  > **Decisiones que nadie pidió:** completar `MENSUAL` en los diccionarios; marcar como **[Propuesta]** el tope de 120 s y 5 MB, no enviar audio sin voz, el origen `VOZ` aunque se edite y no tener respaldo para transcribir.
- [x] 4. **Construir el backend:** puerto `Transcriber` y adaptador Groq; `POST …/entries/transcribe` con el audio como cuerpo crudo (sin multipart) y tope de tamaño; la ejecución `TRANSCRIPCION` (`V9`) con traza, costo y Langfuse; `source` al crear la entrada; en nginx el tamaño y sin búfer a disco.
  - **Se mira:** pruebas verdes que cubren que el audio no se escribe a disco ni a registros, el tope, el fallo de Groq sin llave o caído, el costo y el origen `VOZ`.
  > Hecho el 2026-11-05. Backend con `mvn clean verify`: **436 pruebas, 0 fallos, 14 omitidas** (las en vivo), `BUILD SUCCESS` en 9 min 1 s (404 de partida más 32 nuevas). `scripts/check-env-example.sh` en verde (33 variables) y `nginx -t` acepta la configuración. El frontend no se tocó en este paso.
  > **Qué quedó:** en `llm`, el puerto `Transcriber` y su adaptador `GroqTranscriber` (multipart armado en memoria, `language=es`, `verbose_json`), `AudioFormat` (webm, ogg y mp4, los de los navegadores), `TranscriptionGateway` (reintentos con las mismas esperas que el chat dentro de un presupuesto de 30 s, mensaje honesto, costo), y el precio por hora de audio en `LlmPricing` con el mínimo de 10 s; `Attempt` ganó `audioSeconds` y Langfuse recibe ese costo. `JsonHttp` manda bytes y comparte con `GroqClient` la traducción de errores HTTP. En `dictation`, `DictationService`: lee el cuerpo **a memoria con tope de 5 MB**, valida formato y paciente, deja la ejecución `TRANSCRIPCION` (`V9`) con su traza (intentos y «Transcripción: 8,6 s de audio (N bytes), M caracteres», nunca el texto) y la manda a Langfuse, y **borra el arreglo del audio al terminar**. La ruta es `POST /api/patients/{id}/entries/transcribe` (permiso `ENTRIES_CREATE`, cuerpo crudo, sin multipart). La creación de entradas acepta `source` (`VOZ` o `TEXTO`) y la respuesta lo devuelve. `/api/llm/status` dice si el dictado está disponible. nginx tiene una ruta propia para el dictado: hasta 5 MB, búfer en memoria del mismo tamaño y sin búfer de petición (`proxy_request_buffering off`). `GROQ_TRANSCRIPTION_MODEL` en `application.yml`, `.env.example` y el compose.
  > **La condición del audio, probada:** `DictationApiTest` comprueba que el audio llega íntegro a Whisper, que **después el arreglo queda en ceros**, y que ni el audio ni el texto transcrito aparecen en el registro del servidor, en `agent_steps` ni en lo que se entrega a Langfuse. `DictationNoDiskTest`, con Tomcat de verdad, manda 2 MB y busca la marca del audio en todos los archivos del directorio temporal: ninguno la tiene. Lo que no cubren las pruebas: el disco de nginx (se mira en el paso 6) y lo que haga Groq (ZDR, paso 3).
  > **Las pruebas detectan lo que dicen:** rompí el código a propósito y fallaron las esperadas: no borrar el arreglo, escribir el audio en el registro, poner el texto en la traza y escribir el audio en un archivo temporal.
  > **Hallazgo 1 (prueba que pasaba en falso, corregida):** la primera versión de `DictationNoDiskTest` **no detectó** el archivo temporal: al encontrar un directorio de `/tmp` sin permiso de lectura, la excepción cortaba todo el recorrido y devolvía la lista vacía. Se reescribió para saltar solo lo ilegible, y entonces encontró `/tmp/dictado….webm`. Sin la rotura deliberada la prueba habría quedado como falsa garantía de la condición del equipo.
  > **Hallazgo 2 (compilación incremental):** tras cambiar el constructor de `LlmStatusController`, `mvn test-compile` dio `BUILD SUCCESS` con clases de prueba viejas; solo `mvn clean test-compile` mostró el error. La verificación de cada paso se hace con `clean`.
  > **Hallazgo 3:** la duración en la traza salía con el idioma de la máquina («8,6» aquí, «8.6» en un contenedor en inglés). Se fijó en español.
  > **Decisiones que nadie pidió:** (1) los reintentos y el costo de la transcripción viven en la capa `llm` (`TranscriptionGateway`), paralela a `LlmGateway`, para que `LlmPricing` siga siendo privado de la capa; la regla de espera está duplicada (unas 15 líneas) en vez de compartida; (2) `EntryView` devuelve `source`; (3) `/api/llm/status` agrega `transcription` (proveedor, si tiene llave y modelo) para que la pantalla deshabilite el micrófono sin llave; (4) un formato no admitido responde 415, un audio vacío 400 y uno grande 413, con mensaje en español; si Groq falla, 200 con `failure`, como la estructuración; (5) si Whisper devuelve un texto vacío, se informa «No se reconoció ninguna voz» en lugar de dejar la caja vacía; (6) el multipart lleva `temperature=0`.
- [x] 5. **Construir la pantalla:** grabar con `MediaRecorder` en el formato que soporte cada navegador, deshabilitar el micrófono con su motivo si no puede grabar, estados con íconos nuevos, el texto en la caja y editable, mensajes para el micrófono negado o ausente y el fallo de Groq, y enviar con origen `VOZ`.
  - **Se mira:** `ng test` verde con las pruebas nuevas; la pantalla sin dictar se comporta igual que antes.
  > Hecho el 2026-11-05. Frontend: **145 de 145** (129 de partida más 16 nuevas: 3 del grabador y 13 de la pantalla); las 129 anteriores siguen en verde sin cambios, así que la pantalla sin dictar se comporta igual. `ng build` de producción sin avisos de tamaño (386 kB iniciales). El backend no se tocó en este paso.
  > **Qué quedó:** `core/voice-recorder.service.ts` graba con `MediaRecorder` en el primer formato que soporte el navegador (webm/opus, webm, ogg/opus, mp4), a 32 kb/s, y mide el nivel de la voz cada 100 ms con un `AnalyserNode` que solo lee el volumen; dice por qué no puede dictar (contexto inseguro, navegador sin grabación, permiso negado, sin micrófono) y suelta el micrófono al detener o cancelar. En la pantalla de registro, el **micrófono va a la izquierda de «Enviar»** con íconos SVG nuevos del mismo trazo (`microfono`, `detener`) y sin emojis: Dictar → «Detener 0:12» (cuadro rojo que late; quieto con movimiento reducido) → «Transcribiendo» (el anillo de los botones de proceso). El texto se agrega a lo que haya en la caja y queda en foco para corregirlo; nada se guarda al dictar. La entrada se envía con `source: 'VOZ'` si alguna parte vino de un dictado, aunque se haya editado; si se borra toda la caja, deja de serlo.
  > **Comportamientos probados:** sin llave de Groq o en un navegador que no puede grabar, el micrófono queda deshabilitado (`aria-disabled`) con el motivo en el `title` y, al pulsarlo, en un aviso; sin voz el audio **no sale del navegador**; si la transcripción falla, la caja queda como estaba y se dice por qué; con el permiso negado lo dice y no queda grabando; a los 120 s corta solo y transcribe; salir de la pantalla suelta el micrófono.
  > **Las pruebas detectan lo que dicen:** rompí el código a propósito tres veces y fallaron las tres esperadas (guardar sin origen `VOZ`, mandar el silencio a Groq e ignorar la falta de llave).
  > **Decisiones que nadie pidió:** (1) el grabador es un servicio aparte para poder sustituirlo en las pruebas; (2) el micrófono deshabilitado sigue siendo pulsable (`aria-disabled`, no `disabled`) para poder explicar el motivo con un aviso, que un botón `disabled` no permite; (3) los avisos usan los mismos de la aplicación (toast), sin texto nuevo fijo en la pantalla; (4) el umbral de voz (`VOICE_LEVEL` 0,02, tres muestras de 100 ms) es una **[Propuesta]** sin calibrar: se ajusta en el paso 6 con un micrófono real; (5) la caja sigue siendo de una línea, como el mockup.
  > **No verificado:** nada de esto corrió con un micrófono real: en las pruebas el grabador es un sustituto, y Chrome sin interfaz no da micrófono. `MediaRecorder`, el formato elegido por cada navegador, el nivel de voz y el permiso se miran en el paso 6 en Chrome, Firefox y Brave; Safari sigue sin poder probarse aquí.
- [ ] 6. **Verificar en vivo** en Chrome, Firefox y Brave (Chromium, como Edge); Safari solo si el equipo lo prueba en un Mac o iPhone: un dictado real en cada uno, uno sin llave, uno con el permiso del micrófono negado, uno de más de 120 s; buscar el audio en el disco de los contenedores y la traza en Langfuse.
  - **Se mira:** los seis criterios con evidencia.
- [ ] 7. **Comprobar** el criterio de aceptación, punto por punto, y dejar la evidencia.

### Criterio de aceptación
1. En Chrome, Edge, Firefox y Safari (versiones actuales), en `http://localhost:4200`, una nota dictada aparece en la caja, se puede editar y, al guardarla, la entrada queda con `source = 'VOZ'`; una escrita sigue con `TEXTO`.
2. El audio no queda en la base, ni en el disco del backend o de nginx (ni como archivo temporal), ni en los registros ni en las trazas.
3. Cada dictado es una fila `TRANSCRIPCION` con su traza; su costo es igual al de Langfuse a 6 decimales y Langfuse no recibe el texto.
4. Sin llave, con Groq caído, sin micrófono, con el permiso negado o en un navegador que no puede grabar, hay un mensaje claro y escribir la nota funciona igual.
5. Un dictado de más de 120 s o un audio demasiado grande se corta o se rechaza con un mensaje claro, sin traza de pila.
6. Las pruebas existentes siguen verdes, más las nuevas; `scripts/check-env-example.sh` pasa y `./run.sh` no cambia para el profesor.

### El paso más riesgoso
El **paso 2**. Si Groq no acepta el webm de Chrome, o el precio y los límites no son los esperados, cambia el diseño de los pasos 4 y 5. Si no transcribe en la capa gratuita, se para y se informa.

### Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 4 | nginx y Spring aceptan hasta 1 MB por defecto | 413 con dictados largos |
| 4 | nginx o Tomcat escriben a disco los cuerpos grandes | Archivos en el directorio temporal del contenedor durante la petición |
| 5, 6 | El micrófono solo funciona en `localhost` o HTTPS | En otra máquina por IP el botón no puede grabar |
| 2, 5 | Cada navegador graba en otro formato (Safari en mp4/aac, Firefox en ogg) | Groq rechaza el formato o el navegador no ofrece el que se pidió |
| 5, 6 | Safari pide el micrófono de otra forma (solo tras un gesto, y en iPhone cada vez) | El permiso no aparece o la grabación sale vacía |
| 6 | Safari no se puede probar en Linux | El criterio 1 queda sin verificar para Safari hasta que el equipo lo pruebe |
| 4 | El costo por audio no cabe en la ruta del costo por tokens de `LangfuseRun` | El costo en Langfuse queda vacío o en cero |


## Pieza 3 — «Enviar al médico» y el estado `ENVIADO` (enfermería, como el mockup)

**Origen:** en el mockup vA, el informe aprobado de enfermería tiene el botón «Enviar al médico» y hay un estado más, «Enviado»: aprobar guarda el informe y enviarlo es lo que lo hace llegar al médico. En E12b se quitó y se decidió que aprobar bastaba (`docs/decisiones.md`); el equipo pidió el 2026-11-07 que la aplicación sea lo más fiel posible a los mockups y que se implemente. **[Equipo]**

**Cómo se reparte el módulo del médico:** en cuatro piezas, en este orden: «Enviar al médico» (esta), las pantallas del médico y su registro (Pieza 4), las notificaciones (Pieza 5) y «Armar informe» (Pieza 6). Juntas pasan de siete pasos, y `planea-trabajo` pide partir el encargo en ese caso. Esta va primero porque decide qué informes ve el médico; las piezas 5 y 6 se montan sobre el menú, las rutas y la ficha de la 4. **[Propuesta]**

### Encargo
Que enfermería, en un informe aprobado, pulse «Enviar al médico» y el informe pase a `ENVIADO` con quién y cuándo lo envió; que el médico vea solo informes `ENVIADO`; y que los textos, botones y estados de las pantallas de enfermería que hablan del médico queden como en el mockup.

### Qué falta en enfermería frente al mockup (análisis del 2026-11-07)
Se revisaron las pantallas de enfermería del mockup vA (`docs/07-mockups/mockups-vA/mockups/auditor.fuente.html`) contra las construidas, buscando todo lo que tiene que ver con el médico. **[Propuesta]**

| Pantalla del mockup | Elemento | Hoy en la aplicación | Qué se hace | Pieza |
|---|---|---|---|---|
| Informe en revisión | Aviso «Esta es la vista previa tal como la verá el médico…» | Igual | Nada | — |
| Informe en revisión | Nota «Revise el texto y las fuentes. Al aprobar, el informe queda guardado, pero todavía no llega al médico.» | Cambiada en E12b a «…y el médico podrá consultarlo.» | Volver al texto del mockup | 3 |
| Informe aprobado | Nota «Informe guardado. Puede descargarlo o enviarlo al médico.» | «Informe aprobado y guardado. Ya puede consultarlo el médico y descargarlo en PDF.» | Volver al texto del mockup | 3 |
| Informe aprobado | «Enviar al médico» (primario, «Enviando», ícono de enviar) junto a «Descargar PDF» (secundario) | Solo «Descargar PDF» | Agregar el botón | 3 |
| Informe enviado | Estado «Enviado» (chip verde), nota «Enviado al médico el …» y solo «Descargar PDF» | No existe | Agregar el estado, la nota y el chip | 3 |
| Hoja del informe | «Aprobado por: …» en la cabecera | Solo con `APROBADO` | Mostrarlo también con `ENVIADO` | 3 |
| Lista «Aprobados», lista de pacientes y ficha | Chip «Enviado» | La lista ya muestra todo lo que no está en revisión; falta la etiqueta | Etiqueta y color del mockup | 3 |
| Al enviar | Aviso «Informe enviado. Se notificó al médico» y notificación «Informe recibido» al médico | No existe | En esta pieza, «Informe enviado al médico.»; la frase del mockup llega con las notificaciones, cuando sea cierta | 3 y 5 |
| Nuevo registro | Aviso «Registro guardado. Se notificó al médico.» | «Registro guardado.» | Con las notificaciones | 5 |
| No encontrado | «Volver al inicio» lleva al inicio del rol | Fijo a enfermería | Con las pantallas del médico | 4 |
| Encabezado | Selector de rol (Enfermería, Médico, Administrador) | «Ver como» solo con enfermeras | Con las pantallas del médico; el administrador, con RF-33 | 4 |

Lo demás del mockup de enfermería que no coincide (por ejemplo, generar un informe de un mes calendario, de un rango de fechas o comparado con otro período) no tiene que ver con el médico y no entra en estas piezas.

### Decisiones del encargo
| Duda | Decisión | Por qué |
|---|---|---|
| Implementar «Enviar al médico» y `ENVIADO` | Sí, como el mockup **[Equipo, 2026-11-07]** | Fidelidad al mockup. Reemplaza la decisión de E12b (aprobar bastaba), que queda como alternativa descartada en `docs/decisiones.md` |
| Qué ve el médico (RN-01, D6) | Solo informes `ENVIADO` **[Equipo, 2026-11-07]** | En el mockup el médico solo ve «Enviado» (`vInfVer` y `vInfLista`). La supervisión humana queda en dos pasos de enfermería: aprobar y enviar |
| Transiciones | `APROBADO → ENVIADO` es la única nueva; `ENVIADO` es final **[Propuesta]** | Un enviado no se retira ni se reabre, igual que hoy un aprobado: un cambio se hace con un informe nuevo |
| Quién envía | Cualquier usuario de enfermería, con un permiso nuevo `REPORTS_SEND` **[Propuesta]** | En el mockup lo hace enfermería. Un permiso propio deja al tablero de permisos (RF-34) separarlo de aprobar |
| Qué se guarda | Quién y cuándo envió (`sent_by`, `sent_at`) **[Propuesta]** | La nota «Enviado al médico el …» y la columna «Recibido» del médico salen de ahí; RF-35 pide saber quién hizo cada acción sensible |
| Un enviado para las demás reglas | Cuenta como aprobado: vale para el mensual (RN-10), es el informe vigente de su semana, se imprime en PDF y el agente lo lee como aprobado **[Propuesta]** | Enviar no cambia el contenido aprobado |
| ¿Es un envío automático o por correo? | No: lo pulsa una persona y el médico lo ve dentro de la aplicación **[Propuesta]** | El envío automático y por correo siguen fuera del alcance (SPEC §3) y la pata de salida de la tríada sigue cerrada: el agente no tiene herramienta para enviar |
| Semilla | De los cuatro aprobados de P-014, las tres primeras semanas quedan `ENVIADO` y la cuarta `APROBADO`, para enviarla en vivo **[Propuesta]** | El médico abre la demo con informes recibidos y enfermería tiene uno para enviar. Cambia el reparto de D11 |
| Cómo se actualiza una base ya sembrada | La semilla reconcilia: tras insertar, avanza `APROBADO → ENVIADO` solo en los informes que declara enviados, nunca al revés; `V10` es solo de esquema **[Propuesta]**, decidido el 2026-11-07 al cerrar el paso 1 | La semilla es la dueña de los datos de demostración (D23, RF-05); una migración con los UUID de P-014 pondría datos de demostración en el perfil `prod`. Detalle y alternativas en `docs/decisiones.md` |
| La columna «Generado» de la lista de enfermería | Muestra la fecha de generación **[Propuesta]** | El mockup pone ahí la de envío cuando existe, que no coincide con el título de la columna |

### Fuera del alcance
Las pantallas del médico (Pieza 4) · las notificaciones y las frases «Se notificó al médico» (Pieza 5) · retirar o reenviar un informe enviado · enviar a un médico en particular · envío por correo o mensaje · envío automático al aprobar.

### Supuestos
- Lo que hoy decide por `APROBADO` está en estos lugares, encontrados el 2026-11-07 con una búsqueda: en el backend, `Report.Status`, `ReportTransitions`, `ReportVisibility`, `ReportService` (aprobar y el informe vigente), `MonthlyEligibility`, `ApprovedWeeklyReports`, `AgentRunner` (informe vigente), `ReportPdfService`, `DemoDataSeeder` y las restricciones y el índice de `reports` en `V3`; en el frontend, `ReportStatus`, `report-view`, `status-chip` y `report-list`; y en `eval/`, `checks.py`, `monthly.py` y `test_eval.py`. **Se confirma en el paso 1.**
- Las pruebas que hoy fijan «el médico ve los aprobados» cambian a propósito; las demás no cambian.

### Pasos
- [x] 1. **Mirar** el punto de partida: línea base de pruebas (backend, frontend y `eval/` sin llave), cada lugar que decide por `APROBADO` con archivo y línea, y las pruebas que lo fijan.
  - **Produce:** la línea base y la lista de enganches y de pruebas que cambian.
  - **Se mira:** `mvn -B verify`, `ng test` y `python3 eval/run_eval.py --baseline-only` en verde con el número anotado.
  > Hecho el 2026-11-07. **Aprobación anotada** en la cabecera de este plan (las piezas 3 a 6, «aprobadas el 2026-11-07»). Línea base, con el árbol limpio: **backend 436 pruebas, 0 fallos, 14 omitidas** (`mvn clean verify`, 9 min 4 s, contra `ppa-test-db`, que estaba detenida y se encendió); **frontend 145 de 145**; **`eval/` sin llave**: informes 7 de 7, mensual 1 de 1, estructuración con la solución convencional 15 de 28 exactos (0,5357; 2 falsos positivos, 16 omitidos), y las **36 pruebas de `eval/` en verde**. Lo de `--baseline-only` no es una suite que pase o falle: mide la solución sin modelo, y que falle 13 de 28 casos de estructuración es lo esperado (`docs/decisiones/semana10.md` documentaba 14 de 24 antes de los 4 casos nuevos de E19).
  > **Enganches confirmados (el plan decía «se confirma en el paso 1»; todos existen y hay otros que no listaba):**
  > *Backend, decide por el estado.* `Report.java:28` (enum). `ReportTransitions.java:17-20` (mapa de transiciones; `APROBADO` hoy es final), `:36` y `:49` (mensajes). `ReportVisibility.java:18` (el médico ve solo `APROBADO`; de ahí cuelgan también `ReportService.java:102` el listado, `:251-254` el detalle, `:257-259` `isDoctor`, y `PatientService.java:99` con `ReportRepository.java:49-54` el último informe de cada paciente). `ReportService.java:187-195` (aprobar), `:233` (informe vigente al ofrecer semanas), `:262-266` (la versión aprobada). `MonthlyEligibility.java:59` (vigente), `:90-95` (texto por estado, un `switch`) y `:101-105` (cuál semana cuenta como aprobada). `ApprovedWeeklyReports.java:41` (el agente solo lee `APROBADO`). `AgentRunner.java:156` (informe vigente). `ReportPdfService.java:51-57` (solo imprime `APROBADO`; otro `switch`). `ReportPdfBlocks.java:59-60` («Aprobado por» sale de `reviewedBy` y `reviewedAt`, que deben conservarse en `ENVIADO`). `DemoDataSeeder.java:179-186` y `demo-reports.json:6, 63, 130, 214` (los cuatro aprobados; el pendiente está en la 296). `ReportController.java:91-95` (aprobar; no hay ruta de enviar). `V3`: `:139` (estados), `:145-146` (`reports_review_check` y `reports_approved_version_check`) y `:151-153` (`uq_reports_current_period`). `V4`: el permiso `REPORTS_APPROVE` y su asignación a `ENFERMERIA`.
  > *Frontend.* `reports.service.ts:5` (tipo `ReportStatus`). `status-chip.component.ts:4-9` (colores) y `:12-17` (etiquetas; de ahí leen también la lista de pacientes y la ficha, `patients-list` y `patient-detail`). `report-view.component.ts:84` (qué botones salen según el estado), `:94` (aviso de vista previa), `:179` («Aprobado por», solo con `APROBADO`), `:334-342` (las notas), `:392-395` (marca del menú) y `:493` (aviso al aprobar). `report-list.component.ts:50-52` y `:63` (el filtro de «Aprobados» es todo lo que no está en revisión). `app.routes.ts:31` y `menu.ts:30`.
  > *`eval/`.* `test_eval.py:170-173` arma el conjunto de semanas sembradas filtrando por `APROBADO`. `checks.py:70` y `monthly.py:56` se refieren al estado de un borrador recién generado (`PENDIENTE_REVISION`) y **no cambian**.
  > **Pruebas que cambian a propósito (resultado distinto).** *Backend:* `ReportsApiTest` (el listado del médico, `:94-107`; el detalle del médico tras aprobar, `:236-248`; la traza del médico de un informe `APROBADO`, `:293-309`), `PatientsApiTest` (`:156-164`, el último informe que ve el médico), `ReportPdfApiTest` (`:122-133`, `:148-158` y el mensual de `:180-199`, que además consulta `status = 'APROBADO'` en `:184`), `MonthlyReportsApiTest` (`:66`, la misma consulta, y `:104-118`, el médico tras aprobar), `SeededReportsTest` (`:60-85`, estados y revisores esperados, y `:177-192`, lo que ve el médico), `AgentRunnerTest` (`:292-293`, `containsOnly("APROBADO")` de las semanas enlazadas al mensual), `DraftValidatorTest` (`:396` y `:420`, la consulta por `APROBADO` de las cuatro semanas), `RunSaturationTest` (`:67-68`, un SQL que copia el índice de vigente y debe sumar `ENVIADO`) y `ReportTransitionsTest` (`:12-20`, la matriz esperada). *Frontend:* `report-view.component.spec.ts` (`:151-166`, el flujo de aprobar con su nota y botones, y `:168-180`, «Descargar PDF» pasa a ir con «Enviar al médico»; la nota de revisión de `:147` sigue pasando porque solo comprueba el comienzo). *`eval/`:* `test_eval.py:170-173`.
  > **Pruebas que NO deben cambiar** (guardas de regresión; si alguna cambia, se para): todas las de enfermería que no tocan al médico, `MonthlyEligibilityTest` (usa `APROBADO` de verdad y sigue valiendo), `ReportConcurrencyTest`, `RunsApiTest` (`:219`, P-014 conserva su cuarto informe `APROBADO` y por eso ya tiene uno vigente), `MonthlyRunsApiTest` (pide las cuatro semanas sembradas: pasa solo si el mensual acepta `ENVIADO`), `PatientsApiTest:57` (enfermería ve el último de P-014, el del 12-oct, `APROBADO`), `PermissionMatrixTest` y `DomainSchemaTest` (no fijan el conjunto exacto de permisos de enfermería; se amplían, no cambian), y las 129 de `report-list`, `patient-detail`, `report-generate` y el resto del frontend.
  > **Hallazgo 1 (importante para el paso 3): la semilla no cambia una base ya sembrada.** `DemoDataSeeder` inserta con `ON CONFLICT DO NOTHING` e ids fijos (`:36`, `:182`), así que en una base que ya tiene los informes (hoy `ppa-test-db` los trae los cuatro `APROBADO`, y también el volumen de Docker de quien ya corrió `./run.sh`) los tres primeros **seguirán `APROBADO` después de la migración**. Con una base nueva sí quedarían `ENVIADO`. Las pruebas que esperan tres enviados fallarían en la base de pruebas de siempre y pasarían en una nueva: verde que depende del estado de la base. El plan dice «la semilla es idempotente» pero no cómo se actualiza lo ya sembrado. **Hay que decidirlo antes de programar el paso 3** (opciones: que `V10` marque como enviados esos tres informes cuando existan, o que la semilla actualice esas filas; la tercera, borrar el volumen, no sirve para quien ya tiene datos). Propuesta: la migración, porque no depende de que la semilla corra.
  > *Resuelto en el paso 2 (2026-11-07):* la semilla reconcilia y `V10` no toca datos de demostración; razones y alternativas en `docs/decisiones.md`.
  > **Hallazgo 2:** `V3:145` (`reports_review_check`) exige que `reviewed_by` y `reviewed_at` estén **vacíos** salvo en `APROBADO` y `RECHAZADO`; un informe `ENVIADO` los conserva, así que `V10` tiene que reemplazar esa restricción, no solo agregar el estado. `reports_approved_version_check` (`:146`) pide `approved_version_no` solo si es `APROBADO`: un `ENVIADO` también debe tenerlo. Ya estaba en «dónde se puede caer»; queda confirmado leyendo el SQL.
  > **Hallazgo 3 (el compilador ayuda):** hay dos `switch` exhaustivos sobre el estado, `MonthlyEligibility.java:90-95` y `ReportPdfService.java:52-57`: al agregar `ENVIADO` al enum no compilan hasta que se completen, así que no se pueden olvidar. Los demás sitios usan comparaciones y listas, que el compilador no vigila: son los que el paso 3 recorre con esta lista.
  > **Hallazgo 4:** `Summary` y `Detail` (`ReportService.java:51-52` y `:65`) solo llevan `reviewedBy` y `reviewedAt`; la nota «Enviado al médico el …» necesita `sentAt` (y `sentBy`) en la respuesta de la API. Es parte del paso 3.
  > **Decisiones que nadie pidió:** ninguna de diseño. Sí dejé un archivo: `python3 eval/run_eval.py --baseline-only`, el comando que pide el plan, **guarda siempre** un resultado fechado y creó `eval/results/2026-11-07-1532.json` (sin versionar). No lo borré, por la regla de preguntar antes de borrar; si no lo quieren, se quita a mano.
  > **No verificado:** que la lista de pruebas que cambian sea exhaustiva: sale de leer el código y buscar `APROBADO`, el médico y las consultas por estado, no de correr el cambio; la prueba real es el paso 3 y su regla de parar si cambia una que no figura aquí. Tampoco corrí `eval/` con llave (no toca este paso).
- [x] 2. **Registrar** antes de programar: D29 en la SPEC §7, RN-01 y D6 reescritas, D11 (el reparto de la semilla) y la tabla de autonomía de §4; RF-44 (enviar al médico, hito M3), y RF-28 y CU-05 con «enviado», en los requisitos; el estado, las columnas, la transición y el permiso en los dos diccionarios; `docs/decisiones.md` (el cambio frente a E12b, con la alternativa descartada), `docs/gobernanza.md` y `AGENTS.md`.
  - **Se mira:** sin IDs inexistentes, tablas cuadradas, conteos por hito sumados y ninguna frase que diga que el médico ve los aprobados.
  > Hecho el 2026-11-07. **Qué quedó:** D29 en `docs/SPEC.md` §7 (v0.10), con D6 reescrita (ahora decidida: solo `ENVIADO`) y D11 con la semilla repartida (3 `ENVIADO`, 1 `APROBADO` y 1 `PENDIENTE_REVISION`); la tabla de autonomía y la de permisos de §4, OBJ-05 y OBJ-15, §1, §3.1 y dos casos borde nuevos (enviar dos veces y el médico que pide un no enviado). `docs/REQUISITOS.md` v0.7: **RF-44** (M3), RN-01 reescrita, RN-02 y RN-10 ajustadas, RF-23, RF-28, RF-29 y RF-37, CU-04, CU-05, CU-09 y el flujo crítico con el paso de enviar, trazabilidad y §8. Los dos diccionarios: estado `ENVIADO`, columnas `sent_by` y `sent_at`, las restricciones de `reports` (la de revisión se **reemplaza**, la de versión aprobada se **extiende** y la de envío es nueva), el índice de vigente con `ENVIADO`, la transición `APROBADO → ENVIADO`, el permiso `REPORTS_SEND`, la acción de auditoría `ENVIAR_INFORME` (solo el completo), la semilla y la trazabilidad. `docs/decisiones.md`: sección «Enviar al médico y el estado `ENVIADO`» con diez decisiones y sus alternativas descartadas (E12b entre ellas), y marcas «cambiado el 2026-11-07» en las filas viejas que decían lo contrario. `docs/gobernanza.md` y `AGENTS.md`: reglas y pendientes al día, diciendo qué hace el código hoy y qué hará.
  > **Comprobación (script, sobre `AGENTS.md` y los seis documentos):** ningún ID inexistente (RF hasta 44, RN hasta 11, CU hasta 10, D hasta 29); ninguna tabla descuadrada que no lo estuviera ya en el último commit; RF-44 y D29 una vez cada uno; **conteo por hito de los RF: 44 en total, M2-N 29, M2-S 7, M3 4, Opc 2**, más las dos filas de hito mixto (RF-32 y RF-39), que ya lo eran. Las frases «el médico ve los aprobados» que quedan son las históricas con su marca de cambio (E6, E12a, E12b y la fila de D26 en `decisiones.md`), D6 y la gobernanza diciendo «hoy», y `AGENTS.md` aclarando «con D29 pasará a ser…».
  > **Decisión tomada en este paso (la pidió el equipo al cerrar el paso 1): la semilla reconcilia y la migración no toca datos de demostración.** Razón: la semilla ya es la dueña de los datos de demostración (los diccionarios fijan que los roles y permisos van en migración y los usuarios y el conjunto sintético, en el `ApplicationRunner`), así que `V10` queda solo de esquema y vale en cualquier base; tras insertar, la semilla avanza `APROBADO → ENVIADO` solo en los informes que declara enviados, nunca al revés ni en otro. Alternativas descartadas: un `UPDATE` con UUID dentro de `V10` (datos de demostración en una migración que corre también con `prod`) y borrar el volumen. Escrita en la tabla de decisiones de esta pieza, en SPEC D29 y en `docs/decisiones.md`.
  > **Decisiones que nadie pidió:** (1) numeré la SPEC v0.10 y los requisitos v0.7, siguiendo el patrón de las versiones anteriores; (2) puse RF-44 en la tabla de 3.4 (revisión y aprobación) y no en 3.7, junto a aprobar; (3) RF-23 y RF-28 **conservan su hito M2-N** aunque su parte nueva (el estado `ENVIADO` y que el médico vea solo enviados) se construye en el Momento 3: está dicho en la cabecera de los requisitos; (4) cambié CU-04 a «Aprobar, rechazar o enviar» y le sumé RF-44, porque el plan solo nombraba CU-05; (5) el diccionario recortado al Momento 2 recoge lo de M3 marcado «M3», porque dos documentos sobre la misma tabla no pueden decir cosas distintas.
  > **No verificado:** que `V10` y las restricciones escritas aquí funcionen en PostgreSQL: se prueban en el paso 3. **No toqué** `docs/entregables/RESUMEN_EJECUTIVO.md` (copia de una versión anterior de la SPEC y los requisitos: todavía dice «Supuesto de trabajo» en D6 y no trae RF-43; si el equipo la quiere al día es otra tarea), `docs/PLAN.md` (plan cerrado del Momento 2), ni el `README.md` (paso 6). Revisé `docs/decisiones/semana06.md` (autonomía de la semana 6): solo dice que el agente no puede aprobar, sigue siendo cierto y no lo cambié.
- [x] 3. **Construir el backend:** la migración (`V10`: estado, columnas, restricciones, índice del informe vigente y permiso), la transición y la ruta de enviar, la visibilidad del médico, que el enviado cuente como aprobado en el mensual, el informe vigente, el PDF y el agente, y y la semilla (que además reconcilia una base ya sembrada: avanza a `ENVIADO` solo los informes que declara enviados).
  - **Se mira:** pruebas verdes que cubren que solo enfermería envía (el médico, 403), que solo un aprobado se envía (los demás, 409), que el médico no ve un aprobado sin enviar (404), que un mes con semanas enviadas se puede generar, que la semilla es idempotente y que en una base que ya tiene los cuatro `APROBADO` (como `ppa-test-db`) deja tres `ENVIADO` y uno `APROBADO`.
  > Hecho el 2026-11-07. **Qué quedó:** `V10__report_sent.sql` (solo esquema y permiso: columnas `sent_by` y `sent_at`, `reports_status_check` con `ENVIADO`, `reports_review_check` **reemplazada**, `reports_approved_version_check` **extendida**, `reports_sent_check` nueva, `uq_reports_current_period` con `ENVIADO` y el permiso `REPORTS_SEND` para `ENFERMERIA`, id `…606`). `Report.Status` con `ENVIADO` y dos métodos, `approved()` (aprobado o enviado) y `current()` (ocupa el período), que reemplazan las comparaciones sueltas en `ReportService.weeks`, `MonthlyEligibility`, `ReportPdfService`, `ApprovedWeeklyReports` y `AgentRunner`. `ReportTransitions` con `APROBADO → ENVIADO` y mensajes de enviar; `ReportVisibility` del médico en `ENVIADO`; `ReportService.send` y `POST /api/reports/{id}/send` con `REPORTS_SEND`; `Summary` (y por tanto `Detail`) con `sentBy` y `sentAt`. Semilla: `demo-reports.json` con las tres primeras semanas de P-014 `ENVIADO` (`sentBy` Marcela, `sentAt` una hora después de `reviewedAt`) y `DemoDataSeeder` que inserta así y, si la base ya los trae `APROBADO`, **los avanza a `ENVIADO` y nada más**.
  > **Comprobación:** `mvn clean verify`: **455 pruebas, 0 fallos, 0 errores, 14 omitidas** (las 14 en vivo; la línea base eran 436: **+19 nuevas**), 4 min 20 s, contra `ppa-test-db`. **Base ya sembrada** (`ppa-test-db`, con los cuatro `APROBADO` de antes): quedó con tres `ENVIADO` y uno `APROBADO`. **Base nueva** (una `ppa_fresh_check` vacía que creé y borré): `V1` a `V10` aplicadas desde cero y la misma distribución; `SeededReportsTest`, `DomainSchemaTest` y `ReportsApiTest` sin errores ahí. Las 19 pruebas nuevas cubren: solo enfermería envía (médico 403, sin usuario 401), solo un aprobado se envía (pendiente, incompleto y rechazado 409; id inexistente 404; el mensaje del pendiente dice «Apruébelo antes de enviarlo»), un enviado es final y no cambia al repetirse, el médico no ve un aprobado en lista, detalle, traza ni PDF (404) y sí un enviado, un mes con semanas enviadas se genera (`AgentRunnerTest`: tres `ENVIADO` y uno `APROBADO`), el índice impide un segundo informe de la semana de un enviado (por la API y por la base), el PDF del enviado, las restricciones de la base una por una, `REPORTS_SEND` solo de enfermería y la semilla reconciliando una base ya sembrada sin retroceder.
  > **La lista del paso 1 resultó exacta en lo que cambia:** la primera corrida (antes de tocar ninguna prueba) dio 19 fallos y **todos** estaban en las clases que el paso 1 listó (`AgentRunnerTest`, `DraftValidatorTest`, `MonthlyReportsApiTest`, `PatientsApiTest`, `ReportPdfApiTest`, `ReportsApiTest`, `ReportTransitionsTest`, `SeededReportsTest`); no cambió ninguna guarda de regresión (`MonthlyEligibilityTest`, `ReportConcurrencyTest`, `RunsApiTest`, `MonthlyRunsApiTest`, las de enfermería). `RunSaturationTest` no falló, pero copiaba el índice y lo actualicé igual; `PermissionMatrixTest` solo se amplió. Una prueba `PatientsApiTest:57` (enfermería ve el último de P-014, `APROBADO`) pasa sin tocarse.
  > **Hallazgos:** (1) un `INSERT` que falla aborta la transacción de la prueba: mis primeras pruebas de restricciones de `DomainSchemaTest` hacían dos por prueba y fallaron (ya lo advertía el archivo); ahora es uno por prueba. (2) La prueba nueva de la lista del médico empataba el orden de un rechazado y un incompleto de la misma semana (`created_at` igual dentro de la transacción); ahora fija el orden de los tres primeros y deja libre el de esos dos. (3) En una de las corridas desapareció `seed/demo-data.json` de `target/classes` a media ejecución y cayeron 13 pruebas de arranque de Spring: no fue el código ni las pruebas (la repetición dio verde con el mismo código); lo más probable es que algo externo, como el IDE, tocara `target/` mientras corría Maven. Conviene no abrir un build desde el IDE mientras corre `mvn verify`.
  > **Decisiones que nadie pidió:** (1) `approved()` y `current()` en el enum en vez de listar estados en cada sitio: así no se puede olvidar uno al agregar otro estado; (2) un informe **mensual** también se envía, con la misma ruta, porque el plan habla de «un informe aprobado» y el mensual pasa por la misma revisión; (3) la ruta no lleva cuerpo; (4) `sentAt` de la semilla es una hora después de `reviewedAt` y `sentBy` la misma revisora (datos sintéticos inventados); (5) el fixture `ReportFixtures` ahora arma informes `ENVIADO`.
  > **No verificado:** el frontend (paso 4: hoy la pantalla de enfermería no muestra el botón ni el estado, y `ReportStatus` no conoce `ENVIADO`); `./run.sh` con Docker y un clon limpio (paso 6); `eval/` con el estado nuevo (paso 5). El efecto sobre un volumen de Docker real que ya tenga datos se probó con la base de pruebas, que es una base ya sembrada, pero no con ese volumen.
- [ ] 4. **Construir la pantalla de enfermería:** «Enviar al médico» con su proceso y su aviso, el estado y el chip «Enviado», las tres notas y «Aprobado por» como en el mockup.
  - **Se mira:** `ng test` verde con las pruebas nuevas; las de enfermería cambian solo donde cambia el texto.
- [ ] 5. **Ajustar** `eval/` a los estados nuevos y volver a correr la suite sin llave; con llave, solo el mensual, que lee los aprobados.
  - **Se mira:** los casos dorados dan el mismo resultado que antes del cambio.
- [ ] 6. **Verificar en vivo** con `./run.sh`: enfermería aprueba y envía, el médico (por la API, hasta la Pieza 4) recibe solo los enviados, y el PDF de un enviado; el `README.md` con el paso de enviar.
  - **Se mira:** los criterios 1 a 4 con evidencia.
- [ ] 7. **Comprobar** el criterio de aceptación, punto por punto, y dejar la evidencia.

### Criterio de aceptación
1. En un informe aprobado, enfermería ve «Descargar PDF» y «Enviar al médico»; al enviarlo pasa a «Enviado», con la nota «Enviado al médico el …» y solo «Descargar PDF», como el mockup.
2. Solo enfermería envía, y solo un informe aprobado; cualquier otro intento se rechaza en el servidor con un mensaje claro.
3. El médico no ve un informe aprobado sin enviar en la lista, el detalle, el PDF ni el último informe de un paciente, en pantalla ni por la API; un enviado sí.
4. Un enviado cuenta como aprobado para el mensual, el PDF y el informe vigente de su semana.
5. Las notas y los botones de enfermería que hablan del médico dicen lo que dice el mockup, salvo las frases de notificación, que llegan con la Pieza 5.
6. Las pruebas y los casos de `eval/` siguen en verde, más las nuevas, y `./run.sh` en un clon limpio sigue levantando todo.

### El paso más riesgoso
El **paso 3**: el estado `APROBADO` decide en una decena de lugares del backend, y olvidar uno deja al médico sin ver un enviado o permite generar dos informes de la misma semana. La lista del paso 1 se recorre entera y cada lugar lleva su prueba; si una prueba anterior cambia de resultado sin estar en esa lista, se para y se revisa.

### Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 3 | El índice de «un informe vigente por semana» no incluye `ENVIADO` | Se puede generar un segundo informe de una semana ya enviada |
| 3 | El mensual solo acepta `APROBADO` | P-014 deja de poder generar su mensual con la semilla nueva |
| 3 | Las restricciones de `reviewed_by` o de `approved_version_no` no admiten `ENVIADO` | La migración o el envío fallan en la base |
| 4 | La pantalla decide los botones por estado y no por permiso | El médico vería «Enviar al médico» si llegara al informe |
| 5 | Un caso dorado del mensual fija el estado de los informes sembrados | El caso falla sin que haya cambiado el contenido |

### Qué se entrega al final
- `V10`, la transición, la ruta y la visibilidad en el backend, la semilla con enviados, y el botón, el estado y los textos del mockup en la pantalla de enfermería.
- D29, RN-01, D6, D11, RF-44, RF-28 y CU-05 al día, los dos diccionarios, `docs/decisiones.md`, `docs/gobernanza.md`, el `README.md` y `AGENTS.md`.
- Cambios sin commit: el equipo los verifica y los sube.

## Pieza 4 — Pantallas del médico: pacientes, informes recibidos y registro (RF-28, RF-29, RF-30)

**Origen:** RF-28 es del núcleo del Momento 2 (M2-N) y la puerta de la SPEC §6 pide trabajar con un usuario de enfermería y uno de médico; la pantalla del médico (E12c) quedó fuera del Momento 2 por decisión del equipo del 28-oct (`docs/PLAN.md`). RF-29 y RF-30 son M2-S. El equipo pidió el 2026-11-05 construir el módulo del médico, fiel al mockup vA. **[Equipo]** Depende de la Pieza 3, que decide qué informes ve el médico.

### Encargo
Que un usuario de demostración con rol de médico, elegido en «Ver como», trabaje con las pantallas del mockup vA: «Mis pacientes», la ficha del paciente con el último informe recibido y los registros recientes, «Informes → Recibidos» y el informe enviado con su evidencia y su PDF; y que desde la ficha registre una entrada diaria con la misma pantalla de enfermería, que queda con su autoría. Lo que ve el médico lo sigue decidiendo el servidor: solo informes `ENVIADO` (Pieza 3).

### Decisiones del encargo
| Duda | Decisión | Por qué |
|---|---|---|
| Qué informes ve el médico (D6) | Solo los `ENVIADO` **[Equipo, 2026-11-07]** (Pieza 3) | Es lo que hace el mockup |
| Cómo registra el médico (RF-30, D12) | Un botón «Nuevo registro» en la ficha del paciente, con el estilo y el lugar del de la ficha de enfermería, que abre la misma pantalla de registro en `#/med/registro/nuevo/:id` **[Equipo, 2026-11-05]** | El mockup vA no le da registro al médico; el equipo eligió el cambio más pequeño, con el diseño del mockup |
| Quiénes son «Mis pacientes» | Todos los pacientes **[Propuesta]** | No hay asignación de pacientes a médicos en el diccionario ni en los requisitos |
| Qué evidencia ve el médico (RF-29) | La hoja del informe enviado con sus citas a las entradas originales y la traza a un clic, sin versiones, edición, aprobación, rechazo ni envío **[Equipo]** (ya decidido en E12a, `docs/decisiones.md`: «el médico recibe solo la aprobada y la traza del informe aprobado») | RF-29 pide llegar a las entradas citadas; «fuente y criterio visibles» también vale para el médico |
| «Informes recibidos» | Las columnas del mockup (Paciente, Período, Recibido y Estado), con «Recibido» como la fecha de envío, ordenadas por período del más reciente al más antiguo y con búsqueda; vacío: «Aún no ha recibido informes. Cuando enfermería envíe un informe aparecerá aquí.» **[Propuesta]** | Es lo del mockup (`vInfLista`) y lo que pide RF-28. La invitación a armar uno propio llega con la Pieza 6 |
| Quién aparece en «Ver como» | Las dos enfermeras y los dos médicos sembrados; al cambiar de usuario se vuelve al inicio de su rol (`#/enf/pacientes` o `#/med/pacientes`) **[Propuesta]** | Hoy solo ofrece enfermeras «hasta que existan las pantallas del médico» (`session.service.ts`); el mockup también vuelve al inicio del rol |
| Una ruta de otro rol | Si el usuario elegido no es del rol de la ruta (un médico en `#/enf/…`), se lleva al inicio de su rol **[Propuesta]** | En el mockup el rol sale de la URL; aquí sale del usuario elegido, que es el que usa el servidor. La pantalla no decide permisos: si se fuerza, el servidor responde 403 o 404 |
| «No encontrado» | «Volver al inicio» lleva al inicio del rol, como el mockup **[Propuesta]** | Hoy lleva siempre a enfermería |
| «Armar informe» en el menú | No aparece hasta la Pieza 6 **[Propuesta]** | Un enlace a una pantalla que no existe es un callejón sin salida en la demo |

### Fuera del alcance
Notificaciones y el punto de «registros nuevos» (Pieza 5) · «Armar informe» (Pieza 6) · historial de registros del médico (el mockup no lo tiene) · que el médico edite, apruebe, rechace, envíe o vea versiones · asignar pacientes a médicos · crear o editar pacientes · inicio de sesión (RF-31) y administrador (RF-33) · auditoría (RF-35).

### Supuestos
- Con la Pieza 3 hecha, el backend le da al médico lo que necesita: `PATIENTS_READ`, `ENTRIES_READ`, `ENTRIES_CREATE` y `REPORTS_READ` (`V4`), la lista y el detalle filtrados por `ReportVisibility` a `ENVIADO`, la traza y el PDF. **Se confirma en el paso 1 con llamadas reales como médico.**
- Las pantallas de enfermería se pueden reutilizar en modo médico cambiando el prefijo de las rutas y ocultando lo que el rol no puede hacer, sin copiar componentes. **Se confirma en el paso 1:** hoy hay unas 40 líneas con `/enf` fijo en el frontend.
- Las piezas 3 a 6 se construyen sobre la sesión de demostración (D26). Cuando llegue el inicio de sesión (RF-31), cambia de dónde sale el usuario, no las reglas.

### Pasos
- [ ] 1. **Mirar** el punto de partida: backend y frontend en verde como línea base; los enlaces y textos con `/enf` fijo; y, con `curl` como médico contra la semilla, la lista de pacientes, la ficha, los informes (por período, del más reciente al más antiguo), el detalle, la traza y el PDF de un enviado, y el 404 de un aprobado sin enviar.
  - **Produce:** la línea base, los enganches con archivo y línea, y lo que el backend no da, si falta algo.
  - **Se mira:** `mvn -B verify` y `ng test` en verde con el número anotado; las respuestas como médico cumplen RF-28 y RF-29.
- [ ] 2. **Registrar** antes de programar: D12 con la decisión de interfaz en la SPEC §7 y en los requisitos (§8), `docs/decisiones.md` (sección «Módulo del médico») y `AGENTS.md`.
  - **Se mira:** sin IDs inexistentes, tablas cuadradas y conteos por hito sin cambio (esta pieza no trae RF nuevo).
- [ ] 3. **Construir el armazón por rol:** «Ver como» con los médicos, el menú del médico del mockup («Mis pacientes» e «Informes → Recibidos»), las rutas `med/…`, la vuelta al inicio del rol, «No encontrado» según el rol y los enlaces internos según el rol en vez de `/enf` fijo.
  - **Se mira:** `ng test` verde con pruebas nuevas del menú, las rutas y la guarda; las pruebas de enfermería siguen verdes sin cambiarlas.
- [ ] 4. **Construir** «Mis pacientes», la ficha del médico (último informe recibido, registros recientes y el botón «Nuevo registro») y el registro en `#/med/registro/nuevo/:id`, con la estructuración, la vista previa y el dictado de siempre.
  - **Se mira:** pruebas verdes que cubren que el médico no ve «Nuevo paciente» ni «Generar informe», que el último informe es el enviado y que la entrada creada queda con el médico como autor.
- [ ] 5. **Construir** «Informes recibidos» (las columnas, el orden y el vacío del mockup) y el informe del médico: la hoja con sus citas, la traza a un clic y «Descargar PDF» (primario, como el mockup), sin editar, aprobar, rechazar ni enviar; un informe que no puede ver muestra «No encontrado».
  - **Se mira:** pruebas verdes; desde una cita el médico llega a la entrada (RF-29).
- [ ] 6. **Verificar en vivo** con `./run.sh` y la semilla: el recorrido completo como médico, un aprobado sin enviar y un pendiente que no aparecen en ninguna pantalla ni por la API, una entrada del médico con su autoría, el PDF y el recorrido de enfermería sin cambios; el recorrido del médico en el `README.md`.
  - **Se mira:** los criterios 1 a 5 con evidencia (capturas o salidas de `curl`).
- [ ] 7. **Comprobar** el criterio de aceptación, punto por punto, y dejar la evidencia.

### Criterio de aceptación
1. Con un médico elegido en «Ver como», el menú, los títulos y las pantallas son los del mockup vA («Mis pacientes», «Informes → Recibidos»), y una ruta de enfermería lo lleva al inicio de su rol.
2. Un informe `PENDIENTE_REVISION`, `INCOMPLETO`, `RECHAZADO` o `APROBADO` sin enviar no le aparece al médico en la lista de pacientes, la ficha, «Recibidos», el detalle ni el PDF, en pantalla ni por la API.
3. Desde un informe enviado, semanal o mensual, el médico llega a las entradas citadas y descarga el PDF.
4. Desde la ficha, el médico crea una entrada, confirma sus eventos y la entrada queda con su autoría (RF-30).
5. El recorrido de enfermería no cambia: sus pruebas siguen verdes y su demo funciona igual.
6. Las pruebas existentes siguen en verde, más las nuevas, y `./run.sh` en un clon limpio sigue levantando todo.

### El paso más riesgoso
El **paso 3**. Cambiar los enlaces fijos puede romper pantallas de enfermería que hoy funcionan. Por eso sus pruebas se corren antes y después sin tocarlas; si alguna cambia de resultado, se para y se revisa.

### Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 3 | Un enlace queda con `/enf` en modo médico | El médico cae en una pantalla de enfermería, o la guarda lo devuelve al inicio sin razón |
| 3 | La guarda se evalúa antes de que cargue la sesión | Al recargar `#/med/…` se salta a `#/enf/pacientes` |
| 4 | La pantalla de registro supone enfermería (textos, enlaces, «Ver ficha del paciente») | Mensajes de enfermería o enlaces a `#/enf/…` después de guardar como médico |
| 5 | La vista del informe muestra las acciones por estado y no por permiso | Botones de editar, aprobar o enviar visibles al médico (el servidor los rechaza, pero la pantalla confunde) |
| 5 | «Recibidos» no sigue el orden del mockup (por período, del más reciente al más antiguo) | Un informe de una semana vieja enviado hoy sale arriba |

### Qué se entrega al final
- El menú, las rutas y las pantallas del médico en el frontend, reutilizando las de enfermería; sin cambios en el backend salvo lo que el paso 1 encuentre que falta.
- D12 al día en la SPEC y los requisitos, la sección en `docs/decisiones.md`, el recorrido del médico en el `README.md` y `AGENTS.md` al día (E12c deja de estar fuera).
- Cambios sin commit: el equipo los verifica y los sube.

## Pieza 5 — Notificaciones al médico (requisito nuevo)

**Origen:** el mockup vA trae para el médico una campana con «Nuevo registro…» e «Informe … recibido», y un punto de «registros nuevos» junto al paciente en «Mis pacientes». No estaba en los requisitos; el equipo decidió el 2026-11-05 incluirla como requisito nuevo. **[Equipo]** Depende de la Pieza 4.

### Encargo
Que cada médico vea en el encabezado una campana con sus notificaciones (un registro nuevo de un paciente y un informe recibido), con un punto cuando hay alguna sin leer y otro junto al paciente en «Mis pacientes»; que se marquen leídas al abrir la ficha o la notificación; y que los avisos de enfermería del mockup al guardar un registro («Registro guardado. Se notificó al médico.») y al enviar un informe («Informe enviado. Se notificó al médico») sean ciertos.

### Decisiones del encargo
| Duda | Decisión | Por qué |
|---|---|---|
| Requisito | Incluirla **[Equipo, 2026-11-05]**; como D30 en la SPEC y RF-45 en los requisitos, hito M3 **[Propuesta]** (los IDs se confirman libres en el paso 2) | No se agregan funcionalidades sin requisito (`CLAUDE.md`) |
| Qué notifica | Una entrada guardada de un paciente («Nuevo registro del domingo 25 de octubre») y un informe enviado, semanal o mensual («Informe semanal recibido» o «Informe mensual recibido») **[Propuesta]** | Son los dos tipos del mockup, y en el mockup el aviso del informe sale al enviarlo. Aprobar sin enviar no notifica (RN-01) |
| A quién | A todos los médicos activos, menos al autor de la entrada **[Propuesta]** | «Mis pacientes» son todos (Pieza 4). Avisarle al médico de su propia nota es ruido |
| Dónde vive | Una tabla `notifications` (destinatario, paciente, tipo, entrada o informe, creada, leída), escrita en la misma transacción que guarda la entrada o envía el informe **[Propuesta]** | El mockup lleva leídas y sin leer por notificación; en la misma transacción no se pierde ni sobra ninguna |
| Qué texto guarda | Ninguno: la frase se arma al leerla con el tipo y la fecha **[Propuesta]** | No copia texto clínico a otra tabla (RNF-02) |
| Cuándo se marca leída | Al abrir la ficha del paciente (las de registro de ese paciente, como el mockup), al pulsar la notificación y al abrir el informe que avisa **[Propuesta]** | El mockup hace las dos primeras; la tercera evita que quede sin leer un informe ya leído |
| Cómo se actualiza | Al navegar entre pantallas, como el mockup; sin sondeo ni SSE **[Propuesta]** | Basta para la demo y no suma conexiones abiertas |
| Permiso | `NOTIFICATIONS_READ`, solo para `MEDICO` **[Propuesta]** | El mockup no tiene campana para enfermería |
| Semilla | Como el mockup: dos registros recientes sin leer (P-014 y P-021) y el último informe enviado de P-014 ya leído, para cada médico **[Propuesta]** | El médico abre la demo con algo que mostrar; P-021 no tiene informe enviado (D11) |
| Tríada | Es un aviso dentro de la aplicación: no sale correo ni mensaje, y el agente no tiene herramienta para notificar **[Propuesta]** | La pata de salida se mantiene cerrada |

### Fuera del alcance
Correo, mensajes o notificaciones del navegador · notificaciones para enfermería o administración · preferencias por usuario · borrar notificaciones · tiempo real (sondeo, SSE o WebSocket) · notificar la consulta del médico (Pieza 6).

### Supuestos
- Guardar una entrada y enviar un informe ocurren cada uno en una transacción a la que se puede sumar la notificación. **Se confirma en el paso 1.**
- Los estilos del mockup para la campana, su panel y los puntos (`.campana`, `.campana-panel`, `.notif`, `.punto-notif`, `.punto-lista`) se pueden portar sin tocar el mockup. **Se confirma en el paso 1.**

### Pasos
- [ ] 1. **Mirar** el punto de partida: línea base de pruebas, las transacciones de guardar una entrada y de enviar un informe, el encabezado donde va la campana, los estilos del mockup y los avisos actuales de enfermería al guardar y al enviar.
  - **Produce:** la línea base y los enganches con archivo y línea.
  - **Se mira:** `mvn -B verify` y `ng test` en verde con el número anotado.
- [ ] 2. **Registrar** antes de programar: D30 en la SPEC §7, RF-45 y los conteos en los requisitos, la tabla y el permiso en el diccionario, `docs/decisiones.md`, `docs/gobernanza.md` y `AGENTS.md`.
  - **Se mira:** sin IDs inexistentes, tablas cuadradas y conteos por hito sumados.
- [ ] 3. **Construir el backend:** la migración (`V11`: tabla y permiso), la creación al guardar una entrada y al enviar un informe, la lista del usuario, las rutas para marcar leídas y la semilla.
  - **Se mira:** pruebas verdes que cubren que el autor no recibe la suya, que enfermería no tiene notificaciones (403), que enviar notifica y aprobar no, que la estructuración y el dictado no notifican, que un médico no lee ni marca las de otro (404) y que la semilla es idempotente.
- [ ] 4. **Construir la pantalla:** la campana con su panel en el encabezado, solo para el médico; el punto en «Mis pacientes»; marcar leídas al abrir la ficha, la notificación o el informe; y los avisos de enfermería al guardar y al enviar, con las frases del mockup.
  - **Se mira:** `ng test` verde con las pruebas nuevas; enfermería no ve la campana.
- [ ] 5. **Verificar en vivo** con `./run.sh`: enfermería guarda una entrada y envía un informe, y un médico ve las dos notificaciones y el punto, y cómo desaparecen al leerlas.
  - **Se mira:** los criterios 1 a 5 con evidencia.
- [ ] 6. **Comprobar** el criterio de aceptación, punto por punto, y dejar la evidencia.

### Criterio de aceptación
1. Al guardar una entrada, cada médico activo menos el autor recibe «Nuevo registro del …» de ese paciente, y el paciente muestra su punto en «Mis pacientes».
2. Al enviar un informe, cada médico recibe «Informe semanal recibido» (o mensual), que lleva al informe; aprobarlo sin enviarlo no genera ninguna.
3. Abrir la ficha o la notificación la marca leída, y los puntos de la campana y de la lista se apagan cuando no queda ninguna sin leer.
4. Enfermería no ve la campana ni puede leer notificaciones por la API, y un médico no ve las de otro.
5. Los avisos de enfermería al guardar un registro y al enviar un informe dicen «Se notificó al médico», como el mockup.
6. Las pruebas existentes siguen en verde, más las nuevas, y `./run.sh` en un clon limpio sigue levantando todo.

### El paso más riesgoso
El **paso 3**: sumar la notificación a la transacción de guardar una entrada toca el flujo central de enfermería, y si falla la notificación falla el guardado. Se prueba que una entrada válida siempre se guarda con sus notificaciones y que las pruebas de registro siguen verdes; si no, se para y se revisa antes de seguir.

### Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 3 | La notificación se crea fuera de la transacción | Una entrada que no se guardó deja notificación, o una guardada queda sin ella |
| 3 | La estructuración o el dictado crean notificaciones | Notificaciones sin entrada guardada |
| 3 | La semilla duplica notificaciones al reiniciar | El contador crece con cada arranque |
| 4 | La campana no se refresca al volver de la ficha | El punto sigue encendido con todo leído |

### Qué se entrega al final
- `V11` con la tabla `notifications` y el permiso, el servicio y las rutas en el backend, la campana, el punto y los avisos en el frontend, y la semilla.
- D30 y RF-45 en la SPEC y los requisitos, el diccionario, `docs/decisiones.md`, `docs/gobernanza.md` y `AGENTS.md` al día.
- Cambios sin commit: el equipo los verifica y los sube.

## Pieza 6 — Consulta del médico: «Armar informe» (requisito nuevo)

**Origen:** el mockup vA trae para el médico «Informes → Armar informe»: elige paciente y período, y la IA arma un «Informe de consulta» solo para él, que «no se guarda en el historial ni se envía a nadie». No estaba en los requisitos y, leído rápido, choca con RN-01 (al médico solo llega lo que enfermería aprobó y envió) y con que solo enfermería pide ejecuciones (`docs/decisiones.md`, E11). El equipo decidió el 2026-11-05 incluirlo «de forma coherente, pero fiel a los mockups»: el médico recibe informes aprobados y escribe notas diarias. **[Equipo]** Depende de la Pieza 4.

### Encargo
Que un médico arme desde «Armar informe», con el formulario del mockup (paciente; una semana, un mes o un rango de fechas; y comparar o no con otro período), una consulta redactada por el agente con las mismas reglas del informe: cifras calculadas por el código, cada afirmación con su cita, sin diagnóstico, con los cuatro límites y en segundo plano con progreso real. La consulta se muestra como «Informe de consulta» con la marca «Consulta del médico», no entra al historial ni a ninguna lista, solo la ve quien la pidió y queda con su traza.

### Cómo queda coherente con RN-01 **[Propuesta]**
RN-01 dice que un *informe* solo llega al médico cuando enfermería lo aprobó y lo envió (Pieza 3). La consulta no es un informe del historial: no tiene estado, no la revisa enfermería, no aparece en «Recibidos» ni en la ficha, no notifica y no la ve nadie más que el médico que la pidió. Solo resume registros que el médico ya puede leer (la SPEC §4 le da ver y crear registros diarios): no le llega ningún dato nuevo, solo redactado. Y lo que ve lleva siempre la marca que pide la regla del producto para lo que el médico vea sin aprobación (`AGENTS.md`): «Consulta del médico» en la cabecera, y en el pie «Generado con IA a partir de los registros del período. No pasó por la revisión de enfermería ni forma parte del historial oficial. No contiene diagnóstico ni recomendación de tratamiento.» Se escribe como regla nueva (RN-12) junto a RN-01.

### Decisiones del encargo
| Duda | Decisión | Por qué |
|---|---|---|
| De qué se arma | De las entradas del período con sus eventos confirmados, como el informe semanal **[Equipo, 2026-11-07]** (el equipo pidió la opción más recomendable, alineada con la SPEC, los requisitos y el mockup) | Solo resume lo que el médico ya puede leer: la matriz de la SPEC §4 le da ver los registros y la ficha del mockup ya le muestra los recientes, así que no le llega ningún dato que hoy no vea. Es la única fuente que permite el formulario del mockup (un mes calendario o un rango de fechas no coinciden con semanas aprobadas) y que incluye los días más recientes, que todavía no tienen informe aprobado. Cita las entradas originales (RN-05) con cifras del código (RN-04). **Descartada:** armarla solo de los informes semanales aprobados, como el mensual; es la más cercana a la letra del pie del mockup, pero recorta el formulario a semanas completas con informe aprobado y deja fuera la última semana |
| Requisito | Incluirlo **[Equipo, 2026-11-05]**; como D31 en la SPEC, RF-46 (hito M3), RN-12 y CU-11 en los requisitos **[Propuesta]** (los IDs se confirman libres en el paso 2) | No se agregan funcionalidades sin requisito (`CLAUDE.md`) |
| Los textos de la pantalla | Los del mockup, con dos frases corregidas para que sean ciertas: el pie (arriba) y la introducción del formulario, que queda «Arme su propio informe de un paciente para el período que elija. Se hace con los registros del período y es solo para su consulta: no pasa por la revisión de enfermería ni se guarda en el historial.» **[Propuesta]** | El mockup dice «registros que enfermería ya aprobó», y las entradas no las aprueba enfermería: sus eventos los confirma quien registra, también el médico (RN-11) |
| Períodos | Los del mockup: una semana (lunes a domingo), un mes calendario hasta hoy o un rango de fechas; y comparar con nada, con el período anterior de igual duración o con otro que no se cruce **[Equipo]** (fiel al mockup) | |
| Tope del rango | El mockup pone un año; el tope real sale del presupuesto de 30 000 tokens y de la cuota por minuto de Groq, **medidos en el paso 1**. Si el medido es menor, el mensaje lo dice con el estilo del mockup **[Propuesta]** | Un rango largo puede no caber en el presupuesto del agente |
| Dónde se guarda | Una tabla aparte (`doctor_consultations`: médico, paciente, período, comparación, contenido validado y ejecución) que solo lee su autor; nunca en `reports` **[Propuesta]** | «No se guarda en el historial» y, a la vez, ninguna acción sin traza (SPEC §4). Separada, ninguna consulta de informes la puede devolver por error |
| La ejecución | Tipo nuevo `CONSULTA` en `agent_runs`, con la cola, el SSE, la traza y Langfuse de siempre **[Propuesta]** | Es una llamada al modelo como las demás (RF-22, RF-43). D24: nada síncrono bloqueante |
| Permiso | `CONSULTATIONS_REQUEST`, solo para `MEDICO`; las rutas de ejecuciones aceptan al médico solo para sus consultas **[Propuesta]** | Hoy solo enfermería pide ejecuciones (`REPORTS_REQUEST`) |
| Herramientas | Las de lectura con período en vez de semana, el historial de un síntoma y una sola escritura que guarda la consulta; cada ruta del agente ve solo las suyas, sin pasar de 7 **[Propuesta]** | Pocas, atómicas y con mínimo privilegio (`AGENTS.md`); la herramienta del mensual ya existe solo para su ruta |
| Cifras | Las calcula el código para cualquier período y para su comparación **[Propuesta]** | RN-04 |
| PDF | «Descargar PDF», como en el mockup, con la marca «Consulta del médico» en la cabecera y en el pie **[Propuesta]** | Fiel al mockup, y la marca viaja con el archivo. Alternativa: sin PDF, porque RF-37 solo habla de informes aprobados |
| Quién la ve | Solo el médico que la pidió, por su enlace; a los demás, 404 **[Propuesta]** | Como en RN-01: lo que no se puede ver no existe |
| Lista de consultas | No hay, como en el mockup **[Propuesta]** | «No se guarda en el historial» |
| Solicitud adicional | No hay campo de texto libre, como en el mockup **[Propuesta]** | El formulario del mockup no lo tiene |

### Fuera del alcance
Que la consulta pase a enfermería o se convierta en informe · que aparezca en «Recibidos», la ficha o las notificaciones · una lista de consultas · comparar más de dos períodos · un campo de texto libre · rangos por encima del tope medido · cambiar el informe semanal o el mensual de enfermería.

### Supuestos
- El agente, los validadores y las métricas se pueden generalizar de una semana a un período sin cambiar lo que hacen en el semanal y el mensual: sus pruebas y sus casos de `eval/` lo vigilan. **Se confirma en el paso 1.**
- La hoja del mockup para la consulta (resumen, cifras, patrón a destacar y fuentes) cabe en el esquema de salida del agente. **Se confirma en el paso 1.**

### Pasos
- [ ] 1. **Mirar** el punto de partida: línea base de pruebas y de `eval/`; cómo calculan hoy por semana `MetricsCalculator` y las herramientas; los tokens que gastan las entradas de una semana, de un mes y del rango más largo de la semilla, para fijar el tope del rango; y la plantilla del PDF.
  - **Produce:** la línea base, los enganches con archivo y línea, y el tope del rango medido.
  - **Se mira:** `mvn -B verify`, `ng test` y `python3 eval/run_eval.py --baseline-only` en verde; el tope sale de una medición, no de una estimación.
- [ ] 2. **Registrar** antes de programar: D31 y RN-12 en la SPEC y los requisitos, RF-46 (hito M3) y CU-11, los casos borde (período sin registros, comparación que se cruza, rango sobre el tope), el diccionario (tabla, tipo `CONSULTA` y permiso), `docs/decisiones.md` («por qué la consulta no pasa por enfermería y cómo se marca», que el profesor puede preguntar), `docs/gobernanza.md` y `AGENTS.md`.
  - **Se mira:** sin IDs inexistentes, tablas cuadradas y conteos por hito sumados.
- [ ] 3. **Construir** las cifras de cualquier período y de su comparación, las herramientas de lectura por período y la escritura que guarda la consulta (`V12`: tabla, tipo y permiso).
  - **Se mira:** pruebas verdes; las cifras de una semana pedida como período son iguales a las del semanal; las pruebas del semanal y del mensual siguen verdes sin cambiarlas.
- [ ] 4. **Construir la ruta del agente:** el prompt de la consulta, sus validadores, la ejecución `CONSULTA` con los cuatro límites, las rutas para pedirla, leerla y seguir su progreso, y el PDF con la marca.
  - **Se mira:** pruebas verdes que cubren que la consulta no aparece en ninguna lista de informes ni para enfermería, que otro médico o enfermería reciben 404, que se cumplen los límites y que una afirmación sin cita o con una cifra que no sale del código se rechaza.
- [ ] 5. **Construir la pantalla:** «Armar informe» en el menú del médico, el formulario del mockup con sus validaciones, el progreso real y el «Informe de consulta» con su marca, su nota y «Descargar PDF».
  - **Se mira:** `ng test` verde con las pruebas nuevas.
- [ ] 6. **Medir:** casos dorados de consulta en `eval/` (una semana comparada con la anterior, un rango sin comparación, un período con días sin registro y la nota de P-040 que trae una orden) contra la solución convencional, y las cuatro cifras con fecha y método en `docs/decisiones/semana14.md`.
  - **Se mira:** los casos verificados a mano contra los datos; tasa de éxito, costo por consulta, mediana de latencia y recuperación anotados.
- [ ] 7. **Comprobar** en vivo con `./run.sh` el criterio de aceptación, punto por punto, y dejar la evidencia.

### Criterio de aceptación
1. Un médico arma una consulta de una semana, de un mes y de un rango, con y sin comparación, y la ve como «Informe de consulta» con la marca «Consulta del médico», en pantalla y en el PDF.
2. La consulta corre en segundo plano con progreso real, cumple los cuatro límites, sus cifras son las del código y cada afirmación cita entradas del período.
3. La consulta no aparece en «Recibidos», la ficha, las notificaciones ni ninguna lista de enfermería; otro médico o enfermería reciben 404 al abrirla.
4. Un período sin registros o un rango sobre el tope se rechaza con un mensaje claro, sin crear ejecución.
5. Los casos de consulta de `eval/` pasan y las cuatro cifras quedan con fecha y método; los del semanal y el mensual no empeoran.
6. Las pruebas existentes siguen en verde, más las nuevas, y `./run.sh` en un clon limpio sigue levantando todo.

### El paso más riesgoso
El **paso 1**, por el tope del rango. Si un mes de entradas no cabe en el presupuesto de tokens o en la cuota por minuto de Groq, el formulario del mockup no se puede cumplir entero. Si pasa, se propone el tope medido, o que el agente lea el período por semanas con varias llamadas dentro del límite de iteraciones, y se pregunta antes de seguir.

### Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 3 | Generalizar las cifras cambia las del semanal | Fallan las pruebas o los casos dorados del semanal |
| 4 | La consulta se cuela en una consulta de informes | Aparece en «Recibidos», en la ficha o en las listas de enfermería |
| 4 | Una consulta larga pasa de la cuota por minuto de Groq | 429 y respaldo en casi todas las consultas largas |
| 4 | El validador de cifras supone una semana | Rechaza números válidos de un mes o de un rango |
| 6 | La suite de `eval/` gasta la cuota diaria | La medición queda a medias; se corre en una base desechable y en tandas |

### Qué se entrega al final
- `V12`, las cifras por período, la ruta del agente con su prompt, sus herramientas y sus validadores, las rutas, el PDF con la marca y la pantalla «Armar informe» con el «Informe de consulta».
- Los casos de consulta en `eval/` y las cuatro cifras en `docs/decisiones/semana14.md`.
- D31, RN-12, RF-46 y CU-11 en la SPEC y los requisitos, el diccionario, `docs/decisiones.md`, `docs/gobernanza.md` y `AGENTS.md` al día.
- Cambios sin commit: el equipo los verifica y los sube.
