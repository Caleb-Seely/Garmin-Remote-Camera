// File: MainView.mc
// Main UI view implementation

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Timer;

/**
 * MainView class - Handles the main UI display
 * Extends BaseView for common functionality
 */
class MainView extends BaseView {
    var screenShape;
    var cameraIcon;
    var videoIcon;
    var timeIcons = {}; // Dictionary to store time icons
    var timer;
    var lastUpdateTime = 0;
    var subscreen;
    /**
     * Initialize the view
     */
    function initialize() {
        BaseView.initialize();
        timer = new Timer.Timer();
        timeIcons = {}; // Initialize empty dictionary
        System.println("MainView initialized");
    }

    /**
     * Called when the view is about to be shown and dimensions are available.
     * Sets up resources, icons, and starts the update timer.
     * @param dc The device context
     */
    function onLayout(dc) {
        try {
            screenShape = System.getDeviceSettings().screenShape;
            
            // Check if this watch has a subscreen
            subscreen = (WatchUi has :getSubscreen) ? WatchUi.getSubscreen() : null;
            if (subscreen != null) {
                System.println("Subscreen: " + subscreen.toString());
            } else {
                System.println("Subscreen: null");
            }
            
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
        try { 
            var icon0 = WatchUi.loadResource(Rez.Drawables.time_0);
            timeIcons.put("0", icon0);
        } catch(ex) { 
            System.println("Error loading time_0: " + ex.getErrorMessage()); 
        }
        
        try { 
            var icon3 = WatchUi.loadResource(Rez.Drawables.time_3);
            timeIcons.put("3", icon3);
        } catch(ex) { 
            System.println("Error loading time_3: " + ex.getErrorMessage()); 
        }
        
        try { 
            var icon5 = WatchUi.loadResource(Rez.Drawables.time_5);
            timeIcons.put("5", icon5);
        } catch(ex) { 
            System.println("Error loading time_5: " + ex.getErrorMessage()); 
        }
        
        try { 
            var icon10 = WatchUi.loadResource(Rez.Drawables.time_10);
            timeIcons.put("10", icon10);
        } catch(ex) { 
            System.println("Error loading time_10: " + ex.getErrorMessage()); 
        }
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
            if (CountdownManager.isCountdownActive) {
                // Check if countdown has ended
                var remaining = CountdownManager.getRemainingTime();
                if (remaining <= 0) {
                    // Countdown finished
                    CountdownManager.isCountdownActive = false;
                    AppState.lastMessage = "Sending...";
                    AppState.page = 1;
                    AppState.showMessageTimeout = currentTime + 1000;
                }
                // Always request update for countdown to show changing numbers
                WatchUi.requestUpdate();
                return;
            }
            
            // Always update when recording is active to show the time
            if (RecordingManager.isRecordingActive && AppState.page == 2) {
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
     * Draws the main UI showing camera icon and selected timer value
     * @param dc The device context for drawing
     */
    function drawSimpleUI(dc) {
        try {
            var width = dc.getWidth();
            var height = dc.getHeight();
            var centerX = width / 2;
            var centerY = (height / 2) + 10 ;
            var paddingX = width * 0.1;
            var paddingY = width * 0.2;
            
            // Draw the camera icon - now handles subscreen positioning
            drawCameraIcon(dc, width, height, paddingX, paddingY);
            
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
     * @param height Screen height
     * @param paddingX Horizontal padding
     * @param paddingY Vertical padding
     */
    function drawCameraIcon(dc, width, height, paddingX, paddingY) {
        if (cameraIcon != null) {
            var iconWidth = cameraIcon.getWidth();
            var iconHeight = cameraIcon.getHeight();
            
            // Determine position based on subscreen availability
            var cameraIconX, cameraIconY;
            
            if (subscreen != null) {
                // Center the icon in the subscreen
                var subscreenCenterX = (subscreen.x != null) ? 
                    subscreen.x + (subscreen.width / 2) : width / 2;
                var subscreenCenterY = (subscreen.y != null) ? 
                    subscreen.y + (subscreen.height / 2) : height / 2;
                
                cameraIconX = subscreenCenterX;
                cameraIconY = subscreenCenterY;
                
                System.println("Drawing camera icon in subscreen center: " + cameraIconX + "," + cameraIconY);
            } else {
                // Default positioning in top right corner
                cameraIconX = width - paddingX - (iconWidth / 2);
                cameraIconY = paddingY + (iconHeight / 2);
                
                System.println("Drawing camera icon in default position: " + cameraIconX + "," + cameraIconY);
            }
            
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
            timeIcon = timeIcons.get(currentTimeOption);
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
     * Main update function called by the system to redraw the screen
     * Delegates to specific drawing functions based on app state
     * @param dc The device context for drawing
     */
    function onUpdate(dc) {
        try {
            // Clear the background first
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            // Normal UI handling
            if(AppState has :hasDirectMessagingSupport && AppState.hasDirectMessagingSupport) {
                drawSimpleUI(dc);
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