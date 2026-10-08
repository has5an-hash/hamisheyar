package ir.hamisheyar.app.brain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingPolicyTest {
    @Test fun shortQuestionsUseFastPath() {
        assertTrue(RoutingPolicy.fastAnswer("سلام خوبی؟"))
        assertFalse(RoutingPolicy.needsReview("سلام خوبی؟"))
    }

    @Test fun suspectedScamMustBeReviewed() {
        assertTrue(RoutingPolicy.needsReview("این تبلیغ کلاهبرداریه؟"))
        assertFalse(RoutingPolicy.fastAnswer("این تبلیغ کلاهبرداریه؟"))
    }

    @Test fun longTasksDoNotUseQuickPath() {
        assertFalse(RoutingPolicy.fastAnswer("این موضوع را کامل برایم توضیح بده. ".repeat(12)))
    }

    @Test fun instagramAndYoutubeVideoLinksAreRecognized() {
        assertTrue(RoutingPolicy.containsSocialVideoLink("https://www.instagram.com/reel/C7Test/"))
        assertTrue(RoutingPolicy.containsSocialVideoLink("https://youtu.be/abcd1234"))
        assertFalse(RoutingPolicy.containsSocialVideoLink("https://example.com/anything"))
    }
}
