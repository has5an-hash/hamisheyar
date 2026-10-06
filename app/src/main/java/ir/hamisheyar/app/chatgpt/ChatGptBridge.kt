package ir.hamisheyar.app.chatgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object ChatGptBridge {
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val CHATGPT_WEB = "https://chatgpt.com/"
    private const val CHATGPT_PLAY = "https://play.google.com/store/apps/details?id=com.openai.chatgpt"

    data class LaunchResult(
        val launched: Boolean,
        val directShare: Boolean,
        val message: String
    )

    fun isInstalled(context: Context): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE) != null
        }.getOrDefault(false)

    fun openApp(context: Context): LaunchResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (launchIntent != null) {
            return runCatching {
                context.startActivity(launchIntent)
                LaunchResult(true, false, "ChatGPT با حساب خودت باز شد.")
            }.getOrElse { openWeb(context) }
        }

        return openWeb(context)
    }

    fun openInstallPage(context: Context): LaunchResult {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$CHATGPT_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_PLAY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            context.startActivity(market)
            LaunchResult(true, false, "صفحه نصب ChatGPT باز شد.")
        }.getOrElse {
            runCatching {
                context.startActivity(web)
                LaunchResult(true, false, "صفحه نصب ChatGPT باز شد.")
            }.getOrElse {
                LaunchResult(false, false, "نتونستم صفحه نصب ChatGPT را باز کنم.")
            }
        }
    }

    fun sendPrompt(context: Context, prompt: String): LaunchResult =
        forwardContent(context, prompt, null, null)

    fun forwardContent(
        context: Context,
        prompt: String,
        stream: Uri?,
        mimeType: String?
    ): LaunchResult {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank() && stream == null) {
            return LaunchResult(false, false, "چیزی برای فرستادن به ChatGPT وجود ندارد.")
        }

        if (stream != null) {
            runCatching {
                context.grantUriPermission(
                    CHATGPT_PACKAGE,
                    stream,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }

        val direct = Intent(Intent.ACTION_SEND).apply {
            type = if (stream != null) mimeType?.takeIf { it.isNotBlank() } ?: "*/*" else "text/plain"
            setPackage(CHATGPT_PACKAGE)
            if (cleanPrompt.isNotBlank()) putExtra(Intent.EXTRA_TEXT, cleanPrompt)
            if (stream != null) {
                putExtra(Intent.EXTRA_STREAM, stream)
                clipData = ClipData.newRawUri("Hamisheyar shared item", stream)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        runCatching {
            context.startActivity(direct)
            return LaunchResult(
                launched = true,
                directShare = true,
                message = "درخواست به اپ رسمی ChatGPT و حساب خودت تحویل داده شد."
            )
        }

        if (cleanPrompt.isNotBlank()) copyPrompt(context, cleanPrompt)
        val opened = openApp(context)
        return opened.copy(
            message = if (opened.launched) {
                "ChatGPT باز شد. متن درخواست در کلیپ‌بورد آماده است؛ اگر خودکار وارد نشد Paste کن."
            } else {
                "درخواست آماده شد، اما ChatGPT باز نشد."
            }
        )
    }

    private fun copyPrompt(context: Context, prompt: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hamisheyar prompt", prompt))
    }

    private fun openWeb(context: Context): LaunchResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CHATGPT_WEB)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            LaunchResult(true, false, "نسخه وب ChatGPT با حساب خودت باز شد.")
        }.getOrElse {
            LaunchResult(false, false, "نتونستم ChatGPT را باز کنم.")
        }
    }
}
