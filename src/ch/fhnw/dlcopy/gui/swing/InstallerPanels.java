package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.IsoSystemSource;
import ch.fhnw.dlcopy.PartitionSizes;
import ch.fhnw.dlcopy.PartitionState;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.exceptions.NoExecutableExtLinuxException;
import ch.fhnw.dlcopy.exceptions.NoExtLinuxException;
import ch.fhnw.dlcopy.gui.swing.preferences.DLCopySwingGUIPreferencesHandler;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationDestinationDetailsPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationDestinationSelectionPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationDestinationTransferPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationSourcePreferences;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.jbackpack.JSqueezedLabel;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The panels needed for the Installer
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallerPanels extends JPanel implements DocumentListener {

    private static final Logger LOGGER
            = Logger.getLogger(InstallerPanels.class.getName());

    private final DefaultListModel<StorageDevice> storageDeviceListModel;
    private final DefaultListModel<StorageDevice> transferStorageDeviceListModel;
    private final ResultsTableModel resultsTableModel;
    private final char originalPasswordEchoChar;

    private InstallStorageDeviceRenderer storageDeviceRenderer;
    private InstallTransferStorageDeviceRenderer transferStorageDeviceRenderer;
    private InstallationDestinationSelectionPreferences destinationSelectionPreferences;

    private DLCopySwingGUI dlCopySwingGUI;

    private SystemSource runningSystemSource;
    private SystemSource isoSystemSource;

    private boolean textFieldTriggeredSliderChange;
    private boolean listSelectionTriggeredSliderChange;

    private int explicitExchangeSize;

    private CpActionListener cpActionListener;

    private Timer overwriteTimer;
    private OverwriteRandomActionListener overwriteRandomActionListener;

    /**
     * Creates new form InstallerPanels
     */
    public InstallerPanels() {

        initComponents();

        originalPasswordEchoChar = personalPasswordField.getEchoChar();

        storageDeviceListModel = new DefaultListModel<>();
        transferStorageDeviceListModel = new DefaultListModel<>();

        setSpinnerColums(autoNumberStartSpinner, 2);
        setSpinnerColums(autoNumberIncrementSpinner, 2);

        resultsTableModel = new ResultsTableModel(resultsTable);
        resultsTable.setModel(resultsTableModel);
        TableColumn sizeColumn = resultsTable.getColumnModel().getColumn(
                ResultsTableModel.SIZE_COLUMN);
        sizeColumn.setCellRenderer(new SizeTableCellRenderer());
        resultsTable.setRowSorter(new ResultsTableRowSorter(
                resultsTableModel));

        // Because of its HTML content, the info and transfer labels tends to
        // resize therefore we fix its size here.
        Dimension preferredSize = transferLabel.getPreferredSize();
        transferLabel.setMinimumSize(preferredSize);
        transferLabel.setMaximumSize(preferredSize);

        // The preferred heigth of our device lists is much too small. Therefore
        // we hardcode it here.
        preferredSize = storageDeviceListScrollPane.getPreferredSize();
        preferredSize.height = 200;
        storageDeviceListScrollPane.setPreferredSize(preferredSize);

        // set columns based on current text
        exchangePartitionLabelTextField.setColumns(Math.max(5,
                exchangePartitionLabelTextField.getText().length()));
        autoNumberPatternTextField.setColumns(Math.max(2,
                autoNumberPatternTextField.getText().length()));
    }

    // post-constructor initialization
    public void init(DLCopySwingGUI dlCopySwingGUI,
            SystemSource runningSystemSource,
            DLCopySwingGUIPreferencesHandler preferencesHandler,
            ComboBoxModel<String> exchangePartitionFileSystemsModel) {

        this.dlCopySwingGUI = dlCopySwingGUI;
        this.runningSystemSource = runningSystemSource;

        dataPartitionModeComboBox.setModel(new DefaultComboBoxModel<>(
                DLCopy.DATA_PARTITION_MODES));

        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString, 0, 0);
        selectionCountLabel.setText(countString);

        String text = STRINGS.getString("Boot_Definition");
        String bootSize = LernstickFileTools.getDataVolumeString(
                DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA, 1);
        text = MessageFormat.format(text, bootSize);
        bootDefinitionLabel.setText(text);

        exchangePartitionFileSystemComboBox.setModel(
                exchangePartitionFileSystemsModel);

        transferStorageDeviceList.setModel(
                transferStorageDeviceListModel);
        transferStorageDeviceRenderer
                = new InstallTransferStorageDeviceRenderer();
        transferStorageDeviceList.setCellRenderer(
                transferStorageDeviceRenderer);

        storageDeviceList.setModel(storageDeviceListModel);
        storageDeviceRenderer = new InstallStorageDeviceRenderer(
                dlCopySwingGUI);
        storageDeviceList.setCellRenderer(storageDeviceRenderer);

        // the following block must be called after creating
        // installStorageDeviceRenderer! (otherwise we get an NPE)
        // -----------------------------
        String isoSource = isoSourceTextField.getText();
        if (!isoSource.isEmpty()) {
            setISOInstallationSourcePath(isoSource);
        }
        updateInstallSourceGUI();
        // -----------------------------

        AbstractDocument exchangePartitionDocument
                = (AbstractDocument) exchangePartitionLabelTextField.getDocument();
        exchangePartitionDocument.setDocumentFilter(new DocumentSizeFilter());
        exchangePartitionSizeTextField.getDocument().addDocumentListener(this);

        preferencesHandler.addPreference(
                new InstallationSourcePreferences(this));

        destinationSelectionPreferences
                = new InstallationDestinationSelectionPreferences(
                        copyExchangePartitionCheckBox,
                        exchangePartitionSizeSlider, copyDataPartitionCheckBox,
                        dataPartitionModeComboBox);
        explicitExchangeSize = destinationSelectionPreferences
                .getExplicitExchangeSize();
        preferencesHandler.addPreference(
                destinationSelectionPreferences);

        preferencesHandler.addPreference(
                new InstallationDestinationDetailsPreferences(
                        exchangePartitionFileSystemComboBox,
                        exchangePartitionLabelTextField,
                        autoNumberPatternTextField, autoNumberStartSpinner,
                        autoNumberIncrementSpinner, autoNumberMinDigitsSpinner,
                        personalPasswordCheckBox, secondaryPasswordCheckBox,
                        overwriteWithRandomDataCheckBox,
                        dataPartitionFileSystemComboBox, checkCopiesCheckBox));

        preferencesHandler.addPreference(
                new InstallationDestinationTransferPreferences(
                        transferExchangeCheckBox, transferHomeCheckBox,
                        transferNetworkCheckBox, transferPrinterCheckBox,
                        transferFirewallCheckBox));
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    public void installStorageDeviceListChanged(boolean instantInstallation) {

        // GUI changes
        dlCopySwingGUI.storageDeviceListChanged(
                storageDeviceListModel, selectionCardPanel,
                "noMediaPanel", "selectionTabbedPane",
                storageDeviceRenderer, storageDeviceList);
        updateInstallSelectionCountAndExchangeInfo();

        // run instant installation if needed
        if (instantInstallation) {
            storageDeviceList.setSelectionInterval(
                    0, storageDeviceListModel.size() - 1);
            try {
                dlCopySwingGUI.checkAndInstallSelection(false);
            } catch (IOException | DBusException ex) {
                LOGGER.log(Level.SEVERE,
                        "checking the selected usb flash drive failed", ex);
            }
        }
    }

    public void installTransferStorageDeviceListChanged() {
        if (transferStorageDeviceListModel.size() == 0) {
            return;
        }
        transferStorageDeviceRenderer.setMaxSize(
                DLCopySwingGUI.getMaxStorageDeviceSize(
                        transferStorageDeviceListModel));
        transferStorageDeviceList.repaint();
    }

    public boolean checkSelection(boolean interactive)
            throws IOException, DBusException {

        // check all selected target USB storage devices
        boolean hardDiskSelected = false;

        for (StorageDevice storageDevice : getSelectedDevices()) {

            if (storageDevice.getType() == StorageDevice.Type.HardDrive) {
                hardDiskSelected = true;
            }

            PartitionSizes partitionSizes = DLCopy.getInstallPartitionSizes(
                    dlCopySwingGUI.getSystemSource(), storageDevice,
                    exchangePartitionSizeSlider.getValue());

            if (!checkPersistence(partitionSizes)) {
                return false;
            }

            if (!checkExchange(partitionSizes)) {
                return false;
            }

            if (!checkTransfer(storageDevice, partitionSizes)) {
                return false;
            }
        }

        // exchange copy and exchange transfer are mutually exclusive
        if (!transferStorageDeviceList.isSelectionEmpty()
                && copyExchangePartitionCheckBox.isSelected()
                && transferExchangeCheckBox.isSelected()) {

            JOptionPane.showMessageDialog(dlCopySwingGUI,
                    STRINGS.getString("Error_Exchange_Copy_And_Transfer"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);

            return false;
        }

        // check encryption
        if (!checkNonEmptyPassword(
                personalPasswordCheckBox, personalPasswordField)) {
            return false;
        }
        if (!checkNonEmptyPassword(
                secondaryPasswordCheckBox, secondaryPasswordField)) {
            return false;
        }

        // show big fat warning dialog
        if (hardDiskSelected) {

            // show even bigger and fatter dialog when a hard drive was selected
            String expectedInput = STRINGS.getString("Harddisk_Warning_Input");
            String message = STRINGS.getString("Harddisk_Warning");
            message = MessageFormat.format(message, expectedInput);

            for (boolean correctAnswer = false; !correctAnswer;) {

                String input = JOptionPane.showInputDialog(
                        this, message, STRINGS.getString("Warning"),
                        JOptionPane.WARNING_MESSAGE);

                if (input == null) {
                    // dialog was cancelled or closed
                    return false;
                }

                correctAnswer = expectedInput.equals(input);

                if (!correctAnswer) {

                    JOptionPane.showMessageDialog(this,
                            STRINGS.getString("Warning_Mistyped_Text"),
                            STRINGS.getString("Warning"),
                            JOptionPane.WARNING_MESSAGE);
                }
            }

        } else if (interactive) {

            int result = JOptionPane.showConfirmDialog(this,
                    STRINGS.getString("Final_Installation_Warning"),
                    STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (result != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        return true;
    }

    public void saveExplicitExchangeSize() {
        destinationSelectionPreferences.saveExplicitExchangeSize(
                explicitExchangeSize);
    }

    public String getAutoNumberPattern() {
        return autoNumberPatternTextField.getText();
    }

    public int getAutoNumber() {
        return ((Number) autoNumberStartSpinner.getValue()).intValue();
    }

    public int getAutoIncrement() {
        return ((Number) autoNumberIncrementSpinner.getValue()).intValue();
    }

    public int getAutoMinDigits() {
        return ((Number) autoNumberMinDigitsSpinner.getValue()).intValue();
    }

    public List<StorageDevice> getSelectedDevices() {
        return storageDeviceList.getSelectedValuesList();
    }

    public String getExchangePartitionFileSystem() {
        return exchangePartitionFileSystemComboBox.getSelectedItem().toString();
    }

    public String getDataPartitionFileSystem() {
        return dataPartitionFileSystemComboBox.getSelectedItem().toString();
    }

    public DataPartitionMode getDataPartitionMode() {
        return DLCopySwingGUI.getDataPartitionMode(dataPartitionModeComboBox);
    }

    public boolean isCopyExchangeSelected() {
        return copyExchangePartitionCheckBox.isEnabled()
                && copyExchangePartitionCheckBox.isSelected();
    }

    public boolean isCopyDataSelected() {
        return copyDataPartitionCheckBox.isEnabled()
                && copyDataPartitionCheckBox.isSelected();
    }

    public String getExchangePartitionLabel() {
        return exchangePartitionLabelTextField.getText();
    }

    public int getExchangePartitionSize() {
        return exchangePartitionSizeSlider.getValue();
    }

    public boolean isPersonalEncryptionSelected() {
        return personalPasswordCheckBox.isSelected();
    }

    public boolean isSecondaryEncryptionSelected() {
        return secondaryPasswordCheckBox.isSelected();
    }

    public String getPersonalEncryptionPassword() {
        return String.valueOf(personalPasswordField.getPassword());
    }

    public String getSecondaryEncryptionPassword() {
        return String.valueOf(secondaryPasswordField.getPassword());
    }

    public boolean isOverwriteDataPartitionWithRandomDataSelected() {
        return overwriteWithRandomDataCheckBox.isSelected();
    }

    public StorageDevice getTransferDevice() {
        return transferStorageDeviceList.getSelectedValue();
    }

    public boolean isTransferExchangeSelected() {
        return transferExchangeCheckBox.isSelected();
    }

    public boolean isTransferHomeSelected() {
        return transferHomeCheckBox.isSelected();
    }

    public boolean isTransferNetworkSelected() {
        return transferNetworkCheckBox.isSelected();
    }

    public boolean isTransferPrinterSelected() {
        return transferPrinterCheckBox.isSelected();
    }

    public boolean isTransferFirewallSelected() {
        return transferFirewallCheckBox.isSelected();
    }

    public boolean isCheckCopiesSelected() {
        return checkCopiesCheckBox.isSelected();
    }

    public void showInfoPanel() {
        DLCopySwingGUI.showCard(this, "infoPanel");
    }

    public void showSelectionPanel() {
        DLCopySwingGUI.showCard(this, "selectionPanel");
    }

    public void showProgress() {
        DLCopySwingGUI.showCard(this, "tabbedPane");
    }

    public void showFileCopierPanel(FileCopier fileCopier) {
        fileCopierPanel.setFileCopier(fileCopier);
        SwingUtilities.invokeLater(() -> {
            copyLabel.setText(STRINGS.getString("Copying_Files"));
            DLCopySwingGUI.showCard(installCardPanel, "copyPanel");
        });
    }

    public void showInstallPersistencyCopy(
            Installer installer, String copyScript, String sourcePath) {

        cpActionListener = new CpActionListener(
                cpFilenameLabel, cpTimeLabel, sourcePath);

        Timer cpTimer = new Timer(1000, cpActionListener);
        cpTimer.setInitialDelay(0);
        cpTimer.start();

        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        SwingUtilities.invokeLater(() -> {
            cpFilenameLabel.setText(" ");
            cpPogressBar.setValue(0);
            cpTimeLabel.setText(timeFormat.format(new Date(0)));
            DLCopySwingGUI.showCard(installCardPanel, "cpPanel");
        });

        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        ProcessExecutor processExecutor = new ProcessExecutor(true);
        try {
            processExecutor.addPropertyChangeListener(installer);
            int exitValue = processExecutor.executeScript(
                    true, true, copyScript);
            processExecutor.removePropertyChangeListener(installer);
            cpTimer.stop();
            if (exitValue != 0) {
                String errorMessage = "Could not copy persistence layer!";
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    public void showOverwriteRandomProgressBar(long value, long maximum) {

        if (overwriteTimer == null) {
            SwingUtilities.invokeLater(() -> {
                DLCopySwingGUI.showCard(installCardPanel, "progressPanel");
            });
            overwriteRandomActionListener
                    = new OverwriteRandomActionListener(progressBar, maximum);
            overwriteTimer = new Timer(1000, overwriteRandomActionListener);
            overwriteTimer.setInitialDelay(0);
            overwriteTimer.start();
        }

        overwriteRandomActionListener.setDone(value);
    }

    public void showIndeterminateProgressBarText(final String text) {

        if (overwriteTimer != null) {
            overwriteTimer.stop();
            overwriteTimer = null;
        }

        SwingUtilities.invokeLater(() -> {
            DLCopySwingGUI.showCard(installCardPanel,
                    "indeterminateProgressPanel");
            indeterminateProgressBar.setString(STRINGS.getString(text));
        });
    }

    public void setInstallCopyLine(String line) {
        cpActionListener.setCurrentLine(line);
    }

    public DefaultListModel<StorageDevice> getDeviceListModel() {
        return storageDeviceListModel;
    }

    public DefaultListModel<StorageDevice> getTransferDeviceListModel() {
        return transferStorageDeviceListModel;
    }

    public JList<StorageDevice> getDeviceList() {
        return storageDeviceList;
    }

    public JList<StorageDevice> getTransferDeviceList() {
        return transferStorageDeviceList;
    }

    public boolean isShowHardDisksSelected() {
        return showHardDisksCheckBox.isSelected();
    }

    public void setExplicitExchangeSize(int explicitExchangeSize) {
        this.explicitExchangeSize = explicitExchangeSize;
    }

    public SystemSource getSystemSource() {
        return isoSourceRadioButton.isSelected()
                ? isoSystemSource
                : runningSystemSource;
    }

    public void setExchangePartitionFileSystem(String fileSystem) {
        exchangePartitionFileSystemComboBox.setSelectedItem(fileSystem);
    }

    public void setCopyDataPartition(Boolean copyDataPartition) {
        if (copyDataPartition != null) {
            copyDataPartitionCheckBox.setSelected(copyDataPartition);
        }
    }

    public void startedInstallationOnDevice(StorageDevice storageDevice,
            int batchCounter, List<StorageDeviceResult> resultsList) {

        // update label
        String pattern = STRINGS.getString("Install_Device_Info");
        String deviceInfo = MessageFormat.format(pattern,
                storageDevice.getVendor() + " " + storageDevice.getModel() + " "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1),
                storageDevice.getFullDevice(), batchCounter,
                storageDeviceList.getSelectedIndices().length);
        DLCopySwingGUI.setLabelTextonEDT(
                currentlyInstalledDeviceLabel, deviceInfo);

        resultsTableModel.setList(resultsList);
    }

    public void finishedInstallationOnDevice(int autoNumberStart,
            List<StorageDeviceResult> resultsList) {

        autoNumberStartSpinner.setValue(autoNumberStart);
        resultsTableModel.setList(resultsList);
    }

    public JSlider getExchangePartitionSizeSlider() {
        return exchangePartitionSizeSlider;
    }

    public ResultsTableModel getResultsTableModel() {
        return resultsTableModel;
    }

    /**
     * must be called whenever the selection count and exchange info for the
     * installer needs an update
     */
    public void updateInstallSelectionCountAndExchangeInfo() {

        // early return
        if ((dlCopySwingGUI == null)
                || (dlCopySwingGUI.state
                != DLCopySwingGUI.State.INSTALL_SELECTION)) {
            return;
        }

        // check all selected storage devices
        long minOverhead = Long.MAX_VALUE;
        boolean exchange = true;
        int[] selectedIndices = storageDeviceList.getSelectedIndices();
        int selectionCount = selectedIndices.length;

        if (selectionCount == 0) {

            minOverhead = 0;
            exchange = false;

        } else {

            SystemSource systemSource = dlCopySwingGUI.getSystemSource();

            if (systemSource == null) {
                LOGGER.warning("No valid system source selected!");

            } else {

                long enlargedSystemSize = DLCopy.getEnlargedSystemSize(
                        systemSource.getSystemSize());

                for (int i = 0; i < selectionCount; i++) {

                    StorageDevice device
                            = storageDeviceListModel.get(selectedIndices[i]);

                    long overhead = device.getSize()
                            - (DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)
                            - enlargedSystemSize;

                    minOverhead = Math.min(minOverhead, overhead);

                    PartitionState partitionState = DLCopy.getPartitionState(
                            device.getSize(),
                            (DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)
                            + enlargedSystemSize);

                    if (partitionState != PartitionState.EXCHANGE) {
                        exchange = false;
                        break; // for
                    }
                }
            }
        }

        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, storageDeviceListModel.size());
        selectionCountLabel.setText(countString);

        exchangePartitionSizeLabel.setEnabled(exchange);
        exchangePartitionSizeSlider.setEnabled(exchange);
        exchangePartitionSizeTextField.setEnabled(exchange);
        exchangePartitionSizeUnitLabel.setEnabled(exchange);

        listSelectionTriggeredSliderChange = true;
        if (exchange) {
            int overheadMega = (int) (minOverhead / DLCopy.MEGA);
            exchangePartitionSizeSlider.setMaximum(overheadMega);
            exchangePartitionSizeSlider.setValue(
                    Math.min(explicitExchangeSize, overheadMega));
            setMajorTickSpacing(exchangePartitionSizeSlider, overheadMega);
            exchangePartitionSizeTextField.setText(
                    String.valueOf(exchangePartitionSizeSlider.getValue()));
        } else {
            exchangePartitionSizeSlider.setMaximum(0);
            exchangePartitionSizeSlider.setValue(0);
            // remove text
            exchangePartitionSizeTextField.setText(null);
        }
        listSelectionTriggeredSliderChange = false;

        exchangePartitionLabel.setEnabled(exchange);
        exchangePartitionLabelTextField.setEnabled(exchange);
        autoNumberPatternLabel.setEnabled(exchange);
        autoNumberPatternTextField.setEnabled(exchange);
        autoNumberStartLabel.setEnabled(exchange);
        autoNumberStartSpinner.setEnabled(exchange);
        autoNumberIncrementLabel.setEnabled(exchange);
        autoNumberIncrementSpinner.setEnabled(exchange);
        autoNumberMinDigitsLabel.setEnabled(exchange);
        autoNumberMinDigitsSpinner.setEnabled(exchange);
        exchangePartitionFileSystemLabel.setEnabled(exchange);
        exchangePartitionFileSystemComboBox.setEnabled(exchange);

        SystemSource systemSource = dlCopySwingGUI.getSystemSource();
        copyExchangePartitionCheckBox.setEnabled(exchange
                && (systemSource != null)
                && systemSource.hasExchangePartition());

        // enable nextButton?
        updateNextButton();
    }

    public void unmountIsoSystemSource() {
        if (isoSystemSource != null) {
            isoSystemSource.unmountTmpPartitions();
        }
    }

    public void setInstallationSource(boolean isoSelected, String isoSource) {

        // This automatically sets the system source to an ISO system source...
        if (!(isoSource == null || isoSource.isEmpty())) {
            setISOInstallationSourcePath(isoSource);
        }
        // ... but this will be corrected in the following lines.
        
        if (isoSelected) {
            isoSourceRadioButton.setSelected(true);
        } else {
            runningSystemSourceRadioButton.setSelected(true);
        }

        updateInstallSourceGUI();
    }

    public String getIsoSource() {
        return isoSourceTextField.getText();
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

        installSourceButtonGroup = new javax.swing.ButtonGroup();
        encryptionButtonGroup = new javax.swing.ButtonGroup();
        infoPanel = new javax.swing.JPanel();
        infoLabel = new javax.swing.JLabel();
        selectionPanel = new javax.swing.JPanel();
        sourcePanel = new javax.swing.JPanel();
        runningSystemSourceRadioButton = new javax.swing.JRadioButton();
        isoSourceRadioButton = new javax.swing.JRadioButton();
        isoSourceTextField = new javax.swing.JTextField();
        isoSourceFileChooserButton = new javax.swing.JButton();
        targetCardPanel = new javax.swing.JPanel();
        targetPanel = new javax.swing.JPanel();
        selectionHeaderLabel = new javax.swing.JLabel();
        showHardDisksCheckBox = new javax.swing.JCheckBox();
        selectionCardPanel = new javax.swing.JPanel();
        selectionTabbedPane = new javax.swing.JTabbedPane();
        basicsPanel = new javax.swing.JPanel();
        selectionCountLabel = new javax.swing.JLabel();
        storageDeviceListScrollPane = new javax.swing.JScrollPane();
        storageDeviceList = new javax.swing.JList<>();
        exchangeDefinitionLabel = new javax.swing.JLabel();
        dataDefinitionLabel = new javax.swing.JLabel();
        bootDefinitionLabel = new javax.swing.JLabel();
        systemDefinitionLabel = new javax.swing.JLabel();
        basicExchangePartitionPanel = new javax.swing.JPanel();
        copyExchangePartitionCheckBox = new javax.swing.JCheckBox();
        exchangePartitionSeparator = new javax.swing.JSeparator();
        exchangePartitionSizeLabel = new javax.swing.JLabel();
        exchangePartitionSizeSlider = new javax.swing.JSlider();
        exchangePartitionSizeTextField = new javax.swing.JTextField();
        exchangePartitionSizeUnitLabel = new javax.swing.JLabel();
        basicDataPartitionPanel = new javax.swing.JPanel();
        copyDataPartitionCheckBox = new javax.swing.JCheckBox();
        dataPartitionSeparator = new javax.swing.JSeparator();
        dataPartitionModeLabel = new javax.swing.JLabel();
        dataPartitionModeComboBox = new javax.swing.JComboBox<>();
        detailsPanel = new javax.swing.JPanel();
        exchangePartitionDetailsPanel = new javax.swing.JPanel();
        exchangePartitionFileSystemPanel = new javax.swing.JPanel();
        exchangePartitionFileSystemLabel = new javax.swing.JLabel();
        exchangePartitionFileSystemComboBox = new javax.swing.JComboBox<>();
        exchangePartitionLabelPanel = new javax.swing.JPanel();
        exchangePartitionLabel = new javax.swing.JLabel();
        exchangePartitionLabelTextField = new javax.swing.JTextField();
        autoNumberPanel = new javax.swing.JPanel();
        autoNumberPatternLabel = new javax.swing.JLabel();
        autoNumberPatternTextField = new javax.swing.JTextField();
        autoNumberStartLabel = new javax.swing.JLabel();
        autoNumberStartSpinner = new javax.swing.JSpinner();
        autoNumberIncrementLabel = new javax.swing.JLabel();
        autoNumberIncrementSpinner = new javax.swing.JSpinner();
        autoNumberMinDigitsLabel = new javax.swing.JLabel();
        autoNumberMinDigitsSpinner = new javax.swing.JSpinner();
        dataPartitionDetailsPanel = new javax.swing.JPanel();
        encryptionPanel = new javax.swing.JPanel();
        personalPasswordCheckBox = new javax.swing.JCheckBox();
        personalPasswordField = new javax.swing.JPasswordField();
        personalPasswordToggleButton = new javax.swing.JToggleButton();
        secondaryPasswordCheckBox = new javax.swing.JCheckBox();
        secondaryPasswordField = new javax.swing.JPasswordField();
        secondaryPasswordToggleButton = new javax.swing.JToggleButton();
        overwriteWithRandomDataCheckBox = new javax.swing.JCheckBox();
        fileSystemPanel = new javax.swing.JPanel();
        dataPartitionFileSystemComboBox = new javax.swing.JComboBox<>();
        checkCopiesCheckBox = new javax.swing.JCheckBox();
        transferPanel = new javax.swing.JPanel();
        transferLabel = new javax.swing.JLabel();
        transferCheckboxPanel = new javax.swing.JPanel();
        transferExchangeCheckBox = new javax.swing.JCheckBox();
        transferHomeCheckBox = new javax.swing.JCheckBox();
        transferNetworkCheckBox = new javax.swing.JCheckBox();
        transferPrinterCheckBox = new javax.swing.JCheckBox();
        transferFirewallCheckBox = new javax.swing.JCheckBox();
        transferScrollPane = new javax.swing.JScrollPane();
        transferStorageDeviceList = new javax.swing.JList<>();
        noMediaPanel = new javax.swing.JPanel();
        noMediaLabel = new javax.swing.JLabel();
        noSourcePanel = new javax.swing.JPanel();
        noSouceLabel = new javax.swing.JLabel();
        tabbedPane = new javax.swing.JTabbedPane();
        currentPanel = new javax.swing.JPanel();
        currentlyInstalledDeviceLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        installCardPanel = new javax.swing.JPanel();
        progressPanel = new javax.swing.JPanel();
        progressBar = new javax.swing.JProgressBar();
        indeterminateProgressPanel = new javax.swing.JPanel();
        indeterminateProgressBar = new javax.swing.JProgressBar();
        copyPanel = new javax.swing.JPanel();
        copyLabel = new javax.swing.JLabel();
        fileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
        rsyncPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        rsyncPogressBar = new javax.swing.JProgressBar();
        rsyncTimeLabel = new javax.swing.JLabel();
        cpPanel = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        cpPogressBar = new javax.swing.JProgressBar();
        cpFilenameLabel = new JSqueezedLabel();
        cpTimeLabel = new javax.swing.JLabel();
        reportPanel = new javax.swing.JPanel();
        resultsScrollPane = new javax.swing.JScrollPane();
        resultsTable = new javax.swing.JTable();

        setLayout(new java.awt.CardLayout());

        infoPanel.setLayout(new java.awt.GridBagLayout());

        infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dvd2usb.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        infoLabel.setText(bundle.getString("DLCopySwingGUI.infoLabel.text")); // NOI18N
        infoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        infoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        infoPanel.add(infoLabel, gridBagConstraints);

        add(infoPanel, "infoPanel");

        selectionPanel.setLayout(new java.awt.GridBagLayout());

        sourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.installSourcePanel.border.title"))); // NOI18N
        sourcePanel.setLayout(new java.awt.GridBagLayout());

        installSourceButtonGroup.add(runningSystemSourceRadioButton);
        runningSystemSourceRadioButton.setSelected(true);
        runningSystemSourceRadioButton.setText(bundle.getString("DLCopySwingGUI.runningSystemSourceRadioButton.text")); // NOI18N
        runningSystemSourceRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                runningSystemSourceRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        sourcePanel.add(runningSystemSourceRadioButton, gridBagConstraints);

        installSourceButtonGroup.add(isoSourceRadioButton);
        isoSourceRadioButton.setText(bundle.getString("DLCopySwingGUI.isoSourceRadioButton.text")); // NOI18N
        isoSourceRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                isoSourceRadioButtonItemStateChanged(evt);
            }
        });
        isoSourceRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isoSourceRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        sourcePanel.add(isoSourceRadioButton, gridBagConstraints);

        isoSourceTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        sourcePanel.add(isoSourceTextField, gridBagConstraints);

        isoSourceFileChooserButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-open-folder.png"))); // NOI18N
        isoSourceFileChooserButton.setEnabled(false);
        isoSourceFileChooserButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        isoSourceFileChooserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isoSourceFileChooserButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        sourcePanel.add(isoSourceFileChooserButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        selectionPanel.add(sourcePanel, gridBagConstraints);

        targetCardPanel.setName("targetCardPanel"); // NOI18N
        targetCardPanel.setLayout(new java.awt.CardLayout());

        targetPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.installTargetPanel.border.title"))); // NOI18N
        targetPanel.setLayout(new java.awt.GridBagLayout());

        selectionHeaderLabel.setFont(selectionHeaderLabel.getFont().deriveFont(selectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        selectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        selectionHeaderLabel.setText(bundle.getString("Select_Install_Target_Storage_Media")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        targetPanel.add(selectionHeaderLabel, gridBagConstraints);

        showHardDisksCheckBox.setFont(showHardDisksCheckBox.getFont().deriveFont(showHardDisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, showHardDisksCheckBox.getFont().getSize()-1));
        showHardDisksCheckBox.setText(bundle.getString("DLCopySwingGUI.installShowHardDisksCheckBox.text")); // NOI18N
        showHardDisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                showHardDisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        targetPanel.add(showHardDisksCheckBox, gridBagConstraints);

        selectionCardPanel.setName("selectionCardPanel"); // NOI18N
        selectionCardPanel.setLayout(new java.awt.CardLayout());

        basicsPanel.setLayout(new java.awt.GridBagLayout());

        selectionCountLabel.setText(bundle.getString("Selection_Count")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        basicsPanel.add(selectionCountLabel, gridBagConstraints);

        storageDeviceList.setName("storageDeviceList"); // NOI18N
        storageDeviceList.setVisibleRowCount(3);
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
        basicsPanel.add(storageDeviceListScrollPane, gridBagConstraints);

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("ExchangePartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        basicsPanel.add(exchangeDefinitionLabel, gridBagConstraints);

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DataPartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        basicsPanel.add(dataDefinitionLabel, gridBagConstraints);

        bootDefinitionLabel.setFont(bootDefinitionLabel.getFont().deriveFont(bootDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, bootDefinitionLabel.getFont().getSize()-1));
        bootDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dark_blue_box.png"))); // NOI18N
        bootDefinitionLabel.setText(bundle.getString("Boot_Definition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        basicsPanel.add(bootDefinitionLabel, gridBagConstraints);

        systemDefinitionLabel.setFont(systemDefinitionLabel.getFont().deriveFont(systemDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, systemDefinitionLabel.getFont().getSize()-1));
        systemDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        systemDefinitionLabel.setText(bundle.getString("System_Definition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 0);
        basicsPanel.add(systemDefinitionLabel, gridBagConstraints);

        basicExchangePartitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Exchange_Partition"))); // NOI18N
        basicExchangePartitionPanel.setLayout(new java.awt.GridBagLayout());

        copyExchangePartitionCheckBox.setText(bundle.getString("Copy")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        basicExchangePartitionPanel.add(copyExchangePartitionCheckBox, gridBagConstraints);

        exchangePartitionSeparator.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 3, 0);
        basicExchangePartitionPanel.add(exchangePartitionSeparator, gridBagConstraints);

        exchangePartitionSizeLabel.setText(bundle.getString("Size")); // NOI18N
        exchangePartitionSizeLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        basicExchangePartitionPanel.add(exchangePartitionSizeLabel, gridBagConstraints);

        exchangePartitionSizeSlider.setMaximum(0);
        exchangePartitionSizeSlider.setPaintLabels(true);
        exchangePartitionSizeSlider.setPaintTicks(true);
        exchangePartitionSizeSlider.setEnabled(false);
        exchangePartitionSizeSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                exchangePartitionSizeSliderStateChanged(evt);
            }
        });
        exchangePartitionSizeSlider.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                exchangePartitionSizeSliderComponentResized(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        basicExchangePartitionPanel.add(exchangePartitionSizeSlider, gridBagConstraints);

        exchangePartitionSizeTextField.setColumns(7);
        exchangePartitionSizeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        exchangePartitionSizeTextField.setEnabled(false);
        exchangePartitionSizeTextField.setName("exchangePartitionSizeTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        basicExchangePartitionPanel.add(exchangePartitionSizeTextField, gridBagConstraints);

        exchangePartitionSizeUnitLabel.setText(bundle.getString("DLCopySwingGUI.exchangePartitionSizeUnitLabel.text")); // NOI18N
        exchangePartitionSizeUnitLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        basicExchangePartitionPanel.add(exchangePartitionSizeUnitLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        basicsPanel.add(basicExchangePartitionPanel, gridBagConstraints);

        basicDataPartitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Data_Partition"))); // NOI18N
        basicDataPartitionPanel.setLayout(new java.awt.GridBagLayout());

        copyDataPartitionCheckBox.setText(bundle.getString("Copy")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 0);
        basicDataPartitionPanel.add(copyDataPartitionCheckBox, gridBagConstraints);

        dataPartitionSeparator.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 3, 0);
        basicDataPartitionPanel.add(dataPartitionSeparator, gridBagConstraints);

        dataPartitionModeLabel.setText(bundle.getString("DLCopySwingGUI.dataPartitionModeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
        basicDataPartitionPanel.add(dataPartitionModeLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 10);
        basicDataPartitionPanel.add(dataPartitionModeComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        basicsPanel.add(basicDataPartitionPanel, gridBagConstraints);

        selectionTabbedPane.addTab(bundle.getString("Selection"), basicsPanel); // NOI18N

        detailsPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Exchange_Partition"))); // NOI18N
        exchangePartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionFileSystemPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionFileSystemLabel.setText(bundle.getString("DLCopySwingGUI.exchangePartitionFileSystemLabel.text")); // NOI18N
        exchangePartitionFileSystemLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        exchangePartitionFileSystemPanel.add(exchangePartitionFileSystemLabel, gridBagConstraints);

        exchangePartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "exFAT", "FAT32", "NTFS" }));
        exchangePartitionFileSystemComboBox.setToolTipText(bundle.getString("ExchangePartitionFileSystemComboBoxToolTipText")); // NOI18N
        exchangePartitionFileSystemComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        exchangePartitionFileSystemPanel.add(exchangePartitionFileSystemComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        exchangePartitionDetailsPanel.add(exchangePartitionFileSystemPanel, gridBagConstraints);

        exchangePartitionLabelPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        exchangePartitionLabel.setText(bundle.getString("Label")); // NOI18N
        exchangePartitionLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        exchangePartitionLabelPanel.add(exchangePartitionLabel, gridBagConstraints);

        exchangePartitionLabelTextField.setText(bundle.getString("Exchange")); // NOI18N
        exchangePartitionLabelTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        exchangePartitionLabelPanel.add(exchangePartitionLabelTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(17, 5, 0, 0);
        exchangePartitionDetailsPanel.add(exchangePartitionLabelPanel, gridBagConstraints);

        autoNumberPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.autoNumberPanel.border.title"))); // NOI18N
        autoNumberPanel.setLayout(new java.awt.GridBagLayout());

        autoNumberPatternLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberPatternLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberPatternLabel.text")); // NOI18N
        autoNumberPatternLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoNumberPanel.add(autoNumberPatternLabel, gridBagConstraints);

        autoNumberPatternTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
        autoNumberPanel.add(autoNumberPatternTextField, gridBagConstraints);

        autoNumberStartLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberStartLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberStartLabel.text")); // NOI18N
        autoNumberStartLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        autoNumberPanel.add(autoNumberStartLabel, gridBagConstraints);

        autoNumberStartSpinner.setModel(new javax.swing.SpinnerNumberModel(1, null, null, 1));
        autoNumberStartSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        autoNumberPanel.add(autoNumberStartSpinner, gridBagConstraints);

        autoNumberIncrementLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberIncrementLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberIncrementLabel.text")); // NOI18N
        autoNumberIncrementLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        autoNumberPanel.add(autoNumberIncrementLabel, gridBagConstraints);

        autoNumberIncrementSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        autoNumberIncrementSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        autoNumberPanel.add(autoNumberIncrementSpinner, gridBagConstraints);

        autoNumberMinDigitsLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberMinDigitsLabel.text")); // NOI18N
        autoNumberMinDigitsLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
        autoNumberPanel.add(autoNumberMinDigitsLabel, gridBagConstraints);

        autoNumberMinDigitsSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        autoNumberMinDigitsSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        autoNumberPanel.add(autoNumberMinDigitsSpinner, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.2;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 5);
        exchangePartitionDetailsPanel.add(autoNumberPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        detailsPanel.add(exchangePartitionDetailsPanel, gridBagConstraints);

        dataPartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Data_Partition"))); // NOI18N
        dataPartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        encryptionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("InstallerPanels.encryptionPanel.border.title"))); // NOI18N
        encryptionPanel.setLayout(new java.awt.GridBagLayout());

        personalPasswordCheckBox.setText(bundle.getString("InstallerPanels.personalPasswordCheckBox.text")); // NOI18N
        personalPasswordCheckBox.setToolTipText(bundle.getString("Encryption_ToolTipText")); // NOI18N
        personalPasswordCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                personalPasswordCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        encryptionPanel.add(personalPasswordCheckBox, gridBagConstraints);

        personalPasswordField.setToolTipText(bundle.getString("Encryption_ToolTipText")); // NOI18N
        personalPasswordField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        encryptionPanel.add(personalPasswordField, gridBagConstraints);

        personalPasswordToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/password-show-on.png"))); // NOI18N
        personalPasswordToggleButton.setToolTipText(bundle.getString("Encryption_ToolTipText")); // NOI18N
        personalPasswordToggleButton.setEnabled(false);
        personalPasswordToggleButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        personalPasswordToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                personalPasswordToggleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        encryptionPanel.add(personalPasswordToggleButton, gridBagConstraints);

        secondaryPasswordCheckBox.setText(bundle.getString("InstallerPanels.secondaryPasswordCheckBox.text")); // NOI18N
        secondaryPasswordCheckBox.setToolTipText(bundle.getString("SecondaryPassword_ToolTipText")); // NOI18N
        secondaryPasswordCheckBox.setEnabled(false);
        secondaryPasswordCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                secondaryPasswordCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        encryptionPanel.add(secondaryPasswordCheckBox, gridBagConstraints);

        secondaryPasswordField.setToolTipText(bundle.getString("SecondaryPassword_ToolTipText")); // NOI18N
        secondaryPasswordField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        encryptionPanel.add(secondaryPasswordField, gridBagConstraints);

        secondaryPasswordToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/password-show-on.png"))); // NOI18N
        secondaryPasswordToggleButton.setToolTipText(bundle.getString("SecondaryPassword_ToolTipText")); // NOI18N
        secondaryPasswordToggleButton.setEnabled(false);
        secondaryPasswordToggleButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        secondaryPasswordToggleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                secondaryPasswordToggleButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        encryptionPanel.add(secondaryPasswordToggleButton, gridBagConstraints);

        overwriteWithRandomDataCheckBox.setText(bundle.getString("InstallerPanels.overwriteWithRandomDataCheckBox.text")); // NOI18N
        overwriteWithRandomDataCheckBox.setToolTipText(bundle.getString("RandomFill_ToolTipText")); // NOI18N
        overwriteWithRandomDataCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        encryptionPanel.add(overwriteWithRandomDataCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        dataPartitionDetailsPanel.add(encryptionPanel, gridBagConstraints);

        fileSystemPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("FileSystem"))); // NOI18N
        fileSystemPanel.setLayout(new java.awt.GridBagLayout());

        dataPartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "ext2", "ext3", "ext4" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        fileSystemPanel.add(dataPartitionFileSystemComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 5);
        dataPartitionDetailsPanel.add(fileSystemPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        detailsPanel.add(dataPartitionDetailsPanel, gridBagConstraints);

        checkCopiesCheckBox.setText(bundle.getString("DLCopySwingGUI.checkCopiesCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        detailsPanel.add(checkCopiesCheckBox, gridBagConstraints);

        selectionTabbedPane.addTab(bundle.getString("Details"), detailsPanel); // NOI18N

        transferPanel.setLayout(new java.awt.GridBagLayout());

        transferLabel.setText(bundle.getString("DLCopySwingGUI.transferLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        transferPanel.add(transferLabel, gridBagConstraints);

        transferCheckboxPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.transferCheckboxPanel.border.title"))); // NOI18N
        transferCheckboxPanel.setLayout(new java.awt.GridBagLayout());

        transferExchangeCheckBox.setText(bundle.getString("Exchange_Partition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        transferCheckboxPanel.add(transferExchangeCheckBox, gridBagConstraints);

        transferHomeCheckBox.setSelected(true);
        transferHomeCheckBox.setText(bundle.getString("DLCopySwingGUI.transferHomeCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        transferCheckboxPanel.add(transferHomeCheckBox, gridBagConstraints);

        transferNetworkCheckBox.setText(bundle.getString("DLCopySwingGUI.transferNetworkCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        transferCheckboxPanel.add(transferNetworkCheckBox, gridBagConstraints);

        transferPrinterCheckBox.setText(bundle.getString("DLCopySwingGUI.transferPrinterCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        transferCheckboxPanel.add(transferPrinterCheckBox, gridBagConstraints);

        transferFirewallCheckBox.setText(bundle.getString("DLCopySwingGUI.transferFirewallCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        transferCheckboxPanel.add(transferFirewallCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        transferPanel.add(transferCheckboxPanel, gridBagConstraints);

        transferStorageDeviceList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        transferScrollPane.setViewportView(transferStorageDeviceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        transferPanel.add(transferScrollPane, gridBagConstraints);

        selectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.installTransferPanel.TabConstraints.tabTitle"), transferPanel); // NOI18N

        selectionCardPanel.add(selectionTabbedPane, "selectionTabbedPane");

        noMediaPanel.setLayout(new java.awt.GridBagLayout());

        noMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        noMediaLabel.setText(bundle.getString("Insert_Media")); // NOI18N
        noMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        noMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        noMediaPanel.add(noMediaLabel, new java.awt.GridBagConstraints());

        selectionCardPanel.add(noMediaPanel, "noMediaPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        targetPanel.add(selectionCardPanel, gridBagConstraints);

        targetCardPanel.add(targetPanel, "targetPanel");

        noSourcePanel.setLayout(new java.awt.GridBagLayout());

        noSouceLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        noSouceLabel.setText(bundle.getString("DLCopySwingGUI.installNoSouceLabel.text")); // NOI18N
        noSouceLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        noSouceLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        noSourcePanel.add(noSouceLabel, new java.awt.GridBagConstraints());

        targetCardPanel.add(noSourcePanel, "noSourcePanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        selectionPanel.add(targetCardPanel, gridBagConstraints);

        add(selectionPanel, "selectionPanel");

        currentPanel.setLayout(new java.awt.GridBagLayout());

        currentlyInstalledDeviceLabel.setText(bundle.getString("Install_Device_Info")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        currentPanel.add(currentlyInstalledDeviceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        currentPanel.add(jSeparator3, gridBagConstraints);

        installCardPanel.setName("installCardPanel"); // NOI18N
        installCardPanel.setLayout(new java.awt.CardLayout());

        progressPanel.setLayout(new java.awt.GridBagLayout());

        progressBar.setPreferredSize(new java.awt.Dimension(300, 25));
        progressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        progressPanel.add(progressBar, gridBagConstraints);

        installCardPanel.add(progressPanel, "progressPanel");

        indeterminateProgressPanel.setLayout(new java.awt.GridBagLayout());

        indeterminateProgressBar.setIndeterminate(true);
        indeterminateProgressBar.setPreferredSize(new java.awt.Dimension(300, 25));
        indeterminateProgressBar.setString(bundle.getString("DLCopySwingGUI.installIndeterminateProgressBar.string")); // NOI18N
        indeterminateProgressBar.setStringPainted(true);
        indeterminateProgressPanel.add(indeterminateProgressBar, new java.awt.GridBagConstraints());

        installCardPanel.add(indeterminateProgressPanel, "indeterminateProgressPanel");

        copyPanel.setLayout(new java.awt.GridBagLayout());

        copyLabel.setText(bundle.getString("DLCopySwingGUI.installCopyLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        copyPanel.add(copyLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        copyPanel.add(fileCopierPanel, gridBagConstraints);

        installCardPanel.add(copyPanel, "copyPanel");

        rsyncPanel.setLayout(new java.awt.GridBagLayout());

        jLabel3.setText(bundle.getString("DLCopySwingGUI.jLabel3.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        rsyncPanel.add(jLabel3, gridBagConstraints);

        rsyncPogressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        rsyncPogressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        rsyncPanel.add(rsyncPogressBar, gridBagConstraints);

        rsyncTimeLabel.setText(bundle.getString("DLCopySwingGUI.rsyncTimeLabel.text")); // NOI18N
        rsyncTimeLabel.setName("upgradeBackupTimeLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        rsyncPanel.add(rsyncTimeLabel, gridBagConstraints);

        installCardPanel.add(rsyncPanel, "rsyncPanel");

        cpPanel.setLayout(new java.awt.GridBagLayout());

        jLabel4.setText(bundle.getString("DLCopySwingGUI.jLabel4.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        cpPanel.add(jLabel4, gridBagConstraints);

        cpPogressBar.setIndeterminate(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        cpPanel.add(cpPogressBar, gridBagConstraints);

        cpFilenameLabel.setFont(cpFilenameLabel.getFont().deriveFont(cpFilenameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, cpFilenameLabel.getFont().getSize()-1));
        cpFilenameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        cpFilenameLabel.setText(bundle.getString("DLCopySwingGUI.cpFilenameLabel.text")); // NOI18N
        cpFilenameLabel.setName("cpFilenameLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        cpPanel.add(cpFilenameLabel, gridBagConstraints);

        cpTimeLabel.setText(bundle.getString("DLCopySwingGUI.cpTimeLabel.text")); // NOI18N
        cpTimeLabel.setName("upgradeBackupTimeLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        cpPanel.add(cpTimeLabel, gridBagConstraints);

        installCardPanel.add(cpPanel, "cpPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        currentPanel.add(installCardPanel, gridBagConstraints);

        tabbedPane.addTab(bundle.getString("DLCopySwingGUI.installCurrentPanel.TabConstraints.tabTitle"), currentPanel); // NOI18N

        reportPanel.setLayout(new java.awt.GridBagLayout());

        resultsTable.setAutoCreateRowSorter(true);
        resultsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        resultsScrollPane.setViewportView(resultsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        reportPanel.add(resultsScrollPane, gridBagConstraints);

        tabbedPane.addTab(bundle.getString("Installation_Report"), reportPanel); // NOI18N

        add(tabbedPane, "tabbedPane");
        tabbedPane.getAccessibleContext().setAccessibleName(bundle.getString("DLCopySwingGUI.installCurrentPanel.TabConstraints.tabTitle")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void runningSystemSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runningSystemSourceRadioButtonActionPerformed
        updateInstallSourceGUI();
    }//GEN-LAST:event_runningSystemSourceRadioButtonActionPerformed

    private void isoSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isoSourceRadioButtonActionPerformed
        updateInstallSourceGUI();
    }//GEN-LAST:event_isoSourceRadioButtonActionPerformed

    private void isoSourceFileChooserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isoSourceFileChooserButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        String isoSource = isoSourceTextField.getText();
        if ((isoSource == null) || isoSource.isEmpty()) {
            fileChooser.setCurrentDirectory(new File("/"));
        } else {
            fileChooser.setSelectedFile(new File(isoSource));
        }
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setISOInstallationSourcePath(
                    fileChooser.getSelectedFile().getPath());
        }
    }//GEN-LAST:event_isoSourceFileChooserButtonActionPerformed

    private void showHardDisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_showHardDisksCheckBoxItemStateChanged
        new InstallStorageDeviceListUpdater(dlCopySwingGUI,
                storageDeviceList, storageDeviceListModel,
                showHardDisksCheckBox.isSelected(),
                runningSystemSource.getDeviceName()).execute();
    }//GEN-LAST:event_showHardDisksCheckBoxItemStateChanged

    private void storageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_storageDeviceListValueChanged
        updateInstallSelectionCountAndExchangeInfo();
    }//GEN-LAST:event_storageDeviceListValueChanged

    private void exchangePartitionSizeSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exchangePartitionSizeSliderStateChanged

        if (!textFieldTriggeredSliderChange) {
            // update value text field
            exchangePartitionSizeTextField.setText(
                    String.valueOf(exchangePartitionSizeSlider.getValue()));
        }

        if (!listSelectionTriggeredSliderChange) {
            explicitExchangeSize = exchangePartitionSizeSlider.getValue();
        }

        // repaint partition list
        storageDeviceList.repaint();
    }//GEN-LAST:event_exchangePartitionSizeSliderStateChanged

    private void exchangePartitionSizeSliderComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_exchangePartitionSizeSliderComponentResized
        updateInstallSelectionCountAndExchangeInfo();
    }//GEN-LAST:event_exchangePartitionSizeSliderComponentResized

    private void personalPasswordToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_personalPasswordToggleButtonActionPerformed
        handlePasswordToggleButton(
                personalPasswordToggleButton, personalPasswordField);
    }//GEN-LAST:event_personalPasswordToggleButtonActionPerformed

    private void personalPasswordCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_personalPasswordCheckBoxItemStateChanged
        boolean enabled = personalPasswordCheckBox.isSelected();
        personalPasswordField.setEnabled(enabled);
        personalPasswordToggleButton.setEnabled(enabled);
        secondaryPasswordCheckBox.setEnabled(enabled);
        boolean bothEnabled = enabled && secondaryPasswordCheckBox.isSelected();
        secondaryPasswordField.setEnabled(bothEnabled);
        secondaryPasswordToggleButton.setEnabled(bothEnabled);
        overwriteWithRandomDataCheckBox.setEnabled(enabled);
    }//GEN-LAST:event_personalPasswordCheckBoxItemStateChanged

    private void secondaryPasswordToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_secondaryPasswordToggleButtonActionPerformed
        handlePasswordToggleButton(
                secondaryPasswordToggleButton, secondaryPasswordField);
    }//GEN-LAST:event_secondaryPasswordToggleButtonActionPerformed

    private void secondaryPasswordCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_secondaryPasswordCheckBoxItemStateChanged
        boolean enabled = secondaryPasswordCheckBox.isSelected();
        secondaryPasswordField.setEnabled(enabled);
        secondaryPasswordToggleButton.setEnabled(enabled);
    }//GEN-LAST:event_secondaryPasswordCheckBoxItemStateChanged

    private void isoSourceRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_isoSourceRadioButtonItemStateChanged
        updateInstallSourceGUI();
    }//GEN-LAST:event_isoSourceRadioButtonItemStateChanged

    private boolean checkNonEmptyPassword(
            JCheckBox passwordCheckBox, JPasswordField passwordField) {

        if (passwordCheckBox.isSelected()) {

            char[] password = passwordField.getPassword();

            if (password.length == 0) {

                passwordField.selectAll();
                passwordField.requestFocusInWindow();
                selectionTabbedPane.setSelectedComponent(detailsPanel);

                JOptionPane.showMessageDialog(dlCopySwingGUI,
                        STRINGS.getString("Error_Encryption_No_Password"),
                        STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);

                return false;
            }
        }

        return true;
    }

    private void handlePasswordToggleButton(
            JToggleButton toggleButton, JPasswordField passwordField) {

        if (toggleButton.isSelected()) {
            passwordField.setEchoChar((char) 0);
            toggleButton.setIcon(new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/16x16/password-show-off.png")));
        } else {
            passwordField.setEchoChar(originalPasswordEchoChar);
            toggleButton.setIcon(new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/16x16/password-show-on.png")));
        }
    }

    private void syncWidth(JComponent component1, JComponent component2) {
        int preferredWidth = Math.max(component1.getPreferredSize().width,
                component2.getPreferredSize().width);
        setPreferredWidth(preferredWidth, component1, component2);
    }

    private void setPreferredWidth(
            int preferredWidth, JComponent... components) {

        for (JComponent component : components) {
            Dimension preferredSize = component.getPreferredSize();
            preferredSize.width = preferredWidth;
            component.setPreferredSize(preferredSize);
        }
    }

    private void updateInstallationSource() {
        if (isoSourceRadioButton.isSelected()
                && isoSourceTextField.getText().isEmpty()) {
            DLCopySwingGUI.showCard(targetCardPanel, "noSourcePanel");
        } else {
            DLCopySwingGUI.showCard(targetCardPanel, "targetPanel");
        }
        updateNextButton();
    }

    private void updateInstallSourceGUI() {
        boolean isoSourceSelected = isoSourceRadioButton.isSelected();
        isoSourceTextField.setEnabled(isoSourceSelected);
        isoSourceFileChooserButton.setEnabled(isoSourceSelected);
        setSystemSource(isoSourceSelected
                ? isoSystemSource
                : runningSystemSource);
        updateInstallationSource();
    }

    private void setISOInstallationSourcePath(String path) {

        try {

            ProcessExecutor processExecutor = new ProcessExecutor(true);

            SystemSource newIsoSystemSource
                    = new IsoSystemSource(path, processExecutor);

            if (isoSystemSource != null) {
                isoSystemSource.unmountTmpPartitions();
            }

            isoSystemSource = newIsoSystemSource;
            setSystemSource(isoSystemSource);
            isoSourceTextField.setText(path);
            updateInstallationSource();

        } catch (IllegalStateException | IOException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String errorMessage = STRINGS.getString("Error_Invalid_ISO");
            errorMessage = MessageFormat.format(errorMessage, path);
            dlCopySwingGUI.showErrorMessage(errorMessage);

        } catch (NoExecutableExtLinuxException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String errorMessage = STRINGS.getString(
                    "Error_No_Executable_Extlinux");
            errorMessage = MessageFormat.format(errorMessage, path);
            dlCopySwingGUI.showErrorMessage(errorMessage);

        } catch (NoExtLinuxException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String errorMessage = STRINGS.getString(
                    "Error_Deprecyted_ISO");
            errorMessage = MessageFormat.format(errorMessage, path);
            dlCopySwingGUI.showErrorMessage(errorMessage);
        }
    }

    private void setSystemSource(SystemSource systemSource) {

        // early return
        if (systemSource == null) {
            return;
        }

        // update system source itself
        dlCopySwingGUI.setSystemSource(systemSource);

        // update source dependend strings and states
        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(systemSource.getSystemSize());
        String sizeString
                = LernstickFileTools.getDataVolumeString(enlargedSystemSize, 1);

        storageDeviceRenderer.setSystemSize(enlargedSystemSize);
        storageDeviceList.repaint();

        selectionHeaderLabel.setText(MessageFormat.format(
                STRINGS.getString("Select_Install_Target_Storage_Media"),
                sizeString));

        systemDefinitionLabel.setText(MessageFormat.format(
                STRINGS.getString("System_Definition"), sizeString));

        // detect if system has an exchange partition
        boolean hasExchange = systemSource.hasExchangePartition();
        copyExchangePartitionCheckBox.setEnabled(hasExchange);
        if (hasExchange) {
            long exchangeSize
                    = systemSource.getExchangePartition().getUsedSpace(false);
            String dataVolumeString
                    = LernstickFileTools.getDataVolumeString(exchangeSize, 1);
            copyExchangePartitionCheckBox.setText(
                    STRINGS.getString("Copy") + " (" + dataVolumeString + ')');
            copyExchangePartitionCheckBox.setToolTipText(null);
        } else {
            copyExchangePartitionCheckBox.setToolTipText(
                    STRINGS.getString("No_Exchange_Partition"));
        }

        Partition dataPartition = systemSource.getDataPartition();
        if (dataPartition == null) {
            copyDataPartitionCheckBox.setEnabled(false);
            copyDataPartitionCheckBox.setText(STRINGS.getString("Copy"));
            copyDataPartitionCheckBox.setToolTipText(
                    STRINGS.getString("No_Data_Partition"));

        } else {
            copyDataPartitionCheckBox.setEnabled(true);
            String checkBoxText = STRINGS.getString("Copy")
                    + " (" + LernstickFileTools.getDataVolumeString(
                            dataPartition.getUsedSpace(false), 1) + ')';
            copyDataPartitionCheckBox.setText(checkBoxText);
            copyDataPartitionCheckBox.setToolTipText(null);
        }
        syncWidth(copyExchangePartitionCheckBox, copyDataPartitionCheckBox);

        if (StorageDevice.Type.USBFlashDrive == systemSource.getDeviceType()) {
            infoLabel.setIcon(new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/usb2usb.png")));
        }
    }

    /**
     * checks, if all storage devices selected for installation are large enough
     * and [en/dis]ables the "Next" button accordingly
     */
    private void updateNextButton() {

        // no valid source selected
        if (isoSourceRadioButton.isSelected()
                && isoSourceTextField.getText().isEmpty()) {

            dlCopySwingGUI.disableNextButton();
            return;
        }

        int[] selectedIndices = storageDeviceList.getSelectedIndices();

        // no storage device selected
        if (selectedIndices.length == 0) {
            dlCopySwingGUI.disableNextButton();
            return;
        }

        SystemSource systemSource = dlCopySwingGUI.getSystemSource();

        if (systemSource == null) {
            LOGGER.warning("No system source configured!");

        } else {
            // check selection
            long enlargedSystemSize = DLCopy.getEnlargedSystemSize(
                    systemSource.getSystemSize());

            for (int i : selectedIndices) {

                StorageDevice device = storageDeviceListModel.get(i);

                PartitionState partitionState = DLCopy.getPartitionState(
                        device.getSize(), enlargedSystemSize);

                if (partitionState == PartitionState.TOO_SMALL) {
                    // a selected device is too small, disable nextButton
                    dlCopySwingGUI.disableNextButton();
                    return;
                }
            }

            // all selected devices are large enough, enable nextButton
            dlCopySwingGUI.enableNextButton();
        }
    }

    private void setMajorTickSpacing(JSlider slider, int maxValue) {
        Graphics graphics = slider.getGraphics();
        FontMetrics fontMetrics = graphics.getFontMetrics();
        int width = slider.getWidth();
        int halfWidth = width / 2;
        // try with the following values:
        // 1,2,5,10,20,50,100,200,500,...
        int tickSpacing = 1;
        for (int i = 0, tmpWidthSum = width + 1; tmpWidthSum > halfWidth; i++) {
            tickSpacing = (int) Math.pow(10, (i / 3));
            switch (i % 3) {
                case 1:
                    tickSpacing *= 2;
                    break;
                case 2:
                    tickSpacing *= 5;
            }
            tmpWidthSum = 0;
            for (int j = 0; j < maxValue; j += tickSpacing) {
                Rectangle2D stringBounds = fontMetrics.getStringBounds(
                        String.valueOf(j), graphics);
                tmpWidthSum += (int) stringBounds.getWidth();
                if (tmpWidthSum > halfWidth) {
                    // the labels are too long
                    break;
                }
            }
        }
        slider.setMajorTickSpacing(tickSpacing);
        slider.setLabelTable(createLabels(slider, tickSpacing));
    }

    private Dictionary<Integer, JComponent> createLabels(
            JSlider slider, int tickSpacing) {

        Dictionary<Integer, JComponent> labels = new Hashtable<>();

        // we want to use a number format with grouping
        NumberFormat sliderNumberFormat = NumberFormat.getInstance();
        sliderNumberFormat.setGroupingUsed(true);
        for (int i = 0, max = slider.getMaximum(); i <= max; i += tickSpacing) {
            String text = sliderNumberFormat.format(i);
            labels.put(i, new JLabel(text));
        }

        return labels;
    }

    private void setSpinnerColums(JSpinner spinner, int columns) {
        JComponent editor = spinner.getEditor();
        JFormattedTextField tf
                = ((JSpinner.DefaultEditor) editor).getTextField();
        tf.setColumns(columns);
    }

    private void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        if (document == exchangePartitionSizeTextField.getDocument()) {
            String text = exchangePartitionSizeTextField.getText();
            try {
                int intValue = Integer.parseInt(text);
                if ((intValue >= exchangePartitionSizeSlider.getMinimum())
                        && (intValue <= exchangePartitionSizeSlider.getMaximum())) {
                    textFieldTriggeredSliderChange = true;
                    exchangePartitionSizeSlider.setValue(intValue);
                    textFieldTriggeredSliderChange = false;
                }
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    private boolean checkPersistence(PartitionSizes partitionSizes)
            throws IOException, DBusException {

        if (!(copyDataPartitionCheckBox.isEnabled()
                && copyDataPartitionCheckBox.isSelected())) {
            return true;
        }

        if (!dlCopySwingGUI.isUnmountedPersistenceAvailable()) {
            return false;
        }

        SystemSource systemSource = dlCopySwingGUI.getSystemSource();
        return checkPersistencePartition(
                systemSource.getDataPartition().getUsedSpace(false),
                partitionSizes, "Error_No_Persistence_At_Target",
                "Error_Target_Persistence_Too_Small");
    }

    private boolean checkPersistencePartition(
            long dataSize, PartitionSizes partitionSizes,
            String noPersistenceErrorMessage, String tooSmallErrorMessage) {

        // check if the target medium actually has a persistence partition
        if (partitionSizes.getPersistenceMB() == 0) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString(noPersistenceErrorMessage),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // check that target partition is large enough
        long targetPersistenceSize
                = (long) partitionSizes.getPersistenceMB() * (long) DLCopy.MEGA;
        if (dataSize > targetPersistenceSize) {
            String errorMessage = STRINGS.getString(tooSmallErrorMessage);
            errorMessage = MessageFormat.format(errorMessage,
                    LernstickFileTools.getDataVolumeString(dataSize, 1),
                    LernstickFileTools.getDataVolumeString(
                            targetPersistenceSize, 1));
            JOptionPane.showMessageDialog(this, errorMessage,
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean checkExchange(PartitionSizes partitionSizes)
            throws IOException {

        // early return
        if (!copyExchangePartitionCheckBox.isEnabled()
                || !copyExchangePartitionCheckBox.isSelected()) {
            return true;
        }

        // check if the target storage device actually has an exchange partition
        return checkExchangePartition(
                dlCopySwingGUI.getSystemSource().getExchangePartition(),
                partitionSizes, "Error_No_Exchange_At_Target",
                "Error_Target_Exchange_Too_Small");
    }

    private boolean checkExchangePartition(
            Partition exchangePartition, PartitionSizes partitionSizes,
            String noExchangeErrorMessage, String tooSmallErrorMessage) {

        if (partitionSizes.getExchangeMB() == 0) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString(noExchangeErrorMessage),
                    STRINGS.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // check that target partition is large enough
        if (exchangePartition != null) {
            long sourceExchangeSize = exchangePartition.getUsedSpace(false);
            long targetExchangeSize = (long) partitionSizes.getExchangeMB()
                    * (long) DLCopy.MEGA;
            if (sourceExchangeSize > targetExchangeSize) {
                JOptionPane.showMessageDialog(this,
                        STRINGS.getString(tooSmallErrorMessage),
                        STRINGS.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        return true;
    }

    private boolean checkTransfer(StorageDevice storageDevice,
            PartitionSizes partitionSizes) {

        StorageDevice transferSourceDevice
                = transferStorageDeviceList.getSelectedValue();

        if (transferSourceDevice == null) {
            return true;
        }

        // check that storage device is not selected as transfer source
        if (transferSourceDevice.equals(storageDevice)) {
            String errorMessage = STRINGS.getString(
                    "Error_Device_Is_Transfer_Source");
            errorMessage = MessageFormat.format(errorMessage,
                    InstallStorageDeviceRenderer.getDeviceString(
                            storageDevice));
            JOptionPane.showMessageDialog(this, errorMessage,
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // check that (if exchange partition is transferred) the target
        // exchange partition is large enough
        if (transferExchangeCheckBox.isSelected()
                && !checkExchangePartition(
                        transferSourceDevice.getExchangePartition(),
                        partitionSizes, "Error_Transfer_No_Exchange_At_Target",
                        "Error_Transfer_Target_Exchange_Too_Small")) {
            return false;
        }

        // check that if data is transferred the target persistence partition is
        // large enough
        long sourceSize = 0;
        if (transferHomeCheckBox.isSelected()) {
            sourceSize += transferSourceDevice.getDataPartition().getUsedSpace(
                    "/rw/home/user/");
        }
        if (transferNetworkCheckBox.isSelected()) {
            sourceSize += transferSourceDevice.getDataPartition().getUsedSpace(
                    "/rw/etc/NetworkManager/");
        }
        if (transferPrinterCheckBox.isSelected()) {
            sourceSize += transferSourceDevice.getDataPartition().getUsedSpace(
                    "/rw/etc/cups/");
        }
        if (transferFirewallCheckBox.isSelected()) {
            sourceSize += transferSourceDevice.getDataPartition().getUsedSpace(
                    "/rw/etc/lernstick-firewall/");
        }

        return checkPersistencePartition(sourceSize, partitionSizes,
                "Error_Transfer_No_Persistence_At_Target",
                "Error_Transfer_Target_Persistence_Too_Small");
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel autoNumberIncrementLabel;
    private javax.swing.JSpinner autoNumberIncrementSpinner;
    private javax.swing.JLabel autoNumberMinDigitsLabel;
    private javax.swing.JSpinner autoNumberMinDigitsSpinner;
    private javax.swing.JPanel autoNumberPanel;
    private javax.swing.JLabel autoNumberPatternLabel;
    private javax.swing.JTextField autoNumberPatternTextField;
    private javax.swing.JLabel autoNumberStartLabel;
    private javax.swing.JSpinner autoNumberStartSpinner;
    private javax.swing.JPanel basicDataPartitionPanel;
    private javax.swing.JPanel basicExchangePartitionPanel;
    private javax.swing.JPanel basicsPanel;
    private javax.swing.JLabel bootDefinitionLabel;
    private javax.swing.JCheckBox checkCopiesCheckBox;
    private javax.swing.JCheckBox copyDataPartitionCheckBox;
    private javax.swing.JCheckBox copyExchangePartitionCheckBox;
    private javax.swing.JLabel copyLabel;
    private javax.swing.JPanel copyPanel;
    private javax.swing.JLabel cpFilenameLabel;
    private javax.swing.JPanel cpPanel;
    private javax.swing.JProgressBar cpPogressBar;
    private javax.swing.JLabel cpTimeLabel;
    private javax.swing.JPanel currentPanel;
    private javax.swing.JLabel currentlyInstalledDeviceLabel;
    private javax.swing.JLabel dataDefinitionLabel;
    private javax.swing.JPanel dataPartitionDetailsPanel;
    private javax.swing.JComboBox<String> dataPartitionFileSystemComboBox;
    private javax.swing.JComboBox<String> dataPartitionModeComboBox;
    private javax.swing.JLabel dataPartitionModeLabel;
    private javax.swing.JSeparator dataPartitionSeparator;
    private javax.swing.JPanel detailsPanel;
    private javax.swing.ButtonGroup encryptionButtonGroup;
    private javax.swing.JPanel encryptionPanel;
    private javax.swing.JLabel exchangeDefinitionLabel;
    private javax.swing.JPanel exchangePartitionDetailsPanel;
    private javax.swing.JComboBox<String> exchangePartitionFileSystemComboBox;
    private javax.swing.JLabel exchangePartitionFileSystemLabel;
    private javax.swing.JPanel exchangePartitionFileSystemPanel;
    private javax.swing.JLabel exchangePartitionLabel;
    private javax.swing.JPanel exchangePartitionLabelPanel;
    private javax.swing.JTextField exchangePartitionLabelTextField;
    private javax.swing.JSeparator exchangePartitionSeparator;
    private javax.swing.JLabel exchangePartitionSizeLabel;
    private javax.swing.JSlider exchangePartitionSizeSlider;
    private javax.swing.JTextField exchangePartitionSizeTextField;
    private javax.swing.JLabel exchangePartitionSizeUnitLabel;
    private ch.fhnw.filecopier.FileCopierPanel fileCopierPanel;
    private javax.swing.JPanel fileSystemPanel;
    private javax.swing.JProgressBar indeterminateProgressBar;
    private javax.swing.JPanel indeterminateProgressPanel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JPanel installCardPanel;
    private javax.swing.ButtonGroup installSourceButtonGroup;
    private javax.swing.JButton isoSourceFileChooserButton;
    private javax.swing.JRadioButton isoSourceRadioButton;
    private javax.swing.JTextField isoSourceTextField;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JLabel noMediaLabel;
    private javax.swing.JPanel noMediaPanel;
    private javax.swing.JLabel noSouceLabel;
    private javax.swing.JPanel noSourcePanel;
    private javax.swing.JCheckBox overwriteWithRandomDataCheckBox;
    private javax.swing.JCheckBox personalPasswordCheckBox;
    private javax.swing.JPasswordField personalPasswordField;
    private javax.swing.JToggleButton personalPasswordToggleButton;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JPanel reportPanel;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JPanel rsyncPanel;
    private javax.swing.JProgressBar rsyncPogressBar;
    private javax.swing.JLabel rsyncTimeLabel;
    private javax.swing.JRadioButton runningSystemSourceRadioButton;
    private javax.swing.JCheckBox secondaryPasswordCheckBox;
    private javax.swing.JPasswordField secondaryPasswordField;
    private javax.swing.JToggleButton secondaryPasswordToggleButton;
    private javax.swing.JPanel selectionCardPanel;
    private javax.swing.JLabel selectionCountLabel;
    private javax.swing.JLabel selectionHeaderLabel;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JTabbedPane selectionTabbedPane;
    private javax.swing.JCheckBox showHardDisksCheckBox;
    private javax.swing.JPanel sourcePanel;
    private javax.swing.JList<StorageDevice> storageDeviceList;
    private javax.swing.JScrollPane storageDeviceListScrollPane;
    private javax.swing.JLabel systemDefinitionLabel;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JPanel targetCardPanel;
    private javax.swing.JPanel targetPanel;
    private javax.swing.JPanel transferCheckboxPanel;
    private javax.swing.JCheckBox transferExchangeCheckBox;
    private javax.swing.JCheckBox transferFirewallCheckBox;
    private javax.swing.JCheckBox transferHomeCheckBox;
    private javax.swing.JLabel transferLabel;
    private javax.swing.JCheckBox transferNetworkCheckBox;
    private javax.swing.JPanel transferPanel;
    private javax.swing.JCheckBox transferPrinterCheckBox;
    private javax.swing.JScrollPane transferScrollPane;
    private javax.swing.JList<StorageDevice> transferStorageDeviceList;
    // End of variables declaration//GEN-END:variables
}
