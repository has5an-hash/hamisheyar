package ir.hamisheyar.app.voice

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import ir.hamisheyar.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class VoiceAssetStatus(
    val sttReady: Boolean,
    val ttsReady: Boolean,
    val downloading: Boolean,
    val progress: Float,
    val message: String
) {
    val fullyReady: Boolean get() = sttReady && ttsReady
}

object VoiceAssetsManager {
    private const val STT_FILE = "ggml-tiny.bin"
    private const val TTS_ARCHIVE = "vits-piper-fa_IR-ganji-medium-int8.tar.bz2"

    private const val STT_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin?download=true"
    private const val TTS_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-ganji-medium-int8.tar.bz2"

    private fun voiceBase(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "voice").apply { mkdirs() }

    fun sttFile(context: Context): File =
        File(voiceBase(context), STT_FILE)

    fun ttsRoot(context: Context): File =
        File(voiceBase(context), "persian-tts")

    fun ttsModelFile(context: Context): File? =
        ttsRoot(context).walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("onnx", ignoreCase = true) }

    fun ttsTokensFile(context: Context): File? =
        ttsRoot(context).walkTopDown()
            .firstOrNull { it.isFile && it.name == "tokens.txt" }

    fun ttsEspeakDir(context: Context): File? =
        ttsRoot(context).walkTopDown()
            .firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }

    fun isSttReady(context: Context): Boolean =
        sttFile(context).isFile && sttFile(context).length() > 50L * 1024L * 1024L

    fun isTtsReady(context: Context): Boolean =
        ttsModelFile(context)?.isFile == true &&
            ttsTokensFile(context)?.isFile == true &&
            ttsEspeakDir(context)?.isDirectory == true

    fun startInstall(context: Context) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val voiceDir = voiceBase(context)

        if (!isSttReady(context) && AppSettings.voiceWhisperDownloadId(context) <= 0L) {
            sttFile(context).delete()
            val request = DownloadManager.Request(Uri.parse(STT_URL))
                .setTitle("همیشه‌یار — تشخیص گفتار فارسی")
                .setDescription("دانلود موتور گفتار آفلاین")
                .setMimeType("application/octet-stream")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, "voice", STT_FILE)
            val id = manager.enqueue(request)
            AppSettings.setVoiceWhisperDownloadId(context, id)
            DiagnosticsLogger.log(context, "VOICE", "Whisper download started id=$id")
        }

        if (!isTtsReady(context) && AppSettings.voiceTtsDownloadId(context) <= 0L) {
            File(voiceDir, TTS_ARCHIVE).delete()
            val request = DownloadManager.Request(Uri.parse(TTS_URL))
                .setTitle("همیشه‌یار — صدای فارسی")
                .setDescription("دانلود صدای فارسی آفلاین")
                .setMimeType("application/x-bzip2")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, "voice", TTS_ARCHIVE)
            val id = manager.enqueue(request)
            AppSettings.setVoiceTtsDownloadId(context, id)
            DiagnosticsLogger.log(context, "VOICE", "Persian TTS download started id=$id")
        }
    }

    suspend fun inspectAndPrepare(context: Context): VoiceAssetStatus = withContext(Dispatchers.IO) {
        var sttProgress = 0f
        var ttsProgress = 0f
        var downloading = false
        var message = ""

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val sttId = AppSettings.voiceWhisperDownloadId(context)
        if (sttId > 0L) {
            val state = inspectDownload(manager, sttId)
            sttProgress = state.progress
            downloading = downloading || state.active
            when {
                state.success -> {
                    AppSettings.setVoiceWhisperPath(context, sttFile(context).absolutePath)
                    AppSettings.clearVoiceWhisperDownload(context)
                    message = "تشخیص گفتار آماده شد."
                }
                state.failed -> {
                    AppSettings.clearVoiceWhisperDownload(context)
                    message = "دانلود تشخیص گفتار ناموفق بود."
                }
                else -> message = "در حال دانلود تشخیص گفتار…"
            }
        }

        val ttsId = AppSettings.voiceTtsDownloadId(context)
        if (ttsId > 0L) {
            val state = inspectDownload(manager, ttsId)
            ttsProgress = state.progress
            downloading = downloading || state.active
            when {
                state.success -> {
                    val archive = File(voiceBase(context), TTS_ARCHIVE)
                    if (!isTtsReady(context)) {
                        message = "در حال آماده‌سازی صدای فارسی…"
                        runCatching {
                            extractTtsArchive(context, archive)
                        }.onFailure {
                            DiagnosticsLogger.log(context, "VOICE", "TTS extraction failed", it)
                            message = "آماده‌سازی صدای فارسی ناموفق بود."
                        }
                    }
                    if (isTtsReady(context)) {
                        AppSettings.setVoiceTtsDir(context, ttsRoot(context).absolutePath)
                        AppSettings.clearVoiceTtsDownload(context)
                        archive.delete()
                        message = "صدای فارسی آماده شد."
                    }
                }
                state.failed -> {
                    AppSettings.clearVoiceTtsDownload(context)
                    message = "دانلود صدای فارسی ناموفق بود."
                }
                else -> if (message.isBlank()) message = "در حال دانلود صدای فارسی…"
            }
        }

        val sttReady = isSttReady(context)
        val ttsReady = isTtsReady(context)
        val progress = when {
            sttReady && ttsReady -> 1f
            sttId > 0L && ttsId > 0L -> (sttProgress + ttsProgress) / 2f
            sttId > 0L -> sttProgress * 0.5f
            ttsId > 0L -> 0.5f + ttsProgress * 0.5f
            else -> 0f
        }.coerceIn(0f, 1f)

        if (sttReady) AppSettings.setVoiceWhisperPath(context, sttFile(context).absolutePath)
        if (ttsReady) AppSettings.setVoiceTtsDir(context, ttsRoot(context).absolutePath)

        VoiceAssetStatus(
            sttReady = sttReady,
            ttsReady = ttsReady,
            downloading = downloading,
            progress = progress,
            message = when {
                sttReady && ttsReady -> "حالت ویس کاملاً آفلاین آماده است."
                message.isNotBlank() -> message
                else -> "برای حالت ویس مستقل، بسته صوتی را نصب کن."
            }
        )
    }

    fun clear(context: Context) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        listOf(
            AppSettings.voiceWhisperDownloadId(context),
            AppSettings.voiceTtsDownloadId(context)
        ).filter { it > 0L }.forEach { runCatching { manager.remove(it) } }

        voiceBase(context).deleteRecursively()
        AppSettings.clearVoiceWhisperDownload(context)
        AppSettings.clearVoiceTtsDownload(context)
        AppSettings.setVoiceWhisperPath(context, null)
        AppSettings.setVoiceTtsDir(context, null)
    }

    private data class DownloadState(
        val active: Boolean,
        val progress: Float,
        val success: Boolean,
        val failed: Boolean
    )

    private fun inspectDownload(manager: DownloadManager, id: Long): DownloadState {
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return DownloadState(false, 0f, false, true)
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val progress = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            return DownloadState(
                active = status == DownloadManager.STATUS_RUNNING ||
                    status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_PAUSED,
                progress = progress,
                success = status == DownloadManager.STATUS_SUCCESSFUL,
                failed = status == DownloadManager.STATUS_FAILED
            )
        }
    }

    private fun extractTtsArchive(context: Context, archive: File) {
        require(archive.isFile && archive.length() > 5L * 1024L * 1024L) {
            "TTS archive is missing or incomplete"
        }

        val destination = ttsRoot(context)
        destination.deleteRecursively()
        destination.mkdirs()

        BZip2CompressorInputStream(
            BufferedInputStream(FileInputStream(archive))
        ).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                var entry = tar.nextTarEntry
                while (entry != null) {
                    val out = File(destination, entry.name).canonicalFile
                    require(out.path.startsWith(destination.canonicalPath + File.separator)) {
                        "Unsafe archive path"
                    }

                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            tar.copyTo(output)
                        }
                    }
                    entry = tar.nextTarEntry
                }
            }
        }

        require(isTtsReady(context)) { "TTS model files were not found after extraction" }
    }
}
