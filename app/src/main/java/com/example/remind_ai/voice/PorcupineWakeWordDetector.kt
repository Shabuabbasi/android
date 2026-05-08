package com.example.remind_ai.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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

    // Picovoice removed: this detector is a no-op stub when Picovoice is not used.
    private var isListening = false
    private val scope = MainScope()
    private var disabled = true

    /**
     * Initialize Porcupine with custom wake word
     * Must be called before startListening()
     */
    fun initialize() {
        try {
            // Since Picovoice dependency has been removed, keep detector disabled.
            disabled = true
            Log.w(TAG, "Porcupine wake-word detector disabled (Picovoice not available)")
            onError("Wake-word detector disabled (Picovoice not available)")
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
            // Detector disabled — do not access microphone.
            Log.w(TAG, "Wake-word detector disabled; startListening() no-op")
            onError("Wake-word detector disabled; not listening")
            return

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
        // No-op for disabled detector
        isListening = false
        Log.d(TAG, "stopListening() called on disabled wake-word detector")
    }

    /**
     * Process audio frames and detect wake word
     * This runs on a background thread
     */
    private fun processAudioFrames() {
        // No processing — Picovoice removed
    }

    /**
     * Cleanup and release resources
     * Must be called in onDestroy()
     */
    fun cleanup() {
        try {
            stopListening()
            Log.d(TAG, "Porcupine wake-word detector cleanup (no-op)")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    /**
     * Check if currently listening
     */
    fun isDetecting(): Boolean = isListening
}
