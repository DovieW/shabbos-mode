package dev.dovie.shabbosmode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(graph: AppGraph, settings: AppSettings) {
    var step by rememberSaveable { mutableIntStateOf(if (settings.latitude == null) 0 else 1) }
    var candles by rememberSaveable { mutableStateOf(settings.candleMinutes.toString()) }
    var havdalah by rememberSaveable { mutableStateOf(settings.havdalahMinutes.toString()) }
    var tradition by rememberSaveable { mutableStateOf(if (settings.timingConfigured) settings.tradition.name else ZmanTradition.BOTH.name) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val duration = quietDuration()
    BackHandler(step > 0) { if (!saving) step-- }
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) MarkButton(Mark.Back, "Back") { if (!saving) step-- }
                else CandleMark(Modifier.padding(start = 12.dp, end = 12.dp))
                Text("Shabbos mode", Modifier.padding(start = 8.dp), fontFamily = PrintSerif, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Rule)
            AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) }, label = "Setup") { current ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp)) {
                        Text(when (current) { 0 -> "Your location"; 1 -> "Your Shabbos"; else -> "Your zmanim" },
                            fontFamily = PrintSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
                        Text(when (current) { 0 -> "For local times. No background tracking."; 1 -> settings.city; else -> "Use your shul’s calculation." },
                            Modifier.padding(top = 6.dp, bottom = 20.dp), color = FadedInk, fontSize = 14.sp)
                        when (current) {
                            0 -> {
                                LocationChooser(graph) { step = 1 }
                                if (settings.latitude != null) TextButton(onClick = { step = 1 }) { Text("Keep ${settings.city.substringBefore(',')}") }
                            }
                            1 -> BoundaryChoices(settings, candles, { candles = it }, havdalah, { havdalah = it })
                            else -> TraditionChoices(ZmanTradition.valueOf(tradition)) { tradition = it.name }
                        }
                        if (error.isNotBlank()) Text(error, color = Rust, modifier = Modifier.padding(top = 12.dp))
                        if (current > 0) {
                            Spacer(Modifier.height(24.dp))
                            PrimaryButton(if (saving) "Saving…" else if (current == 1) "Continue" else "Done",
                                enabled = !saving && validTiming(candles, havdalah)) {
                                if (current == 1) step = 2 else {
                                    saving = true
                                    scope.launch {
                                        runCatching {
                                            graph.saveTiming(ZmanTradition.valueOf(tradition), candles.toInt(),
                                                havdalah.toInt(), settings.israelCalendar, complete = true)
                                        }.onFailure { error = "Couldn't save. Try again."; saving = false }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun validTiming(candles: String, havdalah: String) =
    candles.toIntOrNull()?.let { it in 0..90 } == true && havdalah.toIntOrNull()?.let { it in 0..120 } == true

@Composable
private fun PracticeChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) Ink else LightPaper,
        tween(quietDuration()), label = "Practice choice")
    val foreground = if (selected) LightPaper else Ink
    Row(Modifier.fillMaxWidth().clip(CutCornerShape(bottomEnd = 6.dp)).background(background)
        .heightIn(min = 68.dp).selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = foreground, fontFamily = PrintSans, fontWeight = FontWeight.Medium,
                fontSize = 17.sp, lineHeight = 23.sp)
            Text(detail, color = if (selected) Paper else FadedInk, fontSize = 13.sp, lineHeight = 18.sp)
        }
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(
            selectedColor = LightPaper, unselectedColor = FadedInk))
    }
}

@Composable
private fun TraditionChoices(selected: ZmanTradition, onSelect: (ZmanTradition) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ZmanTradition.entries.forEach { practice ->
            PracticeChoice(practice.title, practice.detail, selected == practice) { onSelect(practice) }
        }
    }
}

@Composable
private fun BoundaryChoices(settings: AppSettings, candles: String, onCandles: (String) -> Unit,
                            havdalah: String, onHavdalah: (String) -> Unit) {
    var editCandles by rememberSaveable { mutableStateOf(false) }
    var otherEnd by rememberSaveable { mutableStateOf(havdalah !in setOf("0", "72")) }
    val lead = candles.toIntOrNull()?.takeIf { it > 0 } ?: TimingPractice.candleLead(settings.copy(candleMinutes = 0))
    SettingsGroup("Candle lighting", "$lead min before sunset", editCandles, { editCandles = !editCandles }, compact = true) {
        ChoiceChips(listOf("0" to "Local", "18" to "18", "20" to "20", "30" to "30", "40" to "40"), candles, onCandles)
        Spacer(Modifier.height(16.dp))
        PaperField(if (candles == "0") "" else candles, { onCandles(it.filter(Char::isDigit).take(3)) }, "Other minutes · 1–90", keyboardType = KeyboardType.Number)
        if (candles.toIntOrNull()?.let { it in 0..90 } != true) Text("Enter 1–90 minutes.", color = Rust, fontSize = 13.sp)
    }
    Spacer(Modifier.height(16.dp))
    SectionTitle("Shabbos ends")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PracticeChoice("Nightfall", "Three small stars · 8.5°", havdalah == "0") { onHavdalah("0") }
        PracticeChoice("72 minutes", "After sunset · fixed Rabbeinu Tam", havdalah == "72") { onHavdalah("72") }
    }
    Spacer(Modifier.height(6.dp))
    SettingsGroup("Other timing", if (havdalah !in setOf("0", "72")) "$havdalah min after sunset" else "Use a fixed interval", otherEnd, { otherEnd = !otherEnd }, compact = true) {
        ChoiceChips(listOf("42" to "42 min", "50" to "50 min"), havdalah, onHavdalah)
        Spacer(Modifier.height(16.dp))
        PaperField(if (havdalah == "0") "" else havdalah, { onHavdalah(it.filter(Char::isDigit).take(3)) }, "Other minutes · 1–120", keyboardType = KeyboardType.Number)
        if (havdalah.toIntOrNull()?.let { it in 0..120 } != true) Text("Enter 1–120 minutes.", color = Rust, fontSize = 13.sp)
    }
    Text("Match your community’s published times.", Modifier.padding(top = 16.dp), color = FadedInk, fontSize = 13.sp)
}

@Composable
fun PracticeEditor(graph: AppGraph, settings: AppSettings, onDismiss: () -> Unit) {
    var tradition by rememberSaveable { mutableStateOf(settings.tradition.name) }
    var candles by rememberSaveable { mutableStateOf(settings.candleMinutes.toString()) }
    var havdalah by rememberSaveable { mutableStateOf(settings.havdalahMinutes.toString()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    EditorDialog("Timing practice", onDismiss, onSave = {
        saving = true
        scope.launch {
            runCatching { graph.saveTiming(ZmanTradition.valueOf(tradition), candles.toInt(), havdalah.toInt(), settings.israelCalendar) }
                .onSuccess { onDismiss() }.onFailure { error = "Couldn't save. Try again."; saving = false }
        }
    }, saveEnabled = !saving && validTiming(candles, havdalah)) {
        TraditionChoices(ZmanTradition.valueOf(tradition)) { tradition = it.name }
        Spacer(Modifier.height(24.dp))
        BoundaryChoices(settings, candles, { candles = it }, havdalah, { havdalah = it })
        if (error.isNotBlank()) Text(error, color = Rust)
    }
}
