package ir.hamisheyar.app.ai

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import ir.hamisheyar.app.settings.AppSettings
import java.io.File

data class ModelPreset(
    val id: String,
    val title: String,
    val description: String,
    val fileName: String,
    val url: String,
    val expectedBytes: Long
)

data class ModelDownloadState(
    val active: Boolean,
    val progress: Float,
    val message: String,
    val completedPath: String? = null,
    val failed: Boolean = false
)

object ModelDownloadManager {
    val FAST = ModelPreset(
        id = "qwen3-0.6b-q4",
        title = "مدل سبک",
        description = "حدود ۴۲۹ مگابایت؛ مناسب گوشی‌های ضعیف‌تر و پاسخ‌های سریع‌تر",
        fileName = "Qwen3-0.6B-Q4_0.gguf",
        url = "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_0.gguf?download=true",
        expectedBytes = 429L * 1024L * 1024L
    )

    val BALANCED = ModelPreset(
        id = "qwen3-1.7b-q4km",
        title = "مدل پیشنهادی",
        description = "حدود ۱.۲۸ گیگابایت؛ کیفیت بهتر برای گوشی‌های قوی‌تر",
        fileName = "Qwen3-1.7B-Q4_K_M.gguf",
        url = "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true",
        expectedBytes = 1_280L * 1024L * 1024L
    )

    fun start(context: Context, preset: ModelPreset): Long {
        val root = context.getExternalFilesDir(null)
            ?: error("فضای ذخیره‌سازی برنامه در دسترس نیست.")
        val modelDir = File(root, "models").apply { mkdirs() }
        File(modelDir, preset.fileName).delete()

        val request = DownloadManager.Request(Uri.parse(preset.url))
            .setTitle("همیشه‌یار — " + preset.title)
            .setDescription("در حال دانلود مدل هوش مصنوعی محلی")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, null, "models/" + preset.fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        AppSettings.setModelDownload(context, id, preset.fileName)
        return id
    }

    fun inspect(context: Context): ModelDownloadState {
        val id = AppSettings.modelDownloadId(context)
        if (id <= 0L) return ModelDownloadState(false, 0f, "")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                AppSettings.clearModelDownload(context)
                return ModelDownloadState(false, 0f, "دانلود پیدا نشد.", failed = true)
            }

            val status = cursor.int(DownloadManager.COLUMN_STATUS)
            val downloaded = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val total = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val progress = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val name = AppSettings.modelDownloadFile(context)
                    val root = context.getExternalFilesDir(null)
                    val file = if (root != null && !name.isNullOrBlank()) File(root, "models/" + name) else null
                    if (file != null && file.isFile && file.length() > 1_000_000L) {
                        AppSettings.setModelPath(context, file.absolutePath)
                        AppSettings.clearModelDownload(context)
                        ModelDownloadState(false, 1f, "مدل آماده است.", file.absolutePath)
                    } else {
                        AppSettings.clearModelDownload(context)
                        ModelDownloadState(false, 0f, "فایل مدل بعد از دانلود پیدا نشد.", failed = true)
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.int(DownloadManager.COLUMN_REASON)
                    AppSettings.clearModelDownload(context)
                    ModelDownloadState(false, progress, "دانلود مدل ناموفق بود (کد $reason).", failed = true)
                }

                DownloadManager.STATUS_PAUSED -> ModelDownloadState(true, progress, "دانلود موقتاً متوقف شده…")
                DownloadManager.STATUS_PENDING -> ModelDownloadState(true, progress, "دانلود در صف است…")
                else -> ModelDownloadState(true, progress, if (total > 0L) "در حال دانلود مدل… ${(progress * 100).toInt()}٪" else "در حال دانلود مدل…")
            }
        }
    }

    fun cancel(context: Context) {
        val id = AppSettings.modelDownloadId(context)
        if (id > 0L) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.remove(id)
        }
        val name = AppSettings.modelDownloadFile(context)
        val root = context.getExternalFilesDir(null)
        if (root != null && !name.isNullOrBlank()) File(root, "models/" + name).delete()
        AppSettings.clearModelDownload(context)
    }

    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
}
