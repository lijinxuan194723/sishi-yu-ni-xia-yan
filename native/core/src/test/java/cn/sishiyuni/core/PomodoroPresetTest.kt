package cn.sishiyuni.core

import cn.sishiyuni.core.timer.*
import org.junit.Assert.*
import org.junit.Test

class PomodoroPresetTest {
    @Test fun allPresetValuesRoundTrip() {
        val value=PomodoroPreset(PomodoroConfig(35,8,24,5),"读完这一章","阅读")
        assertEquals(value,PomodoroPreset.decode(value.encode()))
    }
    @Test fun earlyNativePhaseWrapperRemainsReadable() {
        val timer=Pomodoro.ready(PomodoroConfig(40,6,15,3),"阅读","语文","gen")
        val preset=PomodoroPreset.decode(timer.raw)
        assertEquals(PomodoroConfig(40,6,15,3),preset.config)
        assertEquals("语文",preset.group)
    }
    @Test fun missingFieldsAreNotTreatedAsZeroMinuteTimers() {
        try {PomodoroPreset.decode("{}");fail("corrupt settings must not be accepted")}
        catch (_:IllegalStateException) {}
    }
    @Test fun blankTaskCannotBeSaved() {
        try {PomodoroPreset(title=" ").validate();fail("blank task must not be accepted")}
        catch (_:IllegalArgumentException) {}
    }
}
