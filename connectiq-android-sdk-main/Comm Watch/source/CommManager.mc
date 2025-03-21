// File: CommManager.mc
// Manages communication with the phone

using Toybox.Communications;
using Toybox.System;
using Toybox.WatchUi;

/**
 * Manages communication between the watch and phone
 * Handles transmission, cooldown, and other communication-related functionality
 */
class CommManager {
    /**
     * Function to safely transmit data to the phone
     * Handles different states and sends appropriate commands
     * @param swap If true, sends a SWAP command instead of standard capture
     */
    static function safeTransmit(swap) {
        try {
            // Check for cooldown period
            if (isTransmissionBlocked()) {
                return;
            }
            
            AppState.isTransmitting = true;
            var listener = new CommListener();
            
            // Different transmit paths based on app state
            if (CountdownManager.isCountdownActive) {
                handleCancelTransmit(listener);
            } else if (RecordingManager.isRecordingActive) {
                handleStopRecordingTransmit(listener);
            } else {
                handleStandardTransmit(listener, swap);
            }
        } catch (ex) {
            System.println("Error in safeTransmit: " + ex.getErrorMessage());
            AppState.isTransmitting = false;
        }
    }

    /**
     * Check if transmission is currently blocked by cooldown or already in progress
     * @return true if transmission should be blocked, false if it can proceed
     */
    static function isTransmissionBlocked() {
        try {
            var currentTime = System.getTimer();
            if (AppState.isTransmitting || 
                (currentTime - AppState.lastTransmitTime < AppState.TRANSMIT_COOLDOWN)) {
                System.println("Transmit blocked - cooldown or already transmitting");
                return true;
            }
            return false;
        } catch (ex) {
            System.println("Error checking transmission block: " + ex.getErrorMessage());
            return true; // Block on error to be safe
        }
    }

    /**
     * Handle cancel transmission when countdown is active
     * @param listener The CommListener for this transmission
     */
    static function handleCancelTransmit(listener) {
        try {
            CountdownManager.isCountdownActive = false; // Ensure it's disabled immediately
            listener.wasCancelled = true;
            Communications.transmit("CANCEL", null, listener);
            System.println("Cancelling countdown");
        } catch (ex) {
            System.println("Error in handleCancelTransmit: " + ex.getErrorMessage());
            AppState.isTransmitting = false;
        }
    }

    /**
     * Handle stop recording transmission
     * @param listener The CommListener for this transmission
     */
    static function handleStopRecordingTransmit(listener) {
        try {
            RecordingManager.stopRecording();
            CountdownManager.isCountdownActive = false; // Explicitly disable countdown
            RecordingManager.wasRecordingActive = true; // Set the flag
            
            // Set a temporary message until we receive confirmation from the phone
            AppState.lastMessage = "Stopping...";
            AppState.page = 1; // Show the message screen
            AppState.showMessageTimeout = System.getTimer() + 5000; // Longer timeout in case of delay
            
            // Keep page as 1 until we receive "Recording stopped" message from phone
            Communications.transmit("STOP", null, listener);
            System.println("Stopping recording");
            WatchUi.requestUpdate();
        } catch (ex) {
            System.println("Error in handleStopRecordingTransmit: " + ex.getErrorMessage());
            AppState.isTransmitting = false;
        }
    }

    /**
     * Handle standard transmission (photo or mode switch)
     * @param listener The CommListener for this transmission
     * @param swap If true, sends a SWAP command to change capture mode
     */
    static function handleStandardTransmit(listener, swap) {
        try {
            // Reset wasRecordingActive flag to ensure clean state
            RecordingManager.wasRecordingActive = false;
            
            // Get selected time option safely
            var timeOption = "0";
            if (AppState has :timeOptions && AppState has :selectedIndex &&
                AppState.timeOptions != null && AppState.selectedIndex < AppState.timeOptions.size()) {
                timeOption = AppState.timeOptions[AppState.selectedIndex];
            }
            
            if (swap) {
                // Send "SWAP " + time option
                System.println("Swap capture method: " + timeOption);
                Communications.transmit("SWAP " + timeOption, null, listener);
            } else {
                // Original behavior for normal transmit
                System.println("Transmit Normal: " + timeOption);
                Communications.transmit(timeOption, null, listener);
            }
        } catch (ex) {
            System.println("Error in handleStandardTransmit: " + ex.getErrorMessage());
            AppState.isTransmitting = false;
        }
    }
}
