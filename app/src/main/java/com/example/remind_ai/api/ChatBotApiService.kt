package com.example.remind_ai.api

import com.example.remind_ai.model.ChatSendMessageRequest
import com.example.remind_ai.model.ChatSendMessageResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ChatBotApiService {
    @POST("openai/v1/chat/completions")
    fun sendMessage(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GroqChatRequest
    ): Call<GroqChatResponse>
}

data class GroqChatRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1024
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqChatResponse(
    val id: String = "",
    val objectType: String = "",
    val created: Long = 0L,
    val model: String = "",
    val choices: List<GroqChoice> = emptyList(),
    val usage: GroqUsage? = null,
    val error: GroqError? = null
)

data class GroqChoice(
    val index: Int = 0,
    val message: GroqMessage = GroqMessage("", ""),
    val finish_reason: String = ""
)

data class GroqUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

data class GroqError(
    val message: String = "",
    val type: String = "",
    val param: String? = null,
    val code: String? = null
)
