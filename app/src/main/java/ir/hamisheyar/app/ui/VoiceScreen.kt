package ir.hamisheyar.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.hamisheyar.app.brain.BrainSetupCard
import ir.hamisheyar.app.brain.CredentialVault
import ir.hamisheyar.app.brain.LocalGateway
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var readyTick by remember { mutableStateOf(0) }
    val ready = remember(readyTick) { CredentialVault.ready(context) }
    var spoken by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val texts = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = texts?.firstOrNull().orEmpty()
            spoken = text
            if (text.isNotBlank()) scope.launch {
                busy = true
                try {
                    answer = LocalGateway.answer(context, text) { progress = it }.text
                } catch (error: Exception) {
                    answer = "خطا: " + (error.message ?: "سرویس گفتار یا مغز در دسترس نیست.")
                } finally { busy = false; progress = "" }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("گفت‌وگوی صوتی همیشه‌یار", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!ready) {
            Text("ابتدا هر دو سرویس را متصل کن.")
            BrainSetupCard { readyTick++ }
        } else {
            Text("گفتارت با تشخیص صدای اندروید به متن تبدیل می‌شود و پاسخ با مغز همیشه‌یار همین‌جا برمی‌گردد.")
            Button(
                modifier = Modifier.fillMaxWidth(), enabled = !busy,
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "با همیشه‌یار صحبت کن")
                    }
                    try { launcher.launch(intent) }
                    catch (error: Exception) { answer = "تشخیص گفتار روی این گوشی در دسترس نیست." }
                }
            ) { Text("🎙 صحبت کن") }
            if (spoken.isNotBlank()) Card {
                Column(Modifier.padding(14.dp)) {
                    Text("گفتار شما", fontWeight = FontWeight.Bold)
                    Text(spoken)
                }
            }
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(progress)
            }
            if (answer.isNotBlank()) Card {
                Column(Modifier.padding(14.dp)) {
                    Text("پاسخ همیشه‌یار", fontWeight = FontWeight.Bold)
                    Text(answer)
                }
            }
        }
    }
}
