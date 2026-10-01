package ir.hamisheyar.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.hamisheyar.app.assistant.AssistantController
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import ir.hamisheyar.app.voice.OfflineVoiceEngine
import ir.hamisheyar.app.voice.VoiceAssetStatus
import ir.hamisheyar.app.voice.VoiceAssetsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VoiceMode {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assets by remember {
        mutableStateOf(
            VoiceAssetStatus(
                sttReady = VoiceAssetsManager.isSttReady(context),
                ttsReady = VoiceAssetsManager.isTtsReady(context),
                downloading = false,
                progress = 0f,
                message = ""
            )
        )
    }
    var mode by remember { mutableStateOf(VoiceMode.IDLE) }
    var stage by remember { mutableStateOf("آماده‌ام؛ بزن روی میکروفون و حرف بزن.") }
    var transcript by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        stage = if (granted) {
            "میکروفون آماده است؛ دوباره بزن و صحبت کن."
        } else {
            "برای حالت ویس مستقل، اجازه میکروفون لازم است."
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            assets = runCatching {
                VoiceAssetsManager.inspectAndPrepare(context)
            }.getOrElse {
                DiagnosticsLogger.log(context, "VOICE", "asset status failed", it)
                assets.copy(message = "بررسی بسته صوتی ناموفق بود.")
            }
            delay(if (assets.downloading) 800L else 2500L)
        }
    }

    fun installVoicePack() {
        runCatching {
            VoiceAssetsManager.startInstall(context)
            stage = "دانلود بسته صوتی شروع شد…"
        }.onFailure {
            stage = "شروع دانلود بسته صوتی ممکن نشد: " + (it.message ?: "خطا")
        }
    }

    fun startListening() {
        if (!assets.sttReady) {
            installVoicePack()
            return
        }
        if (!micGranted) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        runCatching {
            OfflineVoiceEngine.startRecording(context)
            transcript = ""
            answer = ""
            mode = VoiceMode.LISTENING
            stage = "گوش می‌دم… وقتی حرفت تموم شد دوباره بزن."
        }.onFailure {
            mode = VoiceMode.ERROR
            stage = it.message ?: "میکروفون شروع نشد."
        }
    }

    fun finishListening() {
        if (mode != VoiceMode.LISTENING) return

        scope.launch {
            try {
                mode = VoiceMode.TRANSCRIBING
                stage = "دارم صدات رو آفلاین تبدیل می‌کنم…"

                val voice = OfflineVoiceEngine.stopAndTranscribe(context) {
                    stage = it
                }
                transcript = voice.text

                mode = VoiceMode.THINKING
                stage = "فهمیدم؛ دارم جواب می‌دم…"

                val result = AssistantController.handle(context, voice.text)
                answer = result.text

                if (assets.ttsReady) {
                    mode = VoiceMode.SPEAKING
                    OfflineVoiceEngine.speakPersian(
                        context = context,
                        text = result.text,
                        speed = 1.04f
                    ) {
                        stage = it
                    }
                } else {
                    stage = "جواب آماده است؛ برای شنیدن صدای مستقل، بسته صدای فارسی را کامل نصب کن."
                }

                mode = VoiceMode.IDLE
                stage = "آماده‌ام؛ دوباره بزن و حرف بزن."
            } catch (t: Throwable) {
                DiagnosticsLogger.log(context, "VOICE", "voice conversation failed", t)
                mode = VoiceMode.ERROR
                stage = t.message ?: "حالت ویس با خطا روبه‌رو شد."
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "voicePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mode == VoiceMode.LISTENING) 650 else 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "گفت‌وگوی صوتی",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "بدون باز شدن Google Voice؛ میکروفون، تشخیص گفتار و صدای فارسی داخل همیشه‌یار",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp)
            )

            Spacer(Modifier.height(22.dp))

            if (!assets.fullyReady) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("بسته صوتی مستقل", fontWeight = FontWeight.Bold)
                                Text(
                                    "Whisper Tiny برای فهم فارسی + صدای فارسی Piper",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (assets.downloading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (assets.downloading) {
                            LinearProgressIndicator(
                                progress = { assets.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(assets.message, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Button(
                                onClick = { installVoicePack() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("نصب بسته صوتی آفلاین")
                            }
                            Text(
                                "حدود ۱۰۰ مگابایت دانلود؛ بعد از نصب برای مکالمه صوتی اینترنت لازم نیست.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("ویس آفلاین آماده است", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(if (mode == VoiceMode.LISTENING || mode == VoiceMode.SPEAKING) pulse else 1f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = when (mode) {
                                VoiceMode.LISTENING -> listOf(Color(0xFFFF6B8A), Color(0xFF8C4EFF))
                                VoiceMode.SPEAKING -> listOf(Color(0xFF4DD0E1), Color(0xFF6750A4))
                                VoiceMode.THINKING, VoiceMode.TRANSCRIBING -> listOf(Color(0xFFFFC857), Color(0xFF8C4EFF))
                                VoiceMode.ERROR -> listOf(Color(0xFFFF8A80), Color(0xFFB3261E))
                                VoiceMode.IDLE -> listOf(Color(0xFF8C7CF0), Color(0xFF4B3BAF))
                            }
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(128.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (mode) {
                            VoiceMode.LISTENING -> Icons.Rounded.Stop
                            VoiceMode.SPEAKING -> Icons.Rounded.VolumeUp
                            VoiceMode.THINKING, VoiceMode.TRANSCRIBING -> Icons.Rounded.AutoAwesome
                            else -> Icons.Rounded.Mic
                        }
                        Icon(
                            icon,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    when (mode) {
                        VoiceMode.IDLE, VoiceMode.ERROR -> startListening()
                        VoiceMode.LISTENING -> finishListening()
                        VoiceMode.SPEAKING -> {
                            OfflineVoiceEngine.stopSpeaking()
                            mode = VoiceMode.IDLE
                            stage = "صحبت متوقف شد."
                        }
                        else -> Unit
                    }
                },
                enabled = mode != VoiceMode.TRANSCRIBING && mode != VoiceMode.THINKING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (mode) {
                        VoiceMode.LISTENING -> "پایان حرف و ارسال"
                        VoiceMode.SPEAKING -> "قطع صدا"
                        VoiceMode.TRANSCRIBING -> "در حال تبدیل صدا…"
                        VoiceMode.THINKING -> "در حال فکر کردن…"
                        VoiceMode.ERROR -> "دوباره تلاش کن"
                        VoiceMode.IDLE -> "شروع صحبت"
                    }
                )
            }

            Text(
                stage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            if (transcript.isNotBlank() || answer.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (transcript.isNotBlank()) {
                            Text("شنیدم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(transcript, fontWeight = FontWeight.SemiBold)
                        }
                        if (answer.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("همیشه‌یار", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(answer)
                        }
                    }
                }
            }
        }
    }
}
