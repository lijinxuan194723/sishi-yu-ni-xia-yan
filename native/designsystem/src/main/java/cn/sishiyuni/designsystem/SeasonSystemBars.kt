package cn.sishiyuni.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

/** Small platform update only when icon contrast crosses a threshold, not every animation frame. */
@Composable
fun SeasonSystemBars() {
    val view = LocalView.current
    val window = remember(view) { nativeWindow(view) } ?: return
    val activityWindow = remember(view) { view.context.findActivity()?.window }
    val dim = if (window !== activityWindow && window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        window.attributes.dimAmount.coerceIn(0f, 1f) else 0f
    val behindIcons = Color.Black.copy(alpha = dim).compositeOver(LocalSeason.current.paper)
    val darkIcons = contrastRatio(Color.Black, behindIcons) >= contrastRatio(Color.White, behindIcons)
    DisposableEffect(window, view, darkIcons) {
        @Suppress("DEPRECATION")
        fun applyStyle() {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                // The app paints a seasonal gradient under the controls. A second system
                // scrim otherwise becomes an opaque white block in a manual night theme.
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
        view.post(pending) // Apply after Compose Dialog finishes updating its window parameters.
        onDispose { view.removeCallbacks(pending) }
        // Do not restore a stale style from an outgoing animated screen over its successor.
    }
}
