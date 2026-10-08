package ir.hamisheyar.app.brain

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

data class BrainReply(val text: String, val route: String, val videoSeen: Boolean)
class BrainFailure(val status: Int, message: String) : IOException(message)

interface BrainGateway {
    suspend fun verify(provider: String, key: String): String
    suspend fun answer(context: Context, prompt: String, media: File? = null, status: (String) -> Unit = {}): BrainReply
}

/** All planning, routing and output rendering remain inside the Android application. */
object LocalGateway : BrainGateway {
    private const val GEMINI_MODEL = "gemini-3.8-flash"
    private const val GROQ_FAST = "openai/gpt-oss-20b"
    private const val GROQ_REVIEW = "openai/gpt-oss-120b"
    private const val GENERATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"
    private const val GROQ_CHAT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MAX_MEDIA_BYTES = 24L * 1024 * 1024

    override suspend fun verify(provider: String, key: String): String = withContext(Dispatchers.IO) {
        when (provider) {
            "gemini" -> {
                gemini(key, "فقط کلمه آماده را بگو.")
                "Gemini با یک درخواست واقعی پاسخ داد."
            }
            "groq" -> {
                groq(key, "فقط کلمه آماده را بگو.", GROQ_FAST)
                "Groq با یک درخواست واقعی پاسخ داد."
            }
            else -> throw BrainFailure(400, "سرویس شناخته نشد.")
        }
    }

    override suspend fun answer(context: Context, prompt: String, media: File?, status: (String) -> Unit): BrainReply =
        withContext(Dispatchers.IO) {
            if (!CredentialVault.ready(context)) throw BrainFailure(401, "ابتدا هر دو اتصال Gemini و Groq را در تنظیمات فعال کن.")
            val g = CredentialVault.get(context, "gemini") ?: throw BrainFailure(401, "کلید Gemini پیدا نشد.")
            val q = CredentialVault.get(context, "groq") ?: throw BrainFailure(401, "کلید Groq پیدا نشد.")
            val input = prompt.trim().ifBlank { "این ویدیو را به فارسی تحلیل کن." }
            if (media != null) {
                if (!media.isFile || media.length() !in 1..MAX_MEDIA_BYTES)
                    throw BrainFailure(413, "فایل ویدیو معتبر نیست یا بیشتر از ۲۴ مگابایت است.")
                status("آپلود موقت رسانه واقعی در Gemini…")
                val file = upload(g, media)
                try {
                    status("Gemini دارد تصویر و صدای ویدیو را تحلیل می‌کند…")
                    val visual = gemini(g, "پرسش: " + input + "\nویدیوی پیوست‌شده را دقیق تحلیل کن. ادعاهای قابل‌بررسی را از واقعیتِ دیده‌شده جدا کن و هیچ منبعی نساز.", file.first, file.second)
                    status("Groq در حال بررسی و تطبیق خروجی…")
                    val speech = try {
                        val audio = remuxAudio(context, media)
                        if (audio != null) try { transcribe(q, audio) } finally { audio.delete() } else ""
                    } catch (_: Exception) { "" }
                    val instruction = "نقش تو بازبین محتوای همیشه‌یار است؛ پاسخ نهایی یکپارچه فارسی بده. توافق مدل‌ها اثبات واقعی بودن ادعا نیست. بدون شواهد مستقل ادعای تأیید صحت نکن.\nپرسش: " +
                        input + "\nخلاصه Gemini از رسانه: " + visual +
                        (if (speech.isBlank()) "" else "\nمتن صدا که Groq استخراج کرده: " + speech.take(10000))
                    val reviewed = try { groq(q, instruction, GROQ_REVIEW) } catch (_: IOException) {
                        "محتوای مشاهده‌شده توسط Gemini:\n" + visual + "\n\nبازبینی Groq در دسترس نبود؛ صحت ادعاها مستقل تأیید نشده است."
                    }
                    BrainReply(reviewed, "Gemini video + Groq", true)
                } finally { runCatching { request("DELETE", "https://generativelanguage.googleapis.com/v1beta/" + file.third, mapOf("x-goog-api-key" to g)) } }
            } else {
                val deep = RoutingPolicy.needsReview(input)
                if (!deep && input.length <= 180) {
                    status("دریافت پاسخ سریع…")
                    val result = try { groq(q, input, GROQ_FAST) } catch (_: IOException) { gemini(g, input) }
                    BrainReply(result, "Groq / Gemini fallback", false)
                } else {
                    status("Gemini در حال تحلیل…")
                    val first = try { gemini(g, input) } catch (_: IOException) {
                        return@withContext BrainReply(groq(q, input, GROQ_REVIEW), "Groq fallback", false)
                    }
                    if (!deep) BrainReply(first, "Gemini", false)
                    else {
                        status("Groq در حال بازبینی پاسخ…")
                        val second = runCatching {
                            groq(q, "پرسش: " + input + "\nپاسخ Gemini: " + first + "\nبا دقت بررسی کن و بدون ساختن شواهد، پاسخ نهایی دقیق فارسی ارائه بده.", GROQ_REVIEW)
                        }.getOrDefault(first + "\n\nتأیید دوم در دسترس نبود.")
                        BrainReply(second, "Gemini + Groq", false)
                    }
                }
            }
        }

    private fun gemini(key: String, prompt: String, fileUri: String? = null, mime: String? = null): String {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (fileUri != null && mime != null) parts.put(
            JSONObject().put("file_data", JSONObject().put("mime_type", mime).put("file_uri", fileUri))
        )
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0.3))
        val result = request("POST", GENERATE, mapOf("x-goog-api-key" to key), body.toString())
        val p = result.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val text = (0 until (p?.length() ?: 0)).mapNotNull { p?.optJSONObject(it)?.optString("text") }.joinToString("\n").trim()
        if (text.isBlank()) throw BrainFailure(502, "Gemini پاسخی تولید نکرد.")
        return text
    }

    private fun groq(key: String, prompt: String, model: String): String {
        val body = JSONObject().put("model", model).put("temperature", 0.3)
            .put("max_completion_tokens", 1800)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val result = request("POST", GROQ_CHAT, mapOf("Authorization" to "Bearer " + key), body.toString())
        val text = result.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().trim()
        if (text.isEmpty()) throw BrainFailure(502, "Groq پاسخی تولید نکرد.")
        return text
    }

    private fun upload(key: String, media: File): Triple<String, String, String> {
        val mime = when (media.extension.lowercase()) {
            "webm" -> "video/webm"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }
        val start = URL("https://generativelanguage.googleapis.com/upload/v1beta/files").openConnection() as HttpURLConnection
        start.requestMethod = "POST"; start.connectTimeout = 20000; start.readTimeout = 30000
        start.instanceFollowRedirects = false
        start.setRequestProperty("x-goog-api-key", key)
        start.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
        start.setRequestProperty("X-Goog-Upload-Command", "start")
        start.setRequestProperty("X-Goog-Upload-Header-Content-Length", media.length().toString())
        start.setRequestProperty("X-Goog-Upload-Header-Content-Type", mime)
        start.setRequestProperty("Content-Type", "application/json")
        start.doOutput = true
        try {
            start.outputStream.use { it.write("""{"file":{"display_name":"Hamisheyar video"}}""".toByteArray()) }
            if (start.responseCode !in 200..299) throw BrainFailure(start.responseCode, "Gemini اجازه آپلود نداد.")
            val uploadUrl = start.getHeaderField("X-Goog-Upload-URL")
                ?: throw BrainFailure(502, "نشانی آپلود Gemini موجود نیست.")
            if (!uploadUrl.startsWith("https://generativelanguage.googleapis.com/"))
                throw BrainFailure(502, "نشانی آپلود امن نیست.")
            val conn = URL(uploadUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 90000
            conn.instanceFollowRedirects = false; conn.doOutput = true
            conn.setRequestProperty("X-Goog-Upload-Offset", "0")
            conn.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
            conn.setRequestProperty("Content-Type", mime)
            conn.setFixedLengthStreamingMode(media.length())
            val result = try {
                conn.outputStream.use { out -> media.inputStream().use { it.copyTo(out) } }
                read(conn).getJSONObject("file")
            } finally { conn.disconnect() }
            val name = result.getString("name")
            if (!name.startsWith("files/")) throw BrainFailure(502, "شناسه فایل Gemini نامعتبر است.")
            var info = result
            repeat(35) {
                if (info.optString("state") != "PROCESSING") return@repeat
                Thread.sleep(1500)
                info = request("GET", "https://generativelanguage.googleapis.com/v1beta/" + name, mapOf("x-goog-api-key" to key))
            }
            val state = info.optString("state")
            if (state != "ACTIVE" && state.isNotBlank()) throw BrainFailure(502, "ویدیو در Gemini آماده نشد: " + state)
            return Triple(info.optString("uri").ifBlank { result.getString("uri") }, mime, name)
        } finally { start.disconnect() }
    }

    private fun remuxAudio(context: Context, video: File): File? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val output = File(context.cacheDir, "hamisheyar_audio_" + System.nanoTime() + ".m4a")
        var succeeded = false
        try {
            extractor.setDataSource(video.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: return null
            extractor.selectTrack(track)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(extractor.getTrackFormat(track))
            muxer.start()
            val data = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                data.clear()
                val size = extractor.readSampleData(data, 0)
                if (size < 0) break
                info.offset = 0; info.size = size; info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(outputTrack, data, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release(); muxer = null
            succeeded = output.length() in 1..MAX_MEDIA_BYTES
            return output.takeIf { succeeded }
        } finally {
            extractor.release()
            runCatching { muxer?.release() }
            if (!succeeded) output.delete()
        }
    }

    private fun transcribe(key: String, audio: File): String {
        val boundary = "Hamisheyar" + System.nanoTime()
        val conn = URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 75000
        conn.instanceFollowRedirects = false; conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer " + key)
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
        try {
            conn.outputStream.buffered().use { out ->
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-large-v3-turbo\r\n").toByteArray())
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n").toByteArray())
                audio.inputStream().use { it.copyTo(out) }
                out.write(("\r\n--" + boundary + "--\r\n").toByteArray())
            }
            return read(conn).optString("text")
        } finally { conn.disconnect() }
    }

    private fun request(method: String, url: String, headers: Map<String, String>, body: String? = null): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method; conn.connectTimeout = 18000; conn.readTimeout = 60000
        conn.instanceFollowRedirects = false
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            return read(conn)
        } finally { conn.disconnect() }
    }

    private fun read(conn: HttpURLConnection): JSONObject {
        val code = conn.responseCode
        val raw = ((if (code in 200..299) conn.inputStream else conn.errorStream)
            ?: throw BrainFailure(code, "ارتباط برقرار نشد.")).bufferedReader().use { it.readText().take(100_000) }
        val data = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        if (code !in 200..299) {
            val message = when (code) {
                401, 403 -> "کلید نامعتبر است یا مدل برای حساب فعال نیست."
                429 -> "سهمیه رایگان یا محدودیت درخواست این حساب تمام شده."
                404 -> "مدل برای این حساب در دسترس نیست."
                else -> "سرویس پاسخ موفق نداد."
            }
            throw BrainFailure(code, "HTTP " + code + ": " + message)
        }
        return data
    }
}
