package com.example.remind_ai.voice

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.remind_ai.model.ReminderModel
import com.example.remind_ai.stage1.ReminderReceiver
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Manager class for all reminder-related voice operations
 * Handles:
 * - Creating reminders from voice commands
 * - Fetching upcoming reminders
 * - Setting up alarms for reminders
 */
class ReminderVoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "ReminderVoiceManager"
        private const val REMINDER_CHANNEL_ID = "reminder_notifications"
    }

    private val database = FirebaseDatabase.getInstance().reference
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Create reminder from parsed voice command
     * @param parsedCommand The parsed command containing title, time, date
     * @param onSuccess Callback when reminder is successfully created
     * @param onError Callback if error occurs
     */
    fun createReminderFromVoice(
        parsedCommand: ParsedCommand,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (parsedCommand.title.isEmpty()) {
                onError("Reminder title is empty")
                return
            }

            if (parsedCommand.time.isEmpty()) {
                onError("Reminder time is not set")
                return
            }

            if (parsedCommand.date.isEmpty()) {
                onError("Reminder date is not set")
                return
            }

            // Validate that the reminder time is in the future
            val reminderCalendar = parseReminderDateTime(parsedCommand.date, parsedCommand.time)
            if (reminderCalendar.timeInMillis <= System.currentTimeMillis()) {
                onError("Please set a reminder for a future time")
                return
            }

            // Generate unique reminder ID
            val reminderId = database.child("reminders").push().key ?: return

            // Create reminder model
            val reminder = ReminderModel(
                id = reminderId,
                title = parsedCommand.title,
                date = parsedCommand.date,
                time = parsedCommand.time,
                repeat = "No Repeat",
                notes = parsedCommand.notes,
                timestamp = reminderCalendar.timeInMillis
            )

            // Save to Firebase
            database.child("reminders").child(reminderId).setValue(reminder)
                .addOnSuccessListener {
                    Log.d(TAG, "Reminder saved: ${reminder.title} at ${reminder.time}")
                    
                    // Set alarm for the reminder
                    setReminderAlarm(reminder)
                    
                    val message = "Reminder '${reminder.title}' set for ${reminder.time} on ${reminder.date}"
                    onSuccess(message)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving reminder", e)
                    onError("Failed to save reminder: ${e.message}")
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating reminder from voice", e)
            onError("Error creating reminder: ${e.message}")
        }
    }

    /**
     * Fetch the next upcoming reminder
     * @param onSuccess Callback with reminder text to speak
     * @param onError Callback if no reminders found
     */
    fun getUpcomingReminder(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        database.child("reminders")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val reminders = mutableListOf<ReminderModel>()
                        
                        // Collect all reminders
                        for (reminderSnapshot in snapshot.children) {
                            val reminder = reminderSnapshot.getValue(ReminderModel::class.java)
                            reminder?.let { reminders.add(it) }
                        }

                        if (reminders.isEmpty()) {
                            onError("You don't have any reminders")
                            return
                        }

                        // Find the next upcoming reminder (timestamp >= current time)
                        val currentTime = System.currentTimeMillis()
                        val upcomingReminder = reminders
                            .filter { it.timestamp >= currentTime }
                            .minByOrNull { it.timestamp }

                        if (upcomingReminder == null) {
                            onError("You don't have any upcoming reminders")
                            return
                        }

                        val responseText = formatReminderForSpeech(upcomingReminder)
                        onSuccess(responseText)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing reminders", e)
                        onError("Error fetching reminders")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}")
                    onError("Error fetching reminders from database")
                }
            })
    }

    /**
     * Format reminder for speech output
     * Example: "Your next reminder is Meeting at 5 PM on 15/05/2024"
     */
    private fun formatReminderForSpeech(reminder: ReminderModel): String {
        return "Your next reminder is ${reminder.title} at ${reminder.time} on ${reminder.date}"
    }

    /**
     * Set alarm for reminder notification
     */
    private fun setReminderAlarm(reminder: ReminderModel) {
        try {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("reminder_title", reminder.title)
                putExtra("reminder_id", reminder.id)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.timestamp,
                pendingIntent
            )

            Log.d(TAG, "Alarm set for reminder: ${reminder.title}")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting alarm", e)
        }
    }

    /**
     * Parse date and time strings into Calendar
     * @param dateStr Format: "dd/MM/yyyy"
     * @param timeStr Format: "hh:mm a"
     */
    private fun parseReminderDateTime(dateStr: String, timeStr: String): Calendar {
        val calendar = Calendar.getInstance()
        
        try {
            // Parse date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val parsedDate = dateFormat.parse(dateStr)
            parsedDate?.let {
                val tempCalendar = Calendar.getInstance()
                tempCalendar.time = it
                calendar.set(Calendar.YEAR, tempCalendar.get(Calendar.YEAR))
                calendar.set(Calendar.MONTH, tempCalendar.get(Calendar.MONTH))
                calendar.set(Calendar.DAY_OF_MONTH, tempCalendar.get(Calendar.DAY_OF_MONTH))
            }

            // Parse time
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val parsedTime = timeFormat.parse(timeStr)
            parsedTime?.let {
                val tempCalendar = Calendar.getInstance()
                tempCalendar.time = it
                calendar.set(Calendar.HOUR_OF_DAY, tempCalendar.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, tempCalendar.get(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date/time", e)
        }

        return calendar
    }
}
