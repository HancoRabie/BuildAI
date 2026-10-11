# BuildAI Security Policy

## Current status

BuildAI v0.1 is a development prototype. It is **not recommended for valuable Minecraft worlds or public multiplayer servers**. The construction engine excavates and alters blocks, project snapshots are currently in memory, and rollback does not guarantee complete recovery.

## Reporting security problems

Please do not post API keys, OAuth tokens, secrets or private world files in public GitHub issues. Use GitHub's private vulnerability reporting feature if enabled for the repository, or contact the repository owner privately through an established channel. If private reporting is unavailable, create a public issue containing only a generic request to establish a private reporting path; do not include exploit details.

Include affected versions, a minimal reproduction, expected vs actual results and whether a real world or user data could be affected.

## Credentials and authentication

- OpenAI API keys must remain client-local. Do not send credentials through Minecraft network packets or write them into game logs.
- Future ChatGPT OAuth sign-in must follow the official flow with PKCE, state and nonce checks, local secure session storage, refresh and revocation.
- ChatGPT usernames or passwords must never be collected by the mod.
- Avoid logging full authorization URLs or bearer tokens.
- Imported schematics and AI-generated blueprints are untrusted input and must be validated server-side.

## In-world safety requirements

- Reject non-operator/world-protected construction in prototype deployments.
- Always warn for unknown-origin terrain collisions.
- Block unsafe block entities, inventories, command block NBT and non-restorable blocks.
- Do not claim successful crash-safe construction until durable project journaling has been implemented and tested.
- Do not enable public world editing on community servers before claim/permission checks and abuse-rate limits exist.
