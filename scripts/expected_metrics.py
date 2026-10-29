#!/usr/bin/env python3
"""Calculo independiente de las metricas semanales a partir de la semilla (E7, RF-07).

Lee solo backend/src/main/resources/seed/demo-data.json y escribe el fixture que compara la prueba
MetricsServiceSeedTest. Esta hecho en otro lenguaje y con otro metodo que MetricsCalculator (tablas por dia y
semana en vez de listas de hechos) para que un error de interpretacion no se repita en los dos.

Definiciones (docs/decisiones.md, E7): un sintoma esta presente en una semana si tiene algun evento en algun dia.
Frente a la semana anterior W-1: persistente = en W y W-1; desaparecido = en W-1 y no en W; nuevo = en W, no en
W-1 y nunca antes; reaparecido = en W, no en W-1 y en alguna semana anterior. Si W-1 no tiene entradas, no es
comparable y nada se clasifica.

Uso: python3 scripts/expected_metrics.py > backend/src/test/resources/metrics/expected-metrics.json
"""
import json
import sys
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "backend/src/main/resources/seed/demo-data.json"
ORDER = ["LEVE", "MODERADO", "INTENSO"]
CATALOG = ["VOMITO", "DIARREA", "NAUSEAS", "FIEBRE", "MAREO", "DOLOR_CABEZA", "DOLOR_HUESO", "APETITO_BAJO",
           "ANIMO_DECAIDO"]


def monday(d):
    return d - timedelta(days=d.weekday())


def week_table(entries, start):
    """Todo lo que paso entre start y start+6, dia por dia."""
    days = {}
    for offset in range(7):
        day = start + timedelta(days=offset)
        days[day] = [e for e in entries if e["d"] == day]
    return days


def symptoms_of(days):
    found = {}
    for day, day_entries in days.items():
        for entry in day_entries:
            for ev in entry["events"]:
                found.setdefault(ev["symptom"], []).append((day, ev))
    return found


def main():
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    result = {}
    for patient in seed["patients"]:
        entries = [{"d": date.fromisoformat(e["date"]), "events": e["events"], "unstructured": False}
                   for e in patient["entries"]]
        first, last = monday(min(e["d"] for e in entries)), monday(max(e["d"] for e in entries))
        weeks = {}
        history = set()  # sintomas vistos en semanas anteriores a W-1
        week = first
        seen_by_week = {}
        while week <= last:
            days = week_table(entries, week)
            found = symptoms_of(days)
            seen_by_week[week] = set(found)
            prev_start = week - timedelta(days=7)
            prev_days = week_table(entries, prev_start)
            prev_found = symptoms_of(prev_days)
            prev_count = sum(len(v) for v in prev_days.values())
            earlier = set()
            for w, s in seen_by_week.items():
                if w < prev_start:
                    earlier |= s
            with_entry = [d for d, v in days.items() if v]
            row = {
                "entries": sum(len(v) for v in days.values()),
                "daysWithEntry": len(with_entry),
                "daysWithoutEntry": 7 - len(with_entry),
                "datesWithoutEntry": [d.isoformat() for d, v in days.items() if not v],
                "unstructuredEntries": 0,
                "totalEvents": sum(len(v) for v in found.values()),
                "symptoms": {},
                "comparable": prev_count > 0,
                "previousEntries": prev_count,
                "persistent": [], "disappeared": [], "new": [], "reappeared": [], "daysChange": {},
            }
            for sym in CATALOG:
                if sym not in found:
                    continue
                occ = [ev["occurrences"] for _, ev in found[sym] if ev["occurrences"] is not None]
                inten = [ORDER.index(ev["intensity"]) for _, ev in found[sym] if ev["intensity"] in ORDER]
                row["symptoms"][sym] = {
                    "days": len({d for d, _ in found[sym]}),
                    "dates": sorted({d.isoformat() for d, _ in found[sym]}),
                    "events": len(found[sym]),
                    "occurrences": sum(occ) if occ else None,
                    "maxIntensity": ORDER[max(inten)] if inten else None,
                }
            if row["comparable"]:
                for sym in CATALOG:
                    now, before = sym in found, sym in prev_found
                    if now and before:
                        row["persistent"].append(sym)
                    elif before and not now:
                        row["disappeared"].append(sym)
                    elif now and sym in earlier:
                        row["reappeared"].append(sym)
                    elif now:
                        row["new"].append(sym)
                    if now or before:
                        row["daysChange"][sym] = [len({d for d, _ in prev_found.get(sym, [])}),
                                                  len({d for d, _ in found.get(sym, [])})]
            weeks[week.isoformat()] = row
            week += timedelta(days=7)
        result[patient["alias"]] = weeks
    json.dump(result, sys.stdout, ensure_ascii=False, indent=1, sort_keys=True)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
