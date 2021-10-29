package ch.fhnw.dlcopy.gui.swing.preferences;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.Subdirectory;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JTextField;

/**
 * The preferences of the reset backup module.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetBackupPreferences extends DLCopySwingGUIPreferences {

    private final static String ENABLED = "resetBackup";
    private final static String SOURCE = "resetBackupSource";
    private final static String DESTINATION = "resetBackupDestination";
    private final static String SUBDIR_EXCHANGE_PARTITION_LABEL = "resetBackupSubdirExchangePartitionLabel";
    private final static String SUBDIR_STORAGE_MEDIA_SERIALNUMBER = "resetBackupSubdirStorageMediaSerialNumber";
    private final static String SUBDIR_TIMESTAMP = "resetBackupSubdirTimestamp";
    private final static String SUBDIR_ORDER = "resetBackupSubdirOrder";
    private final static String SUBDIR_ORDER_LABEL = "Label";
    private final static String SUBDIR_ORDER_SERIAL = "Serial";
    private final static String SUBDIR_ORDER_TIMESTAMP = "Timestamp";

    private final JCheckBox checkBox;
    private final JTextField sourceTextField;
    private final JTextField destinationTextField;
    private final Subdirectory exchangePartitionLabelSubdirectory;
    private final Subdirectory storageMediaSerialnumberSubdirectory;
    private final Subdirectory timestampSubdirectory;
    private final List<Subdirectory> orderedSubdirectoriesEntries;

    /**
     * creates a new ResetBackupPreferences
     *
     * @param checkBox the checkbox that enables backups during reset
     * @param sourceTextField the backup source text field
     * @param destinationTextField the backup destination text field
     */
    public ResetBackupPreferences(JCheckBox checkBox,
            JTextField sourceTextField, JTextField destinationTextField) {

        this.checkBox = checkBox;
        this.sourceTextField = sourceTextField;
        this.destinationTextField = destinationTextField;

        exchangePartitionLabelSubdirectory = getSubdirectory(
                SUBDIR_EXCHANGE_PARTITION_LABEL,
                "Exchange_Partition_Label");

        storageMediaSerialnumberSubdirectory = getSubdirectory(
                SUBDIR_STORAGE_MEDIA_SERIALNUMBER,
                "Storage_Media_Serial_Number");

        timestampSubdirectory = getSubdirectory(
                SUBDIR_TIMESTAMP,
                "Timestamp");

        orderedSubdirectoriesEntries = parseOrderedSubdirectoriesEntries();
    }

    @Override
    public void load() {
        checkBox.setSelected(
                preferences.getBoolean(ENABLED, false));
        sourceTextField.setText(
                preferences.get(SOURCE,
                        STRINGS.getString("Default_Backup_Directory")));
        destinationTextField.setText(
                preferences.get(DESTINATION, null));
    }

    @Override
    public void save() {
        preferences.putBoolean(ENABLED, checkBox.isSelected());
        preferences.put(SOURCE,
                sourceTextField.getText());
        preferences.put(DESTINATION,
                destinationTextField.getText());
        preferences.putBoolean(SUBDIR_EXCHANGE_PARTITION_LABEL,
                exchangePartitionLabelSubdirectory.isSelected());
        preferences.putBoolean(SUBDIR_STORAGE_MEDIA_SERIALNUMBER,
                storageMediaSerialnumberSubdirectory.isSelected());
        preferences.putBoolean(SUBDIR_TIMESTAMP,
                timestampSubdirectory.isSelected());
        preferences.put(SUBDIR_ORDER, getSubdirOrderString());
    }

    public final List<Subdirectory> getOrderedSubdirectoriesEntries() {
        return orderedSubdirectoriesEntries;
    }

    private Subdirectory getSubdirectory(
            String preferencesKey, String descriptionKey) {

        return new Subdirectory(preferences.getBoolean(preferencesKey, true),
                STRINGS.getString(descriptionKey));
    }

    private String getSubdirOrderString() {

        StringBuilder builder = new StringBuilder();

        for (Subdirectory entry : orderedSubdirectoriesEntries) {

            if (builder.length() > 0) {
                builder.append(',');
            }

            String description = entry.getDescription();
            if (description.equals(
                    STRINGS.getString("Exchange_Partition_Label"))) {
                builder.append(SUBDIR_ORDER_LABEL);

            } else if (description.equals(
                    STRINGS.getString("Storage_Media_Serial_Number"))) {
                builder.append(SUBDIR_ORDER_SERIAL);

            } else if (description.equals(
                    STRINGS.getString("Timestamp"))) {
                builder.append(SUBDIR_ORDER_TIMESTAMP);
            }
        }

        return builder.toString();
    }

    private List<Subdirectory> parseOrderedSubdirectoriesEntries() {

        List<Subdirectory> subdirectoriesEntries = new ArrayList<>();
        subdirectoriesEntries.add(exchangePartitionLabelSubdirectory);
        subdirectoriesEntries.add(storageMediaSerialnumberSubdirectory);
        subdirectoriesEntries.add(timestampSubdirectory);

        List<Subdirectory> orderedEntries = new ArrayList<>();
        String resetBackupSubdirOrder = preferences.get(
                SUBDIR_ORDER,
                SUBDIR_ORDER_LABEL + ','
                + SUBDIR_ORDER_SERIAL);
        String[] resetBackupSubdirOrderToken
                = resetBackupSubdirOrder.split(",");
        for (String token : resetBackupSubdirOrderToken) {
            if (token.equals(SUBDIR_ORDER_LABEL)) {
                subdirectoriesEntries.remove(
                        exchangePartitionLabelSubdirectory);
                orderedEntries.add(exchangePartitionLabelSubdirectory);
            } else if (token.equals(SUBDIR_ORDER_SERIAL)) {
                subdirectoriesEntries.remove(
                        storageMediaSerialnumberSubdirectory);
                orderedEntries.add(storageMediaSerialnumberSubdirectory);
            } else if (token.equals(SUBDIR_ORDER_TIMESTAMP)) {
                subdirectoriesEntries.remove(
                        timestampSubdirectory);
                orderedEntries.add(timestampSubdirectory);
            }
        }
        // After introducing new subdir entries they are not yet in the
        // preferences. Therefore we have to add all remaining entries here.
        orderedEntries.addAll(subdirectoriesEntries);

        return orderedEntries;
    }
}
