package cn.sishiyuni.core

import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.obj
import cn.sishiyuni.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherRepositoryTest {
    private fun raw(temp: Int = 23, code: Int = 0) = obj("""{"current":{"temperature_2m":$temp,"weather_code":$code,"time":"2026-09-16T12:00","is_day":1}}""")
    private class Fixture {
        var prefs = AppPreferences(weatherCity = "上海", latitude = 31.2, longitude = 121.5)
        var now = 9_000_000L
        var cache: RecordEntity? = null
        var calls = 0
        var readError = false
        var writeError = false
        var request: suspend (String) -> JsonObject = { obj("""{"current":{"temperature_2m":23,"weather_code":0,"time":"2026-09-16T12:00"}}""") }
        val repository = WeatherRepository({ prefs }, { if (readError) throw IOException("read"); cache },
            { if (writeError) throw IOException("full"); cache = it }, { calls++; request(it) }, { now })
    }
    @Test fun freshCacheIsUsedWithoutNetwork() = runTest {
        val f = Fixture(); f.cache = RecordEntity("weather", "key", raw(19).toString(), f.now - 100)
        f.repository.refresh()
        assertEquals(0, f.calls); assertEquals(19.0, f.repository.state.value.report!!.temperature, 0.0)
        assertTrue(f.repository.state.value.report!!.cached); assertFalse(f.repository.state.value.loading)
    }
    @Test fun forceRefreshBypassesCache() = runTest {
        val f = Fixture(); f.cache = RecordEntity("weather", "key", raw(19).toString(), f.now)
        f.repository.refresh(true); assertEquals(1, f.calls); assertEquals(23.0, f.repository.state.value.report!!.temperature, 0.0)
    }
    @Test fun clockGoingBackDoesNotMakeCacheImmortal() = runTest {
        val f = Fixture(); f.cache = RecordEntity("weather", "key", raw().toString(), f.now + 1_000)
        f.repository.refresh(); assertEquals(1, f.calls)
    }
    @Test fun failedCacheReadStillAllowsNetwork() = runTest {
        val f = Fixture(); f.readError = true; f.repository.refresh()
        assertNotNull(f.repository.state.value.report); assertNull(f.repository.state.value.error)
    }
    @Test fun failedCacheWriteDoesNotDiscardFreshWeather() = runTest {
        val f = Fixture(); f.writeError = true; f.repository.refresh()
        assertEquals(23.0, f.repository.state.value.report!!.temperature, 0.0)
        assertTrue(f.repository.state.value.error!!.contains("缓存")); assertFalse(f.repository.state.value.loading)
    }
    @Test fun networkFailureRetainsKnownWeather() = runTest {
        val f = Fixture(); f.cache = RecordEntity("weather", "key", raw(18).toString(), 1)
        f.request = { throw IOException("offline") }; f.repository.refresh()
        assertEquals(18.0, f.repository.state.value.report!!.temperature, 0.0)
        assertFalse(f.repository.state.value.loading); assertNotNull(f.repository.state.value.error)
    }
    @Test fun invalidNetworkResponseDoesNotReplaceCache() = runTest {
        val f = Fixture(); val old = RecordEntity("weather", "key", raw(18).toString(), 1); f.cache = old
        f.request = { obj("""{"current":{"temperature_2m":999,"weather_code":0}}""") }
        f.repository.refresh(true); assertEquals(old, f.cache); assertEquals(18.0, f.repository.state.value.report!!.temperature, 0.0)
    }
    @Test fun aLateCancelledCityCannotOverwriteNewCity() = runTest {
        val f = Fixture(); val release = CompletableDeferred<Unit>()
        f.request = { url -> if (url.contains("31.2")) withContext(NonCancellable) { release.await(); raw(18) } else raw(30) }
        val first = launch { f.repository.refresh(true) }; runCurrent()
        f.prefs = f.prefs.copy(weatherCity = "北京", latitude = 39.9, longitude = 116.4)
        val second = launch { f.repository.refresh(true) }; runCurrent()
        assertEquals("北京", f.repository.state.value.report!!.city)
        release.complete(Unit); first.join(); second.join()
        assertEquals("北京", f.repository.state.value.report!!.city)
        assertEquals(30.0, f.repository.state.value.report!!.temperature, 0.0)
        assertFalse(f.repository.state.value.loading)
    }
    @Test fun locationChangedWithoutRefreshClearsOldSpinner() = runTest {
        val f = Fixture(); val release = CompletableDeferred<Unit>(); f.request = { release.await(); raw() }
        val job = launch { f.repository.refresh() }; runCurrent(); assertTrue(f.repository.state.value.loading)
        f.prefs = f.prefs.copy(weatherCity = "北京", latitude = 39.9)
        release.complete(Unit); job.join()
        assertFalse(f.repository.state.value.loading); assertNull(f.repository.state.value.report)
    }
    @Test fun cancellingRefreshRetainsCacheAndClearsSpinner() = runTest {
        val f = Fixture(); f.cache = RecordEntity("weather", "key", raw(17).toString(), 1)
        f.request = { awaitCancellation() }
        val job = launch { f.repository.refresh(true) }; runCurrent(); job.cancelAndJoin()
        assertFalse(f.repository.state.value.loading); assertEquals(17.0, f.repository.state.value.report!!.temperature, 0.0)
    }
    @Test fun unknownCodeDoesNotPretendItIsRaining() {
        assertEquals("none", WeatherParser.scene(777)); assertEquals("天气变化中", WeatherParser.label(777))
    }
    @Test fun optionalNumbersAreValidatedRatherThanClamped() {
        val r = WeatherParser.parse(obj("""{"current":{"temperature_2m":23,"weather_code":0,"relative_humidity_2m":101,"wind_speed_10m":-5,"apparent_temperature":999}}"""), "上海", 1)
        assertNull(r.humidity); assertNull(r.wind); assertNull(r.feelsLike)
    }
    @Test fun hourlyForecastRejectsBadDatesDuplicatesAndImpossibleValues() {
        val root = raw().toMutableMap()
        root["hourly"] = obj("""{"time":["2026-09-16T11:00","2026-09-16T12:00","2026-09-16T12:00","bad","2026-09-16T13:00"],"temperature_2m":[20,21,22,23,999],"weather_code":[0,0,0,0,0],"precipitation_probability":[0,200,0,0,0]}""")
        val r = WeatherParser.parse(JsonObject(root), "上海", 1)
        assertEquals(1, r.hours.size); assertEquals(21.0, r.hours.single().temperature, 0.0); assertNull(r.hours.single().rain)
    }
    @Test fun dailyForecastRejectsInvertedOrPastDays() {
        val root = raw().toMutableMap()
        root["daily"] = obj("""{"time":["2026-09-15","2026-09-16","2026-09-17"],"temperature_2m_min":[10,30,20],"temperature_2m_max":[20,10,25],"weather_code":[0,0,0]}""")
        assertEquals(listOf("2026-09-17"), WeatherParser.parse(JsonObject(root), "上海", 1).days.map { it.date })
    }
    @Test fun invalidCoordinatesDoNotReachNetwork() = runTest {
        val f = Fixture(); f.prefs = f.prefs.copy(latitude = Double.NaN); f.repository.refresh()
        assertEquals(0, f.calls); assertNotNull(f.repository.state.value.error); assertFalse(f.repository.state.value.loading)
    }
    @Test fun noCityDoesNotReachNetwork() = runTest {
        val f = Fixture(); f.prefs = f.prefs.copy(weatherCity = ""); f.repository.refresh()
        assertEquals(0, f.calls); assertNull(f.repository.state.value.report); assertFalse(f.repository.state.value.loading)
    }
}
