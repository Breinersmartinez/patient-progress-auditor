# Módulo de administración de usuarios — Reporte de ejecución

2026-11-16 · rama `feat/AFL_02_MODULE_ADMIN` · spec: `especificacion.md` · plan: `plan.md`

El usuario autorizó ejecutar todo el plan de corrido, aprobó el endpoint de reenvío (T9) y `docker compose down -v`, y pidió que las pruebas de interfaz se hagan en el navegador real.

---

## Tarea 1. Línea base

**Estado:** verificado

### Qué se hizo
Se corrieron las suites en la rama tal cual, antes de tocar nada (JDK 21 local, PostgreSQL desechable en el puerto 55432).

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| `mvn -B verify` en limpio | 502 pruebas, 2 fallan, 15 omitidas | anotar la cifra de partida | sí (anotada) |
| `ng test` en limpio | 218 pasan | anotar la cifra de partida | sí (anotada) |

### Notado pero no tocado
Las 2 fallas ya existían y no son de este módulo: `ReportPdfApiTest.aMonthlyReportPrintsTheEvolutionTableAndItsFourSourceReports:221` y `DictationNoDiskTest.anAudioOverTheLimitIsRejectedByItsDeclaredLength` (E/S en chunks). Probable causa: JDK 21 en vez de 17 (no se comprobó).

### Tarea siguiente
T2.

---

## Tarea 2. Admin con fila y corte inmediato

**Estado:** verificado

### Qué se hizo
La semilla agrega a `admin@example.test` («Administrador Auditor», id `…0005`, rol `ADMIN`). `JwtPrincipalConverter` busca el `AppUser` activo para todos los roles y `AppPrincipal.unlinked()` es `id == null`.

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Admin con fila entra a `/api/session` | 200, con id y nombre | 200 | sí |
| Admin inactivo / admin sin cuenta | 403 / 403 | 403 | sí |
| `ADMIN` en `/api/patients`, `/api/entries`, `/api/reports` | 403 en los tres | 403 | sí |
| Semilla | 1 `ADMIN` demo, 5 usuarios | 5 usuarios | sí |

### Tarea siguiente
T3.

---

## Tarea 3. Cliente de servicio en el realm

**Estado:** verificado

### Qué se hizo
Cliente confidencial `auditor-admin` con service account, secreto `AUDITOR_ADMIN_CLIENT_SECRET` (generado por `run.sh`, documentado en `.env.example`, pasado por el compose).

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Token por client credentials, tras `down -v` + `./run.sh` | 200 | 200 | sí |
| `GET …/admin/realms/auditor/users` con ese token | 200 | 200 | sí |
| `clients` y el realm `master` con ese token | 403 y 403 | sin acceso fuera de usuarios | sí |
| Secreto equivocado | 401 | 401 | sí |

### Lo que se aprendió
En Keycloak 26.8 no basta con los roles de `realm-management`: el token necesita el scope `kc-admin-api` (audiencia del realm), el scope `roles` (`resource_access`) y los *scope mappings* de los 4 roles. Sin ellos, 401 o 403. Quedó en `keycloak/realm/auditor-realm.json`.

### Tarea siguiente
T4.

---

## Tarea 4. `KeycloakAdminClient`

**Estado:** verificado (con un hueco)

### Qué se hizo
Cliente en el paquete `keycloak`: crear (con rol y borrado si el rol falla), buscar, actualizar (rol, `enabled`, cierre de sesiones), invitar (`execute-actions-email` con `UPDATE_PASSWORD`) y borrar solo para compensar. Errores 409 → `KeycloakUserExistsException`; el resto → `KeycloakUnavailableException`; lectura con 10 s de tope.

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Pruebas con servidor simulado | 13 de 13 | cada llamada y el fallo de red/5xx | sí |
| Prueba contra el Keycloak real que crea y borra | no hay prueba automática | prueba real | no se pudo verificar como prueba automática; se cubrió a mano en T11 (crear, rol, `enabled`, invitación contra el Keycloak real) |

### Tarea siguiente
T5.

---

## Tarea 5. Listar usuarios

**Estado:** verificado

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| `GET /api/admin/users` como `ADMIN` | 200 con los sembrados | 200 | sí |
| Como `ENFERMERIA` y `MEDICO` / sin token | 403 / 401 | 403 / 401 | sí |
| 51 usuarios, medido en el navegador (5 corridas) | 33, 38, 40, 41 y 49 ms | < 1 s | sí |

### Tarea siguiente
T6.

---

## Tarea 6. Crear usuario

**Estado:** verificado (con un hueco)

### Qué se hizo
`POST /api/admin/users`: validación (400 por campo), 409 por correo repetido sin importar mayúsculas, escritura en la base dentro de una transacción sin confirmar, llamada a Keycloak y `commit` solo si responde; la invitación va después del `commit` y su fallo solo marca `invitationSent=false`.

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Crear a Paula Mejía Prueba (médico) | fila en `app_users`, usuario en Keycloak con rol `MEDICO` y 1 correo en Mailpit | las tres cosas | sí |
| Crear con Keycloak detenido | 0 filas de ese correo | sin fila | sí |
| Duplicado `PAULA.prueba@…` | «Ya existe un usuario con ese correo.» | 409 | sí |
| Tiempo de crear (navegador) | 170 ms | < 3 s | sí |
| `commit` de la base que falla después de que Keycloak respondió (compensación) | sin prueba automática | usuario de Keycloak borrado | no se pudo verificar: el camino está escrito pero no probado |

### Notado pero no tocado
`ApiExceptionHandler` tiene un import fuera de orden (cosmético).

### Tarea siguiente
T7.

---

## Tarea 7. Editar y desactivar

**Estado:** verificado

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Cambiar rol de Paula a Enfermería | base `ENFERMERIA`, Keycloak `ENFERMERIA` (y `default-roles-auditor`) | coinciden | sí |
| Desactivar a Paula | login de Keycloak: «La cuenta está desactivada, contacte con el administrador» | sin acceso | sí |
| Reactivar a Paula | base `active = t`, Keycloak `enabled = true` | coinciden | sí |
| Sesión abierta de Paula tras desactivarla | su pestaña recibió «Su usuario no esta registrado en el sistema…» (403) al pedir la ficha | corte inmediato | sí |
| Pruebas de API (autodesactivación, propio rol, último admin, 404, sin `DELETE`) | 20 de 20 | todas | sí |

### Tarea siguiente
T8.

---

## Tarea 8. Pantalla de lista

**Estado:** verificado

### Paso a paso
1. Login como `admin@example.test`: entra directo a `#/adm/usuarios`, menú «Usuarios» y pie «Administrador Auditor · Administración».
2. La tabla muestra 5 usuarios con Nombre, Correo, Rol y Estado, y «5 resultados».
3. Tras crear a Paula aparece «Usuario creado» y la tabla pasa a 6.
4. Paula inactiva se ve con punto gris y «Inactivo».

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Médico entra a la app | ve sus pantallas, sin menú de administración | sin ruta de admin | sí |
| Pruebas del componente | pasan (parte de las 238 del frontend) | pasan | sí |
| Búsqueda en pantalla | solo con pruebas del componente; no se probó a mano | filtra por nombre, correo y rol | verificado solo por prueba automática |

### Tarea siguiente
T9.

---

## Tarea 9. Reenviar invitación

**Estado:** verificado (con un hueco)

### Paso a paso
1. Editar a Paula (activa): aparece «Reenviar invitación»; en Paula inactiva el botón no aparece.
2. Clic: toast «Invitación reenviada».
3. Mailpit pasa de 1 a 2 correos.

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Llega la invitación nueva | 2 correos en Mailpit | uno nuevo | sí |
| La invitación anterior deja de servir | no se probó | pantalla de «enlace usado/vencido» | no se pudo verificar |

### Tarea siguiente
T10.

---

## Tarea 10. Formularios

**Estado:** verificado

### Paso a paso
1. «Nuevo usuario» con el formulario vacío y clic en «Crear usuario»: «Escriba el nombre.» y «Escriba un correo válido.».
2. Con datos válidos el botón pasa a «Creando» y vuelve a la lista con «Usuario creado».
3. Editar: el correo está bloqueado; Estado es editable.
4. Ficha del propio admin: rol y estado bloqueados con «Es su propia cuenta: no puede cambiar su rol ni desactivarla.».
5. Con Keycloak detenido: el botón queda en «Creando» ~12 s y sale «No se pudo contactar el servicio de inicio de sesión. Intente de nuevo.», sin traza; el formulario conserva los datos.
6. Invitación nueva: el enlace del correo abre «Continúe con la acción» → «Cree su contraseña» → «Su contraseña quedó creada» → login de Paula entra a «Mis pacientes».

### Notado pero no tocado
El enlace de invitación dio error 500 en Keycloak cuando se abrió con la sesión del admin activa en el mismo navegador (`different_user_authenticated`). Es una limitación de Keycloak con dos usuarios en un navegador; con la sesión cerrada funciona. Conviene decirlo en el README.

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Crear y editar de punta a punta | funcionan | funcionan | sí |
| Errores 409 y 502 | con su texto | con su texto | sí |
| Doble clic no duplica | no se probó a mano (el botón se deshabilita durante «Creando») | sin duplicado | verificado solo por prueba del componente |
| Error de invitación (`invitationSent=false`) | solo con prueba del componente | aviso | verificado solo por prueba automática |

### Tarea siguiente
T11.

---

## Tarea 11. Tiempos y recorrido en vivo

**Estado:** verificado

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Lista con 51 usuarios | 33 a 49 ms | < 1 s | sí |
| Crear | 170 ms | < 3 s | sí |
| Editar / reenviar invitación | 135 ms / 75 ms | < 3 s | sí |
| Recorrido: crear → invitación → contraseña → entrar → editar → desactivar → reactivar → reenviar → Keycloak caído | cada paso hizo lo que dice la spec | igual | sí |

### Antes y después
Para medir inserté 45 usuarios sintéticos solo en la base y los borré al terminar (7 quedan: 5 sembrados, Paula y `tiempo.prueba@example.test`). Paula y `tiempo.prueba` también existen en Keycloak.

### Lo que se aprendió
Dos pestañas del mismo navegador comparten la sesión de Keycloak: para probar a otro usuario hay que cerrar sesión primero.

### Tarea siguiente
T12.

---

## Tarea 12. Fidelidad al mockup

**Estado:** sin verificar

### Qué se hizo
No se pudo comparar contra el mockup vA: Chrome no abre `file://` y el servidor HTTP local para servirlo fue denegado por el permiso del entorno. Sí se miró cada pantalla en el navegador a 1464 px en tema claro y se siguió la estructura del mockup, pero no se midió 0 px de diferencia ni se probó 390 px ni el tema oscuro.

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Lado a lado con el mockup a 1440 y 390 px, claro y oscuro | no se hizo | 0 px de diferencia | no se pudo verificar |

Haría falta que alguien abra `docs/07-mockups/mockups-vA/mockups/auditor.html` junto a la app y mire las tres pantallas, o permitir un servidor local para el mockup.

### Tarea siguiente
T13.

---

## Decisiones tomadas sin que nadie las pidiera
- Nombre del admin «Administrador Auditor» (igual que en Keycloak).
- Cliente de servicio `auditor-admin` con scopes `kc-admin-api` y `roles` y 4 roles de `realm-management`.
- Variable `AUDITOR_ADMIN_CLIENT_SECRET` (el plan decía `KC_ADMIN_CLIENT_SECRET`; Keycloak trata `KC_*` como configuración propia) y `APP_PUBLIC_URL`.
- Nombre y apellido para Keycloak: se parte el nombre completo; una sola palabra se repite como apellido.
- Tope de lectura a Keycloak: 10 s.
- La invitación usa la acción `UPDATE_PASSWORD`.
- «Estado» del formulario nuevo aparece deshabilitado en «Activo»; «Reenviar invitación» se oculta a usuarios inactivos.

---

## Tarea 13. Documentos y suite completa

**Estado:** verificado (con fallas previas ajenas)

### Qué se hizo
Se actualizaron SPEC §7 (D32), REQUISITOS (nota en RF-33; los conteos no cambian: RF-33 ya era M3), `AGENTS.md`, `docs/decisiones.md` y el README (fila del administrador).

### Comprobación
| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| `mvn -B verify` | 537 pruebas; 2 fallas de la línea base (`ReportPdfApiTest:221`, `DictationNoDiskTest`) | solo las de la línea base | sí |
| `ng test` | 238 de 238 | todas | sí |
| Falla extra en la primera corrida (`SessionEndpointsTest`, nombre «Laura Vega») | fila vieja en la base de pruebas; corregida ahí y la clase pasa (18/18) | 0 fallas nuevas | sí |

### Notado pero no tocado
- El README sigue diciendo que el médico ve «Su rol aún no tiene pantallas», lo cual ya no es cierto desde antes de este módulo.
- `scripts/check-env-example.sh` usa `grep -P` y falla en macOS; se verificó con un equivalente en Python.
- El sembrador no actualiza filas existentes: una base vieja conserva el nombre antiguo del admin.
