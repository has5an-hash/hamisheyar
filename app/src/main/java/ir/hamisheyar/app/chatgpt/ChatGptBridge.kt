package ir.hamisheyar.app.chatgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

object ChatGptBridge {
    const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    private const val CHATGPT_WEB = "https://chatgpt.com/"

    data class LaunchResult(
        val launched: Boolean,
        val directShare: Boolean,
        val message: String
    )

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE) != null

    fun openApp(context: Context): LaunchResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (launchIntent != null) {
            return runCatching {
                context.startActivity(launchIntent)
                LaunchResult(
                    launched = true,
                    directShare = false,
                    message = "ChatGPT با حساب خودت باز شد."
                )
            }.getOrElse {
                openWeb(context)
            }
        }

        return openWeb(context)
    }

    fun sendPrompt(context: Context, prompt: String): LaunchResult {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            return LaunchResult(
                launched = false,
                directShare = false,
                message = "متنی برای فرستادن به ChatGPT وجود ندارد."
            )
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(CHATGPT_PACKAGE)
            putExtra(Intent.EXTRA_TEXT, cleanPrompt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val directResult = runCatching {
            context.startActivity(shareIntent)
            LaunchResult(
                launched = true,
                directShare = true,
                message = "درخواست به ChatGPT حساب خودت تحویل داده شد."
            )
        }.getOrNull()

        if (directResult != null) return directResult

        copyPrompt(context, cleanPrompt)
        val opened = openApp(context)
        return opened.copy(
            message = if (opened.launched) {
                "ChatGPT باز شد. درخواست هم در کلیپ‌بورد آماده است؛ اگر خودکار وارد نشد، Paste کن."
            } else {
                "درخواست در کلیپ‌بورد ذخیره شد، اما ChatGPT باز نشد."
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
            LaunchResult(
                launched = true,
                directShare = false,
                message = "نسخه وب ChatGPT با حساب خودت باز شد."
            )
        }.getOrElse {
            LaunchResult(
                launched = false,
                directShare = false,
                message = "نتونستم ChatGPT را باز کنم."
            )
        }
    }
}
