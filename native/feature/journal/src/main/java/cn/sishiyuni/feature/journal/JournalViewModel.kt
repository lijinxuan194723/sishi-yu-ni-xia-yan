package cn.sishiyuni.feature.journal

import android.net.Uri
import androidx.lifecycle.viewModelScope
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import cn.sishiyuni.core.data.FolderEntity
import cn.sishiyuni.core.data.MemoEntity
import cn.sishiyuni.core.journal.MemoEditorSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** The application-owned editor continues saving when a UI leaves composition. */
class JournalViewModel(graph: AppGraph) : CoreViewModel(graph) {
    val memos = graph.journal.memos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders = graph.journal.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val editor = MutableStateFlow<MemoEditorSession?>(null)
    val busy = MutableStateFlow(false)
    private val commands = Mutex()

    private fun command(block: suspend () -> Unit) = viewModelScope.launch {
        if (!commands.tryLock()) return@launch
        busy.value = true
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作未完成，内容仍保留" }
        finally { busy.value = false; commands.unlock() }
    }

    fun open(id: String) = command {
        val current = editor.value
        if (current?.state?.value?.id == id && !current.state.value.closed) return@command
        if (current != null) check(graph.journal.close(current.state.value.id)) { "请先保存当前手记" }
        editor.value = null
        editor.value = graph.journal.open(id)
    }
    fun create(done: (String) -> Unit) = command {
        val current = editor.value
        if (current != null) check(graph.journal.close(current.state.value.id)) { "请先保存当前手记" }
        editor.value = null
        val id = graph.journal.create()
        editor.value = graph.journal.open(id)
        done(id)
    }
    fun close(done: () -> Unit) = command {
        val current = editor.value
        if (current == null || graph.journal.close(current.state.value.id)) {
            editor.value = null
            done()
        } else error.value = current.state.value.error ?: "保存失败，请重试或导出正文"
    }
    fun retry() = command { editor.value?.flush() }
    fun copyDraft(done: (String) -> Unit) = command {
        val current = editor.value ?: return@command
        val id = graph.journal.copyDraft(current.state.value.id)
        // Keep the conflicting original editor and its recovery record intact.
        editor.value = graph.journal.open(id)
        done(id)
    }
    fun change(memo: MemoEntity, action: String, folderId: String? = null) = command {
        graph.journal.change(memo.id, action, folderId)
    }
    fun renameFolder(folder: FolderEntity?, name: String, done: () -> Unit) = command {
        graph.journal.folder(folder?.id, name)
        done()
    }
    fun deleteFolder(folder: FolderEntity, done: () -> Unit) = command {
        graph.journal.deleteFolder(folder.id)
        done()
    }
    fun purge(memo: MemoEntity, done: () -> Unit) = command {
        graph.journal.purge(memo.id)
        done()
    }
    fun export(uri: Uri, text: String) = command { graph.backup.exportText(uri, text) }
}
