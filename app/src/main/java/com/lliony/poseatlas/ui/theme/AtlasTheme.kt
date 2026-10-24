package com.lliony.poseatlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Paper = lightColorScheme(primary = Color(0xFF98482F), onPrimary = Color.White, primaryContainer = Color(0xFFF2DCCB), onPrimaryContainer = Color(0xFF542714), secondaryContainer = Color(0xFFE3E8D9), onSecondaryContainer = Color(0xFF303C28), background = Color(0xFFF6F1E7), surface = Color(0xFFF6F1E7), surfaceVariant = Color(0xFFEAE1D3), onSurface = Color(0xFF302A25), secondary = Color(0xFF55624B))
private val White = lightColorScheme(primary = Color(0xFFA33D30), primaryContainer = Color(0xFFF5DDD7), onPrimaryContainer = Color(0xFF54251D), secondaryContainer = Color(0xFFEAE3DD), onSecondaryContainer = Color(0xFF382C25), background = Color.White, surface = Color.White, onSurface = Color(0xFF252321), surfaceVariant = Color(0xFFF2ECE7))
private val Dark = darkColorScheme(primary = Color(0xFFF0B37B), onPrimary = Color(0xFF472B14), primaryContainer = Color(0xFF593923), onPrimaryContainer = Color(0xFFFFDDC4), secondaryContainer = Color(0xFF3D4735), onSecondaryContainer = Color(0xFFDFE8D4), background = Color(0xFF201B18), surface = Color(0xFF201B18), surfaceVariant = Color(0xFF3A302A), onSurface = Color(0xFFF1E6DB))
@Composable fun AtlasTheme(theme: String, content: @Composable () -> Unit) {
    val colors = when (theme) { "paper" -> Paper; "white" -> White; "dark" -> Dark; else -> if (isSystemInDarkTheme()) Dark else Paper }
    MaterialTheme(colorScheme = colors, content = content)
}
