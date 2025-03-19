// File: CommApp.mc

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;
using Toybox.Attention;

// Global state shared between files
class AppState {
    // Your existing properties
    static var page = 0;
    static var lastMessage = "";
    static var strings = ["","","","",""];
    static var stringsSize = 5;
    static var selectedIndex = 0;
    static var timeOptions = ["0", "10", "5", "3"];  // Matched to your SVG assets
    static var crashOnMessage = false;
    static var hasDirectMessagingSupport = true;
    static var phoneMethod = null;
    static var showMessageTimeout = 0;
    static const MESSAGE_DISPLAY_TIME = 2; // seconds to show message
    static var lastTransmitTime = 0;
    static var isTransmitting = false;
    static var upButtonPressTime = 0;
    static var UP_BUTTON_HOLD_THRESHOLD = 1000; // 1 second hold threshold
    static var TRANSMIT_COOLDOWN = 2000; // 2 second cooldown between transmits

    // New properties for countdown timer
    static var isCountdownActive = false;
    static var countdownEndTime = 0;
    static var countdownDuration = 0; // Duration in milliseconds
    static var countdownStartTime = 0;
    
    // New property for recording timer
    static var isRecordingActive = false;
    static var recordingStartTime = 0;
    
    // Start a countdown timer based on the selected time string (e.g. "10")
    static function startCountdown(timeString) {
        // Parse the time string (no units in your timeOptions)
        var seconds = 0;
        if (timeString != null && timeString.length() > 0) {
            seconds = timeString.toNumber() - 1; //-1 to better match the phone timer
        }
        
        if (seconds > 0) {
            countdownDuration = seconds * 1000; // Convert to milliseconds
            countdownStartTime = System.getTimer();
            countdownEndTime = countdownStartTime + countdownDuration;
            isCountdownActive = true;
            
            System.println("Starting countdown for " + seconds + " seconds");
            return true;
        }
        
        return false;
    }
    
    // Start recording timer - simplified to avoid potential issues
// In CommApp.mc, update startRecording
static function startRecording() {
    recordingStartTime = System.getTimer();
    isRecordingActive = true;
    isCountdownActive = false; // Ensure countdown is off
    System.println("Recording started at " + recordingStartTime);
    return true;
}
    
    // Stop recording timer - simplified
    static function stopRecording() {
        isRecordingActive = false;
        System.println("Recording stopped");
        return true;
    }
    
    // Get recording elapsed time in seconds - simplified
// In CommApp.mc, update getRecordingElapsedTime with debugging
static function getRecordingElapsedTime() {
    if (!isRecordingActive) {
        return 0;
    }
    
    var currentTime = System.getTimer();
    var elapsed = (currentTime - recordingStartTime) / 1000.0; // Convert to seconds
    
    // Add debug output
    System.println("Recording time: current=" + currentTime + ", start=" + recordingStartTime + ", elapsed=" + elapsed);
    
    return elapsed;
}
    
    // Format recording elapsed time as a string (mm:ss) - with error handling
static function getFormattedRecordingTime() {
    try {
        var elapsed = getRecordingElapsedTime();
        
        if (elapsed < 0) {
            elapsed = 0; // Safeguard against negative time
        }
        
        // Use integer division instead of Math.floor
        var minutes = (elapsed / 60).toNumber();
        var seconds = (elapsed % 60).toNumber();
        
        return minutes.format("%02d") + ":" + seconds.format("%02d");
    } catch (ex) {
        System.println("Error formatting recording time: " + ex.getErrorMessage());
        return "00:00"; // Fallback on error
    }
}
    
    // Get remaining time in seconds
    static function getRemainingTime() {
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
    }
    
    // Format remaining time as a string (e.g. "9")
    static function getFormattedRemainingTime() {
        var remaining = getRemainingTime();
        if (remaining <= 0) {
            return "0";
        }
        
        // Round to nearest second and return as integer
        return (remaining + 0.5).toNumber().toString();
    }
}


class CommExample extends Application.AppBase {
    function initialize() {
        Application.AppBase.initialize();

        // Assign method handler
        AppState.phoneMethod = method(:onPhoneHandler);
        
        if(Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(AppState.phoneMethod);
            AppState.hasDirectMessagingSupport = true;
        } else {
            AppState.hasDirectMessagingSupport = false;
        }
    }
    
    // Class method to handle phone messages
    function onPhoneHandler(msg) {
        var i;

        if((AppState.crashOnMessage == true) && msg.data.equals("Hi")) {
            msg.length(); // Generates a symbol not found error in the VM
        }

        // Store the received message
        var messageText = msg.data.toString();
        AppState.lastMessage = messageText;
        
        // Check if the message is "Recording started"
        if (messageText.equals("Recording started")) {
            // Switch to message view first to avoid direct page transition issues
            AppState.page = 1;
            AppState.startRecording();
            
            // Vibrate to notify user
            if (Attention has :vibrate) {
                Attention.vibrate([new Attention.VibeProfile(50, 500)]); // 500ms vibration
            }
            
            WatchUi.requestUpdate();
            
            // Add a small delay before switching to recording view
            // This gives the system time to process the initial update
            var timer = new Timer.Timer();
            timer.start(method(:delayedSwitchToRecording), 100, false);
            
            return true;
        }
        
        // For other messages, use the standard behavior
        // Set the show message timeout
        AppState.showMessageTimeout = System.getTimer() + (AppState.MESSAGE_DISPLAY_TIME * 1000);
        
        // Vibrate to notify user
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(50, 500)]); // 500ms vibration
        }

        // Store in the history array
        for(i = (AppState.stringsSize - 1); i > 0; i -= 1) {
            AppState.strings[i] = AppState.strings[i-1];
        }
        AppState.strings[0] = messageText;
        
        // Switch to message display
        AppState.page = 1;

        WatchUi.requestUpdate();
        return true;
    }
    
// Helper function to switch to recording view after a delay
function delayedSwitchToRecording() as Void {
    if (AppState.isRecordingActive) {
        AppState.page = 2; // Set to recording page
        WatchUi.requestUpdate();
    }
}

    // onStart() is called on application start up
    function onStart(state) {
        // Don't show initial message when opening the app
    }

    // onStop() is called when your application is exiting
    function onStop(state) {
        // Any cleanup code
    }

    // Return the initial view of your application here
    function getInitialView() {
        return [new CommView(), new CommInputDelegate()];
    }
}