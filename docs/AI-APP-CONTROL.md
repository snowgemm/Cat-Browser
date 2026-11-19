# Cat Browser AI integration

## What is implemented

Cat Browser now contains an optional Cat AI screen that uses the OpenAI Responses API through the repository's self-hostable ai-gateway service.

The Android client:
- Stores the gateway URL and a separate gateway bearer token.
- Encrypts the gateway token with Android Keystore AES-GCM.
- Keeps the conversation in memory while the assistant screen is active.
- Sends the user's chat text and the minimum tool results needed for the active conversation.
- Replays Responses API output items locally with store=false; it does not create a persistent Responses API conversation.
- Requests explicit approval before every browser tool call.
- Excludes private tabs from tab-list and active-tab tools.
- Does not automatically send page contents, browsing history, cookies, credentials, form values, downloads or extension storage.

The OpenAI API key belongs only in the gateway's server environment. It must never be added to Android resources, source code, app preferences or logs.

The Cat AI Gateway source is in ai-gateway/. It is not deployed by this repository and has no API key or gateway token committed. Until an owner deploys it and configures both app and server, the Cat AI screen remains unavailable and shows a setup message.

## Current tool allowlist

| Tool | Behavior | App approval |
|---|---|---|
| list_tabs | List normal tabs by ID, title and URL | Required |
| get_active_tab | Return selected normal tab metadata only | Required |
| open_url | Open a validated HTTP(S) URL in a new normal tab | Required |
| switch_tab | Switch to an existing normal tab ID | Required |
| go_back | Navigate back in the selected normal tab | Required |
| go_forward | Navigate forward in the selected normal tab | Required |
| go_home | Open the configured homepage | Required |
| bookmark_current_page | Add the selected normal page to bookmarks | Required |

No tool can access private-tab content, page DOM/text, cookies, passwords, downloads, local files, extension permissions, website permissions or arbitrary Android intents. The app validates every tool name and argument before action execution. The server declares the same strict tool allowlist.

Websites, messages and tool outputs are untrusted. Page content is not included in the assistant context, which also reduces exposure to website prompt injection.

## Gateway configuration

See ai-gateway/README.md for environment variables, deployment root and setup.

Required server environment variables:
- OPENAI_API_KEY: OpenAI API key stored only in the hosting provider's secret manager.
- CAT_AI_GATEWAY_TOKEN: random, unique bearer token used only between Cat Browser and this gateway.
- OPENAI_MODEL: optional model override; defaults to gpt-6-luna.

In Cat Browser, open Settings → Cat AI and enter the gateway HTTPS base URL plus the gateway token. The app token is encrypted using Android Keystore. A gateway deployment is not automatically created because it needs the account owner's API credential, billing review and secret setup.

OpenAI API usage is billed separately from ChatGPT subscription plans. The account owner should configure budgets, usage limits and rate controls. Users under 18 should have a parent or guardian manage any account and billing that require an adult.

## Data retention and operational limits

- Requests use the OpenAI Responses API with store=false. Stateless replay retains the encrypted reasoning items locally in the active app screen only. See the official Responses API migration guide: https://developers.openai.com/api/docs/guides/migrate-to-responses.
- Cat app chat history is not persisted to SharedPreferences or a Cat backend.
- The gateway rejects oversized requests, limits the number of input items and tool calls, and has a best-effort per-instance rate throttle.
- The in-memory throttle is not a global rate limiter across serverless instances. Before public release, add hosting-level rate limits/firewall rules, usage alerts and monitoring.
- The gateway must run over HTTPS. Rotate the gateway token if it is exposed.
- Avoid logging authorization headers, prompts, tool arguments or response items.
- The server currently returns a normal JSON response; streaming and cancellation are not implemented.

## ChatGPT app control is a separate integration

The Cat AI screen calls the OpenAI API from Cat Browser. It is not the ChatGPT Android app remotely controlling Cat.

ChatGPT custom MCP apps require a remotely reachable server and the current OpenAI documentation lists custom MCP app availability as web-only, with workspace/plan restrictions for write-capable actions. A phone-local MCP server is not directly reachable from ChatGPT. Do not display a "Connected to ChatGPT" state unless a supported ChatGPT client, workspace, authenticated remote MCP relay and end-to-end device pairing have all been implemented and tested.

Official platform references:
- ChatGPT custom MCP server connections: https://help.openai.com/en/articles/12584461-connecting-custom-mcp-servers-in-chatgpt
- OpenAI developer mode and MCP connectors: https://help.openai.com/en/articles/12584461-developer-mode-and-full-mcp-connectors-in-chatgpt-beta
- Responses API and function calling: https://developers.openai.com/api/docs/guides/function-calling
- OpenAI API key security: https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety

## Future provider expansion

Keep app-side browser tools independent from the model provider. Additional providers can be added behind a provider adapter only after each one has been evaluated for API behavior, cost, data retention, privacy, tool-calling reliability and account terms. Do not give a new provider broader browser permissions by default.

## Acceptance criteria before public release

- Gateway deployed by the account owner and protected by a high-entropy access token.
- OpenAI API key only in the server secret manager.
- Hosting-level rate limits, budget alerts and token rotation documented.
- Android tool execution is user-approved, typed and allowlisted.
- Private tabs, cookies, credentials, downloads, extension data and page contents remain excluded.
- API store=false is preserved and conversation state is not persisted by Cat.
- End-to-end tests cover invalid credentials, network failures, malformed tool arguments, user denial, private-tab exclusion and tab-limit behavior.
- Physical-device tests complete on supported Android versions.
