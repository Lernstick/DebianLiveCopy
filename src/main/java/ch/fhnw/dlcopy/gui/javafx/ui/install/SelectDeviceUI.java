package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.gui.javafx.NumericTextField;
import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.MEGA;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.IsoSystemSource;
import ch.fhnw.dlcopy.PartitionSizes;
import ch.fhnw.dlcopy.PartitionState;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.exceptions.NoExecutableExtLinuxException;
import ch.fhnw.dlcopy.exceptions.NoExtLinuxException;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.util.converter.NumberStringConverter;
import org.freedesktop.dbus.exceptions.DBusException;

public class SelectDeviceUI extends View {

    private static final Logger LOGGER = Logger.getLogger(SelectDeviceUI.class.getName());
    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();

    private final Timer listUpdateTimer = new Timer();
    private SystemSource runningSystemSource;
    private SystemSource isoSystemSource;
    private boolean showHarddisks = false;
    private ObservableList<StorageDevice> selectedStds;
    private LongProperty exchangePartitionSize = new SimpleLongProperty();

    // some locks to synchronize the Installer, Upgrader and Resetter with their
    // corresponding StorageDeviceAdder
    private Lock installLock = new ReentrantLock();

    @FXML private Button btnBack;
    @FXML private Button btnInstall;
    @FXML private Button btnDataPartitionShowPersonalPassword;
    @FXML private Button btnDataPartitionShowSecondaryPassword;
    @FXML private ComboBox cmbDataPartitionFilesystem;
    @FXML private ComboBox cmbDataPartitionMode;
    @FXML private ComboBox cmbExchangePartitionFilesystem;
    @FXML private Label lblRequiredDiskspace;
    @FXML private Label lblFilesystem;
    @FXML private ListView<StorageDevice> lvDevices;
    @FXML private CheckBox chbCheckCopies;
    @FXML private CheckBox chbCopyDataPartition;
    @FXML private CheckBox chbCopyExchangePartition;
    @FXML private CheckBox chbShowHarddisk;
    @FXML private CheckBox chbDataPartitionPersonalPassword;
    @FXML private CheckBox chbDataPartitionSecondaryPassword;
    @FXML private CheckBox chbDataPartitionOverwrite;
    @FXML private CheckBox chbExchangePartition;
    @FXML private CheckBox chbHomeFolder;
    @FXML private CheckBox chbNetworkSettings;
    @FXML private CheckBox chbPrinterSettings;
    @FXML private CheckBox chbFirewallSettings;
    @FXML private HBox hbDevices;
    @FXML private HBox hbTarget;
    @FXML private PasswordField pfDataPartitionPersonalPassword;
    @FXML private PasswordField pfDataPartitionSecondaryPassword;
    @FXML private Slider slExchangePartitionSize;
    @FXML private TextField tfExchangePartitionSize;
    @FXML private GridPane gpFilesystem;
    @FXML private RadioButton rdbCurrentSystem;
    @FXML private RadioButton rdbIsoImage;
    @FXML private TabPane tpInstallDetails;
    @FXML private TextField tfPrefixText;
    @FXML private NumericTextField tfStartPattern;
    @FXML private NumericTextField tfSteps;
    @FXML private TextField tfISODirectory;



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
                        if (!lvDevices.getItems().contains(device)) {
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

        addToolTip(chbCopyExchangePartition, stringBundle.getString("install.tooltip.copyExchangePartition"));
        addToolTip(cmbDataPartitionMode, stringBundle.getString("global.tooltip.dataPartitionMode"));
        addToolTip(lblRequiredDiskspace, stringBundle.getString("install.tooltip.requiredDiskSpace"));
        addToolTip(chbCopyDataPartition, stringBundle.getString("install.tooltip.copyDataPartition"));
        addToolTip(chbDataPartitionPersonalPassword, stringBundle.getString("install.tooltip.encryption"));
        addToolTip(chbDataPartitionOverwrite, stringBundle.getString("install.tooltip.randomFill"));
        addToolTip(chbDataPartitionSecondaryPassword, stringBundle.getString("install.tooltip.secondaryPassword"));

        chbCopyDataPartition.setDisable(runningSystemSource.getDataPartition() == null);
        chbCopyExchangePartition.setDisable(!runningSystemSource.hasExchangePartition());

        ObservableList<String> dpfsList = FXCollections.observableArrayList();
        dpfsList.addAll(DLCopy.DATA_PARTITION_FS);
        cmbDataPartitionFilesystem.setItems(dpfsList);
        cmbDataPartitionFilesystem.getSelectionModel().selectLast();

        ObservableList<DataPartitionModeEntry> dpmeList = FXCollections.observableArrayList();
        dpmeList.add(new DataPartitionModeEntry(DataPartitionMode.READ_WRITE, "install.dataPartitionModeRW"));
        dpmeList.add(new DataPartitionModeEntry(DataPartitionMode.READ_ONLY, "install.dataPartitionModeR"));
        dpmeList.add(new DataPartitionModeEntry(DataPartitionMode.NOT_USED, "install.dataPartitionModeN"));
        cmbDataPartitionMode.setItems(dpmeList);
        cmbDataPartitionMode.getSelectionModel().selectFirst();

        ObservableList<String> epfsList = FXCollections.observableArrayList();
        epfsList.addAll(DLCopy.EXCHANGE_PARTITION_FS);
        cmbExchangePartitionFilesystem.setItems(epfsList);
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

        lvDevices.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        lvDevices.getSelectionModel().selectedItemProperty().addListener(
                (ObservableValue<? extends StorageDevice> ov, StorageDevice old_val, StorageDevice new_val) -> {
             selectedStds = lvDevices.getSelectionModel().getSelectedItems();
        });
        lvDevices.setCellFactory(cell -> {
            return new DeviceCell(
                    new SimpleLongProperty(DLCopy.EFI_PARTITION_SIZE * MEGA),
                    exchangePartitionSize,
                    new SimpleLongProperty(runningSystemSource.getSystemSize()),
                    lvDevices.widthProperty()
            );
        });
        btnInstall.setDisable(false);
   }

    @Override
    protected void setupBindings() {
        
        // Enable install button, when exchange partition is not 0
        btnInstall.disableProperty().bind(
                exchangePartitionSize.lessThan(0)
        );
        
        // Bind the textinput and the slider to the same value
        tfExchangePartitionSize.textProperty().bindBidirectional(exchangePartitionSize, new NumberStringConverter());
        slExchangePartitionSize.valueProperty().bindBidirectional(exchangePartitionSize);
    }

    @Override
    protected void setupEventHandlers() {
        btnInstall.setOnAction(event -> {
            try {
                if (!checkSelection(selectedStds)) {
                    return;
                }
                install(selectedStds);
            } catch (DBusException|IOException e) {
                LOGGER.log(Level.WARNING, e.getLocalizedMessage());
            }
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
        btnDataPartitionShowPersonalPassword.setOnMousePressed(event -> {
            pfDataPartitionPersonalPassword.setPromptText(pfDataPartitionPersonalPassword.getText());
            pfDataPartitionPersonalPassword.setText("");
        });
        btnDataPartitionShowPersonalPassword.setOnMouseReleased(event -> {
            pfDataPartitionPersonalPassword.setText(pfDataPartitionPersonalPassword.getPromptText());
            pfDataPartitionPersonalPassword.setPromptText("");
        });
        btnDataPartitionShowSecondaryPassword.setOnMousePressed(event -> {
            pfDataPartitionSecondaryPassword.setPromptText(pfDataPartitionSecondaryPassword.getText());
            pfDataPartitionSecondaryPassword.setText("");
        });
        btnDataPartitionShowSecondaryPassword.setOnMouseReleased(event -> {
            pfDataPartitionSecondaryPassword.setText(pfDataPartitionSecondaryPassword.getPromptText());
            pfDataPartitionSecondaryPassword.setPromptText("");
        });
        lblFilesystem.hoverProperty().addListener((value, hadFocus, hasFocus) -> {
            if (hasFocus) {
                gpFilesystem.setVisible(true);
            }
            if (hadFocus){
                gpFilesystem.setVisible(false);
            }
        });
        rdbCurrentSystem.setOnAction(event -> {
            hbDevices.setVisible(true);
            hbTarget.setVisible(true);
            tpInstallDetails.setVisible(true);
            tfISODirectory.setDisable(true);

        });
        rdbIsoImage.setOnAction(event -> {
            hbDevices.setVisible(false);
            hbTarget.setVisible(false);
            tpInstallDetails.setVisible(false);
            tfISODirectory.setDisable(false);
        });
        tfISODirectory.setOnAction(event -> {
            selectISO();
        });
        tfISODirectory.setOnMouseClicked(event -> {
            selectISO();
        });
    }

    private void install(ObservableList<StorageDevice> devices) {
        // Register the selected devices for the installation report
        InstallControler installcontroller = InstallControler.getInstance(context);
        installcontroller.createInstallationList(selectedStds, 1, 1);
        new Installer(
            runningSystemSource,    // the system source
            selectedStds,   // the list of StorageDevices to install
            "Austausch",     // the label of the exchange partition
            cmbExchangePartitionFilesystem.getValue().toString(),    // the file system of the exchange partition
            cmbDataPartitionFilesystem.getValue().toString(), // the file system of the data partition
            new HashMap<String, byte[]>(),  // a global digest cache for speeding up repeated file checks
            // Register the InstallControler as Callback-Class
            installcontroller,    // the DLCopyGUI
            exchangePartitionSize.intValue(),  // the size of the exchange partition
            valChb(chbCopyExchangePartition),  // if the exchange partition should be copied
            tfPrefixText.getText(), // the auto numbering pattern
            getAutoNrStartVal(),  // the auto numbering start value
            getAutoNrIncrement(),  // the auto numbering increment
            getAutoNrDigits(),  // the minimal number of digits to use for auto numbering
            valChb(chbDataPartitionPersonalPassword),  // if the data partition should be encrypted with a personal password
            pfDataPartitionPersonalPassword.getText(), // the personal password for data partition encryption
            valChb(chbDataPartitionSecondaryPassword),  // if the data partition should be encrypted with a secondary password
            pfDataPartitionSecondaryPassword.getText(), // the secondary password for data partition encryption
            valChb(chbDataPartitionOverwrite),  // if the data partition should be filled with random data before formatting
            valChb(chbCopyDataPartition),  // if the data partition should be copied
            getDataPartitionMode(),   // the mode of the data partition to set in the bootloaders config
            null,   // the device to transfer data from or null, if no data should be transferred
            valChb(chbExchangePartition),  // if the exchange partition should be transferred
            valChb(chbHomeFolder),  // if the home folder should be transferred
            valChb(chbNetworkSettings),  // if the network settings should be transferred
            valChb(chbPrinterSettings),  // if the printer settings should be transferred
            valChb(chbFirewallSettings),  // if the firewall settings should be transferred
            valChb(chbCheckCopies),  // if copies should be checked for errors
            installLock // the lock to aquire before executing in background
        ).execute();
    }

    public int getAutoNrStartVal(){
        int result = 1;
        try{result = Integer.parseInt(tfStartPattern.getText());} catch(Exception e){;}
        return result;
    }

    public int getAutoNrIncrement(){
        int result = 1;
        try{result = Integer.parseInt(tfSteps.getText());} catch(Exception e){;}
        return result;
    }

    public int getAutoNrDigits(){
        int count = 0, num = getAutoNrStartVal();
        while (num != 0) { num /= 10; ++count;}
        return tfStartPattern.getText().length() - count;
    }


    public void updateInstallSelectionCountAndExchangeInfo() {
        // check all selected storage devices
        long minOverhead = Long.MAX_VALUE;
        boolean exchange = true;

        if (selectedStds.isEmpty()) {
            minOverhead = 0;
            exchange = false;
        } else {
            if (runningSystemSource == null) {
                LOGGER.warning("No valid system source selected!");
            } else {
                long enlargedSystemSize = DLCopy.getEnlargedSystemSize(
                        runningSystemSource.getSystemSize());

                for (StorageDevice device : selectedStds) {
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

    private boolean checkSelection(ObservableList<StorageDevice> devices)
            throws DBusException, IOException {
        boolean harddiskSelected = false;
        for (StorageDevice device : devices) {
            if (device.getType() == StorageDevice.Type.HardDrive) {
                harddiskSelected = true;
            }

            PartitionSizes partitionSizes = DLCopy.getInstallPartitionSizes(
                    runningSystemSource, device,
                    exchangePartitionSize.intValue());

            if (!checkPersistence(partitionSizes)) {
                return false;
            }

            if (!checkExchange(partitionSizes)) {
                return false;
            }

            //TODO: checkTransfer from ch.fhnw.dlcopy.gui.swing.InstallerPanels.java:296/2126
        }

        if (valChb(chbDataPartitionPersonalPassword) && pfDataPartitionPersonalPassword.getText().length() == 0) {
            showError(stringBundle.getString("install.error.noPassword"));
            return false;
        }

        if (valChb(chbDataPartitionSecondaryPassword) && pfDataPartitionSecondaryPassword.getText().length() == 0) {
            showError(stringBundle.getString("install.error.noPassword"));
            return false;
        }

        if (harddiskSelected) {
            showHarddiskConfirmation();
        }

        Optional<ButtonType> result = showConfirm(
                stringBundle.getString("install.warningDataLoss"),
                stringBundle.getString("install.installWarning")
        );

        return (result.isPresent() && result.get() == ButtonType.OK);
    }

    private boolean checkPersistence(PartitionSizes partitionSizes)
            throws IOException, DBusException {

        if (!valChb(chbCopyDataPartition)) {
            return true;
        }

        if (!isUnmountedPersistenceAvailable()) {
            return false;
        }

        return checkPersistencePartition(
                runningSystemSource.getDataPartition().getUsedSpace(false),
                partitionSizes);
    }

    private boolean checkPersistencePartition(
            long dataSize, PartitionSizes partitionSizes) {

        // check if the target medium actually has a persistence partition
        if (partitionSizes.getPersistenceMB() == 0) {
            showError(stringBundle.getString("install.error.noDataTarget"));
            return false;
        }

        // check that target partition is large enough
        long targetPersistenceSize
                = (long) partitionSizes.getPersistenceMB() * (long) DLCopy.MEGA;
        if (dataSize > targetPersistenceSize) {
            String errorMessage = MessageFormat.format(stringBundle.getString("install.error.dataSize"),
                    LernstickFileTools.getDataVolumeString(dataSize, 1),
                    LernstickFileTools.getDataVolumeString(
                            targetPersistenceSize, 1));
            showError(errorMessage);
            return false;
        }

        return true;
    }

    private boolean checkExchange(PartitionSizes partitionSizes)
            throws IOException {

        // early return
        if (valChb(chbCopyExchangePartition)) {
            return true;
        }

        // check if the target storage device actually has an exchange partition
        return checkExchangePartition(
                runningSystemSource.getExchangePartition(),
                partitionSizes);
    }

    private boolean checkExchangePartition(
            Partition exchangePartition, PartitionSizes partitionSizes) {

        if (partitionSizes.getExchangeMB() == 0) {
            showError(stringBundle.getString("install.error.noExchangeTarget"));
            return false;
        }

        // check that target partition is large enough
        if (exchangePartition != null) {
            long sourceExchangeSize = exchangePartition.getUsedSpace(false);
            long targetExchangeSize = (long) partitionSizes.getExchangeMB()
                    * (long) DLCopy.MEGA;
            if (sourceExchangeSize > targetExchangeSize) {
                showError(stringBundle.getString("install.error.exchangeSize"));
                return false;
            }
        }

        return true;
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
     * see equivalent function in ch.fhnw.dlcopy.gui.javafx.ui.ExportSystemUI
     * TODO: remove redudancy
     */
    public boolean isUnmountedPersistenceAvailable()
            throws IOException, DBusException {

        // check that a persistence partition is available
        Partition dataPartition = runningSystemSource.getDataPartition();
        if (dataPartition == null) {
            String message = stringBundle.getString("error.noDataPartition");
            LOGGER.log(Level.WARNING, message);
            showError(message);
            printInfo(stringBundle.getString("error.error") + ": " + stringBundle.getString("error.noDataPartition"));
            return false;
        }

        // ensure that the persistence partition is not mounted read-write
        String dataPartitionDevice = dataPartition.getFullDeviceAndNumber();
        if (DLCopy.isMountedReadWrite(dataPartitionDevice)) {
            if (DLCopy.isBootPersistent()) {
                // error and hint
                String message = stringBundle.getString(
                        "warning.dataPartitionInUse") + "\n"
                        + stringBundle.getString("hint.nonpersistentBoot");
                LOGGER.log(Level.WARNING, message);
                showError(message);
                return false;
            } else {
                // persistence partition was manually mounted
                // warning and offer umount
                String message = stringBundle.getString(
                        "warning.dataPartitionInUse") + "\n"
                        + stringBundle.getString("export.unmountQuestion");
                LOGGER.log(Level.WARNING, message);
                Optional<ButtonType> result = showConfirm(stringBundle.getString("global.warning"), message);
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    LOGGER.log(Level.FINEST, result.get().getText());
                    dataPartition.umount();
                    LOGGER.log(Level.WARNING, "Parition unmounted");
                    return isUnmountedPersistenceAvailable();
                } else {
                    return false;
                }
            }
        }
        return true;
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

    private Optional<ButtonType> showConfirm(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText(header);
        alert.setTitle(stringBundle.getString("global.confirm"));
        alert.setContentText(message);
        return alert.showAndWait();
    }

    private void showHarddiskConfirmation() {
        TextInputDialog alert = new TextInputDialog();
        String msg = stringBundle.getString("install.warn.harddisk");
        String answ = stringBundle.getString("install.warn.harddisk.verify");
        alert.setHeaderText(answ);
        alert.setTitle(stringBundle.getString("global.warning"));
        alert.setContentText(MessageFormat.format(msg, answ));
        alert.showAndWait();
        if(!alert.getEditor().getText().equals(answ)) {
            showError(stringBundle.getString("error.mistypedText"));
            showHarddiskConfirmation();
        }
    }

    private DataPartitionMode getDataPartitionMode() {
        DataPartitionModeEntry selection = (DataPartitionModeEntry) cmbDataPartitionMode.getValue();
        return selection.getMode();
    }
    
    private boolean isWholeNumber(String strNum){
        if (strNum == null) {return false;}
        try {int i = Integer.parseInt(strNum);}
        catch (NumberFormatException nfe) {return false;}
        return true;
    }

    private void selectISO() {
        FileChooser chooser = new FileChooser();
        File selectedISO = chooser.showOpenDialog(
            tfISODirectory.getScene().getWindow());
        chooser.setTitle(stringBundle.getString("export.chooseDirectory"));
        if (selectedISO != null) {
            setISOInstallationSourcePath(selectedISO.getAbsolutePath());
        }
    }

    private void setISOInstallationSourcePath(String path) {
        try {

            ProcessExecutor processExecutor = new ProcessExecutor(true);

            SystemSource newIsoSystemSource
                    = new IsoSystemSource(path, processExecutor);

            if (isoSystemSource != null) {
                isoSystemSource.unmountTmpPartitions();
            }

            isoSystemSource = newIsoSystemSource;
            setSystemSource(isoSystemSource);
            tfISODirectory.setText(path);

        } catch (IllegalStateException | IOException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String msg = stringBundle.getString("install.error.invalid_iso");
            msg = MessageFormat.format(msg, path);
            showError(msg);
        } catch (NoExecutableExtLinuxException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String msg = stringBundle.getString("install.error.exec_extlinux");
            msg = MessageFormat.format(msg, path);
            showError(msg);
        } catch (NoExtLinuxException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String msg = stringBundle.getString("install.error.iso_too_old");
            msg = MessageFormat.format(msg, path);
            showError(msg);
        }
    }
}
