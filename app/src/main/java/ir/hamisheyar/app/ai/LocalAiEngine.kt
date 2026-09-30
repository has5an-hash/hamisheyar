package ir.hamisheyar.app.ai

import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import ir.hamisheyar.app.settings.AppSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class AiAnswer(val text: String, val tokensPerSecond: Float)

object LocalAiEngine {
    private val mutex = Mutex()
    private var model: LlamaModel? = null
    private var loadedPath: String? = null

    fun isConfigured(context: Context): Boolean {
        val path = AppSettings.modelPath(context) ?: return false
        return File(path).isFile
    }

    suspend fun answer(
        context: Context,
        prompt: String,
        extraContext: String = ""
    ): AiAnswer = mutex.withLock {
        val active = ensureModel(context)
        val finalPrompt = buildString {
            if (extraContext.isNotBlank()) {
                appendLine("اطلاعات زمینه‌ای:")
                appendLine(extraContext)
                appendLine()
            }
            append(prompt)
        }
        val result = Llama.complete(
            active,
            prompt = finalPrompt,
            systemPrompt = SYSTEM_PROMPT,
            maxTokens = 512
        )
        AiAnswer(result.text.trim(), result.tokensPerSecond)
    }

    suspend fun reset() = mutex.withLock {
        model?.let(Llama::releaseModel)
        model = null
        loadedPath = null
    }

    private suspend fun ensureModel(context: Context): LlamaModel {
        val path = AppSettings.modelPath(context)
            ?: error("مدل محلی هنوز انتخاب نشده است.")
        if (!File(path).isFile) error("فایل مدل پیدا نشد.")

        val current = model
        if (current != null && current.isLoaded && loadedPath == path) return current

        current?.let(Llama::releaseModel)
        val threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)
        return Llama.loadModel(
            modelPath = path,
            config = LlamaConfig(
                contextSize = 3072,
                threads = threads,
                gpuLayers = 0,
                temperature = 0.45f,
                topP = 0.9f,
                topK = 40
            )
        ).also {
            model = it
            loadedPath = path
        }
    }

    private const val SYSTEM_PROMPT = """
تو «همیشه‌یار» هستی؛ دستیار شخصی فارسی‌زبان، دقیق، آرام و کاربردی روی گوشی کاربر.
پاسخ‌ها را پیش‌فرض کوتاه، روان و فارسی بده مگر کاربر جزئیات بخواهد.
برای ادعاهای حساس یا مشکوک با قطعیت ساختگی حرف نزن و تفاوت بین واقعیت، احتمال و حدس را روشن کن.
اگر اطلاعات وب در متن زمینه‌ای آمده، فقط از همان اطلاعات برای بخش به‌روز استفاده کن.
هیچ‌وقت وانمود نکن کاری را انجام داده‌ای که واقعاً انجام نشده است.
"""
}
