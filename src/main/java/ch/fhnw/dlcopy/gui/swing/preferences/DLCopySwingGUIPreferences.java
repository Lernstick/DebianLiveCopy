package ch.fhnw.dlcopy.gui.swing.preferences;

import ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI;
import java.util.prefs.Preferences;

/**
 * The abstract base class for DLCopySwingGUI preferences
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public abstract class DLCopySwingGUIPreferences {

    /**
     * the prefereces node for DLCopySwingGUI
     */
    protected final static Preferences preferences
            = Preferences.userNodeForPackage(DLCopySwingGUI.class);

    public abstract void load();

    public abstract void save();
}
