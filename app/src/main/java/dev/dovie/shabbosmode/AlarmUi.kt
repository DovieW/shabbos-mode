package dev.dovie.shabbosmode

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@Composable
fun AlarmsScreen(graph: AppGraph, settings: AppSettings, alarms: List<AlarmItem>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AlarmItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val exactAccess = remember(permissionTick) { graph.scheduler.canScheduleExact() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    if (!exactAccess) {
        Text("Exact alarm access is required.", color = Rust, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("ALLOW ALARMS & REMINDERS") {
            AppScheduler.requestExactAlarmAccess(context)
            permissionTick++
        }
        Spacer(Modifier.height(20.dp))
    }
    if (!graph.scheduler.canPostNotifications()) {
        Text("Allow notifications to see alarm alerts.", color = Rust, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("ALLOW NOTIFICATIONS") {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Spacer(Modifier.height(20.dp))
    }
    alarms.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.weight(1f).clickable { editing = item }) {
                Text(
                    "%d:%02d".format(item.hour, item.minute),
                    color = Ink, fontFamily = FontFamily.Serif, fontSize = 27.sp
                )
                Text(
                    "${item.label} · ${when (item.repeatDay) { 5 -> "Friday"; 6 -> "Saturday"; else -> "One time" }}" +
                        if (!exactAccess) " · Not scheduled" else "",
                    color = FadedInk, fontSize = 13.sp
                )
            }
            Switch(checked = item.enabled, onCheckedChange = { enabled ->
                scope.launch {
                    graph.dao.saveAlarm(item.copy(enabled = enabled))
                    graph.scheduler.reschedule()
                }
            })
        }
    }
    Spacer(Modifier.height(20.dp))
    PrimaryButton("ADD ALARM") { adding = true }

    val selected = editing
    if (adding || selected != null) {
        AlarmEditor(
            item = selected,
            settings = settings,
            onDismiss = { adding = false; editing = null },
            onSave = { item ->
                scope.launch {
                    graph.dao.saveAlarm(item)
                    graph.scheduler.reschedule()
                }
                adding = false
                editing = null
            },
            onDelete = { item ->
                scope.launch {
                    graph.dao.deleteAlarm(item)
                    graph.scheduler.reschedule()
                }
                editing = null
            }
        )
    }
}

@Composable
private fun AlarmEditor(
    item: AlarmItem?,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AlarmItem) -> Unit,
    onDelete: (AlarmItem) -> Unit
) {
    val context = LocalContext.current
    val zone = TimeLogic.zone(settings)
    val nextSaturday = remember {
        TimeLogic.friday(Instant.now(), zone).plusDays(1)
    }
    var label by remember(item) { mutableStateOf(item?.label ?: "Alarm") }
    var hour by remember(item) { mutableIntStateOf(item?.hour ?: 8) }
    var minute by remember(item) { mutableIntStateOf(item?.minute ?: 0) }
    var repeatDay by remember(item) { mutableIntStateOf(item?.repeatDay ?: 6) }
    var date by remember(item) {
        mutableStateOf(
            if (item != null && item.oneTimeMillis > 0)
                Instant.ofEpochMilli(item.oneTimeMillis).atZone(zone).toLocalDate()
            else nextSaturday
        )
    }
    var tone by remember(item) { mutableStateOf(item?.tone ?: "soft") }
    var volume by remember(item) { mutableIntStateOf(item?.volume ?: 70) }
    var vibrate by remember(item) { mutableIntStateOf(item?.vibrationSeconds ?: 20) }
    var ramp by remember(item) { mutableIntStateOf(item?.rampSeconds ?: 60) }
    var duration by remember(item) { mutableIntStateOf(item?.durationMinutes ?: 5) }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            tone = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "New alarm" else "Edit alarm", color = Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("TIME  %d:%02d".format(hour, minute)) {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m },
                        hour, minute, false).show()
                }
                Row {
                    listOf(0 to "Once", 5 to "Fri", 6 to "Sat").forEach { (day, name) ->
                        FilterChip(
                            selected = repeatDay == day,
                            onClick = { repeatDay = day },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }
                if (repeatDay == 0) {
                    SecondaryButton("DATE  $date") {
                        DatePickerDialog(context, { _, y, m, d ->
                            date = LocalDate.of(y, m + 1, d)
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Tone", color = Ink)
                Row {
                    listOf("soft", "chime", "warm").forEach { name ->
                        FilterChip(
                            selected = tone == name,
                            onClick = { tone = name },
                            label = { Text(name) },
                            modifier = Modifier.padding(end = 5.dp)
                        )
                    }
                }
                SecondaryButton(if (tone.startsWith("content:")) "CUSTOM AUDIO SELECTED"
                    else "IMPORT AUDIO") { importAudio.launch(arrayOf("audio/*")) }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("TEST 8 SECONDS") {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, AlarmPlaybackService::class.java)
                            .putExtra("preview", true)
                            .putExtra("tone", tone)
                            .putExtra("volume", volume)
                            .putExtra("vibrate", vibrate)
                            .putExtra("ramp", ramp)
                    )
                }
                SettingSlider("Volume", volume, 0..100) { volume = it }
                SettingSlider("Vibrate first · seconds", vibrate, 0..60) { vibrate = it }
                SettingSlider("Volume rise · seconds", ramp, 0..120) { ramp = it }
                SettingSlider("Stop after · minutes", duration, 1..20) { duration = it }
                if (item != null) {
                    TextButton(onClick = { onDelete(item) }) { Text("Delete alarm", color = Rust) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = repeatDay != 0 ||
                    TimeLogic.at(date, hour, minute, zone) > System.currentTimeMillis(),
                onClick = {
                    onSave(
                        AlarmItem(
                            id = item?.id ?: 0, label = label.trim().ifBlank { "Alarm" },
                            hour = hour, minute = minute, repeatDay = repeatDay,
                            oneTimeMillis = if (repeatDay == 0)
                                TimeLogic.at(date, hour, minute, zone) else 0,
                            tone = tone, volume = volume, vibrationSeconds = vibrate,
                            rampSeconds = ramp, durationMinutes = duration,
                            enabled = item?.enabled ?: true
                        )
                    )
                }
            ) { Text("Save", color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FadedInk) } },
        containerColor = LightPaper
    )
}

@Composable
fun SettingSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Text("$label  $value", color = Ink, fontSize = 13.sp)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = Ink, activeTrackColor = Ink,
            inactiveTrackColor = FadedInk.copy(alpha = 0.35f)
        )
    )
}
