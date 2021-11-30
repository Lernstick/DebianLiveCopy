package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.ui.export.ConfigUI;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

public class StartscreenUI extends View {

    @FXML private Button btnCopy;
    @FXML private ImageView imgInstall;

    public StartscreenUI() {
        resourcePath = getClass().getResource("/fxml/startscreen.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        imgInstall.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 3.6));
        btnCopy.setOnAction(event -> {
            context.setScene(new ConfigUI());
        });
    }
}
