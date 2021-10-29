package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.JComboBox;

/**
 * The preferences of the main menu.
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class MainMenuPreferences extends DLCopySwingGUIPreferences {

    private final static String JUMP_TO = "jumpTo";
    private final JComboBox jumpComboBox;
    
    public MainMenuPreferences(JComboBox jumpComboBox) {
        this.jumpComboBox = jumpComboBox;
    }

    @Override
    public void load() {
        jumpComboBox.setSelectedIndex(preferences.getInt(JUMP_TO, 0));
    }

    @Override
    public void save() {
        preferences.putInt(JUMP_TO, jumpComboBox.getSelectedIndex());
    }
}
