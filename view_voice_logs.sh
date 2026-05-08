#!/bin/bash
# Script to view Voice Assistant logs in real-time

echo "=========================================="
echo "Voice Assistant Logcat Viewer"
echo "=========================================="
echo ""
echo "Instructions:"
echo "1. This will show logs in real-time"
echo "2. Click the mic button on your device"
echo "3. Speak a voice command"
echo "4. Watch the logs below"
echo ""
echo "Press Ctrl+C to stop viewing logs"
echo "=========================================="
echo ""

# Clear previous logs
adb logcat -c

echo "✅ Logcat cleared. Ready for fresh logs..."
echo ""
echo "Showing logs (filtering for VoiceAssistantActivity, Speech, and errors)..."
echo ""

# Show logs with colors for different log levels
adb logcat -v threadtime | grep -E "(VoiceAssistantActivity|SpeechRecognizer|Speech|E:|W:|D:.*Voice)"

echo ""
echo "=========================================="
echo "Log capture stopped"
echo "=========================================="
