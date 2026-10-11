# Open-source distribution and ChatGPT authentication

## Current repository

BuildAI is distributed from https://github.com/HancoRabie/BuildAI under the **GNU GPL-3.0-only** license. Mod metadata declares the same license.

**Public source code does not automatically mean ChatGPT authentication works.** The current F8 AI generation uses a separate OpenAI developer API key. Current private Minecraft Chat Mode sends prompts through that API-key integration, not through ChatGPT sign-in.

## Target experience

1. Open F8 and select **Continue with ChatGPT**.
2. Minecraft opens the user's **system browser** for OpenAI account authentication and permission to use the user's eligible ChatGPT plan.
3. The local BuildAI client finishes the supported OpenAI OAuth flow with PKCE, a persistent local host ID, the issued dynamic client ID and secured credentials. Login should not be repeated after every mod update.
4. Enable BuildAI Chat Mode, type a building description in Minecraft chat. The mod sends eligible requests through its authorized OpenAI Responses API session.
5. Convert the model's geometry instructions into a bounded canonical blueprint and present it for revision, preview, placement and explicit construction approval.
6. A user can revise a design in chat and receive a fresh blueprint. BuildAI does not receive ChatGPT app chat history or memories automatically.
7. The construction engine and file importers remain usable offline and without AI login.

## Official developer docs (checked 2026-10-11)

- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/siwc/token-sharing-open-source/sign-in
- https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions
- https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt

Supported open-source applications can use dynamic agent client registration. A user must separately grant eligible plan-usage permissions. Plan availability and limits depend on the user, workspace and OpenAI product support.

## Release / integration checklist

- [x] Public repository and GPLv3 source license.
- [x] Compile-verified Fabric 1.21.1 prototype and private Minecraft Chat Mode based on API-key calls.
- [x] In-game How To and one-design conversational revision controls.
- [ ] OAuth browser flow using dynamic registration (`client_id=dynamic_agent_client` for initial registration), unique persisted `ext_agent_host_id`, state + nonce + PKCE, and local callback.
- [ ] Validate ID token against published JWKS and check `chatgpt.tokens.use.direct` scope; handle consent declined, ineligible account, workspace restrictions.
- [ ] Protected per-user OAuth credential storage, refresh, logout/revoke, account switch and rate-limit errors.
- [ ] Use eligible model list and **Responses API** with `store:false` and `stream:true`; check `response.completed` before building.
- [ ] Connect user chat to generated blueprints and show clear progress/error/status within Minecraft.
- [ ] Validate a real end-to-end OAuth → generate → revise → preview → construct test in a disposable Minecraft world.
- [ ] Implement persistent world backups and project journaling before promoting BuildAI for ordinary Survival or public servers.

Do not advertise account sign-in as functional until the complete flow passes a real user acceptance test.
