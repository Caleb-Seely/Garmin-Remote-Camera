// File: CountdownManager.mc
// Manages countdown timer functionality

using Toybox.System;

/**
 * Manages countdown timer functionality
 * Handles starting, stopping, and tracking countdown timers
 */
class CountdownManager {
    // Countdown timer state
    static var isCountdownActive = false;
    static var countdownEndTime = 0;
    static var countdownDuration = 0; // Duration in milliseconds
    static var countdownStartTime = 0;
    
    /**
     * Start a countdown timer based on the selected time string
     * @param timeString The time value as a string (e.g. "10")
     * @return true if countdown was started successfully, false otherwise
     */
    static function startCountdown(timeString) {
        try {
            // Ensure we're not in recording state
            if (RecordingManager.isRecordingActive) {
                System.println("Cannot start countdown while recording is active");
                return false;
            }
            
            // Reset all potentially conflicting flags and states
            RecordingManager.wasRecordingActive = false;
            AppState.lastMessage = ""; // Clear any pending message
            AppState.showMessageTimeout = 0; // Reset message timeout
            
            // Parse the time string (no units in timeOptions)
            var seconds = 0;
            if (timeString != null && timeString.length() > 0) {
                try {
                    seconds = timeString.toNumber() - 1; //-1 to better match the phone timer
                } catch (ex) {
                    System.println("Error parsing time: " + ex.getErrorMessage());
                    return false;
                }
            }
            
            if (seconds > 0) {
                // Set UI state explicitly for countdown
                isCountdownActive = true;
                countdownDuration = seconds * 1000; // Convert to milliseconds
                countdownStartTime = System.getTimer();
                countdownEndTime = countdownStartTime + countdownDuration;
                
                // Force page to 0 which will show countdown in onUpdate
                AppState.page = 0;
                
                System.println("Starting countdown for " + seconds + " seconds");
                System.println("Countdown will end at " + countdownEndTime);
                return true;
            }
            
            return false;
        } catch (ex) {
            System.println("Error in startCountdown: " + ex.getErrorMessage());
            return false;
        }
    }
    
    /**
     * Get remaining time in seconds for the countdown
     * @return Remaining time in seconds, or 0 if not in countdown mode
     */
    static function getRemainingTime() {
        try {
            if (!isCountdownActive) {
                return 0;
            }
            
            var currentTime = System.getTimer();
            var remaining = (countdownEndTime - currentTime) / 1000.0; // Convert to seconds
            
            if (remaining <= 0) {
                isCountdownActive = false;
                return 0;
            }
            
            return remaining;
        } catch (ex) {
            System.println("Error getting remaining time: " + ex.getErrorMessage());
            isCountdownActive = false; // Reset on error
            return 0;
        }
    }
    
    /**
     * Format remaining time as a string
     * @return String representing the remaining time in seconds
     */
    static function getFormattedRemainingTime() {
        try {
            var remaining = getRemainingTime();
            if (remaining <= 0) {
                return "0";
            }
            
            // Round to nearest second and return as integer
            return (remaining + 0.5).toNumber().toString();
        } catch (ex) {
            System.println("Error formatting remaining time: " + ex.getErrorMessage());
            return "0";
        }
    }
}
