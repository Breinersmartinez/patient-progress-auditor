# Reporte — Inicio de sesión con Keycloak

Spec: `especificacion.md`. Plan: `plan.md`. Rama `feat/AFL_01_KEYCLOAK`, **sin commits todavía**.
Pruebas en un proyecto de Compose aparte (`ppa-test`, puertos 7071/8026/8085/4201) para no tocar
el `.env` ni los volúmenes del repo. Estado: **[Propuesta] pendiente de aprobación del equipo.**

## Resumen de lo comprobado

| Qué se miró | Valor obtenido | Valor esperado | ¿Cuadra? |
|---|---|---|---|
| Abrir `localhost:4201` sin sesión | Redirige al login Keycloak del tema `auditor` (captura en el navegador, modo oscuro) | Abre el login | sí |
| Login de enfermera (`marcela.rojas@example.test`) | Vuelve a la app, lista 5 pacientes, tarjeta «Marcela Rojas · Enfermería» | Entra y ve Pacientes | sí |
| «Cerrar sesión» | Vuelve al login | Vuelve al login | sí |
| API sin token / token basura | 401 / 401 | 401 | sí |
| API con token real: enfermera, médico, admin | `/api/session` 200 con su rol y permisos; `/api/patients` 200, 200, 403 (admin) | Por rol | sí |
| `mvn verify` (backend) | 368 pruebas, 1 falla, 14 omitidas | 0 fallas | **no** (ver abajo) |
| `ng test` (frontend) | 136 de 136 | 0 fallas | sí |
| K1: `./run.sh` en copia limpia (sin `.env`, proyecto `ppa-frio`) | Salió 0; 6 contenedores sanos; puertos 7070/8025 ocupados reasignados a 7072/8027; `config.json` correcto, realm `auditor` 200, token endpoint acepta el origen, API 401 sin token | Levanta sin pasos a mano | sí |
| Comparación al píxel del login y las pantallas de estado/alta/código | No se corrió | 0 px | **no se pudo verificar** |
| Flujo del código de recuperación por Mailpit en este repo | No se repitió aquí (sí en PGRP con el mismo código) | Funciona | **no se pudo verificar** |
| Capturas de todas las pantallas en `capturas/` | No se hicieron | Una por caso | **no hecho** |
| K25 (se ve bien) | — | Juicio del usuario | **pendiente del usuario** |

## Hallazgos y arreglos durante la ejecución

- **CORS del token (bug propio, arreglado):** tras el login, el navegador no podía canjear el código:
  Keycloak respondía `Invalid origin` porque `webOrigins: ["+"]` no resuelve el comodín de puerto de
  `http://localhost:*`. Se cambió a `["*"]` en `keycloak/realm/auditor-realm.json` y en el realm vivo.
  El client es público con PKCE y sin secreto. Un realm ya importado no se actualiza solo: hace falta
  `docker compose down -v` para que tome el cambio.
- **`package-lock.json`:** regenerado con el npm de Node 20 (el de la máquina es 11 y dejaba el lock
  fuera de sincronía para `npm ci` del Dockerfile). El diff es solo `keycloak-js`.
- **`run.sh` protege los volúmenes reales:** al probar en una copia, se negó a seguir porque el compose
  fija `name: patient-progress-auditor` y vio el volumen existente. Se probó con `COMPOSE_PROJECT_NAME`.
- **`ReportPdfApiTest`** (1 falla en la suite completa, pasa sola): el PDF imprime «21 sept» y la prueba
  espera «21 sep». Parece un efecto de la versión de JDK (21 aquí, 17 en CI), no de la autenticación.
  **No se confirmó** contra el commit original.

- **`localhost:4200` tapado por otro servidor (entorno, no código):** en esta máquina un `ng serve` de otro
  proyecto escucha en `[::1]:4200` y Chrome lo prefiere sobre Docker (`*:4200`), así que `localhost:4200`
  mostró otra app. Con `127.0.0.1:4200` el stack responde bien. `run.sh` no lo detecta (solo mira si Docker
  puede publicar el puerto). Hay que detener ese servidor o usar otro `FRONTEND_PORT`. No se probó el login
  en el navegador en el arranque en frío por esto; sí se probó con el stack `ppa-test` (puerto 4201).

## Decisiones tomadas por defecto (no confirmadas por el usuario)

- Reenvío del código bloqueado 2 min aunque se anule (opción A). La fila «Pendiente» de la spec sigue
  abierta: no se editó la spec.
- `webOrigins: ["*"]` (ver arriba).

## No tocado / notado

- `docs/DICCIONARIO_DATOS_M2.md` y `docs/PLAN.md` aún hablan del usuario de demostración elegido.
- La columna `is_demo` y la semilla de 4 usuarios siguen: el backend enlaza por correo con esos usuarios.
- Quedan en este reporte las tareas 6–10, 19 y 21 como **hechas sin verificar / pendientes** (ver plan).

## Limpieza de la infraestructura de prueba

Hecha: `ppa-test`, `ppa-frio` y `ppa-testdb` bajados y borrados.
