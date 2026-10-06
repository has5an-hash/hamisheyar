package ir.hamisheyar.app.settings

import android.content.Context

object AppSettings {
    private const val PREFS = "hamisheyar_settings"
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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

    fun liveShareEnabled(context: Context) =
        prefs(context).getBoolean("live_share", true)

    fun setLiveShareEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("live_share", value).apply()

    fun whatsappEnabled(context: Context) =
        prefs(context).getBoolean("whatsapp", true)

    fun setWhatsappEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("whatsapp", value).apply()

    fun telegramEnabled(context: Context) =
        prefs(context).getBoolean("telegram", true)

    fun setTelegramEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("telegram", value).apply()

    fun instagramEnabled(context: Context) =
        prefs(context).getBoolean("instagram", true)

    fun setInstagramEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("instagram", value).apply()

    fun smsEnabled(context: Context) =
        prefs(context).getBoolean("sms", true)

    fun setSmsEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("sms", value).apply()

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
