package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Call the info screen
 */
public class InfoUI extends View {
    @FXML private Button btnFinish;

    private static final Logger LOGGER = Logger.getLogger(InfoUI.class.getName());

    public InfoUI(){
        resourcePath = getClass().getResource("/fxml/exportSystem/info.fxml");
    }

    @Override
    protected void setupEventHandlers() {
        btnFinish.setOnAction(event -> {
            LOGGER.log(Level.INFO, "Mischief managed.");
            context.setScene(new StartscreenUI());
        });
    }
}
