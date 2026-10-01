package ir.hamisheyar.app.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class VoiceTranscript(
    val text: String,
    val processingTimeMs: Long
)

object OfflineVoiceEngine {
    private const val SAMPLE_RATE = 16_000
    private val sttMutex = Mutex()
    private val ttsMutex = Mutex()

    private var whisperModel: WhisperModel? = null
    private var whisperPath: String? = null
    private var tts: OfflineTts? = null
    private var ttsDir: String? = null

    private val recording = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var recordThread: Thread? = null
    private var currentWav: File? = null
    private var currentTrack: AudioTrack? = null

    fun isRecording(): Boolean = recording.get()

    fun startRecording(context: Context): File {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "اجازه میکروفون داده نشده است." }

        check(!recording.get()) { "ضبط صدا از قبل فعال است." }
        check(VoiceAssetsManager.isSttReady(context)) {
            "بسته تشخیص گفتار آفلاین هنوز آماده نیست."
        }

        stopSpeaking()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE / 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            "میکروفون روی این دستگاه آماده نشد."
        }

        val dir = File(context.cacheDir, "voice-input").apply { mkdirs() }
        val wav = File(dir, "utterance_${System.currentTimeMillis()}.wav")
        FileOutputStream(wav).use { output ->
            output.write(ByteArray(44))
        }

        recording.set(true)
        recorder = audioRecord
        currentWav = wav

        audioRecord.startRecording()
        val thread = Thread({
            val buffer = ByteArray(minBuffer)
            FileOutputStream(wav, true).use { output ->
                while (recording.get()) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        output.write(buffer, 0, read)
                    } else if (read < 0) {
                        DiagnosticsLogger.log(context, "VOICE", "AudioRecord read error=$read")
                        break
                    }
                }
            }
        }, "hamisheyar-voice-recorder").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
        recordThread = thread
        DiagnosticsLogger.log(context, "VOICE", "offline microphone recording started")
        return wav
    }

    suspend fun stopAndTranscribe(
        context: Context,
        onStage: (String) -> Unit = {}
    ): VoiceTranscript {
        val wav = stopRecording(context)
            ?: error("ویسی برای تبدیل وجود ندارد.")

        onStage("دارم صدات رو روی خود گوشی تبدیل می‌کنم…")
        return sttMutex.withLock {
            val modelFile = VoiceAssetsManager.sttFile(context)
            check(modelFile.isFile) { "مدل تشخیص گفتار پیدا نشد." }

            val model = if (
                whisperModel?.isValid == true &&
                whisperPath == modelFile.absolutePath
            ) {
                whisperModel!!
            } else {
                whisperModel?.let { runCatching { Whisper.releaseModel(it) } }
                onStage("دارم موتور تشخیص گفتار رو آماده می‌کنم…")
                Whisper.loadModel(context, modelFile.absolutePath).also {
                    whisperModel = it
                    whisperPath = modelFile.absolutePath
                }
            }

            val started = System.currentTimeMillis()
            val result = try {
                Whisper.transcribe(
                    model = model,
                    audioPath = wav.absolutePath,
                    config = WhisperConfig(
                        language = "fa",
                        translate = false,
                        threads = 2,
                        printTimestamps = false
                    )
                )
            } catch (t: Throwable) {
                DiagnosticsLogger.log(context, "VOICE", "offline transcription failed", t)
                throw IllegalStateException(
                    "تشخیص گفتار آفلاین انجام نشد: " + (t.message ?: "خطای نامشخص"),
                    t
                )
            } finally {
                wav.delete()
            }

            val text = result.text.trim()
            if (text.isBlank()) {
                error("صدای واضحی تشخیص داده نشد. کمی نزدیک‌تر به میکروفون صحبت کن.")
            }

            DiagnosticsLogger.log(
                context,
                "VOICE",
                "transcription success chars=${text.length} ms=${System.currentTimeMillis() - started}"
            )
            VoiceTranscript(text, result.processingTimeMs)
        }
    }

    suspend fun speakPersian(
        context: Context,
        text: String,
        speed: Float = 1.0f,
        onStage: (String) -> Unit = {}
    ) {
        if (text.isBlank()) return
        check(VoiceAssetsManager.isTtsReady(context)) {
            "صدای فارسی آفلاین هنوز آماده نیست."
        }

        ttsMutex.withLock {
            stopSpeaking()
            onStage("دارم پاسخ رو با صدای فارسی آماده می‌کنم…")

            val root = VoiceAssetsManager.ttsRoot(context)
            val modelFile = VoiceAssetsManager.ttsModelFile(context)
                ?: error("مدل صدای فارسی پیدا نشد.")
            val tokens = VoiceAssetsManager.ttsTokensFile(context)
                ?: error("فایل واژه‌های صدای فارسی پیدا نشد.")
            val dataDir = VoiceAssetsManager.ttsEspeakDir(context)
                ?: error("داده‌های تلفظ فارسی پیدا نشد.")

            val engine = if (tts != null && ttsDir == root.absolutePath) {
                tts!!
            } else {
                runCatching { tts?.release() }
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = modelFile.absolutePath,
                            tokens = tokens.absolutePath,
                            dataDir = dataDir.absolutePath,
                            lengthScale = 1.0f
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1,
                    silenceScale = 0.18f
                )
                OfflineTts(config = config).also {
                    tts = it
                    ttsDir = root.absolutePath
                }
            }

            val safeText = text
                .replace(Regex("""https?://\S+"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(700)

            val generated = try {
                withContext(Dispatchers.Default) {
                    engine.generate(safeText, sid = 0, speed = speed.coerceIn(0.75f, 1.35f))
                }
            } catch (t: Throwable) {
                DiagnosticsLogger.log(context, "VOICE", "Persian TTS generation failed", t)
                throw IllegalStateException(
                    "ساخت صدای فارسی انجام نشد: " + (t.message ?: "خطای نامشخص"),
                    t
                )
            }

            onStage("دارم صحبت می‌کنم…")
            playPcm(generated.samples, generated.sampleRate)

            val durationMs = ((generated.samples.size.toDouble() / generated.sampleRate) * 1000.0)
                .roundToInt()
                .coerceAtLeast(100)
            delay(durationMs.toLong() + 120L)
            stopSpeaking()
        }
    }

    fun stopSpeaking() {
        val track = currentTrack
        currentTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
    }

    suspend fun reset() {
        sttMutex.withLock {
            whisperModel?.let { runCatching { Whisper.releaseModel(it) } }
            whisperModel = null
            whisperPath = null
        }
        ttsMutex.withLock {
            runCatching { tts?.release() }
            tts = null
            ttsDir = null
            stopSpeaking()
        }
    }

    private fun stopRecording(context: Context): File? {
        if (!recording.getAndSet(false)) return currentWav

        val r = recorder
        recorder = null
        runCatching { r?.stop() }
        runCatching { recordThread?.join(1500) }
        recordThread = null
        runCatching { r?.release() }

        val wav = currentWav
        currentWav = null
        if (wav != null && wav.isFile && wav.length() > 44L) {
            writeWavHeader(wav)
            DiagnosticsLogger.log(context, "VOICE", "offline microphone recording stopped bytes=${wav.length()}")
            return wav
        }
        wav?.delete()
        return null
    }

    private fun writeWavHeader(file: File) {
        val dataLength = file.length() - 44L
        val totalLength = dataLength + 36L
        val byteRate = SAMPLE_RATE * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.writeBytes("RIFF")
            raf.writeIntLE(totalLength.toInt())
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeIntLE(16)
            raf.writeShortLE(1)
            raf.writeShortLE(1)
            raf.writeIntLE(SAMPLE_RATE)
            raf.writeIntLE(byteRate)
            raf.writeShortLE(2)
            raf.writeShortLE(16)
            raf.writeBytes("data")
            raf.writeIntLE(dataLength.toInt())
        }
    }

    private fun playPcm(samples: FloatArray, sampleRate: Int) {
        val shorts = ShortArray(samples.size) { index ->
            (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        }

        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(min)
            .build()

        currentTrack = track
        track.play()

        var offset = 0
        while (offset < shorts.size) {
            val written = track.write(shorts, offset, shorts.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
        write((value shr 24) and 0xff)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }
}
