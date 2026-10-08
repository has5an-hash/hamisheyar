package ir.hamisheyar.app.brain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

data class PreparedMedia(
    val images: List<ImagePart> = emptyList(),
    val audio: File? = null,
    val videoSampled: Boolean = false,
    val note: String = ""
)

/** Only user-shared files and public, directly retrievable media are processed. */
object MediaIntake {
    private const val MAX_BYTES = 30_000_000L
    private val URL_PATTERN = Regex("https://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    fun prepare(context: Context, prompt: String, attachmentPath: String?): PreparedMedia {
        val localFile = attachmentPath?.let { path ->
            runCatching {
                val file = File(path).canonicalFile
                val root = context.filesDir.canonicalFile
                if (file.path.startsWith(root.path + File.separator) &&
                    file.isFile && file.length() in 1..MAX_BYTES) file else null
            }.getOrNull()
        }
        val remoteUrl = URL_PATTERN.find(prompt)?.value?.trimEnd('.', ',', ')', '،')
        val remoteFile = if (localFile == null && remoteUrl != null) {
            runCatching { downloadPublicVideo(context, remoteUrl) }.getOrNull()
        } else null
        val input = localFile ?: remoteFile ?: return PreparedMedia(
            note = if (remoteUrl != null) {
                "فقط لینک در دسترس بود؛ دریافت فایل واقعی ویدیو از این لینک ممکن نشد. " +
                    "برای تحلیل تصویر و صدای ویدیو، فایل آن را با Share بفرست."
            } else ""
        )
        if (input.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "heic")) {
            return imageFile(input)
        }
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(input.absolutePath)
                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.coerceAtMost(3_600_000L) ?: 0L
                val millis = listOf(0L, duration / 3, duration * 2 / 3, duration - 250)
                    .map { it.coerceAtLeast(0L) }.distinct()
                val frames = millis.mapNotNull { position ->
                    runCatching {
                        retriever.getFrameAtTime(
                            position * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )?.let { bitmapToPart(it) }
                    }.getOrNull()
                }.take(4)
                if (frames.isEmpty()) return PreparedMedia(
                    note = "فایل رسید اما امکان استخراج تصویر قابل‌استفاده از ویدیو وجود نداشت."
                )
                val audioFile = runCatching { extractAacAudio(context, input) }.getOrNull()
                PreparedMedia(
                    images = frames,
                    audio = audioFile,
                    videoSampled = true,
                    note = "از فایل ویدیوی واقعی " + frames.size +
                        " فریم نمونه‌برداری شد؛ این کار مشاهده تمام فریم‌ها نیست." +
                        if (audioFile == null) " صدای ویدیو جدا نشد." else ""
                )
            } finally { retriever.release() }
        } finally {
            if (remoteFile != null) runCatching { remoteFile.delete() }
        }
    }

    private fun imageFile(file: File): PreparedMedia {
        val bytes = file.readBytes()
        if (bytes.size > 5_000_000) return PreparedMedia(note = "عکس بیش از حد بزرگ است.")
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return PreparedMedia(note = "تصویر قابل خواندن نیست.")
        return PreparedMedia(images = listOf(bitmapToPart(bitmap)), note = "تصویر ارسالی بررسی شد.")
    }

    private fun bitmapToPart(bitmap: Bitmap): ImagePart {
        val width = bitmap.width
        val height = bitmap.height
        val scale = minOf(1f, 960f / maxOf(width, height).coerceAtLeast(1))
        val resized = if (scale < 1f) Bitmap.createScaledBitmap(
            bitmap,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        ) else bitmap
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 66, out)
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        return ImagePart("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }

    private fun isAllowedUrl(url: URL): Boolean {
        val host = url.host.lowercase()
        return url.protocol == "https" && url.userInfo == null &&
            host.matches(Regex("[a-z0-9-]+(\\.[a-z0-9-]+)+")) &&
            !host.endsWith(".local") && !host.endsWith(".internal")
    }

    private fun openUrl(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(4) { hop ->
            require(isAllowedUrl(current)) { "نشانی غیرمجاز است." }
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 25000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "HamisheyarMedia/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                if (hop == 3) { conn.disconnect(); error("تعداد تغییر مسیر زیاد است.") }
                val target = conn.getHeaderField("Location") ?: error("تغییر مسیر نامعتبر")
                current = URL(current, target)
                conn.disconnect()
            } else {
                if (code != 200) { conn.disconnect(); error("فایل عمومی در دسترس نیست.") }
                return conn
            }
        }
        error("نشانی در دسترس نیست.")
    }

    private fun downloadPublicVideo(context: Context, link: String): File? {
        val startingUrl = URL(link)
        if (!isAllowedUrl(startingUrl)) return null
        var conn = openUrl(startingUrl.toString())
        var type = conn.contentType.orEmpty().lowercase()
        if (type.contains("html")) {
            val html = conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val bytes = ByteArray(8192)
                while (output.size() < 800_000) {
                    val read = input.read(bytes, 0, minOf(bytes.size, 800_000 - output.size()))
                    if (read < 0) break
                    output.write(bytes, 0, read)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
            conn.disconnect()
            // Not every Instagram page exposes a public video URL; do not bypass login or DRM.
            val meta = Regex(
                "<meta[^>]*property=[\"']og:video(?::secure_url)?[\"'][^>]*content=[\"']([^\"']+)",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.get(1)
                ?: Regex(
                    "<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:video",
                    RegexOption.IGNORE_CASE
                ).find(html)?.groupValues?.get(1)
                ?: return null
            val address = meta.replace("&amp;", "&")
            val next = URL(address)
            if (!isAllowedUrl(next)) return null
            conn = openUrl(next.toString())
            type = conn.contentType.orEmpty().lowercase()
        }
        try {
            if (!type.startsWith("video/") && !type.contains("octet-stream")) return null
            if (conn.contentLengthLong > MAX_BYTES) return null
            val folder = File(context.cacheDir, "video-intake").apply { mkdirs() }
            val output = File.createTempFile("media_", ".mp4", folder)
            return try {
                conn.inputStream.use { input ->
                    output.outputStream().use { target ->
                        val buffer = ByteArray(16_384)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) error("حجم ویدیو بیش از حد است.")
                            target.write(buffer, 0, n)
                        }
                    }
                }
                if (output.length() > 128) output else {
                    output.delete()
                    null
                }
            } catch (_: Exception) {
                output.delete()
                null
            }
        } finally { conn.disconnect() }
    }

    private fun extractAacAudio(context: Context, video: File): File? {
        val extractor = MediaExtractor()
        extractor.setDataSource(video.absolutePath)
        try {
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString("mime") == "audio/mp4a-latm"
            } ?: return null
            extractor.selectTrack(track)
            val file = File.createTempFile("speech_", ".m4a", context.cacheDir)
            val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var started = false
            try {
                val newTrack = muxer.addTrack(extractor.getTrackFormat(track))
                muxer.start()
                started = true
                val buffer = ByteBuffer.allocate(512 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    muxer.writeSampleData(newTrack, buffer, info)
                    if (!extractor.advance() || file.length() > 19_000_000L) break
                }
            } finally {
                if (started) runCatching { muxer.stop() }
                muxer.release()
            }
            return if (file.length() in 1..19_000_000) file else {
                file.delete(); null
            }
        } finally { extractor.release() }
    }
}
