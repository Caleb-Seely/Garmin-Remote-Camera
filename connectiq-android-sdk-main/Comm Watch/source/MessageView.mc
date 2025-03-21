// File: MessageView.mc
// Handles message display functionality

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;

/**
 * MessageView class - Handles drawing message displays
 * Extends BaseView for common functionality
 */
class MessageView extends BaseView {
    /**
     * Initialize the view
     */
    function initialize() {
        BaseView.initialize();
        System.println("MessageView initialized");
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
     * Main update function called by the system to redraw the screen
     * @param dc The device context for drawing
     */
    function onUpdate(dc) {
        try {
            // Clear the background first
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            if(AppState has :hasDirectMessagingSupport && AppState.hasDirectMessagingSupport) {
                drawMessage(dc);
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
