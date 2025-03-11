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
    var stopwatchIcon;
    var timer;

    function initialize() {
        View.initialize();
        timer = new Timer.Timer();
    }

    function onLayout(dc) {
        screenShape = System.getDeviceSettings().screenShape;
        
        // Load camera and stopwatch icon bitmaps
        // Note: You'll need to add these icon resources to your project
        cameraIcon = WatchUi.loadResource(Rez.Drawables.CameraIcon);
        stopwatchIcon = WatchUi.loadResource(Rez.Drawables.StopwatchIcon);
        
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
    var width = dc.getWidth();
    var height = dc.getHeight();
    var centerX = width / 2;
    var paddingX = width * 0.1;
    var paddingY = width * 0.2;
    // Group the stopwatch and time in the center of the screen
    var stopwatchIconY = height * 0.60;  // Position stopwatch near the middle
    var timeTextY = height * 0.75;       // Position time text below stopwatch
    
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
        dc.drawText(width - paddingX, paddingX, Graphics.FONT_MEDIUM, "CAMERA", Graphics.TEXT_JUSTIFY_RIGHT);
    }
    
    // Draw the stopwatch icon in the center
    if (stopwatchIcon != null) {
        var iconWidth = stopwatchIcon.getWidth();
        var iconHeight = stopwatchIcon.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawBitmap(centerX - (iconWidth / 2), stopwatchIconY - (iconHeight / 2), stopwatchIcon);
    } else {
        // Fallback if icon isn't available
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(centerX, stopwatchIconY, Graphics.FONT_MEDIUM, "TIMER", Graphics.TEXT_JUSTIFY_CENTER);
    }
    
    // Draw the selected time value directly below the stopwatch
    dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
    dc.drawText(centerX, timeTextY, Graphics.FONT_LARGE, AppState.timeOptions[AppState.selectedIndex], Graphics.TEXT_JUSTIFY_CENTER);
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