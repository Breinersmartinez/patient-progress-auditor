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


class StructuringScore(unittest.TestCase):
    def test_exact_extra_and_missed(self):
        want = [{"symptom": "VOMITO", "occurrences": 2}, {"symptom": "FIEBRE"}]
        s = checks.score_structuring([{"symptom": "VOMITO", "occurrences": 2}, {"symptom": "FIEBRE"}], want)
        self.assertTrue(s["exact"])
        self.assertEqual((s["details_asked"], s["details_ok"]), (1, 1))
        s = checks.score_structuring([{"symptom": "VOMITO", "occurrences": 3}, {"symptom": "MAREO"}], want)
        self.assertFalse(s["exact"])
        self.assertEqual((s["false_positives"], s["missed"], s["details_ok"]), (["MAREO"], ["FIEBRE"], 0))


class SeededReportsAreReproducible(unittest.TestCase):
    def test_the_committed_file_is_what_the_script_generates(self):
        out = subprocess.run([sys.executable, str(ROOT / "scripts/make_seed_reports.py")], capture_output=True, text=True, check=True).stdout
        committed = (ROOT / "backend/src/main/resources/seed/demo-reports.json").read_text(encoding="utf-8")
        self.assertEqual(json.loads(out), json.loads(committed))


if __name__ == "__main__":
    unittest.main()
