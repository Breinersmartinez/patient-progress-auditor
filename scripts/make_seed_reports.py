#!/usr/bin/env python3
"""Genera los cinco informes sembrados (D11, E14) con la plantilla sin modelo de eval/baseline.py.

Lee solo la semilla de entradas y escribe backend/src/main/resources/seed/demo-reports.json. Las cifras NO se guardan
aqui: el sembrador las calcula con el codigo al insertar el informe (`metrics`, `insufficient_data`), asi que no pueden
diferir de las metricas. Aqui solo van el texto, las citas (por fecha de entrada) y las cifras citadas con su clave.

Uso: python3 scripts/make_seed_reports.py > backend/src/main/resources/seed/demo-reports.json
"""
import json
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "eval"))
import baseline  # noqa: E402

SEED = ROOT / "backend/src/main/resources/seed/demo-data.json"
BOGOTA = timezone(timedelta(hours=-5))

# alias, lunes de la semana, estado: 4 aprobados de P-014 (permiten el mensual, RN-10) y 1 pendiente de P-021 (RN-01, RN-02)
REPORTS = [
    ("P-014", "2026-09-21", "APROBADO"), ("P-014", "2026-09-28", "APROBADO"),
    ("P-014", "2026-10-05", "APROBADO"), ("P-014", "2026-10-12", "APROBADO"),
    ("P-021", "2026-10-12", "PENDIENTE_REVISION"),
]


def stamp(day, hour):
    return datetime(day.year, day.month, day.day, hour, 0, tzinfo=BOGOTA).isoformat()


def main():
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    by_alias = {p["alias"]: p for p in seed["patients"]}
    out = []
    for alias, week_start, status in REPORTS:
        week = date.fromisoformat(week_start)
        entries = [baseline.entry(f"{alias}|{e['date']}", date.fromisoformat(e["date"]), e["text"], e["events"])
                   for e in by_alias[alias]["entries"]]
        model = baseline.draft(entries, week)["model"]
        end = week + timedelta(days=6)
        report = {
            "alias": alias, "weekStart": week_start, "status": status, "requestedBy": "camilo",
            "generatedAt": stamp(end + timedelta(days=1), 8),
            "summary": model["summary"],
            "claims": [{"claimKey": c["claim_key"], "text": c["text"], "criterion": c["criterion"],
                        "entryDates": [i.split("|")[1] for i in c["entry_ids"]], "figures": c["figures"]}
                       for c in model["claims"]],
            "changesVsPrevious": model["changes_vs_previous"], "recurringPatterns": model["recurring_patterns"],
        }
        if status == "APROBADO":
            report["reviewer"] = "marcela"
            report["reviewedAt"] = stamp(end + timedelta(days=1), 9)
        out.append(report)
    json.dump({"reports": out}, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
