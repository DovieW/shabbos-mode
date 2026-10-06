package dev.dovie.shabbosmode

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "alarms")
data class AlarmItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "Alarm",
    val hour: Int,
    val minute: Int,
    val repeatDay: Int = 0,
    val oneTimeMillis: Long = 0,
    val tone: String = "soft",
    val volume: Int = 70,
    val vibrationSeconds: Int = 20,
    val rampSeconds: Int = 60,
    val durationMinutes: Int = 5,
    val enabled: Boolean = true
)

@Entity(tableName = "shuls")
data class ShulItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = ""
)

@Entity(tableName = "minyanim")
data class MinyanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shulId: Long,
    val label: String,
    val day: Int,
    val hour: Int,
    val minute: Int
)

@Entity(tableName = "checklist")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val doneWeek: String = ""
)

@Entity(tableName = "events")
data class TimeEvent(
    @PrimaryKey val key: String,
    val label: String,
    val atMillis: Long,
    val week: String,
    val fetchedAt: Long
)

@Entity(tableName = "weather")
data class WeatherHour(
    @PrimaryKey val atMillis: Long,
    val temperature: Double,
    val rainPercent: Int,
    val code: Int,
    val fetchedAt: Long
)

@Dao
interface AppDao {
    @Query("SELECT * FROM alarms ORDER BY repeatDay, hour, minute")
    fun alarms(): Flow<List<AlarmItem>>
    @Query("SELECT * FROM alarms")
    suspend fun allAlarms(): List<AlarmItem>
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun alarm(id: Long): AlarmItem?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAlarm(item: AlarmItem): Long
    @Delete suspend fun deleteAlarm(item: AlarmItem)

    @Query("SELECT * FROM shuls ORDER BY name")
    fun shuls(): Flow<List<ShulItem>>
    @Query("SELECT * FROM shuls")
    suspend fun allShuls(): List<ShulItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveShul(item: ShulItem): Long
    @Delete suspend fun deleteShul(item: ShulItem)
    @Query("DELETE FROM minyanim WHERE shulId = :shulId")
    suspend fun deleteMinyanimForShul(shulId: Long)

    @Query("SELECT * FROM minyanim ORDER BY day, hour, minute")
    fun minyanim(): Flow<List<MinyanItem>>
    @Query("SELECT * FROM minyanim")
    suspend fun allMinyanim(): List<MinyanItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMinyan(item: MinyanItem): Long
    @Delete suspend fun deleteMinyan(item: MinyanItem)

    @Query("SELECT * FROM checklist ORDER BY id")
    fun checklist(): Flow<List<ChecklistItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChecklist(item: ChecklistItem): Long
    @Delete suspend fun deleteChecklist(item: ChecklistItem)

    @Query("SELECT * FROM events ORDER BY atMillis")
    fun events(): Flow<List<TimeEvent>>
    @Query("SELECT * FROM events")
    suspend fun allEvents(): List<TimeEvent>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvents(items: List<TimeEvent>)
    @Query("DELETE FROM events WHERE week != :week")
    suspend fun removeOldEvents(week: String)
    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("SELECT * FROM weather WHERE atMillis >= :from ORDER BY atMillis")
    fun weather(from: Long): Flow<List<WeatherHour>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWeather(items: List<WeatherHour>)
    @Query("DELETE FROM weather WHERE atMillis < :before")
    suspend fun removeOldWeather(before: Long)
    @Query("DELETE FROM weather")
    suspend fun clearWeather()
}

@Database(
    entities = [AlarmItem::class, ShulItem::class, MinyanItem::class,
        ChecklistItem::class, TimeEvent::class, WeatherHour::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shabbos-mode.db"
                ).build().also { instance = it }
            }
    }
}

private val Context.preferences: DataStore<Preferences> by preferencesDataStore("settings")

data class AppSettings(
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val zoneId: String = "",
    val onboardingComplete: Boolean = false,
    val countryCode: String = "",
    val locality: String = "",
    val tradition: ZmanTradition = ZmanTradition.GRA,
    val timingConfigured: Boolean = false,
    val candleMinutes: Int = 0,
    val havdalahMinutes: Int = 0,
    val israelCalendar: Boolean = false,
    val reminderHours: Int = 4,
    val dndEnabled: Boolean = false,
    val selectedZmanim: Set<String> = setOf("sunrise", "sofZmanShma", "chatzot", "sunset"),
    val taskerEvents: Set<String> = emptySet(),
    val confirmedMinyanWeek: String = "",
    val overrideWeek: String = "",
    val overrideStart: Long = 0,
    val overrideEnd: Long = 0
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val city = stringPreferencesKey("city")
        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")
        val zone = stringPreferencesKey("zone")
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val country = stringPreferencesKey("country_code")
        val locality = stringPreferencesKey("locality")
        val tradition = stringPreferencesKey("zman_tradition")
        val candleMinutes = intPreferencesKey("candle_minutes")
        val havdalahMinutes = intPreferencesKey("havdalah_minutes")
        val israel = booleanPreferencesKey("israel_calendar")
        val reminder = intPreferencesKey("reminder_hours")
        val dnd = booleanPreferencesKey("dnd_enabled")
        val zmanim = stringPreferencesKey("selected_zmanim")
        val tasker = stringPreferencesKey("tasker_events")
        val confirmedMinyanWeek = stringPreferencesKey("confirmed_minyan_week")
        val overrideWeek = stringPreferencesKey("override_week")
        val overrideStart = longPreferencesKey("override_start")
        val overrideEnd = longPreferencesKey("override_end")
    }

    val flow: Flow<AppSettings> = context.preferences.data.map { p ->
        AppSettings(
            city = p[Keys.city] ?: "",
            latitude = p[Keys.latitude],
            longitude = p[Keys.longitude],
            zoneId = p[Keys.zone] ?: "",
            onboardingComplete = p[Keys.onboarding] ?: false,
            countryCode = p[Keys.country] ?: "",
            locality = p[Keys.locality] ?: "",
            tradition = ZmanTradition.entries.firstOrNull { it.name == p[Keys.tradition] } ?: ZmanTradition.GRA,
            timingConfigured = p[Keys.tradition] != null,
            candleMinutes = (p[Keys.candleMinutes] ?: 0).coerceIn(0, 90),
            havdalahMinutes = (p[Keys.havdalahMinutes] ?: 0).coerceIn(0, 120),
            israelCalendar = p[Keys.israel] ?: (p[Keys.zone] == "Asia/Jerusalem"),
            reminderHours = p[Keys.reminder] ?: 4,
            dndEnabled = p[Keys.dnd] ?: false,
            selectedZmanim = p[Keys.zmanim]?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: AppSettings().selectedZmanim,
            taskerEvents = p[Keys.tasker]?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet(),
            confirmedMinyanWeek = p[Keys.confirmedMinyanWeek] ?: "",
            overrideWeek = p[Keys.overrideWeek] ?: "",
            overrideStart = p[Keys.overrideStart] ?: 0,
            overrideEnd = p[Keys.overrideEnd] ?: 0
        )
    }

    suspend fun setLocation(location: CityResult) {
        context.preferences.edit {
            it[Keys.city] = location.name
            it[Keys.latitude] = location.latitude
            it[Keys.longitude] = location.longitude
            it[Keys.zone] = location.zoneId
            it[Keys.country] = location.countryCode
            it[Keys.locality] = location.locality
            it[Keys.israel] = location.countryCode == "IL"
        }
    }
    suspend fun setTiming(tradition: ZmanTradition, candles: Int, havdalah: Int, israel: Boolean) {
        require(candles in 0..90 && havdalah in 0..120)
        context.preferences.edit {
            val changedStart = (it[Keys.candleMinutes] ?: 0) != candles
            val changedEnd = (it[Keys.havdalahMinutes] ?: 0) != havdalah
            it[Keys.tradition] = tradition.name
            it[Keys.candleMinutes] = candles
            it[Keys.havdalahMinutes] = havdalah
            it[Keys.israel] = israel
            // Morning prayer choices must not erase a user's Shabbos boundary override.
            if (changedStart) it[Keys.overrideStart] = 0
            if (changedEnd) it[Keys.overrideEnd] = 0
            if ((it[Keys.overrideStart] ?: 0) == 0L && (it[Keys.overrideEnd] ?: 0) == 0L) {
                it[Keys.overrideWeek] = ""
            }
        }
    }
    suspend fun completeOnboarding() {
        context.preferences.edit { it[Keys.onboarding] = true }
    }
    suspend fun setReminderHours(hours: Int) {
        context.preferences.edit { it[Keys.reminder] = hours.coerceIn(1, 24) }
    }
    suspend fun setDnd(enabled: Boolean) {
        context.preferences.edit { it[Keys.dnd] = enabled }
    }
    suspend fun setZmanim(keys: Set<String>) {
        context.preferences.edit { it[Keys.zmanim] = keys.joinToString(",") }
    }
    suspend fun setTasker(keys: Set<String>) {
        context.preferences.edit { it[Keys.tasker] = keys.joinToString(",") }
    }
    suspend fun confirmMinyanWeek(week: String) {
        context.preferences.edit { it[Keys.confirmedMinyanWeek] = week }
    }
    suspend fun setOverride(week: String, start: Long, end: Long) {
        context.preferences.edit {
            it[Keys.overrideWeek] = week
            it[Keys.overrideStart] = start
            it[Keys.overrideEnd] = end
        }
    }
}
