package dev.dovie.shabbosmode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun PrepareScreen(
    graph: AppGraph,
    settings: AppSettings,
    checklist: List<ChecklistItem>,
    events: List<TimeEvent>,
    onRevise: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val week = TimeLogic.weekKey(Instant.now(), TimeLogic.zone(settings))
    val start = events.firstOrNull { it.key == "start" && it.week == week }
    var newItem by remember { mutableStateOf("") }

    SectionTitle("Friday")
    Text(
        start?.let { "Starts ${formatTime(
            if (settings.overrideWeek == week && settings.overrideStart > 0)
                settings.overrideStart else it.atMillis,
            TimeLogic.zone(settings)
        )}" } ?: "Set a location for Shabbos times.",
        color = FadedInk, fontSize = 14.sp
    )
    Spacer(Modifier.height(28.dp))
    Text("Checklist", color = Ink, fontFamily = FontFamily.Serif, fontSize = 23.sp)
    Spacer(Modifier.height(8.dp))
    checklist.forEach { item ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.doneWeek == week,
                onCheckedChange = { checked ->
                    scope.launch {
                        graph.dao.saveChecklist(item.copy(doneWeek = if (checked) week else ""))
                    }
                }
            )
            Text(item.label, modifier = Modifier.weight(1f), color = Ink, fontSize = 16.sp)
            Text("×", modifier = Modifier.clickable {
                scope.launch { graph.dao.deleteChecklist(item) }
            }.padding(10.dp), color = FadedInk, fontSize = 20.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = newItem,
        onValueChange = { newItem = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Add item") },
        singleLine = true
    )
    Spacer(Modifier.height(10.dp))
    SecondaryButton("ADD TO CHECKLIST") {
        val label = newItem.trim()
        if (label.isNotBlank()) {
            scope.launch { graph.dao.saveChecklist(ChecklistItem(label = label)) }
            newItem = ""
        }
    }
    Spacer(Modifier.height(34.dp))
    Text("Minyan times", color = Ink, fontFamily = FontFamily.Serif, fontSize = 23.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        if (settings.confirmedMinyanWeek == week) "Using saved times this week."
        else "Saved times continue from last week.",
        color = FadedInk, fontSize = 14.sp
    )
    Spacer(Modifier.height(14.dp))
    SecondaryButton("KEEP LAST WEEK'S TIMES") {
        scope.launch { graph.settings.confirmMinyanWeek(week) }
    }
    Spacer(Modifier.height(8.dp))
    SecondaryButton("REVISE TIMES") { onRevise() }
}
