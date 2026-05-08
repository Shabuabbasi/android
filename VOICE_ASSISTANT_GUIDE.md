# Voice Assistant System Implementation Guide

## Overview

A complete voice assistant system has been implemented in your ReMind_Ai Android app with:

- **Wake Word Detection** (Optional - Picovoice Porcupine)
- **Speech-to-Text** (Android SpeechRecognizer)
- **Text-to-Speech** (Android TextToSpeech)
- **Intelligent Voice Command Parsing**
- **Firebase-integrated Reminder System**
- **Proper Lifecycle Management**

## Components Created

### 1. **VoiceCommandParser.kt** (`voice/VoiceCommandParser.kt`)
Intelligent voice command parser that extracts intents and parameters from voice input.

**Features:**
- Parses reminder creation commands: "Set reminder at 5 PM for meeting"
- Parses reminder queries: "What is my upcoming reminder"
- Extracts time in various formats (5 PM, 17:30, 3:45 AM)
- Extracts dates (tomorrow, today, next Monday, next week)
- Intelligently extracts reminder title and notes

**Usage Example:**
```kotlin
val parser = VoiceCommandParser()
val parsedCommand = parser.parseCommand("Set reminder at 5 PM for meeting")
// Result: ParsedCommand(intent=CREATE_REMINDER, title="meeting", time="05:00 PM", date="15/05/2024")
```

### 2. **ReminderVoiceManager.kt** (`voice/ReminderVoiceManager.kt`)
Manages all reminder-related voice operations with Firebase integration.

**Features:**
- Creates reminders from parsed voice commands
- Validates reminder data (time must be in future)
- Saves reminders to Firebase Realtime Database
- Sets system alarms for reminder notifications
- Fetches upcoming reminders from Firebase
- Formats reminder data for voice output

**Key Methods:**
- `createReminderFromVoice()` - Creates and saves reminder
- `getUpcomingReminder()` - Fetches next scheduled reminder
- `setReminderAlarm()` - Sets notification alarm

### 3. **PorcupineWakeWordDetector.kt** (`voice/PorcupineWakeWordDetector.kt`)
(Optional) Wake word detection using Picovoice Porcupine.

**Features:**
- Continuous listening for wake word ("Hey Assistant")
- Background audio processing thread
- Proper resource cleanup
- Error handling and callbacks

**Note:** Requires Picovoice access key (see Setup section below)

### 4. **VoiceAssistantActivity.kt** (Updated)
Enhanced main activity integrating all voice components.

**New Features:**
- Intelligent command processing using VoiceCommandParser
- Firebase-integrated reminder creation
- Upcoming reminder queries
- Comprehensive error handling
- Proper lifecycle management (onPause, onResume, onDestroy)
- DetailedLogging for debugging
- UI status updates

**Voice Command Examples:**
```
"Set reminder at 5 PM for meeting"
"Remind me at 3 PM to call mom"
"Create reminder tomorrow at 9 AM for gym"
"What is my upcoming reminder"
"Get reminder"
"Show next reminder"
```

## Setup Instructions

### Step 1: Add Dependencies (✅ COMPLETED)

The following dependencies have been added to `build.gradle.kts`:

```gradle
// Picovoice Porcupine for wake word detection
implementation("ai.picovoice:porcupine-android:3.0.2")

// Lifecycle for proper activity lifecycle management
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
implementation("androidx.lifecycle:lifecycle-common-java8:2.6.2")
```

Sync your Gradle project to download dependencies.

### Step 2: Android Permissions (✅ COMPLETED)

The following permissions have been added to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

The app requests `RECORD_AUDIO` permission at runtime using the permission launcher.

### Step 3: Picovoice Setup (OPTIONAL - FOR WAKE WORD DETECTION)

To use wake word detection:

1. **Get Access Key:**
   - Go to https://console.picovoice.ai
   - Sign up for a free account
   - Create a new access key
   - Create a custom wake word model for "Hey Assistant"

2. **Add Access Key to Your App:**

   **Option A: BuildConfig (Recommended for development)**
   ```gradle
   // In build.gradle.kts under android {}
   buildTypes {
       debug {
           buildConfigField "String", "PICOVOICE_KEY", "\"YOUR_ACCESS_KEY_HERE\""
       }
       release {
           buildConfigField "String", "PICOVOICE_KEY", "\"YOUR_PRODUCTION_KEY_HERE\""
       }
   }
   ```

   **Option B: Update VoiceAssistantActivity.kt**
   ```kotlin
   companion object {
       private const val PICOVOICE_ACCESS_KEY = "YOUR_PICOVOICE_ACCESS_KEY_HERE"
   }
   ```

3. **Add Custom Wake Word Model:**
   - Download the `hey_assistant.ppn` file from Picovoice console
   - Place it in `app/src/main/assets/models/`
   - Update PorcupineWakeWordDetector.kt:
   ```kotlin
   .setKeywordPaths(arrayOf(context.assets.openFd("models/hey_assistant.ppn").fileDescriptor))
   ```

### Step 4: Enable PorcupineWakeWordDetector (Optional)

In VoiceAssistantActivity, you can add wake word detection:

```kotlin
// Initialize in onCreate() after TextToSpeech
private var wakeWordDetector: PorcupineWakeWordDetector? = null

private fun initializeWakeWordDetector() {
    wakeWordDetector = PorcupineWakeWordDetector(
        context = this,
        accessKey = PICOVOICE_ACCESS_KEY,
        onWakeWordDetected = {
            Log.d(TAG, "Wake word detected!")
            speak("Yes, how can I help you?")
            startListeningForCommand()
        },
        onError = { error ->
            Log.e(TAG, "Wake word error: $error")
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    )
    wakeWordDetector?.initialize()
}

// In onResume()
override fun onResume() {
    super.onResume()
    wakeWordDetector?.startListening()
}

// In onPause()
override fun onPause() {
    super.onPause()
    wakeWordDetector?.stopListening()
}

// In onDestroy()
override fun onDestroy() {
    super.onDestroy()
    wakeWordDetector?.cleanup()
    // ... existing cleanup code
}
```

## Voice Command Usage

### Creating Reminders by Voice

**Supported patterns:**
```
"Set reminder at [TIME] for [TITLE]"
"Set reminder at [TIME] on [DATE] for [TITLE]"
"Remind me at [TIME] to [TITLE]"
"Add reminder tomorrow at [TIME] for [TITLE]"
```

**Examples:**
- "Set reminder at 5 PM for meeting"
- "Set reminder at 3:30 PM on 20/05/2024 for doctor appointment"
- "Remind me at 9 AM tomorrow to call mom"
- "Add reminder next Monday at 2 PM for gym"

**Time Formats Supported:**
- "5 PM", "5pm", "5 p.m."
- "17:00", "17.00", "5:30 PM"
- "3:45 AM"

**Date References Supported:**
- "today"
- "tomorrow"
- "next Monday", "next week"
- Specific dates: "20/05/2024" (dd/MM/yyyy)

### Querying Reminders by Voice

**Supported patterns:**
```
"What is my upcoming reminder"
"What is my next reminder"
"Get reminder"
"Show reminder"
"My next reminder"
```

**Response Example:**
- User: "What is my upcoming reminder"
- Assistant: "Your next reminder is Meeting at 5:00 PM on 15/05/2024"

## Technical Architecture

### Flow Diagram

```
User Voice Input
    ↓
Speech Recognizer (Android)
    ↓
Voice Command Parser
    ├─→ CREATE_REMINDER Intent
    │   ↓
    │   Extract: title, time, date
    │   ↓
    │   ReminderVoiceManager.createReminderFromVoice()
    │   ↓
    │   Firebase Database (Save)
    │   ↓
    │   Set Alarm
    │   ↓
    │   Speak: "Reminder added"
    │
    └─→ GET_REMINDER Intent
        ↓
        ReminderVoiceManager.getUpcomingReminder()
        ↓
        Firebase Database (Query)
        ↓
        Format reminder data
        ↓
        Speak: "Your next reminder is..."
```

### Lifecycle Management

**Activity Lifecycle:**
- `onCreate()` - Initialize UI, managers, speech recognizer, TTS
- `onResume()` - Resume TTS if needed
- `onPause()` - Stop speech recognition
- `onDestroy()` - Cleanup speech recognizer, TTS, release resources

**Resource Management:**
- SpeechRecognizer: Properly stopped in onPause() and destroyed in onDestroy()
- TextToSpeech: Stopped and shutdown in onDestroy()
- Firebase listeners: Automatically cleaned up after single value events

## Firebase Integration

### Reminder Data Structure

Reminders are stored in Firebase Realtime Database with the following structure:

```
/reminders
  /{reminderId}
    - id: string (unique)
    - title: string (reminder title)
    - date: string (dd/MM/yyyy format)
    - time: string (hh:mm a format)
    - repeat: string (No Repeat, Daily, Weekly, Monthly)
    - notes: string (optional notes)
    - timestamp: long (milliseconds, used for sorting)
```

### Example Firebase Request

```kotlin
database.child("reminders").child(reminderId).setValue(reminder)
    .addOnSuccessListener { }
    .addOnFailureListener { exception -> }
```

## Error Handling

The system handles various error scenarios:

1. **Empty Commands:** Shows toast and prompts for retry
2. **Invalid Time Format:** Shows error message to user
3. **Past Date/Time:** Validates and rejects past reminder times
4. **Firebase Errors:** Gracefully handles database connection failures
5. **Speech Recognition Errors:** Specific error messages for different failure types
6. **Permission Denials:** Shows permission required toast

## Logging

All components include comprehensive logging with the `Log` class.

**Log Tags:**
- `VoiceAssistantActivity` - Main activity logs
- `VoiceCommandParser` - Command parsing logs
- `ReminderVoiceManager` - Reminder operation logs
- `PorcupineDetector` - Wake word detection logs

**Debug Logcat Filter:**
```
adb logcat | grep -E "(VoiceAssistantActivity|VoiceCommandParser|ReminderVoiceManager|PorcupineDetector)"
```

## Testing Checklist

- [ ] Microphone permission is granted
- [ ] TextToSpeech initializes without errors
- [ ] Voice command is recognized correctly
- [ ] Command parsing extracts correct intent
- [ ] Reminder creation saves to Firebase
- [ ] Reminder time validation rejects past times
- [ ] Reminder queries fetch correct data
- [ ] Voice response is spoken correctly
- [ ] No memory leaks on activity destroy
- [ ] App handles speech recognition errors gracefully
- [ ] Repeated voice commands work without freezing

## Troubleshooting

### Issue: "Could not understand. Try again."
**Solution:** Check microphone permission, speak clearly, ensure device has speech recognition service.

### Issue: Speech recognition stops working
**Solution:** Check that `isListeningForCommand` flag is being reset properly, ensure no exceptions in try-catch blocks.

### Issue: Reminder not saving to Firebase
**Solution:** Check Firebase authentication, verify database rules allow read/write, check network connectivity.

### Issue: TextToSpeech not speaking
**Solution:** Verify `isTtsReady` flag is true, check device volume, ensure TextToSpeech engine is installed.

### Issue: Picovoice initialization fails
**Solution:** Verify access key is correct, check that permissions are granted, ensure model file exists in assets.

## Future Enhancements

1. **Advanced NLP:** Integrate better NLP for more complex commands
2. **Custom Wake Word:** Set custom wake words per user
3. **Voice Training:** Train voice recognition for specific user
4. **Reminder Categories:** Create reminders with categories (Work, Personal, Health)
5. **Reminder Confirmation:** Ask user to confirm before saving
6. **Command History:** Save and analyze voice commands
7. **Smart Scheduling:** AI-based time suggestions
8. **Multi-language Support:** Support multiple languages
9. **Offline Mode:** Work without internet for basic commands
10. **Voice Profiles:** Different responses for different users

## File Summary

### Created Files:
1. **VoiceCommandParser.kt** - Command parsing and intent extraction
2. **ReminderVoiceManager.kt** - Reminder CRUD operations
3. **PorcupineWakeWordDetector.kt** - Wake word detection (optional)

### Modified Files:
1. **VoiceAssistantActivity.kt** - Integrated new voice system
2. **build.gradle.kts** - Added dependencies
3. **AndroidManifest.xml** - Added permissions

## Support

For issues or questions:
1. Check the Troubleshooting section
2. Review logcat output with provided filters
3. Verify all setup steps are completed
4. Check that all permissions are granted

---

**Implementation Complete!** Your voice assistant system is ready to use.
