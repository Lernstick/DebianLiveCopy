package ch.fhnw.dlcopy.gui.javafx.ui;

import java.io.File;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class IsoCreatorPanels extends View {
    @FXML private TextField txtTempDirSelect;
    @FXML private Button btnTempDirSelect;
    private DirectoryChooser directoryChooser;

;

    public IsoCreatorPanels(){
        directoryChooser = new DirectoryChooser();
        resourcePath = getClass().getResource("/fxml/ISOCreatorPanels.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnTempDirSelect.setOnAction(event -> {
                File selectedDirectory = directoryChooser.showDialog(btnTempDirSelect.getScene().getWindow());
                DirectoryChooser folder = new DirectoryChooser();  
                folder.setTitle("Open Directory");  
                txtTempDirSelect.setText(selectedDirectory.getAbsolutePath());
        });
    } 
    
}