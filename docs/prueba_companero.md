# Prueba en frío con una persona ajena al código

> Para qué sirve: el profesor entrega el README a una IA y ejecuta solo sus pasos; si la aplicación no levanta así, no revisa nada. Esta prueba comprueba lo mismo con una persona que **no escribió** el código y que **solo** lee el README. Si algo falla, el error está en el README o en el arranque, no en la persona.
> Se llena **antes de crear el tag `m2-entrega`**. Una fila vacía es una prueba que no se hizo.

## Reglas de la prueba

1. La persona no es de la pareja que escribió el código y no recibe ayuda. Quien observa **no habla**: solo anota.
2. Máquina con **solo Docker Compose v2** (sin Java, Node ni PostgreSQL instalados, o sin usarlos). Lo ideal es una máquina que nunca haya visto el proyecto.
3. Clon limpio del repositorio, del **tag o la rama de entrega**, no de una carpeta de trabajo: `git clone <enlace del repositorio> prueba && cd prueba`. Sin `.env`.
4. La persona abre el README y ejecuta, en orden, lo que dice. Nada más. Si necesita algo que el README no dice, eso es un fallo del README: se anota tal cual.
5. Se mide el tiempo con reloj, desde `git clone` hasta ver la interfaz con los pacientes cargados. **Meta: menos de 5 minutos con las imágenes ya descargadas, y se anota cuánto fue sin ellas.**
6. Se hacen las dos pasadas: **sin llave** y **con llave**.

## Registro

| Campo | Valor |
|---|---|
| Fecha y hora | |
| Persona que probó (nombre) | |
| Quien observó | |
| Sistema operativo y versión de Docker (`docker --version`, `docker compose version`) | |
| Commit o tag probado (`git rev-parse --short HEAD`) | |
| ¿Había imágenes de Docker descargadas de antes? (sí / no) | |
| Dirección del repositorio usada para clonar | |

### Pasada 1 — sin llave

| Pregunta | Respuesta |
|---|---|
| Minutos desde el clon hasta que `./run.sh` terminó | |
| ¿`./run.sh` terminó solo, sin pedir nada? (sí / no) | |
| ¿Imprimió el `AVISO` de llave faltante? (sí / no) | |
| ¿Los tres servicios quedaron en `healthy` (`docker compose ps`)? | |
| ¿Abrió la interfaz en el navegador y vio los pacientes? | |
| ¿Pudo ver un informe aprobado de P-014 y la traza de cómo se generó? | |
| ¿Qué le pareció confuso o faltante del README? | |

### Pasada 2 — con llave

| Pregunta | Respuesta |
|---|---|
| Cómo la recibió (su propia llave / la que envió el equipo) | |
| ¿`GROQ_API_KEY=<llave> ./run.sh` funcionó sin reconstruir todo? | |
| ¿`curl -s http://localhost:8080/api/llm/status` mostró el proveedor principal como configurado? | |
| ¿Pudo registrar un texto, ver los eventos propuestos y guardarlos con «Aprobar y guardar»? | |
| ¿Pudo generar el informe de P-014 (semana del 19-oct) y ver los pasos a medida que ocurrían? | |
| ¿Pudo editar, rechazar o aprobar el informe? | |
| Tiempo que tardó el informe, de pulsar el botón a verlo | |

### Qué falló

| # | Qué pasó (texto exacto del error, si hubo) | En qué paso del README | ¿Bloqueó la prueba? (sí / no) |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |

### Qué se cambió después

| # | Cambio hecho (archivo y qué) | Commit | ¿Se repitió la prueba y pasó? |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |

## Veredicto

| Criterio (sí / no) | Respuesta |
|---|---|
| ¿Quedó corriendo solo con lo que dice el README, sin ayuda? | |
| ¿En menos de 5 minutos con las imágenes descargadas? | |
| ¿Todo fallo quedó corregido y la prueba repetida? | |

**Firma de quien probó y fecha:**
