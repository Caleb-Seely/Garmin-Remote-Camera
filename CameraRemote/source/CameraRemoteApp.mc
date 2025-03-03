using Toybox.Application;
using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Communications;

class CameraRemoteApp extends Application.AppBase {
    function initialize() {
        AppBase.initialize();
    }

    // onStart() is called on application start up
    function onStart(state) {
    }

    // onStop() is called when your application is exiting
    function onStop(state) {
    }

    // Return the initial view of your application here
   function getInitialView() {
      return [ new CameraRemoteView(), new CameraRemoteInputDelegate() ];
   }
}

class CameraRemoteView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    // Load your resources here
    function onLayout(dc) {
        setLayout(Rez.Layouts.MainLayout(dc));
    }

    // Update the view
    function onUpdate(dc) {
        // Call the parent onUpdate function to clear the display
        View.onUpdate(dc);

        // Draw background
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();
        
        // Draw title
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(dc.getWidth()/2, dc.getHeight()/4, 
                   Graphics.FONT_MEDIUM, "Camera Remote", 
                   Graphics.TEXT_JUSTIFY_CENTER);
                   
        // Draw instruction
        dc.drawText(dc.getWidth()/2, dc.getHeight()/2, 
                   Graphics.FONT_SMALL, "Press Start to take photo", 
                   Graphics.TEXT_JUSTIFY_CENTER);
    }
}

class CameraRemoteInputDelegate extends WatchUi.InputDelegate {
    function initialize() {
        InputDelegate.initialize();
    }

    // Handle button press events
    function onKey(keyEvent) {
        System.println("Key pressed: " + keyEvent.getKey());
        if (keyEvent.getKey() == WatchUi.KEY_ENTER) {
            // Start button pressed
            System.println("Start button pressed - would take photo");
            WatchUi.requestUpdate();
            return true;
        }
        return false;
    }
}