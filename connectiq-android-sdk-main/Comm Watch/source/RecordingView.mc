// File: RecordingView.mc
// Handles recording display functionality

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;

/**
 * RecordingView class - Handles drawing recording displays
 * Extends BaseView for common functionality
 */
class RecordingView extends BaseView {
    var videoIcon;
    
    /**
     * Initialize the view
     */
    function initialize() {
        BaseView.initialize();
        System.println("RecordingView initialized");
    }
    
    /**
     * Called when the view is about to be shown and dimensions are available.
     * @param dc The device context
     */
    function onLayout(dc) {
        try {
            // Load video icon
            videoIcon = WatchUi.loadResource(Rez.Drawables.VideoIcon);
        } catch(ex) {
            System.println("Error in RecordingView.onLayout: " + ex.getErrorMessage());
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
            var timeStr = RecordingManager.getFormattedRecordingTime();
            
            // Debug output for the time
            var rawTime = RecordingManager.getRecordingElapsedTime();
            System.println("Drawing UI - Raw time: " + rawTime + "s, Formatted: " + timeStr);
            
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
     * Main update function called by the system to redraw the screen
     * @param dc The device context for drawing
     */
    function onUpdate(dc) {
        try {
            // Clear the background first
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            if(AppState has :hasDirectMessagingSupport && AppState.hasDirectMessagingSupport) {
                drawRecordingUI(dc);
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
