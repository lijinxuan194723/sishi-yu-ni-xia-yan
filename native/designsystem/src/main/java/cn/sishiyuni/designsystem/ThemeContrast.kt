package cn.sishiyuni.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

fun contrastRatio(a: Color, b: Color): Float {
    val first = a.luminance(); val second = b.luminance()
    return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
}

/** Keep the requested hue when readable; otherwise move only as far as necessary. */
fun readableForeground(preferred: Color, backgrounds: List<Color>, minimum: Float = 4.5f): Color {
    require(backgrounds.isNotEmpty())
    fun score(color: Color) = backgrounds.minOf { contrastRatio(color, it) }
    if (score(preferred) >= minimum) return preferred
    val endpoint = if (score(Color.Black) >= score(Color.White)) Color.Black else Color.White
    if (score(endpoint) < minimum) return endpoint
    var low = 0f; var high = 1f
    repeat(12) {
        val middle = (low + high) / 2f
        if (score(lerp(preferred, endpoint, middle)) >= minimum) high = middle else low = middle
    }
    return lerp(preferred, endpoint, high)
}

fun accessibleSeasonColors(raw: SeasonColors): SeasonColors {
    var colors = raw
    val surfaces = listOf(raw.ground, raw.paper, raw.soft)
    val darkestScore = surfaces.minOf { contrastRatio(Color.Black, it) }
    val lightestScore = surfaces.minOf { contrastRatio(Color.White, it) }
    if (maxOf(darkestScore, lightestScore) < 4.5f) {
        // During a light/dark transition, container tones can straddle the usable text
        // threshold. Temporarily pull only those tones toward the paper tone; keeping
        // incompatible mid-gray containers cannot be solved by changing the ink alone.
        val endpoint = if (contrastRatio(Color.Black, raw.paper) >= contrastRatio(Color.White, raw.paper)) Color.Black else Color.White
        fun compatible(surface: Color): Color {
            if (contrastRatio(endpoint, surface) >= 4.5f) return surface
            var low = 0f; var high = 1f
            repeat(12) {
                val middle = (low + high) / 2f
                if (contrastRatio(endpoint, lerp(surface, raw.paper, middle)) >= 4.5f) high = middle else low = middle
            }
            return lerp(surface, raw.paper, high)
        }
        colors = raw.copy(ground = compatible(raw.ground), soft = compatible(raw.soft))
    }
    val backgrounds = listOf(colors.ground, colors.paper, colors.soft)
    return colors.copy(ink = readableForeground(colors.ink, backgrounds),
        muted = readableForeground(colors.muted, backgrounds), accent = readableForeground(colors.accent, backgrounds))
}

/** Fill every Material surface family; default purple containers must not leak into a season. */
fun seasonColorScheme(colors: SeasonColors, night: Boolean): ColorScheme {
    val base = if (night) darkColorScheme() else lightColorScheme()
    val onAccent = readableForeground(if (night) colors.ground else Color.White, listOf(colors.accent))
    return base.copy(
        primary = colors.accent, onPrimary = onAccent,
        primaryContainer = colors.soft, onPrimaryContainer = colors.ink,
        secondary = colors.accent, onSecondary = onAccent,
        secondaryContainer = colors.soft, onSecondaryContainer = colors.ink,
        tertiary = colors.accent, onTertiary = onAccent,
        tertiaryContainer = colors.soft, onTertiaryContainer = colors.ink,
        background = colors.ground, onBackground = colors.ink,
        surface = colors.paper, onSurface = colors.ink,
        surfaceVariant = colors.soft, onSurfaceVariant = colors.muted,
        surfaceTint = colors.accent,
        surfaceDim = colors.ground, surfaceBright = colors.paper,
        surfaceContainerLowest = colors.ground,
        surfaceContainerLow = lerp(colors.ground, colors.paper, .75f),
        surfaceContainer = colors.paper,
        surfaceContainerHigh = lerp(colors.paper, colors.soft, .3f),
        surfaceContainerHighest = lerp(colors.paper, colors.soft, .55f),
        inverseSurface = colors.ink,
        inverseOnSurface = readableForeground(colors.paper, listOf(colors.ink)),
        inversePrimary = readableForeground(colors.soft, listOf(colors.ink)),
        outline = colors.border, outlineVariant = colors.border.copy(alpha = .65f),
    )
}
