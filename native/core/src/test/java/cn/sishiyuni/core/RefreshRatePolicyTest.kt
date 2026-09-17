package cn.sishiyuni.core

import cn.sishiyuni.core.model.DisplayModeSpec
import cn.sishiyuni.core.model.RefreshRatePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshRatePolicyTest {
    private val current = DisplayModeSpec(1, 1080, 2400, 60f)
    @Test fun selectsSupported120AtTheCurrentResolution() {
        assertEquals(3, RefreshRatePolicy.choose(current, listOf(current, current.copy(id=2,hz=90f),current.copy(id=3,hz=120f))))
    }
    @Test fun neverSwitchesResolutionToGainRefreshRate() {
        assertEquals(1, RefreshRatePolicy.choose(current,listOf(current,DisplayModeSpec(2,1440,3200,144f))))
    }
    @Test fun doesNotInventA120HzModeOnA60HzDisplay() {
        assertEquals(1,RefreshRatePolicy.choose(current,listOf(current)))
    }
    @Test fun rejectsInvalidModes() {
        assertEquals(1,RefreshRatePolicy.choose(current,listOf(current,current.copy(id=2,hz=Float.NaN),current.copy(id=3,hz=Float.POSITIVE_INFINITY),current.copy(id=4,hz=0f))))
    }
    @Test fun equalRatesPreferTheCurrentModeToAvoidNeedlessSwitching() {
        assertEquals(4,RefreshRatePolicy.choose(current.copy(id=4),listOf(current,current.copy(id=4))))
    }
    @Test fun missingSupportedModesDelegatesToTheSystem() { assertEquals(0,RefreshRatePolicy.choose(current,emptyList())) }
    @Test fun invalidCurrentResolutionDelegatesToTheSystem() { assertEquals(0,RefreshRatePolicy.choose(current.copy(width=0),listOf(current))) }
    @Test fun swappedResolutionIsNotAssumedEquivalent() {
        assertEquals(1,RefreshRatePolicy.choose(current,listOf(current,DisplayModeSpec(2,2400,1080,120f))))
    }
}
