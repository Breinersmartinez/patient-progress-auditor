# Prompt maestro: construir la aplicación hasta el módulo de enfermería (Momento 2)

**Proyecto:** Patient Progress Auditor · **Rama de trabajo:** `develop_BSMartinez` · **Entrega:** enlace al repositorio el jueves 29-oct-2026 a medianoche; demo en vivo el sábado 31-oct-2026.

**Cómo se usa.** Abre Claude Code en la raíz del repositorio y escribe:

> Lee `docs/prompts/m2-enfermeria.md` completo y ejecuta la ETAPA 0. No escribas código hasta que yo apruebe el plan de la ETAPA 1.

También puedes pegar este archivo entero como primer mensaje. Cada etapa se trabaja en una sesión nueva (ver el Apéndice A).

---

## 0. Qué vas a construir y qué no

Construyes la aplicación completa **hasta el módulo de enfermería**: base de datos y migraciones, backend Spring Boot, agente de IA con proveedor real, frontend Angular con el diseño exacto de los mockups de la versión A, y el arranque en frío con Docker Compose. El resultado es lo que enfermería usa de principio a fin: registrar, generar, revisar, corregir y aprobar.

| Dentro de este prompt | Fuera de este prompt |
|---|---|
| Pacientes y entradas: RF-01, RF-02, RF-04, RF-05 | Vista del médico más allá de la lectura: RF-29, RF-30. La lectura mínima (RF-28) está en la etapa opcional E12c |
| Eventos y métricas: RF-06, RF-07, RF-40, RF-41 | Administrador y todo el Momento 3: RF-31, RF-33, RF-34, JWT, login, credenciales, tablero de permisos |
| Agente e informe: RF-08 a RF-22 | Rechazar con motivo (RF-27) y corregir una entrada (RF-03): el mockup A no tiene pantalla para ellos |
| Revisión y aprobación: RF-23 a RF-26 | PDF (RF-37) y voz (RF-38) |
| Informe mensual de cuatro semanas: RF-36 | Notificaciones, «Enviar al médico», RAG, multiagente, modo simulado del proveedor (D16) |
| Sesión de demostración: RF-32 (roles ENFERMERIA y AGENTE aplicados) y RF-39 | Pantallas del administrador del mockup |
| «Texto corregido» (RF-42, M2-S) porque el mockup lo trae en la pantalla de registro; recortable (ver E9) | |
| Auditoría (RF-35, M2-S) solo backend; última etapa, recortable | |
| Todos los RNF (RNF-01 en la medida de D26), RN-01 a RN-11, CU-01 a CU-04, CU-07, CU-08 y la parte de enfermería de CU-10 | |

El rol `MEDICO` y sus dos usuarios sí existen en el esquema y en la semilla (RF-39, D12), pero **no tienen pantallas ni endpoints propios** salvo los de la etapa opcional E12c. Sin ella, lo único del médico que se prueba es que el servidor le niegue aprobar y que no vea informes que no sean `APROBADO`.

Consecuencia que debes decir en el plan de la ETAPA 1 y al cerrar: RF-28 (el médico consulta los informes aprobados) es M2-N y la puerta de aceptación de la SPEC §6 pide poder trabajar con un usuario de enfermería y uno de médico. **Sin E12c esa puerta no se cumple.** E12c no se ejecuta sin que yo lo decida, pero el plan debe incluirla, estimada.

---

## 1. Reglas que mandan

**Orden de fuentes.** Si dos chocan, dilo antes de asumir una solución.

1. `AGENTS.md` y `CLAUDE.md` (ya cargados).
2. `docs/SPEC.md`: alcance §3, diseño §4, puerta de aceptación §6, decisiones D1 a D27 §7.
3. `docs/REQUISITOS.md`: cada RF, RNF, RN y CU con su hito y su criterio de sí o no.
4. `docs/DICCIONARIO_DATOS_M2.md`: base de `V3` y `V4`. Si choca con `docs/DICCIONARIO_DATOS.md`, avisa; si cambias algo que está en los dos, actualiza los dos.
5. `docs/EDT_M2.xlsx` y `docs/EDT_M2_Patient_Progress_Auditor.xlsx`: la EDT con sus IDs de tarea (1.1 a 15.2). Si difieren, vale la modificada más recientemente. Solo lectura; la tabla de la sección 5 ya trae los IDs que necesitas.
6. `docs/07-mockups/mockups-vA/mockups/auditor.html`: **única** referencia de la interfaz (sección 3). Para el alcance mandan la SPEC y los requisitos; para la interfaz manda este mockup.

**Reglas duras** (resumen de `AGENTS.md`, que sigue mandando):

- **Permisos.** Sin preguntar: leer, editar en la rama, correr pruebas. Preguntar siempre: borrar, hacer push, comandos destructivos, instalar dependencias, tocar `.env` o credenciales. Para no frenar el trabajo, en la ETAPA 1 listas **todas** las dependencias nuevas que prevés (backend, frontend, herramientas de prueba) y se aprueban juntas.
- **Commits.** No hagas commit ni push. Al cerrar cada etapa propón el mensaje (Conventional Commits, en minúsculas e imperativo) y lo hago yo.
- **Repositorio público.** Ninguna llave ni secreto real en archivos ni en el historial; `.env` no se versiona; solo datos sintéticos. Si necesitas una llave para probar, que esté en una variable de entorno del shell: no la escribas en ningún archivo, no la imprimas y no la pegues en trazas ni en documentos.
- **IA real, sin modo simulado (D16).** La aplicación siempre llama a un proveedor real. Las pruebas automáticas de las reglas críticas (aprobación, permisos, validadores) usan dobles de prueba y no necesitan modelo.
- **Verificar o decir que no se pudo.** Un paso por vez, con su comprobación. Si no puedes verificar algo (no hay Docker, no hay llave, no hay Chrome), escribe «no verificado» y por qué. Nunca «funciona bien» sin evidencia.
- **Decisiones explicables.** Cada decisión que el profesor pueda preguntar (proveedor y modelo, con o sin RAG, síncrono o asíncrono, cada herramienta, por qué Java 17) va en `docs/decisiones.md` con su razón y la alternativa descartada. Al cerrar cada etapa lista las decisiones que tomaste y nadie te pidió.
- **La rúbrica del Momento 2 (30 % de la nota) es el criterio de prioridad:** el agente 25 puntos, los datos 20, la aplicación 20, la medición y las trazas 25 y la sustentación 10. Sin presentación no hay nota y sin límite de iteraciones no hay entrega. Cuando falte tiempo se protege el agente y la medición.
- **Cada librería nueva** (backend o frontend) se anota en `docs/decisiones.md` con su razón y la alternativa descartada: en la defensa se pregunta por todas.
- **Código que el equipo pueda explicar.** «Lo hizo la IA» vale cero en la defensa, y a cada integrante le pueden señalar una función al azar. Por eso cada reporte de etapa trae las funciones clave y por qué se hicieron así (sección 7).
- **No toques `docs/07-mockups/**`.** No abras `mockups-vB` (7 MB, no es el diseño) ni `__MACOSX`.
- **Idioma.** Interfaz, mensajes al usuario y documentos en español; identificadores de código y de base de datos en inglés y `snake_case`, como el diccionario.
- **Producto.** La IA propone y la persona confirma (RN-11). Sin diagnóstico ni dosis (RN-03). Si faltan registros, el informe lo dice y no rellena (RN-08). Las cifras las calcula el código (RN-04). Cada afirmación cita al menos una entrada original (RN-05). Un informe solo llega al médico en `APROBADO` y solo enfermería aprueba, validado en el servidor (RN-01, RN-02). Al proveedor del LLM solo salen el id interno del paciente y el texto (RNF-02). El agente no tiene herramientas de salida (RF-21).

---

## 2. ETAPA 0: instalar y verificar las skills de trabajo (primero que todo)

Las cuatro skills del curso son `especifica-encargo`, `planea-trabajo`, `ejecuta-plan` y `valida-resultado`. La fuente es `skills/`; Claude Code las lee desde `.claude/skills/`. Instalarlas es copiar carpetas: no hace falta Node, ni cuenta, ni internet.

1. Muestra la rama y el estado (`git branch --show-current`, `git status --short`) y lista `.claude/skills/`.
2. Instala solo lo que falte:

   ```bash
   mkdir -p .claude/skills
   for s in especifica-encargo planea-trabajo ejecuta-plan valida-resultado; do
     if [ -f ".claude/skills/$s/SKILL.md" ]; then echo "ya instalada: $s"
     else cp -R "skills/$s" ".claude/skills/$s" && echo "instalada: $s"; fi
   done
   diff -rq skills .claude/skills && echo "sin diferencias con skills/"
   ```

3. Si alguna copia difiere de `skills/`, muéstrame la diferencia y **pregunta** antes de sobrescribirla. No edites el contenido de ninguna skill.
4. Comprueba que las cuatro aparecen disponibles en Claude Code (lista de skills o `/skills`). Si no aparecen, dime que reinicie la sesión y espera; las skills se cargan al abrir la sesión.
5. Lee los cuatro `SKILL.md` y sus `references/` (plantillas de encargo, plan, resumen y validación). Úsalas desde ahora con su formato exacto.
6. No instales nada más: ni plugins, ni marketplaces, ni paquetes globales.

**Cierre de la ETAPA 0 (sí o no):** ¿las cuatro skills existen en `.claude/skills/`, son idénticas a `skills/` y aparecen disponibles? Muéstrame la lista. Si una skill falla al usarla, reporta cuál y qué se corrigió (`AGENTS.md`).

---

## 3. Contrato del frontend: mockup A, fiel y único

**Archivo de referencia:** `docs/07-mockups/mockups-vA/mockups/auditor.html` (autónomo; trae tokens, componentes y módulos). `auditor.fuente.html` es la misma lógica sin los componentes inlineados y sirve para leer. Son de solo lectura.

### 3.1 Reglas de fidelidad

- **F1. Se porta, no se rediseña.** Copia los bloques de CSS **tal cual** (tokens `:root`, sombras `*`, `.icon`, `.btn*`, `.sb*` y el CSS del módulo) a `frontend/src/styles.css`. Los nombres de clase no cambian. Los componentes Angular envuelven ese CSS (sin encapsulación de estilos para esas clases).
- **F2. Sin librerías de interfaz.** Ni Angular Material, ni Bootstrap, ni Tailwind, ni fuentes externas, ni iconos externos. Tipografía `system-ui` como el mockup. No agregues colores, sombras, radios ni iconos fuera del sistema del mockup. Los iconos nuevos, si hicieran falta, usan el mismo trazo (`stroke-width 1.8`, `round`) y se agregan a `ICONOS`.
- **F3. Componentes a portar** (lógica del mockup a componentes standalone de Angular 19): `icono()` e `ICONOS`; `btnProceso()` y `conProceso()` (con `aria-busy`, texto en gerundio e icono animado); `menuLateral()` (contraíble, submódulos con guía de árbol, flotantes al contraer, tarjeta de usuario, teclado y `aria`, contraído por defecto bajo 760 px); `listado()` (búsqueda, «N resultados», tabla que se vuelve tarjetas bajo 700 px con `data-label`, estado vacío y «Sin resultados»); chip `estado()`; `toast()` (`#toasts`, `aria-live`); `vacioHTML()`; `hojaHTML()`; `filaSintoma()` y `vistaPrevia()`; los ayudantes de fecha en español.
- **F4. Rutas con hash**, idénticas al mockup (`withHashLocation()`): `#/enf/pacientes`, `#/enf/pacientes/ficha/:id`, `#/enf/registro/nuevo/:id?`, `#/enf/registro/historial`, `#/enf/informes/generar/:id?`, `#/enf/informes/revision`, `#/enf/informes/aprobados`, `#/enf/informes/ver/:id`. La ruta por defecto es `#/enf/pacientes`; lo desconocido muestra «No encontramos lo que busca». Los títulos del encabezado son los del mockup. El hash además evita configurar nginx para rutas de la SPA (anótalo en `docs/decisiones.md`).
- **F5. Textos.** Todo el microcopy en español sale del mockup, palabra por palabra, salvo lo que figura en la tabla 3.3.
- **F6. Se conservan** `:focus-visible`, `.solo-lector`, `prefers-reduced-motion`, los puntos de quiebre de 700 px y 760 px, y el bloque `@media print` aunque el botón de PDF no exista.
- **F7. Fuera de la aplicación** todo lo que en el mockup simula el sistema: el objeto `DATOS`, `sleep()`, `interpretar()`, `redactar()`, la constante `HOY` y las notificaciones. La app usa la API real y la fecha del servidor (zona `America/Bogota`).
- **F8. Verificación visual obligatoria** en cada etapa de pantallas (E5, E6, E9, E12b, E13). Abre el mockup y la app con Chrome sin interfaz y compara a 1280×800 y a 390×844:

  ```bash
  # el binario puede llamarse google-chrome, chromium o el de CHROME_BIN (el mismo que usa Karma)
  chrome --headless=new --disable-gpu --hide-scrollbars --window-size=1280,800 \
    --screenshot=/tmp/m-pacientes.png "file://$PWD/docs/07-mockups/mockups-vA/mockups/auditor.html#/enf/pacientes"
  ```

  Mira las dos imágenes (puedes leerlas) y lista cualquier diferencia de maquetación, tipografía, color o espaciado. Solo se aceptan diferencias de **contenido** y las de la tabla 3.3. Las capturas se quedan fuera del repositorio. Si no hay Chrome, dilo; no instales Playwright sin preguntar.

### 3.2 Pantallas de enfermería y de dónde salen

Las líneas son de `auditor.html` y orientan la lectura; busca por nombre de función. Lee solo lo que la etapa necesita.

| Ruta | Función del mockup | Línea | Datos reales |
|---|---|---|---|
| Shell: menú, encabezado, tarjeta de usuario, toasts | `menuLateral`, `toast`, `ROLES.enf` | 240, 533, 664 | Sesión de demostración (D26) |
| `#/enf/pacientes` | `vPacLista` | 716 | Pacientes con último registro y último informe |
| `#/enf/pacientes/ficha/:id` | `vPacFicha` | 726 | Ficha, último informe, siete registros recientes |
| `#/enf/registro/nuevo/:id?` | `vRegNuevo`, `filaSintoma`, `vistaPrevia` | 769, 793, 802 | Estructuración con IA (D27) |
| `#/enf/registro/historial` | `vRegHistorial` | 840 | Entradas de todos los pacientes |
| `#/enf/informes/generar/:id?` | `vInfGenerar` | 915 | Pide la ejecución del agente (D24) |
| `#/enf/informes/revision` y `aprobados` | `vInfLista` | 981 | Informes por estado |
| `#/enf/informes/ver/:id` | `vInfVer`, `hojaHTML` | 878, 848 | Informe, evidencia, edición, aprobación |

Tokens y botones: líneas 7 a 195. CSS del módulo: 316 a 482. Componentes JS: 196 a 315. Las pantallas de administrador (992 a 1035) y la campana (1037) **no se construyen**; `vUsuForm` y `vUsuLista` sirven solo como patrón de formulario y de cabecera con botón.

### 3.3 Diferencias obligatorias entre el mockup y la SPEC

El mockup manda en la interfaz y la SPEC en el alcance. Estas son las diferencias que debes aplicar, y cada una se anota en `docs/decisiones.md`.

| Mockup | En la aplicación | Razón |
|---|---|---|
| Informe aprobado: botón «Enviar al médico» y estado «Enviado» | No existen. El informe `APROBADO` ya es visible para el médico. La nota del informe aprobado pasa a «Informe aprobado y guardado. Ya puede consultarlo el médico.» | RN-01, RF-23: son cuatro estados y el envío manual no existe |
| Botón «Descargar PDF» | No se muestra | RF-37 es opcional |
| `vInfGenerar`: período «Rango de fechas» | Se quita | La semana es de lunes a domingo (RN-06, D10) |
| `vInfGenerar`: período «Un mes» (calendario) | «Un mes (cuatro semanas)»: ventanas de cuatro semanas completas consecutivas | RN-10, D4 |
| `vInfGenerar`: lista de semanas fija | Solo semanas completas (lunes a domingo ya terminadas) que tengan entradas | La SPEC no dice si se puede pedir una semana en curso; se propone que no (anótalo como propuesta) |
| `vInfGenerar`: «Comparar con» con tres opciones | Se reemplaza por una línea de texto con la clase `.sec`: «Se compara con la semana anterior; el agente decide cuántas semanas más atrás revisar.» | RN-06, D10 |
| `vistaPrevia`: error «Ese paciente ya tiene un registro en esa fecha» | Se elimina | RN-09: varias entradas el mismo día |
| `vistaPrevia`: el síntoma escrito a mano puede ser nuevo («Síntoma nuevo en el catálogo») | El campo exige uno de los nueve del catálogo cerrado (D18); si no, el error «Elija un síntoma de la lista.» | `CHECK` en `entry_events.symptom` |
| `vistaPrevia`: «Registro guardado. Se notificó al médico.» | «Registro guardado.» y el enlace a la ficha | No hay notificaciones |
| `vistaPrevia`: texto guardado = texto corregido | Se guarda el texto **original** en `entries.body`; el corregido es una versión aparte (RF-42). Las listas muestran la versión vigente; el informe cita siempre el original | RN-05, RN-07 |
| Campo de fecha sin límite | Por defecto hoy y `max` hoy | Propuesta; anótala |
| «Aprobar y guardar» | Se conserva el texto del mockup. Queda anotado como pendiente de D27 porque se parece a la aprobación del informe | D27 |
| Tarjeta de usuario con «Mi perfil», «Preferencias» y «Cerrar sesión» | No se muestran esas tres opciones (no hay perfil ni sesión hasta el Momento 3). Las filas Área, Cargo y Último acceso solo aparecen si hay dato, que es como ya funciona el componente | RF-31 es M3 |
| «Ver como» con Enfermería, Médico y Administrador | El mismo control (mismas clases y lugar) con las **enfermeras de demostración** por nombre. El usuario elegido viaja en cada petición y se recuerda en el navegador. El médico aparece solo si se ejecuta E12c; Administrador no aparece hasta el Momento 3 | D26 |
| Estados `En revisión`, `Aprobado`, `Enviado` | `En revisión` = `PENDIENTE_REVISION`, `Aprobado` = `APROBADO`; se agrega `Incompleto` con el color `#E24B4A` ya presente en `COLOR_ESTADO` | RF-17, RF-23 |
| Pacientes con cama, edad e ingreso | Se agregan esas tres columnas a `patients` (sección 4.3) | La lista y la ficha las muestran |

### 3.4 Lo que la aplicación necesita y el mockup no tiene

Se compone **solo** con piezas que ya existen en el mockup y se lista cada una en `docs/decisiones.md` para que el equipo la revise.

| Pantalla o elemento | Se arma con | Notas |
|---|---|---|
| Alta de paciente (RF-01) | El patrón de `vUsuForm` (formulario `.chat-pag`, `.campo`, `.inp`, `.error`, `btnProceso` con icono `guardar`) y un botón «Nuevo paciente» en `.cab` como el «Nuevo usuario» de `vUsuLista`. Ruta `#/enf/pacientes/nuevo` | Alias (código, p. ej. P-045), cama, edad e ingreso. El id interno lo genera el servidor |
| Progreso de la generación (D24, RNF-05) | `.aviso` y una lista de pasos con `.est--d` y `.dot`, dentro de `vInfGenerar`, junto al botón «Generar con IA» que pasa a «Generando» | Cada paso es un evento real de `agent_steps` por SSE («Consultando registros…»). Sin efecto de máquina de escribir. Al terminar redirige a `#/enf/informes/ver/:id`, como el mockup |
| Fallos honestos (RF-19, RNF-04) | `toast(texto, false)`, `.error` o `.aviso` | Un mensaje claro por cada fallo (429 o caída, tiempo agotado, salida que no valida). Nunca un stack trace ni un código crudo |
| Llave del proveedor ausente (D22, RNF-13) | `.aviso` al tope de las pantallas que usan IA (registro y generar) | Se lee de un endpoint de estado del proveedor |
| Fallo de la estructuración (RF-41) | Dentro de `vistaPrevia`: `.aviso` con el motivo y los botones «Guardar solo el texto» y «Agregar síntoma» | La entrada queda `SIN_ESTRUCTURAR` y el informe lo declara |
| Traza (RF-22, RF-24, CU-07) | En `vInfVer`, un botón `btn--sec` «Ver cómo se generó» que abre un bloque con una tabla como la de `vTrazas` (herramienta, parámetros, resultado, ms, tokens) | A un clic desde el informe. La traza de la estructuración se guarda y se expone por API; mostrarla es opcional |
| Evidencia a un clic (RF-24) | En `hojaHTML`, cada `[n]` es un ancla a su fila de «Fuentes»; cada fuente enlaza a la ficha del paciente; la línea «Comparado con» muestra el criterio | La fuente muestra el texto original de la entrada |
| Informe incompleto (RF-17) | Chip «Incompleto», `.aviso` con el motivo y sin botón de aprobar; enlace «Generar de nuevo» | `INCOMPLETO` es un estado final |
| Informe mensual (RF-36, RN-10) | La misma hoja con una tabla por semana (`.hoja table`) y, si falta una semana aprobada, un `.error` que dice cuál | No se genera si falta alguna |

### 3.5 Cómo se muestra el contenido del informe

El informe se guarda como JSON validado (`report_versions.content`, DICCIONARIO_DATOS_M2 §4.8) y la hoja lo dibuja con las secciones del mockup: «Resumen de la evolución» (`summary` y las afirmaciones `claims` con su `[n]`), «Cifras de la semana» (`metrics`, calculadas por el código), «Patrón a destacar» (`recurring_patterns`) y «Fuentes (registros originales)». Lo que el mockup dice de los días sin registro («Cabe señalar que no existe registro…») sale de `insufficient_data`: no es una afirmación con cita (RN-08) y se muestra con la fuente «dom 25: sin registro», como el mockup. Si cambias esta correspondencia, anótalo.

---

## 4. Decisiones

### 4.1 Ya tomadas (no se reabren)

Stack Spring Boot 3.4 con Java 17 y Angular 19 (D9); consulta directa a la BD, sin RAG (D8); agente asíncrono con eventos de progreso por SSE, sin broker, y se transmiten pasos y no tokens (D24); la estructuración al registrar es una llamada corta, sin herramientas, en la misma petición (D27); semana de lunes a domingo (D10); mensual de cuatro semanas con semanal aprobado (D4, RN-10); sin autenticación ni `JWT_SECRET` en el Momento 2 (D23); conjunto sintético de cinco pacientes (D11); sin modo simulado (D16).

### 4.2 Propuestas que cierras tú en el plan (yo las apruebo)

- **Proveedor y modelo (D15).** Propuesta: Google AI Studio como principal y Groq como respaldo. **Verifica los modelos y los límites gratuitos vigentes con una búsqueda web a la fecha de hoy**; no los tomes de memoria ni de la página del curso. Registra latencia, costo por token, tool calling y ventana de contexto en `docs/decisiones.md`. Llamadas por HTTP desde la capa de proveedor, sin SDK (`RestClient` ya está en `spring-boot-starter-web`).
- **Los cuatro límites del agente**, con su valor y su razón en `docs/decisiones/`: iteraciones máximas, presupuesto de tokens por solicitud, confirmación humana antes de lo irreversible (aprobar es siempre una persona) y tiempo máximo por herramienta.
- **Inventario de herramientas:** atómicas, pocas (hasta siete), de lectura salvo guardar el borrador en `PENDIENTE_REVISION`. La descripción de cada una es un prompt: cuándo usarla, cuándo no y qué prerrequisitos tiene. Itera y deja el registro.
- **Cómo viaja el usuario elegido.** Propuesta: cabecera `X-Demo-User` con el id. `EventSource` no permite cabeceras: decide entre un parámetro de consulta solo para el SSE o `fetch` en streaming, y anótalo (no es un control de seguridad en el Momento 2).
- **Perfil de Spring para Docker Compose.** La semilla no corre en `prod` (RF-05); elige el perfil del arranque en frío y justifícalo.
- **Validación del esquema de salida.** Prefiere Jackson con registros tipados y Bean Validation (sin dependencia nueva). Si eliges una biblioteca de JSON Schema, va en la lista de dependencias de la ETAPA 1.
- **Pruebas con base de datos.** Reutiliza el servicio de PostgreSQL de `ci.yml`. No agregues Testcontainers sin aprobación.
- **Variables de entorno.** `.env.example` lista exactamente `GEMINI_API_KEY`, `GROQ_API_KEY` y `DATABASE_URL`, más las que añada la aplicación. La página de entrega trata una variable no documentada como falla crítica.
- **Los tres fallos forzables en vivo** (tiempo agotado, salida que no valida, caída o 429 del proveedor): la rúbrica pide demostrarlos. Propuesta: variables de entorno de fallo inyectado que actúan dentro de la capa de proveedor, documentadas en el README y apagadas por defecto. **No es un modo simulado (D16):** la ruta normal siempre llama al proveedor real. Lo confirmo yo antes de construirlo.
- **Cambio de proveedor en vivo.** Procedimiento corto (variable más reinicio del backend) escrito en el README y probado; se demuestra en la sustentación.
- **Ejecuciones huérfanas.** Al arrancar el backend, toda ejecución que quedó «en curso» pasa a error honesto («interrumpida por un reinicio»); el ejecutor vive en el proceso y un reinicio la mataría sin avisar.

### 4.3 Ajustes al diccionario que debes hacer (en los dos documentos)

El mockup muestra cama, edad e ingreso del paciente y `patients` solo trae `alias`. Agrega a `patients`: `bed VARCHAR(10)`, `age SMALLINT` y `admitted_on DATE`, todas aceptando nulo, sintéticas, y que **nunca** salen al proveedor del LLM (RNF-02). Actualiza `DICCIONARIO_DATOS_M2.md` y `DICCIONARIO_DATOS.md` en el mismo cambio, y lo mismo si surge otro ajuste. Incluye `entry_versions` y `audit_logs` ya en `V3` para no necesitar una `V5`.

### 4.4 Lo que debes preguntarme y no resolver en silencio

Cualquier decisión de producto que la SPEC marque «por confirmar» y que cambie lo que ve la persona: valores de intensidad (el diccionario propone los cuatro del mockup), nombre del botón «Aprobar y guardar», catálogo de síntomas (D18), qué hace «Descartar» (propuesta: cierra la vista previa y no guarda nada).

---

## 5. Etapas

**Una etapa es un encargo con un plan de siete pasos como máximo** (regla de `planea-trabajo`). Si una etapa necesita más, divídela y dilo. Las IDs de la columna EDT son las de las hojas de la EDT.

**Orden de ejecución por puntos de la rúbrica.** Las filas siguen este orden: lo que más puntúa y lo que anula la entrega va antes que lo opcional. Los números de etapa no cambian. Si falta tiempo se recorta desde abajo: primero E16, luego E13 y luego «Texto corregido» dentro de E9. E12c no se recorta sin decirlo, porque es lo que cumple la puerta de la SPEC §6. **E14 y E15 nunca se recortan:** la medición vale 25 de 100 puntos y un README que una IA no pueda ejecutar anula la entrega.

| Etapa | Qué se construye | EDT | Cierre (sí o no) |
|---|---|---|---|
| **E1. Arranque en frío** | Quitar la exigencia de `JWT_SECRET` y la protección con token (`application.yml`, `.env.example`, cadena de seguridad abierta con el usuario de demostración). `CMD` del Dockerfile del backend. Dockerfile del frontend en dos etapas (Node 20, nginx) con proxy `/api` hacia el backend, **sin acumular la respuesta** (`proxy_buffering off`, `X-Accel-Buffering: no`). `docker-compose.yml` con PostgreSQL de versión fijada y comprobaciones de salud. `.env.example` con todas las variables, incluidas exactamente `GEMINI_API_KEY`, `GROQ_API_KEY` y `DATABASE_URL`. `run.sh` que verifica Docker, crea `.env`, genera `DB_PASSWORD`, toma la llave del entorno, la pide solo si hay terminal y, si no hay ninguna, arranca igual y avisa | 1.1 a 1.6 | ¿`docker compose up --build` deja base, backend y frontend sanos sin `JWT_SECRET`? ¿`run.sh` sin terminal y sin llave arranca en vez de quedarse esperando? |
| **E2. Esquema y roles** | `V3` con todas las tablas del diccionario (incluidas `entry_versions` y `audit_logs`) y las columnas nuevas de `patients`; `V4` con roles `ENFERMERIA`, `MEDICO` y `AGENTE` y los permisos del §3.4 con UUID fijos; entidades y repositorios del núcleo. `V1` y `V2` no se editan | 2.1 a 2.6 | ¿Flyway aplica `V3` y `V4` en una base vacía, el `CHECK` rechaza un síntoma fuera del catálogo y un informe que no empieza en lunes, solo `ENFERMERIA` tiene `REPORTS_APPROVE` y `mvn -B verify` pasa? |
| **E3. Semilla** | `ApplicationRunner` idempotente, solo con `APP_SEED_DEMO=true` y nunca en `prod`: 4 usuarios sin contraseña (2 de enfermería y 2 médicos), 5 pacientes con alias estilo mockup (P-014, P-021, P-007, P-033, P-040), unas 120 entradas con eventos ya confirmados y fechas fijas que terminan el domingo 25-oct-2026, repartidas como en D11 (P1 con un síntoma que reaparece en las semanas 1, 3 y 5; P3 con huecos; P4 con negaciones y casi sin eventos; P5 con una nota que trae una instrucción). Textos al estilo de los del mockup. Sin llamar al LLM | 2.7 a 2.12, 2.14 | ¿Existen los conteos de D11, reiniciar dos veces no duplica nada y el perfil `prod` rechaza la semilla? |
| **E4. Sesión y permisos** | Endpoint de usuarios de demostración, filtro que toma el usuario elegido de la petición y deja su rol e identidad, permisos por rol en los endpoints según el §3.4, y pruebas de la matriz de lo que ya existe | 3.1, 3.2 | ¿Una petición sin usuario elegido se rechaza y cada rol hace exactamente lo que dice la matriz? |
| **E5. Frontend: sistema de diseño y shell** | Portar tokens, componentes y shell (menú, encabezado con «Ver como», tarjeta de usuario, toasts, `btnProceso`, `listado`, estados vacíos), rutas con hash, servicio de sesión e interceptor | 3.4 | F8: ¿las capturas del shell, a 1280 y a 390, no muestran diferencias salvo las de la tabla 3.3? ¿Se cambia de enfermera con «Ver como»? |
| **E6. Pacientes y entradas** | API de pacientes (alta) y de entradas (crear con el texto tal como se escribió, listar por paciente y rango, varias el mismo día ordenadas por hora). Pantallas: lista, ficha, alta y historial | 4.1 a 4.4 | ¿El texto guardado es idéntico al enviado, el historial filtra por rango y ordena por fecha, y F8 pasa en las cuatro pantallas? |
| **E7. Métricas** | Cálculo en código por semana: días con y sin entrada, eventos por tipo, días con cada síntoma, variación y síntomas nuevos, persistentes, desaparecidos y reaparecidos | 6.1 a 6.3 | ¿Las cifras coinciden con el cálculo a mano en cada paciente sembrado, incluido el que reaparece en P1? |
| **E8. Capa de proveedor** | Puerto del LLM, adaptador principal y de respaldo, selección por configuración, reintentos con espera de 1 s, 2 s y 4 s y conmutación, mensajes honestos, registro de proveedor, modelo, tokens y costo, y el estado de la llave que alimenta el aviso de la interfaz | 7.1 a 7.6, 1.7 | ¿Una llamada real devuelve JSON, cambiar la variable cambia de proveedor con un procedimiento documentado, cada uno de los tres fallos (tiempo agotado, salida inválida, 429 o caída) se puede forzar y reintenta, conmuta o degrada con un mensaje honesto, y sin llave la app avisa sin error crudo? |
| **E9. Estructuración al registrar** | Esquema y prompt de la propuesta (síntoma del catálogo, intensidad y veces; nada que el texto no diga; las negaciones no crean eventos), servicio de una llamada con un reintento y tiempo máximo, API de proponer y confirmar (proponer no guarda; confirmar guarda entrada y eventos en una transacción), pantalla «Así quedaría el registro», fallo con guardado solo de texto, traza `ESTRUCTURACION`. **Último paso: «Texto corregido» como versión (RF-42)**; si el tiempo aprieta se omite y el campo se oculta con una bandera de configuración | 5.1 a 5.7, 5.8 | Con «Dolor de cabeza intenso desde la mañana. Tuvo diarrea intensa dos veces. Comió poco.», ¿propone dolor de cabeza (intenso), diarrea (intensa, 2 veces) y apetito bajo (sin indicar) y valida? ¿«sin vómito» no crea evento? ¿Nada se guarda hasta confirmar? ¿F8 pasa? |
| **E10. Agente** | Inventario de herramientas, herramienta de lectura y de métricas, la única de escritura (borrador en `PENDIENTE_REVISION`), bucle a mano, los cuatro límites, criterio de parada e `INCOMPLETO`, prompt del sistema, esquema del informe, validador de cifras y de citas, rechazo de diagnóstico y dosis | 8.1 a 8.9, 8.12, 8.13 | ¿Se fuerza cada límite y el agente se detiene y avisa? ¿Una cifra alterada o una cita inexistente se rechaza? ¿La nota con instrucciones de P5 no cambia su conducta? ¿Hay un registro de al menos dos versiones de la descripción de cada herramienta, con el resultado medido de cada una? |
| **E11. Segundo plano y SSE** | Ejecutor acotado en el proceso, estado en `agent_runs`, la petición responde de inmediato con el id y devuelve la ejecución existente si ya hay una igual, eventos reales desde `agent_steps`, reanudación desde el último id | 8.10, 8.11 | ¿Llega el primer evento antes de que termine la ejecución, se reanuda tras cortar la conexión, el proxy no acumula y una ejecución que quedó en curso tras un reinicio aparece como interrumpida? |
| **E12a. Informes: backend** | Pedir informe semanal, estados y transiciones validadas en el servidor, edición con versiones (la versión 1 del modelo no se toca), aprobación solo de enfermería con quién y cuándo, lectura con la regla de estado para el médico, pruebas | 9.1 a 9.4, 3.3 | ¿Una transición no definida se rechaza? ¿El médico recibe rechazo al aprobar y no ve un informe pendiente? ¿Quedan la versión del modelo y la editada? |
| **E12b. Informes: pantallas** | Generar con progreso, listas «En revisión» y «Aprobados», informe con evidencia a un clic, edición, aprobación, traza, incompleto | 9.5 a 9.9 | ¿Se recorre en el navegador generar, revisar, editar y aprobar? ¿Se llega a cada entrada citada en un clic? ¿F8 pasa? |
| **E14. Informes sembrados y evaluación** | Los 5 informes sembrados (4 aprobados de P1, 1 pendiente de P2) con citas válidas y cifras iguales a las métricas; `eval/` con 5 a 10 casos dorados verificados a mano (uno de rechazo), casos de estructuración y el script; la solución convencional sin modelo (una plantilla; puedes portar la lógica de `redactar()` del mockup **solo dentro de `eval/`**, nunca como modo de la aplicación); las cuatro cifras (tasa de éxito, costo por tarea, **mediana** de latencia, nunca el promedio, y recuperación) con fecha y método | 2.13, 12.1 a 12.6 | ¿Los informes sembrados tienen citas y cifras válidas? ¿Un comando corre la suite? ¿Las cuatro cifras están medidas con llave real, con la latencia en mediana, o dicen «no medido»? |
| **E15. Documentación y entrega** | README que una IA ejecute de punta a punta (motor de BD, imagen, versiones exactas, comando, URL, usuarios de demostración, proveedor y modelo con su razón, cómo cambiar de proveedor, cómo forzar los tres fallos, qué pasa sin llave); plantilla `docs/prueba_companero.md` (fecha, quién, minutos hasta tener la app corriendo, qué falló, qué se cambió) para que la llene una persona ajena al código; `docs/decisiones.md`, `decisiones/semana06.md`, `semana08.md`, `semana10.md`; escaneo de secretos del árbol y del historial; lista para la prueba en frío | 13.1 a 13.7, 14.1 a 14.3 | ¿Una IA que solo lee el README llega a la app corriendo? ¿Cada decisión técnica y cada librería tienen razón y alternativa? ¿Existe la plantilla de la prueba del compañero? ¿El escaneo no encuentra secretos? |
| **E12c. Vista del médico (opcional, puerta §6)** | Lectura de pacientes e informes `APROBADO` por paciente y período, del más reciente al más antiguo (RF-28), con la regla de estado aplicada en el servidor; los médicos de demostración en «Ver como»; pantallas de solo lectura que reutilizan la lista de pacientes, `vInfLista` y `vInfVer` con el menú del médico del mockup, sin editar ni aprobar; desde el informe se llega a las entradas citadas (RF-29, si cabe) | ver la EDT | ¿Un informe pendiente, incompleto o rechazado NO aparece para el médico, ni en la pantalla ni por la API? ¿El médico no ve ningún botón de editar o aprobar y el servidor rechaza esas acciones? ¿F8 pasa? |
| **E13. Informe mensual** | Regla de las cuatro semanas aprobadas, generación con las semanales y las métricas, pantalla | 11.1 a 11.3 | ¿Con las cuatro semanas aprobadas de P1 muestra la evolución semana a semana, y con una sin aprobar no se genera e indica cuál? |
| **E16. Auditoría (M2-S, recortable)** | `audit_logs` al aprobar y al editar un informe: quién, cuándo, antes y después | 8.x, 9.x (RF-35) | ¿Cada acción sensible deja una fila con esos cuatro datos? |

**Lo que no haces tú y me dejas listo:** la prueba en frío con un clon limpio, el tag `m2-entrega`, el push, el enlace en el aula virtual, la llave temporal del profesor y el ensayo de la demo (EDT 14.4 a 14.9). Entrégame la lista de pasos y el comando exacto.

**Si trabajamos los dos a la vez** (dos sesiones, dos ramas): después de E2, una persona sigue con E3 a E7 y la otra con E8 a E11, porque la capa de proveedor no depende de la base de datos. Ambos acuerdan primero el contrato de la API en `docs/PLAN.md` y E12 a E15 se hacen juntos.

---

## 6. Cómo se trabaja cada etapa (método del curso)

1. **Sesión nueva por etapa.** Lee `AGENTS.md`, `CLAUDE.md`, esta sección y la nota de traspaso de la etapa anterior en `docs/PLAN.md`.
2. **`especifica-encargo`.** Escribe el encargo de la etapa. Su criterio de aceptación es la columna «Cierre» más los criterios de sí o no de los requisitos listados. Lo que no se pueda deducir va como pregunta pendiente.
3. **`planea-trabajo`.** Escribe el plan en `docs/PLAN.md` (sección de la etapa): siete pasos como máximo, cada uno con su comprobación y el valor esperado, el paso más riesgoso y dónde se puede caer. `docs/PLAN.md` ya trae el plan del retiro del despliegue heredado: déjalo intacto bajo el título «Plan 0 (histórico)» y agrega los nuevos debajo.
4. **Para y espera mi «adelante».** En la ETAPA 1 el plan es el maestro: etapas, dependencias nuevas, decisiones de la sección 4.2 y el paso más riesgoso del proyecto. Sin mi «adelante» no se escribe código.
5. **`ejecuta-plan`.** Un paso por vez: lo entiendes, lo haces, ejecutas su comprobación, escribes el resumen de dos o tres líneas en el plan y marcas la casilla. Tienes autorización para encadenar los pasos de una **misma etapa** mientras cada comprobación pase. Para y pregunta si una comprobación falla, si surge una decisión de producto, si hace falta una dependencia no aprobada o si el paso resultó demasiado grande (pártelo).
6. **`valida-resultado`.** Al cerrar la etapa, contrasta el resultado con el criterio del encargo, condición por condición, con evidencia al lado. «No se puede saber» es una respuesta válida. Quien revisa no es quien escribió: ofrece lanzar la revisión en una sesión nueva o en un subagente (Apéndice A). Si el veredicto es negativo, se vuelve a especificar; no se parcha el resultado.
7. **Higiene de contexto.** No leas el mockup entero cada vez; lee las funciones de la tabla 3.2 que la etapa necesita. No leas documentos que la etapa no usa.

### Comprobaciones por defecto

- Backend: `cd backend && mvn -B verify`.
- Frontend: `cd frontend && npx ng test --watch=false --browsers=ChromeHeadless --code-coverage` y `npm run build`. **`npm run lint` está roto; no cuenta.**
- Arranque: `docker compose up --build` (si no hay Docker, «no verificado» y qué sí corriste: `mvn verify`, `ng build`).
- API: pruebas contra la API real con `curl` y el usuario de demostración, incluida una petición **sin** el usuario o sin el permiso, que debe fallar aunque la interfaz no muestre el botón.

---

## 7. Reporte al cerrar cada etapa

```text
ETAPA N: <nombre>
Resultado: <una o dos frases>
Cierre (sí o no): <cada condición con su evidencia: comando y resultado, o captura comparada>
Decisiones que tomé y nadie me pidió: <lista, también en docs/decisiones.md>
Funciones clave que el equipo debe saber explicar: <de 3 a 5, con archivo, qué hace cada una y por qué se hizo así, en dos líneas>
Diferencias con el mockup: <solo las de la tabla 3.3 y las de 3.4; cualquier otra es un error>
No verificado: <qué y por qué>
Plan actualizado: <casillas marcadas y nota de traspaso de 5 líneas>
Commit propuesto: <tipo(ámbito): descripción en minúsculas, imperativo>
Siguiente etapa: <N+1>
```

---

## 8. Qué no hacer

- No hagas commit, push, borrados ni comandos destructivos; no instales dependencias sin aprobación; no toques `.env`.
- No edites `docs/07-mockups/**`, `skills/` ni `.claude/skills/`; no uses `mockups-vB`.
- No construyas lo que está fuera de la sección 0 aunque el mockup lo muestre (médico, administrador, campana, PDF, «Enviar al médico», rechazo).
- No rediseñes la interfaz ni uses una librería de componentes.
- No inventes datos de pacientes reales ni pongas llaves en ningún archivo.
- No metas el LLM donde el código resuelve exacto (métricas, permisos, estados, validación, límites).
- No agregues RAG ni base vectorial.
- No dejes una decisión técnica sin su entrada en `docs/decisiones.md`.
- No bloquees `run.sh` esperando entrada si no hay terminal.
- No des por terminada una etapa sin su reporte de la sección 7.

---

## Apéndice A. Lanzadores listos para pegar

**Etapa N (sesión nueva):**

> Lee `AGENTS.md`, `CLAUDE.md` y `docs/prompts/m2-enfermeria.md` (secciones 1, 3, 5 y 6). Estamos en la ETAPA N. Lee la nota de traspaso de la etapa anterior en `docs/PLAN.md`. Usa `especifica-encargo` y luego `planea-trabajo`, escribe el plan en `docs/PLAN.md`, muéstramelo y espera mi «adelante» antes de escribir código.

**Revisión con contexto fresco (sesión nueva, otra persona o subagente):**

> Eres revisor: no escribiste el código de la ETAPA N. Usa `valida-resultado` con el encargo y el criterio de la ETAPA N en `docs/PLAN.md`. No modifiques archivos. Corre las comprobaciones, contesta cada condición con sí, no o no se puede saber con su evidencia, y dime a qué tramo del bucle hay que volver si el veredicto es negativo.

**Comparación visual con el mockup (etapas de pantallas):**

> Compara las pantallas de la etapa con `docs/07-mockups/mockups-vA/mockups/auditor.html` a 1280×800 y a 390×844 con Chrome sin interfaz. Lista toda diferencia que no sea de contenido ni esté en la tabla 3.3 de `docs/prompts/m2-enfermeria.md`, y corrígela.

**Retomar tras una caída o un corte de contexto:**

> Lee `docs/PLAN.md`, ubica el primer paso sin marcar de la etapa en curso, dime en una frase qué significa «terminado» para ese paso y continúa con `ejecuta-plan`.
