package com.example.remind_ai.stage1

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.remind_ai.R
import com.example.remind_ai.voice.CommandIntent
import com.example.remind_ai.voice.ReminderVoiceManager
import com.example.remind_ai.voice.VoiceCommandParser
import org.json.JSONArray
import java.util.Locale

/**
 * Voice Assistant Activity with integrated voice-based reminder system
 * 
 * Features:
 * - Continuous wake word detection using Picovoice Porcupine
 * - Speech recognition for voice commands
 * - Text-to-speech for assistant responses
 * - Voice-based reminder creation with intelligent parsing
 * - Voice-based reminder queries
 * 
 * Voice Command Examples:
 * - "Set reminder at 5 PM for meeting"
 * - "What is my upcoming reminder"
 */
class VoiceAssistantActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceAssistantActivity"
        // Note: Replace with your actual Picovoice access key from https://console.picovoice.ai
        private const val PICOVOICE_ACCESS_KEY = "YOUR_PICOVOICE_ACCESS_KEY_HERE"
    }

    // UI Components
    private lateinit var btnBack: ImageView
    private lateinit var micBtn: ImageView
    private lateinit var tvGoodMorning: TextView
    private lateinit var tvHelp: TextView
    private lateinit var tvSpeak: TextView

    private lateinit var btnReminder: Button
    private lateinit var btnMessages: Button
    private lateinit var btnSchedule: Button
    private lateinit var btnFamily: Button

    // Speech and Audio Components
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var prefs: SharedPreferences

    // Voice Assistant Managers
    private lateinit var commandParser: VoiceCommandParser
    private lateinit var reminderManager: ReminderVoiceManager

    private var isTtsReady = false
    private var isListeningForCommand = false
    private var speechRecognitionServiceAvailable = true
    private val listeningTimeoutHandler = Handler(Looper.getMainLooper())
    private val LISTENING_TIMEOUT_MS = 30000L // 30 seconds timeout

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startListeningForCommand()
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voiceassistant_s1)

        // Initialize UI components
        initializeUIComponents()
        
        // Initialize shared preferences and text-to-speech
        prefs = getSharedPreferences("remind_ai_prefs", MODE_PRIVATE)
        textToSpeech = TextToSpeech(this, this)

        // Initialize voice assistant components
        commandParser = VoiceCommandParser()
        reminderManager = ReminderVoiceManager(this)

        // Setup UI event listeners
        setupUIListeners()
        
        // Setup speech recognizer
        setupSpeechRecognizer()
        
        // Greet user
        setupGreeting()

        // If launched with a test command (for automated testing), process it directly
        intent?.getStringExtra("test_command")?.let { testCmd ->
            // Delay slightly to allow initialization to complete
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Received test command via intent: $testCmd")
                processVoiceCommand(testCmd.lowercase(Locale.getDefault()))
            }, 500L)
        }

        // Debug: create a reminder directly via intent extras for automated testing
        if (intent?.hasExtra("test_create_title") == true) {
            val title = intent.getStringExtra("test_create_title") ?: "Test Reminder"
            val offsetMinutes = intent.getIntExtra("test_create_offset_minutes", 1)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // Compute target date/time offset from now
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.MINUTE, offsetMinutes)
                    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val timeFormat = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                    val dateStr = dateFormat.format(cal.time)
                    val timeStr = timeFormat.format(cal.time)

                    val parsed = com.example.remind_ai.voice.ParsedCommand(
                        intent = com.example.remind_ai.voice.CommandIntent.CREATE_REMINDER,
                        title = title,
                        time = timeStr,
                        date = dateStr
                    )

                    Log.d(TAG, "[TEST] Creating reminder via intent: $title at $timeStr on $dateStr")

                    reminderManager.createReminderFromVoice(parsed,
                        onSuccess = { message ->
                            Log.d(TAG, "[TEST] Reminder created: $message")
                        },
                        onError = { err ->
                            Log.e(TAG, "[TEST] Error creating reminder: $err")
                        }
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "[TEST] Error in test create reminder", e)
                }
            }, 700L)
        }

        Log.d(TAG, "Voice Assistant Activity initialized")
    }

    /**
     * Initialize all UI components from layout
     */
    private fun initializeUIComponents() {
        btnBack = findViewById(R.id.btnBack)
        micBtn = findViewById(R.id.micBtn)
        tvGoodMorning = findViewById(R.id.tvGoodMorning)
        tvHelp = findViewById(R.id.tvHelp)
        tvSpeak = findViewById(R.id.tvSpeak)

        btnReminder = findViewById(R.id.btnReminder)
        btnMessages = findViewById(R.id.btnMessages)
        btnSchedule = findViewById(R.id.btnSchedule)
        btnFamily = findViewById(R.id.btnFamily)
    }

    /**
     * Setup click listeners for all UI buttons
     */
    private fun setupUIListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        micBtn.setOnClickListener {
            checkPermissionAndListen()
        }

        tvSpeak.setOnClickListener {
            checkPermissionAndListen()
        }

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

    /**
     * Setup SpeechRecognizer for voice command recognition
     * This captures user voice input after wake word or manual activation
     */
    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition is NOT available on this device")
            speechRecognitionServiceAvailable = false
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateUIStatus("Listening...")
                Log.d(TAG, "Speech recognizer ready for speech")
            }

            override fun onBeginningOfSpeech() {
                updateUIStatus("Listening for your command...")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                updateUIStatus("Processing...")
                Log.d(TAG, "End of speech detected")
            }

            override fun onError(error: Int) {
                listeningTimeoutHandler.removeCallbacksAndMessages(null)
                updateUIStatus("Tap to Speak")
                
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error. Speech Recognition Service may not be available."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please speak clearly."
                    11 -> "Speech Recognition Service not available. Please install Google App or update Google Play Services."
                    else -> "Unknown error: $error"
                }
                
                // Mark speech recognition as unavailable if it's a service error
                if (error == SpeechRecognizer.ERROR_CLIENT || error == 11) {
                    speechRecognitionServiceAvailable = false
                }
                
                Log.e(TAG, "Speech recognition error: $errorMessage")
                Toast.makeText(this@VoiceAssistantActivity, errorMessage, Toast.LENGTH_SHORT).show()
                
                // Resume listening after error
                resumeListening()
            }

            override fun onResults(results: Bundle?) {
                updateUIStatus("Processing command...")
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                
                if (matches != null && matches.isNotEmpty()) {
                    val spokenCommand = matches[0].lowercase(Locale.getDefault())
                    Log.d(TAG, "Speech recognized: $spokenCommand")
                    
                    // Process the voice command
                    if (isListeningForCommand) {
                        isListeningForCommand = false
                        processVoiceCommand(spokenCommand)
                    }
                } else {
                    Log.w(TAG, "No speech matches returned")
                    Toast.makeText(this@VoiceAssistantActivity, "Could not understand. Please try again.", Toast.LENGTH_SHORT).show()
                    resumeListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    /**
     * Check microphone permission and start listening for commands
     */
    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListeningForCommand()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Start listening for voice command with timeout and error handling
     */
    private fun startListeningForCommand() {
        if (isListeningForCommand) {
            Log.w(TAG, "Already listening for command")
            return
        }
        
        if (!speechRecognitionServiceAvailable) {
            Toast.makeText(
                this,
                "Speech Recognition Service not available. Please install Google App or update Google Play Services.",
                Toast.LENGTH_LONG
            ).show()
            speak("Speech recognition service is not available on this device")
            return
        }
        
        try {
            // Cancel any existing sessions to avoid "Recognizer busy" error
            speechRecognizer.cancel()
            
            isListeningForCommand = true
            updateUIStatus("Listening...")
            
            // Set timeout for speech recognition (30 seconds)
            listeningTimeoutHandler.removeCallbacksAndMessages(null)
            listeningTimeoutHandler.postDelayed({
                if (isListeningForCommand) {
                    Log.w(TAG, "Speech recognition timeout - no speech detected")
                    isListeningForCommand = false
                    speechRecognizer.stopListening()
                    updateUIStatus("Tap to Speak")
                    Toast.makeText(this, "No speech detected. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }, LISTENING_TIMEOUT_MS)
            
            speechRecognizer.startListening(speechIntent)
            Log.d(TAG, "Started listening for voice command")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            listeningTimeoutHandler.removeCallbacksAndMessages(null)
            isListeningForCommand = false
            
            val errorMsg = when {
                e.message?.contains("RecognitionService") == true -> 
                    "Speech Recognition Service not found. Install Google App."
                else -> "Error starting voice recognition: ${e.message}"
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            speak(errorMsg)
        }
    }

    /**
     * Resume listening for commands after completing previous command
     */
    private fun resumeListening() {
        updateUIStatus("Tap to Speak")
        isListeningForCommand = false
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Process voice command and handle accordingly
     * Parses the command to extract intent and parameters
     * 
     * @param command The recognized voice command
     */
    private fun processVoiceCommand(command: String) {
        Log.d(TAG, "Processing command: $command")
        
        // Parse the voice command to extract intent and parameters
        val parsedCommand = commandParser.parseCommand(command)
        
        when (parsedCommand.intent) {
            CommandIntent.CREATE_REMINDER -> {
                handleCreateReminder(parsedCommand)
            }
            CommandIntent.GET_REMINDER -> {
                handleGetReminder()
            }
            CommandIntent.UNKNOWN -> {
                handleUnknownCommand(command)
            }
        }
    }

    /**
     * Handle create reminder command
     * Extracts title, time, and date from the parsed command
     * and saves it to Firebase
     */
    private fun handleCreateReminder(parsedCommand: com.example.remind_ai.voice.ParsedCommand) {
        Log.d(TAG, "Creating reminder: ${parsedCommand.title}")
        
        // Validate parsed data
        if (parsedCommand.title.isEmpty()) {
            speak("Please say the reminder title")
            resumeListening()
            return
        }
        
        // Call reminder manager to create the reminder
        reminderManager.createReminderFromVoice(
            parsedCommand,
            onSuccess = { message ->
                Log.d(TAG, "Reminder created successfully: $message")
                Toast.makeText(this, "Reminder Added", Toast.LENGTH_SHORT).show()
                speak("Reminder added successfully for ${parsedCommand.title} at ${parsedCommand.time}")
                resumeListening()
            },
            onError = { errorMessage ->
                Log.e(TAG, "Error creating reminder: $errorMessage")
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                speak(errorMessage)
                resumeListening()
            }
        )
    }

    /**
     * Handle get/query reminder command
     * Fetches the next upcoming reminder and speaks it
     */
    private fun handleGetReminder() {
        Log.d(TAG, "Fetching upcoming reminder")
        updateUIStatus("Fetching your reminder...")
        
        reminderManager.getUpcomingReminder(
            onSuccess = { reminderText ->
                Log.d(TAG, "Retrieved reminder: $reminderText")
                Toast.makeText(this, reminderText, Toast.LENGTH_LONG).show()
                speak(reminderText)
                resumeListening()
            },
            onError = { errorMessage ->
                Log.e(TAG, "Error fetching reminder: $errorMessage")
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                speak(errorMessage)
                resumeListening()
            }
        )
    }

    /**
     * Handle commands that don't match known intents
     * Attempts to match against legacy commands for backward compatibility
     */
    private fun handleUnknownCommand(command: String) {
        Log.d(TAG, "Command not recognized as reminder command: $command")
        
        // Legacy command handling for backward compatibility
        when {
            command.contains("open chatbot") || command.contains("personal chatbot") -> {
                speak("Opening chatbot")
                startActivity(Intent(this, PersonalChatbotS1Activity::class.java))
                resumeListening()
            }

            command.contains("open journal") || command.contains("my journal") -> {
                speak("Opening journal")
                startActivity(Intent(this, MyJournalActivity::class.java))
                resumeListening()
            }

            command.contains("open quick thought") || command.contains("quick thought pad") -> {
                speak("Opening quick thoughts")
                startActivity(Intent(this, QuickThoughtsActivity::class.java))
                resumeListening()
            }

            command.contains("open reminders") || command.contains("my reminders") -> {
                speak("Opening reminders")
                startActivity(Intent(this, AddReminderS1Activity::class.java))
                resumeListening()
            }

            command.contains("add quick thought") || command.contains("save thought") -> {
                val thoughtText = extractQuickThoughtText(command)
                saveQuickThought(thoughtText)
                Toast.makeText(this, "Quick thought added", Toast.LENGTH_SHORT).show()
                speak("Quick thought added")
                resumeListening()
            }

            command.contains("checklist") && command.contains("unchecked") -> {
                val response = getChecklistStatusResponse()
                Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
                speak(response)
                resumeListening()
            }

            command.contains("schedule") -> {
                val response = "Your daily schedule is available in reminders."
                Toast.makeText(this, response, Toast.LENGTH_SHORT).show()
                speak(response)
                resumeListening()
            }

            else -> {
                Toast.makeText(this, "Command not recognized", Toast.LENGTH_SHORT).show()
                speak("Sorry, I did not understand that command. Please try again.")
                resumeListening()
            }
        }
    }

    /**
     * Update UI status text (used for listening, processing, etc.)
     */
    private fun updateUIStatus(status: String) {
        tvSpeak.text = status
    }

    private fun extractQuickThoughtText(command: String): String {
        return when {
            command.contains("add quick thought") ->
                command.substringAfter("add quick thought").trim().ifEmpty { "New quick thought" }

            command.contains("save thought") ->
                command.substringAfter("save thought").trim().ifEmpty { "New quick thought" }

            else -> "New quick thought"
        }
    }

    /**
     * Save quick thought to SharedPreferences
     */
    private fun saveQuickThought(thoughtText: String) {
        val thoughts = getStringList("quick_thoughts")
        thoughts.add(thoughtText)
        saveStringList("quick_thoughts", thoughts)
    }

    /**
     * Get checklist status response
     */
    private fun getChecklistStatusResponse(): String {
        val checklist = getStringList("unchecked_checklist")
        return if (checklist.isEmpty()) {
            "No, all checklist activities are completed."
        } else {
            "Yes, you still have unchecked activities."
        }
    }

    /**
     * Get string list from SharedPreferences (JSON format)
     */
    private fun getStringList(key: String): MutableList<String> {
        val json = prefs.getString(key, null) ?: return mutableListOf()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    /**
     * Save string list to SharedPreferences (JSON format)
     */
    private fun saveStringList(key: String, list: List<String>) {
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it) }
        prefs.edit().putString(key, jsonArray.toString()).apply()
    }

    /**
     * Speak text using TextToSpeech
     * @param message The message to speak
     */
    private fun speak(message: String) {
        if (isTtsReady) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "voice_assistant")
                } else {
                    @Suppress("DEPRECATION")
                    textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null)
                }
                Log.d(TAG, "Speaking: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking text", e)
            }
        } else {
            Log.w(TAG, "TextToSpeech not ready yet")
        }
    }

    /**
     * TextToSpeech initialization callback
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                textToSpeech.language = Locale.UK
                isTtsReady = true
                Log.d(TAG, "TextToSpeech initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TextToSpeech", e)
                isTtsReady = false
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            isTtsReady = false
        }
    }

    /**
     * Cleanup resources when activity is destroyed
     * Important: Stop listening and release TTS to prevent memory leaks
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroying Voice Assistant Activity")
        
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
        
        try {
            // Stop listening and release speech recognizer
            isListeningForCommand = false
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
        
        try {
            // Stop TTS and release resources
            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
                textToSpeech.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying TextToSpeech", e)
        }
    }

    /**
     * Handle pause - stop speech recognition when activity is paused
     */
    override fun onPause() {
        super.onPause()
        listeningTimeoutHandler.removeCallbacksAndMessages(null)
        try {
            isListeningForCommand = false
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing speech recognizer", e)
        }
    }

    /**
     * Handle resume - can restart listening if needed
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Voice Assistant Activity resumed")
    }
}
