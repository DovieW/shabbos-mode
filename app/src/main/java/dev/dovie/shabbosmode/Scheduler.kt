package dev.dovie.shabbosmode

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.TimeUnit

class AppGraph private constructor(context: Context) {
    val context = context.applicationContext
    val dao = AppDatabase.get(this.context).dao()
    val settings = SettingsStore(this.context)
    val remote = RemoteData(dao)
    val scheduler = AppScheduler(this.context, dao, settings)
    val syncMutex = Mutex()

    suspend fun saveLocation(location: CityResult) {
        syncMutex.withLock {
            dao.clearEvents()
            dao.clearWeather()
            settings.setLocation(location)
            settings.setOverride("", 0, 0)
        }
        scheduler.reschedule()
        SyncWorker.refreshNow(context)
    }

    suspend fun saveTiming(tradition: ZmanTradition, candles: Int, havdalah: Int, israel: Boolean, complete: Boolean = false) {
        syncMutex.withLock {
            val previous = settings.flow.first()
            dao.clearEvents()
            settings.setTiming(tradition, candles, havdalah, israel)
            if (previous.tradition != tradition) {
                val selected = previous.selectedZmanim - setOf("sofZmanShmaMGA", "sofZmanTfillaMGA")
                settings.setZmanim(if (tradition == ZmanTradition.BOTH)
                    selected + selected.filter { it in setOf("sofZmanShma", "sofZmanTfilla") }.map { it + "MGA" }
                    else selected)
            }
        }
        scheduler.reschedule()
        SyncWorker.refreshNow(context)
        if (complete) settings.completeOnboarding()
    }

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context).also { instance = it }
        }
    }
}

class AppScheduler(
    private val context: Context,
    private val dao: AppDao,
    private val settingsStore: SettingsStore
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val scheduled = context.getSharedPreferences("scheduled", Context.MODE_PRIVATE)
    private val rescheduleMutex = Mutex()

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canControlDnd(): Boolean = notifications.isNotificationPolicyAccessGranted

    suspend fun reschedule(now: Instant = Instant.now()) = rescheduleMutex.withLock {
        val old = scheduled.getStringSet("keys", emptySet()).orEmpty()
        old.forEach { key -> alarms.cancel(pending(key, 0)) }
        val keys = mutableSetOf<String>()
        val settings = settingsStore.flow.first()
        val zone = TimeLogic.zone(settings)
        val week = TimeLogic.weekKey(now, zone)
        val baseEvents = dao.allEvents().filter { it.week == week }
        val events = baseEvents.map {
            when {
                it.key == "start" && settings.overrideWeek == week && settings.overrideStart > 0 ->
                    it.copy(atMillis = settings.overrideStart)
                it.key == "end" && settings.overrideWeek == week && settings.overrideEnd > 0 ->
                    it.copy(atMillis = settings.overrideEnd)
                else -> it
            }
        }
        val start = events.firstOrNull { it.key == "start" }?.atMillis
        val end = events.firstOrNull { it.key == "end" }?.atMillis

        if (canScheduleExact()) {
            dao.allAlarms().forEach { item ->
                TimeLogic.nextAlarm(item, now, zone)?.let { at ->
                    val key = "alarm:${item.id}"
                    val show = PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarms.setAlarmClock(
                        AlarmManager.AlarmClockInfo(at, show),
                        pending(key, item.id)
                    )
                    keys += key
                }
            }
        }

        if (start != null && start > now.toEpochMilli()) {
            val reminderAt = start - settings.reminderHours * 3600_000L
            if (reminderAt > now.toEpochMilli()) {
                val key = "reminder"
                schedule(key, reminderAt, exact = canScheduleExact())
                keys += key
            }
        }
        if (settings.dndEnabled && canControlDnd()) {
            if (start != null && start > now.toEpochMilli()) {
                schedule("dnd:start", start, exact = canScheduleExact())
                keys += "dnd:start"
            }
            if (end != null && end > now.toEpochMilli()) {
                schedule("dnd:end", end, exact = canScheduleExact())
                keys += "dnd:end"
            }
            if (start != null && end != null && now.toEpochMilli() in start until end) {
                setDnd(true)
            }
        }
        if ((!settings.dndEnabled || end == null || now.toEpochMilli() >= end) &&
            scheduled.getBoolean("dnd_active", false)) setDnd(false)

        val taskerTimes = mutableMapOf<String, Long>()
        events.forEach { taskerTimes[it.key] = it.atMillis }
        dao.allMinyanim().forEach { item ->
            val date = TimeLogic.friday(now, zone).plusDays(if (item.day == 6) 1 else 0)
            var at = TimeLogic.at(date, item.hour, item.minute, zone)
            if (at <= now.toEpochMilli()) {
                at = TimeLogic.at(date.plusWeeks(1), item.hour, item.minute, zone)
            }
            taskerTimes["minyan:${item.id}"] = at
        }
        settings.taskerEvents.forEach { name ->
            val at = taskerTimes[name]
            if (at != null && at > now.toEpochMilli()) {
                val key = "tasker:$name"
                schedule(key, at, exact = canScheduleExact())
                keys += key
            }
        }
        scheduled.edit().putStringSet("keys", keys).apply()
    }

    private fun schedule(key: String, at: Long, exact: Boolean) {
        val intent = pending(key, 0)
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    private fun pending(key: String, id: Long): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setData(Uri.parse("shabbos-mode://schedule/${Uri.encode(key)}"))
            .putExtra("key", key)
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun setDnd(active: Boolean) {
        if (!canControlDnd()) return
        notifications.setInterruptionFilter(
            if (active) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        scheduled.edit().putBoolean("dnd_active", active).apply()
    }

    fun postChecklistReminder(start: Long?, end: Long?) {
        if (!canPostNotifications()) return
        createChannels(context)
        val intent = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).putExtra("page", "prepare"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val other = alarms.nextAlarmClock
        val otherIsExternal = other?.showIntent?.creatorPackage != context.packageName
        val withinShabbos = start != null && end != null && other != null &&
            other.triggerTime in start..end
        val text = if (otherIsExternal && withinShabbos) {
            "Checklist · Another app's next detectable alarm is during Shabbos."
        } else {
            "Review your Shabbos checklist."
        }
        val notification = NotificationCompat.Builder(context, "preparation")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Shabbos preparation")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        notifications.notify(1001, notification)
    }

    companion object {
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(
                "preparation", "Preparation", NotificationManager.IMPORTANCE_DEFAULT
            ))
            manager.createNotificationChannel(NotificationChannel(
                "alarms", "Alarms", NotificationManager.IMPORTANCE_HIGH
            ))
        }

        fun requestExactAlarmAccess(context: Context) {
            if (Build.VERSION.SDK_INT >= 31) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }
}

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = AppGraph.get(context)
                val key = intent.getStringExtra("key").orEmpty()
                when {
                    key.startsWith("alarm:") -> {
                        val item = graph.dao.alarm(intent.getLongExtra("id", 0))
                        if (item?.enabled == true) {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, AlarmPlaybackService::class.java)
                                    .putExtra("alarmId", item.id)
                            )
                            if (item.repeatDay == 0) graph.dao.saveAlarm(item.copy(enabled = false))
                        }
                    }
                    key == "reminder" -> {
                        val events = graph.dao.allEvents()
                        val settings = graph.settings.flow.first()
                        val week = TimeLogic.weekKey(Instant.now(), TimeLogic.zone(settings))
                        val start = events.firstOrNull { it.key == "start" && it.week == week }?.atMillis
                        val end = events.firstOrNull { it.key == "end" && it.week == week }?.atMillis
                        graph.scheduler.postChecklistReminder(
                            if (settings.overrideWeek == week && settings.overrideStart > 0)
                                settings.overrideStart else start,
                            if (settings.overrideWeek == week && settings.overrideEnd > 0)
                                settings.overrideEnd else end
                        )
                    }
                    key == "dnd:start" -> graph.scheduler.setDnd(true)
                    key == "dnd:end" -> graph.scheduler.setDnd(false)
                    key.startsWith("tasker:") -> TaskerBridge.fire(context, key.removePrefix("tasker:"))
                }
                graph.scheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val graph = AppGraph.get(context)
                graph.scheduler.reschedule()
                SyncWorker.ensureScheduled(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = AppGraph.get(applicationContext)
        val success = graph.syncMutex.withLock {
            val settings = graph.settings.flow.first()
            if (settings.latitude == null) return@withLock true
            val times = runCatching { graph.remote.refreshTimes(settings) }.isSuccess
            val weather = runCatching { graph.remote.refreshWeather(settings) }.isSuccess
            times && weather
        }
        graph.scheduler.reschedule()
        return if (success) Result.success() else Result.retry()
    }

    companion object {
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "refresh-forecasts",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    ).build()
            )
        }
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    ).build()
            )
        }
    }
}
