package com.example.remind_ai.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GroqWhisperApiService {
    @Multipart
    @POST("openai/v1/audio/transcriptions")
    fun transcribeAudio(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaTypeOrNull()),
        @Part("response_format") responseFormat: RequestBody = "json".toRequestBody("text/plain".toMediaTypeOrNull()),
        @Part("language") language: RequestBody? = null
    ): Call<WhisperResponse>
}

data class WhisperResponse(
    val text: String = "",
    val error: GroqError? = null
)
