package com.iwillkam.routineclock.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.iwillkam.routineclock.MainActivity
import com.iwillkam.routineclock.model.AlarmItem
import com.iwillkam.routineclock.receiver.AlarmReceiver

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleAlarm(context: Context, alarm: AlarmItem) {
        if (!alarm.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.iwillkam.routineclock.ALARM_TRIGGER"
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_TIME", alarm.time)
            putExtra("IS_BREAK", alarm.isBreak)
        }

        val requestCode = (alarm.id % Int.MAX_VALUE).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = alarm.triggerTimestamp

        try {
            // AlarmClockInfo is the highest priority alarm on Android OS:
            // - Wakes device from deep Doze mode
            // - Shows system alarm clock icon in status bar
            // - Exact firing guaranteed by Android OS
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Scheduled alarm ID ${alarm.id} for time ${alarm.time} at epoch $triggerTime")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission missing, falling back to setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.iwillkam.routineclock.ALARM_TRIGGER"
        }
        val requestCode = (alarmId % Int.MAX_VALUE).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm ID $alarmId")
        }
    }

    fun rescheduleAll(context: Context, alarms: List<AlarmItem>) {
        val now = System.currentTimeMillis()
        for (alarm in alarms) {
            if (alarm.isEnabled && alarm.triggerTimestamp > now) {
                scheduleAlarm(context, alarm)
            }
        }
    }
}
