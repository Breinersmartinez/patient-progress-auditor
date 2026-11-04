## Qué cambia y por qué

<!-- Una o dos frases. Enlace al RF, RNF o D que lo pide, o a la etapa de docs/PLAN.md. -->

## Cómo se verificó

- [ ] Backend: `mvn -B verify` en verde (indique contra qué base)
- [ ] Frontend: `npx ng test --watch=false --browsers=ChromeHeadless` en verde
- [ ] `scripts/check-env-example.sh` en verde, si cambió una variable de entorno
- [ ] Solo documentos: sin referencias a IDs que no existen y sin tablas descuadradas

**Lo que no pude verificar:**

## Decisiones que tomé y nadie pidió

<!-- Se listan al terminar cada tarea (AGENTS.md). Si es una decisión técnica, también va en docs/decisiones.md. -->

## Lista de comprobación

- [ ] Sin llaves, `.env` ni datos reales de pacientes: el repositorio es público
- [ ] Si cambió una decisión: `docs/SPEC.md` §7, `docs/REQUISITOS.md`, diccionarios y `AGENTS.md` quedaron coherentes
- [ ] Si cambió el modelo, el prompt, una herramienta del agente o el proveedor: se corrió `eval/` y se anotó la cifra en `docs/decisiones/semana10.md`
- [ ] Si cambió el arranque: el README sigue siendo ejecutable por una IA con `./run.sh`
- [ ] Commits en Conventional Commits, en minúsculas e imperativo
