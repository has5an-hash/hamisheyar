package ir.hamisheyar.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.hamisheyar.app.brain.AgentBrain
import ir.hamisheyar.app.brain.CredentialVault
import ir.hamisheyar.app.brain.LocalGateway
import ir.hamisheyar.app.data.ChatMessage
import ir.hamisheyar.app.data.LocalStore
import ir.hamisheyar.app.settings.AppSettings
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AgenticHomeScreen(onChat: () -> Unit, onSettings: () -> Unit, onVoice: () -> Unit) {
    val ctx = LocalContext.current
    val active = CredentialVault.hasBoth(ctx)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("✦ همیشه‌یار", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold)
        Text("مغز عامل‌محور شخصی شما", style = MaterialTheme.typography.titleMedium)
        Card(shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (active) "✓ مغز همیشه‌یار فعال است" else "اتصال مغز هنوز کامل نیست",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Gemini تحلیل چندرسانه‌ای را انجام می‌دهد و Groq برای پاسخ سریع، صوت و بررسی تکمیلی کمک می‌کند. مدیریت هر دو داخل گوشی است.")
                Text("سرور اختصاصی همیشه‌یار لازم نیست؛ ارتباط اینترنتی مستقیم با سرویس‌ها برقرار می‌شود.")
                Button(onClick = if (active) onChat else onSettings,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (active) "شروع گفتگو" else "اتصال اجباری Gemini و Groq")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onChat, modifier = Modifier.weight(1f), enabled = active) {
                Text("گفتگو")
            }
            OutlinedButton(onClick = onVoice, modifier = Modifier.weight(1f), enabled = active) {
                Text("گفتگوی صوتی")
            }
        }
        Text("همیشه‌یار می‌تواند محتوایی را که با Share به آن می‌دهی بررسی کند. برای لینک ریلز، فقط در صورت دسترسی واقعی به فایل ویدیو تحلیل تصویر و صدا ممکن است.")
        Text("استفاده از سهمیه رایگان APIها تضمینی یا نامحدود نیست و بعضی حساب‌ها به اعتبار پرداخت نیاز دارند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AgenticSettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var geminiKey by rememberSaveable { mutableStateOf("") }
    var groqKey by rememberSaveable { mutableStateOf("") }
    var geminiSaved by remember { mutableStateOf(!CredentialVault.read(ctx, CredentialVault.GEMINI).isNullOrEmpty()) }
    var groqSaved by remember { mutableStateOf(!CredentialVault.read(ctx, CredentialVault.GROQ).isNullOrEmpty()) }
    var testingGemini by remember { mutableStateOf(false) }
    var testingGroq by remember { mutableStateOf(false) }
    var statusGemini by remember { mutableStateOf("") }
    var statusGroq by remember { mutableStateOf("") }
    var liveShare by remember { mutableStateOf(AppSettings.liveShareEnabled(ctx)) }
    fun open(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text("راه‌اندازی مغز همیشه‌یار", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("اتصال هر دو سرویس الزامی است. حساب‌ها و API Keyها متعلق به خودت هستند؛ کلیدها با Android Keystore داخل گوشی رمزگذاری می‌شوند و به سرور همیشه‌یار ارسال نمی‌شوند.")
        ConnectionCard(
            title = "۱. اتصال Gemini",
            linkLabel = "ساخت کلید در Google AI Studio",
            onOpen = { open("https://aistudio.google.com/api-keys") },
            value = geminiKey,
            onChange = { geminiKey = it },
            connected = geminiSaved,
            loading = testingGemini,
            status = statusGemini,
            onTest = {
                val candidate = geminiKey.trim()
                if (candidate.isBlank()) { statusGemini = "کلید Gemini را وارد کن." }
                else {
                    testingGemini = true
                    statusGemini = "در حال آزمون واقعی Gemini…"
                    scope.launch {
                        try {
                            LocalGateway().validateGemini(candidate)
                            CredentialVault.save(ctx, CredentialVault.GEMINI, candidate)
                            geminiSaved = true
                            geminiKey = ""
                            statusGemini = "✓ کلید Gemini آزمایش و ایمن ذخیره شد."
                        } catch (ex: Exception) {
                            statusGemini = "اتصال ناموفق: " + (ex.message ?: "خطای شبکه")
                        } finally { testingGemini = false }
                    }
                }
            },
            onRemove = {
                CredentialVault.clear(ctx, CredentialVault.GEMINI)
                geminiSaved = false
                statusGemini = "اتصال Gemini حذف شد."
            }
        )
        ConnectionCard(
            title = "۲. اتصال Groq",
            linkLabel = "ساخت کلید در Groq Console",
            onOpen = { open("https://console.groq.com/keys") },
            value = groqKey,
            onChange = { groqKey = it },
            connected = groqSaved,
            loading = testingGroq,
            status = statusGroq,
            onTest = {
                val candidate = groqKey.trim()
                if (candidate.isBlank()) { statusGroq = "کلید Groq را وارد کن." }
                else {
                    testingGroq = true
                    statusGroq = "در حال آزمون واقعی Groq…"
                    scope.launch {
                        try {
                            LocalGateway().validateGroq(candidate)
                            CredentialVault.save(ctx, CredentialVault.GROQ, candidate)
                            groqSaved = true
                            groqKey = ""
                            statusGroq = "✓ کلید Groq آزمایش و ایمن ذخیره شد."
                        } catch (ex: Exception) {
                            statusGroq = "اتصال ناموفق: " + (ex.message ?: "خطای شبکه")
                        } finally { testingGroq = false }
                    }
                }
            },
            onRemove = {
                CredentialVault.clear(ctx, CredentialVault.GROQ)
                groqSaved = false
                statusGroq = "اتصال Groq حذف شد."
            }
        )
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(if (geminiSaved && groqSaved) "✓ مغز آماده است" else "🔒 مغز تا اتصال هر دو سرویس قفل است",
                    fontWeight = FontWeight.Bold)
                Text("کلید API با ورود به حساب Google یا Groq فرق دارد؛ باید کلید را از کنسول توسعه‌دهندگان خودت بسازی.")
                Text("هشدار: کلید شخصی حتی با رمزگذاری محلی بی‌خطر مطلق نیست. سهمیه رایگان محدود است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("بررسی خودکار موارد Share شده", fontWeight = FontWeight.Bold)
                    Text("اگر خاموش باشد محتوای اشتراکی فقط در صف می‌ماند.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = liveShare, onCheckedChange = {
                    liveShare = it
                    AppSettings.setLiveShareEnabled(ctx, it)
                })
            }
        }
        HorizontalDivider()
        Text("داده‌های درخواست، تصویر و صدا فقط هنگام درخواست خودت به Gemini یا Groq ارسال می‌شوند. مدل‌ها از اینترنت استفاده می‌کنند؛ هیچ مغز واحد جدیدی درون گوشی آموزش داده نمی‌شود.")
    }
}

@Composable
private fun ConnectionCard(
    title: String,
    linkLabel: String,
    onOpen: () -> Unit,
    value: String,
    onChange: (String) -> Unit,
    connected: Boolean,
    loading: Boolean,
    status: String,
    onTest: () -> Unit,
    onRemove: () -> Unit
) {
    Card(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (connected) "✓ کلید ذخیره شده" else "هنوز متصل نشده",
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(linkLabel) }
            OutlinedTextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                label = { Text("API Key شخصی") },
                placeholder = { Text("کلید جدید را وارد کن") }
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = onTest, enabled = !loading && value.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text("آزمون واقعی و ذخیره امن") }
            if (connected) OutlinedButton(onClick = onRemove, enabled = !loading,
                modifier = Modifier.fillMaxWidth()) { Text("قطع اتصال و حذف کلید") }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AgenticChatScreen(incomingShare: String?, attachmentPath: String?, autoRun: Boolean) {
    val ctx = LocalContext.current
    val store = remember { LocalStore(ctx) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(store.messages()) } }
    var input by rememberSaveable { mutableStateOf("") }
    var pendingPath by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val ready = CredentialVault.hasBoth(ctx)

    fun submit(text: String, filePath: String?) {
        if (busy || text.isBlank()) return
        busy = true
        stage = "همیشه‌یار در حال بررسی…"
        input = ""
        pendingPath = null
        val userText = text.trim()
        val id = store.addMessage("user", userText)
        messages.add(ChatMessage(id, "user", userText, System.currentTimeMillis()))
        scope.launch {
            val answer = try {
                AgentBrain.answer(ctx, userText, filePath) { stage = it }
            } catch (ex: Exception) {
                "درخواست کامل نشد: " + (ex.message ?: "خطای نامشخص") +
                    "\nکلیدها و اتصال اینترنت را در تنظیمات بررسی کن."
            } finally { busy = false; stage = "" }
            val answerId = store.addMessage("assistant", answer)
            messages.add(ChatMessage(answerId, "assistant", answer, System.currentTimeMillis()))
        }
    }

    LaunchedEffect(incomingShare, attachmentPath, autoRun) {
        if (!incomingShare.isNullOrBlank()) {
            val request = "این مورد را بررسی کن. فقط درباره محتوایی که واقعاً دریافت کرده‌ای نظر بده:\n" + incomingShare
            if (autoRun && ready) submit(request, attachmentPath)
            else { input = request; pendingPath = attachmentPath }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!ready) {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("🔒 ابتدا در تنظیمات کلیدهای Gemini و Groq را آزمایش و ثبت کن. بدون اتصال هر دو مغز فعال نمی‌شود.",
                    Modifier.padding(16.dp))
            }
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(messages, key = { it.id }) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.role == "user")
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(13.dp)) {
                        Text(if (msg.role == "user") "شما" else "همیشه‌یار",
                            fontWeight = FontWeight.Bold)
                        Text(msg.text)
                    }
                }
            }
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stage, modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it },
                enabled = ready && !busy,
                label = { Text("به همیشه‌یار بگو…") },
                maxLines = 5, modifier = Modifier.fillMaxWidth())
            if (pendingPath != null) Text("فایل پیوست آماده تحلیل است",
                style = MaterialTheme.typography.labelSmall)
            Button(
                onClick = { submit(input, pendingPath) },
                enabled = ready && !busy && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("ارسال به مغز همیشه‌یار") }
        }
    }
}

@Composable
fun AgenticVoiceScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("برای صحبت، دکمه میکروفون را بزن.") }
    var busy by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(ctx) { }.apply { language = Locale("fa", "IR") }
    }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }
    val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val spoken = it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            busy = true
            status = "دارم بررسی می‌کنم…"
            scope.launch {
                status = try {
                    AgentBrain.answer(ctx, spoken)
                } catch (ex: Exception) { "خطا: " + (ex.message ?: "ارتباط ناموفق") }
                busy = false
                tts.speak(status, TextToSpeech.QUEUE_FLUSH, null, "hamisheyar_voice")
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("گفتگوی صوتی همیشه‌یار", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(15.dp))
        Card(shape = RoundedCornerShape(22.dp)) {
            Text(status, Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        if (busy) CircularProgressIndicator()
        Button(
            onClick = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "با همیشه‌یار صحبت کن")
                }
                runCatching { recognizer.launch(intent) }
                    .onFailure { status = "سرویس تبدیل گفتار اندروید روی این گوشی در دسترس نیست." }
            },
            enabled = CredentialVault.hasBoth(ctx) && !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("🎙 صحبت با همیشه‌یار") }
        Text("تشخیص گفتار از سرویس‌های موجود روی گوشی استفاده می‌کند؛ پاسخ در همین صفحه نمایش و در صورت پشتیبانی TTS خوانده می‌شود.",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
    }
}
