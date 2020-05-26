package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * The preferences of the installation source.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallationSourcePreferences extends DLCopySwingGUIPreferences {

    private final static String ISO_SOURCE_SELECTED = "isoSourceSelected";
    private final static String ISO_SOURCE = "isoSource";

    private final JRadioButton isoSourceRadioButton;
    private final JTextField isoSourceTextField;

    public InstallationSourcePreferences(JRadioButton isoSourceRadioButton,
            JTextField isoSourceTextField) {
        this.isoSourceRadioButton = isoSourceRadioButton;
        this.isoSourceTextField = isoSourceTextField;
    }

    @Override
    public void load() {
        isoSourceRadioButton.setSelected(preferences.getBoolean(
                ISO_SOURCE_SELECTED, false));
        isoSourceTextField.setText(preferences.get(ISO_SOURCE, null));
    }

    @Override
    public void save() {
        preferences.putBoolean(ISO_SOURCE_SELECTED,
                isoSourceRadioButton.isSelected());
        preferences.put(ISO_SOURCE, isoSourceTextField.getText());
    }
}
