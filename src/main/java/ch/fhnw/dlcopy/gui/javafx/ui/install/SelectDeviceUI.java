package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

public class SelectDeviceUI extends View {
    
    private List<StorageDevice> devicesRaw;
    
    public SelectDeviceUI(){
        resourcePath = getClass().getResource("/fxml/install/selectdevice.fxml");
    }
    
    protected void initControls(){
        try {
            devicesRaw = DLCopy.getStorageDevices(false, false, "bootDeviceName");
            devicesRaw.forEach(device -> {System.out.println(device);});
        } catch (IOException | DBusException ex) {
            Logger.getLogger(SelectDeviceUI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}