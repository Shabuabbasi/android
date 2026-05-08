package com.example.remind_ai.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.remind_ai.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class CaregiverHomeActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var btnProfile: ImageButton
    private lateinit var tvTotalPatients: TextView
    private lateinit var tvActiveAlerts: TextView
    private lateinit var tvStableNow: TextView
    private lateinit var emptyPatientsCard: CardView
    private lateinit var btnAddPatient: MaterialButton
    private lateinit var patientListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiverhome)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        btnProfile = findViewById(R.id.btnProfile)
        tvTotalPatients = findViewById(R.id.tvTotalPatients)
        tvActiveAlerts = findViewById(R.id.tvActiveAlerts)
        tvStableNow = findViewById(R.id.tvStableNow)
        emptyPatientsCard = findViewById(R.id.emptyPatientsCard)
        btnAddPatient = findViewById(R.id.btnAddPatient)
        patientListContainer = findViewById(R.id.patientListContainer)

        btnAddPatient.setOnClickListener {
            startActivity(Intent(this, CaregiverConnectActivity::class.java))
        }

        btnProfile.setOnClickListener {
            Toast.makeText(this, "Profile screen can be added next", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        loadAssignedPatients()
    }

    private fun loadAssignedPatients() {
        val caregiverUid = auth.currentUser?.uid

        if (caregiverUid == null) {
            Toast.makeText(this, "Caregiver not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val assignedPatientsRef = database.reference
            .child("caregivers")
            .child(caregiverUid)
            .child("assigned_patients")

        assignedPatientsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                patientListContainer.removeAllViews()

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    tvTotalPatients.text = "0"
                    tvActiveAlerts.text = "0"
                    tvStableNow.text = "0"
                    emptyPatientsCard.visibility = View.VISIBLE
                    return
                }

                emptyPatientsCard.visibility = View.GONE

                val totalPatients = snapshot.childrenCount.toInt()
                var activeAlertsCount = 0
                var stableCount = 0

                for (patientSnapshot in snapshot.children) {
                    val patientId = patientSnapshot.child("patientId").getValue(String::class.java) ?: continue
                    val patientName = patientSnapshot.child("patientName").getValue(String::class.java) ?: "Unknown Patient"
                    val stage = patientSnapshot.child("stage").getValue(String::class.java) ?: "Stage not set"

                    val patientRef = database.reference.child("patients").child(patientId)

                    patientRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(patientData: DataSnapshot) {
                            val status = patientData.child("status").getValue(String::class.java) ?: "Stable"
                            val alertCount = patientData.child("alertCount").getValue(Int::class.java) ?: 0
                            val lastUpdated = patientData.child("lastUpdated").getValue(String::class.java) ?: "Just now"

                            if (alertCount > 0) activeAlertsCount++
                            if (status.equals("Stable", ignoreCase = true)) stableCount++

                            tvTotalPatients.text = totalPatients.toString()
                            tvActiveAlerts.text = activeAlertsCount.toString()
                            tvStableNow.text = stableCount.toString()

                            addPatientCard(
                                patientId = patientId,
                                patientName = patientName,
                                stage = stage,
                                status = status,
                                alertCount = alertCount,
                                lastUpdated = lastUpdated
                            )
                        }

                        override fun onCancelled(error: DatabaseError) {
                            addPatientCard(
                                patientId = patientId,
                                patientName = patientName,
                                stage = stage,
                                status = "Stable",
                                alertCount = 0,
                                lastUpdated = "Just now"
                            )
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@CaregiverHomeActivity,
                    "Failed to load patients: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun addPatientCard(
        patientId: String,
        patientName: String,
        stage: String,
        status: String,
        alertCount: Int,
        lastUpdated: String
    ) {
        val card = CardView(this).apply {
            radius = dpToPx(22f)
            cardElevation = dpToPx(3f)
            setCardBackgroundColor(Color.WHITE)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(dpToPx(20f).toInt(), dpToPx(14f).toInt(), dpToPx(20f).toInt(), 0)
            layoutParams = params
        }

        val root = RelativeLayout(this).apply {
            setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
        }

        if (alertCount > 0) {
            val alertBadge = TextView(this).apply {
                text = "ALERT"
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#DC5858"))
                id = View.generateViewId()
            }

            val badgeParams = RelativeLayout.LayoutParams(dpToPx(64f).toInt(), dpToPx(26f).toInt())
            badgeParams.addRule(RelativeLayout.ALIGN_PARENT_END)
            alertBadge.layoutParams = badgeParams
            root.addView(alertBadge)
        }

        val patientIcon = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.profile)
            setBackgroundResource(R.drawable.profile_circle_bg)
            setPadding(dpToPx(12f).toInt(), dpToPx(12f).toInt(), dpToPx(12f).toInt(), dpToPx(12f).toInt())
        }

        val iconParams = RelativeLayout.LayoutParams(dpToPx(60f).toInt(), dpToPx(60f).toInt())
        if (alertCount > 0) {
            iconParams.topMargin = dpToPx(16f).toInt()
        }
        patientIcon.layoutParams = iconParams
        root.addView(patientIcon)

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
        }

        val infoParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        infoParams.addRule(RelativeLayout.END_OF, patientIcon.id)
        infoParams.marginStart = dpToPx(14f).toInt()
        if (alertCount > 0) {
            infoParams.topMargin = dpToPx(16f).toInt()
        }
        infoLayout.layoutParams = infoParams

        val tvName = TextView(this).apply {
            text = patientName
            setTextColor(Color.parseColor("#2C3442"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val tvStage = TextView(this).apply {
            text = stage
            setTextColor(Color.parseColor("#6F54B5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }

        val tvStatus = TextView(this).apply {
            text = if (alertCount > 0) "$alertCount Active Alert" else status
            setTextColor(
                if (alertCount > 0) Color.parseColor("#DC5858")
                else Color.parseColor("#64AA78")
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val tvUpdated = TextView(this).apply {
            text = "Updated: $lastUpdated"
            setTextColor(Color.parseColor("#6E7E8C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }

        infoLayout.addView(tvName)
        infoLayout.addView(tvStage)
        infoLayout.addView(tvStatus)
        infoLayout.addView(tvUpdated)
        root.addView(infoLayout)

        val btnOpen = MaterialButton(this).apply {
            text = "Open"
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            cornerRadius = dpToPx(22f).toInt()
            setBackgroundColor(Color.parseColor("#6F54B5"))
        }

        val buttonParams = RelativeLayout.LayoutParams(dpToPx(100f).toInt(), dpToPx(46f).toInt())
        buttonParams.addRule(RelativeLayout.ALIGN_PARENT_END)
        buttonParams.addRule(RelativeLayout.CENTER_VERTICAL)
        btnOpen.layoutParams = buttonParams

        btnOpen.setOnClickListener {
            Toast.makeText(
                this,
                "Open dashboard for $patientName",
                Toast.LENGTH_SHORT
            ).show()

            // Replace this later with your real patient dashboard screen
            // Example:
            // val intent = Intent(this, Stage3CaregiverDashboardActivity::class.java)
            // intent.putExtra("patientId", patientId)
            // startActivity(intent)
        }

        root.addView(btnOpen)
        card.addView(root)
        patientListContainer.addView(card)
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }
}
