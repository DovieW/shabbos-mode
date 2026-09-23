package dev.dovie.shabbosmode

import android.view.WindowManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun ClockScreen(
    graph: AppGraph,
    settings: AppSettings,
    shuls: List<ShulItem>,
    minyanim: List<MinyanItem>,
    events: List<TimeEvent>,
    onClose: () -> Unit
) {
    val activity = LocalActivity.current as MainActivity
    val zone = TimeLogic.zone(settings)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSchedule by remember { mutableStateOf(false) }
    val weather by graph.dao.weather(0).collectAsState(initial = emptyList())
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    DisposableEffect(activity) {
        val window = activity.window
        val oldBrightness = window.attributes.screenBrightness
        val oldFlags = window.attributes.flags
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.08f }
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            if (oldFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WindowCompat.getInsetsController(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val effectiveEvents = events.map {
        if (it.key == "start" && settings.overrideWeek == it.week && settings.overrideStart > 0)
            it.copy(atMillis = settings.overrideStart)
        else if (it.key == "end" && settings.overrideWeek == it.week && settings.overrideEnd > 0)
            it.copy(atMillis = settings.overrideEnd)
        else it
    }
    val nextMinyan = TimeLogic.nextMinyan(minyanim, shuls, Instant.ofEpochMilli(now), zone)
    val nextZman = effectiveEvents
        .filter { it.key == "start" || it.key == "end" || settings.selectedZmanim.contains(it.key) }
        .filter { it.atMillis > now }
        .minByOrNull { it.atMillis }
    val connectivity = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivity.activeNetwork
    val online = network != null && connectivity.getNetworkCapabilities(network)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val upcomingForecast = weather.filter { it.atMillis >= now - 3600_000L }.take(4)
    val forecast = if (upcomingForecast.isNotEmpty()) upcomingForecast
        else if (!online) weather.takeLast(4) else emptyList()
    val ageHours = forecast.firstOrNull()?.let { ((now - it.fetchedAt) / 3600_000L).coerceAtLeast(0) }

    Box(Modifier.fillMaxSize().background(Night).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "SHABBOS MODE", color = NightInk,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp
            )
            Text(
                "×", modifier = Modifier.clickable(onClick = onClose),
                color = NightInk, fontSize = 25.sp
            )
        }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                formatClock(now, zone),
                color = NightInk, fontFamily = FontFamily.Serif, fontSize = 76.sp
            )
            Text(
                Instant.ofEpochMilli(now).atZone(zone)
                    .format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                color = NightInk.copy(alpha = 0.72f), fontSize = 14.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val phase = Instant.ofEpochMilli(now).atZone(zone).minute % 4
                repeat(4) { index ->
                    Box(Modifier.size(4.dp).background(
                        NightInk.copy(alpha = if (index == phase) 0.8f else 0.18f)
                    ))
                }
            }
            Spacer(Modifier.height(46.dp))
            if (nextMinyan != null) {
                val shul = shuls.find { it.id == nextMinyan.first.shulId }
                Text(
                    "${nextMinyan.first.label} · ${formatTime(nextMinyan.second, zone)}",
                    color = NightInk, fontSize = 17.sp, textAlign = TextAlign.Center
                )
                Text(shul?.name.orEmpty(), color = NightInk.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            if (nextZman != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "${nextZman.label} · ${formatTime(nextZman.atMillis, zone)}",
                    color = NightInk.copy(alpha = 0.8f), fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            if (forecast.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    forecast.forEach { hour ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Instant.ofEpochMilli(hour.atMillis).atZone(zone)
                                    .format(DateTimeFormatter.ofPattern("ha")),
                                color = NightInk.copy(alpha = 0.7f), fontSize = 11.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${hour.temperature.toInt()}°",
                                color = NightInk, fontSize = 17.sp
                            )
                            Text(
                                "${hour.rainPercent}%",
                                color = NightInk.copy(alpha = 0.7f), fontSize = 11.sp
                            )
                        }
                    }
                }
                if (ageHours != null && (!online || ageHours >= 3)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Forecast ${if (ageHours == 0L) "<1h" else "${ageHours}h"} old",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = NightInk.copy(alpha = 0.6f), fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "VIEW SCHEDULE",
                modifier = Modifier.fillMaxWidth().clickable { showSchedule = true },
                color = NightInk.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showSchedule) {
        AlertDialog(
            onDismissRequest = { showSchedule = false },
            title = { Text("Schedule", color = Ink) },
            text = {
                Column {
                    (effectiveEvents.filter { it.atMillis >= now } +
                        minyanim.mapNotNull { minyan ->
                            if (shuls.none { it.id == minyan.shulId }) return@mapNotNull null
                            val friday = TimeLogic.friday(Instant.ofEpochMilli(now), zone)
                            val date = friday.plusDays(if (minyan.day == 6) 1 else 0)
                            var at = TimeLogic.at(date, minyan.hour, minyan.minute, zone)
                            if (at < now) at = TimeLogic.at(date.plusWeeks(1),
                                minyan.hour, minyan.minute, zone)
                            TimeEvent(
                                "minyan:${minyan.id}",
                                minyan.label + " · " +
                                    (shuls.find { it.id == minyan.shulId }?.name ?: ""),
                                at, friday.toString(), 0
                            )
                        }).sortedBy { it.atMillis }.take(12).forEach {
                        Text(
                            "${formatTime(it.atMillis, zone)}  ${it.label}",
                            modifier = Modifier.padding(vertical = 5.dp),
                            color = Ink, fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSchedule = false }) { Text("Close", color = Ink) }
            },
            containerColor = LightPaper
        )
    }
}
