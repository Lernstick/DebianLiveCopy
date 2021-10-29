package ch.fhnw.dlcopy.gui.swing.preferences;

import ch.fhnw.dlcopy.gui.swing.OverwriteConfigurationPanel;
import javax.swing.JCheckBox;

/**
 * The preferences of the reset restore module.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetRestorePreferences extends DLCopySwingGUIPreferences {

    private final static String ENABLED = "resetRestoreEnabled";
    private final static String DATA = "resetRestoreData";

    private final JCheckBox checkBox;
    private final OverwriteConfigurationPanel panel;

    public ResetRestorePreferences(
            JCheckBox checkBox, OverwriteConfigurationPanel panel) {

        this.checkBox = checkBox;
        this.panel = panel;
    }

    @Override
    public void load() {

        checkBox.setSelected(preferences.getBoolean(ENABLED, false));

        String resetRestoreData = preferences.get(DATA, null);
        if (resetRestoreData != null && !resetRestoreData.isEmpty()) {
            panel.setXML(resetRestoreData);
        }

    }

    @Override
    public void save() {
        preferences.putBoolean(ENABLED, checkBox.isSelected());
        preferences.put(DATA, panel.getXML());
    }
}
