// File: CommListeners.mc
//
// Copyright 2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
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
        System.println("Transmit Complete");
    }

    function onError() {
        System.println("Transmit Failed");
    }
}

// Main input delegate for the application
class CommInputDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
    }

    function onMenu() {
        // Send time immediately rather than showing menu
        var listener = new CommListener();
        Communications.transmit(AppState.timeOptions[AppState.selectedIndex], null, listener);
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
                var listener = new CommListener();
                Communications.transmit(AppState.timeOptions[AppState.selectedIndex], null, listener);
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
            if (key == WatchUi.KEY_UP || key == WatchUi.KEY_DOWN) {
                if (key == WatchUi.KEY_UP) {
                    AppState.selectedIndex = (AppState.selectedIndex - 1 + AppState.timeOptions.size()) % AppState.timeOptions.size();
                } else {
                    AppState.selectedIndex = (AppState.selectedIndex + 1) % AppState.timeOptions.size();
                }
                WatchUi.requestUpdate();
                return true;
            } else if (key == WatchUi.KEY_ENTER) {
                // Enter key sends the selected time
                var listener = new CommListener();
                Communications.transmit(AppState.timeOptions[AppState.selectedIndex], null, listener);
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