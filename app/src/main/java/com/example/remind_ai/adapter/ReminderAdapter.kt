package com.example.remind_ai.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.remind_ai.R
import com.example.remind_ai.model.ReminderModel

class ReminderAdapter(
    private var reminders: List<ReminderModel>,
    private val onDeleteClick: (ReminderModel) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    class ReminderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvReminderTitle)
        val tvTime: TextView = view.findViewById(R.id.tvReminderTime)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        holder.tvTitle.text = reminder.title
        holder.tvTime.text = "${reminder.time} - ${reminder.date}"
        
        holder.btnDelete.setOnClickListener {
            onDeleteClick(reminder)
        }
    }

    override fun getItemCount() = reminders.size

    fun updateData(newList: List<ReminderModel>) {
        reminders = newList
        notifyDataSetChanged()
    }
}
