package ir.hamisheyar.app.ai

object FastReplyEngine {
    fun tryReply(input: String): String? {
        val text = input
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace("‌", " ")
            .trim()
            .lowercase()

        return when {
            text.isBlank() -> null
            text in setOf("سلام", "سلام خوبی", "سلام خوبی؟", "سلام چطوری", "سلام چطوری؟") ->
                "سلام! خوبم، مرسی 😊 تو چطوری؟"

            text in setOf("خوبی", "خوبی؟", "چطوری", "چطوری؟") ->
                "خوبم، مرسی 😊 چی لازم داری؟"

            text.contains("اسمت چیه") || text.contains("کی هستی") ->
                "من همیشه‌یارم؛ دستیار شخصی روی گوشی‌ت."

            text in setOf("مرسی", "ممنون", "دمت گرم", "تشکر") ->
                "خواهش می‌کنم 🌱"

            text == "تست" || text == "test" ->
                "همیشه‌یار فعاله."

            else -> null
        }
    }
}
