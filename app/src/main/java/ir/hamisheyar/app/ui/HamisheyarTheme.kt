package ir.hamisheyar.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6FF),
    onPrimaryContainer = Color(0xFF1B1B5E),
    secondary = Color(0xFF007F7D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F1EF),
    onSecondaryContainer = Color(0xFF003735),
    tertiary = Color(0xFFB54D29),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF181A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181A20),
    surfaceVariant = Color(0xFFEEF0F7),
    onSurfaceVariant = Color(0xFF5A5D68),
    outline = Color(0xFFD9DDEA),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBFC0FF),
    onPrimary = Color(0xFF29287C),
    primaryContainer = Color(0xFF3F3F9E),
    onPrimaryContainer = Color(0xFFE4E3FF),
    secondary = Color(0xFF73DAD6),
    onSecondary = Color(0xFF003735),
    secondaryContainer = Color(0xFF00504E),
    onSecondaryContainer = Color(0xFF96F7F3),
    tertiary = Color(0xFFFFB59A),
    background = Color(0xFF111218),
    onBackground = Color(0xFFE5E6ED),
    surface = Color(0xFF17181F),
    onSurface = Color(0xFFE5E6ED),
    surfaceVariant = Color(0xFF242630),
    onSurfaceVariant = Color(0xFFC5C7D1),
    outline = Color(0xFF444754),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
        bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun HamisheyarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
