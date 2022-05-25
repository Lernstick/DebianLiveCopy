package ch.fhnw.dlcopy.gui.javafx.ui.exportsystem;

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

    /**
     * This method is called from the core when the system is exported
     * The Loading Screen should be displayed, when this method is called.
     */
    public void showIsoProgress() {
        context.setScene(new LoadUI());
    }

    @Override
    /**
     * This method is called from the core when the system is exported
     * The Loading Screen should be displayed and the given message should be shown, when this method is called.
     */
    public void showIsoProgressMessage(String message) {
        context.setScene(new LoadUI(message));
    }

    @Override
    /**
     * This method is called from the core when the system is exported
     * The Loading Screen should be displayed, the given message should be shown and the progress should be set to the value when this method is called.
     */
    public void showIsoProgressMessage(String message, int value) {
        context.setScene(new LoadUI(message, value));
    }

    @Override
    /**
     * This method is called from the core when the system is exported
     * The End Screen (Info Screen called) should be displayed.
     */
    public void isoCreationFinished(String path, boolean success) {
        context.setScene(new InfoUI(path, success));
    }
}
