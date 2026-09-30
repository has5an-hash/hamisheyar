package ir.hamisheyar.app.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification

object NotificationActionRegistry {
    private data class Target(
        val key: String,
        val packageName: String,
        val replyAction: Notification.Action?,
        val playAction: Notification.Action?
    )

    @Volatile private var latest: Target? = null

    fun remember(sbn: StatusBarNotification) {
        val actions = sbn.notification.actions.orEmpty()
        val reply = actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val play = actions.firstOrNull { action ->
            val title = action.title?.toString()?.lowercase().orEmpty()
            title.contains("play") || title.contains("پخش")
        }
        latest = Target(sbn.key, sbn.packageName, reply, play)
    }

    fun replyLatest(context: Context, text: String): Boolean {
        val target = latest ?: return false
        val action = target.replyAction ?: return false
        val inputs = action.remoteInputs ?: return false
        return runCatching {
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            val fillIn = Intent()
            RemoteInput.addResultsToIntent(inputs, fillIn, results)
            action.actionIntent.send(context, 0, fillIn)
            true
        }.getOrDefault(false)
    }

    fun playLatest(context: Context): Boolean {
        val action = latest?.playAction ?: return false
        return runCatching {
            action.actionIntent.send(context, 0, Intent())
            true
        }.getOrDefault(false)
    }

    fun latestPackage(): String? = latest?.packageName
}
