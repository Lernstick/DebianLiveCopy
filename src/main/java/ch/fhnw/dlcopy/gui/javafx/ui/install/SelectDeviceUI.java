package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
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
    
    public SelectDeviceUI() {
        
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

    /**
     * This function is called, when the view should be deinitalized.
     * It has to be called manually!
     */
    @Override
    public void deinitialize() {
        listUpdateTimer.cancel();
    }
    
    @Override
    protected void initControls() {
        TimerTask listUpdater = new TimerTask() {
            @Override
            public void run() {
                try {
                    List<StorageDevice> pluggedDevices = DLCopy.getStorageDevices(false, false, "bootDeviceName");
                    List<StorageDevice> removedDevices = new ArrayList<>();
                    List<StorageDevice> addedDevices = new ArrayList<>();
                    for (StorageDevice device : pluggedDevices) {
                        if(!lvDevices.getItems().contains(device)) {
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
    protected void setupEventHandlers() {
        
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
        
        btnInstall.setOnAction(event -> {
            
            new Installer(
                runningSystemSource,    // the system source
                lvDevices.getSelectionModel().getSelectedItems(),   // the list of StorageDevices to install
                "Austausch",     // the label of the exchange partition
                "exFat",    // the file system of the exchange partition
                "ext4", // the file system of the data partition
                new HashMap<String, byte[]>(),  // a global digest cache for speeding up repeated file checks
                context,    // the DLCopy GUI
                0,  // the size of the exchange partition
                false,  // if the exchange partition should be copied
                "", // the auto numbering pattern
                1,  // the auto numbering start value
                1,  // the auto numbering increment
                1,  // the minimal number of digits to use for auto numbering
                false,  // f the data partition should be encrypted with a personal password
                "", // the personal password for data partition encryption
                false,  // if the data partition should be encrypted with a secondary password
                "", // the secondary password for data partition encryption
                false,  // if the data partition should be filled with random data before formatting
                false,  // if the data partition should be copied
                DataPartitionMode.READ_WRITE,   // the mode of the data partition to set in the bootloaders config
                null,   // the device to transfer data from or null, if no data should be transferred
                false,  // if the exchange partition should be transferred
                false,  // if the home folder should be transferred
                false,  // if the network settings should be transferred
                false,  // if the printer settings should be transferred
                false,  // if the firewall settings should be transferred
                false,  // if copies should be checked for errors
                installLock // the lock to aquire before executing in background
            );
            context.setScene(new InstallationReportUI());
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