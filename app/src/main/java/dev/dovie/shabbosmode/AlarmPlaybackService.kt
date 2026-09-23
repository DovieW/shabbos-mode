package dev.dovie.shabbosmode

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

class AlarmPlaybackService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var playback: Job? = null
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        AppScheduler.createChannels(this)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AlarmPlaybackService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).putExtra("page", "alarms"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, "alarms")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Shabbos Mode alarm")
            .setContentText("Alarm is ringing")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .build()
        startForeground(2001, notification)

        playback?.cancel()
        playback = scope.launch {
            val item = if (intent?.getBooleanExtra("preview", false) == true) {
                AlarmItem(hour = 0, minute = 0, tone = intent.getStringExtra("tone") ?: "soft",
                    volume = intent.getIntExtra("volume", 35).coerceIn(0, 100),
                    vibrationSeconds = intent.getIntExtra("vibrate", 0).coerceIn(0, 1),
                    rampSeconds = intent.getIntExtra("ramp", 4).coerceIn(0, 4),
                    durationMinutes = 1)
            } else {
                withContext(Dispatchers.IO) {
                    AppGraph.get(this@AlarmPlaybackService).dao.alarm(
                        intent?.getLongExtra("alarmId", 0) ?: 0
                    )
                }
            }
            if (item == null) {
                stopSelf()
                return@launch
            }
            val durationMs = if (intent?.getBooleanExtra("preview", false) == true) 8_000L
                else item.durationMinutes.coerceIn(1, 30) * 60_000L
            runCatching { play(item, durationMs) }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun play(item: AlarmItem, durationMs: Long) {
        val started = SystemClock.elapsedRealtime()
        val vibrationMs = item.vibrationSeconds.coerceIn(0, 120) * 1000L
        vibrator = getSystemService(Vibrator::class.java)
        if (vibrationMs > 0 && vibrator?.hasVibrator() == true) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 450, 550), 0)
            )
        }
        delay(vibrationMs.coerceAtMost(durationMs))
        vibrator?.cancel()
        if (SystemClock.elapsedRealtime() - started >= durationMs) return
        if (item.tone.startsWith("content:")) {
            playCustom(item, started, durationMs)
        } else {
            playSynth(item, started, durationMs)
        }
    }

    private suspend fun playCustom(item: AlarmItem, started: Long, durationMs: Long) {
        val media = MediaPlayer()
        player = media
        media.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        )
        media.setDataSource(this, Uri.parse(item.tone))
        withContext(Dispatchers.IO) { media.prepare() }
        media.isLooping = true
        media.setVolume(0f, 0f)
        media.start()
        while (currentCoroutineContext().isActive &&
            SystemClock.elapsedRealtime() - started < durationMs) {
            val elapsed = SystemClock.elapsedRealtime() - started - item.vibrationSeconds * 1000L
            val fraction = if (item.rampSeconds <= 0) 1f
                else (elapsed.toFloat() / (item.rampSeconds * 1000f)).coerceIn(0.05f, 1f)
            val volume = item.volume.coerceIn(0, 100) / 100f * fraction
            media.setVolume(volume, volume)
            delay(250)
        }
    }

    private suspend fun playSynth(item: AlarmItem, started: Long, durationMs: Long) =
        withContext(Dispatchers.IO) {
            val sampleRate = 22050
            val samples = sampleRate / 4
            val audio = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(samples * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = audio
            audio.play()
            var chunk = 0
            while (currentCoroutineContext().isActive &&
                SystemClock.elapsedRealtime() - started < durationMs) {
                val elapsed = SystemClock.elapsedRealtime() - started - item.vibrationSeconds * 1000L
                val fraction = if (item.rampSeconds <= 0) 1.0
                    else (elapsed.toDouble() / (item.rampSeconds * 1000.0)).coerceIn(0.04, 1.0)
                val volume = item.volume.coerceIn(0, 100) / 100.0 * fraction
                val notes = when (item.tone) {
                    "chime" -> doubleArrayOf(523.25, 659.25, 783.99, 659.25)
                    "warm" -> doubleArrayOf(392.0, 493.88, 587.33, 493.88)
                    else -> doubleArrayOf(440.0, 523.25, 659.25, 523.25)
                }
                val frequency = notes[(chunk / 2) % notes.size]
                val data = ShortArray(samples) { index ->
                    val t = (chunk * samples + index).toDouble() / sampleRate
                    val envelope = (1.0 - index.toDouble() / samples * 0.25)
                    val wave = sin(2 * PI * frequency * t) * 0.7 +
                        sin(2 * PI * frequency * 2 * t) * 0.12
                    (wave * envelope * volume * 13000).toInt().toShort()
                }
                audio.write(data, 0, data.size)
                chunk++
            }
        }

    override fun onDestroy() {
        playback?.cancel()
        scope.cancel()
        runCatching { track?.stop() }
        track?.release()
        track = null
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val STOP = "dev.dovie.shabbosmode.STOP_ALARM"
    }
}
