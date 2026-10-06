package ir.hamisheyar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.hamisheyar.app.chatgpt.ChatGptBridge
import ir.hamisheyar.app.data.ChatMessage
import ir.hamisheyar.app.data.InboxEvent
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.settings.AppSettings
import ir.hamisheyar.app.system.AccessManager
import ir.hamisheyar.app.ui.HamisheyarTheme
import ir.hamisheyar.app.ui.ProfessionalHomeScreen
import ir.hamisheyar.app.ui.VoiceScreen
import kotlinx.coroutines.delay
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
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
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
        val state = AccessManager.notificationAccessState(this)
        if (state.granted && !state.serviceConnectedRecently) {
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

    LaunchedEffect(incomingShare) {
        if (!incomingShare.isNullOrBlank()) tab = MainTab.CHAT
    }
    LaunchedEffect(openVoice) {
        if (openVoice) tab = MainTab.VOICE
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("همیشه‌یار", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "پل شخصی شما به ChatGPT",
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
private fun ChatScreen(incomingShare: String?) {
    val context = LocalContext.current
    val store = remember { LocalStore(context) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(store.messages()) }
    }
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(incomingShare) {
        if (!incomingShare.isNullOrBlank()) {
            input = "این مورد را بررسی کن:\n$incomingShare"
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val prompt = input.trim()
        if (prompt.isBlank()) return

        input = ""
        val userId = store.addMessage("user", prompt)
        messages += ChatMessage(userId, "user", prompt, System.currentTimeMillis())

        val result = ChatGptBridge.sendPrompt(context, prompt)
        val status = buildString {
            append(result.message)
            append("\n\n")
            append("هوش مصنوعی داخل حساب ChatGPT خودت اجرا می‌شود؛ همیشه‌یار API Key یا مدل جداگانه‌ای ندارد.")
            if (result.launched) {
                append(" پاسخ را داخل ChatGPT می‌بینی.")
            }
        }
        val statusId = store.addMessage("assistant", status)
        messages += ChatMessage(statusId, "assistant", status, System.currentTimeMillis())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "پیام‌ها از اینجا به اپ رسمی ChatGPT و حساب خودت تحویل داده می‌شوند.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.SmartToy,
                        null,
                        Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "چی می‌خوای از ChatGPT بپرسی؟",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "بنویس، یا از اینستاگرام و برنامه‌های دیگر چیزی برای همیشه‌یار Share کن.",
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
                    placeholder = { Text("پیام به ChatGPT از طریق همیشه‌یار…") },
                    maxLines = 5
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        ChatGptBridge.openApp(context)
                    }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = "باز کردن ChatGPT")
                    }
                    Button(onClick = { send() }, enabled = input.isNotBlank()) {
                        Icon(Icons.Rounded.Send, null)
                        Spacer(Modifier.width(6.dp))
                        Text("فرستادن")
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
            color = if (user) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = if (user) {
                RoundedCornerShape(22.dp, 22.dp, 8.dp, 22.dp)
            } else {
                RoundedCornerShape(22.dp, 22.dp, 22.dp, 8.dp)
            },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                Text(
                    if (user) "شما" else "همیشه‌یار",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(5.dp))
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
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
            events = store.events()
            delay(1500)
        }
    }

    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp)
            ) {
                Icon(
                    Icons.Rounded.Inbox,
                    null,
                    Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
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
        items(events, key = { it.id }) { event -> InboxCard(event) }
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
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }

    var liveShare by remember { mutableStateOf(AppSettings.liveShareEnabled(context)) }
    var whatsapp by remember { mutableStateOf(AppSettings.whatsappEnabled(context)) }
    var telegram by remember { mutableStateOf(AppSettings.telegramEnabled(context)) }
    var instagram by remember { mutableStateOf(AppSettings.instagramEnabled(context)) }
    var sms by remember { mutableStateOf(AppSettings.smsEnabled(context)) }
    var privacyDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val chatGptInstalled = remember(tick) { ChatGptBridge.isInstalled(context) }
    val access = remember(tick) { AccessManager.notificationAccessState(context) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("اتصال ChatGPT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (chatGptInstalled) "اپ رسمی ChatGPT پیدا شد" else "اپ ChatGPT نصب نیست",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ورود و سهمیه داخل خود ChatGPT است؛ همیشه‌یار رمز، توکن یا API Key شما را نمی‌گیرد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        if (chatGptInstalled) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        null,
                        tint = if (chatGptInstalled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (chatGptInstalled) ChatGptBridge.openApp(context)
                        else ChatGptBridge.openInstallPage(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (chatGptInstalled) "باز کردن ChatGPT" else "نصب ChatGPT")
                }
            }
        }

        Text("دسترسی اعلان‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    when {
                        access.granted && access.serviceConnectedRecently -> "✓ سرویس اعلان‌ها متصل است."
                        access.granted -> "دسترسی داده شده؛ اتصال سرویس را دوباره برقرار کن."
                        else -> "برای فهمیدن پیام‌های واتس‌اپ، تلگرام، اینستاگرام و پیامک دسترسی اعلان لازم است."
                    }
                )
                Spacer(Modifier.height(10.dp))
                if (!access.granted) {
                    Button(
                        onClick = { AccessManager.openNotificationAccess(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("فعال‌کردن دسترسی اعلان‌ها") }
                } else if (!access.serviceConnectedRecently) {
                    FilledTonalButton(
                        onClick = {
                            AccessManager.requestNotificationListenerReconnect(context)
                            tick++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("بازاتصال سرویس اعلان") }
                }
            }
        }

        Text("رفتار Share", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingsSwitch(
            "ارسال زنده به ChatGPT",
            "وقتی از اینستاگرام یا برنامه دیگری چیزی Share می‌کنی، مستقیم به ChatGPT حساب خودت تحویل داده شود.",
            liveShare
        ) {
            liveShare = it
            AppSettings.setLiveShareEnabled(context, it)
        }

        Text("برنامه‌های مجاز برای اعلان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingsSwitch("واتس‌اپ", "اعلان‌ها و پاسخ سریع در صورت پشتیبانی خود واتس‌اپ.", whatsapp) {
            whatsapp = it
            AppSettings.setWhatsappEnabled(context, it)
        }
        SettingsSwitch("تلگرام", "اعلان‌ها و پاسخ سریع در صورت پشتیبانی خود تلگرام.", telegram) {
            telegram = it
            AppSettings.setTelegramEnabled(context, it)
        }
        SettingsSwitch("اینستاگرام", "دایرکت، فالو، لایک و اعلان‌های قابل مشاهده.", instagram) {
            instagram = it
            AppSettings.setInstagramEnabled(context, it)
        }
        SettingsSwitch("پیامک", "از اعلان برنامه پیامک؛ بدون READ_SMS یا SEND_SMS.", sms) {
            sms = it
            AppSettings.setSmsEnabled(context, it)
        }

        Text("حریم خصوصی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        Spacer(Modifier.height(20.dp))
    }

    if (privacyDialog) {
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            confirmButton = {
                TextButton(onClick = { privacyDialog = false }) { Text("باشه") }
            },
            title = { Text("حریم خصوصی همیشه‌یار") },
            text = {
                Text(
                    "• همیشه‌یار API Key شما را نمی‌خواهد و حساب ChatGPT را داخل خودش لاگین نمی‌کند.\n" +
                        "• ورود و استفاده از مدل داخل اپ رسمی ChatGPT انجام می‌شود.\n" +
                        "• متن‌های Share شده و تاریخچه صندوق برای امکانات همیشه‌یار روی گوشی ذخیره می‌شوند.\n" +
                        "• پاسخ ChatGPT به‌صورت رسمی به همیشه‌یار برگردانده نمی‌شود؛ پاسخ را داخل ChatGPT می‌بینی.\n" +
                        "• دسترسی اعلان‌ها فقط برای برنامه‌هایی است که خودت روشن کرده‌ای."
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
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) { Text("لغو") }
            },
            title = { Text("پاک کردن تاریخچه؟") },
            text = { Text("چت‌های محلی و صندوق همیشه‌یار پاک می‌شوند.") }
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
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
