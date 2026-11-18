package com.iwillkam.routineclock.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.iwillkam.routineclock.model.AlarmItem

class AlarmStorage(context: Context) {
    private val prefs = context.getSharedPreferences("routine_clock_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveAlarms(alarms: List<AlarmItem>) {
        val json = gson.toJson(alarms)
        prefs.edit().putString("saved_alarms", json).apply()
    }

    fun loadAlarms(): MutableList<AlarmItem> {
        val json = prefs.getString("saved_alarms", null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<AlarmItem>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveSelectedSound(soundId: String) {
        prefs.edit().putString("selected_sound", soundId).apply()
    }

    fun getSelectedSound(): String {
        return prefs.getString("selected_sound", "random") ?: "random"
    }

    fun saveMode(mode: String) {
        prefs.edit().putString("mode", mode).apply()
    }

    fun getMode(): String {
        return prefs.getString("mode", "target_hours") ?: "target_hours"
    }

    fun saveGoalHours(hours: Float) {
        prefs.edit().putFloat("goal_hours", hours).apply()
    }

    fun getGoalHours(): Float {
        return prefs.getFloat("goal_hours", 5.0f)
    }

    fun saveSessionDuration(minutes: Int) {
        prefs.edit().putInt("session_dur", minutes).apply()
    }

    fun getSessionDuration(): Int {
        return prefs.getInt("session_dur", 30)
    }

    fun saveBreakDuration(minutes: Int) {
        prefs.edit().putInt("break_dur", minutes).apply()
    }

    fun getBreakDuration(): Int {
        return prefs.getInt("break_dur", 7)
    }

    fun saveTargetStartTime(time: String) {
        prefs.edit().putString("target_start_time", time).apply()
    }

    fun getTargetStartTime(): String {
        return prefs.getString("target_start_time", "08:00") ?: "08:00"
    }

    fun saveRangeStartTime(time: String) {
        prefs.edit().putString("range_start_time", time).apply()
    }

    fun getRangeStartTime(): String {
        return prefs.getString("range_start_time", "05:00") ?: "05:00"
    }

    fun saveRangeEndTime(time: String) {
        prefs.edit().putString("range_end_time", time).apply()
    }

    fun getRangeEndTime(): String {
        return prefs.getString("range_end_time", "17:00") ?: "17:00"
    }
}
