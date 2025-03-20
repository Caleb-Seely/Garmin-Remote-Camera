// File: CommApp.mc

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;
using Toybox.Attention;

/**
 * AppState - Global state management class
 * Stores and manages application state data shared between files
 * TODO: In the future, this could be refactored into domain-specific state managers
 */
class AppState {
    // UI state
    static var page = 0;
    static var lastMessage = "";
    static var showMessageTimeout = 0;
    static const MESSAGE_DISPLAY_TIME = 2; // seconds to show message
    
    // Message history - used for debug purposes
    static var strings = ["","","","",""];  // Message history array
    static var stringsSize = 5;
    
    // Time selection options
    static var selectedIndex = 0;
    static var timeOptions = ["0", "10", "5", "3"];  // Matched to SVG assets
    
    // Communication state
    static var hasDirectMessagingSupport = true;
    static var phoneMethod = null;
    static var lastTransmitTime = 0;
    static var isTransmitting = false;
    static var TRANSMIT_COOLDOWN = 2000; // 2 second cooldown between transmits
    
    // Button handling
    static var upButtonPressTime = 0;
    static var UP_BUTTON_HOLD_THRESHOLD = 1000; // 1 second hold threshold
    
    // Countdown timer state
    static var isCountdownActive = false;
    static var countdownEndTime = 0;
    static var countdownDuration = 0; // Duration in milliseconds
    static var countdownStartTime = 0;
    
    // Recording state
    static var isRecordingActive = false;
    static var recordingStartTime = 1; //to account for delay
    static var wasRecordingActive = false; // Flag to track if we just stopped recording
    
    // App lifecycle
    static var appStartTime = 0;
    static var ignoreMessagesOnStartup = true; // Flag to prevent processing messages at app startup
    
    /**
     * Start a countdown timer based on the selected time string
     * @param timeString The time value as a string (e.g. "10")
     * @return true if countdown was started successfully, false otherwise
     */
    static function startCountdown(timeString) {
        try {
            // Ensure we're not in recording state
            if (isRecordingActive) {
                System.println("Cannot start countdown while recording is active");
                return false;
            }
            
            // Reset all potentially conflicting flags and states
            wasRecordingActive = false;
            lastMessage = ""; // Clear any pending message
            showMessageTimeout = 0; // Reset message timeout
            
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
                page = 0;
                
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
     * Clear all message history
     */
    static function clearMessageHistory() {
        try {
            for (var i = 0; i < stringsSize; i++) {
                strings[i] = "";
            }
            lastMessage = "";
        } catch (ex) {
            System.println("Error clearing message history: " + ex.getErrorMessage());
        }
    }
    
    /**
     * Start the recording timer
     * @return true if recording started successfully
     */
    static function startRecording() {
        try {
            isRecordingActive = true;
            isCountdownActive = false; // Ensure countdown is off
            recordingStartTime = System.getTimer();
            System.println("Recording started at " + recordingStartTime);
            return true;
        } catch (ex) {
            System.println("Error starting recording: " + ex.getErrorMessage());
            return false;
        }
    }
    
    /**
     * Stop the recording timer
     * @return true if recording stopped successfully
     */
    static function stopRecording() {
        try {
            isRecordingActive = false;
            wasRecordingActive = true; // Set flag to indicate recording was active
            System.println("Recording stopped");
            return true;
        } catch (ex) {
            System.println("Error stopping recording: " + ex.getErrorMessage());
            return false;
        }
    }
    
    /**
     * Get the elapsed recording time in seconds
     * @return Recording duration in seconds, or 0 if not recording
     */
    static function getRecordingElapsedTime() {
        try {
            if (!isRecordingActive) {
                return 0;
            }
            
            var currentTime = System.getTimer();
            var elapsed = (currentTime - recordingStartTime) / 1000.0; // Convert to seconds
            
            // Add debug output
            System.println("Recording time: current=" + currentTime + ", start=" + recordingStartTime + ", elapsed=" + elapsed);
            
            // Ensure non-negative value
            return elapsed < 0 ? 0 : elapsed;
        } catch (ex) {
            System.println("Error getting recording time: " + ex.getErrorMessage());
            return 0;
        }
    }
    
    /**
     * Format recording elapsed time as a string (mm:ss)
     * @return Formatted time string in mm:ss format
     */
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
    
    /**
     * Validate an incoming message for safety
     * @param message The message to validate
     * @return true if the message is valid, false otherwise
     */
    static function validateMessage(message) {
        try {
            // Null check
            if (message == null) {
                System.println("Invalid message: null");
                return false;
            }
            
            // Check different message formats
            // Format 1: Raw string with data property
            if (message has :data) {
                // Check if data can be converted to string
                var dataStr = null;
                try {
                    dataStr = message.data.toString();
                    
                    // Don't allow excessively long messages
                    if (dataStr.length() > 100) {
                        System.println("Invalid message: too long");
                        return false;
                    }
                    
                    return true;
                } catch (ex) {
                    System.println("Invalid message data: cannot convert to string");
                }
            }
            
            // Format 2: Direct string messages
            if (message has :toString) {
                try {
                    var msgStr = message.toString();
                    if (msgStr != null && msgStr.length() <= 100) {
                        return true;
                    }
                } catch (ex) {
                    System.println("Error converting message to string: " + ex.getErrorMessage());
                }
            }
            
            // Format 3: Messages with specific properties used by the app
            // Check for common message properties that might be used
            if ((message has :text) || 
                (message has :message) || 
                (message has :value)) {
                return true;
            }
            
            System.println("Message failed all format validations");
            return false;
        } catch (ex) {
            System.println("Error validating message: " + ex.getErrorMessage());
            return false;
        }
    }
}

/**
 * CommExample - Main application class
 * Handles application lifecycle and communication with the phone
 */
class CommExample extends Application.AppBase {
    /**
     * Initialize the application
     */
    function initialize() {
        try {
            Application.AppBase.initialize();
    
            // Assign method handler
            AppState.phoneMethod = method(:onPhoneHandler);
            
            // Check if direct messaging is supported on this device
            if(Communications has :registerForPhoneAppMessages) {
                Communications.registerForPhoneAppMessages(AppState.phoneMethod);
                AppState.hasDirectMessagingSupport = true;
            } else {
                AppState.hasDirectMessagingSupport = false;
            }
        } catch (ex) {
            System.println("Error in initialize: " + ex.getErrorMessage());
            // Set direct messaging as unsupported on error
            AppState.hasDirectMessagingSupport = false;
        }
    }
    
    /**
     * Handle incoming messages from phone
     * @param msg The message received from the phone
     * @return true to indicate message was handled
     */
    function onPhoneHandler(msg) {
        try {
            // Skip processing messages if we're in startup mode
            if (AppState.ignoreMessagesOnStartup) {
                System.println("Ignoring message during app startup");
                return true;
            }
            
            // Validate incoming message
            if (!AppState.validateMessage(msg)) {
                System.println("Message validation failed, ignoring: " + (msg has :toString ? msg.toString() : "unprintable message"));
                return true;
            }

            // Debug the received message
            System.println("Processing valid message: " + (msg has :data ? msg.data.toString() : "direct message"));
            
            // Extract the message text based on format
            var messageText;
            if (msg has :data) {
                messageText = msg.data.toString();
            } else if (msg has :text) {
                messageText = msg.text;
            } else if (msg has :message) {
                messageText = msg.message;
            } else if (msg has :value) {
                messageText = msg.value;
            } else {
                messageText = msg.toString();
            }
            
            // Store the received message
            AppState.lastMessage = messageText;
            
            // Handle recording start message
            if (messageText.equals("Recording started")) {
                return handleRecordingStarted();
            }
            
            // Handle recording stop message
            if (messageText.equals("Recording stopped")) {
                return handleRecordingStopped();
            }
            
            // For other messages, use the standard behavior
            return handleStandardMessage(messageText);
        } catch (ex) {
            System.println("Error in onPhoneHandler: " + ex.getErrorMessage());
            // Recover to a safe state on error
            AppState.page = 0;
            WatchUi.requestUpdate();
            return true;
        }
    }
    
    /**
     * Handle "Recording started" message from phone
     * @return true to indicate message was handled
     */
    function handleRecordingStarted() {
        try {
            // Start recording and immediately switch to recording page
            AppState.recordingStartTime = System.getTimer() - 1000; // +1sec to account for delay. Watch doesn't know it's in video mode until it gets this msg 
            AppState.startRecording();
            AppState.page = 2; // Set directly to recording page
            AppState.wasRecordingActive = false; // Ensure this flag is reset
            
            // Vibrate to notify user
            vibrate();
            
            WatchUi.requestUpdate();
            return true;
        } catch (ex) {
            System.println("Error handling recording started: " + ex.getErrorMessage());
            return true;
        }
    }
    
    /**
     * Handle "Recording stopped" message from phone
     * @return true to indicate message was handled
     */
    function handleRecordingStopped() {
        try {
            AppState.stopRecording();
            AppState.page = 1; // Show message screen instead of returning directly to main screen
            AppState.lastMessage = "Recording finished";
            AppState.wasRecordingActive = true; // Set this flag to indicate we just stopped recording
            
            // Set the show message timeout
            AppState.showMessageTimeout = System.getTimer() + (AppState.MESSAGE_DISPLAY_TIME * 1000);
            
            // Vibrate to notify user
            vibrate();
            
            WatchUi.requestUpdate();
            return true;
        } catch (ex) {
            System.println("Error handling recording stopped: " + ex.getErrorMessage());
            return true;
        }
    }
    
    /**
     * Handle standard (non-recording) messages from phone
     * @param messageText The text of the message
     * @return true to indicate message was handled
     */
    function handleStandardMessage(messageText) {
        try {
            // Reset wasRecordingActive flag for any other messages
            AppState.wasRecordingActive = false;
            
            // Set the show message timeout
            AppState.showMessageTimeout = System.getTimer() + (AppState.MESSAGE_DISPLAY_TIME * 1000);
            
            // Vibrate to notify user
            vibrate();
    
            // Store in the history array
            for(var i = (AppState.stringsSize - 1); i > 0; i -= 1) {
                AppState.strings[i] = AppState.strings[i-1];
            }
            AppState.strings[0] = messageText;
            
            // Switch to message display
            AppState.page = 1;
    
            WatchUi.requestUpdate();
            return true;
        } catch (ex) {
            System.println("Error handling standard message: " + ex.getErrorMessage());
            return true;
        }
    }
    
    /**
     * Helper function to vibrate the watch
     * Safely handles devices without vibration support
     */
    function vibrate() {
        try {
            if (Attention has :vibrate) {
                Attention.vibrate([new Attention.VibeProfile(50, 500)]); // 500ms vibration
            }
        } catch (ex) {
            System.println("Error vibrating: " + ex.getErrorMessage());
        }
    }
    
    /**
     * Enable message processing after startup delay
     */
    function enableMessageProcessing() as Void {
        AppState.ignoreMessagesOnStartup = false;
        System.println("Message processing enabled");
    }
    
    /**
     * Called when the application starts
     * @param state Application state information
     */
    function onStart(state) {
        try {
            // Clear message history when the app starts
            AppState.clearMessageHistory();
            
            // Make sure we're on the main page
            AppState.page = 0;
            AppState.showMessageTimeout = 0;
            
            // Set flag to ignore messages during startup
            AppState.ignoreMessagesOnStartup = true;
            
            // Create a timer to reset the ignore flag after a short delay
            var startupTimer = new Timer.Timer();
            startupTimer.start(method(:enableMessageProcessing), 2000, false);
        } catch (ex) {
            System.println("Error in onStart: " + ex.getErrorMessage());
        }
    }
    
    /**
     * Called when the application is exiting
     * @param state Application state information
     */
    function onStop(state) {
        try {
            // Clear message history when the app exits
            AppState.clearMessageHistory();
        } catch (ex) {
            System.println("Error in onStop: " + ex.getErrorMessage());
        }
    }

    /**
     * Return the initial view of the application
     * @return Array containing View and InputDelegate instances
     */
    function getInitialView() {
        return [new CommView(), new CommInputDelegate()];
    }
}