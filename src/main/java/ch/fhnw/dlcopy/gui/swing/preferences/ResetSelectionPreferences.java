package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.JRadioButton;

/**
 * The preferences of the reset selection.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetSelectionPreferences extends DLCopySwingGUIPreferences {

    private final static String AUTOMATIC = "resetAutomatic";

    private final JRadioButton automaticModeRadioButton;
    private final JRadioButton listModeRadioButton;

    /**
     * creates new ResetSelectionPreferences
     *
     * @param automaticModeRadioButton the radio button for automatic mode
     * @param listModeRadioButton the radio button for list mode
     */
    public ResetSelectionPreferences(JRadioButton automaticModeRadioButton,
            JRadioButton listModeRadioButton) {

        this.automaticModeRadioButton = automaticModeRadioButton;
        this.listModeRadioButton = listModeRadioButton;
    }

    @Override
    public void load() {
        if (preferences.getBoolean(AUTOMATIC, false)) {
            automaticModeRadioButton.setSelected(true);
        } else {
            listModeRadioButton.setSelected(true);
        }
    }

    @Override
    public void save() {
        preferences.putBoolean(AUTOMATIC,
                automaticModeRadioButton.isSelected());
    }
}
