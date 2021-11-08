package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;

public class ConfigUI extends View {
    @FXML private TextField txtDVDLabel;
    @FXML private Button btnBack;

    public ConfigUI(){
        resourcePath = getClass().getResource("/fxml/export/config.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnBack.setOnAction(event -> {
            context.setScene(new StartscreenUI());
        });
    }
    
    
}
