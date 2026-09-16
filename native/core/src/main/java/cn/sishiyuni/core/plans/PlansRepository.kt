package cn.sishiyuni.core.plans

import androidx.room.withTransaction
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.serialization.json.*

/** All edits read current flags inside a Room transaction, never re-upsert a stale row. */
class PlansRepository(private val db: LukeDatabase) {
    private val dao = db.dao()
    val plans = dao.plans()
    val dates = dao.records("anniversary")
    private fun text(value: String): String = value.trim().also { require(it.length in 1..150) { "计划内容为 1–150 个字" } }
    suspend fun save(date: String, value: String, expected: PlanEntity? = null): String = db.withTransaction {
        require(CalendarModel.parse(date) != null) { "日期无效" }
        val body = text(value)
        if (expected == null) {
            val item = PlanEntity(newId(), date, body); dao.putPlan(item); item.id
        } else {
            val current = dao.allPlans().firstOrNull { it.id == expected.id } ?: error("这项计划已删除，输入内容仍保留")
            check(current.text == expected.text && current.date == expected.date) { "计划内容已在其他位置更新，请重新确认" }
            dao.putPlan(current.copy(date = date, text = body)); current.id
        }
    }
    suspend fun done(id: String, value: Boolean) = db.withTransaction {
        val current = dao.allPlans().firstOrNull { it.id == id } ?: error("计划不存在")
        dao.putPlan(current.copy(done = value))
    }
    suspend fun important(id: String, value: Boolean) = db.withTransaction {
        val current = dao.allPlans().firstOrNull { it.id == id } ?: error("计划不存在")
        dao.putPlan(current.copy(important = value))
    }
    suspend fun delete(expected: PlanEntity) = db.withTransaction {
        val current = dao.allPlans().firstOrNull { it.id == expected.id } ?: return@withTransaction
        check(current == expected) { "计划已经更新，请重新查看后删除" }
        dao.putRecord(RecordEntity("deleted-plan", current.id, buildJsonObject {
            put("id", current.id); put("date", current.date); put("text", current.text)
            put("done", current.done); put("important", current.important); put("raw", current.raw)
        }.toString()))
        dao.deletePlan(current.id)
    }
    suspend fun undoDelete(id: String) = db.withTransaction {
        require(dao.allPlans().none { it.id == id }) { "同编号计划已存在，没有覆盖" }
        val record = dao.record("deleted-plan", id) ?: error("没有可恢复的计划")
        val p = obj(record.payload)
        require(CalendarModel.parse(p.str("date")) != null)
        dao.putPlan(PlanEntity(id, p.str("date"), text(p.str("text")), p.flag("done"), p.flag("important"), p.str("raw", "{}")))
        dao.deleteRecord("deleted-plan", id)
    }
    suspend fun anniversary(title: String, date: String, expected: RecordEntity? = null): String = db.withTransaction {
        val name = title.trim(); require(name.length in 1..40 && CalendarModel.parse(date) != null) { "请填写有效的名称和日期" }
        if (expected != null) check(dao.record("anniversary", expected.id) == expected) { "这个约定已更新，请重新确认" }
        val id = expected?.id ?: newId()
        val old = expected?.let { obj(it.payload) }.orEmpty()
        dao.putRecord(RecordEntity("anniversary", id, JsonObject(old + mapOf("id" to JsonPrimitive(id), "title" to JsonPrimitive(name), "date" to JsonPrimitive(date))).toString()))
        id
    }
    suspend fun deleteAnniversary(expected: RecordEntity) = db.withTransaction {
        check(dao.record("anniversary", expected.id) == expected) { "这个约定已更新，请重新确认" }
        dao.deleteRecord("anniversary", expected.id)
    }
}
