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
    //private static final Logger LOGGER = Logger.getLogger(ConfigUI.class.getName());

    public ConfigUI(){
        resourcePath = getClass().getResource("/fxml/export/config.fxml");
        init();
        setText(); 
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
        File selectedDirectory = folder.showDialog(btnTempDirSelect.getScene().getWindow());
        folder.setTitle("Open Directory");  
        txtTempDirSelect.setText(selectedDirectory.getAbsolutePath());
        checkFreeSpace();
    }
    
    public void checkFreeSpace() {
        File tmpDir = new File(txtTempDirSelect.getText());
        if (tmpDir.exists()) {
            long freeSpace = tmpDir.getFreeSpace();
            txtFreeSpace.setText(
                    LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                txtWritable.setText(STRINGS.getString("Yes"));
                //txtWritable.setForeground(Color.BLACK);
                if (dlCopySwingGUI != null) {
                    dlCopySwingGUI.enableNextButton();
                } 
            } else {
                txtWritable.setText(STRINGS.getString("No"));
                //txtWritable.setForeground(Color.RED);
                if (dlCopySwingGUI != null) {
                    dlCopySwingGUI.disableNextButton();
                }
            }
        } else {
            txtFreeSpace.setText(null);
            txtWritable.setText(
                    STRINGS.getString("Directory_Does_Not_Exist"));
            //txtWritable.setForeground(Color.RED);
            if (dlCopySwingGUI != null) {
                dlCopySwingGUI.disableNextButton();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void init() {
        this.dlCopySwingGUI = dlCopySwingGUI;
        //txtTempDirSelect.getDocument().addDocumentListener(this);
    }
    
    private void setText() {
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N

        //TODO infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        //TODO tmpDirSelectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/fileopen.png"))); // NOI18N
        //TODO jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
/*
        labelTmpDir.setText(bundle.getString("DLCopySwingGUI.tmpDirLabel.text")); // NOI18N
        txtTempDirSelect.setText("/media/");
        labelFreeSpace.setText(bundle.getString("DLCopySwingGUI.freeSpaceLabel.text")); // NOI18N
        labelWritable.setText(bundle.getString("DLCopySwingGUI.writableLabel.text")); // NOI18N
        labelDVDLabel.setText(bundle.getString("DLCopySwingGUI.isoLabelLabel.text")); // NOI18N
*/
    }
}