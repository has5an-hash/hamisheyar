package ir.hamisheyar.app.brain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Device-side coordinator. The providers are private tools; HamisheYar owns the final reply. */
object AgentBrain {
    private val gateway: BrainGateway = LocalGateway()

    suspend fun answer(
        context: Context,
        prompt: String,
        mediaPath: String? = null,
        onStage: (String) -> Unit = {}
    ): String {
        val gemini = CredentialVault.read(context, CredentialVault.GEMINI)
        val groq = CredentialVault.read(context, CredentialVault.GROQ)
        if (gemini.isNullOrBlank() || groq.isNullOrBlank()) {
            return "مغز همیشه‌یار هنوز فعال نیست. ابتدا در تنظیمات، هر دو اتصال Gemini و Groq را آزمایش و ثبت کن."
        }
        suspend fun stage(message: String) {
            withContext(Dispatchers.Main) { onStage(message) }
        }
        stage("دارم داده‌های واقعی درخواست را آماده می‌کنم…")
        val prepared = withContext(Dispatchers.IO) {
            MediaIntake.prepare(context, prompt, mediaPath)
        }
        val refersToUnsharedMedia = listOf("این ویدیو", "این ریل", "این عکس", "این فیلم",
            "همین ویدیو", "این پست").any { prompt.contains(it, ignoreCase = true) }
        if (refersToUnsharedMedia && prepared.images.isEmpty() &&
            mediaPath.isNullOrBlank() && !prompt.contains("https://", ignoreCase = true)) {
            return "برای اینکه درباره این ویدیو یا عکس دقیق نظر بدهم، باید خود محتوایش را دریافت کنم. " +
                "از داخل برنامه اصلی گزینه Share و سپس همیشه‌یار را انتخاب کن، یا فایل رسانه را بفرست. " +
                "صرف بازبودن حباب روی اینستاگرام دسترسی به محتوای ویدیو نمی‌دهد."
        }
        val containsLink = prompt.contains("https://", ignoreCase = true)
        if (containsLink && !prepared.videoSampled && prepared.images.isEmpty() &&
            mediaPath.isNullOrBlank()) {
            return prepared.note.ifEmpty { "فقط لینک دریافت شد؛ خود ویدیو قابل دسترس نیست." }
        }
        val factCheck = listOf("راست", "واقعی", "درست", "دروغ", "فیک", "کلاهبرداری",
            "fact", "verify", "حقیقت").any { prompt.contains(it, ignoreCase = true) }
        val isMedia = prepared.images.isNotEmpty()
        var transcript: String? = null
        try {
            if (prepared.audio != null) {
                stage("در حال پیاده‌سازی گفتار ویدیو با Groq…")
                transcript = try {
                    gateway.transcribe(groq, prepared.audio)
                } catch (_: Exception) { null }
            }
            val body = buildString {
                append("تو مغز تحلیلی همیشه‌یار هستی. فارسی دقیق، مفید و بی‌اغراق جواب بده. ")
                append("درباره محتوایی که واقعاً ندیده‌ای ادعا نکن. ")
                append("درباره صحت ادعاها بین شواهد موجود و راستی‌آزمایی مستقل تفاوت بگذار. ")
                append("اگر منبع مستقل وجود ندارد، قطعیت کاذب نده.\n")
                append("درخواست کاربر:\n")
                append(prompt.take(9000))
                if (prepared.note.isNotBlank()) append("\nوضعیت دریافت رسانه: " + prepared.note)
                if (!transcript.isNullOrBlank()) {
                    append("\nرونوشت صدا (ممکن است خطای تشخیص گفتار داشته باشد):\n")
                    append(transcript!!.take(9000))
                }
                if (isMedia) append("\nفریم‌های واقعی رسانه در ضمیمه ارسال شده‌اند.")
            }
            stage("در حال تحلیل با Gemini…")
            val primary = try {
                gateway.gemini(gemini, body, prepared.images)
            } catch (ex: BrainNetworkException) {
                if (isMedia || ex.statusCode in listOf(401, 403)) throw ex
                stage("Gemini پاسخ نداد؛ تلاش با Groq…")
                return gateway.groq(groq, body, true) +
                    "\n\nتوجه: Gemini در این درخواست در دسترس نبود؛ این پاسخ فقط با Groq تولید شد."
            }
            if (!factCheck && !isMedia) return primary
            stage("در حال بررسی تکمیلی با Groq…")
            val review = try {
                gateway.groq(groq, buildString {
                    append("نقش: بازبین تحلیل همیشه‌یار. متن اصلی را بررسی کن. ")
                    append("حواست باشد توافق دو مدل جایگزین شواهد مستقل نیست. ")
                    append("اگر منبع زنده برای راستی‌آزمایی نداری، صریح بگو. ")
                    append("در کمتر از 250 کلمه به فارسی نکات اصلاحی، تضادها و میزان قطعیت را بگو.\n")
                    append("درخواست: " + prompt.take(4000))
                    append("\nپاسخ اولیه Gemini:\n" + primary.take(7000))
                    if (!transcript.isNullOrBlank()) append("\nگفتار استخراج‌شده:\n" + transcript!!.take(5000))
                }, true)
            } catch (_: Exception) { null }
            return buildString {
                append(primary)
                if (!review.isNullOrBlank()) {
                    append("\n\nبررسی تکمیلی همیشه‌یار:\n")
                    append(review)
                } else append("\n\nبررسی تکمیلی Groq در این نوبت انجام نشد.")
                if (isMedia) {
                    append("\n\nمحدوده مشاهده: " + prepared.note)
                    append("\nتأیید مستقل ادعاها به منابع قابل استناد نیاز دارد؛ ")
                    append("توافق مدل‌ها به‌تنهایی اثبات صحت نیست.")
                }
            }
        } finally {
            prepared.audio?.delete()
        }
    }
}
