package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.JCheckBox;

/**
 * The preferences of the installation data transfer details.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallationDestinationTransferPreferences
        extends DLCopySwingGUIPreferences {

    private final static String EXCHANGE = "transferExchange";
    private final static String HOME = "transferHome";
    private final static String NETWORK = "transferNetwork";
    private final static String PRINTER = "transferPrinter";
    private final static String FIREWALL = "transferFirewall";
    private final static String USER_SETTINGS = "transferUserSettings";

    private final JCheckBox exchangeCheckBox;
    private final JCheckBox homeCheckBox;
    private final JCheckBox networkCheckBox;
    private final JCheckBox printerCheckBox;
    private final JCheckBox firewallCheckBox;
    private final JCheckBox userSettingsCheckBox;

    public InstallationDestinationTransferPreferences(
            JCheckBox exchangeCheckBox, JCheckBox homeCheckBox,
            JCheckBox networkCheckBox, JCheckBox printerCheckBox,
            JCheckBox firewallCheckBox, JCheckBox userSettingsCheckBox) {

        this.exchangeCheckBox = exchangeCheckBox;
        this.homeCheckBox = homeCheckBox;
        this.networkCheckBox = networkCheckBox;
        this.printerCheckBox = printerCheckBox;
        this.firewallCheckBox = firewallCheckBox;
        this.userSettingsCheckBox = userSettingsCheckBox;
    }

    @Override
    public void load() {
        exchangeCheckBox.setSelected(preferences.getBoolean(EXCHANGE, false));
        homeCheckBox.setSelected(preferences.getBoolean(HOME, true));
        networkCheckBox.setSelected(preferences.getBoolean(NETWORK, false));
        printerCheckBox.setSelected(preferences.getBoolean(PRINTER, false));
        firewallCheckBox.setSelected(preferences.getBoolean(FIREWALL, false));
        userSettingsCheckBox.setSelected(
                preferences.getBoolean(USER_SETTINGS, false));
    }

    @Override
    public void save() {
        preferences.putBoolean(EXCHANGE, exchangeCheckBox.isSelected());
        preferences.putBoolean(HOME, homeCheckBox.isSelected());
        preferences.putBoolean(NETWORK, networkCheckBox.isSelected());
        preferences.putBoolean(PRINTER, printerCheckBox.isSelected());
        preferences.putBoolean(FIREWALL, firewallCheckBox.isSelected());
        preferences.putBoolean(
                USER_SETTINGS, userSettingsCheckBox.isSelected());
    }
}
