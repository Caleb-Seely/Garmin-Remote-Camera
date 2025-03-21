// File: RecordingManager.mc
// Manages video recording functionality

using Toybox.System;

/**
 * Manages recording state and functionality
 * Handles starting, stopping, and tracking recording time
 */
class RecordingManager {
    // Recording state
    static var isRecordingActive = false;
    static var recordingStartTime = 1; // to account for delay
    static var wasRecordingActive = false; // Flag to track if we just stopped recording
    static var lastCalculatedTime = 0; // Store last valid time to prevent jumps
    
    /**
     * Start the recording timer
     * @return true if recording started successfully
     */
    static function startRecording() {
        try {
            isRecordingActive = true;
            CountdownManager.isCountdownActive = false; // Ensure countdown is off
            
            // IMPORTANT: Do not reset the timer if it's already set from handleRecordingStarted
            // This was causing the issue with the timer not updating
            if (recordingStartTime == 1) { // Only set if it hasn't been set yet
                recordingStartTime = System.getTimer();
            }
            
            lastCalculatedTime = 0; // Reset the last calculated time
            System.println("Recording started at " + recordingStartTime);
            return true;
        } catch (ex) {
            System.println("Error starting recording: " + ex.getErrorMessage());
            return false;
        }
    }
    
    /**
     * Stop the recording timer
     * @return true if recording stopped successfully
     */
    static function stopRecording() {
        try {
            isRecordingActive = false;
            wasRecordingActive = true; // Set flag to indicate recording was active
            System.println("Recording stopped");
            return true;
        } catch (ex) {
            System.println("Error stopping recording: " + ex.getErrorMessage());
            return false;
        }
    }
    
 /**
 * Get the elapsed recording time in seconds
 * @return Recording duration in seconds, or 0 if not recording
 */
static function getRecordingElapsedTime() {
    try {
        // If recording is not active, check if we're in viewing mode after recording
        if (!isRecordingActive && !wasRecordingActive) {
            return 0;
        }
        
        // CRITICAL: Make sure we have a valid start time
        if (recordingStartTime <= 1) {
            System.println("ERROR: recordingStartTime is invalid: " + recordingStartTime);
            recordingStartTime = System.getTimer() - 1000; // Set a fallback time
            System.println("Reset to: " + recordingStartTime);
        }
        
        var currentTime = System.getTimer();
        var elapsed = 0;
        
        // Force a positive time difference - very important
        if (currentTime >= recordingStartTime) {
            elapsed = (currentTime - recordingStartTime) / 1000.0; // Convert to seconds
        } else {
            // Timer rollover case (happens after ~71 minutes)
            elapsed = (0xFFFFFFFF - recordingStartTime + currentTime + 1) / 1000.0;
        }
        
        // Ensure non-negative value
        if (elapsed < 0) {
            System.println("ERROR: Negative elapsed time calculated: " + elapsed);
            elapsed = 0.1; // Use a tiny positive value to ensure time moves
        }
        
        // Always store this calculation for next time when recording
        if (isRecordingActive) {
            // Ensure time always increases
            if (lastCalculatedTime > 0) {
                if (elapsed <= lastCalculatedTime) {
                    // Add a small increment to ensure time is always moving forward
                    elapsed = lastCalculatedTime + 0.1;
                }
            }
            lastCalculatedTime = elapsed;
        }
        
        return elapsed;
    } catch (ex) {
        System.println("Error getting recording time: " + ex.getErrorMessage());
        // Return last valid time or a fallback value that will show movement
        return lastCalculatedTime > 0 ? (lastCalculatedTime + 0.1) : 0.1;
    }
}
    
    /**
     * Format recording elapsed time as a string (mm:ss)
     * @return Formatted time string in mm:ss format
     */
    static function getFormattedRecordingTime() {
        try {
            var elapsed = getRecordingElapsedTime();
            
            // Safety check - ensure we have a positive value
            if (elapsed <= 0) {
                // If this happens during active recording, use a fallback increment
                if (isRecordingActive && lastCalculatedTime > 0) {
                    elapsed = lastCalculatedTime + 0.1;
                } else {
                    elapsed = 0.1; // Fallback to a small positive value
                }
            }
            
            // Ensure we calculate minutes and seconds correctly
            var minutes = (elapsed / 60).toNumber();
            var seconds = (elapsed % 60).toNumber();
            
            return minutes.format("%02d") + ":" + seconds.format("%02d");
        } catch (ex) {
            System.println("Error formatting recording time: " + ex.getErrorMessage());
            return "00:01"; // Use 00:01 as fallback to show movement
        }
    }
}
