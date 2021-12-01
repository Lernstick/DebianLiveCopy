package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.ui.exportdata.ConfigUI;
import ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.SystemexportUI;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;

public class StartscreenUI extends View {

    @FXML private Button btnCopy;
    @FXML private ImageView imgCopy;
    @FXML private ImageView imgDefault;
    @FXML private Button btnExport;
    @FXML private ImageView imgExport;
    @FXML private ImageView imgInstall;
    @FXML private ImageView imgReset;
    @FXML private ImageView imgUpdate;

    public StartscreenUI() {
        resourcePath = getClass().getResource("/fxml/startscreen.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnCopy.setOnAction(event -> {
            context.setScene(new ConfigUI());
        });

        btnExport.setOnAction(event -> {
            context.setScene(new SystemexportUI());
        });

        imgCopy.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.65));
        imgCopy.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 5));

        imgDefault.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.19));
        imgDefault.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 2.59));

        imgExport.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.65));
        imgExport.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 5));

        imgInstall.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 3.6));
        imgInstall.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 4));

        imgReset.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.65));
        imgReset.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 5));

        imgUpdate.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.65));
        imgUpdate.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 5));
    }
}
