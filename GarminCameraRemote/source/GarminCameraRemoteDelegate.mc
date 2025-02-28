import Toybox.Lang;
import Toybox.WatchUi;

class GarminCameraRemoteDelegate extends WatchUi.BehaviorDelegate {

    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onMenu() as Boolean {
        WatchUi.pushView(new Rez.Menus.MainMenu(), new GarminCameraRemoteMenuDelegate(), WatchUi.SLIDE_UP);
        return true;
    }

}