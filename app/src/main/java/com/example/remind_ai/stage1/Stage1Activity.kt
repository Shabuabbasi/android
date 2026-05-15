package com.example.remind_ai.stage1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.remind_ai.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Stage1Activity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvTodayLabel: TextView
    private lateinit var tvCurrentDate: TextView
    private lateinit var cardDailyReminders: CardView
    private lateinit var cardVoiceAssistant: CardView
    private lateinit var cardChatbot: CardView
    private lateinit var cardRoutineChecklist: CardView
    private lateinit var cardQuickThoughtsPad: CardView
    private lateinit var cardMyJornal: CardView

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private lateinit var textToSpeech: TextToSpeech

    private var isTtsReady = false
    private var isOpeningVoiceAssistant = false
    private var isListening = false

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startWakeWordListening()
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_01)

        tvTodayLabel = findViewById(R.id.tvTodayLabel)
        tvCurrentDate = findViewById(R.id.tvCurrentDate)
        cardDailyReminders = findViewById(R.id.cardDailyReminders)
        cardVoiceAssistant = findViewById(R.id.cardVoiceAssistant)
        cardChatbot = findViewById(R.id.cardChatbot)
        cardRoutineChecklist = findViewById(R.id.cardRoutineChecklist)
        cardQuickThoughtsPad = findViewById(R.id.cardQuickThoughtsPad)
        cardMyJornal = findViewById(R.id.cardMyJornal)

        updateCurrentDate()

        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()

        cardDailyReminders.setOnClickListener {
            startActivity(Intent(this, AddReminderS1Activity::class.java))
        }

        cardVoiceAssistant.setOnClickListener {
            openVoiceAssistant()
        }

        cardChatbot.setOnClickListener {
            startActivity(Intent(this, PersonalChatbotS1Activity::class.java))
        }

        cardRoutineChecklist.setOnClickListener {
            startActivity(Intent(this, RoutineChecklistActivity::class.java))
        }

        cardQuickThoughtsPad.setOnClickListener {
            startActivity(Intent(this, QuickThoughtsActivity::class.java))
        }

        cardMyJornal.setOnClickListener {
            startActivity(Intent(this, MyJournalActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentDate()
        isOpeningVoiceAssistant = false

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startWakeWordListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    private fun updateCurrentDate() {
        val currentDate = Date()
        val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
        tvTodayLabel.text = "Today"
        tvCurrentDate.text = dateFormat.format(currentDate)
    }

    private fun setupSpeechRecognizer() {
        val recognizerIntent = Intent(android.speech.RecognitionService.SERVICE_INTERFACE)
        recognizerIntent.setPackage("com.google.android.googlequicksearchbox")
        val services = packageManager.queryIntentServices(recognizerIntent, 0)

        if (services.isEmpty()) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        } else {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(
                this,
                android.content.ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
                )
            )
        }

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                isListening = false
                if (!isOpeningVoiceAssistant) {
                    restartListening()
                }
            }

            override fun onError(error: Int) {
                isListening = false
                if (!isOpeningVoiceAssistant) {
                    restartListening()
                }
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.lowercase(Locale.getDefault()).orEmpty()

                if (spokenText.contains("remind ai")) {
                    speakAndOpenAssistant()
                } else if (!isOpeningVoiceAssistant) {
                    restartListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun startWakeWordListening() {
        if (!::speechRecognizer.isInitialized || isListening || isOpeningVoiceAssistant) return
        speechRecognizer.startListening(speechIntent)
    }

    private fun stopListening() {
        if (::speechRecognizer.isInitialized && isListening) {
            speechRecognizer.cancel()
            isListening = false
        }
    }

    private fun restartListening() {
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed && !isOpeningVoiceAssistant) {
                startWakeWordListening()
            }
        }, 800)
    }

    private fun speakAndOpenAssistant() {
        isOpeningVoiceAssistant = true
        val message = "Yes, your assistant is here. How may I help you?"

        if (isTtsReady) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "wake_word_response")
            window.decorView.postDelayed({
                openVoiceAssistant()
            }, 2200)
        } else {
            openVoiceAssistant()
        }
    }

    private fun openVoiceAssistant() {
        startActivity(Intent(this, VoiceAssistantActivity::class.java))
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.UK
            isTtsReady = true
        }
    }
}
