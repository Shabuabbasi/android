package com.example.remind_ai.stage1

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import com.example.remind_ai.model.ChecklistItem

class RoutineChecklistActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var btnAddTask: Button
    private lateinit var tvProgress: TextView
    private lateinit var tvChecklistDate: TextView
    private lateinit var tvEmptyTasks: TextView
    private lateinit var progressChecklist: ProgressBar
    private lateinit var tasksContainer: LinearLayout

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val checklistItems = mutableListOf<ChecklistItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routinechecklist_s1)

        btnBack = findViewById(R.id.btnBack)
        btnAddTask = findViewById(R.id.btnAddTask)
        tvProgress = findViewById(R.id.tvProgress)
        tvChecklistDate = findViewById(R.id.tvChecklistDate)
        tvEmptyTasks = findViewById(R.id.tvEmptyTasks)
        progressChecklist = findViewById(R.id.progressChecklist)
        tasksContainer = findViewById(R.id.tasksContainer)

        createNotificationChannel()
        setTodayDate()
        updateProgress()
        loadChecklist()

        btnBack.setOnClickListener {
            finish()
        }

        btnAddTask.setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun setTodayDate() {
        val format = SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault())
        tvChecklistDate.text = format.format(Calendar.getInstance().time)
    }

    private fun getUid(): String? {
        return auth.currentUser?.uid
    }

    private fun loadChecklist() {
        val uid = getUid()
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            updateProgress()
            return
        }

        db.child("checklists").child(uid).child("items").get()
            .addOnSuccessListener { snapshot ->
                checklistItems.clear()
                tasksContainer.removeAllViews()

                for (child in snapshot.children) {
                    val item = child.getValue(ChecklistItem::class.java)
                    if (item != null) {
                        checklistItems.add(item)
                    }
                }

                checklistItems.forEach { item ->
                    addTaskView(item)
                }

                updateProgress()
                scheduleUncheckedReminder()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load checklist", Toast.LENGTH_SHORT).show()
                updateProgress()
            }
    }

    private fun showAddTaskDialog() {
        val input = EditText(this)
        input.hint = "Enter task"

        AlertDialog.Builder(this)
            .setTitle("Add New Task")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    saveTask(title)
                } else {
                    Toast.makeText(this, "Task cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveTask(title: String) {
        val uid = getUid()
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val item = ChecklistItem(
            id = UUID.randomUUID().toString(),
            title = title,
            checked = false
        )

        db.child("checklists").child(uid).child("items").child(item.id).setValue(item)
            .addOnSuccessListener {
                checklistItems.add(item)
                addTaskView(item)
                updateProgress()
                scheduleUncheckedReminder()
                Toast.makeText(this, "Task added", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to add task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addTaskView(item: ChecklistItem) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 24, 24, 24)
            background = getDrawable(R.drawable.bg_edittext_dark)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20
            }
        }

        val checkBox = CheckBox(this).apply {
            isChecked = item.checked
        }

        val titleView = TextView(this).apply {
            text = item.title
            textSize = 15f
            setTextColor(resources.getColor(android.R.color.black, theme))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 16
            }
        }

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            updateTaskStatus(item, isChecked)
        }

        row.addView(checkBox)
        row.addView(titleView)
        tasksContainer.addView(row)
    }

    private fun updateTaskStatus(item: ChecklistItem, checked: Boolean) {
        val uid = getUid() ?: return

        db.child("checklists").child(uid).child("items").child(item.id).child("checked")
            .setValue(checked)
            .addOnSuccessListener {
                val index = checklistItems.indexOfFirst { it.id == item.id }
                if (index != -1) {
                    checklistItems[index] = checklistItems[index].copy(checked = checked)
                }
                updateProgress()
                scheduleUncheckedReminder()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProgress() {
        val total = checklistItems.size
        val completed = checklistItems.count { it.checked }

        tvProgress.text = "Completed: $completed of $total"

        if (total == 0) {
            progressChecklist.max = 1
            progressChecklist.progress = 0
            tvEmptyTasks.visibility = TextView.VISIBLE
        } else {
            progressChecklist.max = total
            progressChecklist.progress = completed
            tvEmptyTasks.visibility = TextView.GONE
        }
    }

    private fun scheduleUncheckedReminder() {
        val uncheckedItems = checklistItems.filter { !it.checked }
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val message = if (uncheckedItems.isEmpty()) {
            ""
        } else {
            "Left in checklist: " + uncheckedItems.joinToString { it.title }
        }

        val intent = Intent(this, ChecklistReminderReceiver::class.java).apply {
            putExtra("message", message)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        if (uncheckedItems.isEmpty()) return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "checklist_channel",
                "Checklist Reminder",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminder for unchecked checklist tasks"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
