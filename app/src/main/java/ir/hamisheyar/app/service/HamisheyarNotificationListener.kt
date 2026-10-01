package ir.hamisheyar.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.settings.AppSettings
import ir.hamisheyar.app.voice.OfflineVoiceEngine
import ir.hamisheyar.app.voice.VoiceAssetsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HamisheyarNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        AppSettings.markNotificationListenerConnected(this)
        sendBroadcast(android.content.Intent(ACTION_INBOX_CHANGED).setPackage(packageName))
    }

    override fun onListenerDisconnected() {
        AppSettings.markNotificationListenerDisconnected(this)
        sendBroadcast(android.content.Intent(ACTION_INBOX_CHANGED).setPackage(packageName))
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn ?: return
        if (item.packageName == packageName) return
        if (!AppSettings.isPackageEnabled(this, item.packageName)) return

        val extras = item.notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            .orEmpty().ifBlank { AppSettings.sourceName(item.packageName) }
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            )?.toString()?.trim().orEmpty()

        if (body.isBlank()) return

        val source = AppSettings.sourceName(item.packageName)
        LocalStore(this).addEvent(
            source = source,
            sender = sender,
            body = body,
            packageName = item.packageName,
            notificationKey = item.key,
            kind = if (source == "اینستاگرام") "social" else "message"
        )
        NotificationActionRegistry.remember(item)

        if (AppSettings.announcementEnabled(this) && VoiceAssetsManager.isTtsReady(this)) {
            val phrase = if (source == "اینستاگرام") {
                "از اینستاگرام یک اعلان جدید داری."
            } else {
                "یه پیام از $sender داری."
            }
            scope.launch {
                runCatching {
                    OfflineVoiceEngine.speakPersian(
                        this@HamisheyarNotificationListener,
                        phrase,
                        speed = 1.05f
                    )
                }
            }
        }

        sendBroadcast(android.content.Intent(ACTION_INBOX_CHANGED).setPackage(packageName))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_INBOX_CHANGED = "ir.hamisheyar.app.INBOX_CHANGED"
    }
}
