package cn.sishiyuni.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.ViewTreeObserver
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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

fun nativeWindow(view: View): Window? {
    var node: Any? = view
    while (node != null) {
        if (node is DialogWindowProvider) return node.window
        node = when (node) { is View -> node.parent; is ViewParent -> node.parent; else -> null }
    }
    return view.context.findActivity()?.window
}

/** Logical palette/focus/control changes update Android; animated frames stay in Compose. */
@Composable
fun SeasonSystemBars() {
    val view = LocalView.current
    val window = remember(view) { nativeWindow(view) } ?: return
    val activityWindow = remember(view) { view.context.findActivity()?.window }
    val preferences = LocalAppPreferences.current
    val now = LocalAppTime.current
    val season = preferences.resolvedSeason(now)
    val night = preferences.isNight(now)
    val backing = remember(season, night) { seasonColors(season, night).paper.toArgb() }
    val dim = if (window !== activityWindow && window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0)
        window.attributes.dimAmount.coerceIn(0f, 1f) else 0f
    val behindIcons = Color.Black.copy(alpha = dim).compositeOver(LocalSeason.current.paper)
    val darkIcons = contrastRatio(Color.Black, behindIcons) >= contrastRatio(Color.White, behindIcons)
    DisposableEffect(window, view, darkIcons, backing) {
        val controller = WindowCompat.getInsetsController(window, view)
        @Suppress("DEPRECATION")
        fun applyStyle() {
            // A dialog keeps its transparent rounded margins, unlike the activity's
            // opaque safety backing beneath the transparent system-bar surfaces.
            if (window === activityWindow) window.setBackgroundDrawable(ColorDrawable(backing))
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 28) window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            controller.isAppearanceLightStatusBars = darkIcons
            controller.isAppearanceLightNavigationBars = darkIcons
        }
        val pending = Runnable { applyStyle() }
        fun refresh() { applyStyle(); view.removeCallbacks(pending); view.post(pending) }
        // Initial composition can precede actual ownership of the system bars. The
        // activity also regains that ownership when a dialog or IME window leaves.
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) refresh() }
        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(focus)
        val attached = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { refresh() }
            override fun onViewDetachedFromWindow(v: View) { v.removeCallbacks(pending) }
        }
        view.addOnAttachStateChangeListener(attached)
        val controllable = WindowInsetsControllerCompat.OnControllableInsetsChangedListener { _, types ->
            if (types and WindowInsetsCompat.Type.systemBars() != 0) refresh()
        }
        controller.addOnControllableInsetsChangedListener(controllable)
        refresh()
        onDispose {
            view.removeCallbacks(pending)
            view.removeOnAttachStateChangeListener(attached)
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
            controller.removeOnControllableInsetsChangedListener(controllable)
            // Never restore an outgoing screen's stale appearance over its successor.
        }
    }
}
