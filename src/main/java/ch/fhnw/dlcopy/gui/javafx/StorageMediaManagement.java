package ch.fhnw.dlcopy.gui.javafx;

import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.model.PresentationModel;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * This class is the base of the JavaFX frontend.
 * It's starts the JavaFX and manages the displayed view.
 */
public class StorageMediaManagement
        extends Application
        implements SceneContext {

    private Scene scene;

    /**
     * The presentation model, which holds crosscutting states
     */
    private PresentationModel model = PresentationModel.getInstance();

    /**
     * The resource bundle with the internationalized strings
     */
    private ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");

    /**
     * The currently displayed view
     */
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
    /**
     * This methode is called by JavaFX, when the application stops.
     * It deinitalize the current view, to avoid memory leakes.
     */
    public void stop() throws Exception {
        view.deinitialize();
    }

    @Override
    /**
     * This methode is called by JavaFX, when the application starts.
     * It initalizes the windows and views the startscreen - view.
     */
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
     * When this methode is called, the JavaFX frontend will be initalized.
     * @param args the command line arguments
     */
    public static void launchFX(String[] args) {
        launch(args);
    }
}
