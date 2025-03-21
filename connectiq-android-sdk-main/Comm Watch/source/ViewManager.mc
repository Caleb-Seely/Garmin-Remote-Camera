// File: ViewManager.mc
// Manages which view is displayed based on app state

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.System;
using Toybox.Timer;

/**
 * ViewManager class - Manages which view should be displayed based on app state
 * This class serves as a central manager for view switching without needing to
 * use WatchUi push/pop operations, which can be expensive and complex.
 */
class ViewManager extends BaseView {
    // Instance references to our views to avoid recreating them
    private var mainView;
    private var messageView;
    private var countdownView;
    private var recordingView;
    
    // Timer for periodic updates
    private var updateTimer;
    
    /**
     * Initialize the view manager
     */
    function initialize() {
        BaseView.initialize();
        
        // Create instances of all our views
        mainView = new MainView();
        messageView = new MessageView();
        countdownView = new CountdownView();
        recordingView = new RecordingView();
        
        // Set up the update timer
        updateTimer = new Timer.Timer();
        startUpdateTimer();
        
        System.println("ViewManager initialized");
    }
    
    /**
     * Starts the update timer to ensure regular UI updates, especially for the recording view
     */
    function startUpdateTimer() {
        try {
            // Stop any existing timer first to prevent duplicates
            if (updateTimer != null) {
                updateTimer.stop();
            }
            
            // Start a new timer that fires every 100ms for more frequent updates
            updateTimer.start(method(:onTimerUpdate), 100, true);
            System.println("ViewManager update timer started with 100ms interval");
        } catch(ex) {
            System.println("Error starting ViewManager timer: " + ex.getErrorMessage());
            
            // Try to recover by creating a new timer
            try {
                updateTimer = new Timer.Timer();
                updateTimer.start(method(:onTimerUpdate), 100, true);
                System.println("ViewManager recovery timer started");
            } catch (ex2) {
                System.println("Fatal error creating recovery timer: " + ex2.getErrorMessage());
            }
        }
    }
    
    /**
     * Timer callback that ensures UI updates happen regularly
     */
    function onTimerUpdate() as Void {
        try {
            // Force UI update every time this timer fires when on recording screen
            // Critical for ensuring the timer display updates
            if (RecordingManager.isRecordingActive || AppState.page == 2) {
                // Force a call to get elapsed time to ensure it's updated
                var elapsed = RecordingManager.getRecordingElapsedTime();
                
                // Critical - this forces a redraw of the screen
                WatchUi.requestUpdate();
            }
        } catch(ex) {
            System.println("Error in ViewManager timer: " + ex.getErrorMessage());
            
            // Try to restart the timer if it fails
            try {
                startUpdateTimer();
            } catch (ex2) {
                System.println("Could not restart timer: " + ex2.getErrorMessage());
            }
        }
    }
    
    /**
     * Called when the view is about to be shown and dimensions are available
     * @param dc The device context
     */
    function onLayout(dc) {
        try {
            // Layout all our sub-views
            mainView.onLayout(dc);
            messageView.onLayout(dc);
            countdownView.onLayout(dc);
            recordingView.onLayout(dc);
        } catch (ex) {
            System.println("Error in ViewManager.onLayout: " + ex.getErrorMessage());
        }
    }
    
    /**
     * Main update function called by the system to redraw the screen
     * Delegates to the appropriate view based on app state
     * @param dc The device context for drawing
     */
    function onUpdate(dc) {
        try {
            // Clear the background first
            dc.setColor(Graphics.COLOR_TRANSPARENT, Graphics.COLOR_BLACK);
            dc.clear();
            
            // If device doesn't support direct messaging, show that screen
            if (!AppState.hasDirectMessagingSupport) {
                drawApiNotSupportedScreen(dc);
                return;
            }
            
            // Decide which view to show based on app state
            // Priority: Countdown > Recording > Message > Main
            
            // Check if countdown is active - this takes highest priority
            if (CountdownManager.isCountdownActive) {
                countdownView.onUpdate(dc);
                return;
            }
            
            // Check if recording is active
            if (RecordingManager.isRecordingActive && AppState.page == 2) {
                recordingView.onUpdate(dc);
                return;
            }
            
            // Message display has next priority
            if (AppState.page == 1) {
                messageView.onUpdate(dc);
                return;
            }
            
            // Default to main view for all other states
            mainView.onUpdate(dc);
        } catch (ex) {
            // Catch any drawing errors to prevent crashes
            System.println("Error in ViewManager.onUpdate: " + ex.getErrorMessage());
            
            // Fall back to a very simple screen
            drawErrorScreen(dc, "Display Error");
        }
    }
}
