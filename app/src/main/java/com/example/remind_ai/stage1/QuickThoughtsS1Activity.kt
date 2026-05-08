package com.example.remind_ai.stage1

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class QuickThoughtsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etThought: TextInputEditText
    private lateinit var btnSaveThought: MaterialButton

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_thoughts_s1)

        btnBack = findViewById(R.id.btnBack)
        etThought = findViewById(R.id.etThought)
        btnSaveThought = findViewById(R.id.btnSaveThought)

        btnBack.setOnClickListener {
            finish()
        }

        btnSaveThought.setOnClickListener {
            saveThought()
        }
    }

    private fun saveThought() {
        val uid = auth.currentUser?.uid
        val thoughtText = etThought.text?.toString()?.trim().orEmpty()

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (thoughtText.isEmpty()) {
            etThought.error = "Please write something"
            etThought.requestFocus()
            return
        }

        val thoughtId = UUID.randomUUID().toString()
        val currentTime = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        val thoughtData = hashMapOf(
            "id" to thoughtId,
            "text" to thoughtText,
            "createdAt" to System.currentTimeMillis(),
            "formattedTime" to currentTime
        )

        btnSaveThought.isEnabled = false
        btnSaveThought.text = "Saving..."

        db.child("quick_thoughts").child(uid).child(thoughtId).setValue(thoughtData)
            .addOnSuccessListener {
                Toast.makeText(this, "Thought saved successfully", Toast.LENGTH_SHORT).show()
                etThought.text?.clear()
                btnSaveThought.isEnabled = true
                btnSaveThought.text = "Save Thought"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save thought", Toast.LENGTH_SHORT).show()
                btnSaveThought.isEnabled = true
                btnSaveThought.text = "Save Thought"
            }
    }
}
