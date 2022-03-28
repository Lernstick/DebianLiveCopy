package ch.fhnw.dlcopy.gui.swing.preferences;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 * The preferences of the installation destination details.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallationDestinationDetailsPreferences
        extends DLCopySwingGUIPreferences {

    private final static String EXCHANGE_PARTITION_LABEL = "exchangePartitionLabel";
    private final static String AUTO_NUMBER_PATTERN = "autoNumberPattern";
    private final static String AUTO_NUMBER_START = "autoNumberStart";
    private final static String AUTO_NUMBER_INCREMENT = "autoNumberIncrement";
    private final static String AUTO_NUMBER_MIN_DIGITS = "autoNumberMinDigits";
    private final static String EXCHANGE_PARTITION_FILESYSTEM = "exchangePartitionFileSystem";
    private final static String PERSONAL_ENCRYPTION = "personalEncryption";
    private final static String SECONDARY_ENCRYPTION = "secondaryEncryption";
    private final static String RANDOM_OVERWRITE = "randomOverwrite";
    private final static String DATA_PARTITION_FILESYSTEM = "dataPartitionFileSystem";
    private final static String CHECK_COPIES = "checkCopies";

    private final JComboBox<String> exchangePartitionFileSystemComboBox;
    private final JTextField exchangePartitionTextField;
    private final JTextField autoNumberPatternTextField;
    private final JSpinner autoNumberStartSpinner;
    private final JSpinner autoNumberIncrementSpinner;
    private final JSpinner autoNumberMinDigitsSpinner;
    private final JCheckBox personalEncryptionCheckBox;
    private final JCheckBox secondaryEncryptionCheckBox;
    private final JCheckBox overwriteWithRandomDataCheckBox;
    private final JComboBox dataPartitionFileSystemComboBox;
    private final JCheckBox checkCopiesCheckBox;

    public InstallationDestinationDetailsPreferences(
            JComboBox<String> exchangePartitionFileSystemComboBox,
            JTextField exchangePartitionTextField,
            JTextField autoNumberPatternTextField,
            JSpinner autoNumberStartSpinner,
            JSpinner autoNumberIncrementSpinner,
            JSpinner autoNumberMinDigitsSpinner,
            JCheckBox personalEncryptionCheckBox,
            JCheckBox secondaryEncryptionCheckBox,
            JCheckBox overwriteWithRandomDataCheckBox,
            JComboBox dataPartitionFileSystemComboBox,
            JCheckBox checkCopiesCheckBox) {

        this.exchangePartitionFileSystemComboBox = exchangePartitionFileSystemComboBox;
        this.exchangePartitionTextField = exchangePartitionTextField;
        this.autoNumberPatternTextField = autoNumberPatternTextField;
        this.autoNumberStartSpinner = autoNumberStartSpinner;
        this.autoNumberIncrementSpinner = autoNumberIncrementSpinner;
        this.autoNumberMinDigitsSpinner = autoNumberMinDigitsSpinner;
        this.personalEncryptionCheckBox = personalEncryptionCheckBox;
        this.secondaryEncryptionCheckBox = secondaryEncryptionCheckBox;
        this.overwriteWithRandomDataCheckBox = overwriteWithRandomDataCheckBox;
        this.dataPartitionFileSystemComboBox = dataPartitionFileSystemComboBox;
        this.checkCopiesCheckBox = checkCopiesCheckBox;
    }

    @Override
    public void load() {
        exchangePartitionFileSystemComboBox.setSelectedItem(
                preferences.get(EXCHANGE_PARTITION_FILESYSTEM,
                        exchangePartitionFileSystemComboBox.getItemAt(0)));
        exchangePartitionTextField.setText(preferences.get(
                EXCHANGE_PARTITION_LABEL, STRINGS.getString("Exchange")));
        autoNumberPatternTextField.setText(
                preferences.get(AUTO_NUMBER_PATTERN, ""));
        autoNumberStartSpinner.setValue(
                preferences.getInt(AUTO_NUMBER_START, 1));
        autoNumberIncrementSpinner.setValue(
                preferences.getInt(AUTO_NUMBER_INCREMENT, 1));
        autoNumberMinDigitsSpinner.setValue(
                preferences.getInt(AUTO_NUMBER_MIN_DIGITS, 1));
        personalEncryptionCheckBox.setSelected(
                preferences.getBoolean(PERSONAL_ENCRYPTION, false));
        secondaryEncryptionCheckBox.setSelected(
                preferences.getBoolean(SECONDARY_ENCRYPTION, false));
        overwriteWithRandomDataCheckBox.setSelected(
                preferences.getBoolean(RANDOM_OVERWRITE, false));
        dataPartitionFileSystemComboBox.setSelectedItem(
                preferences.get(DATA_PARTITION_FILESYSTEM, "ext4"));
        checkCopiesCheckBox.setSelected(
                preferences.getBoolean(CHECK_COPIES, false));
    }

    @Override
    public void save() {
        preferences.put(EXCHANGE_PARTITION_FILESYSTEM,
                exchangePartitionFileSystemComboBox.getSelectedItem().toString());
        preferences.put(EXCHANGE_PARTITION_LABEL,
                exchangePartitionTextField.getText());
        preferences.put(AUTO_NUMBER_PATTERN,
                autoNumberPatternTextField.getText());
        preferences.putInt(AUTO_NUMBER_START,
                ((Number) autoNumberStartSpinner.getValue()).intValue());
        preferences.putInt(AUTO_NUMBER_INCREMENT,
                ((Number) autoNumberIncrementSpinner.getValue()).intValue());
        preferences.putInt(AUTO_NUMBER_MIN_DIGITS,
                ((Number) autoNumberMinDigitsSpinner.getValue()).intValue());
        preferences.putBoolean(PERSONAL_ENCRYPTION, 
                personalEncryptionCheckBox.isSelected());
        preferences.putBoolean(SECONDARY_ENCRYPTION, 
                secondaryEncryptionCheckBox.isSelected());
        preferences.putBoolean(RANDOM_OVERWRITE, 
                overwriteWithRandomDataCheckBox.isSelected());
        preferences.put(DATA_PARTITION_FILESYSTEM,
                dataPartitionFileSystemComboBox.getSelectedItem().toString());
        preferences.putBoolean(CHECK_COPIES, checkCopiesCheckBox.isSelected());
    }
}
