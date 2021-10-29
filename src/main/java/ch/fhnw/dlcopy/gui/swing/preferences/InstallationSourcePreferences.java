package ch.fhnw.dlcopy.gui.swing.preferences;

import ch.fhnw.dlcopy.IsoSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.swing.InstallerPanels;
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

    private InstallerPanels installerPanels;

    public InstallationSourcePreferences(InstallerPanels installerPanels) {
        this.installerPanels = installerPanels;
    }

    @Override
    public void load() {
        installerPanels.setInstallationSource(
                preferences.getBoolean(ISO_SOURCE_SELECTED, false),
                preferences.get(ISO_SOURCE, null));
    }

    @Override
    public void save() {
        preferences.putBoolean(ISO_SOURCE_SELECTED,
                installerPanels.getSystemSource() instanceof IsoSystemSource);
        
        preferences.put(ISO_SOURCE, installerPanels.getIsoSource());
    }
}
