package com.iwillkam.routineclock

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.iwillkam.routineclock.adapter.AlarmAdapter
import com.iwillkam.routineclock.databinding.ActivityMainBinding
import com.iwillkam.routineclock.model.AlarmItem
import com.iwillkam.routineclock.model.SoundTrack
import com.iwillkam.routineclock.util.AlarmScheduler
import com.iwillkam.routineclock.util.AlarmStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: AlarmStorage
    private lateinit var adapter: AlarmAdapter
    private var alarms = mutableListOf<AlarmItem>()

    private var previewPlayer: MediaPlayer? = null
    private val tracks = listOf(
        SoundTrack("random", "🎲 Random each alarm", R.raw.alarm),
        SoundTrack("alarm", "♪ Classic Alarm", R.raw.alarm),
        SoundTrack("track_001", "♪ Arlequin waltz", R.raw.track_001),
        SoundTrack("track_002", "♪ Claire", R.raw.track_002),
        SoundTrack("track_003", "♪ Harmonic Blossoms", R.raw.track_003)
    )

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission needed for alarm alerts", Toast.LENGTH_LONG).show()
        }
    }

    private val alarmUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadAlarmsFromStorage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = AlarmStorage(this)

        checkPermissions()
        setupClockTick()
        setupModeSelector()
        setupSoundSelector()
        setupSettingsToggle()
        setupRecyclerView()
        setupActionButtons()
        loadAlarmsFromStorage()

        val filter = IntentFilter("com.iwillkam.routineclock.ALARM_LIST_UPDATED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alarmUpdateReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(alarmUpdateReceiver)
        stopSoundPreview()
        super.onDestroy()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager?.canScheduleExactAlarms() == false) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun setupClockTick() {
        lifecycleScope.launch {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (isActive) {
                binding.tvLiveClock.text = sdf.format(Date())
                delay(1000)
            }
        }
    }

    private fun setupModeSelector() {
        val modes = listOf("Target Hours", "Day Range")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        binding.spinnerMode.adapter = modeAdapter

        val savedMode = storage.getMode()
        binding.spinnerMode.setSelection(if (savedMode == "time_range") 1 else 0)

        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    binding.rowTargetMode.visibility = View.VISIBLE
                    binding.rowRangeMode.visibility = View.GONE
                    storage.saveMode("target_hours")
                } else {
                    binding.rowTargetMode.visibility = View.GONE
                    binding.rowRangeMode.visibility = View.VISIBLE
                    storage.saveMode("time_range")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Time pickers
        val nowCal = Calendar.getInstance()
        val defaultNowStr = String.format("%02d:%02d", nowCal.get(Calendar.HOUR_OF_DAY), nowCal.get(Calendar.MINUTE))

        binding.etTargetStartTime.setText(storage.getTargetStartTime())
        binding.etRangeStartTime.setText(storage.getRangeStartTime())
        binding.etRangeEndTime.setText(storage.getRangeEndTime())
        binding.etTargetHours.setText(storage.getGoalHours().toString())
        binding.etSessionDuration.setText(storage.getSessionDuration().toString())
        binding.etBreakDuration.setText(storage.getBreakDuration().toString())

        binding.etTargetStartTime.setOnClickListener { showTimePicker(binding.etTargetStartTime) }
        binding.etRangeStartTime.setOnClickListener { showTimePicker(binding.etRangeStartTime) }
        binding.etRangeEndTime.setOnClickListener { showTimePicker(binding.etRangeEndTime) }

        binding.btnNow.setOnClickListener {
            val c = Calendar.getInstance()
            val timeStr = String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            binding.etTargetStartTime.setText(timeStr)
            binding.etRangeStartTime.setText(timeStr)
            storage.saveTargetStartTime(timeStr)
            storage.saveRangeStartTime(timeStr)
        }
    }

    private fun showTimePicker(targetView: android.widget.EditText) {
        val cal = Calendar.getInstance()
        val currentText = targetView.text.toString()
        if (currentText.contains(":")) {
            val parts = currentText.split(":")
            cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: cal.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: cal.get(Calendar.MINUTE))
        }

        TimePickerDialog(this, { _, hourOfDay, minute ->
            val formatted = String.format("%02d:%02d", hourOfDay, minute)
            targetView.setText(formatted)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun setupSoundSelector() {
        val titles = tracks.map { it.title }
        val soundAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, titles)
        binding.spinnerSound.adapter = soundAdapter

        val savedSound = storage.getSelectedSound()
        val savedIndex = tracks.indexOfFirst { it.id == savedSound }.coerceAtLeast(0)
        binding.spinnerSound.setSelection(savedIndex)

        binding.spinnerSound.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                storage.saveSelectedSound(tracks[position].id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnTestSound.setOnClickListener {
            if (previewPlayer?.isPlaying == true) {
                stopSoundPreview()
            } else {
                startSoundPreview()
            }
        }
    }

    private fun startSoundPreview() {
        stopSoundPreview()
        val selectedIdx = binding.spinnerSound.selectedItemPosition
        val track = tracks.getOrElse(selectedIdx) { tracks[0] }
        val resId = if (track.id == "random") R.raw.alarm else track.rawResId

        previewPlayer = MediaPlayer.create(this, resId).apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener { stopSoundPreview() }
            start()
        }
        binding.btnTestSound.text = "⏹"
    }

    private fun stopSoundPreview() {
        try {
            previewPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {}
        previewPlayer = null
        binding.btnTestSound.text = "▶"
    }

    private fun setupSettingsToggle() {
        binding.btnToggleSettings.setOnClickListener {
            if (binding.layoutSettingsPanel.visibility == View.VISIBLE) {
                binding.layoutSettingsPanel.visibility = View.GONE
            } else {
                binding.layoutSettingsPanel.visibility = View.VISIBLE
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter(
            alarms,
            onAlarmChanged = { idx, newDuration -> onAlarmDurationChanged(idx, newDuration) },
            onAlarmToggled = { alarm, isEnabled -> onAlarmToggled(alarm, isEnabled) },
            onAlarmDeleted = { alarm -> onAlarmDeleted(alarm) }
        )
        binding.recyclerViewAlarms.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAlarms.adapter = adapter
    }

    private fun setupActionButtons() {
        binding.btnGenerate.setOnClickListener {
            generateRoutine()
        }

        binding.btnClear.setOnClickListener {
            clearAllAlarms()
        }
    }

    private fun generateRoutine() {
        val sessDur = binding.etSessionDuration.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 30
        val brkDur = binding.etBreakDuration.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 7
        storage.saveSessionDuration(sessDur)
        storage.saveBreakDuration(brkDur)

        // Cancel all previous alarms
        for (a in alarms) {
            AlarmScheduler.cancelAlarm(this, a.id)
        }
        alarms.clear()

        val now = Calendar.getInstance()
        val newAlarms = mutableListOf<AlarmItem>()
        val baseId = System.currentTimeMillis()

        if (binding.spinnerMode.selectedItemPosition == 0) {
            // Target Hours Mode
            val goalHours = binding.etTargetHours.text.toString().toFloatOrNull()?.coerceAtLeast(0.1f) ?: 5.0f
            storage.saveGoalHours(goalHours)
            val goalMins = (goalHours * 60).toInt()

            val startParts = binding.etTargetStartTime.text.toString().split(":")
            val sh = startParts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.HOUR_OF_DAY)
            val sm = startParts.getOrNull(1)?.toIntOrNull() ?: now.get(Calendar.MINUTE)
            storage.saveTargetStartTime(String.format("%02d:%02d", sh, sm))

            val startCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, sh)
                set(Calendar.MINUTE, sm)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If more than 12 hours in past today, advance to tomorrow
                if (timeInMillis < now.timeInMillis - 12 * 3600 * 1000) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            var accumMins = 0
            var accumGoal = 0
            var isSession = true

            while (accumGoal < goalMins) {
                val dur = if (isSession) minOf(sessDur, goalMins - accumGoal) else brkDur
                if (isSession) accumGoal += dur

                accumMins += dur
                val alarmCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.MINUTE, accumMins)
                }

                val timeStr = String.format("%02d:%02d", alarmCal.get(Calendar.HOUR_OF_DAY), alarmCal.get(Calendar.MINUTE))
                val item = AlarmItem(
                    id = baseId + newAlarms.size * 1000 + (1..999).random(),
                    time = timeStr,
                    durationMinutes = dur,
                    isBreak = !isSession,
                    isEnabled = true,
                    triggerTimestamp = alarmCal.timeInMillis
                )
                newAlarms.add(item)
                isSession = !isSession
            }

        } else {
            // Day Range Mode
            val startParts = binding.etRangeStartTime.text.toString().split(":")
            val endParts = binding.etRangeEndTime.text.toString().split(":")
            val sh = startParts.getOrNull(0)?.toIntOrNull() ?: 5
            val sm = startParts.getOrNull(1)?.toIntOrNull() ?: 0
            val eh = endParts.getOrNull(0)?.toIntOrNull() ?: 17
            val em = endParts.getOrNull(1)?.toIntOrNull() ?: 0

            storage.saveRangeStartTime(String.format("%02d:%02d", sh, sm))
            storage.saveRangeEndTime(String.format("%02d:%02d", eh, em))

            val startMins = sh * 60 + sm
            val endMins = eh * 60 + em
            var totalRangeMins = (endMins - startMins + 1440) % 1440
            if (totalRangeMins == 0) totalRangeMins = 1440

            val startCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, sh)
                set(Calendar.MINUTE, sm)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis + totalRangeMins * 60000L < now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            var accumMins = 0
            var isSession = true

            while (accumMins < totalRangeMins) {
                var dur = if (isSession) sessDur else brkDur
                if (accumMins + dur > totalRangeMins) {
                    dur = totalRangeMins - accumMins
                }
                accumMins += dur
                val alarmCal = (startCal.clone() as Calendar).apply {
                    add(Calendar.MINUTE, accumMins)
                }

                val timeStr = String.format("%02d:%02d", alarmCal.get(Calendar.HOUR_OF_DAY), alarmCal.get(Calendar.MINUTE))
                val item = AlarmItem(
                    id = baseId + newAlarms.size * 1000 + (1..999).random(),
                    time = timeStr,
                    durationMinutes = dur,
                    isBreak = !isSession,
                    isEnabled = true,
                    triggerTimestamp = alarmCal.timeInMillis
                )
                newAlarms.add(item)
                isSession = !isSession
            }
        }

        alarms.addAll(newAlarms)
        storage.saveAlarms(alarms)
        adapter.updateList(alarms)

        // Schedule all new alarms directly in AlarmManager
        for (item in alarms) {
            AlarmScheduler.scheduleAlarm(this, item)
        }

        Toast.makeText(this, "Generated ${alarms.size} independent alarms", Toast.LENGTH_SHORT).show()
    }

    private fun onAlarmDurationChanged(idx: Int, newDuration: Int) {
        if (idx < 0 || idx >= alarms.size) return

        val oldDur = alarms[idx].durationMinutes
        val validDur = newDuration.coerceIn(1, 240)
        alarms[idx].durationMinutes = validDur

        // Compute start timestamp of this alarm
        val startTs = if (alarms[idx].triggerTimestamp > 0) {
            alarms[idx].triggerTimestamp - (oldDur * 60000L)
        } else {
            System.currentTimeMillis()
        }

        var currTs = startTs
        val cal = Calendar.getInstance()

        // Cascade subsequent alarms cleanly
        for (i in idx until alarms.size) {
            // Cancel old alarm schedule
            AlarmScheduler.cancelAlarm(this, alarms[i].id)

            currTs += (alarms[i].durationMinutes * 60000L)
            alarms[i].triggerTimestamp = currTs
            cal.timeInMillis = currTs
            alarms[i].time = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

            // Re-schedule with new exact time
            if (alarms[i].isEnabled) {
                AlarmScheduler.scheduleAlarm(this, alarms[i])
            }
        }

        storage.saveAlarms(alarms)
        adapter.notifyItemRangeChanged(idx, alarms.size - idx)
    }

    private fun onAlarmToggled(alarm: AlarmItem, isEnabled: Boolean) {
        alarm.isEnabled = isEnabled
        if (isEnabled) {
            AlarmScheduler.scheduleAlarm(this, alarm)
        } else {
            AlarmScheduler.cancelAlarm(this, alarm.id)
        }
        storage.saveAlarms(alarms)
    }

    private fun onAlarmDeleted(alarm: AlarmItem) {
        AlarmScheduler.cancelAlarm(this, alarm.id)
        alarms.removeAll { it.id == alarm.id }
        storage.saveAlarms(alarms)
        adapter.updateList(alarms)
    }

    private fun clearAllAlarms() {
        for (alarm in alarms) {
            AlarmScheduler.cancelAlarm(this, alarm.id)
        }
        alarms.clear()
        storage.saveAlarms(alarms)
        adapter.updateList(alarms)
    }

    private fun loadAlarmsFromStorage() {
        alarms = storage.loadAlarms()
        adapter.updateList(alarms)
    }
}
