import assert from "node:assert/strict";
import test from "node:test";

import {
  MAX_TOOL_OUTPUT_CHARS,
  RequestValidationError,
  validateConversation
} from "../supabase/functions/cat-ai/core.mjs";

function conversationWithToolResult(text) {
  return [
    {
      type: "user_input",
      content: [{ type: "text", text: "List my open tabs." }]
    },
    {
      type: "function_call",
      id: "call-list-tabs",
      name: "list_tabs",
      arguments: {}
    },
    {
      type: "function_result",
      call_id: "call-list-tabs",
      name: "list_tabs",
      result: [{ type: "text", text }]
    }
  ];
}

test("accepts a bounded tool result larger than the old 2 KB limit", () => {
  assert.equal(MAX_TOOL_OUTPUT_CHARS, 6_000);
  assert.doesNotThrow(() => {
    validateConversation(conversationWithToolResult("x".repeat(2_500)));
  });
});

test("rejects a tool result above the configured limit", () => {
  assert.throws(
    () => validateConversation(conversationWithToolResult("x".repeat(MAX_TOOL_OUTPUT_CHARS + 1))),
    RequestValidationError
  );
});
