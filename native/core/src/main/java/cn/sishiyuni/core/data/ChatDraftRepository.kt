package cn.sishiyuni.core.data

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class ChatDraft(val text: String, val revision: Long = 0)

/** Application-owned drafts outlive feature ViewModels. All disk writes use the latest revision. */
class ChatDraftRepository(
    private val read: suspend (String) -> String?,
    private val write: suspend (String, String) -> Unit,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow<Map<String, ChatDraft>>(emptyMap())
    val state: StateFlow<Map<String, ChatDraft>> = mutable.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    private val gate = Mutex()
    private val pending = ConcurrentHashMap<String, Job>()

    suspend fun load(session: String) = gate.withLock {
        if (session !in mutable.value) {
            val saved = read(session) ?: return@withLock
            mutable.update { if (session in it) it else it + (session to ChatDraft(saved)) }
        }
    }
    fun edit(session: String, text: String) {
        require(text.length <= 20000) { "这条消息超过 20,000 字，请分开发送。" }
        check(session in mutable.value) { "对话尚未读取，请稍后再输入。" }
        mutable.update { old -> old + (session to ChatDraft(text, old.getValue(session).revision + 1)) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try { delay(350); gate.withLock { persistLatest(session) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = "草稿未能保存，文字仍保留在当前应用中：${e.message}" }
            finally { pending.remove(session, currentCoroutineContext()[Job]) }
        }
        pending.put(session, job)?.cancel()
        job.start()
    }
    private suspend fun persistLatest(session: String) {
        val latest = mutable.value[session] ?: return
        write(session, latest.text)
        error.value = null
    }
    suspend fun flush(session: String) {
        pending.remove(session)?.cancelAndJoin()
        gate.withLock { persistLatest(session) }
    }
    fun flushInBackground(session: String) = scope.launch {
        try { flush(session) } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = "草稿保存失败，未清空输入：${e.message}" }
    }
    suspend fun submit(session: String, send: suspend (String) -> Unit): Boolean {
        pending.remove(session)?.cancelAndJoin()
        return gate.withLock {
            val captured = mutable.value[session] ?: return@withLock false
            if (captured.text.isBlank()) return@withLock false
            send(captured.text)
            mutable.update { latest ->
                if (latest[session]?.revision == captured.revision) latest + (session to ChatDraft("", captured.revision + 1))
                else latest
            }
            persistLatest(session)
            true
        }
    }
}
