package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.IsoCreator;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
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
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.freedesktop.dbus.exceptions.DBusException;

public class ConfigUI extends View {

    private SystemSource runningSystemSource;
    private final static ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();

    @FXML private Label labelDVDLabel;
    @FXML private TextField txtDVDLabel;
    @FXML private Label labelTmpDir;
    @FXML private TextField txtTempDirSelect;
    @FXML private Button btnTempDirSelect;
    @FXML private Label labelFreeSpace;
    @FXML private TextField txtFreeSpace;
    @FXML private Label labelWritable;
    @FXML private TextField txtWritable;
    @FXML private TextField txtDVDLabel;
    @FXML private Button btnNext;
    @FXML private Button btnBack;
    private static final Logger LOGGER = Logger.getLogger(ConfigUI.class.getName());

    public ConfigUI(){

        // prepare processExecutor to always use the POSIX locale
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        resourcePath = getClass().getResource("/fxml/export/config.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });

        btnNext.setOnAction(event -> {
            try {
                if (!isUnmountedPersistenceAvailable()) {
                    btnNext.setDisable(true);
                    return;
                }
            } catch (IOException | DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            context.setScene(new ProgressUI());
            new IsoCreator(
                    context,
                    runningSystemSource,
                    false,                          // Only boot medium
                    txtTempDirSelect.getText(),     // tmpDirectory
                    DataPartitionMode.READ_WRITE,   // Data Partition mode
                    false,                          // showNotUsedDialog
                    false,                          // autoStartInstaller
                    txtDVDLabel.getText()           // TODO: txtDVDLabel
            ).execute();
        });

        btnTempDirSelect.setOnAction(event -> {
            selectDirectory();
        });
    }

    private void selectDirectory() {
        DirectoryChooser folder = new DirectoryChooser();
        File selectedDirectory = folder.showDialog(
            btnTempDirSelect.getScene().getWindow());
        folder.setTitle("Open Directory");
        if (selectedDirectory != null) {
            txtTempDirSelect.setText(selectedDirectory.getAbsolutePath());
            checkFreeSpace();
        }
    }

    public void checkFreeSpace() {
        File tmpDir = new File(txtTempDirSelect.getText());
        if (tmpDir.exists()) {
            long freeSpace = tmpDir.getFreeSpace();
            txtFreeSpace.setText(
                LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                txtWritable.setText(STRINGS.getString("Yes"));
                txtWritable.setStyle("-fx-text-fill: red ;");
                btnNext.setDisable(false);
            } else {
                txtWritable.setText(STRINGS.getString("No"));
                txtWritable.setStyle("-fx-text-fill: red ;") ;
                btnNext.setDisable(true);
            }
        } else {
            txtFreeSpace.setText(null);
            txtWritable.setText(
                    STRINGS.getString("Directory_Does_Not_Exist"));
            txtWritable.setStyle("-fx-text-fill: red ;") ;
            btnNext.setDisable(true);
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
            LOGGER.log(Level.WARNING, "Error_No_Persistence");
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
                return false;
            } else {
                // persistence partition was manually mounted
                // TODO: warning and offer umount
                String message = STRINGS.getString(
                        "Warning_Persistence_Mounted") + "\n"
                        + STRINGS.getString("Umount_Question");
                LOGGER.log(Level.WARNING, message);
                runningSystemSource.getDataPartition().umount();
                LOGGER.log(Level.WARNING, "Parition unmounted");
                return isUnmountedPersistenceAvailable();
            }
        }

        return true;
    }


    public void init() {
        setText();
    }


    //TODO David discuss MVC: same class fxml constructor.
    @FXML
    private void setText() {
        java.util.ResourceBundle bundle =
            java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N

        System.out.println(labelTmpDir);
        labelTmpDir.setText(bundle.getString("DLCopySwingGUI.tmpDirLabel.text")); // NOI18N
        /*
        txtTempDirSelect.setText("/media/");
        labelFreeSpace.setText(bundle.getString("DLCopySwingGUI.freeSpaceLabel.text")); // NOI18N
        labelWritable.setText(bundle.getString("DLCopySwingGUI.writableLabel.text")); // NOI18N
        labelDVDLabel.setText(bundle.getString("DLCopySwingGUI.isoLabelLabel.text")); // NOI18N
        */
    }
}
