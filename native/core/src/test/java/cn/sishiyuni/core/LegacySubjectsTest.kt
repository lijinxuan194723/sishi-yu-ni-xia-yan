package cn.sishiyuni.core

import cn.sishiyuni.core.backup.LegacySubjects
import cn.sishiyuni.core.model.JsonCodec
import org.junit.Assert.*
import org.junit.Test

class LegacySubjectsTest {
    private fun parse(text: String)=LegacySubjects.decode(JsonCodec.parseToJsonElement(text))
    @Test fun actualVersion206ItemsSchemaKeepsIdsAndNames() {
        val subjects=parse("""{"version":1,"items":[{"id":"m-1","name":"阅读"},{"id":"e-2","name":"英文"}],"selected":"e-2"}""")
        assertEquals(listOf("m-1","e-2"),subjects.map {it.id})
        assertEquals(listOf("阅读","英文"),subjects.map {it.name})
    }
    @Test fun earlierStringArrayAndSubjectsWrapperStillWork() {
        assertEquals("阅读",parse("[\"阅读\"]").single().name)
        assertEquals("x",parse("""{"subjects":[{"id":"x","name":"阅读"}]}""").single().id)
    }
    @Test fun namesUseTheLegacyNfkcAndWhitespaceNormalization() {
        assertEquals("A B",parse("""{"version":1,"items":[{"id":"a","name":" Ａ  B "}],"selected":"a"}""").single().name)
    }
    @Test fun missingOrMalformedItemsMustNotBecomeAnEmptyList() {
        for (value in listOf("{}","{\"version\":1,\"items\":false}","false"))
            assertTrue(runCatching {parse(value)}.isFailure)
    }
    @Test fun duplicateIdentifiersAreRejectedRatherThanUpsertedOverEachOther() {
        assertTrue(runCatching {parse("""{"subjects":[{"id":"a","name":"阅读"},{"id":"a","name":"英文"}]}""")}.isFailure)
    }
    @Test fun noSavedSubjectsMeansNoInventedDefaults() {
        assertTrue(LegacySubjects.decode(null).isEmpty())
        assertTrue(parse("""{"version":1,"items":[],"selected":""}""").isEmpty())
    }
}
