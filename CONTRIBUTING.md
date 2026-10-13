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

## Submit expert redstone feedback (Minecraft, no developer tools needed)

Use **F8 → Feedback & Styles → Community Share** in BuildAI. Select
the failed generated build in *My Build Feedback* first. In the contribution
screen choose the pillar and 1–5 rating; optionally browse to your corrected
`.schem`/supported imported schematic. Supply concrete **expected** and
**observed** behavior (including input combinations and measured outputs),
select whether the correction has really been tested, and explicitly confirm
that you own the work and agree to a **PUBLIC CC BY 4.0** contribution.

Press **Prepare Local ZIP**. The mod makes
`<Minecraft game directory>/buildai/community-submissions/redstone-feedback-*.zip`
without uploading anything. **Open ZIP Folder** to review the package and
**Open Public Submission Page** to open the repository's [redstone
feedback issue template](https://github.com/HancoRabie/BuildAI/issues/new?template=redstone-feedback.md).
Attach the ZIP manually in GitHub's issue editor and submit. A GitHub
account is currently required. This deliberately does **not** send files
over the ChatGPT OAuth connection or to the Minecraft server.

The export strips the original schematic's embedded prompt, model
operation history, command scripts, arbitrary block-entity NBT, player
metadata, world coordinates, filenames/absolute paths, and sign text.
Only block-state geometry and the **feedback text you explicitly entered**
are included. Both generated and corrected geometries are exported in
a clean Sponge `.schem` representation. There is a 16 MB source-schematic
import limit and a 23 MB final-ZIP size limit. Back up all input files;
the exporter does not overwrite them.

If you contribute a correction, identify the exact changes to facing,
power support, isolation, truth-table transitions, timing or reset.
Do **not** label an untested schematic as a verified solution. "Corrected
and tested" means you personally checked its behavior in Minecraft;
maintainers will still reproduce it before treating it as trusted.
Please avoid including private information in free-text feedback.
The GitHub account that posts the issue is publicly visible.

### Dataset quality workflow (maintainers)

- Keep **failure** cases separate from **expert correction** cases and
  **independently reproduced** examples. Never train/evaluate on a
  self-declared "passes" label alone.
- Verify the schema, files, dimension limits, palette and block states;
  import in a disposable world with no command execution or inventory
  side effects. Scan attachments before storing them.
- Reproduce the reported input/outputs, tag Minecraft version and
  redstone mechanics, and record the smallest generalizable failure
  (wrong facing, unsupported component, OR-vs-XOR, missing lamp link, etc.).
- Keep held-out fixed benchmark tasks separate from training examples
  to avoid falsely inflating BuildAI mastery scores.
- Deduplicate by sanitized schematic SHA-256; honor licenses and
  contributor deletion/takedown requests where possible.
- Use the resulting validated corpus for retrieval, procedural
  component references or future model fine-tuning **only after**
  legal, safety and data-quality review. Saving ZIPs does not
  automatically train or improve a model.

## License

By contributing, you agree that your contributions are licensed under [GNU GPL-3.0-only](LICENSE). You must have the necessary rights to submit the code, artwork or tests. Do not copy proprietary schematics or code from third-party mod repositories.

## Compatibility

CI compilation is required but **not enough** to demonstrate in-world safety. The current prototype can excavate and is not crash-safe. Always use temporary test worlds and keep backups.
