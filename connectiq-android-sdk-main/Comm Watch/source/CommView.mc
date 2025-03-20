using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Timer;

/**
 * CommView class - Handles all UI rendering and display logic for the watch application.
 * Responsible for presenting different screens based on current app state and 
 * managing update intervals through a timer.
 */
class CommView extends WatchUi.View {
    var screenShape;
    var cameraIcon;
    var videoIcon;
    var timeIcons = {}; // Dictionary to store time icons
    var timer;
    var lastUpdateTime = 0;

    // In CommView.mc
   function initialize() {
      View.initialize();
      timer = new Timer.Timer();
      System.println("CommView initialized");
   }

   /**
    * Called when the view is about to be shown and dimensions are available.
    * Sets up resources, icons, and starts the update timer.
    * @param dc The device context
    */
   function onLayout(dc) {
      try {
          screenShape = System.getDeviceSettings().screenShape;
          
          // Load camera icon
          cameraIcon = WatchUi.loadResource(Rez.Drawables.CameraIcon);
          videoIcon = WatchUi.loadResource(Rez.Drawables.VideoIcon);
          
          // Load time icons - use try/catch to handle missing resources
          loadTimeIcons();
          
          // Start the timer with a faster update rate
          startUpdateTimer();
      } catch(ex) {
          System.println("Error in onLayout: " + ex.getErrorMessage());
          // Ensure timer is running even if other initialization fails
          startUpdateTimer();
      }
   }

   /**
    * Loads the time icons needed for display
    * Uses individual try/catch blocks to prevent one missing resource from
    * causing others to fail loading
    */
   function loadTimeIcons() {
      try { timeIcons["0"] = WatchUi.loadResource(Rez.Drawables.time_0); } 
      catch(ex) { System.println("Error loading time_0: " + ex.getErrorMessage()); }
      
      try { timeIcons["3"] = WatchUi.loadResource(Rez.Drawables.time_3); } 
      catch(ex) { System.println("Error loading time_3: " + ex.getErrorMessage()); }
      
      try { timeIcons["5"] = WatchUi.loadResource(Rez.Drawables.time_5); } 
      catch(ex) { System.println("Error loading time_5: " + ex.getErrorMessage()); }
      
      try { timeIcons["10"] = WatchUi.loadResource(Rez.Drawables.time_10); } 
      catch(ex) { System.println("Error loading time_10: " + ex.getErrorMessage()); }
   }
   
   /**
    * Starts or restarts the update timer
    * Extracted into a separate method for maintainability and reuse
    */
   function startUpdateTimer() {
      try {
          if (timer != null) {
              timer.stop();
              timer.start(method(:onTimer), 200, true);
              System.println("Timer started");
          } else {
              timer = new Timer.Timer();
              timer.start(method(:onTimer), 200, true);
              System.println("New timer created and started");
          }
      } catch(ex) {
          System.println("Error starting timer: " + ex.getErrorMessage());
      }
   }

   /**
    * Timer callback to handle UI updates based on app state
    * Controls update frequency and state transitions
    */
   function onTimer() as Void {
      try {
          var currentTime = System.getTimer();
          var needsUpdate = false;
          
          // Always update when countdown is active to show the changing numbers
          if (AppState.isCountdownActive) {
              // Check if countdown has ended
              var remaining = AppState.getRemainingTime();
              if (remaining <= 0) {
                  // Countdown finished
                  AppState.isCountdownActive = false;
                  AppState.lastMessage = "Sending...";
                  AppState.page = 1;
                  AppState.showMessageTimeout = currentTime + 1000;
              }
              // Always request update for countdown to show changing numbers
              WatchUi.requestUpdate();
              return;
          }
          
          // Always update when recording is active to show the time
          if (AppState.isRecordingActive && AppState.page == 2) {
              WatchUi.requestUpdate();
              return;
          }
          
          // Update less frequently for other states
          if (currentTime - lastUpdateTime < 250) {
              return; // Skip updates if too frequent
          }
          
          // Check message timeout
          if (AppState.page == 1 && AppState.showMessageTimeout > 0) {
              if (currentTime > AppState.showMessageTimeout) {
                  AppState.showMessageTimeout = 0;
                  AppState.page = 0;
                  needsUpdate = true;
              }
          }
          
          if (needsUpdate) {
              lastUpdateTime = currentTime;
              WatchUi.requestUpdate();
          }
      } catch(ex) {
          System.println("Error in onTimer: " + ex.getErrorMessage());
          // If timer fails, ensure we get back to a safe state
          if (AppState has :page) {
              AppState.page = 0;  // Return to main screen as fallback
          }
          WatchUi.requestUpdate();
      }
   }

   /**
    * Draws the countdown screen showing remaining time
    * @param dc The device context for drawing
    */
    function drawCountdownUI(dc) {
        try {
            var width = dc.getWidth();
            var height = dc.getHeight();
            var centerX = width / 2;
            var centerY = height / 2;
            
            // Clear background
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            dc.clear();
            
            // Draw the countdown number
            if (AppState.isCountdownActive) {
                dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
                var font = Graphics.FONT_NUMBER_THAI_HOT;
                var timeStr = AppState.getFormattedRemainingTime();
                // Protect against null or invalid time string
                if (timeStr != null && timeStr.length() > 0) {
                    dc.drawText(centerX, centerY, font, timeStr, 
                        Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
                }
            }
        } catch(ex) {
            System.println("Error in drawCountdownUI: " + ex.getErrorMessage());
            // Draw fallback content if there's an error
            drawErrorScreen(dc, "Countdown Error");
        }
    }
    
   /**
    * Draws the recording UI showing elapsed recording time
    * @param dc The device context for drawing
    */
    function drawRecordingUI(dc) {
    try {
        var width = dc.getWidth();
        var height = dc.getHeight();
        var centerX = width / 2;
        var centerY = height / 2;
        var paddingX = width * 0.1;
        var paddingY = width * 0.2;

        // Clear background
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        
        // Draw the video icon in the top right corner
        drawVideoIcon(dc, width, paddingX, paddingY);
        
        // Get and display recording time
        var timeStr = getFormattedRecordingTime();
        
        // Draw time elapsed
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, centerY, Graphics.FONT_NUMBER_MEDIUM, timeStr, 
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        
        // Draw instructions
        dc.drawText(centerX, height * 0.75, Graphics.FONT_SMALL, "Press to stop", 
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    } catch(ex) {
        System.println("Error in drawRecordingUI: " + ex.getErrorMessage());
        // Draw fallback content
        drawErrorScreen(dc, "Recording Error");
    }
}

   /**
    * Helper function to draw the video icon with "REC" text
    * @param dc The device context
    * @param width Screen width
    * @param paddingX Horizontal padding
    * @param paddingY Vertical padding
    */
   function drawVideoIcon(dc, width, paddingX, paddingY) {
       if (videoIcon != null) {
          var iconWidth = videoIcon.getWidth();
          var iconHeight = videoIcon.getHeight();
          
          // Add padding from the edges
          var videoIconX = width - paddingX - (iconWidth / 2);
          var videoIconY = paddingY + (iconHeight / 2);
          
          // Draw "REC" text to the left of the icon
          dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
          dc.drawText(videoIconX - (iconWidth / 2) - 5, videoIconY, Graphics.FONT_TINY, "REC", 
             Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
          
          dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
          dc.drawBitmap(videoIconX - (iconWidth / 2), videoIconY - (iconHeight / 2), videoIcon);
       } else {
          // Fallback if icon isn't available
          dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
          dc.drawText(width - paddingX, paddingY, Graphics.FONT_TINY, "REC", 
             Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
       }
   }
   
   /**
    * Gets the formatted recording time string safely
    * @return A string showing the elapsed recording time in MM:SS format
    */
   function getFormattedRecordingTime() {
       var timeStr = "00:00";
       try {
           if (AppState.isRecordingActive && AppState.recordingStartTime > 0) {
               var currentTime = System.getTimer();
               var elapsedSeconds = (currentTime - AppState.recordingStartTime) / 1000;
               if (elapsedSeconds < 0) {
                   elapsedSeconds = 0;  // Safety check for negative time
               }
               var minutes = (elapsedSeconds / 60).toNumber();
               var seconds = (elapsedSeconds % 60).toNumber();
               timeStr = minutes.format("%02d") + ":" + seconds.format("%02d");
           }
       } catch (ex) {
           System.println("Time calculation error: " + ex.getErrorMessage());
       }
       return timeStr;
   }

   /**
    * Draws the main UI showing camera icon and selected timer value
    * @param dc The device context for drawing
    */
    function drawSimpleUI(dc) {
        try {
            var width = dc.getWidth();
            var height = dc.getHeight();
            var centerX = width / 2;
            var centerY = height / 2;
            var paddingX = width * 0.1;
            var paddingY = width * 0.2;
            
            // Draw the camera icon in the top right corner
            drawCameraIcon(dc, width, paddingX, paddingY);
            
            // Draw the selected time option in the center
            drawSelectedTimeIcon(dc, centerX, centerY);
        } catch(ex) {
            System.println("Error in drawSimpleUI: " + ex.getErrorMessage());
            drawErrorScreen(dc, "UI Error");
        }
    }
    
   /**
    * Helper function to draw the camera icon
    * @param dc The device context
    * @param width Screen width
    * @param paddingX Horizontal padding
    * @param paddingY Vertical padding
    */
    function drawCameraIcon(dc, width, paddingX, paddingY) {
        if (cameraIcon != null) {
            var iconWidth = cameraIcon.getWidth();
            var iconHeight = cameraIcon.getHeight();
            
            // Add padding from the edges
            var cameraIconX = width - paddingX - (iconWidth / 2);
            var cameraIconY = paddingY + (iconHeight / 2);
            
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawBitmap(cameraIconX - (iconWidth / 2), cameraIconY - (iconHeight / 2), cameraIcon);
        } else {
            // Fallback if icon isn't available
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(width - paddingX, paddingX, Graphics.FONT_MEDIUM, "Capture", Graphics.TEXT_JUSTIFY_RIGHT);
        }
    }
    
   /**
    * Helper function to draw the selected time icon
    * @param dc The device context
    * @param centerX Horizontal center of the screen
    * @param centerY Vertical center of the screen
    */
    function drawSelectedTimeIcon(dc, centerX, centerY) {
        // Get the current selected time option with safety checks
        var selectedIndex = 0;
        if (AppState has :selectedIndex && AppState.selectedIndex != null) {
            selectedIndex = AppState.selectedIndex;
        }
        
        var timeOptions = ["0"];  // Default fallback
        if (AppState has :timeOptions && AppState.timeOptions != null && AppState.timeOptions.size() > 0) {
            timeOptions = AppState.timeOptions;
        }
        
        // Ensure index is within bounds
        if (selectedIndex < 0 || selectedIndex >= timeOptions.size()) {
            selectedIndex = 0;
        }
        
        var currentTimeOption = timeOptions[selectedIndex];
        
        // Draw the selected time icon in the center
        var timeIcon = null;
        if (timeIcons.hasKey(currentTimeOption)) {
            timeIcon = timeIcons[currentTimeOption];
        }
        
        if (timeIcon != null) {
            var iconWidth = timeIcon.getWidth();
            var iconHeight = timeIcon.getHeight();
            
            // Center the icon
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawBitmap(centerX - (iconWidth / 2), centerY - (iconHeight / 2), timeIcon);
        } else {
            // Fallback if icon isn't available
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(centerX, centerY, Graphics.FONT_LARGE, currentTimeOption, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

   /**
    * Draws the message display screen
    * @param dc The device context for drawing
    */
    function drawMessage(dc) {
        try {
            var width = dc.getWidth();
            var height = dc.getHeight();
            
            // Make the entire screen black
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            dc.fillRectangle(0, 0, width, height);
            
            // Set text color to white
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            
            // Get font height for proper vertical centering
            var font = Graphics.FONT_MEDIUM;
            var fontHeight = dc.getFontHeight(font);
            
            // Calculate center positions
            var centerX = width / 2;
            // Adjust vertical position to account for baseline alignment
            var centerY = height / 2 - (fontHeight / 2);
            
            // Get message with safety check
            var message = "Status";
            if (AppState has :lastMessage && AppState.lastMessage != null) {
                message = AppState.lastMessage;
            }
            
            // Draw the message in the center of the screen
            dc.drawText(centerX, centerY, font, message, Graphics.TEXT_JUSTIFY_CENTER);
        } catch(ex) {
            System.println("Error in drawMessage: " + ex.getErrorMessage());
            drawErrorScreen(dc, "Message Error");
        }
    }
    
   /**
    * Draws a simple error screen as a fallback when other UI functions fail
    * @param dc The device context for drawing
    * @param errorMsg The error message to display
    */
    function drawErrorScreen(dc, errorMsg) {
        try {
            // Clear screen
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            dc.clear();
            
            // Draw error message
            dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
            dc.drawText(dc.getWidth()/2, dc.getHeight()/2, Graphics.FONT_SMALL, 
                errorMsg, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        } catch(ex) {
            // Nothing more we can do if this fails
            System.println("Critical error in drawErrorScreen: " + ex.getErrorMessage());
        }
    }

   /**
    * Main update function called by the system to redraw the screen
    * Delegates to specific drawing functions based on app state
    * @param dc The device context for drawing
    */
    function onUpdate(dc) {
        try {
            // Clear the background first
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            // Reduced logging in production code
            // System.println("onUpdate: page=" + AppState.page + 
            //              ", isCountdownActive=" + AppState.isCountdownActive +
            //              ", isRecordingActive=" + AppState.isRecordingActive);
            
            // Check if countdown is active - this takes highest priority
            // Note: We check just isCountdownActive without page dependency
            if (AppState has :isCountdownActive && AppState.isCountdownActive) {
                drawCountdownUI(dc);
                return;
            }
            
            // Check if recording is active
            if (AppState has :isRecordingActive && AppState has :page && 
                AppState.isRecordingActive && AppState.page == 2) {
                drawRecordingUI(dc);
                return;
            }
            
            // Normal UI handling
            if(AppState has :hasDirectMessagingSupport && AppState.hasDirectMessagingSupport) {
                if(AppState has :page) {
                    if(AppState.page == 0) {
                        drawSimpleUI(dc);
                    } else if(AppState.page == 1) {
                        drawMessage(dc);
                    } else {
                        // Fallback for unknown page state
                        drawSimpleUI(dc);
                    }
                } else {
                    drawSimpleUI(dc);  // Fallback if page not defined
                }
            } else {
                drawApiNotSupportedScreen(dc);
            }
            
        } catch (ex) {
            // Catch any drawing errors to prevent crashes
            System.println("Error in onUpdate: " + ex.getErrorMessage());
            
            // Fall back to a very simple screen
            drawErrorScreen(dc, "Display Error");
        }
    }
    
   /**
    * Draws a screen indicating the API is not supported
    * @param dc The device context for drawing
    */
    function drawApiNotSupportedScreen(dc) {
        try {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(dc.getWidth() / 2, dc.getHeight() / 3, Graphics.FONT_MEDIUM, 
                "Direct Messaging API\nNot Supported", Graphics.TEXT_JUSTIFY_CENTER);
        } catch(ex) {
            System.println("Error in drawApiNotSupportedScreen: " + ex.getErrorMessage());
            drawErrorScreen(dc, "API Error");
        }
    }
}