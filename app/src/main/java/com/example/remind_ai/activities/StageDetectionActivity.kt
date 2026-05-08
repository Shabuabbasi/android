package com.example.remind_ai.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID

import com.example.remind_ai.stage1.Stage1Activity
import com.example.remind_ai.stage2.Stage2DashboardActivity
import com.example.remind_ai.Stage3.Stage3PatientDashboardActivity

class StageDetectionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var btnBack: ImageView
    private lateinit var btnUploadMRI: MaterialButton
    private lateinit var etMemoryScore: TextInputEditText
    private lateinit var etCognitiveScore: TextInputEditText
    private lateinit var etBehaviorScore: TextInputEditText
    private lateinit var btnAnalyzeStage: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_detection)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        btnBack = findViewById(R.id.btnBack)
        btnUploadMRI = findViewById(R.id.btnUploadMRI)
        etMemoryScore = findViewById(R.id.etMemoryScore)
        etCognitiveScore = findViewById(R.id.etCognitiveScore)
        etBehaviorScore = findViewById(R.id.etBehaviorScore)
        btnAnalyzeStage = findViewById(R.id.btnAnalyzeStage)

        // ✅ FIXED BACK BUTTON
        btnBack.setOnClickListener {
            val intent = Intent(this, RoleSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        btnUploadMRI.setOnClickListener {
            Toast.makeText(this, "MRI upload will be connected later", Toast.LENGTH_SHORT).show()
        }

        btnAnalyzeStage.setOnClickListener {
            analyzeStage()
        }
    }

    private fun analyzeStage() {
        val memoryText = etMemoryScore.text.toString().trim()
        val cognitiveText = etCognitiveScore.text.toString().trim()
        val behaviorText = etBehaviorScore.text.toString().trim()

        if (memoryText.isEmpty() || cognitiveText.isEmpty() || behaviorText.isEmpty()) {
            Toast.makeText(this, "Please enter all values", Toast.LENGTH_SHORT).show()
            return
        }

        val memoryScore = memoryText.toIntOrNull()
        val cognitiveScore = cognitiveText.toIntOrNull()
        val behaviorScore = behaviorText.toIntOrNull()

        if (memoryScore == null || cognitiveScore == null || behaviorScore == null) {
            Toast.makeText(this, "Please enter valid numeric values", Toast.LENGTH_SHORT).show()
            return
        }

        btnAnalyzeStage.isEnabled = false
        btnAnalyzeStage.text = "Analyzing..."

        val detectedStage = detectStage(memoryScore, cognitiveScore, behaviorScore)
        savePatientAssessment(detectedStage, memoryScore, cognitiveScore, behaviorScore)
    }

    // Temporary logic (later replace with AI model)
    private fun detectStage(memory: Int, cognitive: Int, behavior: Int): String {
        val average = (memory + cognitive + behavior) / 3

        return when {
            average <= 33 -> "Stage 1"
            average <= 66 -> "Stage 2"
            else -> "Stage 3"
        }
    }

    private fun savePatientAssessment(
        stage: String,
        memory: Int,
        cognitive: Int,
        behavior: Int
    ) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            btnAnalyzeStage.isEnabled = true
            btnAnalyzeStage.text = "Analyze Stage"
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val patientRef = database.reference.child("patients").child(uid)
        val usersRef = database.reference.child("users").child(uid)

        val connectionCode = generateConnectionCode()

        val patientData = hashMapOf(
            "patientId" to uid,
            "userId" to uid,
            "fullName" to "",
            "stage" to stage,
            "memoryScore" to memory,
            "cognitiveScore" to cognitive,
            "behaviorScore" to behavior,
            "connectionCode" to connectionCode,
            "status" to "Stable",
            "alertCount" to 0,
            "lastUpdated" to "Just now",
            "createdAt" to System.currentTimeMillis()
        )

        patientRef.setValue(patientData)
            .addOnSuccessListener {
                usersRef.child("stage").setValue(stage)
                    .addOnSuccessListener {
                        btnAnalyzeStage.isEnabled = true
                        btnAnalyzeStage.text = "Analyze Stage"

                        Toast.makeText(
                            this,
                            "Detected $stage successfully",
                            Toast.LENGTH_LONG
                        ).show()

                        openDashboard(stage)
                    }
                    .addOnFailureListener { e ->
                        btnAnalyzeStage.isEnabled = true
                        btnAnalyzeStage.text = "Analyze Stage"

                        Toast.makeText(
                            this,
                            "Stage saved but user update failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()

                        openDashboard(stage)
                    }
            }
            .addOnFailureListener { e ->
                btnAnalyzeStage.isEnabled = true
                btnAnalyzeStage.text = "Analyze Stage"

                Toast.makeText(
                    this,
                    "Failed to save assessment: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun generateConnectionCode(): String {
        return UUID.randomUUID().toString().substring(0, 6).uppercase()
    }

    private fun openDashboard(stage: String) {
        val intent = when (stage) {
            "Stage 1" -> Intent(this, Stage1Activity::class.java)
            "Stage 2" -> Intent(this, Stage2DashboardActivity::class.java)
            else -> Intent(this, Stage3PatientDashboardActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}