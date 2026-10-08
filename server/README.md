# HamisheYar Hybrid Brain Server

Lightweight stateless gateway for the HamisheYar Android app.

## Architecture

- Gemini is the primary reasoning/multimodal provider.
- Groq is the low-latency and speech specialist.
- `critical` requests use Gemini first, then Groq as a verifier.
- Retryable Gemini text failures fall back to Groq; retryable Groq text failures fall back to Gemini.
- Both user connections are mandatory before the brain endpoint is enabled.

## Security model

This first server version is intentionally **stateless BYOK**:

- The server has no shared Gemini or Groq key.
- User keys arrive only in TLS request headers:
  - `X-Gemini-Api-Key`
  - `X-Groq-Api-Key`
- Keys are never written to logs or persisted by this service.
- The Android client should store them with Android Keystore-backed encryption.
- This keeps the backend lightweight and makes migration to a future local-only gateway straightforward.

## Run

Requires Node.js 20+.

```bash
npm test
npm start
```

Health endpoint:

```text
GET /health
```

Brain endpoint:

```text
POST /v1/brain/respond
X-Gemini-Api-Key: ...
X-Groq-Api-Key: ...
Content-Type: application/json

{
  "prompt": "این پیام را بررسی کن",
  "task": "auto"
}
```

Tasks:

- `auto`, `general`, `analysis`, `vision` -> Gemini primary
- `fast`, `voice` -> Groq primary
- `critical` -> Gemini draft + Groq verification

Optional vision payload:

```json
{
  "prompt": "این تصویر را بررسی کن",
  "task": "vision",
  "image": {
    "mimeType": "image/jpeg",
    "dataBase64": "..."
  }
}
```

Speech-to-text endpoint:

```text
POST /v1/brain/transcribe
X-Groq-Api-Key: ...
Content-Type: application/json
```

Body:

```json
{
  "audioBase64": "...",
  "mimeType": "audio/m4a",
  "filename": "voice.m4a"
}
```

## Deployment

A root `railway.json` is included. The service needs no database and no AI secrets of its own.

Optional model overrides:

- `GEMINI_MODEL`
- `GROQ_FAST_MODEL`
- `GROQ_STRONG_MODEL`
- `GROQ_TRANSCRIBE_MODEL`
- `MAX_JSON_BYTES`

The default models are intentionally centralized in `server/src/brain.mjs` so model changes do not require Android UI changes.
