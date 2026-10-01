package ir.hamisheyar.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.hamisheyar.app.ai.LocalAiEngine
import ir.hamisheyar.app.ai.FastReplyEngine
import ir.hamisheyar.app.ai.ModelDownloadManager
import ir.hamisheyar.app.data.ChatMessage
import ir.hamisheyar.app.data.InboxEvent
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.diagnostics.DiagnosticsLogger
import ir.hamisheyar.app.service.FloatingAssistantService
import ir.hamisheyar.app.settings.AppSettings
import ir.hamisheyar.app.system.AccessManager
import ir.hamisheyar.app.ui.HamisheyarTheme
import ir.hamisheyar.app.ui.ProfessionalHomeScreen
import ir.hamisheyar.app.ui.VoiceScreen
import ir.hamisheyar.app.voice.OfflineVoiceEngine
import ir.hamisheyar.app.voice.VoiceAssetsManager
import ir.hamisheyar.app.web.SearchHit
import ir.hamisheyar.app.web.WebResearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val incomingShare = mutableStateOf<String?>(null)
    private val openVoice = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingShare.value = intent.getStringExtra(EXTRA_INCOMING_SHARE)
        openVoice.value = intent.getBooleanExtra(EXTRA_OPEN_VOICE, false)
        setContent {
            HamisheyarTheme {
                androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HamisheyarRoot(incomingShare.value, openVoice.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShare.value = intent.getStringExtra(EXTRA_INCOMING_SHARE)
        openVoice.value = intent.getBooleanExtra(EXTRA_OPEN_VOICE, false)
    }

    override fun onResume() {
        super.onResume()
        val notificationState = AccessManager.notificationAccessState(this)
        if (notificationState.granted && !notificationState.serviceConnectedRecently) {
            AccessManager.requestNotificationListenerReconnect(this)
        }
    }

    companion object {
        const val EXTRA_INCOMING_SHARE = "incoming_share"
        const val EXTRA_OPEN_VOICE = "open_voice"
    }
}

private enum class MainTab(val title: String, val icon: ImageVector) {
    HOME("خانه", Icons.Rounded.Home),
    CHAT("گفتگو", Icons.Rounded.ChatBubble),
    VOICE("ویس", Icons.Rounded.GraphicEq),
    INBOX("صندوق", Icons.Rounded.Inbox),
    SETTINGS("تنظیمات", Icons.Rounded.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HamisheyarRoot(incomingShare: String?, openVoice: Boolean) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    if (!incomingShare.isNullOrBlank()) {
        LaunchedEffect(incomingShare) { tab = MainTab.CHAT }
    }
    if (openVoice) {
        LaunchedEffect(openVoice) { tab = MainTab.VOICE }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("همیشه‌یار", fontWeight = FontWeight.Bold)
                        Text(
                            "دستیار خصوصی روی گوشی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            when (tab) {
                MainTab.HOME -> ProfessionalHomeScreen(
                    onOpenChat = { tab = MainTab.CHAT },
                    onOpenVoice = { tab = MainTab.VOICE }
                )
                MainTab.CHAT -> ChatScreen(incomingShare)
                MainTab.VOICE -> VoiceScreen()
                MainTab.INBOX -> InboxScreen()
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenChat: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canOverlay = remember(tick) { Settings.canDrawOverlays(context) }
    val notificationState = remember(tick) {
        AccessManager.notificationAccessState(context)
    }
    val notificationAccess = notificationState.granted
    val micGranted = remember(tick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val modelReady = remember(tick) { LocalAiEngine.isConfigured(context) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SmartToy, null, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("همراهت، هرجا که باشی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "حباب شناور، اعلان‌ها، اشتراک‌گذاری، صدا و هوش مصنوعی محلی.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.ChatBubble, null)
                        Spacer(Modifier.width(8.dp))
                        Text("گفتگو با همیشه‌یار")
                    }
                }
            }
        }

        item {
            Text("راه‌اندازی اولیه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        item {
            StatusCard(
                icon = Icons.Rounded.Memory,
                title = "مدل هوش مصنوعی محلی",
                description = if (modelReady) "مدل GGUF آماده اجراست." else "از تنظیمات یک مدل GGUF وارد کن.",
                ready = modelReady
            )
        }

        item {
            NotificationAccessCard(
                granted = notificationAccess,
                connected = notificationState.serviceConnectedRecently,
                likelySideloaded = notificationState.likelySideloaded,
                installerPackage = notificationState.installerPackage,
                onOpenAppInfo = {
                    AccessManager.openAppInfoForRestrictedSettings(context)
                },
                onOpenAccess = {
                    AccessManager.openNotificationAccess(context)
                },
                onReconnect = {
                    AccessManager.requestNotificationListenerReconnect(context)
                    tick++
                }
            )
        }

        item {
            StatusCard(
                icon = Icons.Rounded.SmartToy,
                title = "حباب شناور",
                description = if (canOverlay) "همیشه‌یار می‌تواند روی برنامه‌های دیگر ظاهر شود." else "اجازه نمایش روی برنامه‌ها لازم است.",
                ready = canOverlay,
                button = if (canOverlay) "اجرای حباب" else "دادن اجازه",
                onButton = {
                    if (Settings.canDrawOverlays(context)) {
                        ContextCompat.startForegroundService(context, Intent(context, FloatingAssistantService::class.java))
                    } else {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            )
        }

        item {
            StatusCard(
                icon = Icons.Rounded.Mic,
                title = "میکروفون",
                description = if (micGranted) "فرمان صوتی و ضبط ویس آماده است." else "برای صحبت با همیشه‌یار و ضبط ویس اجازه بده.",
                ready = micGranted,
                button = if (!micGranted) "اجازه میکروفون" else null,
                onButton = { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            item {
                OutlinedButton(
                    onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("اجازه نمایش اعلان دائمی حباب")
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("حریم خصوصی", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "چت و تحلیل مدل روی خود گوشی انجام می‌شود. فقط وقتی «جست‌وجوی وب» روشن باشد، عبارت جست‌وجو برای دریافت نتایج اینترنتی ارسال می‌شود.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    description: String,
    ready: Boolean,
    button: String? = null,
    onButton: () -> Unit = {}
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    null,
                    tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            if (button != null) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onButton, modifier = Modifier.fillMaxWidth()) { Text(button) }
            }
        }
    }
}

@Composable
private fun NotificationAccessCard(
    granted: Boolean,
    connected: Boolean,
    likelySideloaded: Boolean,
    installerPackage: String?,
    onOpenAppInfo: () -> Unit,
    onOpenAccess: () -> Unit,
    onReconnect: () -> Unit
) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Notifications,
                    null,
                    tint = if (granted && connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("دسترسی اعلان‌ها", fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            granted && connected -> "دسترسی فعال است و سرویس همیشه‌یار واقعاً به اعلان‌ها وصل شده."
                            granted -> "دسترسی فعال است؛ همیشه‌یار در حال اتصال مجدد سرویس اعلان‌هاست."
                            likelySideloaded && Build.VERSION.SDK_INT >= 33 ->
                                "Android نصب مستقیم APK را تشخیص داده و ممکن است این دسترسی را با Restricted settings قفل کرده باشد."
                            else -> "برای فهمیدن پیام‌ها و پاسخ سریع، Notification Access را فعال کن."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            if (!granted) {
                Spacer(Modifier.height(12.dp))
                if (likelySideloaded && Build.VERSION.SDK_INT >= 33) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("رفع قفل امنیتی اندروید", fontWeight = FontWeight.Bold)
                            Text(
                                "۱) «اطلاعات برنامه» را باز کن.\n" +
                                    "۲) بالای صفحه سه‌نقطه را بزن.\n" +
                                    "۳) «Allow restricted settings / اجازه تنظیمات محدودشده» را تأیید کن.\n" +
                                    "۴) برگرد و «فعال‌کردن دسترسی اعلان‌ها» را بزن.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (!installerPackage.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Installer: $installerPackage",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenAppInfo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("۱. باز کردن اطلاعات برنامه")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenAccess,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (likelySideloaded && Build.VERSION.SDK_INT >= 33) "۲. فعال‌کردن دسترسی اعلان‌ها" else "فعال‌کردن دسترسی اعلان‌ها")
                }
            } else if (!connected) {
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onReconnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بازاتصال سرویس اعلان")
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(incomingShare: String?) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(store.messages()) }
    }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var generationStatus by remember { mutableStateOf<String?>(null) }
    var inlineListening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(incomingShare) {
        if (!incomingShare.isNullOrBlank()) {
            input = "این مورد را بررسی کن:\n" + incomingShare
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        generationStatus = if (granted) {
            "میکروفون آماده است؛ دوباره روی آیکن میکروفون بزن."
        } else {
            "برای گفت‌وگوی صوتی، اجازه میکروفون لازم است."
        }
    }

    fun startVoice() {
        if (!VoiceAssetsManager.isSttReady(context)) {
            runCatching { VoiceAssetsManager.startInstall(context) }
            generationStatus = "دارم بسته ویس آفلاین رو آماده می‌کنم؛ از تب «ویس» هم می‌تونی وضعیتش رو ببینی."
            return
        }

        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!inlineListening) {
            runCatching {
                OfflineVoiceEngine.startRecording(context)
                inlineListening = true
                generationStatus = "گوش می‌دم… وقتی حرفت تموم شد دوباره روی میکروفون بزن."
            }.onFailure {
                generationStatus = it.message ?: "میکروفون شروع نشد."
            }
        } else {
            inlineListening = false
            generationStatus = "دارم صدات رو روی خود گوشی تبدیل می‌کنم…"
            scope.launch {
                runCatching {
                    OfflineVoiceEngine.stopAndTranscribe(context) {
                        generationStatus = it
                    }
                }.onSuccess {
                    input = it.text
                    generationStatus = null
                }.onFailure {
                    generationStatus = it.message ?: "تشخیص گفتار انجام نشد."
                }
            }
        }
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isBlank() || busy) return
        input = ""
        val id = store.addMessage("user", prompt)
        messages += ChatMessage(id, "user", prompt, System.currentTimeMillis())
        busy = true
        generationStatus = "در حال آماده‌سازی…"

        scope.launch {
            try {
                var hits: List<SearchHit> = emptyList()
                val webContext = if (AppSettings.webResearchEnabled(context)) {
                    generationStatus = "دارم اطلاعات به‌روز وب را می‌گیرم…"
                    hits = runCatching { WebResearchService.search(prompt) }.getOrDefault(emptyList())
                    if (hits.isEmpty()) "" else "نتایج جست‌وجوی تازه وب:\n" + WebResearchService.asContext(hits)
                } else ""

                val instant = if (webContext.isBlank()) FastReplyEngine.tryReply(prompt) else null
                val answerText = when {
                    instant != null -> instant
                    !LocalAiEngine.isConfigured(context) -> {
                        val health = LocalAiEngine.health(context)
                        health.warning ?: "مدل محلی هنوز آماده نیست. از تنظیمات مدل سریع مخصوص ARM را دانلود و تست کن."
                    }
                    else -> {
                        runCatching {
                            LocalAiEngine.answer(
                                context = context,
                                prompt = prompt,
                                extraContext = webContext,
                                onStage = { generationStatus = it }
                            ).text
                        }.getOrElse {
                            DiagnosticsLogger.log(context, "CHAT", "chat generation failed", it)
                            "اجرای مدل محلی انجام نشد: " + (it.message ?: "خطای نامشخص") +
                                "\n\nاز تنظیمات روی «تست مدل» بزن تا علت دقیق مشخص شود."
                        }
                    }
                }

                val sources = if (hits.isNotEmpty()) {
                    "\n\nمنابع وب:\n" + hits.joinToString("\n") { "• " + it.title + " — " + it.url }
                } else ""
                val finalText = answerText.ifBlank {
                    "مدل پاسخ خالی داد. از تنظیمات «تست مدل» را اجرا کن."
                } + sources
                val answerId = store.addMessage("assistant", finalText)
                messages += ChatMessage(answerId, "assistant", finalText, System.currentTimeMillis())
            } catch (t: Throwable) {
                DiagnosticsLogger.log(context, "CHAT", "unexpected chat failure", t)
                val text = "خطای غیرمنتظره در چت: " + (t.message ?: "خطای نامشخص")
                val answerId = store.addMessage("assistant", text)
                messages += ChatMessage(answerId, "assistant", text, System.currentTimeMillis())
            } finally {
                busy = false
                generationStatus = null
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (busy) {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                generationStatus?.let {
                    Text(
                        it,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Icon(Icons.Rounded.SmartToy, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("چی می‌خوای بدونی؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "سؤال کن، لینک یا پست برای همیشه‌یار Share کن، یا درباره آخرین پیام بپرس.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageBubble(message)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("پیام به همیشه‌یار…") },
                    maxLines = 5
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(onClick = { startVoice() }) {
                            Icon(
                                if (inlineListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                contentDescription = if (inlineListening) "پایان ضبط" else "صدا"
                            )
                        }
                        if (AppSettings.webResearchEnabled(context)) {
                            AssistChip(onClick = {}, label = { Text("وب روشن") }, leadingIcon = { Icon(Icons.Rounded.Cloud, null) })
                        }
                    }
                    Button(onClick = { send() }, enabled = input.isNotBlank() && !busy) {
                        Icon(Icons.Rounded.Send, null)
                        Spacer(Modifier.width(6.dp))
                        Text("ارسال")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (user) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Surface(
            color = if (user) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = if (user) {
                RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)
            } else {
                RoundedCornerShape(22.dp, 22.dp, 22.dp, 8.dp)
            },
            tonalElevation = if (user) 0.dp else 2.dp,
            shadowElevation = if (user) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!user) {
                        Surface(
                            shape = RoundedCornerShape(99.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "✦",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        if (user) "شما" else "همیشه‌یار",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (user) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun InboxScreen() {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    var events by remember { mutableStateOf(store.events()) }

    LaunchedEffect(Unit) {
        while (true) {
            events = withContext(Dispatchers.IO) { store.events() }
            delay(1500)
        }
    }

    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
                Icon(Icons.Rounded.Inbox, null, Modifier.size(60.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("صندوق هنوز خالیه", fontWeight = FontWeight.Bold)
                Text(
                    "اعلان‌های مجاز و چیزهایی که برای همیشه‌یار Share می‌کنی اینجا می‌آیند.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(events, key = { it.id }) { event ->
            InboxCard(event)
        }
    }
}

@Composable
private fun InboxCard(event: InboxEvent) {
    val time = remember(event.createdAt) {
        SimpleDateFormat("HH:mm", Locale("fa", "IR")).format(Date(event.createdAt))
    }
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (event.kind == "shared") Icons.Rounded.Share else Icons.Rounded.Notifications,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(event.source, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(7.dp))
            Text(event.sender, style = MaterialTheme.typography.labelLarge)
            Text(event.body, style = MaterialTheme.typography.bodyMedium, maxLines = 5)
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var announce by remember { mutableStateOf(AppSettings.announcementEnabled(context)) }
    var liveShare by remember { mutableStateOf(AppSettings.liveShareEnabled(context)) }
    var webResearch by remember { mutableStateOf(AppSettings.webResearchEnabled(context)) }
    var whatsapp by remember { mutableStateOf(AppSettings.whatsappEnabled(context)) }
    var telegram by remember { mutableStateOf(AppSettings.telegramEnabled(context)) }
    var instagram by remember { mutableStateOf(AppSettings.instagramEnabled(context)) }
    var sms by remember { mutableStateOf(AppSettings.smsEnabled(context)) }
    var modelPath by remember { mutableStateOf(AppSettings.modelPath(context)) }
    var importing by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var modelDownloadState by remember { mutableStateOf(ModelDownloadManager.inspect(context)) }
    var modelTestRunning by remember { mutableStateOf(false) }
    var modelTestResult by remember { mutableStateOf<String?>(null) }
    var voiceSttReady by remember { mutableStateOf(VoiceAssetsManager.isSttReady(context)) }
    var voiceTtsReady by remember { mutableStateOf(VoiceAssetsManager.isTtsReady(context)) }
    var voiceDownloading by remember { mutableStateOf(false) }
    var voiceProgress by remember { mutableStateOf(0f) }
    var voiceMessage by remember { mutableStateOf("بسته صوتی هنوز نصب نشده.") }
    var privacyDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val previousPath = modelPath
            val state = withContext(Dispatchers.IO) { ModelDownloadManager.inspect(context) }
            modelDownloadState = state
            if (!state.completedPath.isNullOrBlank() && state.completedPath != previousPath) {
                LocalAiEngine.reset()
                modelPath = state.completedPath
                importMessage = "مدل دانلود شد و آماده استفاده است."
            }
            val voice = runCatching {
                VoiceAssetsManager.inspectAndPrepare(context)
            }.getOrNull()
            if (voice != null) {
                voiceSttReady = voice.sttReady
                voiceTtsReady = voice.ttsReady
                voiceDownloading = voice.downloading
                voiceProgress = voice.progress
                voiceMessage = voice.message
            }
            delay(if (state.active || voiceDownloading) 1_000L else 3_000L)
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            importMessage = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
                        val target = File(dir, "hamisheyar-model.gguf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("فایل باز نشد")
                        target
                    }
                }
                result.onSuccess { file ->
                    AppSettings.setModelPath(context, file.absolutePath)
                    LocalAiEngine.reset()
                    modelPath = file.absolutePath
                    importMessage = "مدل با موفقیت آماده شد."
                }.onFailure {
                    importMessage = "ورود مدل ناموفق بود: " + (it.message ?: "خطا")
                }
                importing = false
            }
        }
    }

    val modelFile = modelPath?.let(::File)?.takeIf { it.isFile }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("هوش مصنوعی روی دستگاه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (modelFile != null) "مدل محلی آماده است" else "مدل محلی انتخاب نشده", fontWeight = FontWeight.Bold)
                        Text(
                            if (modelFile != null) {
                                modelFile.name + " • " + (modelFile.length() / (1024 * 1024)) + " MB"
                            } else {
                                "فرمت GGUF؛ برای گوشی‌های معمولی مدل‌های کوچک‌تر بهترند."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (importing) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("در حال کپی مدل؛ برنامه را نبند…", style = MaterialTheme.typography.labelSmall)
                } else {
                    Spacer(Modifier.height(12.dp))

                    if (modelDownloadState.active) {
                        Text(modelDownloadState.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        if (modelDownloadState.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { modelDownloadState.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                ModelDownloadManager.cancel(context)
                                modelDownloadState = ModelDownloadManager.inspect(context)
                                importMessage = "دانلود لغو شد."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("لغو دانلود")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    ModelDownloadManager.start(context, ModelDownloadManager.FAST)
                                    modelDownloadState = ModelDownloadManager.inspect(context)
                                    importMessage = "دانلود مدل سبک شروع شد؛ از نوار اعلان هم می‌تونی وضعیتش را ببینی."
                                }.onFailure {
                                    importMessage = "شروع دانلود ممکن نشد: " + (it.message ?: "خطا")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("دانلود مدل سریع مخصوص ARM • ۳۵۲ MB", fontWeight = FontWeight.Bold)
                                Text("Qwen2.5 0.5B • بهینه برای گوشی‌های ARM و کم‌رم", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    ModelDownloadManager.start(context, ModelDownloadManager.BALANCED)
                                    modelDownloadState = ModelDownloadManager.inspect(context)
                                    importMessage = "دانلود مدل پیشنهادی شروع شد؛ ممکنه کمی زمان ببره."
                                }.onFailure {
                                    importMessage = "شروع دانلود ممکن نشد: " + (it.message ?: "خطا")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("دانلود مدل قوی‌تر • ۱.۱۲ GB", fontWeight = FontWeight.Bold)
                                Text("Qwen2.5 1.5B • برای گوشی‌های با RAM بیشتر", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (modelFile == null) "یا انتخاب فایل GGUF از گوشی" else "تعویض مدل با فایل GGUF")
                    }
                }
                importMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                if (modelFile != null) {
                    val health = LocalAiEngine.health(context)
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = if (health.warning == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                if (health.warning == null) "بررسی اولیه مدل: سالم" else "هشدار مدل",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                health.warning ?: "GGUF معتبر است • RAM آزاد فعلی: ${health.availableRamMb} MB",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (modelFile.name.contains("qwen3", ignoreCase = true)) {
                                Text(
                                    "حالت سازگاری Qwen3 فعال است؛ Thinking به‌صورت خودکار خاموش می‌شود.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            modelTestRunning = true
                            modelTestResult = "در حال شروع تست…"
                            scope.launch {
                                runCatching {
                                    LocalAiEngine.selfTest(context) { modelTestResult = it }
                                }.onSuccess { result ->
                                    modelTestResult =
                                        "✅ مدل جواب داد: ${result.text}\n" +
                                            "سرعت: " + String.format(Locale.US, "%.1f", result.tokensPerSecond) +
                                            " token/s • زمان: ${result.durationMs / 1000.0} ثانیه"
                                }.onFailure {
                                    modelTestResult = "❌ تست ناموفق: " + (it.message ?: "خطای نامشخص")
                                }
                                modelTestRunning = false
                            }
                        },
                        enabled = !modelTestRunning && !importing && !modelDownloadState.active,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (modelTestRunning) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (modelTestRunning) "در حال تست واقعی مدل…" else "تست واقعی مدل روی همین گوشی")
                    }
                    modelTestResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        run {
            val deviceHealth = LocalAiEngine.health(context)
            val profile = when {
                deviceHealth.availableRamMb < 1200 -> "خیلی سبک"
                deviceHealth.availableRamMb < 2200 -> "کم‌رم / اقتصادی"
                deviceHealth.availableRamMb < 3500 -> "متعادل"
                else -> "قدرت بالاتر"
            }
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("پروفایل خودکار دستگاه", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Build.MANUFACTURER + " " + Build.MODEL,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "RAM آزاد فعلی: " + deviceHealth.availableRamMb + " MB • پروفایل: " + profile,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Context و تعداد Thread مدل بر اساس همین وضعیت به‌صورت خودکار تنظیم می‌شود.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text("دسترسی‌های سیستمی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val accessState = AccessManager.notificationAccessState(context)
        NotificationAccessCard(
            granted = accessState.granted,
            connected = accessState.serviceConnectedRecently,
            likelySideloaded = accessState.likelySideloaded,
            installerPackage = accessState.installerPackage,
            onOpenAppInfo = { AccessManager.openAppInfoForRestrictedSettings(context) },
            onOpenAccess = { AccessManager.openNotificationAccess(context) },
            onReconnect = { AccessManager.requestNotificationListenerReconnect(context) }
        )

        Text("رفتار دستیار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingsSwitch(
            "اعلام صوتی پیام جدید",
            "مثلاً «یه پیام از حسین داری».",
            announce
        ) {
            announce = it
            AppSettings.setAnnouncementEnabled(context, it)
        }
        SettingsSwitch(
            "حالت زنده Share",
            "بعد از Share، همیشه‌یار همان لحظه باز شود؛ خاموش باشد فقط در صف ذخیره می‌کند.",
            liveShare
        ) {
            liveShare = it
            AppSettings.setLiveShareEnabled(context, it)
        }
        SettingsSwitch(
            "جست‌وجوی وب",
            "برای سؤال‌های به‌روز، عبارت جست‌وجو از گوشی به اینترنت فرستاده می‌شود.",
            webResearch
        ) {
            webResearch = it
            AppSettings.setWebResearchEnabled(context, it)
        }

        Text("برنامه‌های مجاز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingsSwitch("واتس‌اپ", "خواندن اعلان و پاسخ سریع در صورت پشتیبانی خود واتس‌اپ.", whatsapp) {
            whatsapp = it
            AppSettings.setWhatsappEnabled(context, it)
        }
        SettingsSwitch("تلگرام", "خواندن اعلان و پاسخ سریع در صورت پشتیبانی خود تلگرام.", telegram) {
            telegram = it
            AppSettings.setTelegramEnabled(context, it)
        }
        SettingsSwitch("اینستاگرام", "اعلان دایرکت، فالو، لایک و سایر اعلان‌های قابل مشاهده.", instagram) {
            instagram = it
            AppSettings.setInstagramEnabled(context, it)
        }
        SettingsSwitch("پیامک", "از اعلان برنامه SMS؛ بدون گرفتن READ_SMS یا SEND_SMS.", sms) {
            sms = it
            AppSettings.setSmsEnabled(context, it)
        }

        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("موتور ویس مستقل همیشه‌یار", fontWeight = FontWeight.Bold)
                        Text(
                            if (voiceSttReady && voiceTtsReady) {
                                "Whisper فارسی + صدای فارسی آفلاین آماده‌اند؛ Google Voice استفاده نمی‌شود."
                            } else {
                                "برای شنیدن و صحبت‌کردن مستقل، یک بار بسته صوتی حدود ۱۰۰ مگابایت نصب می‌شود."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (voiceDownloading) {
                    LinearProgressIndicator(
                        progress = { voiceProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(voiceMessage, style = MaterialTheme.typography.labelSmall)
                } else if (!(voiceSttReady && voiceTtsReady)) {
                    Button(
                        onClick = {
                            runCatching { VoiceAssetsManager.startInstall(context) }
                                .onSuccess {
                                    voiceDownloading = true
                                    voiceMessage = "دانلود بسته صوتی شروع شد…"
                                }
                                .onFailure {
                                    voiceMessage = "شروع دانلود ممکن نشد: " + (it.message ?: "خطا")
                                }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("نصب بسته ویس آفلاین")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(voiceMessage, style = MaterialTheme.typography.labelSmall)
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            "✓ میکروفون مستقل و صدای فارسی آماده است",
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    OfflineVoiceEngine.speakPersian(
                                        context,
                                        "سلام، من همیشه یارم. صدای آفلاین من آماده است."
                                    )
                                }.onFailure {
                                    voiceMessage = "تست صدا ناموفق بود: " + (it.message ?: "خطا")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تست صدای فارسی")
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                OfflineVoiceEngine.reset()
                                VoiceAssetsManager.clear(context)
                                voiceSttReady = false
                                voiceTtsReady = false
                                voiceDownloading = false
                                voiceProgress = 0f
                                voiceMessage = "بسته صوتی پاک شد؛ دوباره نصبش کن."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("نصب مجدد بسته صوتی")
                    }
                }
            }
        }

        Text("حریم خصوصی و داده‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { privacyDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Security, null)
            Spacer(Modifier.width(8.dp))
            Text("سیاست حریم خصوصی")
        }
        OutlinedButton(onClick = { clearDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Delete, null)
            Spacer(Modifier.width(8.dp))
            Text("پاک کردن تاریخچه محلی")
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("نکته سازگاری پیام‌رسان‌ها", fontWeight = FontWeight.Bold)
                Text(
                    "پاسخ متنی وقتی خود اعلان دکمه Reply بدهد مستقیم ارسال می‌شود. برای ویس، همیشه‌یار فایل را ضبط می‌کند و پیام‌رسان را با ویس آماده باز می‌کند؛ ارسال نهایی صوت در بعضی پیام‌رسان‌ها به محدودیت خود آن برنامه بستگی دارد.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedButton(
            onClick = {
                val report = buildString {
                    appendLine("Hamisheyar diagnostics")
                    appendLine("Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT)
                    appendLine("Device: " + Build.MANUFACTURER + " " + Build.MODEL)
                    val health = LocalAiEngine.health(context)
                    appendLine("Model: " + (health.modelName ?: "none"))
                    appendLine("Model valid: " + health.validGguf)
                    appendLine("Model size MB: " + health.fileSizeMb)
                    appendLine("Available RAM MB: " + health.availableRamMb)
                    appendLine("Notification granted: " + AccessManager.notificationAccessState(context).granted)
                    appendLine()
                    append(DiagnosticsLogger.read(context))
                }
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Hamisheyar diagnostics", report))
                Toast.makeText(context, "گزارش فنی کپی شد.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("کپی گزارش فنی برای عیب‌یابی")
        }
        Spacer(Modifier.height(20.dp))
    }

    if (privacyDialog) {
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text("باشه") } },
            title = { Text("حریم خصوصی همیشه‌یار") },
            text = {
                Text(
                    "• گفتگو و فایل مدل روی دستگاه نگهداری می‌شوند.\n" +
                        "• متن اعلان‌های برنامه‌هایی که خودت روشن کرده‌ای فقط برای قابلیت دستیار در حافظه محلی ذخیره می‌شود.\n" +
                        "• همیشه‌یار اطلاعات شخصی را نمی‌فروشد.\n" +
                        "• جست‌وجوی وب اختیاری است و هنگام روشن بودن، عبارت جست‌وجو به سرویس جست‌وجوی اینترنتی ارسال می‌شود.\n" +
                        "• هر دسترسی از تنظیمات قابل خاموش کردن است و تاریخچه را می‌توانی همین‌جا پاک کنی."
                )
            }
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            confirmButton = {
                Button(onClick = {
                    LocalStore(context).clearAll()
                    clearDialog = false
                }) { Text("پاک کن") }
            },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("لغو") } },
            title = { Text("پاک کردن تاریخچه؟") },
            text = { Text("چت‌ها و صندوق محلی حذف می‌شوند. فایل مدل حذف نمی‌شود.") }
        )
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
