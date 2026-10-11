# BuildAI authentication strategy

## User-facing goals

BuildAI should offer **Continue with ChatGPT**, then directly create and revise blueprint files through the approved model connection. Those blueprints should remain editable and enter the BuildAI placement flow without users hunting for generated files.

**Current status (2026-10-11): not implemented.** The first development build uses an optional local, separately billed OpenAI API key. ChatGPT credentials, browser cookies, access tokens and passwords must not be placed into the API key field.

## Official platform constraints

OpenAI currently supports Sign in with ChatGPT for participating partners and eligible open-source/local clients, with selected private apps. Sign-in/identity and delegated ChatGPT plan usage are separate permissions. BuildAI's GitHub repository is private; enrollment, client eligibility and setup must be confirmed before exposing a functioning login.

Documentation:
- https://developers.openai.com/siwc/quickstart
- https://developers.openai.com/siwc/token-sharing-open-source
- https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt
- https://help.openai.com/en/articles/20001410-sign-in-with-chatgpt

## Planned implementation

1. Obtain appropriate developer client registration or confirm an eligible documented local-host sign-in method for this application.
2. Implement an explicitly approved, browser-mediated OAuth flow with PKCE, unique per-installation host ID, and a loopback callback or the prescribed redirect scheme.
3. Store only necessary encrypted tokens locally, using an OS-appropriate secure credential store; never copy tokens into Minecraft world or server packets.
4. Handle token refresh, expired sessions, user revocation, multiple ChatGPT profiles, and canceling consent.
5. Use delegated access tokens only with the documented eligible Responses API endpoint or authorized inference harness; do not send them as ordinary API keys into an unrelated endpoint.
6. Show whether plan usage is authorized and provide a way to manage/revoke access.
7. Preserve optional API-key mode as a distinct account provider.
8. Keep generated blueprints server-validated and leave excavation behind explicit approval.

Never advertise a fake Continue with ChatGPT button or silently substitute an API-key login for ChatGPT identity sign-in.

## In-game revision sessions

The current first-cut revision feature remembers the last AI-generated primitive-operation JSON in client memory. A follow-up can request changes and the model returns a complete replacement design, which is compiled and displayed for approval. Original designs remain unchanged if a revision fails.

This first cut does not preserve these AI sessions across Minecraft restarts and does not guarantee unrequested architectural details survive a model rewrite. In the future, add versioned immutable blueprint history and a constrained edit/diff operation format.
