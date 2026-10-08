package ir.hamisheyar.app.assistant

import android.content.Context
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.service.NotificationActionRegistry

enum class AssistantAction {
    NONE,
    START_VOICE_NOTE,
    STOP_AND_SHARE_VOICE,
    ASK_BRAIN
}

data class AssistantResult(
    val text: String,
    val action: AssistantAction = AssistantAction.NONE,
    val externalPrompt: String? = null
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

        if ((input.contains("ویسش") || input.contains("صوتش")) &&
            (input.contains("پلی") || input.contains("پخش"))
        ) {
            val ok = NotificationActionRegistry.playLatest(context)
            return AssistantResult(
                if (ok) "پخشش کردم."
                else "این پیام‌رسان امکان پخش مستقیم ویس از اعلان را در اختیار همیشه‌یار نگذاشته."
            )
        }

        if (input.contains("با ویس") && (input.contains("جواب") || input.contains("پیام"))) {
            return AssistantResult(
                "باشه؛ حالت ویس همیشه‌یار را باز می‌کنم.",
                AssistantAction.START_VOICE_NOTE
            )
        }

        val replyText = extractReply(rawInput)
        if (replyText != null) {
            val ok = NotificationActionRegistry.replyLatest(context, replyText)
            return AssistantResult(
                if (ok) "فرستادم: $replyText"
                else "برای آخرین اعلان، پاسخ سریع در دسترس همیشه‌یار نیست؛ خود پیام‌رسان باید Reply را در اعلان ارائه کند."
            )
        }

        return AssistantResult(
            text = "دارم با مغز همیشه‌یار بررسی می‌کنم…",
            action = AssistantAction.ASK_BRAIN,
            externalPrompt = rawInput.trim()
        )
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
