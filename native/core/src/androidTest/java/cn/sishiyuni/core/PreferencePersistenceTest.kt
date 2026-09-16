package cn.sishiyuni.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import cn.sishiyuni.core.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.util.UUID

/** Uses the actual Android preferences DataStore and a file, not an in-memory database. */
class PreferencePersistenceTest {
    private lateinit var file: File
    private lateinit var job: CompletableJob
    private lateinit var data: DataStore<Preferences>
    private lateinit var prefs: PreferencesStore
    @Before fun setUp() = runBlocking {
        file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        open()
    }
    private suspend fun open() {
        job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        prefs = PreferencesStore(data, scope)
        withTimeout(10000) { prefs.ready.first { it } }
    }
    @After fun close() = runBlocking { job.cancelAndJoin(); file.delete(); Unit }
    private suspend fun await(test: (AppPreferences) -> Boolean) = withTimeout(10000) { prefs.state.first(test) }
    @Test fun defaultsAreSmallAndBothTypographySettingsAreIndependent() = runBlocking {
        assertEquals(.95f, prefs.state.value.scale); assertEquals(13f, prefs.state.value.chatSize)
        prefs.size("chatSize", 22f)
        assertEquals(.95f, await { it.chatSize == 22f }.scale)
    }
    @Test fun settingsSurviveClosingAndReopeningTheRealFile() = runBlocking {
        prefs.restore(buildJsonObject { put("name", "小鹿"); put("scale", .85f); put("chatSize", 17f); put("sharing-notes", false) })
        job.cancelAndJoin(); open()
        assertEquals("小鹿", prefs.state.value.name)
        assertEquals(.85f, prefs.state.value.scale); assertEquals(17f, prefs.state.value.chatSize)
        assertEquals(false, prefs.state.value.sharing["notes"])
    }
    @Test fun invalidPatchCannotPartiallyChangeExistingSettings() = runBlocking {
        prefs.text("name", "原称呼")
        val before = prefs.snapshot()
        try { prefs.restore(buildJsonObject { put("name", "新称呼"); put("latitude", 99) }); fail("Invalid patch was accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, prefs.snapshot())
    }
    @Test fun unprefixedSharingCannotCreateAnUnusedStoredFlag() = runBlocking {
        try { prefs.flag("notes", false); fail("Missing prefix accepted") } catch (_: IllegalArgumentException) { }
        assertFalse(data.data.first().contains(booleanPreferencesKey("notes")))
        prefs.flag("sharing-notes", false)
        assertEquals(false, await { it.sharing["notes"] == false }.sharing["notes"])
    }
    @Test fun restoreDefaultsDoesNotResetPhotosIdentityOrSharing() = runBlocking {
        prefs.restore(buildJsonObject { put("name", "小鹿"); put("avatarMine", "images/companions/cat.webp"); put("scale", 1.5f); put("chatSize", 30); put("sharing-plans", false) })
        prefs.resetTypography()
        val actual = await { it.scale == .95f && it.chatSize == 13f && it.name == "小鹿" }
        assertEquals("images/companions/cat.webp", actual.avatarMine)
        assertEquals(false, actual.sharing["plans"])
    }
    @Test fun concurrentIndependentEditsDoNotLoseOtherFields() = runBlocking {
        coroutineScope {
            launch { prefs.text("name", "小鹿") }
            launch { prefs.size("scale", .8f) }
            launch { prefs.size("chatSize", 12f) }
            launch { prefs.flag("effects", false) }
            launch { prefs.flag("sharing-notes", false) }
        }
        val actual = await { it.name == "小鹿" && it.scale == .8f && it.chatSize == 12f && !it.effects && it.sharing["notes"] == false }
        assertEquals("2023-07-08", actual.since)
    }
    @Test fun writeFailureIsReportedAndTheSameEditCanBeRetried() = runBlocking {
        var failNext = true
        val wrapper = object : DataStore<Preferences> {
            override val data = this@PreferencePersistenceTest.data.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                if (failNext) { failNext = false; throw IOException("Injected write failure") }
                return this@PreferencePersistenceTest.data.updateData(transform)
            }
        }
        val tested = PreferencesStore(wrapper, CoroutineScope(job + Dispatchers.IO))
        try { tested.text("name", "新称呼"); fail("Failed write was hidden") } catch (_: IOException) { }
        assertFalse(prefs.snapshot().containsKey("name"))
        tested.text("name", "新称呼")
        assertEquals("新称呼", await { it.name == "新称呼" }.name)
    }
    @Test fun exportContainsOnlyRecognizedPreferences() = runBlocking {
        data.edit { it[stringPreferencesKey("unexpected-private-field")] = "fixture" }
        prefs.text("name", "小鹿")
        assertFalse(prefs.snapshot().containsKey("unexpected-private-field"))
        assertEquals("小鹿", prefs.snapshot().getValue("name").jsonPrimitive.content)
    }
}
