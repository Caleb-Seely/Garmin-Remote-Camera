// File: CountdownView.mc
// Handles countdown display functionality

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;

/**
 * CountdownView class - Handles drawing countdown displays
 * Extends BaseView for common functionality
 */
class CountdownView extends BaseView {
    /**
     * Initialize the view
     */
    function initialize() {
        BaseView.initialize();
        System.println("CountdownView initialized");
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
            if (CountdownManager.isCountdownActive) {
                dc.setColor(Graphics.COLOR_YELLOW, Graphics.COLOR_TRANSPARENT);
                var font = Graphics.FONT_NUMBER_THAI_HOT;
                var timeStr = CountdownManager.getFormattedRemainingTime();
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
     * Main update function called by the system to redraw the screen
     * @param dc The device context for drawing
     */
    function onUpdate(dc) {
        try {
            // Clear the background first
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            if(AppState has :hasDirectMessagingSupport && AppState.hasDirectMessagingSupport) {
                drawCountdownUI(dc);
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
}
