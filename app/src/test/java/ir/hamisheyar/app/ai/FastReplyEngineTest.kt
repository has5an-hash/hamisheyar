package ir.hamisheyar.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastReplyEngineTest {

    @Test
    fun persianGreetingRepliesImmediately() {
        assertEquals(
            "سلام! خوبم، مرسی 😊 تو چطوری؟",
            FastReplyEngine.tryReply("سلام خوبی")
        )
    }

    @Test
    fun normalGreetingRepliesImmediately() {
        assertEquals(
            "سلام! خوبم، مرسی 😊 تو چطوری؟",
            FastReplyEngine.tryReply("سلام")
        )
    }

    @Test
    fun healthCheckDoesNotNeedLlm() {
        assertEquals(
            "همیشه‌یار فعاله.",
            FastReplyEngine.tryReply("تست")
        )
    }

    @Test
    fun unknownTextFallsThroughToLlm() {
        assertNull(FastReplyEngine.tryReply("نظریه نسبیت را ساده توضیح بده"))
    }
}
