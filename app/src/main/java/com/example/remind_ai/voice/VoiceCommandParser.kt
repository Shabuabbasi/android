package com.example.remind_ai.voice

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parses voice commands and extracts intent and parameters
 * Handles commands like:
 * - "Set reminder at 5 PM for meeting"
 * - "What is my upcoming reminder"
 */
data class ParsedCommand(
    val intent: CommandIntent,
    val title: String = "",
    val time: String = "",
    val date: String = "",
    val notes: String = ""
)

enum class CommandIntent {
    CREATE_REMINDER,
    GET_REMINDER,
    UNKNOWN
}

class VoiceCommandParser {

    companion object {
        private const val TAG = "VoiceCommandParser"
    }

    /**
     * Parse voice command and extract intent
     * @param command The voice command string
     * @return ParsedCommand with extracted intent and parameters
     */
    fun parseCommand(command: String): ParsedCommand {
        val lowerCommand = command.lowercase(Locale.getDefault()).trim()

        return when {
            // Create reminder intents
            lowerCommand.contains("set reminder") || 
            lowerCommand.contains("create reminder") ||
            lowerCommand.contains("add reminder") ||
            lowerCommand.contains("remind me") -> {
                parseCreateReminder(lowerCommand)
            }

            // Get reminder intents
            lowerCommand.contains("what is") && lowerCommand.contains("reminder") ||
            lowerCommand.contains("get reminder") ||
            lowerCommand.contains("upcoming reminder") ||
            lowerCommand.contains("next reminder") ||
            lowerCommand.contains("show reminder") -> {
                ParsedCommand(intent = CommandIntent.GET_REMINDER)
            }

            else -> {
                ParsedCommand(intent = CommandIntent.UNKNOWN)
            }
        }
    }

    /**
     * Parse create reminder command
     * Examples:
     * - "Set reminder at 5 PM for meeting"
     * - "Remind me at 3 PM to call mom"
     * - "Create reminder tomorrow at 9 AM for gym"
     */
    private fun parseCreateReminder(command: String): ParsedCommand {
        var title = ""
        var time = ""
        var date = ""

        // Extract time (e.g., "5 PM", "17:30", "3:45 AM")
        val timePattern = Pattern.compile(
            "(\\d{1,2})\\s*(?::|\\.)?(\\d{2})?\\s*(am|pm|AM|PM)?",
            Pattern.CASE_INSENSITIVE
        )
        val timeMatcher = timePattern.matcher(command)

        if (timeMatcher.find()) {
            val hour = timeMatcher.group(1) ?: "0"
            val minute = timeMatcher.group(2) ?: "00"
            val period = timeMatcher.group(3) ?: ""

            time = formatTime(hour, minute, period)
        }

        // Extract title (text before "for" or after "reminder")
        val titleMatch = when {
            command.contains("for ") -> {
                val forIndex = command.indexOf("for ")
                command.substring(forIndex + 4).trim()
            }
            command.contains("to ") -> {
                val toIndex = command.indexOf("to ")
                command.substring(toIndex + 3).trim()
            }
            else -> {
                // Extract from the entire command, removing time and prepositions
                command
                    .replace(Regex("(set|add|create|remind) reminder"), "")
                    .replace(Regex("at \\d{1,2}.*?(am|pm)?"), "")
                    .replace(Regex("for "), "")
                    .replace(Regex("to "), "")
                    .trim()
            }
        }

        title = if (titleMatch.isNotEmpty()) titleMatch else "Reminder"

        // Extract date (tomorrow, next Monday, specific date)
        date = extractDate(command)

        Log.d(TAG, "Parsed reminder - Title: $title, Time: $time, Date: $date")

        return ParsedCommand(
            intent = CommandIntent.CREATE_REMINDER,
            title = title,
            time = time,
            date = date
        )
    }

    /**
     * Extract date from command
     * Handles: "tomorrow", "next Monday", "next week", etc.
     */
    private fun extractDate(command: String): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        return when {
            command.contains("tomorrow") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                dateFormat.format(calendar.time)
            }
            command.contains("today") -> {
                dateFormat.format(calendar.time)
            }
            command.contains("next week") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 7)
                dateFormat.format(calendar.time)
            }
            command.contains("next monday") || command.contains("monday") -> {
                val daysUntilMonday = (Calendar.MONDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (daysUntilMonday == 0) calendar.add(Calendar.DAY_OF_YEAR, 7)
                else calendar.add(Calendar.DAY_OF_YEAR, daysUntilMonday)
                dateFormat.format(calendar.time)
            }
            else -> {
                // Default to today if no date specified
                dateFormat.format(calendar.time)
            }
        }
    }

    /**
     * Format time from extracted components
     * @param hour Hour (1-12 or 0-23)
     * @param minute Minute (00-59)
     * @param period AM/PM
     * @return Formatted time string "hh:mm a"
     */
    private fun formatTime(hour: String, minute: String, period: String): String {
        return try {
            val hourInt = hour.toInt()
            val minuteInt = minute.toInt()

            val calendar = Calendar.getInstance()
            var finalHour = hourInt

            // Handle AM/PM
            if (period.isNotEmpty()) {
                val isPM = period.lowercase().contains("pm")
                finalHour = when {
                    isPM && hourInt != 12 -> hourInt + 12
                    !isPM && hourInt == 12 -> 0
                    else -> hourInt
                }
            }

            calendar.set(Calendar.HOUR_OF_DAY, finalHour)
            calendar.set(Calendar.MINUTE, minuteInt)
            calendar.set(Calendar.SECOND, 0)

            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            timeFormat.format(calendar.time)
        } catch (e: Exception) {
            Log.e(TAG, "Error formatting time", e)
            ""
        }
    }
}
