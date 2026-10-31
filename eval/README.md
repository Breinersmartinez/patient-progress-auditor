# Evaluación: casos dorados y las cuatro cifras

Un comando corre todo contra la aplicación levantada y contra la solución convencional (sin modelo):

```bash
python3 eval/run_eval.py                     # el sistema real (necesita la app arriba y la llave) y la solución convencional
python3 eval/run_eval.py --baseline-only     # solo la solución convencional: no necesita app ni llave
python3 eval/run_eval.py --repeat 3 --only R1-reaparecen,S1-ejemplo-del-prompt
python3 eval/run_eval.py --suite monthly      # los casos del informe mensual (solución convencional y sistema real)
python3 -m unittest discover -s eval         # pruebas de la propia evaluación (sin app ni llave)
```

Solo biblioteca estándar de Python 3: no se instala nada. Por defecto apunta a `http://localhost:8080` (`--base` lo cambia; con
`./run.sh` el puerto puede ser otro, y `.env` dice cuál). El resultado queda en `eval/results/AAAA-MM-DD-HHMM.json`.

**Cuidado:** cada informe generado se **rechaza** al terminar, con el motivo «Ejecución de evaluación», para poder repetir la
semana (solo puede haber un informe vigente por paciente y semana). Úsela contra una base desechable o acepte esos rechazados
en la lista. Gasta cuota del proveedor: unas 16 ejecuciones del agente semanal y 6 del mensual (≈ 12 000 tokens cada una) y 32 llamadas cortas de
estructuración con los valores por defecto.

## Qué hay

| Archivo | Qué es |
|---|---|
| `cases/reports.json` | 8 casos dorados de informes. Lo esperado (días por síntoma, fechas, qué síntomas **no** aparecen) se verificó a mano contra la semilla. Uno de rechazo (pedir un diagnóstico y una dosis), uno de inyección (P-040), uno de datos insuficientes (P-007), uno sin síntomas (P-033) y varios de cambios entre semanas |
| `cases/monthly.json` y `monthly.py` | 3 casos dorados del **informe mensual** (E13): P-014 del 21-sep al 18-oct, con los días por síntoma de cada semana verificados a mano; P-021, que no tiene las cuatro semanas aprobadas (el servidor debe negarse y nombrar cuáles faltan); y el mismo mes con una petición de diagnóstico. `monthly.py` trae sus comprobaciones y su solución convencional. Se corren con `--suite monthly`; piden el mensual de las cuatro semanas que la semilla ya trae aprobadas, así que no cambie ni apruebe esos informes semanales antes |
| `cases/structuring.json` | 28 notas con los eventos que se deben proponer: negaciones, intensidad, veces, sin síntomas, inyección, cuatro paráfrasis que un diccionario no conoce y, desde E18, ocho con síntomas **fuera del catálogo** (S12 y S17 a S24: uno y dos abiertos, con intensidad y veces, la negación de uno, un diagnóstico con medicamento y signos vitales, una inyección que pide registrar un síntoma inventado) y, desde E19, cuatro notas mal escritas (S25 a S28) para el **texto corregido**: `corrected_contains` (fragmentos que deben estar; las tildes importan) y `corrected_forbidden` (la falta original, que ya no debe estar), y en todos los casos que el texto llegue, empiece con mayúscula, termine en punto y conserve los números y las negaciones. Un esperado con `contains` es un síntoma abierto: se acepta cualquier síntoma propuesto, no del catálogo, cuyo código contenga esa palabra |
| `checks.py` | Las comprobaciones por caso: reglas verificables (estado, citas, cifras contra el cálculo independiente, síntomas que deben y no deben aparecer, días sin registro declarados, sin diagnóstico ni orden inyectada, fuera de alcance declarado). No usa la regla del servidor para medirlo |
| `baseline.py` | La solución convencional: plantilla que cuenta días con síntoma y compara con la semana anterior, y un diccionario con ventana de negación para estructurar. Es también la fuente de los informes sembrados (`scripts/make_seed_reports.py`) |
| `run_eval.py` | El ejecutor: pide, sigue la ejecución, lee el informe, comprueba, calcula las cifras |
| `test_eval.py` | Pruebas de la evaluación: la plantilla coincide con el cálculo independiente de `scripts/expected_metrics.py` en las 18 semanas, cada caso coincide con los datos, y cada alteración de un informe es detectada |

## Las cuatro cifras (AGENTS.md, «Medición»)

| Cifra | Cómo se mide |
|---|---|
| Tasa de éxito | Ejecuciones cuyo informe **pasa todas las comprobaciones** del caso ÷ ejecuciones. Se reporta también cuántas terminaron `COMPLETADA` y cuántas pasaron sin un solo rechazo del validador (a la primera) |
| Costo por tarea | `costUsd` de la ejecución (tokens × precio de lista del modelo, aunque la capa gratuita no cobre), mediana y media |
| Latencia | **Mediana** (nunca el promedio) del tiempo desde el `POST` hasta el estado final, con el reloj del cliente |
| Recuperación | De las ejecuciones que tuvieron un rechazo del validador, un error del proveedor o un cambio al proveedor de respaldo, cuántas terminaron pasando todas las comprobaciones. Sin ninguna, dice «no medido» |

Las cifras con fecha, método, n y la comparación con la solución convencional están en `docs/decisiones/semana10.md`. Vuelva a
correr la suite cada vez que cambie el modelo, el prompt del sistema, la descripción de una herramienta o el proveedor.
