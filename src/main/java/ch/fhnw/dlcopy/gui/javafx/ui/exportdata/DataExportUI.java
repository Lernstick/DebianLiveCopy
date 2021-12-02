package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.IsoCreator;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SquashFSCreator;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.gui.javafx.StorageMediaManagement;
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
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.freedesktop.dbus.exceptions.DBusException;

public class DataExportUI extends View {

    private SystemSource runningSystemSource;
    private final static ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();

    @FXML private Label lblTargetDirectory;
    @FXML private TextField tfTargetDirectory;
    @FXML private Button btnTargetDirectory;
    @FXML private Label lblWritable;
    @FXML private Label lblWritableDisplay;
    @FXML private Label lblFreeSpace;
    @FXML private Label lblFreeSpaceDisplay;    
    //private DLCopyGUI dlCopyGUI;
    /*
    @FXML private Button tbtnExpertModeOn;
    @FXML private Button tbtnExpertModeOff;
    */
    @FXML private CheckBox chbInformationDialog;
    @FXML private CheckBox chbInstallationProgram;
    
    @FXML private Button btnNext;
    @FXML private Button btnBack;
    private static final Logger LOGGER = Logger.getLogger(DataExportUI.class.getName());

    
    private void createDataPartiton() {
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);
        
        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        new SquashFSCreator(context, runningSystemSource,
            tfTargetDirectory.getText(),
            isChbInformationDialogChecked(),
            isChbInstallationProgramChecked())
            .execute();
    }   
    
        @Override
    protected void setupEventHandlers() {
        
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
        
        btnNext.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    /*
                    if (!isUnmountedPersistenceAvailable()) {
                        btnNext.setDisable(true);
                        return;
                    }*/
                    
                    //context.setScene(new ProgressUI());
                    //createDataPartiton();
                    //System.out.println(isChbInformationDialogChecked());    
                    //System.out.println(isChbInstallationProgramChecked());
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                    showError(ex.getLocalizedMessage());
                }
            }
        });
        
        btnTargetDirectory.setOnAction(event -> {
            selectDirectory();
        }); 
    }
                
                
    public DataExportUI(){
        // prepare processExecutor to always use the POSIX locale
        resourcePath = getClass().getResource("/fxml/exportData/dataexport-responsive.fxml");
    }

    /*
    private void createISO() {
        //TODO showProgressPanel();
        if(lblTargetDirectory.getText() != ""){
            RunningSystemSource runningSystemSource = null;

            try {
                runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);

            } catch (IOException | DBusException ex) {
                Logger.getLogger(StorageMediaManagement.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (runningSystemSource != null){
                new SquashFSCreator(dlCopyGUI, runningSystemSource,
                       getTemporaryDirectory(),
                       isShowNotUsedDialogSelected(),
                       isAutoStartInstallerSelected())
                       .execute();
             }
        }
    }*/

    private void selectDirectory() {
        DirectoryChooser folder = new DirectoryChooser();
        File selectedDirectory = folder.showDialog(btnTargetDirectory.getScene().getWindow());
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
            lblWritableDisplay.setText(
                LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                lblFreeSpaceDisplay.setText(STRINGS.getString("Yes"));
                lblFreeSpaceDisplay.setStyle("-fx-text-fill: red ;");
                btnNext.setDisable(false);
            } else {
                lblFreeSpaceDisplay.setText(STRINGS.getString("No"));
                lblFreeSpaceDisplay.setStyle("-fx-text-fill: red ;") ;
                btnNext.setDisable(true);
            }
        } else {
            lblWritableDisplay.setText(null);
            lblFreeSpaceDisplay.setText(
                    STRINGS.getString("Directory_Does_Not_Exist"));
            lblFreeSpaceDisplay.setStyle("-fx-text-fill: red ;") ;
            btnNext.setDisable(true);
        }
    }

    /**
     * see equivalent function in ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI
     * TODO: avoid code duplication
     */
    /*

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
*/
    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(STRINGS.getString("Error"));
        alert.setHeaderText(STRINGS.getString("Error"));
        alert.setContentText(message);
        alert.showAndWait();
    }
    public void init() {
       //this.dlCopyGUI = context;
    }
    
    public boolean isChbInformationDialogChecked(){
        return chbInformationDialog.isSelected();
    }
    
    
    public boolean isChbInstallationProgramChecked(){
        return chbInstallationProgram.isSelected();
    }
}  