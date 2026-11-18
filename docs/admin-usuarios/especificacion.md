# Módulo de administración de usuarios — Especificación

2026-11-16 · Andrés (rama `feat/AFL_02_MODULE_ADMIN`, sale de `feat/AFL_01_KEYCLOAK`)

> Desarrolla RF-33 de `docs/REQUISITOS.md` (hito M3) sobre el login con Keycloak de `docs/login-keycloak/`.
> Mientras el equipo no apruebe en `docs/SPEC.md` §7 el login con Keycloak, esto es **[Propuesta]**. Diseño de
> pantallas: mockup vA, `docs/07-mockups/mockups-vA/mockups/auditor.fuente.html` (sección «administrador»).

## 01. Resumen
Un administrador inicia sesión y ve la lista de personas con acceso al sistema. Desde ahí crea un usuario (que
recibe una invitación por correo para elegir su contraseña), edita su nombre, su rol y su estado, o lo desactiva
sin borrarlo. El administrador gestiona cuentas, nunca datos clínicos.

## 02. Objetivos
- **< 1 s** — la lista de usuarios carga en menos de 1 segundo con 50 usuarios, medido en el backend local.
- **< 3 s** — crear un usuario (incluye la llamada a Keycloak y el envío de la invitación) responde en menos de 3
  segundos.
- **0 respuestas 200** — de `/api/patients`, entradas e informes al rol `ADMIN`: todas devuelven 403 (criterio de
  RF-33).
- **0 respuestas 200** — de `/api/admin/**` a los roles `ENFERMERIA` y `MEDICO`, y a peticiones sin token (403 y 401).
- **0 inconsistencias** — tras cada crear, editar o desactivar, `app_users` y el usuario de Keycloak coinciden en
  nombre, rol y estado; si una de las dos escrituras falla, ninguna queda aplicada.
- **0 px de diferencia** — de las tres pantallas (lista, nuevo, editar) contra el mockup vA, a 1440 y 390 px, en
  claro y oscuro.
- **0 pruebas rotas** — las existentes del backend y del frontend siguen pasando.

## 03. Fuera de alcance
- **Permisos por rol (RF-34)**: descartado por el equipo; los roles ya tienen definido lo que pueden y no se
  agregan roles.
- **Trazas del agente**: son otra pantalla del admin en el mockup; no se pidieron.
- **Borrar usuarios**: solo se desactivan; un usuario con historial no se elimina nunca.
- **Cambiar el correo** de un usuario existente: para otro correo se crea un usuario nuevo.
- **Que el admin vea o defina contraseñas**, y «olvidé mi contraseña» del admin (ya lo cubre el login).
- **Más de un rol por usuario** y crear roles nuevos.
- **Paginación y filtros por columna** de la lista: son decenas de usuarios, solo hay búsqueda de texto.
- **Auditoría de quién cambió qué** en la gestión de usuarios (RF-35 quedó fuera del Momento 2).
- **Eliminar `app_users` o hacer de Keycloak la única fuente de identidad**: es una refactorización de las 10 claves
  foráneas de autoría (D12); va en una rama y spec propias, no en esta.
- **Cambiar el diseño del mockup**; correo real (SMTP): la invitación llega a Mailpit.

## 04. Diseño

```
 Admin (Angular, #/adm/usuarios)
   |  Bearer <JWT con rol ADMIN>
   v
+----------------------+  1. valida rol  +---------------------+
| UserAdminController  | --------------> | SecurityConfig      |
| /api/admin/users     |                 | (hasRole ADMIN)     |
+----------------------+                 +---------------------+
   |  2. reglas (correo único, protecciones)
   v
+----------------------+  3. escribe     +---------------------+
| UserAdminService     | --------------> | app_users + roles   |
| (@Transactional)     |                 | (PostgreSQL)        |
+----------------------+                 +---------------------+
   |  4. mismo cambio en Keycloak (API Admin)
   v
+----------------------+  5. invitación  +---------+
| KeycloakAdminClient  | --------------> | Mailpit |
| (crear/editar/       |  (solo al crear)| :8025   |
|  desactivar)         |                 +---------+
+----------------------+
   6. si el paso 4 falla: se revierte la transacción del paso 3 y se responde 502 sin dejar nada a medias
```

**Orden crear:** primero la transacción en la base (sin confirmarla), luego Keycloak; solo si Keycloak responde
bien se confirma. Si la base falla antes, Keycloak no se toca. Si falla el envío de la invitación, la cuenta se
crea igual y la pantalla avisa «Usuario creado, pero no se pudo enviar la invitación» con la opción de reenviarla.

**API (solo `ADMIN`)**
- `GET /api/admin/users` — lista (nombre, correo, rol, estado).
- `POST /api/admin/users` — `{nombre, correo, rol, activo?}`; por defecto crea activo y manda la invitación. Con `activo: false` crea la cuenta ya desactivada (también en Keycloak) y no manda invitación (cambio del 2026-11-16, ver Decisiones).
- `PUT /api/admin/users/{id}` — `{nombre, rol, estado}`; el correo no se acepta.
- `POST /api/admin/users/{id}/invitation` — reenvía la invitación (la anterior deja de servir).
- Desactivar y reactivar son `PUT` con `estado`. No hay `DELETE`.

**Efecto de desactivar:** `app_users.active=false`, `enabled=false` en Keycloak y cierre de sus sesiones. Su
historial (entradas, informes) queda intacto con su nombre.

**El admin también tiene fila en `app_users`** (con rol `ADMIN`): así la lista sale de una sola tabla y valen las
protecciones y el corte inmediato. La semilla agrega la fila de `admin@example.test`, y `JwtPrincipalConverter` y
`LinkedUserFilter` dejan de tratar a `ADMIN` como un caso sin `AppUser`: un token de `ADMIN` sin fila activa recibe 403,
como ya pasa con enfermería y médico. La fila solo lo identifica; `ADMIN` sigue sin acceso a datos clínicos.

**Pantallas** (mockup vA, rutas con prefijo `adm`): `#/adm/usuarios` (lista con búsqueda por nombre, correo o rol
y estado vacío), `#/adm/usuarios/nuevo` y `#/adm/usuarios/editar/:id` (nombre, correo, rol, estado y botón con
proceso). El correo aparece deshabilitado al editar. Validación en la pantalla y de nuevo en el servidor.

**Por qué el backend llama a Keycloak:** una sola pantalla y un solo flujo para el admin, y el rol del token y la
fila de `app_users` no se separan. Lo que no muestra el diagrama: el backend necesita un cliente de servicio en el
realm con permiso solo de gestionar usuarios (no es el admin de la consola).

## 05. Casos borde
- **Correo ya existente** (en la base o en Keycloak): 409 con «Ya existe un usuario con ese correo»; no se crea
  nada.
- **Nombre vacío o correo sin formato válido:** error bajo el campo y no se llama al servidor; el servidor también
  responde 400.
- **El admin se desactiva a sí mismo o se quita su rol:** 409 con «No puede desactivar su propia cuenta» o «No
  puede cambiar su propio rol».
- **Desactivar o quitar el rol al último administrador activo:** 409 con «Debe quedar al menos un administrador
  activo».
- **Keycloak caído o con error al crear, editar o desactivar:** 502 con «No se pudo contactar el servicio de
  inicio de sesión. Intente de nuevo.»; la base queda como estaba y no hay stack trace.
- **Falla solo el envío de la invitación:** la cuenta existe; aviso en pantalla y reenvío posible desde editar.
- **Invitación vencida (12 h) o ya usada:** el admin puede reenviar una nueva desde editar; la anterior deja de
  servir.
- **Lista vacía:** «Aún no hay usuarios. Cree el primero.» con el botón «Nuevo usuario».
- **Búsqueda sin resultados:** el estado vacío de la lista con el texto buscado.
- **Doble clic en «Crear usuario»:** el botón queda en proceso («Creando») y no manda dos peticiones.
- **Editar un usuario que ya no existe:** 404 y el front vuelve a la lista con un aviso.
- **Usuario desactivado intenta entrar:** Keycloak lo rechaza («cuenta desactivada», pantalla ya diseñada).
- **Cambio de rol de alguien con sesión abierta:** vale desde que renueva su token; desactivar corta al instante
  (el backend rechaza con 403 a un `AppUser` inactivo).
- **Usuarios sembrados:** (incluido el admin, que gana su fila) aparecen en la lista; sus correos terminan en `@example.test`, no `@demo.local`.
- **Un `ADMIN` intenta ver pacientes por URL o por API:** 403; la interfaz no le muestra esas rutas.

## 06. Criterios de aceptación
| Criterio | Respuesta |
|---|---|
| A1. Un admin entra a `#/adm/usuarios` y ve la lista con nombre, correo, rol y estado de los usuarios sembrados | sí / no |
| A2. La lista de 50 usuarios responde en menos de 1 segundo | sí / no |
| A3. La búsqueda filtra por nombre, correo y rol | sí / no |
| A4. «Nuevo usuario» con datos válidos crea la fila, el usuario en Keycloak y una invitación en Mailpit con enlace de 12 h | sí / no |
| A5. Crear un usuario responde en menos de 3 segundos | sí / no |
| A6. El usuario creado entra con la contraseña que eligió y llega al tablero de su rol | sí / no |
| A7. Un correo repetido muestra «Ya existe un usuario con ese correo» y no crea nada | sí / no |
| A8. Un correo sin formato o un nombre vacío se rechazan en pantalla y en el servidor (400) | sí / no |
| A9. En editar se puede cambiar nombre, rol y estado y el correo está deshabilitado | sí / no |
| A10. El servidor ignora o rechaza un cambio de correo enviado por API | sí / no |
| A11. Tras editar, `app_users` y Keycloak coinciden en nombre, rol y estado | sí / no |
| A12. Desactivar a un usuario le impide iniciar sesión y conserva su historial | sí / no |
| A13. Reactivar a un usuario desactivado le devuelve el acceso | sí / no |
| A14. El admin no puede desactivarse ni cambiar su propio rol (409) | sí / no |
| A15. No se puede desactivar ni quitar el rol al último administrador activo (409) | sí / no |
| A16. Con Keycloak detenido, crear responde 502 con mensaje claro y no deja fila en `app_users` | sí / no |
| A17. Si falla solo la invitación, la cuenta existe y la pantalla lo avisa | sí / no |
| A18. `ENFERMERIA`, `MEDICO` y peticiones sin token reciben 403 y 401 en `/api/admin/**` | sí / no |
| A19. `ADMIN` recibe 403 en `/api/patients`, entradas e informes | sí / no |
| A20. No existe ninguna ruta `DELETE` de usuarios | sí / no |
| A21. Las tres pantallas coinciden con el mockup vA, 0 px, a 1440 y 390, en claro y oscuro | sí / no |
| A22. Las pruebas existentes del backend y del frontend pasan, más las nuevas de este módulo | sí / no |
| A23. SPEC §7, REQUISITOS (RF-33), AGENTS.md y `docs/decisiones.md` reflejan el cambio | sí / no |
| A24. El admin sembrado `admin@example.test` aparece en la lista y sigue pudiendo entrar | sí / no |
| A25. Un `AppUser` desactivado (de cualquier rol) con token aún vigente recibe 403 en su siguiente petición | sí / no |

A21 se compara con capturas contra el mockup; si no se puede verificar a ojo, queda «hecha, sin verificar».

## 07. Decisiones
| Fecha | Se eligió | Se descartó | Por qué |
|---|---|---|---|
| 2026-11-16 | El backend llama a la API Admin de Keycloak al crear, editar y desactivar | Gestionar solo `app_users` y dejar Keycloak a mano; Keycloak como única fuente | Un solo flujo para el admin y sin dos pasos manuales; la segunda opción no corta el login al desactivar y la tercera cambia cómo se enlaza el autor (D12). Lo eligió Andrés. |
| 2026-11-16 | Alcance: solo Usuarios (RF-33) | Sumar Permisos (RF-34) y Trazas | El equipo descartó permisos: los roles ya están definidos y no se agregan más. Trazas no se pidió. |
| 2026-11-16 | Editables: nombre, rol y estado; correo fijo | Editar también el correo | El correo enlaza con Keycloak y con el autor de las acciones históricas (D12); cambiarlo rompe esa traza. |
| 2026-11-16 | Desactivar, no borrar | Borrado físico | El historial clínico firma con el usuario (D12); borrarlo dejaría acciones sin autor. |
| 2026-11-16 | Un rol por usuario (Enfermería, Médico, Administrador) | Varios roles | Es lo que muestra el mockup y lo que ya espera el login. |
| 2026-11-16 | Invitación por correo con enlace de 12 h; el admin no define contraseñas | Contraseña inicial puesta por el admin | Ya está diseñado en el login (K19) y el admin nunca conoce una contraseña. |
| 2026-11-16 | Protecciones: no autodesactivarse, no cambiarse el rol, siempre un admin activo | Sin protecciones | Evita dejar el sistema sin quién lo administre. |
| 2026-11-16 | Si Keycloak falla, se revierte la base y se responde 502 | Dejar la fila creada y reintentar luego | Evita usuarios que existen en un lugar y no en el otro (objetivo de 0 inconsistencias). |
| 2026-11-16 | Umbrales: lista < 1 s y crear < 3 s | b) < 2 s / < 5 s | Elegido por Andrés. |
| 2026-11-16 | Desactivar corta el acceso al instante: el backend comprueba `app_users.active` en cada petición, además de `enabled=false` y cierre de sesiones en Keycloak | Que valga desde que expira el token actual | Desactivar a alguien con acceso a datos clínicos no debería esperar minutos; el backend ya resuelve el `AppUser` en cada petición. Confirmado por Andrés. |
| 2026-11-16 | El admin también tiene fila en `app_users`; se mantiene la tabla | Admins solo en Keycloak (la lista junta dos fuentes y llama a Keycloak en cada carga); eliminar `app_users` y dejar a Keycloak como única fuente | Una sola tabla hace posibles las protecciones y el objetivo de lista < 1 s. Eliminarla toca 10 claves foráneas, 9 clases y la semilla (477 + 194 pruebas): Andrés lo planteó y decidió dejarlo para una rama aparte. Cambia la regla del login «ADMIN no tiene AppUser». |
| 2026-11-16 | Endpoint `POST /api/admin/users/{id}/invitation` para reenviar la invitación | Pedir al admin que cree el usuario de nuevo | La spec ya promete el reenvío en los casos borde pero no tenía endpoint. Confirmado por Andrés al aprobar el plan. |
| 2026-11-16 (cambio) | El administrador puede crear un usuario ya inactivo: el campo Estado queda habilitado en «Nuevo usuario»; inactivo se crea con `enabled=false` en Keycloak y sin invitación (al activarlo se usa «Reenviar invitación») | Antes: siempre activo, Estado bloqueado en «Activo» (decisión no pedida, tomada para calzar el mockup) | Lo pidió el dueño; una cuenta inactiva no puede entrar, así que mandarle invitación no sirve | 
| 2026-11-16 (criterio) | A26: crear con Estado «Inactivo» deja la fila inactiva, el usuario deshabilitado en Keycloak y 0 correos — verificado en el navegador | | | 
