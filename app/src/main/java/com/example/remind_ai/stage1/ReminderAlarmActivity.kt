package com.example.remind_ai.stage1

import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R

class ReminderAlarmActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_alarm)

        val title = intent.getStringExtra("title") ?: "Reminder"
        val notes = intent.getStringExtra("notes") ?: ""

        findViewById<TextView>(R.id.tvAlarmTitle).text = title
        findViewById<TextView>(R.id.tvAlarmNotes).text = notes.ifEmpty { "It's time for your reminder!" }

        findViewById<Button>(R.id.btnStopAlarm).setOnClickListener {
            stopAlarm()
            finish()
        }

        // Handle back button to ensure user uses the 'I REMEMBERED' button
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing, force user to use the button
            }
        })

        startAlarm()
    }

    private fun startAlarm() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@ReminderAlarmActivity, alarmUri)
                setLooping(true)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
