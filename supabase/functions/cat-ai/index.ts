import { withSupabase } from "npm:@supabase/server@1";
import {
  MODEL,
  MAX_BODY_BYTES,
  RequestValidationError,
  buildGeminiRequest,
  validateConversation,
  extractOutputText,
  extractToolCalls
} from "./core.mjs";

const GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta";
const MAX_RETRY_AFTER_SECONDS = 86_400;

function json(data: unknown, status = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extraHeaders
    }
  });
}

async function readBoundedJson(request: Request): Promise<unknown> {
  const contentLength = Number(request.headers.get("content-length") ?? 0);
  if (contentLength > MAX_BODY_BYTES) throw new RequestValidationError("Request is too large.");
  if (!request.body) throw new RequestValidationError("Missing request body.");
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > MAX_BODY_BYTES) {
      await reader.cancel();
      throw new RequestValidationError("Request is too large.");
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    throw new RequestValidationError("Invalid JSON body.");
  }
}

async function checkGeminiAccess(apiKey: string): Promise<Response> {
  try {
    const upstream = await fetch(GEMINI_URL + "/models/" + MODEL, {
      method: "GET",
      headers: { "x-goog-api-key": apiKey },
      signal: AbortSignal.timeout(10_000)
    });
    if (upstream.ok) return json({ ok: true, model: MODEL });
    if (upstream.status === 401 || upstream.status === 403) {
      return json({ error: "Gemini API key is invalid or lacks model access." }, 503);
    }
    if (upstream.status === 404) return json({ error: "Configured Gemini model is unavailable." }, 503);
    return json({ error: "Gemini access could not be verified." }, 502);
  } catch {
    return json({ error: "Could not reach Gemini to verify model access." }, 502);
  }
}

export default {
  fetch: withSupabase({ auth: "user" }, async (request, ctx) => {
    const userId = typeof ctx.userClaims?.sub === "string" ? ctx.userClaims.sub : "";
    if (!userId) return json({ error: "Unauthorized." }, 401);

    const apiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
    if (!apiKey) return json({ error: "Gemini is not configured on the server." }, 503);

    if (request.method === "GET") return await checkGeminiAccess(apiKey);
    if (request.method !== "POST") {
      return json({ error: "Method not allowed." }, 405, { "Allow": "GET, POST" });
    }

    try {
      const body = await readBoundedJson(request);
      if (!body || typeof body !== "object" || Array.isArray(body)) {
        throw new RequestValidationError("Invalid request body.");
      }
      const input = (body as { input?: unknown }).input;
      validateConversation(input);

      const { data: quotaData, error: quotaError } = await ctx.supabaseAdmin.rpc(
        "consume_cat_ai_quota",
        { p_user_id: userId }
      );
      if (quotaError) {
        return json({ error: "Cat AI quota service is unavailable." }, 503);
      }
      const quotaRow = (Array.isArray(quotaData) ? quotaData[0] : quotaData) as
        | { allowed?: unknown; retry_after_seconds?: unknown }
        | null;
      if (!quotaRow || typeof quotaRow.allowed !== "boolean") {
        return json({ error: "Cat AI quota service returned an invalid response." }, 503);
      }
      if (!quotaRow.allowed) {
        const retryAfter = Math.min(
          MAX_RETRY_AFTER_SECONDS,
          Math.max(1, Number(quotaRow.retry_after_seconds) || 60)
        );
        return json({
          error: "Cat AI usage limit reached. Try again later.",
          code: "cat_ai_rate_limited",
          retryAfterSeconds: retryAfter
        }, 429, { "Retry-After": String(retryAfter) });
      }

      const upstream = await fetch(GEMINI_URL + "/interactions", {
        method: "POST",
        headers: {
          "x-goog-api-key": apiKey,
          "Content-Type": "application/json"
        },
        body: JSON.stringify(buildGeminiRequest(input)),
        signal: AbortSignal.timeout(40_000)
      });
      const data = await upstream.json().catch(() => null);
      if (!upstream.ok || !data || !Array.isArray(data.steps)) {
        if (upstream.status === 429) {
          const retryAfter = Math.min(
            MAX_RETRY_AFTER_SECONDS,
            Math.max(1, Number(upstream.headers.get("retry-after")) || 60)
          );
          return json({
            error: "Gemini is rate limiting requests. Try again later.",
            code: "gemini_rate_limited",
            retryAfterSeconds: retryAfter
          }, 429, { "Retry-After": String(retryAfter) });
        }
        if (upstream.status === 401 || upstream.status === 403) {
          return json({ error: "Gemini API credentials are invalid or unauthorized." }, 503);
        }
        return json({ error: "Gemini could not complete this request." }, 502);
      }

      return json({
        outputText: extractOutputText(data.steps),
        toolCalls: extractToolCalls(data.steps),
        responseItems: data.steps
      });
    } catch (error) {
      if (error instanceof RequestValidationError) return json({ error: error.message }, 400);
      return json({ error: "Cat AI could not complete this request." }, 502);
    }
  })
};
