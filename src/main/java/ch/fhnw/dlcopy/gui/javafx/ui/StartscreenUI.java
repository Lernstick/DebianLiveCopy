package ch.fhnw.dlcopy.gui.javafx.ui;

import ch.fhnw.dlcopy.gui.javafx.ui.exportdata.DataExportUI;
import ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.SystemexportUI;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class StartscreenUI extends View {

    @FXML private Button btnExportData;
    @FXML private Button btnExportSystem;
    @FXML private Button btnInstall;
    @FXML private Button btnReset;
    @FXML private Button btnUpdate;
    @FXML private ImageView imgDefault;
    @FXML private ImageView imgExportData;
    @FXML private ImageView imgExportSystem;
    @FXML private ImageView imgInstall;
    @FXML private ImageView imgReset;
    @FXML private ImageView imgUpdate;
    @FXML private Pane panDefault;
    @FXML private Pane panExportData;
    @FXML private Pane panExportSystem;
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
        setupMenuButtonHandler(btnExportSystem, panExportSystem);
        setupMenuButtonHandler(btnExportData, panExportData);
        setupMenuButtonHandler(btnInstall, panInstall);
        setupMenuButtonHandler(btnReset, panReset);
        setupMenuButtonHandler(btnUpdate, panUpdate);

        btnExportData.setOnAction(event -> {
            context.setScene(new DataExportUI());
        });
        
        btnExportSystem.setOnAction(event -> {
            context.setScene(new SystemexportUI());
        });

        imgDefault      .fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.16));
        imgExportData   .fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.16));
        imgExportSystem .fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.16));
        imgInstall      .fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.16));
        imgReset        .fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.16));
        imgUpdate       .fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 2.16));
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