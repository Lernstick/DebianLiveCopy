package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JTextField;

/**
 * The preferences of the upgrade module.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpgradePreferences extends DLCopySwingGUIPreferences {

    private final static String UPGRADE_SYSTEM_PARTITION = "upgradeSystemPartition";
    private final static String UPGRADE_RESET_DATA_PARTITION = "upgradeResetDataPartition";
    private final static String REACTIVATE_WELCOME = "reactivateWelcome";
    private final static String KEEP_PRINTER_SETTINGS = "keepPrinterSettings";
    private final static String KEEP_NETWORK_SETTINGS = "keepNetworkSettings";
    private final static String KEEP_FIREWALL_SETTINGS = "keepFirewallSettings";
    private final static String KEEP_USER_SETTINGS = "keepUserSettings";
    private final static String REMOVE_HIDDEN_FILES = "removeHiddenFiles";
    private final static String AUTOMATIC_BACKUP = "automaticBackup";
    private final static String BACKUP_DESTINATION = "backupDestination";
    private final static String AUTO_REMOVE_BACKUP = "autoRemoveBackup";
    private final static String UPGRADE_OVERWRITE_LIST = "upgradeOverwriteList";

    private final JCheckBox upgradeSystemPartitionCheckBox;
    private final JCheckBox resetDataPartitionCheckBox;
    private final JCheckBox keepPrinterSettingsCheckBox;
    private final JCheckBox keepNetworkSettingsCheckBox;
    private final JCheckBox keepFirewallSettingsCheckBox;
    private final JCheckBox keepUserSettingsCheckBox;
    private final JCheckBox reactivateWelcomeCheckBox;
    private final JCheckBox removeHiddenFilesCheckBox;
    private final JCheckBox automaticBackupCheckBox;
    private final JTextField automaticBackupTextField;
    private final JCheckBox automaticBackupRemoveCheckBox;
    private final DefaultListModel<String> upgradeOverwriteListModel;

    public UpgradePreferences(JCheckBox upgradeSystemPartitionCheckBox,
            JCheckBox resetDataPartitionCheckBox,
            JCheckBox keepPrinterSettingsCheckBox,
            JCheckBox keepNetworkSettingsCheckBox,
            JCheckBox keepFirewallSettingsCheckBox,
            JCheckBox keepUserSettingsCheckBox,
            JCheckBox reactivateWelcomeCheckBox,
            JCheckBox removeHiddenFilesCheckBox,
            JCheckBox automaticBackupCheckBox,
            JTextField automaticBackupTextField,
            JCheckBox automaticBackupRemoveCheckBox,
            DefaultListModel<String> upgradeOverwriteListModel) {

        this.upgradeSystemPartitionCheckBox = upgradeSystemPartitionCheckBox;
        this.resetDataPartitionCheckBox = resetDataPartitionCheckBox;
        this.keepPrinterSettingsCheckBox = keepPrinterSettingsCheckBox;
        this.keepNetworkSettingsCheckBox = keepNetworkSettingsCheckBox;
        this.keepFirewallSettingsCheckBox = keepFirewallSettingsCheckBox;
        this.keepUserSettingsCheckBox = keepUserSettingsCheckBox;
        this.reactivateWelcomeCheckBox = reactivateWelcomeCheckBox;
        this.removeHiddenFilesCheckBox = removeHiddenFilesCheckBox;
        this.automaticBackupCheckBox = automaticBackupCheckBox;
        this.automaticBackupTextField = automaticBackupTextField;
        this.automaticBackupRemoveCheckBox = automaticBackupRemoveCheckBox;
        this.upgradeOverwriteListModel = upgradeOverwriteListModel;
    }

    @Override
    public void load() {
        // upgrade -> details -> options
        upgradeSystemPartitionCheckBox.setSelected(
                preferences.getBoolean(UPGRADE_SYSTEM_PARTITION, true));
        resetDataPartitionCheckBox.setSelected(
                preferences.getBoolean(UPGRADE_RESET_DATA_PARTITION, true));
        keepPrinterSettingsCheckBox.setSelected(
                preferences.getBoolean(KEEP_PRINTER_SETTINGS, true));
        keepNetworkSettingsCheckBox.setSelected(
                preferences.getBoolean(KEEP_NETWORK_SETTINGS, true));
        keepFirewallSettingsCheckBox.setSelected(
                preferences.getBoolean(KEEP_FIREWALL_SETTINGS, true));
        keepUserSettingsCheckBox.setSelected(
                preferences.getBoolean(KEEP_USER_SETTINGS, true));
        reactivateWelcomeCheckBox.setSelected(
                preferences.getBoolean(REACTIVATE_WELCOME, true));
        removeHiddenFilesCheckBox.setSelected(
                preferences.getBoolean(REMOVE_HIDDEN_FILES, false));
        automaticBackupCheckBox.setSelected(
                preferences.getBoolean(AUTOMATIC_BACKUP, false));
        automaticBackupTextField.setText(
                preferences.get(BACKUP_DESTINATION, null));
        automaticBackupRemoveCheckBox.setSelected(
                preferences.getBoolean(AUTO_REMOVE_BACKUP, false));

        // upgrade -> details -> always overwrite
        String upgradeOverWriteList = preferences.get(
                UPGRADE_OVERWRITE_LIST, "");
        fillUpgradeOverwriteList(upgradeOverWriteList);
    }

    @Override
    public void save() {
        // upgrade -> details -> options
        preferences.putBoolean(UPGRADE_SYSTEM_PARTITION,
                upgradeSystemPartitionCheckBox.isSelected());
        preferences.putBoolean(UPGRADE_RESET_DATA_PARTITION,
                resetDataPartitionCheckBox.isSelected());
        preferences.putBoolean(KEEP_PRINTER_SETTINGS,
                keepPrinterSettingsCheckBox.isSelected());
        preferences.putBoolean(KEEP_NETWORK_SETTINGS,
                keepNetworkSettingsCheckBox.isSelected());
        preferences.putBoolean(KEEP_FIREWALL_SETTINGS,
                keepFirewallSettingsCheckBox.isSelected());
        preferences.putBoolean(KEEP_USER_SETTINGS,
                keepUserSettingsCheckBox.isSelected());
        preferences.putBoolean(REACTIVATE_WELCOME,
                reactivateWelcomeCheckBox.isSelected());
        preferences.putBoolean(REMOVE_HIDDEN_FILES,
                removeHiddenFilesCheckBox.isSelected());
        preferences.putBoolean(AUTOMATIC_BACKUP,
                automaticBackupCheckBox.isSelected());
        preferences.put(BACKUP_DESTINATION,
                automaticBackupTextField.getText());
        preferences.putBoolean(AUTO_REMOVE_BACKUP,
                automaticBackupRemoveCheckBox.isSelected());

        // upgrade -> details -> always overwrite
        preferences.put(UPGRADE_OVERWRITE_LIST,
                getUpgradeOverwriteListString());
    }

    private void fillUpgradeOverwriteList(String list) {
        if (!list.isEmpty()) {
            String[] upgradeOverWriteTokens = list.split("\n");
            for (String upgradeOverWriteToken : upgradeOverWriteTokens) {
                upgradeOverwriteListModel.addElement(upgradeOverWriteToken);
            }
        }
    }

    private String getUpgradeOverwriteListString() {

        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0, size = upgradeOverwriteListModel.size();
                i < size; i++) {

            String entry = upgradeOverwriteListModel.get(i);
            stringBuilder.append(entry);
            if (i != (size - 1)) {
                stringBuilder.append('\n');
            }
        }

        return stringBuilder.toString();
    }
}
