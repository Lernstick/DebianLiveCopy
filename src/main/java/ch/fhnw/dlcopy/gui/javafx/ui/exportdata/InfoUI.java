package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;

/**
 * Call the info screen
 */
public class InfoUI extends View {
    @FXML private Button btnFinish;
    @FXML private ImageView imgExportFile;

    private static final Logger LOGGER = Logger.getLogger(InfoUI.class.getName());

    public InfoUI(){
        resourcePath = getClass().getResource("/fxml/exportData/info.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnFinish.setOnAction(event -> {
            LOGGER.log(Level.INFO, "Mischief managed.");
            context.setScene(new StartscreenUI());
        });
        
        imgExportFile.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 5.45));
        imgExportFile.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 10.666));
    }
}
