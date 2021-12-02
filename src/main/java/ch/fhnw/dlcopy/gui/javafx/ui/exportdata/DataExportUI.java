package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SquashFSCreator;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.SwitchButton;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.ProcessExecutor;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import org.freedesktop.dbus.exceptions.DBusException;

public class DataExportUI extends View {

    private SystemSource runningSystemSource;
    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();
    private static final Logger LOGGER = Logger.getLogger(DataExportUI.class.getName());

    @FXML private Label lblTargetDirectory;
    @FXML private TextField tfTargetDirectory;
    @FXML private Button btnTargetDirectory;
    @FXML private Label lblWriteable;
    @FXML private Label lblWriteableDisplay;
    @FXML private Label lblFreeSpace;
    @FXML private Label lblFreeSpaceDisplay;
    @FXML private Label lblInfo;
    @FXML private CheckBox chbInformationDialog;
    @FXML private CheckBox chbInstallationProgram;
    @FXML private Button btnExport;
    @FXML private Button btnBack;
    @FXML private SwitchButton switchBtn;

    public DataExportUI() {
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        resourcePath = getClass().getResource("/fxml/exportData/dataexport.fxml");
    }

    @Override
    protected void setupEventHandlers() {

        switchBtn.getButton().setOnAction(event -> {
            toggleExpertMode();
        });
        
        btnTargetDirectory.setOnAction(event -> {
            selectDirectory();
        });

        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });

        btnExport.setOnAction((ActionEvent event) -> {
            if(tfTargetDirectory.getText().length() > 0){
                createDataPartiton();
            }
        });
    }

    private void selectDirectory() {
        DirectoryChooser folder = new DirectoryChooser();
        File selectedDirectory = folder.showDialog(btnTargetDirectory.getScene().getWindow());
        folder.setTitle("Open Directory");
        if (selectedDirectory != null) {
            tfTargetDirectory.setText(selectedDirectory.getAbsolutePath());
            checkFreeSpace();
        }
    }

    private void checkFreeSpace() {
        File tmpDir = new File(tfTargetDirectory.getText());
        if (tmpDir.exists()) {
            long freeSpace = tmpDir.getFreeSpace();
            lblWriteableDisplay.setText(
                    LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                lblFreeSpaceDisplay.setText(STRINGS.getString("Yes"));
                lblFreeSpaceDisplay.getStyleClass().clear();
                lblFreeSpaceDisplay.getStyleClass().add("target-rw");
                btnExport.setDisable(false);
            } else {
                lblFreeSpaceDisplay.setText(STRINGS.getString("No"));
                lblFreeSpaceDisplay.getStyleClass().clear();
                lblFreeSpaceDisplay.getStyleClass().add("target-ro");
                btnExport.setDisable(true);
            }
        } else {
            lblWriteableDisplay.setText(null);
            lblFreeSpaceDisplay.setText(
                STRINGS.getString("Directory_Does_Not_Exist"));
            lblFreeSpaceDisplay.getStyleClass().clear();
            lblFreeSpaceDisplay.getStyleClass().add("target-na");
            btnExport.setDisable(true);
        }
    }

    private void createDataPartiton() {
        try {
            context.setScene(new LoadUI());
            new SquashFSCreator(
                context,
                runningSystemSource,
                tfTargetDirectory.getText(),
                chbInformationDialog.isSelected(),
                chbInstallationProgram.isSelected()
            ).createSquashFS();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "", ex);
            showError(ex.getLocalizedMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(STRINGS.getString("Error"));
        alert.setHeaderText(STRINGS.getString("Error"));
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    
    private void toggleExpertMode(){
        switchBtn.toggle();
        for (Node n : new Node[]{chbInformationDialog,chbInstallationProgram}){
            n.setVisible(switchBtn.isEnabled());
        }
    }
}
