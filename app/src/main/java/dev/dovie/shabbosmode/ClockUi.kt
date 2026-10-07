package dev.dovie.shabbosmode

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun ClockScreen(graph: AppGraph, settings: AppSettings, shuls: List<ShulItem>,
                minyanim: List<MinyanItem>, events: List<TimeEvent>, onClose: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val zone = TimeLogic.zone(settings)
    val now = rememberNow()
    val localNow = Instant.ofEpochMilli(now).atZone(zone)
    val use24Hour = DateFormat.is24HourFormat(activity)
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    val weather by graph.dao.weather(0).collectAsState(initial = emptyList())
    val overnight = localNow.hour >= 22 || localNow.hour < 6
    DisposableEffect(activity) {
        val window = activity.window
        val oldBrightness = window.attributes.screenBrightness
        val wasAwake = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            if (!wasAwake) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(overnight) {
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = if (overnight) .04f else .18f
        }
    }
    val effective = effectiveEvents(events, settings).filter {
        it.key == "start" || it.key == "end" || settings.selectedZmanim.contains(it.key)
    }
    val nextMinyan = TimeLogic.nextMinyan(minyanim, shuls, Instant.ofEpochMilli(now), zone, events, settings)
    val nextZman = effective.filter { it.atMillis > now }.minByOrNull { it.atMillis }
    val connectivity = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivity.activeNetwork
    val online = network != null && connectivity.getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val upcomingForecast = weather.filter { it.atMillis >= now - 3600_000L }.take(4)
    val forecast = if (upcomingForecast.isNotEmpty()) upcomingForecast
        else if (!online) weather.takeLast(4) else emptyList()
    val ageHours = forecast.firstOrNull()?.let { ((now - it.fetchedAt) / 3600_000L).coerceAtLeast(0) }
    val motion = quietDuration() > 0

    Column(Modifier.fillMaxSize().background(Night)
        .windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shortCity(settings.city), Modifier.weight(1f), color = NightInk.copy(alpha = .75f),
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MarkButton(Mark.Close, "Close clock", NightInk, onClose)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val clockSize = ((if (landscape) maxWidth.value / 2 else maxWidth.value) / 4.2f)
                .coerceIn(48f, 96f) / fontScale
            val timeFace: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        Text(localNow.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm")),
                            color = NightInk, fontFamily = PrintSerif, fontSize = clockSize.sp,
                            lineHeight = (clockSize * 1.12f).sp, maxLines = 1)
                        if (!use24Hour) Text(localNow.format(DateTimeFormatter.ofPattern("a")),
                            Modifier.padding(start = 8.dp, bottom = 12.dp), color = NightInk.copy(alpha = .75f), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(localNow.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                        color = NightInk.copy(alpha = .75f), fontSize = 14.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(if (landscape) 12.dp else 24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        val phase = if (motion) localNow.minute % 4 else 0
                        repeat(4) { index ->
                            Box(Modifier.size(3.dp).background(NightInk.copy(alpha = if (index == phase) .75f else .2f)))
                        }
                    }
                }
            }
            val nextEvents: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (nextMinyan != null) {
                        Text(nextMinyan.first.label, color = NightInk, fontSize = 16.sp, lineHeight = 22.sp,
                            textAlign = TextAlign.Center)
                        Text(formatNextTime(nextMinyan.second, now, zone, use24Hour), color = NightInk,
                            fontSize = 24.sp, lineHeight = 30.sp, fontFamily = PrintSerif)
                        Text(shuls.find { it.id == nextMinyan.first.shulId }?.name.orEmpty(),
                            color = NightInk.copy(alpha = .75f), fontSize = 13.sp, lineHeight = 18.sp,
                            textAlign = TextAlign.Center)
                    }
                    if (nextZman != null) {
                        if (nextMinyan != null) Spacer(Modifier.height(if (landscape) 12.dp else 24.dp))
                        Text("${nextZman.label} · ${formatNextTime(nextZman.atMillis, now, zone, use24Hour)}",
                            color = NightInk.copy(alpha = .85f), fontSize = 14.sp, lineHeight = 20.sp,
                            textAlign = TextAlign.Center)
                    }
                    if (nextMinyan == null && nextZman == null) Text("No upcoming times",
                        color = NightInk.copy(alpha = .75f), fontSize = 14.sp)
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) { timeFace() }
                    Box(Modifier.weight(1f).padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center) { nextEvents() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    timeFace()
                    Spacer(Modifier.height(48.dp))
                    nextEvents()
                }
            }
        }
        if (forecast.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = if (landscape) 8.dp else 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                forecast.forEach { hour ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val time = Instant.ofEpochMilli(hour.atMillis).atZone(zone)
                            .format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "ha"))
                        if (landscape) Text("$time · ${hour.temperature.toInt()}°", color = NightInk,
                            fontSize = 14.sp, lineHeight = 20.sp)
                        else {
                            Text(time, color = NightInk.copy(alpha = .75f), fontSize = 12.sp, lineHeight = 16.sp)
                            Text("${hour.temperature.toInt()}°", color = NightInk, fontSize = 20.sp, lineHeight = 26.sp)
                        }
                        Text("${hour.rainPercent}% rain", color = NightInk.copy(alpha = .75f), fontSize = 11.sp,
                            lineHeight = 16.sp)
                    }
                }
            }
            if (ageHours != null && (!online || ageHours >= 3)) Text(
                "Forecast ${if (ageHours == 0L) "<1h" else "${ageHours}h"} old",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
                color = NightInk.copy(alpha = .75f), fontSize = 12.sp)
        }
        TextButton(onClick = { showSchedule = true }, modifier = Modifier.align(Alignment.CenterHorizontally)
            .padding(vertical = 8.dp).heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = NightInk)) { Text("Schedule") }
    }
    if (showSchedule) {
        val schedule = (effective.filter { it.atMillis >= now } + minyanim.mapNotNull { minyan ->
            val shul = shuls.find { it.id == minyan.shulId } ?: return@mapNotNull null
            val friday = TimeLogic.friday(Instant.ofEpochMilli(now), zone)
            val at = TimeLogic.nextMinyanTime(minyan, Instant.ofEpochMilli(now), zone, events, settings)
                ?: return@mapNotNull null
            TimeEvent("minyan:${minyan.id}", "${minyan.label} · ${shul.name}", at, friday.toString(), 0)
        }).sortedBy { it.atMillis }.take(24)
        AlertDialog(onDismissRequest = { showSchedule = false }, title = { Text("Schedule") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (schedule.isEmpty()) Text("No times saved.")
                    schedule.forEach { event ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Text(Instant.ofEpochMilli(event.atMillis).atZone(zone).format(DateTimeFormatter.ofPattern("EEE, MMM d")) +
                                " · " + formatLocalTime(event.atMillis, zone, use24Hour), color = FadedInk, fontSize = 12.sp)
                            Text(event.label, color = Ink, fontSize = 15.sp)
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showSchedule = false }) { Text("Close") } })
    }
}
