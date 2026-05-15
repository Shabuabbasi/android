package com.example.remind_ai.voice

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import android.os.Build
import com.example.remind_ai.BuildConfig
import com.example.remind_ai.api.GroqWhisperApiService
import com.example.remind_ai.api.WhisperResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException

class GroqWhisperManager(private val context: Context) {

    companion object {
        private const val TAG = "GroqWhisperManager"
        private const val BASE_URL = "https://api.groq.com/"
    }

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val apiService: GroqWhisperApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(GroqWhisperApiService::class.java)
    }

    fun startRecording() {
        if (isRecording) return

        try {
            audioFile = File(context.cacheDir, "voice_input.m4a")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d(TAG, "Recording started: ${audioFile?.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Recording failed to start", e)
        }
    }

    fun stopRecording(onResult: (String?) -> Unit) {
        if (!isRecording) {
            onResult(null)
            return
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            Log.d(TAG, "Recording stopped")

            audioFile?.let { file ->
                if (file.exists()) {
                    transcribeAudio(file, onResult)
                } else {
                    onResult(null)
                }
            } ?: onResult(null)

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onResult(null)
        }
    }

    private fun transcribeAudio(file: File, onResult: (String?) -> Unit) {
        val requestFile = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val authHeader = "Bearer ${BuildConfig.GROQ_API_KEY}"

        apiService.transcribeAudio(authHeader, body).enqueue(object : Callback<WhisperResponse> {
            override fun onResponse(call: Call<WhisperResponse>, response: Response<WhisperResponse>) {
                if (response.isSuccessful) {
                    val text = response.body()?.text
                    Log.i(TAG, "Transcription successful: $text")
                    onResult(text)
                } else {
                    Log.e(TAG, "Transcription failed: ${response.code()} ${response.errorBody()?.string()}")
                    onResult(null)
                }
            }

            override fun onFailure(call: Call<WhisperResponse>, t: Throwable) {
                Log.e(TAG, "Transcription network error", t)
                onResult(null)
            }
        })
    }
}
