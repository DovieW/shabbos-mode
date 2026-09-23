package dev.dovie.shabbosmode

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ShulsScreen(
    graph: AppGraph,
    settings: AppSettings,
    shuls: List<ShulItem>,
    minyanim: List<MinyanItem>
) {
    val scope = rememberCoroutineScope()
    var editShul by remember { mutableStateOf<ShulItem?>(null) }
    var addShul by remember { mutableStateOf(false) }
    var editMinyan by remember { mutableStateOf<MinyanItem?>(null) }
    var addMinyanTo by remember { mutableStateOf<Long?>(null) }

    SectionTitle("Saved shuls")
    Text("Times stay saved until you change them.", color = FadedInk, fontSize = 14.sp)
    Spacer(Modifier.height(24.dp))
    shuls.forEach { shul ->
        Text(
            shul.name, modifier = Modifier.clickable { editShul = shul },
            color = Ink, fontFamily = FontFamily.Serif, fontSize = 24.sp
        )
        if (shul.address.isNotBlank()) {
            Text(shul.address, color = FadedInk, fontSize = 13.sp)
        }
        minyanim.filter { it.shulId == shul.id }.forEach { minyan ->
            Row(Modifier.fillMaxWidth().clickable { editMinyan = minyan }
                .padding(vertical = 8.dp)) {
                Text(
                    if (minyan.day == 5) "Fri" else "Sat",
                    color = FadedInk, fontFamily = FontFamily.Monospace, fontSize = 13.sp
                )
                Text(
                    "  %d:%02d  %s".format(minyan.hour, minyan.minute, minyan.label),
                    color = Ink, fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        SecondaryButton("ADD MINYAN") { addMinyanTo = shul.id }
        Spacer(Modifier.height(28.dp))
    }
    PrimaryButton("ADD SHUL") { addShul = true }

    if (addShul || editShul != null) {
        ShulEditor(
            item = editShul,
            onDismiss = { addShul = false; editShul = null },
            onSave = { item ->
                scope.launch { graph.dao.saveShul(item) }
                addShul = false; editShul = null
            },
            onDelete = { item ->
                scope.launch {
                    graph.dao.deleteMinyanimForShul(item.id)
                    graph.dao.deleteShul(item)
                    graph.scheduler.reschedule()
                }
                editShul = null
            }
        )
    }
    if (addMinyanTo != null || editMinyan != null) {
        MinyanEditor(
            item = editMinyan,
            shulId = editMinyan?.shulId ?: addMinyanTo!!,
            onDismiss = { addMinyanTo = null; editMinyan = null },
            onSave = { item ->
                scope.launch {
                    graph.dao.saveMinyan(item)
                    graph.scheduler.reschedule()
                }
                addMinyanTo = null; editMinyan = null
            },
            onDelete = { item ->
                scope.launch {
                    graph.dao.deleteMinyan(item)
                    graph.scheduler.reschedule()
                }
                editMinyan = null
            }
        )
    }
}

@Composable
private fun ShulEditor(
    item: ShulItem?,
    onDismiss: () -> Unit,
    onSave: (ShulItem) -> Unit,
    onDelete: (ShulItem) -> Unit
) {
    var name by remember(item) { mutableStateOf(item?.name ?: "") }
    var address by remember(item) { mutableStateOf(item?.address ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add shul" else "Edit shul", color = Ink) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Address") }
                )
                if (item != null) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = { onDelete(item) }) {
                        Text("Delete shul", color = Rust)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(ShulItem(item?.id ?: 0, name.trim(), address.trim()))
            }) { Text("Save", color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FadedInk) } },
        containerColor = LightPaper
    )
}

@Composable
private fun MinyanEditor(
    item: MinyanItem?,
    shulId: Long,
    onDismiss: () -> Unit,
    onSave: (MinyanItem) -> Unit,
    onDelete: (MinyanItem) -> Unit
) {
    val context = LocalContext.current
    var label by remember(item) { mutableStateOf(item?.label ?: "Shacharis") }
    var day by remember(item) { mutableIntStateOf(item?.day ?: 6) }
    var hour by remember(item) { mutableIntStateOf(item?.hour ?: 9) }
    var minute by remember(item) { mutableIntStateOf(item?.minute ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Add minyan" else "Edit minyan", color = Ink) },
        text = {
            Column {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                Row {
                    listOf(5 to "Friday", 6 to "Saturday").forEach { (value, title) ->
                        FilterChip(
                            selected = day == value, onClick = { day = value },
                            label = { Text(title) }, modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                SecondaryButton("TIME  %d:%02d".format(hour, minute)) {
                    TimePickerDialog(context, { _, h, m -> hour = h; minute = m },
                        hour, minute, false).show()
                }
                if (item != null) {
                    TextButton(onClick = { onDelete(item) }) {
                        Text("Delete minyan", color = Rust)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = {
                onSave(MinyanItem(item?.id ?: 0, shulId, label.trim(), day, hour, minute))
            }) { Text("Save", color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = FadedInk) } },
        containerColor = LightPaper
    )
}
