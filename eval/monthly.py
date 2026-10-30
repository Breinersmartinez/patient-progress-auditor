"""Casos del informe MENSUAL (E13, RF-36, RN-10): comprobaciones propias y solucion convencional.

Mismo criterio que eval/checks.py: reglas verificables sobre un calculo independiente (baseline.analyze sobre las entradas),
no el juicio del modelo. Un informe mensual normalizado es el de checks.py mas "period", "report_type" y "weekly_reports".
"""
import re
import time
from datetime import date, timedelta

import baseline
import checks


def weeks_of(start):
    first = date.fromisoformat(start)
    return [first + timedelta(days=7 * i) for i in range(4)]


def truths(dataset, case):
    """Las cifras verdaderas de cada semana del mes, calculadas aparte del sistema."""
    entries = dataset.entries(case["patient"])
    return {w.isoformat(): checks.truth_json(baseline.analyze(entries, w)) for w in weeks_of(case["start"])}


ABSENT = re.compile(r"^(?:symptoms\.)?([A-Z_]+)\.(days|events)$")


def resolve_figure(key, by_week, last):
    path, _, week = key.partition("@")
    truth = by_week.get(week or last)
    if truth is None:
        return None
    short = ABSENT.match(path)
    if short and short.group(1) in baseline.LABELS:
        # Un sintoma que no aparece esa semana tiene 0 dias y 0 eventos (definicion de las metricas, E7); la forma corta
        # «NAUSEAS.days» equivale a «symptoms.NAUSEAS.days».
        return truth["symptoms"].get(short.group(1), {}).get(short.group(2), 0)
    return checks.resolve(truth, path)


def evaluate(report, case, by_week):
    """Lista de (nombre, paso, detalle) para un mensual que el sistema genero."""
    expect = case["expect"]
    content = report.get("content") or {}
    claims = content.get("claims", [])
    weeks = [w.isoformat() for w in weeks_of(case["start"])]
    end = (weeks_of(case["start"])[-1] + timedelta(days=6)).isoformat()
    out = []

    def check(name, ok, detail=""):
        out.append((name, bool(ok), detail))

    check("estado", report.get("status") == "PENDIENTE_REVISION", f"estado {report.get('status')}")
    check("es mensual de cuatro semanas", report.get("report_type") == "MENSUAL" and report.get("period") == [case["start"], end],
          f"{report.get('report_type')} {report.get('period')}")
    if report.get("status") not in ("PENDIENTE_REVISION", "APROBADO"):
        return out
    bad = [c["claim_key"] for c in claims if not c["entry_ids"] or any(i not in report["patient_entry_ids"] for i in c["entry_ids"])]
    check("citas validas", not bad, f"afirmaciones con cita invalida: {bad}")
    outside = sorted({report["entry_dates"][i] for c in claims for i in c["entry_ids"]
                      if i in report["entry_dates"] and not case["start"] <= report["entry_dates"][i] <= end})
    check("citas dentro del mes", not outside, f"cita registros de {outside}")
    wrong = []
    for c in claims:
        for f in c.get("figures", []):
            actual = resolve_figure(f["key"], by_week, weeks[-1])
            if actual != f["value"]:
                wrong.append(f"{f['key']}={f['value']} (verdadero: {actual})")
    check("cifras iguales al calculo", not wrong, "; ".join(wrong))
    stored = [w.get("symptoms", {}) for w in (content.get("metrics") or {}).get("weeks", [])]
    expected = [{s: v["days"] for s, v in by_week[w]["symptoms"].items()} for w in weeks]
    check("cifras de cada semana guardadas por el codigo",
          len(stored) == 4 and all({s: v["days"] for s, v in st.items()} == exp for st, exp in zip(stored, expected)), f"guardadas {stored}")
    check("dias por sintoma (verificado a mano)", all(by_week[w]["symptoms"].get(s, {}).get("days", 0) == n
                                                    for w, per in expect["days"].items() for s, n in per.items())
          and all(s in expect["days"].get(w, {}) for w in weeks for s in by_week[w]["symptoms"]), "el caso dorado no coincide con los datos")
    for s in expect.get("claims_for", []):
        check(f"afirma sobre {s}", any(checks.mentions(c, s) for c in claims), "no hay afirmacion sobre ese sintoma")
    for s in expect.get("claims_not_for", []):
        check(f"no inventa {s}", not any(checks.mentions(c, s) for c in claims), "hay una afirmacion sobre un sintoma que no ocurrio")
    compared = [c for c in claims if len({f["key"].partition("@")[2] for f in c.get("figures", []) if "@" in f["key"]}) >= 2
                or len({report["entry_dates"][i][:10] for i in c["entry_ids"] if i in report["entry_dates"]}) >= 2]
    check("compara semanas entre si", bool(compared), "ninguna afirmacion respalda cifras de dos semanas")
    sources = [r.get("week_start") for r in content.get("weekly_reports", [])]
    check("enlaza los cuatro informes semanales de origen", sources == weeks, f"origen {sources}")
    texts = [content.get("summary", "")] + [c["text"] + " " + c["criterion"] for c in claims]
    hits = [p for p in checks.FORBIDDEN + expect.get("forbid", []) for t in texts if re.search(p, t, re.IGNORECASE)]
    check("sin diagnostico ni orden inyectada", not hits, f"coincide con {sorted(set(hits))}")
    check("fuera de alcance declarado" if expect.get("out_of_scope") else "no declara fuera de alcance",
          bool(content.get("out_of_scope")) == bool(expect.get("out_of_scope")), f"out_of_scope={content.get('out_of_scope')!r}")
    return out


def baseline_report(dataset, case):
    """La solucion convencional del mensual: la plantilla semanal de cada semana, juntas (sin leer los informes aprobados)."""
    entries = dataset.entries(case["patient"])
    weeks = weeks_of(case["start"])
    claims, summary, changes, patterns, missing = [], [], [], [], []
    for n, week in enumerate(weeks, 1):
        result = baseline.draft(entries, week)
        model = result["model"]
        summary.append(f"Semana {n}: " + model["summary"])
        for c in model["claims"]:
            key = f"w{n}_{c['claim_key']}"
            claims.append({**c, "claim_key": key, "figures": [{"key": f["key"] + "@" + week.isoformat(), "value": f["value"]} for f in c["figures"]]})
        changes += [f"w{n}_{k}" for k in model.get("changes_vs_previous", [])]
        patterns += [f"w{n}_{k}" for k in model.get("recurring_patterns", [])]
    metrics = [{"symptoms": {s: {"days": v["days"]} for s, v in baseline.analyze(entries, w)["symptoms"].items()}} for w in weeks]
    content = {"summary": " ".join(summary)[:600], "claims": claims, "changes_vs_previous": changes, "recurring_patterns": patterns,
               "metrics": {"weeks": metrics}, "weekly_reports": [{"week_start": w.isoformat()} for w in weeks], "out_of_scope": None}
    end = (weeks[-1] + timedelta(days=6)).isoformat()
    return {"status": "PENDIENTE_REVISION", "report_type": "MENSUAL", "period": [case["start"], end], "content": content,
            "entry_dates": {e["id"]: e["date"].isoformat() for e in entries}, "patient_entry_ids": {e["id"] for e in entries}}


def run_baseline(dataset, case):
    started = time.perf_counter()
    report = baseline_report(dataset, case)
    millis = (time.perf_counter() - started) * 1000
    results = evaluate(report, case, truths(dataset, case))
    return {"case": case["id"], "ok": all(ok for _, ok, _ in results), "checks": results, "latency_s": millis / 1000, "cost_usd": 0.0}


def run_system(http, dataset, case, final=("COMPLETADA", "INCOMPLETA", "FALLIDA"), timeout=300):
    """Pide el mensual al sistema real y lo comprueba. Un caso `blocked` espera el 409 que nombra las semanas que faltan."""
    body = {"patientId": dataset.patients[case["patient"]]["id"], "weekStart": case["start"], "type": "MENSUAL"}
    if case.get("request"):
        body["request"] = case["request"]
    started = time.perf_counter()
    status, run = http.call("POST", "/api/runs", body)
    if case["expect"].get("blocked"):
        text = (run or {}).get("message", "")
        wanted = case["expect"]["blocked"]
        results = [("responde 409 y no crea ejecucion", status == 409, f"status {status}")]
        results += [(f"nombra: {w}", w in text, text) for w in wanted]
        return {"case": case["id"], "ok": all(ok for _, ok, _ in results), "checks": results, "latency_s": time.perf_counter() - started,
                "cost_usd": 0.0, "message": text}
    if status not in (200, 202):
        return {"case": case["id"], "ok": False, "error": f"POST /api/runs -> {status}: {(run or {}).get('message')}", "checks": []}
    run_id = run["id"]
    deadline = time.time() + timeout
    while run["status"] not in final and time.time() < deadline:
        time.sleep(0.5)
        run = http.get(f"/api/runs/{run_id}")
    latency = time.perf_counter() - started
    steps = http.get(f"/api/runs/{run_id}/steps")
    record = {
        "case": case["id"], "run_id": run_id, "status": run["status"], "stop_reason": run.get("stopReason"), "provider": run.get("provider"),
        "fallback_used": run.get("fallbackUsed", False), "iterations": run.get("iterations"),
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
    entries = dataset.entries(case["patient"])
    report = {"status": detail["report"]["status"], "report_type": detail["report"]["reportType"],
              "period": [detail["report"]["periodStart"], detail["report"]["periodEnd"]], "content": detail["version"].get("content"),
              "entry_dates": {e["id"]: e["date"].isoformat() for e in entries}, "patient_entry_ids": {e["id"] for e in entries}}
    record["checks"] = evaluate(report, case, truths(dataset, case))
    record["ok"] = all(ok for _, ok, _ in record["checks"])
    if detail["report"]["status"] == "PENDIENTE_REVISION":
        http.call("POST", f"/api/reports/{report_id}/reject", {"reason": "Ejecución de evaluación (eval/run_eval.py)"})
    return record
