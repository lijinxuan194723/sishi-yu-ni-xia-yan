package cn.sishiyuni.app

import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cn.sishiyuni.core.model.DisplayModeSpec
import cn.sishiyuni.core.model.RefreshRatePolicy

/** Requests a supported SAME-resolution mode only while resumed. No device-wide setting is changed. */
@Composable
internal fun RefreshRateEffect(activity: ComponentActivity, enabled: Boolean) {
    DisposableEffect(activity, enabled) {
        val manager = activity.getSystemService(DisplayManager::class.java)
        val original = activity.window.attributes.preferredDisplayModeId
        @Suppress("DEPRECATION")
        fun display(): Display? = if (Build.VERSION.SDK_INT >= 30) activity.display else activity.windowManager.defaultDisplay
        fun spec(mode: Display.Mode) = DisplayModeSpec(mode.modeId, mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
        fun update() {
            val current = display()
            val requested = if (enabled && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && current != null)
                RefreshRatePolicy.choose(spec(current.mode), current.supportedModes.map(::spec)) else original
            if (activity.window.attributes.preferredDisplayModeId != requested) {
                activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = requested }
            }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) { if (display()?.displayId == displayId) update() }
        }
        activity.lifecycle.addObserver(observer)
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        update()
        onDispose {
            activity.lifecycle.removeObserver(observer)
            manager.unregisterDisplayListener(listener)
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = original }
        }
    }
}
