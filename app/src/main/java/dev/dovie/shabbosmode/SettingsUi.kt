package dev.dovie.shabbosmode

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun SettingsScreen(
    graph: AppGraph,
    settings: AppSettings,
    events: List<TimeEvent>,
    minyanim: List<MinyanItem>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val week = TimeLogic.weekKey(Instant.now(), TimeLogic.zone(settings))
    val start = events.firstOrNull { it.key == "start" && it.week == week }
    val end = events.firstOrNull { it.key == "end" && it.week == week }
    var cityQuery by remember { mutableStateOf("") }
    var cityResults by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var cityError by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf("") }

    fun saveLocation(name: String, lat: Double, lon: Double, zone: String) {
        scope.launch {
            graph.dao.clearEvents()
            graph.dao.clearWeather()
            graph.settings.setLocation(name, lat, lon, zone)
            SyncWorker.refreshNow(context)
            graph.scheduler.reschedule()
            cityResults = emptyList()
            cityQuery = ""
        }
    }

    @SuppressLint("MissingPermission")
    fun useCurrentLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            locationError = "Location permission is needed."
            return
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        val provider = providers.firstOrNull()
        if (provider == null) {
            locationError = "Location is unavailable."
            return
        }
        fun saveBestAvailable(current: android.location.Location?) {
            val location = current ?: providers.mapNotNull { candidate ->
                runCatching { manager.getLastKnownLocation(candidate) }.getOrNull()
            }.maxByOrNull { it.time }
            if (location == null) locationError = "Could not find your location. Choose a city."
            else {
                locationError = ""
                saveLocation("Current location", location.latitude, location.longitude,
                    ZoneId.systemDefault().id)
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                    saveBestAvailable(location)
                }
            } catch (_: SecurityException) {
                locationError = "Location permission is needed."
            }
        } else {
            saveBestAvailable(null)
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) useCurrentLocation()
        else locationError = "Location permission was not granted."
    }

    SectionTitle("Location")
    Text(settings.city.ifBlank { "No location set" }, color = FadedInk, fontSize = 14.sp)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        cityQuery, { cityQuery = it },
        modifier = Modifier.fillMaxWidth(), label = { Text("City") }, singleLine = true
    )
    Spacer(Modifier.height(8.dp))
    SecondaryButton(if (searching) "SEARCHING…" else "FIND CITY") {
        if (cityQuery.length >= 2) {
            searching = true
            scope.launch {
                runCatching { graph.remote.findCities(cityQuery) }
                    .onSuccess {
                        cityResults = it
                        cityError = if (it.isEmpty()) "No city found." else ""
                    }
                    .onFailure { cityError = "City search failed." }
                searching = false
            }
        }
    }
    cityResults.forEach { city ->
        Spacer(Modifier.height(6.dp))
        SecondaryButton(city.name) {
            saveLocation(city.name, city.latitude, city.longitude, city.zoneId)
        }
    }
    if (cityError.isNotBlank()) Text(cityError, color = Rust, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    SecondaryButton("USE CURRENT LOCATION") {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) useCurrentLocation()
        else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (locationError.isNotBlank()) Text(locationError, color = Rust, fontSize = 13.sp)

    Spacer(Modifier.height(36.dp))
    SectionTitle("Shabbos times")
    if (start != null && end != null) {
        val shownStart = if (settings.overrideWeek == week && settings.overrideStart > 0)
            settings.overrideStart else start.atMillis
        val shownEnd = if (settings.overrideWeek == week && settings.overrideEnd > 0)
            settings.overrideEnd else end.atMillis
        Text("Start  ${formatTime(shownStart, TimeLogic.zone(settings))}", color = Ink)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("CHANGE START") {
            val at = Instant.ofEpochMilli(shownStart).atZone(TimeLogic.zone(settings))
            TimePickerDialog(context, { _, h, m ->
                scope.launch {
                    graph.settings.setOverride(
                        week,
                        TimeLogic.at(at.toLocalDate(), h, m, TimeLogic.zone(settings)),
                        if (settings.overrideWeek == week) settings.overrideEnd else 0
                    )
                    graph.scheduler.reschedule()
                }
            }, at.hour, at.minute, false).show()
        }
        Spacer(Modifier.height(12.dp))
        Text("End  ${formatTime(shownEnd, TimeLogic.zone(settings))}", color = Ink)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("CHANGE END") {
            val at = Instant.ofEpochMilli(shownEnd).atZone(TimeLogic.zone(settings))
            TimePickerDialog(context, { _, h, m ->
                scope.launch {
                    graph.settings.setOverride(
                        week,
                        if (settings.overrideWeek == week) settings.overrideStart else 0,
                        TimeLogic.at(at.toLocalDate(), h, m, TimeLogic.zone(settings))
                    )
                    graph.scheduler.reschedule()
                }
            }, at.hour, at.minute, false).show()
        }
    } else {
        Text("Times appear after location sync.", color = FadedInk, fontSize = 14.sp)
    }
    Spacer(Modifier.height(12.dp))
    SecondaryButton("REFRESH TIMES & WEATHER") { SyncWorker.refreshNow(context) }

    Spacer(Modifier.height(36.dp))
    SectionTitle("Friday reminder")
    SettingSlider("Hours before start", settings.reminderHours, 1..12) {
        scope.launch {
            graph.settings.setReminderHours(it)
            graph.scheduler.reschedule()
        }
    }

    Spacer(Modifier.height(30.dp))
    SectionTitle("Do Not Disturb")
    if (!graph.scheduler.canControlDnd()) {
        SecondaryButton("ALLOW DND ACCESS") {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Automatic rule", Modifier.weight(1f), color = Ink)
        Switch(checked = settings.dndEnabled, colors = SwitchDefaults.colors(
            checkedThumbColor = LightPaper, checkedTrackColor = Ink,
            uncheckedThumbColor = FadedInk, uncheckedTrackColor = Paper,
            uncheckedBorderColor = FadedInk
        ), onCheckedChange = {
            scope.launch {
                graph.settings.setDnd(it)
                graph.scheduler.reschedule()
            }
        })
    }

    Spacer(Modifier.height(30.dp))
    SectionTitle("Clock zmanim")
    val options = listOf(
        "sunrise" to "Sunrise", "sofZmanShma" to "Sof zman Shema",
        "chatzot" to "Chatzot", "minchaGedola" to "Mincha gedola",
        "plagHaMincha" to "Plag hamincha", "sunset" to "Sunset",
        "tzeit7083deg" to "Tzeit"
    )
    options.forEach { (key, label) ->
        CheckOption(label, settings.selectedZmanim.contains(key)) {
            val updated = settings.selectedZmanim.toMutableSet()
            if (it) updated += key else updated -= key
            scope.launch { graph.settings.setZmanim(updated) }
        }
    }

    Spacer(Modifier.height(30.dp))
    SectionTitle("Tasker events")
    Text("Select times to send to Tasker.", color = FadedInk, fontSize = 13.sp)
    val taskerOptions = listOf("start" to "Shabbos start", "end" to "Shabbos end") +
        options + minyanim.map { "minyan:${it.id}" to it.label }
    taskerOptions.distinctBy { it.first }.forEach { (key, label) ->
        CheckOption(label, settings.taskerEvents.contains(key)) {
            val updated = settings.taskerEvents.toMutableSet()
            if (it) updated += key else updated -= key
            scope.launch {
                graph.settings.setTasker(updated)
                graph.scheduler.reschedule()
            }
        }
    }
    Spacer(Modifier.height(30.dp))
    Text(
        "Times: Hebcal.com · Weather: Open-Meteo.com · CC BY 4.0",
        color = FadedInk, fontSize = 11.sp
    )
}

@Composable
private fun CheckOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink, fontSize = 14.sp)
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}
