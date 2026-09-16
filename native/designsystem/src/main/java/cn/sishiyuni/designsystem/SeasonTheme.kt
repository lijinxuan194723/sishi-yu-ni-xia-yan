package cn.sishiyuni.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cn.sishiyuni.core.data.AppPreferences
import java.time.LocalDateTime

@Immutable
data class SeasonColors(
    val ground: Color, val paper: Color, val soft: Color,
    val accent: Color, val ink: Color, val muted: Color, val border: Color,
)
val LocalSeason = staticCompositionLocalOf {
    SeasonColors(Color(0xFFE1F1E8), Color(0xFFF3FAF6), Color(0xFFCBE5D8),
        Color(0xFF2B6A5A), Color(0xFF244C41), Color(0xFF5B756C), Color(0xFFBEDACC))
}
val LocalAppPreferences = staticCompositionLocalOf { AppPreferences() }

/** Text scales, not hit targets or layout density. Chat uses its separate sp setting. */
@Composable
fun LukeTheme(preferences: AppPreferences, now: LocalDateTime = LocalDateTime.now(), content: @Composable () -> Unit) {
    val motion = rememberMotionPolicy(preferences)
    val night = preferences.isNight(now)
    val target = seasonColors(preferences.resolvedSeason(now), night)
    val spec = if (motion.reduced) snap<Color>() else tween(240)
    val ground by animateColorAsState(target.ground, spec, label = "season-ground")
    val paper by animateColorAsState(target.paper, spec, label = "season-paper")
    val soft by animateColorAsState(target.soft, spec, label = "season-soft")
    val accent by animateColorAsState(target.accent, spec, label = "season-accent")
    val ink by animateColorAsState(target.ink, spec, label = "season-ink")
    val muted by animateColorAsState(target.muted, spec, label = "season-muted")
    val border by animateColorAsState(target.border, spec, label = "season-border")
    val colors = SeasonColors(ground, paper, soft, accent, ink, muted, border)
    val scheme = if (night) darkColorScheme() else lightColorScheme()
    val scale = preferences.scale.coerceIn(.75f, 1.6f)
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = (size * scale).sp, lineHeight = (line * scale).sp, fontWeight = weight)
    val typography = Typography(
        headlineMedium = style(24, 32, FontWeight.SemiBold),
        titleLarge = style(18, 26, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.SemiBold),
        titleSmall = style(14, 21, FontWeight.Medium),
        bodyLarge = style(15, 24), bodyMedium = style(14, 22), bodySmall = style(12, 19),
        labelLarge = style(13, 20, FontWeight.Medium),
        labelMedium = style(12, 18, FontWeight.Medium), labelSmall = style(11, 16),
    )
    CompositionLocalProvider(LocalSeason provides colors, LocalAppPreferences provides preferences, LocalLukeMotion provides motion) {
        MaterialTheme(colorScheme = scheme.copy(
            primary = accent, onPrimary = if (night) ground else Color.White,
            primaryContainer = soft, onPrimaryContainer = ink,
            secondary = muted, secondaryContainer = soft, onSecondaryContainer = ink,
            background = ground, onBackground = ink, surface = paper, onSurface = ink,
            surfaceVariant = soft, onSurfaceVariant = muted, outline = border,
        ), typography = typography, content = content)
    }
}

fun seasonColors(season: String, night: Boolean): SeasonColors {
    if (night) return when (season) {
        "summer" -> SeasonColors(Color(0xFF19252C), Color(0xFF22353E), Color(0xFF314A52), Color(0xFF95CDD2), Color(0xFFE5F4F4), Color(0xFFABC3C8), Color(0xFF425C64))
        "autumn" -> SeasonColors(Color(0xFF2C241F), Color(0xFF382E26), Color(0xFF504034), Color(0xFFE8BC87), Color(0xFFF3E8DC), Color(0xFFC8B6A5), Color(0xFF625040))
        "winter" -> SeasonColors(Color(0xFF192730), Color(0xFF233640), Color(0xFF334C59), Color(0xFFA2CEDF), Color(0xFFE4F1F6), Color(0xFFADC7D2), Color(0xFF45606D))
        else -> SeasonColors(Color(0xFF1D2B25), Color(0xFF283B32), Color(0xFF3C5145), Color(0xFFA6D4B8), Color(0xFFE3F0E7), Color(0xFFB0C5B7), Color(0xFF4B6456))
    }
    return when (season) {
        "summer" -> SeasonColors(Color(0xFFE5F2F3), Color(0xFFF5FBFA), Color(0xFFCDE6E5), Color(0xFF337F82), Color(0xFF285457), Color(0xFF607D7C), Color(0xFFBDDCD9))
        "autumn" -> SeasonColors(Color(0xFFF4EADC), Color(0xFFFFF9F1), Color(0xFFF0DDBF), Color(0xFF98652E), Color(0xFF634A31), Color(0xFF8A7660), Color(0xFFE3CDAE))
        "winter" -> SeasonColors(Color(0xFFE0F0F6), Color(0xFFF3FBFF), Color(0xFFC2E2EC), Color(0xFF1E7388), Color(0xFF244F5C), Color(0xFF587986), Color(0xFFB6D7E3))
        else -> SeasonColors(Color(0xFFE1F1E8), Color(0xFFF3FAF6), Color(0xFFCBE5D8), Color(0xFF2B6A5A), Color(0xFF244C41), Color(0xFF5B756C), Color(0xFFBEDACC))
    }
}
