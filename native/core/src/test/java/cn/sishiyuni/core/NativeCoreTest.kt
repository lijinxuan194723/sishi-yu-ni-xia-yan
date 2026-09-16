package cn.sishiyuni.core

import cn.sishiyuni.core.backup.*
import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.memory.*
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.network.*
import cn.sishiyuni.core.skills.*
import cn.sishiyuni.core.timer.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NativeCoreTest {
 private class Clock(var w:Long=1000,var e:Long=1000,var b:Int=1):TimeSource{override fun wall()=w;override fun elapsed()=e;override fun boot()=b}
 @Test fun defaultsAreRequestedValues(){val p=AppPreferences();assertEquals(.95f,p.scale,0f);assertEquals(13f,p.chatSize,0f)}
 @Test fun allSeasonsFollowMonths(){val expected=listOf("winter","winter","spring","spring","spring","summer","summer","summer","autumn","autumn","autumn","winter");(1..12).forEach{assertEquals(expected[it-1],AppPreferences().resolvedSeason(java.time.LocalDateTime.of(2026,it,1,12,0)))}}
 @Test fun explicitSeasonOverridesCalendar(){assertEquals("spring",AppPreferences(season="spring").resolvedSeason(java.time.LocalDateTime.of(2026,12,1,12,0)))}
 @Test fun dateValidationRejectsImpossibleDates(){assertFalse(validDate("2026-02-29"));assertFalse(validDate("2026-2-1"));assertTrue(validDate("2024-02-29"));assertFalse(validDate("2101-01-01"))}
 @Test fun completionUrlIsNormalized(){assertEquals("https://example.com/v1/chat/completions",completionUrl("https://example.com/v1/").toString());assertEquals("https://example.com/v1/chat/completions",completionUrl("https://example.com/v1/chat/completions").toString())}
 @Test fun completionUrlRejectsCredentialsAndCleartext(){listOf("http://example.com","https://name:password@example.com/v1","https://example.com/v1?key=x").forEach{assertTrue(runCatching{completionUrl(it)}.isFailure)}}
 @Test fun sseAssemblesMultilineEvents(){val d=SseDecoder();assertNull(d.line(":comment"));assertNull(d.line("data: one"));assertNull(d.line("data: two"));assertEquals("one\ntwo",d.line(""));assertNull(d.finish())}
 @Test fun countdownIgnoresWallClockChangesInSameBoot(){val c=Clock();val t=TimerEntity(durationMs=60000,remainingMs=60000,elapsedDeadline=61000,wallDeadline=61000,bootCount=1,running=true);c.w=999999999;assertEquals(60000,TimerMath.remaining(t,c));c.e=31000;assertEquals(30000,TimerMath.remaining(t,c))}
 @Test fun countdownRecoversAcrossBootAndNeverNegative(){val c=Clock(w=71000,b=2);val t=TimerEntity(durationMs=60000,elapsedDeadline=61000,wallDeadline=61000,bootCount=1,running=true);assertEquals(0,TimerMath.remaining(t,c));c.w=31000;assertEquals(30000,TimerMath.remaining(t,c));c.w=-99999;assertEquals(60000,TimerMath.remaining(t,c))}
 @Test fun pausedCountdownDoesNotAdvance(){val c=Clock(w=999999,e=999999);assertEquals(12500,TimerMath.remaining(TimerEntity(remainingMs=12500),c))}
 @Test fun rulerValuesStayWithinRange(){(-200..400).forEach{p->listOf(-100f,0f,100f).forEach{v->assertTrue(TimerMath.releaseMinutes(p.toFloat(),v) in 1..180)}}}
 @Test fun studyIsSplitAcrossMidnight(){val start=java.time.Instant.parse("2026-09-15T15:55:00Z").toEpochMilli();val parts=TimerMath.splitStudy(start,10*60000,ZoneId.of("Asia/Shanghai"));assertEquals(2,parts.size);assertEquals(5.0,parts[0].second,0.001);assertEquals(5.0,parts[1].second,0.001)}
 @Test fun memoryOnlyAcceptsLiteralUserEvidence(){val m=MessageEntity("u","s",0,"me","我更喜欢爵士音乐。");val f=obj("{\"key\":\"music\",\"value\":\"爵士音乐\",\"quote\":\"喜欢爵士音乐\",\"sourceId\":\"u\"}");assertTrue(MemoryPolicy.validFact(f,listOf(m),emptySet()));assertFalse(MemoryPolicy.validFact(f,listOf(m.copy(who="luke")),emptySet()));assertFalse(MemoryPolicy.validFact(f,listOf(m.copy(muted=true)),emptySet()));assertFalse(MemoryPolicy.validFact(f,listOf(m),setOf("music")))}
 @Test fun changingEvidenceChangesFingerprint(){val m=MessageEntity("u","s",0,"me","原文");assertNotEquals(MemoryPolicy.fingerprint(listOf(m)),MemoryPolicy.fingerprint(listOf(m.copy(text="更正"))));assertNotEquals(MemoryPolicy.fingerprint(listOf(m)),MemoryPolicy.fingerprint(listOf(m.copy(muted=true))))}
 @Test fun dangerousZipPathsAreRejected(){listOf("../SKILL.md","a/../SKILL.md","/SKILL.md","C:/SKILL.md","a\\..\\SKILL.md").forEach{assertFalse(SkillParser.safePath(it))};assertTrue(SkillParser.safePath("a/SKILL.md"))}
 @Test fun skillInstallationNeverAutoEnables(){val c=SkillParser.parse("---\nname: writing\ndescription: Make writing clearer\n---\n# Writing\nRead the user text.","local");assertEquals("writing",c.skill.name);assertFalse(c.skill.enabled);assertEquals(c.skill.content.sha256(),c.skill.digest)}
 @Test fun zipInspectionDoesNotRunScripts(){val out=java.io.ByteArrayOutputStream();ZipOutputStream(out).use{z->z.putNextEntry(ZipEntry("skill/SKILL.md"));z.write("# A\nUse scripts/tool.py if available".toByteArray());z.closeEntry();z.putNextEntry(ZipEntry("skill/scripts/tool.py"));z.write("raise Exception('must never run')".toByteArray());z.closeEntry()};val parsed=SkillParser.zip(out.toByteArray());assertEquals(1,parsed.size);assertFalse(parsed[0].skill.enabled);assertTrue(parsed[0].warnings.any{it.contains("不执行")})}
 @Test fun legacyBackupPreservesUnknownRecordsAndOriginal(){val raw="{\"name\":\"冬清\",\"since\":\"2023-07-08\",\"messages\":[{\"who\":\"me\",\"text\":\"你好\"}],\"tasks\":[],\"notes\":[],\"checks\":[],\"extra\":{\"future\":true}}";val p=BackupDecoder.decode(raw);assertEquals(raw,p.source);assertEquals(1,p.messages.size);assertTrue(p.records.any{it.kind=="legacy-main"&&obj(it.payload).child("extra").flag("future")})}
 @Test fun malformedLegacyBackupCannotBecomeEmptyImport(){assertTrue(runCatching{BackupDecoder.decode("{\"name\":\"冬清\",\"since\":\"2023-07-08\",\"messages\":[]}")}.isFailure)}
 @Test fun weatherMissingDataIsNotFabricated(){assertTrue(runCatching{WeatherParser.parse(obj("{\"current\":{}}"),"测试城市",0)}.isFailure);val r=WeatherParser.parse(obj("{\"current\":{\"temperature_2m\":23,\"weather_code\":0,\"is_day\":1}}"),"测试城市",0);assertNull(r.humidity);assertTrue(r.days.isEmpty())}
}
