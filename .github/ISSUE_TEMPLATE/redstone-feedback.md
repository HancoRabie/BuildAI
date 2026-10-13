---
name: "Redstone expert feedback / schematic correction"
about: "Contribute a reproducible BuildAI redstone failure, verified correction or mastery example"
title: "[Redstone Feedback] "
labels: []
assignees: []
---

## Submission checklist

- [ ] I used BuildAI **F8 → Feedback & Styles → Community Share → Redstone Insight** to create the **schema-v2** ZIP, and I have inspected its contents before posting.
- [ ] I attached the **redstone-insight-....zip** file to this issue. **Drag and drop it into this text area, then wait for GitHub's attachment link to appear.**
- [ ] I am the owner or have permission to redistribute both schematics and evidence under **CC BY 4.0**, including reuse in open datasets, evaluation and AI training.
- [ ] The included schematics were built/tested in Minecraft **Java 1.21.1**, or I clearly state the alternative version below.
- [ ] I understand that this issue and file are **PUBLIC**. GitHub links the contribution to my GitHub profile.

## Files

**Attach the ZIP here:** *(drop the generated ZIP file here before submitting)*

## Reproduction and verification

Pillar or redstone subsystem:

Benchmark ID / benchmark revision (if applicable):

Exporter BuildAI version / Minecraft version / Fabric Loader version (from report.json):

Minecraft / Fabric / BuildAI version (if known):

Describe steps to reproduce the observed failure:

Exact input sequence and observed output:

If providing corrected.schem, how was it verified? (all truth-table cases, repeated cycles, chunk reload, etc.)

## Maintainer review — NOT AUTOMATICALLY ACCEPTED

- Every ZIP starts `review_status=quarantined`, `trust_level=unverified`, and `eligible_for_shared_learning=false`, regardless of contributor ratings or "Corrected and tested" checkboxes.
- Maintainers must run the offline ZIP integrity/schema validator, confirm rights and source version, test corrected geometry in Minecraft, document input/output evidence, and explicitly approve a SEPARATE signed-off review record. Nothing auto-feeds BuildAI or any model.
- Duplicate, unsafe, low-evidence, functionally incorrect, or irrelevant submissions may be rejected. A corrected circuit is not necessarily a correct circuit.
- No attached files will be automatically executed in Minecraft.
- A contributor-reported PASS is not a verified solution until reproduced.
- Maintainers may annotate geometry, circuit component facing, input/output ports, and measured outcomes.
- Contributions may be used for benchmark datasets, reference retrieval and future machine-learning training only when rights and quality checks permit.
- Please **never** attach API keys, OAuth files, world/player databases, raw server logs containing personal data, or private screenshots.

Thanks for making BuildAI a better engineer.
