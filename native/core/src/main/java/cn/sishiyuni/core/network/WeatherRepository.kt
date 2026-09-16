package cn.sishiyuni.core.network

import cn.sishiyuni.core.data.*
import cn.sishiyuni.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val WEATHER_TTL = 30 * 60 * 1000L
data class WeatherHour(val time: String, val temperature: Double, val code: Int, val rain: Double?)
data class WeatherDay(val date: String, val min: Double, val max: Double, val code: Int, val rain: Double?)
data class WeatherReport(val city: String, val temperature: Double, val feelsLike: Double?, val humidity: Double?, val wind: Double?, val code: Int, val day: Boolean, val time: String, val hours: List<WeatherHour>, val days: List<WeatherDay>, val fetchedAt: Long, val cached: Boolean = false)
data class CityResult(val name: String, val label: String, val latitude: Double, val longitude: Double)
data class WeatherState(val report: WeatherReport? = null, val loading: Boolean = false, val error: String? = null, val locationKey: String? = null)

object WeatherParser {
    private fun number(o: JsonObject, key: String): Double? = (o[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
    private fun number(a: JsonArray, index: Int): Double? = (a.getOrNull(index) as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
    private fun code(value: Double?): Int? = value?.takeIf { it in 0.0..999.0 && it % 1.0 == 0.0 }?.toInt()
    private fun probability(value: Double?): Double? = value?.takeIf { it in 0.0..100.0 }
    fun parse(root: JsonObject, city: String, at: Long): WeatherReport {
        val current = root.child("current")
        val temperature = number(current, "temperature_2m")?.takeIf { it in -100.0..70.0 } ?: error("天气响应缺少有效温度")
        val weatherCode = code(number(current, "weather_code")) ?: error("天气状态缺失或无效")
        val timestamp = current.str("time")
        val currentTime = runCatching { LocalDateTime.parse(timestamp) }.getOrNull()
        val hourly = root.child("hourly")
        val daily = root.child("daily")
        val hours = hourly.arr("time").mapIndexedNotNull { index, element ->
            val text = (element as? JsonPrimitive)?.content ?: return@mapIndexedNotNull null
            val time = runCatching { LocalDateTime.parse(text) }.getOrNull() ?: return@mapIndexedNotNull null
            val temp = number(hourly.arr("temperature_2m"), index)?.takeIf { it in -100.0..70.0 }
            val hc = code(number(hourly.arr("weather_code"), index))
            if (temp == null || hc == null || currentTime == null || time < currentTime.truncatedTo(ChronoUnit.HOURS)) null
            else WeatherHour(text, temp, hc, probability(number(hourly.arr("precipitation_probability"), index)))
        }.distinctBy { it.time }.sortedBy { it.time }.take(24)
        val days = daily.arr("time").mapIndexedNotNull { index, element ->
            val text = (element as? JsonPrimitive)?.content ?: return@mapIndexedNotNull null
            val date = runCatching { LocalDate.parse(text) }.getOrNull() ?: return@mapIndexedNotNull null
            val low = number(daily.arr("temperature_2m_min"), index)?.takeIf { it in -100.0..70.0 }
            val high = number(daily.arr("temperature_2m_max"), index)?.takeIf { it in -100.0..70.0 }
            val dc = code(number(daily.arr("weather_code"), index))
            if (low == null || high == null || dc == null || low > high || currentTime != null && date < currentTime.toLocalDate()) null
            else WeatherDay(text, low, high, dc, probability(number(daily.arr("precipitation_probability_max"), index)))
        }.distinctBy { it.date }.sortedBy { it.date }.take(10)
        return WeatherReport(city, temperature, number(current, "apparent_temperature")?.takeIf { it in -120.0..90.0 },
            probability(number(current, "relative_humidity_2m")), number(current, "wind_speed_10m")?.takeIf { it in 0.0..500.0 },
            weatherCode, current.num("is_day", 1) == 1L, timestamp, hours, days, at)
    }
    fun label(code: Int): String = when (code) {
        0 -> "晴"; 1, 2 -> "多云"; 3 -> "阴"; 45, 48 -> "雾"
        51, 53, 55, 56, 57 -> "细雨"; 61, 63, 65, 66, 67, 80, 81, 82 -> "雨"
        71, 73, 75, 77, 85, 86 -> "雪"; 95, 96, 99 -> "雷雨"; else -> "天气变化中"
    }
    fun scene(code: Int): String = when (code) {
        0 -> "sun"; 1, 2, 3 -> "cloud"; 45, 48 -> "fog"; 71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "thunder"; 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        else -> "none"
    }
}

/** Injectable I/O keeps race and failure tests independent of Android platform stubs. */
class WeatherRepository(
    private val preferences: () -> AppPreferences,
    private val loadCache: suspend (String) -> RecordEntity?,
    private val saveCache: suspend (RecordEntity) -> Unit,
    private val requestJson: suspend (String) -> JsonObject,
    private val clock: () -> Long = System::currentTimeMillis
) {
    constructor(http: Http, dao: LukeDao, prefs: PreferencesStore) : this(
        { prefs.state.value }, { dao.record("weather", it) }, { dao.putRecord(it) }, { http.json(it).jsonObject }
    )
    private val mutableState = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = mutableState.asStateFlow()
    private val lock = Any()
    private var generation = 0L
    private var active: Job? = null
    private fun key(p: AppPreferences) = "open-meteo:${p.latitude}:${p.longitude}"
    private fun sameLocation(a: AppPreferences, b: AppPreferences) =
        a.latitude.toBits() == b.latitude.toBits() && a.longitude.toBits() == b.longitude.toBits() && a.weatherCity == b.weatherCity
    private fun publish(ticket: Long, p: AppPreferences, next: WeatherState) = synchronized(lock) {
        if (ticket == generation) {
            mutableState.value = if (sameLocation(p, preferences())) next.copy(locationKey = key(p)) else WeatherState()
        }
    }
    suspend fun search(query: String): List<CityResult> {
        require(query.trim().length in 1..80)
        val url = httpsUrl("https://geocoding-api.open-meteo.com/v1/search").newBuilder()
            .addQueryParameter("name", query.trim()).addQueryParameter("count", "15").addQueryParameter("language", "zh").build()
        return requestJson(url.toString()).arr("results").mapNotNull { element ->
            val city = element as? JsonObject ?: return@mapNotNull null
            val lat = (city["latitude"] as? JsonPrimitive)?.doubleOrNull
            val lon = (city["longitude"] as? JsonPrimitive)?.doubleOrNull
            if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 || city.str("name").isBlank()) null
            else CityResult(city.str("name"), listOf(city.str("name"), city.str("admin1"), city.str("country")).filter { it.isNotBlank() }.distinct().joinToString(" · "), lat, lon)
        }
    }
    suspend fun refresh(force: Boolean = false): Unit = coroutineScope {
        val ownJob = currentCoroutineContext().job
        val (ticket, previous) = synchronized(lock) {
            generation += 1
            val previous = active
            active = ownJob
            generation to previous
        }
        previous?.cancel()
        val p = preferences()
        val cacheKey = key(p)
        var retained = state.value.takeIf { it.locationKey == cacheKey && it.report?.city == p.weatherCity }?.report
        try {
            require(p.latitude.isFinite() && p.latitude in -90.0..90.0 && p.longitude.isFinite() && p.longitude in -180.0..180.0) { "城市坐标无效，请重新选择" }
            if (p.weatherCity.isBlank()) { publish(ticket, p, WeatherState(error = "先选择一个城市")); return@coroutineScope }
            publish(ticket, p, WeatherState(retained, loading = true))
            val cached = try { loadCache(cacheKey) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val decoded = cached?.let { runCatching { WeatherParser.parse(obj(it.payload), p.weatherCity, it.updatedAt).copy(cached = true) }.getOrNull() }
            if (decoded != null && (retained == null || decoded.fetchedAt > retained!!.fetchedAt)) retained = decoded
            if (!force && retained != null && clock() - retained!!.fetchedAt in 0 until WEATHER_TTL) {
                publish(ticket, p, WeatherState(retained)); return@coroutineScope
            }
            val url = httpsUrl("https://api.open-meteo.com/v1/forecast").newBuilder()
                .addQueryParameter("latitude", p.latitude.toString()).addQueryParameter("longitude", p.longitude.toString())
                .addQueryParameter("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day")
                .addQueryParameter("hourly", "temperature_2m,weather_code,precipitation_probability")
                .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                .addQueryParameter("timezone", "auto").addQueryParameter("forecast_days", "7").build()
            val raw = requestJson(url.toString())
            currentCoroutineContext().ensureActive()
            val at = clock()
            val report = WeatherParser.parse(raw, p.weatherCity, at)
            val cacheError = try { saveCache(RecordEntity("weather", cacheKey, raw.toString(), at)); null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { "天气已更新，本机缓存未保存成功" }
            publish(ticket, p, WeatherState(report, error = cacheError))
        } catch (e: CancellationException) {
            publish(ticket, p, WeatherState(retained))
            throw e
        } catch (e: Exception) {
            publish(ticket, p, WeatherState(retained, error = if (retained != null) "更新失败，继续显示上次记录" else e.message ?: "天气暂时不可用"))
        } finally {
            synchronized(lock) { if (active === ownJob) active = null }
        }
    }
}
