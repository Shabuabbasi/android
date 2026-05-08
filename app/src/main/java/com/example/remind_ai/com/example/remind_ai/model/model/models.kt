package com.example.remind_ai.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "",
    val createdAt: Long = 0L
)

data class Patient(
    val patientId: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val stage: String = "",
    val profileImageUrl: String = "",
    val connectionCode: String = "",
    val createdAt: Long = 0L
)

data class Caregiver(
    val caregiverId: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val createdAt: Long = 0L
)

data class Assessment(
    val mriReportUrl: String = "",
    val measures: Map<String, String> = emptyMap(),
    val detectedStage: String = "",
    val updatedAt: Long = 0L
)

data class AssignedPatient(
    val patientId: String = "",
    val patientName: String = "",
    val stage: String = "",
    val linkedAt: Long = 0L
)

data class ReminderModel(
    val id: String = "",
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val repeat: String = "",
    val notes: String = "",
    val timestamp: Long = 0L
)

data class ChecklistItem(
    val id: String = "",
    val title: String = "",
    val checked: Boolean = false
)

data class JournalEntry(
    val id: String = "",
    val text: String = "",
    val mood: String = "",
    val supportMessage: String = "",
    val ayahArabic: String = "",
    val ayahTranslation: String = "",
    val createdAt: Long = 0L,
    val formattedDate: String = ""
)

data class QuranAyahResult(
    val reference: String = "",
    val arabic: String = "",
    val translation: String = ""
)

data class JournalAnalysis(
    val mood: String = "",
    val supportMessage: String = ""
)

data class ChatMessage(
    val text: String = "",
    val isUser: Boolean = false
)

data class ChatConversationRequest(
    val title: String = ""
)

data class ChatConversationResponse(
    val id: Int = 0,
    val title: String? = null
)

data class ChatSendMessageRequest(
    val content: String = ""
)

data class ChatApiMessage(
    val id: Int? = null,
    val role: String? = null,
    val content: String? = null
)

data class ChatSendMessageResponse(
    val userMessage: ChatApiMessage? = null,
    val assistantMessage: ChatApiMessage? = null,
    val reply: String? = null,
    val error: String? = null
)
