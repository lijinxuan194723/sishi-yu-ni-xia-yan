package cn.sishiyuni.core.data

import cn.sishiyuni.core.model.validDate
import kotlinx.serialization.json.*

/** Validate a complete patch before entering a persistent transaction. */
object PreferenceRules {
    fun isSharingKey(key: String): Boolean = key.startsWith("sharing-") && key.removePrefix("sharing-") in PreferencesStore.sharingKeys
    fun isKnown(key: String): Boolean = key in PreferencesStore.textKeys || key in PreferencesStore.flagKeys ||
        isSharingKey(key) || key in setOf("scale", "chatSize", "latitude", "longitude", "photoIndex")

    fun text(key: String, value: String): String {
        require(key in PreferencesStore.textKeys) { "未知的文字设置" }
        val normalized = if (key == "name") value.trim() else value
        val limit = when (key) {
            "name" -> 12
            "since", "birthday" -> 10
            "season", "period", "bubbleMine", "bubbleLuke", "weatherProvider" -> 80
            "modelName", "fallbackModel", "activeSession" -> 200
            "modelUrl", "fallbackUrl" -> 2048
            "weatherCity" -> 120
            "songPreference", "bookPreference" -> 5000
            else -> 400000 // Existing embedded avatars remain importable.
        }
        require(normalized.length <= limit) { "设置内容过长" }
        if (key == "name") require(normalized.isNotBlank() && '\n' !in normalized && '\r' !in normalized) { "称呼请使用 1–12 个字符" }
        if (key == "since" || key == "birthday" && normalized.isNotEmpty()) require(validDate(normalized)) { "日期无效，请使用 YYYY-MM-DD" }
        if (key in setOf("modelName", "fallbackModel", "modelUrl", "fallbackUrl")) require('\n' !in normalized && '\r' !in normalized) { "连接设置不能包含换行" }
        return if (key == "weatherProvider") "open-meteo" else normalized
    }

    fun size(key: String, value: Float): Float {
        require(key in setOf("scale", "chatSize") && value.isFinite()) { "字号设置无效" }
        return if (key == "scale") value.coerceIn(.75f, 1.6f) else value.coerceIn(10f, 32f)
    }

    fun normalize(value: JsonObject): JsonObject = buildJsonObject {
        for ((key, raw) in value) {
            if (!isKnown(key)) continue
            val primitive = raw as? JsonPrimitive
            require(primitive != null && primitive != JsonNull) { "设置 $key 的格式无效" }
            when {
                key in PreferencesStore.textKeys -> {
                    require(primitive.isString) { "设置 $key 应为文字" }
                    put(key, text(key, primitive.content))
                }
                key in PreferencesStore.flagKeys || isSharingKey(key) -> {
                    require(!primitive.isString) { "设置 $key 应为开关" }
                    put(key, requireNotNull(primitive.booleanOrNull) { "设置 $key 应为开关" })
                }
                key in setOf("scale", "chatSize") -> put(key, size(key, requireNotNull(primitive.floatOrNull) { "字号设置无效" }))
                key in setOf("latitude", "longitude") -> {
                    val number = requireNotNull(primitive.doubleOrNull) { "天气坐标无效" }
                    val bound = if (key == "latitude") 90.0 else 180.0
                    require(number.isFinite() && number in -bound..bound) { "天气坐标超出范围" }
                    put(key, number)
                }
                key == "photoIndex" -> put(key, requireNotNull(primitive.intOrNull) { "照片编号无效" }.coerceIn(0, 99))
            }
        }
    }
}
