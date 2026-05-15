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
    OPEN_CHATBOT,
    OPEN_JOURNAL,
    OPEN_QUICK_THOUGHTS,
    OPEN_REMINDERS,
    SAVE_QUICK_THOUGHT,
    CHECKLIST_STATUS,
    VIEW_SCHEDULE,
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

        var processedCommand = lowerCommand
        
        // Strip common greetings/wake words if they are part of the recognized speech
        val greetings = listOf("hey assistant", "hi assistant", "assistant", "hey remind ai", "remind ai")
        for (greeting in greetings) {
            if (processedCommand.startsWith(greeting)) {
                processedCommand = processedCommand.substring(greeting.length).trim()
                break
            }
        }

        return when {
            // Create reminder intents (Regex for "set [a/the] reminder", "remind me", etc.)
            processedCommand.contains(Regex("(set|add|create|make) (a |the )?reminder")) ||
            processedCommand.contains("remind me") -> {
                parseCreateReminder(processedCommand)
            }

            // Get reminder intents
            processedCommand.contains(Regex("(what is|get|show|check) (my )?(upcoming |next )?reminder")) ||
            processedCommand.contains("my reminders") -> {
                ParsedCommand(intent = CommandIntent.GET_REMINDER)
            }

            // Open activities
            processedCommand.contains(Regex("open (the )?chatbot")) || processedCommand.contains("personal chatbot") -> {
                ParsedCommand(intent = CommandIntent.OPEN_CHATBOT)
            }

            processedCommand.contains(Regex("open (my |the )?journal")) -> {
                ParsedCommand(intent = CommandIntent.OPEN_JOURNAL)
            }

            processedCommand.contains(Regex("open (my |the )?quick thought")) || processedCommand.contains("thought pad") -> {
                ParsedCommand(intent = CommandIntent.OPEN_QUICK_THOUGHTS)
            }

            processedCommand.contains(Regex("open (my |the )?reminders")) -> {
                ParsedCommand(intent = CommandIntent.OPEN_REMINDERS)
            }

            // Action intents
            processedCommand.contains(Regex("(add|save|write) (a |the )?(quick )?thought")) -> {
                val title = extractQuickThoughtText(processedCommand)
                ParsedCommand(intent = CommandIntent.SAVE_QUICK_THOUGHT, title = title)
            }

            processedCommand.contains("checklist") && (processedCommand.contains("status") || processedCommand.contains("check") || processedCommand.contains("unchecked")) -> {
                ParsedCommand(intent = CommandIntent.CHECKLIST_STATUS)
            }

            processedCommand.contains("schedule") -> {
                ParsedCommand(intent = CommandIntent.VIEW_SCHEDULE)
            }

            else -> {
                ParsedCommand(intent = CommandIntent.UNKNOWN)
            }
        }
    }

    private fun extractQuickThoughtText(command: String): String {
        return when {
            command.contains("add quick thought") ->
                command.substringAfter("add quick thought").trim().ifEmpty { "" }
            command.contains("save thought") ->
                command.substringAfter("save thought").trim().ifEmpty { "" }
            else -> ""
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

        // 1. Check for relative time patterns first (e.g., "in 10 minutes", "in 1 hour")
        val relativePattern = Pattern.compile(
            "in\\s+(?:a|an|(\\d+)|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(minute|hour)s?",
            Pattern.CASE_INSENSITIVE
        )
        val relativeMatcher = relativePattern.matcher(command)

        if (relativeMatcher.find()) {
            val amountStr = relativeMatcher.group(1) ?: relativeMatcher.group(0)
            val unit = relativeMatcher.group(2) ?: "minute"
            
            val amount = when {
                amountStr.contains("one") || amountStr.contains("a") || amountStr.contains("an") -> 1
                amountStr.contains("two") -> 2
                amountStr.contains("three") -> 3
                amountStr.contains("four") -> 4
                amountStr.contains("five") -> 5
                amountStr.contains("six") -> 6
                amountStr.contains("seven") -> 7
                amountStr.contains("eight") -> 8
                amountStr.contains("nine") -> 9
                amountStr.contains("ten") -> 10
                else -> amountStr.filter { it.isDigit() }.toIntOrNull() ?: 1
            }

            val cal = Calendar.getInstance()
            if (unit.contains("hour")) {
                cal.add(Calendar.HOUR_OF_DAY, amount)
            } else {
                cal.add(Calendar.MINUTE, amount)
            }

            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            time = timeFormat.format(cal.time)
            date = dateFormat.format(cal.time)
            
            Log.i(TAG, "Relative time detected: in $amount $unit -> $time on $date")
        } else {
            // 2. Fallback to absolute time pattern: handles "5", "5:30", "5 30", "5pm", "5:30pm", "5 30 pm"
            val timePattern = Pattern.compile(
                "\\b(?:at\\s+)?(\\d{1,2})(?:[:\\s](\\d{2}))?\\s*(am|pm|AM|PM)?\\b",
                Pattern.CASE_INSENSITIVE
            )
            val timeMatcher = timePattern.matcher(command)

            if (timeMatcher.find()) {
                val hour = timeMatcher.group(1) ?: "0"
                val minute = timeMatcher.group(2) ?: "00"
                val period = timeMatcher.group(3) ?: ""

                time = formatTime(hour, minute, period)
            } else {
                // Default time if not found
                time = "09:00 AM"
            }
            
            // Extract date (tomorrow, next Monday, specific date) for absolute time commands
            date = extractDate(command)
        }

        // Extract title (text after time or after "for"/"to" if not followed by time)
        var titleMatch = ""
        
        // Strategy: Remove the command keywords and time, whatever is left is the title
        var cleaned = command
            .replace(Regex("(set|add|create|remind) reminder (at|to|on)?"), "")
            .replace(Regex("remind me (at|to|on)?"), "")
            .replace(Regex("at \\d{1,2}.*?(am|pm)?"), "")
            .replace(Regex("on (today|tomorrow|next week|next monday|\\d{1,2}/\\d{1,2}/\\d{4})"), "")
            .trim()
            
        // Handle "for [TITLE]" or "to [TITLE]"
        if (cleaned.startsWith("for ")) {
            cleaned = cleaned.substring(4).trim()
        } else if (cleaned.startsWith("to ")) {
            // Only strip "to" if it's not part of the time we missed in the regex
            if (!cleaned.matches(Regex("to \\d{1,2}.*"))) {
                cleaned = cleaned.substring(3).trim()
            }
        }
        
        titleMatch = cleaned
        title = if (titleMatch.isNotEmpty() && titleMatch.length > 2) {
            titleMatch.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            "Reminder"
        }

        // Date already extracted if relative time was used, otherwise extract now
        if (date.isEmpty()) {
            date = extractDate(command)
        }

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
            } else {
                // Heuristic for missing AM/PM (very helpful for dementia patients)
                // If someone says "at 5", they usually mean 5 PM. 
                // We'll assume 1-7 are PM, 8-11 are AM unless it's already past that time.
                if (hourInt in 1..7) {
                    finalHour = hourInt + 12
                } else if (hourInt == 12) {
                    finalHour = 12 // Noon
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
