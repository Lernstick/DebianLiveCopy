package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import java.util.ResourceBundle;

/**
 * A controler for the installation path.
 * Holds the current state of the installation.
 */
public class ExportControler implements DLCopyGUI {

    protected ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");

    /**
     * The singleton instance of the class
     */
    private static ExportControler instance = null;

    /**
     * The scene context, witch is used to switch the scene
     */
    private final SceneContext context;

    /**
     * Mark the constructor <c>private</c>, so it is not accessable from outside
     */
    private ExportControler(SceneContext context) {
        this.context = context;
    }

    /**
     * A thread save lacy constructor for the singleton
     * @param context The scene context, witch will be used to switch the scene.
     * @return The instance of the singleton
     */
    public static synchronized ExportControler getInstance(SceneContext context){
        if (instance == null) {
            instance = new ExportControler(context);
        }
        return instance;
    }

    public void showSquashFSProgress() {
        context.setScene(new LoadUI());
    }

    @Override
    public void showSquashFSProgressMessage(String message) {
        context.setScene(new LoadUI(message));
    }

    @Override
    public void showSquashFSProgressMessage(String message, int value) {
        context.setScene(new LoadUI(message, value));
    }

    @Override
    public void squashFSCreationFinished(String path, boolean success) {
        context.setScene(new InfoUI(path, success));
    }
}
