package ch.fhnw.dlcopy.gui.javafx;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;

/**
 * A instance of this interface can be a context of the state-machine.
 * The instance displayes the scenes
 * 
 * @since 2021-11-05
 */
public interface SceneContext extends DLCopyGUI{
    /**
     * A view calls this methode, when the scene should be cnaged to another view
     * @param view The target view to be displayed
     */
    void setScene(View view);
}
