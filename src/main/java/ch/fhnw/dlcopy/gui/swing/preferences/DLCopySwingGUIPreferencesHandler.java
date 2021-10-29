package ch.fhnw.dlcopy.gui.swing.preferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores and restores the preferences of DLCopySwingGUI
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class DLCopySwingGUIPreferencesHandler {

    private final List<DLCopySwingGUIPreferences> preferencesList;

    public DLCopySwingGUIPreferencesHandler() {
        preferencesList = new ArrayList<>();
    }

    public void addPreference(DLCopySwingGUIPreferences preference) {
        preferencesList.add(preference);
    }

    public void load() {
        preferencesList.forEach((preference) -> preference.load());
    }

    public void save() {
        preferencesList.forEach((preference) -> preference.save());
    }
}
