import test from "node:test";
import assert from "node:assert/strict";
import { chooseProvider, respondHybrid } from "../src/brain.mjs";

function response(status, payload) {
  return {
    ok: status >= 200 && status < 300,
    status,
    async text() { return JSON.stringify(payload); }
  };
}

test("router sends general and vision work to Gemini", () => {
  assert.equal(chooseProvider({ task: "auto" }), "gemini");
  assert.equal(chooseProvider({ task: "vision", hasImage: true }), "gemini");
});

test("router sends fast and voice work to Groq", () => {
  assert.equal(chooseProvider({ task: "fast" }), "groq");
  assert.equal(chooseProvider({ task: "voice" }), "groq");
});

test("critical mode uses Gemini draft then Groq verifier", async () => {
  const calls = [];
  const fetchImpl = async (url) => {
    calls.push(String(url));
    if (String(url).includes("generativelanguage.googleapis.com")) {
      return response(200, { candidates: [{ content: { parts: [{ text: "draft" }] } }] });
    }
    return response(200, { choices: [{ message: { content: "verified final" } }] });
  };

  const result = await respondHybrid({
    geminiKey: "g",
    groqKey: "q",
    prompt: "important question",
    task: "critical",
    fetchImpl
  });

  assert.equal(result.text, "verified final");
  assert.equal(result.route, "hybrid");
  assert.equal(calls.length, 2);
});

test("Gemini transient failure falls back to strong Groq for text", async () => {
  const fetchImpl = async (url) => {
    if (String(url).includes("generativelanguage.googleapis.com")) {
      return response(429, { error: { message: "rate limited" } });
    }
    return response(200, { choices: [{ message: { content: "fallback answer" } }] });
  };

  const result = await respondHybrid({
    geminiKey: "g",
    groqKey: "q",
    prompt: "hello",
    task: "auto",
    fetchImpl
  });

  assert.equal(result.text, "fallback answer");
  assert.equal(result.route, "gemini->groq");
});
