# AI control for Cat Browser

## Product direction

OpenAI is the first provider. Keep the browser action layer provider-neutral so other providers can be added later without giving each model unrestricted Android access.

There are two different integration modes:

1. **Assistant inside Cat Browser**: Cat calls the OpenAI API and offers a small allowlist of browser tools. This can run as part of Cat but is not the ChatGPT Android app remotely controlling Cat.
2. **ChatGPT app controlling Cat**: requires an internet-reachable MCP app/server. ChatGPT's current official documentation says custom MCP apps are web-only, cannot connect directly to a phone-local server, and full write actions are currently available to eligible Business and Enterprise/Edu workspaces. It therefore cannot be advertised as an available ChatGPT Android-app integration today.

Official references:
- https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt
- https://help.openai.com/en/articles/12584461-developer-mode-and-full-mcp-connectors-in-chatgpt-beta
- https://developers.openai.com/api/docs/guides/function-calling
- https://developers.openai.com/api/docs/guides/tools

## Phase 1 — OpenAI assistant inside Cat

- Call the OpenAI Responses API through a trusted backend; never ship a shared API key in the APK.
- Use function calling with strict allowlists, typed argument schemas, timeouts, rate limits and idempotency.
- Start with read-only tools: get active-tab title/URL, list tabs, read user-requested page text, search bookmarks/history.
- Add user-confirmed actions: open URL, create/switch tab, add/remove bookmark and find in page.
- Require a fresh confirmation for high-impact actions: close tabs, download files, clear browsing data, grant site/extension permissions or disclose private-tab content.
- Exclude private tabs and history by default. Do not place them in background prompts.
- Show which page data will be sent and what action will execute.
- Treat all page text, extension messages and retrieved content as untrusted data; prompt injection must not expand tools or bypass approval.

## Phase 2 — ChatGPT remote control

Only if the user's ChatGPT plan, workspace and client support custom MCP apps:
- The phone companion uses an outbound authenticated channel to a relay. Do not expose an unauthenticated local listener.
- The remote MCP service pairs one device using a short-lived code, scopes tools to that paired device and supports expiry/revocation.
- Start with read-only actions; write actions require explicit per-action approval in Cat.
- Never expose cookies, passwords, complete browsing history, private tabs, extension data or arbitrary local files.
- Log action names and results without logging page contents, tokens or secrets.
- Use HTTPS, one-time pairing, rate limits, replay protection and revocation.
- Never allow model-generated shell commands, arbitrary JavaScript, filesystem paths or unrestricted Android intents.

## Developer unlock code

`dev-cat` only reveals local diagnostics. It is not authentication, DRM or a security boundary because the application is open source. It must never protect secrets, permissions, private data or remote control.

## Acceptance criteria

- No provider API key or relay secret in the APK, repository or logs.
- Every tool has a typed schema and app/server-side validation.
- Important actions request confirmation at execution time.
- Website content cannot inject new tools or skip confirmation.
- Private browsing stays excluded unless the user explicitly approves a specific disclosure.
- External ChatGPT control remains labelled unavailable until it passes an end-to-end test on a supported ChatGPT client and workspace.
