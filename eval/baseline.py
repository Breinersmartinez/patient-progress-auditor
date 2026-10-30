"""Solucion convencional, sin modelo (AGENTS.md, «Medicion»; E14).

Hace lo mismo que el agente con codigo y plantillas: cuenta los dias con cada sintoma, compara con la semana anterior y
escribe frases fijas. Sirve para dos cosas:
  1. la linea de comparacion de las cuatro cifras (costo cero, latencia de milisegundos, pero sin lenguaje natural ni
     lectura del texto libre);
  2. los textos de los cinco informes sembrados (scripts/make_seed_reports.py), con las cifras que calcula el codigo.
Vive solo en eval/ y scripts/: la aplicacion no tiene ningun modo simulado (D16).

Definiciones (docs/decisiones.md, E7): un sintoma esta presente en una semana si tiene algun evento en algun dia. Frente
a la semana anterior W-1: persistente = en W y W-1; desaparecido = en W-1 y no en W; nuevo = en W, no en W-1 y nunca
antes; reaparecido = en W, no en W-1 y en alguna semana anterior. Si W-1 no tiene entradas, nada se clasifica.
"""
from datetime import date, timedelta

LABELS = {
    "VOMITO": "Vómito", "DIARREA": "Diarrea", "NAUSEAS": "Náuseas", "FIEBRE": "Fiebre", "MAREO": "Mareo",
    "DOLOR_CABEZA": "Dolor de cabeza", "DOLOR_HUESO": "Dolor de hueso", "APETITO_BAJO": "Apetito bajo",
    "ANIMO_DECAIDO": "Ánimo decaído",
}
ORDER = list(LABELS)
MAX_CITES = 6


def monday(d):
    return d - timedelta(days=d.weekday())


def entry(id_, day, body, events=(), structured=True):
    """Una entrada normalizada: el texto original, sus eventos confirmados y si se estructuro."""
    return {"id": id_, "date": day, "body": body, "events": list(events), "structured": structured}


def in_week(entries, week):
    end = week + timedelta(days=6)
    return [e for e in entries if week <= e["date"] <= end]


def symptoms_of(week_entries):
    found = {}
    for e in week_entries:
        for ev in e["events"]:
            found.setdefault(ev["symptom"], []).append((e["date"], e["id"], ev))
    return found


def analyze(entries, week):
    """Lo que el codigo calcula de una semana, con el mismo significado que `metrics` del informe."""
    cur_entries = in_week(entries, week)
    prev_week = week - timedelta(days=7)
    prev_entries = in_week(entries, prev_week)
    older = [e for e in entries if e["date"] < prev_week]
    cur = symptoms_of(cur_entries)
    prev = symptoms_of(prev_entries)
    history = set(symptoms_of(older))
    comparable = bool(prev_entries)
    days_with = {e["date"] for e in cur_entries}
    missing = [week + timedelta(days=i) for i in range(7) if week + timedelta(days=i) not in days_with]
    result = {
        "week": week, "entries": len(cur_entries), "days_with_entry": len(days_with), "missing_dates": missing,
        "comparable": comparable, "previous_entries": len(prev_entries),
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
        for s in ORDER:
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


PLURAL = {"NAUSEAS"}


def _v(symptom, singular, plural):
    return plural if symptom in PLURAL else singular


def _days(n):
    return f"{n} día" if n == 1 else f"{n} días"


def draft(entries, week):
    """El borrador que escribiria la plantilla: summary, claims con citas por id de entrada y figuras con su clave."""
    a = analyze(entries, week)
    claims = []
    changes, patterns = [], []

    def add(kind, symptom, text, criterion, cited, figures):
        key = f"c{len(claims) + 1}"
        claims.append({"claim_key": key, "text": text, "criterion": criterion, "entry_ids": cited[:MAX_CITES],
                       "figures": figures, "symptom": symptom, "kind": kind})
        if kind in ("reappeared", "new", "disappeared"):
            changes.append(key)
        if kind in ("reappeared", "persistent"):
            patterns.append(key)

    for s in ORDER:
        now = a["symptoms"].get(s)
        was = a["previous_days"].get(s, 0)
        name = LABELS[s]
        if now:
            fig_now = {"key": f"symptoms.{s}.days", "value": now["days"]}
            fig_prev = {"key": f"vs_previous.days.{s}.0", "value": was}
            if not a["comparable"]:
                add("present", s, f"{name} {_v(s, 'se registró', 'se registraron')} en {_days(now['days'])} de la semana.",
                    "Eventos confirmados de la semana; la semana anterior no tiene registros para comparar", now["entry_ids"], [fig_now])
            elif s in a["reappeared"]:
                add("reappeared", s, f"{name} {_v(s, 'reapareció', 'reaparecieron')} en {_days(now['days'])} de la semana; no se registró la semana anterior.",
                    "Días con el síntoma esta semana frente a la semana anterior y a semanas previas", now["entry_ids"], [fig_now])
            elif s in a["new"]:
                add("new", s, f"{name} {_v(s, 'aparece', 'aparecen')} por primera vez en {_days(now['days'])} de la semana.",
                    "Días con el síntoma esta semana frente a la semana anterior y a semanas previas", now["entry_ids"], [fig_now])
            else:
                add("persistent", s, f"{name} {_v(s, 'persiste', 'persisten')}: {_days(now['days'])} esta semana y {_days(was)} la semana anterior.",
                    "Días con el síntoma esta semana frente a la semana anterior", now["entry_ids"], [fig_now, fig_prev])
        elif s in a["disappeared"]:
            cited = [i for _, i, _ in a["_prev_symptoms"][s]]
            add("disappeared", s, f"{name} {_v(s, 'se registró', 'se registraron')} {_days(was)} la semana anterior y no {_v(s, 'aparece', 'aparecen')} esta semana.",
                "Días con el síntoma la semana anterior frente a esta semana", cited, [{"key": f"vs_previous.days.{s}.0", "value": was}])
    if not claims:
        cited = [e["id"] for e in a["_entries"]]
        add("none", None, "No se registraron síntomas en la semana.", "Eventos confirmados de la semana", cited,
            [{"key": "total_events", "value": 0}])
    summary = f"La semana tiene registros de {a['days_with_entry']} de 7 días."
    if a["comparable"]:
        summary += " Se comparó con la semana anterior."
    else:
        summary += " La semana anterior no tiene registros: no se pudo comparar."
    return {
        "analysis": a,
        "model": {"summary": summary, "claims": claims, "changes_vs_previous": changes, "recurring_patterns": patterns},
    }


# ---- estructuracion de una nota (D27): diccionario con ventana de negacion ----

KEYWORDS = {
    "VOMITO": ["vómito", "vomito", "vomitó", "vomito"], "DIARREA": ["diarrea"], "NAUSEAS": ["náusea", "nausea", "náuseas", "nauseas"],
    "FIEBRE": ["fiebre"], "MAREO": ["mareo", "mareado", "mareada"], "DOLOR_CABEZA": ["dolor de cabeza", "cefalea"],
    "DOLOR_HUESO": ["dolor de hueso", "dolor de huesos", "dolor óseo"], "APETITO_BAJO": ["comió poco", "no quiso comer", "rechazó el desayuno", "rechazó el almuerzo", "sin apetito", "apetito bajo"],
    "ANIMO_DECAIDO": ["decaído", "decaida", "decaída", "ánimo bajo", "desanimado"],
}
NEGATIONS = ["sin ", "no tuvo ", "no refiere ", "no presentó ", "no hubo ", "niega ", "ni "]
INTENSITY = {"leve": "LEVE", "moderado": "MODERADO", "moderada": "MODERADO", "intenso": "INTENSO", "intensa": "INTENSO", "fuerte": "INTENSO"}


def structure(note):
    """Los eventos que propondria el diccionario: palabra clave sin negacion en las cuatro palabras previas."""
    text = note.lower()
    events = []
    for symptom, words in KEYWORDS.items():
        for word in words:
            at = text.find(word)
            if at < 0:
                continue
            # La negacion vale dentro de la misma oracion: se corta en el ultimo punto.
            before = text[max(0, at - 24):at].split(".")[-1]
            if any(n in before for n in NEGATIONS):
                continue
            window = text[at:at + 60]
            intensity = next((v for k, v in INTENSITY.items() if k in window), "SIN_INDICAR")
            events.append({"symptom": symptom, "intensity": intensity})
            break
    return events
