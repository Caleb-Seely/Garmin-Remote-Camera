//
// Copyright 2015-2016 by Garmin Ltd. or its subsidiaries.
// Subject to Garmin SDK License Agreement and Wearables
// Application Developer Agreement.
//

using Toybox.Application;
using Toybox.Communications;
using Toybox.WatchUi;
using Toybox.System;

// Global variables shared between files
var page = 0;
var strings = ["","","","",""];
var stringsSize = 5;
var phoneMethod = null;
var crashOnMessage = false;
var hasDirectMessagingSupport = true;

// Define the global onPhone function
function onPhone(msg) {
    var i;

    if((crashOnMessage == true) && msg.data.equals("Hi")) {
        msg.length(); // Generates a symbol not found error in the VM
    }

    for(i = (stringsSize - 1); i > 0; i -= 1) {
        strings[i] = strings[i-1];
    }
    strings[0] = msg.data.toString();
    page = 1;

    WatchUi.requestUpdate();
    return true;
}

class CommExample extends Application.AppBase {

    function initialize() {
        Application.AppBase.initialize();

        // Assign the global function to phoneMethod
        phoneMethod = method(:onPhoneHandler);
        
        if(Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(phoneMethod);
            hasDirectMessagingSupport = true;
        } else {
            hasDirectMessagingSupport = false;
        }
    }
    
    // Class method to handle phone messages
    function onPhoneHandler(msg) {
        // Call the global function
        return onPhone(msg);
    }

    // onStart() is called on application start up
    function onStart(state) {
    }

    // onStop() is called when your application is exiting
    function onStop(state) {
    }

    // Return the initial view of your application here
    function getInitialView() {
        return [new CommView(), new CommInputDelegate()];
    }
}