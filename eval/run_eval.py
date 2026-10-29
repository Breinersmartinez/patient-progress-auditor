#!/usr/bin/env python3
"""Suite de evaluacion de Patient Progress Auditor (E14). Un comando, solo biblioteca estandar de Python 3.

    python3 eval/run_eval.py                      # el sistema real (la app levantada, con llave) y la solucion convencional
    python3 eval/run_eval.py --baseline-only      # solo la solucion convencional: no necesita la app ni llave
    python3 eval/run_eval.py --repeat 3 --only R1-reaparecen,S1-ejemplo-del-prompt

Llama a la app por HTTP como lo haria una persona (pide un informe, sigue la ejecucion, lee el resultado) y comprueba cada
caso con reglas verificables (eval/checks.py). Cada informe generado se RECHAZA al terminar con el motivo «Ejecucion de
evaluacion» para poder repetir la semana: usela contra una base desechable o acepte esos rechazados en la lista.
Mide las cuatro cifras de AGENTS.md: tasa de exito, costo por tarea, MEDIANA de latencia y recuperacion; ver eval/README.md.
"""
import argparse
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import baseline  # noqa: E402
import checks  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "backend/src/main/resources/seed/demo-data.json"
CASES = Path(__file__).resolve().parent / "cases"
FINAL = ("COMPLETADA", "INCOMPLETA", "FALLIDA")


# ---------------------------------------------------------------- datos

class Dataset:
    """Las entradas de cada paciente, normalizadas: de la semilla (sin servidor) o de la API (el sistema real)."""

    def __init__(self, patients):
        self.patients = patients  # alias -> {"id": ..., "entries": [baseline.entry]}

    @classmethod
    def from_seed(cls):
        seed = json.loads(SEED.read_text(encoding="utf-8"))
        patients = {}
        for p in seed["patients"]:
            entries = [baseline.entry(f"{p['alias']}|{e['date']}", date.fromisoformat(e["date"]), e["text"], e["events"])
                       for e in p["entries"]]
            patients[p["alias"]] = {"id": p["alias"], "entries": entries}
        return cls(patients)

    @classmethod
    def from_api(cls, http):
        patients = {}
        for p in http.get("/api/patients"):
            rows = http.get(f"/api/patients/{p['id']}/entries?order=asc&limit=500")
            entries = [baseline.entry(r["id"], date.fromisoformat(r["entryDate"]), r["body"],
                                      [{"symptom": s["symptom"], "intensity": s["intensity"], "occurrences": s.get("occurrences")}
                                       for s in r.get("symptoms", [])], r["structuringStatus"] == "ESTRUCTURADA") for r in rows]
            patients[p["alias"]] = {"id": p["id"], "entries": entries}
        return cls(patients)

    def entries(self, alias):
        return self.patients[alias]["entries"]


class Http:
    def __init__(self, base, user):
        self.base, self.user = base.rstrip("/"), user

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method, headers={
            "Content-Type": "application/json", "X-Demo-User": self.user or "", "User-Agent": "ppa-eval/1"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.status, json.loads(response.read() or b"null")
        except urllib.error.HTTPError as e:
            raw = e.read()
            return e.code, (json.loads(raw) if raw else None)

    def get(self, path):
        status, body = self.call("GET", path)
        if status != 200:
            raise RuntimeError(f"GET {path} -> {status} {body}")
        return body


def connect(base, user):
    http = Http(base, user)
    if not user:
        status, users = http.call("GET", "/api/demo-users")
        http.user = next(u["id"] for u in users if u["role"] == "ENFERMERIA")
    return http


# ---------------------------------------------------------------- informes

def holdout_cases(dataset, golden):
    """Casos de RESERVA: las semanas libres que ningun caso dorado usa, con lo esperado DERIVADO de los datos (no a mano).

    Sirven para ver si lo ajustado con los casos dorados se sostiene en otras semanas (riesgo de sobreajuste): comprueban
    validez, no la redaccion. Un sintoma sin dias en ninguna de las dos semanas no debe aparecer en una afirmacion."""
    seeded = {(r["alias"], r["weekStart"]) for r in json.loads((SEED.parent / "demo-reports.json").read_text(encoding="utf-8"))["reports"]}
    used = {(c["patient"], c["week"]) for c in golden}
    cases = []
    for alias, patient in sorted(dataset.patients.items()):
        weeks = sorted({baseline.monday(e["date"]) for e in patient["entries"]})
        for week in weeks:
            if (alias, week.isoformat()) in seeded or (alias, week.isoformat()) in used:
                continue
            a = baseline.analyze(patient["entries"], week)
            absent = [s for s in baseline.ORDER if s not in a["symptoms"] and s not in a["previous_days"]]
            cases.append({"id": f"H-{alias}-{week}", "patient": alias, "week": week.isoformat(), "expect": {
                "days": {s: v["days"] for s, v in a["symptoms"].items()}, "claims_for": [], "claims_not_for": absent, "cite_dates": {},
                "missing_dates": [d.isoformat() for d in a["missing_dates"]]}})
    return cases

def normalized_from_baseline(dataset, case):
    """El borrador de la plantilla, con la forma de un informe del sistema, para pasarlo por las mismas comprobaciones."""
    entries = dataset.entries(case["patient"])
    result = baseline.draft(entries, date.fromisoformat(case["week"]))
    a = result["analysis"]
    model = result["model"]
    content = dict(model)
    content["insufficient_data"] = [{"kind": "DIAS_SIN_REGISTRO", "dates": [d.isoformat() for d in a["missing_dates"]]}] if a["missing_dates"] else []
    content["out_of_scope"] = None
    return {"status": "PENDIENTE_REVISION", "content": content, "entry_dates": {e["id"]: e["date"].isoformat() for e in entries},
            "patient_entry_ids": {e["id"] for e in entries}}


def case_truth(dataset, case):
    return checks.truth_json(baseline.analyze(dataset.entries(case["patient"]), date.fromisoformat(case["week"])))


def run_report_baseline(dataset, case):
    started = time.perf_counter()
    report = normalized_from_baseline(dataset, case)
    millis = (time.perf_counter() - started) * 1000
    results = checks.evaluate(report, case, case_truth(dataset, case))
    return {"case": case["id"], "ok": all(ok for _, ok, _ in results), "checks": results, "latency_s": millis / 1000, "cost_usd": 0.0}


def run_report_system(http, dataset, case, timeout=240):
    alias, week = case["patient"], case["week"]
    body = {"patientId": dataset.patients[alias]["id"], "weekStart": week}
    if case.get("request"):
        body["request"] = case["request"]
    started = time.perf_counter()
    status, run = http.call("POST", "/api/runs", body)
    if status not in (200, 202):
        return {"case": case["id"], "ok": False, "error": f"POST /api/runs -> {status}: {(run or {}).get('message')}", "checks": []}
    run_id = run["id"]
    deadline = time.time() + timeout
    while run["status"] not in FINAL and time.time() < deadline:
        time.sleep(0.5)
        run = http.get(f"/api/runs/{run_id}")
    latency = time.perf_counter() - started
    steps = http.get(f"/api/runs/{run_id}/steps")
    record = {
        "case": case["id"], "run_id": run_id, "status": run["status"], "stop_reason": run.get("stopReason"),
        "provider": run.get("provider"), "fallback_used": run.get("fallbackUsed", False), "iterations": run.get("iterations"),
        "tokens": (run.get("tokensIn") or 0) + (run.get("tokensOut") or 0), "cost_usd": run.get("costUsd"), "latency_s": latency,
        "rejections": sum(1 for s in steps if s["type"] == "VALIDACION"),
        "provider_errors": sum(1 for s in steps if s["type"] == "LLAMADA_LLM" and s.get("error")),
        "fallbacks": sum(1 for s in steps if s["type"] == "FALLBACK"), "message": run.get("message"), "checks": [],
    }
    report_id = run.get("reportId")
    if not report_id:
        record["ok"] = False
        return record
    detail = http.get(f"/api/reports/{report_id}")
    entries = dataset.entries(alias)
    report = {"status": detail["report"]["status"], "content": detail["version"].get("content"),
              "entry_dates": {e["id"]: e["date"].isoformat() for e in entries}, "patient_entry_ids": {e["id"] for e in entries}}
    record["checks"] = checks.evaluate(report, case, case_truth(dataset, case))
    record["ok"] = all(ok for _, ok, _ in record["checks"])
    if detail["report"]["status"] == "PENDIENTE_REVISION":
        http.call("POST", f"/api/reports/{report_id}/reject", {"reason": "Ejecución de evaluación (eval/run_eval.py)"})
    return record


# ---------------------------------------------------------------- estructuracion

def run_structuring_baseline(case):
    started = time.perf_counter()
    events = baseline.structure(case["note"])
    millis = (time.perf_counter() - started) * 1000
    return {"case": case["id"], "latency_s": millis / 1000, "cost_usd": 0.0, "score": checks.score_structuring(events, case["expect"]), "proposed": events}


def run_structuring_system(http, patient_id, case):
    started = time.perf_counter()
    status, answer = http.call("POST", f"/api/patients/{patient_id}/entries/propose", {"body": case["note"]})
    latency = time.perf_counter() - started
    if status != 200:
        return {"case": case["id"], "error": f"propose -> {status}: {(answer or {}).get('message')}", "latency_s": latency,
                "score": checks.score_structuring([], case["expect"]), "failed": True}
    events = answer.get("events", [])
    record = {"case": case["id"], "latency_s": latency, "proposed": events, "provider": answer.get("provider"), "failure": answer.get("failure"),
              "failed": bool(answer.get("failure")), "score": checks.score_structuring(events, case["expect"]), "cost_usd": None, "tokens": None}
    if answer.get("runId"):
        run = http.get(f"/api/runs/{answer['runId']}")
        record["cost_usd"] = run.get("costUsd")
        record["tokens"] = (run.get("tokensIn") or 0) + (run.get("tokensOut") or 0)
    return record


# ---------------------------------------------------------------- cifras

def median(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else None


def report_figures(records, with_agent=True):
    n = len(records)
    ok = [r for r in records if r.get("ok")]
    figures = {
        "n": n, "success": len(ok), "success_rate": len(ok) / n if n else None,
        "median_latency_s": median([r.get("latency_s") for r in records]),
        "median_cost_usd": median([r.get("cost_usd") for r in records]),
        "mean_cost_usd": (sum(r["cost_usd"] for r in records if r.get("cost_usd") is not None) / n) if n else None,
    }
    if with_agent:
        completed = [r for r in records if r.get("status") == "COMPLETADA"]
        needed = [r for r in records if r.get("rejections") or r.get("provider_errors") or r.get("fallbacks")]
        figures.update({
            "completed": len(completed), "first_try": len([r for r in ok if not r.get("rejections")]),
            "failed_runs": len([r for r in records if r.get("status") == "FALLIDA"]),
            "incomplete_runs": len([r for r in records if r.get("status") == "INCOMPLETA"]),
            "median_tokens": median([r.get("tokens") for r in records]),
            "needed_recovery": len(needed), "recovered": len([r for r in needed if r.get("ok")]),
            "recovery_rate": (len([r for r in needed if r.get("ok")]) / len(needed)) if needed else None,
            "with_fallback": len([r for r in records if r.get("fallbacks")]),
        })
    return figures


def structuring_figures(records):
    n = len(records)
    exact = [r for r in records if r["score"]["exact"] and not r.get("failed")]
    return {
        "n": n, "exact": len(exact), "exact_rate": len(exact) / n if n else None,
        "false_positives": sum(len(r["score"]["false_positives"]) for r in records),
        "missed": sum(len(r["score"]["missed"]) for r in records),
        "details_ok": sum(r["score"]["details_ok"] for r in records), "details_asked": sum(r["score"]["details_asked"] for r in records),
        "failed_calls": len([r for r in records if r.get("failed")]),
        "median_latency_s": median([r.get("latency_s") for r in records]),
        "median_cost_usd": median([r.get("cost_usd") for r in records]),
    }


def fmt(value, unit=""):
    return "no medido" if value is None else (f"{value:.4f}{unit}" if isinstance(value, float) else f"{value}{unit}")


def print_report(title, figures, records, structured=False):
    print(f"\n== {title}")
    for key, value in figures.items():
        print(f"   {key}: {fmt(value)}")
    for r in records:
        failed = [name for name, ok, _ in r.get("checks", []) if not ok]
        mark = "OK " if (r.get("ok") if not structured else r["score"]["exact"]) else "FALLA"
        extra = r.get("error") or (f"{r['score']['false_positives']} de más, {r['score']['missed']} omitidos" if structured and not r["score"]["exact"] else "")
        print(f"   [{mark}] {r['case']}  {fmt(r.get('latency_s'), ' s')}  {', '.join(failed) if failed else ''} {extra}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", default="http://localhost:8080", help="URL de la API (por defecto http://localhost:8080)")
    parser.add_argument("--user", help="id del usuario de demostracion de enfermeria (por defecto el primero)")
    parser.add_argument("--repeat", type=int, default=2, help="veces que se corre cada caso con el sistema real (por defecto 2)")
    parser.add_argument("--pause", type=float, default=12, help="segundos entre ejecuciones, para no agotar la cuota (por defecto 12)")
    parser.add_argument("--only", help="ids de casos separados por coma")
    parser.add_argument("--suite", choices=["all", "reports", "structuring"], default="all")
    parser.add_argument("--holdout", action="store_true", help="ademas, las semanas libres que no son casos dorados (reserva contra el sobreajuste)")
    parser.add_argument("--baseline-only", action="store_true", help="solo la solucion convencional (sin app ni llave)")
    parser.add_argument("--system-only", action="store_true", help="solo el sistema real")
    parser.add_argument("--out", help="archivo de resultado (por defecto eval/results/AAAA-MM-DD-HHMM.json)")
    args = parser.parse_args()

    only = set(args.only.split(",")) if args.only else None
    report_cases = [c for c in json.loads((CASES / "reports.json").read_text(encoding="utf-8"))["cases"] if not only or c["id"] in only]
    struct_cases = [c for c in json.loads((CASES / "structuring.json").read_text(encoding="utf-8"))["cases"] if not only or c["id"] in only]
    started = datetime.now(timezone.utc)
    result = {"date": started.isoformat(timespec="seconds"), "repeat": args.repeat, "base": None if args.baseline_only else args.base}

    seed = Dataset.from_seed()
    if args.suite in ("all", "reports") and not args.system_only:
        records = [run_report_baseline(seed, c) for c in report_cases if c.get("expect", {}).get("baseline") != "no_aplica"]
        result["reports_baseline"] = {"figures": report_figures(records, with_agent=False), "records": records,
                                      "no_aplica": [c["id"] for c in report_cases if c["expect"].get("baseline") == "no_aplica"]}
        print_report("Informes: solución convencional (plantilla sin modelo)", result["reports_baseline"]["figures"], records)
    if args.suite in ("all", "structuring") and not args.system_only:
        records = [run_structuring_baseline(c) for c in struct_cases]
        result["structuring_baseline"] = {"figures": structuring_figures(records), "records": records}
        print_report("Estructuración: solución convencional (diccionario con negación)", result["structuring_baseline"]["figures"], records, True)

    if not args.baseline_only:
        http = connect(args.base, args.user)
        live = Dataset.from_api(http)
        if args.suite in ("all", "reports"):
            records = []
            for case in report_cases:
                for _ in range(args.repeat):
                    records.append(run_report_system(http, live, case))
                    time.sleep(args.pause)
            result["reports_system"] = {"figures": report_figures(records), "records": records}
            print_report("Informes: sistema real (agente)", result["reports_system"]["figures"], records)
        if args.holdout:
            records = []
            for case in holdout_cases(live, json.loads((CASES / "reports.json").read_text(encoding="utf-8"))["cases"]):
                records.append(run_report_system(http, live, case))
                time.sleep(args.pause)
            result["reports_holdout"] = {"figures": report_figures(records), "records": records}
            print_report("Informes de reserva (semanas que no son casos dorados)", result["reports_holdout"]["figures"], records)
        if args.suite in ("all", "structuring"):
            patient_id = live.patients["P-033"]["id"]
            records = []
            for case in struct_cases:
                for _ in range(args.repeat):
                    records.append(run_structuring_system(http, patient_id, case))
                    time.sleep(min(args.pause, 4))
            result["structuring_system"] = {"figures": structuring_figures(records), "records": records}
            print_report("Estructuración: sistema real (IA)", result["structuring_system"]["figures"], records, True)

    out = Path(args.out) if args.out else Path(__file__).resolve().parent / "results" / (started.astimezone().strftime("%Y-%m-%d-%H%M") + ".json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
    print(f"\nResultado guardado en {out}")


if __name__ == "__main__":
    main()
