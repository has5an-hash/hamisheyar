package ir.hamisheyar.app.settings

import android.content.Context

object AppSettings {
    private const val PREFS = "hamisheyar_settings"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun modelPath(context: Context): String? = prefs(context).getString("model_path", null)
    fun setModelPath(context: Context, value: String?) = prefs(context).edit().putString("model_path", value).apply()

    fun modelDownloadId(context: Context): Long = prefs(context).getLong("model_download_id", -1L)
    fun modelDownloadFile(context: Context): String? = prefs(context).getString("model_download_file", null)
    fun setModelDownload(context: Context, id: Long, fileName: String) = prefs(context).edit()
        .putLong("model_download_id", id)
        .putString("model_download_file", fileName)
        .apply()
    fun clearModelDownload(context: Context) = prefs(context).edit()
        .remove("model_download_id")
        .remove("model_download_file")
        .apply()

    fun notificationListenerConnectedAt(context: Context): Long =
        prefs(context).getLong("notification_listener_connected_at", 0L)

    fun notificationListenerDisconnectedAt(context: Context): Long =
        prefs(context).getLong("notification_listener_disconnected_at", 0L)

    fun notificationListenerConnected(context: Context): Boolean =
        prefs(context).getBoolean("notification_listener_connected", false)

    fun markNotificationListenerConnected(context: Context) = prefs(context).edit()
        .putBoolean("notification_listener_connected", true)
        .putLong("notification_listener_connected_at", System.currentTimeMillis())
        .apply()

    fun markNotificationListenerDisconnected(context: Context) = prefs(context).edit()
        .putBoolean("notification_listener_connected", false)
        .putLong("notification_listener_disconnected_at", System.currentTimeMillis())
        .apply()

    fun voiceWhisperPath(context: Context): String? = prefs(context).getString("voice_whisper_path", null)
    fun setVoiceWhisperPath(context: Context, value: String?) = prefs(context).edit()
        .putString("voice_whisper_path", value)
        .apply()

    fun voiceTtsDir(context: Context): String? = prefs(context).getString("voice_tts_dir", null)
    fun setVoiceTtsDir(context: Context, value: String?) = prefs(context).edit()
        .putString("voice_tts_dir", value)
        .apply()

    fun voiceWhisperDownloadId(context: Context): Long = prefs(context).getLong("voice_whisper_download_id", -1L)
    fun setVoiceWhisperDownloadId(context: Context, id: Long) = prefs(context).edit()
        .putLong("voice_whisper_download_id", id)
        .apply()
    fun clearVoiceWhisperDownload(context: Context) = prefs(context).edit()
        .remove("voice_whisper_download_id")
        .apply()

    fun voiceTtsDownloadId(context: Context): Long = prefs(context).getLong("voice_tts_download_id", -1L)
    fun setVoiceTtsDownloadId(context: Context, id: Long) = prefs(context).edit()
        .putLong("voice_tts_download_id", id)
        .apply()
    fun clearVoiceTtsDownload(context: Context) = prefs(context).edit()
        .remove("voice_tts_download_id")
        .apply()

    fun offlineVoiceEnabled(context: Context) = prefs(context).getBoolean("offline_voice_enabled", true)
    fun setOfflineVoiceEnabled(context: Context, value: Boolean) = prefs(context).edit()
        .putBoolean("offline_voice_enabled", value)
        .apply()

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
