// File: CommDelegate.mc
//

using Toybox.WatchUi;
using Toybox.System;
using Toybox.Communications;

// Communication listener for transmit operations
class CommListener extends Communications.ConnectionListener {
    function initialize() {
        Communications.ConnectionListener.initialize();
    }

    function onComplete() {
        AppState.isTransmitting = false;
        AppState.lastTransmitTime = System.getTimer();
        AppState.lastMessage = "Sending ";
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 3000; // Show for 3 seconds
        WatchUi.requestUpdate();
        System.println("Transmit Complete");
    }

    function onError() {
        AppState.isTransmitting = false;
        AppState.lastMessage = "Send Failed!";
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 3000;
        WatchUi.requestUpdate();
        System.println("Transmit Failed");
    }
}

// Helper function to check if we can transmit
function canTransmit() {
    if (AppState.isTransmitting) {
        AppState.lastMessage = "Already sending...";
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 1500;
        WatchUi.requestUpdate();
        return false;
    }
    
    var currentTime = System.getTimer();
    if (currentTime - AppState.lastTransmitTime < AppState.TRANSMIT_COOLDOWN) {
        AppState.lastMessage = "Please wait...";
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 1500;
        WatchUi.requestUpdate();
        return false;
    }
    
    return true;
}

// Function to safely transmit data
function safeTransmit(isTest) {
    if (!canTransmit()) {
        return;
    }
    
    AppState.isTransmitting = true;
    var listener = new CommListener();
    if (isTest) {
        Communications.transmit("TEST", null, listener);
    } else {
        Communications.transmit(AppState.timeOptions[AppState.selectedIndex], null, listener);
    }
}

// Main input delegate for the application
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
    }

    function onMenu() {
        // Send time immediately rather than showing menu
        safeTransmit(false);
        return true;
    }

    function onTap(event) {
        var screenWidth = System.getDeviceSettings().screenWidth;
        var screenHeight = System.getDeviceSettings().screenHeight;
        var centerX = screenWidth / 2;
        var centerY = screenHeight / 2;
        var radius;
        
        if (screenWidth < screenHeight) {
            radius = screenWidth * 0.4;
        } else {
            radius = screenHeight * 0.4;
        }
        
        // Calculate camera icon position
        var iconX = centerX + (radius * 0.6);
        var iconY = centerY - (radius * 0.6);
        var iconRadius = 20; // Tap target size
        
        // Get tap coordinates
        var coords = event.getCoordinates();
        
        if(AppState.page == 0) {
            // Check if tap is on the camera icon area
            if (isInCircle(coords[0], coords[1], iconX, iconY, iconRadius)) {
                // Camera icon tapped - send the selected time
                safeTransmit(false);
            } else {
                // Otherwise, cycle through time options
                AppState.selectedIndex = (AppState.selectedIndex + 1) % AppState.timeOptions.size();
                WatchUi.requestUpdate();
            }
        } else {
            // Dismiss message and return to main screen
            AppState.page = 0;
            AppState.showMessageTimeout = 0;
            WatchUi.requestUpdate();
        }
        return true;
    }
    
    // Helper function to detect if a point is inside a circle
    function isInCircle(x, y, centerX, centerY, radius) {
        var dx = x - centerX;
        var dy = y - centerY;
        return (dx*dx + dy*dy) <= (radius*radius);
    }

    function onKey(evt) {
        var key = evt.getKey();
        
        if (AppState.page == 0) {
            if (key == WatchUi.KEY_UP) {
               AppState.selectedIndex = (AppState.selectedIndex - 1 + AppState.timeOptions.size()) % AppState.timeOptions.size();
               WatchUi.requestUpdate();
               return true;
            } else if (key == WatchUi.KEY_DOWN) {
                AppState.selectedIndex = (AppState.selectedIndex + 1) % AppState.timeOptions.size();
                WatchUi.requestUpdate();
                return true;
            } else if (key == WatchUi.KEY_ENTER) {
                safeTransmit(false);
                return true;
            }
        } else if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_ESC) {
            // Any key dismisses the message screen
            AppState.page = 0;
            AppState.showMessageTimeout = 0;
            WatchUi.requestUpdate();
            return true;
        }
        return false;
    }
}

// Menu delegate for listener registration
class ListenerMenuDelegate extends WatchUi.MenuInputDelegate {
    function initialize() {
        WatchUi.MenuInputDelegate.initialize();
    }

    function onMenuItem(item) {
        if(item == :phone) {
            if(Communications has :registerForPhoneAppMessages) {
                Communications.registerForPhoneAppMessages(AppState.phoneMethod);
            }
        } else if(item == :none) {
            Communications.registerForPhoneAppMessages(null);
        } else if(item == :phoneFail) {
            AppState.crashOnMessage = true;
            Communications.registerForPhoneAppMessages(AppState.phoneMethod);
        }

        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }
}