package ch.fhnw.dlcopy.gui.javafx;

import java.io.IOException;
import java.net.URL;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;

/**
 * Inspired by https://stackoverflow.com/a/57290206 and
 * https://stackoverflow.com/a/17494262
 *
 * In the future a library could be used for this, but for now it is not worth
 * for just a single button.
 */
public class SwitchButton extends Label {

    @FXML
    private ToggleButton switchBtn;

    private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);
    private String labelOn = "ON";
    private String labelOff = "OFF";

    public SwitchButton() throws IOException {
        init();
    }

    public SwitchButton(String labelOn, String labelOff) throws IOException {
        this.labelOn = labelOn;
        this.labelOff = labelOff;
        init();
    }

    private void init() throws IOException {
        URL resourcePath = getClass().getResource("/fxml/controls/SwitchButton.fxml");
        FXMLLoader loader = new FXMLLoader(resourcePath);
        getStylesheets().add("/fxml/css/global.css");
        loader.setRoot(this);
        loader.setController(this);
        loader.load();

        switchBtn.setOnAction((ActionEvent event) -> {
            toggle();
        });

        setGraphic(switchBtn);

        enabled.addListener((ObservableValue<? extends Boolean> ov,
                Boolean t0, Boolean t1) -> {
            // TODO: can we apply the styles in CSS?
            if (t1) {
                setText(labelOn);
                setStyle(""
                        + "-fx-background-radius: 1em;"
                        + "-fx-background-color: #1F76E1;"
                        + "-fx-text-fill: #EEEEEE;"
                );
                setContentDisplay(ContentDisplay.RIGHT);
            } else {
                setText(labelOff);
                setStyle(""
                        + "-fx-background-radius: 1em;"
                        + "-fx-background-color: #3C3C3C;"
                        + "-fx-text-fill: #EEEEEE;"
                );
                setContentDisplay(ContentDisplay.LEFT);
            }
        });

        enabled.set(false);
    }

    @FXML
    public boolean isEnabled() {
        return enabled.get();
    }

    @FXML
    public void toggle() {
        enabled.set(!enabled.get());
    }
}
