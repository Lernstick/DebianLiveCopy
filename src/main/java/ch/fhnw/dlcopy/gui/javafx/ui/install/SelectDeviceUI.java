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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import org.freedesktop.dbus.exceptions.DBusException;

public class SelectDeviceUI extends View {

    private static final Logger LOGGER = Logger.getLogger(SelectDeviceUI.class.getName());
    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();

    private final Timer listUpdateTimer = new Timer();
    private SystemSource runningSystemSource;
    private int exchangePartitionSize = -1;
    private boolean showHarddisks = false;

    // some locks to synchronize the Installer, Upgrader and Resetter with their
    // corresponding StorageDeviceAdder
    private Lock installLock = new ReentrantLock();

    @FXML private Button btnBack;
    @FXML private Button btnInstall;
    @FXML private Button btnDataPartitionShowPersonalPassword;
    @FXML private Button btnDataPartitionShowSecondaryPassword;
    @FXML private ComboBox cmbDataPartitionMode;
    @FXML private ComboBox cmbExchangePartitionFilesystem;
    @FXML private Label lblRequiredDiskspace;
    @FXML private ListView<StorageDevice> lvDevices;
    @FXML private CheckBox chbCheckCopies;
    @FXML private CheckBox chbCopyDataPartition;
    @FXML private CheckBox chbCopyExchangePartition;
    @FXML private CheckBox chbShowHarddisk;
    @FXML private CheckBox chbDataPartitionPersonalPassword;
    @FXML private CheckBox chbDataPartitionSecondaryPassword;
    @FXML private CheckBox chbDataPartitionOverwrite;
    @FXML private PasswordField pfDataPartitionPersonalPassword;
    @FXML private PasswordField pfDataPartitionSecondaryPassword;
    @FXML private TextField tfExchangePartitionSize;

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
                    List<StorageDevice> pluggedDevices = DLCopy.getStorageDevices(showHarddisks, false, "bootDeviceName");
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
                } catch (IOException | DBusException ex) {
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

        setTooltip(chbCopyExchangePartition, stringBundle.getString("install.tooltip.copyExchangePartition"));
        setTooltip(cmbDataPartitionMode, stringBundle.getString("global.tooltip.dataPartitionMode"));
        setTooltip(lblRequiredDiskspace, stringBundle.getString("install.tooltip.requiredDiskSpace"));
        setTooltip(chbCopyDataPartition, stringBundle.getString("install.tooltip.copyDataPartition"));
        setTooltip(chbDataPartitionPersonalPassword, stringBundle.getString("install.tooltip.encryption"));
        setTooltip(chbDataPartitionOverwrite, stringBundle.getString("install.tooltip.randomFill"));
        setTooltip(chbDataPartitionSecondaryPassword, stringBundle.getString("install.tooltip.secondaryPassword"));

        chbCopyDataPartition.setDisable(runningSystemSource.getDataPartition() == null);
        chbCopyExchangePartition.setDisable(!runningSystemSource.hasExchangePartition());

        ObservableList<DataPartitionModeEntry> dpmeList = FXCollections.observableArrayList();
        dpmeList.add(new DataPartitionModeEntry(DataPartitionMode.READ_WRITE, "install.dataPartitionModeRW"));
        dpmeList.add(new DataPartitionModeEntry(DataPartitionMode.READ_ONLY, "install.dataPartitionModeR"));
        dpmeList.add(new DataPartitionModeEntry(DataPartitionMode.NOT_USED, "install.dataPartitionModeN"));
        cmbDataPartitionMode.setItems(dpmeList);
        cmbDataPartitionMode.getSelectionModel().selectFirst();

        ObservableList<String> epmeList = FXCollections.observableArrayList();
        epmeList.addAll(DLCopy.EXCHANGE_PARTITION_FS);
        cmbExchangePartitionFilesystem.setItems(epmeList);
        cmbExchangePartitionFilesystem.getSelectionModel().selectFirst();

        // Install Button should be disabled, till a valid exchangePartition size is choosen.
        btnInstall.setDisable(true);

        chbDataPartitionPersonalPassword.setDisable(false);
        pfDataPartitionPersonalPassword.setDisable(true);
        chbDataPartitionSecondaryPassword.setDisable(true);
        pfDataPartitionSecondaryPassword.setDisable(true);
        chbDataPartitionOverwrite.setDisable(true);
        btnDataPartitionShowPersonalPassword.setDisable(true);
        btnDataPartitionShowSecondaryPassword.setDisable(true);
    }

    @Override
    protected void setupBindings() {
        tfExchangePartitionSize.textProperty().addListener(event -> {
            exchangePartitionSize = valExPartSize(tfExchangePartitionSize.getText().trim());
            btnInstall.setDisable(exchangePartitionSize < 0);
        });
    }

    @Override
    protected void setupEventHandlers() {
        btnInstall.setOnAction(event -> {
            if (valChb(chbDataPartitionPersonalPassword) && pfDataPartitionPersonalPassword.getPromptText().length() == 0) {
                showError(stringBundle.getString("install.error.noPassword"));
                return;
            }
            if (valChb(chbDataPartitionSecondaryPassword) && pfDataPartitionSecondaryPassword.getPromptText().length() == 0) {
                showError(stringBundle.getString("install.error.noPassword"));
                return;
            }
            showInstallConfirmation(stringBundle.getString("install.installWarning"));
        });
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
        chbShowHarddisk.setOnAction(event -> {
            showHarddisks = valChb(chbShowHarddisk);
        });
        chbDataPartitionPersonalPassword.setOnAction(event -> {
            // other options are only available if encryption is enabled
            boolean notEnc = !valChb(chbDataPartitionPersonalPassword);
            chbDataPartitionOverwrite.setDisable(notEnc);
            pfDataPartitionPersonalPassword.setDisable(notEnc);
            btnDataPartitionShowPersonalPassword.setDisable(notEnc);
            chbDataPartitionSecondaryPassword.setDisable(notEnc);
            boolean noSnd = !valChb(chbDataPartitionSecondaryPassword);
            pfDataPartitionSecondaryPassword.setDisable(noSnd);
            btnDataPartitionShowSecondaryPassword.setDisable(noSnd);
        });
        chbDataPartitionSecondaryPassword.setOnAction(event -> {
            boolean notEnc = !valChb(chbDataPartitionPersonalPassword);
            boolean noSnd = !valChb(chbDataPartitionSecondaryPassword);
            pfDataPartitionSecondaryPassword.setDisable(notEnc || noSnd);
            btnDataPartitionShowSecondaryPassword.setDisable(notEnc || noSnd);
        });
    }

    private void install() {
        // Register the selected devices for the installation report
        InstallControler installcontroller = InstallControler.getInstance(context);
        List<StorageDevice> devices = lvDevices.getSelectionModel().getSelectedItems();
        installcontroller.createInstallationList(devices, 1, 1);
        
        new Installer(
            runningSystemSource,    // the system source
            devices,   // the list of StorageDevices to install
            "Austausch",     // the label of the exchange partition
            cmbExchangePartitionFilesystem.getValue().toString(),    // the file system of the exchange partition
            "ext4", // the file system of the data partition
            new HashMap<String, byte[]>(),  // a global digest cache for speeding up repeated file checks
            // Register the InstallControler as Callback-Class
            installcontroller,    // the DLCopyGUI
            exchangePartitionSize,  // the size of the exchange partition
            valChb(chbCopyExchangePartition),  // if the exchange partition should be copied
            "", // the auto numbering pattern
            1,  // the auto numbering start value
            1,  // the auto numbering increment
            1,  // the minimal number of digits to use for auto numbering
            valChb(chbDataPartitionPersonalPassword),  // if the data partition should be encrypted with a personal password
            pfDataPartitionPersonalPassword.getPromptText(), // the personal password for data partition encryption
            valChb(chbDataPartitionSecondaryPassword),  // if the data partition should be encrypted with a secondary password
            pfDataPartitionSecondaryPassword.getPromptText(), // the secondary password for data partition encryption
            valChb(chbDataPartitionOverwrite),  // if the data partition should be filled with random data before formatting
            valChb(chbCopyDataPartition),  // if the data partition should be copied
            getDataPartitionMode(),   // the mode of the data partition to set in the bootloaders config
            null,   // the device to transfer data from or null, if no data should be transferred
            false,  // if the exchange partition should be transferred
            false,  // if the home folder should be transferred
            false,  // if the network settings should be transferred
            false,  // if the printer settings should be transferred
            false,  // if the firewall settings should be transferred
            valChb(chbCheckCopies),  // if copies should be checked for errors
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

    /**
     * Validates a checkbox filed.
     *
     * @param chb
     * @return true if selected and enabled.
     */
    private boolean valChb(CheckBox chb) {
        return chb.isSelected() && !chb.isDisabled();
    }

    /**
     * Validate exchange partition size.
     *
     * @param size
     * @return positive number if valid, else -1
     */
    private int valExPartSize(String size) {
        try {
            return Integer.parseInt(size);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(stringBundle.getString("error.error"));
        alert.setHeaderText(stringBundle.getString("error.error"));
        alert.setContentText(message);
        alert.showAndWait();
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
        DataPartitionModeEntry selection = (DataPartitionModeEntry) cmbDataPartitionMode.getValue();
        return selection.getMode();
    }
}
