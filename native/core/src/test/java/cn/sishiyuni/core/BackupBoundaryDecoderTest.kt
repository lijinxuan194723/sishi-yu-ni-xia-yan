package cn.sishiyuni.core

import cn.sishiyuni.core.backup.*
import cn.sishiyuni.core.data.TimerEntity
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.timer.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BackupBoundaryDecoderTest {
    private fun native(timer: JsonObject) = buildJsonObject {
        put("format", "four-seasons-luke-native-backup"); put("version", 1)
        put("preferences", buildJsonObject { put("name", "华生"); put("since", "2023-07-08") })
        for (name in listOf("sessions","messages","plans","folders","memos","facts","chapters","subjects","focusLogs","skills","records"))
            put(name, JsonArray(emptyList()))
        put("timers", JsonArray(listOf(timer)))
    }.toString()
    @Test fun decoderUsesPortableStudySnapshotNotTheOldLossyTimerConstructor() {
        val clock = object : TimeSource {
            var wall = 1700000000000L; var elapsed = 1000L
            override fun wall() = wall; override fun elapsed() = elapsed; override fun boot() = 1
        }
        val live = TimerTransitions.startStudy(null,"阅读",clock,"study-live")
        clock.wall += 45000; clock.elapsed += 45000
        val restored = BackupDecoder.decode(native(TimerBackup.encode(live,clock))).timers.single()
        assertEquals(45000L,restored.remainingMs)
        assertFalse(restored.running)
        assertEquals(45000L,obj(restored.raw).arr("segments").single().jsonObject.num("millis"))
    }
    @Test fun nativeDecoderDoesNotManufactureActivityForIdleTimers() {
        val clock = object : TimeSource {override fun wall()=0L; override fun elapsed()=0L; override fun boot()=1}
        val restored = BackupDecoder.decode(native(TimerBackup.encode(TimerEntity(),clock))).timers.single()
        assertEquals("",restored.generation)
    }
    @Test fun fullLegacyArchiveUsesRealItemsFieldAndRetainsOriginalSettings() {
        val main = buildJsonObject {
            put("name","华生"); put("since","2023-07-08")
            for (name in listOf("messages","tasks","notes","checks")) put(name,JsonArray(emptyList()))
        }.toString()
        val subjects = """{"version":1,"items":[{"id":"reading","name":"阅读"},{"id":"art","name":"美术"}],"selected":"art"}"""
        val archive = buildJsonObject {
            put("format","four-seasons-luke-full-backup"); put("version",1)
            put("storage",buildJsonObject {put("luke-companion-v1",main);put("luke-study-subjects-v206",subjects)})
        }.toString()
        val imported = BackupDecoder.decode(archive)
        assertEquals(listOf("reading","art"),imported.subjects.map{it.id})
        assertEquals(subjects,imported.records.single {it.id=="luke-study-subjects-v206"}.payload)
        assertEquals(archive,imported.source)
    }
    @Test fun incompleteActiveStudySnapshotFailsBeforeProducingAnImportPlan() {
        val bad = buildJsonObject {
            put("id","study");put("kind","study");put("label","阅读");put("durationMs",0)
            put("remainingMs",60000);put("startedWall",1700000000000L);put("generation","old")
            put("running",true);put("raw","{}")
        }
        assertTrue(runCatching {BackupDecoder.decode(native(bad))}.isFailure)
    }
}
