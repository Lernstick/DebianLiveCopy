package ch.fhnw.dlcopy.gui.swing;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.Resetter;
import ch.fhnw.dlcopy.Subdirectory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Stores and restores the preferences of DLCopySwingGUI
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class DLCopySwingGUIPreferences {

    private static final Logger LOGGER = Logger.getLogger(
            DLCopySwingGUIPreferences.class.getName());

    private final Preferences preferences
            = Preferences.userNodeForPackage(DLCopySwingGUI.class);

    private final static String JUMP_TO = "jumpTo";
    private final static String ISO_SOURCE_SELECTED = "isoSourceSelected";
    private final static String ISO_SOURCE = "isoSource";
    private final static String EXPLICIT_EXCHANGE_SIZE = "explicitExchangeSize";
    private final static String EXCHANGE_PARTITION_LABEL = "exchangePartitionLabel";
    private final static String AUTO_NUMBER_PATTERN = "autoNumberPattern";
    private final static String AUTO_NUMBER_START = "autoNumberStart";
    private final static String AUTO_NUMBER_INCREMENT = "autoNumberIncrement";
    private final static String AUTO_NUMBER_MIN_DIGITS = "autoNumberMinDigits";
    private final static String EXCHANGE_PARTITION_FILESYSTEM = "exchangePartitionFileSystem";
    private final static String COPY_EXCHANGE_PARTITION = "copyExchangePartition";
    private final static String DATA_PARTITION_FILESYSTEM = "dataPartitionFileSystem";
    private final static String CHECK_COPIES = "checkCopies";
    private final static String DATA_PARTITION_MODE = "dataPartitionMode";
    private final static String COPY_DATA_PARTITION = "copyDataPartition";
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
    private final static String RESET_AUTOMATIC = "resetAutomatic";
    private final static String PRINT_DOCUMENTS = "printDocuments";
    private final static String PRINTING_DIRECTORIES = "printingDirectories";
    private final static String SCAN_DIRECTORIES_RECURSIVELY = "scanDirectoriesRecursively";
    private final static String PRINT_ODT = "printODT";
    private final static String PRINT_ODS = "printODS";
    private final static String PRINT_ODP = "printODP";
    private final static String PRINT_PDF = "printPDF";
    private final static String PRINT_DOC = "printDOC";
    private final static String PRINT_DOCX = "printDOCX";
    private final static String PRINT_XLS = "printXLS";
    private final static String PRINT_XLSX = "printXLSX";
    private final static String PRINT_PPT = "printPPT";
    private final static String PRINT_PPTX = "printPPTX";
    private final static String AUTO_PRINT_MODE = "autoPrintMode";
    private final static String PRINT_COPIES = "printCopies";
    private final static String PRINT_DUPLEX = "printDuplex";
    private final static String RESET_BACKUP = "resetBackup";
    private final static String RESET_BACKUP_SOURCE = "resetBackupSource";
    private final static String RESET_BACKUP_DESTINATION = "resetBackupDestination";
    private final static String RESET_BACKUP_SUBDIR_EXCHANGE_PARTITION_LABEL = "resetBackupSubdirExchangePartitionLabel";
    private final static String RESET_BACKUP_SUBDIR_STORAGE_MEDIA_SERIALNUMBER = "resetBackupSubdirStorageMediaSerialNumber";
    private final static String RESET_BACKUP_SUBDIR_TIMESTAMP = "resetBackupSubdirTimestamp";
    private final static String RESET_BACKUP_SUBDIR_ORDER = "resetBackupSubdirOrder";
    private final static String RESET_BACKUP_SUBDIR_ORDER_LABEL = "Label";
    private final static String RESET_BACKUP_SUBDIR_ORDER_SERIAL = "Serial";
    private final static String RESET_BACKUP_SUBDIR_ORDER_TIMESTAMP = "Timestamp";
    private final static String RESET_FORMAT_EXCHANGE_PARTITION = "resetformatExchangePartition";
    private final static String RESET_FORMAT_EXCHANGE_PARTITION_FILE_SYSTEM = "resetFormatExchangePartitionFileSystem";
    private final static String RESET_FORMAT_EXCHANGE_PARTITION_KEEP_LABEL = "resetFormatExchangePartitionKeepLabel";
    private final static String RESET_FORMAT_EXCHANGE_PARTITION_NEW_LABEL = "resetFormatExchangePartitionNewLabel";
    private final static String RESET_DELETE_ON_DATA_PARTITION = "resetDeleteOnDataPartition";
    private final static String RESET_FORMAT_DATA_PARTITION = "resetformatDataPartition";
    private final static String RESET_REMOVE_SYSTEM_FILES = "resetRemoveSystemFiles";
    private final static String RESET_REMOVE_HOME_DIRECTORY = "resetRemoveHomeDirectory";
    private final static String RESET_RESTORE_ENABLED = "resetRestoreEnabled";
    private final static String RESET_RESTORE_DATA = "resetRestoreData";
    private final static String TRANSFER_EXCHANGE = "transferExchange";
    private final static String TRANSFER_HOME = "transferHome";
    private final static String TRANSFER_NETWORK = "transferNetwork";
    private final static String TRANSFER_PRINTER = "transferPrinter";
    private final static String TRANSFER_FIREWALL = "transferFirewall";
    private final static String TRANSFER_USER_SETTINGS = "transferUserSettings";

    private final JComboBox jumpComboBox;
    private final JRadioButton isoSourceRadioButton;
    private final JTextField isoSourceTextField;
    private final JSlider exchangePartitionSizeSlider;
    private final JTextField exchangePartitionTextField;
    private final JTextField autoNumberPatternTextField;
    private final JSpinner autoNumberStartSpinner;
    private final JSpinner autoNumberIncrementSpinner;
    private final JSpinner autoNumberMinDigitsSpinner;
    private final JComboBox exchangePartitionFileSystemComboBox;
    private final JCheckBox copyExchangePartitionCheckBox;
    private final JComboBox dataPartitionFileSystemComboBox;
    private final JCheckBox checkCopiesCheckBox;
    private final JComboBox<String> dataPartitionModeComboBox;
    private final JCheckBox copyDataPartitionCheckBox;
    private final JCheckBox upgradeSystemPartitionCheckBox;
    private final JCheckBox resetDataPartitionCheckBox;
    private final JCheckBox reactivateWelcomeCheckBox;
    private final JCheckBox keepPrinterSettingsCheckBox;
    private final JCheckBox keepNetworkSettingsCheckBox;
    private final JCheckBox keepFirewallSettingsCheckBox;
    private final JCheckBox keepUserSettingsCheckBox;
    private final JCheckBox automaticBackupCheckBox;
    private final JCheckBox automaticBackupTextField;
    private final JCheckBox removeHiddenFilesCheckBox;
    private final JCheckBox automaticBackupRemoveCheckBox;
    private final DefaultListModel<String> upgradeOverwriteListModel;
    private final JRadioButton resetAutomaticModeRadioButton;
    private final JRadioButton resetListModeRadioButton;
    private final JComboBox<String> isoDataPartitionModeComboBox;
    private final JCheckBox printDocumentsCheckBox;
    private final JTextArea printingDirectoriesTextArea;
    private final JCheckBox scanDirectoriesRecursivelyCheckBox;
    private final JCheckBox printOdtCheckBox;
    private final JCheckBox printOdsCheckBox;
    private final JCheckBox printOdpCheckBox;
    private final JCheckBox printPdfCheckBox;
    private final JCheckBox printDocCheckBox;
    private final JCheckBox printDocxCheckBox;
    private final JCheckBox printXlsCheckBox;
    private final JCheckBox printXlsxCheckBox;
    private final JCheckBox printPptCheckBox;
    private final JCheckBox printPptxCheckBox;
    private final JRadioButton autoPrintAllDocumentsRadioButton;
    private final JRadioButton autoPrintSingleDocumentsRadioButton;
    private final JRadioButton autoPrintNoneRadioButton;
    private final JSpinner printCopiesSpinner;
    private final JCheckBox printDuplexCheckBox;
    private final JCheckBox resetBackupCheckBox;
    private final JTextField resetBackupSourceTextField;
    private final JTextField resetBackupDestinationTextField;
    private final Subdirectory exchangePartitionLabelSubdirectory;
    private final Subdirectory storageMediaSerialnumberSubdirectory;
    private final Subdirectory timestampSubdirectory;
    private final List<Subdirectory> orderedSubdirectoriesEntries;
    private final JCheckBox resetFormatExchangePartitionCheckBox;
    private final JComboBox resetFormatExchangePartitionFileSystemComboBox;
    private final JRadioButton resetFormatExchangePartitionKeepLabelRadioButton;
    private final JRadioButton resetFormatExchangePartitionNewLabelRadioButton;
    private final JTextField resetFormatExchangePartitionNewLabelTextField;
    private final JCheckBox deleteOnDataPartitionCheckBox;
    private final JRadioButton formatDataPartitionRadioButton;
    private final JRadioButton removeFilesRadioButton;
    private final JCheckBox systemFilesCheckBox;
    private final JCheckBox homeDirectoryCheckBox;
    private final JCheckBox resetRestoreDataCheckBox;
    private final OverwriteConfigurationPanel resetRestoreConfigurationPanel;
    private final JCheckBox transferExchangeCheckBox;
    private final JCheckBox transferHomeCheckBox;
    private final JCheckBox transferNetworkCheckBox;
    private final JCheckBox transferPrinterCheckBox;
    private final JCheckBox transferFirewallCheckBox;
    private final JCheckBox transferUserSettingsCheckBox;

    public DLCopySwingGUIPreferences(JComboBox jumpComboBox,
            JRadioButton isoSourceRadioButton, JTextField isoSourceTextField,
            JSlider exchangePartitionSizeSlider,
            JTextField exchangePartitionTextField,
            JTextField autoNumberPatternTextField,
            JSpinner autoNumberStartSpinner,
            JSpinner autoNumberIncrementSpinner,
            JSpinner autoNumberMinDigitsSpinner,
            JComboBox exchangePartitionFileSystemComboBox,
            JCheckBox copyExchangePartitionCheckBox,
            JComboBox dataPartitionFileSystemComboBox,
            JCheckBox checkCopiesCheckBox,
            JComboBox<String> dataPartitionModeComboBox,
            JCheckBox copyDataPartitionCheckBox,
            JCheckBox upgradeSystemPartitionCheckBox,
            JCheckBox resetDataPartitionCheckBox,
            JCheckBox reactivateWelcomeCheckBox,
            JCheckBox keepPrinterSettingsCheckBox,
            JCheckBox keepNetworkSettingsCheckBox,
            JCheckBox keepFirewallSettingsCheckBox,
            JCheckBox keepUserSettingsCheckBox,
            JCheckBox automaticBackupCheckBox,
            JCheckBox automaticBackupTextField,
            JCheckBox removeHiddenFilesCheckBox,
            JCheckBox automaticBackupRemoveCheckBox,
            DefaultListModel<String> upgradeOverwriteListModel,
            JRadioButton resetAutomaticModeRadioButton,
            JRadioButton resetListModeRadioButton,
            JComboBox<String> isoDataPartitionModeComboBox,
            JCheckBox printDocumentsCheckBox,
            JTextArea printingDirectoriesTextArea,
            JCheckBox scanDirectoriesRecursivelyCheckBox,
            JCheckBox printOdtCheckBox, JCheckBox printOdsCheckBox,
            JCheckBox printOdpCheckBox, JCheckBox printPdfCheckBox,
            JCheckBox printDocCheckBox, JCheckBox printDocxCheckBox,
            JCheckBox printXlsCheckBox, JCheckBox printXlsxCheckBox,
            JCheckBox printPptCheckBox, JCheckBox printPptxCheckBox,
            JRadioButton autoPrintAllDocumentsRadioButton,
            JRadioButton autoPrintSingleDocumentsRadioButton,
            JRadioButton autoPrintNoneRadioButton, JSpinner printCopiesSpinner,
            JCheckBox printDuplexCheckBox, JCheckBox resetBackupCheckBox,
            JTextField resetBackupSourceTextField,
            JTextField resetBackupDestinationTextField,
            JCheckBox resetFormatExchangePartitionCheckBox,
            JComboBox resetFormatExchangePartitionFileSystemComboBox,
            ComboBoxModel<String> exchangePartitionFileSystemsModel,
            JRadioButton resetFormatExchangePartitionKeepLabelRadioButton,
            JRadioButton resetFormatExchangePartitionNewLabelRadioButton,
            JTextField resetFormatExchangePartitionNewLabelTextField,
            JCheckBox deleteOnDataPartitionCheckBox,
            JRadioButton formatDataPartitionRadioButton,
            JRadioButton removeFilesRadioButton,
            JCheckBox systemFilesCheckBox, JCheckBox homeDirectoryCheckBox,
            JCheckBox resetRestoreDataCheckBox,
            OverwriteConfigurationPanel resetRestoreConfigurationPanel,
            JCheckBox transferExchangeCheckBox, JCheckBox transferHomeCheckBox,
            JCheckBox transferNetworkCheckBox,
            JCheckBox transferPrinterCheckBox,
            JCheckBox transferFirewallCheckBox,
            JCheckBox transferUserSettingsCheckBox) {

        this.jumpComboBox = jumpComboBox;
        this.isoSourceRadioButton = isoSourceRadioButton;
        this.isoSourceTextField = isoSourceTextField;
        this.exchangePartitionSizeSlider = exchangePartitionSizeSlider;
        this.exchangePartitionTextField = exchangePartitionTextField;
        this.autoNumberPatternTextField = autoNumberPatternTextField;
        this.autoNumberStartSpinner = autoNumberStartSpinner;
        this.autoNumberIncrementSpinner = autoNumberIncrementSpinner;
        this.autoNumberMinDigitsSpinner = autoNumberMinDigitsSpinner;
        this.exchangePartitionFileSystemComboBox = exchangePartitionFileSystemComboBox;
        this.copyExchangePartitionCheckBox = copyExchangePartitionCheckBox;
        this.dataPartitionFileSystemComboBox = dataPartitionFileSystemComboBox;
        this.checkCopiesCheckBox = checkCopiesCheckBox;
        this.dataPartitionModeComboBox = dataPartitionModeComboBox;
        this.copyDataPartitionCheckBox = copyDataPartitionCheckBox;
        this.upgradeSystemPartitionCheckBox = upgradeSystemPartitionCheckBox;
        this.resetDataPartitionCheckBox = resetDataPartitionCheckBox;
        this.reactivateWelcomeCheckBox = reactivateWelcomeCheckBox;
        this.keepPrinterSettingsCheckBox = keepPrinterSettingsCheckBox;
        this.keepNetworkSettingsCheckBox = keepNetworkSettingsCheckBox;
        this.keepFirewallSettingsCheckBox = keepFirewallSettingsCheckBox;
        this.keepUserSettingsCheckBox = keepUserSettingsCheckBox;
        this.automaticBackupCheckBox = automaticBackupCheckBox;
        this.automaticBackupTextField = automaticBackupTextField;
        this.removeHiddenFilesCheckBox = removeHiddenFilesCheckBox;
        this.automaticBackupRemoveCheckBox = automaticBackupRemoveCheckBox;
        this.upgradeOverwriteListModel = upgradeOverwriteListModel;
        this.resetAutomaticModeRadioButton = resetAutomaticModeRadioButton;
        this.resetListModeRadioButton = resetListModeRadioButton;
        this.isoDataPartitionModeComboBox = isoDataPartitionModeComboBox;
        this.printDocumentsCheckBox = printDocumentsCheckBox;
        this.printingDirectoriesTextArea = printingDirectoriesTextArea;
        this.scanDirectoriesRecursivelyCheckBox = scanDirectoriesRecursivelyCheckBox;
        this.printOdtCheckBox = printOdtCheckBox;
        this.printOdsCheckBox = printOdsCheckBox;
        this.printOdpCheckBox = printOdpCheckBox;
        this.printPdfCheckBox = printPdfCheckBox;
        this.printDocCheckBox = printDocCheckBox;
        this.printDocxCheckBox = printDocxCheckBox;
        this.printXlsCheckBox = printXlsCheckBox;
        this.printXlsxCheckBox = printXlsxCheckBox;
        this.printPptCheckBox = printPptCheckBox;
        this.printPptxCheckBox = printPptxCheckBox;
        this.autoPrintAllDocumentsRadioButton = autoPrintAllDocumentsRadioButton;
        this.autoPrintSingleDocumentsRadioButton = autoPrintSingleDocumentsRadioButton;
        this.autoPrintNoneRadioButton = autoPrintNoneRadioButton;
        this.printCopiesSpinner = printCopiesSpinner;
        this.printDuplexCheckBox = printDuplexCheckBox;
        this.resetBackupCheckBox = resetBackupCheckBox;
        this.resetBackupSourceTextField = resetBackupSourceTextField;
        this.resetBackupDestinationTextField = resetBackupDestinationTextField;
        this.resetFormatExchangePartitionCheckBox = resetFormatExchangePartitionCheckBox;
        this.resetFormatExchangePartitionFileSystemComboBox = resetFormatExchangePartitionFileSystemComboBox;
        this.resetFormatExchangePartitionKeepLabelRadioButton = resetFormatExchangePartitionKeepLabelRadioButton;
        this.resetFormatExchangePartitionNewLabelRadioButton = resetFormatExchangePartitionNewLabelRadioButton;
        this.resetFormatExchangePartitionNewLabelTextField = resetFormatExchangePartitionNewLabelTextField;
        this.deleteOnDataPartitionCheckBox = deleteOnDataPartitionCheckBox;
        this.formatDataPartitionRadioButton = formatDataPartitionRadioButton;
        this.removeFilesRadioButton = removeFilesRadioButton;
        this.systemFilesCheckBox = systemFilesCheckBox;
        this.homeDirectoryCheckBox = homeDirectoryCheckBox;
        this.resetRestoreDataCheckBox = resetRestoreDataCheckBox;
        this.resetRestoreConfigurationPanel = resetRestoreConfigurationPanel;
        this.transferExchangeCheckBox = transferExchangeCheckBox;
        this.transferHomeCheckBox = transferHomeCheckBox;
        this.transferNetworkCheckBox = transferNetworkCheckBox;
        this.transferPrinterCheckBox = transferPrinterCheckBox;
        this.transferFirewallCheckBox = transferFirewallCheckBox;
        this.transferUserSettingsCheckBox = transferUserSettingsCheckBox;

        exchangePartitionLabelSubdirectory = getSubdirectory(
                RESET_BACKUP_SUBDIR_EXCHANGE_PARTITION_LABEL,
                "Exchange_Partition_Label");
        storageMediaSerialnumberSubdirectory = getSubdirectory(
                RESET_BACKUP_SUBDIR_STORAGE_MEDIA_SERIALNUMBER,
                "Storage_Media_Serial_Number");
        timestampSubdirectory = getSubdirectory(
                RESET_BACKUP_SUBDIR_TIMESTAMP,
                "Timestamp");
        orderedSubdirectoriesEntries = getOrderedSubdirectoriesEntries();

        loadPreferences(exchangePartitionFileSystemsModel);
    }

    public final List<Subdirectory> getOrderedSubdirectoriesEntries() {

        List<Subdirectory> subdirectoriesEntries = new ArrayList<>();
        subdirectoriesEntries.add(exchangePartitionLabelSubdirectory);
        subdirectoriesEntries.add(storageMediaSerialnumberSubdirectory);
        subdirectoriesEntries.add(timestampSubdirectory);

        List<Subdirectory> orderedEntries = new ArrayList<>();
        String resetBackupSubdirOrder = preferences.get(
                RESET_BACKUP_SUBDIR_ORDER,
                RESET_BACKUP_SUBDIR_ORDER_LABEL + ','
                + RESET_BACKUP_SUBDIR_ORDER_SERIAL);
        String[] resetBackupSubdirOrderToken
                = resetBackupSubdirOrder.split(",");
        for (String token : resetBackupSubdirOrderToken) {
            if (token.equals(RESET_BACKUP_SUBDIR_ORDER_LABEL)) {
                subdirectoriesEntries.remove(
                        exchangePartitionLabelSubdirectory);
                orderedEntries.add(exchangePartitionLabelSubdirectory);
            } else if (token.equals(RESET_BACKUP_SUBDIR_ORDER_SERIAL)) {
                subdirectoriesEntries.remove(
                        storageMediaSerialnumberSubdirectory);
                orderedEntries.add(storageMediaSerialnumberSubdirectory);
            } else if (token.equals(RESET_BACKUP_SUBDIR_ORDER_TIMESTAMP)) {
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

    public Resetter.AutoPrintMode getAutoPrintMode() {
        if (autoPrintAllDocumentsRadioButton.isSelected()) {
            return Resetter.AutoPrintMode.ALL;
        } else if (autoPrintSingleDocumentsRadioButton.isSelected()) {
            return Resetter.AutoPrintMode.SINGLE;
        } else {
            return Resetter.AutoPrintMode.NONE;
        }
    }

    public void savePreferences(int explicitExchangeSize) {

        preferences.putInt(JUMP_TO, jumpComboBox.getSelectedIndex());
        preferences.putBoolean(ISO_SOURCE_SELECTED,
                isoSourceRadioButton.isSelected());
        preferences.put(ISO_SOURCE, isoSourceTextField.getText());
        preferences.putInt(EXPLICIT_EXCHANGE_SIZE, explicitExchangeSize);
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
        preferences.put(EXCHANGE_PARTITION_FILESYSTEM,
                exchangePartitionFileSystemComboBox.getSelectedItem().
                        toString());
        preferences.putBoolean(COPY_EXCHANGE_PARTITION,
                copyExchangePartitionCheckBox.isSelected());
        preferences.put(DATA_PARTITION_FILESYSTEM,
                dataPartitionFileSystemComboBox.getSelectedItem().toString());
        preferences.putBoolean(CHECK_COPIES, checkCopiesCheckBox.isSelected());
        preferences.putInt(DATA_PARTITION_MODE,
                dataPartitionModeComboBox.getSelectedIndex());
        preferences.putBoolean(COPY_DATA_PARTITION,
                copyDataPartitionCheckBox.isSelected());
        preferences.putBoolean(UPGRADE_SYSTEM_PARTITION,
                upgradeSystemPartitionCheckBox.isSelected());
        preferences.putBoolean(UPGRADE_RESET_DATA_PARTITION,
                resetDataPartitionCheckBox.isSelected());
        preferences.putBoolean(REACTIVATE_WELCOME,
                reactivateWelcomeCheckBox.isSelected());
        preferences.putBoolean(KEEP_PRINTER_SETTINGS,
                keepPrinterSettingsCheckBox.isSelected());
        preferences.putBoolean(KEEP_NETWORK_SETTINGS,
                keepNetworkSettingsCheckBox.isSelected());
        preferences.putBoolean(KEEP_FIREWALL_SETTINGS,
                keepFirewallSettingsCheckBox.isSelected());
        preferences.putBoolean(KEEP_USER_SETTINGS,
                keepUserSettingsCheckBox.isSelected());
        preferences.putBoolean(AUTOMATIC_BACKUP,
                automaticBackupCheckBox.isSelected());
        preferences.put(BACKUP_DESTINATION,
                automaticBackupTextField.getText());
        preferences.putBoolean(REMOVE_HIDDEN_FILES,
                removeHiddenFilesCheckBox.isSelected());
        preferences.putBoolean(AUTO_REMOVE_BACKUP,
                automaticBackupRemoveCheckBox.isSelected());
        preferences.put(UPGRADE_OVERWRITE_LIST,
                getUpgradeOverwriteListString());

        preferences.putBoolean(RESET_AUTOMATIC,
                resetAutomaticModeRadioButton.isSelected());
        preferences.putBoolean(PRINT_DOCUMENTS,
                printDocumentsCheckBox.isSelected());
        preferences.put(PRINTING_DIRECTORIES,
                printingDirectoriesTextArea.getText());
        preferences.putBoolean(SCAN_DIRECTORIES_RECURSIVELY,
                scanDirectoriesRecursivelyCheckBox.isSelected());
        preferences.putBoolean(PRINT_ODT, printOdtCheckBox.isSelected());
        preferences.putBoolean(PRINT_ODS, printOdsCheckBox.isSelected());
        preferences.putBoolean(PRINT_ODP, printOdpCheckBox.isSelected());
        preferences.putBoolean(PRINT_PDF, printPdfCheckBox.isSelected());
        preferences.putBoolean(PRINT_DOC, printDocCheckBox.isSelected());
        preferences.putBoolean(PRINT_DOCX, printDocxCheckBox.isSelected());
        preferences.putBoolean(PRINT_XLS, printXlsCheckBox.isSelected());
        preferences.putBoolean(PRINT_XLSX, printXlsxCheckBox.isSelected());
        preferences.putBoolean(PRINT_PPT, printPptCheckBox.isSelected());
        preferences.putBoolean(PRINT_PPTX, printPptxCheckBox.isSelected());
        preferences.put(AUTO_PRINT_MODE, getAutoPrintMode().toString());
        preferences.putInt(PRINT_COPIES,
                ((Number) printCopiesSpinner.getValue()).intValue());
        preferences.putBoolean(PRINT_DUPLEX, printDuplexCheckBox.isSelected());
        preferences.putBoolean(RESET_BACKUP, resetBackupCheckBox.isSelected());
        preferences.put(RESET_BACKUP_SOURCE,
                resetBackupSourceTextField.getText());
        preferences.put(RESET_BACKUP_DESTINATION,
                resetBackupDestinationTextField.getText());
        preferences.putBoolean(RESET_BACKUP_SUBDIR_EXCHANGE_PARTITION_LABEL,
                exchangePartitionLabelSubdirectory.isSelected());
        preferences.putBoolean(RESET_BACKUP_SUBDIR_STORAGE_MEDIA_SERIALNUMBER,
                storageMediaSerialnumberSubdirectory.isSelected());
        preferences.putBoolean(RESET_BACKUP_SUBDIR_TIMESTAMP,
                timestampSubdirectory.isSelected());
        preferences.put(RESET_BACKUP_SUBDIR_ORDER, getSubdirOrderString());
        preferences.putBoolean(RESET_FORMAT_EXCHANGE_PARTITION,
                resetFormatExchangePartitionCheckBox.isSelected());
        preferences.put(RESET_FORMAT_EXCHANGE_PARTITION_FILE_SYSTEM,
                resetFormatExchangePartitionFileSystemComboBox.getSelectedItem()
                        .toString());
        preferences.putBoolean(RESET_FORMAT_EXCHANGE_PARTITION_KEEP_LABEL,
                resetFormatExchangePartitionKeepLabelRadioButton.isSelected());
        preferences.put(RESET_FORMAT_EXCHANGE_PARTITION_NEW_LABEL,
                resetFormatExchangePartitionNewLabelTextField.getText());
        preferences.putBoolean(RESET_DELETE_ON_DATA_PARTITION,
                deleteOnDataPartitionCheckBox.isSelected());
        preferences.putBoolean(RESET_FORMAT_DATA_PARTITION,
                formatDataPartitionRadioButton.isSelected());
        preferences.putBoolean(RESET_REMOVE_SYSTEM_FILES,
                systemFilesCheckBox.isSelected());
        preferences.putBoolean(RESET_REMOVE_HOME_DIRECTORY,
                homeDirectoryCheckBox.isSelected());
        preferences.putBoolean(RESET_RESTORE_ENABLED,
                resetRestoreDataCheckBox.isSelected());
        preferences.put(RESET_RESTORE_DATA,
                resetRestoreConfigurationPanel.getXML());

        preferences.putBoolean(TRANSFER_EXCHANGE,
                transferExchangeCheckBox.isSelected());
        preferences.putBoolean(TRANSFER_HOME,
                transferHomeCheckBox.isSelected());
        preferences.putBoolean(TRANSFER_NETWORK,
                transferNetworkCheckBox.isSelected());
        preferences.putBoolean(TRANSFER_PRINTER,
                transferPrinterCheckBox.isSelected());
        preferences.putBoolean(TRANSFER_FIREWALL,
                transferFirewallCheckBox.isSelected());
        preferences.putBoolean(TRANSFER_USER_SETTINGS,
                transferUserSettingsCheckBox.isSelected());

        try {
            preferences.flush();
        } catch (BackingStoreException ex) {
            LOGGER.warning("failed flushing preferences");
        }
    }

    private void loadPreferences(
            ComboBoxModel<String> exchangePartitionFileSystemsModel) {

        // main menu quick jump
        jumpComboBox.setSelectedIndex(preferences.getInt(JUMP_TO, 0));

        // installation source
        isoSourceRadioButton.setSelected(preferences.getBoolean(
                ISO_SOURCE_SELECTED, false));
        isoSourceTextField.setText(preferences.get(ISO_SOURCE, null));

        // installation destination -> selection
        copyExchangePartitionCheckBox.setSelected(
                preferences.getBoolean(COPY_EXCHANGE_PARTITION, false));
        exchangePartitionSizeSlider.setValue(
                preferences.getInt(EXPLICIT_EXCHANGE_SIZE, 0));
        copyDataPartitionCheckBox.setSelected(
                preferences.getBoolean(COPY_DATA_PARTITION, false));
        String[] dataPartitionModes = new String[]{
            STRINGS.getString("Read_Write"),
            STRINGS.getString("Read_Only"),
            STRINGS.getString("Not_Used")
        };
        dataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel<>(dataPartitionModes));
        dataPartitionModeComboBox.setSelectedIndex(
                preferences.getInt(DATA_PARTITION_MODE, 0));

        // installation destination -> details
        exchangePartitionFileSystemComboBox.setSelectedItem(
                preferences.get(EXCHANGE_PARTITION_FILESYSTEM,
                        exchangePartitionFileSystemsModel.getElementAt(0)));
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
        dataPartitionFileSystemComboBox.setSelectedItem(
                preferences.get(DATA_PARTITION_FILESYSTEM, "ext4"));
        checkCopiesCheckBox.setSelected(
                preferences.getBoolean(CHECK_COPIES, false));

        // installation destination -> data transfer
        transferExchangeCheckBox.setSelected(
                preferences.getBoolean(TRANSFER_EXCHANGE, false));
        transferHomeCheckBox.setSelected(
                preferences.getBoolean(TRANSFER_HOME, true));
        transferNetworkCheckBox.setSelected(
                preferences.getBoolean(TRANSFER_NETWORK, false));
        transferPrinterCheckBox.setSelected(
                preferences.getBoolean(TRANSFER_PRINTER, false));
        transferFirewallCheckBox.setSelected(
                preferences.getBoolean(TRANSFER_FIREWALL, false));
        transferUserSettingsCheckBox.setSelected(
                preferences.getBoolean(TRANSFER_USER_SETTINGS, false));

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

        // reset -> selection
        if (preferences.getBoolean(RESET_AUTOMATIC, false)) {
            resetAutomaticModeRadioButton.setSelected(true);
        } else {
            resetListModeRadioButton.setSelected(true);
        }

        // convert to ISO
        isoDataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel<>(dataPartitionModes));

        // reset -> 1. print data
        printDocumentsCheckBox.setSelected(
                preferences.getBoolean(PRINT_DOCUMENTS, false));
        printingDirectoriesTextArea.setText(preferences.get(
                PRINTING_DIRECTORIES,
                STRINGS.getString("Default_Backup_Directory")
                + File.separatorChar
                + STRINGS.getString("Default_Documents_Directory")));
        scanDirectoriesRecursivelyCheckBox.setSelected(
                preferences.getBoolean(SCAN_DIRECTORIES_RECURSIVELY, true));
        printOdtCheckBox.setSelected(
                preferences.getBoolean(PRINT_ODT, false));
        printOdsCheckBox.setSelected(
                preferences.getBoolean(PRINT_ODS, false));
        printOdpCheckBox.setSelected(
                preferences.getBoolean(PRINT_ODP, false));
        printPdfCheckBox.setSelected(
                preferences.getBoolean(PRINT_PDF, false));
        printDocCheckBox.setSelected(
                preferences.getBoolean(PRINT_DOC, false));
        printDocxCheckBox.setSelected(
                preferences.getBoolean(PRINT_DOCX, false));
        printXlsCheckBox.setSelected(
                preferences.getBoolean(PRINT_XLS, false));
        printXlsxCheckBox.setSelected(
                preferences.getBoolean(PRINT_XLSX, false));
        printPptCheckBox.setSelected(
                preferences.getBoolean(PRINT_PPT, false));
        printPptxCheckBox.setSelected(
                preferences.getBoolean(PRINT_PPTX, false));
        Resetter.AutoPrintMode autoPrintMode = Resetter.AutoPrintMode.valueOf(
                preferences.get(AUTO_PRINT_MODE,
                        Resetter.AutoPrintMode.NONE.toString()));
        switch (autoPrintMode) {
            case ALL:
                autoPrintAllDocumentsRadioButton.setSelected(true);
                break;
            case SINGLE:
                autoPrintSingleDocumentsRadioButton.setSelected(true);
                break;
            case NONE:
                autoPrintNoneRadioButton.setSelected(true);
        }
        printCopiesSpinner.setValue(preferences.getInt(PRINT_COPIES, 1));
        printDuplexCheckBox.setSelected(
                preferences.getBoolean(PRINT_DUPLEX, false));

        // reset -> 2. backup
        resetBackupCheckBox.setSelected(
                preferences.getBoolean(RESET_BACKUP, false));
        resetBackupSourceTextField.setText(
                preferences.get(RESET_BACKUP_SOURCE,
                        STRINGS.getString("Default_Backup_Directory")));
        resetBackupDestinationTextField.setText(
                preferences.get(RESET_BACKUP_DESTINATION, null));
        // subdirectories handling is more complex and therefore already
        // processed in the constructor

        // reset -> 3. delete data
        resetFormatExchangePartitionCheckBox.setSelected(
                preferences.getBoolean(RESET_FORMAT_EXCHANGE_PARTITION, false));
        if (preferences.getBoolean(RESET_AUTOMATIC, false)) {
            resetAutomaticModeRadioButton.setSelected(true);
        } else {
            resetListModeRadioButton.setSelected(true);
        }
        resetFormatExchangePartitionFileSystemComboBox.setSelectedItem(
                preferences.get(RESET_FORMAT_EXCHANGE_PARTITION_FILE_SYSTEM,
                        exchangePartitionFileSystemsModel.getElementAt(0)));
        if (preferences.getBoolean(
                RESET_FORMAT_EXCHANGE_PARTITION_KEEP_LABEL, true)) {
            resetFormatExchangePartitionKeepLabelRadioButton.setSelected(true);
        } else {
            resetFormatExchangePartitionNewLabelRadioButton.setSelected(true);
        }
        resetFormatExchangePartitionNewLabelTextField.setText(preferences.get(
                RESET_FORMAT_EXCHANGE_PARTITION_NEW_LABEL, null));
        deleteOnDataPartitionCheckBox.setSelected(
                preferences.getBoolean(RESET_DELETE_ON_DATA_PARTITION, false));
        if (preferences.getBoolean(RESET_FORMAT_DATA_PARTITION, false)) {
            formatDataPartitionRadioButton.setSelected(true);
        } else {
            removeFilesRadioButton.setSelected(true);
        }
        systemFilesCheckBox.setSelected(
                preferences.getBoolean(RESET_REMOVE_SYSTEM_FILES, false));
        homeDirectoryCheckBox.setSelected(
                preferences.getBoolean(RESET_REMOVE_HOME_DIRECTORY, false));

        // reset -> 4. restore data
        resetRestoreDataCheckBox.setSelected(
                preferences.getBoolean(RESET_RESTORE_ENABLED, false));
        String resetRestoreData = preferences.get(RESET_RESTORE_DATA, null);
        if (resetRestoreData != null && !resetRestoreData.isEmpty()) {
            resetRestoreConfigurationPanel.setXML(resetRestoreData);
        }

    }

    private Subdirectory getSubdirectory(
            String preferencesKey, String descriptionKey) {

        return new Subdirectory(preferences.getBoolean(preferencesKey, true),
                STRINGS.getString(descriptionKey));
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

    private String getSubdirOrderString() {

        StringBuilder builder = new StringBuilder();

        for (Subdirectory entry : orderedSubdirectoriesEntries) {

            if (builder.length() > 0) {
                builder.append(',');
            }

            String description = entry.getDescription();
            if (description.equals(
                    STRINGS.getString("Exchange_Partition_Label"))) {
                builder.append(RESET_BACKUP_SUBDIR_ORDER_LABEL);

            } else if (description.equals(
                    STRINGS.getString("Storage_Media_Serial_Number"))) {
                builder.append(RESET_BACKUP_SUBDIR_ORDER_SERIAL);

            } else if (description.equals(
                    STRINGS.getString("Timestamp"))) {
                builder.append(RESET_BACKUP_SUBDIR_ORDER_TIMESTAMP);
            }
        }

        return builder.toString();
    }

    private void fillUpgradeOverwriteList(String list) {
        if (!list.isEmpty()) {
            String[] upgradeOverWriteTokens = list.split("\n");
            for (String upgradeOverWriteToken : upgradeOverWriteTokens) {
                upgradeOverwriteListModel.addElement(upgradeOverWriteToken);
            }
        }
    }
}
