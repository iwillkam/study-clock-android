package com.iwillkam.routineclock.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.iwillkam.routineclock.AlarmRingingActivity
import com.iwillkam.routineclock.MainActivity
import com.iwillkam.routineclock.R
import com.iwillkam.routineclock.util.AlarmStorage

class AlarmSoundService : Service() {

    companion object {
        const val ACTION_START_ALARM = "ACTION_START_ALARM"
        const val ACTION_DISMISS = "ACTION_DISMISS"
        const val CHANNEL_ID = "routine_clock_alarm_ringing_channel"
        const val NOTIFICATION_ID = 9001
        private const val TAG = "AlarmSoundService"

        var isRinging = false
            private set
        var currentAlarmId: Long = -1L
            private set
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        if (action == ACTION_DISMISS) {
            val alarmId = intent.getLongExtra("ALARM_ID", -1L)
            dismissCurrentAlarm(alarmId)
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_ALARM) {
            val alarmId = intent.getLongExtra("ALARM_ID", -1L)
            val alarmTime = intent.getStringExtra("ALARM_TIME") ?: "00:00"
            val isBreak = intent.getBooleanExtra("IS_BREAK", false)

            currentAlarmId = alarmId
            isRinging = true

            startForeground(NOTIFICATION_ID, buildAlarmNotification(alarmId, alarmTime, isBreak))
            startSound(alarmId)
            startVibration()
        }

        return START_STICKY
    }

    private fun startSound(alarmId: Long) {
        stopSound()
        try {
            val storage = AlarmStorage(this)
            val selectedSound = storage.getSelectedSound()

            val rawResId = when (selectedSound) {
                "track_001" -> R.raw.track_001
                "track_002" -> R.raw.track_002
                "track_003" -> R.raw.track_003
                "alarm" -> R.raw.alarm
                else -> {
                    // Random each alarm
                    val tracks = listOf(R.raw.alarm, R.raw.track_001, R.raw.track_002, R.raw.track_003)
                    tracks.random()
                }
            }

            mediaPlayer = MediaPlayer.create(this, rawResId).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                start()
            }
            Log.d(TAG, "Playing alarm sound resource $rawResId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm sound", e)
        }
    }

    private fun startVibration() {
        stopVibration()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val pattern = longArrayOf(0, 800, 400, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sound", e)
        }
        mediaPlayer = null
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun dismissCurrentAlarm(alarmId: Long) {
        val targetId = if (alarmId != -1L) alarmId else currentAlarmId
        Log.d(TAG, "Dismissing alarm ID $targetId")

        // 1. Remove dismissed alarm from storage
        val storage = AlarmStorage(this)
        val alarms = storage.loadAlarms()
        val updated = alarms.filter { it.id != targetId }
        storage.saveAlarms(updated)

        // 2. Broadcast to UI so MainActivity refreshes immediately
        sendBroadcast(Intent("com.iwillkam.routineclock.ALARM_LIST_UPDATED"))

        isRinging = false
        currentAlarmId = -1L
        stopSound()
        stopVibration()
    }

    private fun buildAlarmNotification(alarmId: Long, alarmTime: String, isBreak: Boolean): Notification {
        val title = "⏰ Routine Alarm: $alarmTime"
        val message = if (isBreak) "Break is over! Time to start your session." else "Session complete! Time for a break."

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_TIME", alarmTime)
            putExtra("IS_BREAK", isBreak)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = ACTION_DISMISS
            putExtra("ALARM_ID", alarmId)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            alarmId.toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Dismiss Alarm", dismissPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Alarm Ringing",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full screen notification displayed when an alarm is actively ringing"
                setSound(null, null) // Sound is handled directly by MediaPlayer
                enableVibration(false) // Vibration is handled directly by Vibrator
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopSound()
        stopVibration()
        isRinging = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
