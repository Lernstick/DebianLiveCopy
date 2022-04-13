package ch.fhnw.dlcopy.gui.javafx;

import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.gui.javafx.ui.install.InstallControler;
import ch.fhnw.dlcopy.model.PresentationModel;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class StorageMediaManagement
        extends Application
        implements SceneContext{

    private Scene scene;
    private PresentationModel model = PresentationModel.getInstance();
    private ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");
    private View view;

    @Override
    /**
     * A view calls this methode, when the scene should be cnaged to another view
     * @param view The target view to be displayed
     */
    public void setScene(View view){
        this.view.deinitialize();
        this.view = view;
        try {
            scene.setRoot(view.getRoot(this));
        } catch (IOException ex) {
            Logger.getLogger(StorageMediaManagement.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void stop() throws Exception {
        view.deinitialize();
    }
    
    @Override
    public void start(Stage stage) throws Exception {
        view = new StartscreenUI();
        Parent root = view.getRoot(this);
        scene = new Scene(root);

        stage.setScene(scene);
        stage.setHeight(model.getHeight());
        stage.setWidth(model.getWidth());
        stage.setTitle(stringBundle.getString("global.title"));
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/branding/Lernstick_Icon.png")));

        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            model.setHeight((int) (double) newValue);
        });
        stage.widthProperty().addListener((observable, oldValue, newValue) -> {
            model.setWidth((int) (double) newValue);
        });

        stage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void launchFX(String[] args) {
        launch(args);
    }

    @Override
    public void showIsoProgressMessage(String message) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.LoadUI(message));
    }

    @Override
    public void showIsoProgressMessage(String message, int value) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.LoadUI(message, value));
    }

    @Override
    public void isoCreationFinished(String path, boolean success) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.InfoUI(path, success));
    }

    @Override
    public void showSquashFSProgressMessage(String message) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportdata.LoadUI(message));
    }

    @Override
    public void showSquashFSProgressMessage(String message, int value) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportdata.LoadUI(message, value));
    }

    @Override
    public void squashFSCreationFinished(String path, boolean success) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportdata.InfoUI(path, success));
    }
}
