package com.example.remind_ai.stage1

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.remind_ai.R

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Reminder"
        val notes = intent.getStringExtra("notes") ?: "You have a reminder."

        // Play alarm sound and show full screen activity for dementia patients
        val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            putExtra("title", title)
            putExtra("notes", notes)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(alarmIntent)

        // Also show a standard notification as a backup
        val openIntent = Intent(context, Stage1Activity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "reminder_channel")
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(notes.ifEmpty { "It's time for your reminder." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(notes.ifEmpty { "It's time for your reminder." }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
