package ir.hamisheyar.app.brain

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Both services must pass an actual generated-answer test before activating the brain. */
@Composable
fun BrainSetupCard(onReadyChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var geminiKey by remember { mutableStateOf("") }
    var groqKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val geminiConnected = remember(refresh) { CredentialVault.verified(context, "gemini") }
    val groqConnected = remember(refresh) { CredentialVault.verified(context, "groq") }
    val ready = geminiConnected && groqConnected

    fun connect(provider: String, key: String) {
        if (key.isBlank()) {
            message = "ابتدا کلید API خودت را وارد کن."
            return
        }
        busy = provider
        message = "در حال امتحان اتصال واقعی " + provider + "…"
        scope.launch {
            try {
                val reply = LocalGateway.verify(provider, key.trim())
                CredentialVault.put(context, provider, key.trim())
                CredentialVault.setVerified(context, provider, true)
                message = reply
                if (provider == "gemini") geminiKey = "" else groqKey = ""
            } catch (error: Exception) {
                message = "اتصال برقرار نشد: " + (error.message ?: "خطای شبکه")
            } finally {
                busy = ""
                refresh++
                onReadyChanged()
            }
        }
    }

    Card(shape = RoundedCornerShape(23.dp),
         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("مغز هوشمند همیشه‌یار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (ready) "✓ مغز فعال است؛ هر دو سرویس آزمایش و تأیید شدند."
                else "برای فعال شدن مغز باید Gemini و Groq هر دو متصل شوند. کلیدها فقط روی گوشی خودت رمزگذاری می‌شوند.",
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("۱. Gemini  " + if (geminiConnected) "✓ متصل" else "— اتصال لازم", fontWeight = FontWeight.SemiBold)
            if (!geminiConnected) {
                OutlinedTextField(
                    value = geminiKey, onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://aistudio.google.com/apikey"))) },
                        modifier = Modifier.weight(1f)
                    ) { Text("ساخت کلید") }
                    Button(onClick = { connect("gemini", geminiKey) },
                        enabled = busy.isEmpty(), modifier = Modifier.weight(1f)) { Text("تست و اتصال") }
                }
            } else {
                OutlinedButton(onClick = {
                    CredentialVault.clear(context, "gemini"); refresh++; onReadyChanged()
                }) { Text("قطع و تعویض کلید Gemini") }
            }
            Text("۲. Groq  " + if (groqConnected) "✓ متصل" else "— اتصال لازم", fontWeight = FontWeight.SemiBold)
            if (!groqConnected) {
                OutlinedTextField(
                    value = groqKey, onValueChange = { groqKey = it },
                    label = { Text("Groq API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://console.groq.com/keys"))) },
                        modifier = Modifier.weight(1f)
                    ) { Text("ساخت کلید") }
                    Button(onClick = { connect("groq", groqKey) },
                        enabled = busy.isEmpty(), modifier = Modifier.weight(1f)) { Text("تست و اتصال") }
                }
            } else {
                OutlinedButton(onClick = {
                    CredentialVault.clear(context, "groq"); refresh++; onReadyChanged()
                }) { Text("قطع و تعویض کلید Groq") }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            Text("استفاده از API ممکن است محدودیت رایگان یا هزینه مستقل داشته باشد. ورود عادی گوگل یا Groq جایگزین کلید API نیست.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
