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
    var subscreen;
    
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
            // Check if this watch has a subscreen
            subscreen = (WatchUi has :getSubscreen) ? WatchUi.getSubscreen() : null;
            if (subscreen != null) {
                System.println("Subscreen: " + subscreen.toString());
            } else {
                System.println("Subscreen: null");
            }
            
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
            
            // Draw the video icon - now handles subscreen positioning
            drawVideoIcon(dc, width, height, paddingX, paddingY);
            
            // Get and display recording time
            var timeStr = RecordingManager.getFormattedRecordingTime();
            
            // Debug output for the time
            var rawTime = RecordingManager.getRecordingElapsedTime();
            System.println("Drawing UI - Raw time: " + rawTime + "s, Formatted: " + timeStr);
            
            // Verify the timing calculation more explicitly
            if (RecordingManager.isRecordingActive) {
                var currentTime = System.getTimer();
                var diffMs = currentTime - RecordingManager.recordingStartTime;
                var realSeconds = (diffMs / 1000).toNumber();
                System.println("TIMING CHECK: diffMs=" + diffMs + "ms, realSeconds=" + realSeconds + "s vs displayed=" + rawTime + "s");
            }
            
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
     * @param height Screen height
     * @param paddingX Horizontal padding
     * @param paddingY Vertical padding
     */
    function drawVideoIcon(dc, width, height, paddingX, paddingY) {
        if (videoIcon != null) {
           var iconWidth = videoIcon.getWidth();
           var iconHeight = videoIcon.getHeight();
           
           // Determine position based on subscreen availability
           var videoIconX, videoIconY;
           
           if (subscreen != null) {
               // Center the icon in the subscreen
               var subscreenCenterX = (subscreen.x != null) ? 
                   subscreen.x + (subscreen.width / 2) : width / 2;
               var subscreenCenterY = (subscreen.y != null) ? 
                   subscreen.y + (subscreen.height / 2) : height / 2;
               
               videoIconX = subscreenCenterX;
               videoIconY = subscreenCenterY;
               
               // Draw only the icon (no text) when using subscreen
               dc.setColor(Graphics.COLOR_RED, Graphics.COLOR_TRANSPARENT);
               dc.drawBitmap(videoIconX - (iconWidth / 2), videoIconY - (iconHeight / 2), videoIcon);
               
               System.println("Drawing video icon in subscreen center: " + videoIconX + "," + videoIconY);
           } else {
               // Default positioning in top right corner with "REC" text
               videoIconX = width - paddingX - (iconWidth / 2);
               videoIconY = paddingY + (iconHeight / 2);
               
               // Draw "REC" text to the left of the icon
               dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
               dc.drawText(videoIconX - (iconWidth / 2) - 5, videoIconY, Graphics.FONT_TINY, "REC", 
                  Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
               
               dc.drawBitmap(videoIconX - (iconWidth / 2), videoIconY - (iconHeight / 2), videoIcon);
               
               System.println("Drawing video icon in default position: " + videoIconX + "," + videoIconY);
           }
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