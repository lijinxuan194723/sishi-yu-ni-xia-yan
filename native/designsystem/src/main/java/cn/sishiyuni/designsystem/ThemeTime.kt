package cn.sishiyuni.designsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.LocalDateTime

val LocalAppTime = staticCompositionLocalOf { LocalDateTime.now() }

/** One foreground observer; no per-second timer and no receiver left behind on stop. */
@Composable
fun rememberAppTime(): State<LocalDateTime> {
    val context = LocalContext.current.applicationContext
    val updates = remember(context) {
        callbackFlow {
            fun update() { trySend(LocalDateTime.now().withSecond(0).withNano(0)) }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { update() }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            update() // Register first, then take a snapshot so no clock-change event is lost.
            awaitClose { context.unregisterReceiver(receiver) }
        }.conflate()
    }
    return rememberForegroundTime(updates)
}

@Composable
fun rememberForegroundTime(updates: Flow<LocalDateTime>): State<LocalDateTime> =
    updates.collectAsStateWithLifecycle(initialValue = LocalDateTime.now(), minActiveState = Lifecycle.State.RESUMED)
