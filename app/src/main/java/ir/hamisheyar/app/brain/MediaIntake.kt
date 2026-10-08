package ir.hamisheyar.app.brain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import android.content.Context

/** Only downloads truly public, HTTP-accessible video bytes; HTML is never treated as video. */
object MediaIntake {
    private const val LIMIT = 24L * 1024 * 1024

    suspend fun findPublicVideo(context: Context, text: String, status: (String) -> Unit = {}): File? =
        withContext(Dispatchers.IO) {
            val link = Regex("""https://[^\s<>\"']+""").find(text)?.value
                ?.trimEnd('.', ',', ')', '،') ?: return@withContext null
            val url = runCatching { URL(link) }.getOrNull() ?: return@withContext null
            if (!safe(url)) return@withContext null
            status("در حال بررسی لینک عمومی و دسترسی واقعی به فایل ویدیو…")
            val first = open(url)
            try {
                val type = first.contentType.orEmpty().lowercase()
                if (type.startsWith("video/") || url.path.lowercase().endsWith(".mp4"))
                    return@withContext download(context, first)
                if (!type.contains("html") || first.contentLengthLong > 2_000_000) return@withContext null
                val html = first.inputStream.bufferedReader().use { it.readText().take(1_000_000) }
                val candidates = listOf(
                    Regex("""<meta[^>]+property=["']og:video(?::secure_url)?["'][^>]+content=["']([^"']+)""", RegexOption.IGNORE_CASE),
                    Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video""", RegexOption.IGNORE_CASE)
                )
                val candidate = candidates.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
                    ?.replace("&amp;", "&")?.replace("&#38;", "&") ?: return@withContext null
                val direct = runCatching { URL(candidate) }.getOrNull() ?: return@withContext null
                if (!safe(direct)) return@withContext null
                val second = open(direct)
                try { download(context, second) } finally { second.disconnect() }
            } catch (_: Exception) {
                null
            } finally { first.disconnect() }
        }

    private fun safe(url: URL): Boolean {
        if (url.protocol != "https" || url.port !in -1..443) return false
        val host = url.host.lowercase()
        if (host == "localhost" || host.endsWith(".local") ||
            host.matches(Regex("""[0-9.]+""")) || host.contains(":")) return false
        return host.contains(".") && !host.endsWith(".internal")
    }

    private fun open(url: URL): HttpURLConnection {
        var current = url
        repeat(5) {
            if (!safe(current)) throw IllegalArgumentException("Unsafe media URL")
            val conn = current.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Hamisheyar/0.5 (Android; public media inspector)")
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IllegalStateException("Empty redirect")
                val next = URL(current, location)
                conn.disconnect()
                current = next
            } else {
                if (code !in 200..299) {
                    conn.disconnect()
                    throw IllegalStateException("HTTP " + code)
                }
                return conn
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    private fun download(context: Context, conn: HttpURLConnection): File? {
        if (!conn.contentType.orEmpty().lowercase().startsWith("video/")) return null
        if (conn.contentLengthLong > LIMIT) return null
        val temp = File(context.cacheDir, "public_video_" + System.nanoTime() + ".mp4")
        try {
            conn.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buf = ByteArray(32768)
                    var total = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        total += read
                        if (total > LIMIT) return null
                        output.write(buf, 0, read)
                    }
                }
            }
            if (temp.length() < 16) return null
            val header = ByteArray(12)
            temp.inputStream().use { it.read(header) }
            if (String(header, 4, 4) != "ftyp") return null
            return temp
        } catch (_: Exception) {
            return null
        } finally {
            if (!temp.exists() || temp.length() < 16 ||
                runCatching { temp.inputStream().use { i -> i.skip(4); String(i.readNBytes(4)) != "ftyp" } }.getOrDefault(true))
                temp.delete()
        }
    }
}
