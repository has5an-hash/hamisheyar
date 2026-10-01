package ir.hamisheyar.app.ai

object AiTextSanitizer {
    private val completeThinkBlock = Regex(
        pattern = """(?s)<think>.*?</think>""",
        option = RegexOption.IGNORE_CASE
    )

    fun clean(raw: String): String {
        var value = raw
            .replace("<|im_end|>", "")
            .replace("<|endoftext|>", "")
            .trim()

        value = value.replace(completeThinkBlock, "").trim()

        if (
            value.startsWith("<think>", ignoreCase = true) &&
            !value.contains("</think>", ignoreCase = true)
        ) {
            return ""
        }

        return value.trim()
    }
}
