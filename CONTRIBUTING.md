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

## License

By contributing, you agree that your contributions are licensed under [GNU GPL-3.0-only](LICENSE). You must have the necessary rights to submit the code, artwork or tests. Do not copy proprietary schematics or code from third-party mod repositories.

## Compatibility

CI compilation is required but **not enough** to demonstrate in-world safety. The current prototype can excavate and is not crash-safe. Always use temporary test worlds and keep backups.
