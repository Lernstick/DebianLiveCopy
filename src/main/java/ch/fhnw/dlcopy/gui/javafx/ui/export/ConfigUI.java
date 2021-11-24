package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI;
import ch.fhnw.util.LernstickFileTools;
import java.util.logging.Logger;
import java.io.File;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;

public class ConfigUI extends View {
    @FXML private Label labelDVDLabel;
    @FXML private TextField txtDVDLabel;
    @FXML private Label labelTmpDir;
    @FXML private TextField txtTempDirSelect;
    @FXML private Button btnTempDirSelect;
    @FXML private Label labelFreeSpace;
    @FXML private TextField txtFreeSpace;
    @FXML private Label labelWritable;
    @FXML private TextField txtWritable;
    @FXML private Button btnNext;
    @FXML private Button btnBack;
    private DLCopySwingGUI dlCopySwingGUI;
    private static final Logger LOGGER = Logger.getLogger(ConfigUI.class.getName());

    public ConfigUI(){
        resourcePath = getClass().getResource("/fxml/export/config.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
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
                if (dlCopySwingGUI != null) {
                    dlCopySwingGUI.enableNextButton();
                }
            } else {
                txtWritable.setText(STRINGS.getString("No"));
                txtWritable.setStyle("-fx-text-fill: red ;");

                if (dlCopySwingGUI != null) {
                    dlCopySwingGUI.disableNextButton();
                }
            }
        } else {
            txtFreeSpace.setText(null);
            txtWritable.setText(
            STRINGS.getString("Directory_Does_Not_Exist"));
            txtWritable.setStyle("-fx-text-fill: red ;");

            if (dlCopySwingGUI != null) {
                dlCopySwingGUI.disableNextButton();
            }
        }
    }


    public void init() {
        this.dlCopySwingGUI = dlCopySwingGUI;
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
