package ir.hamisheyar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.hamisheyar.app.chatgpt.ChatGptBridge

@Composable
fun VoiceScreen() {
    val context = LocalContext.current
    val installed = ChatGptBridge.isInstalled(context)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        null,
                        modifier = Modifier.size(58.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                "گفت‌وگوی صوتی با حساب خودت",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "همیشه‌یار مدل و موتور صوتی جداگانه دانلود نمی‌کند. برای ویس، اپ رسمی ChatGPT با همان حساب و سهمیه خودت باز می‌شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            Card(shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Rounded.Mic,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("روش استفاده", fontWeight = FontWeight.Bold)
                    Text(
                        "۱) ChatGPT را باز کن.\n۲) با حساب خودت وارد باش.\n۳) داخل ChatGPT روی دکمه Voice بزن و صحبت کن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (installed) ChatGptBridge.openApp(context)
                    else ChatGptBridge.openInstallPage(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.OpenInNew, null)
                Spacer(Modifier.size(8.dp))
                Text(if (installed) "باز کردن ChatGPT برای ویس" else "نصب ChatGPT")
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "این مسیر عمداً بدون API Key و بدون مدل آفلاین طراحی شده.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
