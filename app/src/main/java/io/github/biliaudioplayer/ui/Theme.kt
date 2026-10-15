package io.github.biliaudioplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Coral = Color(0xFFE56451)
internal val Cream = Color(0xFFFAF7F2)
internal val Ink = Color(0xFF302E2B)
internal val Muted = Color(0xFF756E65)
internal val SoftCoral = Color(0xFFFFE9E2)

private val MusicColors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = SoftCoral,
    onPrimaryContainer = Color(0xFF763528),
    secondary = Color(0xFF756A5F),
    secondaryContainer = Color(0xFFEFE7DC),
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EAE2),
    onSurfaceVariant = Muted,
    outline = Color(0xFFB7AEA3),
    outlineVariant = Color(0xFFE7DFD5),
    error = Color(0xFFB44537),
)

private val MusicTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun BiliTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MusicColors, typography = MusicTypography, content = content)
}
