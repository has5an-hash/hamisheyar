package ir.hamisheyar.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

object SpeechOutput {
    private var tts: TextToSpeech? = null
    private var pending: String? = null
    private val main = Handler(Looper.getMainLooper())

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        main.post {
            val current = tts
            if (current != null) {
                current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hamisheyar")
            } else {
                pending = text
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val instance = tts
                        if (instance != null) {
                            val fa = Locale("fa", "IR")
                            val result = instance.setLanguage(fa)
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                instance.language = Locale.getDefault()
                            }
                            val value = pending
                            if (!value.isNullOrBlank()) {
                                instance.speak(value, TextToSpeech.QUEUE_FLUSH, null, "hamisheyar")
                            }
                            pending = null
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        main.post { tts?.stop() }
    }
}
