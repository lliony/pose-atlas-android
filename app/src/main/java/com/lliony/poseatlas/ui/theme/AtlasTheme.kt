package com.lliony.poseatlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 收深渐变亮端，保留暖橙风格并保证白色按钮文字的对比度。
val AtlasAccentBrush = Brush.linearGradient(listOf(Color(0xFFC45432), Color(0xFFAE3826)))

private val Paper = lightColorScheme(
    primary = Color(0xFFC3482F), onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE7DF), onPrimaryContainer = Color(0xFF793321),
    secondary = Color(0xFF79665B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3EAE1), onSecondaryContainer = Color(0xFF514136),
    tertiary = Color(0xFF956B38),
    background = Color(0xFFFAF8F6), onBackground = Color(0xFF2B2220),
    surface = Color.White, onSurface = Color(0xFF2B2220),
    surfaceVariant = Color(0xFFF3EDE7), onSurfaceVariant = Color(0xFF78685F),
    surfaceContainer = Color(0xFFFFF8F0), surfaceContainerLow = Color(0xFFFFFCF9),
    surfaceContainerHigh = Color(0xFFF3EDE7), surfaceContainerHighest = Color(0xFFECE3DA),
    outline = Color(0xFF9B8A7E), outlineVariant = Color(0xFFE8DDD4),
    inverseSurface = Color(0xFF2B2220), inverseOnSurface = Color(0xFFFFF8F2),
)
private val White = Paper.copy(background = Color.White)
private val Dark = darkColorScheme(
    primary = Color(0xFFFFAB8D), onPrimary = Color(0xFF582414),
    primaryContainer = Color(0xFF633323), onPrimaryContainer = Color(0xFFFFDBCD),
    secondary = Color(0xFFE0C2AD), onSecondary = Color(0xFF3D2C22),
    secondaryContainer = Color(0xFF49372C), onSecondaryContainer = Color(0xFFF3DDCC),
    tertiary = Color(0xFFEBBE85),
    background = Color(0xFF211B18), onBackground = Color(0xFFF5E9DF),
    surface = Color(0xFF2C2420), onSurface = Color(0xFFF5E9DF),
    surfaceVariant = Color(0xFF40332B), onSurfaceVariant = Color(0xFFD0BCAD),
    surfaceContainer = Color(0xFF332820), surfaceContainerLow = Color(0xFF29211C),
    surfaceContainerHigh = Color(0xFF3C3028), surfaceContainerHighest = Color(0xFF48392F),
    outline = Color(0xFFAA9382), outlineVariant = Color(0xFF59473A),
    inverseSurface = Color(0xFFF5E9DF), inverseOnSurface = Color(0xFF382A23),
)
private val AtlasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
private val BaseTypography = Typography()
private val AtlasTypography = Typography(
    headlineLarge = BaseTypography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = BaseTypography.bodyMedium.copy(lineHeight = 23.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable fun AtlasTheme(theme: String, content: @Composable () -> Unit) {
    val colors = when (theme) { "paper" -> Paper; "white" -> White; "dark" -> Dark; else -> if (isSystemInDarkTheme()) Dark else Paper }
    MaterialTheme(colorScheme = colors, typography = AtlasTypography, shapes = AtlasShapes, content = content)
}
