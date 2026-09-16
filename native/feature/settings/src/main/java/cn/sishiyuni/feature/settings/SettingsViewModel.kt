package cn.sishiyuni.feature.settings

import android.net.Uri
import androidx.work.WorkManager
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import cn.sishiyuni.core.model.newId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class SettingsViewModel(private val app: AppGraph) : CoreViewModel(app) {
    val preferences = app.prefs.state
    val readError = app.prefs.error
    val ready = app.prefs.ready
    val completed = MutableStateFlow<String?>(null)
    val pending = MutableStateFlow(0)
    val holidayBusy = MutableStateFlow(false)
    val holidayStatus = app.holidays.status
    val holidayYears = app.holidays.years
    private val writes = Mutex()
    private val avatarRequests = mutableMapOf<String, Long>()

    // Settings writes belong to the application, not the lifetime of a leaving screen.
    private fun write(id: String, block: suspend () -> Unit) {
        pending.update { it + 1 }
        app.scope.launch {
            try {
                writes.withLock { block(); completed.value = id }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error.value = failure.message ?: "保存失败，请重试" }
            finally { pending.update { (it - 1).coerceAtLeast(0) } }
        }
    }
    fun change(patch: JsonObject, id: String = newId()) {
        clearError()
        write(id) {
            app.prefs.restore(patch)
            val enabled = (patch["autoMemory"] as? JsonPrimitive)?.booleanOrNull
            if (enabled != null) {
                withTimeout(10000) { preferences.first { it.autoMemory == enabled } }
                if (enabled) app.memory.enqueue(preferences.value.activeSession)
                else WorkManager.getInstance(app.context).cancelAllWorkByTag("native-memory")
            }
        }
    }
    fun text(key: String, value: String) = change(buildJsonObject { put(key, value) })
    fun flag(key: String, value: Boolean) = change(buildJsonObject { put(key, value) })
    fun size(key: String, value: Float) = change(buildJsonObject { put(key, value) })
    fun resetTypography() = change(buildJsonObject { put("scale", .95f); put("chatSize", 13f) })
    fun saveConnection(id: String, url: String, model: String, key: String, fallback: Boolean) {
        clearError()
        write(id) { app.saveConnection(url.trim(), model.trim(), key.trim(), fallback) }
    }
    fun clearConnection(id: String, fallback: Boolean) {
        clearError(); write(id) { app.clearConnection(fallback) }
    }
    fun avatar(key: String, uri: Uri) {
        require(key in setOf("avatarMine", "avatarLuke"))
        val ticket = (avatarRequests[key] ?: 0L) + 1
        avatarRequests[key] = ticket
        clearError()
        app.scope.launch {
            try {
                val pixels = app.images.avatar(uri)
                writes.withLock { if (avatarRequests[key] == ticket) app.prefs.text(key, pixels) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (avatarRequests[key] == ticket) error.value = failure.message ?: "头像读取失败，原头像已保留" }
        }
    }
    fun refreshCalendar(year: Int) {
        if (holidayBusy.value) return
        holidayBusy.value = true; clearError()
        task { try { app.holidays.load(year, force = true) } finally { holidayBusy.value = false } }
    }
}
