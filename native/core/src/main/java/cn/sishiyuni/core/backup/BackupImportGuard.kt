package cn.sishiyuni.core.backup

import cn.sishiyuni.core.data.LukeDao

object BackupImportGuard {
    /** Call inside the same Room transaction as import writes. Unknown record kinds fail closed. */
    suspend fun requireEmpty(dao: LukeDao) {
        val bootstrapSessions = dao.allSessions().all {
            it.id == "legacy" && it.title == "新的悄悄话" && it.draft.isEmpty() && !it.archived
        }
        val cacheOnly = dao.allRecords().all { it.kind in setOf("weather", "holiday", "holiday-source", "holiday-attempt", "migration") }
        check(bootstrapSessions && cacheOnly && dao.messageCount() == 0 && dao.memoCount() == 0 &&
            dao.allPlans().isEmpty() && dao.allFolders().isEmpty() && dao.allSubjects().isEmpty() && dao.allTimers().isEmpty() &&
            dao.allFocusLogs().isEmpty() && dao.allFacts().isEmpty() && dao.allChapters().isEmpty() && dao.allSkills().isEmpty()) {
            "本机已有原生数据、草稿或计时设置，本次未覆盖。请先导出备份。"
        }
    }
}
