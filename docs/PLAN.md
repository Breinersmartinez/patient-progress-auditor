# PLAN — Retiro de despliegue heredado

> **Estado:** en ejecución · **Fecha:** 2026-10-27

## Alcance del plan

Retirar la configuración de publicación heredada y las secciones de documentación que la describen. Cubre el arranque autosuficiente exigido por `docs/SPEC.md` (OBJ-13, RNF-08 y RNF-13) al conservar Docker Compose como única vía de ejecución. No modifica la SPEC ni los requisitos.

## Tareas

- [ ] 1. Retirar la publicación heredada y sus referencias directas
  - **Resultado:** no existe configuración de publicación heredada ni documentación que la describa; CI, seguridad y análisis de calidad continúan definidos.
  - **Responsable:** Breiner Martinez Muñoz.
  - **Dependencias:** ninguna.
  - **Estimación:** menos de media jornada.
  - **Criterio de aceptación (sí/no):** ¿La búsqueda de las referencias a los dos proveedores retirados devuelve cero resultados, los workflows restantes son YAML válido y las verificaciones de backend y frontend siguen pasando?

## Orden y dependencias

Solo hay una tarea: primero se retiran el manifiesto y el workflow, junto con sus referencias documentales directas; después se validan las configuraciones y compilaciones que permanecen.

## Primera tarea

**Tarea 1 — Retirar la publicación heredada y sus referencias directas.** Está lista para ejecutarse.

## Fuera de este ciclo

- Crear o modificar Docker Compose, `run.sh`, el arranque en frío o infraestructura nueva: no forman parte del retiro solicitado.
- Cambiar `docs/SPEC.md` o `docs/REQUISITOS.md`: no contienen referencias directas a los proveedores retirados.
