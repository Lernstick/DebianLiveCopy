package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.RepartitionStrategy;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.SystemSource;
import static ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI.showCard;
import ch.fhnw.dlcopy.gui.swing.preferences.DLCopySwingGUIPreferencesHandler;
import ch.fhnw.dlcopy.gui.swing.preferences.UpgradePreferences;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.FileCopierPanel;
import ch.fhnw.jbackpack.JSqueezedLabel;
import ch.fhnw.jbackpack.chooser.SelectBackupDirectoryDialog;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.StorageDevice;
import java.awt.Color;
import java.awt.Container;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The panels needed for the Upgrader
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpgraderPanels extends JPanel implements ListDataListener {

    private static final Logger LOGGER
            = Logger.getLogger(UpgraderPanels.class.getName());

    private final DefaultListModel<StorageDevice> storageDeviceListModel;
    private final DefaultListModel<String> overwriteListModel;
    private final ResultsTableModel resultsTableModel;
    private final FileFilter noHiddenFilesFilter;

    private final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private final Color DARK_GREEN = new Color(0, 190, 0);

    private DLCopySwingGUI dlCopySwingGUI;
    private SystemSource runningSystemSource;
    private UpgradeStorageDeviceRenderer storageDeviceRenderer;
    private JFileChooser addFileChooser;

    /**
     * Creates new form UpgraderPanels
     */
    public UpgraderPanels() {

        initComponents();

        storageDeviceListModel = new DefaultListModel<>();
        overwriteListModel = new DefaultListModel<>();
        resultsTableModel = new ResultsTableModel(resultsTable);
        noHiddenFilesFilter = NoHiddenFilesSwingFileFilter.getInstance();

        resultsTable.setModel(resultsTableModel);
        TableColumn sizeColumn = resultsTable.getColumnModel().getColumn(
                ResultsTableModel.SIZE_COLUMN);
        sizeColumn.setCellRenderer(new SizeTableCellRenderer());
        resultsTable.setRowSorter(new ResultsTableRowSorter(resultsTableModel));

    }

    // post-constructor initialization
    public void init(DLCopySwingGUI dlCopySwingGUI,
            SystemSource runningSystemSource,
            DLCopySwingGUIPreferencesHandler preferencesHandler) {

        this.dlCopySwingGUI = dlCopySwingGUI;
        this.runningSystemSource = runningSystemSource;

        preferencesHandler.addPreference(new UpgradePreferences(
                upgradeSystemPartitionCheckBox, resetDataPartitionCheckBox,
                keepPrinterSettingsCheckBox, keepNetworkSettingsCheckBox,
                keepFirewallSettingsCheckBox, keepUserSettingsCheckBox,
                reactivateWelcomeCheckBox, deleteHiddenFilesCheckBox,
                automaticBackupCheckBox, automaticBackupTextField,
                automaticBackupDeleteCheckBox, overwriteListModel));

        // do not show initial "{0}" placeholder
        selectionCountLabel.setText(MessageFormat.format(
                STRINGS.getString("Selection_Count"), 0, 0));

        storageDeviceListModel.addListDataListener(this);
        storageDeviceList.setModel(storageDeviceListModel);

        storageDeviceRenderer
                = new UpgradeStorageDeviceRenderer(runningSystemSource);
        storageDeviceList.setCellRenderer(storageDeviceRenderer);

        overwriteListModel.addListDataListener(this);
        overwriteList.setModel(overwriteListModel);
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

    public void setSystemSource(SystemSource systemSource) {
        String sizeString = LernstickFileTools.getDataVolumeString(
                systemSource.getSystemSize(), 1);
        String text = STRINGS.getString("Select_Upgrade_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        selectionHeaderLabel.setText(text);
    }

    public void upgradeStorageDeviceListChanged(boolean instantUpgrade) {

        if (listModeRadioButton.isSelected()) {
            dlCopySwingGUI.storageDeviceListChanged(
                    storageDeviceListModel, selectionCardPanel,
                    "noMediaPanel", "selectionDeviceListPanel",
                    storageDeviceRenderer, storageDeviceList);
        }

        updateUpgradeSelectionCountAndNextButton();

        // run instant upgrade if needed
        if (instantUpgrade) {
            storageDeviceList.setSelectionInterval(
                    0, storageDeviceListModel.size() - 1);
        }
    }

    /**
     * must be called whenever the selection count and next button for the
     * upgrader needs an update
     */
    public void updateUpgradeSelectionCountAndNextButton() {

        // early return
        if ((dlCopySwingGUI == null)
                || (dlCopySwingGUI.state
                != DLCopySwingGUI.State.UPGRADE_SELECTION)) {
            return;
        }

        boolean backupSelected = automaticBackupCheckBox.isSelected();

        // check all selected storage devices
        boolean canUpgrade = true;
        int[] selectedIndices = storageDeviceList.getSelectedIndices();
        for (int i : selectedIndices) {
            StorageDevice storageDevice = storageDeviceListModel.get(i);
            try {
                StorageDevice.SystemUpgradeVariant systemUpgradeVariant
                        = storageDevice.getSystemUpgradeVariant(
                                DLCopy.getEnlargedSystemSize(
                                        runningSystemSource.getSystemSize()));

                switch (systemUpgradeVariant) {
                    case REGULAR:
                    case REPARTITION:
                        switch (storageDevice.getEfiUpgradeVariant(
                                DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)) {
                            case ENLARGE_BACKUP:
                                if (!backupSelected) {
                                    canUpgrade = false;
                                }
                                break;
                        }
                        break;

                    case IMPOSSIBLE:
                        canUpgrade = false;
                        break;

                    case BACKUP:
                        if (!backupSelected) {
                            canUpgrade = false;
                        }
                        break;

                    default:
                        LOGGER.log(java.util.logging.Level.WARNING,
                                "unsupported systemUpgradeVariant: {0}",
                                systemUpgradeVariant);
                }
                if (!canUpgrade) {
                    break;
                }
            } catch (DBusException | IOException ex) {
                LOGGER.log(java.util.logging.Level.SEVERE, "", ex);
            }
        }

        int selectionCount = selectedIndices.length;
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, storageDeviceListModel.size());
        selectionCountLabel.setText(countString);

        // update nextButton state
        if ((selectionCount > 0) && canUpgrade
                && listModeRadioButton.isSelected()) {
            dlCopySwingGUI.enableNextButton();
        } else {
            dlCopySwingGUI.disableNextButton();
        }
    }

    public void setReactivateWelcomeCheckBoxSelected(boolean selected) {
        reactivateWelcomeCheckBox.setSelected(selected);
    }

    public void clickAutomaticRadioButton() {
        automaticRadioButton.doClick();
    }

    public void setGuiToIsolatedAutoUpgrade() {
        // reduce the upgrade parts
        selectionHeaderLabel.setVisible(false);
        showHardDisksCheckBox.setVisible(false);
        listModeRadioButton.setVisible(false);
        automaticRadioButton.setVisible(false);
        selectionTabbedPane.remove(optionsPanel);
        selectionTabbedPane.remove(overwritePanel);
        progressTabbedPane.remove(reportPanel);

        // change colors
        noMediaPanel.setBackground(Color.YELLOW);
        currentUpgradePanel.setBackground(Color.RED);
        indeterminateProgressPanel.setBackground(Color.RED);
        fileCopierPanel.setBackground(Color.RED);
        copyPanel.setBackground(Color.RED);

        // change texts
        noMediaLabel.setText(STRINGS.getString("Insert_Media_Isolated"));
    }

    public void upgradeSelectedStorageDevices(boolean instantUpgrade) {
        // some backup related sanity checks
        if (!upgradeSanityChecks()) {
            return;
        }

        List<StorageDevice> selectedDevices
                = storageDeviceList.getSelectedValuesList();
        int noDataPartitionCounter = 0;
        for (StorageDevice selectedDevice : selectedDevices) {
            if (selectedDevice.getDataPartition() == null) {
                noDataPartitionCounter++;
            }
        }

        if (noDataPartitionCounter != 0) {
            int result = JOptionPane.showConfirmDialog(this,
                    STRINGS.getString("Warning_Upgrade_Without_Data_Partition"),
                    STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (!instantUpgrade) {
            int result = JOptionPane.showConfirmDialog(this,
                    STRINGS.getString("Final_Upgrade_Warning"),
                    STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        List<StorageDevice> deviceList = new ArrayList<>();
        for (int i : storageDeviceList.getSelectedIndices()) {
            deviceList.add(storageDeviceListModel.get(i));
        }
        dlCopySwingGUI.upgradeStorageDevices(deviceList);
    }

    public boolean isShowHardDiskSelected() {
        return showHardDisksCheckBox.isSelected();
    }

    public boolean isListModeSelected() {
        return listModeRadioButton.isSelected();
    }

    public boolean isAutomaticUpgradeSelected() {
        return automaticRadioButton.isSelected();
    }

    public boolean isAutomaticBackupSelected() {
        return automaticBackupCheckBox.isSelected();
    }

    public String getBackupDestination() {
        return automaticBackupTextField.getText();
    }

    public boolean isAutomaticBackupDeleteSelected() {
        return automaticBackupDeleteCheckBox.isSelected();
    }

    public boolean isUpgradeSystemPartitionSelected() {
        return upgradeSystemPartitionCheckBox.isSelected();
    }

    public boolean isResetDataPartitionSelected() {
        return resetDataPartitionCheckBox.isSelected();
    }

    public boolean isKeepPrinterSettingsSelected() {
        return keepPrinterSettingsCheckBox.isSelected();
    }

    public boolean isKeepNetworkSettingsSelected() {
        return keepNetworkSettingsCheckBox.isSelected();
    }

    public boolean isKeepFirewallSettingsSelected() {
        return keepFirewallSettingsCheckBox.isSelected();
    }

    public boolean isKeepUserSettingsSelected() {
        return keepUserSettingsCheckBox.isSelected();
    }

    public boolean isReactivateWelcomeSelected() {
        return reactivateWelcomeCheckBox.isSelected();
    }

    public boolean isDeleteHiddenFilesSelected() {
        return deleteHiddenFilesCheckBox.isSelected();
    }

    public DefaultListModel<StorageDevice> getDeviceListModel() {
        return storageDeviceListModel;
    }

    public JList<StorageDevice> getDeviceList() {
        return storageDeviceList;
    }

    public DefaultListModel<String> getUpgradeOverwriteListModel() {
        return overwriteListModel;
    }

    public ResultsTableModel getResultsTableModel() {
        return resultsTableModel;
    }

    public RepartitionStrategy getRepartitionStrategy() {
        RepartitionStrategy repartitionStrategy;
        if (originalExchangeRadioButton.isSelected()) {
            repartitionStrategy = RepartitionStrategy.KEEP;
        } else if (resizeExchangeRadioButton.isSelected()) {
            repartitionStrategy = RepartitionStrategy.RESIZE;
        } else {
            repartitionStrategy = RepartitionStrategy.REMOVE;
        }
        return repartitionStrategy;
    }

    public int getRepartitionExchangeSize() {
        return resizeExchangeRadioButton.isSelected()
                ? Integer.parseInt(resizeExchangeTextField.getText())
                : 0;
    }

    public void startedUpgradeOnDevice(StorageDevice storageDevice,
            int batchCounter, List<StorageDeviceResult> resultsList) {

        int selectionCount = listModeRadioButton.isSelected()
                ? storageDeviceList.getSelectedIndices().length
                : 1;

        // update label
        String pattern = STRINGS.getString("Upgrade_Device_Info");
        String deviceInfo = MessageFormat.format(pattern, batchCounter,
                selectionCount,
                storageDevice.getVendor() + " " + storageDevice.getModel(), " ("
                + STRINGS.getString("Size") + ": "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1) + ", "
                + STRINGS.getString("Revision") + ": "
                + storageDevice.getRevision() + ", "
                + STRINGS.getString("Serial") + ": "
                + storageDevice.getSerial() + ", "
                + "&#47;dev&#47;" + storageDevice.getDevice() + ")");
        DLCopySwingGUI.setLabelTextonEDT(
                currentlyUpgradedDeviceLabel, deviceInfo);

        // add "in progress" entry to results table
        resultsTableModel.setList(resultsList);
    }

    public void showInfo() {
        DLCopySwingGUI.showCard(this, "infoPanel");
    }

    public void showSelection() {
        DLCopySwingGUI.showCard(this, "selectionTabbedPane");
    }

    public void showProgress() {
        DLCopySwingGUI.showCard(this, "progressTabbedPane");
    }

    public void showUpgradeBackup() {
        SwingUtilities.invokeLater(() -> {
            backupLabel.setText(STRINGS.getString("Backing_Up_User_Data"));
            backupDurationLabel.setText(timeFormat.format(new Date(0)));
            DLCopySwingGUI.showCard(upgradeCardPanel, "backupPanel");
        });
    }

    public void setUpgradeBackupProgress(String progressInfo) {
        DLCopySwingGUI.setLabelTextonEDT(backupProgressLabel, progressInfo);
    }

    public void setUpgradeBackupFilename(String filename) {
        DLCopySwingGUI.setLabelTextonEDT(backupFilenameLabel, filename);
    }

    public void setUpgradeBackupDuration(long time) {
        DLCopySwingGUI.setLabelTextonEDT(backupDurationLabel,
                timeFormat.format(new Date(time)));
    }

    public void showUpgradeBackupExchangePartition(FileCopier fileCopier) {
        SwingUtilities.invokeLater(() -> {
            copyLabel.setText(STRINGS.getString(
                    "Backing_Up_Exchange_Partition"));
            DLCopySwingGUI.showCard(upgradeCardPanel, "copyPanel");
        });
        fileCopierPanel.setFileCopier(fileCopier);
    }

    public void showUpgradeRestoreInit() {
        SwingUtilities.invokeLater(() -> {
            indeterminateProgressBar.setString(
                    STRINGS.getString("Reading_Backup"));
            DLCopySwingGUI.showCard(upgradeCardPanel,
                    "indeterminateProgressPanel");
        });
    }

    public void showUpgradeRestoreRunning() {
        SwingUtilities.invokeLater(() -> {
            backupLabel.setText(STRINGS.getString("Restoring_User_Data"));
            backupDurationLabel.setText(timeFormat.format(new Date(0)));
            DLCopySwingGUI.showCard(upgradeCardPanel, "backupPanel");
        });
    }

    public void showUpgradeRestoreExchangePartition(FileCopier fileCopier) {
        SwingUtilities.invokeLater(() -> {
            copyLabel.setText(STRINGS.getString(
                    "Restoring_Exchange_Partition"));
            DLCopySwingGUI.showCard(upgradeCardPanel, "copyPanel");
        });
        fileCopierPanel.setFileCopier(fileCopier);
    }

    public void showUpgradeChangingPartitionSizes() {
        SwingUtilities.invokeLater(() -> {
            DLCopySwingGUI.showCard(upgradeCardPanel,
                    "indeterminateProgressPanel");
        });
        DLCopySwingGUI.setProgressBarStringOnEDT(
                indeterminateProgressBar, "Changing_Partition_Sizes");
    }

    public void showUpgradeDataPartitionReset() {
        SwingUtilities.invokeLater(() -> {
            DLCopySwingGUI.showCard(upgradeCardPanel,
                    "indeterminateProgressPanel");
        });
        DLCopySwingGUI.setProgressBarStringOnEDT(
                indeterminateProgressBar, "Resetting_Data_Partition");
    }

    public void showUpgradeIndeterminateProgressBarText(final String text) {
        showIndeterminateProgressBarText(upgradeCardPanel,
                "indeterminateProgressPanel",
                indeterminateProgressBar, text);
    }

    public void showUpgradeFileCopy(FileCopier fileCopier) {
        showFileCopy(fileCopierPanel, fileCopier, copyLabel,
                upgradeCardPanel, "copyPanel");
    }

    public void showUpgradeSystemPartitionReset() {
        SwingUtilities.invokeLater(() -> {
            showCard(upgradeCardPanel, "indeterminateProgressPanel");
            indeterminateProgressBar.setString(
                    STRINGS.getString("Resetting_System_Partition"));
        });
    }

    public void finishedUpgradeOnDevice(List<StorageDeviceResult> resultsList) {
        resultsTableModel.setList(resultsList);
    }

    public void promptForNextIsolatedAutoUpgradeMedium() {
        noMediaPanel.setBackground(Color.YELLOW);
        noMediaLabel.setText(STRINGS.getString("Insert_Media_Isolated"));
    }

    public void isolatedAutoUpgradeDone() {
        noMediaPanel.setBackground(DARK_GREEN);
        noMediaLabel.setText(STRINGS.getString("Upgrade_Done_Isolated"));
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

        upgradeSelectionModeButtonGroup = new javax.swing.ButtonGroup();
        exchangeButtonGroup = new javax.swing.ButtonGroup();
        infoPanel = new javax.swing.JPanel();
        infoLabel = new javax.swing.JLabel();
        selectionTabbedPane = new javax.swing.JTabbedPane();
        selectionPanel = new javax.swing.JPanel();
        listModeRadioButton = new javax.swing.JRadioButton();
        showHardDisksCheckBox = new javax.swing.JCheckBox();
        automaticRadioButton = new javax.swing.JRadioButton();
        selectionSeparator = new javax.swing.JSeparator();
        selectionCardPanel = new javax.swing.JPanel();
        selectionDeviceListPanel = new javax.swing.JPanel();
        selectionInfoPanel = new javax.swing.JPanel();
        selectionHeaderLabel = new javax.swing.JLabel();
        selectionCountLabel = new javax.swing.JLabel();
        storageDeviceListScrollPane = new javax.swing.JScrollPane();
        storageDeviceList = new javax.swing.JList<>();
        exchangeDefinitionLabel = new javax.swing.JLabel();
        dataDefinitionLabel = new javax.swing.JLabel();
        bootDefinitionLabel = new javax.swing.JLabel();
        osDefinitionLabel = new javax.swing.JLabel();
        noMediaPanel = new javax.swing.JPanel();
        noMediaLabel = new javax.swing.JLabel();
        optionsPanel = new javax.swing.JPanel();
        upgradeSystemPartitionCheckBox = new javax.swing.JCheckBox();
        resetDataPartitionCheckBox = new javax.swing.JCheckBox();
        reactivateWelcomeCheckBox = new javax.swing.JCheckBox();
        keepPrinterSettingsCheckBox = new javax.swing.JCheckBox();
        keepNetworkSettingsCheckBox = new javax.swing.JCheckBox();
        keepFirewallSettingsCheckBox = new javax.swing.JCheckBox();
        keepUserSettingsCheckBox = new javax.swing.JCheckBox();
        deleteHiddenFilesCheckBox = new javax.swing.JCheckBox();
        automaticBackupCheckBox = new javax.swing.JCheckBox();
        backupDestinationPanel = new javax.swing.JPanel();
        automaticBackupLabel = new javax.swing.JLabel();
        automaticBackupTextField = new javax.swing.JTextField();
        automaticBackupButton = new javax.swing.JButton();
        automaticBackupDeleteCheckBox = new javax.swing.JCheckBox();
        repartitionExchangeOptionsPanel = new javax.swing.JPanel();
        originalExchangeRadioButton = new javax.swing.JRadioButton();
        removeExchangeRadioButton = new javax.swing.JRadioButton();
        resizeExchangeRadioButton = new javax.swing.JRadioButton();
        resizeExchangeTextField = new javax.swing.JTextField();
        resizeExchangeLabel = new javax.swing.JLabel();
        overwritePanel = new javax.swing.JPanel();
        moveUpButton = new javax.swing.JButton();
        moveDownButton = new javax.swing.JButton();
        sortAscendingButton = new javax.swing.JButton();
        sortDescendingButton = new javax.swing.JButton();
        overwriteScrollPane = new javax.swing.JScrollPane();
        overwriteList = new javax.swing.JList<>();
        overwriteAddButton = new javax.swing.JButton();
        overwriteEditButton = new javax.swing.JButton();
        overwriteRemoveButton = new javax.swing.JButton();
        overwriteExportButton = new javax.swing.JButton();
        overwriteImportButton = new javax.swing.JButton();
        progressTabbedPane = new javax.swing.JTabbedPane();
        currentUpgradePanel = new javax.swing.JPanel();
        currentlyUpgradedDeviceLabel = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        upgradeCardPanel = new javax.swing.JPanel();
        indeterminateProgressPanel = new javax.swing.JPanel();
        indeterminateProgressBar = new javax.swing.JProgressBar();
        copyPanel = new javax.swing.JPanel();
        copyLabel = new javax.swing.JLabel();
        fileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
        backupPanel = new javax.swing.JPanel();
        backupLabel = new javax.swing.JLabel();
        backupProgressLabel = new javax.swing.JLabel();
        backupFilenameLabel = new JSqueezedLabel();
        backupProgressBar = new javax.swing.JProgressBar();
        backupDurationLabel = new javax.swing.JLabel();
        reportPanel = new javax.swing.JPanel();
        resultsScrollPane = new javax.swing.JScrollPane();
        resultsTable = new javax.swing.JTable();

        setLayout(new java.awt.CardLayout());

        infoPanel.setLayout(new java.awt.GridBagLayout());

        infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/usbupgrade.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        infoLabel.setText(bundle.getString("DLCopySwingGUI.upgradeInfoLabel.text")); // NOI18N
        infoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        infoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        infoPanel.add(infoLabel, gridBagConstraints);

        add(infoPanel, "infoPanel");

        selectionTabbedPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                selectionTabbedPaneComponentShown(evt);
            }
        });

        selectionPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSelectionModeButtonGroup.add(listModeRadioButton);
        listModeRadioButton.setSelected(true);
        listModeRadioButton.setText(bundle.getString("Select_Storage_Media_From_List")); // NOI18N
        listModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                listModeRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        selectionPanel.add(listModeRadioButton, gridBagConstraints);

        showHardDisksCheckBox.setFont(showHardDisksCheckBox.getFont().deriveFont(showHardDisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, showHardDisksCheckBox.getFont().getSize()-1));
        showHardDisksCheckBox.setText(bundle.getString("DLCopySwingGUI.upgradeShowHardDisksCheckBox.text")); // NOI18N
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

        upgradeSelectionModeButtonGroup.add(automaticRadioButton);
        automaticRadioButton.setText(bundle.getString("DLCopySwingGUI.upgradeAutomaticRadioButton.text")); // NOI18N
        automaticRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                automaticRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        selectionPanel.add(automaticRadioButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        selectionPanel.add(selectionSeparator, gridBagConstraints);

        selectionCardPanel.setName("selectionCardPanel"); // NOI18N
        selectionCardPanel.setLayout(new java.awt.CardLayout());

        selectionDeviceListPanel.setLayout(new java.awt.GridBagLayout());

        selectionInfoPanel.setLayout(new java.awt.GridBagLayout());

        selectionHeaderLabel.setFont(selectionHeaderLabel.getFont().deriveFont(selectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        selectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        selectionHeaderLabel.setText(bundle.getString("Select_Upgrade_Target_Storage_Media")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        selectionInfoPanel.add(selectionHeaderLabel, gridBagConstraints);

        selectionCountLabel.setText(bundle.getString("Selection_Count")); // NOI18N
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
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        selectionDeviceListPanel.add(storageDeviceListScrollPane, gridBagConstraints);

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("ExchangePartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        selectionDeviceListPanel.add(exchangeDefinitionLabel, gridBagConstraints);

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DataPartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 10);
        selectionDeviceListPanel.add(dataDefinitionLabel, gridBagConstraints);

        bootDefinitionLabel.setFont(bootDefinitionLabel.getFont().deriveFont(bootDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, bootDefinitionLabel.getFont().getSize()-1));
        bootDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/dark_blue_box.png"))); // NOI18N
        bootDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBootDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 0);
        selectionDeviceListPanel.add(bootDefinitionLabel, gridBagConstraints);

        osDefinitionLabel.setFont(osDefinitionLabel.getFont().deriveFont(osDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, osDefinitionLabel.getFont().getSize()-1));
        osDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/blue_box.png"))); // NOI18N
        osDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.upgradeOsDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 10);
        selectionDeviceListPanel.add(osDefinitionLabel, gridBagConstraints);

        selectionCardPanel.add(selectionDeviceListPanel, "selectionDeviceListPanel");

        noMediaPanel.setLayout(new java.awt.GridBagLayout());

        noMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/messagebox_info.png"))); // NOI18N
        noMediaLabel.setText(bundle.getString("Insert_Media")); // NOI18N
        noMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        noMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        noMediaPanel.add(noMediaLabel, new java.awt.GridBagConstraints());

        selectionCardPanel.add(noMediaPanel, "noMediaPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        selectionPanel.add(selectionCardPanel, gridBagConstraints);

        selectionTabbedPane.addTab(bundle.getString("Selection"), selectionPanel); // NOI18N

        optionsPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSystemPartitionCheckBox.setSelected(true);
        upgradeSystemPartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.upgradeSystemPartitionCheckBox.text")); // NOI18N
        upgradeSystemPartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                upgradeSystemPartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        optionsPanel.add(upgradeSystemPartitionCheckBox, gridBagConstraints);

        resetDataPartitionCheckBox.setSelected(true);
        resetDataPartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.resetDataPartitionCheckBox.text")); // NOI18N
        resetDataPartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                resetDataPartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        optionsPanel.add(resetDataPartitionCheckBox, gridBagConstraints);

        reactivateWelcomeCheckBox.setSelected(true);
        reactivateWelcomeCheckBox.setText(bundle.getString("DLCopySwingGUI.reactivateWelcomeCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        optionsPanel.add(reactivateWelcomeCheckBox, gridBagConstraints);

        keepPrinterSettingsCheckBox.setSelected(true);
        keepPrinterSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepPrinterSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        optionsPanel.add(keepPrinterSettingsCheckBox, gridBagConstraints);

        keepNetworkSettingsCheckBox.setSelected(true);
        keepNetworkSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepNetworkSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        optionsPanel.add(keepNetworkSettingsCheckBox, gridBagConstraints);

        keepFirewallSettingsCheckBox.setSelected(true);
        keepFirewallSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepFirewallSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        optionsPanel.add(keepFirewallSettingsCheckBox, gridBagConstraints);

        keepUserSettingsCheckBox.setSelected(true);
        keepUserSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepUserSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        optionsPanel.add(keepUserSettingsCheckBox, gridBagConstraints);

        deleteHiddenFilesCheckBox.setText(bundle.getString("DLCopySwingGUI.removeHiddenFilesCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        optionsPanel.add(deleteHiddenFilesCheckBox, gridBagConstraints);

        automaticBackupCheckBox.setText(bundle.getString("DLCopySwingGUI.automaticBackupCheckBox.text")); // NOI18N
        automaticBackupCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                automaticBackupCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        optionsPanel.add(automaticBackupCheckBox, gridBagConstraints);

        backupDestinationPanel.setLayout(new java.awt.GridBagLayout());

        automaticBackupLabel.setText(bundle.getString("DLCopySwingGUI.automaticBackupLabel.text")); // NOI18N
        automaticBackupLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 30, 0, 0);
        backupDestinationPanel.add(automaticBackupLabel, gridBagConstraints);

        automaticBackupTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        backupDestinationPanel.add(automaticBackupTextField, gridBagConstraints);

        automaticBackupButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/document-open-folder.png"))); // NOI18N
        automaticBackupButton.setEnabled(false);
        automaticBackupButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        automaticBackupButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                automaticBackupButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 10);
        backupDestinationPanel.add(automaticBackupButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        optionsPanel.add(backupDestinationPanel, gridBagConstraints);

        automaticBackupDeleteCheckBox.setText(bundle.getString("DLCopySwingGUI.automaticBackupRemoveCheckBox.text")); // NOI18N
        automaticBackupDeleteCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 28, 0, 10);
        optionsPanel.add(automaticBackupDeleteCheckBox, gridBagConstraints);

        repartitionExchangeOptionsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.repartitionExchangeOptionsPanel.border.title"))); // NOI18N
        repartitionExchangeOptionsPanel.setLayout(new java.awt.GridBagLayout());

        exchangeButtonGroup.add(originalExchangeRadioButton);
        originalExchangeRadioButton.setSelected(true);
        originalExchangeRadioButton.setText(bundle.getString("DLCopySwingGUI.originalExchangeRadioButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        repartitionExchangeOptionsPanel.add(originalExchangeRadioButton, gridBagConstraints);

        exchangeButtonGroup.add(removeExchangeRadioButton);
        removeExchangeRadioButton.setText(bundle.getString("DLCopySwingGUI.removeExchangeRadioButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        repartitionExchangeOptionsPanel.add(removeExchangeRadioButton, gridBagConstraints);

        exchangeButtonGroup.add(resizeExchangeRadioButton);
        resizeExchangeRadioButton.setText(bundle.getString("DLCopySwingGUI.resizeExchangeRadioButton.text")); // NOI18N
        repartitionExchangeOptionsPanel.add(resizeExchangeRadioButton, new java.awt.GridBagConstraints());

        resizeExchangeTextField.setColumns(4);
        resizeExchangeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        repartitionExchangeOptionsPanel.add(resizeExchangeTextField, new java.awt.GridBagConstraints());

        resizeExchangeLabel.setText(bundle.getString("DLCopySwingGUI.resizeExchangeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        repartitionExchangeOptionsPanel.add(resizeExchangeLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 10, 10, 10);
        optionsPanel.add(repartitionExchangeOptionsPanel, gridBagConstraints);

        selectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeOptionsPanel.TabConstraints.tabTitle"), optionsPanel); // NOI18N

        overwritePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        overwritePanel.setLayout(new java.awt.GridBagLayout());

        moveUpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/arrow-up.png"))); // NOI18N
        moveUpButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeMoveUpButton.toolTipText")); // NOI18N
        moveUpButton.setEnabled(false);
        moveUpButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveUpButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        overwritePanel.add(moveUpButton, gridBagConstraints);

        moveDownButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/arrow-down.png"))); // NOI18N
        moveDownButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeMoveDownButton.toolTipText")); // NOI18N
        moveDownButton.setEnabled(false);
        moveDownButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveDownButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        overwritePanel.add(moveDownButton, gridBagConstraints);

        sortAscendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/view-sort-ascending.png"))); // NOI18N
        sortAscendingButton.setToolTipText(bundle.getString("DLCopySwingGUI.sortAscendingButton.toolTipText")); // NOI18N
        sortAscendingButton.setEnabled(false);
        sortAscendingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        sortAscendingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortAscendingButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        overwritePanel.add(sortAscendingButton, gridBagConstraints);

        sortDescendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/view-sort-descending.png"))); // NOI18N
        sortDescendingButton.setToolTipText(bundle.getString("DLCopySwingGUI.sortDescendingButton.toolTipText")); // NOI18N
        sortDescendingButton.setEnabled(false);
        sortDescendingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        sortDescendingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortDescendingButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        overwritePanel.add(sortDescendingButton, gridBagConstraints);

        overwriteList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                overwriteListMouseClicked(evt);
            }
        });
        overwriteList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                overwriteListValueChanged(evt);
            }
        });
        overwriteScrollPane.setViewportView(overwriteList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 0);
        overwritePanel.add(overwriteScrollPane, gridBagConstraints);

        overwriteAddButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/list-add.png"))); // NOI18N
        overwriteAddButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteAddButton.toolTipText")); // NOI18N
        overwriteAddButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        overwriteAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overwriteAddButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        overwritePanel.add(overwriteAddButton, gridBagConstraints);

        overwriteEditButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/document-edit.png"))); // NOI18N
        overwriteEditButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteEditButton.toolTipText")); // NOI18N
        overwriteEditButton.setEnabled(false);
        overwriteEditButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        overwriteEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overwriteEditButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        overwritePanel.add(overwriteEditButton, gridBagConstraints);

        overwriteRemoveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/list-remove.png"))); // NOI18N
        overwriteRemoveButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteRemoveButton.toolTipText")); // NOI18N
        overwriteRemoveButton.setEnabled(false);
        overwriteRemoveButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        overwriteRemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overwriteRemoveButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        overwritePanel.add(overwriteRemoveButton, gridBagConstraints);

        overwriteExportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/document-export.png"))); // NOI18N
        overwriteExportButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteExportButton.toolTipText")); // NOI18N
        overwriteExportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        overwriteExportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overwriteExportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        overwritePanel.add(overwriteExportButton, gridBagConstraints);

        overwriteImportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/icons/16x16/document-import.png"))); // NOI18N
        overwriteImportButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteImportButton.toolTipText")); // NOI18N
        overwriteImportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        overwriteImportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                overwriteImportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        overwritePanel.add(overwriteImportButton, gridBagConstraints);

        selectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeOverwritePanel.TabConstraints.tabTitle"), overwritePanel); // NOI18N

        add(selectionTabbedPane, "selectionTabbedPane");

        currentUpgradePanel.setLayout(new java.awt.GridBagLayout());

        currentlyUpgradedDeviceLabel.setFont(currentlyUpgradedDeviceLabel.getFont().deriveFont(currentlyUpgradedDeviceLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        currentlyUpgradedDeviceLabel.setText(bundle.getString("Upgrade_Device_Info")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        currentUpgradePanel.add(currentlyUpgradedDeviceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        currentUpgradePanel.add(jSeparator4, gridBagConstraints);

        upgradeCardPanel.setName("upgradeCardPanel"); // NOI18N
        upgradeCardPanel.setLayout(new java.awt.CardLayout());

        indeterminateProgressPanel.setLayout(new java.awt.GridBagLayout());

        indeterminateProgressBar.setIndeterminate(true);
        indeterminateProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        indeterminateProgressBar.setString(bundle.getString("DLCopySwingGUI.upgradeIndeterminateProgressBar.string")); // NOI18N
        indeterminateProgressBar.setStringPainted(true);
        indeterminateProgressPanel.add(indeterminateProgressBar, new java.awt.GridBagConstraints());

        upgradeCardPanel.add(indeterminateProgressPanel, "indeterminateProgressPanel");

        copyPanel.setLayout(new java.awt.GridBagLayout());

        copyLabel.setText(bundle.getString("DLCopySwingGUI.upgradeCopyLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        copyPanel.add(copyLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        copyPanel.add(fileCopierPanel, gridBagConstraints);

        upgradeCardPanel.add(copyPanel, "copyPanel");

        backupPanel.setLayout(new java.awt.GridBagLayout());

        backupLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        backupPanel.add(backupLabel, gridBagConstraints);

        backupProgressLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupProgressLabel.text")); // NOI18N
        backupProgressLabel.setName("backupProgressLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        backupPanel.add(backupProgressLabel, gridBagConstraints);

        backupFilenameLabel.setFont(backupFilenameLabel.getFont().deriveFont(backupFilenameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, backupFilenameLabel.getFont().getSize()-1));
        backupFilenameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        backupFilenameLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupFilenameLabel.text")); // NOI18N
        backupFilenameLabel.setName("backupFilenameLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        backupPanel.add(backupFilenameLabel, gridBagConstraints);

        backupProgressBar.setIndeterminate(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        backupPanel.add(backupProgressBar, gridBagConstraints);

        backupDurationLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupDurationLabel.text")); // NOI18N
        backupDurationLabel.setName("backupDurationLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        backupPanel.add(backupDurationLabel, gridBagConstraints);

        upgradeCardPanel.add(backupPanel, "backupPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        currentUpgradePanel.add(upgradeCardPanel, gridBagConstraints);

        progressTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradePanel.TabConstraints.tabTitle"), currentUpgradePanel); // NOI18N

        reportPanel.setLayout(new java.awt.GridBagLayout());

        resultsTable.setAutoCreateRowSorter(true);
        resultsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        resultsScrollPane.setViewportView(resultsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        reportPanel.add(resultsScrollPane, gridBagConstraints);

        progressTabbedPane.addTab(bundle.getString("Upgrade_Report"), reportPanel); // NOI18N

        add(progressTabbedPane, "progressTabbedPane");
    }// </editor-fold>//GEN-END:initComponents

    private void listModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_listModeRadioButtonActionPerformed
        DLCopySwingGUI.showCard(selectionCardPanel, "selectionDeviceListPanel");
        updateStorageDeviceList();
        showHardDisksCheckBox.setEnabled(listModeRadioButton.isSelected());
    }//GEN-LAST:event_listModeRadioButtonActionPerformed

    private void showHardDisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_showHardDisksCheckBoxItemStateChanged
        updateStorageDeviceList();
    }//GEN-LAST:event_showHardDisksCheckBoxItemStateChanged

    private void automaticRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_automaticRadioButtonActionPerformed
        DLCopySwingGUI.showCard(selectionCardPanel, "noMediaPanel");
        dlCopySwingGUI.disableNextButton();
        showHardDisksCheckBox.setEnabled(!automaticRadioButton.isSelected());
    }//GEN-LAST:event_automaticRadioButtonActionPerformed

    private void storageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_storageDeviceListValueChanged
        updateUpgradeSelectionCountAndNextButton();
    }//GEN-LAST:event_storageDeviceListValueChanged

    private void upgradeSystemPartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeSystemPartitionCheckBoxItemStateChanged
        resetDataPartitionCheckBox.setSelected(true);
    }//GEN-LAST:event_upgradeSystemPartitionCheckBoxItemStateChanged

    private void resetDataPartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_resetDataPartitionCheckBoxItemStateChanged
        boolean selected = resetDataPartitionCheckBox.isSelected();
        keepFirewallSettingsCheckBox.setEnabled(selected);
        keepNetworkSettingsCheckBox.setEnabled(selected);
        keepPrinterSettingsCheckBox.setEnabled(selected);
        keepUserSettingsCheckBox.setEnabled(selected);

        if (!selected && upgradeSystemPartitionCheckBox.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Warning_Reset_Data_Partition_Enforced"),
                    STRINGS.getString("Warning"),
                    JOptionPane.WARNING_MESSAGE);
            resetDataPartitionCheckBox.setSelected(true);
        }
    }//GEN-LAST:event_resetDataPartitionCheckBoxItemStateChanged

    private void automaticBackupCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_automaticBackupCheckBoxItemStateChanged
        boolean automaticBackup = automaticBackupCheckBox.isSelected();
        automaticBackupLabel.setEnabled(automaticBackup);
        automaticBackupTextField.setEnabled(automaticBackup);
        automaticBackupButton.setEnabled(automaticBackup);
        automaticBackupDeleteCheckBox.setEnabled(automaticBackup);
        updateUpgradeSelectionCountAndNextButton();
    }//GEN-LAST:event_automaticBackupCheckBoxItemStateChanged

    private void automaticBackupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_automaticBackupButtonActionPerformed
        selectBackupDestination(automaticBackupTextField);
    }//GEN-LAST:event_automaticBackupButtonActionPerformed

    private void moveUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveUpButtonActionPerformed
        int selectedIndices[] = overwriteList.getSelectedIndices();
        overwriteList.clearSelection();
        for (int selectedIndex : selectedIndices) {
            // swap values with previous index
            int previousIndex = selectedIndex - 1;
            String previousValue = overwriteListModel.get(previousIndex);
            String value = overwriteListModel.get(selectedIndex);
            overwriteListModel.set(previousIndex, value);
            overwriteListModel.set(selectedIndex, previousValue);
            overwriteList.addSelectionInterval(previousIndex, previousIndex);
        }
    }//GEN-LAST:event_moveUpButtonActionPerformed

    private void moveDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveDownButtonActionPerformed
        int selectedIndices[] = overwriteList.getSelectedIndices();
        overwriteList.clearSelection();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            // swap values with next index
            int selectedIndex = selectedIndices[i];
            int nextIndex = selectedIndex + 1;
            String nextValue = overwriteListModel.get(nextIndex);
            String value = overwriteListModel.get(selectedIndex);
            overwriteListModel.set(nextIndex, value);
            overwriteListModel.set(selectedIndex, nextValue);
            overwriteList.addSelectionInterval(nextIndex, nextIndex);
        }
    }//GEN-LAST:event_moveDownButtonActionPerformed

    private void sortAscendingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortAscendingButtonActionPerformed
        sortList(true);
    }//GEN-LAST:event_sortAscendingButtonActionPerformed

    private void sortDescendingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortDescendingButtonActionPerformed
        sortList(false);
    }//GEN-LAST:event_sortDescendingButtonActionPerformed

    private void overwriteListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_overwriteListMouseClicked
        if (evt.getClickCount() == 2) {
            editPathListEntry(overwriteList,
                    JFileChooser.FILES_AND_DIRECTORIES);
        }
    }//GEN-LAST:event_overwriteListMouseClicked

    private void overwriteListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_overwriteListValueChanged
        int[] selectedIndices = overwriteList.getSelectedIndices();
        boolean selected = selectedIndices.length > 0;
        moveUpButton.setEnabled(selected && (selectedIndices[0] != 0));
        if (selected) {
            int lastSelectionIndex
                    = selectedIndices[selectedIndices.length - 1];
            int lastListIndex = overwriteListModel.getSize() - 1;
            moveDownButton.setEnabled(lastSelectionIndex != lastListIndex);
        } else {
            moveDownButton.setEnabled(false);
        }
        overwriteEditButton.setEnabled(selectedIndices.length == 1);
        overwriteRemoveButton.setEnabled(selected);
    }//GEN-LAST:event_overwriteListValueChanged

    private void overwriteAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overwriteAddButtonActionPerformed
        addPathToList(JFileChooser.FILES_AND_DIRECTORIES, overwriteListModel);
        // adding elements could enable the "move down" button
        // therefore we trigger a "spurious" selection update event here
        overwriteListValueChanged(null);
    }//GEN-LAST:event_overwriteAddButtonActionPerformed

    private void overwriteEditButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overwriteEditButtonActionPerformed
        editPathListEntry(overwriteList, JFileChooser.FILES_AND_DIRECTORIES);
    }//GEN-LAST:event_overwriteEditButtonActionPerformed

    private void overwriteRemoveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overwriteRemoveButtonActionPerformed
        removeSelectedListEntries(overwriteList);
    }//GEN-LAST:event_overwriteRemoveButtonActionPerformed

    private void overwriteExportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overwriteExportButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try (FileWriter fileWriter = new FileWriter(selectedFile)) {
                String listString = getUpgradeOverwriteListString();
                fileWriter.write(listString);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }
    }//GEN-LAST:event_overwriteExportButtonActionPerformed

    private void overwriteImportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_overwriteImportButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            overwriteListModel.clear();
            File selectedFile = fileChooser.getSelectedFile();
            try (FileReader fileReader = new FileReader(selectedFile)) {
                try (BufferedReader bufferedReader
                        = new BufferedReader(fileReader)) {
                    for (String line = bufferedReader.readLine(); line != null;
                            line = bufferedReader.readLine()) {
                        overwriteListModel.addElement(line);
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }
    }//GEN-LAST:event_overwriteImportButtonActionPerformed

    private void selectionTabbedPaneComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_selectionTabbedPaneComponentShown
        if (listModeRadioButton.isSelected()) {
            updateStorageDeviceList();
        }
    }//GEN-LAST:event_selectionTabbedPaneComponentShown

    private void updateStorageDeviceList() {
        new UpgradeStorageDeviceListUpdater(runningSystemSource,
                dlCopySwingGUI, storageDeviceList, storageDeviceListModel,
                showHardDisksCheckBox.isSelected()).execute();
    }

    private void selectBackupDestination(JTextField textField) {
        String selectedPath = textField.getText();
        SelectBackupDirectoryDialog dialog = new SelectBackupDirectoryDialog(
                dlCopySwingGUI, null, selectedPath, false);
        if (dialog.showDialog() == JOptionPane.OK_OPTION) {
            textField.setText(dialog.getSelectedPath());
        }
    }

    private void sortList(boolean ascending) {
        // remember selection before sorting
        List<String> selectedValues = overwriteList.getSelectedValuesList();

        // sort
        List<String> list = new ArrayList<>();
        Enumeration enumeration = overwriteListModel.elements();
        while (enumeration.hasMoreElements()) {
            list.add((String) enumeration.nextElement());
        }
        if (ascending) {
            Collections.sort(list);
        } else {
            Collections.sort(list, Collections.reverseOrder());
        }

        // refill list with sorted values
        overwriteListModel.removeAllElements();
        list.forEach((string) -> {
            overwriteListModel.addElement(string);
        });

        // restore original selection
        for (String selectedValue : selectedValues) {
            int selectedIndex = overwriteListModel.indexOf(selectedValue);
            overwriteList.addSelectionInterval(selectedIndex, selectedIndex);
        }
    }

    private void editPathListEntry(JList<String> list, int selectionMode) {
        Object selectedValue = list.getSelectedValue();
        if (selectedValue == null) {
            // happens when double clicking in an empty list...
            return;
        }
        String oldPath = (String) selectedValue;
        File oldDirectory = new File(oldPath);
        JFileChooser fileChooser
                = new JFileChooser(oldDirectory.getParentFile());
        fileChooser.setFileSelectionMode(selectionMode);
        fileChooser.setSelectedFile(oldDirectory);
        addHiddenFilesFilter(fileChooser);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String newPath = fileChooser.getSelectedFile().getPath();
            DefaultListModel<String> model
                    = (DefaultListModel<String>) list.getModel();
            model.set(list.getSelectedIndex(), newPath);
        }
    }

    private void removeSelectedListEntries(JList list) {
        int[] selectedIndices = list.getSelectedIndices();
        DefaultListModel model = (DefaultListModel) list.getModel();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            model.remove(selectedIndices[i]);
        }
    }

    private void addPathToList(int selectionMode,
            DefaultListModel<String> listModel) {
        if (addFileChooser == null) {
            addFileChooser = new JFileChooser("/");
            addFileChooser.setFileSelectionMode(selectionMode);
            addFileChooser.setMultiSelectionEnabled(true);
            addHiddenFilesFilter(addFileChooser);
        }
        if (addFileChooser.showOpenDialog(this)
                == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = addFileChooser.getSelectedFiles();
            for (File selectedFile : selectedFiles) {
                String selectedPath = selectedFile.getPath();
                listModel.addElement(selectedPath);
            }
        }
    }

    private void addHiddenFilesFilter(final JFileChooser fileChooser) {
        fileChooser.addPropertyChangeListener(e -> {
            fileChooser.setFileHidingEnabled(
                    fileChooser.getFileFilter() == noHiddenFilesFilter);
            fileChooser.rescanCurrentDirectory();
        });
        fileChooser.addChoosableFileFilter(noHiddenFilesFilter);
        fileChooser.setFileFilter(noHiddenFilesFilter);
    }

    private String getUpgradeOverwriteListString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0, size = overwriteListModel.size(); i < size; i++) {
            String entry = overwriteListModel.get(i);
            stringBuilder.append(entry);
            if (i != (size - 1)) {
                stringBuilder.append('\n');
            }
        }
        return stringBuilder.toString();
    }

    private void handleListDataEvent(ListDataEvent e) {
        LOGGER.info(e.toString());
        Object source = e.getSource();

        if (source == overwriteListModel) {
            LOGGER.info("source == upgradeOverwriteListModel");
            boolean sortable = overwriteListModel.getSize() > 1;
            sortAscendingButton.setEnabled(sortable);
            sortDescendingButton.setEnabled(sortable);

        } else if (source == storageDeviceListModel) {
            LOGGER.info("source == upgradeStorageDeviceListModel");
            if ((e.getType() == ListDataEvent.INTERVAL_ADDED)
                    && automaticRadioButton.isSelected()) {

                List<StorageDevice> deviceList = new ArrayList<>();
                for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
                    LOGGER.log(Level.INFO,
                            "adding index {0} to device list", i);
                    deviceList.add(storageDeviceListModel.get(i));
                }

                dlCopySwingGUI.upgradeStorageDevices(deviceList);
            }

        } else {
            LOGGER.log(Level.WARNING, "unknown source: {0}", source);
        }
    }

    private boolean upgradeSanityChecks() {
        if (automaticBackupCheckBox.isSelected()) {
            String destinationPath = automaticBackupTextField.getText();
            File destinationDirectory = null;
            if (destinationPath != null) {
                destinationDirectory = new File(destinationPath);
            }

            // file checks
            String errorMessage = null;
            JTextField textField = automaticBackupTextField;
            if (destinationDirectory == null
                    || destinationDirectory.getPath().length() == 0) {
                errorMessage = STRINGS.getString(
                        "Error_No_Automatic_Backup_Directory");
            } else if (!destinationDirectory.exists()) {
                errorMessage = STRINGS.getString(
                        "Error_Automatic_Backup_Directory_Does_Not_Exist");
            } else if (!destinationDirectory.isDirectory()) {
                errorMessage = STRINGS.getString(
                        "Error_Automatic_Backup_Destination_No_Directory");
            } else if (!destinationDirectory.canRead()) {
                errorMessage = STRINGS.getString(
                        "Error_Automatic_Backup_Directory_Unreadable");
            } else if (resizeExchangeRadioButton.isSelected()) {
                String newSizeText = resizeExchangeTextField.getText();
                try {
                    Integer.parseInt(newSizeText);
                } catch (NumberFormatException ex) {
                    errorMessage = STRINGS.getString("Invalid_Partition_Size");
                    errorMessage = MessageFormat.format(
                            errorMessage, newSizeText);
                    textField = resizeExchangeTextField;
                }
            }
            if (errorMessage != null) {
                selectionTabbedPane.setSelectedComponent(optionsPanel);
                textField.requestFocusInWindow();
                textField.selectAll();
                dlCopySwingGUI.showErrorMessage(errorMessage);
                return false;
            }
        }
        if (resizeExchangeRadioButton.isSelected()) {
            String newSizeText = resizeExchangeTextField.getText();
            String errorMessage = null;
            if (newSizeText.isEmpty()) {
                errorMessage = STRINGS.getString(
                        "Error_No_Exchange_Resize_Size");
            } else {
                try {
                    Integer.parseInt(newSizeText);
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.WARNING, "", ex);
                    errorMessage = STRINGS.getString(
                            "Error_Parsing_Exchange_Resize_Size");
                }
            }
            if (errorMessage != null) {
                selectionTabbedPane.setSelectedComponent(optionsPanel);
                dlCopySwingGUI.showErrorMessage(errorMessage);
                resizeExchangeTextField.requestFocusInWindow();
                resizeExchangeTextField.selectAll();
                return false;
            }
        }
        return true;
    }

    private static void showIndeterminateProgressBarText(
            final Container container, final String cardName,
            final JProgressBar progressBar, final String text) {

        SwingUtilities.invokeLater(() -> {
            showCard(container, cardName);
            progressBar.setString(STRINGS.getString(text));
        });
    }

    private void showFileCopy(FileCopierPanel fileCopierPanel,
            FileCopier fileCopier, final JLabel label,
            final Container container, final String cardName) {

        fileCopierPanel.setFileCopier(fileCopier);

        SwingUtilities.invokeLater(() -> {
            label.setText(STRINGS.getString("Copying_Files"));
            showCard(container, cardName);
        });
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton automaticBackupButton;
    private javax.swing.JCheckBox automaticBackupCheckBox;
    private javax.swing.JCheckBox automaticBackupDeleteCheckBox;
    private javax.swing.JLabel automaticBackupLabel;
    private javax.swing.JTextField automaticBackupTextField;
    private javax.swing.JRadioButton automaticRadioButton;
    private javax.swing.JPanel backupDestinationPanel;
    private javax.swing.JLabel backupDurationLabel;
    private javax.swing.JLabel backupFilenameLabel;
    private javax.swing.JLabel backupLabel;
    private javax.swing.JPanel backupPanel;
    private javax.swing.JProgressBar backupProgressBar;
    private javax.swing.JLabel backupProgressLabel;
    private javax.swing.JLabel bootDefinitionLabel;
    private javax.swing.JLabel copyLabel;
    private javax.swing.JPanel copyPanel;
    private javax.swing.JPanel currentUpgradePanel;
    private javax.swing.JLabel currentlyUpgradedDeviceLabel;
    private javax.swing.JLabel dataDefinitionLabel;
    private javax.swing.JCheckBox deleteHiddenFilesCheckBox;
    private javax.swing.ButtonGroup exchangeButtonGroup;
    private javax.swing.JLabel exchangeDefinitionLabel;
    private ch.fhnw.filecopier.FileCopierPanel fileCopierPanel;
    private javax.swing.JProgressBar indeterminateProgressBar;
    private javax.swing.JPanel indeterminateProgressPanel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JCheckBox keepFirewallSettingsCheckBox;
    private javax.swing.JCheckBox keepNetworkSettingsCheckBox;
    private javax.swing.JCheckBox keepPrinterSettingsCheckBox;
    private javax.swing.JCheckBox keepUserSettingsCheckBox;
    private javax.swing.JRadioButton listModeRadioButton;
    private javax.swing.JButton moveDownButton;
    private javax.swing.JButton moveUpButton;
    private javax.swing.JLabel noMediaLabel;
    private javax.swing.JPanel noMediaPanel;
    private javax.swing.JPanel optionsPanel;
    private javax.swing.JRadioButton originalExchangeRadioButton;
    private javax.swing.JLabel osDefinitionLabel;
    private javax.swing.JButton overwriteAddButton;
    private javax.swing.JButton overwriteEditButton;
    private javax.swing.JButton overwriteExportButton;
    private javax.swing.JButton overwriteImportButton;
    private javax.swing.JList<String> overwriteList;
    private javax.swing.JPanel overwritePanel;
    private javax.swing.JButton overwriteRemoveButton;
    private javax.swing.JScrollPane overwriteScrollPane;
    private javax.swing.JTabbedPane progressTabbedPane;
    private javax.swing.JCheckBox reactivateWelcomeCheckBox;
    private javax.swing.JRadioButton removeExchangeRadioButton;
    private javax.swing.JPanel repartitionExchangeOptionsPanel;
    private javax.swing.JPanel reportPanel;
    private javax.swing.JCheckBox resetDataPartitionCheckBox;
    private javax.swing.JLabel resizeExchangeLabel;
    private javax.swing.JRadioButton resizeExchangeRadioButton;
    private javax.swing.JTextField resizeExchangeTextField;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JPanel selectionCardPanel;
    private javax.swing.JLabel selectionCountLabel;
    private javax.swing.JPanel selectionDeviceListPanel;
    private javax.swing.JLabel selectionHeaderLabel;
    private javax.swing.JPanel selectionInfoPanel;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JSeparator selectionSeparator;
    private javax.swing.JTabbedPane selectionTabbedPane;
    private javax.swing.JCheckBox showHardDisksCheckBox;
    private javax.swing.JButton sortAscendingButton;
    private javax.swing.JButton sortDescendingButton;
    private javax.swing.JList<StorageDevice> storageDeviceList;
    private javax.swing.JScrollPane storageDeviceListScrollPane;
    private javax.swing.JPanel upgradeCardPanel;
    private javax.swing.ButtonGroup upgradeSelectionModeButtonGroup;
    private javax.swing.JCheckBox upgradeSystemPartitionCheckBox;
    // End of variables declaration//GEN-END:variables
}
