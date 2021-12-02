package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.ui.exportdata.ConfigUI;
import ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.SystemexportUI;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class StartscreenUI extends View {

    @FXML private Button btnCopy;
    @FXML private Button btnExport;
    @FXML private Button btnInstall;
    @FXML private Button btnReset;
    @FXML private Button btnUpdate;
    @FXML private ImageView imgCopy;
    @FXML private ImageView imgDefault;
    @FXML private ImageView imgExport;
    @FXML private ImageView imgInstall;
    @FXML private ImageView imgReset;
    @FXML private ImageView imgUpdate;
    @FXML private Pane panCopy;
    @FXML private Pane panDefault;
    @FXML private Pane panExport;
    @FXML private Pane panInstall;
    @FXML private Pane panReset;
    @FXML private Pane panUpdate;

    public StartscreenUI() {
        resourcePath = getClass().getResource("/fxml/startscreen.fxml");
    }

    @Override
    protected void initControls() {
        panDefault.setVisible(true);
    }

    @Override
    protected void setupEventHandlers() {
        btnCopy.setOnAction(event -> {
            context.setScene(new ConfigUI());
        });

        setupMenuButtonHandler(btnCopy, panCopy);
        setupMenuButtonHandler(btnExport, panExport);
        setupMenuButtonHandler(btnInstall, panInstall);
        setupMenuButtonHandler(btnReset, panReset);
        setupMenuButtonHandler(btnUpdate, panUpdate);
        //setupMenuButtonHandler(btnDefault, panDefault);

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

    private void setupMenuButtonHandler(Button btn, Pane ivw) {

        btn.addEventHandler(MouseEvent.MOUSE_ENTERED, (MouseEvent e) -> {
            panDefault.setVisible(false);
            ivw.setVisible(true);
        });

        btn.addEventHandler(MouseEvent.MOUSE_EXITED, (MouseEvent e) -> {
            panDefault.setVisible(true);
            ivw.setVisible(false);
        });
    }
}