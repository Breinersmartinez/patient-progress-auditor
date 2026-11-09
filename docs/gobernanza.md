# Ficha de gobernanza — Patient Progress Auditor

> **Estado:** v0.1 para revisión · **Fecha:** 2026-11-03 · **Equipo:** Breiner Martinez Muñoz y Andres Felipe Lopez Vivas · **Entregable:** Momento 3, OBJ-14 **[Curso]**
>
> **Qué describe.** El sistema como está hoy: el módulo de enfermería del Momento 2, **sin autenticación** (usuarios de demostración, D26), más la integración opcional de Langfuse (D28), ya escrita en el código (pasos 4 y 5 de `docs/PLAN_M3.md`) y **todavía sin comprobar contra Langfuse Cloud** (paso 6). Cada afirmación sin etiqueta se verificó en el código o en una prueba, y la ruta va entre paréntesis. Lo que es decisión del equipo lleva **[Equipo]** y lo que es propuesta, **[Propuesta]**. Lo que cambia con el Momento 3 va marcado «M3». **Decidido el 2026-11-07 y construido ese día:** «Enviar al médico» y el estado `ENVIADO` (D29, Pieza 3 de `docs/PLAN_M3.md`, pasos 1 a 6).
>
> **Qué no es.** No es el modelo de amenazas: `docs/seguridad.md` no existe todavía (Momento 3) y esta ficha se debe reescribir sobre él. El curso pide una página; esta versión la supera porque también dice lo que falta, y se compacta al cerrar el Momento 3 **[Propuesta]**.

## 1. ¿Qué decide el sistema solo?

El sistema es apoyo al seguimiento documental: no diagnostica, no receta y no sustituye el criterio profesional. Estas son las acciones que ejecuta sin que una persona las apruebe:

| Acción | Herramienta o componente | Qué hace | Límite |
|---|---|---|---|
| Leer las semanas con registros | `list_patient_weeks` | Lista las semanas con registros del paciente de la ejecución | Solo lectura |
| Leer los registros | `get_week_entries` | Devuelve fecha y texto de los registros de una semana | Solo lectura |
| Leer las métricas | `get_week_metrics` | Devuelve las cifras de una semana, calculadas por el código | Solo lectura |
| Leer un síntoma | `get_symptom_history` | Devuelve el historial de un síntoma entre semanas | Solo lectura |
| Leer los informes aprobados | `get_approved_weekly_reports` | Solo en el mensual: los informes semanales aprobados de las cuatro semanas | Solo lectura |
| **Guardar el borrador** | `save_draft` | **La única escritura del agente**: crea el informe en `PENDIENTE_REVISION`, si pasa el validador de cifras y citas | Nunca aprueba ni envía |
| Cambiar al proveedor de respaldo | `LlmGateway` | Tras reintentos de 1, 2 y 4 s, pasa de Groq a Gemini | Sin acciones de salida |
| Detenerse | `AgentRunner` | Para por límite: 5 llamadas, 30 000 tokens o el segundo tiempo agotado de una herramienta (5 s cada una) | Deja el informe `INCOMPLETO` con el motivo |
| Marcar como interrumpida | `OrphanRunRecovery` | Al reiniciar el backend, las ejecuciones abiertas pasan a `FALLIDA` | — |

**Lo que no decide:** aprobar, rechazar, editar un informe aprobado, enviar algo al médico, borrar, ni guardar un evento de una entrada sin que la persona lo confirme. Tampoco tiene herramientas de salida (internet, correo, mensajes): esa pata de la tríada se mantiene cerrada. Si el texto de una nota trae órdenes, el prompt le dice que son dato y no instrucción; la defensa real es que la herramienta no existe. (`agent/tools/`, `agent/AgentPrompt.java`)

## 2. ¿Qué necesita un humano, y quién es ese humano?

| Qué necesita un humano | Quién | Cómo lo exige el sistema |
|---|---|---|
| **Aprobar o rechazar un informe** | Una persona con el rol **ENFERMERIA** | `@PreAuthorize("hasAuthority('REPORTS_APPROVE')")` en `approve` y `reject` (`report/ReportController.java`). Ese permiso solo lo tiene ENFERMERIA en la matriz sembrada (`V4__seed_domain_rbac.sql`) |
| **Enviar al médico (D29)** | Una persona con el rol **ENFERMERIA** | Decidido y construido el 2026-11-07: el permiso `REPORTS_SEND` (`V10`), solo de ENFERMERIA, y el servidor solo deja enviar un informe `APROBADO` (`ReportController`, `ReportService.send`, `ReportTransitions`); el médico recibe 403. Aprobar guarda el informe; enviarlo es lo que lo hace llegar al médico **[Equipo]** |
| Que el informe sea aprobable | — | `ReportTransitions` solo deja pasar de `PENDIENTE_REVISION` a `APROBADO` o `RECHAZADO`; los demás estados son finales. Con D29 se suma `APROBADO` a `ENVIADO`, y `ENVIADO` es final. El servidor lo decide, no la pantalla |
| Que el médico no vea un borrador | — | `ReportVisibility`: con D29 el médico solo ve `ENVIADO`, y un informe sin enviar «parece no existir» (404 en lista, detalle, traza, PDF y último informe del paciente) |
| **Confirmar los eventos al registrar** | Quien registra (enfermería o médico) | La IA propone síntomas, intensidad y veces; no se guarda nada hasta confirmarlos o editarlos. «Descartar» no guarda (RN-11, D27) |
| Contrastar el informe con los registros | La persona de enfermería que lo revisa | Cada afirmación lleva sus citas a los registros originales y la traza está a un clic (RF-22) |

La supervisión es **exigida por el sistema**, no una costumbre del operador: sin el permiso, la aprobación responde 403, y lo comprueban `ReportsApiTest` (el médico no puede aprobar ni rechazar; aprobar registra quién, cuándo y qué versión), `PermissionMatrixTest` (`onlyNursingCanApproveAndTheAgentPermissionBelongsToNobodyWhoLogsIn`) y `DomainSchemaTest` (`approvedReportNeedsReviewer`, `onlyNursingHasApprovePermission`, `doctorCannotCreateOrApprove`).

**Lo que hoy no se puede afirmar: quién es la persona.** El sistema exige el *rol*, pero no verifica la *identidad*. El usuario viaja en la cabecera `X-Demo-User` y el propio filtro lo dice: «No es autenticación: cualquiera puede mandar un id» (`session/DemoUserFilter.java`). Quien conozca el id de un usuario de enfermería puede aprobar como esa persona. Es la decisión del equipo (D23, D26) y se cierra con RF-31, que es del M3: hasta entonces la ficha no puede decir que el aprobador es quien dice ser **[Equipo]**.

**Hueco conocido:** un informe aprobado, y con D29 uno enviado, no se puede retirar ni anular; solo se emite uno nuevo (`ReportTransitions`). Si hace falta un estado «retirado», es una decisión por tomar, y hoy no está en `docs/REQUISITOS.md`.

## 3. ¿Qué queda registrado, dónde y por cuánto tiempo?

| Qué | Dónde | Contenido | Cuánto tiempo |
|---|---|---|---|
| Entradas, eventos, versiones de informe y citas | PostgreSQL, tablas inmutables | El texto original tal como se escribió, los eventos confirmados, cada versión del informe (origen `MODELO` o `ENFERMERIA`) y sus citas | **Sin plazo definido.** No hay borrado ni tarea programada de retención (RNF-10) |
| Aprobación | `reports` | Quién (`reviewed_by`), cuándo (`reviewed_at`) y qué versión (`approved_version_no`) | Igual |
| Ejecuciones | `agent_runs` | Tipo, estado, motivo de parada, proveedor, modelo, respaldo, tokens, costo a precio de lista, tiempos | Igual |
| **Trazas** | `agent_steps` | Por paso: herramienta, parámetros, resultado (hasta 1 500 caracteres, que incluyen texto de notas), ms y tokens. Se ven en la pantalla del informe (RF-22) | Igual |
| Registro del servidor | Salida estándar del contenedor, nivel INFO | Por llamada: proveedor, modelo, tokens, ms e intento; errores con el id de la ejecución. Ninguna línea escribe a propósito el texto de las notas, **pero** el motivo técnico de un fallo del proveedor puede traer un fragmento de su respuesta | Lo que Docker conserve; no se configuró rotación |
| Langfuse (si se activa; **escrito y probado contra un servidor local, no contra la nube**) | Langfuse Cloud, región de Estados Unidos | Los metadatos de cada llamada (ver 4) | Lo define Langfuse; **no verificado** (hay cifras de terceros: 30 días en el plan gratuito) |
| Notificaciones al médico (M3, D30; **decidido, en construcción**) | `notifications` | A quién, de qué paciente, de qué tipo (entrada o informe) y cuándo; nunca el texto de la nota ni el resumen del informe (la frase se arma al leerla) | **Sin plazo definido**, igual que el resto; no hay borrado ni purga de leídas |

**Lo que no existe:** una política de retención, un registro de auditoría aparte (RF-35 quedó fuera del Momento 2) y rotación de los registros. Los datos son sintéticos; si el sistema pasara a datos reales, el plazo tendría que acordarse con la institución antes **[Propuesta]**.

## 4. ¿Qué datos toca y cuáles salen a un tercero?

**Flujo.** PostgreSQL → herramientas de lectura del agente → mensajes al modelo (Groq; Gemini si Groq falla) → borrador → PostgreSQL. La estructuración al registrar es una llamada corta que solo lleva el texto de la nota que se está escribiendo.

| Tercero | Qué recibe | Qué no recibe | Lo que no sabemos |
|---|---|---|---|
| **Groq** (principal, `openai/gpt-oss-120b`) | El texto de los registros de la semana (fecha y texto), las métricas, los eventos, los informes semanales aprobados (en el mensual), el prompt del sistema y la «indicación» de hasta 300 caracteres si la API la recibe (la interfaz no la expone) | Nombre, alias, cama, edad, ingreso, documento y **el id del paciente**: el agente no lo recibe (las herramientas lo toman del contexto de la ejecución) y los registros se citan con alias como `e1` | Su política de retención: **no verificada** |
| **Groq — Whisper** (dictado por voz, RF-38, D14; **decidido, en construcción**) | El audio de cada dictado (la voz de quien registra, con lo que diga, que puede incluir nombres) | El id y el alias del paciente, otros registros; el audio no se guarda en la aplicación | Según `console.groq.com/docs/your-data` (leído el 2026-11-05), por defecto Groq no retiene lo que recibe, pero **puede guardar entradas y salidas hasta 30 días** para fiabilidad o abuso, salvo que se active *Zero Data Retention* en la consola; la página no dice si entrena con ellos. Datos en GCP en Estados Unidos |
| **Gemini** (respaldo, `gemini-3.5-flash-lite`) | Lo mismo, solo si Groq falla | Lo mismo | La página de precios de Google dice que el contenido enviado en la capa gratuita puede usarse para mejorar sus productos (verificado el 28-oct). Por eso solo se usan datos sintéticos |
| **Langfuse Cloud** (opcional, D28; **escrito y probado contra un servidor local, no contra la nube**) | Solo metadatos: proveedor, modelo, tokens, costo, latencia, categoría del fallo, tipo y resultado de la ejecución y su id | Texto de notas, prompts, respuestas, el id del paciente y el detalle técnico del proveedor | Retención y límites del plan gratuito, no verificados |
| **GitHub** (repositorio público) | El código y los datos sintéticos de la semilla y de `eval/` | Llaves y datos reales (Gitleaks en CI) | — |

**El audio del dictado** sale a Groq para transcribirse y la aplicación no lo guarda: ni en la base, ni en el disco del backend o de nginx (tampoco como archivo temporal), ni en registros o trazas (condición del equipo, 2026-11-05). Lo que sí queda es el texto que la persona revisa y envía, con origen `VOZ`. Para que Groq tampoco lo conserve en sus registros de abuso, el equipo debe activar *Zero Data Retention* en la consola de Groq **[Propuesta]**.

**El prompt puede contener datos personales.** El texto de las notas es libre y **no hay depuración automática**: si alguien escribe un nombre propio o un documento en una nota, sale tal cual a Groq o a Gemini. Hoy no es un riesgo real porque todos los datos son sintéticos; con datos reales sería el primer hueco a cerrar. Los datos en reposo están en un PostgreSQL local en Docker, sin cifrado en reposo configurado, y la interfaz se sirve por HTTP en `localhost`.

## 5. ¿Qué hacemos si el sistema causa un daño?

Daños que consideramos: **a)** un informe con una afirmación falsa o un diagnóstico llega al médico; **b)** texto de pacientes sale a un tercero; **c)** se filtra una llave. Hoy los datos son sintéticos, así que ninguno afecta a una persona real.

| Pregunta | Respuesta |
|---|---|
| **¿Quién se entera?** | Hoy, **nadie de forma automática**: no hay alertas, correo ni monitor. Se entera la persona de enfermería al revisar, o el equipo al mirar la traza o los registros. **[Propuesta]** responsables: las dos personas del equipo; quien detecte avisa a la otra de inmediato |
| **¿Cómo se apaga?** | **Solo la IA** (los registros siguen): quitar `GROQ_API_KEY` y `GEMINI_API_KEY` del `.env` y ejecutar `docker compose up -d backend`; registrar entradas sigue funcionando (RF-41). **Todo:** `docker compose stop backend`; los datos permanecen en el volumen. **Langfuse:** quitar `LANGFUSE_PUBLIC_KEY` y `LANGFUSE_SECRET_KEY`, o revocarlas en su panel. **Una llave filtrada:** revocarla en la consola del proveedor; borrarla del historial no basta |
| **Lo que NO existe** | Un botón o endpoint para cancelar una ejecución en curso (`RunController` solo crea, consulta y sigue el progreso). La ejecución termina sola por sus límites o se corta al detener el backend; al reiniciar queda `FALLIDA` |
| **¿Qué se revisa?** | El informe afectado: su traza (herramienta, parámetros, resultado, ms y tokens), sus versiones (la del modelo y las de enfermería), quién lo aprobó y cuándo, y si ya llegó al médico (hoy el estado `APROBADO`; con D29, `ENVIADO`, con quién y cuándo lo envió). Para un fallo del proveedor, `agent_runs` dice el motivo de parada |
| **¿Cómo se corrige?** | Un informe aprobado o enviado no se modifica ni se retira: se pide uno nuevo, que pasa de nuevo por enfermería. Avisar al médico que lo recibió se hace **fuera del sistema** (hoy no envía mensajes) |
| **¿A quién se avisa?** | **[Propuesta]** a la persona de enfermería que aprobó, al médico que recibió el informe, al profesor del curso mientras sea una entrega, y al proveedor si hubo una fuga. Con datos reales, a la institución y a quien exija la normativa aplicable, que el equipo debe confirmar antes de usarlos |

## Lo que esta ficha todavía no puede decir

1. **Quién es la persona que aprueba:** falta la autenticación (RF-31, M3).
2. **El modelo de amenazas:** `docs/seguridad.md` no existe; esta ficha se reescribe sobre él.
3. **La retención y la auditoría:** sin política de plazos y sin registro de auditoría (RF-35 fuera).
4. **Qué hacen Groq, Google y Langfuse con lo que reciben:** no está verificado del todo; de Groq se sabe lo de `docs/your-data` (sección 4), no si entrena con los datos. Por eso solo hay datos sintéticos.
5. **Langfuse en la práctica:** el envío existe y se probó contra un servidor local (`LangfuseExporterTest`, `LangfusePayloadTest`, `AgentRunnerTest`, `StructuringServiceTest`), pero no contra Langfuse Cloud. Lo que puede salir lo limita la forma de `LangfuseRun` (no tiene campo para texto, paciente ni detalle técnico). Se confirma al cerrar el paso 6 de `docs/PLAN_M3.md`.
