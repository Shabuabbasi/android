package com.example.remind_ai.stage1

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.remind_ai.R
import com.example.remind_ai.voice.CommandIntent
import com.example.remind_ai.voice.GroqWhisperManager
import com.example.remind_ai.voice.ReminderVoiceManager
import com.example.remind_ai.voice.VoiceCommandParser
import org.json.JSONArray
import java.util.Locale

/**
 * Voice Assistant Activity using Groq Whisper for robust speech-to-text
 */
class VoiceAssistantActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAssistantActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // UI Components
    private lateinit var btnBack: ImageView
    private lateinit var micBtn: ImageView
    private lateinit var tvGoodMorning: TextView
    private lateinit var tvHelp: TextView
    private lateinit var tvSpeak: TextView
    private lateinit var tvLiveSpeech: TextView

    private lateinit var btnReminder: Button
    private lateinit var btnMessages: Button
    private lateinit var btnSchedule: Button
    private lateinit var btnFamily: Button

    // Components
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var prefs: SharedPreferences
    private lateinit var commandParser: VoiceCommandParser
    private lateinit var reminderManager: ReminderVoiceManager
    private lateinit var groqWhisperManager: GroqWhisperManager

    private var isTtsReady = false
    private var isRecording = false
    private val listeningTimeoutHandler = Handler(Looper.getMainLooper())
    private val LISTENING_TIMEOUT_MS = 15000L // 15 seconds max recording

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voiceassistant_s1)

        // Initialize UI components
        initializeUIComponents()
        
        // Initialize managers
        prefs = getSharedPreferences("remind_ai_prefs", MODE_PRIVATE)
        textToSpeech = TextToSpeech(this, this)
        commandParser = VoiceCommandParser()
        reminderManager = ReminderVoiceManager(this)
        groqWhisperManager = GroqWhisperManager(this)

        // Setup UI event listeners
        setupUIListeners()
        setupGreeting()

        Log.i(TAG, "Voice Assistant Activity initialized with Groq Whisper")
    }

    private fun initializeUIComponents() {
        btnBack = findViewById(R.id.btnBack)
        micBtn = findViewById(R.id.micBtn)
        tvGoodMorning = findViewById(R.id.tvGoodMorning)
        tvHelp = findViewById(R.id.tvHelp)
        tvSpeak = findViewById(R.id.tvSpeak)
        tvLiveSpeech = findViewById(R.id.tvLiveSpeech)

        btnReminder = findViewById(R.id.btnReminder)
        btnMessages = findViewById(R.id.btnMessages)
        btnSchedule = findViewById(R.id.btnSchedule)
        btnFamily = findViewById(R.id.btnFamily)
    }

    private fun setupUIListeners() {
        btnBack.setOnClickListener { finish() }
        micBtn.setOnClickListener { checkPermissionAndListen() }
        tvSpeak.setOnClickListener { checkPermissionAndListen() }

        btnReminder.setOnClickListener {
            startActivity(Intent(this, AddReminderS1Activity::class.java))
        }
        btnMessages.setOnClickListener {
            startActivity(Intent(this, QuickThoughtsActivity::class.java))
        }
        btnSchedule.setOnClickListener {
            speak("Opening reminders for your daily schedule")
            startActivity(Intent(this, AddReminderS1Activity::class.java))
        }
        btnFamily.setOnClickListener {
            startActivity(Intent(this, MyJournalActivity::class.java))
        }
    }

    private fun setupGreeting() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        tvGoodMorning.text = when {
            hour < 12 -> "Good Morning,"
            hour < 17 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
        tvHelp.text = "How can I help you today?"
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        } else {
            if (isRecording) {
                stopRecordingAndProcess()
            } else {
                startRecordingFlow()
            }
        }
    }

    private fun startRecordingFlow() {
        if (isRecording) return
        
        try {
            isRecording = true
            updateUIStatus("Recording...")
            tvLiveSpeech.text = "Listening to your voice..."
            tvLiveSpeech.visibility = android.view.View.VISIBLE
            
            groqWhisperManager.startRecording()
            
            listeningTimeoutHandler.removeCallbacksAndMessages(null)
            listeningTimeoutHandler.postDelayed({
                if (isRecording) stopRecordingAndProcess()
            }, LISTENING_TIMEOUT_MS)
            
            Log.i(TAG, "Started recording for Groq Whisper")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            isRecording = false
            updateUIStatus("Tap to Speak")
        }
    }

    private fun stopRecordingAndProcess() {
        if (!isRecording) return
        
        isRecording = false
        updateUIStatus("Processing...")
        tvLiveSpeech.text = "Transcribing with AI..."
        
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
        
        groqWhisperManager.stopRecording { text ->
            runOnUiThread {
                if (text != null && text.isNotEmpty()) {
                    Log.i(TAG, "Groq Transcription: $text")
                    tvLiveSpeech.text = text
                    processVoiceCommand(text)
                } else {
                    Log.w(TAG, "Transcription failed or was empty")
                    updateUIStatus("Tap to Speak")
                    tvLiveSpeech.text = "Could not hear you clearly. Try again."
                    tvLiveSpeech.postDelayed({
                        tvLiveSpeech.visibility = android.view.View.GONE
                    }, 3000)
                }
            }
        }
    }

    private fun processVoiceCommand(command: String) {
        Log.i(TAG, "Processing command: $command")
        var parsedCommand = commandParser.parseCommand(command)
        
        if (parsedCommand.intent == CommandIntent.CREATE_REMINDER) {
            // Ensure the spoken text is what they see as the reminder
            parsedCommand = parsedCommand.copy(
                title = command,
                notes = "Original spoken command: $command"
            )
        }
        
        when (parsedCommand.intent) {
            CommandIntent.CREATE_REMINDER -> handleCreateReminder(parsedCommand)
            CommandIntent.GET_REMINDER -> handleGetReminder()
            CommandIntent.OPEN_CHATBOT -> {
                speak("Opening your personal chatbot")
                startActivity(Intent(this, PersonalChatbotS1Activity::class.java))
                resumeListening()
            }
            CommandIntent.OPEN_JOURNAL -> {
                speak("Opening your journal")
                startActivity(Intent(this, MyJournalActivity::class.java))
                resumeListening()
            }
            CommandIntent.OPEN_QUICK_THOUGHTS -> {
                speak("Opening quick thoughts pad")
                startActivity(Intent(this, QuickThoughtsActivity::class.java))
                resumeListening()
            }
            CommandIntent.OPEN_REMINDERS -> {
                speak("Opening your reminders")
                startActivity(Intent(this, AddReminderS1Activity::class.java))
                resumeListening()
            }
            CommandIntent.SAVE_QUICK_THOUGHT -> handleSaveQuickThought(parsedCommand.title)
            CommandIntent.CHECKLIST_STATUS -> handleChecklistStatus()
            CommandIntent.VIEW_SCHEDULE -> {
                speak("Showing your daily schedule in reminders")
                startActivity(Intent(this, AddReminderS1Activity::class.java))
                resumeListening()
            }
            CommandIntent.UNKNOWN -> handleUnknownCommand(command)
        }
    }

    private fun handleSaveQuickThought(text: String) {
        if (text.isEmpty()) {
            speak("What thought would you like me to save?")
            resumeListening()
            return
        }
        updateUIStatus("Saving thought...")
        reminderManager.saveQuickThought(text,
            onSuccess = { message ->
                speak(message)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                resumeListening()
            },
            onError = { error ->
                speak(error)
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                resumeListening()
            }
        )
    }

    private fun handleChecklistStatus() {
        val checklist = getStringList("unchecked_checklist")
        val response = if (checklist.isEmpty()) {
            "All your checklist activities are completed."
        } else {
            "You still have ${checklist.size} unchecked activities in your routine."
        }
        speak(response)
        Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
        resumeListening()
    }

    private fun handleCreateReminder(parsedCommand: com.example.remind_ai.voice.ParsedCommand) {
        if (parsedCommand.title.isEmpty()) {
            speak("Please say the reminder title")
            resumeListening()
            return
        }
        reminderManager.createReminderFromVoice(parsedCommand,
            onSuccess = { message ->
                Toast.makeText(this, "Reminder Added", Toast.LENGTH_SHORT).show()
                speak("Reminder added successfully for ${parsedCommand.title} at ${parsedCommand.time}")
                resumeListening()
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                speak(errorMessage)
                resumeListening()
            }
        )
    }

    private fun handleGetReminder() {
        updateUIStatus("Fetching your reminder...")
        reminderManager.getUpcomingReminder(
            onSuccess = { reminderText ->
                Toast.makeText(this, reminderText, Toast.LENGTH_LONG).show()
                speak(reminderText)
                resumeListening()
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                speak(errorMessage)
                resumeListening()
            }
        )
    }

    private fun handleUnknownCommand(command: String) {
        Toast.makeText(this, "Command not recognized", Toast.LENGTH_SHORT).show()
        speak("Sorry, I did not understand that command. Please try again.")
        resumeListening()
    }

    private fun resumeListening() {
        updateUIStatus("Tap to Speak")
        isRecording = false
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
    }

    private fun updateUIStatus(status: String) {
        tvSpeak.text = status
    }

    private fun getStringList(key: String): MutableList<String> {
        val json = prefs.getString(key, null) ?: return mutableListOf()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    private fun speak(message: String) {
        if (isTtsReady) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "voice_assistant")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.UK
            isTtsReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    override fun onPause() {
        super.onPause()
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
        if (isRecording) {
            groqWhisperManager.stopRecording { }
            isRecording = false
        }
    }
}
