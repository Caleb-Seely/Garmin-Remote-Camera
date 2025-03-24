// File: InputDelegate.mc
// Handles user input delegation

using Toybox.WatchUi;
using Toybox.System;

/**
 * Main input delegate for the application
 * Handles button presses and other input events
 */
class InputDelegate extends WatchUi.BehaviorDelegate {
    /**
     * Initialize the delegate
     */
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
        System.println("InputDelegate Init");
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
                CommManager.safeTransmit(false);
                System.println("Select (ENTER)");
                return true;
            } else if (AppState.page == 1) {
                // Dismiss message and return to main screen
                dismissMessageScreen();
                return true;
            } else if (AppState.page == 2 && RecordingManager.isRecordingActive) {
                // We're in recording mode, stop recording
                CommManager.safeTransmit(false); // Use safeTransmit to handle recording stop consistently
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
            CommManager.safeTransmit(true);
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
           CommManager.safeTransmit(true);
           System.println("OnMenu transmit");
           return true;
        } catch (ex) {
            System.println("Error in onMenu: " + ex.getErrorMessage());
        }
        return false;
    }
}
