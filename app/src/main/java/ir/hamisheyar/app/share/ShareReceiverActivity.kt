package ir.hamisheyar.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import ir.hamisheyar.app.MainActivity
import ir.hamisheyar.app.chatgpt.ChatGptBridge
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        lifecycleScope.launch {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            @Suppress("DEPRECATION")
            val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val mime = intent.type.orEmpty()
            val copied = if (stream != null) copyIntoApp(stream, mime) else null

            val body = when {
                text.isNotBlank() -> text
                copied != null -> "فایل به اشتراک گذاشته‌شده: ${copied.name}"
                else -> "مورد به اشتراک گذاشته‌شده"
            }

            LocalStore(this@ShareReceiverActivity).addEvent(
                source = "اشتراک‌گذاری",
                sender = "از برنامه دیگر",
                body = body,
                packageName = callingPackage.orEmpty(),
                kind = "shared",
                sharedUri = copied?.absolutePath
            )

            if (AppSettings.liveShareEnabled(this@ShareReceiverActivity)) {
                val prompt = buildString {
                    append("این مورد را که از یک برنامه دیگر برای همیشه‌یار فرستادم بررسی کن. ")
                    append("محتوا را توضیح بده، نکات مهمش را بگو و اگر ادعا، لینک یا پیشنهاد مشکوکی دارد هشدار بده.")
                    if (text.isNotBlank()) {
                        append("\n\n")
                        append(text)
                    }
                }

                val result = ChatGptBridge.forwardContent(
                    this@ShareReceiverActivity,
                    prompt,
                    stream,
                    mime
                )
                Toast.makeText(this@ShareReceiverActivity, result.message, Toast.LENGTH_LONG).show()

                if (!result.launched) {
                    startActivity(Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_INCOMING_SHARE, body)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }
            } else {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "برای بعد داخل صف همیشه‌یار ذخیره شد.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }
    }

    private suspend fun copyIntoApp(uri: Uri, mime: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(filesDir, "shared").apply { mkdirs() }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?.takeIf { it.length <= 8 }
                ?: "bin"
            val file = File(dir, "shared_${System.currentTimeMillis()}.$ext")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file
        }.getOrNull()
    }
}
