package dev.dovie.shabbosmode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Paper = Color(0xFFE9DBC0)
val LightPaper = Color(0xFFF4E9D2)
val Ink = Color(0xFF39291E)
val Rust = Color(0xFFAE7044)
val FadedInk = Color(0xFF765C45)
val Night = Color(0xFF130F0B)
val NightInk = Color(0xFFD6B994)

@Composable
fun AppRoot(graph: AppGraph, page: MutableState<String>) {
    BackHandler(enabled = page.value != "home") { page.value = "home" }
    val settings by graph.settings.flow.collectAsState(initial = AppSettings())
    val alarms by graph.dao.alarms().collectAsState(initial = emptyList())
    val shuls by graph.dao.shuls().collectAsState(initial = emptyList())
    val minyanim by graph.dao.minyanim().collectAsState(initial = emptyList())
    val checklist by graph.dao.checklist().collectAsState(initial = emptyList())
    val events by graph.dao.events().collectAsState(initial = emptyList())

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = LightPaper,
            secondary = Rust,
            onSecondary = LightPaper,
            surface = LightPaper,
            onSurface = Ink,
            surfaceVariant = Paper,
            onSurfaceVariant = FadedInk,
            outline = FadedInk,
            surfaceContainer = LightPaper,
            surfaceContainerHigh = LightPaper,
            inverseSurface = Ink,
            inverseOnSurface = LightPaper
        )
    ) {
        if (page.value == "clock") {
            ClockScreen(graph, settings, shuls, minyanim, events) { page.value = "home" }
        } else {
            Scaffold(containerColor = Paper) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page.value != "home") {
                            Text(
                                "←", modifier = Modifier.clickable { page.value = "home" }
                                    .padding(end = 18.dp),
                                color = Ink, fontSize = 26.sp
                            )
                        }
                        Text(
                            when (page.value) {
                                "prepare" -> "PREPARE"
                                "alarms" -> "ALARMS"
                                "shuls" -> "SHULS"
                                "settings" -> "SETTINGS"
                                else -> "SHABBOS MODE"
                            },
                            color = Ink, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                    when (page.value) {
                        "prepare" -> PrepareScreen(graph, settings, checklist, events) {
                            page.value = "shuls"
                        }
                        "alarms" -> AlarmsScreen(graph, settings, alarms)
                        "shuls" -> ShulsScreen(graph, settings, shuls, minyanim)
                        "settings" -> SettingsScreen(graph, settings, events, minyanim)
                        else -> HomeScreen(
                            graph, settings, alarms, shuls, minyanim, events,
                            onNavigate = { page.value = it }
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    graph: AppGraph,
    settings: AppSettings,
    alarms: List<AlarmItem>,
    shuls: List<ShulItem>,
    minyanim: List<MinyanItem>,
    events: List<TimeEvent>,
    onNavigate: (String) -> Unit
) {
    val now = Instant.now()
    val zone = TimeLogic.zone(settings)
    val next = TimeLogic.nextMinyan(minyanim, shuls, now, zone)
    val upcoming = events.firstOrNull {
        (it.key == "start" || it.key == "end") && it.atMillis > now.toEpochMilli()
    }
    Text(
        if (settings.city.isBlank()) "Choose location"
        else upcoming?.let { formatTime(it.atMillis, zone) } ?: "Times updating",
        modifier = Modifier.clickable { if (settings.city.isBlank()) onNavigate("settings") },
        color = Ink, fontFamily = FontFamily.Serif, fontSize = 34.sp
    )
    Spacer(Modifier.height(12.dp))
    Text(
        if (settings.city.isBlank()) "Set a city in Settings." else settings.city,
        color = FadedInk, fontSize = 14.sp
    )
    Spacer(Modifier.height(36.dp))
    PrimaryButton("OPEN CLOCK") { onNavigate("clock") }
    Spacer(Modifier.height(32.dp))
    NavRow("Prepare", "Checklist") { onNavigate("prepare") }
    NavRow(
        "Alarms",
        if (graph.scheduler.canScheduleExact()) "${alarms.count { it.enabled }} active"
        else "Access needed"
    ) { onNavigate("alarms") }
    NavRow("Shuls", next?.let { "${shuls.find { s -> s.id == it.first.shulId }?.name ?: ""} · ${formatTime(it.second, zone)}" }
        ?: "${shuls.size} saved") { onNavigate("shuls") }
    NavRow("Settings", if (settings.city.isBlank()) "Location needed" else settings.city) {
        onNavigate("settings")
    }
    if (!graph.scheduler.canScheduleExact()) {
        Spacer(Modifier.height(20.dp))
        Text("Alarm access needed", color = Rust, fontSize = 14.sp)
    }
}

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = LightPaper)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RectangleShape,
        border = BorderStroke(1.dp, FadedInk),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    ) { Text(label, color = Ink, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
}

@Composable
fun NavRow(title: String, detail: String = "", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Ink, fontFamily = FontFamily.Serif, fontSize = 24.sp)
        Text(detail, color = FadedInk, fontSize = 12.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.25f)))
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Ink, fontFamily = FontFamily.Serif, fontSize = 27.sp)
    Spacer(Modifier.height(16.dp))
}

fun formatTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("EEE h:mm a"))

fun formatClock(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("h:mm"))
