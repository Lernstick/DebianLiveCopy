package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.Resetter.AutoPrintMode;
import ch.fhnw.dlcopy.Subdirectory;
import ch.fhnw.dlcopy.gui.swing.preferences.DLCopySwingGUIPreferencesHandler;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetBackupPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetDeletePreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetPrintPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetRestorePreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetSelectionPreferences;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The panels needed for the Resetter
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetterPanels
        extends JPanel
        implements ListDataListener, ListSelectionListener {

    private static final Logger LOGGER
            = Logger.getLogger(ResetterPanels.class.getName());

    private final DefaultListModel<StorageDevice> storageDeviceListModel;
    private final ResetStorageDeviceRenderer storageDeviceRenderer;

    private DLCopySwingGUI dlCopySwingGUI;
    private String runningSystemSourceDeviceName;
    private List<Subdirectory> orderedSubdirectoriesEntries;
    private SubdirectoryTableModel subdirectoryTableModel;
    private ResetPrintPreferences resetPrintPreferences;

    /**
     * Creates new form ResetterPanels
     */
    public ResetterPanels() {

        initComponents();

        storageDeviceListModel = new DefaultListModel<>();
        storageDeviceList.setModel(storageDeviceListModel);
        storageDeviceRenderer = new ResetStorageDeviceRenderer();
        storageDeviceList.setCellRenderer(storageDeviceRenderer);

        // set renderer that respects the "enabled" state of the table
        setEnabledRespectingDefaultRenderer(
                backupDestinationSubdirectoryTable, Boolean.class);
        setEnabledRespectingDefaultRenderer(
                backupDestinationSubdirectoryTable, String.class);
    }

    // post-constructor initialization
    public void init(DLCopySwingGUI dlCopySwingGUI,
            String runningSystemSourceDeviceName,
            DLCopySwingGUIPreferencesHandler preferencesHandler,
            ComboBoxModel<String> exchangePartitionFileSystemsModel) {

        this.dlCopySwingGUI = dlCopySwingGUI;
        this.runningSystemSourceDeviceName = runningSystemSourceDeviceName;

        String countString = DLCopy.STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString, 0, 0);
        selectionCountLabel.setText(countString);

        preferencesHandler.addPreference(new ResetSelectionPreferences(
                automaticModeRadioButton, selectFromListModeRadioButton));

        resetPrintPreferences = new ResetPrintPreferences(
                printDocumentsCheckBox, printingDirectoriesTextArea,
                scanDirectoriesRecursivelyCheckBox, printOdtCheckBox,
                printOdsCheckBox, printOdpCheckBox, printPdfCheckBox,
                printDocCheckBox, printDocxCheckBox, printXlsCheckBox,
                printXlsxCheckBox, printPptCheckBox, printPptxCheckBox,
                autoPrintAllDocumentsRadioButton,
                autoPrintSingleDocumentsRadioButton, autoPrintNoneRadioButton,
                printCopiesSpinner, printDuplexCheckBox);
        preferencesHandler.addPreference(resetPrintPreferences);

        ResetBackupPreferences resetBackupPreferences
                = new ResetBackupPreferences(backupCheckBox,
                        backupSourceTextField, backupDestinationTextField);
        preferencesHandler.addPreference(resetBackupPreferences);
        orderedSubdirectoriesEntries = resetBackupPreferences.
                getOrderedSubdirectoriesEntries();
        subdirectoryTableModel = new SubdirectoryTableModel(
                backupDestinationSubdirectoryTable,
                orderedSubdirectoriesEntries);
        backupDestinationSubdirectoryTable.setModel(subdirectoryTableModel);
        backupDestinationSubdirectoryTable.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        preferencesHandler.addPreference(new ResetDeletePreferences(
                formatExchangePartitionCheckBox,
                formatExchangePartitionFileSystemComboBox,
                formatExchangePartitionKeepLabelRadioButton,
                formatExchangePartitionNewLabelRadioButton,
                formatExchangePartitionNewLabelTextField,
                deleteFromDataPartitionCheckBox, formatDataPartitionRadioButton,
                deleteFilesRadioButton, deleteSystemFilesCheckBox,
                deleteHomeDirectoryCheckBox));

        preferencesHandler.addPreference(new ResetRestorePreferences(
                restoreDataCheckBox, restoreConfigurationPanel));

        formatExchangePartitionFileSystemComboBox.setModel(
                exchangePartitionFileSystemsModel);

        storageDeviceListModel.addListDataListener(this);

        backupDestinationSubdirectoryTable.getSelectionModel().
                addListSelectionListener(this);
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        handleListDataEvent(e);
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        handleListDataEvent(e);
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        handleListDataEvent(e);
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getSource()
                == backupDestinationSubdirectoryTable.getSelectionModel()) {
            int[] selectedRows
                    = backupDestinationSubdirectoryTable.getSelectedRows();
            if (selectedRows.length == 0) {
                moveUpButton.setEnabled(false);
                moveDownButton.setEnabled(false);
            } else {
                moveUpButton.setEnabled(selectedRows[0] != 0);
                moveDownButton.setEnabled(
                        selectedRows[selectedRows.length - 1]
                        != subdirectoryTableModel.getRowCount() - 1);
            }
        }
    }

    public void showInfo() {
        DLCopySwingGUI.showCard(this, "infoPanel");
    }

    public void showSelection() {
        DLCopySwingGUI.showCard(this, "selectionAndConfigurationTabbedPane");
        selectionAndConfigurationTabbedPane.setSelectedComponent(
                selectionPanel);
    }

    public boolean isShowHardDiskSelected() {
        return showHardDisksCheckBox.isSelected();
    }

    public boolean isListSelectionSelected() {
        return selectFromListModeRadioButton.isSelected();
    }

    public DefaultListModel<StorageDevice> getDeviceListModel() {
        return storageDeviceListModel;
    }

    public JList<StorageDevice> getDeviceList() {
        return storageDeviceList;
    }

    public void storageDeviceListChanged() {
        if (selectFromListModeRadioButton.isSelected()) {
            dlCopySwingGUI.storageDeviceListChanged(
                    storageDeviceListModel, selectionCardPanel,
                    "noMediaPanel", "selectionDeviceListPanel",
                    storageDeviceRenderer, storageDeviceList);
        }
    }

    public void updateSelectionCountAndNextButton() {

        // check all selected storage devices
        boolean canReset = true;
        List<StorageDevice> selectedStorageDevices
                = storageDeviceList.getSelectedValuesList();
        for (StorageDevice storageDevice : selectedStorageDevices) {
            Partition dataPartition = storageDevice.getDataPartition();
            try {
                if ((dataPartition != null)
                        && dataPartition.isActivePersistencePartition()) {
                    canReset = false;
                    break;
                }
            } catch (DBusException | IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                canReset = false;
                break;
            }
        }

        int selectionCount = selectedStorageDevices.size();
        String countString = DLCopy.STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, storageDeviceListModel.size());
        selectionCountLabel.setText(countString);

        // update nextButton state
        if ((selectionCount > 0) && canReset) {
            dlCopySwingGUI.enableNextButton();
        } else {
            dlCopySwingGUI.disableNextButton();
        }
    }

    public String getExchangePartitionFileSystem() {
        return formatExchangePartitionFileSystemComboBox.
                getSelectedItem().toString();
    }

    public boolean isPrintDocumentsSelected() {
        return printDocumentsCheckBox.isSelected();
    }

    public String getPrintingDirectories() {
        return printingDirectoriesTextArea.getText();
    }

    public boolean isRecursiveDirectoryScanningEnabled() {
        return scanDirectoriesRecursivelyCheckBox.isSelected();
    }

    public boolean isPrintOdtSelected() {
        return printOdtCheckBox.isSelected();
    }

    public boolean isPrintOdsSelected() {
        return printOdsCheckBox.isSelected();
    }

    public boolean isPrintOdpSelected() {
        return printOdpCheckBox.isSelected();
    }

    public boolean isPrintPdfSelected() {
        return printPdfCheckBox.isSelected();
    }

    public boolean isPrintDocSelected() {
        return printDocCheckBox.isSelected();
    }

    public boolean isPrintDocxSelected() {
        return printDocxCheckBox.isSelected();
    }

    public boolean isPrintXlsSelected() {
        return printXlsCheckBox.isSelected();
    }

    public boolean isPrintXlsxSelected() {
        return printXlsxCheckBox.isSelected();
    }

    public boolean isPrintPptSelected() {
        return printPptCheckBox.isSelected();
    }

    public boolean isPrintPptxSelected() {
        return printPptxCheckBox.isSelected();
    }

    public AutoPrintMode getAutoPrintMode() {
        return resetPrintPreferences.getAutoPrintMode();
    }

    public int getNumberOfPrintCopies() {
        return ((Number) printCopiesSpinner.getValue()).intValue();
    }

    public boolean isDuplexPrintingSelected() {
        return printDuplexCheckBox.isSelected();
    }

    public boolean isBackupSelected() {
        return backupCheckBox.isSelected();
    }

    public String getBackupSource() {
        return backupSourceTextField.getText();
    }

    public String getBackupDestination() {
        return backupDestinationTextField.getText();
    }

    public List<Subdirectory> getBackupDestinationSubdirectoryEntries() {
        return orderedSubdirectoriesEntries;
    }

    public boolean isFormatExchangePartitionSelected() {
        return formatExchangePartitionCheckBox.isSelected();
    }

    public boolean isKeepExchangePartitionLabelSelected() {
        return formatExchangePartitionKeepLabelRadioButton.isSelected();
    }

    public String getNewExchangePartitionLabel() {
        return formatExchangePartitionNewLabelTextField.getText();
    }

    public boolean isDeleteFromDataPartitionSelected() {
        return deleteFromDataPartitionCheckBox.isSelected();
    }

    public boolean isFormatDataPartitionSelected() {
        return formatDataPartitionRadioButton.isSelected();
    }

    public boolean isDeleteHomeDirectorySelected() {
        return deleteHomeDirectoryCheckBox.isSelected();
    }

    public boolean isDeleteSystemFilesSelected() {
        return deleteSystemFilesCheckBox.isSelected();
    }

    public boolean isRestoreDataSelected() {
        return restoreDataCheckBox.isSelected();
    }

    public List<OverwriteEntry> getRestoreEntries() {
        return restoreConfigurationPanel.getEntries();
    }

    public void startedResetOnDevice(
            int batchCounter, StorageDevice storageDevice) {

        String pattern = DLCopy.STRINGS.getString("Reset_Device_Info");
        String message = MessageFormat.format(pattern, batchCounter,
                storageDeviceList.getSelectedIndices().length,
                storageDevice.getVendor() + " " + storageDevice.getModel(),
                " (" + DLCopy.STRINGS.getString("Size") + ": "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1) + ", "
                + DLCopy.STRINGS.getString("Revision") + ": "
                + storageDevice.getRevision() + ", "
                + DLCopy.STRINGS.getString("Serial") + ": "
                + storageDevice.getSerial() + ", " + "&#47;dev&#47;"
                + storageDevice.getDevice() + ")");

        // We are called from the udisks monitoring thread, therefore we must
        // use SwingUtilities.invokeLater() here.
        DLCopySwingGUI.setLabelTextonEDT(
                currentlyResettingDeviceLabel, message);
    }

    public void showProgressPanel() {
        DLCopySwingGUI.showCard(this, "resetPanel");
        DLCopySwingGUI.showCard(resetCardPanel, "progressPanel");
    }

    public void showPrintingDocuments() {
        DLCopySwingGUI.setProgressBarStringOnEDT(
                progressBar, "Printing_Documents");
    }

    public void showBackup(FileCopier fileCopier) {
        SwingUtilities.invokeLater(() -> DLCopySwingGUI.showCard(
                resetCardPanel, "backupPanel"));
        backupFileCopierPanel.setFileCopier(fileCopier);
    }

    public void showFormattingExchangePartition() {
        SwingUtilities.invokeLater(() -> showProgressPanel());
        DLCopySwingGUI.setProgressBarStringOnEDT(
                progressBar, "Formatting_Exchange_Partition");
    }

    public void showResetFormattingDataPartition() {
        SwingUtilities.invokeLater(() -> showProgressPanel());
        DLCopySwingGUI.setProgressBarStringOnEDT(
                progressBar, "Formatting_Data_Partition");
    }

    public void showResetRemovingFiles() {
        SwingUtilities.invokeLater(() -> showProgressPanel());
        DLCopySwingGUI.setProgressBarStringOnEDT(
                progressBar, "Removing_Selected_Files");
    }

    public void showNoMediaPanel() {
        showSelection();
        DLCopySwingGUI.showCard(selectionCardPanel, "noMediaPanel");
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        selectionModeButtonGroup = new javax.swing.ButtonGroup();
        automaticPrintingButtonGroup = new javax.swing.ButtonGroup();
        exchangePartitionLabelButtonGroup = new javax.swing.ButtonGroup();
        deleteDataPartitionButtonGroup = new javax.swing.ButtonGroup();
        infoPanel = new javax.swing.JPanel();
        resetInfoLabel = new javax.swing.JLabel();
        selectionAndConfigurationTabbedPane = new javax.swing.JTabbedPane();
        selectionPanel = new javax.swing.JPanel();
        selectFromListModeRadioButton = new javax.swing.JRadioButton();
        showHardDisksCheckBox = new javax.swing.JCheckBox();
        automaticModeRadioButton = new javax.swing.JRadioButton();
        jSeparator2 = new javax.swing.JSeparator();
        selectionCardPanel = new javax.swing.JPanel();
        noMediaPanel = new javax.swing.JPanel();
        noMediaLabel = new javax.swing.JLabel();
        selectionDeviceListPanel = new javax.swing.JPanel();
        selectionInfoPanel = new javax.swing.JPanel();
        selectionHeaderLabel = new javax.swing.JLabel();
        selectionCountLabel = new javax.swing.JLabel();
        storageDeviceListScrollPane = new javax.swing.JScrollPane();
        storageDeviceList = new javax.swing.JList<>();
        exchangeDefinitionLabel = new javax.swing.JLabel();
        dataDefinitionLabel = new javax.swing.JLabel();
        osDefinitionLabel = new javax.swing.JLabel();
        printingDetailsPanel = new javax.swing.JPanel();
        printDocumentsCheckBox = new javax.swing.JCheckBox();
        printingDirectoryPanel = new javax.swing.JPanel();
        printingDirectoriesScrollPane = new javax.swing.JScrollPane();
        printingDirectoriesTextArea = new javax.swing.JTextArea();
        scanDirectoriesRecursivelyCheckBox = new javax.swing.JCheckBox();
        printFileFormatsPanel = new javax.swing.JPanel();
        printOdtCheckBox = new javax.swing.JCheckBox();
        printOdsCheckBox = new javax.swing.JCheckBox();
        printOdpCheckBox = new javax.swing.JCheckBox();
        printPdfCheckBox = new javax.swing.JCheckBox();
        printDocCheckBox = new javax.swing.JCheckBox();
        printDocxCheckBox = new javax.swing.JCheckBox();
        printXlsCheckBox = new javax.swing.JCheckBox();
        printXlsxCheckBox = new javax.swing.JCheckBox();
        printPptCheckBox = new javax.swing.JCheckBox();
        printPptxCheckBox = new javax.swing.JCheckBox();
        rightPrintingPanel = new javax.swing.JPanel();
        automaticPrintingPanel = new javax.swing.JPanel();
        autoPrintAllDocumentsRadioButton = new javax.swing.JRadioButton();
        autoPrintSingleDocumentsRadioButton = new javax.swing.JRadioButton();
        autoPrintNoneRadioButton = new javax.swing.JRadioButton();
        printCopiesPanel = new javax.swing.JPanel();
        printCopiesLabel = new javax.swing.JLabel();
        printCopiesSpinner = new javax.swing.JSpinner();
        printDuplexCheckBox = new javax.swing.JCheckBox();
        backupDetailsPanel = new javax.swing.JPanel();
        backupCheckBox = new javax.swing.JCheckBox();
        backupSourcePanel = new javax.swing.JPanel();
        backupSourceTextField = new javax.swing.JTextField();
        backupDestinationPanel = new javax.swing.JPanel();
        backupDestinationTextField = new javax.swing.JTextField();
        backupDestinationButton = new javax.swing.JButton();
        backupDestinationSubdirectoryPanel = new javax.swing.JPanel();
        moveUpButton = new javax.swing.JButton();
        moveDownButton = new javax.swing.JButton();
        backupDestinationSubdirectoryScrollPane = new javax.swing.JScrollPane();
        backupDestinationSubdirectoryTable = new javax.swing.JTable();
        spacerPanel = new javax.swing.JPanel();
        deletePanel = new javax.swing.JPanel();
        deleteExchangePartitionDetailsPanel = new javax.swing.JPanel();
        formatExchangePartitionCheckBox = new javax.swing.JCheckBox();
        formatExchangePartitionFileSystemLabel = new javax.swing.JLabel();
        formatExchangePartitionFileSystemComboBox = new javax.swing.JComboBox<>();
        formatExchangePartitionKeepLabelRadioButton = new javax.swing.JRadioButton();
        formatExchangePartitionNewLabelRadioButton = new javax.swing.JRadioButton();
        formatExchangePartitionNewLabelTextField = new javax.swing.JTextField();
        exchangeSpacer = new javax.swing.JPanel();
        deleteDataPartitionDetailsPanel = new javax.swing.JPanel();
        deleteFromDataPartitionCheckBox = new javax.swing.JCheckBox();
        formatDataPartitionRadioButton = new javax.swing.JRadioButton();
        deleteFilesRadioButton = new javax.swing.JRadioButton();
        deleteSystemFilesCheckBox = new javax.swing.JCheckBox();
        deleteHomeDirectoryCheckBox = new javax.swing.JCheckBox();
        restorePanel = new javax.swing.JPanel();
        restoreDataCheckBox = new javax.swing.JCheckBox();
        restoreConfigurationPanel = new ch.fhnw.dlcopy.gui.swing.OverwriteConfigurationPanel();
        resetPanel = new javax.swing.JPanel();
        currentlyResettingDeviceLabel = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        resetCardPanel = new javax.swing.JPanel();
        progressPanel = new javax.swing.JPanel();
        progressBar = new javax.swing.JProgressBar();
        backupPanel = new javax.swing.JPanel();
        backupCopyLabel = new javax.swing.JLabel();
        backupFileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();

        setName("resetterPanels"); // NOI18N
        setLayout(new java.awt.CardLayout());

        infoPanel.setLayout(new java.awt.GridBagLayout());

        resetInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/lernstick_reset.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        resetInfoLabel.setText(bundle.getString("DLCopySwingGUI.resetInfoLabel.text")); // NOI18N
        resetInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        resetInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        infoPanel.add(resetInfoLabel, gridBagConstraints);

        add(infoPanel, "infoPanel");

        selectionAndConfigurationTabbedPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                selectionAndConfigurationTabbedPaneComponentShown(evt);
            }
        });

        selectionPanel.setLayout(new java.awt.GridBagLayout());

        selectionModeButtonGroup.add(selectFromListModeRadioButton);
        selectFromListModeRadioButton.setSelected(true);
        selectFromListModeRadioButton.setText(bundle.getString("Select_Storage_Media_From_List")); // NOI18N
        selectFromListModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectFromListModeRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        selectionPanel.add(selectFromListModeRadioButton, gridBagConstraints);

        showHardDisksCheckBox.setFont(showHardDisksCheckBox.getFont().deriveFont(showHardDisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, showHardDisksCheckBox.getFont().getSize()-1));
        showHardDisksCheckBox.setText(bundle.getString("DLCopySwingGUI.resetShowHarddisksCheckBox.text")); // NOI18N
        showHardDisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                showHardDisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 30, 0, 0);
        selectionPanel.add(showHardDisksCheckBox, gridBagConstraints);

        selectionModeButtonGroup.add(automaticModeRadioButton);
        automaticModeRadioButton.setText(bundle.getString("DLCopySwingGUI.resetAutomaticModeRadioButton.text")); // NOI18N
        automaticModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                automaticModeRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        selectionPanel.add(automaticModeRadioButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        selectionPanel.add(jSeparator2, gridBagConstraints);

        selectionCardPanel.setName("selectionCardPanel"); // NOI18N
        selectionCardPanel.setLayout(new java.awt.CardLayout());

        noMediaPanel.setLayout(new java.awt.GridBagLayout());

        noMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        noMediaLabel.setText(bundle.getString("DLCopySwingGUI.resetNoMediaLabel.text")); // NOI18N
        noMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        noMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        noMediaPanel.add(noMediaLabel, new java.awt.GridBagConstraints());

        selectionCardPanel.add(noMediaPanel, "noMediaPanel");

        selectionDeviceListPanel.setLayout(new java.awt.GridBagLayout());

        selectionInfoPanel.setLayout(new java.awt.GridBagLayout());

        selectionHeaderLabel.setFont(selectionHeaderLabel.getFont().deriveFont(selectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        selectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        selectionHeaderLabel.setText(bundle.getString("DLCopySwingGUI.resetSelectionHeaderLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        selectionInfoPanel.add(selectionHeaderLabel, gridBagConstraints);

        selectionCountLabel.setText(bundle.getString("DLCopySwingGUI.resetSelectionCountLabel.text")); // NOI18N
        selectionInfoPanel.add(selectionCountLabel, new java.awt.GridBagConstraints());

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        selectionDeviceListPanel.add(selectionInfoPanel, gridBagConstraints);

        storageDeviceList.setName("storageDeviceList"); // NOI18N
        storageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                storageDeviceListValueChanged(evt);
            }
        });
        storageDeviceListScrollPane.setViewportView(storageDeviceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        selectionDeviceListPanel.add(storageDeviceListScrollPane, gridBagConstraints);

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("ExchangePartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        selectionDeviceListPanel.add(exchangeDefinitionLabel, gridBagConstraints);

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DataPartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        selectionDeviceListPanel.add(dataDefinitionLabel, gridBagConstraints);

        osDefinitionLabel.setFont(osDefinitionLabel.getFont().deriveFont(osDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, osDefinitionLabel.getFont().getSize()-1));
        osDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        osDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.resetOsDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 10, 10);
        selectionDeviceListPanel.add(osDefinitionLabel, gridBagConstraints);

        selectionCardPanel.add(selectionDeviceListPanel, "selectionDeviceListPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        selectionPanel.add(selectionCardPanel, gridBagConstraints);

        selectionAndConfigurationTabbedPane.addTab(bundle.getString("Selection"), selectionPanel); // NOI18N

        printingDetailsPanel.setLayout(new java.awt.GridBagLayout());

        printDocumentsCheckBox.setText(bundle.getString("DLCopySwingGUI.printDocumentsCheckBox.text")); // NOI18N
        printDocumentsCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                printDocumentsCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        printingDetailsPanel.add(printDocumentsCheckBox, gridBagConstraints);

        printingDirectoryPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.printingDirectoryPanel.border.title"))); // NOI18N
        printingDirectoryPanel.setLayout(new java.awt.GridBagLayout());

        printingDirectoriesTextArea.setEnabled(false);
        printingDirectoriesScrollPane.setViewportView(printingDirectoriesTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        printingDirectoryPanel.add(printingDirectoriesScrollPane, gridBagConstraints);

        scanDirectoriesRecursivelyCheckBox.setText(bundle.getString("DLCopySwingGUI.scanDirectoriesRecursivelyCheckBox.text")); // NOI18N
        scanDirectoriesRecursivelyCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        printingDirectoryPanel.add(scanDirectoriesRecursivelyCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        printingDetailsPanel.add(printingDirectoryPanel, gridBagConstraints);

        printFileFormatsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.printFileFormatsPanel.border.title"))); // NOI18N
        printFileFormatsPanel.setLayout(new java.awt.GridBagLayout());

        printOdtCheckBox.setText(bundle.getString("DLCopySwingGUI.printOdtCheckBox.text")); // NOI18N
        printOdtCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printOdtCheckBox, gridBagConstraints);

        printOdsCheckBox.setText(bundle.getString("DLCopySwingGUI.printOdsCheckBox.text")); // NOI18N
        printOdsCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printOdsCheckBox, gridBagConstraints);

        printOdpCheckBox.setText(bundle.getString("DLCopySwingGUI.printOdpCheckBox.text")); // NOI18N
        printOdpCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printOdpCheckBox, gridBagConstraints);

        printPdfCheckBox.setText(bundle.getString("DLCopySwingGUI.printPdfCheckBox.text")); // NOI18N
        printPdfCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printPdfCheckBox, gridBagConstraints);

        printDocCheckBox.setText(bundle.getString("DLCopySwingGUI.printDocCheckBox.text")); // NOI18N
        printDocCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printDocCheckBox, gridBagConstraints);

        printDocxCheckBox.setText(bundle.getString("DLCopySwingGUI.printDocxCheckBox.text")); // NOI18N
        printDocxCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printDocxCheckBox, gridBagConstraints);

        printXlsCheckBox.setText(bundle.getString("DLCopySwingGUI.printXlsCheckBox.text")); // NOI18N
        printXlsCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printXlsCheckBox, gridBagConstraints);

        printXlsxCheckBox.setText(bundle.getString("DLCopySwingGUI.printXlsxCheckBox.text")); // NOI18N
        printXlsxCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printXlsxCheckBox, gridBagConstraints);

        printPptCheckBox.setText(bundle.getString("DLCopySwingGUI.printPptCheckBox.text")); // NOI18N
        printPptCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        printFileFormatsPanel.add(printPptCheckBox, gridBagConstraints);

        printPptxCheckBox.setText(bundle.getString("DLCopySwingGUI.printPptxCheckBox.text")); // NOI18N
        printPptxCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        printFileFormatsPanel.add(printPptxCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 5, 5);
        printingDetailsPanel.add(printFileFormatsPanel, gridBagConstraints);

        rightPrintingPanel.setLayout(new java.awt.GridBagLayout());

        automaticPrintingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.automaticPrintingPanel.border.title"))); // NOI18N
        automaticPrintingPanel.setLayout(new java.awt.GridBagLayout());

        automaticPrintingButtonGroup.add(autoPrintAllDocumentsRadioButton);
        autoPrintAllDocumentsRadioButton.setText(bundle.getString("DLCopySwingGUI.autoPrintAllDocumentsRadioButton.text")); // NOI18N
        autoPrintAllDocumentsRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        automaticPrintingPanel.add(autoPrintAllDocumentsRadioButton, gridBagConstraints);

        automaticPrintingButtonGroup.add(autoPrintSingleDocumentsRadioButton);
        autoPrintSingleDocumentsRadioButton.setText(bundle.getString("DLCopySwingGUI.autoPrintSingleDocumentsRadioButton.text")); // NOI18N
        autoPrintSingleDocumentsRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        automaticPrintingPanel.add(autoPrintSingleDocumentsRadioButton, gridBagConstraints);

        automaticPrintingButtonGroup.add(autoPrintNoneRadioButton);
        autoPrintNoneRadioButton.setSelected(true);
        autoPrintNoneRadioButton.setText(bundle.getString("DLCopySwingGUI.autoPrintNoneRadioButton.text")); // NOI18N
        autoPrintNoneRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        automaticPrintingPanel.add(autoPrintNoneRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        rightPrintingPanel.add(automaticPrintingPanel, gridBagConstraints);

        printCopiesPanel.setLayout(new java.awt.GridBagLayout());

        printCopiesLabel.setText(bundle.getString("DLCopySwingGUI.printCopiesLabel.text")); // NOI18N
        printCopiesLabel.setEnabled(false);
        printCopiesPanel.add(printCopiesLabel, new java.awt.GridBagConstraints());

        printCopiesSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        printCopiesSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        printCopiesPanel.add(printCopiesSpinner, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 7, 0, 5);
        rightPrintingPanel.add(printCopiesPanel, gridBagConstraints);

        printDuplexCheckBox.setSelected(true);
        printDuplexCheckBox.setText(bundle.getString("DLCopySwingGUI.printDuplexCheckBox.text")); // NOI18N
        printDuplexCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 4, 5, 0);
        rightPrintingPanel.add(printDuplexCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 5, 5);
        printingDetailsPanel.add(rightPrintingPanel, gridBagConstraints);

        selectionAndConfigurationTabbedPane.addTab(bundle.getString("DLCopySwingGUI.resetPrintingDetailsPanel.TabConstraints.tabTitle"), printingDetailsPanel); // NOI18N

        backupDetailsPanel.setLayout(new java.awt.GridBagLayout());

        backupCheckBox.setText(bundle.getString("DLCopySwingGUI.resetBackupCheckBox.text")); // NOI18N
        backupCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                backupCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        backupDetailsPanel.add(backupCheckBox, gridBagConstraints);

        backupSourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.resetBackupSourcePanel.border.title"))); // NOI18N
        backupSourcePanel.setLayout(new java.awt.GridBagLayout());

        backupSourceTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        backupSourcePanel.add(backupSourceTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 5);
        backupDetailsPanel.add(backupSourcePanel, gridBagConstraints);

        backupDestinationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.resetBackupDestinationPanel.border.title"))); // NOI18N
        backupDestinationPanel.setLayout(new java.awt.GridBagLayout());

        backupDestinationTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        backupDestinationPanel.add(backupDestinationTextField, gridBagConstraints);

        backupDestinationButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/fileopen.png"))); // NOI18N
        backupDestinationButton.setEnabled(false);
        backupDestinationButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        backupDestinationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupDestinationButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        backupDestinationPanel.add(backupDestinationButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 5);
        backupDetailsPanel.add(backupDestinationPanel, gridBagConstraints);

        backupDestinationSubdirectoryPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.resetBackupSubdirectoryPanel.border.title"))); // NOI18N
        backupDestinationSubdirectoryPanel.setLayout(new java.awt.GridBagLayout());

        moveUpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-up.png"))); // NOI18N
        moveUpButton.setToolTipText(bundle.getString("DLCopySwingGUI.resetMoveUpButton.toolTipText")); // NOI18N
        moveUpButton.setEnabled(false);
        moveUpButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveUpButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        backupDestinationSubdirectoryPanel.add(moveUpButton, gridBagConstraints);

        moveDownButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-down.png"))); // NOI18N
        moveDownButton.setToolTipText(bundle.getString("DLCopySwingGUI.resetMoveDownButton.toolTipText")); // NOI18N
        moveDownButton.setEnabled(false);
        moveDownButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveDownButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 5, 0);
        backupDestinationSubdirectoryPanel.add(moveDownButton, gridBagConstraints);

        backupDestinationSubdirectoryTable.setEnabled(false);
        backupDestinationSubdirectoryScrollPane.setViewportView(backupDestinationSubdirectoryTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        backupDestinationSubdirectoryPanel.add(backupDestinationSubdirectoryScrollPane, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        backupDetailsPanel.add(backupDestinationSubdirectoryPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        backupDetailsPanel.add(spacerPanel, gridBagConstraints);

        selectionAndConfigurationTabbedPane.addTab(bundle.getString("DLCopySwingGUI.resetBackupDetailsPanel.TabConstraints.tabTitle"), backupDetailsPanel); // NOI18N

        deletePanel.setLayout(new java.awt.GridBagLayout());

        deleteExchangePartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), bundle.getString("Exchange_Partition"))); // NOI18N
        deleteExchangePartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        formatExchangePartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionCheckBox.text")); // NOI18N
        formatExchangePartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                formatExchangePartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 10, 0);
        deleteExchangePartitionDetailsPanel.add(formatExchangePartitionCheckBox, gridBagConstraints);

        formatExchangePartitionFileSystemLabel.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionFileSystemLabel.text")); // NOI18N
        formatExchangePartitionFileSystemLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 25, 10, 0);
        deleteExchangePartitionDetailsPanel.add(formatExchangePartitionFileSystemLabel, gridBagConstraints);

        formatExchangePartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        formatExchangePartitionFileSystemComboBox.setToolTipText(bundle.getString("ExchangePartitionFileSystemComboBoxToolTipText")); // NOI18N
        formatExchangePartitionFileSystemComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 10, 10);
        deleteExchangePartitionDetailsPanel.add(formatExchangePartitionFileSystemComboBox, gridBagConstraints);

        exchangePartitionLabelButtonGroup.add(formatExchangePartitionKeepLabelRadioButton);
        formatExchangePartitionKeepLabelRadioButton.setSelected(true);
        formatExchangePartitionKeepLabelRadioButton.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionKeepLabelRadioButton.text")); // NOI18N
        formatExchangePartitionKeepLabelRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        deleteExchangePartitionDetailsPanel.add(formatExchangePartitionKeepLabelRadioButton, gridBagConstraints);

        exchangePartitionLabelButtonGroup.add(formatExchangePartitionNewLabelRadioButton);
        formatExchangePartitionNewLabelRadioButton.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionNewLabelRadioButton.text")); // NOI18N
        formatExchangePartitionNewLabelRadioButton.setEnabled(false);
        formatExchangePartitionNewLabelRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                formatExchangePartitionNewLabelRadioButtonItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 5, 0);
        deleteExchangePartitionDetailsPanel.add(formatExchangePartitionNewLabelRadioButton, gridBagConstraints);

        formatExchangePartitionNewLabelTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 10);
        deleteExchangePartitionDetailsPanel.add(formatExchangePartitionNewLabelTextField, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        deleteExchangePartitionDetailsPanel.add(exchangeSpacer, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        deletePanel.add(deleteExchangePartitionDetailsPanel, gridBagConstraints);

        deleteDataPartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), bundle.getString("Data_Partition"))); // NOI18N
        deleteDataPartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        deleteFromDataPartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.deleteOnDataPartitionCheckBox.text")); // NOI18N
        deleteFromDataPartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                deleteFromDataPartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        deleteDataPartitionDetailsPanel.add(deleteFromDataPartitionCheckBox, gridBagConstraints);

        deleteDataPartitionButtonGroup.add(formatDataPartitionRadioButton);
        formatDataPartitionRadioButton.setText(bundle.getString("DLCopySwingGUI.formatDataPartitionRadioButton.text")); // NOI18N
        formatDataPartitionRadioButton.setEnabled(false);
        formatDataPartitionRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                formatDataPartitionRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 20, 0, 10);
        deleteDataPartitionDetailsPanel.add(formatDataPartitionRadioButton, gridBagConstraints);

        deleteDataPartitionButtonGroup.add(deleteFilesRadioButton);
        deleteFilesRadioButton.setSelected(true);
        deleteFilesRadioButton.setText(bundle.getString("DLCopySwingGUI.deleteFilesRadioButton.text")); // NOI18N
        deleteFilesRadioButton.setEnabled(false);
        deleteFilesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteFilesRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 10);
        deleteDataPartitionDetailsPanel.add(deleteFilesRadioButton, gridBagConstraints);

        deleteSystemFilesCheckBox.setText(bundle.getString("DLCopySwingGUI.systemFilesCheckBox.text")); // NOI18N
        deleteSystemFilesCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 40, 0, 0);
        deleteDataPartitionDetailsPanel.add(deleteSystemFilesCheckBox, gridBagConstraints);

        deleteHomeDirectoryCheckBox.setText(bundle.getString("DLCopySwingGUI.homeDirectoryCheckBox.text")); // NOI18N
        deleteHomeDirectoryCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 40, 10, 10);
        deleteDataPartitionDetailsPanel.add(deleteHomeDirectoryCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        deletePanel.add(deleteDataPartitionDetailsPanel, gridBagConstraints);

        selectionAndConfigurationTabbedPane.addTab(bundle.getString("Delete_Data"), deletePanel); // NOI18N

        restorePanel.setLayout(new java.awt.GridBagLayout());

        restoreDataCheckBox.setText(bundle.getString("DLCopySwingGUI.resetRestoreDataCheckBox.text")); // NOI18N
        restoreDataCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                restoreDataCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        restorePanel.add(restoreDataCheckBox, gridBagConstraints);

        restoreConfigurationPanel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        restorePanel.add(restoreConfigurationPanel, gridBagConstraints);

        selectionAndConfigurationTabbedPane.addTab(bundle.getString("DLCopySwingGUI.resetRestorePanel.TabConstraints.tabTitle"), restorePanel); // NOI18N

        add(selectionAndConfigurationTabbedPane, "selectionAndConfigurationTabbedPane");

        resetPanel.setLayout(new java.awt.GridBagLayout());

        currentlyResettingDeviceLabel.setFont(currentlyResettingDeviceLabel.getFont().deriveFont(currentlyResettingDeviceLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        currentlyResettingDeviceLabel.setText(bundle.getString("Reset_Device_Info")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        resetPanel.add(currentlyResettingDeviceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        resetPanel.add(jSeparator5, gridBagConstraints);

        resetCardPanel.setName("resetCardPanel"); // NOI18N
        resetCardPanel.setLayout(new java.awt.CardLayout());

        progressPanel.setLayout(new java.awt.GridBagLayout());

        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        progressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weighty = 1.0;
        progressPanel.add(progressBar, gridBagConstraints);

        resetCardPanel.add(progressPanel, "progressPanel");

        backupPanel.setLayout(new java.awt.GridBagLayout());

        backupCopyLabel.setText(bundle.getString("DLCopySwingGUI.resetBackupCopyLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        backupPanel.add(backupCopyLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        backupPanel.add(backupFileCopierPanel, gridBagConstraints);

        resetCardPanel.add(backupPanel, "backupPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resetPanel.add(resetCardPanel, gridBagConstraints);

        add(resetPanel, "resetPanel");
    }// </editor-fold>//GEN-END:initComponents

    private void selectFromListModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectFromListModeRadioButtonActionPerformed

        boolean selected = selectFromListModeRadioButton.isSelected();

        showHardDisksCheckBox.setEnabled(selected);

        if (selected) {
            DLCopySwingGUI.showCard(selectionCardPanel,
                    "selectionDeviceListPanel");

            new ResetStorageDeviceListUpdater(dlCopySwingGUI,
                    storageDeviceList, storageDeviceListModel,
                    showHardDisksCheckBox.isSelected(),
                    runningSystemSourceDeviceName).execute();
        }
    }//GEN-LAST:event_selectFromListModeRadioButtonActionPerformed

    private void showHardDisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_showHardDisksCheckBoxItemStateChanged
        new ResetStorageDeviceListUpdater(dlCopySwingGUI,
                storageDeviceList, storageDeviceListModel,
                showHardDisksCheckBox.isSelected(),
                runningSystemSourceDeviceName).execute();
    }//GEN-LAST:event_showHardDisksCheckBoxItemStateChanged

    private void automaticModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_automaticModeRadioButtonActionPerformed

        DLCopySwingGUI.showCard(selectionCardPanel, "noMediaPanel");

        dlCopySwingGUI.disableNextButton();

        showHardDisksCheckBox.setEnabled(
                !automaticModeRadioButton.isSelected());
    }//GEN-LAST:event_automaticModeRadioButtonActionPerformed

    private void storageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_storageDeviceListValueChanged
        dlCopySwingGUI.updateResetSelectionCountAndNextButton();
    }//GEN-LAST:event_storageDeviceListValueChanged

    private void printDocumentsCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_printDocumentsCheckBoxItemStateChanged
        boolean enabled = printDocumentsCheckBox.isSelected();
        printingDirectoriesTextArea.setEnabled(enabled);
        scanDirectoriesRecursivelyCheckBox.setEnabled(enabled);
        printOdtCheckBox.setEnabled(enabled);
        printOdsCheckBox.setEnabled(enabled);
        printOdpCheckBox.setEnabled(enabled);
        printPdfCheckBox.setEnabled(enabled);
        printDocCheckBox.setEnabled(enabled);
        printDocxCheckBox.setEnabled(enabled);
        printXlsCheckBox.setEnabled(enabled);
        printXlsxCheckBox.setEnabled(enabled);
        printPptCheckBox.setEnabled(enabled);
        printPptxCheckBox.setEnabled(enabled);
        autoPrintAllDocumentsRadioButton.setEnabled(enabled);
        autoPrintSingleDocumentsRadioButton.setEnabled(enabled);
        autoPrintNoneRadioButton.setEnabled(enabled);
        printCopiesLabel.setEnabled(enabled);
        printCopiesSpinner.setEnabled(enabled);
        printDuplexCheckBox.setEnabled(enabled);
    }//GEN-LAST:event_printDocumentsCheckBoxItemStateChanged

    private void backupCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_backupCheckBoxItemStateChanged
        boolean enabled = backupCheckBox.isSelected();
        backupSourceTextField.setEnabled(enabled);
        backupDestinationButton.setEnabled(enabled);
        backupDestinationSubdirectoryTable.setEnabled(enabled);
        moveUpButton.setEnabled(enabled);
        moveDownButton.setEnabled(enabled);
    }//GEN-LAST:event_backupCheckBoxItemStateChanged

    private void backupDestinationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupDestinationButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(
                DLCopy.STRINGS.getString("Destination_Directory"));
        fileChooser.setApproveButtonText(DLCopy.STRINGS.getString("Select"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File directory = new File(backupDestinationTextField.getText());
        fileChooser.setSelectedFile(directory);
        // TODO: still broken? (https://bugs.openjdk.java.net/browse/JDK-6572365)
        fileChooser.ensureFileIsVisible(directory);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            backupDestinationTextField.setText(selectedFile.toString());
        }
    }//GEN-LAST:event_backupDestinationButtonActionPerformed

    private void moveUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveUpButtonActionPerformed
        int selectedRows[]
                = backupDestinationSubdirectoryTable.getSelectedRows();
        subdirectoryTableModel.moveUp(selectedRows);
        backupDestinationSubdirectoryTable.clearSelection();
        for (int selectedRow : selectedRows) {
            int previousRow = selectedRow - 1;
            backupDestinationSubdirectoryTable.addRowSelectionInterval(
                    previousRow, previousRow);
        }
    }//GEN-LAST:event_moveUpButtonActionPerformed

    private void moveDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveDownButtonActionPerformed
        int selectedRows[]
                = backupDestinationSubdirectoryTable.getSelectedRows();
        subdirectoryTableModel.moveDown(selectedRows);
        backupDestinationSubdirectoryTable.clearSelection();
        for (int selectedRow : selectedRows) {
            int nextRow = selectedRow + 1;
            backupDestinationSubdirectoryTable.addRowSelectionInterval(
                    nextRow, nextRow);
        }
    }//GEN-LAST:event_moveDownButtonActionPerformed

    private void formatExchangePartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_formatExchangePartitionCheckBoxItemStateChanged
        boolean enabled = formatExchangePartitionCheckBox.isSelected();
        formatExchangePartitionFileSystemLabel.setEnabled(enabled);
        formatExchangePartitionFileSystemComboBox.setEnabled(enabled);
        formatExchangePartitionKeepLabelRadioButton.setEnabled(enabled);
        formatExchangePartitionNewLabelRadioButton.setEnabled(enabled);
        formatExchangePartitionNewLabelTextField.setEnabled(enabled
                && formatExchangePartitionNewLabelRadioButton.isSelected());
    }//GEN-LAST:event_formatExchangePartitionCheckBoxItemStateChanged

    private void formatExchangePartitionNewLabelRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_formatExchangePartitionNewLabelRadioButtonItemStateChanged
        formatExchangePartitionNewLabelTextField.setEnabled(
                formatExchangePartitionCheckBox.isSelected()
                && formatExchangePartitionNewLabelRadioButton.isSelected());
    }//GEN-LAST:event_formatExchangePartitionNewLabelRadioButtonItemStateChanged

    private void deleteFromDataPartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_deleteFromDataPartitionCheckBoxItemStateChanged
        boolean enabled = deleteFromDataPartitionCheckBox.isSelected();
        formatDataPartitionRadioButton.setEnabled(enabled);
        deleteFilesRadioButton.setEnabled(enabled);
        deleteSystemFilesCheckBox.setEnabled(enabled);
        deleteHomeDirectoryCheckBox.setEnabled(enabled);
    }//GEN-LAST:event_deleteFromDataPartitionCheckBoxItemStateChanged

    private void formatDataPartitionRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_formatDataPartitionRadioButtonActionPerformed
        updateResetDataPartitionButtonState();
    }//GEN-LAST:event_formatDataPartitionRadioButtonActionPerformed

    private void deleteFilesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteFilesRadioButtonActionPerformed
        updateResetDataPartitionButtonState();
    }//GEN-LAST:event_deleteFilesRadioButtonActionPerformed

    private void restoreDataCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_restoreDataCheckBoxItemStateChanged
        restoreConfigurationPanel.setEnabled(
                restoreDataCheckBox.isSelected());
    }//GEN-LAST:event_restoreDataCheckBoxItemStateChanged

    private void selectionAndConfigurationTabbedPaneComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_selectionAndConfigurationTabbedPaneComponentShown
        if (selectFromListModeRadioButton.isSelected()) {
            new ResetStorageDeviceListUpdater(dlCopySwingGUI,
                    storageDeviceList, storageDeviceListModel,
                    showHardDisksCheckBox.isSelected(),
                    runningSystemSourceDeviceName).execute();
        }
    }//GEN-LAST:event_selectionAndConfigurationTabbedPaneComponentShown

    private void updateResetDataPartitionButtonState() {
        boolean selected = deleteFilesRadioButton.isSelected();
        deleteSystemFilesCheckBox.setEnabled(selected);
        deleteHomeDirectoryCheckBox.setEnabled(selected);
    }

    private void setEnabledRespectingDefaultRenderer(
            JTable table, Class columnClass) {

        TableCellRenderer renderer = table.getDefaultRenderer(columnClass);

        table.setDefaultRenderer(columnClass, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table,
                    Object value, boolean isSelected, boolean hasFocus,
                    int row, int column) {

                Component component = renderer.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                component.setEnabled(table.isEnabled());
                return component;
            }
        });
    }

    private void handleListDataEvent(ListDataEvent e) {
        LOGGER.info(e.toString());
        Object source = e.getSource();

        if (source == storageDeviceListModel) {
            LOGGER.info("source == resetStorageDeviceListModel");
            if ((e.getType() == ListDataEvent.INTERVAL_ADDED)
                    && automaticModeRadioButton.isSelected()) {

                List<StorageDevice> deviceList = new ArrayList<>();
                for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
                    LOGGER.log(Level.INFO,
                            "adding index {0} to device list", i);
                    deviceList.add(storageDeviceListModel.get(i));
                }

                dlCopySwingGUI.resetStorageDevices(deviceList);
            }

        } else {
            LOGGER.log(Level.WARNING, "unknown source: {0}", source);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton autoPrintAllDocumentsRadioButton;
    private javax.swing.JRadioButton autoPrintNoneRadioButton;
    private javax.swing.JRadioButton autoPrintSingleDocumentsRadioButton;
    private javax.swing.JRadioButton automaticModeRadioButton;
    private javax.swing.ButtonGroup automaticPrintingButtonGroup;
    private javax.swing.JPanel automaticPrintingPanel;
    private javax.swing.JCheckBox backupCheckBox;
    private javax.swing.JLabel backupCopyLabel;
    private javax.swing.JButton backupDestinationButton;
    private javax.swing.JPanel backupDestinationPanel;
    private javax.swing.JPanel backupDestinationSubdirectoryPanel;
    private javax.swing.JScrollPane backupDestinationSubdirectoryScrollPane;
    private javax.swing.JTable backupDestinationSubdirectoryTable;
    private javax.swing.JTextField backupDestinationTextField;
    private javax.swing.JPanel backupDetailsPanel;
    private ch.fhnw.filecopier.FileCopierPanel backupFileCopierPanel;
    private javax.swing.JPanel backupPanel;
    private javax.swing.JPanel backupSourcePanel;
    private javax.swing.JTextField backupSourceTextField;
    private javax.swing.JLabel currentlyResettingDeviceLabel;
    private javax.swing.JLabel dataDefinitionLabel;
    private javax.swing.ButtonGroup deleteDataPartitionButtonGroup;
    private javax.swing.JPanel deleteDataPartitionDetailsPanel;
    private javax.swing.JPanel deleteExchangePartitionDetailsPanel;
    private javax.swing.JRadioButton deleteFilesRadioButton;
    private javax.swing.JCheckBox deleteFromDataPartitionCheckBox;
    private javax.swing.JCheckBox deleteHomeDirectoryCheckBox;
    private javax.swing.JPanel deletePanel;
    private javax.swing.JCheckBox deleteSystemFilesCheckBox;
    private javax.swing.JLabel exchangeDefinitionLabel;
    private javax.swing.ButtonGroup exchangePartitionLabelButtonGroup;
    private javax.swing.JPanel exchangeSpacer;
    private javax.swing.JRadioButton formatDataPartitionRadioButton;
    private javax.swing.JCheckBox formatExchangePartitionCheckBox;
    private javax.swing.JComboBox<String> formatExchangePartitionFileSystemComboBox;
    private javax.swing.JLabel formatExchangePartitionFileSystemLabel;
    private javax.swing.JRadioButton formatExchangePartitionKeepLabelRadioButton;
    private javax.swing.JRadioButton formatExchangePartitionNewLabelRadioButton;
    private javax.swing.JTextField formatExchangePartitionNewLabelTextField;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JButton moveDownButton;
    private javax.swing.JButton moveUpButton;
    private javax.swing.JLabel noMediaLabel;
    private javax.swing.JPanel noMediaPanel;
    private javax.swing.JLabel osDefinitionLabel;
    private javax.swing.JLabel printCopiesLabel;
    private javax.swing.JPanel printCopiesPanel;
    private javax.swing.JSpinner printCopiesSpinner;
    private javax.swing.JCheckBox printDocCheckBox;
    private javax.swing.JCheckBox printDocumentsCheckBox;
    private javax.swing.JCheckBox printDocxCheckBox;
    private javax.swing.JCheckBox printDuplexCheckBox;
    private javax.swing.JPanel printFileFormatsPanel;
    private javax.swing.JCheckBox printOdpCheckBox;
    private javax.swing.JCheckBox printOdsCheckBox;
    private javax.swing.JCheckBox printOdtCheckBox;
    private javax.swing.JCheckBox printPdfCheckBox;
    private javax.swing.JCheckBox printPptCheckBox;
    private javax.swing.JCheckBox printPptxCheckBox;
    private javax.swing.JCheckBox printXlsCheckBox;
    private javax.swing.JCheckBox printXlsxCheckBox;
    private javax.swing.JPanel printingDetailsPanel;
    private javax.swing.JScrollPane printingDirectoriesScrollPane;
    private javax.swing.JTextArea printingDirectoriesTextArea;
    private javax.swing.JPanel printingDirectoryPanel;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JPanel resetCardPanel;
    private javax.swing.JLabel resetInfoLabel;
    private javax.swing.JPanel resetPanel;
    private ch.fhnw.dlcopy.gui.swing.OverwriteConfigurationPanel restoreConfigurationPanel;
    private javax.swing.JCheckBox restoreDataCheckBox;
    private javax.swing.JPanel restorePanel;
    private javax.swing.JPanel rightPrintingPanel;
    private javax.swing.JCheckBox scanDirectoriesRecursivelyCheckBox;
    private javax.swing.JRadioButton selectFromListModeRadioButton;
    private javax.swing.JTabbedPane selectionAndConfigurationTabbedPane;
    private javax.swing.JPanel selectionCardPanel;
    private javax.swing.JLabel selectionCountLabel;
    private javax.swing.JPanel selectionDeviceListPanel;
    private javax.swing.JLabel selectionHeaderLabel;
    private javax.swing.JPanel selectionInfoPanel;
    private javax.swing.ButtonGroup selectionModeButtonGroup;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JCheckBox showHardDisksCheckBox;
    private javax.swing.JPanel spacerPanel;
    private javax.swing.JList<StorageDevice> storageDeviceList;
    private javax.swing.JScrollPane storageDeviceListScrollPane;
    // End of variables declaration//GEN-END:variables
}
