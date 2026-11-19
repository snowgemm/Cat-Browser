# Cat Browser AI Gateway

This is a small Vercel Node.js function that relays approved Cat Browser assistant requests to the OpenAI Responses API.

The Android app does **not** contain an OpenAI API key. It stores only a separate gateway access token, encrypted with Android Keystore.

## Required environment variables

Set these on the Vercel project, for Preview and Production as appropriate:

| Variable | Value |
|---|---|
| `OPENAI_API_KEY` | OpenAI API key for the project |
| `CAT_AI_GATEWAY_TOKEN` | Random, unique bearer token used only by this Cat Browser gateway |
| `OPENAI_MODEL` | Optional; defaults to `gpt-6-luna` |

Generate a gateway token with a trusted local password manager or cryptographic random generator, for example:

```bash
openssl rand -hex 32
```

Do not use the OpenAI API key as the gateway token. Do not commit either value to Git, put them in an APK, paste them into an issue, or include them in logs.

## Current Vercel project

A Vercel Hobby project has been created and linked to `snowgemm/Cat-Browser`:

- Project: `cat-browser-ai-gateway`
- Project ID: `prj_cbiMZz3C2VnPZS1JUEeLkrQo1JB4`
- Root Directory: `ai-gateway`
- Runtime: Node.js `22.x`
- Production branch: `main`

The linked `main` branch has generated production deployments that reached `READY`. Vercel automatically creates a new deployment after each push, so the Vercel project dashboard is the source of truth for the current deployment URL.

However, **the API is not operational yet**. No environment variables and no custom domain are configured. Requests reach the handler only after deployment protection is passed, and the handler returns HTTP 503 until `OPENAI_API_KEY` and `CAT_AI_GATEWAY_TOKEN` are set.

Vercel currently has SSO protection enabled for all deployment domains except custom domains. A native Android client cannot complete Vercel's interactive SSO flow. Before enabling the Android connection, attach a domain you control (custom domains are outside this SSO rule), or deliberately review the deployment-protection setting only after a high-entropy gateway token and hosting-level rate limits are in place. Do not leave an unauthenticated endpoint exposed.

## Deploy

1. Create or select a Vercel project for this repository.
2. Set the project's Root Directory to `ai-gateway`.
3. Use the Node.js 22 runtime.
4. Add the environment variables above in Vercel Project Settings.
5. Deploy the project over HTTPS.
6. In Cat Browser, open Settings → Cat AI and enter the deployment base URL and the `CAT_AI_GATEWAY_TOKEN`.
7. The OpenAI API key remains only in the server environment.

No OpenAI key setup or deployment is performed automatically by this source code. The account owner must review the API billing settings, usage limits and gateway access before enabling it. Users under 18 should have a parent or guardian manage any account and billing that require an adult.

## Connection check

The same authenticated endpoint also supports `GET /api/assistant`. It:
- checks the gateway bearer token;
- checks whether the configured OpenAI API key can retrieve the configured model using `GET /v1/models/{model}`;
- returns only `{ "ok": true, "model": "..." }` when successful;
- does not create a model response or submit user chat content.

Cat Browser Settings uses this request to verify HTTPS routing, gateway authentication and access to the configured model before starting a chat. A 3xx response is treated as a redirect/SSO gate; do not disable deployment protection just to make the check pass.

## Request contract

Android sends:

```json
{
  "inputItems": [
    { "role": "user", "content": [{ "type": "input_text", "text": "..." }] }
  ]
}
```

The gateway calls `POST https://api.openai.com/v1/responses` with `store: false`, then returns:
- `outputText`
- `toolCalls` (a normalized list of declared browser functions)
- `responseItems` (the Responses API output items needed for stateless continuation)

The app replays these items in memory and clears them when the user starts a new chat or leaves the assistant screen. It does not ask the gateway to fetch page contents.

## Tool allowlist

The gateway exposes only these app-side actions:

- `list_tabs` — list regular tabs; private tabs are filtered by the app.
- `get_active_tab` — get regular active tab title and URL only.
- `open_url` — open a user-requested HTTP(S) URL in a new regular tab.
- `switch_tab` — switch to a regular tab by ID.
- `go_back`, `go_forward`, `go_home`.
- `bookmark_current_page`.

Cat Browser displays a fresh approval dialog for every function call. The app validates arguments again before running the action. The model cannot run JavaScript, shell commands, arbitrary Android intents, downloads, extension changes, site permission changes, data clearing or file operations.

## Privacy and limits

- The system prompt and app both exclude private tabs and private history.
- Page text, cookies, passwords, form values, downloads and extension storage are not sent to the model.
- Responses are created with `store: false`; the gateway does not save conversation items.
- The Android app holds conversation state in memory for the active assistant screen only.
- The gateway validates Responses API assistant message items, encrypted reasoning items, and matching function-call/function-output pairs before stateless replay.
- The gateway enforces request-size limits using UTF-8 byte size, input-item limits and a small per-instance request throttle.
- The in-memory throttle is best-effort only; serverless instances do not share it. Before public distribution, configure Vercel Firewall/rate limits and monitor provider usage.
- Restrict who can obtain the gateway token. Rotate it if it is exposed.
- Keep the OpenAI API project on conservative budget/usage limits. API usage is billed separately from ChatGPT subscriptions.

## Local checks

```bash
npm run check
npm test
```

These local checks use mocked upstream responses; they do not call OpenAI, require secrets, or test a deployed Vercel environment.
