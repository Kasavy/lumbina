package dev.lumbina.tv.core.designsystem

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.theme.ThemeBase
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.domain.theme.ThemeSpec
import dev.lumbina.tv.core.domain.theme.Themes

/** The dark theme's base colors, for backgrounds drawn outside [LumbinaTheme]. */
object LumbinaColors {
    val Background = Themes.Dark.colorOf(ThemeRole.Background)
}

/**
 * The theme in use, for the roles Material has no slot for (warning, the veil over wallpaper).
 * A local because it is one app-wide choice every screen reads, set once at the root.
 */
@Suppress("CompositionLocalAllowlist")
val LocalTheme = staticCompositionLocalOf { Themes.Dark }

fun ThemeSpec.colorOf(role: ThemeRole): Color = Color(color(role))

/**
 * Material's slots filled from [theme]'s roles; [accent], when the user chose one, takes the place
 * of the theme's own highlight, with dark text on it: the accents are all light colors. The dark
 * theme leaves the slots it never set at the library's values, so that it looks as it always did;
 * the others set every slot they have a role for.
 */
private fun colors(theme: ThemeSpec, accent: Accent?): ColorScheme {
    val base = if (theme.base == ThemeBase.Dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = accent?.color ?: theme.colorOf(ThemeRole.Accent),
        onPrimary = (if (accent == null) theme else Themes.Dark).colorOf(ThemeRole.OnAccent),
        background = theme.colorOf(ThemeRole.Background),
        onBackground = theme.colorOf(ThemeRole.Text),
        surface = theme.colorOf(ThemeRole.Panel),
        onSurface = theme.colorOf(ThemeRole.Text),
    )
    if (theme.id == Themes.Dark.id) return scheme
    return scheme.copy(
        surfaceVariant = theme.colorOf(ThemeRole.PanelRaised),
        onSurfaceVariant = theme.colorOf(ThemeRole.TextSecondary),
        border = theme.colorOf(ThemeRole.Line),
        error = theme.colorOf(ThemeRole.Error),
    )
}

/** The accent's color. */
val Accent.color: Color get() = Color(argb)

/** [accent]: the user's own highlight color; null follows [theme]. */
@Composable
fun LumbinaTheme(
    accent: Accent? = null,
    textScale: TextScale = TextScale.Normal,
    theme: ThemeSpec = Themes.Dark,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(theme, accent) { colors(theme, accent) }
    val typography = remember(textScale) {
        val base = Typography()
        if (textScale == TextScale.Large) base.scaled(LARGE_TEXT) else base
    }
    // A remote does not drag lists past their ends, so the overscroll effect is never seen; it costs
    // every row work on every frame all the same.
    CompositionLocalProvider(LocalTheme provides theme, LocalOverscrollFactory provides null) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
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
