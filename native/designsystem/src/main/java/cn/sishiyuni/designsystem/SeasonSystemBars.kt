package cn.sishiyuni.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

private fun Context.findActivity(): Activity? {
    var candidate: Context = this
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate
        val next = candidate.baseContext
        if (next === candidate) break
        candidate = next
    }
    return candidate as? Activity
}

/** Resolve a dialog's own window before falling back to the activity window. */
fun nativeWindow(view: View): Window? {
    var node: Any? = view
    while (node != null) {
        if (node is DialogWindowProvider) return node.window
        node = when (node) { is View -> node.parent; is ViewParent -> node.parent; else -> null }
    }
    return view.context.findActivity()?.window
}

/** Platform updates occur at a logical theme change or icon contrast crossing, not every frame. */
@Composable
fun SeasonSystemBars() {
    val view = LocalView.current
    val window = remember(view) { nativeWindow(view) } ?: return
    val activityWindow = remember(view) { view.context.findActivity()?.window }
    val preferences = LocalAppPreferences.current
    val now = LocalAppTime.current
    val season = preferences.resolvedSeason(now)
    val night = preferences.isNight(now)
    // The window remains opaque even before the next Compose buffer covers resized
    // insets. This is the target palette, not a Binder/window update per animated color.
    val backing = remember(season, night) { seasonColors(season, night).paper.toArgb() }
    val dim = if (window !== activityWindow && window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        window.attributes.dimAmount.coerceIn(0f, 1f) else 0f
    val behindIcons = Color.Black.copy(alpha = dim).compositeOver(LocalSeason.current.paper)
    val darkIcons = contrastRatio(Color.Black, behindIcons) >= contrastRatio(Color.White, behindIcons)
    DisposableEffect(window, view, darkIcons, backing) {
        @Suppress("DEPRECATION")
        fun applyStyle() {
            // Never give a Compose Dialog a rectangular background: its rounded,
            // transparent margins must still reveal the dimmed activity underneath.
            if (window === activityWindow) window.setBackgroundDrawable(ColorDrawable(backing))
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
        applyStyle()
        val pending = Runnable { applyStyle() }
        view.post(pending)
        onDispose { view.removeCallbacks(pending) }
        // Do not restore an outgoing screen's stale style over its successor.
    }
}
