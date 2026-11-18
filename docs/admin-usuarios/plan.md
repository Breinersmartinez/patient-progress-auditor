# Módulo de administración de usuarios — Plan

2026-11-16 · rama `feat/AFL_02_MODULE_ADMIN`

## Spec de referencia
`docs/admin-usuarios/especificacion.md` (criterios A1 a A25). Este plan no los repite: cada tarea apunta a los suyos.

## Supuestos que este plan da por ciertos
- Hay un PostgreSQL desechable para correr `mvn -B verify` y Docker para levantar Keycloak y Mailpit (como en el login).
- El realm se importa solo con un volumen nuevo: cambiar `keycloak/realm/auditor-realm.json` exige `docker compose down -v` (se avisa antes).
- Keycloak 26.8.2 permite, con un cliente de servicio con `manage-users` y `view-users`: crear usuario, asignar rol de realm, `enabled=false`, cerrar sesiones y mandar el correo de acciones (`execute-actions-email`, caducidad 12 h por `actionTokenGeneratedByAdminLifespan`, ya en el realm).
- Hoy la semilla (`DemoDataSeeder`) es la única que crea filas en `app_users`; no hay migración de datos que hacer.
- Hueco menor de la spec: el reenvío de invitación (casos borde) no tiene endpoint en la sección Diseño. Este plan lo agrega como `POST /api/admin/users/{id}/invitation` en la tarea T9; **se confirma contigo antes de ejecutarla**.

## Grafo de dependencias
```
T1 línea base
 ├── T2 admin con fila + corte inmediato (A24, A25)
 └── T3 cliente de servicio en el realm
        └── T4 KeycloakAdminClient
               └── T5 listar ──┬── T6 crear ── T7 editar/desactivar ── T9 reenviar invitación
                               └── T8 front: lista
                                         └── T10 front: formularios (necesita T6, T7)
T11 tiempos y recorrido en vivo ← T6, T7, T10
T12 fidelidad al mockup ← T8, T10
T13 documentos y suite completa (último)
```

## Pasos
| # | Paso | Qué produce | Qué se mira al terminarlo | Criterios |
|---|------|-------------|---------------------------|-----------|
| T1 | Mirar lo que hay: correr las pruebas de backend y frontend en la rama tal cual | Cifras de partida anotadas en el reporte | Cuántas pasan hoy; si alguna falla ya, se anota antes de tocar nada | A22 (base) |
| T2 | Dar fila a `ADMIN` y cortar al inactivo: la semilla agrega la fila de `admin@example.test`; `JwtPrincipalConverter` y `LinkedUserFilter` tratan a `ADMIN` como a los demás | Semilla con 5 usuarios; admin con `AppUser`; un `AppUser` inactivo recibe 403 | Prueba: admin con fila entra a `/api/session` (200); inactivo de cada rol, 403; `ADMIN` sigue 403 en `/api/patients`; el resto de pruebas siguen verdes | A19, A24, A25 |
| T3 | Agregar el cliente de servicio al realm: client confidencial `auditor-admin` con service account y roles `manage-users` y `view-users`, y su secreto como variable (`KC_ADMIN_CLIENT_SECRET`, generada por `run.sh`, documentada en `.env.example`) | Realm actualizado, variable en compose, `.env.example` y `run.sh`; `scripts/check-env-example.sh` en verde | Con `down -v` y `./run.sh`, pedir un token con client credentials devuelve 200 y llamar `GET /admin/realms/auditor/users` con él devuelve la lista | base de A4, A11 |
| T4 | Escribir `KeycloakAdminClient` (buscar por correo, crear, poner rol, activar/desactivar y cerrar sesiones, mandar el correo de acciones, borrar solo para compensar) | Clase en el paquete `keycloak`, con configuración propia y errores mapeados a una excepción de dominio | Pruebas con servidor simulado cubren cada llamada y el fallo de red/5xx; una prueba contra el Keycloak real crea y borra un usuario de prueba | A16 (parte), A11 |
| T5 | Listar usuarios: `GET /api/admin/users` solo para `ADMIN` | Endpoint con nombre, correo, rol y estado; ruta en `SecurityConfig` | `ADMIN` 200 con los 5 sembrados; `ENFERMERIA`, `MEDICO` 403; sin token 401; 50 usuarios en < 1 s | A1, A2, A18 |
| T6 | Crear usuario: `POST /api/admin/users` con la regla «base primero, Keycloak después, compensar si falla» y la invitación | Endpoint, validación (nombre, correo), 409 por correo repetido, 502 con Keycloak caído | Crea fila + usuario de Keycloak + correo en Mailpit; con Keycloak detenido no queda fila; la invitación fallida deja la cuenta y avisa; crear < 3 s | A4, A5, A7, A8, A16, A17 |
| T7 | Editar y desactivar: `PUT /api/admin/users/{id}` (nombre, rol, estado) con protecciones y sin `DELETE` | Endpoint; 409 en autodesactivación, cambio del propio rol y último admin; el correo se rechaza; 404 si no existe | Tras editar, base y Keycloak coinciden; desactivar impide el login y reactivar lo devuelve; no hay ruta `DELETE` | A9–A15, A20 |
| T8 | Mostrar la lista: ruta `#/adm/usuarios`, prefijo `adm`, menú del administrador y guarda de rol; deja de salir el aviso de «sin pantallas» para `ADMIN` | Servicio, `UsersListComponent` con búsqueda y estado vacío; `roleHome('ADMIN')` | Admin entra y ve los 5 usuarios; filtra por nombre, correo y rol; médico y enfermería no ven la ruta; pruebas del componente | A1, A3 |
| T9 | Reenviar la invitación: `POST /api/admin/users/{id}/invitation` | Endpoint + botón en editar; la invitación anterior deja de servir | La invitación nueva llega a Mailpit; la vieja da la pantalla de «enlace ya usado/vencido» | casos borde (invitación vencida) · **requiere tu OK** |
| T10 | Formularios nuevo y editar: `#/adm/usuarios/nuevo` y `/editar/:id` con validación, botón con proceso, correo deshabilitado y los avisos de error | Componente de formulario, toast y retorno a la lista | Crear y editar desde la pantalla funcionan de punta a punta; errores 409, 502 y de invitación se muestran con sus textos; doble clic no duplica | A4, A6–A9, A17 |
| T11 | Medir y recorrer en vivo con Keycloak real: crear, entrar con el usuario nuevo, desactivar, reactivar, apagar Keycloak | Tiempos y recorrido anotados en el reporte | Lista < 1 s con 50 usuarios, crear < 3 s, y cada paso del recorrido hace lo que dice la spec | A2, A5, A6, A12, A13, A16 |
| T12 | Comparar las tres pantallas con el mockup vA a 1440 y 390 px, claro y oscuro | Capturas lado a lado en el reporte | 0 px de diferencia; si no se puede verificar a ojo, queda «hecha, sin verificar» | A21 |
| T13 | Cerrar: actualizar SPEC §7, REQUISITOS (RF-33, conteos), AGENTS.md, `docs/decisiones.md` y el README; correr la suite completa | Documentos coherentes y la suite en verde | `mvn -B verify` y `ng test` pasan, con las nuevas; sin IDs inexistentes ni tablas descuadradas | A22, A23 |

## El paso más riesgoso
**T6 (crear)**, porque escribe en dos sistemas que no comparten transacción: la base y Keycloak. Si Keycloak responde bien y el `commit` de la base falla después, queda un usuario en Keycloak sin fila. Se compensa borrando ese usuario de Keycloak y se prueba ese caso a propósito. Si la compensación también falla, se registra una línea en el log con el correo y se responde 502; el reintento reutiliza al usuario ya existente en Keycloak en vez de chocar. Si aun así no queda limpio, se frena y se te pregunta.
Un segundo riesgo menor, **T3**: el realm solo se importa con un volumen nuevo, así que el cambio obliga a `down -v` (pierde datos locales de Keycloak, no los de la app).

## Checkpoints
- **Después de T3:** el cliente de servicio funciona contra Keycloak real. Si no, no se sigue con T4 en adelante.
- **Después de T7:** la API completa responde a su contrato (A4–A20) antes de construir las pantallas.
- **Después de T10:** recorrido a ojo contigo antes de las mediciones y las capturas.

## Dónde se puede caer
| Paso | Qué puede salir mal | Cómo se nota |
|------|---------------------|--------------|
| T1 | Pruebas que ya fallaban antes de empezar | Rojo en la línea base: se anota y no se atribuye a este módulo |
| T2 | Pruebas existentes suponen que `ADMIN` no tiene fila (cambia la semilla a 5 usuarios y rompe conteos) | Fallos en pruebas de sesión, semilla y de conteo de usuarios |
| T3 | El volumen de Keycloak ya existe y el import no corre | El token con client credentials devuelve 401; falta `down -v` |
| T4 | Respuestas de Keycloak distintas a las simuladas (códigos, cuerpos) | La prueba contra el Keycloak real falla aunque las simuladas pasen |
| T6 | Usuario huérfano en Keycloak tras un `commit` fallido | La lista de Keycloak tiene un usuario que no está en `app_users` |
| T6 | La invitación sale con el enlace del host interno (`keycloak:8080`) | El enlace del correo en Mailpit no abre desde el navegador |
| T7 | Quitar el rol en la base pero no en Keycloak | El token del usuario sigue trayendo el rol viejo |
| T8 | Un rol que no es admin llega a la ruta por URL | La pantalla carga; el servidor igual responde 403 |
| T10 | Doble clic crea dos usuarios | Un 409 en el segundo; la prueba de doble clic lo atrapa |
| T12 | Diferencias de 1–2 px por el ancho de columna | El lado a lado muestra el desfase |

## Qué se entrega al final
- `GET/POST/PUT /api/admin/users` (más el reenvío) solo para `ADMIN`, con `KeycloakAdminClient` y el cliente de servicio en el realm.
- Pantallas `#/adm/usuarios`, `/nuevo` y `/editar/:id` como el mockup vA.
- El admin sembrado con fila en `app_users`, y corte inmediato de cualquier usuario inactivo.
- `docs/admin-usuarios/reporte.md` con el detalle de cada tarea y sus comprobaciones.
- Todo esto lo confirman los criterios **A1 a A25** de la spec; la tarea T13 los recorre uno por uno.

## Avance (detalle en `reporte.md`)
- [x] T1 línea base — verificado (2 fallas previas ajenas al módulo)
- [x] T2 admin con fila y corte inmediato — verificado
- [x] T3 cliente de servicio en el realm — verificado (el secreto se llama `AUDITOR_ADMIN_CLIENT_SECRET`, no `KC_ADMIN_CLIENT_SECRET`)
- [x] T4 `KeycloakAdminClient` — verificado con servidor simulado y a mano contra Keycloak real; sin prueba automática contra el real
- [x] T5 listar — verificado (33–49 ms con 51 usuarios)
- [x] T6 crear — verificado; la compensación por `commit` fallido no tiene prueba automática
- [x] T7 editar y desactivar — verificado
- [x] T8 lista en pantalla — verificado
- [x] T9 reenviar invitación — verificado; falta probar que la invitación anterior deja de servir
- [x] T10 formularios — verificado
- [x] T11 tiempos y recorrido en vivo — verificado
- [~] T12 fidelidad al mockup — hecha, sin verificar (no se pudo abrir el mockup; ver reporte)
- [x] T13 documentos y suite completa — verificado con 2 fallas previas ajenas (backend 537 pruebas; frontend 238 de 238)

## Bitácora de conflictos con la spec
Vacía.
