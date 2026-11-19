import test from "node:test";
import assert from "node:assert/strict";
import {
  MODEL,
  RequestValidationError,
  buildGeminiRequest,
  validateConversation,
  extractOutputText,
  extractToolCalls
} from "../../supabase/functions/cat-ai/core.mjs";

test("builds stateless Gemini Interactions requests", () => {
  const input = [{ type: "user_input", content: [{ type: "text", text: "Xin chào" }] }];
  const request = buildGeminiRequest(input);
  assert.equal(request.model, MODEL);
  assert.equal(request.store, false);
  assert.equal("temperature" in request.generation_config, false);
  assert.equal(request.input, input);
  assert.ok(Array.isArray(request.tools));
  assert.equal(typeof request.system_instruction, "string");
});

test("accepts valid user and completed function-call history", () => {
  const input = [
    { type: "user_input", content: [{ type: "text", text: "Mở example.com" }] },
    { type: "function_call", id: "call_1", name: "open_url", arguments: { url: "https://example.com" } },
    { type: "function_result", name: "open_url", call_id: "call_1", result: [{ type: "text", text: "{\"ok\":true}" }] }
  ];
  assert.doesNotThrow(() => validateConversation(input));
});

test("rejects unsupported tools and unresolved calls", () => {
  assert.throws(() => validateConversation([{ type: "function_call", id: "x", name: "run_shell", arguments: {} }]), RequestValidationError);
  assert.throws(() => validateConversation([{ type: "function_call", id: "x", name: "open_url", arguments: { url: "https://example.com" } }]), /unresolved function call/);
});

test("rejects mismatched function results", () => {
  assert.throws(() => validateConversation([
    { type: "function_call", id: "call_1", name: "open_url", arguments: { url: "https://example.com" } },
    { type: "function_result", name: "switch_tab", call_id: "call_1", result: [{ type: "text", text: "no" }] }
  ]), /does not match/);
});

test("extracts text and supported function calls from Gemini steps", () => {
  const steps = [
    { type: "thought", signature: "signed" },
    { type: "model_output", content: [{ type: "text", text: "Đang mở website." }] },
    { type: "function_call", id: "call_2", name: "open_url", arguments: { url: "https://example.com" } }
  ];
  assert.equal(extractOutputText(steps), "Đang mở website.");
  assert.deepEqual(extractToolCalls(steps), [{
    callId: "call_2",
    name: "open_url",
    arguments: { url: "https://example.com" }
  }]);
});

test("supports no-argument browser functions", () => {
  assert.deepEqual(extractToolCalls([
    { type: "function_call", id: "call_list", name: "list_tabs" }
  ]), [{
    callId: "call_list",
    name: "list_tabs",
    arguments: {}
  }]);
});

test("rejects oversized and legacy OpenAI history", () => {
  assert.throws(() => validateConversation([{ type: "user_input", content: [{ type: "text", text: "x".repeat(4_001) }] }]), RequestValidationError);
  assert.throws(() => validateConversation([{ role: "user", content: "legacy OpenAI format" }]), RequestValidationError);
});
