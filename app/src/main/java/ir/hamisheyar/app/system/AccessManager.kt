package ir.hamisheyar.app.system

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import ir.hamisheyar.app.service.HamisheyarNotificationListener

data class NotificationAccessState(
    val granted: Boolean,
    val serviceConnectedRecently: Boolean,
    val likelySideloaded: Boolean,
    val installerPackage: String?,
    val connectedAt: Long
)

object AccessManager {
    private val trustedStorePackages = setOf(
        "com.android.vending",
        "com.sec.android.app.samsungapps",
        "com.amazon.venezia",
        "com.huawei.appmarket",
        "com.xiaomi.mipicks"
    )

    fun notificationComponent(context: Context): ComponentName =
        ComponentName(context, HamisheyarNotificationListener::class.java)

    fun notificationAccessState(context: Context): NotificationAccessState {
        val granted = isNotificationAccessGranted(context)
        val connectedAt = ir.hamisheyar.app.settings.AppSettings.notificationListenerConnectedAt(context)
        val connectedRecently = granted &&
            connectedAt > 0L &&
            System.currentTimeMillis() - connectedAt < 10 * 60 * 1000L

        val installer = installerPackage(context)
        val likelySideloaded = installer == null || installer !in trustedStorePackages

        return NotificationAccessState(
            granted = granted,
            serviceConnectedRecently = connectedRecently,
            likelySideloaded = likelySideloaded,
            installerPackage = installer,
            connectedAt = connectedAt
        )
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 27) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.isNotificationListenerAccessGranted(notificationComponent(context))
        } else {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }
    }

    fun openNotificationAccess(context: Context): Boolean {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 30) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            notificationComponent(context)
                        )
                    }
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        return startFirstAvailable(context, intents)
    }

    fun openAppInfoForRestrictedSettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        return startFirstAvailable(context, listOf(intent))
    }

    fun requestNotificationListenerReconnect(context: Context): Boolean {
        if (!isNotificationAccessGranted(context)) return false
        return runCatching {
            NotificationListenerService.requestRebind(notificationComponent(context))
            true
        }.getOrDefault(false)
    }

    fun installerPackage(context: Context): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()
    }

    private fun startFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                return runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            }
        }
        return false
    }
}
