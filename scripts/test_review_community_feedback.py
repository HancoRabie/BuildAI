"""Offline trust-boundary tests. No Minecraft simulation or AI code tests."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from review_community_feedback import Rejected, read_and_validate, promote


def sha(data):
    return hashlib.sha256(data).hexdigest()


def sample(category="redstone_insight", corrected=True):
    original = b"\x1f\x8b"+"sample redstone".encode()
    fixed = b"\x1f\x8b"+"fixed circuit".encode()
    is_logic = category == "redstone_insight"
    report = {
        "schema": "buildai-community-example-v2",
        "schema_version": 2,
        "category": category,
        "namespace": "redstone_insights" if is_logic else "build_preferences",
        "review_status": "quarantined",
        "trust_level": "unverified",
        "eligible_for_shared_learning": False,
        "reviewer_verified": False,
        "contains_verified_solution": False,
        "rights_confirmed": True,
        "license": "CC BY 4.0",
        "exporter_buildai_version": "0.1.0",
        "minecraft_version": "1.21.1",
        "minecraft_edition": "Java",
        "fabric_loader_version": "0.16.7",
        "source_generation_version": "unknown",
        "benchmark_id": "BENCHY-P2" if is_logic else "unspecified",
        "benchmark_revision": "v1" if is_logic else "unspecified",
        "expected": "OFF",
        "observed": "ON",
        "contributor_claim": "Corrected and tested" if is_logic else "Subjective preference",
        "pillar": "Boolean logic" if is_logic else "Not applicable",
        "generated_sha256": sha(original),
    }
    if corrected:
        report["corrected_sha256"] = sha(fixed)
    contents = {
        "report.json": json.dumps(report).encode(),
        "README.txt": b"Sanitized example",
        "generated.schem": original,
    }
    if corrected:
        contents["corrected.schem"] = fixed
    return contents


class IntakeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)

    def write(self, contents):
        result = self.folder / "submission.zip"
        with zipfile.ZipFile(result, "w") as zip_out:
            for name, value in contents.items():
                zip_out.writestr(name, value)
        return result

    def mutate_report(self, contents, **fields):
        obj = json.loads(contents["report.json"])
        obj.update(fields)
        contents["report.json"] = json.dumps(obj).encode()
        return contents

    def test_v2_redstone_valid_but_quarantined(self):
        path = self.write(sample())
        report, fingerprint = read_and_validate(path)
        self.assertEqual(report["review_status"], "quarantined")
        self.assertFalse(report["eligible_for_shared_learning"])
        self.assertEqual(fingerprint, sha(path.read_bytes()))

    def test_preference_is_distinct_from_logic(self):
        report, _ = read_and_validate(self.write(sample("build_preference",False)))
        self.assertEqual(report["namespace"], "build_preferences")
        self.assertEqual(report["contributor_claim"], "Subjective preference")

    def test_reject_attempted_self_approval(self):
        contents = self.mutate_report(sample(), reviewer_verified=True,
            review_status="approved", eligible_for_shared_learning=True)
        with self.assertRaises(Rejected):
            read_and_validate(self.write(contents))

    def test_reject_mixed_lane(self):
        contents = self.mutate_report(sample("build_preference",False),
            namespace="redstone_insights")
        with self.assertRaises(Rejected):
            read_and_validate(self.write(contents))

    def test_reject_changed_schematic_or_unknown_file(self):
        contents = sample()
        contents["generated.schem"] = b"\x1f\x8b" + b"TAMPER"
        with self.assertRaises(Rejected):
            read_and_validate(self.write(contents))
        contents = sample()
        contents["../../unsafe.txt"] = b"do not extract"
        with self.assertRaises(Rejected):
            read_and_validate(self.write(contents))

    def decision(self, path, category="redstone_insight", **overrides):
        obj = {
            "decision": "approve",
            "category": category,
            "source_zip_sha256": sha(path.read_bytes()),
            "rights_reviewed": True,
            "reviewer": "maintainer",
            "review_notes": "Geometry inspected and independently checked",
            "independent_minecraft_test": True,
            "test_evidence": "All input combinations and reset tested",
            "verified_minecraft_version": "1.21.1",
        }
        obj.update(overrides)
        decision_file = self.folder / "review.json"
        decision_file.write_text(json.dumps(obj))
        return decision_file

    def test_redstone_claim_alone_never_promotes(self):
        path = self.write(sample())
        dest = self.folder / "curated"
        with self.assertRaises(Rejected):
            promote(path, self.decision(path,independent_minecraft_test=False),
                dest)
        self.assertFalse((dest / "redstone_insights").exists() and
                         list((dest / "redstone_insights").iterdir()))

    def test_approved_redstone_kept_separate_and_attested(self):
        path = self.write(sample())
        dest = self.folder / "curated"
        zip_out, review_out = promote(path,self.decision(path),dest)
        self.assertEqual(zip_out.parent.name,"redstone_insights")
        self.assertTrue(zip_out.is_file())
        result = json.loads(review_out.read_text())
        self.assertTrue(result["training_eligible"])
        self.assertEqual(result["curated_status"],"approved_redstone_insight")
        with self.assertRaises(Rejected):
            promote(path,self.decision(path),dest)

    def test_subjective_preference_can_only_be_approved_as_style(self):
        path = self.write(sample("build_preference",False))
        dest = self.folder / "curated"
        decision = self.decision(path,"build_preference",
            independent_minecraft_test=False,test_evidence="")
        out, attestation = promote(path,decision,dest)
        self.assertEqual(out.parent.name,"build_preferences")
        self.assertEqual(json.loads(attestation.read_text())["curated_status"],
                         "approved_style_example")

    def test_holdout_not_promoted(self):
        path = self.write(sample())
        with self.assertRaises(Rejected):
            promote(path,self.decision(path,benchmark_holdout=True),
                self.folder / "curated")


if __name__ == "__main__":
    unittest.main()
