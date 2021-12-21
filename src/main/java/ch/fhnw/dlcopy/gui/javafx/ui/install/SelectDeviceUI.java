package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.StorageDevice;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;

public class SelectDeviceUI extends View {
    
    private final Timer listUpdateTimer = new Timer();
    
    @FXML private Button btnBack;
    @FXML private Button btnInstall;
    @FXML private ListView<StorageDevice> lvDevices;
    
    public SelectDeviceUI(){
        resourcePath = getClass().getResource("/fxml/install/selectdevice.fxml");
    }

    @Override
    /**
     * This function is called, when the view should be deinitalized.
     * It has to be called manually!
     */
    public void deinitialize() {
        listUpdateTimer.cancel();
    }
    
    @Override
    protected void initControls(){
        TimerTask listUpdater = new TimerTask(){
            @Override
            public void run() {
                try {
                    List<StorageDevice> pluggedDevices = DLCopy.getStorageDevices(false, false, "bootDeviceName");
                    List<StorageDevice> removedDevices = new ArrayList<>();
                    List<StorageDevice> addedDevices = new ArrayList<>();
                    for (StorageDevice device : pluggedDevices) {
                        if(!lvDevices.getItems().contains(device)){
                            // Plugged deice is not shown yet
                            addedDevices.add(device);
                        }
                    }
                    for (StorageDevice device : lvDevices.getItems()) {
                        if (!pluggedDevices.contains(device)) {
                            // Shown device is not plugged anymore
                            removedDevices.add(device);
                        }
                    }
                    Platform.runLater(() -> {
                        lvDevices.getItems().removeAll(removedDevices);
                        lvDevices.getItems().addAll(addedDevices);
                    });
                } catch (Exception ex) {
                    Logger.getLogger(SelectDeviceUI.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        listUpdateTimer.scheduleAtFixedRate(listUpdater, 0, 1000L); // Starts the `lisstUpdater`-task each 1000ms (1sec)
    }
    
    @Override
    protected void setupEventHandlers(){
        
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
    }
}