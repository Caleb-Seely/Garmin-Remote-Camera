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
        
        // Normal timer start case
        var timeString = AppState.timeOptions[AppState.selectedIndex];
        if (AppState.startCountdown(timeString)) {
            AppState.lastMessage = "Hello";           //Countdown, will probably replace this with time immediatly 
        } else {
            AppState.lastMessage = "Sending...";      // No countdown
        }
        
        AppState.page = 1;
        AppState.showMessageTimeout = System.getTimer() + 2000;
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
function safeTransmit(isTest) {
    
    AppState.isTransmitting = true;
    var listener = new CommListener();
    
    // If countdown is active, send cancel message
    if (AppState.isCountdownActive) {
        listener.wasCancelled = true;
        Communications.transmit("CANCEL", null, listener);
        System.println("Cancelling countdown");
        return;
    }
    
    System.println("Transmit Normal");
    Communications.transmit(AppState.timeOptions[AppState.selectedIndex], null, listener);
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
        if (AppState.page == 0) {
            safeTransmit(false);
            System.println("Select (ENTER)");
            return true;
        } else {
            // Dismiss message and return to main screen
            dismissMessageScreen();
            return true;
        }
    }

    // Handle key events not covered by behavioral methods
    function onKey(evt) {
        var key = evt.getKey();
        System.println("onKey");
        
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
        // For other pages (message screen)
        else if (key == WatchUi.KEY_ENTER || key == WatchUi.KEY_ESC) {
            dismissMessageScreen();
            return true;
        }
        
        return false;
    }

        
    // Handle swipe events
    function onSwipe(swipeEvent) {
        var direction = swipeEvent.getDirection();
        
        if (AppState.page == 0) {
            if (direction == WatchUi.SWIPE_UP) {
                return onNextPage();
            } else if (direction == WatchUi.SWIPE_DOWN) {
                return onPreviousPage();
            }
        } else {
            // Swipe dismisses message screen
            dismissMessageScreen();
            return true;
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
    
    // Commented out but preserved from original code
    // Holding the up key triggers this
    // function onMenu() {
    //     // Send time immediately rather than showing menu
    //     safeTransmit(false);
    //     System.println("OnMenu transmit");
    //     return true;
    // }
}

