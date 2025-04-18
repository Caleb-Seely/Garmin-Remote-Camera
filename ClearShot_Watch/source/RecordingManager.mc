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
    static var LAG_COMPENSATION_MS = 500; // seconds of lag compensation
    
    /**
     * Start the recording timer
     * @return true if recording started successfully
     */
    static function startRecording() {
        try {
            isRecordingActive = true;
            CountdownManager.isCountdownActive = false; // Ensure countdown is off
            
            // Set recording start time with lag compensation
            // This accounts for the 2-second lag by setting the start time earlier
            recordingStartTime = System.getTimer() - LAG_COMPENSATION_MS;
            
            lastCalculatedTime = 0; // Reset the last calculated time
            System.println("Recording started at " + recordingStartTime + " (with " + (LAG_COMPENSATION_MS/1000) + "s lag compensation)");
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
            
            var currentTime = System.getTimer();
            var elapsed = 0;
            
            // Calculate time difference in milliseconds
            var diffMillis = 0;
            if (currentTime >= recordingStartTime) {
                diffMillis = currentTime - recordingStartTime;
            } else {
                // Timer rollover case (happens after ~71 minutes)
                diffMillis = (0xFFFFFFFF - recordingStartTime + currentTime + 1);
            }
            
            // Convert milliseconds to seconds by integer division
            // System.getTimer() returns milliseconds, need to convert to true seconds
            elapsed = (diffMillis / 1000).toNumber();
            
            // For debugging
            System.println("Raw time: current=" + currentTime + " start=" + recordingStartTime + 
                          " diffMillis=" + diffMillis + " elapsed=" + elapsed + "s");
            
            return elapsed;
        } catch (ex) {
            System.println("Error getting recording time: " + ex.getErrorMessage());
            return lastCalculatedTime > 0 ? lastCalculatedTime : 0;
        }
    }
    
    /**
     * Format recording elapsed time as a string (mm:ss)
     * @return Formatted time string in mm:ss format
     */
    static function getFormattedRecordingTime() {
        try {
            var elapsed = getRecordingElapsedTime();
            
            // Safety check - ensure we have a valid value
            if (elapsed < 0) {
                elapsed = 0;
            }
            
            // Ensure we have integers for formatting
            var totalSeconds = elapsed.toNumber();
            var minutes = (totalSeconds / 60).toNumber();
            var seconds = (totalSeconds % 60).toNumber();
            
            return minutes.format("%02d") + ":" + seconds.format("%02d");
        } catch (ex) {
            System.println("Error formatting recording time: " + ex.getErrorMessage());
            return "00:00"; // Fallback on error
        }
    }
}