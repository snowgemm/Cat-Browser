import test from "node:test";
import assert from "node:assert/strict";
import handler from "../api/assistant.js";

const originalFetch = globalThis.fetch;
const originalEnv = {
  token: process.env.CAT_AI_GATEWAY_TOKEN,
  apiKey: process.env.OPENAI_API_KEY,
  model: process.env.OPENAI_MODEL
};

function responseRecorder() {
  return {
    headers: {},
    statusCode: 200,
    body: null,
    setHeader(name, value) {
      this.headers[name.toLowerCase()] = value;
    },
    status(code) {
      this.statusCode = code;
      return this;
    },
    json(body) {
      this.body = body;
      return this;
    }
  };
}

function validRequest(ip) {
  return {
    method: "POST",
    headers: {
      authorization: "Bearer test-gateway-token-00000000000000000000000000000000",
      "x-forwarded-for": ip
    },
    body: {
      inputItems: [{
        role: "user",
        content: [{ type: "input_text", text: "Mở example.com" }]
      }]
    }
  };
}

test.before(() => {
  process.env.CAT_AI_GATEWAY_TOKEN = "test-gateway-token-00000000000000000000000000000000";
  process.env.OPENAI_API_KEY = "test-openai-key-not-real";
  process.env.OPENAI_MODEL = "gpt-6-luna";
});

test.after(() => {
  globalThis.fetch = originalFetch;
  if (originalEnv.token === undefined) delete process.env.CAT_AI_GATEWAY_TOKEN;
  else process.env.CAT_AI_GATEWAY_TOKEN = originalEnv.token;
  if (originalEnv.apiKey === undefined) delete process.env.OPENAI_API_KEY;
  else process.env.OPENAI_API_KEY = originalEnv.apiKey;
  if (originalEnv.model === undefined) delete process.env.OPENAI_MODEL;
  else process.env.OPENAI_MODEL = originalEnv.model;
});

test("rejects non-POST requests", async () => {
  const res = responseRecorder();
  await handler({ method: "PATCH", headers: {}, body: null }, res);
  assert.equal(res.statusCode, 405);
  assert.equal(res.headers.allow, "GET, POST");
});

test("checks gateway credentials and model access without generating a response", async () => {
  let upstreamRequest;
  globalThis.fetch = async (url, init) => {
    upstreamRequest = { url, init };
    return new Response(JSON.stringify({
      id: "gpt-6-luna",
      object: "model",
      owned_by: "openai"
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  const req = validRequest("198.51.100.19");
  req.method = "GET";
  const res = responseRecorder();
  await handler(req, res);

  assert.equal(res.statusCode, 200);
  assert.equal(upstreamRequest.url, "https://api.openai.com/v1/models/gpt-6-luna");
  assert.equal(upstreamRequest.init.method, "GET");
  assert.equal(upstreamRequest.init.headers.Authorization, "Bearer test-openai-key-not-real");
  assert.deepEqual(res.body, { ok: true, model: "gpt-6-luna" });
});

test("rejects requests without a valid gateway token", async () => {
  const res = responseRecorder();
  const req = validRequest("198.51.100.11");
  req.headers.authorization = "Bearer wrong-token";
  await handler(req, res);
  assert.equal(res.statusCode, 401);
});

test("rejects malformed conversation items before calling OpenAI", async () => {
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    throw new Error("OpenAI must not be called for invalid input");
  };
  const res = responseRecorder();
  const req = validRequest("198.51.100.12");
  req.body.inputItems = [{ type: "function_call", name: "run_shell", arguments: "{}" }];
  await handler(req, res);
  assert.equal(res.statusCode, 400);
  assert.equal(called, false);
});

test("rejects oversized UTF-8 payloads even when the string character count is smaller", async () => {
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    throw new Error("OpenAI must not be called for oversized input");
  };
  const res = responseRecorder();
  const req = validRequest("198.51.100.15");
  req.body.inputItems = [{
    role: "user",
    content: [{ type: "input_text", text: "🧪".repeat(8_000) }]
  }];
  await handler(req, res);
  assert.equal(res.statusCode, 400);
  assert.equal(called, false);
});

test("uses stateless Responses API and serializes browser tool calls", async () => {
  let upstreamRequest;
  globalThis.fetch = async (url, init) => {
    upstreamRequest = { url, init };
    return new Response(JSON.stringify({
      output: [
        {
          type: "reasoning",
          id: "rs_123",
          encrypted_content: "opaque-encrypted-reasoning"
        },
        {
          type: "function_call",
          id: "fc_123",
          call_id: "call_123",
          name: "open_url",
          arguments: JSON.stringify({ url: "https://example.com" })
        }
      ]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  const res = responseRecorder();
  await handler(validRequest("198.51.100.13"), res);

  assert.equal(res.statusCode, 200);
  assert.equal(upstreamRequest.url, "https://api.openai.com/v1/responses");
  const requestBody = JSON.parse(upstreamRequest.init.body);
  assert.equal(requestBody.store, false);
  assert.deepEqual(requestBody.include, ["reasoning.encrypted_content"]);
  assert.equal(requestBody.parallel_tool_calls, false);
  assert.equal(requestBody.model, "gpt-6-luna");
  assert.ok(requestBody.tools.every((tool) => tool.type === "function"));
  assert.ok(requestBody.tools.every((tool) => tool.strict === true));
  assert.equal(res.body.toolCalls.length, 1);
  assert.equal(res.body.toolCalls[0].name, "open_url");
  assert.equal(res.body.responseItems.length, 2);
});

test("accepts assistant message items and completed tool outputs on stateless replay", async () => {
  let called = false;
  globalThis.fetch = async (_url, init) => {
    called = true;
    const requestBody = JSON.parse(init.body);
    assert.equal(requestBody.input.length, 6);
    return new Response(JSON.stringify({
      output: [{
        type: "message",
        id: "msg_reply",
        role: "assistant",
        content: [{ type: "output_text", text: "Đã mở trang." }]
      }]
    }), { status: 200, headers: { "content-type": "application/json" } });
  };

  const req = validRequest("198.51.100.16");
  req.body.inputItems = [
    {
      role: "user",
      content: [{ type: "input_text", text: "Mở example.com" }]
    },
    {
      type: "reasoning",
      id: "rs_first",
      encrypted_content: "opaque-first-reasoning"
    },
    {
      type: "function_call",
      id: "fc_first",
      call_id: "call_first",
      name: "open_url",
      arguments: JSON.stringify({ url: "https://example.com" })
    },
    {
      type: "function_call_output",
      call_id: "call_first",
      output: JSON.stringify({ ok: true })
    },
    {
      type: "reasoning",
      id: "rs_second",
      encrypted_content: "opaque-second-reasoning"
    },
    {
      type: "message",
      id: "msg_previous",
      role: "assistant",
      content: [{ type: "output_text", text: "Đang mở trang." }]
    }
  ];

  const res = responseRecorder();
  await handler(req, res);
  assert.equal(called, true);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.outputText, "Đã mở trang.");
});

test("rejects a function output that has no matching function call", async () => {
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    throw new Error("OpenAI must not be called for an unpaired tool output");
  };

  const req = validRequest("198.51.100.17");
  req.body.inputItems = [
    { role: "user", content: [{ type: "input_text", text: "Hello" }] },
    { type: "function_call_output", call_id: "missing_call", output: JSON.stringify({ ok: true }) }
  ];
  const res = responseRecorder();
  await handler(req, res);
  assert.equal(res.statusCode, 400);
  assert.equal(called, false);
});

test("rejects unresolved tool calls instead of sending an incomplete tool cycle", async () => {
  let called = false;
  globalThis.fetch = async () => {
    called = true;
    throw new Error("OpenAI must not be called for an unresolved tool call");
  };

  const req = validRequest("198.51.100.18");
  req.body.inputItems = [
    { role: "user", content: [{ type: "input_text", text: "Hello" }] },
    {
      type: "function_call",
      call_id: "pending_call",
      name: "go_home",
      arguments: "{}"
    }
  ];
  const res = responseRecorder();
  await handler(req, res);
  assert.equal(res.statusCode, 400);
  assert.equal(called, false);
});


test("classifies exhausted OpenAI quota separately from request rate limits", async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({
    error: {
      message: "Sensitive billing diagnostic",
      type: "insufficient_quota",
      code: "insufficient_quota"
    }
  }), { status: 429, headers: { "content-type": "application/json" } });

  const quotaRes = responseRecorder();
  await handler(validRequest("198.51.100.21"), quotaRes);
  assert.equal(quotaRes.statusCode, 429);
  assert.equal(quotaRes.body.code, "insufficient_quota");
  assert.match(quotaRes.body.error, /quota or billing limit/i);
  assert.equal(JSON.stringify(quotaRes.body).includes("Sensitive billing diagnostic"), false);

  globalThis.fetch = async () => new Response(JSON.stringify({
    error: { message: "Sensitive rate diagnostic", type: "requests", code: "rate_limit_exceeded" }
  }), {
    status: 429,
    headers: { "content-type": "application/json", "retry-after": "7" }
  });

  const rateRes = responseRecorder();
  await handler(validRequest("198.51.100.22"), rateRes);
  assert.equal(rateRes.statusCode, 429);
  assert.equal(rateRes.body.code, "rate_limit_exceeded");
  assert.equal(rateRes.body.retryAfterSeconds, 7);
  assert.equal(rateRes.headers["retry-after"], "7");
  assert.equal(JSON.stringify(rateRes.body).includes("Sensitive rate diagnostic"), false);
});

test("does not expose OpenAI upstream error bodies to the client", async () => {
  globalThis.fetch = async () =>
    new Response(JSON.stringify({ error: { message: "sensitive upstream diagnostic" } }), {
      status: 401,
      headers: { "content-type": "application/json" }
    });
  const res = responseRecorder();
  await handler(validRequest("198.51.100.14"), res);
  assert.equal(res.statusCode, 502);
  assert.equal(res.body.error, "OpenAI API request failed. Check the gateway configuration.");
  assert.equal(JSON.stringify(res.body).includes("sensitive upstream diagnostic"), false);
});
