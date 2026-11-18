package com.iwillkam.routineclock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.iwillkam.routineclock.databinding.ActivityAlarmRingingBinding
import com.iwillkam.routineclock.service.AlarmSoundService

class AlarmRingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingingBinding
    private var alarmId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityAlarmRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getLongExtra("ALARM_ID", -1L)
        val alarmTime = intent.getStringExtra("ALARM_TIME") ?: "00:00"
        val isBreak = intent.getBooleanExtra("IS_BREAK", false)

        binding.tvAlarmTime.text = alarmTime
        binding.tvAlarmStatus.text = if (isBreak) "☕ Break Finished!" else "🎉 Session Finished!"
        binding.tvAlarmSubtitle.text = if (isBreak) "Time to focus on your next session." else "Time to take a well-deserved break."

        binding.btnDismiss.setOnClickListener {
            dismissAlarm()
        }

        // Back button also dismisses the alarm
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismissAlarm()
            }
        })
    }

    private fun dismissAlarm() {
        val dismissIntent = Intent(this, AlarmSoundService::class.java).apply {
            action = AlarmSoundService.ACTION_DISMISS
            putExtra("ALARM_ID", alarmId)
        }
        startService(dismissIntent)
        finish()
    }
}
