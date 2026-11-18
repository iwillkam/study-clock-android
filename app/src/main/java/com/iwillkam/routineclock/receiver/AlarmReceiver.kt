package com.iwillkam.routineclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.iwillkam.routineclock.AlarmRingingActivity
import com.iwillkam.routineclock.service.AlarmSoundService

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("ALARM_ID", -1L)
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: "00:00"
        val isBreak = intent.getBooleanExtra("IS_BREAK", false)

        Log.d(TAG, "Alarm received: ID=$alarmId, Time=$alarmTime, isBreak=$isBreak")

        // Acquire a temporary wake lock so CPU stays awake to launch the service and activity
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "RoutineClock:AlarmWakeLock"
        )
        wakeLock?.acquire(15000L) // 15 seconds max

        // 1. Start foreground alarm sound and vibration service
        val serviceIntent = Intent(context, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_START_ALARM
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_TIME", alarmTime)
            putExtra("IS_BREAK", isBreak)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 2. Launch full-screen ringing activity
        val activityIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_TIME", alarmTime)
            putExtra("IS_BREAK", isBreak)
        }
        context.startActivity(activityIntent)
    }
}
