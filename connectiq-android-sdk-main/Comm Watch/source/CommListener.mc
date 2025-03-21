// File: CommListener.mc
// Communication listener for transmit operations

using Toybox.Communications;
using Toybox.System;
using Toybox.WatchUi;

/**
 * Communication listener for transmit operations
 * Handles callback events from the Communications API
 */
class CommListener extends Communications.ConnectionListener {
    var wasCancelled = false;

    /**
     * Initialize the connection listener
     */
    function initialize() {
        Communications.ConnectionListener.initialize();
        System.println("CommListener Init");
    }

    /**
     * Callback when transmission is complete
     * Handles different scenarios based on current app state
     */
    function onComplete() {
        try {
            // Reset transmission flag
            AppState.isTransmitting = false;
            AppState.lastTransmitTime = System.getTimer();
            
            // Handle cancel case
            if (wasCancelled) {
                handleCancelComplete();
                return;
            }
            
            // For STOP commands (recording was active)
            if (RecordingManager.wasRecordingActive) {
                handleStopRecordingComplete();
                return;
            }
            
            // For regular commands (not cancel, not recording)
            handleStandardComplete();
            return;
        } catch (ex) {
            System.println("Error in onComplete: " + ex.getErrorMessage());
            // Recover to a safe state
            safeResetToMainScreen();
        }
    }
    
    /**
     * Handle the cancel operation complete case
     */
    function handleCancelComplete() as Void {
        try {
            CountdownManager.isCountdownActive = false;
            AppState.lastMessage = "Cancelled";
            AppState.page = 1;
            AppState.showMessageTimeout = System.getTimer() + 2000;
            WatchUi.requestUpdate();
            System.println("Cancel Complete");
        } catch (ex) {
            System.println("Error in handleCancelComplete: " + ex.getErrorMessage());
            safeResetToMainScreen();
        }
    }
    
    /**
     * Handle the stop recording operation complete case
     */
    function handleStopRecordingComplete() as Void {
        try {
            // Clear the flag immediately to prevent interference with other operations
            RecordingManager.wasRecordingActive = false;
            
            AppState.lastMessage = "Stopping...";
            AppState.page = 1;
            AppState.showMessageTimeout = System.getTimer() + 2000;
            WatchUi.requestUpdate();
            System.println("Recording Stop Message Sent");
        } catch (ex) {
            System.println("Error in handleStopRecordingComplete: " + ex.getErrorMessage());
            safeResetToMainScreen();
        }
    }
    
    /**
     * Handle the standard operation complete case
     * This includes normal photo capture with optional countdown
     */
    function handleStandardComplete() {
        try {
            var timeString = "0";
            // Safely get the time string with bounds checking
            if (AppState has :timeOptions && AppState has :selectedIndex && 
                AppState.timeOptions != null && AppState.selectedIndex < AppState.timeOptions.size()) {
                timeString = AppState.timeOptions[AppState.selectedIndex];
            }
            
            var shouldCountdown = false;
            // Parse time safely
            try {
                var timeValue = timeString.toNumber();
                shouldCountdown = timeValue > 0;
            } catch (ex) {
                System.println("Error parsing time value: " + ex.getErrorMessage());
                shouldCountdown = false;
            }
            
            // For non-zero timer options, start countdown
            if (shouldCountdown && !RecordingManager.isRecordingActive) {
                if (CountdownManager has :startCountdown && CountdownManager.startCountdown(timeString)) {
                    System.println("Starting countdown for " + timeString + " seconds");
                    // No need to set a message - the countdown UI will show
                    WatchUi.requestUpdate();
                    return;
                }
            }
            
            // Default case - just show a simple message
            AppState.lastMessage = "Sending...";
            AppState.page = 1;
            AppState.showMessageTimeout = System.getTimer() + 1000;
            WatchUi.requestUpdate();
            System.println("Transmit Complete - No Countdown");

            return;
        } catch (ex) {
            System.println("Error in handleStandardComplete: " + ex.getErrorMessage());
            safeResetToMainScreen();

            return;
        }
    }

    /**
     * Callback when transmission encounters an error
     */
    function onError() {
        try {
            AppState.isTransmitting = false;
            CountdownManager.isCountdownActive = false;
            AppState.lastMessage = "Send Failed!";
            AppState.page = 1;
            AppState.showMessageTimeout = System.getTimer() + 3000;
            WatchUi.requestUpdate();
            System.println("Transmit Failed");
        } catch (ex) {
            System.println("Error in onError handler: " + ex.getErrorMessage());
            safeResetToMainScreen();
        }
    }
    
    /**
     * Reset to main screen safely in case of errors
     */
    function safeResetToMainScreen() {
        try {
            // Clean up state
            AppState.isTransmitting = false;
            CountdownManager.isCountdownActive = false;
            RecordingManager.wasRecordingActive = false;
            
            // Reset to main screen
            AppState.page = 0;
            WatchUi.requestUpdate();
        } catch (ex) {
            // Last resort error handling
            System.println("Critical error in safeResetToMainScreen: " + ex.getErrorMessage());
        }
    }
}
