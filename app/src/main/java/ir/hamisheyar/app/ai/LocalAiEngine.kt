package ir.hamisheyar.app.ai

import android.app.ActivityManager
import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import ir.hamisheyar.app.settings.AppSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile

data class AiAnswer(
    val text: String,
    val tokensPerSecond: Float,
    val durationMs: Long,
    val modelName: String
)

data class ModelHealth(
    val configured: Boolean,
    val validGguf: Boolean,
    val modelName: String?,
    val fileSizeMb: Long,
    val availableRamMb: Long,
    val warning: String?
)

object LocalAiEngine {
    private val mutex = Mutex()
    private var model: LlamaModel? = null
    private var loadedPath: String? = null

    fun isConfigured(context: Context): Boolean {
        val path = AppSettings.modelPath(context) ?: return false
        val file = File(path)
        return file.isFile && isValidGguf(file)
    }

    fun health(context: Context): ModelHealth {
        val path = AppSettings.modelPath(context)
        val file = path?.let(::File)
        val configured = file?.isFile == true
        val valid = configured && isValidGguf(file!!)
        val fileSizeMb = if (configured) file!!.length() / (1024L * 1024L) else 0L
        val availableRamMb = availableRamMb(context)

        val warning = when {
            !configured -> "مدل محلی هنوز انتخاب نشده است."
            !valid -> "فایل انتخاب‌شده GGUF سالم نیست یا دانلود ناقص مانده است."
            fileSizeMb > 900 && availableRamMb < 2200 ->
                "مدل برای RAM آزاد فعلی سنگین است؛ مدل سبک را انتخاب کن."
            fileSizeMb > 450 && availableRamMb < 1200 ->
                "RAM آزاد گوشی کم است و مدل ممکن است بسیار کند یا ناپایدار شود."
            else -> null
        }

        return ModelHealth(
            configured = configured,
            validGguf = valid,
            modelName = file?.name,
            fileSizeMb = fileSizeMb,
            availableRamMb = availableRamMb,
            warning = warning
        )
    }

    suspend fun answer(
        context: Context,
        prompt: String,
        extraContext: String = "",
        onStage: (String) -> Unit = {}
    ): AiAnswer = mutex.withLock {
        val started = System.currentTimeMillis()

        val instant = if (extraContext.isBlank()) FastReplyEngine.tryReply(prompt) else null
        if (instant != null) {
            return@withLock AiAnswer(
                text = instant,
                tokensPerSecond = 0f,
                durationMs = System.currentTimeMillis() - started,
                modelName = "instant-local"
            )
        }

        val path = AppSettings.modelPath(context)
            ?: error("مدل محلی هنوز انتخاب نشده است.")
        val file = File(path)
        if (!file.isFile) error("فایل مدل پیدا نشد.")
        if (!isValidGguf(file)) error("فایل مدل GGUF معتبر نیست یا ناقص دانلود شده است.")

        val memoryWarning = health(context).warning
        DiagnosticsLogger.log(
            context,
            "AI",
            "request model=${file.name} sizeMb=${file.length() / (1024L * 1024L)} ramMb=${availableRamMb(context)} warning=${memoryWarning.orEmpty()}"
        )

        onStage(
            if (loadedPath == path && model?.isLoaded == true) {
                "مدل آماده است؛ دارم جواب می‌سازم…"
            } else {
                "دارم مدل را روی گوشی آماده می‌کنم… اولین پاسخ کمی طول می‌کشد."
            }
        )

        val active = ensureModel(context, file)
        onStage("مدل آماده شد؛ در حال تولید پاسخ…")

        val finalPrompt = buildString {
            if (extraContext.isNotBlank()) {
                appendLine("اطلاعات زمینه‌ای:")
                appendLine(extraContext)
                appendLine()
            }
            append(prompt.trim())
            if (isQwen3(file)) {
                appendLine()
                append("/no_think")
            }
        }

        val first = runCompletion(
            active = active,
            prompt = finalPrompt,
            maxTokens = if (isQwen3(file)) 72 else 80
        )

        var clean = AiTextSanitizer.clean(first.text)
        var tps = first.tokensPerSecond

        if (clean.isBlank()) {
            DiagnosticsLogger.log(
                context,
                "AI",
                "empty/think-only response from ${file.name}; retrying in fast mode"
            )
            onStage("پاسخ اول ناقص بود؛ دارم یک بار سریع دوباره تلاش می‌کنم…")

            val retryPrompt = buildString {
                appendLine(prompt.trim())
                appendLine()
                appendLine("فقط پاسخ نهایی، کوتاه و مستقیم را بنویس. وارد فرایند فکر کردن نشو.")
                append("/no_think")
            }
            val retry = Llama.complete(
                active,
                prompt = retryPrompt,
                systemPrompt = "",
                maxTokens = 48
            )
            clean = AiTextSanitizer.clean(retry.text)
            tps = retry.tokensPerSecond
        }

        if (clean.isBlank()) {
            val error = IllegalStateException(
                "مدل خروجی قابل نمایش تولید نکرد. مدل را از تنظیمات تست کن یا مدل سبک پیشنهادی را نصب کن."
            )
            DiagnosticsLogger.log(context, "AI", "generation failed: empty output", error)
            throw error
        }

        val duration = System.currentTimeMillis() - started
        DiagnosticsLogger.log(
            context,
            "AI",
            "success model=${file.name} durationMs=$duration tps=$tps chars=${clean.length}"
        )

        AiAnswer(
            text = clean,
            tokensPerSecond = tps,
            durationMs = duration,
            modelName = file.name
        )
    }

    suspend fun selfTest(
        context: Context,
        onStage: (String) -> Unit = {}
    ): AiAnswer {
        return answer(
            context = context,
            prompt = "فقط همین عبارت را کوتاه جواب بده: آماده‌ام",
            onStage = onStage
        )
    }

    suspend fun reset() = mutex.withLock {
        model?.let(Llama::releaseModel)
        model = null
        loadedPath = null
    }

    private suspend fun ensureModel(context: Context, file: File): LlamaModel {
        val path = file.absolutePath
        val current = model
        if (current != null && current.isLoaded && loadedPath == path) return current

        current?.let(Llama::releaseModel)
        model = null
        loadedPath = null

        val fileSizeMb = file.length() / (1024L * 1024L)
        val freeRam = availableRamMb(context)
        val contextSize = when {
            freeRam < 1200 -> 512
            freeRam < 2200 -> 768
            fileSizeMb <= 550 -> 1024
            else -> 1280
        }
        val threads = when {
            freeRam < 1600 -> 2
            Runtime.getRuntime().availableProcessors() >= 8 -> 3
            else -> 2
        }

        DiagnosticsLogger.log(
            context,
            "AI",
            "loading model=${file.name} ctx=$contextSize threads=$threads"
        )

        return try {
            Llama.loadModel(
                modelPath = path,
                config = LlamaConfig(
                    contextSize = contextSize,
                    threads = threads,
                    gpuLayers = 0,
                    temperature = 0.45f,
                    topP = 0.85f,
                    topK = 20
                )
            ).also {
                model = it
                loadedPath = path
                DiagnosticsLogger.log(context, "AI", "model loaded: ${file.name}")
            }
        } catch (t: Throwable) {
            DiagnosticsLogger.log(context, "AI", "model load failed: ${file.name}", t)
            throw IllegalStateException(
                when {
                    t is OutOfMemoryError ->
                        "RAM گوشی برای این مدل کافی نیست. مدل سبک را از تنظیمات نصب کن."
                    else ->
                        "مدل روی این گوشی Load نشد: " + (t.message ?: "خطای نامشخص")
                },
                t
            )
        }
    }

    private suspend fun runCompletion(
        active: LlamaModel,
        prompt: String,
        maxTokens: Int
    ) = Llama.complete(
        active,
        prompt = prompt,
        systemPrompt = SYSTEM_PROMPT,
        maxTokens = maxTokens
    )

    private fun isQwen3(file: File): Boolean =
        file.name.lowercase().contains("qwen3")

    fun isValidGguf(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                magic.contentEquals(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
            }
        }.getOrDefault(false)
    }

    private fun availableRamMb(context: Context): Long {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.availMem / (1024L * 1024L)
    }

    private const val SYSTEM_PROMPT = """
تو «همیشه‌یار» هستی؛ دستیار شخصی فارسی‌زبان، دقیق، آرام و کاربردی روی گوشی کاربر.
پاسخ‌ها را کوتاه، روان و فارسی بده مگر کاربر جزئیات بخواهد.
برای مکالمه‌های ساده در یک یا دو جمله جواب بده.
پاسخ نهایی را مستقیم بده و زنجیره فکر یا تحلیل درونی را نمایش نده.
برای ادعاهای حساس یا مشکوک با قطعیت ساختگی حرف نزن و تفاوت بین واقعیت، احتمال و حدس را روشن کن.
اگر اطلاعات وب در متن زمینه‌ای آمده، فقط از همان اطلاعات برای بخش به‌روز استفاده کن.
هیچ‌وقت وانمود نکن کاری را انجام داده‌ای که واقعاً انجام نشده است.
/no_think
"""
}
