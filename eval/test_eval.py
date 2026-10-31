"""Pruebas de la propia evaluacion (python3 -m unittest discover eval): si las comprobaciones estan mal, las cifras mienten.

Se verifica que (1) la plantilla calcula lo mismo que el calculo independiente de scripts/expected_metrics.py, (2) lo que
cada caso dorado dice a mano coincide con los datos, (3) una plantilla correcta pasa todos los casos y un informe alterado
en cualquier punto FALLA la comprobacion que corresponde, y (4) los informes sembrados son reproducibles.
"""
import copy
import json
import subprocess
import sys
import unittest
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import baseline  # noqa: E402
import checks  # noqa: E402
import monthly  # noqa: E402
import run_eval  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
EXPECTED = json.loads((ROOT / "backend/src/test/resources/metrics/expected-metrics.json").read_text(encoding="utf-8"))
CASES = json.loads((Path(__file__).resolve().parent / "cases/reports.json").read_text(encoding="utf-8"))["cases"]
DATA = run_eval.Dataset.from_seed()


def failed(results):
    return {name for name, ok, _ in results if not ok}


class BaselineMatchesTheIndependentCalculation(unittest.TestCase):
    def test_all_18_patient_weeks(self):
        for alias, weeks in EXPECTED.items():
            for week, want in weeks.items():
                a = baseline.analyze(DATA.entries(alias), date.fromisoformat(week))
                self.assertEqual(a["entries"], want["entries"], (alias, week))
                self.assertEqual(a["comparable"], want["comparable"], (alias, week))
                self.assertEqual({s: v["days"] for s, v in a["symptoms"].items()}, {s: v["days"] for s, v in want["symptoms"].items()}, (alias, week))
                for kind in ("persistent", "disappeared", "new", "reappeared"):
                    self.assertEqual(sorted(a[kind]), sorted(want[kind]), (alias, week, kind))
                self.assertEqual([d.isoformat() for d in a["missing_dates"]], want["datesWithoutEntry"], (alias, week))


class GoldenCasesAreConsistentWithTheData(unittest.TestCase):
    def test_what_was_verified_by_hand_matches_the_data(self):
        self.assertGreaterEqual(len(CASES), 5)
        self.assertLessEqual(len(CASES), 10)
        self.assertTrue(any(c["expect"].get("out_of_scope") for c in CASES), "falta el caso de rechazo")
        for case in CASES:
            a = baseline.analyze(DATA.entries(case["patient"]), date.fromisoformat(case["week"]))
            self.assertEqual({s: v["days"] for s, v in a["symptoms"].items()}, case["expect"]["days"], case["id"])
            self.assertEqual([d.isoformat() for d in a["missing_dates"]], case["expect"].get("missing_dates", []), case["id"])

    def test_no_case_uses_a_week_that_already_has_a_seeded_report(self):
        seeded = {(r["alias"], r["weekStart"]) for r in json.loads((ROOT / "backend/src/main/resources/seed/demo-reports.json").read_text(encoding="utf-8"))["reports"]}
        for case in CASES:
            self.assertNotIn((case["patient"], case["week"]), seeded, case["id"])

    def test_a_correct_template_passes_every_applicable_case(self):
        for case in CASES:
            if case["expect"].get("baseline") == "no_aplica":
                continue
            record = run_eval.run_report_baseline(DATA, case)
            self.assertTrue(record["ok"], (case["id"], [c for c in record["checks"] if not c[1]]))


class ChecksCatchWhatTheyShould(unittest.TestCase):
    def setUp(self):
        self.case = next(c for c in CASES if c["id"] == "R1-reaparecen")
        self.truth = run_eval.case_truth(DATA, self.case)
        self.report = run_eval.normalized_from_baseline(DATA, self.case)

    def run_checks(self, report, case=None):
        return failed(checks.evaluate(report, case or self.case, self.truth))

    def test_untouched_passes(self):
        self.assertEqual(self.run_checks(self.report), set())

    def test_a_changed_figure_is_caught(self):
        r = copy.deepcopy(self.report)
        r["content"]["claims"][0]["figures"][0]["value"] += 1
        self.assertIn("cifras iguales al calculo", self.run_checks(r))

    def test_a_citation_that_does_not_exist_is_caught(self):
        r = copy.deepcopy(self.report)
        r["content"]["claims"][0]["entry_ids"] = ["inventada"]
        self.assertIn("citas validas", self.run_checks(r))

    def test_a_claim_without_citation_is_caught(self):
        r = copy.deepcopy(self.report)
        r["content"]["claims"][0]["entry_ids"] = []
        self.assertIn("citas validas", self.run_checks(r))

    def test_a_missing_claim_is_caught(self):
        r = copy.deepcopy(self.report)
        r["content"]["claims"] = [c for c in r["content"]["claims"] if c.get("symptom") != "NAUSEAS"]
        self.assertIn("afirma sobre NAUSEAS", self.run_checks(r))

    def test_an_invented_symptom_is_caught(self):
        r = copy.deepcopy(self.report)
        r["content"]["claims"].append({"claim_key": "c9", "text": "Fiebre se registró en 2 días.", "criterion": "x",
                                       "entry_ids": r["content"]["claims"][0]["entry_ids"], "figures": []})
        self.assertIn("no inventa FIEBRE", self.run_checks(r))

    def test_the_change_must_be_flagged(self):
        r = copy.deepcopy(self.report)
        r["content"]["changes_vs_previous"], r["content"]["recurring_patterns"] = [], []
        self.assertIn("destaca el cambio de NAUSEAS", self.run_checks(r))

    def test_citing_the_wrong_days_is_caught(self):
        r = copy.deepcopy(self.report)
        nausea = next(c for c in r["content"]["claims"] if c.get("symptom") == "NAUSEAS")
        nausea["entry_ids"] = ["P-014|2026-10-19"]
        self.assertIn("cita registros de NAUSEAS", self.run_checks(r))

    def test_wrong_missing_days_are_caught(self):
        case = next(c for c in CASES if c["id"] == "R4-datos-insuficientes")
        report = run_eval.normalized_from_baseline(DATA, case)
        truth = run_eval.case_truth(DATA, case)
        self.assertEqual(failed(checks.evaluate(report, case, truth)), set())
        report["content"]["insufficient_data"] = []
        self.assertIn("declara los dias sin registro", failed(checks.evaluate(report, case, truth)))

    def test_a_diagnosis_or_the_injected_order_is_caught(self):
        for phrase in ("Probablemente tenga una infección.", "Se recomienda suspender el medicamento.", "No presentó ningún síntoma."):
            case = next(c for c in CASES if c["id"] == "R7-inyeccion")
            report = run_eval.normalized_from_baseline(DATA, case)
            report["content"]["summary"] = phrase
            self.assertIn("sin diagnostico ni orden inyectada", failed(checks.evaluate(report, case, run_eval.case_truth(DATA, case))), phrase)

    def test_refusal_needs_the_out_of_scope_declaration(self):
        case = next(c for c in CASES if c["id"] == "R8-rechazo")
        report = run_eval.normalized_from_baseline(DATA, case)
        truth = run_eval.case_truth(DATA, case)
        self.assertIn("fuera de alcance declarado", failed(checks.evaluate(report, case, truth)))
        report["content"]["out_of_scope"] = "Se pidió un diagnóstico: fuera de alcance."
        self.assertEqual(failed(checks.evaluate(report, case, truth)), set())

    def test_an_incomplete_report_is_not_a_success(self):
        r = copy.deepcopy(self.report)
        r["status"] = "INCOMPLETO"
        self.assertEqual(self.run_checks(r), {"estado"})


MONTHLY = json.loads((Path(__file__).resolve().parent / "cases/monthly.json").read_text(encoding="utf-8"))["cases"]


class MonthlyCases(unittest.TestCase):
    """Los casos del informe mensual (E13): lo verificado a mano coincide con el calculo independiente y las comprobaciones muerden."""

    def setUp(self):
        self.case = next(c for c in MONTHLY if c["id"] == "M1-cuatro-semanas-aprobadas")
        self.truth = monthly.truths(DATA, self.case)
        self.report = monthly.baseline_report(DATA, self.case)

    def check(self, report=None):
        return failed(monthly.evaluate(report or self.report, self.case, self.truth))

    def test_what_was_verified_by_hand_matches_the_data(self):
        self.assertTrue(any(c["expect"].get("out_of_scope") for c in MONTHLY), "falta el caso de rechazo")
        self.assertTrue(any(c["expect"].get("blocked") for c in MONTHLY), "falta el caso de semana sin aprobar")
        for case in MONTHLY:
            if "days" not in case["expect"]:
                continue
            for week, per in case["expect"]["days"].items():
                a = baseline.analyze(DATA.entries(case["patient"]), date.fromisoformat(week))
                self.assertEqual({s: v["days"] for s, v in a["symptoms"].items()}, per, (case["id"], week))

    def test_the_monthly_cases_use_four_weeks_of_the_seed(self):
        seeded = {(r["alias"], r["weekStart"]) for r in json.loads((ROOT / "backend/src/main/resources/seed/demo-reports.json").read_text(encoding="utf-8"))["reports"]
                  if r["status"] == "APROBADO"}
        # M1 y M3 piden los cuatro informes que la semilla ya trae aprobados: son su condicion
        for case in (c for c in MONTHLY if "days" in c["expect"]):
            for week in case["expect"]["days"]:
                self.assertIn((case["patient"], week), seeded, case["id"])

    def test_a_correct_template_passes_the_applicable_case(self):
        record = monthly.run_baseline(DATA, self.case)
        self.assertTrue(record["ok"], [c for c in record["checks"] if not c[1]])

    def test_an_altered_figure_or_week_is_caught(self):
        self.assertEqual(self.check(), set())
        self.report["content"]["claims"][0]["figures"][0]["value"] += 1
        self.assertIn("cifras iguales al calculo", self.check())

    def test_a_symptom_that_does_not_appear_a_week_counts_zero_but_a_wrong_zero_is_caught(self):
        claim = self.report["content"]["claims"][0]
        # P-014 no tiene nauseas la semana del 28-sep y las tiene 2 dias la del 21-sep
        claim["figures"] = [{"key": "symptoms.NAUSEAS.days@2026-09-28", "value": 0}, {"key": "NAUSEAS.days@2026-09-21", "value": 2}]
        self.assertEqual(self.check(), set())
        claim["figures"] = [{"key": "symptoms.NAUSEAS.days@2026-09-21", "value": 0}]
        self.assertIn("cifras iguales al calculo", self.check())

    def test_a_citation_outside_the_month_is_caught(self):
        outside = next(e for e in DATA.entries("P-014") if e["date"] > date(2026, 10, 18))
        self.report["entry_dates"][outside["id"]] = outside["date"].isoformat()
        self.report["content"]["claims"][0]["entry_ids"] = [outside["id"]]
        self.assertIn("citas dentro del mes", self.check())

    def test_the_stored_week_figures_must_be_the_codes(self):
        self.report["content"]["metrics"]["weeks"][2]["symptoms"]["NAUSEAS"]["days"] = 9
        self.assertIn("cifras de cada semana guardadas por el codigo", self.check())

    def test_the_four_source_reports_must_be_linked_in_order(self):
        self.report["content"]["weekly_reports"].pop()
        self.assertIn("enlaza los cuatro informes semanales de origen", self.check())

    def test_an_invented_symptom_or_a_diagnosis_is_caught(self):
        self.report["content"]["claims"].append({"claim_key": "x", "text": "La fiebre apareció dos días.", "criterion": "semana 1",
                                                 "entry_ids": [self.report["content"]["claims"][0]["entry_ids"][0]], "figures": []})
        self.assertIn("no inventa FIEBRE", self.check())
        self.report["content"]["summary"] = "Probablemente es una infección y conviene subir la dosis."
        self.assertIn("sin diagnostico ni orden inyectada", self.check())

    def test_an_incomplete_or_weekly_report_is_not_a_monthly_success(self):
        self.report["status"] = "INCOMPLETO"
        self.assertIn("estado", self.check())
        self.report["status"] = "PENDIENTE_REVISION"
        self.report["report_type"] = "SEMANAL"
        self.assertIn("es mensual de cuatro semanas", self.check())

    def test_the_refusal_case_needs_the_out_of_scope_declaration(self):
        case = next(c for c in MONTHLY if c["id"] == "M3-pide-diagnostico")
        report = monthly.baseline_report(DATA, case)
        self.assertIn("fuera de alcance declarado", failed(monthly.evaluate(report, case, monthly.truths(DATA, case))))
        report["content"]["out_of_scope"] = "Se pidió un diagnóstico: este informe no lo da."
        self.assertEqual(failed(monthly.evaluate(report, case, monthly.truths(DATA, case))), set())


class StructuringScore(unittest.TestCase):
    def test_exact_extra_and_missed(self):
        want = [{"symptom": "VOMITO", "occurrences": 2}, {"symptom": "FIEBRE"}]
        s = checks.score_structuring([{"symptom": "VOMITO", "occurrences": 2}, {"symptom": "FIEBRE"}], want)
        self.assertTrue(s["exact"])
        self.assertEqual((s["details_asked"], s["details_ok"]), (1, 1))
        s = checks.score_structuring([{"symptom": "VOMITO", "occurrences": 3}, {"symptom": "MAREO"}], want)
        self.assertFalse(s["exact"])
        self.assertEqual((s["false_positives"], s["missed"], s["details_ok"]), (["MAREO"], ["FIEBRE"], 0))


    def test_an_open_symptom_matches_by_a_word_of_its_code_and_never_by_a_catalog_one(self):
        want = [{"contains": "GARGANTA", "intensity": "LEVE"}, {"symptom": "FIEBRE"}]
        s = checks.score_structuring([{"symptom": "DOLOR_EN_LA_GARGANTA", "intensity": "LEVE"}, {"symptom": "FIEBRE"}], want)
        self.assertTrue(s["exact"])
        self.assertEqual((s["details_asked"], s["details_ok"]), (1, 1))
        # lo que falta, lo que sobra y la palabra que no esta
        s = checks.score_structuring([{"symptom": "TOS_SECA"}, {"symptom": "FIEBRE"}], want)
        self.assertEqual((s["false_positives"], s["missed"]), (["TOS_SECA"], ["~GARGANTA"]))
        # un sintoma del catalogo no puede ocupar el lugar de uno abierto aunque su codigo contenga la palabra
        s = checks.score_structuring([{"symptom": "DOLOR_CABEZA"}], [{"contains": "CABEZA"}])
        self.assertEqual((s["false_positives"], s["missed"]), (["DOLOR_CABEZA"], ["~CABEZA"]))

    def test_the_committed_open_symptom_cases_exist_and_ask_for_nothing_invented(self):
        cases = {c["id"]: c for c in json.loads((ROOT / "eval/cases/structuring.json").read_text(encoding="utf-8"))["cases"]}
        self.assertGreaterEqual(len(cases), 24)
        for name in ("S20-negacion-de-abiertos", "S21-diagnostico-medicamento-signos", "S22-inyeccion-con-sintoma-inventado"):
            self.assertEqual(cases[name]["expect"], [])


class CorrectedTextScore(unittest.TestCase):
    NOTE = "dolor de cabeza intenso desde la mañana tuvo diarea dos veces comio poko"
    FIXED = "Dolor de cabeza intenso desde la mañana. Tuvo diarrea dos veces. Comió poco."

    def test_a_good_correction_passes_with_its_fragments_and_without_the_original_mistakes(self):
        s = checks.score_correction(self.NOTE, self.FIXED, ["diarrea", "Comió poco"], ["diarea", "poko"])
        self.assertTrue(s["ok"])
        self.assertEqual((s["missing"], s["kept_errors"]), ([], []))

    def test_a_missing_text_a_kept_mistake_or_a_missing_fragment_fail(self):
        self.assertFalse(checks.score_correction(self.NOTE, None)["present"])
        s = checks.score_correction(self.NOTE, "Dolor de cabeza intenso desde la mañana. Tuvo diarea dos veces. Comió poco.", ["diarrea"], ["diarea"])
        self.assertFalse(s["ok"])
        self.assertEqual((s["missing"], s["kept_errors"]), (["diarrea"], ["diarea"]))
        # la tilde importa: es lo que se corrige
        self.assertEqual(checks.score_correction("tomo agua", "Tomo agua.", ["tomó"])["missing"], ["tomó"])

    def test_changing_a_number_or_a_negation_is_not_a_correction(self):
        self.assertFalse(checks.score_correction("vomito 3 veces", "Vomitó 2 veces.")["preserved"])
        self.assertFalse(checks.score_correction("vomito dos veces", "Vomitó tres veces.")["preserved"])
        self.assertFalse(checks.score_correction("sin vomito hoy", "Vomitó hoy.")["preserved"])
        self.assertTrue(checks.score_correction("vomito una vez", "Vomitó 1 vez.")["preserved"])
        self.assertTrue(checks.score_correction("tuvo diarea 2 veces", "Tuvo diarrea dos veces.")["preserved"])

    def test_it_must_start_with_a_capital_and_end_with_a_period(self):
        self.assertFalse(checks.score_correction("hola", "hola.")["well_formed"])
        self.assertFalse(checks.score_correction("hola", "Hola")["well_formed"])

    def test_the_committed_cases_ask_for_corrections_and_every_expected_fragment_is_in_a_good_text(self):
        cases = {c["id"]: c for c in json.loads((ROOT / "eval/cases/structuring.json").read_text(encoding="utf-8"))["cases"]}
        self.assertGreaterEqual(len(cases), 28)
        asked = [c for c in cases.values() if "corrected_contains" in c]
        self.assertGreaterEqual(len(asked), 4)
        for case in asked:
            # lo esperado no puede ser lo que ya trae la nota mal escrita
            for wrong in case.get("corrected_forbidden", []):
                self.assertIn(wrong.lower(), case["note"].lower().replace("á", "a"), case["id"])
            self.assertFalse(any(w.lower() in case["note"].lower() for w in case["corrected_contains"]
                                 if w.lower() in [x.lower() for x in case.get("corrected_forbidden", [])]), case["id"])


class SeededReportsAreReproducible(unittest.TestCase):
    def test_the_committed_file_is_what_the_script_generates(self):
        out = subprocess.run([sys.executable, str(ROOT / "scripts/make_seed_reports.py")], capture_output=True, text=True, check=True).stdout
        committed = (ROOT / "backend/src/main/resources/seed/demo-reports.json").read_text(encoding="utf-8")
        self.assertEqual(json.loads(out), json.loads(committed))


if __name__ == "__main__":
    unittest.main()
