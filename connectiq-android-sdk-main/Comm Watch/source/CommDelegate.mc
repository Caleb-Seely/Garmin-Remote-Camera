// File: CommDelegate.mc

using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;

// Communication listener for transmit operations
class CommListener extends Communications.ConnectionListener {
    var wasCancelled = false;

    function initialize() {
        Communications.ConnectionListener.initialize();
        System.println("CommListener Init");
    }

    function onComplete() {
        AppState.isTransmitting = false;
        AppState.lastTransmitTime = System.getTimer();
        
        // Handle cancel case
        if (wasCancelled) {
            AppState.isCountdownActive = false;
            AppState.lastMessage = "Cancelled";
            AppState.page = 1;
            AppState.showMessageTimeout = System.getTimer() + 2000;
            WatchUi.requestUpdate();
            System.println("Cancel Complete");
            return;
        }
        
        // Check if we're coming from a recording state - don't start countdown
        if (AppState.isRecordingActive || AppState.wasRecordingActive) {
            AppState.isCountdownActive = false; // Ensure countdown is disabled
            AppState.wasRecordingActive = false; // Reset the flag
            AppState.page = 1;
            AppState.showMessageTimeout = System.getTimer() + 1000;
            WatchUi.requestUpdate();
            System.println("Recording Message Sent");
            return;
        }
        
        // Normal timer start case
        var timeString = AppState.timeOptions[AppState.selectedIndex];
        if (timeString != "0" && AppState.startCountdown(timeString)) {
            AppState.lastMessage = "Starting...";
        } else {
            AppState.lastMessage = "Sending...";      // No countdown
        }
        
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 1000;
        WatchUi.requestUpdate();
        System.println("Transmit Complete");
    }

    function onError() {
        AppState.isTransmitting = false;
        AppState.isCountdownActive = false;
        AppState.lastMessage = "Send Failed!";
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 3000;
        WatchUi.requestUpdate();
        System.println("Transmit Failed");
    }
}


// Function to safely transmit data
function safeTransmit(swap) {
    try {
        // Don't allow transmits during cooldown period
        var currentTime = System.getTimer();
        if (AppState.isTransmitting || 
            (currentTime - AppState.lastTransmitTime < AppState.TRANSMIT_COOLDOWN)) {
            System.println("Transmit blocked - cooldown or already transmitting");
            return;
        }
        
        AppState.isTransmitting = true;
        var listener = new CommListener();
        
        // If countdown is active, send cancel message
        if (AppState.isCountdownActive) {
            AppState.isCountdownActive = false; // Ensure it's disabled immediately
            listener.wasCancelled = true;
            Communications.transmit("CANCEL", null, listener);
            System.println("Cancelling countdown");
            return;
        }
        
        // If recording is active, stop recording and send stop message
        if (AppState.isRecordingActive) {
            AppState.stopRecording();
            AppState.isCountdownActive = false; // Explicitly disable countdown
            AppState.wasRecordingActive = true; // Set the flag
            AppState.page = 0; // Return to main screen
            Communications.transmit("STOP", null, listener);
            System.println("Stopping recording");
            WatchUi.requestUpdate();
            return;
        }

        if (swap) {
            // Send "SWAP " + time option
            System.println("Swap capture method: " + AppState.timeOptions[AppState.selectedIndex]);
            Communications.transmit("SWAP " + AppState.timeOptions[AppState.selectedIndex], null, listener);
        } else {
            // Original behavior for normal transmit
            System.println("Transmit Normal: " + AppState.timeOptions[AppState.selectedIndex]);
            Communications.transmit(AppState.timeOptions[AppState.selectedIndex], null, listener);
        }
    } catch (ex) {
        System.println("Error in safeTransmit: " + ex.getErrorMessage());
        AppState.isTransmitting = false;
    }
}


// Main input delegate for the application
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
        System.println("CommInputDelegate Init");
    }

    // Implements Previous Page behavior (typically UP button or SWIPE_DOWN)
    function onPreviousPage() {
        if (AppState.page == 0) {
            AppState.selectedIndex = (AppState.selectedIndex - 1 + AppState.timeOptions.size()) % AppState.timeOptions.size();
            WatchUi.requestUpdate();
            System.println("Previous Page (UP)");
            return true;
        }
        return false;
    }

    // Implements Next Page behavior (typically DOWN button or SWIPE_UP)
    function onNextPage(){
        if (AppState.page == 0) {
            AppState.selectedIndex = (AppState.selectedIndex + 1) % AppState.timeOptions.size();
            WatchUi.requestUpdate();
            System.println("Next Page (DOWN)");
            return true;
        }
        return false;
    }

    // Implements Select behavior (typically ENTER button or CLICK_TYPE_TAP center)
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
                AppState.stopRecording();
                AppState.page = 0;
                AppState.isCountdownActive = false;
                AppState.wasRecordingActive = true; // Set the flag
                Communications.transmit("STOP", null, new CommListener());
                System.println("Stop Recording");
                WatchUi.requestUpdate();
                return true;
            }
        } catch (ex) {
            System.println("Error in onSelect: " + ex.getErrorMessage());
        }
        return false;
    }

    // Handle key events not covered by behavioral methods
    function onKey(evt) {
        try {
            var key = evt.getKey();
            System.println("onKey: " + key);
            
            // For page 0 (main selection screen)
            if (AppState.page == 0) {
                if (key == WatchUi.KEY_UP) {
                    return onPreviousPage();
                } else if (key == WatchUi.KEY_DOWN) {
                    return onNextPage();
                } else if (key == WatchUi.KEY_ENTER) {
                    return onSelect();
                }
            } 
            // For page 1 (message screen)
            else if (AppState.page == 1) {
                if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_ESC) {
                    dismissMessageScreen();
                    return true;
                }
            }
            // For page 2 (recording screen)
            else if (AppState.page == 2 && AppState.isRecordingActive) {
                if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_ESC) {
                    AppState.stopRecording();
                    AppState.page = 0;
                    AppState.isCountdownActive = false;
                    AppState.wasRecordingActive = true; // Set the flag
                    Communications.transmit("STOP", null, new CommListener());
                    System.println("Stop Recording (Key)");
                    WatchUi.requestUpdate();
                    return true;
                }
            }
        } catch (ex) {
            System.println("Error in onKey: " + ex.getErrorMessage());
        }
        
        return false;
    }

        
    // Handle swipe events
    function onSwipe(swipeEvent) {
        try {
            var direction = swipeEvent.getDirection();
            
            if (AppState.page == 0) {
                if (direction == WatchUi.SWIPE_UP) {
                    return onNextPage();
                } else if (direction == WatchUi.SWIPE_DOWN) {
                    return onPreviousPage();
                }
            } else if (AppState.page == 1) {
                // Swipe dismisses message screen
                dismissMessageScreen();
                return true;
            } else if (AppState.page == 2 && AppState.isRecordingActive) {
                // Swipe stops recording
                AppState.stopRecording();
                AppState.page = 0;
                AppState.isCountdownActive = false;
                AppState.wasRecordingActive = true; // Set the flag
                Communications.transmit("STOP", null, new CommListener());
                System.println("Stop Recording (Swipe)");
                WatchUi.requestUpdate();
                return true;
            }
        } catch (ex) {
            System.println("Error in onSwipe: " + ex.getErrorMessage());
        }
        
        return false;
    }
        
    // Helper function to dismiss message screen
    function dismissMessageScreen() {
        System.println("Dismiss message screen");
        AppState.page = 0;
        AppState.showMessageTimeout = 0;
        WatchUi.requestUpdate();
    }
    
    // Holding the up key triggers this
    function onMenu() {
        try {
           // Call safeTransmit with "VIDEO" parameter
           safeTransmit(true);
           System.println("OnMenu transmit");
           return true;
        } catch (ex) {
            System.println("Error in onMenu: " + ex.getErrorMessage());
        }
        return false;
    }
}