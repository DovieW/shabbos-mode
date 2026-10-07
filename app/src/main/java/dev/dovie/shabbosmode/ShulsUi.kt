package dev.dovie.shabbosmode

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.abs

@Composable
fun ShulsScreen(graph: AppGraph, settings: AppSettings, shuls: List<ShulItem>, minyanim: List<MinyanItem>, events: List<TimeEvent>) {
    val scope = rememberCoroutineScope()
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    var editShulId by rememberSaveable { mutableLongStateOf(-1) }
    var addShul by rememberSaveable { mutableStateOf(false) }
    var editMinyanId by rememberSaveable { mutableLongStateOf(-1) }
    var addMinyanTo by rememberSaveable { mutableLongStateOf(-1) }
    val editShul = shuls.find { it.id == editShulId }
    val editMinyan = minyanim.find { it.id == editMinyanId }
    val week = TimeLogic.weekKey(Instant.ofEpochMilli(rememberNow()), TimeLogic.zone(settings))
    if (minyanim.any { minyan -> shuls.any { it.id == minyan.shulId } }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (settings.confirmedMinyanWeek == week) "Confirmed for this week" else "Weekly times",
                Modifier.weight(1f), color = FadedInk, fontFamily = PrintMono, fontSize = 12.sp)
            if (settings.confirmedMinyanWeek != week) TextButton(onClick = {
                scope.launch { graph.settings.confirmMinyanWeek(week) }
            }) { Text("Keep times") }
        }
        Spacer(Modifier.height(12.dp))
    }
    if (shuls.isEmpty()) EmptyState("No shuls saved.")
    shuls.forEach { shul ->
        PaperPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(shul.name, Modifier.weight(1f), color = Ink, fontFamily = PrintSerif, fontSize = 21.sp, fontWeight = FontWeight.Medium)
                MarkButton(Mark.Edit, "Edit ${shul.name}") { editShulId = shul.id }
            }
            if (shul.address.isNotBlank()) Text(shul.address, color = FadedInk, fontSize = 13.sp)
            val times = minyanim.filter { it.shulId == shul.id }
            times.forEach { minyan ->
                val detail = minyan.candleOffsetMinutes?.let { offset ->
                    val resolved = TimeLogic.nextMinyanTime(minyan, Instant.ofEpochMilli(rememberNow()),
                        TimeLogic.zone(settings), events, settings)
                    candleOffsetLabel(offset) + "\n" + (resolved?.let { formatLocalTime(it, TimeLogic.zone(settings), use24Hour) }
                        ?: "Awaiting times")
                } ?: "${if (minyan.day == 5) "Friday" else "Saturday"} · ${formatWallTime(minyan.hour, minyan.minute, use24Hour)}"
                NavigationRow(minyan.label, detail, detailMaxLines = if (minyan.candleOffsetMinutes == null) 2 else 4) {
                    editMinyanId = minyan.id
                }
            }
            TextButton(onClick = { addMinyanTo = shul.id }) {
                MarkIcon(Mark.Plus); Spacer(Modifier.width(8.dp)); Text("Add minyan")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Add shul") { addShul = true }
    if (addShul || editShul != null) ShulEditor(editShul,
        onDismiss = { addShul = false; editShulId = -1 },
        onSave = { item -> scope.launch { graph.dao.saveShul(item) }; addShul = false; editShulId = -1 },
        onDelete = { item -> scope.launch {
            graph.dao.deleteMinyanimForShul(item.id); graph.dao.deleteShul(item); graph.scheduler.reschedule()
        }; editShulId = -1 })
    if (addMinyanTo >= 0 || editMinyan != null) MinyanEditor(editMinyan, editMinyan?.shulId ?: addMinyanTo, settings, events,
        onDismiss = { addMinyanTo = -1; editMinyanId = -1 },
        onSave = { item -> scope.launch { graph.dao.saveMinyan(item); graph.scheduler.reschedule() }; addMinyanTo = -1; editMinyanId = -1 },
        onDelete = { item -> scope.launch { graph.dao.deleteMinyan(item); graph.scheduler.reschedule() }; editMinyanId = -1 })
}

@Composable
private fun ShulEditor(item: ShulItem?, onDismiss: () -> Unit, onSave: (ShulItem) -> Unit, onDelete: (ShulItem) -> Unit) {
    var name by rememberSaveable(item?.id) { mutableStateOf(item?.name ?: "") }
    var address by rememberSaveable(item?.id) { mutableStateOf(item?.address ?: "") }
    var deleting by remember { mutableStateOf(false) }
    EditorDialog(if (item == null) "New shul" else "Edit shul", onDismiss,
        saveEnabled = name.isNotBlank(), onSave = { onSave(ShulItem(item?.id ?: 0, name.trim(), address.trim())) }) {
        PaperField(name, { name = it }, "Name")
        Spacer(Modifier.height(20.dp))
        PaperField(address, { address = it }, "Address · optional", singleLine = false)
        if (item != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { deleting = true }) { Text("Delete shul", color = Rust) }
        }
    }
    if (deleting && item != null) ConfirmDelete("Delete shul and its times?", { deleting = false }) { onDelete(item) }
}

@Composable
private fun MinyanEditor(item: MinyanItem?, shulId: Long, settings: AppSettings, events: List<TimeEvent>, onDismiss: () -> Unit,
                         onSave: (MinyanItem) -> Unit, onDelete: (MinyanItem) -> Unit) {
    val context = LocalContext.current
    val use24Hour = DateFormat.is24HourFormat(context)
    var label by rememberSaveable(item?.id) { mutableStateOf(item?.label ?: "Shacharis") }
    var day by rememberSaveable(item?.id) { mutableIntStateOf(item?.day ?: 6) }
    var hour by rememberSaveable(item?.id) { mutableIntStateOf(item?.hour ?: 9) }
    var minute by rememberSaveable(item?.id) { mutableIntStateOf(item?.minute ?: 0) }
    var relative by rememberSaveable(item?.id) { mutableStateOf(item?.candleOffsetMinutes != null) }
    var before by rememberSaveable(item?.id) { mutableStateOf((item?.candleOffsetMinutes ?: -15) < 0) }
    var offsetText by rememberSaveable(item?.id) { mutableStateOf(abs(item?.candleOffsetMinutes ?: -15).toString()) }
    val minutes = offsetText.toIntOrNull()?.takeIf { it in 0..1440 }
    val offset = minutes?.let { if (before) -it else it }
    var submitted by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    EditorDialog(if (item == null) "New minyan" else "Edit minyan", onDismiss,
        saveEnabled = label.isNotBlank() && (!relative || offset != null) && !submitted, onSave = {
            if (!submitted && label.isNotBlank() && (!relative || offset != null)) {
                submitted = true
                onSave(MinyanItem(item?.id ?: 0, shulId, label.trim(), if (relative) 5 else day,
                    hour, minute, if (relative) offset else null))
            }
        }) {
        PaperField(label, { label = it }, "Name")
        Spacer(Modifier.height(24.dp))
        ChoiceChips(listOf(false to "Fixed time", true to "Candle lighting"), relative) { relative = it }
        Spacer(Modifier.height(20.dp))
        if (relative) {
            ChoiceChips(listOf(true to "Before", false to "After"), before) { before = it }
            Spacer(Modifier.height(20.dp))
            PaperField(offsetText, { offsetText = it.filter(Char::isDigit).take(4) }, "Minutes",
                keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(16.dp))
            if (offset != null) {
                val draft = MinyanItem(shulId = shulId, label = label, day = 5, hour = hour,
                    minute = minute, candleOffsetMinutes = offset)
                val now = rememberNow()
                val resolved = TimeLogic.nextMinyanTime(draft, Instant.ofEpochMilli(now),
                    TimeLogic.zone(settings), events, settings)
                Text(resolved?.let { formatNextTime(it, now, TimeLogic.zone(settings), use24Hour) }
                    ?: "Awaiting times", color = FadedInk, fontFamily = PrintMono, fontSize = 13.sp)
            } else if (offsetText.isNotBlank()) Text("Use 0–1440 minutes.", color = Rust, fontSize = 13.sp)
        } else {
            ChoiceChips(listOf(5 to "Friday", 6 to "Saturday"), day) { day = it }
            NavigationRow("Time", formatWallTime(hour, minute, use24Hour)) {
                TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, use24Hour).show()
            }
        }
        if (item != null) {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { deleting = true }) { Text("Delete minyan", color = Rust) }
        }
    }
    if (deleting && item != null) ConfirmDelete("Delete minyan?", { deleting = false }) { onDelete(item) }
}

private fun candleOffsetLabel(minutes: Int): String = when {
    minutes == 0 -> "At candle lighting"
    minutes < 0 -> "${abs(minutes)} min before candle lighting"
    else -> "$minutes min after candle lighting"
}
