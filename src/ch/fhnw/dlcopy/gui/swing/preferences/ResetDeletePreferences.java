package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * The preferences of the reset delete module.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetDeletePreferences extends DLCopySwingGUIPreferences {

    private final static String FORMAT_EXCHANGE_PARTITION = "resetformatExchangePartition";
    private final static String FILE_SYSTEM = "resetFormatExchangePartitionFileSystem";
    private final static String KEEP_LABEL = "resetFormatExchangePartitionKeepLabel";
    private final static String NEW_LABEL = "resetFormatExchangePartitionNewLabel";
    private final static String DELETE_ON_DATA_PARTITION = "resetDeleteOnDataPartition";
    private final static String FORMAT_DATA_PARTITION = "resetformatDataPartition";
    private final static String REMOVE_SYSTEM_FILES = "resetRemoveSystemFiles";
    private final static String REMOVE_HOME_DIRECTORY = "resetRemoveHomeDirectory";

    private final JCheckBox formatExchangePartitionCheckBox;
    private final JComboBox fileSystemComboBox;
    private final JRadioButton keepLabelRadioButton;
    private final JRadioButton newLabelRadioButton;
    private final JTextField newLabelTextField;
    private final JCheckBox deleteOnDataPartitionCheckBox;
    private final JRadioButton formatDataPartitionRadioButton;
    private final JRadioButton removeFilesRadioButton;
    private final JCheckBox systemFilesCheckBox;
    private final JCheckBox homeDirectoryCheckBox;

    public ResetDeletePreferences(JCheckBox formatExchangePartitionCheckBox,
            JComboBox fileSystemComboBox, JRadioButton keepLabelRadioButton,
            JRadioButton newLabelRadioButton, JTextField newLabelTextField,
            JCheckBox deleteOnDataPartitionCheckBox,
            JRadioButton formatDataPartitionRadioButton,
            JRadioButton removeFilesRadioButton,
            JCheckBox systemFilesCheckBox, JCheckBox homeDirectoryCheckBox) {

        this.formatExchangePartitionCheckBox = formatExchangePartitionCheckBox;
        this.fileSystemComboBox = fileSystemComboBox;
        this.keepLabelRadioButton = keepLabelRadioButton;
        this.newLabelRadioButton = newLabelRadioButton;
        this.newLabelTextField = newLabelTextField;
        this.deleteOnDataPartitionCheckBox = deleteOnDataPartitionCheckBox;
        this.formatDataPartitionRadioButton = formatDataPartitionRadioButton;
        this.removeFilesRadioButton = removeFilesRadioButton;
        this.systemFilesCheckBox = systemFilesCheckBox;
        this.homeDirectoryCheckBox = homeDirectoryCheckBox;
    }

    @Override
    public void load() {

        formatExchangePartitionCheckBox.setSelected(
                preferences.getBoolean(FORMAT_EXCHANGE_PARTITION, false));

        fileSystemComboBox.setSelectedItem(
                preferences.get(FILE_SYSTEM,
                        fileSystemComboBox.getItemAt(0).toString()));

        if (preferences.getBoolean(KEEP_LABEL, true)) {
            keepLabelRadioButton.setSelected(true);
        } else {
            newLabelRadioButton.setSelected(true);
        }

        newLabelTextField.setText(preferences.get(
                NEW_LABEL, null));

        deleteOnDataPartitionCheckBox.setSelected(
                preferences.getBoolean(DELETE_ON_DATA_PARTITION, false));

        if (preferences.getBoolean(FORMAT_DATA_PARTITION, false)) {
            formatDataPartitionRadioButton.setSelected(true);
        } else {
            removeFilesRadioButton.setSelected(true);
        }

        systemFilesCheckBox.setSelected(
                preferences.getBoolean(REMOVE_SYSTEM_FILES, false));

        homeDirectoryCheckBox.setSelected(
                preferences.getBoolean(REMOVE_HOME_DIRECTORY, false));
    }

    @Override
    public void save() {

        preferences.putBoolean(FORMAT_EXCHANGE_PARTITION,
                formatExchangePartitionCheckBox.isSelected());

        preferences.put(FILE_SYSTEM,
                fileSystemComboBox.getSelectedItem().toString());

        preferences.putBoolean(KEEP_LABEL, keepLabelRadioButton.isSelected());

        preferences.put(NEW_LABEL, newLabelTextField.getText());

        preferences.putBoolean(DELETE_ON_DATA_PARTITION,
                deleteOnDataPartitionCheckBox.isSelected());

        preferences.putBoolean(FORMAT_DATA_PARTITION,
                formatDataPartitionRadioButton.isSelected());

        preferences.putBoolean(REMOVE_SYSTEM_FILES,
                systemFilesCheckBox.isSelected());

        preferences.putBoolean(REMOVE_HOME_DIRECTORY,
                homeDirectoryCheckBox.isSelected());
    }
}
