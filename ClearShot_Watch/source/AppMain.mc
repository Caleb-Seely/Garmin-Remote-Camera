// File: AppMain.mc
// Main application class and entry point

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;
using Toybox.Attention;
using Toybox.Timer;

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
      AppState.phoneMethod = method( : onPhoneHandler);

      // Check if direct messaging is supported on this device
      if (Communications has : registerForPhoneAppMessages) {
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
        System.println("Message validation failed, ignoring: " +
                       (msg has
                        : toString ? msg.toString() : "unprintable message"));
        return true;
      }

      // Debug the received message
      System.println("Processing valid message: " +
                     (msg has
                      : data ? msg.data.toString() : "direct message"));

      // Extract the message text based on format
      var messageText;
      if (msg has : data) {
        messageText = msg.data.toString();
      } else if (msg has : text) {
        messageText = msg.text;
      } else if (msg has : message) {
        messageText = msg.message;
      } else if (msg has : value) {
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
      // IMPORTANT: Set recording time BEFORE calling startRecording()
      // This ensures the time isn't reset by the function
      RecordingManager.recordingStartTime = System.getTimer();
      System.println("RECORDING START TIME SET TO: " +
                     RecordingManager.recordingStartTime);

      // Now start recording
      RecordingManager.startRecording();
      AppState.page = 2;  // Set directly to recording page
      RecordingManager.wasRecordingActive = false;  // Ensure this flag is reset

      // Debug output for tracking timing
      System.println("Recording Started - Timer set to: " +
                     RecordingManager.recordingStartTime);

      // Vibrate to notify user
      vibrate();

      WatchUi.requestUpdate();
      return true;
    } catch (ex) {
      System.println("Error handling recording started: " +
                     ex.getErrorMessage());
      return true;
    }
  }

  /**
   * Handle "Recording stopped" message from phone
   * @return true to indicate message was handled
   */
  function handleRecordingStopped() {
    try {
      RecordingManager.stopRecording();
      AppState.page = 1;  // Show message screen instead of returning directly
                          // to main screen
      AppState.lastMessage = "Recording finished";
      RecordingManager.wasRecordingActive =
          true;  // Set this flag to indicate we just stopped recording

      // Set the show message timeout
      AppState.showMessageTimeout =
          System.getTimer() + (AppState.MESSAGE_DISPLAY_TIME * 1000);

      // Vibrate to notify user
      vibrate();

      WatchUi.requestUpdate();
      return true;
    } catch (ex) {
      System.println("Error handling recording stopped: " +
                     ex.getErrorMessage());
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
      RecordingManager.wasRecordingActive = false;

      // Set the show message timeout
      AppState.showMessageTimeout =
          System.getTimer() + (AppState.MESSAGE_DISPLAY_TIME * 1000);

      // Vibrate to notify user
      vibrate();

      // Store in the history array
      for (var i = (AppState.stringsSize - 1); i > 0; i -= 1) {
        AppState.strings[i] = AppState.strings[i - 1];
      }
      AppState.strings[0] = messageText;

      // Switch to message display
      AppState.page = 1;

      WatchUi.requestUpdate();
      return true;
    } catch (ex) {
      System.println("Error handling standard message: " +
                     ex.getErrorMessage());
      return true;
    }
  }

  /**
   * Helper function to vibrate the watch
   * Safely handles devices without vibration support
   */
  function vibrate() {
    try {
      if (Attention has : vibrate) {
        Attention.vibrate(
            [new Attention.VibeProfile(50, 500)]);  // 500ms vibration
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
      startupTimer.start(method( : enableMessageProcessing), 2000, false);
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
    try {
      return [ new ViewManager(), new InputDelegate() ];
    } catch (ex) {
      System.println("Error in getInitialView: " + ex.getErrorMessage());
      // Return basic view if there's an error
      return [ new MainView(), new InputDelegate() ];
    }
  }
}
