package dev.dovie.shabbosmode

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import android.text.format.DateFormat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun SettingsScreen(
    graph: AppGraph,
    settings: AppSettings,
    events: List<TimeEvent>,
    minyanim: List<MinyanItem>,
    shuls: List<ShulItem>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val week = TimeLogic.weekKey(Instant.ofEpochMilli(rememberNow()), TimeLogic.zone(settings))
    val start = events.firstOrNull { it.key == "start" && it.week == week }
    val end = events.firstOrNull { it.key == "end" && it.week == week }
    var refreshRequested by remember { mutableStateOf(false) }
    var editingPractice by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf("") }
    val use24Hour = DateFormat.is24HourFormat(context)
    val permissionTick = rememberPermissionRefresh()
    val dndAccess = remember(permissionTick) { graph.scheduler.canControlDnd() }

    fun toggle(key: String) { expanded = if (expanded == key) "" else key }
    SettingsGroup("Location", settings.city.ifBlank { "Choose a city" }, expanded == "location", { toggle("location") }) {
        LocationChooser(graph) { expanded = "" }
    }
    NavigationRow("Zmanim preferences", "Candle lighting · daytime · Shabbos end") {
        editingPractice = true
    }
    if (editingPractice) PracticeEditor(graph, settings) { editingPractice = false }

    val shownStart = if (settings.overrideWeek == week && settings.overrideStart > 0) settings.overrideStart else start?.atMillis
    val shownEnd = if (settings.overrideWeek == week && settings.overrideEnd > 0) settings.overrideEnd else end?.atMillis
    SettingsGroup("Shabbos times", shownStart?.let {
        Instant.ofEpochMilli(it).atZone(TimeLogic.zone(settings)).format(java.time.format.DateTimeFormatter.ofPattern("EEE")) +
            " · " + formatLocalTime(it, TimeLogic.zone(settings), use24Hour)
    } ?: if (settings.city.isBlank()) "Location needed" else "Awaiting times",
        expanded == "times", { toggle("times") }) {
        if (shownStart != null && shownEnd != null) {
            fun changeTime(startTime: Boolean) {
                val millis = if (startTime) shownStart else shownEnd
                val at = Instant.ofEpochMilli(millis).atZone(TimeLogic.zone(settings))
                TimePickerDialog(context, { _, h, m ->
                    scope.launch {
                        val chosen = TimeLogic.at(at.toLocalDate(), h, m, TimeLogic.zone(settings))
                        graph.settings.setOverride(week,
                            if (startTime) chosen else if (settings.overrideWeek == week) settings.overrideStart else 0,
                            if (!startTime) chosen else if (settings.overrideWeek == week) settings.overrideEnd else 0)
                        graph.scheduler.reschedule()
                    }
                }, at.hour, at.minute, use24Hour).show()
            }
            NavigationRow("Starts", formatLocalTime(shownStart, TimeLogic.zone(settings), use24Hour)) { changeTime(true) }
            NavigationRow("Ends", formatLocalTime(shownEnd, TimeLogic.zone(settings), use24Hour)) { changeTime(false) }
            if (settings.overrideWeek == week && (settings.overrideStart > 0 || settings.overrideEnd > 0)) {
                TextButton(onClick = { scope.launch {
                    graph.settings.setOverride("", 0, 0); graph.scheduler.reschedule()
                } }) { Text("Use Hebcal times") }
            }
        } else Text(if (settings.city.isBlank()) "Choose a location first." else "Times haven't synced yet.", color = FadedInk)
        Spacer(Modifier.height(16.dp))
        SecondaryButton("Refresh times & weather") {
            SyncWorker.refreshNow(context)
            refreshRequested = true
        }
        if (refreshRequested) Text("Refresh requested", Modifier.padding(top = 8.dp), color = FadedInk, fontSize = 13.sp)
    }

    SettingsGroup("Friday reminder", "${settings.reminderHours} hours before start",
        expanded == "reminder", { toggle("reminder") }) {
        var hours by remember(settings.reminderHours) { mutableIntStateOf(settings.reminderHours) }
        SettingSlider("Hours before start", hours, 1..12, onFinished = {
            scope.launch { graph.settings.setReminderHours(hours); graph.scheduler.reschedule() }
        }) { hours = it }
    }
    SettingsGroup("Clock", "${settings.selectedZmanim.size} zmanim selected",
        expanded == "clock", { toggle("clock") }) {
        zmanOptions(settings).forEach { (key, label) ->
            CheckOption(label, settings.selectedZmanim.contains(key)) {
                val updated = settings.selectedZmanim.toMutableSet()
                if (it) updated += key else updated -= key
                scope.launch { graph.settings.setZmanim(updated) }
            }
        }
    }
    SettingsGroup("Do Not Disturb", if (!dndAccess && settings.dndEnabled) "Access needed" else if (settings.dndEnabled) "Automatic" else "Off",
        expanded == "dnd", { toggle("dnd") }) {
        if (!dndAccess) SecondaryButton("Allow DND access") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("During Shabbos", Modifier.weight(1f), color = Ink)
            Switch(checked = settings.dndEnabled, enabled = dndAccess,
                modifier = Modifier.semantics { contentDescription = "Do Not Disturb during Shabbos" }, onCheckedChange = {
                scope.launch { graph.settings.setDnd(it); graph.scheduler.reschedule() }
            })
        }
    }
    SettingsGroup("Tasker", "${settings.taskerEvents.size} events selected",
        expanded == "tasker", { toggle("tasker") }) {
        val taskerOptions = listOf("start" to "Shabbos start", "end" to "Shabbos end") + zmanOptions(settings) +
            minyanim.map { "minyan:${it.id}" to "${shuls.find { s -> s.id == it.shulId }?.name.orEmpty()} · ${it.label}" }
        taskerOptions.distinctBy { it.first }.forEach { (key, label) ->
            CheckOption(label, settings.taskerEvents.contains(key)) {
                val updated = settings.taskerEvents.toMutableSet()
                if (it) updated += key else updated -= key
                scope.launch { graph.settings.setTasker(updated); graph.scheduler.reschedule() }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth()) {
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.hebcal.com"))) }) {
            Text("Hebcal", fontSize = 12.sp, color = FadedInk)
        }
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com"))) }) {
            Text("Open-Meteo · CC BY 4.0", fontSize = 12.sp, color = FadedInk)
        }
    }
}

private fun zmanOptions(settings: AppSettings) =
    TimingPractice.zmanSources(settings.tradition).map { (key, source) -> key to source.second }
