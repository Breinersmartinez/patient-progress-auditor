# Inicio de sesión con Keycloak — Especificación

2026-11-10 · Andrés (rama `feat/AFL_01_KEYCLOAK`)

> Adelanta al Momento 2 lo que la SPEC dejaba para el Momento 3 (D23, D26, RF-31). Hasta que el equipo
> lo apruebe en `docs/SPEC.md` §7, todo lo de aquí es **[Propuesta]**. El diseño de pantallas y la mecánica
> de Keycloak se repiten de PGRP (`DOC-PGRP/docs/tema-pantallas-estado/`).

## 01. Resumen
Al abrir `http://localhost:4200` la app manda a una pantalla de inicio de sesión de Keycloak con el diseño
del mockup `docs/07-mockups/login-sanitas/auditor_login_sanitas.html`. Hay tres roles (`ADMIN`, `ENFERMERIA`,
`MEDICO`) y por ahora solo enfermería tiene pantallas. Todo queda listo con `./run.sh`: el realm y los usuarios
sembrados se importan solos. Reemplaza al selector "Ver como" y a la cabecera `X-Demo-User` (D26).

## 02. Objetivos
- **1 comando** — con un clon limpio, `./run.sh` deja la app, Keycloak y Mailpit arriba, y un usuario de
  enfermería puede iniciar sesión sin abrir la consola de Keycloak.
- **0 px de diferencia** — el login (vacío, con errores de validación, credenciales inválidas, bloqueo y
  sesión expirada) contra el mockup, a 1440 y 390 px de ancho, en claro y oscuro, con movimiento reducido.
  Se admite el cursor de texto parpadeando, como en PGRP.
- **0 peticiones a `/api/**` sin token válido** — toda ruta de la API responde 401 sin JWT, 403 con un rol que
  no la permite.
- **3 roles** — `ADMIN`, `ENFERMERIA` y `MEDICO` salen del token. Enfermería entra a su tablero. Médico y admin
  se loguean y ven un aviso con "Cerrar sesión", sin llamar a la API clínica.
- **Código de 6 dígitos** — recuperar contraseña manda un código que vale 15 min, se anula al 5.º intento
  fallido y se puede reenviar 2 min después de cada envío.
- **Enlace de alta de 12 h** — la invitación del admin vence a las 12 horas y no sirve dos veces.
- **0 pruebas rotas** — las 366 del backend y las 129 del frontend pasan con JWT simulado.
- **0 texto fijo** en las plantillas del tema: todo sale de `messages_es.properties`.

## 03. Fuera de alcance
- Pantallas del médico y del administrador, gestión de usuarios y tablero de permisos (RF-33, RF-34). Solo
  se entrega el login y el aviso de "rol sin pantallas".
- Los modos del mockup que no son del auditor: "puertas" (¿Cómo desea entrar?), "Soy candidato" y el login en
  dos pasos. Se implementa el login de un paso, modo equipo.
- Los botones "Contactar a soporte" y "Pedir un enlace nuevo" no hacen nada: muestran "El contacto con
  soporte todavía no está disponible.".
- HTTPS, dominios reales, correo real (SMTP) y cualquier despliegue fuera de localhost.
- Login con Google o con otros proveedores, MFA de app (TOTP) y autoservicio de registro.
- Cambiar el diseño de la app (la interfaz de enfermería sigue siendo la del mockup del equipo).
- Un panel de administración de Keycloak propio: se usa la consola estándar.

## 04. Diseño

```
 Navegador :4200
   |  1. entra sin sesión
   v
+--------------+  2. redirige (PKCE)  +--------------------+  3. correo / código  +---------+
| Angular      | -------------------> | Keycloak :7070     | -------------------> | Mailpit |
| (keycloak-js)| <------------------- | tema "auditor"     |                      | :8025   |
+--------------+  4. vuelve con token | + extensión código |                      +---------+
   |  5. Authorization: Bearer <JWT>    +--------------------+
   v                                      | Postgres propio
+--------------+  6. valida firma        v
| Spring Boot  | <-------- claves públicas del realm (JWKS)
| :8080        |
+--------------+  7. rol del token -> permisos; correo -> AppUser (autor de cada acción, D12)
```

**Piezas**
- **Compose:** se suman `keycloak` (26.8.2, imagen propia con extensión y tema), `keycloak-db` (Postgres 17,
  separada de la base de la app) y `mailpit` (v1.28.0, SMTP 1025 solo dentro de la red). Puertos: Keycloak
  7070 y Mailpit 8025; `run.sh` los reasigna si están ocupados, como ya hace con 4200 y 8080.
- **Realm `auditor`** importado con `--import-realm` desde un JSON versionado: client `auditor-web` (público,
  PKCE, redirecciones `http://localhost:*`), roles `ADMIN`/`ENFERMERIA`/`MEDICO`, login con correo, español,
  política de contraseñas, bloqueo por fuerza bruta (15 min), SMTP hacia Mailpit, tema `auditor`, flujo "reset
  credentials" con el paso del código.
- **Usuarios sembrados en el realm (por `./run.sh`):** los 4 de la semilla (`marcela.rojas` y `camilo.torres`
  como enfermería; `andres.salazar` y `julian.ortiz` como médico, todos `@example.test`) y un `admin`. Una
  contraseña de demo común, escrita en el README. Son datos sintéticos.
- **Backend:** `spring-boot-starter-oauth2-resource-server`; valida firma, vencimiento y emisor. Del token
  toma el rol y el correo; el `AppUser` se busca por correo para saber quién actúa (D12). Se elimina
  `DemoUserFilter`, `/api/demo-users` y la cabecera `X-Demo-User`.
- **Frontend:** `keycloak-js`; guarda el token en memoria (no en `localStorage`), lo renueva antes de que
  venza y lo manda en cada petición. El progreso de los informes (SSE) deja de usar `EventSource` y
  `?demoUser=`: se lee con `fetch` en streaming con el Bearer en la cabecera, para que el token no quede en
  la URL ni en logs.
- **Extensión Java** copiada de PGRP, paquete `com.patientprogressauditor.keycloak`, compilada en una etapa de
  Maven dentro del `docker build`. Guarda solo el hash del código, con sal, en la sesión de login.
- **Tema `auditor`** (login, correo y pantallas de estado): CSS literal de cada prototipo y un archivo de
  ajustes aparte. Las pantallas de estado (Contorno), el alta (diseño A) y el código (OTP) son las de PGRP con
  la paleta del mockup: claro `#0079C1`/`#1A97D4`, oscuro `#3FB8EC`/`#8FD8F7`, fondos `#E3F1FA` y `#00172B`. El
  login sale del mockup tal cual.

**Pantallas** (como en PGRP): login; recuperar contraseña (pide el correo) → código de 6 dígitos → crear
contraseña; alta por invitación → crear contraseña → "Contraseña creada"; error 500; activación vencida y
ya usada; cuenta desactivada; sesión vencida (401); demasiados intentos (429); verifica tu correo; correo
verificado; continuar acción; cuenta actualizada.

**Por qué Keycloak y no el JWT propio del scaffold:** ver Decisiones. Lo que no se ve en el diagrama: la
consola de Keycloak queda abierta en `:7070` con el administrador del `.env`, y es la que se usa para
practicar y explicar cada concepto en el README.

## 05. Casos borde
- **Sin sesión en `:4200`:** redirige a Keycloak y, al entrar, vuelve a la ruta pedida.
- **Token vencido o inválido en la API:** 401; el front intenta renovar y, si no puede, vuelve al login con
  el aviso de sesión expirada.
- **Rol sin permiso para una ruta:** 403, y la app muestra "No tiene permiso para esta acción".
- **Usuario con rol de enfermería pero sin `AppUser` con ese correo:** 403 con un mensaje claro y una línea
  en el log. No se crea un `AppUser` solo.
- **Médico o admin:** entran, ven el aviso "Su rol aún no tiene pantallas" y "Cerrar sesión". No hay llamadas
  a datos clínicos.
- **Cerrar sesión:** termina la sesión en Keycloak y vuelve al login.
- **Contraseña mal escrita 5 veces:** pantalla completa de bloqueo por 15 minutos (429).
- **Correo inexistente al recuperar:** la pantalla del código aparece igual y no se manda nada.
- **Código incorrecto 5 veces o vencido:** "Este código ya no sirve. Pide uno nuevo." y el reenvío queda
  disponible. **[Pendiente de confirmar; ver Decisiones, 2026-11-10 sobre el reenvío]**
- **Reenviar antes de 2 minutos:** el botón está desactivado y muestra la cuenta atrás; una petición
  adelantada no manda nada.
- **Falla el SMTP al mandar el código:** error 500 de la pantalla de estado y una línea en el log, sin código
  en el HTML.
- **Enlace de alta vencido, ya usado o con usuario desactivado:** tres pantallas distintas, sin código de
  referencia, con "Contactar a soporte" que muestra el aviso de no disponible.
- **El front en otro puerto (`run.sh` elige 4201):** las redirecciones `localhost:*` lo aceptan.
- **Keycloak todavía arrancando:** `docker compose` espera su healthcheck antes de levantar el frontend.
- **Volumen de Keycloak ya creado:** el import no se repite ni pisa cambios; cambiar el JSON exige
  `docker compose down -v` (se explica en el README).

## 06. Criterios de aceptación
| Criterio | Respuesta |
|---|---|
| K1. Con un clon limpio, `./run.sh` deja app, Keycloak y Mailpit arriba sin tocar la consola | sí / no |
| K2. `http://localhost:4200` sin sesión lleva al login de Keycloak con el tema `auditor` | sí / no |
| K3. `marcela.rojas@example.test` con la contraseña de demo entra y ve el tablero de pacientes de enfermería | sí / no |
| K4. `andres.salazar@example.test` (médico) y `admin` entran y ven "Su rol aún no tiene pantallas" con "Cerrar sesión", sin peticiones a `/api/patients` | sí / no |
| K5. Cualquier ruta de `/api/**` sin token responde 401 y con token de médico a una ruta de enfermería responde 403 | sí / no |
| K6. La cabecera `X-Demo-User`, `?demoUser=` y `/api/demo-users` ya no existen | sí / no |
| K7. El selector "Ver como" ya no está en la interfaz | sí / no |
| K8. El progreso de un informe (SSE) llega con el Bearer en la cabecera y la URL no lleva el token | sí / no |
| K9. El token no está en `localStorage` ni `sessionStorage` | sí / no |
| K10. Una acción de enfermería queda con el `AppUser` de ese correo como autor | sí / no |
| K11. Un usuario con rol de enfermería sin `AppUser` recibe 403 y no se crea nada | sí / no |
| K12. Cerrar sesión termina la sesión de Keycloak y vuelve al login | sí / no |
| K13. Login vs mockup: 0 px (salvo el cursor) en vacío, validaciones, inválido, bloqueo y sesión expirada, a 1440 y 390, claro y oscuro | sí / no |
| K14. Las pantallas de estado, el alta y el código siguen el diseño de PGRP con la paleta del mockup, 0 px contra el prototipo recoloreado | sí / no |
| K15. Recuperar contraseña manda un correo con 6 dígitos a Mailpit y el código correcto lleva a "Crea tu contraseña" | sí / no |
| K16. Un código incorrecto muestra error y suma un intento; al 5.º, el correcto ya no sirve | sí / no |
| K17. El código vence a los 15 min y "Reenviar" se habilita a los 2 min y manda uno nuevo | sí / no |
| K18. El código no aparece en el HTML, la URL ni el almacenamiento del navegador | sí / no |
| K19. La invitación del admin llega a Mailpit con enlace de 12 h; usada o vencida muestra su pantalla propia | sí / no |
| K20. Los 5 intentos fallidos de login muestran la pantalla completa de bloqueo | sí / no |
| K21. Las 366 pruebas del backend y las 129 del frontend pasan | sí / no |
| K22. Las plantillas del tema no tienen texto visible fijo | sí / no |
| K23. El README explica cada pieza (realm, client, roles, flujo, SMTP, tema, extensión) y cómo se importó | sí / no |
| K24. SPEC §7 (D23 y D26), REQUISITOS, AGENTS.md y `docs/decisiones.md` reflejan el cambio | sí / no |
| K25. El login, las pantallas y los dos correos se ven bien a ojo de Andrés | sí / no |

K13 y K14 se comparan con capturas contra el prototipo. K25 depende del juicio de Andrés: queda "hecha, sin
verificar" hasta que lo mire.

## 07. Decisiones
| Fecha | Se eligió | Se descartó | Por qué |
|---|---|---|---|
| 2026-11-10 | Adelantar el login al Momento 2 y actualizar SPEC, REQUISITOS, AGENTS.md y decisiones | Dejar D26 vigente en `main`; solo hacer el tema | Lo pidió Andrés. Contradice D23/D26 y `CLAUDE.md` ("no adelantes lo del Momento 3"), por eso es **[Propuesta]** hasta que el equipo lo apruebe. |
| 2026-11-10 | Keycloak con tema propio | Login propio en Spring con JWT del scaffold | Es lo que se hizo en PGRP y Andrés quiere practicarlo. Costo: tres servicios más en el compose. |
| 2026-11-10 | Realm importado al arrancar | Configurarlo a mano en la consola | La puerta de entrega exige que un clon limpio funcione con `./run.sh`. La parte de practicar queda en el README, que explica cada concepto. |
| 2026-11-10 | Usuarios sembrados en el realm (4 de la semilla + admin) | Crearlos a mano | También lo exige `./run.sh`: sin usuarios no hay con quién entrar. Andrés lo pidió expresamente. |
| 2026-11-10 | Roles en el realm de Keycloak | Roles en la base de datos | Lo eligió Andrés. Costo: la fuente de verdad del rol queda fuera de la base y los permisos finos (RF-34) tendrán que volver a decidirse. |
| 2026-11-10 | Correo del token → `AppUser` | Id del `AppUser` dentro del token | No hay que cambiar el esquema de la base y el realm sigue siendo independiente. |
| 2026-11-10 | Diseño: login del mockup pixel-exacto; estado, alta y código de PGRP con la paleta del mockup | Dejar esas pantallas con el diseño estándar de Keycloak | El mockup no las trae. Elegido por Andrés. |
| 2026-11-10 | Paleta azul de EPS Sanitas (`#0079C1` / `#3FB8EC`) | Turquesa y marino del primer bloque de CSS del mockup | Es la última regla del mockup y la que se ve. Los otros valores no llegan a la pantalla. |
| 2026-11-10 | Solo el modo "equipo", un paso | Puertas, "Soy candidato" y dos pasos | **Supuesto mío, no confirmado:** el auditor no tiene candidatos. Se corrige si no es así. |
| 2026-11-10 | SSE con `fetch` y Bearer en la cabecera | `EventSource` con el token en la URL | El token en la URL queda en logs y en el historial. |
| 2026-11-10 | Token en memoria, renovado con `keycloak-js` | `localStorage` | Un XSS lo leería. Costo: al recargar la página se vuelve a pedir la sesión a Keycloak. |
| 2026-11-10 | Médico y admin ven un aviso | Dejarlos entrar al tablero de enfermería | No tienen pantallas ni permisos. Elegido por Andrés. |
| 2026-11-10 | Código de 6 dígitos, 15 min, 5 intentos, reenvío cada 2 min | Enlace por correo | Lo decidió Andrés en PGRP y se mantiene. 1 de 1.000.000 con 2 min entre envíos hace inviable adivinarlo. |
| 2026-11-10 | **Pendiente:** si el código está anulado, ¿el reenvío es inmediato o espera 2 min? | — | En PGRP quedó inmediato y choca con la justificación anterior: quien falla 5 veces puede pedir otro código al instante. Hay que decidir A (esperar 2 min siempre) o B (inmediato y corregir la justificación). |
| 2026-11-10 | "Contactar a soporte" sin función | Enviar un correo al admin | Aún no existe un canal; se avisa en pantalla. Igual que en PGRP. |
