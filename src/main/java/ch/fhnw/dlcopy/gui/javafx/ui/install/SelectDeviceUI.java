package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.utility.checker.DeviceInstallationChecker;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.freedesktop.dbus.exceptions.DBusException;

public class SelectDeviceUI extends View {
    
    private static final Logger LOGGER = Logger.getLogger(SelectDeviceUI.class.getName());
    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();
    
    private final Timer listUpdateTimer = new Timer();
    private SystemSource runningSystemSource;
    
    // some locks to synchronize the Installer, Upgrader and Resetter with their
    // corresponding StorageDeviceAdder
    private Lock installLock = new ReentrantLock();
    
    @FXML private Button btnBack;
    @FXML private Button btnInstall;
    @FXML private ListView<StorageDevice> lvDevices;
    
    public SelectDeviceUI(){  
        
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        
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
        
        lvDevices.setPlaceholder(new Label(stringBundle.getString("install.lvDevices")));
    }
    
    @Override
    protected void setupEventHandlers(){
        
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
        
        btnInstall.setOnAction(event -> {
            
            lvDevices.getSelectionModel().getSelectedItems().forEach(device -> {
                DeviceInstallationChecker deviceInstallationChecker = new DeviceInstallationChecker();
                if (!deviceInstallationChecker.check(device)){
                    showError(deviceInstallationChecker.getErrorMessage());
                }
            });
            
            new Installer(
                runningSystemSource,
                lvDevices.getSelectionModel().getSelectedItems(),
                "Austausch",
                "exFat",
                "ext4",
                new HashMap<String, byte[]>(),
                context,
                0,
                false,
                "",
                1,
                1,
                1,
                false,
                "",
                false,
                "",
                false,
                false,
                DataPartitionMode.READ_WRITE,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                installLock
            );
        });
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(STRINGS.getString("Error"));
        alert.setHeaderText(STRINGS.getString("Error"));
        alert.setContentText(message);
        alert.showAndWait();
    }
}