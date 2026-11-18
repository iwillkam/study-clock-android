package com.iwillkam.routineclock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.iwillkam.routineclock.util.AlarmScheduler
import com.iwillkam.routineclock.util.AlarmStorage

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootReceiver triggered with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            val storage = AlarmStorage(context)
            val alarms = storage.loadAlarms()
            Log.d(TAG, "Rescheduling ${alarms.size} alarms after system event")
            AlarmScheduler.rescheduleAll(context, alarms)
        }
    }
}
