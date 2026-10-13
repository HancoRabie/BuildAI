#!/usr/bin/env python3
"""Offline maintainer-only Community Feedback intake and explicit promotion.

Uses only the Python standard library. The contributor's ZIP, including its
self-declared verification status, is ALWAYS UNTRUSTED. This script does not
train a model, publish schematics, or accept PR/issue content automatically.

  python scripts/review_community_feedback.py validate contribution.zip
  python scripts/review_community_feedback.py promote contribution.zip \
      --decision reviewed.json --destination curated/

The signed-in maintainer must independently inspect evidence and prepare
the decision JSON OUTSIDE the contributor's ZIP. GitHub branch protection and
CODEOWNERS must be enforced separately before any public pipeline ingest.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

SCHEMA = "buildai-community-example-v2"
CATEGORIES = {
    "redstone_insight": ("redstone_insights", "approved_redstone_insight"),
    "build_preference": ("build_preferences", "approved_style_example"),
}
MAX_ZIP = 23_000_000
MAX_UNCOMPRESSED = 40_000_000
ALLOWED = {"report.json", "README.txt", "generated.schem", "corrected.schem"}


class Rejected(Exception):
    pass


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_and_validate(archive):
    path = Path(archive)
    if not path.is_file() or path.stat().st_size > MAX_ZIP:
        raise Rejected("Archive missing or larger than permitted limit.")
    try:
        with zipfile.ZipFile(path, "r") as container:
            infos = container.infolist()
            names = [info.filename for info in infos]
            if (len(names) != len(set(names)) or
                any(name not in ALLOWED for name in names) or
                not {"report.json", "README.txt", "generated.schem"}.issubset(names)):
                raise Rejected("Unexpected/duplicate ZIP entries; reject unsafe payload.")
            if sum(info.file_size for info in infos) > MAX_UNCOMPRESSED:
                raise Rejected("Expanded archive exceeds limits.")
            for info in infos:
                if info.file_size > 16_000_000:
                    raise Rejected(f"Entry too large: {info.filename}")
                if info.flag_bits & 0x1:
                    raise Rejected("Password-encrypted ZIP entries not accepted.")
            members = {name: container.read(name) for name in names}
    except (zipfile.BadZipFile, OSError, EOFError) as problem:
        raise Rejected(f"Cannot read ZIP: {problem}") from problem
    try:
        report = json.loads(members["report.json"].decode("utf-8"))
    except (ValueError, UnicodeError) as problem:
        raise Rejected(f"Invalid report.json: {problem}") from problem
    if not isinstance(report, dict):
        raise Rejected("Report must be one JSON object.")
    if report.get("schema") != SCHEMA or report.get("schema_version") != 2:
        raise Rejected("Wrong or unsupported schema version.")
    category = report.get("category")
    if category not in CATEGORIES:
        raise Rejected("Unknown feedback category.")
    namespace, _ = CATEGORIES[category]
    if report.get("namespace") != namespace:
        raise Rejected("Feedback lane and namespace disagree.")
    if (report.get("review_status") != "quarantined" or
        report.get("trust_level") != "unverified" or
        report.get("eligible_for_shared_learning") is not False or
        report.get("reviewer_verified") is not False or
        report.get("contains_verified_solution") is not False):
        raise Rejected("Contributor must never declare an approved or trusted record.")
    if report.get("rights_confirmed") is not True or report.get("license") != "CC BY 4.0":
        raise Rejected("Rights and public contribution license are required.")
    for key in ("exporter_buildai_version", "exporter_source_revision",
                "minecraft_version",
                "fabric_loader_version", "source_generation_version",
                "benchmark_id", "benchmark_revision"):
        if not isinstance(report.get(key), str):
            raise Rejected(f"Missing version metadata field: {key}")
    if report.get("minecraft_edition") != "Java":
        raise Rejected("This pipeline only supports Minecraft Java Edition.")
    for key in ("expected", "observed"):
        if not isinstance(report.get(key), str) or not report[key].strip():
            raise Rejected("Expected and observed behavior must be supplied.")
    if category == "redstone_insight":
        if report.get("contributor_claim") not in (
            "Failure observed", "Correction unverified", "Corrected and tested"
        ):
            raise Rejected("Bad contributor redstone claim.")
        if (report["contributor_claim"] == "Corrected and tested" and
            "corrected.schem" not in members):
            raise Rejected("Claimed tested correction has no corrected geometry.")
    elif (report.get("contributor_claim") != "Subjective preference" or
          report.get("pillar") != "Not applicable"):
        raise Rejected("A style preference cannot masquerade as a redstone pass.")
    for member, key in (("generated.schem", "generated_sha256"),
                        ("corrected.schem", "corrected_sha256")):
        if member in members:
            if digest(members[member]) != report.get(key):
                raise Rejected(f"{member} SHA-256 mismatch.")
            if not members[member].startswith(b"\x1f\x8b"):
                raise Rejected(f"{member} is not a GZip-compressed schematic.")
        elif key in report:
            raise Rejected(f"{key} supplied without {member}")
    # No schematic execution, world loading, model evaluation or trust promotion.
    return report, digest(path.read_bytes())


def promote(archive, decision_path, destination):
    report, archive_hash = read_and_validate(archive)
    try:
        decision = json.loads(Path(decision_path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as problem:
        raise Rejected(f"Invalid MAINTAINER decision file: {problem}") from problem
    category = report["category"]
    namespace, approval = CATEGORIES[category]
    if (decision.get("decision") != "approve" or
        decision.get("category") != category or
        decision.get("source_zip_sha256") != archive_hash or
        decision.get("rights_reviewed") is not True or
        not str(decision.get("reviewer", "")).strip() or
        not str(decision.get("review_notes", "")).strip()):
        raise Rejected("Explicit independent decision, category, license check, "+
                       "reviewer notes and exact ZIP checksum required.")
    if category == "redstone_insight":
        if (not decision.get("independent_minecraft_test") or
            not str(decision.get("test_evidence", "")).strip() or
            decision.get("verified_minecraft_version") != report["minecraft_version"] or
            "corrected_sha256" not in report):
            raise Rejected("Redstone promotion requires independently tested "+
                           "corrected schematic and version-specific evidence.")
    if decision.get("benchmark_holdout") is True:
        raise Rejected("Held-out benchmark cases must not enter training examples.")
    target = Path(destination) / namespace
    target.mkdir(parents=True, exist_ok=True)
    # Reject multiple submissions of the SAME original and correction even
    # when ZIP timestamps / free-text notes produce a different ZIP hash.
    for existing in target.glob("*.review.json"):
        try:
            approved = json.loads(existing.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            raise Rejected(f"Cannot verify existing curation record: {existing}")
        if (approved.get("original_schematic_sha256") == report["generated_sha256"] and
            approved.get("corrected_schematic_sha256") ==
                report.get("corrected_sha256")):
            raise Rejected("Duplicate approved schematic geometry pair in this lane.")
    prefix = archive_hash[:16]
    output = target / (prefix + ".zip")
    attestation = target / (prefix + ".review.json")
    if output.exists() or attestation.exists():
        raise Rejected("This exact ZIP was already curated; no silent overwrite.")
    # The approved label exists ONLY outside the contributor-controlled ZIP.
    # Never change review_status inside submitted report.json.
    result = {
        "review_schema": "buildai-curation-v1",
        "curation_version": 1,
        "source_zip_sha256": archive_hash,
        "original_schematic_sha256": report["generated_sha256"],
        "corrected_schematic_sha256": report.get("corrected_sha256"),
        "category": category,
        "namespace": namespace,
        "curated_status": approval,
        "reviewer": decision["reviewer"],
        "review_notes": decision["review_notes"],
        "rights_reviewed": True,
        "independent_minecraft_test": bool(decision.get("independent_minecraft_test")),
        "test_evidence": decision.get("test_evidence", ""),
        "verified_minecraft_version": decision.get("verified_minecraft_version", ""),
        "benchmark_id": report.get("benchmark_id", ""),
        "benchmark_revision": report.get("benchmark_revision", ""),
        "exporter_buildai_version": report["exporter_buildai_version"],
        "exporter_source_revision": report["exporter_source_revision"],
        "schema_version": report["schema_version"],
        "training_eligible": True,
    }
    shutil.copyfile(archive, output)
    try:
        attestation.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    except Exception:
        output.unlink(missing_ok=True)
        raise
    return output, attestation


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["validate", "promote"])
    parser.add_argument("archive")
    parser.add_argument("--decision", help="Maintainer-written review JSON")
    parser.add_argument("--destination", help="Destination for approved, separated lanes")
    args = parser.parse_args(argv)
    try:
        if args.action == "validate":
            report, zip_hash = read_and_validate(args.archive)
            print(json.dumps({
                "result": "VALID PACKAGE; STILL QUARANTINED",
                "category": report["category"],
                "schema": report["schema"],
                "exporter_buildai_version": report["exporter_buildai_version"],
                "exporter_source_revision": report["exporter_source_revision"],
                "minecraft_version": report["minecraft_version"],
                "zip_sha256": zip_hash
            }, indent=2))
        else:
            if not args.decision or not args.destination:
                parser.error("promote requires --decision and --destination")
            files = promote(args.archive, args.decision, args.destination)
            print("Curated files (maintainer-only, NOT auto-learned):", *files, sep="\n")
    except Rejected as error:
        print("REJECTED:", error, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
