// File: CommDelegate.mc

using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;

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
        if (AppState.wasRecordingActive) {
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
            AppState.isCountdownActive = false;
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
            AppState.wasRecordingActive = false;
            
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
            if (shouldCountdown && !AppState.isRecordingActive) {
                if (AppState has :startCountdown && AppState.startCountdown(timeString)) {
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
            AppState.isCountdownActive = false;
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
            AppState.isCountdownActive = false;
            AppState.wasRecordingActive = false;
            
            // Reset to main screen
            AppState.page = 0;
            WatchUi.requestUpdate();
        } catch (ex) {
            // Last resort error handling
            System.println("Critical error in safeResetToMainScreen: " + ex.getErrorMessage());
        }
    }
}

/**
 * Function to safely transmit data to the phone
 * Handles different states and sends appropriate commands
 * @param swap If true, sends a SWAP command instead of standard capture
 */
function safeTransmit(swap) {
    try {
        // Check for cooldown period
        if (isTransmissionBlocked()) {
            return;
        }
        
        AppState.isTransmitting = true;
        var listener = new CommListener();
        
        // Different transmit paths based on app state
        if (AppState.isCountdownActive) {
            handleCancelTransmit(listener);
        } else if (AppState.isRecordingActive) {
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
function isTransmissionBlocked() {
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
function handleCancelTransmit(listener) {
    try {
        AppState.isCountdownActive = false; // Ensure it's disabled immediately
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
function handleStopRecordingTransmit(listener) {
    try {
        AppState.stopRecording();
        AppState.isCountdownActive = false; // Explicitly disable countdown
        AppState.wasRecordingActive = true; // Set the flag
        
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
function handleStandardTransmit(listener, swap) {
    try {
        // Reset wasRecordingActive flag to ensure clean state
        AppState.wasRecordingActive = false;
        
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

/**
 * Main input delegate for the application
 * Handles button presses and other input events
 */
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    /**
     * Initialize the delegate
     */
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
        System.println("CommInputDelegate Init");
    }

    /**
     * Implements Previous Page behavior (typically UP button or SWIPE_DOWN)
     * @return true if handled, false otherwise
     */
    function onPreviousPage() {
        try {
            if (AppState.page == 0) {
                decrementSelectedIndex();
                WatchUi.requestUpdate();
                System.println("Previous Page (UP)");
                return true;
            }
        } catch (ex) {
            System.println("Error in onPreviousPage: " + ex.getErrorMessage());
        }
        return false;
    }
    
    /**
     * Helper function to safely decrement the selected index with bounds checking
     */
    function decrementSelectedIndex() {
        try {
            if (AppState has :selectedIndex && AppState has :timeOptions && 
                AppState.timeOptions != null) {
                var optionsSize = AppState.timeOptions.size();
                AppState.selectedIndex = (AppState.selectedIndex - 1 + optionsSize) % optionsSize;
            }
        } catch (ex) {
            System.println("Error decrementing selected index: " + ex.getErrorMessage());
        }
    }

    /**
     * Implements Next Page behavior (typically DOWN button or SWIPE_UP)
     * @return true if handled, false otherwise
     */
    function onNextPage() {
        try {
            if (AppState.page == 0) {
                incrementSelectedIndex();
                WatchUi.requestUpdate();
                System.println("Next Page (DOWN)");
                return true;
            }
        } catch (ex) {
            System.println("Error in onNextPage: " + ex.getErrorMessage());
        }
        return false;
    }
    
    /**
     * Helper function to safely increment the selected index with bounds checking
     */
    function incrementSelectedIndex() {
        try {
            if (AppState has :selectedIndex && AppState has :timeOptions && 
                AppState.timeOptions != null) {
                var optionsSize = AppState.timeOptions.size();
                AppState.selectedIndex = (AppState.selectedIndex + 1) % optionsSize;
            }
        } catch (ex) {
            System.println("Error incrementing selected index: " + ex.getErrorMessage());
        }
    }

    /**
     * Implements Select behavior (typically ENTER button or CLICK_TYPE_TAP center)
     * @return true if handled, false otherwise
     */
    function onSelect() {
        try {
            if (AppState.page == 0) {
                safeTransmit(false);
                System.println("Select (ENTER)");
                return true;
            } else if (AppState.page == 1) {
                // Dismiss message and return to main screen
                dismissMessageScreen();
                return true;
            } else if (AppState.page == 2 && AppState.isRecordingActive) {
                // We're in recording mode, stop recording
                safeTransmit(false); // Use safeTransmit to handle recording stop consistently
                System.println("Stop Recording");
                return true;
            }
        } catch (ex) {
            System.println("Error in onSelect: " + ex.getErrorMessage());
        }
        return false;
    }
    
    /**
     * Helper function to dismiss the message screen
     */
    function dismissMessageScreen() {
        try {
            AppState.showMessageTimeout = 0;
            AppState.page = 0;
            WatchUi.requestUpdate();
            System.println("Message dismissed");
        } catch (ex) {
            System.println("Error dismissing message: " + ex.getErrorMessage());
        }
    }

    /**
     * Handle key events not covered by behavioral methods
     * @param evt The key event
     * @return true if handled, false otherwise
     */
    function onKey(evt) {
        try {
            var key = evt.getKey();
            
            // Validate evt and key
            if (key == null) {
                return false;
            }
            
            // Handle key down events
            if (evt.getType() == WatchUi.PRESS_TYPE_DOWN) {
                // Allow key handling in any page state instead of checking page == 0
                // Specifically for UP button
                if (key == WatchUi.KEY_UP) {
                    AppState.upButtonPressTime = System.getTimer();
                    // Don't handle the event further until the button is released
                    return true;
                }
            } 
            // Handle key release events
            else if (evt.getType() == WatchUi.PRESS_TYPE_UP) {
                if (key == WatchUi.KEY_UP && AppState.upButtonPressTime > 0) {
                    var currentTime = System.getTimer();
                    var holdTime = currentTime - AppState.upButtonPressTime;
                    
                    // Reset the timestamp
                    AppState.upButtonPressTime = 0;
                    
                    // Check if this was a long hold
                    if (holdTime >= AppState.UP_BUTTON_HOLD_THRESHOLD) {
                        handleUpButtonLongPress();
                        return true;
                    }
                }
            }
        } catch (ex) {
            System.println("Error in onKey: " + ex.getErrorMessage());
        }
        return false;
    }
    
    /**
     * Handle UP button long press action
     */
    function handleUpButtonLongPress() {
        try {
            safeTransmit(true);
            System.println("UP long press - SWAP command");
        } catch (ex) {
            System.println("Error handling UP long press: " + ex.getErrorMessage());
        }
    }
    
    /**
     * Handle menu button press
     * @return true if handled, false otherwise
     */
    function onMenu() {
        try {
           // Call safeTransmit with "SWAP" parameter
           safeTransmit(true);
           System.println("OnMenu transmit");
           return true;
        } catch (ex) {
            System.println("Error in onMenu: " + ex.getErrorMessage());
        }
        return false;
    }
}