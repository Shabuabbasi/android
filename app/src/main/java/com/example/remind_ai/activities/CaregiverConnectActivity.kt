package com.example.remind_ai.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.remind_ai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class CaregiverConnectActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var btnBack: ImageButton
    private lateinit var etPatientCode: TextInputEditText
    private lateinit var btnConnectPatient: MaterialButton
    private lateinit var btnSkipForNow: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiverconnect)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        btnBack = findViewById(R.id.btnBack)
        etPatientCode = findViewById(R.id.etPatientCode)
        btnConnectPatient = findViewById(R.id.btnConnectPatient)
        btnSkipForNow = findViewById(R.id.btnSkipForNow)

        btnBack.setOnClickListener {
            finish()
        }

        btnConnectPatient.setOnClickListener {
            connectPatientByCode()
        }

        btnSkipForNow.setOnClickListener {
            startActivity(Intent(this, CaregiverHomeActivity::class.java))
            finish()
        }
    }

    private fun connectPatientByCode() {
        val code = etPatientCode.text.toString().trim()
        val caregiverUid = auth.currentUser?.uid

        if (code.isEmpty()) {
            Toast.makeText(this, "Please enter patient code", Toast.LENGTH_SHORT).show()
            return
        }

        if (caregiverUid == null) {
            Toast.makeText(this, "Caregiver not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val patientsRef = database.reference.child("patients")

        patientsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var patientFound = false

                for (patientSnapshot in snapshot.children) {
                    val connectionCode =
                        patientSnapshot.child("connectionCode").getValue(String::class.java)

                    if (connectionCode == code) {
                        patientFound = true

                        val patientId = patientSnapshot.key ?: return
                        val patientName =
                            patientSnapshot.child("fullName").getValue(String::class.java) ?: ""
                        val patientStage =
                            patientSnapshot.child("stage").getValue(String::class.java) ?: ""

                        val assignedPatient = hashMapOf(
                            "patientId" to patientId,
                            "patientName" to patientName,
                            "stage" to patientStage,
                            "linkedAt" to System.currentTimeMillis()
                        )

                        database.reference
                            .child("caregivers")
                            .child(caregiverUid)
                            .child("assigned_patients")
                            .child(patientId)
                            .setValue(assignedPatient)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this@CaregiverConnectActivity,
                                    "Patient connected successfully",
                                    Toast.LENGTH_SHORT
                                ).show()

                                startActivity(
                                    Intent(
                                        this@CaregiverConnectActivity,
                                        CaregiverHomeActivity::class.java
                                    )
                                )
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    this@CaregiverConnectActivity,
                                    "Failed to connect patient: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        break
                    }
                }

                if (!patientFound) {
                    Toast.makeText(
                        this@CaregiverConnectActivity,
                        "Invalid patient code",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@CaregiverConnectActivity,
                    "Error finding patient: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
