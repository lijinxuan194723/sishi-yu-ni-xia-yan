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
) {
    fun draft() = MemoDraft(id, title, body, mood, revision)
}
class MemoRevisionConflict : IllegalStateException("这篇手记已在其他位置更新，当前草稿已保留；可以另存为新笔记")

/**
 * Runs in the application scope, not a page scope. Editing never cancels a database
 * write. The conflated worker samples the newest text; a late commit only acknowledges
 * the sequence it actually wrote. A navigation caller waits for flush() before closing.
 */
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
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        for (signal in wake) {
            delay(delayMs)
            while (wake.tryReceive().isSuccess) { /* coalesce typing, never the stored text */ }
            flush()
        }
    }
    fun edit(title: String = state.value.title, body: String = state.value.body, mood: String = state.value.mood): Boolean {
        if (title.length > 300 || body.length > 100000 || mood.length > 50) {
            mutable.update { it.copy(error = "内容超过长度上限，原草稿未截断") }; return false
        }
        mutable.update { old ->
            if (old.title == title && old.body == body && old.mood == mood) old
            else old.copy(title = title, body = body, mood = mood, sequence = old.sequence + 1,
                dirty = true, error = if (old.conflict) old.error else null)
        }
        if (state.value.dirty && !state.value.conflict) wake.trySend(Unit)
        return true
    }
    suspend fun flush(): Boolean = gate.withLock {
        // Loop if more typing arrived during the write; no successful close can omit it.
        while (state.value.dirty) {
            val snapshot = state.value
            mutable.update { it.copy(saving = true) }
            try {
                val revision = persist(snapshot.draft())
                mutable.update { latest -> latest.copy(revision = revision,
                    dirty = latest.sequence != snapshot.sequence, saving = false, error = null, conflict = false) }
            } catch (e: CancellationException) {
                mutable.update { it.copy(saving = false) }; throw e
            } catch (e: Exception) {
                mutable.update { it.copy(saving = false, dirty = true,
                    error = e.message ?: "保存失败，草稿仍在；请重试或导出", conflict = e is MemoRevisionConflict) }
                return@withLock false
            }
        }
        true
    }
    /** Call only after a successful close; failed drafts must remain accessible. */
    fun dispose() { wake.close(); worker.cancel() }
}
