package ir.hamisheyar.app.settings

import android.content.Context

object AppSettings {
    private const val PREFS = "hamisheyar_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun modelPath(context: Context): String? = prefs(context).getString("model_path", null)
    fun setModelPath(context: Context, value: String?) = prefs(context).edit().putString("model_path", value).apply()

    fun announcementEnabled(context: Context) = prefs(context).getBoolean("announce", false)
    fun setAnnouncementEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("announce", value).apply()

    fun liveShareEnabled(context: Context) = prefs(context).getBoolean("live_share", true)
    fun setLiveShareEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("live_share", value).apply()

    fun webResearchEnabled(context: Context) = prefs(context).getBoolean("web_research", false)
    fun setWebResearchEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("web_research", value).apply()

    fun whatsappEnabled(context: Context) = prefs(context).getBoolean("whatsapp", true)
    fun setWhatsappEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("whatsapp", value).apply()

    fun telegramEnabled(context: Context) = prefs(context).getBoolean("telegram", true)
    fun setTelegramEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("telegram", value).apply()

    fun instagramEnabled(context: Context) = prefs(context).getBoolean("instagram", true)
    fun setInstagramEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("instagram", value).apply()

    fun smsEnabled(context: Context) = prefs(context).getBoolean("sms", true)
    fun setSmsEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("sms", value).apply()

    fun isPackageEnabled(context: Context, packageName: String): Boolean = when (packageName) {
        "com.whatsapp", "com.whatsapp.w4b" -> whatsappEnabled(context)
        "org.telegram.messenger", "org.telegram.messenger.web" -> telegramEnabled(context)
        "com.instagram.android" -> instagramEnabled(context)
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging" -> smsEnabled(context)
        else -> false
    }

    fun sourceName(packageName: String): String = when (packageName) {
        "com.whatsapp", "com.whatsapp.w4b" -> "واتس‌اپ"
        "org.telegram.messenger", "org.telegram.messenger.web" -> "تلگرام"
        "com.instagram.android" -> "اینستاگرام"
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging" -> "پیامک"
        else -> "اعلان"
    }
}
