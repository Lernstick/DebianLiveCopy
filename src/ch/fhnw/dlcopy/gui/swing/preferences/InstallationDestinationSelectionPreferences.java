package ch.fhnw.dlcopy.gui.swing.preferences;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSlider;

/**
 * The preferences of the installation destination selection.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallationDestinationSelectionPreferences
        extends DLCopySwingGUIPreferences {

    private final static String COPY_EXCHANGE_PARTITION = "copyExchangePartition";
    private final static String EXPLICIT_EXCHANGE_SIZE = "explicitExchangeSize";
    private final static String DATA_PARTITION_MODE = "dataPartitionMode";
    private final static String COPY_DATA_PARTITION = "copyDataPartition";

    private final JCheckBox copyExchangePartitionCheckBox;
    private final JSlider exchangePartitionSizeSlider;
    private final JCheckBox copyDataPartitionCheckBox;
    private final JComboBox<String> dataPartitionModeComboBox;

    public InstallationDestinationSelectionPreferences(
            JCheckBox copyExchangePartitionCheckBox,
            JSlider exchangePartitionSizeSlider,
            JCheckBox copyDataPartitionCheckBox,
            JComboBox<String> dataPartitionModeComboBox) {

        this.copyExchangePartitionCheckBox = copyExchangePartitionCheckBox;
        this.exchangePartitionSizeSlider = exchangePartitionSizeSlider;
        this.copyDataPartitionCheckBox = copyDataPartitionCheckBox;
        this.dataPartitionModeComboBox = dataPartitionModeComboBox;
    }

    @Override
    public void load() {
        copyExchangePartitionCheckBox.setSelected(
                preferences.getBoolean(COPY_EXCHANGE_PARTITION, false));
        exchangePartitionSizeSlider.setValue(
                preferences.getInt(EXPLICIT_EXCHANGE_SIZE, 0));
        copyDataPartitionCheckBox.setSelected(
                preferences.getBoolean(COPY_DATA_PARTITION, false));
        dataPartitionModeComboBox.setSelectedIndex(
                preferences.getInt(DATA_PARTITION_MODE, 0));
    }

    @Override
    public void save() {
        preferences.putBoolean(COPY_EXCHANGE_PARTITION,
                copyExchangePartitionCheckBox.isSelected());
        preferences.putBoolean(COPY_DATA_PARTITION,
                copyDataPartitionCheckBox.isSelected());
        preferences.putInt(DATA_PARTITION_MODE,
                dataPartitionModeComboBox.getSelectedIndex());
    }

    public int getExplicitExchangeSize() {
        return preferences.getInt(EXPLICIT_EXCHANGE_SIZE, 0);
    }

    public void saveExplicitExchangeSize(int explicitExchangeSize) {
        preferences.putInt(EXPLICIT_EXCHANGE_SIZE, explicitExchangeSize);
    }
}
