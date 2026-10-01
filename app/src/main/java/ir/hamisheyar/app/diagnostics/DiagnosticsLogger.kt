package ir.hamisheyar.app.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsLogger {
    private const val FILE_NAME = "hamisheyar-diagnostics.log"
    private const val MAX_BYTES = 256 * 1024L

    @Synchronized
    fun log(context: Context, tag: String, message: String, error: Throwable? = null) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists() && file.length() > MAX_BYTES) {
                val backup = File(context.filesDir, FILE_NAME + ".old")
                backup.delete()
                file.renameTo(backup)
            }
            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS",
                Locale.US
            ).format(Date())
            file.appendText(
                buildString {
                    append(timestamp)
                    append(" [")
                    append(tag)
                    append("] ")
                    append(message)
                    append('\n')
                    if (error != null) {
                        append(error.stackTraceToString())
                        append('\n')
                    }
                }
            )
        }
    }

    fun read(context: Context): String =
        runCatching {
            File(context.filesDir, FILE_NAME)
                .takeIf { it.isFile }
                ?.readText()
                .orEmpty()
        }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
        runCatching { File(context.filesDir, FILE_NAME + ".old").delete() }
    }
}
