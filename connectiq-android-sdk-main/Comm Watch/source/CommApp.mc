// File: CommApp.mc
//
// Copyright 2015-2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;
using Toybox.Attention;

// Global state shared between files
class AppState {
    static var page = 0;
    static var lastMessage = "";
    static var strings = ["","","","",""];
    static var stringsSize = 5;
    static var selectedIndex = 0;
    static var timeOptions = ["0", "5", "10"];
    static var crashOnMessage = false;
    static var hasDirectMessagingSupport = true;
    static var phoneMethod = null;
    static var showMessageTimeout = 0;
    static const MESSAGE_DISPLAY_TIME = 3; // seconds to show message
}

class CommExample extends Application.AppBase {
    function initialize() {
        Application.AppBase.initialize();

        // Assign method handler
        AppState.phoneMethod = method(:onPhoneHandler);
        
        if(Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(AppState.phoneMethod);
            AppState.hasDirectMessagingSupport = true;
        } else {
            AppState.hasDirectMessagingSupport = false;
        }
    }
    
    // Class method to handle phone messages
    function onPhoneHandler(msg) {
        var i;

        if((AppState.crashOnMessage == true) && msg.data.equals("Hi")) {
            msg.length(); // Generates a symbol not found error in the VM
        }

        // Store the received message
        var messageText = msg.data.toString();
        AppState.lastMessage = messageText;
        
        // Set the show message timeout
        AppState.showMessageTimeout = System.getTimer() + (AppState.MESSAGE_DISPLAY_TIME * 1000);
        
        // Vibrate to notify user
        if (Attention has :vibrate) {
            Attention.vibrate([new Attention.VibeProfile(50, 500)]); // 500ms vibration
        }

        // Store in the history array
        for(i = (AppState.stringsSize - 1); i > 0; i -= 1) {
            AppState.strings[i] = AppState.strings[i-1];
        }
        AppState.strings[0] = messageText;
        
        // Switch to message display
        AppState.page = 1;

        WatchUi.requestUpdate();
        return true;
    }

    // onStart() is called on application start up
    function onStart(state) {
        // Any initialization code
    }

    // onStop() is called when your application is exiting
    function onStop(state) {
        // Any cleanup code
    }

    // Return the initial view of your application here
    function getInitialView() {
        return [new CommView(), new CommInputDelegate()];
    }
}