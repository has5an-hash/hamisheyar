const GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta";
const GROQ_BASE = "https://api.groq.com/openai/v1";

export const DEFAULT_MODELS = Object.freeze({
  gemini: process.env.GEMINI_MODEL || "gemini-3.8-flash",
  groqFast: process.env.GROQ_FAST_MODEL || "openai/gpt-oss-20b",
  groqStrong: process.env.GROQ_STRONG_MODEL || "openai/gpt-oss-120b",
  groqTranscribe: process.env.GROQ_TRANSCRIBE_MODEL || "whisper-large-v3-turbo"
});

export class ProviderError extends Error {
  constructor(provider, status, message, retryable = false) {
    super(message);
    this.name = "ProviderError";
    this.provider = provider;
    this.status = status;
    this.retryable = retryable;
  }
}

export function chooseProvider({ task = "auto", hasImage = false } = {}) {
  const normalized = String(task || "auto").toLowerCase();
  if (hasImage || ["vision", "image", "analysis", "reasoning", "general", "auto"].includes(normalized)) {
    return "gemini";
  }
  if (["fast", "quick", "voice", "transcript", "transcription"].includes(normalized)) {
    return "groq";
  }
  if (normalized === "critical") return "hybrid";
  return "gemini";
}

function isRetryableStatus(status) {
  return status === 408 || status === 409 || status === 425 || status === 429 || status >= 500;
}

async function readJsonResponse(response, provider) {
  const raw = await response.text();
  let parsed;
  try {
    parsed = raw ? JSON.parse(raw) : {};
  } catch {
    parsed = { raw };
  }

  if (!response.ok) {
    const providerMessage =
      parsed?.error?.message ||
      parsed?.message ||
      `${provider} request failed with HTTP ${response.status}`;
    throw new ProviderError(
      provider,
      response.status,
      providerMessage,
      isRetryableStatus(response.status)
    );
  }
  return parsed;
}

export async function callGemini({
  apiKey,
  prompt,
  image,
  model = DEFAULT_MODELS.gemini,
  fetchImpl = fetch,
  timeoutMs = 45000
}) {
  if (!apiKey) throw new ProviderError("gemini", 401, "Gemini API key is missing.");
  const parts = [{ text: prompt }];
  if (image?.dataBase64 && image?.mimeType) {
    parts.push({
      inline_data: {
        mime_type: image.mimeType,
        data: image.dataBase64
      }
    });
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(
      `${GEMINI_BASE}/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`,
      {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          contents: [{ role: "user", parts }],
          generationConfig: { temperature: 0.35 }
        }),
        signal: controller.signal
      }
    );
    const data = await readJsonResponse(response, "gemini");
    const text = data?.candidates?.[0]?.content?.parts
      ?.map((part) => part?.text || "")
      .join("")
      .trim();
    if (!text) throw new ProviderError("gemini", 502, "Gemini returned no text.", true);
    return { provider: "gemini", model, text };
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new ProviderError("gemini", 504, "Gemini request timed out.", true);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export async function callGroq({
  apiKey,
  prompt,
  model = DEFAULT_MODELS.groqFast,
  fetchImpl = fetch,
  timeoutMs = 35000
}) {
  if (!apiKey) throw new ProviderError("groq", 401, "Groq API key is missing.");
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(`${GROQ_BASE}/chat/completions`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${apiKey}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model,
        messages: [{ role: "user", content: prompt }],
        temperature: 0.3
      }),
      signal: controller.signal
    });
    const data = await readJsonResponse(response, "groq");
    const text = data?.choices?.[0]?.message?.content?.trim();
    if (!text) throw new ProviderError("groq", 502, "Groq returned no text.", true);
    return { provider: "groq", model, text };
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new ProviderError("groq", 504, "Groq request timed out.", true);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export async function transcribeWithGroq({
  apiKey,
  audioBase64,
  mimeType = "audio/m4a",
  filename = "voice.m4a",
  model = DEFAULT_MODELS.groqTranscribe,
  fetchImpl = fetch,
  timeoutMs = 60000
}) {
  if (!apiKey) throw new ProviderError("groq", 401, "Groq API key is missing.");
  if (!audioBase64) throw new ProviderError("groq", 400, "Audio payload is missing.");

  const bytes = Buffer.from(audioBase64, "base64");
  const form = new FormData();
  form.append("model", model);
  form.append("response_format", "json");
  form.append("file", new Blob([bytes], { type: mimeType }), filename);

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetchImpl(`${GROQ_BASE}/audio/transcriptions`, {
      method: "POST",
      headers: { authorization: `Bearer ${apiKey}` },
      body: form,
      signal: controller.signal
    });
    const data = await readJsonResponse(response, "groq");
    const text = data?.text?.trim();
    if (!text) throw new ProviderError("groq", 502, "Groq returned no transcription.", true);
    return { provider: "groq", model, text };
  } catch (error) {
    if (error?.name === "AbortError") {
      throw new ProviderError("groq", 504, "Groq transcription timed out.", true);
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

export async function respondHybrid({
  geminiKey,
  groqKey,
  prompt,
  task = "auto",
  image,
  fetchImpl = fetch
}) {
  if (!geminiKey || !groqKey) {
    throw new ProviderError(
      "hybrid",
      401,
      "Both Gemini and Groq connections are required before the HamisheYar brain can be activated."
    );
  }

  const route = chooseProvider({ task, hasImage: Boolean(image?.dataBase64) });

  if (route === "hybrid") {
    const draft = await callGemini({ apiKey: geminiKey, prompt, image, fetchImpl });
    const reviewPrompt = [
      "You are the fast verification layer of HamisheYar.",
      "Review the draft below for factual errors, missing caveats, contradictions, and unclear wording.",
      "Return one corrected final answer only, in the same language as the user's request.",
      "",
      "USER REQUEST:",
      prompt,
      "",
      "GEMINI DRAFT:",
      draft.text
    ].join("\n");
    const reviewed = await callGroq({
      apiKey: groqKey,
      prompt: reviewPrompt,
      model: DEFAULT_MODELS.groqStrong,
      fetchImpl
    });
    return {
      ...reviewed,
      route: "hybrid",
      primaryProvider: "gemini",
      verifierProvider: "groq"
    };
  }

  if (route === "groq") {
    try {
      const result = await callGroq({ apiKey: groqKey, prompt, fetchImpl });
      return { ...result, route: "groq" };
    } catch (error) {
      if (!error?.retryable) throw error;
      const fallback = await callGemini({ apiKey: geminiKey, prompt, image, fetchImpl });
      return { ...fallback, route: "groq->gemini", fallbackFrom: "groq" };
    }
  }

  try {
    const result = await callGemini({ apiKey: geminiKey, prompt, image, fetchImpl });
    return { ...result, route: "gemini" };
  } catch (error) {
    if (!error?.retryable || image?.dataBase64) throw error;
    const fallback = await callGroq({
      apiKey: groqKey,
      prompt,
      model: DEFAULT_MODELS.groqStrong,
      fetchImpl
    });
    return { ...fallback, route: "gemini->groq", fallbackFrom: "gemini" };
  }
}
