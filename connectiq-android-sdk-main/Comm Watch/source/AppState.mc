// File: AppState.mc
// Global application state management

using Toybox.System;
using Toybox.WatchUi;

/**
 * AppState - Global state management class
 * Stores and manages application state data shared between files
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
    
    // App lifecycle
    static var appStartTime = 0;
    static var ignoreMessagesOnStartup = true; // Flag to prevent processing messages at app startup
    
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
