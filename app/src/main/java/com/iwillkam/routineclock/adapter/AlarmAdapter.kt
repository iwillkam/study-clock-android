package com.iwillkam.routineclock.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.iwillkam.routineclock.R
import com.iwillkam.routineclock.databinding.ItemAlarmCardBinding
import com.iwillkam.routineclock.model.AlarmItem

class AlarmAdapter(
    private var alarms: MutableList<AlarmItem>,
    private val onAlarmChanged: (idx: Int, newDuration: Int) -> Unit,
    private val onAlarmToggled: (alarm: AlarmItem, isEnabled: Boolean) -> Unit,
    private val onAlarmDeleted: (alarm: AlarmItem) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    fun updateList(newAlarms: List<AlarmItem>) {
        alarms.clear()
        alarms.addAll(newAlarms)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val binding = ItemAlarmCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlarmViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        holder.bind(alarm, position)
    }

    override fun getItemCount(): Int = alarms.size

    inner class AlarmViewHolder(private val binding: ItemAlarmCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alarm: AlarmItem, position: Int) {
            binding.tvAlarmTime.text = alarm.time
            binding.tvDurationReadout.text = "${alarm.durationMinutes}m"
            binding.cbEnabled.isChecked = alarm.isEnabled

            // Visual differentiation: Session (Green Accent) vs Break (Purple)
            val context = binding.root.context
            if (alarm.isBreak) {
                binding.cardView.strokeColor = ContextCompat.getColor(context, R.color.purple)
                binding.tvTag.text = "Break"
                binding.tvTag.setTextColor(ContextCompat.getColor(context, R.color.purple))
            } else {
                binding.cardView.strokeColor = ContextCompat.getColor(context, R.color.accent)
                binding.tvTag.text = "Session"
                binding.tvTag.setTextColor(ContextCompat.getColor(context, R.color.accent))
            }

            // Seekbar (Slider)
            binding.seekBarDuration.max = 180
            binding.seekBarDuration.progress = alarm.durationMinutes.coerceIn(1, 180)

            binding.cbEnabled.setOnCheckedChangeListener { _, isChecked ->
                alarm.isEnabled = isChecked
                onAlarmToggled(alarm, isChecked)
            }

            binding.btnMinus.setOnClickListener {
                if (alarm.durationMinutes > 1) {
                    val newDur = alarm.durationMinutes - 1
                    onAlarmChanged(bindingAdapterPosition, newDur)
                }
            }

            binding.btnPlus.setOnClickListener {
                if (alarm.durationMinutes < 240) {
                    val newDur = alarm.durationMinutes + 1
                    onAlarmChanged(bindingAdapterPosition, newDur)
                }
            }

            binding.btnDelete.setOnClickListener {
                onAlarmDeleted(alarm)
            }

            binding.seekBarDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val validDur = progress.coerceAtLeast(1)
                        binding.tvDurationReadout.text = "${validDur}m"
                        onAlarmChanged(bindingAdapterPosition, validDur)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
    }
}
