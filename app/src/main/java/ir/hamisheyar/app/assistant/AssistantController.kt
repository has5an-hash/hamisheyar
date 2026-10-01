package ir.hamisheyar.app.assistant

import android.content.Context
import ir.hamisheyar.app.ai.FastReplyEngine
import ir.hamisheyar.app.ai.LocalAiEngine
import ir.hamisheyar.app.ai.SafeAiClient
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.service.NotificationActionRegistry
import ir.hamisheyar.app.settings.AppSettings
import ir.hamisheyar.app.web.WebResearchService

enum class AssistantAction {
    NONE,
    START_VOICE_NOTE,
    STOP_AND_SHARE_VOICE
}

data class AssistantResult(
    val text: String,
    val action: AssistantAction = AssistantAction.NONE
)

object AssistantController {
    suspend fun handle(context: Context, rawInput: String): AssistantResult {
        val input = normalize(rawInput)
        val latest = LocalStore(context).latestEvent()

        if ((input.contains("کیه") || input.contains("کی بود")) && latest != null) {
            return AssistantResult("آخرین پیام یا اعلان از ${latest.sender} در ${latest.source} است.")
        }

        if ((input.contains("چی گفته") || input.contains("چی نوشته") || input.contains("پیامش چیه")) && latest != null) {
            return AssistantResult("${latest.sender} گفته: ${latest.body}")
        }

        if ((input.contains("ویسش") || input.contains("صوتش")) && (input.contains("پلی") || input.contains("پخش"))) {
            val ok = NotificationActionRegistry.playLatest(context)
            return AssistantResult(
                if (ok) "پخشش کردم." else "این پیام‌رسان امکان پخش مستقیم ویس از اعلان را در اختیار همیشه‌یار نگذاشته."
            )
        }

        if (input.contains("با ویس") && (input.contains("جواب") || input.contains("پیام"))) {
            return AssistantResult("باشه، الان بگو. وقتی تمام شد دکمه پایان و ارسال را بزن.", AssistantAction.START_VOICE_NOTE)
        }

        if (input == "ارسال کن" || input.endsWith("ارسال کن")) {
            return AssistantResult("باشه.", AssistantAction.STOP_AND_SHARE_VOICE)
        }

        val replyText = extractReply(rawInput)
        if (replyText != null) {
            val ok = NotificationActionRegistry.replyLatest(context, replyText)
            return AssistantResult(
                if (ok) "فرستادم: $replyText"
                else "برای آخرین اعلان، دکمه پاسخ سریع در اختیار همیشه‌یار نیست. خود پیام‌رسان باید این قابلیت را در اعلانش ارائه کند."
            )
        }

        FastReplyEngine.tryReply(rawInput)?.let {
            return AssistantResult(it)
        }

        val eventContext = latest?.let {
            "آخرین رویداد گوشی: منبع=${it.source}، فرستنده=${it.sender}، متن=${it.body}"
        }.orEmpty()

        if (!LocalAiEngine.isConfigured(context)) {
            return AssistantResult(
                "برای سؤال‌های آزاد، اول یک مدل GGUF محلی از تنظیمات وارد کن. فرمان‌های پیام‌ها، اعلان‌ها و پاسخ سریع بدون مدل هم کار می‌کنند."
            )
        }

        val webContext = if (AppSettings.webResearchEnabled(context)) {
            runCatching {
                val hits = WebResearchService.search(rawInput)
                if (hits.isEmpty()) "" else "نتایج جست‌وجوی وب:\n" + WebResearchService.asContext(hits)
            }.getOrDefault("")
        } else ""

        val contextBlock = listOf(eventContext, webContext).filter { it.isNotBlank() }.joinToString("\n\n")
        return runCatching {
            val answer = SafeAiClient.answer(context, rawInput, contextBlock)
            AssistantResult(answer.text)
        }.getOrElse {
            AssistantResult("نتونستم مدل محلی را اجرا کنم: ${it.message ?: "خطای نامشخص"}")
        }
    }

    private fun extractReply(input: String): String? {
        val patterns = listOf(
            Regex("""(?:جوابش|جواب|بهش جواب بده).*?بگو\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:بهش|براش)\s+بگو\s+(.+)""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(input)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun normalize(value: String): String = value
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace("‌", " ")
        .trim()
        .lowercase()
}
