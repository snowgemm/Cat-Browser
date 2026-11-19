# Cat Browser AI integration

## Active architecture

The Android Cat AI screen uses Google Gemini 3.8 Flash through the Supabase Edge Function at `supabase/functions/cat-ai/`. The legacy Vercel/OpenAI gateway under `ai-gateway/` is not used by the current Android app and is retained temporarily for rollback and CI compatibility.

### Android client

- Uses the existing Supabase project URL and publishable key. The publishable key is public client configuration; no Supabase secret/service-role key is embedded in the APK.
- Creates an anonymous Supabase Auth session automatically through the Auth signup endpoint when no valid session exists.
- Refreshes expired sessions and encrypts the access/refresh token bundle with Android Keystore AES-GCM.
- Does not ask the user to enter a gateway URL, gateway access token, Gemini API key, or Supabase secret.
- Keeps conversation state in memory while the assistant screen is active.
- Sends the user message and the full Gemini Interactions history required for stateless continuation.
- Sends tool results only after the user approves the corresponding browser action.
- Does not automatically send page contents, cookies, credentials, form values, downloads, extension storage, or private-tab metadata.

If the user clears app data or changes devices, the anonymous session cannot be recovered automatically. A new anonymous identity is created on the next use.

### Supabase Edge Function

- Function name: `cat-ai`.
- Uses Supabase's official `withSupabase({ auth: "user" })` wrapper to validate the user JWT against the project's JWKS before processing a request. The platform `verify_jwt` flag is disabled because this project uses the new `sb_publishable_` / `sb_secret_` API-key format; authentication is still enforced by the wrapper.
- Validates the authenticated user JWT before processing a request.
- Reads `GEMINI_API_KEY` only from Supabase Edge Function secrets.
- Uses the Supabase server secret only inside the Edge Function to call the quota RPC. No server secret is sent to Android.
- Sends requests to `POST https://generativelanguage.googleapis.com/v1beta/interactions`.
- Uses `store=false`; each request includes the full client-managed history.
- Preserves model-generated steps exactly, including `thought` and `function_call`, then appends matching `function_result` steps after the app executes approved actions.
- Does not log prompts, tool arguments, response steps, or authorization headers.
- Rejects malformed/oversized history, unknown function names, mismatched tool results, and unresolved function calls.

### Database quota

The `private.cat_ai_usage` table has RLS enabled and is not exposed to the client. The quota RPC is executable only by `service_role`; `anon` and `authenticated` cannot call it.

Current limits:
- Per anonymous/authenticated user: 8 requests per minute and 60 requests per UTC day.
- Global project limit: 50 requests per minute and 500 requests per UTC day.

The limits are server-side and shared across Edge Function instances. Requests that exceed a limit receive HTTP 429 with a retry delay. These limits are protective ceilings, not a guarantee of zero provider cost.

The existing `public.rls_auto_enable()` function no longer grants direct execution to `PUBLIC`, `anon`, or `authenticated`; the event trigger remains installed.

## Browser tool allowlist

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

No tool can access private-tab content, page DOM/text, cookies, passwords, downloads, local files, extension permissions, website permissions, or arbitrary Android intents. The app validates tool names and executes only its fixed allowlist.

Websites, messages, and tool outputs are untrusted. Page content is not automatically included in the assistant context. Tab titles and URLs are sent only when the user approves a tab-related tool call.

## Privacy, retention, and cost

- Gemini Interactions requests use `store=false`; the API does not create a stored interaction resource for the conversation.
- Cat Browser keeps chat history only in memory for the active assistant screen.
- Supabase stores quota counters, not chat messages.
- User messages and approved tool results are still transmitted to Google Gemini for processing.
- Google AI Studio Free Tier may use submitted prompts and responses to improve Google products. `store=false` does not override the Free Tier data-use terms. Do not send personal, confidential, or sensitive information.
- Paid Gemini API usage has separate billing. If billing is enabled, a parent or guardian should manage the account and spending limits.
- Clearing app data or moving to another device loses the anonymous session unless a future account-linking/recovery feature is added.

Official references:
- Supabase anonymous sign-ins: https://supabase.com/docs/guides/auth/auth-anonymous
- Supabase Edge Functions: https://supabase.com/docs/guides/functions
- Gemini Interactions API: https://ai.google.dev/gemini-api/docs/interactions-overview
- Gemini stateless conversations and function calling: https://ai.google.dev/gemini-api/docs/get-started
- Gemini pricing and data-use terms: https://ai.google.dev/gemini-api/docs/pricing

## Remaining validation

The backend is deployed, but a successful deployment does not prove end-to-end operation from a physical Android device. Before treating the migration as complete, verify on-device:

1. Anonymous Auth creates a session without manual configuration.
2. The session survives app restart and refreshes after expiry.
3. The Settings connection check confirms Gemini model access.
4. A text-only conversation succeeds.
5. A browser tool call appears in the approval dialog, runs only after approval, and returns its result to Gemini.
6. Denying a tool call is handled correctly.
7. Rate-limit responses are displayed without crashing the app.
8. Private tabs and page contents remain excluded.

Do not remove the legacy Vercel project or its rollback configuration until these checks pass. The legacy gateway is not the active Android backend.
