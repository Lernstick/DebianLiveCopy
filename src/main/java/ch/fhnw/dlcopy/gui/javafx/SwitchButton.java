package ch.fhnw.dlcopy.gui.javafx;

import java.io.IOException;
import java.net.URL;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
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

    @FXML private ToggleButton switchBtn;
    @FXML private final BooleanProperty enabled = new SimpleBooleanProperty(false);
    @FXML private final StringProperty textOn = new SimpleStringProperty("ON");
    @FXML private final StringProperty textOff = new SimpleStringProperty("OFF");
    private EventHandler<ActionEvent> onAction;

    /**
     * Creates a button with default values.
     *
     * @throws IOException for unreadable ressources
     */
    public SwitchButton() throws IOException {
        init();
    }

    /**
     * Creats a button with predefined labels.
     *
     * @param textOn
     * @param textOff
     * @throws IOException for unreadable ressources
     */
    public SwitchButton(String textOn, String textOff) throws IOException {
        init();
        setTextOn(textOn);
        setTextOff(textOff);
    }

    private void init() throws IOException {
        URL resourcePath = getClass().getResource("/fxml/controls/switchbutton.fxml");
        FXMLLoader loader = new FXMLLoader(resourcePath);
        getStylesheets().add("/fxml/css/global.css");
        loader.setRoot(this);
        loader.setController(this);
        loader.load();

        updateText();

        switchBtn.setOnAction((ActionEvent event) -> {
            toggle();
            if (onAction != null) {
                onAction.handle(event);
            }
        });

        setGraphic(switchBtn);
    }

    /**
     * Fetches the selected text property.
     *
     * @return textOn
     */
    @FXML
    public final StringProperty textOnProperty() {
        return textOn;
    }

    /**
     * Fetches the selected text.
     *
     * @return textOn
     */
    @FXML
    public final String getTextOn() {
        return textOn.get();
    }

    /**
     * Sets the selected text.
     *
     * @param textOn
     */
    @FXML
    public final void setTextOn(String textOn) {
        this.textOn.set(textOn);
        updateText();
    }

    /**
     * Fetches the deselected text property.
     *
     * @return textOff
     */
    @FXML
    public final StringProperty textOffProperty() {
        return textOff;
    }

    /**
     * Fetches the deselected text.
     *
     * @return textOff
     */
    @FXML
    public final String getTextOff() {
        return textOff.get();
    }

    /**
     * Sets the deselected text.
     *
     * @param textOff
     */
    @FXML
    public final void setTextOff(String textOff) {
        this.textOff.set(textOff);
        updateText();
    }

    /**
     * Fetches the current value of the button.
     *
     * @return boolean enabled
     */
    @FXML
    public final boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Sets the on action event.
     *
     * @param event
     */
    @FXML
    public final void setOnAction(EventHandler<ActionEvent> event) {
        this.onAction = event;
    }

    /**
     * Fetches the on action event of the button.
     *
     * @return EventHandler, might be null.
     */
    @FXML
    public final EventHandler<ActionEvent> getOnAction() {
        return this.onAction;
    }

    /**
     * Toggles the value of the button.
     */
    @FXML
    public final void toggle() {
        enabled.set(!enabled.get());

        getStyleClass().clear();
        getStyleClass().add("switchButtonInline");
        updateText();
        if (enabled.get()) {
            getStyleClass().add("switchButtonOn");
            setContentDisplay(ContentDisplay.RIGHT);
        } else {
            getStyleClass().add("switchButtonOff");
            setContentDisplay(ContentDisplay.LEFT);
        }
    }

    private final void updateText() {
        if (enabled.get()) {
            setText(textOn.get());
        } else {
            setText(textOff.get());
        }
    }
}
