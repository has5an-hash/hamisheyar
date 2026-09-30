package ir.hamisheyar.app.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceRecognizer(
    context: Context,
    private val onText: (String) -> Unit,
    private val onFailure: (String) -> Unit
) : RecognitionListener {

    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer = if (
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
    ) {
        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
    } else {
        SpeechRecognizer.createSpeechRecognizer(appContext)
    }.also { it.setRecognitionListener(this) }

    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.startListening(intent)
    }

    fun destroy() = recognizer.destroy()

    override fun onResults(results: Bundle) {
        val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (text.isNullOrBlank()) onFailure("چیزی نشنیدم.") else onText(text)
    }

    override fun onError(error: Int) {
        onFailure(
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "متوجه نشدم، دوباره بگو."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازه میکروفون لازم است."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تشخیص گفتار آفلاین روی این گوشی در دسترس نیست."
                else -> "تشخیص صدا انجام نشد."
            }
        )
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
