package cn.sishiyuni.core

import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import cn.sishiyuni.core.network.ChatContextWindow
import cn.sishiyuni.core.skills.SkillParser
import cn.sishiyuni.core.timer.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StateBoundaryTest {
    private class Clock(var w:Long=1_000_000,var e:Long=50_000,var b:Int=2):TimeSource {
        override fun wall()=w; override fun elapsed()=e; override fun boot()=b
    }
    private fun zip(vararg files:Pair<String,String>):ByteArray {
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> files.forEach { (path,text) ->
            zip.putNextEntry(ZipEntry(path));zip.write(text.toByteArray());zip.closeEntry()
        } }
        return output.toByteArray()
    }
    @Test fun pausedStudyCannotBeOverwrittenByNewStart() {
        val c=Clock();val active=TimerTransitions.startStudy(null,"阅读",c,"a");c.e+=2_500
        val paused=TimerTransitions.pauseStudy(active,c)
        assertTrue(runCatching { TimerTransitions.startStudy(paused,"阅读",c,"b") }.isFailure)
        assertEquals(2_500L,paused.remainingMs)
    }
    @Test fun repeatedStartPreservesGenerationAndTime() {
        val c=Clock();val active=TimerTransitions.startStudy(null,"阅读",c,"a");c.e+=2_500
        assertSame(active,TimerTransitions.startStudy(active,"阅读",c,"b"))
        assertEquals(2_500L,TimerMath.studyElapsed(active,c))
    }
    @Test fun runningStudyCannotSilentlyChangeSubject() {
        val c=Clock();val active=TimerTransitions.startStudy(null,"阅读",c,"a")
        assertTrue(runCatching { TimerTransitions.startStudy(active,"英语",c,"b") }.isFailure)
    }
    @Test fun completedStudyAllowsNewSession() {
        val c=Clock();val previous=TimerTransitions.startStudy(null,"阅读",c,"a").copy(running=false,completed=true)
        val next=TimerTransitions.startStudy(previous,"英语",c,"b")
        assertEquals("b",next.generation);assertEquals("英语",next.label);assertEquals(0L,next.remainingMs)
    }
    @Test fun pauseIsIdempotentAndKeepsAllPriorSegments() {
        val c=Clock();val active=TimerTransitions.startStudy(null,"阅读",c,"a");c.e+=2_500
        val paused=TimerTransitions.pauseStudy(active,c);c.e+=5_000
        assertEquals(paused,TimerTransitions.pauseStudy(paused,c))
        val resumed=paused.copy(running=true,elapsedDeadline=c.e,wallDeadline=c.w)
        c.e+=2_500;val again=TimerTransitions.pauseStudy(resumed,c)
        assertEquals(5_000L,again.remainingMs)
        assertEquals(listOf(2_500L,2_500L),obj(again.raw).arr("segments").map{it.jsonObject.num("millis")})
    }
    @Test fun studyPauseUsesMonotonicDurationDespiteClockJump() {
        val c=Clock();val active=TimerTransitions.startStudy(null,"阅读",c,"a");c.e+=60_000;c.w-=600_000
        val paused=TimerTransitions.pauseStudy(active,c)
        assertEquals(60_000L,paused.remainingMs)
        assertEquals(1_000_000L,obj(paused.raw).arr("segments").single().jsonObject.num("from"))
    }
    @Test fun resetOfUnfinishedStudyRequiresExplicitDiscard() {
        val c=Clock();val active=TimerTransitions.startStudy(null,"阅读",c,"a")
        assertTrue(runCatching { TimerTransitions.reset(active,false) }.isFailure)
        val reset=TimerTransitions.reset(active,true)
        assertFalse(TimerTransitions.hasStudy(reset));assertEquals("",reset.generation);assertFalse(reset.running)
        assertEquals("b",TimerTransitions.startStudy(reset,"阅读",c,"b").generation)
    }
    @Test fun malformedStudySegmentsAreNotSilentlyDiscarded() {
        val active=TimerTransitions.startStudy(null,"阅读",Clock(),"a").copy(raw="not JSON")
        assertTrue(runCatching{TimerTransitions.pauseStudy(active,Clock())}.isFailure)
    }
    @Test fun countdownReplacementIsExplicitEvenWhenPaused() {
        val paused=TimerEntity(generation="a",remainingMs=1_000,running=false)
        assertTrue(runCatching{TimerTransitions.requireCountdownReplacement(paused,false)}.isFailure)
        TimerTransitions.requireCountdownReplacement(paused,true)
        TimerTransitions.requireCountdownReplacement(paused.copy(completed=true),false)
    }
    @Test fun resetCountdownCanStartAgainWithoutReplacementPrompt() {
        val old=TimerEntity(generation="a",durationMs=60_000,remainingMs=1_000,running=true)
        val reset=TimerTransitions.reset(old,false)
        TimerTransitions.requireCountdownReplacement(reset,false)
        assertEquals(60_000L,reset.remainingMs);assertFalse(reset.running);assertTrue(reset.generation.isEmpty())
    }
    @Test fun durationFormattingCannotOverflowIntoNegativeValues() {
        assertFalse(TimerMath.duration(Long.MAX_VALUE).contains('-'))
        assertEquals("00:00:00",TimerMath.duration(-1));assertEquals("00:00:01",TimerMath.duration(1));assertEquals("00:00:01",TimerMath.duration(1000))
    }
    @Test fun invalidRulerCoordinatesAreRejected() {
        listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertTrue(runCatching{TimerMath.releaseMinutes(invalid,0f)}.isFailure)
            assertTrue(runCatching{TimerMath.releaseMinutes(10f,invalid)}.isFailure)
            assertTrue(runCatching{TimerMath.rulerResistance(invalid)}.isFailure)
        }
        assertTrue(runCatching{TimerMath.rulerResistance(1f,10f,0f)}.isFailure)
    }
    @Test fun studySplitRejectsOverflowRatherThanDroppingAllRecords() {
        assertTrue(runCatching{TimerMath.splitStudy(Long.MAX_VALUE,10_000)}.isFailure)
    }
    @Test fun contextDoesNotJumpAcrossAnOversizeMiddleMessage() {
        val list=listOf(MessageEntity("old","s",0,"me","old"),MessageEntity("large","s",1,"luke","x".repeat(20)),MessageEntity("new","s",2,"me","new"))
        assertEquals(listOf("new"),ChatContextWindow.select(list,maxCharacters=10).map{it.id})
    }
    @Test fun contextIsChronologicalAndRespectsTheMessageLimit() {
        val list=(0L..9L).reversed().map { MessageEntity("$it","s",it,"me","a") }
        assertEquals(listOf("7","8","9"),ChatContextWindow.select(list,maxMessages=3).map{it.id})
    }
    @Test fun contextExcludesUnfinishedAssistantAndNonChatRecords() {
        val list=listOf(MessageEntity("user","s",0,"me","a"),MessageEntity("failed","s",1,"luke","partial",status="interrupted"),MessageEntity("meta","s",2,"system","x"))
        assertEquals(listOf("user"),ChatContextWindow.select(list).map{it.id})
    }
    @Test fun contextBudgetIncludesNewestUserMessageWithoutTruncatingIt() {
        val list=listOf(MessageEntity("first","s",0,"me","1234"),MessageEntity("last","s",1,"me","5678"))
        assertEquals(listOf("last"),ChatContextWindow.select(list,maxCharacters=4).map{it.id})
        assertTrue(ChatContextWindow.select(list,maxCharacters=3).isEmpty())
    }
    @Test fun duplicateSkillIdentitiesCannotOverwriteEachOtherInOneZip() {
        val archive=zip("a/SKILL.md" to "# writing\nFirst.","b/SKILL.md" to "# writing\nSecond.")
        assertTrue(runCatching{SkillParser.zip(archive)}.isFailure)
    }
    @Test fun distinctSkillsInOneZipRemainInstallable() {
        val archive=zip("a/SKILL.md" to "# writing\nFirst.","b/SKILL.md" to "# reading\nSecond.")
        assertEquals(2,SkillParser.zip(archive).size)
    }
    @Test fun unicodeEquivalentZipPathsAreRejected() {
        val archive=zip("caf\u00e9/SKILL.md" to "# a", "cafe\u0301/SKILL.md" to "# b")
        assertTrue(runCatching{SkillParser.zip(archive)}.isFailure)
    }
    @Test fun skillIdentityDoesNotDependOnPhoneLanguage() {
        val original=Locale.getDefault()
        try {
            Locale.setDefault(Locale.US);val a=SkillParser.parse("# WRITING\nText","local").skill.id
            Locale.setDefault(Locale("tr","TR"));val b=SkillParser.parse("# WRITING\nText","local").skill.id
            assertEquals(a,b)
        } finally { Locale.setDefault(original) }
    }
    @Test fun skillFilePathsCannotContainControlCharacters() {
        assertFalse(SkillParser.safePath("folder\n/SKILL.md"));assertFalse(SkillParser.safePath("folder\u0000/SKILL.md"))
        assertTrue(SkillParser.safePath("中文目录/SKILL.md"))
    }
}
