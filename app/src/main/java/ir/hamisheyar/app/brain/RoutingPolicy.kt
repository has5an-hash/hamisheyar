package ir.hamisheyar.app.brain

/** Pure routing rules, covered by JVM tests and shared by chat and the gateway. */
object RoutingPolicy {
    private val reviewTerms = listOf(
        "راست", "واقعی", "کلاهبردار", "بررسی", "صحت", "ادعا",
        "تحلیل", "خبر", "معتبر", "دروغ", "تایید", "تأیید", "اثبات"
    )

    fun needsReview(text: String): Boolean = reviewTerms.any(text::contains)

    fun fastAnswer(text: String): Boolean =
        text.length <= 180 && !needsReview(text)

    fun containsSocialVideoLink(text: String): Boolean =
        Regex("""https?://(?:www\.)?(?:instagram\.com/(?:reel|p|tv)/|tiktok\.com/|youtube\.com/(?:watch|shorts/)|youtu\.be/|fb\.watch/)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}
