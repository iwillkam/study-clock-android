package com.iwillkam.routineclock.model

data class AlarmItem(
    val id: Long,
    var time: String,               // "HH:mm"
    var durationMinutes: Int,       // Duration in minutes
    var isBreak: Boolean,           // true = break, false = session
    var isEnabled: Boolean = true,  // Toggle switch
    var triggerTimestamp: Long = 0L // Epoch milliseconds when alarm should fire
)
