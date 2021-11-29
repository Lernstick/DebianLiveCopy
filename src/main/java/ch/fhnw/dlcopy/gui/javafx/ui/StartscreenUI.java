package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.ui.export.ConfigUI;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class StartscreenUI extends View{

    @FXML private Button btnCopy;

    public StartscreenUI(){
        resourcePath = getClass().getResource("/fxml/startscreen.fxml");
    }

    @Override
    protected void setupEventHandlers(){
        btnCopy.setOnAction(event -> {
            context.setScene(new ConfigUI());
        });
    }
}
