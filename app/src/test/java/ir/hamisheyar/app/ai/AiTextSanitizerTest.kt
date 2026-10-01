package ir.hamisheyar.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTextSanitizerTest {

    @Test
    fun removesThinkingBlockAndKeepsFinalAnswer() {
        val raw = "<think>reasoning that must stay hidden</think>\n\nسلام، آماده‌ام."
        assertEquals("سلام، آماده‌ام.", AiTextSanitizer.clean(raw))
    }

    @Test
    fun returnsEmptyForUnfinishedThinkingOnlyOutput() {
        val raw = "<think>long unfinished reasoning"
        assertEquals("", AiTextSanitizer.clean(raw))
    }

    @Test
    fun removesModelEndTokens() {
        val raw = "پاسخ نهایی<|im_end|>"
        assertEquals("پاسخ نهایی", AiTextSanitizer.clean(raw))
    }
}
