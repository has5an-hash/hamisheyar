package ir.hamisheyar.app.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import ir.hamisheyar.app.MainActivity
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Receives only Android-granted share data. Never opens a third-party AI app. */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != Intent.ACTION_SEND) { finish(); return }
        lifecycleScope.launch {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            @Suppress("DEPRECATION")
            val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            val copied = if (stream != null) copyIntoApp(stream, intent.type.orEmpty()) else null
            val body = when {
                text.isNotBlank() -> text
                copied != null -> "رسانه اشتراک‌گذاری‌شده: " + copied.name
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
            val live = AppSettings.liveShareEnabled(this@ShareReceiverActivity)
            if (!live) {
                Toast.makeText(this@ShareReceiverActivity,
                    "در صف همیشه‌یار ذخیره شد.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            startActivity(Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_INCOMING_SHARE, body)
                if (copied != null) putExtra(MainActivity.EXTRA_MEDIA_PATH, copied.absolutePath)
                putExtra(MainActivity.EXTRA_AUTO_ANALYZE, live)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
        }
    }

    private suspend fun copyIntoApp(uri: Uri, mime: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?.takeIf { it.length in 1..8 } ?: "bin"
            val dir = File(filesDir, "shared").apply { mkdirs() }
            val file = File(dir, "shared_" + System.currentTimeMillis() + "." + ext)
            var total = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > 30_000_000) error("حداکثر اندازه فایل ۳۰ مگابایت است.")
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return@runCatching null
            if (file.length() == 0L) { file.delete(); null } else file
        }.getOrNull()
    }
}
