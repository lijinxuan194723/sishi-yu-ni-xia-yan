package cn.sishiyuni.core

import cn.sishiyuni.core.backup.BackupDecoder
import cn.sishiyuni.core.data.PreferenceRules
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BackupPreferencesTest {
    private fun legacy() = buildJsonObject {
        put("name", "华生"); put("since", "2023-07-08")
        for (key in listOf("messages", "tasks", "notes", "checks")) put(key, JsonArray(emptyList()))
    }
    private fun native(preferences: JsonObject) = buildJsonObject {
        put("format", "four-seasons-luke-native-backup"); put("version", 1)
        put("preferences", preferences)
        for (key in listOf("sessions", "messages", "plans", "memos", "folders", "facts", "chapters", "subjects", "focusLogs", "records", "skills", "timers")) put(key, JsonArray(emptyList()))
    }.toString()
    @Test fun validLegacyBackupStillParsesAndPreservesItsExactOriginal() {
        val text = legacy().toString(); val decoded = BackupDecoder.decode(text)
        assertEquals(text, decoded.source)
        assertEquals("华生", decoded.preferences.getValue("name").jsonPrimitive.content)
        assertEquals(.95f, decoded.preferences.getValue("scale").jsonPrimitive.float)
        assertEquals(13f, decoded.preferences.getValue("chatSize").jsonPrimitive.float)
    }
    @Test fun badOptionalDateIsRejectedBeforeAnImportPlanCanBeCreated() {
        val text = JsonObject(legacy() + ("birthday" to JsonPrimitive("2026-02-30"))).toString()
        assertThrows(IllegalArgumentException::class.java) { BackupDecoder.decode(text) }
    }
    @Test fun invalidNativeKnownFieldsAreRejectedDuringInspectionNotAfterDatabaseCommit() {
        val patches = listOf(
            buildJsonObject { put("glass", "true") },
            buildJsonObject { put("scale", "NaN") },
            buildJsonObject { put("chatSize", JsonObject(emptyMap())) },
            buildJsonObject { put("birthday", "wrong-date") }
        )
        for (patch in patches) assertThrows(IllegalArgumentException::class.java) { BackupDecoder.decode(native(patch)) }
    }
    @Test fun copyingAnInspectedPlanCannotIntroduceInvalidPreferences() {
        val original = BackupDecoder.decode(legacy().toString())
        assertThrows(IllegalArgumentException::class.java) { original.copy(preferences = buildJsonObject { put("name", "") }) }
        assertEquals("华生", original.preferences.getValue("name").jsonPrimitive.content)
    }
    @Test fun unknownFieldsRemainInOriginalButAreExcludedFromApplicationPatch() {
        val text = native(buildJsonObject { put("futureSetting", "preserved"); put("chatSize", 12) })
        val plan = BackupDecoder.decode(text)
        assertEquals(text, plan.source)
        assertFalse(PreferenceRules.normalize(plan.preferences).containsKey("futureSetting"))
        assertEquals(12f, PreferenceRules.normalize(plan.preferences).getValue("chatSize").jsonPrimitive.float)
    }
    @Test fun outOfRangeLegacyTypographyIsBoundedWithoutChangingTheBackupSource() {
        val text = buildJsonObject {
            put("format", "four-seasons-luke-full-backup"); put("version", 1)
            putJsonObject("storage") {
                put("luke-companion-v1", legacy().toString())
                put("luke-display-v206", "{\"scale\":5,\"chatSize\":2}")
            }
        }.toString()
        val plan = BackupDecoder.decode(text)
        assertEquals(text, plan.source)
        assertEquals(1.6f, plan.preferences.getValue("scale").jsonPrimitive.float)
        assertEquals(10f, plan.preferences.getValue("chatSize").jsonPrimitive.float)
    }
}
