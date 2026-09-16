package cn.sishiyuni.feature.chat

import androidx.lifecycle.viewModelScope
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(graph: AppGraph) : CoreViewModel(graph) {
    val sessions = graph.dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val active = graph.prefs.state.map { it.activeSession }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, graph.prefs.state.value.activeSession)
    val messages = active.flatMapLatest { graph.dao.messages(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = graph.dao.favoriteMessages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val replying = graph.chat.replying
    val replyError = graph.chat.error
    val drafts = graph.drafts.state
    val draftError = graph.drafts.error
    val busy = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val matches = query.mapLatest { q ->
        if (q.isBlank()) emptyList() else { delay(250); graph.dao.searchMessages(q.trim()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val commandGate = Mutex()
    init { task { active.collectLatest { graph.drafts.load(it) } } }
    fun editDraft(text: String) {
        try { graph.drafts.edit(active.value, text) }
        catch (e: IllegalArgumentException) { error.value = e.message }
        catch (e: IllegalStateException) { error.value = e.message }
    }
    fun flushDraft() { graph.drafts.flushInBackground(active.value) }
    private fun command(block: suspend () -> Unit) = viewModelScope.launch {
        if (!commandGate.tryLock()) return@launch
        busy.value = true
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作失败，已保留原有内容" }
        finally { busy.value = false; commandGate.unlock() }
    }
    fun send() = command { val id = active.value; graph.drafts.submit(id) { graph.chat.send(id, it) } }
    fun stop() = command { graph.chat.cancel() }
    fun create() = command { graph.drafts.flush(active.value); graph.chat.newSession() }
    fun switch(id: String) = command { graph.drafts.flush(active.value); graph.chat.switchSession(id) }
    fun rename(id: String, title: String, done: () -> Unit) = command { graph.chat.rename(id, title); done() }
    fun archive(id: String, archived: Boolean) = command { graph.drafts.flush(active.value); graph.chat.archive(id, archived) }
    fun star(id: String) = task { graph.dao.starMessage(id) }
    fun editMessage(id: String, text: String, done: () -> Unit) = command { graph.chat.edit(id, text); done() }
    fun dismissReplyError() { graph.chat.error.value = null }
    override fun onCleared() { graph.drafts.flushInBackground(active.value); super.onCleared() }
}
