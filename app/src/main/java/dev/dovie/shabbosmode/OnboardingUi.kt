package dev.dovie.shabbosmode

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(graph: AppGraph, settings: AppSettings, onExit: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(-1) }
    var candles by rememberSaveable { mutableStateOf(settings.candleMinutes.toString()) }
    var havdalah by rememberSaveable { mutableStateOf(settings.havdalahMinutes.toString()) }
    var tradition by rememberSaveable { mutableStateOf(if (settings.timingConfigured) settings.tradition.name else ZmanTradition.BOTH.name) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val duration = quietDuration()
    BackHandler(step >= 0) { if (!saving) step-- }
    if (step < 0) {
        WelcomeScreen(saving, onStart = { step = if (settings.latitude == null) 0 else 1 }, onLater = {
            saving = true
            scope.launch {
                runCatching {
                    withContext(NonCancellable) {
                        graph.deferOnboarding()
                        onExit()
                    }
                }.onFailure { saving = false; error = "Couldn't save. Try again." }
            }
        })
        if (error.isNotBlank()) TimingHelp("Couldn't save", onDismiss = { error = "" }) { Text("Try again.") }
        return
    }
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp)) {
                    MarkButton(Mark.Back, "Back") { if (!saving) step-- }
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.semantics {
                    contentDescription = "Setup progress"
                    stateDescription = "Step ${step + 1} of 4"
                }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { index ->
                        val color by animateColorAsState(if (index <= step) Ink else Rule,
                            tween(duration), label = "Setup progress")
                        Box(Modifier.size(5.dp).background(color))
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CandleMark() }
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Rule)
            AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) }, label = "Setup") { current ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp)) {
                        if (current > 0) Text("Zmanim preferences", Modifier.padding(bottom = 8.dp),
                            color = FadedInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(when (current) {
                            0 -> "Location"
                            1 -> "Candle lighting"
                            2 -> "Daytime calculation"
                            else -> "Shabbos ends"
                        }, fontFamily = PrintSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
                        if (current == 1) Text("Before sunset",
                            Modifier.padding(top = 6.dp), color = FadedInk, fontSize = 14.sp)
                        Spacer(Modifier.height(20.dp))
                        when (current) {
                            0 -> {
                                LocationChooser(graph) { step = 1 }
                                if (settings.latitude != null) TextButton(onClick = { step = 1 }) {
                                    Text("Keep ${shortCity(settings.city)}")
                                }
                            }
                            1 -> CandleLightingChoices(settings, candles) { candles = it }
                            2 -> DaytimeChoices(ZmanTradition.valueOf(tradition)) { tradition = it.name }
                            else -> ShabbosEndChoices(havdalah) { havdalah = it }
                        }
                        if (error.isNotBlank()) Text(error, color = Rust, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
            if (step > 0) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        PrimaryButton(if (saving) "Saving…" else if (step < 3) "Continue" else "Done",
                            enabled = !saving && validTiming(candles, havdalah)) {
                            if (step < 3) step++ else {
                                saving = true
                                scope.launch {
                                    runCatching {
                                        withContext(NonCancellable) {
                                            graph.saveTiming(ZmanTradition.valueOf(tradition), candles.toInt(),
                                                havdalah.toInt(), settings.israelCalendar, complete = true)
                                            onExit()
                                        }
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

@Composable
private fun WelcomeScreen(busy: Boolean, onStart: () -> Unit, onLater: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = Paper) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CandleMark(size = 112.dp)
                    Spacer(Modifier.height(24.dp))
                    Text("Shabbos Mode", color = Ink, fontFamily = PrintSerif, fontWeight = FontWeight.Medium,
                        fontSize = 38.sp, lineHeight = 46.sp, textAlign = TextAlign.Center)
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    PrimaryButton("Get started", enabled = !busy, onClick = onStart)
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onLater, enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Set up later", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private fun validTiming(candles: String, havdalah: String) =
    candles.toIntOrNull()?.let { it in 0..90 } == true && havdalah.toIntOrNull()?.let { it in 0..120 } == true

@Composable
private fun PracticeChoice(title: String, detail: String = "", selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) Ink else LightPaper,
        tween(quietDuration()), label = "Practice choice")
    val foreground = if (selected) LightPaper else Ink
    Row(Modifier.fillMaxWidth().clip(CutCornerShape(bottomEnd = 6.dp)).background(background)
        .heightIn(min = if (detail.isBlank()) 56.dp else 68.dp)
        .selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = foreground, fontFamily = PrintSans, fontWeight = FontWeight.Medium,
                fontSize = 17.sp, lineHeight = 23.sp)
            if (detail.isNotBlank()) Text(detail, color = if (selected) Paper else FadedInk,
                fontSize = 13.sp, lineHeight = 18.sp)
        }
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(
            selectedColor = LightPaper, unselectedColor = FadedInk))
    }
}

@Composable
private fun DaytimeChoices(selected: ZmanTradition, onSelect: (ZmanTradition) -> Unit) {
    var help by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ZmanTradition.entries.forEach { practice ->
            val community = when (practice) {
                ZmanTradition.GRA -> "Ashkenazi & Sephardi communities"
                ZmanTradition.MGA -> "Sephardi & Ashkenazi · earlier Shema"
                ZmanTradition.CHABAD -> "Chabad-Lubavitch · Baal Hatanya"
                ZmanTradition.BOTH -> "Gra + Magen Avraham"
            }
            PracticeChoice(practice.title, community,
                selected == practice) { onSelect(practice) }
        }
    }
    TextButton(onClick = { help = true }) { Text("Help choosing") }
    if (help) TimingHelp("Daytime calculation", onDismiss = { help = false }) {
        ZmanTradition.entries.forEach { practice ->
            Text(practice.title, fontWeight = FontWeight.Medium)
            Text(practice.detail, color = FadedInk, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
        }
        Text("Communities may use different methods for Shema and tefillah. Magen Avraham variants differ; follow your community’s calendar.",
            color = FadedInk, fontSize = 14.sp)
    }
}

@Composable
private fun CandleLightingChoices(settings: AppSettings, candles: String, onCandles: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(false) }
    val local = TimingPractice.candleLead(settings.copy(candleMinutes = 0))
    val lead = candles.toIntOrNull()?.takeIf { it > 0 } ?: local
    val options = (listOf(local) + listOf(18, 20, 30, 40)).distinct().sorted()
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { minutes ->
            PracticeChoice("$minutes min", selected = lead == minutes) {
                onCandles(if (minutes == local) "0" else minutes.toString())
            }
        }
        PracticeChoice("Other", if (lead !in options) "$lead min" else "", lead !in options) { custom = true }
    }
    if (custom) MinuteDialog("Candle lighting", "Minutes before sunset",
        if (lead !in options) lead.toString() else "", 1..90,
        onDismiss = { custom = false }, onConfirm = { onCandles(it); custom = false })
}

@Composable
private fun ShabbosEndChoices(havdalah: String, onHavdalah: (String) -> Unit) {
    var custom by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    val other = havdalah !in setOf("0", "72")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PracticeChoice("Nightfall", "Chabad · many Ashkenazi communities", havdalah == "0") { onHavdalah("0") }
        PracticeChoice("72 min after sunset", "Rabbeinu Tam · many Sephardim & Chassidim", havdalah == "72") { onHavdalah("72") }
        PracticeChoice("Other", if (other) "$havdalah min after sunset" else "Local community custom", other) { custom = true }
    }
    TextButton(onClick = { help = true }) { Text("Help choosing") }
    if (help) TimingHelp("Shabbos end", onDismiss = { help = false }) {
        Text("Nightfall", fontWeight = FontWeight.Medium)
        Text("Three small stars · 8.5°", color = FadedInk, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text("72 minutes", fontWeight = FontWeight.Medium)
        Text("Fixed minutes after sunset. One Rabbeinu Tam calculation.", color = FadedInk, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text("Customs vary within communities. Follow your community’s published times.", color = FadedInk, fontSize = 14.sp)
    }
    if (custom) MinuteDialog("Shabbos end", "Minutes after sunset",
        if (other) havdalah else "", 1..120,
        presets = listOf("42" to "42 min", "50" to "50 min"),
        onDismiss = { custom = false }, onConfirm = { onHavdalah(it); custom = false })
}

@Composable
private fun TimingHelp(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = CutCornerShape(bottomEnd = 8.dp),
        containerColor = LightPaper, title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun MinuteDialog(title: String, label: String, initial: String, range: IntRange,
                         presets: List<Pair<String, String>> = emptyList(),
                         onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val valid = value.toIntOrNull()?.let { it in range } == true
    AlertDialog(onDismissRequest = onDismiss, shape = CutCornerShape(bottomEnd = 8.dp),
        containerColor = LightPaper, title = { Text(title, style = MaterialTheme.typography.titleLarge) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (presets.isNotEmpty()) {
                ChoiceChips(presets, value) { value = it }
                Spacer(Modifier.height(16.dp))
            }
            PaperField(value, { value = it.filter(Char::isDigit).take(3) }, label, keyboardType = KeyboardType.Number)
            if (value.isNotEmpty() && !valid) Text("Enter ${range.first}–${range.last} minutes.",
                Modifier.padding(top = 8.dp), color = Rust, fontSize = 13.sp)
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(value.toInt().toString()) }, enabled = valid) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun PracticeEditor(graph: AppGraph, settings: AppSettings, onDismiss: () -> Unit) {
    var tradition by rememberSaveable { mutableStateOf(settings.tradition.name) }
    var candles by rememberSaveable { mutableStateOf(settings.candleMinutes.toString()) }
    var havdalah by rememberSaveable { mutableStateOf(settings.havdalahMinutes.toString()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    EditorDialog("Zmanim preferences", onDismiss, onSave = {
        saving = true
        scope.launch {
            runCatching { graph.saveTiming(ZmanTradition.valueOf(tradition), candles.toInt(), havdalah.toInt(), settings.israelCalendar) }
                .onSuccess { onDismiss() }.onFailure { error = "Couldn't save. Try again."; saving = false }
        }
    }, saveEnabled = !saving && validTiming(candles, havdalah)) {
        SectionTitle("Candle lighting · before sunset")
        CandleLightingChoices(settings, candles) { candles = it }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Daytime calculation")
        DaytimeChoices(ZmanTradition.valueOf(tradition)) { tradition = it.name }
        Spacer(Modifier.height(24.dp))
        SectionTitle("Shabbos ends")
        ShabbosEndChoices(havdalah) { havdalah = it }
        if (error.isNotBlank()) Text(error, color = Rust)
    }
}
