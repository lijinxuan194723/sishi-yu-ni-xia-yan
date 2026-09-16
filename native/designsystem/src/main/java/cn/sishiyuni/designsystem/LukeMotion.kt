package cn.sishiyuni.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cn.sishiyuni.core.data.AppPreferences
import kotlinx.coroutines.isActive

@Immutable data class LukeMotion(val reduced: Boolean, val effects: Boolean)
val LocalLukeMotion = staticCompositionLocalOf { LukeMotion(false, true) }

@Composable
fun rememberMotionPolicy(p: AppPreferences): LukeMotion {
    val context = LocalContext.current
    fun systemOff() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var disabled by remember { mutableStateOf(systemOff()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { disabled = systemOff() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return LukeMotion(p.reduceMotion || disabled, p.effects && !p.reduceMotion && !disabled)
}

/** A single local frame clock. Read its value from draw/graphicsLayer, never a whole screen composition. */
@Composable
fun rememberSceneTime(active: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val effects = LocalLukeMotion.current.effects
    LaunchedEffect(active, effects, lifecycle) {
        if (active && effects) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) {
                withFrameNanos { frame ->
                    if (previous != 0L) seconds.floatValue += ((frame - previous) / 1_000_000_000f).coerceIn(0f, .05f)
                    previous = frame
                }
            }
        }
    }
    return seconds
}

fun Modifier.lukePress(source: MutableInteractionSource): Modifier = composed {
    val down by source.collectIsPressedAsState()
    val reduced = LocalLukeMotion.current.reduced
    val scale = animateFloatAsState(if (down && !reduced) .97f else 1f,
        if (reduced) snap() else spring(dampingRatio = .88f, stiffness = 650f), label = "press")
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
