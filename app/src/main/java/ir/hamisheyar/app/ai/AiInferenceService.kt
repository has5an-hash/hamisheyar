package ir.hamisheyar.app.ai

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AiInferenceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what != SafeAiClient.MSG_REQUEST) return@Handler false

            val replyTo = message.replyTo ?: return@Handler true
            val prompt = message.data.getString(SafeAiClient.KEY_PROMPT).orEmpty()
            val extra = message.data.getString(SafeAiClient.KEY_EXTRA).orEmpty()

            scope.launch {
                try {
                    val answer = LocalAiEngine.answer(
                        context = applicationContext,
                        prompt = prompt,
                        extraContext = extra,
                        onStage = { stage ->
                            runCatching {
                                replyTo.send(
                                    Message.obtain(null, SafeAiClient.MSG_STAGE).apply {
                                        data = Bundle().apply {
                                            putString(SafeAiClient.KEY_STAGE, stage)
                                        }
                                    }
                                )
                            }
                        }
                    )

                    replyTo.send(
                        Message.obtain(null, SafeAiClient.MSG_RESULT).apply {
                            data = Bundle().apply {
                                putString(SafeAiClient.KEY_TEXT, answer.text)
                                putFloat(SafeAiClient.KEY_TPS, answer.tokensPerSecond)
                                putLong(SafeAiClient.KEY_DURATION, answer.durationMs)
                                putString(SafeAiClient.KEY_MODEL, answer.modelName)
                            }
                        }
                    )
                } catch (t: Throwable) {
                    DiagnosticsLogger.log(applicationContext, "AI-PROCESS", "inference failed", t)
                    runCatching {
                        replyTo.send(
                            Message.obtain(null, SafeAiClient.MSG_ERROR).apply {
                                data = Bundle().apply {
                                    putString(
                                        SafeAiClient.KEY_ERROR,
                                        t.message ?: "خطای نامشخص موتور مدل"
                                    )
                                }
                            }
                        )
                    }
                }
            }
            true
        }
    )

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
