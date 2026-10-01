package ir.hamisheyar.app.assistant

import android.content.Context
import ir.hamisheyar.app.ai.FastReplyEngine
import ir.hamisheyar.app.ai.LocalAiEngine
import ir.hamisheyar.app.ai.SafeAiClient
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.service.NotificationActionRegistry
import ir.hamisheyar.app.settings.AppSettings
import ir.hamisheyar.app.web.WebResearchService

enum class AssistantAction { NONE, START_VOICE_NOTE, STOP_AND_SHARE_VOICE }

data class AssistantResult(val text: String, val action: AssistantAction = AssistantAction.NONE)

object AssistantController {
    suspend fun handle(context: Context, rawInput: String): AssistantResult {
        val latest = LocalStore(context).latestEvent()

        when (val command = VoiceCommandRouter.route(rawInput)) {
            VoiceCommand.ReadLatestMessage -> {
                return if (latest != null) AssistantResult("${latest.sender} گفته: ${latest.body}")
                else AssistantResult("هنوز پیامی برای خوندن ندارم.")
            }
            is VoiceCommand.Reply -> {
                val ok = NotificationActionRegistry.replyLatest(context, command.text)
                return AssistantResult(
                    if (ok) "فرستادم: ${command.text}"
                    else "برای آخرین اعلان، دکمه پاسخ سریع در اختیار همیشه‌یار نیست. خود پیام‌رسان باید این قابلیت را در اعلانش ارائه کند."
                )
            }
            is VoiceCommand.ChangeMode -> {
                CompanionModeStore.set(context, command.mode)
                val text = when (command.mode) {
                    CompanionMode.ACTIVE -> "فعالم."
                    CompanionMode.STANDBY -> "باشه، استندبای می‌مونم."
                    CompanionMode.MUTED -> "باشه، دیگه حرف نمی‌زنم."
                    CompanionMode.OFF -> "همراه صوتی خاموش شد."
                }
                return AssistantResult(text)
            }
            null -> Unit
        }

        val mode = CompanionModeStore.get(context)
        if (mode == CompanionMode.OFF) {
            return AssistantResult("همراه صوتی خاموشه؛ بگو «بیدار شو» تا دوباره فعال بشم.")
        }

        val input = normalize(rawInput)
        if ((input.contains("کیه") || input.contains("کی بود")) && latest != null) {
            return AssistantResult("آخرین پیام یا اعلان از ${latest.sender} در ${latest.source} است.")
        }
        if ((input.contains("چی گفته") || input.contains("چی نوشته") || input.contains("پیامش چیه")) && latest != null) {
            return AssistantResult("${latest.sender} گفته: ${latest.body}")
        }
        if ((input.contains("ویسش") || input.contains("صوتش")) && (input.contains("پلی") || input.contains("پخش"))) {
            val ok = NotificationActionRegistry.playLatest(context)
            return AssistantResult(if (ok) "پخشش کردم." else "این پیام‌رسان امکان پخش مستقیم ویس از اعلان را در اختیار همیشه‌یار نگذاشته.")
        }
        if (input.contains("با ویس") && (input.contains("جواب") || input.contains("پیام"))) {
            return AssistantResult("باشه، الان بگو. وقتی تمام شد دکمه پایان و ارسال را بزن.", AssistantAction.START_VOICE_NOTE)
        }
        if (input == "ارسال کن" || input.endsWith("ارسال کن")) {
            return AssistantResult("باشه.", AssistantAction.STOP_AND_SHARE_VOICE)
        }

        FastReplyEngine.tryReply(rawInput)?.let { return AssistantResult(it) }

        val eventContext = latest?.let {
            "آخرین رویداد گوشی: منبع=${it.source}، فرستنده=${it.sender}، متن=${it.body}"
        }.orEmpty()

        if (!LocalAiEngine.isConfigured(context)) {
            return AssistantResult("برای سؤال‌های آزاد، اول یک مدل GGUF محلی از تنظیمات وارد کن. فرمان‌های پیام‌ها، اعلان‌ها و پاسخ سریع بدون مدل هم کار می‌کنند.")
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

    private fun normalize(value: String): String = value
        .replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ").trim().lowercase()
}
