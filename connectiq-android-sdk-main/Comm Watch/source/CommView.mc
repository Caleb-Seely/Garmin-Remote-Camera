// File: CommView.mc
//
// Copyright 2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Timer;

class CommView extends WatchUi.View {
    var screenShape;
    var cameraIcon;
    var timer;

    function initialize() {
        View.initialize();
        timer = new Timer.Timer();
    }

    function onLayout(dc) {
        screenShape = System.getDeviceSettings().screenShape;
        
        // Load camera icon bitmap
        // Note: You'll need to add a camera icon resource to your project
        cameraIcon = WatchUi.loadResource(Rez.Drawables.CameraIcon);
        
        // Start the timer to check for message timeout
        timer.start(method(:onTimer), 1000, true);
    }
    
    function onTimer() {
        // Check if the message display should timeout
        if (AppState.page == 1 && AppState.showMessageTimeout > 0) {
            if (System.getTimer() > AppState.showMessageTimeout) {
                AppState.showMessageTimeout = 0;
                AppState.page = 0;
                WatchUi.requestUpdate();
            }
        }
    }

    function drawSimpleUI(dc) {
        var centerX = dc.getWidth() / 2;
        var centerY = dc.getHeight() / 2;
        var radius;
        
        // Determine circle size based on screen dimensions
        if (dc.getWidth() < dc.getHeight()) {
            radius = dc.getWidth() * 0.4;
        } else {
            radius = dc.getHeight() * 0.4;
        }
        
        // Draw yellow background circle
      //   dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
      //   dc.fillCircle(centerX, centerY, radius);
        
        // Draw the "Delay" label at top of circle
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, centerY - (radius / 2), Graphics.FONT_MEDIUM, "Delay", Graphics.TEXT_JUSTIFY_CENTER);
        
        // Draw the selected time value in large font
        dc.drawText(centerX, centerY + (radius / 4), Graphics.FONT_LARGE, AppState.timeOptions[AppState.selectedIndex], Graphics.TEXT_JUSTIFY_CENTER);
        
        // Draw camera icon in top-right of circle
        var iconX = centerX + (radius * 0.6);
        var iconY = centerY - (radius * 0.6);
        
        if (cameraIcon != null) {
            dc.drawBitmap(iconX - (cameraIcon.getWidth() / 2), iconY - (cameraIcon.getHeight() / 2), cameraIcon);
        } else {
            // Fallback if icon isn't available - draw a small camera-like shape
            dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.fillRectangle(iconX - 10, iconY - 7, 20, 14);
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.fillCircle(iconX, iconY, 5);
        }
    }

    function drawMessage(dc) {
        var centerX = dc.getWidth() / 2;
        var centerY = dc.getHeight() / 2;
        
        // Draw a dark overlay with 80% opacity
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.fillRectangle(0, 0, dc.getWidth(), dc.getHeight());
        
        // Draw message box
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(centerX - (dc.getWidth() * 0.4), centerY - (dc.getHeight() * 0.2), 
                              dc.getWidth() * 0.8, dc.getHeight() * 0.4, 10);
        
        // Draw title
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, centerY - (dc.getHeight() * 0.1), 
                   Graphics.FONT_SMALL, "Message Received", Graphics.TEXT_JUSTIFY_CENTER);
        
        // Draw the message
        dc.drawText(centerX, centerY, Graphics.FONT_MEDIUM, 
                   AppState.lastMessage, Graphics.TEXT_JUSTIFY_CENTER);
        
        // Draw tap instruction
        dc.drawText(centerX, centerY + (dc.getHeight() * 0.15), 
                   Graphics.FONT_TINY, "Tap to dismiss", Graphics.TEXT_JUSTIFY_CENTER);
    }

    function onUpdate(dc) {
        // Clear the screen
        dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
        dc.clear();
        
        if(AppState.hasDirectMessagingSupport) {
            if(AppState.page == 0) {
                drawSimpleUI(dc);
            } else {
                // First draw the UI in the background
                drawSimpleUI(dc);
                // Then overlay the message
                drawMessage(dc);
            }
        } else {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(dc.getWidth() / 2, dc.getHeight() / 3, Graphics.FONT_MEDIUM, 
                "Direct Messaging API\nNot Supported", Graphics.TEXT_JUSTIFY_CENTER);
        }
    }
}