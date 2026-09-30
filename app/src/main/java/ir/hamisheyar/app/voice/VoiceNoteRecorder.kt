package ir.hamisheyar.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Recording already active" }
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_" + System.currentTimeMillis() + ".m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        output = file
        recorder = mediaRecorder
        return file
    }

    fun stop(): File? {
        val r = recorder ?: return output
        val file = output
        runCatching { r.stop() }
        r.reset()
        r.release()
        recorder = null
        return file?.takeIf { it.exists() && it.length() > 0 }
    }

    fun cancel() {
        stop()?.delete()
        output = null
    }
}
