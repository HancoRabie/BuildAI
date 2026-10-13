# Contributing to BuildAI

Welcome! BuildAI is a community-built Minecraft Java **Fabric 1.21.1** mod for AI-generated architecture, imported blueprints, and survival-friendly construction.

## Before changing code

- Build in **Java 21**. Open the repository in IntelliJ IDEA and run `gradle build` or `.\gradlew.bat build` on Windows.
- Please open a GitHub issue before large architectural changes.
- Keep BuildAI independent of WorldEdit, Litematica, ChainFrames, PinLock or any other gameplay mod. Fabric API is permitted.
- Work in a topic branch and propose a pull request. Describe behavior changes and a reproducible test scenario.
- Do not submit unrelated reformatting of the codebase in a behavioral fix.
- When modifying schematic importers, include fixtures or documentation for format version, palette ordering, offsets and exact block states.
- When modifying construction or material handling, test Survival pause/resume, excavation boundaries, inventory consumption, cancellation and an undo scenario.
- Redstone logic must be distinguished between plausible structure generation and **functionally verified** mechanisms.

## Secure development

**Never commit** OpenAI API keys, OAuth tokens, refresh tokens, session cookies, authorization URLs, local user profiles or world files containing private player data. Use OS-protected storage for future login credentials.

For vulnerabilities or security problems involving unsafe world mutation or auth credentials, follow [SECURITY.md](SECURITY.md) instead of publicly posting exploitable details.

## Contribute Community Feedback (versioned v2)

BuildAI's private review and personal style memory remain separate from
PUBLIC community feedback. From **F8 → Feedback & Styles → Community
Share**, choose an explicit category:

| Category | What contributors provide | What it can become |
|---|---|---|
| **Build Preference** (`build_preferences`) | Subjective before/after design comparison, colors/materials, preferences, notes | Reviewed *optional style examples*; never universal taste or electrical truth |
| **Redstone Insight** (`redstone_insights`) | Wrong/corrected functional circuits, full input sequences, physical results, redstone pillar and repeatable evidence | Reviewed technical references **only after independent Java Minecraft reproduction** |

The Minecraft form provides a native picker for the optional comparison
schematic, pillar when applicable, self-reported test status, ratings,
expected versus observed results and an optional benchmark ID/revision.
It records installed **BuildAI mod version, compiled Git source revision,
Fabric Loader version and Minecraft version**. The original schematic's
generation version is recorded as **unknown** unless independently known;
do not infer one from the current exporter.

All contributions require explicit consent to **PUBLIC CC BY 4.0**
redistribution. **Prepare Local ZIP** writes
`<Minecraft game directory>/buildai/community-submissions/` and never
uploads it automatically. **Open ZIP Folder** lets you inspect files.
**Open Public Submission Page** opens one of two GitHub issue templates;
you must attach the ZIP yourself. GitHub shows your account publicly.

Each archive uses `buildai-community-example-v2` and contains a
`report.json`, anonymized `generated.schem`, optional
`corrected.schem` and `README.txt`. It excludes original prompts,
model history, scripts, tile-entity NBT, sign text and source paths;
the public free-text explanation is included, so review it.

### Trust and intake gates

**All user-submitted records are untrusted**. Ratings, "Corrected and
tested" checkboxes, and GitHub reputation cannot approve them.
A contributor cannot set `review_status=approved` or
`eligible_for_shared_learning=true` in an accepted archive. The
immutable v2 export starts as `quarantined`, `unverified`, and
`eligible_for_shared_learning=false`.

Maintainers may validate untrusted ZIPs **offline**:

```bash
python3 scripts/review_community_feedback.py validate path/to/submission.zip
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

This checks allowed entries, hashes, schema/version fields, category,
trust labels and file bounds. It does **not** test Minecraft mechanics
and does not interpret contributor claims as proof.

A maintainer who separately verifies a submission may prepare an
independent JSON decision, explicitly listing `decision=approve`,
`category`, `source_zip_sha256`, `rights_reviewed=true`,
`reviewer`, `review_notes`, and for **Redstone Insight** specifically
`independent_minecraft_test=true`, `test_evidence` and
`verified_minecraft_version` matching the versioned report.
The approved example must have a corrected schematic. Set
`benchmark_holdout=true` for held-out evaluation examples so the
script refuses training promotion.

```bash
python3 scripts/review_community_feedback.py promote path/to/submission.zip \
  --decision path/to/maintainer-review.json --destination local-curated/
```

The script saves a **separate**, reviewer-attested metadata file into
`redstone_insights/` or `build_preferences/`; it never rewrites an
untrusted contributor report to forge verification. This is an offline
curation utility, **not reviewer authentication**: a production pipeline
must require restricted maintainer access, protected branches, signed
review records or equivalent access control, deduplication and holdout
enforcement. Nothing automatically modifies BuildAI's knowledge or
trains a model today.

For redstone review, test **all truth table inputs, output connectivity,
retrigger/reload states, facing, block support, power strength and
Java-specific mechanics** in a disposable Minecraft world. Reject
spam, low-evidence schematics, invalid claims and unrelated data.
For style reviews, judge relevance and rights without promoting
subjective choices into redstone facts.

### Versioning and benchmark integrity

The feedback schema (`v2`), curation-attestation schema (`v1`),
Minecraft Java version, BuildAI Git commit SHA, and optional benchmark
ID/revision are separate version axes. A later Minecraft version or
BuildAI refactor must not silently overwrite earlier verified evidence.
Keep fixed benchmark test prompts held out from training and keep
verifier notes linked to the exact source ZIP checksum.

## License

By contributing, you agree that your contributions are licensed under [GNU GPL-3.0-only](LICENSE). You must have the necessary rights to submit the code, artwork or tests. Do not copy proprietary schematics or code from third-party mod repositories.

## Compatibility

CI compilation is required but **not enough** to demonstrate in-world safety. The current prototype can excavate and is not crash-safe. Always use temporary test worlds and keep backups.
