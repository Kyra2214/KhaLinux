package com.khalinux.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta do app: tons de grafite com destaque verde-água; o terminal tem tema próprio (TerminalThemes). */
object KhaColors {
    val Background = Color(0xFF0E1317)
    val Surface = Color(0xFF151C21)
    val SurfaceHigh = Color(0xFF1D262C)
    val Outline = Color(0xFF3A4850)
    val OutlineSoft = Color(0xFF2A353C)
    val Primary = Color(0xFF80CBC4)
    val OnPrimary = Color(0xFF00201D)
    val PrimaryContainer = Color(0xFF1F4A45)
    val Text = Color(0xFFE8F0F2)
    val TextMuted = Color(0xFF9BAAB0)
    val Ok = Color(0xFF81C995)
    val Warn = Color(0xFFFFD580)
    val Error = Color(0xFFFFAB91)
    val ErrorContainer = Color(0xFF4A2A22)
}

private val KhaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun KhaLinuxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KhaColors.Primary,
            onPrimary = KhaColors.OnPrimary,
            primaryContainer = KhaColors.PrimaryContainer,
            onPrimaryContainer = KhaColors.Text,
            secondaryContainer = KhaColors.PrimaryContainer,
            onSecondaryContainer = KhaColors.Text,
            background = KhaColors.Background,
            onBackground = KhaColors.Text,
            surface = KhaColors.Surface,
            onSurface = KhaColors.Text,
            surfaceVariant = KhaColors.SurfaceHigh,
            onSurfaceVariant = KhaColors.TextMuted,
            outline = KhaColors.Outline,
            outlineVariant = KhaColors.OutlineSoft,
            error = KhaColors.Error,
            errorContainer = KhaColors.ErrorContainer,
            onErrorContainer = KhaColors.Text
        ),
        shapes = KhaShapes,
        content = content
    )
}
