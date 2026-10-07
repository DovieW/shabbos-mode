package dev.dovie.shabbosmode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun TodoScreen(graph: AppGraph, settings: AppSettings, checklist: List<ChecklistItem>) {
    val scope = rememberCoroutineScope()
    val week = TimeLogic.weekKey(Instant.ofEpochMilli(rememberNow()), TimeLogic.zone(settings))
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableLongStateOf(-1) }
    val selected = checklist.find { it.id == editingId }
    if (checklist.isEmpty()) EmptyState("No items yet.")
    else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("${checklist.count { it.doneWeek == week }} / ${checklist.size}",
                color = FadedInk, fontFamily = PrintMono, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Column {
            checklist.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 60.dp)
                        .toggleable(item.doneWeek == week, role = Role.Checkbox, onValueChange = { checked ->
                            scope.launch { graph.dao.saveChecklist(item.copy(doneWeek = if (checked) week else "")) }
                        }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(item.doneWeek == week, onCheckedChange = null)
                        Text(item.label, Modifier.padding(start = 12.dp, end = 8.dp), color = Ink, fontSize = 16.sp)
                    }
                    MarkButton(Mark.Edit, "Edit ${item.label}") { editingId = item.id }
                }
                HorizontalDivider(color = Rule)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    PrimaryButton("Add item") { adding = true }
    if (adding || selected != null) {
        var label by rememberSaveable(selected?.id) { mutableStateOf(selected?.label ?: "") }
        var deleting by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        EditorDialog(if (selected == null) "New item" else "Edit item",
            onDismiss = { if (!saving) { adding = false; editingId = -1 } },
            saveEnabled = label.isNotBlank() && !saving,
            onSave = {
                if (!saving && label.isNotBlank()) {
                    saving = true
                    error = ""
                    val draft = selected?.copy(label = label.trim()) ?: ChecklistItem(label = label.trim())
                    scope.launch {
                        runCatching { withContext(NonCancellable) { graph.dao.saveChecklist(draft) } }
                            .onSuccess { adding = false; editingId = -1 }
                            .onFailure { error = "Couldn't save. Try again." }
                        saving = false
                    }
                }
            }) {
            PaperField(label, { label = it }, "Item")
            if (error.isNotBlank()) Text(error, color = Rust, fontSize = 13.sp)
            if (selected != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { deleting = true }, enabled = !saving) { Text("Delete item", color = Rust) }
            }
        }
        if (deleting && selected != null) ConfirmDelete("Delete item?", { deleting = false }) {
            scope.launch { graph.dao.deleteChecklist(selected) }; editingId = -1
        }
    }
}
