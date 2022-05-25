package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;

/**
 * This class represents the View, where all the information is shown at the end of the export
 */
public class InfoUI extends View {
    @FXML private Button btnFinish;
    @FXML private ImageView imgExportFile;
    @FXML private TextArea taExtraInfo;

    private static final Logger LOGGER = Logger.getLogger(InfoUI.class.getName());
    private String tmpPath;
    private boolean tmpSuccess = false;

    public InfoUI() {
        resourcePath = getClass().getResource("/fxml/exportdata/info.fxml");
    }

    public InfoUI(String path, boolean success) {
        this();
        tmpPath = path;
        tmpSuccess = success;
    }

    @Override
    /**
    * This method is called during the initialize-process.
    * The class itself should be initialized here.
    * In here the references to the FXML - elements are not null. In the constructor, these elements are all null - references.
    */
    protected void initSelf() {
        String message;
        if (tmpSuccess) {
            message = stringBundle.getString("export.isoDoneLabel.text");
            message = MessageFormat.format(message, tmpPath);
        } else {
            message = stringBundle.getString("export.error.isoCreation");
        }
        taExtraInfo.setText(message);
    }

    @Override
    /**
    * This method is called during the initialize-process.
    * In this method JavaFX - event handlers are set up
    */
    protected void setupEventHandlers() {
        btnFinish.setOnAction(event -> {
            LOGGER.log(Level.INFO, "Mischief managed.");
            context.setScene(new StartscreenUI());
        });

        imgExportFile.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 5.45));
        imgExportFile.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 10.666));
    }
}
