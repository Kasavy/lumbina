package dev.lumbina.tv.core.domain.theme

import dev.lumbina.tv.core.domain.home.HomeAppearance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Whether a theme is dark or light: decides how wallpaper is veiled and what the system bars look like. */
enum class ThemeBase { Dark, Light }

/**
 * What a screen may ask a theme for. Screens name a role, never a color, so a new theme is a new
 * set of values and no screen changes. The list is closed: a role is added with the code that uses it.
 */
enum class ThemeRole {
    /** Behind everything, where no wallpaper shows. */
    Background,

    /** Panels, dialogs, menus. */
    Panel,

    /** A raised part of a panel: a focused row's neighbour, a field, a chip. */
    PanelRaised,

    /** Main text and icons. */
    Text,

    /** Hints, values, captions. */
    TextSecondary,

    /** Dividers and outlines. */
    Line,

    /** Focus, switches, progress, unless the user chose a highlight color of their own. */
    Accent,

    /** Text and icons on [Accent]. */
    OnAccent,

    /** A problem the user can fix: a missing permission, a failed download. */
    Warning,

    /** Something that failed for good. */
    Error,

    /** The veil over wallpaper: [Veil] at [ThemeSpec.veilTop] above the rows, at [ThemeSpec.veilBottom] under them. */
    Veil,
}

/**
 * A theme as data: a name and a value for every [ThemeRole]. Colors are ARGB, as `0xFFRRGGBB`.
 * The built-in themes are values of this type, and so will be any theme that comes from outside.
 */
data class ThemeSpec(
    val id: String,
    val base: ThemeBase,
    val colors: Map<ThemeRole, Long>,
    /** How dense [ThemeRole.Veil] is over the top of the wallpaper, 0 to 1. */
    val veilTop: Float,
    /** How dense it is under the rows of tiles, where text has to be read. */
    val veilBottom: Float,
) {
    init {
        val missing = ThemeRole.entries - colors.keys
        require(missing.isEmpty()) { "theme $id has no value for $missing" }
        require(veilTop in 0f..1f && veilBottom in 0f..1f) { "theme $id: veil out of range" }
    }

    fun color(role: ThemeRole): Long = colors.getValue(role)

    /**
     * Pairs of roles whose text can't be read on their background: the contrast is below
     * [MIN_CONTRAST]. A theme with any is not to be offered.
     */
    fun unreadable(): List<Pair<ThemeRole, ThemeRole>> = READ_ON.filter { (text, ground) ->
        contrast(color(text), color(ground)) < MIN_CONTRAST
    }

    companion object {
        /** WCAG AA for normal text; TV text is large, but it is read from across the room. */
        const val MIN_CONTRAST = 4.5

        private val READ_ON = listOf(
            ThemeRole.Text to ThemeRole.Background,
            ThemeRole.Text to ThemeRole.Panel,
            ThemeRole.Text to ThemeRole.PanelRaised,
            ThemeRole.TextSecondary to ThemeRole.Panel,
            ThemeRole.OnAccent to ThemeRole.Accent,
            ThemeRole.Warning to ThemeRole.Panel,
            ThemeRole.Error to ThemeRole.Panel,
        )
    }
}

/** The themes Lumbina ships with. */
object Themes {
    /** The look Lumbina has always had. */
    val Dark = ThemeSpec(
        id = "dark",
        base = ThemeBase.Dark,
        colors = mapOf(
            ThemeRole.Background to 0xFF0E1014,
            ThemeRole.Panel to 0xFF15181E,
            ThemeRole.PanelRaised to 0xFF22262E,
            ThemeRole.Text to 0xFFE8E9EC,
            ThemeRole.TextSecondary to 0xFFAEB4BE,
            ThemeRole.Line to 0xFF3A3F49,
            ThemeRole.Accent to 0xFFFFB547,
            ThemeRole.OnAccent to 0xFF1B1300,
            ThemeRole.Warning to 0xFFFFC478,
            ThemeRole.Error to 0xFFFFB4AB,
            ThemeRole.Veil to 0xFF000000,
        ),
        veilTop = 0.18f,
        veilBottom = 0.86f,
    )

    val Light = ThemeSpec(
        id = "light",
        base = ThemeBase.Light,
        colors = mapOf(
            ThemeRole.Background to 0xFFEEF1F3,
            ThemeRole.Panel to 0xFFFAFBFC,
            ThemeRole.PanelRaised to 0xFFE6EAED,
            ThemeRole.Text to 0xFF10161A,
            ThemeRole.TextSecondary to 0xFF4E5A62,
            ThemeRole.Line to 0xFFC9D1D6,
            ThemeRole.Accent to 0xFF0B6B57,
            ThemeRole.OnAccent to 0xFFFFFFFF,
            ThemeRole.Warning to 0xFF8A4B00,
            ThemeRole.Error to 0xFFB3261E,
            ThemeRole.Veil to 0xFFF0F3F5,
        ),
        veilTop = 0.35f,
        veilBottom = 0.93f,
    )

    /** Pure black for projectors and OLED screens in a dark room: the picture ends where the light ends. */
    val Black = ThemeSpec(
        id = "black",
        base = ThemeBase.Dark,
        colors = Dark.colors + mapOf(
            ThemeRole.Background to 0xFF000000,
            ThemeRole.Panel to 0xFF0A0B0D,
            ThemeRole.PanelRaised to 0xFF17191D,
            ThemeRole.Line to 0xFF2C3037,
        ),
        veilTop = 0.25f,
        veilBottom = 1f,
    )

    val builtIn: List<ThemeSpec> = listOf(Dark, Light, Black)

    /** The theme saved under [id]; one that is gone (a theme from outside, removed) falls back to [Dark]. */
    fun byId(id: String?): ThemeSpec = builtIn.firstOrNull { it.id == id } ?: Dark

    /**
     * The theme the setting [id] means: "as the system" is the dark or the light one, as the
     * device is set ([systemDark]).
     */
    fun forSetting(id: String, systemDark: Boolean): ThemeSpec = when (id) {
        HomeAppearance.THEME_SYSTEM -> if (systemDark) Dark else Light
        else -> byId(id)
    }
}

/** WCAG contrast ratio of two opaque ARGB colors, 1 (the same) to 21 (black on white). */
fun contrast(first: Long, second: Long): Double {
    val a = luminance(first)
    val b = luminance(second)
    return (max(a, b) + LUMINANCE_OFFSET) / (min(a, b) + LUMINANCE_OFFSET)
}

private fun luminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val value = ((argb shr shift) and BYTE) / BYTE.toDouble()
        return if (value <= LINEAR_LIMIT) value / LINEAR_DIVISOR else ((value + GAMMA_OFFSET) / GAMMA_SCALE).pow(GAMMA)
    }
    return RED_WEIGHT * channel(RED_SHIFT) + GREEN_WEIGHT * channel(GREEN_SHIFT) + BLUE_WEIGHT * channel(0)
}

private const val BYTE = 0xFFL
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val LINEAR_LIMIT = 0.03928
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_SCALE = 1.055
private const val GAMMA = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val LUMINANCE_OFFSET = 0.05
