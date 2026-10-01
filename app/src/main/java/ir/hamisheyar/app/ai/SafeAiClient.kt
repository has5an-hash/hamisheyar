package ir.hamisheyar.app.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SafeAiClient {
    const val MSG_REQUEST = 100
    const val MSG_STAGE = 101
    const val MSG_RESULT = 102
    const val MSG_ERROR = 103

    const val KEY_PROMPT = "prompt"
    const val KEY_EXTRA = "extra"
    const val KEY_STAGE = "stage"
    const val KEY_TEXT = "text"
    const val KEY_TPS = "tps"
    const val KEY_DURATION = "duration"
    const val KEY_MODEL = "model"
    const val KEY_ERROR = "error"

    suspend fun answer(
        context: Context,
        prompt: String,
        extraContext: String = "",
        onStage: (String) -> Unit = {}
    ): AiAnswer = withTimeout(45_000L) {
        suspendCancellableCoroutine { continuation ->
            val app = context.applicationContext
            val finished = AtomicBoolean(false)
            var bound = false

            lateinit var connection: ServiceConnection

            fun cleanup() {
                if (bound) {
                    runCatching { app.unbindService(connection) }
                    bound = false
                }
            }

            fun fail(message: String, cause: Throwable? = null) {
                if (!finished.compareAndSet(false, true)) return
                cleanup()
                val error = IllegalStateException(message, cause)
                DiagnosticsLogger.log(app, "AI-CLIENT", message, cause)
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }

            val reply = Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    when (message.what) {
                        MSG_STAGE -> {
                            if (!finished.get()) {
                                onStage(message.data.getString(KEY_STAGE).orEmpty())
                            }
                            true
                        }

                        MSG_RESULT -> {
                            if (finished.compareAndSet(false, true)) {
                                val data = message.data
                                cleanup()
                                if (continuation.isActive) {
                                    continuation.resume(
                                        AiAnswer(
                                            text = data.getString(KEY_TEXT).orEmpty(),
                                            tokensPerSecond = data.getFloat(KEY_TPS, 0f),
                                            durationMs = data.getLong(KEY_DURATION, 0L),
                                            modelName = data.getString(KEY_MODEL).orEmpty()
                                        )
                                    )
                                }
                            }
                            true
                        }

                        MSG_ERROR -> {
                            fail(
                                message.data.getString(KEY_ERROR)
                                    ?: "موتور مدل پاسخ نداد."
                            )
                            true
                        }

                        else -> false
                    }
                }
            )

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        fail("Process مدل بدون Binder متصل شد.")
                        return
                    }

                    val remote = Messenger(binder)
                    val request = Message.obtain(null, MSG_REQUEST).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putString(KEY_PROMPT, prompt)
                            putString(KEY_EXTRA, extraContext)
                        }
                    }

                    try {
                        remote.send(request)
                    } catch (t: RemoteException) {
                        fail("ارتباط با Process مدل قطع شد.", t)
                    } catch (t: Throwable) {
                        fail("ارسال درخواست به موتور مدل انجام نشد.", t)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    fail("موتور مدل متوقف شد؛ خود برنامه همچنان فعال است.")
                }

                override fun onBindingDied(name: ComponentName?) {
                    fail("Process مدل کرش کرد یا توسط اندروید بسته شد.")
                }

                override fun onNullBinding(name: ComponentName?) {
                    fail("موتور مدل نتوانست راه‌اندازی شود.")
                }
            }

            continuation.invokeOnCancellation {
                cleanup()
            }

            val intent = Intent(app, AiInferenceService::class.java)
            bound = runCatching {
                app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrElse {
                fail("راه‌اندازی Process مدل ممکن نشد.", it)
                false
            }

            if (!bound && !finished.get()) {
                fail("اندروید اجازه اتصال به موتور مدل را نداد.")
            }
        }
    }

    suspend fun selfTest(
        context: Context,
        onStage: (String) -> Unit = {}
    ): AiAnswer = answer(
        context = context,
        prompt = "فقط کوتاه جواب بده: آماده‌ام",
        onStage = onStage
    )
}
