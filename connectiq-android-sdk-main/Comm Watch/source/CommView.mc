using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Timer;

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

   function onLayout(dc) {
      screenShape = System.getDeviceSettings().screenShape;
      
      // Load camera icon
      cameraIcon = WatchUi.loadResource(Rez.Drawables.CameraIcon);
      videoIcon = WatchUi.loadResource(Rez.Drawables.VideoIcon);
      
      // Load time icons - use try/catch to handle missing resources
      try {
         timeIcons["0"] = WatchUi.loadResource(Rez.Drawables.time_0);
         timeIcons["3"] = WatchUi.loadResource(Rez.Drawables.time_3);
         timeIcons["5"] = WatchUi.loadResource(Rez.Drawables.time_5);
         timeIcons["10"] = WatchUi.loadResource(Rez.Drawables.time_10);
      } catch(ex) {
         System.println("Error loading time icons: " + ex.getErrorMessage());
      }
      
      // Start the timer with a faster update rate
      if (timer != null) {
         timer.stop();
         timer.start(method(:onTimer), 200, true);
         System.println("Timer started in onLayout");
      }
   }

   function onTimer() {
      // Always request an update when recording is active
      if (AppState.isRecordingActive && AppState.page == 2) {
         WatchUi.requestUpdate();
         return;
      }
      
      var currentTime = System.getTimer();
      if (currentTime - lastUpdateTime < 250) {
         return; // Skip other updates if too frequent
      }
      
      var needsUpdate = false;
      
      // Check message timeout
      if (AppState.page == 1 && AppState.showMessageTimeout > 0) {
         if (currentTime > AppState.showMessageTimeout) {
               AppState.showMessageTimeout = 0;
               AppState.page = 0;
               needsUpdate = true;
         }
      }
      
      // Check countdown
      if (AppState.isCountdownActive) {
         var remaining = AppState.getRemainingTime();
         if (remaining <= 0) {
               AppState.isCountdownActive = false;
               AppState.lastMessage = "Sending..";
               AppState.page = 1;
               AppState.showMessageTimeout = currentTime + 1000;
               needsUpdate = true;
         } else {
               needsUpdate = true;
         }
      }
      
      if (needsUpdate) {
         lastUpdateTime = currentTime;
         WatchUi.requestUpdate();
      }
   }

    function drawCountdownUI(dc) {
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
    }
    
    function drawRecordingUI(dc) {
    var width = dc.getWidth();
    var height = dc.getHeight();
    var centerX = width / 2;
    var centerY = height / 2;
    var paddingX = width * 0.1;
    var paddingY = width * 0.2;

    // Clear background
    dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
    dc.clear();
    
        // Draw the camera icon in the top right corner
        if (videoIcon != null) {
            var iconWidth = videoIcon.getWidth();
            var iconHeight = videoIcon.getHeight();
            
            // Add padding from the edges
            var videoIconX = width - paddingX - (iconWidth / 2);
            var videoIconY = paddingY + (iconHeight / 2);
            
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawBitmap(videoIconX - (iconWidth / 2), videoIconY - (iconHeight / 2), videoIcon);
        } else {
            // Fallback if icon isn't available

        }
        
    
    // Draw "REC" text
    dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
    dc.drawText(width * 0.7, height * 0.2 - 5, Graphics.FONT_TINY, "REC", 
        Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    
    // Get current time as a simple calculation to avoid function call errors
    var timeStr = "00:00";
    try {
        if (AppState.isRecordingActive && AppState.recordingStartTime > 0) {
            var currentTime = System.getTimer();
            var elapsedSeconds = (currentTime - AppState.recordingStartTime) / 1000;
            var minutes = (elapsedSeconds / 60).toNumber();
            var seconds = (elapsedSeconds % 60).toNumber();
            timeStr = minutes.format("%02d") + ":" + seconds.format("%02d");
        }
    } catch (ex) {
        System.println("Time calculation error: " + ex.getErrorMessage());
    }
    
    // Draw time elapsed
    dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
    dc.drawText(centerX, centerY, Graphics.FONT_NUMBER_MEDIUM, timeStr, 
        Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    
    // Draw instructions
    dc.drawText(centerX, height * 0.75, Graphics.FONT_SMALL, "Press to stop", 
        Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
}

    function drawSimpleUI(dc) {
        var width = dc.getWidth();
        var height = dc.getHeight();
        var centerX = width / 2;
        var centerY = height / 2;
        var paddingX = width * 0.1;
        var paddingY = width * 0.2;
        
        // Draw the camera icon in the top right corner
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
        
        // Get the current selected time option
        var currentTimeOption = AppState.timeOptions[AppState.selectedIndex];
        
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

    function drawMessage(dc) {
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
        
        // Draw the message in the center of the screen
        dc.drawText(centerX, centerY, 
                    font, 
                    AppState.lastMessage, 
                    Graphics.TEXT_JUSTIFY_CENTER);
         AppState.lastMessage = "";    //Clear the last message to avoid is showing at the wrong time
    }

    function onUpdate(dc) {
        try {
            // Check if countdown is active - this takes highest priority
            if (AppState.isCountdownActive) {
                drawCountdownUI(dc);
                return;
            }
            
            // Check if recording is active
            if (AppState.isRecordingActive && AppState.page == 2) {
                drawRecordingUI(dc);
                return;
            }
            
            // Normal UI handling
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            if(AppState.hasDirectMessagingSupport) {
                if(AppState.page == 0) {
                    drawSimpleUI(dc);
                } else if(AppState.page == 1) {
                    // First draw the UI in the background
                    drawSimpleUI(dc);
                    // Then overlay the message
                    drawMessage(dc);
                } else {
                    // Fallback for unknown page state
                    drawSimpleUI(dc);
                }
            } else {
                dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
                dc.drawText(dc.getWidth() / 2, dc.getHeight() / 3, Graphics.FONT_MEDIUM, 
                    "Direct Messaging API\nNot Supported", Graphics.TEXT_JUSTIFY_CENTER);
            }
            
        } catch (ex) {
            // Catch any drawing errors to prevent crashes
            System.println("Error in onUpdate: " + ex.getErrorMessage());
            
            // Fall back to a very simple screen
            dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
            dc.clear();
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(dc.getWidth()/2, dc.getHeight()/2, Graphics.FONT_MEDIUM, 
                "Error", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
    }
}