package dev.dovie.shabbosmode

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val Paper = Color(0xFFF0E6D2)
val LightPaper = Color(0xFFF8F1E2)
val Ink = Color(0xFF39291E)
val Rust = Color(0xFF8D4D2C)
val FadedInk = Color(0xFF66503D)
val Rule = Color(0xFFC7B294)
val Night = Color.Black
val NightInk = Color(0xFFD6B994)
val PrintSerif = FontFamily(Font(R.font.plex_serif), Font(R.font.plex_serif_medium, FontWeight.Medium))
val PrintSans = FontFamily(Font(R.font.plex_sans), Font(R.font.plex_sans_medium, FontWeight.Medium))
val PrintMono = FontFamily(Font(R.font.plex_mono))
private val LocalQuietMotion = staticCompositionLocalOf { true }

@Composable
fun ShabbosTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    fun enabled() = Settings.Global.getFloat(context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var motion by remember { mutableStateOf(enabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = enabled() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalQuietMotion provides motion) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Ink, onPrimary = LightPaper, primaryContainer = Paper,
                onPrimaryContainer = Ink, secondary = FadedInk, onSecondary = LightPaper,
                secondaryContainer = Paper, onSecondaryContainer = Ink,
                tertiary = Rust, onTertiary = LightPaper,
                background = Paper, onBackground = Ink, surface = LightPaper, onSurface = Ink,
                surfaceVariant = Paper, onSurfaceVariant = FadedInk, outline = FadedInk,
                outlineVariant = Rule, surfaceContainer = LightPaper,
                surfaceContainerLow = LightPaper, surfaceContainerHigh = LightPaper,
                surfaceContainerHighest = Paper, surfaceContainerLowest = LightPaper,
                inverseSurface = Ink, inverseOnSurface = LightPaper,
                error = Rust, onError = LightPaper
            ),
            typography = Typography(
                headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = PrintSerif,
                    fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, color = Ink),
                titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = PrintSerif, fontSize = 24.sp,
                    lineHeight = 30.sp, fontWeight = FontWeight.Medium),
                titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = PrintSans, fontSize = 18.sp,
                    lineHeight = 24.sp, fontWeight = FontWeight.Medium),
                bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = PrintSans, fontSize = 16.sp, lineHeight = 24.sp),
                bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = PrintSans, fontSize = 14.sp, lineHeight = 20.sp),
                bodySmall = androidx.compose.ui.text.TextStyle(fontFamily = PrintMono, fontSize = 12.sp, lineHeight = 18.sp),
                labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp,
                    fontFamily = PrintSans, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = PrintMono, fontSize = 12.sp, lineHeight = 18.sp)
            ),
            shapes = Shapes(small = RoundedCornerShape(2.dp), medium = RoundedCornerShape(2.dp),
                large = RoundedCornerShape(4.dp)),
            content = content
        )
    }
}

@Composable fun quietDuration() = if (LocalQuietMotion.current) 160 else 0

@Composable
fun rememberPermissionRefresh(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    return now
}

enum class Mark { Back, Close, Settings, Chevron, Plus, Edit, Clock, Check }

@Composable
fun MarkIcon(mark: Mark, modifier: Modifier = Modifier, color: Color = Ink) {
    Canvas(modifier.size(if (mark == Mark.Edit) 32.dp else 20.dp)) {
        val s = size.width
        val strokeWidth = (if (mark == Mark.Edit) 2.dp else 1.6.dp).toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
            Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth, StrokeCap.Round)
        when (mark) {
            Mark.Back -> { line(.2f,.5f,.8f,.5f); line(.2f,.5f,.47f,.23f); line(.2f,.5f,.47f,.77f) }
            Mark.Close -> { line(.25f,.25f,.75f,.75f); line(.75f,.25f,.25f,.75f) }
            Mark.Chevron -> { line(.4f,.25f,.65f,.5f); line(.65f,.5f,.4f,.75f) }
            Mark.Plus -> { line(.2f,.5f,.8f,.5f); line(.5f,.2f,.5f,.8f) }
            Mark.Check -> { line(.2f,.5f,.42f,.72f); line(.42f,.72f,.8f,.28f) }
            Mark.Edit -> {
                val pencil = Path().apply {
                    moveTo(.14f * s, .86f * s)
                    lineTo(.19f * s, .65f * s)
                    lineTo(.66f * s, .18f * s)
                    lineTo(.82f * s, .34f * s)
                    lineTo(.35f * s, .81f * s)
                    close()
                }
                drawPath(pencil, color, style = Stroke(strokeWidth))
                line(.58f,.26f,.74f,.42f)
                line(.19f,.65f,.35f,.81f)
            }
            Mark.Clock -> { drawCircle(color, s * .36f, style = Stroke(1.6.dp.toPx())); line(.5f,.3f,.5f,.5f); line(.5f,.5f,.68f,.6f) }
            Mark.Settings -> {
                for (y in listOf(.25f,.5f,.75f)) line(.16f,y,.84f,y)
                for ((x,y) in listOf(.38f to .25f,.65f to .5f,.38f to .75f)) {
                    drawCircle(color, s * .07f, Offset(x*s,y*s))
                }
            }
        }
    }
}

@Composable
fun MarkButton(mark: Mark, description: String, color: Color = Ink, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Box(Modifier.semantics { contentDescription = description }) { MarkIcon(mark, color = color) }
    }
}

@Composable
fun CandleMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(28.dp).clearAndSetSemantics {}) {
        val p = size.width / 14f
        for (x in listOf(3f, 9f)) {
            drawRect(Ink, Offset(x*p, 7*p), androidx.compose.ui.geometry.Size(2*p, 6*p))
            drawRect(Rust, Offset(x*p, 3*p), androidx.compose.ui.geometry.Size(2*p, 2*p))
            drawRect(Rust, Offset((x+1)*p, 2*p), androidx.compose.ui.geometry.Size(p, p))
        }
    }
}

@Composable
fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = CutCornerShape(bottomEnd = 8.dp), contentPadding = PaddingValues(16.dp, 12.dp)) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        MarkIcon(when {
            label == "Save" -> Mark.Check
            label.startsWith("Add") -> Mark.Plus
            label == "Open clock" -> Mark.Clock
            else -> Mark.Chevron
        }, color = if (enabled) LightPaper else FadedInk)
    }
}

@Composable
fun SecondaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(2.dp), contentPadding = PaddingValues(16.dp, 12.dp)) {
        Text(label)
    }
}

@Composable
fun NavigationRow(title: String, detail: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = Ink, fontFamily = PrintSerif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
            if (detail.isNotBlank()) Text(detail, color = FadedInk, fontSize = 13.sp,
                fontFamily = PrintSans, lineHeight = 18.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        MarkIcon(Mark.Chevron, color = FadedInk)
    }
    HorizontalDivider(color = Rule.copy(alpha = .6f))
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = Ink, fontFamily = PrintSans, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
fun PaperPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(LightPaper, CutCornerShape(topEnd = 12.dp))
        .padding(16.dp), content = content)
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = FadedInk, fontSize = 16.sp)
    }
}

@Composable
fun SettingsGroup(title: String, detail: String, expanded: Boolean,
                  onClick: () -> Unit, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val duration = quietDuration()
    Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
        .clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 64.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, color = Ink, fontFamily = if (compact) PrintSans else PrintSerif,
                fontSize = if (compact) 17.sp else 20.sp, lineHeight = if (compact) 23.sp else 26.sp,
                fontWeight = FontWeight.Medium)
            Text(detail, color = FadedInk, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = PrintSans, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        Text(if (expanded) "−" else "+", Modifier.clearAndSetSemantics {}, color = FadedInk, fontSize = 22.sp)
    }
    AnimatedVisibility(expanded,
        enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), content = content)
    }
    HorizontalDivider(color = Rule.copy(alpha = .6f))
}

@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val chosen = selected == value
            val background by animateColorAsState(if (chosen) Ink else Color.Transparent,
                tween(quietDuration()), label = "Choice")
            val shape = CutCornerShape(bottomEnd = 6.dp)
            Box(Modifier.heightIn(min = 48.dp).clip(shape).background(background)
                .border(1.dp, if (chosen) Ink else Rule, shape)
                .selectable(chosen, role = Role.RadioButton, onClick = { onSelect(value) })
                .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, color = if (chosen) LightPaper else Ink, fontFamily = PrintSans, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun PaperField(value: String, onValueChange: (String) -> Unit, label: String,
               singleLine: Boolean = true, maxLines: Int = if (singleLine) 1 else 3,
               keyboardType: KeyboardType = KeyboardType.Text) {
    TextField(value, onValueChange, Modifier.fillMaxWidth(), label = { Text(label, fontFamily = PrintSans, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        singleLine = singleLine, maxLines = maxLines, shape = RoundedCornerShape(0.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Ink,
            unfocusedIndicatorColor = Rule), textStyle = MaterialTheme.typography.bodyLarge)
}

@Composable
fun CheckOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Checkbox(checked, onCheckedChange = null)
    }
}

@Composable
fun EditorDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit,
                 saveEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    MarkButton(Mark.Close, "Close editor", onClick = onDismiss)
                    Text(title, modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleLarge)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), content = content)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 512.dp).fillMaxWidth()) {
                        PrimaryButton("Save", saveEnabled, onSave)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDelete(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = Rust) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
