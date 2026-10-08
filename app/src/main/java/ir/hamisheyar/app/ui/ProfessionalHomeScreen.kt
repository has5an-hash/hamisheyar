package ir.hamisheyar.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.hamisheyar.app.brain.CredentialVault
import ir.hamisheyar.app.brain.BrainSetupCard
import ir.hamisheyar.app.service.FloatingAssistantService
import ir.hamisheyar.app.system.AccessManager

@Composable
fun ProfessionalHomeScreen(
    onOpenChat: () -> Unit,
    onOpenVoice: () -> Unit
) {
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

    val brainReady = remember(tick) { CredentialVault.ready(context) }
    val notification = remember(tick) { AccessManager.notificationAccessState(context) }
    val overlay = remember(tick) { Settings.canDrawOverlays(context) }
    val readyCount = listOf(brainReady, notification.granted, overlay).count { it }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF3F46C8),
                                    Color(0xFF6750D8),
                                    Color(0xFF008C89)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Box(
                                    modifier = Modifier.size(58.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "همیشه‌یار",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "مغز مستقل با Gemini + Groq",
                                    color = Color.White.copy(alpha = 0.84f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))
                        Text(
                            if (readyCount == 3) "همه‌چیز آماده‌ست"
                            else "راه‌اندازی $readyCount از ۳ بخش کامل شده",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(99.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(readyCount / 3f)
                                    .height(8.dp)
                                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(99.dp))
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onOpenChat,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.ChatBubble, null)
                                Spacer(Modifier.width(7.dp))
                                Text("چت")
                            }
                            FilledTonalButton(
                                onClick = onOpenVoice,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.GraphicEq, null)
                                Spacer(Modifier.width(7.dp))
                                Text("ویس")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "وضعیت اتصال",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.SmartToy,
                    title = "مغز هوشمند",
                    value = if (brainReady) "فعال" else "نیاز به اتصال هر دو",
                    ready = brainReady
                )
                DashboardMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Notifications,
                    title = "اعلان‌ها",
                    value = if (notification.granted) {
                        if (notification.serviceConnectedRecently) "متصل" else "اجازه داده شده"
                    } else "خاموش",
                    ready = notification.granted
                )
            }
        }

        item {
            DashboardMetric(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.SmartToy,
                title = "حباب شناور",
                value = if (overlay) "فعال" else "خاموش",
                ready = overlay
            )
        }

        item {
            Text(
                "راه‌اندازی سریع",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        item {
            BrainSetupCard { tick++ }
        }

        if (!notification.granted) {
            item {
                SetupActionCard(
                    icon = Icons.Rounded.Notifications,
                    title = "دسترسی اعلان‌ها",
                    description = if (notification.likelySideloaded) {
                        "اگر اندروید گزینه را قفل کرده، از اطلاعات برنامه Allow restricted settings را فعال کن و دوباره برگرد."
                    } else {
                        "برای فهمیدن پیام‌های واتس‌اپ، تلگرام، اینستاگرام و پیامک."
                    },
                    button = if (notification.likelySideloaded) "اطلاعات برنامه" else "تنظیم دسترسی"
                ) {
                    if (notification.likelySideloaded) {
                        AccessManager.openAppInfoForRestrictedSettings(context)
                    } else {
                        AccessManager.openNotificationAccess(context)
                    }
                }
            }
        }

        if (!overlay) {
            item {
                SetupActionCard(
                    icon = Icons.Rounded.SmartToy,
                    title = "حباب شناور",
                    description = "همیشه‌یار را روی برنامه‌های دیگر در دسترس نگه می‌دارد.",
                    button = "فعال‌سازی"
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        } else {
            item {
                SetupActionCard(
                    icon = Icons.Rounded.SmartToy,
                    title = "حباب شناور",
                    description = "دسترسی آماده است؛ حباب را اجرا کن.",
                    button = "اجرای حباب",
                    ready = true
                ) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, FloatingAssistantService::class.java)
                    )
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "بدون سرور اختصاصی؛ کلیدهای شخصی کاربر به‌صورت رمزگذاری‌شده روی گوشی ذخیره می‌شوند و همیشه‌یار مستقیماً به Gemini و Groq وصل می‌شود.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    ready: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp)) {
            Surface(
                shape = CircleShape,
                color = if (ready) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (ready) Icons.Rounded.Check else icon,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (ready) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    button: String,
    ready: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (ready) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = if (ready) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (ready) {
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(button)
                }
            } else {
                Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text(button)
                }
            }
        }
    }
}
