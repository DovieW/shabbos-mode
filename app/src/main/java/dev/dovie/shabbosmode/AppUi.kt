package dev.dovie.shabbosmode

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AppRoot(graph: AppGraph, page: MutableState<String>) {
    var previous by rememberSaveable { mutableStateOf("home") }
    fun navigate(target: String) { previous = page.value; page.value = target }
    fun back() { page.value = if (page.value == "shuls" && previous == "prepare") "prepare" else "home" }
    BackHandler(enabled = page.value != "home") { back() }
    val settings by produceState<AppSettings?>(null, graph) {
        graph.settings.flow.collect { value = it }
    }
    val alarms by graph.dao.alarms().collectAsState(initial = emptyList())
    val shuls by graph.dao.shuls().collectAsState(initial = emptyList())
    val minyanim by graph.dao.minyanim().collectAsState(initial = emptyList())
    val checklist by graph.dao.checklist().collectAsState(initial = emptyList())
    val events by graph.dao.events().collectAsState(initial = emptyList())

    ShabbosTheme {
        val currentSettings = settings ?: return@ShabbosTheme
        if (!currentSettings.onboardingComplete) {
            OnboardingScreen(graph, currentSettings)
        } else if (page.value == "clock") {
            ClockScreen(graph, currentSettings, shuls, minyanim, events) { back() }
        } else {
            val duration = quietDuration()
            Scaffold(containerColor = Paper, topBar = {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (page.value != "home") MarkButton(Mark.Back, "Back", onClick = ::back)
                        if (page.value == "home") {
                            CandleMark(Modifier.padding(start = 12.dp, end = 12.dp))
                        }
                        Text(when (page.value) {
                            "prepare" -> "Prepare"
                            "alarms" -> "Alarms"
                            "shuls" -> "Shuls"
                            "settings" -> "Settings"
                            else -> "Shabbos mode"
                        }, modifier = Modifier.weight(1f).padding(start = if (page.value == "home") 12.dp else 8.dp),
                            fontFamily = PrintSerif,
                            fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
                        if (page.value == "home") MarkButton(Mark.Settings, "Settings") { navigate("settings") }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Rule)
                }
            }) { padding ->
                AnimatedContent(page.value, modifier = Modifier.fillMaxSize().padding(padding),
                    transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                    label = "Screen") { target ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()).imePadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)) {
                            when (target) {
                                "prepare" -> PrepareScreen(graph, currentSettings, checklist,
                                    minyanim.any { minyan -> shuls.any { it.id == minyan.shulId } }) { navigate("shuls") }
                                "alarms" -> AlarmsScreen(graph, currentSettings, alarms)
                                "shuls" -> ShulsScreen(graph, shuls, minyanim)
                                "settings" -> SettingsScreen(graph, currentSettings, events, minyanim, shuls)
                                else -> HomeScreen(graph, currentSettings, alarms, shuls, minyanim, checklist, events, ::navigate)
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(graph: AppGraph, settings: AppSettings, alarms: List<AlarmItem>,
                       shuls: List<ShulItem>, minyanim: List<MinyanItem>, checklist: List<ChecklistItem>,
                       events: List<TimeEvent>, onNavigate: (String) -> Unit) {
    val now = rememberNow()
    val context = LocalContext.current
    val zone = TimeLogic.zone(settings)
    val week = TimeLogic.weekKey(Instant.ofEpochMilli(now), zone)
    val effective = effectiveEvents(events, settings)
    val currentStart = effective.firstOrNull { it.key == "start" && it.week == week }
    val currentEnd = effective.firstOrNull { it.key == "end" && it.week == week }
    val liveWeek = currentStart != null && currentEnd != null && now in currentStart.atMillis until currentEnd.atMillis
    val start = if (liveWeek) currentStart else effective.filter { it.key == "start" && it.atMillis > now }.minByOrNull { it.atMillis } ?: currentStart
    val end = effective.firstOrNull { it.key == "end" && it.week == start?.week }
    val during = start != null && end != null && now in start.atMillis until end.atMillis
    val ended = end != null && end.atMillis <= now && start?.week == week
    val next = if (during || ended) end else start?.takeIf { it.atMillis > now }
    if (settings.city.isBlank()) {
        Text("Shabbos times", fontFamily = PrintSerif, fontSize = 36.sp, color = Ink)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Choose location") { onNavigate("settings") }
    } else PaperPanel {
        Text(if (during) "Shabbos ends" else if (ended) "Shabbos ended" else "Shabbos starts",
            color = FadedInk, fontFamily = PrintMono, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (next != null) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val use24Hour = DateFormat.is24HourFormat(context)
            val shown = formatLocalTime(next.atMillis, zone, use24Hour)
            val size = (maxWidth.value / 5.5f).coerceIn(40f, 56f)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (use24Hour) shown else shown.substringBefore(' '), color = Ink,
                    fontFamily = PrintSerif, fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
                    modifier = Modifier.alignByBaseline())
                if (!use24Hour) Text(shown.substringAfter(' '), Modifier.padding(start = 10.dp).alignByBaseline(),
                    color = FadedInk, fontFamily = PrintMono, fontSize = 14.sp)
            }
        } else Text("Awaiting times", color = Ink, fontFamily = PrintSerif, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(next?.let { Instant.ofEpochMilli(it.atMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) } ?: shortCity(settings.city),
            color = FadedInk, fontFamily = PrintMono, fontSize = 12.sp)
        if (next != null) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(shortCity(settings.city), Modifier.weight(1f).padding(end = 16.dp), color = FadedInk, fontSize = 13.sp)
                if (!during && !ended && end != null) Text("Ends ${formatLocalTime(end.atMillis, zone, DateFormat.is24HourFormat(context))}",
                    color = FadedInk, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Open clock") { onNavigate("clock") }
    }
    Spacer(Modifier.height(16.dp))
    val completed = checklist.count { it.doneWeek == week }
    NavigationRow("Prepare", if (checklist.isEmpty()) "Checklist & minyan times" else "$completed of ${checklist.size} ready") {
        onNavigate("prepare")
    }
    val activeAlarms = alarms.count { TimeLogic.nextAlarm(it, Instant.ofEpochMilli(now), zone) != null }
    NavigationRow("Alarms", if (!graph.scheduler.canScheduleExact()) "Access needed"
        else if (activeAlarms == 0) "None active" else "$activeAlarms active") { onNavigate("alarms") }
    val nextMinyan = TimeLogic.nextMinyan(minyanim, shuls, Instant.ofEpochMilli(now), zone)
    NavigationRow("Shuls", nextMinyan?.let {
        "${shuls.find { shul -> shul.id == it.first.shulId }?.name.orEmpty()} · " +
            Instant.ofEpochMilli(it.second).atZone(zone).format(DateTimeFormatter.ofPattern("EEE")) +
            " " + formatLocalTime(it.second, zone, DateFormat.is24HourFormat(context))
    } ?: if (shuls.isEmpty()) "Save weekly minyan times" else "${shuls.size} saved") { onNavigate("shuls") }
    if (settings.city.isBlank()) {
        Spacer(Modifier.height(24.dp))
        SecondaryButton("Open clock") { onNavigate("clock") }
    }
}

fun effectiveEvents(events: List<TimeEvent>, settings: AppSettings): List<TimeEvent> = events.map {
    when {
        it.key == "start" && settings.overrideWeek == it.week && settings.overrideStart > 0 -> it.copy(atMillis = settings.overrideStart)
        it.key == "end" && settings.overrideWeek == it.week && settings.overrideEnd > 0 -> it.copy(atMillis = settings.overrideEnd)
        else -> it
    }
}

fun shortCity(city: String) = city.substringBefore(',')
fun formatLocalTime(millis: Long, zone: ZoneId, use24Hour: Boolean): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a"))
fun formatNextTime(millis: Long, now: Long, zone: ZoneId, use24Hour: Boolean): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val day = if (at.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) ""
        else at.format(DateTimeFormatter.ofPattern("EEE")) + " "
    return day + formatLocalTime(millis, zone, use24Hour)
}
fun formatWallTime(hour: Int, minute: Int, use24Hour: Boolean): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a"))
