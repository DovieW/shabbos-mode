package dev.dovie.shabbosmode

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun LocationChooser(graph: AppGraph, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var cancellation by remember { mutableStateOf(CancellationSignal()) }
    DisposableEffect(Unit) { onDispose { cancellation.cancel() } }

    fun save(city: CityResult) {
        saving = true
        scope.launch {
            runCatching { graph.saveLocation(city) }
                .onSuccess { onSaved() }
                .onFailure { error = "Couldn't save. Try again." }
            saving = false
            locating = false
        }
    }

    @SuppressLint("MissingPermission")
    fun locate() {
        cancellation.cancel()
        val request = CancellationSignal()
        cancellation = request
        locating = true
        error = ""
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        fun received(location: android.location.Location?) {
            if (!locating || request.isCanceled) return
            if (location == null) {
                locating = false
                error = "Choose a city instead."
                return
            }
            scope.launch {
                runCatching {
                    @Suppress("DEPRECATION")
                    val address = withContext(Dispatchers.IO) {
                        Geocoder(context, Locale.ENGLISH).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                    } ?: throw IllegalStateException("No address")
                    val name = address.locality ?: address.subAdminArea ?: throw IllegalStateException("No city")
                    val country = address.countryCode ?: throw IllegalStateException("No country")
                    val zone = graph.remote.locationZone(location.latitude, location.longitude)
                    CityResult(listOfNotNull(name, address.adminArea, address.countryName).distinct().joinToString(", "),
                        location.latitude, location.longitude, zone, country, name)
                }.onSuccess { if (locating && !request.isCanceled) save(it) }
                    .onFailure { locating = false; error = "Choose a city instead." }
            }
        }
        try {
            val provider = providers.firstOrNull()
            if (provider == null) received(null)
            else if (Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(provider, request, context.mainExecutor, ::received)
            } else {
                // Older Android: accept only a recent fix; otherwise offer manual city search.
                received(providers.mapNotNull { manager.getLastKnownLocation(it) }
                    .filter { System.currentTimeMillis() - it.time in 0..300_000L }.maxByOrNull { it.time })
            }
        } catch (_: SecurityException) {
            locating = false
            error = "Choose a city or allow location."
        }
    }
    LaunchedEffect(locating) {
        if (locating) {
            delay(20_000)
            if (locating && !saving) { cancellation.cancel(); locating = false; error = "Choose a city instead." }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) locate() else error = "Choose a city or allow location."
    }
    PaperField(query, { query = it; error = ""; results = emptyList() }, "City")
    Spacer(Modifier.height(16.dp))
    PrimaryButton(if (searching) "Searching…" else "Find city", enabled = query.trim().length >= 2 && !searching && !locating && !saving) {
        searching = true
        val requested = query
        scope.launch {
            runCatching { graph.remote.findCities(requested) }
                .onSuccess { if (query == requested) { results = it; error = if (it.isEmpty()) "No city found." else "" } }
                .onFailure { error = "Search failed. Try again." }
            searching = false
        }
    }
    if (!saving) results.forEach { city -> NavigationRow(city.name) { save(city) } }
    Spacer(Modifier.height(16.dp))
    SecondaryButton(if (locating) "Locating…" else "Use current location", enabled = !locating && !saving && !searching) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (error.isNotBlank()) Text(error, Modifier.padding(top = 12.dp), color = Rust, fontSize = 14.sp)
}
