package cn.sishiyuni.core.journal

import cn.sishiyuni.core.data.MemoEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A persisted draft is distinct from the accepted memo revision. */
data class MemoDraft(val id: String, val title: String, val body: String, val mood: String, val baseRevision: Long)
data class MemoEditorState(
    val id: String, val title: String, val body: String, val mood: String, val revision: Long,
    val sequence: Long = 0, val dirty: Boolean = false, val saving: Boolean = false,
    val error: String? = null, val conflict: Boolean = false,
    val closing: Boolean = false, val closed: Boolean = false,
) {
    fun draft() = MemoDraft(id, title, body, mood, revision)
}
class MemoRevisionConflict : IllegalStateException("这篇手记已在其他位置更新，当前草稿已保留；可以另存为新笔记")

/** Saves accepted input in application scope; the view never owns a database write. */
class MemoEditorSession(
    memo: MemoEntity,
    restored: MemoDraft?,
    private val persist: suspend (MemoDraft) -> Long,
    scope: CoroutineScope,
    private val delayMs: Long = 350,
) {
    private val mutable = MutableStateFlow(MemoEditorState(memo.id, restored?.title ?: memo.title,
        restored?.body ?: memo.body, restored?.mood ?: memo.mood, restored?.baseRevision ?: memo.revision,
        dirty = restored != null))
    val state: StateFlow<MemoEditorState> = mutable.asStateFlow()
    private val gate = Mutex()
    private val closingGate = Mutex()
    private val inputGate = Any()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        for (signal in wake) {
            delay(delayMs)
            while (wake.tryReceive().isSuccess) { /* coalesce input, never truncate stored text */ }
            flush()
        }
    }

    fun edit(title: String = state.value.title, body: String = state.value.body, mood: String = state.value.mood): Boolean = synchronized(inputGate) {
        // A stale UI handle must never accept text after the repository has detached it.
        // Freezing before close.flush also removes the last-write/dispose race window.
        if (state.value.closing || state.value.closed) return@synchronized false
        if (title.length > 300 || body.length > 100000 || mood.length > 50) {
            mutable.update { it.copy(error = "内容超过长度上限，原草稿未截断") }
            return@synchronized false
        }
        mutable.update { old ->
            if (old.title == title && old.body == body && old.mood == mood) old
            else old.copy(title = title, body = body, mood = mood, sequence = old.sequence + 1,
                dirty = true, error = if (old.conflict) old.error else null)
        }
        if (state.value.dirty && !state.value.conflict) wake.trySend(Unit)
        true
    }

    suspend fun flush(): Boolean = gate.withLock {
        if (state.value.closed) return@withLock !state.value.dirty
        while (state.value.dirty) {
            val snapshot = state.value
            mutable.update { it.copy(saving = true) }
            try {
                val revision = persist(snapshot.draft())
                mutable.update { latest -> latest.copy(revision = revision,
                    dirty = latest.sequence != snapshot.sequence, saving = false, error = null, conflict = false) }
            } catch (e: CancellationException) {
                mutable.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(saving = false, dirty = true,
                    error = e.message ?: "保存失败，草稿仍在；请重试或导出", conflict = e is MemoRevisionConflict) }
                return@withLock false
            }
        }
        true
    }

    /** Stop accepting input BEFORE the final flush. Failed/cancelled closes stay editable. */
    suspend fun close(): Boolean = closingGate.withLock {
        val alreadyClosed = synchronized(inputGate) {
            if (state.value.closed) true else {
                mutable.update { it.copy(closing = true) }
                false
            }
        }
        if (alreadyClosed) return@withLock !state.value.dirty
        var success = false
        try {
            success = flush()
            if (success) dispose()
            success
        } finally {
            if (!success) synchronized(inputGate) {
                if (!state.value.closed) mutable.update { it.copy(closing = false) }
            }
        }
    }

    /** Force-stop is for owner teardown; normal navigation must call close(). */
    fun dispose() {
        synchronized(inputGate) { mutable.update { it.copy(closing = false, closed = true) } }
        wake.close()
        worker.cancel()
    }
}
