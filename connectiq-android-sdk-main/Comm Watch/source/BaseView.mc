// File: BaseView.mc
// Base View class with common functionality for all views

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;

/**
 * BaseView class - Base class for all views with common functionality
 */
class BaseView extends WatchUi.View {
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
