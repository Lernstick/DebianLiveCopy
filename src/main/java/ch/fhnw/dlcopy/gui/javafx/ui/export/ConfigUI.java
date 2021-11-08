package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.ui.View;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class ConfigUI extends View {
    @FXML private TextField txtDVDLabel;

    public ConfigUI(){
        resourcePath = getClass().getResource("/fxml/export/config.fxml");
    }
}
