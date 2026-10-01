package ir.hamisheyar.app.assistant

enum class CompanionMode { ACTIVE, STANDBY, MUTED, OFF }

sealed interface VoiceCommand {
    data object ReadLatestMessage : VoiceCommand
    data class Reply(val text: String) : VoiceCommand
    data class ChangeMode(val mode: CompanionMode) : VoiceCommand
}

object VoiceCommandRouter {
    fun route(rawInput: String): VoiceCommand? {
        val input = normalize(rawInput)
        if (input in setOf("پیامم رو بخون", "پیام من رو بخون", "آخرین پیام رو بخون", "آخرین پیامم رو بخون")) {
            return VoiceCommand.ReadLatestMessage
        }
        if (input in setOf("استندبای باش", "برو استندبای", "حالت استندبای")) {
            return VoiceCommand.ChangeMode(CompanionMode.STANDBY)
        }
        if (input in setOf("دیگه حرف نزن", "ساکت باش", "بی صدا باش", "برو روی بی صدا")) {
            return VoiceCommand.ChangeMode(CompanionMode.MUTED)
        }
        if (input in setOf("خاموش شو", "کامل خاموش شو")) {
            return VoiceCommand.ChangeMode(CompanionMode.OFF)
        }
        if (input in setOf("بیدار شو", "فعال شو", "برگرد")) {
            return VoiceCommand.ChangeMode(CompanionMode.ACTIVE)
        }
        extractReply(rawInput)?.let { return VoiceCommand.Reply(it) }
        return null
    }

    private fun extractReply(input: String): String? {
        val normalized = input.replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ")
        val patterns = listOf(
            Regex("""(?:جوابش|جواب|بهش جواب بده).*?بگو\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:بهش|براش)\s+بگو\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""بهش جواب بده\s+(.+)""", RegexOption.IGNORE_CASE)
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun normalize(value: String): String = value
        .replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ")
        .replace(Regex("""\s+"""), " ").trim().lowercase()
}

object CompanionModeStore {
    private const val PREFS = "hamisheyar_companion"
    private const val KEY_MODE = "mode"

    fun get(context: android.content.Context): CompanionMode {
        val value = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY_MODE, CompanionMode.ACTIVE.name)
        return runCatching { CompanionMode.valueOf(value ?: CompanionMode.ACTIVE.name) }
            .getOrDefault(CompanionMode.ACTIVE)
    }

    fun set(context: android.content.Context, mode: CompanionMode) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }
}
