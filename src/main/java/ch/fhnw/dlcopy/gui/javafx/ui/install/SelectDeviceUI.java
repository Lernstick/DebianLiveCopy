package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.PartitionState;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.text.MessageFormat;
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.text.Text;
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
    @FXML private ComboBox cmbDataPartitionMode;
    @FXML private ComboBox cmbExchangePartitionMode;
    @FXML private Label lblRequiredDiskspace;
    @FXML private ListView<StorageDevice> lvDevices;
    @FXML private CheckBox chbCopyDataPartition;
    @FXML private CheckBox chbCopyExchangePartition;

    public SelectDeviceUI() {

        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        resourcePath = getClass().getResource("/fxml/install/selectDevice.fxml");
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
    @SuppressWarnings("unchecked") // cmbDataPartitionMode items' type safety does not need validation, as they are the same raw type.
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


        // use running system as initial value
        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(runningSystemSource.getSystemSize());
        String sizeString
                = LernstickFileTools.getDataVolumeString(enlargedSystemSize, 1);
        lblRequiredDiskspace.setText(MessageFormat.format(
                stringBundle.getString("install.select_install_target_storage_media"),
                sizeString));

        ObservableList<DataPartionModeEntry> dpmeList = FXCollections.observableArrayList();
        dpmeList.add(new DataPartionModeEntry(1, "install.dataPartitionModeRW"));
        dpmeList.add(new DataPartionModeEntry(2, "install.dataPartitionModeR"));
        dpmeList.add(new DataPartionModeEntry(3, "install.dataPartitionModeN"));
        cmbDataPartitionMode.setItems(dpmeList);
        cmbDataPartitionMode.getSelectionModel().selectFirst();

        ObservableList<String> epmeList = FXCollections.observableArrayList();
        epmeList.addAll(DLCopy.EXCHANGE_PARTITION_FS);
        cmbExchangePartitionMode.setItems(epmeList);
        cmbExchangePartitionMode.getSelectionModel().selectFirst();

        setTooltip(cmbDataPartitionMode, stringBundle.getString("global.tooltip.dataPartitionMode"));
        setTooltip(lblRequiredDiskspace, stringBundle.getString("install.tooltip.requiredDiskSpace"));
        setTooltip(chbCopyDataPartition, stringBundle.getString("install.tooltip.copyDataPartition"));
        setTooltip(chbCopyExchangePartition, stringBundle.getString("install.tooltip.copyExchangePartition"));
    }

    @Override
    protected void setupEventHandlers() {
        btnInstall.setOnAction(event ->
            showInstallConfirmation(stringBundle.getString("install.installWarning"))
        );
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
    }

    private void install() {
        new Installer(
            runningSystemSource,    // the system source
            lvDevices.getSelectionModel().getSelectedItems(),   // the list of StorageDevices to install
            "Austausch",     // the label of the exchange partition
            cmbExchangePartitionMode.getSelectionModel().getSelectedItem().toString(),    // the file system of the exchange partition
            "ext4", // the file system of the data partition
            new HashMap<String, byte[]>(),  // a global digest cache for speeding up repeated file checks
            InstallControler.getInstance(),    // the DLCopy GUI
            0,  // the size of the exchange partition
            chbCopyExchangePartition.isSelected(),  // if the exchange partition should be copied
            "", // the auto numbering pattern
            1,  // the auto numbering start value
            1,  // the auto numbering increment
            1,  // the minimal number of digits to use for auto numbering
            false,  // f the data partition should be encrypted with a personal password
            "", // the personal password for data partition encryption
            false,  // if the data partition should be encrypted with a secondary password
            "", // the secondary password for data partition encryption
            false,  // if the data partition should be filled with random data before formatting
            chbCopyDataPartition.isSelected(),  // if the data partition should be copied
            getDataPartitionMode(),   // the mode of the data partition to set in the bootloaders config
            null,   // the device to transfer data from or null, if no data should be transferred
            false,  // if the exchange partition should be transferred
            false,  // if the home folder should be transferred
            false,  // if the network settings should be transferred
            false,  // if the printer settings should be transferred
            false,  // if the firewall settings should be transferred
            false,  // if copies should be checked for errors
            installLock // the lock to aquire before executing in background
        ).execute();
    }

    public void updateInstallSelectionCountAndExchangeInfo() {
        // check all selected storage devices
        long minOverhead = Long.MAX_VALUE;
        boolean exchange = true;
        ObservableList<StorageDevice> selectedIndices = lvDevices.getSelectionModel().getSelectedItems();

        if (selectedIndices.isEmpty()) {
            minOverhead = 0;
            exchange = false;
        } else {
            if (runningSystemSource == null) {
                LOGGER.warning("No valid system source selected!");
            } else {
                long enlargedSystemSize = DLCopy.getEnlargedSystemSize(
                        runningSystemSource.getSystemSize());

                for (StorageDevice device : selectedIndices) {
                    long overhead = device.getSize()
                            - (DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)
                            - enlargedSystemSize;

                    minOverhead = Math.min(minOverhead, overhead);

                    PartitionState partitionState = DLCopy.getPartitionState(
                            device.getSize(),
                            (DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)
                            + enlargedSystemSize);

                    if (partitionState != PartitionState.EXCHANGE) {
                        exchange = false;
                        break; // for
                    }
                }
            }
        }

        // TODO: update all other parts of the UI
        // see ch.fhnw.dlcopy.gui.swing.InstallerPanels
    }

    private void setSystemSource(SystemSource systemSource) {
        // early return
        if (systemSource == null) {
            return;
        }

        // update source dependend strings and states
        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(systemSource.getSystemSize());
        String sizeString
                = LernstickFileTools.getDataVolumeString(enlargedSystemSize, 1);

        lblRequiredDiskspace.setText(MessageFormat.format(
                stringBundle.getString("install.select_install_target_storage_media"),
                sizeString));

        // TODO: define fxid for device list legend
        //lblRequiredDiskspace.setText(MessageFormat.format(
        //        stringBundle.getString("install.system_definition"),
        //        sizeString));

        // TODO: update all other parts of the UI
        // see ch.fhnw.dlcopy.gui.swing.InstallerPanels
    }

    private void showInstallConfirmation(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText(stringBundle.getString("install.warningDataLoss"));
        alert.setTitle(stringBundle.getString("global.confirm"));
        alert.setContentText(message);
        Text text = new Text(message);
        text.setWrappingWidth(300);
        alert.getDialogPane().setContent(text);
        alert.showAndWait()
            .filter(response -> response == ButtonType.OK)
            .ifPresent(response -> install());
    }

    private DataPartitionMode getDataPartitionMode() {
        String cmbValue = cmbDataPartitionMode.getValue().toString();
        if (cmbValue.equals(stringBundle.getString("install.dataPartitionModeRW"))){ return DataPartitionMode.READ_WRITE;}
        else if (cmbValue.equals(stringBundle.getString("install.dataPartitionModeR"))){ return DataPartitionMode.READ_ONLY;}
        else { return DataPartitionMode.NOT_USED;}
    }
}
