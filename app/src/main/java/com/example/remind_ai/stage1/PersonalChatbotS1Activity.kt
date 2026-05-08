package com.example.remind_ai.stage1

import android.os.Bundle
import com.example.remind_ai.BuildConfig
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.remind_ai.R
import com.example.remind_ai.adapter.MessageAdapter
import com.example.remind_ai.databinding.ActivityPersonalchatbotS1Binding
import com.example.remind_ai.model.ChatMessage
import com.example.remind_ai.repository.ChatRepository

class PersonalChatbotS1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalchatbotS1Binding
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var chatRepository: ChatRepository
    private val messages = mutableListOf<ChatMessage>()

    companion object {
        //API key injected into BuildConfig from Gradle (local.properties or env)
        private val API_KEY: String = try { BuildConfig.GROQ_API_KEY } catch (e: Exception) { "" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalchatbotS1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize chat repository
        chatRepository = ChatRepository(API_KEY)

        // Setup RecyclerView
        setupRecyclerView()

        // Setup listeners
        setupListeners()
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages)
        binding.recyclerViewMessages.apply {
            layoutManager = LinearLayoutManager(this@PersonalChatbotS1Activity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun setupListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Send button
        binding.buttonSend.setOnClickListener {
            val messageText = binding.editTextMessage.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        // Add button (e.g., for attachments or quick actions)
        binding.btnAdd.setOnClickListener {
            Toast.makeText(this, "Add feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage(messageText: String) {
        // Add user message to UI
        val userMessage = ChatMessage(messageText, isUser = true)
        messages.add(userMessage)
        messageAdapter.notifyItemInserted(messages.size - 1)
        binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)

        // Clear input field
        binding.editTextMessage.text.clear()
        hideKeyboard()

        // Disable send button while loading
        binding.buttonSend.isEnabled = false

        // Send to API
        chatRepository.sendMessage(
            userMessage = messageText,
            onSuccess = { response ->
                runOnUiThread {
                    // Add bot response to UI
                    val botMessage = ChatMessage(response, isUser = false)
                    messages.add(botMessage)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)

                    // Re-enable send button
                    binding.buttonSend.isEnabled = true
                }
            },
            onError = { error ->
                runOnUiThread {
                    // Add error message to UI
                    val errorMessage = ChatMessage("Error: $error", isUser = false)
                    messages.add(errorMessage)
                    messageAdapter.notifyItemInserted(messages.size - 1)
                    binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)

                    // Re-enable send button
                    binding.buttonSend.isEnabled = true
                    Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.editTextMessage.windowToken, 0)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (currentFocus != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        }
        return super.dispatchTouchEvent(ev)
    }
}
