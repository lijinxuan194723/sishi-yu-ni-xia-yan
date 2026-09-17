package cn.sishiyuni.core.backup

import cn.sishiyuni.core.data.SubjectEntity
import cn.sishiyuni.core.model.*
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.Locale

object LegacySubjects {
    fun decode(value: JsonElement?): List<SubjectEntity> {
        val rows = when (value) {
            null -> JsonArray(emptyList())
            is JsonArray -> value
            is JsonObject -> {
                if (value.containsKey("items")) {
                    require(value.num("version") == 1L) { "科目设置版本不受支持" }
                    value["items"] as? JsonArray ?: error("科目 items 必须是列表")
                } else value["subjects"] as? JsonArray ?: error("科目列表缺失，未按空列表导入")
            }
            else -> error("科目设置格式无效")
        }
        require(rows.size <= 500) { "科目数量超限" }
        val result = rows.map { element ->
            val original = if (element is JsonPrimitive && element.isString) element.content
                else (element as? JsonObject)?.str("name") ?: error("科目名称无效")
            val clean = Normalizer.normalize(original, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ")
            require(clean.length in 1..30) { "科目名称需要 1–30 个字" }
            val row = element as? JsonObject
            val id = row?.str("id", clean.sha256()) ?: clean.sha256()
            require(id.isNotBlank() && id.length <= 200) { "科目编号无效" }
            SubjectEntity(id, clean, row?.flag("deleted") ?: false)
        }
        require(result.map { it.id }.distinct().size == result.size) { "科目编号重复，未覆盖原记录" }
        val visible = result.filterNot { it.deleted }.map { it.name.lowercase(Locale.ROOT) }
        require(visible.distinct().size == visible.size) { "存在重名科目，请在原应用整理后导出" }
        return result
    }
}
