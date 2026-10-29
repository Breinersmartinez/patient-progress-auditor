# Semana 10 — Medición: línea base del Momento 2

> Fecha de las mediciones: **2026-10-29** (la línea base a la 01:30, la de después del ajuste a la 01:48 y la de reserva a la 02:00, hora de Bogotá). Suite: `eval/run_eval.py`; resultados completos en `eval/results/2026-10-29-*.json`; reglas y casos en `eval/`. Todo con **llave real** (Groq `openai/gpt-oss-120b` como principal y Gemini `gemini-3.5-flash-lite` como respaldo), contra el backend local y la semilla de D11. AGENTS.md pide estas cifras: tasa de éxito, costo por tarea, **mediana** de latencia y recuperación, comparadas contra la solución convencional.

## Método

- **Casos de informes (8, dorados):** cada uno es un paciente y una semana de la semilla, con lo esperado verificado **a mano** contra los datos (días por síntoma, fechas, qué síntomas **no** deben aparecer, días sin registro). Entre ellos: reaparición (P-014), reaparición y desaparición (P-021), persistencia, datos insuficientes (P-007, cuatro días sin registro), sin síntomas y solo negaciones (P-033), un síntoma que desaparece, la inyección de P-040 y el de **rechazo** (se pide un diagnóstico y una dosis). Se eligen semanas sin informe sembrado: solo puede haber un informe vigente por semana.
- **Qué cuenta como éxito:** el informe pasa **todas** las comprobaciones del caso (`eval/checks.py`): estado `PENDIENTE_REVISION`, cada afirmación cita registros reales del paciente, **cada cifra citada coincide con el cálculo independiente** de la evaluación, los síntomas que deben aparecer aparecen y destacan el cambio, ninguno de los que no ocurrieron aparece, los días sin registro se declaran, no hay diagnóstico ni la orden inyectada y, en el caso de rechazo, se declara fuera de alcance. No juzga la calidad de la redacción: eso no se midió.
- **Casos de estructuración (16):** una nota y los eventos que debe proponer. Incluyen negaciones, intensidad, veces, sin síntomas, un texto fuera del catálogo, la inyección y **cuatro paráfrasis** que un diccionario no conoce.
- **n:** cada caso de informe se corrió **2 veces** (n = 16) y cada de estructuración 2 veces (n = 32). Es poco: una diferencia de una o dos ejecuciones mueve la tasa varios puntos.
- **Latencia:** **mediana** (nunca el promedio) del tiempo desde el `POST` hasta ver el estado final, con el reloj del cliente y consulta cada 0,5 s. **Costo:** `costUsd` de la ejecución, a precio de lista del modelo aunque la capa gratuita no cobre. **Recuperación:** de las ejecuciones que tuvieron un rechazo del validador o un error del proveedor, cuántas terminaron pasando todas las comprobaciones.
- **Solución convencional (`eval/baseline.py`):** una plantilla que cuenta los días con cada síntoma, compara con la semana anterior y escribe frases fijas, y un diccionario de palabras con ventana de negación (corta en la oración). Pasa por las **mismas comprobaciones**. No recibe la solicitud en texto libre: el caso de rechazo **no le aplica**.
- **Las comprobaciones se prueban a sí mismas** (`python3 -m unittest discover -s eval`, 18 pruebas): la plantilla coincide con el cálculo independiente de `scripts/expected_metrics.py` en las 18 semanas, cada caso coincide con los datos, y alterar un informe (una cifra, una cita, un síntoma inventado, los días sin registro, una palabra de diagnóstico) hace fallar la comprobación que corresponde.

## Las cuatro cifras

### Informes semanales

| Cifra | Agente, línea base (01:30) | Agente tras el ajuste (01:48) | Convencional |
|---|---|---|---|
| **Tasa de éxito** | **11 de 16 (69 %)**; a la primera 6 de 16 (38 %) | **16 de 16 (100 %)**; a la primera 15 de 16 (94 %) | 7 de 7 aplicables (100 %) |
| **Costo por tarea** (mediana; media) | 0,0033 USD; 0,0035 | 0,0033 USD; 0,0032 | 0 |
| **Mediana de latencia** (mín–máx) | 17,8 s (4,9–69,5) | 4,5 s (3,2–12,7) | 0,2 ms |
| **Recuperación** | tras un rechazo del validador: 5 de 10; tras un error del proveedor o un cambio al respaldo: 9 de 14 | tras un rechazo del validador: 1 de 1; tras un error del proveedor: 16 de 16 | no aplica |
| Tokens por ejecución (mediana) | 10 130 | 7 566 | 0 |
| Informes `INCOMPLETO` | 5 (todas `SALIDA_INVALIDA`) | 0 | — |
| **Reserva** (6 semanas que no son casos dorados) | — | **6 de 6**; a la primera 5 de 6 | — |

### Estructuración de la entrada (D27)

| Cifra | IA (n = 32) | Convencional (n = 16) |
|---|---|---|
| Conjunto de síntomas idéntico al esperado | **32 de 32 (100 %)** | **12 de 16 (75 %)** |
| Síntomas de más / omitidos | 0 / 0 | 1 / 4 (las cuatro paráfrasis) |
| Intensidad y veces acertadas | 16 de 16 | 5 de 7 |
| Mediana de latencia | 1,10 s | 0,03 ms |
| Costo por llamada (mediana) | 0,0003 USD | 0 |

## Qué se concluye, y qué no

1. **La estructuración es donde el modelo se gana el lugar:** iguala al diccionario en todo lo que este conoce y lo supera en las cuatro paráfrasis («punzadas en la cabeza», «ganas de vomitar», «evacuó líquido tres veces», «desanimada»). Lo que el diccionario hace bien (negaciones simples) ya está resuelto con código; lo que no (el lenguaje de cada persona) es del modelo. Esto es lo que AGENTS.md pide: LLM para interpretar texto libre, código para lo exacto.
2. **En los informes, las cifras ya son del código** (RN-04) y la plantilla pasa las mismas reglas con costo cero y en milisegundos. **Esta suite no demuestra que el agente supere a la plantilla**: mide que su informe es válido (cifras, citas, sin invención, sin diagnóstico), no que esté mejor redactado ni que decida mejor qué mirar. Lo que el agente aporta (redactar el patrón en lenguaje claro, elegir cuántas semanas mirar, atender una solicitud) no está medido. Se declara, no se afirma.
3. **El costo es de lista**, y en la capa gratuita es 0 real. A este precio, un informe cuesta unos 0,003 USD y una estructuración unos 0,0003.

## Qué cambió entre las dos mediciones del agente, y cuánto vale la comparación

La línea base (69 %) mostró cinco informes `INCOMPLETO` por salida inválida, todos terminados en Gemini tras agotarse la cuota de Groq. Los rechazos del validador mostraron cuatro causas, y se corrigieron las causas, no el validador (no se relajó ninguna regla de fondo):

| Causa medida | Corrección |
|---|---|
| Claves de cifras mal escritas: `vs_previous.days.FIEBRE[1]`, `symptoms.X.days:4` y `vs_previous.days.FIEBRE` sin índice | El resolvedor entiende esas variaciones **de sintaxis**; el valor se sigue comparando con el cálculo del código y la clave se guarda canónica. El prompt dice que termina en entero (`.0` semana anterior, `.1` esta semana) |
| Fechas escritas como número («el 10 de octubre») sin respaldo | El prompt pide «el martes»; el rechazo dice «escriba el día de la semana» |
| `claims` vacío cuando no hubo síntomas | El prompt y el rechazo dicen cómo escribir la afirmación «no se registraron síntomas», con `total_events = 0` |
| Afirmación sobre algo que desapareció, sin cita | El prompt y el rechazo piden citar los registros de la semana anterior (leerlos con `get_week_entries`) |

**Comparación justa.** La cuota diaria de Groq se agotó durante el día: en la segunda medición **todas** las llamadas a Groq devolvieron límite de tasa (45 errores) y las 16 ejecuciones terminaron en Gemini. Por eso el 69 % → 100 % no es idéntico. Comparando solo lo que terminó en **Gemini** (el mismo modelo en las dos mediciones): **8 de 13 (62 %) antes, 16 de 16 (100 %) después**; a la primera, 3 de 13 (23 %) contra 15 de 16 (94 %); rechazos del validador, 15 contra 1. La latencia **no** es comparable: antes incluye las esperas por Groq antes de pasar a Gemini (la mediana de 17,8 s), y después Groq falla de inmediato.

## Riesgos y lo que no se midió

- **Sobreajuste.** Los cambios se hicieron mirando las fallas de estos mismos casos. Para acotarlo se corrió un conjunto de **reserva**: las 6 semanas libres que ningún caso dorado usa (`--holdout`), con lo esperado **derivado** de los datos (no verificado a mano) y comprobando validez: 6 de 6. Sigue siendo n pequeño.
- **Groq tras el ajuste: no medido.** Su cuota diaria (200 000 tokens por modelo) se agotó; antes del ajuste, las 3 ejecuciones que terminaron en Groq pasaron las tres. Hay que repetir la medición cuando se renueve la cuota. Por la misma razón, **la ruta normal de la demo (Groq primero) no tiene cifra posterior al ajuste**.
- **Todas las ejecuciones pasan por el respaldo** en las dos mediciones (13 de 16 y 16 de 16): con la capa gratuita, Groq se agota a mitad de la ejecución o del día. El sistema funciona porque el respaldo funciona; la cifra de recuperación del proveedor (16 de 16) es en rigor la del cambio a Gemini.
- **Límites gratuitos de Gemini: no publicados y no verificados.** Los 16 informes consecutivos en Gemini no fallaron por límite, pero eso no es una cifra de capacidad.
- **n = 16 y n = 32:** una ejecución más o menos cambia la tasa entre 3 y 6 puntos. La latencia depende del proveedor y de la hora.
- **La calidad del lenguaje no se midió** (ni la utilidad clínica): las reglas verifican validez. Tampoco se midió con varias personas a la vez ni con el modelo de Groq de 20B (que falló en E10).
- **El conjunto es sintético y chico** (5 pacientes, 18 semanas): las notas de los casos de estructuración las escribimos nosotros, parecidas a las de la semilla salvo las cuatro paráfrasis.
- **Latencia con resolución de 0,5 s** (consulta periódica) y con el cliente en la misma máquina que el servidor.

## Cómo repetirla

```bash
python3 eval/run_eval.py --base http://localhost:8080 --repeat 2 --holdout   # con la app arriba y la llave
python3 eval/run_eval.py --baseline-only                                       # solo la solución convencional
python3 -m unittest discover -s eval                                           # pruebas de la evaluación
```

Cada informe generado se **rechaza** al terminar para poder repetir la semana; úsela contra una base desechable. Volver a correrla cuando cambie el modelo, el prompt, la descripción de una herramienta o el proveedor.
