package ch.fhnw.dlcopy.gui.javafx.ui.exportsystem;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.IsoCreator;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.SwitchButton;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.gui.javafx.ui.export.ProgressUI;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import org.freedesktop.dbus.exceptions.DBusException;
import javafx.stage.DirectoryChooser;

public class SystemexportUI extends View{

    private String option_NotUsed = stringBundle.getString("global.notUsed");
    private String option_ReadOnly = stringBundle.getString("global.readWrite");
    private String option_ReadWrite = stringBundle.getString("global.readOnly");
    private SystemSource runningSystemSource;

    private static final Logger LOGGER = Logger.getLogger(SystemexportUI.class.getName());
    private final static ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();

    @FXML private Button btnBack;
    @FXML private Button btnExport;
    @FXML private Button btnTargetDirectory;
    @FXML private CheckBox chbInformationDialog;
    @FXML private CheckBox chbInstallationProgram;
    @FXML private ComboBox<String> cmbDataPartitionMode;
    @FXML private ImageView imgTargetDirectory;
    @FXML private Label lblFreeSpaceDisplay;
    @FXML private Label lblWriteable;
    @FXML private Label lblInfo;
    @FXML private SwitchButton switchBtn;
    @FXML private TextField tfDvdLabel;
    @FXML private TextField tfTargetDirectory;

    public SystemexportUI() {

        // prepare processExecutor to always use the POSIX locale
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        resourcePath = getClass().getResource("/fxml/exportSystem/systemexport.fxml");
    }

    @Override
    protected void initControls(){
        cmbDataPartitionMode.getItems().addAll(option_ReadWrite, option_ReadOnly, option_NotUsed);
        cmbDataPartitionMode.setValue(option_ReadWrite);
    }

    @Override
    protected void setupEventHandlers() {
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });

        btnTargetDirectory.setOnAction(event -> {
            selectDirectory();
        });

        btnExport.setOnAction(event -> {
            try {
                if (!isUnmountedPersistenceAvailable()) {
                    btnExport.setDisable(true);
                    return;
                }
                context.setScene(new ProgressUI());
                new IsoCreator(
                    context,
                    runningSystemSource,
                    false,                               // Only boot medium
                    tfTargetDirectory.getText(),         // tmpDirectory
                    getDataPartitionMode(),              // Data Partition mode
                    chbInformationDialog.isSelected(),   // showNotUsedDialog
                    chbInstallationProgram.isSelected(), // autoStartInstaller
                    tfDvdLabel.getText()                 // partition label
                ).createISO();
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                showError(ex.getLocalizedMessage());
            }
        });

        imgTargetDirectory.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 25.714));
        imgTargetDirectory.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 4.1739));
    }

    public DataPartitionMode getDataPartitionMode(){
        if (option_NotUsed.equals(cmbDataPartitionMode.getValue())) {
            return DataPartitionMode.NOT_USED;
        } else if (option_ReadWrite.equals(cmbDataPartitionMode.getValue())){
            return DataPartitionMode.READ_WRITE;
        } else if (option_ReadOnly.equals(cmbDataPartitionMode.getValue())){
            return DataPartitionMode.READ_ONLY;
        } else {
            throw new IllegalArgumentException();
        }
    }

        private void selectDirectory() {
        DirectoryChooser folder = new DirectoryChooser();
        File selectedDirectory = folder.showDialog(
            btnTargetDirectory.getScene().getWindow());
        folder.setTitle("Open Directory");
        if (selectedDirectory != null) {
            tfTargetDirectory.setText(selectedDirectory.getAbsolutePath());
            checkFreeSpace();
        }
    }

    public void checkFreeSpace() {
        File tmpDir = new File(tfTargetDirectory.getText());
        if (tmpDir.exists()) {
            long freeSpace = tmpDir.getFreeSpace();
            lblFreeSpaceDisplay.setText(
                LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                lblWriteable.setText(STRINGS.getString("Yes"));
                lblWriteable.setStyle("-fx-text-fill: red ;");
                btnExport.setDisable(false);
            } else {
                lblWriteable.setText(STRINGS.getString("No"));
                lblWriteable.setStyle("-fx-text-fill: red ;") ;
                btnExport.setDisable(true);
            }
        } else {
            lblFreeSpaceDisplay.setText(null);
            lblWriteable.setText(
                    STRINGS.getString("Directory_Does_Not_Exist"));
            lblWriteable.setStyle("-fx-text-fill: red ;") ;
            btnExport.setDisable(true);
        }
    }

    /**
     * see equivalent function in ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI
     * TODO: avoid code duplication
     */
    public boolean isUnmountedPersistenceAvailable()
            throws IOException, DBusException {

        String cmdLineFileName = "/proc/cmdline";
        String cmdLine = DLCopy.readOneLineFile(new File(cmdLineFileName));
        boolean persistenceBoot = cmdLine.contains(" persistence ");
        LOGGER.log(Level.FINEST, "persistenceBoot: {0}", persistenceBoot);

        // check that a persistence partition is available
        Partition dataPartition = runningSystemSource.getDataPartition();
        if (dataPartition == null) {
            String message = STRINGS.getString("Error_No_Persistence");
            LOGGER.log(Level.WARNING, message);
            showError(message);
            return false;
        }

        // ensure that the persistence partition is not mounted read-write
        String dataPartitionDevice
                = "/dev/" + dataPartition.getDeviceAndNumber();
        boolean mountedReadWrite = false;
        List<String> mounts = LernstickFileTools.readFile(
                new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            String mountedPartition = tokens[0];
            if (mountedPartition.equals(dataPartitionDevice)) {
                // check mount options
                String mountOptions = tokens[3];
                if (mountOptions.startsWith("rw")) {
                    mountedReadWrite = true;
                    break;
                }
            }
        }

        if (mountedReadWrite) {
            if (persistenceBoot) {
                // error and hint
                String message = STRINGS.getString(
                        "Warning_Persistence_Mounted") + "\n"
                        + STRINGS.getString("Hint_Nonpersistent_Boot");
                LOGGER.log(Level.WARNING, message);
                showError(message);
                return false;
            } else {
                // persistence partition was manually mounted
                // TODO: warning and offer umount
                String message = STRINGS.getString(
                        "Warning_Persistence_Mounted") + "\n"
                        + STRINGS.getString("Umount_Question");
                LOGGER.log(Level.WARNING, message);
                showError(message);
                runningSystemSource.getDataPartition().umount();
                LOGGER.log(Level.WARNING, "Parition unmounted");
                return isUnmountedPersistenceAvailable();
            }
        }

        return true;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(STRINGS.getString("Error"));
        alert.setHeaderText(STRINGS.getString("Error"));
        alert.setContentText(message);
        alert.showAndWait();
    }
}
