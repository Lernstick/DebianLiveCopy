package ch.fhnw.dlcopy.gui.javafx.ui.export;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

public class ProgressUI extends View{
    @FXML private Label labelProgress;
    @FXML private ProgressBar barProgress;
    @FXML private Button btnNext;
    @FXML private Button btnBack;

    private static final Logger LOGGER = Logger.getLogger(ConfigUI.class.getName());
    private String tmpMessage;
    private double tmpProgress = -1;

    public ProgressUI(){
        resourcePath = getClass().getResource("/fxml/export/progress.fxml");
    }

    public ProgressUI(String message, double progress){
        this();
        tmpMessage = message;
        tmpProgress = progress;
    }

    public ProgressUI(String message){
        this(message, -1);
    }

    @Override
    protected void initSelf(){
        // Set values
        if(tmpMessage != null){
            labelProgress.setText(tmpMessage);
        } else {
            labelProgress.setText("Not inizalised");
        }
        barProgress.setProgress(tmpProgress);

        if(tmpProgress >= 100) {
            btnNext.setDisable(false);
        }

        if(tmpProgress < 0) {
            labelProgress.setText("An error occured");
            btnBack.setDisable(false);
        }
    }


    @Override
    protected void setupEventHandlers() {
        btnBack.setOnAction(event -> {
            context.setScene(new ConfigUI());
        });

        btnNext.setOnAction(event -> {
            LOGGER.log(Level.INFO, "Mischief managed.");
            context.setScene(new StartscreenUI());
        });
    }
}
