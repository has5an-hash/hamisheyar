package ir.hamisheyar.app.brain

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class ImagePart(val mimeType: String, val base64: String)
class BrainNetworkException(val statusCode: Int, message: String) : Exception(message)

/** Explicit boundary: can later be replaced without changing the orchestration/UI. */
interface BrainGateway {
    suspend fun gemini(apiKey: String, text: String, images: List<ImagePart> = emptyList()): String
    suspend fun groq(apiKey: String, text: String, strong: Boolean = false): String
    suspend fun transcribe(apiKey: String, audio: File): String
}

class LocalGateway : BrainGateway {
    companion object {
        const val GEMINI_MODEL = "gemini-3.8-flash"
        const val GROQ_FAST = "openai/gpt-oss-20b"
        const val GROQ_STRONG = "openai/gpt-oss-120b"
        const val GROQ_WHISPER = "whisper-large-v3-turbo"
    }

    override suspend fun gemini(apiKey: String, text: String, images: List<ImagePart>): String =
        withContext(Dispatchers.IO) {
            val parts = JSONArray().put(JSONObject().put("text", text))
            for (img in images.take(4)) {
                parts.put(JSONObject().put("inline_data", JSONObject()
                    .put("mime_type", img.mimeType).put("data", img.base64)))
            }
            val body = JSONObject().put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", parts)
            )).put("generationConfig", JSONObject()
                .put("temperature", 0.3)
                .put("maxOutputTokens", 1400))
            val json = request(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    GEMINI_MODEL + ":generateContent",
                mapOf("x-goog-api-key" to apiKey), body
            )
            val candidates = json.optJSONArray("candidates") ?: JSONArray()
            val blocks = candidates.optJSONObject(0)?.optJSONObject("content")
                ?.optJSONArray("parts") ?: JSONArray()
            buildString {
                for (i in 0 until blocks.length()) {
                    val part = blocks.optJSONObject(i) ?: continue
                    if (part.has("text")) append(part.optString("text"))
                }
            }.trim().ifEmpty { throw BrainNetworkException(502, "Gemini پاسخی تولید نکرد.") }
        }

    override suspend fun groq(apiKey: String, text: String, strong: Boolean): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", if (strong) GROQ_STRONG else GROQ_FAST)
                .put("temperature", 0.25)
                .put("max_completion_tokens", 1150)
                .put("messages", JSONArray().put(JSONObject()
                    .put("role", "user").put("content", text)))
            val json = request("https://api.groq.com/openai/v1/chat/completions",
                mapOf("Authorization" to ("Bearer " + apiKey)), body)
            json.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw BrainNetworkException(502, "Groq پاسخی تولید نکرد.")
        }

    override suspend fun transcribe(apiKey: String, audio: File): String =
        withContext(Dispatchers.IO) {
            require(audio.exists() && audio.length() in 1..20_000_000) {
                "فایل صوتی برای تبدیل گفتار مناسب نیست."
            }
            val boundary = "HY-" + UUID.randomUUID().toString()
            val conn = (URL("https://api.groq.com/openai/v1/audio/transcriptions")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 65000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
            }
            try {
                conn.outputStream.use { out ->
                    fun ascii(value: String) = out.write(value.toByteArray(Charsets.UTF_8))
                    ascii("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n" +
                        GROQ_WHISPER + "\r\n")
                    ascii("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.m4a\"\r\n" +
                        "Content-Type: audio/mp4\r\n\r\n")
                    audio.inputStream().use { it.copyTo(out) }
                    ascii("\r\n--" + boundary + "--\r\n")
                }
                val result = readResponse(conn)
                result.optString("text").trim().takeIf { it.isNotEmpty() }
                    ?: throw BrainNetworkException(502, "Groq متن صوتی برنگرداند.")
            } finally { conn.disconnect() }
        }

    suspend fun validateGemini(key: String): Boolean =
        gemini(key, "فقط یک کلمه بنویس: آماده").isNotBlank()

    suspend fun validateGroq(key: String): Boolean =
        groq(key, "فقط یک کلمه بنویس: آماده").isNotBlank()

    private fun request(url: String, headers: Map<String, String>, data: JSONObject): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 55000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            conn.outputStream.use { it.write(data.toString().toByteArray(Charsets.UTF_8)) }
            return readResponse(conn)
        } finally { conn.disconnect() }
    }

    private fun readResponse(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        // Never include provider response bodies in an exception, as they may contain secrets.
        if (code !in 200..299) {
            val explanation = when (code) {
                400 -> "درخواست برای سرویس هوش مصنوعی پذیرفته نشد."
                401, 403 -> "کلید یا دسترسی API معتبر نیست، یا این مدل برای حساب فعال نیست."
                404 -> "مدل انتخابی برای این حساب در دسترس نیست."
                408, 504 -> "زمان پاسخ‌گویی سرویس تمام شد."
                429 -> "سهمیه یا محدودیت سرعت API این حساب پر شده است."
                in 500..599 -> "سرویس هوش مصنوعی موقتاً مشکل دارد."
                else -> "خطای ارتباط با سرویس هوش مصنوعی."
            }
            throw BrainNetworkException(code, explanation + " (HTTP " + code + ")")
        }
        val stream = conn.inputStream
        val bytes = stream.use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 300_000) throw BrainNetworkException(502, "پاسخ بیش از حد بزرگ است.")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return try { JSONObject(String(bytes, Charsets.UTF_8)) }
        catch (_: Exception) { throw BrainNetworkException(502, "پاسخ سرویس معتبر نیست.") }
    }
}
