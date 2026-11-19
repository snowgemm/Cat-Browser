# Legacy Cat Browser AI Gateway

> **Deprecated for the current Android app.** The active Cat AI backend is the Supabase Edge Function in `supabase/functions/cat-ai/`, which uses Gemini Interactions API. Android no longer calls this Vercel/OpenAI gateway and no longer asks users for a gateway URL or access token.

This directory is retained temporarily for rollback and existing mocked CI tests. Do not deploy it as the active Cat AI backend, and do not add provider credentials to this directory.

The current Supabase/Gemini architecture, authentication flow, quotas, privacy notes and device acceptance checks are documented in [AI-APP-CONTROL.md](../docs/AI-APP-CONTROL.md).

The legacy source and tests may be removed after the Supabase/Gemini implementation has passed end-to-end tests on supported physical Android devices and the owner explicitly approves decommissioning the old Vercel project.

## Legacy test compatibility

The tests in this folder mock the old Vercel/OpenAI handler and do not call any provider API. They remain in CI temporarily so rollback code does not regress while the new backend is validated.

```bash
npm run check
npm test
```
