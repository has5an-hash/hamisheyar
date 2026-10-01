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
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
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
        return runCatching {
            if (Build.VERSION.SDK_INT >= 27) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.isNotificationListenerAccessGranted(notificationComponent(context))
            } else {
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            }
        }.onFailure {
            DiagnosticsLogger.log(context, "ACCESS", "failed to query notification access", it)
        }.getOrDefault(false)
    }

    fun openNotificationAccess(context: Context): Boolean {
        // Intentionally avoid ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS.
        // Several OEM Settings implementations resolve that intent but crash after launch.
        // The general listener screen is the most compatible route across Android vendors.
        val intents = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        val opened = startFirstAvailable(context, intents, "notification-access")
        if (!opened) {
            DiagnosticsLogger.log(
                context,
                "ACCESS",
                "No compatible system Settings activity could be opened for notification access"
            )
        }
        return opened
    }

    fun openAppInfoForRestrictedSettings(context: Context): Boolean {
        val intents = listOf(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_SETTINGS)
        )
        return startFirstAvailable(context, intents, "app-info")
    }

    fun requestNotificationListenerReconnect(context: Context): Boolean {
        if (!isNotificationAccessGranted(context)) return false
        return runCatching {
            NotificationListenerService.requestRebind(notificationComponent(context))
            DiagnosticsLogger.log(context, "ACCESS", "notification listener rebind requested")
            true
        }.onFailure {
            DiagnosticsLogger.log(context, "ACCESS", "notification listener rebind failed", it)
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
        }.onFailure {
            DiagnosticsLogger.log(context, "ACCESS", "installer lookup failed", it)
        }.getOrNull()
    }

    private fun startFirstAvailable(
        context: Context,
        intents: List<Intent>,
        reason: String
    ): Boolean {
        for (candidate in intents) {
            val intent = Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolvable = runCatching {
                intent.resolveActivity(context.packageManager) != null
            }.getOrDefault(false)

            if (!resolvable) {
                DiagnosticsLogger.log(
                    context,
                    "ACCESS",
                    "settings route not resolvable reason=$reason action=${intent.action}"
                )
                continue
            }

            val started = runCatching {
                context.startActivity(intent)
                true
            }.onFailure {
                DiagnosticsLogger.log(
                    context,
                    "ACCESS",
                    "settings route failed reason=$reason action=${intent.action}",
                    it
                )
            }.getOrDefault(false)

            if (started) {
                DiagnosticsLogger.log(
                    context,
                    "ACCESS",
                    "settings route opened reason=$reason action=${intent.action}"
                )
                return true
            }
        }
        return false
    }
}
