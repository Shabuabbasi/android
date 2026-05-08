package com.example.remind_ai.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var btnPatient: Button
    private lateinit var btnCaregiver: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        btnPatient = findViewById(R.id.btnPatient)
        btnCaregiver = findViewById(R.id.btnCaregiver)

        btnPatient.setOnClickListener {
            saveUserRole("patient")
        }

        btnCaregiver.setOnClickListener {
            saveUserRole("caregiver")
        }
    }

    private fun saveUserRole(role: String) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        database.reference
            .child("users")
            .child(uid)
            .child("role")
            .setValue(role)
            .addOnSuccessListener {
                Toast.makeText(this, "Role selected: $role", Toast.LENGTH_SHORT).show()

                if (role == "patient") {
                    startActivity(Intent(this, StageDetectionActivity::class.java))
                } else {
                    startActivity(Intent(this, CaregiverHomeActivity::class.java))
                }
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Failed to save role: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
