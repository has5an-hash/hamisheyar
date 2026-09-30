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
            val engine = tts
            if (engine != null) {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hamisheyar")
                return@post
            }
            pending = text
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val instance = tts ?: return@TextToSpeech
                    val fa = Locale("fa", "IR")
                    val result = instance.setLanguage(fa)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        instance.language = Locale.getDefault()
                    }
                    pending?.let {
                        instance.speak(it, TextToSpeech.QUEUE_FLUSH, null, "hamisheyar")
                    }
                    pending = null
                }
            }
        }
    }

    fun stop() {
        main.post { tts?.stop() }
    }
}
