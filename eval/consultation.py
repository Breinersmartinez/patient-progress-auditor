"""Casos de la CONSULTA del medico, «Armar informe» (D31, RF-46): comprobaciones propias y solucion convencional.

Generaliza baseline.analyze/draft a un periodo cualquiera (no una semana fija), con una comparacion opcional, igual que
el backend generalizo MetricsCalculator.compute a computePeriod (Pieza 6, paso 3 de docs/PLAN_M3.md). Reutiliza
checks.evaluate tal cual (no depende de que el periodo sea una semana de 7 dias); solo truth_json cambia, porque
"days_without_entry" usa el largo real del periodo, no un 7 fijo.
"""
import time
from datetime import timedelta

import baseline
import checks


def analyze_period(entries, start, end, comparison_start=None, comparison_end=None):
    """Lo que el codigo calcularia de un periodo cualquiera, con el mismo significado que `metrics` de la consulta."""
    cur_entries = [e for e in entries if start <= e["date"] <= end]
    comparable = comparison_start is not None
    prev_entries = [e for e in entries if comparison_start <= e["date"] <= comparison_end] if comparable else []
    older_cutoff = comparison_start if comparable else start
    older = [e for e in entries if e["date"] < older_cutoff]
    cur = baseline.symptoms_of(cur_entries)
    prev = baseline.symptoms_of(prev_entries)
    history = set(baseline.symptoms_of(older))
    days_with = {e["date"] for e in cur_entries}
    total_days = (end - start).days + 1
    missing = [start + timedelta(days=i) for i in range(total_days) if start + timedelta(days=i) not in days_with]
    result = {
        "start": start, "end": end, "total_days": total_days, "entries": len(cur_entries), "days_with_entry": len(days_with),
        "missing_dates": missing, "comparable": comparable, "previous_entries": len(prev_entries),
        "total_events": sum(len(e["events"]) for e in cur_entries),
        "unstructured": sum(1 for e in cur_entries if not e["structured"]),
        "previous_unstructured": sum(1 for e in prev_entries if not e["structured"]),
        "previous_total_events": sum(len(e["events"]) for e in prev_entries),
        "symptoms": {s: {"days": len({d for d, _, _ in v}), "dates": sorted({d for d, _, _ in v}),
                         "entry_ids": [i for _, i, _ in v], "events": len(v),
                         "occurrences": (sum(ev.get("occurrences") or 0 for _, _, ev in v)
                                         if any(ev.get("occurrences") for _, _, ev in v) else None)}
                     for s, v in cur.items()},
        "previous_days": {s: len({d for d, _, _ in v}) for s, v in prev.items()},
        "persistent": [], "disappeared": [], "new": [], "reappeared": [],
    }
    if comparable:
        for s in baseline.ORDER:
            if s in cur and s in prev:
                result["persistent"].append(s)
            elif s in prev and s not in cur:
                result["disappeared"].append(s)
            elif s in cur and s not in prev:
                (result["reappeared"] if s in history else result["new"]).append(s)
    result["_entries"] = cur_entries
    result["_prev_entries"] = prev_entries
    result["_prev_symptoms"] = prev
    return result


def truth_json(a):
    """Como checks.truth_json, pero `days_without_entry` usa el largo real del periodo, no 7 fijo (unica diferencia)."""
    days = {s: v["days"] for s, v in a["symptoms"].items()}
    names = set(days) | set(a["previous_days"])
    return {
        "entries": a["entries"], "days_with_entry": a["days_with_entry"], "total_events": a["total_events"],
        "days_without_entry": a["total_days"] - a["days_with_entry"], "unstructured_entries": a["unstructured"],
        "symptoms": {s: {"days": v["days"], "events": v["events"], "occurrences": v["occurrences"]} for s, v in a["symptoms"].items()},
        "vs_previous": {"previous_entries": a["previous_entries"], "previous_total_events": a["previous_total_events"],
                        "previous_unstructured_entries": a["previous_unstructured"],
                        "days": {s: [a["previous_days"].get(s, 0), days.get(s, 0)] for s in names}},
    }


def draft(entries, start, end, comparison_start=None, comparison_end=None):
    """El borrador que escribiria la plantilla para una consulta: summary, claims con citas y figuras sin «@»."""
    a = analyze_period(entries, start, end, comparison_start, comparison_end)
    claims, changes, patterns = [], [], []

    def add(kind, text, criterion, cited, figures):
        key = f"c{len(claims) + 1}"
        claims.append({"claim_key": key, "text": text, "criterion": criterion, "entry_ids": cited[:baseline.MAX_CITES], "figures": figures})
        if kind in ("reappeared", "new", "disappeared"):
            changes.append(key)
        if kind in ("reappeared", "persistent"):
            patterns.append(key)

    for s in baseline.ORDER:
        now = a["symptoms"].get(s)
        was = a["previous_days"].get(s, 0)
        name = baseline.LABELS[s]
        if now:
            fig_now = {"key": f"symptoms.{s}.days", "value": now["days"]}
            fig_prev = {"key": f"vs_previous.days.{s}.0", "value": was}
            if not a["comparable"]:
                add("present", f"{name} {baseline._v(s, 'se registró', 'se registraron')} en {baseline._days(now['days'])} del período.",
                    "Eventos confirmados del período; no se pidió comparar con otro período", now["entry_ids"], [fig_now])
            elif s in a["reappeared"]:
                add("reappeared", f"{name} {baseline._v(s, 'reapareció', 'reaparecieron')} en {baseline._days(now['days'])} del período; "
                    "no se registró en el período de comparación.", "Días con el síntoma en el período frente al período de comparación",
                    now["entry_ids"], [fig_now])
            elif s in a["new"]:
                add("new", f"{name} {baseline._v(s, 'aparece', 'aparecen')} por primera vez en {baseline._days(now['days'])} del período.",
                    "Días con el síntoma en el período frente al período de comparación", now["entry_ids"], [fig_now])
            else:
                add("persistent", f"{name} {baseline._v(s, 'persiste', 'persisten')}: {baseline._days(now['days'])} en el período y "
                    f"{baseline._days(was)} en el período de comparación.", "Días con el síntoma en el período frente al período de comparación",
                    now["entry_ids"], [fig_now, fig_prev])
        elif s in a["disappeared"]:
            cited = [i for _, i, _ in a["_prev_symptoms"][s]]
            add("disappeared", f"{name} {baseline._v(s, 'se registró', 'se registraron')} {baseline._days(was)} en el período de comparación "
                f"y no {baseline._v(s, 'aparece', 'aparecen')} en este período.", "Días con el síntoma en el período de comparación frente a este período",
                cited, [{"key": f"vs_previous.days.{s}.0", "value": was}])
    if not claims:
        cited = [e["id"] for e in a["_entries"]]
        add("none", "No se registraron síntomas en el período.", "Eventos confirmados del período", cited, [{"key": "total_events", "value": 0}])
    summary = f"El período tiene registros de {a['days_with_entry']} de {a['total_days']} días."
    summary += " Se comparó con el período elegido." if a["comparable"] else " No se pidió comparar con otro período."
    return {"analysis": a, "model": {"summary": summary, "claims": claims, "changes_vs_previous": changes, "recurring_patterns": patterns}}


def baseline_report(dataset, case):
    """La solucion convencional de la consulta: la misma plantilla del informe, generalizada al periodo del caso."""
    entries = dataset.entries(case["patient"])
    cmp_start = date_or_none(case.get("comparison_start"))
    cmp_end = date_or_none(case.get("comparison_end"))
    result = draft(entries, iso(case["start"]), iso(case["end"]), cmp_start, cmp_end)
    content = dict(result["model"])
    content["insufficient_data"] = ([{"kind": "DIAS_SIN_REGISTRO", "dates": [d.isoformat() for d in result["analysis"]["missing_dates"]]}]
                                     if result["analysis"]["missing_dates"] else [])
    content["out_of_scope"] = None
    return {"status": "PENDIENTE_REVISION", "content": content, "entry_dates": {e["id"]: e["date"].isoformat() for e in entries},
            "patient_entry_ids": {e["id"] for e in entries}}


def iso(text):
    from datetime import date
    return date.fromisoformat(text)


def date_or_none(text):
    return iso(text) if text else None


def case_truth(dataset, case):
    entries = dataset.entries(case["patient"])
    a = analyze_period(entries, iso(case["start"]), iso(case["end"]), date_or_none(case.get("comparison_start")), date_or_none(case.get("comparison_end")))
    return truth_json(a)


def run_baseline(dataset, case):
    started = time.perf_counter()
    report = baseline_report(dataset, case)
    millis = (time.perf_counter() - started) * 1000
    results = checks.evaluate(report, case, case_truth(dataset, case))
    return {"case": case["id"], "ok": all(ok for _, ok, _ in results), "checks": results, "latency_s": millis / 1000, "cost_usd": 0.0}


def run_system(http, dataset, case, final=("COMPLETADA", "INCOMPLETA", "FALLIDA"), timeout=300):
    """Pide la consulta al sistema real (D31, RF-46) y la comprueba. No se rechaza al terminar: la consulta no tiene
    flujo (sin estado, sin aprobacion) y no bloquea pedir la misma otra vez."""
    body = {"patientId": dataset.patients[case["patient"]]["id"], "periodStart": case["start"], "periodEnd": case["end"]}
    if case.get("comparison_start"):
        body["comparisonStart"] = case["comparison_start"]
        body["comparisonEnd"] = case["comparison_end"]
    started = time.perf_counter()
    status, run = http.call("POST", "/api/consultations", body)
    if case["expect"].get("blocked"):
        text = (run or {}).get("message", "")
        results = [(f"responde {case['expect'].get('blocked_status', 409)} y no crea ejecución", status == case["expect"].get("blocked_status", 409),
                    f"status {status}")]
        results += [(f"nombra: {w}", w in text, text) for w in case["expect"].get("blocked", [])]
        return {"case": case["id"], "ok": all(ok for _, ok, _ in results), "checks": results, "latency_s": time.perf_counter() - started,
                "cost_usd": 0.0, "message": text}
    if status not in (200, 202):
        return {"case": case["id"], "ok": False, "error": f"POST /api/consultations -> {status}: {(run or {}).get('message')}", "checks": []}
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
    consultation_id = run.get("consultationId")
    if not consultation_id:
        record["ok"] = False
        return record
    detail = http.get(f"/api/consultations/{consultation_id}")
    entries = dataset.entries(case["patient"])
    # La consulta no tiene su propia columna de estado (decision del paso 3, docs/PLAN_M3.md): el estado de la
    # ejecucion ya lo dice. Se traduce a los dos nombres que checks.evaluate conoce, igual que un informe.
    report = {"status": "PENDIENTE_REVISION" if run["status"] == "COMPLETADA" else "INCOMPLETO", "content": detail.get("content"),
              "entry_dates": {e["id"]: e["date"].isoformat() for e in entries}, "patient_entry_ids": {e["id"] for e in entries}}
    record["checks"] = checks.evaluate(report, case, case_truth(dataset, case))
    record["ok"] = all(ok for _, ok, _ in record["checks"])
    return record
