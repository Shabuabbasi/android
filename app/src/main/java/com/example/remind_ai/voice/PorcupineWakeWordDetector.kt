package com.example.remind_ai.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import ai.picovoice.porcupine.Porcupine

/**
 * Wrapper for Picovoice Porcupine wake word detection
 * Handles continuous listening for "Hey Assistant" wake word
 * 
 * SETUP REQUIREMENTS:
 * 1. Get a Picovoice AccessKey from https://console.picovoice.ai
 * 2. Add the AccessKey to your app's build.gradle or as a BuildConfig field
 * 3. Download the custom wake word model for "Hey Assistant" from Picovoice console
 * 4. Place the model file in assets/models/
 */
class PorcupineWakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val TAG = "PorcupineDetector"
        
        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val FRAME_LENGTH = 512
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val scope = MainScope()
    private var audioThread: Thread? = null

    /**
     * Initialize Porcupine with custom wake word
     * Must be called before startListening()
     */
    fun initialize() {
        try {
            // Build Porcupine with custom wake word model
            // Using the "Hey Assistant" wake word
            porcupine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywords(arrayOf(Porcupine.BuiltInKeyword.COMPUTER)) // Fallback to built-in keyword
                // For custom "Hey Assistant" wake word, use:
                // .setKeywordPaths(arrayOf(context.assets.openFd("models/hey_assistant.ppn").fileDescriptor))
                .build(context)

            Log.d(TAG, "Porcupine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Porcupine", e)
            onError("Failed to initialize wake word detector: ${e.message}")
        }
    }

    /**
     * Start listening for wake word
     * Initializes AudioRecord and starts background thread for audio processing
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }

        try {
            if (porcupine == null) {
                initialize()
            }

            // Initialize AudioRecord
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            isListening = true

            // Start background thread for wake word detection
            audioThread = Thread {
                try {
                    processAudioFrames()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in audio processing thread", e)
                    onError("Error in wake word detection: ${e.message}")
                }
            }.apply {
                start()
            }

            Log.d(TAG, "Wake word detection started")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            onError("Failed to start wake word detection: ${e.message}")
            isListening = false
        }
    }

    /**
     * Stop listening for wake word
     */
    fun stopListening() {
        isListening = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioThread?.interrupt()
            audioThread?.join(1000)
            audioThread = null

            Log.d(TAG, "Wake word detection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
    }

    /**
     * Process audio frames and detect wake word
     * This runs on a background thread
     */
    private fun processAudioFrames() {
        val frame = ShortArray(FRAME_LENGTH)
        
        while (isListening && audioRecord != null) {
            try {
                val numRead = audioRecord!!.read(frame, 0, frame.size)
                
                if (numRead == FRAME_LENGTH) {
                    // Process frame with Porcupine
                    val keywordIndex = porcupine?.process(frame) ?: -1
                    
                    // Check if any keyword was detected
                    if (keywordIndex >= 0) {
                        Log.d(TAG, "Wake word detected!")
                        isListening = false
                        
                        // Callback on main thread
                        scope.launch {
                            onWakeWordDetected()
                        }
                        
                        // Stop listening after detection
                        stopListening()
                        break
                    }
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e(TAG, "Error processing audio frame", e)
                }
            }
        }
    }

    /**
     * Cleanup and release resources
     * Must be called in onDestroy()
     */
    fun cleanup() {
        try {
            stopListening()
            
            porcupine?.delete()
            porcupine = null

            Log.d(TAG, "Porcupine cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Check if currently listening
     */
    fun isDetecting(): Boolean = isListening
}
