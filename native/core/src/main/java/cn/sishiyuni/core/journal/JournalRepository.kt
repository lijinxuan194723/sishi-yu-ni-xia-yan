package cn.sishiyuni.core.journal

import androidx.room.withTransaction
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class JournalRepository(private val db: LukeDatabase, private val scope: CoroutineScope) {
    private val dao = db.dao()
    private val gate = Mutex()
    private val editors = mutableMapOf<String, MemoEditorSession>()
    val memos = dao.memos()
    val folders = dao.folders()
    suspend fun create(title: String = "", body: String = ""): String {
        require(title.length <= 300 && body.length <= 100000)
        val note = MemoEntity(newId(), title = title, body = body)
        dao.putMemo(note); return note.id
    }
    suspend fun open(id: String): MemoEditorSession = gate.withLock {
        editors[id]?.takeUnless { it.state.value.closed }?.let { return@withLock it }
        editors.remove(id)
        val memo = dao.memo(id) ?: error("手记不存在")
        require(memo.deletedAt == null) { "请先从回收站恢复这篇手记" }
        val raw = dao.record("journal-draft", id)
        val draft = raw?.let {
            val o = obj(it.payload)
            require(o.str("title").length <= 300 && o.str("body").length <= 100000 && o.str("mood").length <= 50)
            MemoDraft(id, o.str("title"), o.str("body"), o.str("mood"), o.num("baseRevision", -1))
        }
        MemoEditorSession(memo, draft, ::persist, scope).also { editors[id] = it }
    }
    private suspend fun persist(draft: MemoDraft): Long {
        dao.putRecord(RecordEntity("journal-draft", draft.id, buildJsonObject {
            put("title", draft.title); put("body", draft.body); put("mood", draft.mood); put("baseRevision", draft.baseRevision)
        }.toString()))
        return db.withTransaction {
            val current = dao.memo(draft.id) ?: throw MemoRevisionConflict()
            if (current.deletedAt != null || current.revision != draft.baseRevision) throw MemoRevisionConflict()
            val at = maxOf(System.currentTimeMillis(), current.createdAt, current.updatedAt)
            if (dao.editMemo(draft.id, draft.title, draft.body, draft.mood, at, draft.baseRevision) != 1) throw MemoRevisionConflict()
            dao.deleteRecord("journal-draft", draft.id)
            Math.addExact(draft.baseRevision, 1)
        }
    }
    suspend fun close(id: String): Boolean = gate.withLock {
        val editor = editors[id] ?: return@withLock true
        if (!editor.close()) return@withLock false
        editors.remove(id)
        true
    }
    suspend fun flushAll() {
        val items = gate.withLock { editors.values.toList() }
        items.forEach { it.flush() }
    }
    suspend fun copyDraft(id: String): String {
        val value = open(id).state.value
        return db.withTransaction {
            val copy = MemoEntity(newId(), title = value.title, body = value.body, mood = value.mood)
            dao.putMemo(copy)
            copy.id
        }
    }
    suspend fun change(id: String, action: String, folder: String? = null) = gate.withLock {
        require(action in setOf("star", "pin", "trash", "restore", "folder"))
        // Serialize detach and reopen. An existing editor rejects new input before
        // its revision is replaced; another open cannot receive that stale handle.
        val editor = editors[id]
        if (editor != null) check(editor.close()) { "请先处理这篇手记未保存的草稿" }
        editors.remove(id)
        db.withTransaction {
            val old = dao.memo(id) ?: error("手记不存在")
            if (action != "restore") require(old.deletedAt == null) { "手记已经移入回收站" }
            if (action == "folder" && folder != null) require(dao.allFolders().any { it.id == folder }) { "笔记本不存在" }
            val now = System.currentTimeMillis()
            val next = when (action) {
                "star" -> old.copy(starred = !old.starred)
                "pin" -> old.copy(pinnedAt = if (old.pinnedAt == null) now else null)
                "trash" -> old.copy(deletedAt = now)
                "restore" -> old.copy(deletedAt = null)
                else -> old.copy(folderId = folder)
            }
            dao.putMemo(next.copy(revision = Math.addExact(old.revision, 1)))
        }
    }
    suspend fun folder(id: String? = null, name: String): String = db.withTransaction {
        val text = name.trim(); require(text.length in 1..30) { "笔记本名称为 1–30 个字" }
        val all = dao.allFolders()
        require(all.none { it.id != id && it.name == text }) { "已存在同名笔记本" }
        if (id == null) require(all.size < 100) { "笔记本数量已到上限" }
        val old = if (id == null) null else all.firstOrNull { it.id == id } ?: error("笔记本不存在")
        val next = old?.copy(name = text) ?: FolderEntity(newId(), text)
        dao.putFolder(next); next.id
    }
    suspend fun deleteFolder(id: String) = db.withTransaction { dao.unfileMemos(id); dao.deleteFolder(id) }
    suspend fun purge(id: String) = gate.withLock {
        // Re-check deletion eligibility in the SAME transaction as DELETE. A note
        // restored concurrently must not disappear through an old purge confirmation.
        db.withTransaction {
            require(dao.memo(id)?.deletedAt != null) { "只能彻底删除回收站中的手记" }
            dao.purgeMemo(id)
            dao.deleteRecord("journal-draft", id)
        }
        editors.remove(id)?.dispose()
    }
}
