package ch.fhnw.dlcopy.gui.javafx.ui.exportsystem;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.text.MessageFormat;
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
    private String tmpPath;
    private boolean tmpSuccess = false;

    public InfoUI() {
        resourcePath = getClass().getResource("/fxml/exportsystem/info.fxml");
    }

    public InfoUI(String path, boolean success) {
        this();
        tmpPath = path;
        tmpSuccess = success;
    }

    @Override
    protected void initSelf() {
        String message;
        if (tmpSuccess) {
            message = stringBundle.getString("export.isoDoneLabel.text");
            message = MessageFormat.format(message, tmpPath);
        } else {
            message = stringBundle.getString("export.error.isoCreation");
        }
        btnFinish.setText(message);
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
