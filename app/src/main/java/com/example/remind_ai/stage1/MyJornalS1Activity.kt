package com.example.remind_ai.stage1

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R
import com.example.remind_ai.model.JournalAnalysis
import com.example.remind_ai.model.JournalEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MyJournalActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var tvSubtitle: TextView
    private lateinit var etJournal: EditText
    private lateinit var btnSave: Button
    private lateinit var btnAnalyze: Button

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_myjornal_s1)

        btnBack = findViewById(R.id.btnBack)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        etJournal = findViewById(R.id.etJournal)
        btnSave = findViewById(R.id.btnSave)
        btnAnalyze = findViewById(R.id.btnAnalyze)

        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveJournalOnly()
        }

        btnAnalyze.setOnClickListener {
            analyzeAndSaveJournal()
        }
    }

    private fun saveJournalOnly() {
        val uid = auth.currentUser?.uid
        val text = etJournal.text.toString().trim()

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (text.isEmpty()) {
            etJournal.error = "Please write something"
            etJournal.requestFocus()
            return
        }

        val journalId = UUID.randomUUID().toString()
        val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        val entry = JournalEntry(
            id = journalId,
            text = text,
            mood = "Not analyzed",
            supportMessage = "",
            ayahArabic = "",
            ayahTranslation = "",
            createdAt = System.currentTimeMillis(),
            formattedDate = formattedDate
        )

        setButtonsEnabled(false)

        db.child("journals").child(uid).child(journalId).setValue(entry)
            .addOnSuccessListener {
                setButtonsEnabled(true)
                Toast.makeText(this, "Journal saved successfully", Toast.LENGTH_SHORT).show()
                etJournal.text.clear()
            }
            .addOnFailureListener {
                setButtonsEnabled(true)
                Toast.makeText(this, "Failed to save journal", Toast.LENGTH_SHORT).show()
            }
    }

    private fun analyzeAndSaveJournal() {
        val uid = auth.currentUser?.uid
        val text = etJournal.text.toString().trim()

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (text.isEmpty()) {
            etJournal.error = "Please write something"
            etJournal.requestFocus()
            return
        }

        val analysis = analyzeMood(text)
        val reference = MoodAyahRepository.randomReferenceForMood(analysis.mood)

        setButtonsEnabled(false)

        QuranAyahService.fetchAyah(
            reference = reference,
            onSuccess = { ayah ->
                runOnUiThread {
                    val journalId = UUID.randomUUID().toString()
                    val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

                    val entry = JournalEntry(
                        id = journalId,
                        text = text,
                        mood = analysis.mood,
                        supportMessage = analysis.supportMessage,
                        ayahArabic = ayah.arabic,
                        ayahTranslation = "${ayah.translation} (${ayah.reference})",
                        createdAt = System.currentTimeMillis(),
                        formattedDate = formattedDate
                    )

                    db.child("journals").child(uid).child(journalId).setValue(entry)
                        .addOnSuccessListener {
                            setButtonsEnabled(true)
                            showAnalysisDialog(
                                mood = analysis.mood,
                                supportMessage = analysis.supportMessage,
                                ayahArabic = ayah.arabic,
                                ayahTranslation = ayah.translation,
                                reference = ayah.reference
                            )
                        }
                        .addOnFailureListener {
                            setButtonsEnabled(true)
                            Toast.makeText(this, "Failed to save journal", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onError = { error ->
                runOnUiThread {
                    setButtonsEnabled(true)
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun analyzeMood(text: String): JournalAnalysis {
        val lower = text.lowercase(Locale.getDefault())

        val sadWords = listOf("sad", "lonely", "cry", "down", "depressed", "empty", "hopeless", "hurt")
        val stressWords = listOf("stress", "stressed", "anxious", "worried", "afraid", "angry", "frustrated", "tired")
        val positiveWords = listOf("happy", "good", "grateful", "peaceful", "better", "calm", "thankful", "fine")

        val sadCount = sadWords.count { lower.contains(it) }
        val stressCount = stressWords.count { lower.contains(it) }
        val positiveCount = positiveWords.count { lower.contains(it) }

        return when {
            sadCount >= stressCount && sadCount > positiveCount && sadCount > 0 ->
                JournalAnalysis(
                    mood = "Sad",
                    supportMessage = "You may be feeling low today. You are not alone, and difficult moments do pass."
                )

            stressCount > sadCount && stressCount > positiveCount && stressCount > 0 ->
                JournalAnalysis(
                    mood = "Stressed",
                    supportMessage = "It seems your heart may be carrying a lot today. Take a breath and remember Allah is near."
                )

            positiveCount > 0 ->
                JournalAnalysis(
                    mood = "Positive",
                    supportMessage = "It is beautiful to notice the good in your day. Keep holding onto gratitude and peace."
                )

            else ->
                JournalAnalysis(
                    mood = "Neutral",
                    supportMessage = "Thank you for writing today. Reflecting on your thoughts is a meaningful step."
                )
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSave.isEnabled = enabled
        btnAnalyze.isEnabled = enabled
        btnSave.text = if (enabled) "Save" else "Saving..."
        btnAnalyze.text = if (enabled) "Analyze" else "Analyzing..."
    }

    private fun showAnalysisDialog(
        mood: String,
        supportMessage: String,
        ayahArabic: String,
        ayahTranslation: String,
        reference: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("Journal Reflection")
            .setMessage(
                "Mood: $mood\n\n" +
                        "$supportMessage\n\n" +
                        "$ayahArabic\n\n" +
                        "$ayahTranslation\n\n" +
                        "Reference: $reference"
            )
            .setPositiveButton("OK") { dialog, _ ->
                etJournal.text.clear()
                dialog.dismiss()
            }
            .show()
    }
}
