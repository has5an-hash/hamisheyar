import http from "node:http";
import crypto from "node:crypto";
import { ProviderError, respondHybrid, transcribeWithGroq, DEFAULT_MODELS } from "./brain.mjs";

const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || "0.0.0.0";
const MAX_JSON_BYTES = Number(process.env.MAX_JSON_BYTES || 14 * 1024 * 1024);

function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(body);
}

async function readJson(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_JSON_BYTES) {
      const error = new Error("Request body is too large.");
      error.status = 413;
      throw error;
    }
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    const error = new Error("Invalid JSON body.");
    error.status = 400;
    throw error;
  }
}

function getCredentials(req) {
  return {
    geminiKey: String(req.headers["x-gemini-api-key"] || "").trim(),
    groqKey: String(req.headers["x-groq-api-key"] || "").trim()
  };
}

function publicError(error, requestId) {
  if (error instanceof ProviderError) {
    return {
      status: Number(error.status) || 502,
      body: {
        ok: false,
        requestId,
        error: error.message,
        provider: error.provider,
        retryable: Boolean(error.retryable)
      }
    };
  }
  return {
    status: Number(error?.status) || 500,
    body: {
      ok: false,
      requestId,
      error: error?.message || "Unexpected server error."
    }
  };
}

const server = http.createServer(async (req, res) => {
  const requestId = crypto.randomUUID();
  const startedAt = Date.now();

  try {
    if (req.method === "GET" && req.url === "/health") {
      return sendJson(res, 200, {
        ok: true,
        service: "hamisheyar-brain",
        architecture: "gemini-primary-groq-accelerator",
        models: DEFAULT_MODELS
      });
    }

    if (req.method === "POST" && req.url === "/v1/brain/respond") {
      const body = await readJson(req);
      const prompt = String(body?.prompt || "").trim();
      if (!prompt) return sendJson(res, 400, { ok: false, requestId, error: "prompt is required." });

      const { geminiKey, groqKey } = getCredentials(req);
      const result = await respondHybrid({
        geminiKey,
        groqKey,
        prompt,
        task: body?.task || "auto",
        image: body?.image,
        fetchImpl: fetch
      });

      console.info(JSON.stringify({
        requestId,
        path: req.url,
        status: 200,
        route: result.route,
        provider: result.provider,
        elapsedMs: Date.now() - startedAt
      }));

      return sendJson(res, 200, {
        ok: true,
        requestId,
        answer: result.text,
        provider: result.provider,
        model: result.model,
        route: result.route,
        fallbackFrom: result.fallbackFrom || null
      });
    }

    if (req.method === "POST" && req.url === "/v1/brain/transcribe") {
      const body = await readJson(req);
      const { groqKey } = getCredentials(req);
      const result = await transcribeWithGroq({
        apiKey: groqKey,
        audioBase64: body?.audioBase64,
        mimeType: body?.mimeType,
        filename: body?.filename,
        fetchImpl: fetch
      });

      console.info(JSON.stringify({
        requestId,
        path: req.url,
        status: 200,
        provider: result.provider,
        elapsedMs: Date.now() - startedAt
      }));

      return sendJson(res, 200, {
        ok: true,
        requestId,
        text: result.text,
        provider: result.provider,
        model: result.model
      });
    }

    return sendJson(res, 404, { ok: false, requestId, error: "Not found." });
  } catch (error) {
    const output = publicError(error, requestId);
    console.warn(JSON.stringify({
      requestId,
      path: req.url,
      status: output.status,
      provider: error?.provider || null,
      retryable: Boolean(error?.retryable),
      elapsedMs: Date.now() - startedAt
    }));
    return sendJson(res, output.status, output.body);
  }
});

server.listen(PORT, HOST, () => {
  console.info(`HamisheYar brain server listening on http://${HOST}:${PORT}`);
});
