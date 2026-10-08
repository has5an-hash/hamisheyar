package ir.hamisheyar.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import ir.hamisheyar.app.MainActivity
import ir.hamisheyar.app.R
import ir.hamisheyar.app.assistant.AssistantAction
import ir.hamisheyar.app.assistant.AssistantController
import ir.hamisheyar.app.brain.LocalGateway
import ir.hamisheyar.app.brain.CredentialVault
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingAssistantService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var panel: LinearLayout? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelStatus: TextView? = null
    private var panelInput: EditText? = null
    private var recordButton: Button? = null
    private var voiceButton: Button? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        if (Settings.canDrawOverlays(this)) createBubble() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val channelId = "hamisheyar_bubble"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.bubble_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 10, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.bubble_running))
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
        startForeground(4101, notification)
    }

    private fun createBubble() {
        if (bubble != null) return
        val size = dp(62)
        val text = TextView(this).apply {
            this.text = "✦"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            elevation = dp(10).toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#5757D8"),
                    Color.parseColor("#7255D9"),
                    Color.parseColor("#008C89")
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), Color.parseColor("#EEF0FF"))
            }
        }
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(14)
            y = dp(180)
        }
        bubbleParams = params

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        text.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + event.rawX - downX).toInt()
                        .coerceIn(0, resources.displayMetrics.widthPixels - size)
                    params.y = (startY + event.rawY - downY).toInt().coerceAtLeast(0)
                    windowManager.updateViewLayout(text, params)
                    updatePanelPosition()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - downX) < dp(8) && abs(event.rawY - downY) < dp(8)) {
                        togglePanel()
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(text, params)
        bubble = text
    }

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#FCFCFF"))
                setStroke(dp(1), Color.parseColor("#DFE3F0"))
            }
        }

        val title = TextView(this).apply {
            text = "همیشه‌یار  ✦"
            textSize = 20f
            setTextColor(Color.parseColor("#181A20"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.RIGHT
        }
        val status = TextView(this).apply {
            text = "هرجا هستی، بگو چی لازم داری"
            textSize = 14f
            setTextColor(Color.parseColor("#5A5D68"))
            gravity = Gravity.RIGHT
            setPadding(0, dp(8), 0, dp(8))
        }
        val input = EditText(this).apply {
            hint = "بنویس یا با صدا بگو…"
            textSize = 15f
            gravity = Gravity.RIGHT
            maxLines = 3
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F2F4FA"))
                setStroke(dp(1), Color.parseColor("#D9DDEA"))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val send = Button(this).apply {
            text = "ارسال"
            setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isNotBlank()) {
                    input.setText("")
                    handleCommand(value)
                }
            }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val mic = Button(this).apply {
            text = "🎙 صحبت"
            setOnClickListener { startVoiceCommand() }
        }
        val record = Button(this).apply {
            text = "🎤 ویس"
            setOnClickListener { startVoiceNote() }
        }
        val close = Button(this).apply {
            text = "×"
            setOnClickListener { removePanel() }
        }
        actions.addView(mic, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(record, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(close, LinearLayout.LayoutParams(dp(54), dp(48)))

        root.addView(title)
        root.addView(status)
        root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(send, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))
        root.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val width = dp(340).coerceAtMost(resources.displayMetrics.widthPixels - dp(24))
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        panel = root
        panelStatus = status
        panelInput = input
        recordButton = record
        voiceButton = mic
        updatePanelPosition(params)
        windowManager.addView(root, params)
    }

    private fun updatePanelPosition(explicit: WindowManager.LayoutParams? = null) {
        val view = panel ?: return
        val bubbleLayout = bubbleParams ?: return
        val params = explicit ?: (view.layoutParams as? WindowManager.LayoutParams) ?: return
        val width = if (params.width > 0) params.width else dp(340)
        params.x = (bubbleLayout.x - width - dp(8)).coerceIn(dp(8), resources.displayMetrics.widthPixels - width - dp(8))
        params.y = bubbleLayout.y.coerceAtLeast(dp(24))
        if (explicit == null) runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun removePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        panelStatus = null
        panelInput = null
        recordButton = null
        voiceButton = null
    }

    private fun startVoiceCommand() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_VOICE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching {
            startActivity(intent)
            panelStatus?.text = "حالت ویس مستقل همیشه‌یار باز شد."
        }.onFailure {
            panelStatus?.text = "نتونستم حالت ویس رو باز کنم."
        }
    }

    private fun handleCommand(value: String, speakAnswer: Boolean = false) {
        panelStatus?.text = "دارم بررسی می‌کنم…"
        scope.launch {
            val result = AssistantController.handle(this@FloatingAssistantService, value)
            panelStatus?.text = result.text

            if (speakAnswer) {
                panelStatus?.text = result.text
            }

            when (result.action) {
                AssistantAction.START_VOICE_NOTE -> startVoiceNote()
                AssistantAction.STOP_AND_SHARE_VOICE -> stopVoiceNoteAndShare()
                AssistantAction.ASK_BRAIN -> {
                    if (!CredentialVault.ready(this@FloatingAssistantService)) {
                        panelStatus?.text = "برای اتصال Gemini و Groq بخش تنظیمات همیشه‌یار را باز کن."
                    } else {
                        try {
                            val answer = LocalGateway.answer(
                                this@FloatingAssistantService,
                                result.externalPrompt ?: value
                            ) { stage ->
                                Handler(Looper.getMainLooper()).post { panelStatus?.text = stage }
                            }
                            panelStatus?.text = answer.text
                        } catch (error: Exception) {
                            panelStatus?.text = "نتوانستم پاسخ بدهم: " + (error.message ?: "مشکل اتصال")
                        }
                    }
                }
                AssistantAction.NONE -> Unit
            }
        }
    }

    private fun startVoiceNote() {
        panelStatus?.text = "حالت ویس مستقل رو باز می‌کنم…"
        startVoiceCommand()
    }

    private fun stopVoiceNoteAndShare() {
        panelStatus?.text = "برای پاسخ صوتی، از حالت ویس مستقل استفاده کن."
        startVoiceCommand()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        removePanel()
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
