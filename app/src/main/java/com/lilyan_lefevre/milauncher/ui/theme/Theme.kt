package com.lilyan_lefevre.milauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val LauncherColors = darkColorScheme(
    primary = Color(0xFF5B8CFF),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF1A1E27),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFF8A93A6),
    border = Color.White,
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LauncherColors) {
        // No Surface at the root, so the default text color is provided here.
        CompositionLocalProvider(LocalContentColor provides LauncherColors.onSurface, content = content)
    }
}
