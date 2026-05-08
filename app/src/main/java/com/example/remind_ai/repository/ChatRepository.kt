package com.example.remind_ai.repository

import android.util.Log
import com.example.remind_ai.api.ChatBotApiService
import com.example.remind_ai.api.GroqChatRequest
import com.example.remind_ai.api.GroqChatResponse
import com.example.remind_ai.api.GroqMessage
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatRepository(private val apiKey: String) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val chatBotApiService = retrofit.create(ChatBotApiService::class.java)

    private val conversationHistory = mutableListOf<GroqMessage>()

    fun sendMessage(
        userMessage: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Add user message to history
        conversationHistory.add(GroqMessage("user", userMessage))

        // Create request with conversation history
        val request = GroqChatRequest(
            model = "llama-3.3-70b-versatile",
            messages = conversationHistory
        )

        Log.d("ChatRepository", "Sending message: $userMessage")
        Log.d("ChatRepository", "API Key: ${apiKey.take(10)}...")
        Log.d("ChatRepository", "Request: $request")

        val call = chatBotApiService.sendMessage(
            authorization = "Bearer $apiKey",
            request = request
        )

        call.enqueue(object : Callback<GroqChatResponse> {
            override fun onResponse(call: Call<GroqChatResponse>, response: Response<GroqChatResponse>) {
                Log.d("ChatRepository", "Response Code: ${response.code()}")
                Log.d("ChatRepository", "Response Body: ${response.body()}")
                Log.d("ChatRepository", "Response Error Body: ${response.errorBody()?.string()}")

                if (response.isSuccessful && response.body() != null) {
                    val responseBody = response.body()!!
                    
                    if (responseBody.error != null) {
                        val errorMsg = "API Error: ${responseBody.error.message}"
                        Log.e("ChatRepository", errorMsg)
                        onError(errorMsg)
                        return
                    }

                    val assistantMessage = responseBody.choices.firstOrNull()?.message?.content
                    if (assistantMessage != null) {
                        // Add assistant message to history
                        conversationHistory.add(GroqMessage("assistant", assistantMessage))
                        Log.d("ChatRepository", "Success: $assistantMessage")
                        onSuccess(assistantMessage)
                    } else {
                        val errorMsg = "No response from API"
                        Log.e("ChatRepository", errorMsg)
                        onError(errorMsg)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    val errorMsg = "API Error ${response.code()}: $errorBody"
                    Log.e("ChatRepository", errorMsg)
                    onError(errorMsg)
                }
            }

            override fun onFailure(call: Call<GroqChatResponse>, t: Throwable) {
                val errorMsg = "Network Error: ${t.message}"
                Log.e("ChatRepository", errorMsg, t)
                onError(errorMsg)
            }
        })
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}
