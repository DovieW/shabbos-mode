package dev.dovie.shabbosmode

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun PrepareScreen(graph: AppGraph, settings: AppSettings, checklist: List<ChecklistItem>,
                  hasMinyanTimes: Boolean, onRevise: () -> Unit) {
    val scope = rememberCoroutineScope()
    val week = TimeLogic.weekKey(Instant.ofEpochMilli(rememberNow()), TimeLogic.zone(settings))
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableLongStateOf(-1) }
    val selected = checklist.find { it.id == editingId }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Checklist", Modifier.weight(1f), color = FadedInk, fontFamily = PrintMono, fontSize = 13.sp)
        if (checklist.isNotEmpty()) Text("${checklist.count { it.doneWeek == week }} / ${checklist.size}", color = FadedInk, fontFamily = PrintMono, fontSize = 13.sp)
    }
    if (checklist.isEmpty()) EmptyState("No checklist items.")
    else {
        Spacer(Modifier.height(16.dp))
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
    Spacer(Modifier.height(24.dp))
    Column {
        Text("Minyan times", color = Ink, fontFamily = PrintSerif, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(if (!hasMinyanTimes) "No times saved" else if (settings.confirmedMinyanWeek == week) "Confirmed for this week" else "Using last week's times",
            color = FadedInk, fontFamily = PrintMono, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (hasMinyanTimes && settings.confirmedMinyanWeek != week) TextButton(onClick = {
                scope.launch { graph.settings.confirmMinyanWeek(week) }
            }) { Text("Keep times") }
            TextButton(onClick = onRevise) { Text(if (hasMinyanTimes) "Edit times" else "Add times") }
        }
    }
    if (adding || selected != null) {
        var label by rememberSaveable(selected?.id) { mutableStateOf(selected?.label ?: "") }
        var deleting by remember { mutableStateOf(false) }
        EditorDialog(if (selected == null) "New checklist item" else "Edit checklist item",
            onDismiss = { adding = false; editingId = -1 }, saveEnabled = label.isNotBlank(),
            onSave = {
                scope.launch { graph.dao.saveChecklist(selected?.copy(label = label.trim()) ?: ChecklistItem(label = label.trim())) }
                adding = false; editingId = -1
            }) {
            PaperField(label, { label = it }, "Item")
            if (selected != null) {
                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { deleting = true }) { Text("Delete item", color = Rust) }
            }
        }
        if (deleting && selected != null) ConfirmDelete("Delete item?", { deleting = false }) {
            scope.launch { graph.dao.deleteChecklist(selected) }; editingId = -1
        }
    }
}
