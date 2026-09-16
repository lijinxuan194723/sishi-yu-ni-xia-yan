package cn.sishiyuni.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.sishiyuni.core.AppGraph
import cn.sishiyuni.core.CoreViewModel
import cn.sishiyuni.core.network.*
import cn.sishiyuni.designsystem.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModel(private val app: AppGraph) : CoreViewModel(app) {
    val state = app.weather.state
    val search = MutableStateFlow("")
    val candidates = MutableStateFlow<List<CityResult>>(emptyList())
    val searching = MutableStateFlow(false)
    init { task { search.collectLatest { text ->
        candidates.value = emptyList()
        if (text.isBlank()) return@collectLatest
        delay(350); searching.value = true
        try { candidates.value = app.weather.search(text) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "城市搜索失败" }
        finally { searching.value = false }
    } } }
    fun refresh(force: Boolean = false) = task { app.weather.refresh(force) }
    fun choose(city: CityResult, done: () -> Unit) = task {
        app.prefs.location(city.label, city.latitude, city.longitude)
        app.prefs.state.first { it.weatherCity == city.label && it.latitude == city.latitude && it.longitude == city.longitude }
        done(); app.weather.refresh()
    }
}

@Composable
fun WeatherCard(graph: AppGraph, active: Boolean, modifier: Modifier = Modifier) {
    val vm = nativeViewModel(graph, "weather") { WeatherViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val p = LocalAppPreferences.current
    var choosing by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    LaunchedEffect(active, p.weatherCity) { if (active && p.weatherCity.isNotBlank()) vm.refresh() }
    val report = state.report
    LukeCard(modifier.fillMaxWidth(), padding = 0.dp) {
        Box(Modifier.fillMaxWidth().heightIn(min = 210.dp).clip(RoundedCornerShape(24.dp))) {
            WeatherScene(report?.code ?: -1, report?.day ?: !p.isNight(), active, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(report?.city ?: p.weatherCity.ifBlank { "今日天气" }, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { choosing = true }) { Icon(Icons.Outlined.LocationOn, "选择天气城市") }
                    IconButton(onClick = { vm.refresh(true) }, enabled = !state.loading && p.weatherCity.isNotBlank()) { Icon(Icons.Outlined.Refresh, "更新天气") }
                }
                if (report != null) {
                    Text("${report.temperature.roundToInt()}°", style = MaterialTheme.typography.headlineMedium)
                    Text(WeatherParser.label(report.code), style = MaterialTheme.typography.bodyLarge)
                    Text(buildList {
                        report.feelsLike?.let { add("体感 ${it.roundToInt()}°") }
                        report.humidity?.let { add("湿度 ${it.roundToInt()}%") }
                        report.wind?.let { add("风速 ${it.roundToInt()} km/h") }
                    }.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { details = true }, Modifier.testTag("weather-forecast")) { Text("小时与七日预报") }
                } else {
                    Text(if (state.loading) "正在获取天气…" else "选一个城市，看看窗外。", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { choosing = true }) { Text("选择城市") }
                }
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (report?.cached == true) Text("显示上次记录 · ${report.time.replace('T', ' ')}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (choosing) CityChooser(vm) { choosing = false }
    if (details && report != null) NativeDialog("${report.city} · 天气", { details = false }) {
        LazyColumn(Modifier.heightIn(max = 490.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("接下来 24 小时", style = MaterialTheme.typography.titleMedium) }
            item { TemperatureTrace(report.hours) }
            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(report.hours, key = { it.time }) { hour -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hour.time.substringAfter('T').take(5), style = MaterialTheme.typography.labelSmall)
                    Text("${hour.temperature.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                    Text(WeatherParser.label(hour.code), style = MaterialTheme.typography.bodySmall)
                    hour.rain?.let { Text("${it.roundToInt()}%", style = MaterialTheme.typography.labelSmall) }
                } }
            } }
            item { Text("七日预报", style = MaterialTheme.typography.titleMedium) }
            items(report.days, key = { it.date }) { day -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.date.takeLast(5), style = MaterialTheme.typography.bodyMedium)
                Text(WeatherParser.label(day.code), style = MaterialTheme.typography.bodyMedium)
                Text("${day.min.roundToInt()}° / ${day.max.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
            } }
            item { Text("数据时间 ${report.time.replace('T', ' ')}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun CityChooser(vm: WeatherViewModel, close: () -> Unit) {
    val text by vm.search.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    NativeDialog("选择城市", close) {
        ErrorNotice(error, vm::clearError)
        OutlinedTextField(text, { vm.search.value = it.take(80) }, singleLine = true, label = { Text("城市名称") }, modifier = Modifier.fillMaxWidth())
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.heightIn(max = 330.dp)) {
            items(candidates, key = { "${it.latitude}:${it.longitude}:${it.label}" }) { city ->
                TextButton(onClick = { vm.choose(city, close) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(city.label, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun TemperatureTrace(hours: List<WeatherHour>) {
    if (hours.size < 2) return
    val points = remember(hours) {
        val low = hours.minOf { it.temperature }; val range = (hours.maxOf { it.temperature } - low).coerceAtLeast(2.0)
        hours.mapIndexed { index, h -> Offset(index.toFloat() / (hours.size - 1), (1.0 - (h.temperature - low) / range).toFloat()) }
    }
    val color = LocalSeason.current.accent
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        points.zipWithNext { a, b ->
            drawLine(color.copy(alpha = .7f), Offset(a.x * size.width, 8.dp.toPx() + a.y * (size.height - 16.dp.toPx())),
                Offset(b.x * size.width, 8.dp.toPx() + b.y * (size.height - 16.dp.toPx())), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
