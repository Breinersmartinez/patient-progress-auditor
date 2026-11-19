# Plan — Inicio de sesión con Keycloak

## Spec de referencia
`docs/login-keycloak/especificacion.md` (2026-11-10). Los criterios K1–K25 se citan por número.

## Supuestos que este plan da por ciertos
- Keycloak 26.8.2 importa un realm con usuarios, roles, client, SMTP y flujo propio con `--import-realm`, y el flujo
  referencia al autenticador de la extensión por su id.
- Spring Boot 3.4.1 valida el JWT con la clave pública que publica Keycloak y no necesita llegar a Keycloak al arrancar.
- Las pruebas del backend se pueden mover a JWT con un `JwtDecoder` de pruebas, sin cambiar lo que cada una verifica.
- El tema, la extensión y los prototipos de PGRP se copian de `PGRP/pgrp/` y `DOC-PGRP/` (solo lectura allá).
- El puerto 7070 y el 8025 los ocupa PGRP mientras se prueba: `run.sh` reasigna los puertos y la spec lo permite.
- Reenvío con el código anulado: **A** (esperar 2 min siempre). Andrés no contestó; se toma la opción más segura y se
  puede revertir en una línea. Queda anotado en la spec.
- Modo del login: solo "equipo", un paso. Supuesto de la spec, sin confirmar.

## Funcionalidades (un commit cada una, con OK de Andrés)
- **F1. Keycloak y Mailpit en el compose, con realm importado:** tareas 1–3
- **F2. Código de recuperación por correo (extensión Java):** tareas 4–5
- **F3. Tema `auditor`:** tareas 6–10
- **F4. Backend valida JWT y quita la sesión de demostración:** tareas 11–14
- **F5. Frontend con login y sin selector "Ver como":** tareas 15–18
- **F6. Documentación y cierre:** tareas 19–21

## Pasos
- [x] 1. Mirar insumos: tema, extensión y prototipos de PGRP; paleta final del mockup; carpetas de destino en este repo. — verificado
  > Inventario hecho; paleta del mockup en `ppa-paleta.css`.
  Criterio: lista de qué se copia y qué cambia de color/paquete/nombre.
- [x] 2. Armar `keycloak/` (Dockerfile con etapa Maven y tema, realm JSON con roles, client y 5 usuarios) y sumar `keycloak`, `keycloak-db` y `mailpit` al compose, con healthchecks y variables en `.env.example`. — verificado
  > Imagen de Keycloak 26.8.2 construye; realm `auditor` se importa con 5 usuarios y 3 roles.
  Criterio: K1 (parte de Keycloak). `docker compose up` deja el realm `auditor` importado con los 5 usuarios y los 3 roles.
- [x] 3. Adaptar `run.sh` (contraseñas generadas, puertos de Keycloak y Mailpit, `KC_HOSTNAME`) y la espera del compose. — verificado
  > Ver K1 en el reporte (arranque en frío en copia limpia).
  Criterio: K1. Un clon limpio (sin `.env`) levanta con `./run.sh` y el token se obtiene por la API del realm.
- [x] 4. Copiar la extensión de PGRP con el paquete nuevo, reenvío con espera también tras anular el código, y el flujo en el realm. — verificado
  > La extensión compila y el flujo `ppa reset credentials` queda con el paso REQUIRED. Reenvío con espera también tras anular (opción A, por defecto; la fila pendiente de la spec sigue abierta).
  Criterio: K16, K17 (parte de la lógica), K18.
- [x] 5. Correo del código y de invitación (tema `email`) con la paleta del mockup. — verificado
  > Correos en el tema `email` con la paleta Sanitas. Falta el juicio visual (K25).
  Criterio: K15 (el correo llega a Mailpit con 6 dígitos), K19 (invitación con 12 h).
- [~] 6. Tema `auditor`: marco del login, CSS del mockup literal, ajustes de Keycloak, `login.ftl`, `login-reset-password.ftl`, textos. — hecha, sin verificar al píxel
  > El login se ve como el mockup en oscuro (captura del navegador). No se corrió la comparación al píxel 1440/390 × claro/oscuro.
  Criterio: K2 (se ve el tema), K13 (0 px contra el mockup en 5 estados × 4 combinaciones).
- [~] 7. Pantalla del código y animación (`login-codigo.ftl`) con la paleta del mockup. — hecha, sin verificar al píxel
  > Pantalla del código escrita; flujo completo por Mailpit sin repetir en este repo.
  Criterio: K14 (código), K15, K16.
- [~] 8. Pantallas de estado Contorno (`error.ftl`, `info.ftl`, `login-page-expired.ftl`, `login-verify-email.ftl`) y bloqueo 429. — hecha, sin verificar al píxel
  > Pantallas de estado copiadas de PGRP y recoloreadas; sin comparación al píxel.
  Criterio: K14, K20.
- [~] 9. Alta de cuenta: `login-update-password.ftl` y "Contraseña creada". — hecha, sin verificar al píxel
  > Alta de cuenta copiada de PGRP; sin comparación al píxel.
  Criterio: K14, K19.
- [~] 10. Revisar textos fijos y comparar al píxel el conjunto de pantallas contra los prototipos recoloreados. — hecha, sin verificar
  > Falta revisar textos fijos con grep y la comparación al píxel.
  Criterio: K13, K14, K22.
- [x] 11. Backend: dependencia `oauth2-resource-server`, `JwtDecoder` con JWKS y validación de emisor, conversor del token a `AppPrincipal` (rol del token, correo → `AppUser`), filtro 403 si falta el `AppUser`, `/api/session` y CORS sin `X-Demo-User`. — verificado
  > Tokens reales: enfermera 200, médico 200 en lectura, admin 403 en pacientes, token basura 401, sin token 401.
  Criterio: K5, K10, K11 (a nivel de pruebas nuevas).
- [x] 12. Backend: borrar `DemoUserFilter`, `/api/demo-users`, `?demoUser=`; renombrar `DemoPrincipal` a `AppPrincipal`. — verificado
  > Sin `DemoUserFilter`, `/api/demo-users` ni `?demoUser=`.
  Criterio: K6.
- [x] 13. Backend: mover las pruebas existentes a JWT con un `JwtDecoder` de pruebas. — verificado
  > `mvn verify`: 368 pruebas, 1 falla ajena (`ReportPdfApiTest`, abreviatura de mes según JDK; pasa sola).
  Criterio: K21 (366 pruebas del backend pasan, con las nuevas de la tarea 11).
- [x] 14. Backend: variables `OIDC_*` en el compose y el perfil `docker`; comprobar el arranque real contra Keycloak. — verificado
  > Backend en Docker contra Keycloak real (ver tarea 11).
  Criterio: K5 con un token real de Keycloak.
- [x] 15. Frontend: `keycloak-js`, servicio de sesión, interceptor con Bearer, inicializador, renovación, cierre de sesión. — verificado
  > `keycloak-js`, `AuthService`, interceptor Bearer, `config.json` por nginx; login real con enfermera y retorno a Pacientes.
  Criterio: K2, K9, K12.
- [x] 16. Frontend: quitar "Ver como", mostrar el aviso a médico y admin, y manejar 401/403. — verificado
  > Sin «Ver como»; aviso para médico/admin probado en prueba unitaria; «Cerrar sesión» probado en el navegador.
  Criterio: K3, K4, K7.
- [x] 17. Frontend: SSE con `fetch` y Bearer (`FetchEventSource`) en lugar de `EventSource` con `?demoUser=`. — verificado
  > `FetchEventSource` con 3 pruebas (Bearer, cortes, reconexión con Last-Event-ID). No se probó una ejecución de IA real.
  Criterio: K8.
- [x] 18. Frontend: migrar las pruebas (129) y sumar las nuevas. — verificado
  > `ng test`: 136 de 136.
  Criterio: K21 (frontend).
- [~] 19. Recorrido de punta a punta en Chrome con capturas de todas las pantallas, guardadas con su título en `docs/login-keycloak/capturas/`. — hecha a medias, sin capturas
  > Recorrido hecho: login de enfermera, Pacientes, cierre de sesión. No se guardaron capturas de todas las pantallas en `capturas/`.
  Criterio: K3, K4, K12, K15–K20, K25 (el juicio visual es de Andrés).
- [x] 20. README y documentos del repo: SPEC §7 (D23, D26), REQUISITOS, AGENTS.md, `docs/decisiones.md`. — verificado
  > README, SPEC §7, REQUISITOS, AGENTS.md, CLAUDE.md y `docs/decisiones.md` actualizados como [Propuesta]. La spec de esta carpeta no se tocó.
  Criterio: K23, K24.
- [~] 21. Revisar cada criterio K1–K25 con sí/no y `revisar-codigo`. — pendiente
  > K1–K25 en el reporte; `revisar-codigo` sin correr; K25 es juicio del usuario.
  Criterio: K1–K25 respondidos.

## El paso más riesgoso
**Tarea 13.** Migrar 366 pruebas sin perder lo que verifican. Se hace con un único ayudante de pruebas que reemplaza
la cabecera por un token de pruebas y un `JwtDecoder` de pruebas que pasa por la cadena real. Le sigue la **tarea 2**: si
el import del realm no acepta el flujo con la extensión, no hay login y el resto no se puede probar.

## Checkpoints
- Después de la tarea 3: se pide un token a Keycloak con `marcela.rojas` por la API.
- Después de la tarea 10: recorrido visual de las pantallas.
- Después de la tarea 14: el backend rechaza sin token y acepta un token real.
- Después de la tarea 18: inicio de sesión real en `:4200`.

## Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|---|---|---|
| 2 | El import no encuentra el autenticador o el flujo | Keycloak arranca sin el realm; el log muestra el error del import |
| 3 | El emisor del token cambia con el puerto | El backend responde 401 con un token válido |
| 11 | El backend intenta llegar a Keycloak al arrancar | El contenedor falla antes de que Keycloak esté sano |
| 13 | Una prueba pasa sin comprobar lo que antes comprobaba | Una prueba verde con el usuario equivocado |
| 15 | `keycloak-js` no renueva el token | 401 a los 5 minutos de usar la app |
| 17 | `fetch` corta el stream por el proxy | El informe no muestra pasos en vivo |

## Qué se entrega al final
Rama `feat/AFL_01_KEYCLOAK` con Keycloak, Mailpit, el tema `auditor`, la extensión, el backend y el frontend con login
real, y los documentos al día. Lo confirman K1–K25.

## Bitácora de conflictos con la spec
(vacía)
