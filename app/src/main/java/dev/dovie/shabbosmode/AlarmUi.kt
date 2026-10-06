package dev.dovie.shabbosmode

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun AlarmsScreen(graph: AppGraph, settings: AppSettings, alarms: List<AlarmItem>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingId by rememberSaveable { mutableLongStateOf(-1) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var permissionResult by remember { mutableIntStateOf(0) }
    val permissionTick = rememberPermissionRefresh()
    val exactAccess = remember(permissionTick, permissionResult) { graph.scheduler.canScheduleExact() }
    val notifications = remember(permissionTick, permissionResult) { graph.scheduler.canPostNotifications() }
    val use24Hour = DateFormat.is24HourFormat(context)
    val now = rememberNow()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionResult++ }

    if (!exactAccess) {
        PaperPanel {
            Text("Alarm access required", color = Rust)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Allow alarms & reminders") { AppScheduler.requestExactAlarmAccess(context) }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (!notifications && Build.VERSION.SDK_INT >= 33) {
        SecondaryButton("Allow notifications") { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        Spacer(Modifier.height(16.dp))
    }
    if (alarms.isEmpty()) EmptyState("No alarms yet.")
    alarms.forEach { item ->
        val finished = item.repeatDay == 0 && item.oneTimeMillis <= now
        PaperPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { editingId = item.id }
                    .padding(top = 8.dp, bottom = 8.dp, end = 12.dp)) {
                    Text(if (item.repeatDay == 0) formatLocalTime(item.oneTimeMillis, TimeLogic.zone(settings), use24Hour)
                        else formatWallTime(item.hour, item.minute, use24Hour), color = Ink,
                        fontFamily = PrintSerif, fontSize = 36.sp)
                    Text(item.label, color = Ink, fontSize = 15.sp)
                    val repeat = when (item.repeatDay) {
                        5 -> "Every Friday"
                        6 -> "Every Saturday"
                        else -> Instant.ofEpochMilli(item.oneTimeMillis).atZone(TimeLogic.zone(settings))
                            .format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                    }
                    Text(if (finished) "Finished · $repeat" else if (item.enabled && !exactAccess) "Not scheduled · $repeat" else repeat,
                        color = if (item.enabled && !finished && !exactAccess) Rust else FadedInk, fontSize = 13.sp)
                }
                Switch(checked = item.enabled && !finished, enabled = !finished, modifier = Modifier.semantics {
                    contentDescription = "Enable ${item.label}"
                }, onCheckedChange = { enabled -> scope.launch {
                    graph.dao.saveAlarm(item.copy(enabled = enabled)); graph.scheduler.reschedule()
                } })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Add alarm") { adding = true }
    val selected = alarms.find { it.id == editingId }
    if (adding || selected != null) AlarmEditor(selected, settings,
        onDismiss = { adding = false; editingId = -1 },
        onSave = { item ->
            scope.launch { graph.dao.saveAlarm(item); graph.scheduler.reschedule() }
            adding = false; editingId = -1
        },
        onDelete = { item ->
            scope.launch { graph.dao.deleteAlarm(item); graph.scheduler.reschedule() }
            editingId = -1
        })
}

@Composable
private fun AlarmEditor(item: AlarmItem?, settings: AppSettings, onDismiss: () -> Unit,
                        onSave: (AlarmItem) -> Unit, onDelete: (AlarmItem) -> Unit) {
    val context = LocalContext.current
    val zone = TimeLogic.zone(settings)
    val use24Hour = DateFormat.is24HourFormat(context)
    val oneTime = item?.takeIf { it.repeatDay == 0 && it.oneTimeMillis > 0 }
        ?.let { Instant.ofEpochMilli(it.oneTimeMillis).atZone(zone) }
    var label by rememberSaveable(item?.id) { mutableStateOf(item?.label ?: "Alarm") }
    var hour by rememberSaveable(item?.id) { mutableIntStateOf(oneTime?.hour ?: item?.hour ?: 8) }
    var minute by rememberSaveable(item?.id) { mutableIntStateOf(oneTime?.minute ?: item?.minute ?: 0) }
    var repeatDay by rememberSaveable(item?.id) { mutableIntStateOf(item?.repeatDay ?: 6) }
    var dateText by rememberSaveable(item?.id) { mutableStateOf(
        if (item != null && item.oneTimeMillis > 0) Instant.ofEpochMilli(item.oneTimeMillis).atZone(zone).toLocalDate().toString()
        else TimeLogic.friday(Instant.now(), zone).plusDays(1).toString()) }
    val date = LocalDate.parse(dateText)
    var tone by rememberSaveable(item?.id) { mutableStateOf(item?.tone ?: "soft") }
    var volume by rememberSaveable(item?.id) { mutableIntStateOf(item?.volume ?: 70) }
    var vibrate by rememberSaveable(item?.id) { mutableIntStateOf(item?.vibrationSeconds ?: 20) }
    var ramp by rememberSaveable(item?.id) { mutableIntStateOf(item?.rampSeconds ?: 60) }
    var duration by rememberSaveable(item?.id) { mutableIntStateOf(item?.durationMinutes ?: 5) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf("") }
    val future = repeatDay != 0 || TimeLogic.at(date, hour, minute, zone) > rememberNow()
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            tone = uri.toString(); importError = ""
        }.onFailure { importError = "Couldn't open this audio file." }
    }
    EditorDialog(if (item == null) "New alarm" else "Edit alarm", onDismiss,
        saveEnabled = future,
        onSave = { onSave(AlarmItem(id = item?.id ?: 0, label = label.trim().ifBlank { "Alarm" },
            hour = hour, minute = minute, repeatDay = repeatDay,
            oneTimeMillis = if (repeatDay == 0) TimeLogic.at(date, hour, minute, zone) else 0,
            tone = tone, volume = volume, vibrationSeconds = vibrate, rampSeconds = ramp,
            durationMinutes = duration, enabled = item?.enabled ?: true)) }) {
        Column {
            Text("Time", color = FadedInk, fontFamily = PrintMono, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable {
                TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, use24Hour).show()
            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatWallTime(hour, minute, use24Hour), Modifier.weight(1f), color = Ink,
                    fontFamily = PrintSerif, fontSize = 40.sp, lineHeight = 46.sp)
                MarkIcon(Mark.Chevron, color = FadedInk)
            }
            HorizontalDivider(color = Rule)
            Spacer(Modifier.height(16.dp))
            ChoiceChips(listOf(0 to "Once", 5 to "Friday", 6 to "Saturday"), repeatDay) { repeatDay = it }
            if (repeatDay == 0) NavigationRow("Date", date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))) {
                DatePickerDialog(context, { _, y, m, d -> dateText = LocalDate.of(y, m + 1, d).toString() },
                    date.year, date.monthValue - 1, date.dayOfMonth).show()
            }
        }
        if (!future) { Spacer(Modifier.height(8.dp)); Text("Choose a future time.", color = Rust, fontSize = 13.sp) }
        Spacer(Modifier.height(24.dp))
        PaperField(label, { label = it }, "Name")
        Spacer(Modifier.height(28.dp))
        SectionTitle("Sound")
        ChoiceChips(listOf("soft" to "Soft", "chime" to "Chime", "warm" to "Warm"), tone) { tone = it }
        TextButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) {
            Text(if (tone.startsWith("content:")) "Change custom audio" else "Import audio")
        }
        if (importError.isNotBlank()) Text(importError, color = Rust, fontSize = 13.sp)
        SettingSlider("Volume", volume, 0..100, suffix = "%") { volume = it }
        Spacer(Modifier.height(12.dp))
        SettingsGroup("Vibration & timing", "${vibrate}s first · stops after ${duration}m", advanced, { advanced = !advanced }) {
            SettingSlider("Vibrate first", vibrate, 0..60, suffix = "s") { vibrate = it }
            SettingSlider("Volume rise", ramp, 0..120, suffix = "s") { ramp = it }
            SettingSlider("Stop after", duration, 1..20, suffix = "m") { duration = it }
        }
        Spacer(Modifier.height(20.dp))
        SecondaryButton("Preview · 8 seconds") {
            ContextCompat.startForegroundService(context, Intent(context, AlarmPlaybackService::class.java)
                .putExtra("preview", true).putExtra("tone", tone).putExtra("volume", volume)
                .putExtra("vibrate", vibrate).putExtra("ramp", ramp))
        }
        if (item != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { deleting = true }) { Text("Delete alarm", color = Rust) }
        }
    }
    if (deleting && item != null) ConfirmDelete("Delete alarm?", { deleting = false }) { onDelete(item) }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingSlider(label: String, value: Int, range: IntRange, suffix: String = "",
                  onFinished: (() -> Unit)? = null, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink, fontSize = 14.sp)
        Text("$value$suffix", color = FadedInk, fontSize = 14.sp)
    }
    val colors = SliderDefaults.colors(thumbColor = Ink, activeTrackColor = Ink,
        inactiveTrackColor = Rule, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent)
    Slider(value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
        modifier = Modifier.semantics { contentDescription = label; stateDescription = "$value$suffix" },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0), onValueChangeFinished = onFinished,
        colors = colors,
        thumb = { Box(Modifier.size(20.dp).background(Ink, CircleShape)) },
        track = { state -> SliderDefaults.Track(state, Modifier.height(4.dp), colors = colors,
            drawStopIndicator = null, drawTick = { _, _ -> }, thumbTrackGapSize = 0.dp) })
}
