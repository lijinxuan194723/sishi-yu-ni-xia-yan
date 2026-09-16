package cn.sishiyuni.core

import cn.sishiyuni.core.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PreferenceRulesTest {
    private fun rejects(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }
    @Test fun sharingRequiresThePrefix() {
        for (key in PreferencesStore.sharingKeys) { assertTrue(PreferenceRules.isSharingKey("sharing-$key")); assertFalse(PreferenceRules.isSharingKey(key)) }
        assertFalse(PreferenceRules.isSharingKey("sharing-unknown"))
    }
    @Test fun unknownBackupFieldsAreNotImported() {
        assertEquals(JsonObject(emptyMap()), PreferenceRules.normalize(buildJsonObject { put("apiKey", "not-a-real-key"); put("weather", false); put("extra", JsonArray(emptyList())) }))
    }
    @Test fun invalidDatesAndEmptyNamesAreRejected() {
        for (date in listOf("2026-02-29", "2026-13-01", "2026-2-01", "")) rejects { PreferenceRules.text("since", date) }
        for (name in listOf(" ", "a\nb", "a".repeat(13))) rejects { PreferenceRules.text("name", name) }
    }
    @Test fun datesAndOptionalBirthdayRemainCompatible() {
        assertEquals("2024-02-29", PreferenceRules.text("since", "2024-02-29"))
        assertEquals("", PreferenceRules.text("birthday", ""))
        assertEquals("华生", PreferenceRules.text("name", " 华生 "))
    }
    @Test fun nestedKnownFieldsDoNotReachTheDataStore() {
        for (key in listOf("name", "glass", "scale", "latitude")) rejects { PreferenceRules.normalize(buildJsonObject { put(key, JsonObject(emptyMap())) }) }
    }
    @Test fun nullAndWrongTypesAreRejected() {
        rejects { PreferenceRules.normalize(buildJsonObject { put("name", 123) }) }
        rejects { PreferenceRules.normalize(buildJsonObject { put("glass", "true") }) }
        rejects { PreferenceRules.normalize(buildJsonObject { put("chatSize", JsonNull) }) }
    }
    @Test fun typographyRangesAndDefaultsAreConsistent() {
        val p = AppPreferences(); assertEquals(.95f, p.scale); assertEquals(13f, p.chatSize)
        assertEquals(.75f, PreferenceRules.size("scale", -1f)); assertEquals(1.6f, PreferenceRules.size("scale", 5f))
        assertEquals(10f, PreferenceRules.size("chatSize", 0f)); assertEquals(32f, PreferenceRules.size("chatSize", 100f))
    }
    @Test fun nonFiniteTypographyIsNeverPersisted() {
        for (v in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) rejects { PreferenceRules.size("scale", v) }
    }
    @Test fun coordinatesMustBeFiniteAndInsideBothBoundaries() {
        for ((key, value) in listOf("latitude" to "91", "longitude" to "-181", "latitude" to "NaN", "longitude" to "Infinity")) rejects { PreferenceRules.normalize(buildJsonObject { put(key, value) }) }
        val result = PreferenceRules.normalize(buildJsonObject { put("latitude", -90); put("longitude", 180) })
        assertEquals(-90.0, result.getValue("latitude").jsonPrimitive.double, 0.0)
    }
    @Test fun existingEmbeddedAvatarsAndIndependentSkinsSurvive() {
        val source = buildJsonObject { put("avatarMine", "data:image/png;base64,AAAA"); put("bubbleMine", "tea"); put("bubbleLuke", "ribbon") }
        assertEquals(source, PreferenceRules.normalize(source))
    }
    @Test fun modelConfigurationRejectsNewlinesAndExcessiveLengths() {
        rejects { PreferenceRules.text("modelName", "a\nb") }; rejects { PreferenceRules.text("modelUrl", "https://example.com\rheader") }
        rejects { PreferenceRules.text("modelName", "a".repeat(201)) }
    }
    @Test fun weatherProviderRemainsOpenMeteoAndPhotoIndexIsBounded() {
        val normalized = PreferenceRules.normalize(buildJsonObject { put("weatherProvider", "legacy-provider"); put("photoIndex", 300) })
        assertEquals("open-meteo", normalized.getValue("weatherProvider").jsonPrimitive.content)
        assertEquals(99, normalized.getValue("photoIndex").jsonPrimitive.int)
    }
}
