package dev.lumbina.tv.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.TextScale

/** Lumbina's fixed dark palette, shared by the theme and backgrounds drawn outside it. */
object LumbinaColors {
    val Background = Color(0xFF0E1014)
    val Surface = Color(0xFF15181E)
    val OnSurface = Color(0xFFE8E9EC)
    val OnAccent = Color(0xFF1B1300)
}

private fun colors(accent: Accent) = darkColorScheme(
    primary = accent.color,
    onPrimary = LumbinaColors.OnAccent,
    background = LumbinaColors.Background,
    onBackground = LumbinaColors.OnSurface,
    surface = LumbinaColors.Surface,
    onSurface = LumbinaColors.OnSurface,
)

/** The accent's color: light enough for dark text on it, bright enough to mark focus from across the room. */
val Accent.color: Color
    get() = when (this) {
        Accent.Amber -> Color(0xFFFFB547)
        Accent.Sky -> Color(0xFF7CC4FF)
        Accent.Mint -> Color(0xFF6FE3B4)
        Accent.Rose -> Color(0xFFFF8FA8)
        Accent.Violet -> Color(0xFFC3A6FF)
    }

@Composable
fun LumbinaTheme(
    accent: Accent = Accent.Amber,
    textScale: TextScale = TextScale.Normal,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(accent) { colors(accent) }
    val typography = remember(textScale) {
        val base = Typography()
        if (textScale == TextScale.Large) base.scaled(LARGE_TEXT) else base
    }
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

private fun Typography.scaled(factor: Float) = Typography(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
)

private fun TextStyle.scaled(factor: Float) = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

private const val LARGE_TEXT = 1.2f
