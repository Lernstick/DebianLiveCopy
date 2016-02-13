/*
 * DLCopy.java
 *
 * Created on 16. April 2008, 09:14
 */
package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.DebianLiveDistribution;
import ch.fhnw.dlcopy.PartitionState;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.InstallationSource;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.IsoCreator;
import ch.fhnw.dlcopy.IsoInstallationSource;
import ch.fhnw.dlcopy.PartitionSizes;
import ch.fhnw.dlcopy.Repairer;
import ch.fhnw.dlcopy.RepartitionStrategy;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.SystemInstallationSource;
import ch.fhnw.dlcopy.Upgrader;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.FileCopierPanel;
import ch.fhnw.jbackpack.JSqueezedLabel;
import ch.fhnw.jbackpack.RdiffBackupRestore;
import ch.fhnw.jbackpack.chooser.SelectBackupDirectoryDialog;
import ch.fhnw.util.DbusTools;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Installs Debian Live to a USB flash drive
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DLCopySwingGUI extends JFrame
        implements DLCopyGUI, DocumentListener, PropertyChangeListener,
        ListDataListener {

    private final static Logger LOGGER
            = Logger.getLogger(DLCopySwingGUI.class.getName());
    private final static ProcessExecutor PROCESS_EXECUTOR
            = new ProcessExecutor();
    private final static FileFilter NO_HIDDEN_FILES_FILTER
            = NoHiddenFilesSwingFileFilter.getInstance();
    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";
    private final DefaultListModel<StorageDevice> installStorageDeviceListModel
            = new DefaultListModel<>();
    private final DefaultListModel<StorageDevice> upgradeStorageDeviceListModel
            = new DefaultListModel<>();
    private final DefaultListModel<StorageDevice> repairStorageDeviceListModel
            = new DefaultListModel();
    private final InstallStorageDeviceRenderer installStorageDeviceRenderer;
    private final UpgradeStorageDeviceRenderer upgradeStorageDeviceRenderer;
    private final RepairStorageDeviceRenderer repairStorageDeviceRenderer;
    private final DateFormat timeFormat;

    private enum State {

        INSTALL_INFORMATION, INSTALL_SELECTION, INSTALLATION,
        UPGRADE_INFORMATION, UPGRADE_SELECTION, UPGRADE,
        REPAIR_INFORMATION, REPAIR_SELECTION, REPAIR,
        ISO_INFORMATION, ISO_SELECTION, ISO_INSTALLATION
    }
    private State state = State.INSTALL_INFORMATION;

    // Information about currently running system image
    private InstallationSource systemSource;
    private InstallationSource source = null;

    private boolean persistenceBoot;
    private boolean textFieldTriggeredSliderChange;

    private String isoImagePath;
    private DebianLiveDistribution debianLiveDistribution;

    private UdisksMonitorThread udisksMonitorThread;
    private DefaultListModel<String> upgradeOverwriteListModel;
    private JFileChooser addFileChooser;
    private RdiffBackupRestore rdiffBackupRestore;
    private Preferences preferences;
    private final static String UPGRADE_SYSTEM_PARTITION = "upgradeSystemPartition";
    private final static String REACTIVATE_WELCOME = "reactivateWelcome";
    private final static String KEEP_PRINTER_SETTINGS = "keepPrinterSettings";
    private final static String REMOVE_HIDDEN_FILES = "removeHiddenFiles";
    private final static String AUTOMATIC_BACKUP = "automaticBackup";
    private final static String BACKUP_DESTINATION = "backupDestination";
    private final static String AUTO_REMOVE_BACKUP = "autoRemoveBackup";
    private final static String UPGRADE_OVERWRITE_LIST = "upgradeOverwriteList";

    private final ResultsTableModel installationResultsTableModel;
    private final ResultsTableModel upgradeResultsTableModel;
    private final ResultsTableModel resultsTableModel;
    private CpActionListener cpActionListener;

    private final static Pattern ADDED_PATTERN = Pattern.compile(
            ".*: Added (/org/freedesktop/UDisks2/block_devices/.*)");
    private final static Pattern REMOVED_PATTERN = Pattern.compile(
            ".*: Removed (/org/freedesktop/UDisks2/block_devices/.*)");

    private final StorageDeviceListUpdateDialogHandler storageDeviceListUpdateDialogHandler
            = new StorageDeviceListUpdateDialogHandler(this);

    private int batchCounter;
    private long deviceStartTime;
    private StorageDevice currentDevice;
    private List<StorageDeviceResult> resultsList;

    /**
     * Creates new form DLCopy
     *
     * @param arguments the command line arguments
     */
    public DLCopySwingGUI(String[] arguments) {
        /**
         * set up logging
         */
        Logger globalLogger = Logger.getLogger("ch.fhnw");
        globalLogger.setLevel(Level.ALL);
        // log to console
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.ALL);
        globalLogger.addHandler(consoleHandler);
        // also log into a rotating temporaty file of max 5 MB
        try {
            FileHandler fileHandler
                    = new FileHandler("%t/DebianLiveCopy", 5000000, 2, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            globalLogger.addHandler(fileHandler);

        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.SEVERE, "can not create log file", ex);
        }
        // prevent double logs in console
        globalLogger.setUseParentHandlers(false);
        LOGGER.info("*********** Starting dlcopy ***********");

        // prepare processExecutor to always use the POSIX locale
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        ToolTipManager.sharedInstance().setDismissDelay(60000);

        timeFormat = new SimpleDateFormat("HH:mm:ss");
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        if (LOGGER.isLoggable(Level.INFO)) {
            if (arguments.length > 0) {
                StringBuilder stringBuilder = new StringBuilder("arguments: ");
                for (String argument : arguments) {
                    stringBuilder.append(argument);
                    stringBuilder.append(' ');
                }
                LOGGER.info(stringBuilder.toString());
            } else {
                LOGGER.info("no command line arguments");
            }
        }

        // parse command line arguments
        debianLiveDistribution = DebianLiveDistribution.DEFAULT;
        isoImagePath = null;
        for (int i = 0, length = arguments.length; i < length; i++) {

            if (arguments[i].equals("--variant")
                    && (i != length - 1)
                    && (arguments[i + 1].equals("lernstick"))) {
                debianLiveDistribution = DebianLiveDistribution.LERNSTICK;
            }
            if (arguments[i].equals("--variant")
                    && (i != length - 1)
                    && (arguments[i + 1].equals("lernstick-pu"))) {
                debianLiveDistribution = DebianLiveDistribution.LERNSTICK_EXAM;
            }
            if (arguments[i].equals("--iso") && (i != length - 1)) {
                isoImagePath = arguments[i + 1];
            }
        }
        if (LOGGER.isLoggable(Level.INFO)) {
            switch (debianLiveDistribution) {
                case LERNSTICK:
                    LOGGER.info("using lernstick distribution");
                    break;
                case LERNSTICK_EXAM:
                    LOGGER.info("using lernstick exam environment distribution");
                    break;
                case DEFAULT:
                    LOGGER.info("using Debian Live distribution");
                    break;
                default:
                    LOGGER.log(Level.WARNING, "unsupported distribution: {0}",
                            debianLiveDistribution);
            }
        }

        switch (debianLiveDistribution) {
            case LERNSTICK:
            case LERNSTICK_EXAM:
                DLCopy.systemPartitionLabel = "system";
                break;
            default:
                DLCopy.systemPartitionLabel = "DEBIAN_LIVE";
        }

        try {
            systemSource = new SystemInstallationSource(PROCESS_EXECUTOR);
            source = systemSource;
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);

        }
        if (isoImagePath != null) {
            source = new IsoInstallationSource(isoImagePath, PROCESS_EXECUTOR);
        }

        initComponents();

        // do not show initial "{0}" placeholder
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString, 0, 0);
        installSelectionCountLabel.setText(countString);
        upgradeSelectionCountLabel.setText(countString);
        repairSelectionCountLabel.setText(countString);

        tmpDirTextField.getDocument().addDocumentListener(this);
        if (StorageDevice.Type.USBFlashDrive == source.getDeviceType()) {
            Icon usb2usbIcon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/usb2usb.png"));
            infoLabel.setIcon(usb2usbIcon);
            installButton.setIcon(usb2usbIcon);
        }
        getRootPane().setDefaultButton(installButton);
        installButton.requestFocusInWindow();

        URL imageURL = getClass().getResource(
                "/ch/fhnw/dlcopy/icons/usbpendrive_unmount.png");
        setIconImage(new ImageIcon(imageURL).getImage());

        DLCopy.systemSize = source.getSystemSize();
        DLCopy.systemSizeEnlarged
                = (long) (DLCopy.systemSize * DLCopy.SYSTEM_SIZE_FACTOR);
        String sizeString = LernstickFileTools.getDataVolumeString(
                DLCopy.systemSizeEnlarged, 1);

        String text = STRINGS.getString("Select_Install_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        installSelectionHeaderLabel.setText(text);

        text = STRINGS.getString("Boot_Definition");
        String bootSize = LernstickFileTools.getDataVolumeString(
                DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA, 1);
        text = MessageFormat.format(text, bootSize);
        bootDefinitionLabel.setText(text);

        text = STRINGS.getString("System_Definition");
        text = MessageFormat.format(text, sizeString);
        systemDefinitionLabel.setText(text);

        sizeString = LernstickFileTools.getDataVolumeString(
                DLCopy.systemSize, 1);
        text = STRINGS.getString("Select_Upgrade_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        upgradeSelectionHeaderLabel.setText(text);

        // detect if system has an exchange partition
        if (!source.hasExchangePartition()) {
            copyExchangeCheckBox.setEnabled(false);
            copyExchangeCheckBox.setToolTipText(
                    STRINGS.getString("No_Exchange_Partition"));
        }

        Partition dataPartition = source.getDataPartition();
        if (dataPartition == null) {
            copyPersistenceCheckBox.setEnabled(false);
            copyPersistenceCheckBox.setToolTipText(
                    STRINGS.getString("No_Data_Partition"));
        } else {
            final String CMD_LINE_FILENAME = "/proc/cmdline";
            try {
                String cmdLine = DLCopy.readOneLineFile(
                        new File(CMD_LINE_FILENAME));
                persistenceBoot = cmdLine.contains(" persistence ");
                LOGGER.log(Level.FINEST,
                        "persistenceBoot: {0}", persistenceBoot);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                        "could not read \"" + CMD_LINE_FILENAME + '\"', ex);
            }

            // We don't just disable the copyPersistenceCheckBox in the case of
            // persistenceBoot but show a more helpful error/hint dialog later.
            copyPersistenceCheckBox.setEnabled(true);

            String checkBoxText = STRINGS.getString("Copy_Data_Partition")
                    + " (" + LernstickFileTools.getDataVolumeString(
                            dataPartition.getUsedSpace(false), 1) + ')';
            copyPersistenceCheckBox.setText(checkBoxText);
        }

        installStorageDeviceList.setModel(installStorageDeviceListModel);
        installStorageDeviceRenderer = new InstallStorageDeviceRenderer(
                this, DLCopy.systemSizeEnlarged);
        installStorageDeviceList.setCellRenderer(installStorageDeviceRenderer);

        upgradeStorageDeviceList.setModel(upgradeStorageDeviceListModel);
        upgradeStorageDeviceRenderer = new UpgradeStorageDeviceRenderer();
        upgradeStorageDeviceList.setCellRenderer(upgradeStorageDeviceRenderer);

        repairStorageDeviceList.setModel(repairStorageDeviceListModel);
        repairStorageDeviceRenderer = new RepairStorageDeviceRenderer();
        repairStorageDeviceList.setCellRenderer(repairStorageDeviceRenderer);

        AbstractDocument exchangePartitionDocument
                = (AbstractDocument) exchangePartitionTextField.getDocument();
        exchangePartitionDocument.setDocumentFilter(new DocumentSizeFilter());
        exchangePartitionSizeTextField.getDocument().addDocumentListener(this);

        switch (debianLiveDistribution) {
            case LERNSTICK:
                isoLabelTextField.setText("lernstick");
                // default to exFAT for exchange partition
                ComboBoxModel model = new DefaultComboBoxModel(
                        new String[]{"exFAT", "FAT32", "NTFS"});
                exchangePartitionFileSystemComboBox.setModel(model);
                exchangePartitionFileSystemComboBox.setSelectedItem("exFAT");
                break;
            case LERNSTICK_EXAM:
                isoLabelTextField.setText("lernstick");
                // default to FAT32 for exchange partition
                // (rdiff-backup can't cope with destinations on exFAT)
                model = new DefaultComboBoxModel(
                        new String[]{"FAT32", "exFAT", "NTFS"});
                exchangePartitionFileSystemComboBox.setModel(model);
                exchangePartitionFileSystemComboBox.setSelectedItem("FAT32");
                break;
        }

        // monitor udisks changes
        udisksMonitorThread = new UdisksMonitorThread();
        udisksMonitorThread.start();

        upgradeOverwriteListModel = new DefaultListModel();
        upgradeOverwriteListModel.addListDataListener(this);
        upgradeOverwriteList.setModel(upgradeOverwriteListModel);

        preferences = Preferences.userNodeForPackage(DLCopySwingGUI.class);
        upgradeSystemPartitionCheckBox.setSelected(
                preferences.getBoolean(UPGRADE_SYSTEM_PARTITION, true));
        reactivateWelcomeCheckBox.setSelected(
                preferences.getBoolean(REACTIVATE_WELCOME, true));
        keepPrinterSettingsCheckBox.setSelected(
                preferences.getBoolean(KEEP_PRINTER_SETTINGS, true));
        automaticBackupCheckBox.setSelected(
                preferences.getBoolean(AUTOMATIC_BACKUP, false));
        automaticBackupTextField.setText(
                preferences.get(BACKUP_DESTINATION, null));
        removeHiddenFilesCheckBox.setSelected(
                preferences.getBoolean(REMOVE_HIDDEN_FILES, false));
        automaticBackupRemoveCheckBox.setSelected(
                preferences.getBoolean(AUTO_REMOVE_BACKUP, false));
        String upgradeOverWriteList = preferences.get(
                UPGRADE_OVERWRITE_LIST, "");
        fillUpgradeOverwriteList(upgradeOverWriteList);

        // default to ext4 for data partition
        dataPartitionFilesystemComboBox.setSelectedItem("ext4");

        // init data partition modes
        String[] dataPartitionModes = new String[]{
            STRINGS.getString("Read_Write"),
            STRINGS.getString("Read_Only"),
            STRINGS.getString("Not_Used")
        };
        dataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel(dataPartitionModes));
        isoDataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel(dataPartitionModes));

        DataPartitionMode sourceDataPartitionMode
                = source.getDataPartitionMode();
        if (sourceDataPartitionMode != null) {
            String selectedItem = null;
            switch (sourceDataPartitionMode) {
                case NOT_USED:
                    selectedItem = STRINGS.getString("Not_Used");
                    break;

                case READ_ONLY:
                    selectedItem = STRINGS.getString("Read_Only");
                    break;

                case READ_WRITE:
                    selectedItem = STRINGS.getString("Read_Write");
                    break;

                default:
                    LOGGER.warning("Unsupported data partition mode!");
            }
            dataPartitionModeComboBox.setSelectedItem(selectedItem);
            isoDataPartitionModeComboBox.setSelectedItem(selectedItem);
        }

        // set colums for spinners
        setSpinnerColums(autoNumberStartSpinner, 2);
        setSpinnerColums(autoNumberIncrementSpinner, 2);

        installationResultsTableModel
                = new ResultsTableModel(installationResultsTable);
        installationResultsTable.setModel(installationResultsTableModel);
        TableColumn sizeColumn
                = installationResultsTable.getColumnModel().getColumn(
                        ResultsTableModel.SIZE_COLUMN);
        sizeColumn.setCellRenderer(new SizeTableCellRenderer());
        installationResultsTable.setRowSorter(
                new ResultsTableRowSorter(installationResultsTableModel));

        upgradeResultsTableModel
                = new ResultsTableModel(upgradeResultsTable);
        upgradeResultsTable.setModel(upgradeResultsTableModel);
        sizeColumn = upgradeResultsTable.getColumnModel().getColumn(
                ResultsTableModel.SIZE_COLUMN);
        sizeColumn.setCellRenderer(new SizeTableCellRenderer());
        upgradeResultsTable.setRowSorter(
                new ResultsTableRowSorter(upgradeResultsTableModel));

        resultsTableModel = new ResultsTableModel(resultsTable);
        resultsTable.setModel(resultsTableModel);
        sizeColumn = resultsTable.getColumnModel().getColumn(
                ResultsTableModel.SIZE_COLUMN);
        sizeColumn.setCellRenderer(new SizeTableCellRenderer());
        resultsTable.setRowSorter(new ResultsTableRowSorter(resultsTableModel));

        // TODO: pack() does not work reliably!?
        //pack();
        setSize(950, 550);
        setLocationRelativeTo(null);
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
    public void propertyChange(final PropertyChangeEvent evt) {

        // Take great care when calling Swing functions,
        // because here we are on the UdisksMonitorThread!
        // only handle line changes
        if (!ProcessExecutor.LINE.equals(evt.getPropertyName())) {
            return;
        }

        String line = (String) evt.getNewValue();

        boolean deviceAdded;
        Matcher matcher = null;
        if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
            deviceAdded = line.startsWith(UDISKS_ADDED);
        } else {
            matcher = ADDED_PATTERN.matcher(line);
            deviceAdded = matcher.matches();
        }

        if (deviceAdded) {
            String addedPath;
            if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
                addedPath = line.substring(UDISKS_ADDED.length()).trim();
            } else {
                addedPath = matcher.group(1);
            }
            LOGGER.log(Level.INFO, "added path: \"{0}\"", addedPath);

            switch (state) {
                case INSTALL_SELECTION:
                    new InstallStorageDeviceAdder(addedPath,
                            installShowHarddisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            installStorageDeviceListModel,
                            installStorageDeviceList, this).execute();
                    break;

                case UPGRADE_SELECTION:
                    new UpgradeStorageDeviceAdder(addedPath,
                            upgradeShowHarddisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            upgradeStorageDeviceListModel,
                            upgradeStorageDeviceList, this).execute();
                    break;

                case REPAIR_SELECTION:
                    new RepairStorageDeviceAdder(addedPath,
                            repairShowHarddisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            repairStorageDeviceListModel,
                            repairStorageDeviceList, this).execute();
                    break;

                default:
                    LOGGER.log(Level.INFO,
                            "device change not handled in state {0}",
                            state);
            }

        } else {
            // check, if a device was removed
            boolean deviceRemoved;
            if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
                deviceRemoved = line.startsWith(UDISKS_REMOVED);
            } else {
                matcher = REMOVED_PATTERN.matcher(line);
                deviceRemoved = matcher.matches();
            }
            if (deviceRemoved) {
                removeStorageDevice(line);
            }
        }
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

    @Override
    public void showInstallProgress() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(cardPanel, "installTabbedPane");
            }
        });
    }

    @Override
    public void installingDeviceStarted(StorageDevice storageDevice) {
        deviceStarted(storageDevice);

        // update label
        String pattern = STRINGS.getString("Install_Device_Info");
        String deviceInfo = MessageFormat.format(pattern,
                storageDevice.getVendor() + " " + storageDevice.getModel() + " "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1),
                "/dev/" + storageDevice.getDevice(), batchCounter,
                installStorageDeviceList.getSelectedIndices().length);
        setLabelTextonEDT(currentlyInstalledDeviceLabel, deviceInfo);

        // add "in progress" entry to results table
        installationResultsTableModel.setList(resultsList);
    }

    @Override
    public void showInstallCreatingFileSystems() {
        showInstallIndeterminateProgressBarText("Creating_File_Systems");
    }

    @Override
    public void showInstallFileCopy(FileCopier fileCopier) {
        showFileCopy(installFileCopierPanel, fileCopier, installCopyLabel,
                installCardPanel, "installCopyPanel");
    }

    @Override
    public void showInstallPersistencyCopy(
            Installer installer, String copyScript, String sourcePath) {
        cpActionListener = new CpActionListener(
                cpFilenameLabel, cpTimeLabel, sourcePath);
        javax.swing.Timer cpTimer
                = new javax.swing.Timer(1000, cpActionListener);
        cpTimer.setInitialDelay(0);
        cpTimer.start();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                cpFilenameLabel.setText(" ");
                cpPogressBar.setValue(0);
                cpTimeLabel.setText(timeFormat.format(new Date(0)));
                showCard(installCardPanel, "cpPanel");
            }
        });

        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        try {
            PROCESS_EXECUTOR.addPropertyChangeListener(installer);
            int exitValue
                    = PROCESS_EXECUTOR.executeScript(true, true, copyScript);
            PROCESS_EXECUTOR.removePropertyChangeListener(installer);
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

    @Override
    public void setInstallCopyLine(String line) {
        cpActionListener.setCurrentLine(line);
    }

    @Override
    public void showInstallUnmounting() {
        showInstallIndeterminateProgressBarText("Unmounting_File_Systems");
    }

    @Override
    public void showInstallWritingBootSector() {
        showInstallIndeterminateProgressBarText("Writing_Boot_Sector");
    }

    @Override
    public void installingDeviceFinished(
            String errorMessage, int autoNumberStart) {
        // update final report
        deviceFinished(errorMessage);

        // update current report
        installationResultsTableModel.setList(resultsList);

        autoNumberStartSpinner.setValue(autoNumberStart);
    }

    @Override
    public void installingListFinished() {
        batchFinished(
                "Installation_Done_Message_From_Non_Removable_Boot_Device",
                "Installation_Done_Message_From_Removable_Boot_Device",
                "Installation_Report");
    }

    @Override
    public void upgradingDeviceStarted(StorageDevice storageDevice) {
        deviceStarted(storageDevice);

        // update label
        String pattern = STRINGS.getString("Upgrade_Device_Info");
        String deviceInfo = MessageFormat.format(pattern, batchCounter,
                upgradeStorageDeviceList.getSelectedIndices().length,
                storageDevice.getVendor() + " " + storageDevice.getModel(), " ("
                + STRINGS.getString("Size") + ": "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1) + ", "
                + STRINGS.getString("Revision") + ": "
                + storageDevice.getRevision() + ", "
                + STRINGS.getString("Serial") + ": "
                + storageDevice.getSerial() + ", "
                + "&#47;dev&#47;" + storageDevice.getDevice() + ")");
        setLabelTextonEDT(currentlyUpgradedDeviceLabel, deviceInfo);

        // add "in progress" entry to results table
        upgradeResultsTableModel.setList(resultsList);
    }

    @Override
    public void showUpgradeBackup() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                upgradeBackupLabel.setText(
                        STRINGS.getString("Backing_Up_User_Data"));
                upgradeBackupDurationLabel.setText(
                        timeFormat.format(new Date(0)));
                showCard(upgradeCardPanel, "upgradeBackupPanel");
            }
        });
    }

    @Override
    public void setUpgradeBackupProgress(String progressInfo) {
        setLabelTextonEDT(upgradeBackupProgressLabel, progressInfo);
    }

    @Override
    public void setUpgradeBackupFilename(String filename) {
        setLabelTextonEDT(upgradeBackupFilenameLabel, filename);
    }

    @Override
    public void setUpgradeBackupDuration(final long time) {
        setLabelTextonEDT(upgradeBackupDurationLabel,
                timeFormat.format(new Date(time)));
    }

    @Override
    public void showUpgradeBackupExchangePartition(FileCopier fileCopier) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                upgradeCopyLabel.setText(STRINGS.getString(
                        "Backing_Up_Exchange_Partition"));
                showCard(upgradeCardPanel, "upgradeCopyPanel");
            }
        });
        upgradeFileCopierPanel.setFileCopier(fileCopier);
    }

    @Override
    public void showUpgradeRestoreInit() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(upgradeCardPanel, "upgradeIndeterminateProgressPanel");
                upgradeIndeterminateProgressBar.setString(
                        STRINGS.getString("Reading_Backup"));
            }
        });
    }

    @Override
    public void showUpgradeRestoreRunning() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                upgradeBackupLabel.setText(
                        STRINGS.getString("Restoring_User_Data"));
                upgradeBackupDurationLabel.setText(
                        timeFormat.format(new Date(0)));
                showCard(upgradeCardPanel, "upgradeBackupPanel");
            }
        });
    }

    @Override
    public void showUpgradeRestoreExchangePartition(FileCopier fileCopier) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                upgradeCopyLabel.setText(STRINGS.getString(
                        "Restoring_Exchange_Partition"));
                showCard(upgradeCardPanel, "upgradeCopyPanel");
            }
        });
        upgradeFileCopierPanel.setFileCopier(fileCopier);
    }

    @Override
    public void showUpgradeChangingPartitionSizes() {
        setProgressBarStringOnEDT(upgradeIndeterminateProgressBar,
                STRINGS.getString("Changing_Partition_Sizes"));
    }

    @Override
    public void showUpgradeDataPartitionReset() {
        setProgressBarStringOnEDT(upgradeIndeterminateProgressBar,
                STRINGS.getString("Resetting_Data_Partition"));
    }

    @Override
    public void showUpgradeCreatingFileSystems() {
        showUpgradeIndeterminateProgressBarText("Creating_File_Systems");
    }

    @Override
    public void showUpgradeFileCopy(FileCopier fileCopier) {
        showFileCopy(upgradeFileCopierPanel, fileCopier, upgradeCopyLabel,
                upgradeCardPanel, "upgradeCopyPanel");
    }

    @Override
    public void showUpgradeUnmounting() {
        showUpgradeIndeterminateProgressBarText("Unmounting_File_Systems");
    }

    @Override
    public void showUpgradeSystemPartitionReset() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(upgradeCardPanel, "upgradeIndeterminateProgressPanel");
                upgradeIndeterminateProgressBar.setString(
                        STRINGS.getString("Resetting_System_Partition"));
            }
        });
    }

    @Override
    public void showUpgradeWritingBootSector() {
        showUpgradeIndeterminateProgressBarText("Writing_Boot_Sector");
    }

    @Override
    public void upgradingDeviceFinished(String errorMessage) {
        // upgrade final report
        deviceFinished(errorMessage);

        // update current report
        upgradeResultsTableModel.setList(resultsList);
    }

    @Override
    public void upgradingListFinished() {
        batchFinished(
                "Upgrade_Done_From_Non_Removable_Device",
                "Upgrade_Done_From_Removable_Device",
                "Upgrade_Report");
    }

    @Override
    public void showIsoProgressMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                toISOProgressBar.setIndeterminate(true);
                toISOProgressBar.setString(message);
            }
        });
    }

    @Override
    public void showIsoProgressMessage(final String message, final int value) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                toISOProgressBar.setIndeterminate(false);
                toISOProgressBar.setString(message);
                toISOProgressBar.setValue(value);
            }
        });
    }

    @Override
    public void isoCreationFinished(String isoPath, boolean success) {
        String message;
        if (success) {
            message = STRINGS.getString("DLCopySwingGUI.isoDoneLabel.text");
            message = MessageFormat.format(message, isoPath);
        } else {
            message = STRINGS.getString("Error_ISO_Creation");
        }
        isoDoneLabel.setText(message);
        showCard(cardPanel, "toISODonePanel");
        processDone();
    }

    @Override
    public void showRepairProgress() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(cardPanel, "repairPanel");
            }
        });
    }

    @Override
    public void repairingDeviceStarted(StorageDevice storageDevice) {
        deviceStarted(storageDevice);

        String pattern = STRINGS.getString("Repair_Device_Info");
        String message = MessageFormat.format(pattern, batchCounter,
                repairStorageDeviceList.getSelectedIndices().length,
                storageDevice.getVendor() + " " + storageDevice.getModel(),
                " (" + STRINGS.getString("Size") + ": "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1) + ", "
                + STRINGS.getString("Revision") + ": "
                + storageDevice.getRevision() + ", "
                + STRINGS.getString("Serial") + ": "
                + storageDevice.getSerial() + ", " + "&#47;dev&#47;"
                + storageDevice.getDevice() + ")");
        setLabelTextonEDT(currentlyRepairedDeviceLabel, message);
    }

    @Override
    public void showRepairFormattingDataPartition() {
        setProgressBarStringOnEDT(repairProgressBar,
                STRINGS.getString("Formatting_Data_Partition"));
    }

    @Override
    public void showRepairRemovingFiles() {
        setProgressBarStringOnEDT(repairProgressBar,
                STRINGS.getString("Removing_Selected_Files"));
    }

    @Override
    public void repairingFinished(boolean success) {
        setTitle(STRINGS.getString("DLCopySwingGUI.title"));
        if (success) {
            doneLabel.setText(STRINGS.getString("Repair_Done"));
            showCard(cardPanel, "donePanel");
            processDone();
        } else {
            switchToRepairSelection();
        }
    }

    @Override
    public boolean showConfirmDialog(String title, String message) {
        int selection = JOptionPane.showConfirmDialog(this, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return selection == JOptionPane.YES_OPTION;
    }

    @Override
    public void showErrorMessage(final String errorMessage) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(DLCopySwingGUI.this, errorMessage,
                        STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * sets the text on a JLabel to display info about a StorageDevice
     *
     * @param label the JLabel where to set the text
     * @param storageDevice the given StorageDevice
     */
    public static void setStorageDeviceLabel(
            JLabel label, StorageDevice storageDevice) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<html><b>");
        if (storageDevice.isRaid()) {
            stringBuilder.append("RAID (");
            storageDevice.getRaidLevel();
            stringBuilder.append(", ");
            storageDevice.getRaidDeviceCount();
            stringBuilder.append(" ");
            stringBuilder.append(STRINGS.getString("Devices"));
            stringBuilder.append(")");
        } else {
            String vendor = storageDevice.getVendor();
            if ((vendor != null) && !vendor.isEmpty()) {
                stringBuilder.append(vendor);
                stringBuilder.append(" ");
            }
            String model = storageDevice.getModel();
            if ((model != null) && !model.isEmpty()) {
                stringBuilder.append(model);
            }
        }
        stringBuilder.append("</b>, ");
        stringBuilder.append(STRINGS.getString("Size"));
        stringBuilder.append(": ");
        stringBuilder.append(LernstickFileTools.getDataVolumeString(
                storageDevice.getSize(), 1));
        stringBuilder.append(", ");
        String revision = storageDevice.getRevision();
        if ((revision != null) && !revision.isEmpty()) {
            stringBuilder.append(STRINGS.getString("Revision"));
            stringBuilder.append(": ");
            stringBuilder.append(revision);
            stringBuilder.append(", ");
        }
        String serial = storageDevice.getSerial();
        if ((serial != null) && !serial.isEmpty()) {
            stringBuilder.append(STRINGS.getString("Serial"));
            stringBuilder.append(": ");
            stringBuilder.append(serial);
            stringBuilder.append(", ");
        }
        stringBuilder.append("&#47;dev&#47;");
        stringBuilder.append(storageDevice.getDevice());
        stringBuilder.append("</html>");
        label.setText(stringBuilder.toString());
    }

    /**
     * returns the exchange partition size slider
     *
     * @return the exchange partition size slider
     */
    public JSlider getExchangePartitionSizeSlider() {
        return exchangePartitionSizeSlider;
    }

    /**
     * must be called whenever the install storage device list changes to
     * execute some updates (e.g. maximum storage device size) and some sanity
     * checks
     */
    public void installStorageDeviceListChanged() {
        storageDeviceListChanged(
                installStorageDeviceListModel, installSelectionCardPanel,
                "installNoMediaPanel", "installListPanel",
                installStorageDeviceRenderer, installStorageDeviceList);
        updateInstallNextButton();
    }

    /**
     * must be called whenever the upgrade storage device list changes to
     * execute some updates (e.g. maximum storage device size) and some sanity
     * checks
     */
    public void upgradeStorageDeviceListChanged() {
        storageDeviceListChanged(
                upgradeStorageDeviceListModel, upgradeSelectionCardPanel,
                "upgradeNoMediaPanel", "upgradeSelectionDeviceListPanel",
                upgradeStorageDeviceRenderer, upgradeStorageDeviceList);
    }

    /**
     * must be called whenever the repair storage device list changes to execute
     * some updates
     */
    public void repairStorageDeviceListChanged() {
        storageDeviceListChanged(
                repairStorageDeviceListModel, repairSelectionCardPanel,
                "repairNoMediaPanel", "repairSelectionDeviceListPanel",
                repairStorageDeviceRenderer, repairStorageDeviceList);

    }

    /**
     * must be called whenever the selection count and exchange info for the
     * installer needs an update
     */
    public void updateInstallSelectionCountAndExchangeInfo() {

        // early return
        if (state != State.INSTALL_SELECTION) {
            return;
        }

        // check all selected storage devices
        long minOverhead = Long.MAX_VALUE;
        boolean exchange = true;
        int[] selectedIndices = installStorageDeviceList.getSelectedIndices();
        int selectionCount = selectedIndices.length;
        if (selectionCount == 0) {
            minOverhead = 0;
            exchange = false;
        } else {
            for (int i = 0; i < selectionCount; i++) {
                StorageDevice device
                        = (StorageDevice) installStorageDeviceListModel.get(
                                selectedIndices[i]);
                long overhead = device.getSize()
                        - (DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)
                        - DLCopy.systemSizeEnlarged;
                minOverhead = Math.min(minOverhead, overhead);
                PartitionState partitionState = DLCopy.getPartitionState(
                        device.getSize(),
                        (DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA)
                        + DLCopy.systemSizeEnlarged);
                if (partitionState != PartitionState.EXCHANGE) {
                    exchange = false;
                    break; // for
                }
            }
        }

        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, installStorageDeviceListModel.size());
        installSelectionCountLabel.setText(countString);

        exchangePartitionSizeLabel.setEnabled(exchange);
        exchangePartitionSizeSlider.setEnabled(exchange);
        exchangePartitionSizeTextField.setEnabled(exchange);
        exchangePartitionSizeUnitLabel.setEnabled(exchange);
        if (exchange) {
            int overheadMega = (int) (minOverhead / DLCopy.MEGA);
            exchangePartitionSizeSlider.setMaximum(overheadMega);
            setMajorTickSpacing(exchangePartitionSizeSlider, overheadMega);
            exchangePartitionSizeTextField.setText(
                    String.valueOf(exchangePartitionSizeSlider.getValue()));
        } else {
            exchangePartitionSizeSlider.setMaximum(0);
            exchangePartitionSizeSlider.setValue(0);
            // remove text
            exchangePartitionSizeTextField.setText(null);
        }
        exchangePartitionLabel.setEnabled(exchange);
        exchangePartitionTextField.setEnabled(exchange);
        autoNumberPatternLabel.setEnabled(exchange);
        autoNumberPatternTextField.setEnabled(exchange);
        autoNumberStartLabel.setEnabled(exchange);
        autoNumberStartSpinner.setEnabled(exchange);
        autoNumberIncrementLabel.setEnabled(exchange);
        autoNumberIncrementSpinner.setEnabled(exchange);
        exchangePartitionFileSystemLabel.setEnabled(exchange);
        exchangePartitionFileSystemComboBox.setEnabled(exchange);
        copyExchangeCheckBox.setEnabled(exchange
                && (source.hasExchangePartition()));

        // enable nextButton?
        updateInstallNextButton();
    }

    /**
     * must be called whenever the selection count and next button for the
     * upgrader needs an update
     */
    public void updateUpgradeSelectionCountAndNextButton() {
        // early return
        if (state != State.UPGRADE_SELECTION) {
            return;
        }

        boolean backupSelected = automaticBackupCheckBox.isSelected();

        // check all selected storage devices
        boolean canUpgrade = true;
        int[] selectedIndices = upgradeStorageDeviceList.getSelectedIndices();
        for (int i : selectedIndices) {
            StorageDevice storageDevice
                    = (StorageDevice) upgradeStorageDeviceListModel.get(i);
            try {
                StorageDevice.UpgradeVariant upgradeVariant
                        = storageDevice.getUpgradeVariant();
                switch (upgradeVariant) {
                    case IMPOSSIBLE:
                        canUpgrade = false;
                        break;
                    case BACKUP:
                        if (!backupSelected) {
                            canUpgrade = false;
                        }
                        break;
                    default:
                        LOGGER.log(Level.WARNING,
                                "unsupported upgradeVariant: {0}",
                                upgradeVariant);
                }
                if (!canUpgrade) {
                    break;
                }
            } catch (DBusException | IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }

        int selectionCount = selectedIndices.length;
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, upgradeStorageDeviceListModel.size());
        upgradeSelectionCountLabel.setText(countString);

        // update nextButton state
        if ((selectionCount > 0) && canUpgrade) {
            enableNextButton();
        } else {
            disableNextButton();
        }
    }

    /**
     * must be called whenever the selection count and next button for the
     * repairer needs an update
     */
    public void updateRepairSelectionCountAndNextButton() {

        // early return
        if (state != State.REPAIR_SELECTION) {
            return;
        }

        // check all selected storage devices
        boolean canRepair = true;
        int[] selectedIndices = repairStorageDeviceList.getSelectedIndices();
        for (int i : selectedIndices) {
            StorageDevice storageDevice
                    = (StorageDevice) repairStorageDeviceListModel.get(i);
            Partition dataPartition = storageDevice.getDataPartition();
            try {
                if ((dataPartition == null)
                        || dataPartition.isActivePersistencePartition()) {
                    canRepair = false;
                    break;
                }
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                canRepair = false;
                break;
            }
        }

        int selectionCount = selectedIndices.length;
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, repairStorageDeviceListModel.size());
        repairSelectionCountLabel.setText(countString);

        // update nextButton state
        if ((selectionCount > 0) && canRepair) {
            enableNextButton();
        } else {
            disableNextButton();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        repairButtonGroup = new javax.swing.ButtonGroup();
        isoButtonGroup = new javax.swing.ButtonGroup();
        exchangeButtonGroup = new javax.swing.ButtonGroup();
        installSourceButtonGroup = new javax.swing.ButtonGroup();
        choicePanel = new javax.swing.JPanel();
        choiceLabel = new javax.swing.JLabel();
        buttonGridPanel = new javax.swing.JPanel();
        northWestPanel = new javax.swing.JPanel();
        installButton = new javax.swing.JButton();
        installLabel = new javax.swing.JLabel();
        northEastPanel = new javax.swing.JPanel();
        upgradeButton = new javax.swing.JButton();
        upgradeLabel = new javax.swing.JLabel();
        toISOButton = new javax.swing.JButton();
        repairButton = new javax.swing.JButton();
        executionPanel = new javax.swing.JPanel();
        stepsPanel = new javax.swing.JPanel();
        stepsLabel = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        infoStepLabel = new javax.swing.JLabel();
        selectionLabel = new javax.swing.JLabel();
        executionLabel = new javax.swing.JLabel();
        cardPanel = new javax.swing.JPanel();
        installInfoPanel = new javax.swing.JPanel();
        infoLabel = new javax.swing.JLabel();
        installSelectionPanel = new javax.swing.JPanel();
        installSourcePanel = new javax.swing.JPanel();
        runningSystemSourceRadioButton = new javax.swing.JRadioButton();
        isoSourceRadioButton = new javax.swing.JRadioButton();
        isoSourceTextField = new javax.swing.JTextField();
        isoSourceFileChooserButton = new javax.swing.JButton();
        installTargetPanel = new javax.swing.JPanel();
        installSelectionHeaderLabel = new javax.swing.JLabel();
        installShowHarddisksCheckBox = new javax.swing.JCheckBox();
        installSelectionCardPanel = new javax.swing.JPanel();
        installListPanel = new javax.swing.JPanel();
        installStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        installStorageDeviceList = new javax.swing.JList();
        exchangeDefinitionLabel = new javax.swing.JLabel();
        dataDefinitionLabel = new javax.swing.JLabel();
        bootDefinitionLabel = new javax.swing.JLabel();
        systemDefinitionLabel = new javax.swing.JLabel();
        installSelectionCountLabel = new javax.swing.JLabel();
        installListTabbedPane = new javax.swing.JTabbedPane();
        exchangePartitionPanel = new javax.swing.JPanel();
        exchangePartitionSizeLabel = new javax.swing.JLabel();
        exchangePartitionSizeSlider = new javax.swing.JSlider();
        exchangePartitionSizeTextField = new javax.swing.JTextField();
        exchangePartitionSizeUnitLabel = new javax.swing.JLabel();
        exchangePartitionBottomPanel = new javax.swing.JPanel();
        exchangePartitionLabelPanel = new javax.swing.JPanel();
        exchangePartitionLabel = new javax.swing.JLabel();
        exchangePartitionTextField = new javax.swing.JTextField();
        autoNumberPanel = new javax.swing.JPanel();
        autoNumberPatternLabel = new javax.swing.JLabel();
        autoNumberPatternTextField = new javax.swing.JTextField();
        autoNumberStartLabel = new javax.swing.JLabel();
        autoNumberStartSpinner = new javax.swing.JSpinner();
        autoNumberIncrementLabel = new javax.swing.JLabel();
        autoNumberIncrementSpinner = new javax.swing.JSpinner();
        exchangePartitionFileSystemPanel = new javax.swing.JPanel();
        exchangePartitionFileSystemLabel = new javax.swing.JLabel();
        exchangePartitionFileSystemComboBox = new javax.swing.JComboBox();
        copyExchangeCheckBox = new javax.swing.JCheckBox();
        dataPartitionPanel = new javax.swing.JPanel();
        dataPartitionFileSystemLabel = new javax.swing.JLabel();
        dataPartitionFilesystemComboBox = new javax.swing.JComboBox();
        dataPartitionModeLabel = new javax.swing.JLabel();
        dataPartitionModeComboBox = new javax.swing.JComboBox();
        copyPersistenceCheckBox = new javax.swing.JCheckBox();
        installNoMediaPanel = new javax.swing.JPanel();
        installNoMediaLabel = new javax.swing.JLabel();
        installTabbedPane = new javax.swing.JTabbedPane();
        installCurrentPanel = new javax.swing.JPanel();
        currentlyInstalledDeviceLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        installCardPanel = new javax.swing.JPanel();
        installIndeterminateProgressPanel = new javax.swing.JPanel();
        installIndeterminateProgressBar = new javax.swing.JProgressBar();
        installCopyPanel = new javax.swing.JPanel();
        installCopyLabel = new javax.swing.JLabel();
        installFileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
        rsyncPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        rsyncPogressBar = new javax.swing.JProgressBar();
        rsyncTimeLabel = new javax.swing.JLabel();
        cpPanel = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        cpPogressBar = new javax.swing.JProgressBar();
        cpFilenameLabel = new JSqueezedLabel();
        cpTimeLabel = new javax.swing.JLabel();
        installReportPanel = new javax.swing.JPanel();
        installationResultsScrollPane = new javax.swing.JScrollPane();
        installationResultsTable = new javax.swing.JTable();
        donePanel = new javax.swing.JPanel();
        doneLabel = new javax.swing.JLabel();
        upgradeInfoPanel = new javax.swing.JPanel();
        upgradeInfoLabel = new javax.swing.JLabel();
        upgradeSelectionPanel = new javax.swing.JPanel();
        upgradeSelectionHeaderLabel = new javax.swing.JLabel();
        upgradeShowHarddisksCheckBox = new javax.swing.JCheckBox();
        upgradeSelectionTabbedPane = new javax.swing.JTabbedPane();
        upgradeSelectionCardPanel = new javax.swing.JPanel();
        upgradeSelectionDeviceListPanel = new javax.swing.JPanel();
        upgradeSelectionCountLabel = new javax.swing.JLabel();
        upgradeStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        upgradeStorageDeviceList = new javax.swing.JList();
        upgradeExchangeDefinitionLabel = new javax.swing.JLabel();
        upgradeDataDefinitionLabel = new javax.swing.JLabel();
        upgradeBootDefinitionLabel = new javax.swing.JLabel();
        upgradeOsDefinitionLabel = new javax.swing.JLabel();
        upgradeNoMediaPanel = new javax.swing.JPanel();
        upgradeNoMediaLabel = new javax.swing.JLabel();
        upgradeDetailsTabbedPane = new javax.swing.JTabbedPane();
        upgradeOptionsPanel = new javax.swing.JPanel();
        upgradeSystemPartitionCheckBox = new javax.swing.JCheckBox();
        reactivateWelcomeCheckBox = new javax.swing.JCheckBox();
        keepPrinterSettingsCheckBox = new javax.swing.JCheckBox();
        removeHiddenFilesCheckBox = new javax.swing.JCheckBox();
        automaticBackupCheckBox = new javax.swing.JCheckBox();
        automaticBackupLabel = new javax.swing.JLabel();
        automaticBackupTextField = new javax.swing.JTextField();
        automaticBackupButton = new javax.swing.JButton();
        automaticBackupRemoveCheckBox = new javax.swing.JCheckBox();
        repartitionExchangeOptionsPanel = new javax.swing.JPanel();
        originalExchangeRadioButton = new javax.swing.JRadioButton();
        removeExchangeRadioButton = new javax.swing.JRadioButton();
        resizeExchangeRadioButton = new javax.swing.JRadioButton();
        resizeExchangeTextField = new javax.swing.JTextField();
        resizeExchangeLabel = new javax.swing.JLabel();
        upgradeOverwritePanel = new javax.swing.JPanel();
        upgradeMoveUpButton = new javax.swing.JButton();
        upgradeMoveDownButton = new javax.swing.JButton();
        sortAscendingButton = new javax.swing.JButton();
        sortDescendingButton = new javax.swing.JButton();
        upgradeOverwriteScrollPane = new javax.swing.JScrollPane();
        upgradeOverwriteList = new javax.swing.JList();
        upgradeOverwriteAddButton = new javax.swing.JButton();
        upgradeOverwriteEditButton = new javax.swing.JButton();
        upgradeOverwriteRemoveButton = new javax.swing.JButton();
        upgradeOverwriteExportButton = new javax.swing.JButton();
        upgradeOverwriteImportButton = new javax.swing.JButton();
        upgradeTabbedPane = new javax.swing.JTabbedPane();
        upgradePanel = new javax.swing.JPanel();
        currentlyUpgradedDeviceLabel = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        upgradeCardPanel = new javax.swing.JPanel();
        upgradeIndeterminateProgressPanel = new javax.swing.JPanel();
        upgradeIndeterminateProgressBar = new javax.swing.JProgressBar();
        upgradeCopyPanel = new javax.swing.JPanel();
        upgradeCopyLabel = new javax.swing.JLabel();
        upgradeFileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
        upgradeBackupPanel = new javax.swing.JPanel();
        upgradeBackupLabel = new javax.swing.JLabel();
        upgradeBackupProgressLabel = new javax.swing.JLabel();
        upgradeBackupFilenameLabel = new JSqueezedLabel();
        upgradeBackupProgressBar = new javax.swing.JProgressBar();
        upgradeBackupDurationLabel = new javax.swing.JLabel();
        upgradeReportPanel = new javax.swing.JPanel();
        upgradeResultsScrollPane = new javax.swing.JScrollPane();
        upgradeResultsTable = new javax.swing.JTable();
        repairInfoPanel = new javax.swing.JPanel();
        repairInfoLabel = new javax.swing.JLabel();
        repairSelectionPanel = new javax.swing.JPanel();
        repairSelectionHeaderLabel = new javax.swing.JLabel();
        repairShowHarddisksCheckBox = new javax.swing.JCheckBox();
        repairSelectionCardPanel = new javax.swing.JPanel();
        repairNoMediaPanel = new javax.swing.JPanel();
        repairNoMediaLabel = new javax.swing.JLabel();
        repairSelectionDeviceListPanel = new javax.swing.JPanel();
        repairSelectionCountLabel = new javax.swing.JLabel();
        repairStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        repairStorageDeviceList = new javax.swing.JList();
        repairExchangeDefinitionLabel = new javax.swing.JLabel();
        repairDataDefinitionLabel = new javax.swing.JLabel();
        repairOsDefinitionLabel = new javax.swing.JLabel();
        formatDataPartitionRadioButton = new javax.swing.JRadioButton();
        removeFilesRadioButton = new javax.swing.JRadioButton();
        systemFilesCheckBox = new javax.swing.JCheckBox();
        homeDirectoryCheckBox = new javax.swing.JCheckBox();
        repairPanel = new javax.swing.JPanel();
        currentlyRepairedDeviceLabel = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        repairProgressBar = new javax.swing.JProgressBar();
        toISOInfoPanel = new javax.swing.JPanel();
        toISOInfoLabel = new javax.swing.JLabel();
        toISOSelectionPanel = new javax.swing.JPanel();
        tmpDriveInfoLabel = new javax.swing.JLabel();
        toIsoGridBagPanel = new javax.swing.JPanel();
        tmpDirLabel = new javax.swing.JLabel();
        tmpDirTextField = new javax.swing.JTextField();
        tmpDirSelectButton = new javax.swing.JButton();
        freeSpaceLabel = new javax.swing.JLabel();
        freeSpaceTextField = new javax.swing.JTextField();
        writableLabel = new javax.swing.JLabel();
        writableTextField = new javax.swing.JTextField();
        isoLabelLabel = new javax.swing.JLabel();
        isoLabelTextField = new javax.swing.JTextField();
        radioButtonPanel = new javax.swing.JPanel();
        bootMediumRadioButton = new javax.swing.JRadioButton();
        systemMediumRadioButton = new javax.swing.JRadioButton();
        isoOptionsPanel = new javax.swing.JPanel();
        isoDataPartitionModeLabel = new javax.swing.JLabel();
        isoDataPartitionModeComboBox = new javax.swing.JComboBox();
        isoOptionsCardPanel = new javax.swing.JPanel();
        systemMediumPanel = new javax.swing.JPanel();
        showNotUsedDialogCheckBox = new javax.swing.JCheckBox();
        autoStartInstallerCheckBox = new javax.swing.JCheckBox();
        bootMediumPanel = new javax.swing.JPanel();
        toISOProgressPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        toISOProgressBar = new javax.swing.JProgressBar();
        toISODonePanel = new javax.swing.JPanel();
        isoDoneLabel = new javax.swing.JLabel();
        resultsPanel = new javax.swing.JPanel();
        resultsInfoLabel = new javax.swing.JLabel();
        resultsTitledPanel = new javax.swing.JPanel();
        resultsScrollPane = new javax.swing.JScrollPane();
        resultsTable = new javax.swing.JTable();
        previousButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        setTitle(bundle.getString("DLCopySwingGUI.title")); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(new java.awt.CardLayout());

        choicePanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                choicePanelComponentShown(evt);
            }
        });
        choicePanel.setLayout(new java.awt.GridBagLayout());

        choiceLabel.setFont(choiceLabel.getFont().deriveFont(choiceLabel.getFont().getSize()+5f));
        choiceLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        choiceLabel.setText(bundle.getString("DLCopySwingGUI.choiceLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        choicePanel.add(choiceLabel, gridBagConstraints);

        buttonGridPanel.setLayout(new java.awt.GridBagLayout());

        northWestPanel.setLayout(new java.awt.GridBagLayout());

        installButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dvd2usb.png"))); // NOI18N
        installButton.setText(bundle.getString("DLCopySwingGUI.installButton.text")); // NOI18N
        installButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        installButton.setName("installButton"); // NOI18N
        installButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        installButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                installButtonActionPerformed(evt);
            }
        });
        installButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                installButtonFocusGained(evt);
            }
        });
        installButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                installButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        northWestPanel.add(installButton, gridBagConstraints);

        installLabel.setFont(installLabel.getFont().deriveFont(installLabel.getFont().getStyle() & ~java.awt.Font.BOLD, installLabel.getFont().getSize()-1));
        installLabel.setText(bundle.getString("DLCopySwingGUI.installLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        northWestPanel.add(installLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
        buttonGridPanel.add(northWestPanel, gridBagConstraints);

        northEastPanel.setLayout(new java.awt.GridBagLayout());

        upgradeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbupgrade.png"))); // NOI18N
        upgradeButton.setText(bundle.getString("DLCopySwingGUI.upgradeButton.text")); // NOI18N
        upgradeButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        upgradeButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        upgradeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeButtonActionPerformed(evt);
            }
        });
        upgradeButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                upgradeButtonFocusGained(evt);
            }
        });
        upgradeButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                upgradeButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        northEastPanel.add(upgradeButton, gridBagConstraints);

        upgradeLabel.setFont(upgradeLabel.getFont().deriveFont(upgradeLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeLabel.getFont().getSize()-1));
        upgradeLabel.setText(bundle.getString("DLCopySwingGUI.upgradeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        northEastPanel.add(upgradeLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        buttonGridPanel.add(northEastPanel, gridBagConstraints);

        toISOButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        toISOButton.setText(bundle.getString("DLCopySwingGUI.toISOButton.text")); // NOI18N
        toISOButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        toISOButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toISOButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toISOButtonActionPerformed(evt);
            }
        });
        toISOButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                toISOButtonFocusGained(evt);
            }
        });
        toISOButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                toISOButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 5);
        buttonGridPanel.add(toISOButton, gridBagConstraints);

        repairButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/lernstick_repair.png"))); // NOI18N
        repairButton.setText(bundle.getString("DLCopySwingGUI.repairButton.text")); // NOI18N
        repairButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        repairButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        repairButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                repairButtonActionPerformed(evt);
            }
        });
        repairButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                repairButtonFocusGained(evt);
            }
        });
        repairButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                repairButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 0);
        buttonGridPanel.add(repairButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        choicePanel.add(buttonGridPanel, gridBagConstraints);

        getContentPane().add(choicePanel, "choicePanel");

        stepsPanel.setBackground(java.awt.Color.white);
        stepsPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        stepsLabel.setText(bundle.getString("DLCopySwingGUI.stepsLabel.text")); // NOI18N

        infoStepLabel.setFont(infoStepLabel.getFont().deriveFont(infoStepLabel.getFont().getStyle() | java.awt.Font.BOLD));
        infoStepLabel.setText(bundle.getString("DLCopySwingGUI.infoStepLabel.text")); // NOI18N

        selectionLabel.setFont(selectionLabel.getFont().deriveFont(selectionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        selectionLabel.setForeground(java.awt.Color.darkGray);
        selectionLabel.setText(bundle.getString("DLCopySwingGUI.selectionLabel.text")); // NOI18N

        executionLabel.setFont(executionLabel.getFont().deriveFont(executionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        executionLabel.setForeground(java.awt.Color.darkGray);
        executionLabel.setText(bundle.getString("Installation_Label")); // NOI18N

        javax.swing.GroupLayout stepsPanelLayout = new javax.swing.GroupLayout(stepsPanel);
        stepsPanel.setLayout(stepsPanelLayout);
        stepsPanelLayout.setHorizontalGroup(
            stepsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(stepsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(stepsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 104, Short.MAX_VALUE)
                    .addGroup(stepsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(stepsLabel)
                        .addComponent(infoStepLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(selectionLabel))
                    .addComponent(executionLabel))
                .addContainerGap())
        );
        stepsPanelLayout.setVerticalGroup(
            stepsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(stepsPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(stepsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(15, 15, 15)
                .addComponent(infoStepLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(selectionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(executionLabel)
                .addContainerGap(302, Short.MAX_VALUE))
        );

        cardPanel.setLayout(new java.awt.CardLayout());

        installInfoPanel.setLayout(new java.awt.GridBagLayout());

        infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dvd2usb.png"))); // NOI18N
        infoLabel.setText(bundle.getString("DLCopySwingGUI.infoLabel.text")); // NOI18N
        infoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        infoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        installInfoPanel.add(infoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(installInfoPanel, "installInfoPanel");

        installSelectionPanel.setLayout(new java.awt.GridBagLayout());

        installSourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.installSourcePanel.border.title"))); // NOI18N
        installSourcePanel.setLayout(new java.awt.GridBagLayout());

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
        installSourcePanel.add(runningSystemSourceRadioButton, gridBagConstraints);

        installSourceButtonGroup.add(isoSourceRadioButton);
        isoSourceRadioButton.setText(bundle.getString("DLCopySwingGUI.isoSourceRadioButton.text")); // NOI18N
        isoSourceRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isoSourceRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        installSourcePanel.add(isoSourceRadioButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        installSourcePanel.add(isoSourceTextField, gridBagConstraints);

        isoSourceFileChooserButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-open-folder.png"))); // NOI18N
        isoSourceFileChooserButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        isoSourceFileChooserButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                isoSourceFileChooserButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        installSourcePanel.add(isoSourceFileChooserButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        installSelectionPanel.add(installSourcePanel, gridBagConstraints);

        installTargetPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.installTargetPanel.border.title"))); // NOI18N
        installTargetPanel.setLayout(new java.awt.GridBagLayout());

        installSelectionHeaderLabel.setFont(installSelectionHeaderLabel.getFont().deriveFont(installSelectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        installSelectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        installSelectionHeaderLabel.setText(bundle.getString("Select_Install_Target_Storage_Media")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        installTargetPanel.add(installSelectionHeaderLabel, gridBagConstraints);

        installShowHarddisksCheckBox.setFont(installShowHarddisksCheckBox.getFont().deriveFont(installShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, installShowHarddisksCheckBox.getFont().getSize()-1));
        installShowHarddisksCheckBox.setText(bundle.getString("DLCopySwingGUI.installShowHarddisksCheckBox.text")); // NOI18N
        installShowHarddisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                installShowHarddisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        installTargetPanel.add(installShowHarddisksCheckBox, gridBagConstraints);

        installSelectionCardPanel.setLayout(new java.awt.CardLayout());

        installStorageDeviceList.setName("installStorageDeviceList"); // NOI18N
        installStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                installStorageDeviceListValueChanged(evt);
            }
        });
        installStorageDeviceListScrollPane.setViewportView(installStorageDeviceList);

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.exchangeDefinitionLabel.text")); // NOI18N

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.dataDefinitionLabel.text")); // NOI18N

        bootDefinitionLabel.setFont(bootDefinitionLabel.getFont().deriveFont(bootDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, bootDefinitionLabel.getFont().getSize()-1));
        bootDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dark_blue_box.png"))); // NOI18N
        bootDefinitionLabel.setText(bundle.getString("Boot_Definition")); // NOI18N

        systemDefinitionLabel.setFont(systemDefinitionLabel.getFont().deriveFont(systemDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, systemDefinitionLabel.getFont().getSize()-1));
        systemDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        systemDefinitionLabel.setText(bundle.getString("System_Definition")); // NOI18N

        installSelectionCountLabel.setText(bundle.getString("Selection_Count")); // NOI18N

        exchangePartitionPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionSizeLabel.setText(bundle.getString("Size")); // NOI18N
        exchangePartitionSizeLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        exchangePartitionPanel.add(exchangePartitionSizeLabel, gridBagConstraints);

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
        exchangePartitionPanel.add(exchangePartitionSizeSlider, gridBagConstraints);

        exchangePartitionSizeTextField.setColumns(7);
        exchangePartitionSizeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        exchangePartitionSizeTextField.setEnabled(false);
        exchangePartitionSizeTextField.setName("exchangePartitionSizeTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        exchangePartitionPanel.add(exchangePartitionSizeTextField, gridBagConstraints);

        exchangePartitionSizeUnitLabel.setText(bundle.getString("DLCopySwingGUI.exchangePartitionSizeUnitLabel.text")); // NOI18N
        exchangePartitionSizeUnitLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        exchangePartitionPanel.add(exchangePartitionSizeUnitLabel, gridBagConstraints);

        exchangePartitionBottomPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionLabelPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        exchangePartitionLabel.setText(bundle.getString("Label")); // NOI18N
        exchangePartitionLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        exchangePartitionLabelPanel.add(exchangePartitionLabel, gridBagConstraints);

        exchangePartitionTextField.setColumns(11);
        exchangePartitionTextField.setText(bundle.getString("Exchange")); // NOI18N
        exchangePartitionTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        exchangePartitionLabelPanel.add(exchangePartitionTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        exchangePartitionBottomPanel.add(exchangePartitionLabelPanel, gridBagConstraints);

        autoNumberPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.autoNumberPanel.border.title"))); // NOI18N
        autoNumberPanel.setLayout(new java.awt.GridBagLayout());

        autoNumberPatternLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberPatternLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberPatternLabel.text")); // NOI18N
        autoNumberPatternLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 0);
        autoNumberPanel.add(autoNumberPatternLabel, gridBagConstraints);

        autoNumberPatternTextField.setColumns(11);
        autoNumberPatternTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 0);
        autoNumberPanel.add(autoNumberPatternTextField, gridBagConstraints);

        autoNumberStartLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberStartLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberStartLabel.text")); // NOI18N
        autoNumberStartLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 2, 0);
        autoNumberPanel.add(autoNumberStartLabel, gridBagConstraints);

        autoNumberStartSpinner.setModel(new javax.swing.SpinnerNumberModel(1, null, null, 1));
        autoNumberStartSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 0);
        autoNumberPanel.add(autoNumberStartSpinner, gridBagConstraints);

        autoNumberIncrementLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberIncrementLabel.setText(bundle.getString("DLCopySwingGUI.autoNumberIncrementLabel.text")); // NOI18N
        autoNumberIncrementLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 2, 0);
        autoNumberPanel.add(autoNumberIncrementLabel, gridBagConstraints);

        autoNumberIncrementSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        autoNumberIncrementSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 5);
        autoNumberPanel.add(autoNumberIncrementSpinner, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        exchangePartitionBottomPanel.add(autoNumberPanel, gridBagConstraints);

        exchangePartitionFileSystemPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionFileSystemLabel.setText(bundle.getString("DLCopySwingGUI.exchangePartitionFileSystemLabel.text")); // NOI18N
        exchangePartitionFileSystemLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        exchangePartitionFileSystemPanel.add(exchangePartitionFileSystemLabel, gridBagConstraints);

        exchangePartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "exFAT", "FAT32", "NTFS" }));
        exchangePartitionFileSystemComboBox.setToolTipText(bundle.getString("DLCopySwingGUI.exchangePartitionFileSystemComboBox.toolTipText")); // NOI18N
        exchangePartitionFileSystemComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        exchangePartitionFileSystemPanel.add(exchangePartitionFileSystemComboBox, gridBagConstraints);

        copyExchangeCheckBox.setText(bundle.getString("DLCopySwingGUI.copyExchangeCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 10);
        exchangePartitionFileSystemPanel.add(copyExchangeCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        exchangePartitionBottomPanel.add(exchangePartitionFileSystemPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 0);
        exchangePartitionPanel.add(exchangePartitionBottomPanel, gridBagConstraints);

        installListTabbedPane.addTab(bundle.getString("DLCopySwingGUI.exchangePartitionPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png")), exchangePartitionPanel); // NOI18N

        dataPartitionPanel.setLayout(new java.awt.GridBagLayout());

        dataPartitionFileSystemLabel.setText(bundle.getString("DLCopySwingGUI.dataPartitionFileSystemLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 0);
        dataPartitionPanel.add(dataPartitionFileSystemLabel, gridBagConstraints);

        dataPartitionFilesystemComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "ext2", "ext3", "ext4" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 10);
        dataPartitionPanel.add(dataPartitionFilesystemComboBox, gridBagConstraints);

        dataPartitionModeLabel.setText(bundle.getString("DLCopySwingGUI.dataPartitionModeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        dataPartitionPanel.add(dataPartitionModeLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 10);
        dataPartitionPanel.add(dataPartitionModeComboBox, gridBagConstraints);

        copyPersistenceCheckBox.setText(bundle.getString("Copy_Data_Partition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
        dataPartitionPanel.add(copyPersistenceCheckBox, gridBagConstraints);

        installListTabbedPane.addTab(bundle.getString("DLCopySwingGUI.dataPartitionPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png")), dataPartitionPanel); // NOI18N

        javax.swing.GroupLayout installListPanelLayout = new javax.swing.GroupLayout(installListPanel);
        installListPanel.setLayout(installListPanelLayout);
        installListPanelLayout.setHorizontalGroup(
            installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(installListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(installListPanelLayout.createSequentialGroup()
                        .addComponent(installSelectionCountLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(installListTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(installStorageDeviceListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(installListPanelLayout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addGroup(installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dataDefinitionLabel)
                            .addComponent(exchangeDefinitionLabel)
                            .addGroup(installListPanelLayout.createSequentialGroup()
                                .addComponent(bootDefinitionLabel)
                                .addGap(18, 18, 18)
                                .addComponent(systemDefinitionLabel)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 214, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        installListPanelLayout.setVerticalGroup(
            installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, installListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(installSelectionCountLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(installStorageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exchangeDefinitionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataDefinitionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(systemDefinitionLabel)
                    .addComponent(bootDefinitionLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(installListTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        installSelectionCardPanel.add(installListPanel, "installListPanel");

        installNoMediaPanel.setLayout(new java.awt.GridBagLayout());

        installNoMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        installNoMediaLabel.setText(bundle.getString("Insert_Media")); // NOI18N
        installNoMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        installNoMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        installNoMediaPanel.add(installNoMediaLabel, new java.awt.GridBagConstraints());

        installSelectionCardPanel.add(installNoMediaPanel, "installNoMediaPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        installTargetPanel.add(installSelectionCardPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        installSelectionPanel.add(installTargetPanel, gridBagConstraints);

        cardPanel.add(installSelectionPanel, "installSelectionPanel");

        installCurrentPanel.setLayout(new java.awt.GridBagLayout());

        currentlyInstalledDeviceLabel.setText(bundle.getString("Install_Device_Info")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        installCurrentPanel.add(currentlyInstalledDeviceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        installCurrentPanel.add(jSeparator3, gridBagConstraints);

        installCardPanel.setLayout(new java.awt.CardLayout());

        installIndeterminateProgressPanel.setLayout(new java.awt.GridBagLayout());

        installIndeterminateProgressBar.setIndeterminate(true);
        installIndeterminateProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        installIndeterminateProgressBar.setString(bundle.getString("DLCopySwingGUI.installIndeterminateProgressBar.string")); // NOI18N
        installIndeterminateProgressBar.setStringPainted(true);
        installIndeterminateProgressPanel.add(installIndeterminateProgressBar, new java.awt.GridBagConstraints());

        installCardPanel.add(installIndeterminateProgressPanel, "installIndeterminateProgressPanel");

        installCopyPanel.setLayout(new java.awt.GridBagLayout());

        installCopyLabel.setText(bundle.getString("DLCopySwingGUI.installCopyLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        installCopyPanel.add(installCopyLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        installCopyPanel.add(installFileCopierPanel, gridBagConstraints);

        installCardPanel.add(installCopyPanel, "installCopyPanel");

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
        installCurrentPanel.add(installCardPanel, gridBagConstraints);

        installTabbedPane.addTab(bundle.getString("DLCopySwingGUI.installCurrentPanel.TabConstraints.tabTitle"), installCurrentPanel); // NOI18N

        installReportPanel.setLayout(new java.awt.GridBagLayout());

        installationResultsTable.setAutoCreateRowSorter(true);
        installationResultsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        installationResultsScrollPane.setViewportView(installationResultsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        installReportPanel.add(installationResultsScrollPane, gridBagConstraints);

        installTabbedPane.addTab(bundle.getString("Installation_Report"), installReportPanel); // NOI18N

        cardPanel.add(installTabbedPane, "installTabbedPane");

        doneLabel.setFont(doneLabel.getFont().deriveFont(doneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        doneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        doneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbpendrive_unmount_tux.png"))); // NOI18N
        doneLabel.setText(bundle.getString("Installation_Done_Message_From_Removable_Boot_Device")); // NOI18N
        doneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        doneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout donePanelLayout = new javax.swing.GroupLayout(donePanel);
        donePanel.setLayout(donePanelLayout);
        donePanelLayout.setHorizontalGroup(
            donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 746, Short.MAX_VALUE)
            .addGroup(donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(donePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(doneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 722, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        donePanelLayout.setVerticalGroup(
            donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 430, Short.MAX_VALUE)
            .addGroup(donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(donePanelLayout.createSequentialGroup()
                    .addGap(83, 83, 83)
                    .addComponent(doneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(181, Short.MAX_VALUE)))
        );

        cardPanel.add(donePanel, "donePanel");

        upgradeInfoPanel.setLayout(new java.awt.GridBagLayout());

        upgradeInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbupgrade.png"))); // NOI18N
        upgradeInfoLabel.setText(bundle.getString("DLCopySwingGUI.upgradeInfoLabel.text")); // NOI18N
        upgradeInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        upgradeInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        upgradeInfoPanel.add(upgradeInfoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(upgradeInfoPanel, "upgradeInfoPanel");

        upgradeSelectionPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                upgradeSelectionPanelComponentShown(evt);
            }
        });
        upgradeSelectionPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSelectionHeaderLabel.setFont(upgradeSelectionHeaderLabel.getFont().deriveFont(upgradeSelectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        upgradeSelectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        upgradeSelectionHeaderLabel.setText(bundle.getString("Select_Upgrade_Target_Storage_Media")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        upgradeSelectionPanel.add(upgradeSelectionHeaderLabel, gridBagConstraints);

        upgradeShowHarddisksCheckBox.setFont(upgradeShowHarddisksCheckBox.getFont().deriveFont(upgradeShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeShowHarddisksCheckBox.getFont().getSize()-1));
        upgradeShowHarddisksCheckBox.setText(bundle.getString("DLCopySwingGUI.upgradeShowHarddisksCheckBox.text")); // NOI18N
        upgradeShowHarddisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                upgradeShowHarddisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        upgradeSelectionPanel.add(upgradeShowHarddisksCheckBox, gridBagConstraints);

        upgradeSelectionCardPanel.setLayout(new java.awt.CardLayout());

        upgradeSelectionDeviceListPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSelectionCountLabel.setText(bundle.getString("Selection_Count")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        upgradeSelectionDeviceListPanel.add(upgradeSelectionCountLabel, gridBagConstraints);

        upgradeStorageDeviceList.setName("storageDeviceList"); // NOI18N
        upgradeStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                upgradeStorageDeviceListValueChanged(evt);
            }
        });
        upgradeStorageDeviceListScrollPane.setViewportView(upgradeStorageDeviceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeStorageDeviceListScrollPane, gridBagConstraints);

        upgradeExchangeDefinitionLabel.setFont(upgradeExchangeDefinitionLabel.getFont().deriveFont(upgradeExchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeExchangeDefinitionLabel.getFont().getSize()-1));
        upgradeExchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        upgradeExchangeDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.upgradeExchangeDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeExchangeDefinitionLabel, gridBagConstraints);

        upgradeDataDefinitionLabel.setFont(upgradeDataDefinitionLabel.getFont().deriveFont(upgradeDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeDataDefinitionLabel.getFont().getSize()-1));
        upgradeDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        upgradeDataDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.upgradeDataDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeDataDefinitionLabel, gridBagConstraints);

        upgradeBootDefinitionLabel.setFont(upgradeBootDefinitionLabel.getFont().deriveFont(upgradeBootDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeBootDefinitionLabel.getFont().getSize()-1));
        upgradeBootDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dark_blue_box.png"))); // NOI18N
        upgradeBootDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBootDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 0);
        upgradeSelectionDeviceListPanel.add(upgradeBootDefinitionLabel, gridBagConstraints);

        upgradeOsDefinitionLabel.setFont(upgradeOsDefinitionLabel.getFont().deriveFont(upgradeOsDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeOsDefinitionLabel.getFont().getSize()-1));
        upgradeOsDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        upgradeOsDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.upgradeOsDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 10);
        upgradeSelectionDeviceListPanel.add(upgradeOsDefinitionLabel, gridBagConstraints);

        upgradeSelectionCardPanel.add(upgradeSelectionDeviceListPanel, "upgradeSelectionDeviceListPanel");

        upgradeNoMediaPanel.setLayout(new java.awt.GridBagLayout());

        upgradeNoMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        upgradeNoMediaLabel.setText(bundle.getString("Insert_Media")); // NOI18N
        upgradeNoMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        upgradeNoMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        upgradeNoMediaPanel.add(upgradeNoMediaLabel, new java.awt.GridBagConstraints());

        upgradeSelectionCardPanel.add(upgradeNoMediaPanel, "upgradeNoMediaPanel");

        upgradeSelectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeSelectionCardPanel.TabConstraints.tabTitle"), upgradeSelectionCardPanel); // NOI18N

        upgradeSystemPartitionCheckBox.setSelected(true);
        upgradeSystemPartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.upgradeSystemPartitionCheckBox.text")); // NOI18N

        reactivateWelcomeCheckBox.setSelected(true);
        reactivateWelcomeCheckBox.setText(bundle.getString("DLCopySwingGUI.reactivateWelcomeCheckBox.text")); // NOI18N

        keepPrinterSettingsCheckBox.setSelected(true);
        keepPrinterSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepPrinterSettingsCheckBox.text")); // NOI18N

        removeHiddenFilesCheckBox.setText(bundle.getString("DLCopySwingGUI.removeHiddenFilesCheckBox.text")); // NOI18N

        automaticBackupCheckBox.setText(bundle.getString("DLCopySwingGUI.automaticBackupCheckBox.text")); // NOI18N
        automaticBackupCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                automaticBackupCheckBoxItemStateChanged(evt);
            }
        });

        automaticBackupLabel.setText(bundle.getString("DLCopySwingGUI.automaticBackupLabel.text")); // NOI18N
        automaticBackupLabel.setEnabled(false);

        automaticBackupTextField.setEnabled(false);

        automaticBackupButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-open-folder.png"))); // NOI18N
        automaticBackupButton.setEnabled(false);
        automaticBackupButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        automaticBackupButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                automaticBackupButtonActionPerformed(evt);
            }
        });

        automaticBackupRemoveCheckBox.setText(bundle.getString("DLCopySwingGUI.automaticBackupRemoveCheckBox.text")); // NOI18N
        automaticBackupRemoveCheckBox.setEnabled(false);

        repartitionExchangeOptionsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.repartitionExchangeOptionsPanel.border.title"))); // NOI18N

        exchangeButtonGroup.add(originalExchangeRadioButton);
        originalExchangeRadioButton.setSelected(true);
        originalExchangeRadioButton.setText(bundle.getString("DLCopySwingGUI.originalExchangeRadioButton.text")); // NOI18N

        exchangeButtonGroup.add(removeExchangeRadioButton);
        removeExchangeRadioButton.setText(bundle.getString("DLCopySwingGUI.removeExchangeRadioButton.text")); // NOI18N

        exchangeButtonGroup.add(resizeExchangeRadioButton);
        resizeExchangeRadioButton.setText(bundle.getString("DLCopySwingGUI.resizeExchangeRadioButton.text")); // NOI18N

        resizeExchangeTextField.setColumns(4);
        resizeExchangeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);

        resizeExchangeLabel.setText(bundle.getString("DLCopySwingGUI.resizeExchangeLabel.text")); // NOI18N

        javax.swing.GroupLayout repartitionExchangeOptionsPanelLayout = new javax.swing.GroupLayout(repartitionExchangeOptionsPanel);
        repartitionExchangeOptionsPanel.setLayout(repartitionExchangeOptionsPanelLayout);
        repartitionExchangeOptionsPanelLayout.setHorizontalGroup(
            repartitionExchangeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repartitionExchangeOptionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(repartitionExchangeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(removeExchangeRadioButton)
                    .addComponent(originalExchangeRadioButton)
                    .addGroup(repartitionExchangeOptionsPanelLayout.createSequentialGroup()
                        .addComponent(resizeExchangeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(resizeExchangeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(resizeExchangeLabel)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        repartitionExchangeOptionsPanelLayout.setVerticalGroup(
            repartitionExchangeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repartitionExchangeOptionsPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(removeExchangeRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(originalExchangeRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(repartitionExchangeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(resizeExchangeRadioButton)
                    .addComponent(resizeExchangeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(resizeExchangeLabel)))
        );

        javax.swing.GroupLayout upgradeOptionsPanelLayout = new javax.swing.GroupLayout(upgradeOptionsPanel);
        upgradeOptionsPanel.setLayout(upgradeOptionsPanelLayout);
        upgradeOptionsPanelLayout.setHorizontalGroup(
            upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(repartitionExchangeOptionsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                        .addGroup(upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                                .addGroup(upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(automaticBackupLabel)
                                    .addComponent(automaticBackupCheckBox))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(automaticBackupTextField)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED))
                            .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                                .addGap(14, 14, 14)
                                .addComponent(automaticBackupRemoveCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addComponent(automaticBackupButton))
                    .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                        .addGroup(upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(upgradeSystemPartitionCheckBox)
                            .addComponent(reactivateWelcomeCheckBox)
                            .addComponent(keepPrinterSettingsCheckBox)
                            .addComponent(removeHiddenFilesCheckBox))
                        .addGap(0, 241, Short.MAX_VALUE)))
                .addContainerGap())
        );
        upgradeOptionsPanelLayout.setVerticalGroup(
            upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                .addGroup(upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(upgradeSystemPartitionCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(reactivateWelcomeCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(keepPrinterSettingsCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeHiddenFilesCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(automaticBackupCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(upgradeOptionsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(automaticBackupButton)
                            .addComponent(automaticBackupTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(automaticBackupRemoveCheckBox))
                    .addGroup(upgradeOptionsPanelLayout.createSequentialGroup()
                        .addGap(127, 127, 127)
                        .addComponent(automaticBackupLabel)))
                .addGap(18, 18, 18)
                .addComponent(repartitionExchangeOptionsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        upgradeDetailsTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeOptionsPanel.TabConstraints.tabTitle"), upgradeOptionsPanel); // NOI18N

        upgradeOverwritePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        upgradeMoveUpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-up.png"))); // NOI18N
        upgradeMoveUpButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeMoveUpButton.toolTipText")); // NOI18N
        upgradeMoveUpButton.setEnabled(false);
        upgradeMoveUpButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeMoveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeMoveUpButtonActionPerformed(evt);
            }
        });

        upgradeMoveDownButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-down.png"))); // NOI18N
        upgradeMoveDownButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeMoveDownButton.toolTipText")); // NOI18N
        upgradeMoveDownButton.setEnabled(false);
        upgradeMoveDownButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeMoveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeMoveDownButtonActionPerformed(evt);
            }
        });

        sortAscendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/view-sort-ascending.png"))); // NOI18N
        sortAscendingButton.setToolTipText(bundle.getString("DLCopySwingGUI.sortAscendingButton.toolTipText")); // NOI18N
        sortAscendingButton.setEnabled(false);
        sortAscendingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        sortAscendingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortAscendingButtonActionPerformed(evt);
            }
        });

        sortDescendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/view-sort-descending.png"))); // NOI18N
        sortDescendingButton.setToolTipText(bundle.getString("DLCopySwingGUI.sortDescendingButton.toolTipText")); // NOI18N
        sortDescendingButton.setEnabled(false);
        sortDescendingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        sortDescendingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortDescendingButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                upgradeOverwriteListMouseClicked(evt);
            }
        });
        upgradeOverwriteList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                upgradeOverwriteListValueChanged(evt);
            }
        });
        upgradeOverwriteScrollPane.setViewportView(upgradeOverwriteList);

        upgradeOverwriteAddButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/list-add.png"))); // NOI18N
        upgradeOverwriteAddButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteAddButton.toolTipText")); // NOI18N
        upgradeOverwriteAddButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteAddButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteEditButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-edit.png"))); // NOI18N
        upgradeOverwriteEditButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteEditButton.toolTipText")); // NOI18N
        upgradeOverwriteEditButton.setEnabled(false);
        upgradeOverwriteEditButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteEditButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteRemoveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/list-remove.png"))); // NOI18N
        upgradeOverwriteRemoveButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteRemoveButton.toolTipText")); // NOI18N
        upgradeOverwriteRemoveButton.setEnabled(false);
        upgradeOverwriteRemoveButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteRemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteRemoveButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteExportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-export.png"))); // NOI18N
        upgradeOverwriteExportButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteExportButton.toolTipText")); // NOI18N
        upgradeOverwriteExportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteExportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteExportButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteImportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-import.png"))); // NOI18N
        upgradeOverwriteImportButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteImportButton.toolTipText")); // NOI18N
        upgradeOverwriteImportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteImportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteImportButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout upgradeOverwritePanelLayout = new javax.swing.GroupLayout(upgradeOverwritePanel);
        upgradeOverwritePanel.setLayout(upgradeOverwritePanelLayout);
        upgradeOverwritePanelLayout.setHorizontalGroup(
            upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, upgradeOverwritePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(upgradeMoveUpButton)
                        .addComponent(upgradeMoveDownButton))
                    .addComponent(sortAscendingButton)
                    .addComponent(sortDescendingButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upgradeOverwriteScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 646, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(upgradeOverwriteAddButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteEditButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteRemoveButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteExportButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteImportButton, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        upgradeOverwritePanelLayout.setVerticalGroup(
            upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeOverwritePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(upgradeOverwritePanelLayout.createSequentialGroup()
                        .addComponent(upgradeOverwriteAddButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upgradeOverwriteEditButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upgradeOverwriteRemoveButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upgradeOverwriteExportButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upgradeOverwriteImportButton)
                        .addGap(0, 163, Short.MAX_VALUE))
                    .addGroup(upgradeOverwritePanelLayout.createSequentialGroup()
                        .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(upgradeOverwriteScrollPane)
                            .addGroup(upgradeOverwritePanelLayout.createSequentialGroup()
                                .addComponent(upgradeMoveUpButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(upgradeMoveDownButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sortAscendingButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(sortDescendingButton)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())))
        );

        upgradeDetailsTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeOverwritePanel.TabConstraints.tabTitle"), upgradeOverwritePanel); // NOI18N

        upgradeSelectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeDetailsTabbedPane.TabConstraints.tabTitle"), upgradeDetailsTabbedPane); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        upgradeSelectionPanel.add(upgradeSelectionTabbedPane, gridBagConstraints);

        cardPanel.add(upgradeSelectionPanel, "upgradeSelectionPanel");

        upgradePanel.setLayout(new java.awt.GridBagLayout());

        currentlyUpgradedDeviceLabel.setFont(currentlyUpgradedDeviceLabel.getFont().deriveFont(currentlyUpgradedDeviceLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        currentlyUpgradedDeviceLabel.setText(bundle.getString("Upgrade_Device_Info")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        upgradePanel.add(currentlyUpgradedDeviceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        upgradePanel.add(jSeparator4, gridBagConstraints);

        upgradeCardPanel.setLayout(new java.awt.CardLayout());

        upgradeIndeterminateProgressPanel.setLayout(new java.awt.GridBagLayout());

        upgradeIndeterminateProgressBar.setIndeterminate(true);
        upgradeIndeterminateProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        upgradeIndeterminateProgressBar.setString(bundle.getString("DLCopySwingGUI.upgradeIndeterminateProgressBar.string")); // NOI18N
        upgradeIndeterminateProgressBar.setStringPainted(true);
        upgradeIndeterminateProgressPanel.add(upgradeIndeterminateProgressBar, new java.awt.GridBagConstraints());

        upgradeCardPanel.add(upgradeIndeterminateProgressPanel, "upgradeIndeterminateProgressPanel");

        upgradeCopyPanel.setLayout(new java.awt.GridBagLayout());

        upgradeCopyLabel.setText(bundle.getString("DLCopySwingGUI.upgradeCopyLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        upgradeCopyPanel.add(upgradeCopyLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        upgradeCopyPanel.add(upgradeFileCopierPanel, gridBagConstraints);

        upgradeCardPanel.add(upgradeCopyPanel, "upgradeCopyPanel");

        upgradeBackupPanel.setLayout(new java.awt.GridBagLayout());

        upgradeBackupLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        upgradeBackupPanel.add(upgradeBackupLabel, gridBagConstraints);

        upgradeBackupProgressLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupProgressLabel.text")); // NOI18N
        upgradeBackupProgressLabel.setName("upgradeBackupProgressLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        upgradeBackupPanel.add(upgradeBackupProgressLabel, gridBagConstraints);

        upgradeBackupFilenameLabel.setFont(upgradeBackupFilenameLabel.getFont().deriveFont(upgradeBackupFilenameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeBackupFilenameLabel.getFont().getSize()-1));
        upgradeBackupFilenameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        upgradeBackupFilenameLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupFilenameLabel.text")); // NOI18N
        upgradeBackupFilenameLabel.setName("upgradeBackupFilenameLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        upgradeBackupPanel.add(upgradeBackupFilenameLabel, gridBagConstraints);

        upgradeBackupProgressBar.setIndeterminate(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        upgradeBackupPanel.add(upgradeBackupProgressBar, gridBagConstraints);

        upgradeBackupDurationLabel.setText(bundle.getString("DLCopySwingGUI.upgradeBackupDurationLabel.text")); // NOI18N
        upgradeBackupDurationLabel.setName("upgradeBackupDurationLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        upgradeBackupPanel.add(upgradeBackupDurationLabel, gridBagConstraints);

        upgradeCardPanel.add(upgradeBackupPanel, "upgradeBackupPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        upgradePanel.add(upgradeCardPanel, gridBagConstraints);

        upgradeTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradePanel.TabConstraints.tabTitle"), upgradePanel); // NOI18N

        upgradeReportPanel.setLayout(new java.awt.GridBagLayout());

        upgradeResultsTable.setAutoCreateRowSorter(true);
        upgradeResultsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        upgradeResultsScrollPane.setViewportView(upgradeResultsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        upgradeReportPanel.add(upgradeResultsScrollPane, gridBagConstraints);

        upgradeTabbedPane.addTab(bundle.getString("Upgrade_Report"), upgradeReportPanel); // NOI18N

        cardPanel.add(upgradeTabbedPane, "upgradeTabbedPane");

        repairInfoPanel.setLayout(new java.awt.GridBagLayout());

        repairInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/lernstick_repair.png"))); // NOI18N
        repairInfoLabel.setText(bundle.getString("DLCopySwingGUI.repairInfoLabel.text")); // NOI18N
        repairInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        repairInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        repairInfoPanel.add(repairInfoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(repairInfoPanel, "repairInfoPanel");

        repairSelectionPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                repairSelectionPanelComponentShown(evt);
            }
        });
        repairSelectionPanel.setLayout(new java.awt.GridBagLayout());

        repairSelectionHeaderLabel.setFont(repairSelectionHeaderLabel.getFont().deriveFont(repairSelectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        repairSelectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        repairSelectionHeaderLabel.setText(bundle.getString("DLCopySwingGUI.repairSelectionHeaderLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        repairSelectionPanel.add(repairSelectionHeaderLabel, gridBagConstraints);

        repairShowHarddisksCheckBox.setFont(repairShowHarddisksCheckBox.getFont().deriveFont(repairShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, repairShowHarddisksCheckBox.getFont().getSize()-1));
        repairShowHarddisksCheckBox.setText(bundle.getString("DLCopySwingGUI.repairShowHarddisksCheckBox.text")); // NOI18N
        repairShowHarddisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                repairShowHarddisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        repairSelectionPanel.add(repairShowHarddisksCheckBox, gridBagConstraints);

        repairSelectionCardPanel.setLayout(new java.awt.CardLayout());

        repairNoMediaPanel.setLayout(new java.awt.GridBagLayout());

        repairNoMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        repairNoMediaLabel.setText(bundle.getString("DLCopySwingGUI.repairNoMediaLabel.text")); // NOI18N
        repairNoMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        repairNoMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        repairNoMediaPanel.add(repairNoMediaLabel, new java.awt.GridBagConstraints());

        repairSelectionCardPanel.add(repairNoMediaPanel, "repairNoMediaPanel");

        repairSelectionCountLabel.setText(bundle.getString("DLCopySwingGUI.repairSelectionCountLabel.text")); // NOI18N

        repairStorageDeviceList.setName("storageDeviceList"); // NOI18N
        repairStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                repairStorageDeviceListValueChanged(evt);
            }
        });
        repairStorageDeviceListScrollPane.setViewportView(repairStorageDeviceList);

        repairExchangeDefinitionLabel.setFont(repairExchangeDefinitionLabel.getFont().deriveFont(repairExchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, repairExchangeDefinitionLabel.getFont().getSize()-1));
        repairExchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        repairExchangeDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.repairExchangeDefinitionLabel.text")); // NOI18N

        repairDataDefinitionLabel.setFont(repairDataDefinitionLabel.getFont().deriveFont(repairDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, repairDataDefinitionLabel.getFont().getSize()-1));
        repairDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        repairDataDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.repairDataDefinitionLabel.text")); // NOI18N

        repairOsDefinitionLabel.setFont(repairOsDefinitionLabel.getFont().deriveFont(repairOsDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, repairOsDefinitionLabel.getFont().getSize()-1));
        repairOsDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        repairOsDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.repairOsDefinitionLabel.text")); // NOI18N

        repairButtonGroup.add(formatDataPartitionRadioButton);
        formatDataPartitionRadioButton.setText(bundle.getString("DLCopySwingGUI.formatDataPartitionRadioButton.text")); // NOI18N
        formatDataPartitionRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                formatDataPartitionRadioButtonActionPerformed(evt);
            }
        });

        repairButtonGroup.add(removeFilesRadioButton);
        removeFilesRadioButton.setSelected(true);
        removeFilesRadioButton.setText(bundle.getString("DLCopySwingGUI.removeFilesRadioButton.text")); // NOI18N
        removeFilesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeFilesRadioButtonActionPerformed(evt);
            }
        });

        systemFilesCheckBox.setSelected(true);
        systemFilesCheckBox.setText(bundle.getString("DLCopySwingGUI.systemFilesCheckBox.text")); // NOI18N

        homeDirectoryCheckBox.setText(bundle.getString("DLCopySwingGUI.homeDirectoryCheckBox.text")); // NOI18N

        javax.swing.GroupLayout repairSelectionDeviceListPanelLayout = new javax.swing.GroupLayout(repairSelectionDeviceListPanel);
        repairSelectionDeviceListPanel.setLayout(repairSelectionDeviceListPanelLayout);
        repairSelectionDeviceListPanelLayout.setHorizontalGroup(
            repairSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repairSelectionDeviceListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(repairSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(repairStorageDeviceListScrollPane)
                    .addComponent(repairSelectionCountLabel)
                    .addComponent(repairDataDefinitionLabel)
                    .addComponent(repairExchangeDefinitionLabel)
                    .addComponent(repairOsDefinitionLabel)
                    .addComponent(formatDataPartitionRadioButton)
                    .addComponent(removeFilesRadioButton)
                    .addGroup(repairSelectionDeviceListPanelLayout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(systemFilesCheckBox)
                        .addGap(18, 18, 18)
                        .addComponent(homeDirectoryCheckBox)))
                .addContainerGap())
        );
        repairSelectionDeviceListPanelLayout.setVerticalGroup(
            repairSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repairSelectionDeviceListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(repairSelectionCountLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(repairStorageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 194, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(repairExchangeDefinitionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(repairDataDefinitionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(repairOsDefinitionLabel)
                .addGap(18, 18, 18)
                .addComponent(formatDataPartitionRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(removeFilesRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(repairSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(systemFilesCheckBox)
                    .addComponent(homeDirectoryCheckBox))
                .addContainerGap())
        );

        repairSelectionCardPanel.add(repairSelectionDeviceListPanel, "repairSelectionDeviceListPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        repairSelectionPanel.add(repairSelectionCardPanel, gridBagConstraints);

        cardPanel.add(repairSelectionPanel, "repairSelectionPanel");

        repairPanel.setLayout(new java.awt.GridBagLayout());

        currentlyRepairedDeviceLabel.setFont(currentlyRepairedDeviceLabel.getFont().deriveFont(currentlyRepairedDeviceLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        currentlyRepairedDeviceLabel.setText(bundle.getString("Repair_Device_Info")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        repairPanel.add(currentlyRepairedDeviceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        repairPanel.add(jSeparator5, gridBagConstraints);

        repairProgressBar.setIndeterminate(true);
        repairProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        repairProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weighty = 1.0;
        repairPanel.add(repairProgressBar, gridBagConstraints);

        cardPanel.add(repairPanel, "repairPanel");

        toISOInfoPanel.setLayout(new java.awt.GridBagLayout());

        toISOInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        toISOInfoLabel.setText(bundle.getString("DLCopySwingGUI.toISOInfoLabel.text")); // NOI18N
        toISOInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        toISOInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toISOInfoPanel.add(toISOInfoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(toISOInfoPanel, "toISOInfoPanel");

        tmpDriveInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tmpDriveInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/file_temporary.png"))); // NOI18N
        tmpDriveInfoLabel.setText(bundle.getString("DLCopySwingGUI.tmpDriveInfoLabel.text")); // NOI18N
        tmpDriveInfoLabel.setIconTextGap(15);

        toIsoGridBagPanel.setLayout(new java.awt.GridBagLayout());

        tmpDirLabel.setText(bundle.getString("DLCopySwingGUI.tmpDirLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        toIsoGridBagPanel.add(tmpDirLabel, gridBagConstraints);

        tmpDirTextField.setColumns(20);
        tmpDirTextField.setText("/media/");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        toIsoGridBagPanel.add(tmpDirTextField, gridBagConstraints);

        tmpDirSelectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/fileopen.png"))); // NOI18N
        tmpDirSelectButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        tmpDirSelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tmpDirSelectButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        toIsoGridBagPanel.add(tmpDirSelectButton, gridBagConstraints);

        freeSpaceLabel.setText(bundle.getString("DLCopySwingGUI.freeSpaceLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 0);
        toIsoGridBagPanel.add(freeSpaceLabel, gridBagConstraints);

        freeSpaceTextField.setEditable(false);
        freeSpaceTextField.setColumns(20);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        toIsoGridBagPanel.add(freeSpaceTextField, gridBagConstraints);

        writableLabel.setText(bundle.getString("DLCopySwingGUI.writableLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 0);
        toIsoGridBagPanel.add(writableLabel, gridBagConstraints);

        writableTextField.setEditable(false);
        writableTextField.setColumns(20);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        toIsoGridBagPanel.add(writableTextField, gridBagConstraints);

        isoLabelLabel.setText(bundle.getString("DLCopySwingGUI.isoLabelLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 3, 0, 0);
        toIsoGridBagPanel.add(isoLabelLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 0);
        toIsoGridBagPanel.add(isoLabelTextField, gridBagConstraints);

        radioButtonPanel.setLayout(new java.awt.GridBagLayout());

        isoButtonGroup.add(bootMediumRadioButton);
        bootMediumRadioButton.setText(bundle.getString("DLCopySwingGUI.bootMediumRadioButton.text")); // NOI18N
        bootMediumRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bootMediumRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        radioButtonPanel.add(bootMediumRadioButton, gridBagConstraints);

        isoButtonGroup.add(systemMediumRadioButton);
        systemMediumRadioButton.setSelected(true);
        systemMediumRadioButton.setText(bundle.getString("DLCopySwingGUI.systemMediumRadioButton.text")); // NOI18N
        systemMediumRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                systemMediumRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        radioButtonPanel.add(systemMediumRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        toIsoGridBagPanel.add(radioButtonPanel, gridBagConstraints);

        isoOptionsPanel.setLayout(new java.awt.GridBagLayout());

        isoDataPartitionModeLabel.setText(bundle.getString("DLCopySwingGUI.isoDataPartitionModeLabel.text")); // NOI18N
        isoOptionsPanel.add(isoDataPartitionModeLabel, new java.awt.GridBagConstraints());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        isoOptionsPanel.add(isoDataPartitionModeComboBox, gridBagConstraints);

        isoOptionsCardPanel.setLayout(new java.awt.CardLayout());

        systemMediumPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.systemMediumPanel.border.title"))); // NOI18N

        showNotUsedDialogCheckBox.setText(bundle.getString("DLCopySwingGUI.showNotUsedDialogCheckBox.text")); // NOI18N

        autoStartInstallerCheckBox.setText(bundle.getString("DLCopySwingGUI.autoStartInstallerCheckBox.text")); // NOI18N

        javax.swing.GroupLayout systemMediumPanelLayout = new javax.swing.GroupLayout(systemMediumPanel);
        systemMediumPanel.setLayout(systemMediumPanelLayout);
        systemMediumPanelLayout.setHorizontalGroup(
            systemMediumPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(showNotUsedDialogCheckBox)
            .addComponent(autoStartInstallerCheckBox)
        );
        systemMediumPanelLayout.setVerticalGroup(
            systemMediumPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(systemMediumPanelLayout.createSequentialGroup()
                .addComponent(showNotUsedDialogCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(autoStartInstallerCheckBox))
        );

        isoOptionsCardPanel.add(systemMediumPanel, "systemMediumPanel");

        javax.swing.GroupLayout bootMediumPanelLayout = new javax.swing.GroupLayout(bootMediumPanel);
        bootMediumPanel.setLayout(bootMediumPanelLayout);
        bootMediumPanelLayout.setHorizontalGroup(
            bootMediumPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 319, Short.MAX_VALUE)
        );
        bootMediumPanelLayout.setVerticalGroup(
            bootMediumPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 67, Short.MAX_VALUE)
        );

        isoOptionsCardPanel.add(bootMediumPanel, "bootMediumPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        isoOptionsPanel.add(isoOptionsCardPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        toIsoGridBagPanel.add(isoOptionsPanel, gridBagConstraints);

        javax.swing.GroupLayout toISOSelectionPanelLayout = new javax.swing.GroupLayout(toISOSelectionPanel);
        toISOSelectionPanel.setLayout(toISOSelectionPanelLayout);
        toISOSelectionPanelLayout.setHorizontalGroup(
            toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tmpDriveInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(toIsoGridBagPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        toISOSelectionPanelLayout.setVerticalGroup(
            toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tmpDriveInfoLabel)
                .addGap(18, 18, 18)
                .addComponent(toIsoGridBagPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 293, Short.MAX_VALUE)
                .addContainerGap())
        );

        cardPanel.add(toISOSelectionPanel, "toISOSelectionPanel");

        toISOProgressPanel.setLayout(new java.awt.GridBagLayout());

        jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        toISOProgressPanel.add(jLabel6, gridBagConstraints);

        toISOProgressBar.setIndeterminate(true);
        toISOProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        toISOProgressBar.setString(bundle.getString("DLCopySwingGUI.toISOProgressBar.string")); // NOI18N
        toISOProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(30, 30, 0, 30);
        toISOProgressPanel.add(toISOProgressBar, gridBagConstraints);

        cardPanel.add(toISOProgressPanel, "toISOProgressPanel");

        isoDoneLabel.setFont(isoDoneLabel.getFont().deriveFont(isoDoneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        isoDoneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        isoDoneLabel.setText(bundle.getString("DLCopySwingGUI.isoDoneLabel.text")); // NOI18N
        isoDoneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout toISODonePanelLayout = new javax.swing.GroupLayout(toISODonePanel);
        toISODonePanel.setLayout(toISODonePanelLayout);
        toISODonePanelLayout.setHorizontalGroup(
            toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 746, Short.MAX_VALUE)
            .addGroup(toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(toISODonePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(isoDoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 722, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        toISODonePanelLayout.setVerticalGroup(
            toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 430, Short.MAX_VALUE)
            .addGroup(toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(toISODonePanelLayout.createSequentialGroup()
                    .addGap(83, 83, 83)
                    .addComponent(isoDoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(116, Short.MAX_VALUE)))
        );

        cardPanel.add(toISODonePanel, "toISODonePanel");

        resultsInfoLabel.setText(bundle.getString("Installation_Done_Message_From_Removable_Boot_Device")); // NOI18N

        resultsTitledPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Installation_Report"))); // NOI18N
        resultsTitledPanel.setLayout(new java.awt.GridBagLayout());

        resultsTable.setAutoCreateRowSorter(true);
        resultsTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        resultsScrollPane.setViewportView(resultsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resultsTitledPanel.add(resultsScrollPane, gridBagConstraints);

        javax.swing.GroupLayout resultsPanelLayout = new javax.swing.GroupLayout(resultsPanel);
        resultsPanel.setLayout(resultsPanelLayout);
        resultsPanelLayout.setHorizontalGroup(
            resultsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(resultsTitledPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 746, Short.MAX_VALUE)
            .addGroup(resultsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(resultsInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addContainerGap())
        );
        resultsPanelLayout.setVerticalGroup(
            resultsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(resultsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(resultsInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(resultsTitledPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 337, Short.MAX_VALUE))
        );

        cardPanel.add(resultsPanel, "resultsPanel");

        previousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/previous.png"))); // NOI18N
        previousButton.setText(bundle.getString("DLCopySwingGUI.previousButton.text")); // NOI18N
        previousButton.setName("previousButton"); // NOI18N
        previousButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                previousButtonFocusGained(evt);
            }
        });
        previousButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previousButtonActionPerformed(evt);
            }
        });
        previousButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                previousButtonKeyPressed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/next.png"))); // NOI18N
        nextButton.setText(bundle.getString("DLCopySwingGUI.nextButton.text")); // NOI18N
        nextButton.setName("nextButton"); // NOI18N
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });
        nextButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                nextButtonFocusGained(evt);
            }
        });
        nextButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                nextButtonKeyPressed(evt);
            }
        });

        javax.swing.GroupLayout executionPanelLayout = new javax.swing.GroupLayout(executionPanel);
        executionPanel.setLayout(executionPanelLayout);
        executionPanelLayout.setHorizontalGroup(
            executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(executionPanelLayout.createSequentialGroup()
                .addGroup(executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(executionPanelLayout.createSequentialGroup()
                        .addComponent(stepsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cardPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, executionPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(previousButton)
                        .addGap(18, 18, 18)
                        .addComponent(nextButton)))
                .addContainerGap())
            .addComponent(jSeparator2)
        );
        executionPanelLayout.setVerticalGroup(
            executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, executionPanelLayout.createSequentialGroup()
                .addGroup(executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(executionPanelLayout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addComponent(cardPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                    .addComponent(stepsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nextButton)
                    .addComponent(previousButton))
                .addContainerGap())
        );

        getContentPane().add(executionPanel, "executionPanel");
    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        switch (state) {

            case INSTALL_INFORMATION:
                switchToInstallSelection();
                break;

            case ISO_INFORMATION:
                setLabelHighlighted(infoStepLabel, false);
                setLabelHighlighted(selectionLabel, true);
                setLabelHighlighted(executionLabel, false);
                showCard(cardPanel, "toISOSelectionPanel");
                checkFreeSpaceTextField();
                enableNextButton();
                state = State.ISO_SELECTION;
                break;

            case UPGRADE_INFORMATION:
                switchToUpgradeSelection();
                break;

            case REPAIR_INFORMATION:
                switchToRepairSelection();
                break;

            case INSTALL_SELECTION:
                try {
                    checkInstallSelection();
                } catch (IOException | DBusException ex) {
                    LOGGER.log(Level.SEVERE,
                            "checking the selected usb flash drive failed", ex);
                }
                break;

            case ISO_SELECTION:
                try {
                    if (systemMediumRadioButton.isSelected()
                            && !isUnmountedPersistenceAvailable()) {
                        return;
                    }
                } catch (IOException | DBusException ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                }
                state = State.ISO_INSTALLATION;
                setLabelHighlighted(infoStepLabel, false);
                setLabelHighlighted(selectionLabel, false);
                setLabelHighlighted(executionLabel, true);
                showCard(cardPanel, "toISOProgressPanel");
                previousButton.setEnabled(false);
                nextButton.setEnabled(false);
                DataPartitionMode dataPartitionMode
                        = getDataPartitionMode(isoDataPartitionModeComboBox);
                new IsoCreator(this, systemSource,
                        bootMediumRadioButton.isSelected(),
                        tmpDirTextField.getText(), dataPartitionMode,
                        showNotUsedDialogCheckBox.isSelected(),
                        autoStartInstallerCheckBox.isSelected(),
                        isoLabelTextField.getText()).execute();
                break;

            case UPGRADE_SELECTION:
                upgrade();
                break;

            case REPAIR_SELECTION:
                repair();
                break;

            case INSTALLATION:
            case UPGRADE:
            case ISO_INSTALLATION:
            case REPAIR:
                exitProgram();
                break;

            default:
                LOGGER.log(Level.WARNING, "unsupported state {0}", state);
        }
    }//GEN-LAST:event_nextButtonActionPerformed

    private void previousButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousButtonActionPerformed
        switch (state) {

            case INSTALL_INFORMATION:
            case UPGRADE_INFORMATION:
            case REPAIR_INFORMATION:
            case ISO_INFORMATION:
                getRootPane().setDefaultButton(installButton);
                installButton.requestFocusInWindow();
                globalShow("choicePanel");
                break;

            case INSTALL_SELECTION:
                switchToUSBInformation();
                break;

            case UPGRADE_SELECTION:
                switchToUpgradeInformation();
                break;

            case ISO_SELECTION:
                switchToISOInformation();
                break;

            case REPAIR_SELECTION:
                switchToRepairInformation();
                break;

            case ISO_INSTALLATION:
                getRootPane().setDefaultButton(installButton);
                installButton.requestFocusInWindow();
                globalShow("choicePanel");
                resetNextButton();
                break;

            case INSTALLATION:
                switchToInstallSelection();
                resetNextButton();
                break;

            case UPGRADE:
                switchToUpgradeInformation();
                resetNextButton();
                break;

            case REPAIR:
                switchToRepairInformation();
                resetNextButton();
                break;

            default:
                LOGGER.log(Level.WARNING, "unsupported state: {0}", state);
        }
    }//GEN-LAST:event_previousButtonActionPerformed

    private void installStorageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_installStorageDeviceListValueChanged
        updateInstallSelectionCountAndExchangeInfo();
    }//GEN-LAST:event_installStorageDeviceListValueChanged

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        exitProgram();
    }//GEN-LAST:event_formWindowClosing

    private void exchangePartitionSizeSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_exchangePartitionSizeSliderStateChanged
        if (!textFieldTriggeredSliderChange) {
            // update value text field
            exchangePartitionSizeTextField.setText(
                    String.valueOf(exchangePartitionSizeSlider.getValue()));
        }
        // repaint partition list
        installStorageDeviceList.repaint();
}//GEN-LAST:event_exchangePartitionSizeSliderStateChanged

    private void exchangePartitionSizeSliderComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_exchangePartitionSizeSliderComponentResized
        updateInstallSelectionCountAndExchangeInfo();
    }//GEN-LAST:event_exchangePartitionSizeSliderComponentResized

    private void nextButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_nextButtonFocusGained
        getRootPane().setDefaultButton(nextButton);
    }//GEN-LAST:event_nextButtonFocusGained

    private void previousButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_previousButtonFocusGained
        getRootPane().setDefaultButton(previousButton);
    }//GEN-LAST:event_previousButtonFocusGained

    private void installButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_installButtonActionPerformed
        globalShow("executionPanel");
        switchToUSBInformation();
    }//GEN-LAST:event_installButtonActionPerformed

    private void toISOButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toISOButtonActionPerformed
        // We don't need to check for unmounted persistence here
        // because a valid use case is to produce a simple boot image from
        // a full system image.
        // In this use case there is no persistence partition available...
        globalShow("executionPanel");
        switchToISOInformation();
    }//GEN-LAST:event_toISOButtonActionPerformed

    private void tmpDirSelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tmpDirSelectButtonActionPerformed
        selectDirectory(tmpDirTextField);
}//GEN-LAST:event_tmpDirSelectButtonActionPerformed

    private void installButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_installButtonFocusGained
        getRootPane().setDefaultButton(installButton);
    }//GEN-LAST:event_installButtonFocusGained

    private void toISOButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_toISOButtonFocusGained
        getRootPane().setDefaultButton(toISOButton);
    }//GEN-LAST:event_toISOButtonFocusGained

    private void installShowHarddisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_installShowHarddisksCheckBoxItemStateChanged
        new InstallStorageDeviceListUpdater(this, installStorageDeviceList,
                installStorageDeviceListModel,
                installShowHarddisksCheckBox.isSelected()).execute();
    }//GEN-LAST:event_installShowHarddisksCheckBoxItemStateChanged

    private void upgradeButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_upgradeButtonFocusGained
        getRootPane().setDefaultButton(upgradeButton);
    }//GEN-LAST:event_upgradeButtonFocusGained

    private void installButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_installButtonKeyPressed
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_DOWN:
                toISOButton.requestFocusInWindow();
                break;
            case KeyEvent.VK_RIGHT:
                upgradeButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_installButtonKeyPressed

    private void upgradeButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_upgradeButtonKeyPressed
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                installButton.requestFocusInWindow();
                break;
            case KeyEvent.VK_DOWN:
                repairButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_upgradeButtonKeyPressed

    private void choicePanelComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_choicePanelComponentShown
        installButton.requestFocusInWindow();
    }//GEN-LAST:event_choicePanelComponentShown

    private void toISOButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_toISOButtonKeyPressed
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
                installButton.requestFocusInWindow();
                break;
            case KeyEvent.VK_RIGHT:
                repairButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_toISOButtonKeyPressed

    private void nextButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_nextButtonKeyPressed
        if (KeyEvent.VK_LEFT == evt.getKeyCode()) {
            previousButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_nextButtonKeyPressed

    private void previousButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_previousButtonKeyPressed
        if (KeyEvent.VK_RIGHT == evt.getKeyCode()) {
            nextButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_previousButtonKeyPressed

    private void upgradeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeButtonActionPerformed
        globalShow("executionPanel");
        switchToUpgradeInformation();
    }//GEN-LAST:event_upgradeButtonActionPerformed

    private void upgradeStorageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_upgradeStorageDeviceListValueChanged
        updateUpgradeSelectionCountAndNextButton();
    }//GEN-LAST:event_upgradeStorageDeviceListValueChanged

    private void upgradeSelectionPanelComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_upgradeSelectionPanelComponentShown
        new UpgradeStorageDeviceListUpdater(this, upgradeStorageDeviceList,
                upgradeStorageDeviceListModel,
                upgradeShowHarddisksCheckBox.isSelected()).execute();
    }//GEN-LAST:event_upgradeSelectionPanelComponentShown

private void upgradeShowHarddisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeShowHarddisksCheckBoxItemStateChanged
    new UpgradeStorageDeviceListUpdater(this, upgradeStorageDeviceList,
            upgradeStorageDeviceListModel,
            upgradeShowHarddisksCheckBox.isSelected()).execute();
}//GEN-LAST:event_upgradeShowHarddisksCheckBoxItemStateChanged

    private void upgradeOverwriteAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteAddButtonActionPerformed
        addPathToList(JFileChooser.FILES_AND_DIRECTORIES,
                upgradeOverwriteListModel);
        // adding elements could enable the "move down" button
        // therefore we trigger a "spurious" selection update event here
        upgradeOverwriteListValueChanged(null);
    }//GEN-LAST:event_upgradeOverwriteAddButtonActionPerformed

    private void upgradeOverwriteEditButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteEditButtonActionPerformed
        editPathListEntry(upgradeOverwriteList,
                JFileChooser.FILES_AND_DIRECTORIES);
    }//GEN-LAST:event_upgradeOverwriteEditButtonActionPerformed

    private void upgradeOverwriteRemoveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteRemoveButtonActionPerformed
        removeSelectedListEntries(upgradeOverwriteList);
    }//GEN-LAST:event_upgradeOverwriteRemoveButtonActionPerformed

    private void upgradeOverwriteListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_upgradeOverwriteListMouseClicked
        if (evt.getClickCount() == 2) {
            editPathListEntry(upgradeOverwriteList,
                    JFileChooser.FILES_AND_DIRECTORIES);
        }
    }//GEN-LAST:event_upgradeOverwriteListMouseClicked

    private void upgradeOverwriteListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_upgradeOverwriteListValueChanged
        int[] selectedIndices = upgradeOverwriteList.getSelectedIndices();
        boolean selected = selectedIndices.length > 0;
        upgradeMoveUpButton.setEnabled(selected && (selectedIndices[0] != 0));
        if (selected) {
            int lastSelectionIndex
                    = selectedIndices[selectedIndices.length - 1];
            int lastListIndex = upgradeOverwriteListModel.getSize() - 1;
            upgradeMoveDownButton.setEnabled(
                    lastSelectionIndex != lastListIndex);
        } else {
            upgradeMoveDownButton.setEnabled(false);
        }
        upgradeOverwriteEditButton.setEnabled(selectedIndices.length == 1);
        upgradeOverwriteRemoveButton.setEnabled(selected);
    }//GEN-LAST:event_upgradeOverwriteListValueChanged

    private void automaticBackupCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_automaticBackupCheckBoxItemStateChanged
        boolean automaticBackup = automaticBackupCheckBox.isSelected();
        automaticBackupLabel.setEnabled(automaticBackup);
        automaticBackupTextField.setEnabled(automaticBackup);
        automaticBackupButton.setEnabled(automaticBackup);
        automaticBackupRemoveCheckBox.setEnabled(automaticBackup);
        updateUpgradeSelectionCountAndNextButton();
    }//GEN-LAST:event_automaticBackupCheckBoxItemStateChanged

    private void automaticBackupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_automaticBackupButtonActionPerformed
        String selectedPath = automaticBackupTextField.getText();
        SelectBackupDirectoryDialog dialog = new SelectBackupDirectoryDialog(
                this, null, selectedPath, false);
        if (dialog.showDialog() == JOptionPane.OK_OPTION) {
            automaticBackupTextField.setText(dialog.getSelectedPath());
        }
    }//GEN-LAST:event_automaticBackupButtonActionPerformed

    private void repairButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_repairButtonActionPerformed
        globalShow("executionPanel");
        switchToRepairInformation();
    }//GEN-LAST:event_repairButtonActionPerformed

    private void repairButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_repairButtonFocusGained
        getRootPane().setDefaultButton(repairButton);
    }//GEN-LAST:event_repairButtonFocusGained

    private void repairButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_repairButtonKeyPressed
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
                upgradeButton.requestFocusInWindow();
                break;
            case KeyEvent.VK_LEFT:
                toISOButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_repairButtonKeyPressed

    private void repairStorageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_repairStorageDeviceListValueChanged
        updateRepairSelectionCountAndNextButton();
    }//GEN-LAST:event_repairStorageDeviceListValueChanged

    private void repairSelectionPanelComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_repairSelectionPanelComponentShown
        new RepairStorageDeviceListUpdater(this, repairStorageDeviceList,
                repairStorageDeviceListModel,
                repairShowHarddisksCheckBox.isSelected(),
                systemSource.getDeviceName()).execute();
    }//GEN-LAST:event_repairSelectionPanelComponentShown

    private void formatDataPartitionRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_formatDataPartitionRadioButtonActionPerformed
        updateRepairButtonState();
    }//GEN-LAST:event_formatDataPartitionRadioButtonActionPerformed

    private void removeFilesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeFilesRadioButtonActionPerformed
        updateRepairButtonState();
    }//GEN-LAST:event_removeFilesRadioButtonActionPerformed

    private void upgradeMoveUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeMoveUpButtonActionPerformed
        int selectedIndices[] = upgradeOverwriteList.getSelectedIndices();
        upgradeOverwriteList.clearSelection();
        for (int selectedIndex : selectedIndices) {
            // swap values with previous index
            int previousIndex = selectedIndex - 1;
            String previousValue = upgradeOverwriteListModel.get(previousIndex);
            String value = upgradeOverwriteListModel.get(selectedIndex);
            upgradeOverwriteListModel.set(previousIndex, value);
            upgradeOverwriteListModel.set(selectedIndex, previousValue);
            upgradeOverwriteList.addSelectionInterval(previousIndex, previousIndex);
        }
    }//GEN-LAST:event_upgradeMoveUpButtonActionPerformed

    private void upgradeMoveDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeMoveDownButtonActionPerformed
        int selectedIndices[] = upgradeOverwriteList.getSelectedIndices();
        upgradeOverwriteList.clearSelection();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            // swap values with next index
            int selectedIndex = selectedIndices[i];
            int nextIndex = selectedIndex + 1;
            String nextValue = upgradeOverwriteListModel.get(nextIndex);
            String value = upgradeOverwriteListModel.get(selectedIndex);
            upgradeOverwriteListModel.set(nextIndex, value);
            upgradeOverwriteListModel.set(selectedIndex, nextValue);
            upgradeOverwriteList.addSelectionInterval(nextIndex, nextIndex);
        }
    }//GEN-LAST:event_upgradeMoveDownButtonActionPerformed

    private void sortAscendingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortAscendingButtonActionPerformed
        sortList(true);
    }//GEN-LAST:event_sortAscendingButtonActionPerformed

    private void sortDescendingButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sortDescendingButtonActionPerformed
        sortList(false);
    }//GEN-LAST:event_sortDescendingButtonActionPerformed

    private void repairShowHarddisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_repairShowHarddisksCheckBoxItemStateChanged
        new RepairStorageDeviceListUpdater(this, repairStorageDeviceList,
                repairStorageDeviceListModel,
                repairShowHarddisksCheckBox.isSelected(),
                systemSource.getDeviceName()).execute();
    }//GEN-LAST:event_repairShowHarddisksCheckBoxItemStateChanged

    private void upgradeOverwriteExportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteExportButtonActionPerformed
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
    }//GEN-LAST:event_upgradeOverwriteExportButtonActionPerformed

    private void upgradeOverwriteImportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteImportButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            upgradeOverwriteListModel.clear();
            File selectedFile = fileChooser.getSelectedFile();
            try (FileReader fileReader = new FileReader(selectedFile)) {
                try (BufferedReader bufferedReader
                        = new BufferedReader(fileReader)) {
                    for (String line = bufferedReader.readLine(); line != null;
                            line = bufferedReader.readLine()) {
                        upgradeOverwriteListModel.addElement(line);
                    }
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }
    }//GEN-LAST:event_upgradeOverwriteImportButtonActionPerformed

    private void bootMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bootMediumRadioButtonActionPerformed
        showMediumPanel();
    }//GEN-LAST:event_bootMediumRadioButtonActionPerformed

    private void systemMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemMediumRadioButtonActionPerformed
        showMediumPanel();
    }//GEN-LAST:event_systemMediumRadioButtonActionPerformed

    private void isoSourceFileChooserButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isoSourceFileChooserButtonActionPerformed
        File currentFile = new File(isoSourceTextField.getText());
        String currentFileDir = currentFile.getParentFile().getPath();
        JFileChooser fileChooser = new JFileChooser(currentFileDir);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fileChooser.getSelectedFile().getPath();
            isoSourceTextField.setText(selectedPath);
        }
        // TODO:
        //  - check if selected file is really a Debian Live ISO
        //  - update installation source info (size, etc.)
    }//GEN-LAST:event_isoSourceFileChooserButtonActionPerformed

    private void runningSystemSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runningSystemSourceRadioButtonActionPerformed
        updateInstallSourceGUI();
    }//GEN-LAST:event_runningSystemSourceRadioButtonActionPerformed

    private void isoSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isoSourceRadioButtonActionPerformed
        updateInstallSourceGUI();
    }//GEN-LAST:event_isoSourceRadioButtonActionPerformed

    private void setSpinnerColums(JSpinner spinner, int columns) {
        JComponent editor = spinner.getEditor();
        JFormattedTextField tf
                = ((JSpinner.DefaultEditor) editor).getTextField();
        tf.setColumns(columns);
    }

    private void updateInstallSourceGUI() {
        boolean isoSource = isoSourceRadioButton.isSelected();
        isoSourceTextField.setEnabled(isoSource);
        isoSourceFileChooserButton.setEnabled(isoSource);
    }

    private void playNotifySound() {
        URL url = getClass().getResource("/ch/fhnw/dlcopy/KDE_Notify.wav");
        AudioClip clip = Applet.newAudioClip(url);
        clip.play();
    }

    private void switchToUpgradeSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.UPGRADE_SELECTION;
        showCard(cardPanel, "upgradeSelectionPanel");
        enableNextButton();
    }

    private void showMediumPanel() {
        CardLayout cardLayout = (CardLayout) isoOptionsCardPanel.getLayout();
        if (systemMediumRadioButton.isSelected()) {
            cardLayout.show(isoOptionsCardPanel, "systemMediumPanel");
        } else {
            cardLayout.show(isoOptionsCardPanel, "bootMediumPanel");
        }
    }

    private DataPartitionMode getDataPartitionMode(JComboBox comboBox) {
        DataPartitionMode dataPartitionMode = null;
        String comboBoxItemText = (String) comboBox.getSelectedItem();
        if (comboBoxItemText.equals(STRINGS.getString("Not_Used"))) {
            dataPartitionMode = DataPartitionMode.NOT_USED;
        } else if (comboBoxItemText.equals(STRINGS.getString("Read_Only"))) {
            dataPartitionMode = DataPartitionMode.READ_ONLY;
        } else if (comboBoxItemText.equals(STRINGS.getString("Read_Write"))) {
            dataPartitionMode = DataPartitionMode.READ_WRITE;
        } else {
            LOGGER.log(Level.WARNING, "unsupported data partition mode: {0}",
                    comboBoxItemText);
        }
        return dataPartitionMode;
    }

    private void resetNextButton() {
        nextButton.setIcon(new ImageIcon(
                getClass().getResource("/ch/fhnw/dlcopy/icons/next.png")));
        nextButton.setText(
                STRINGS.getString("DLCopySwingGUI.nextButton.text"));
    }

    private void repair() {
        // some sanity checks
        if (removeFilesRadioButton.isSelected()
                && !systemFilesCheckBox.isSelected()
                && !homeDirectoryCheckBox.isSelected()) {
            showErrorMessage(STRINGS.getString("Select_Files_To_Remove"));
            return;
        }
        // final warning
        int result = JOptionPane.showConfirmDialog(this,
                STRINGS.getString("Final_Repair_Warning"),
                STRINGS.getString("Warning"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        state = State.REPAIR;

        batchCounter = 0;
        resultsList = new ArrayList<>();
        int[] selectedIndices = repairStorageDeviceList.getSelectedIndices();
        List<StorageDevice> deviceList = new ArrayList<>();
        for (int i : selectedIndices) {
            deviceList.add(repairStorageDeviceListModel.get(i));
        }
        // TODO: using dataPartitionFilesystemComboBox.getSelectedItem() here is
        // ugly because the input field it is not visible when upgrading
        String dataPartitionFileSystem
                = dataPartitionFilesystemComboBox.getSelectedItem().toString();

        new Repairer(this, deviceList,
                formatDataPartitionRadioButton.isSelected(),
                dataPartitionFileSystem, homeDirectoryCheckBox.isSelected(),
                systemFilesCheckBox.isSelected()).execute();
    }

    private void sortList(boolean ascending) {
        // remember selection before sorting
        List selectedValues = upgradeOverwriteList.getSelectedValuesList();

        // sort
        List<String> list = new ArrayList<>();
        Enumeration enumeration = upgradeOverwriteListModel.elements();
        while (enumeration.hasMoreElements()) {
            list.add((String) enumeration.nextElement());
        }
        if (ascending) {
            Collections.sort(list);
        } else {
            Collections.sort(list, Collections.reverseOrder());
        }

        // refill list with sorted values
        upgradeOverwriteListModel.removeAllElements();
        for (String string : list) {
            upgradeOverwriteListModel.addElement(string);
        }

        // restore original selection
        for (Object selectedValue : selectedValues) {
            int selectedIndex
                    = upgradeOverwriteListModel.indexOf(selectedValue);
            upgradeOverwriteList.addSelectionInterval(
                    selectedIndex, selectedIndex);
        }
    }

    private void handleListDataEvent(ListDataEvent e) {
        if (e.getSource() == upgradeOverwriteListModel) {
            boolean sortable = upgradeOverwriteListModel.getSize() > 1;
            sortAscendingButton.setEnabled(sortable);
            sortDescendingButton.setEnabled(sortable);
        }
    }

    private void updateRepairButtonState() {
        boolean selected = removeFilesRadioButton.isSelected();
        systemFilesCheckBox.setEnabled(selected);
        homeDirectoryCheckBox.setEnabled(selected);
    }

    private void removeSelectedListEntries(JList list) {
        int[] selectedIndices = list.getSelectedIndices();
        DefaultListModel model = (DefaultListModel) list.getModel();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            model.remove(selectedIndices[i]);
        }
    }

    private void addPathToList(int selectionMode, DefaultListModel listModel) {
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
        fileChooser.addPropertyChangeListener(
                new java.beans.PropertyChangeListener() {
            @Override
            public void propertyChange(
                    java.beans.PropertyChangeEvent evt) {
                fileChooser.setFileHidingEnabled(
                        fileChooser.getFileFilter()
                        == NO_HIDDEN_FILES_FILTER);
                fileChooser.rescanCurrentDirectory();
            }
        });
        fileChooser.addChoosableFileFilter(NO_HIDDEN_FILES_FILTER);
        fileChooser.setFileFilter(NO_HIDDEN_FILES_FILTER);
    }

    private void removeStorageDevice(String udisksLine) {
        // the device was just removed, so we can not use getStorageDevice()
        // here...
        String[] tokens = udisksLine.split("/");
        final String device = tokens[tokens.length - 1];
        LOGGER.log(Level.INFO, "removed device: {0}", device);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                DefaultListModel listModel;
                switch (state) {
                    case INSTALL_SELECTION:
                        listModel = installStorageDeviceListModel;
                        break;
                    case UPGRADE_SELECTION:
                        listModel = upgradeStorageDeviceListModel;
                        break;
                    case REPAIR_SELECTION:
                        listModel = repairStorageDeviceListModel;
                        break;
                    default:
                        LOGGER.log(Level.WARNING,
                                "Unsupported state: {0}", state);
                        return;
                }

                for (int i = 0, size = listModel.getSize(); i < size; i++) {
                    StorageDevice storageDevice
                            = (StorageDevice) listModel.get(i);
                    if (storageDevice.getDevice().equals(device)) {
                        listModel.remove(i);
                        LOGGER.log(Level.INFO,
                                "removed from storage device list: {0}",
                                device);
                        switch (state) {
                            case INSTALL_SELECTION:
                                installStorageDeviceListChanged();
                                break;
                            case UPGRADE_SELECTION:
                                upgradeStorageDeviceListChanged();
                        }
                        break; // for
                    }
                }
            }
        });
    }

    private long getMaxStorageDeviceSize(ListModel listModel) {
        long maxSize = 0;
        for (int i = 0, size = listModel.getSize(); i < size; i++) {
            StorageDevice storageDevice
                    = (StorageDevice) listModel.getElementAt(i);
            long deviceSize = storageDevice.getSize();
            if (deviceSize > maxSize) {
                maxSize = deviceSize;
            }
        }
        LOGGER.log(Level.INFO, "maxSize = {0}", maxSize);
        return maxSize;
    }

    private void editPathListEntry(JList list, int selectionMode) {
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
            DefaultListModel model = (DefaultListModel) list.getModel();
            model.set(list.getSelectedIndex(), newPath);
        }
    }

    private void selectDirectory(JTextField textField) {
        String path = textField.getText();
        JFileChooser fileChooser = new JFileChooser(path);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fileChooser.getSelectedFile().getPath();
            textField.setText(selectedPath);
        }
    }

    private void exitProgram() {
        // save preferences
        preferences.putBoolean(UPGRADE_SYSTEM_PARTITION,
                upgradeSystemPartitionCheckBox.isSelected());
        preferences.putBoolean(REACTIVATE_WELCOME,
                reactivateWelcomeCheckBox.isSelected());
        preferences.putBoolean(KEEP_PRINTER_SETTINGS,
                keepPrinterSettingsCheckBox.isSelected());
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

        source.unmountTmpPartitions();

        // stop monitoring thread
        udisksMonitorThread.stopMonitoring();

        // everything is done, disappear now
        System.exit(0);
    }

    private String getUpgradeOverwriteListString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0, size = upgradeOverwriteListModel.size();
                i < size; i++) {
            String entry = (String) upgradeOverwriteListModel.get(i);
            stringBuilder.append(entry);
            if (i != (size - 1)) {
                stringBuilder.append('\n');
            }
        }
        return stringBuilder.toString();
    }

    private void fillUpgradeOverwriteList(String list) {
        if (!list.isEmpty()) {
            String[] upgradeOverWriteTokens = list.split("\n");
            for (String upgradeOverWriteToken : upgradeOverWriteTokens) {
                upgradeOverwriteListModel.addElement(upgradeOverWriteToken);
            }
        }
    }

    private void switchToInstallSelection() {
        // update label highlights
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);

        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // !!! state must be updated before updating the device list !!!
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        state = State.INSTALL_SELECTION;

        // update storage device list
        new InstallStorageDeviceListUpdater(this, installStorageDeviceList,
                installStorageDeviceListModel,
                installShowHarddisksCheckBox.isSelected()).execute();

        // update copyExchangeCheckBox
        if (source.hasExchangePartition()) {
            long exchangeSize = source.getExchangePartition()
                    .getUsedSpace(false);
            String dataVolumeString
                    = LernstickFileTools.getDataVolumeString(exchangeSize, 1);
            copyExchangeCheckBox.setText(
                    STRINGS.getString("DLCopySwingGUI.copyExchangeCheckBox.text")
                    + " (" + dataVolumeString + ')');
        }

        previousButton.setEnabled(true);
        updateInstallSelectionCountAndExchangeInfo();
        showCard(cardPanel, "installSelectionPanel");
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

    private void setLabelHighlighted(JLabel label, boolean selected) {
        if (selected) {
            label.setForeground(Color.BLACK);
            label.setFont(label.getFont().deriveFont(
                    label.getFont().getStyle() | Font.BOLD));
        } else {
            label.setForeground(Color.DARK_GRAY);
            label.setFont(label.getFont().deriveFont(
                    label.getFont().getStyle() & ~Font.BOLD));
        }

    }

    /**
     * checks, if all storage devices selected for installation are large enough
     * and [en/dis]ables the "Next" button accordingly
     */
    private void updateInstallNextButton() {
        int[] selectedIndices = installStorageDeviceList.getSelectedIndices();

        // no storage device selected
        if (selectedIndices.length == 0) {
            disableNextButton();
            return;
        }

        // check selection
        for (int i : selectedIndices) {
            StorageDevice device
                    = (StorageDevice) installStorageDeviceListModel.get(i);
            PartitionState partitionState = DLCopy.getPartitionState(
                    device.getSize(), DLCopy.systemSizeEnlarged);
            if (partitionState == PartitionState.TOO_SMALL) {
                // a selected device is too small, disable nextButton
                disableNextButton();
                return;
            }
        }
        // all selected devices are large enough, enable nextButton
        enableNextButton();
    }

    private void enableNextButton() {
        if (nextButton.isShowing()) {
            nextButton.setEnabled(true);
            getRootPane().setDefaultButton(nextButton);
            if (previousButton.hasFocus()) {
                nextButton.requestFocusInWindow();
            }
        }
    }

    private void disableNextButton() {
        if (nextButton.hasFocus()) {
            previousButton.requestFocusInWindow();
        }
        getRootPane().setDefaultButton(previousButton);
        nextButton.setEnabled(false);
    }

    private void showInstallIndeterminateProgressBarText(final String text) {
        showIndeterminateProgressBarText(installCardPanel,
                "installIndeterminateProgressPanel",
                installIndeterminateProgressBar, text);
    }

    private void showUpgradeIndeterminateProgressBarText(final String text) {
        showIndeterminateProgressBarText(upgradeCardPanel,
                "upgradeIndeterminateProgressPanel",
                upgradeIndeterminateProgressBar, text);
    }

    private static void showIndeterminateProgressBarText(
            final Container container, final String cardName,
            final JProgressBar progressBar, final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(container, cardName);
                progressBar.setString(STRINGS.getString(text));
            }
        });
    }

    private void deviceStarted(StorageDevice storageDevice) {
        currentDevice = storageDevice;
        deviceStartTime = System.currentTimeMillis();
        batchCounter++;
        resultsList.add(new StorageDeviceResult(storageDevice, -1, null));
    }

    private void deviceFinished(String errorMessage) {
        long duration = System.currentTimeMillis() - deviceStartTime;

        // remove "in progress" entry
        resultsList.remove(resultsList.size() - 1);

        // add current result
        resultsList.add(new StorageDeviceResult(
                currentDevice, duration, errorMessage));

        // update final report
        resultsTableModel.setList(resultsList);
    }

    private void batchFinished(String nonRemovableKey,
            String removableKey, String reportKey) {
        setTitle(STRINGS.getString("DLCopySwingGUI.title"));
        String key;
        switch (source.getDeviceType()) {
            case HardDrive:
            case OpticalDisc:
                key = nonRemovableKey;
                break;
            default:
                key = removableKey;
        }
        resultsInfoLabel.setText(STRINGS.getString(key));
        resultsTitledPanel.setBorder(BorderFactory.createTitledBorder(
                STRINGS.getString(reportKey)));
        showCard(cardPanel, "resultsPanel");
        processDone();
    }

    private void processDone() {
        previousButton.setEnabled(true);
        nextButton.setText(STRINGS.getString("Done"));
        nextButton.setIcon(new ImageIcon(getClass().getResource(
                "/ch/fhnw/dlcopy/icons/exit.png")));
        nextButton.setEnabled(true);
        previousButton.requestFocusInWindow();
        getRootPane().setDefaultButton(previousButton);
        playNotifySound();
        toFront();
    }

    private static void showCard(Container container, String cardName) {
        CardLayout cardLayout = (CardLayout) container.getLayout();
        cardLayout.show(container, cardName);
    }

    private void showFileCopy(FileCopierPanel fileCopierPanel,
            FileCopier fileCopier, final JLabel label,
            final Container container, final String cardName) {
        fileCopierPanel.setFileCopier(fileCopier);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                label.setText(STRINGS.getString("Copying_Files"));
                showCard(container, cardName);
            }
        });
    }

    private static void setLabelTextonEDT(
            final JLabel label, final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }

    private static void setProgressBarStringOnEDT(
            final JProgressBar progressBar, final String string) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressBar.setString(string);
            }
        });
    }

    private void checkInstallSelection() throws IOException, DBusException {

        // check all selected target USB storage devices
        int[] selectedIndices = installStorageDeviceList.getSelectedIndices();
        boolean harddiskSelected = false;
        for (int i : selectedIndices) {
            StorageDevice storageDevice
                    = (StorageDevice) installStorageDeviceListModel.getElementAt(i);
            if (storageDevice.getType() == StorageDevice.Type.HardDrive) {
                harddiskSelected = true;
            }
            PartitionSizes partitionSizes = DLCopy.getInstallPartitionSizes(
                    storageDevice, exchangePartitionSizeSlider.getValue());
            if (!checkPersistence(partitionSizes)) {
                return;
            }
            if (!checkExchange(partitionSizes)) {
                return;
            }
        }

        // show big fat warning dialog
        if (harddiskSelected) {
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
                    return;
                }
                correctAnswer = expectedInput.equals(input);
                if (!correctAnswer) {
                    JOptionPane.showMessageDialog(this,
                            STRINGS.getString("Warning_Mistyped_Text"),
                            STRINGS.getString("Warning"),
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        } else {
            int result = JOptionPane.showConfirmDialog(this,
                    STRINGS.getString("Final_Installation_Warning"),
                    STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        state = State.INSTALLATION;

        // let's start...
        Number autoNumberStart = (Number) autoNumberStartSpinner.getValue();
        Number autoIncrementNumber
                = (Number) autoNumberIncrementSpinner.getValue();
        int autoNumber = autoNumberStart.intValue();
        int autoIncrement = autoIncrementNumber.intValue();
        List<StorageDevice> deviceList = new ArrayList<>();
        for (int i : selectedIndices) {
            deviceList.add(installStorageDeviceListModel.get(i));
        }
        Object selectedFileSystem
                = exchangePartitionFileSystemComboBox.getSelectedItem();
        String exchangePartitionFileSystem = selectedFileSystem.toString();
        String dataPartitionFileSystem
                = dataPartitionFilesystemComboBox.getSelectedItem().toString();
        DataPartitionMode dataPartitionMode
                = getDataPartitionMode(dataPartitionModeComboBox);
        resultsList = new ArrayList<>();
        batchCounter = 0;

        new Installer(source, deviceList, exchangePartitionTextField.getText(),
                exchangePartitionFileSystem, dataPartitionFileSystem, this,
                exchangePartitionSizeSlider.getValue(),
                copyExchangeCheckBox.isSelected(), autoNumber,
                autoIncrement, autoNumberPatternTextField.getText(),
                copyPersistenceCheckBox.isSelected(),
                dataPartitionMode).execute();
    }

    private void upgrade() {
        // some backup related sanity checks
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
                upgradeSelectionTabbedPane.setSelectedComponent(
                        upgradeDetailsTabbedPane);
                upgradeDetailsTabbedPane.setSelectedComponent(
                        upgradeOptionsPanel);
                textField.requestFocusInWindow();
                textField.selectAll();
                showErrorMessage(errorMessage);
                return;
            }
        }
        int exchangeMB = 0;
        if (resizeExchangeRadioButton.isSelected()) {
            String newSizeText = resizeExchangeTextField.getText();
            String errorMessage = null;
            if (newSizeText.isEmpty()) {
                errorMessage = STRINGS.getString(
                        "Error_No_Exchange_Resize_Size");
            } else {
                try {
                    exchangeMB = Integer.parseInt(newSizeText);
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.WARNING, "", ex);
                    errorMessage = STRINGS.getString(
                            "Error_Parsing_Exchange_Resize_Size");
                }
            }
            if (errorMessage != null) {
                upgradeSelectionTabbedPane.setSelectedComponent(
                        upgradeDetailsTabbedPane);
                upgradeDetailsTabbedPane.setSelectedComponent(
                        upgradeOptionsPanel);
                showErrorMessage(errorMessage);
                resizeExchangeTextField.requestFocusInWindow();
                resizeExchangeTextField.selectAll();
                return;
            }
        }

        List selectedDevices = upgradeStorageDeviceList.getSelectedValuesList();
        int noDataPartitionCounter = 0;
        for (Object object : selectedDevices) {
            StorageDevice storageDevice = (StorageDevice) object;
            if (storageDevice.getDataPartition() == null) {
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

        int result = JOptionPane.showConfirmDialog(this,
                STRINGS.getString("Final_Upgrade_Warning"),
                STRINGS.getString("Warning"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);

        // let's start...
        state = State.UPGRADE;
        showCard(cardPanel, "upgradeTabbedPane");
        resultsList = new ArrayList<>();
        batchCounter = 0;
        boolean removeBackup = automaticBackupCheckBox.isSelected()
                && automaticBackupRemoveCheckBox.isSelected();
        List<StorageDevice> deviceList = new ArrayList<>();
        int[] selectedIndices = upgradeStorageDeviceList.getSelectedIndices();
        for (int i : selectedIndices) {
            deviceList.add(upgradeStorageDeviceListModel.get(i));
        }
        List<String> overWriteList = new ArrayList<>();
        for (int i = 0, size = upgradeOverwriteListModel.size(); i < size; i++) {
            overWriteList.add(upgradeOverwriteListModel.get(i));
        }
        RepartitionStrategy repartitionStrategy;
        if (originalExchangeRadioButton.isSelected()) {
            repartitionStrategy = RepartitionStrategy.KEEP;
        } else if (resizeExchangeRadioButton.isSelected()) {
            repartitionStrategy = RepartitionStrategy.RESIZE;
        } else {
            repartitionStrategy = RepartitionStrategy.REMOVE;
        }

        Object selectedItem
                = exchangePartitionFileSystemComboBox.getSelectedItem();
        String exchangePartitionFileSystem = selectedItem.toString();
        // TODO: using dataPartitionFilesystemComboBox.getSelectedItem() here is
        // ugly because the input field it is not visible when upgrading
        String dataPartitionFileSystem
                = dataPartitionFilesystemComboBox.getSelectedItem().toString();

        // TODO: using exchangePartitionTextField.getText() here is ugly
        // because the input field it is not visible when upgrading
        new Upgrader(source, deviceList, exchangePartitionTextField.getText(),
                exchangePartitionFileSystem, dataPartitionFileSystem, this,
                this, repartitionStrategy, exchangeMB,
                automaticBackupCheckBox.isSelected(),
                automaticBackupTextField.getText(), removeBackup,
                upgradeSystemPartitionCheckBox.isSelected(),
                keepPrinterSettingsCheckBox.isSelected(),
                reactivateWelcomeCheckBox.isSelected(),
                removeHiddenFilesCheckBox.isSelected(), overWriteList,
                DLCopy.systemSizeEnlarged).execute();
    }

    private boolean checkExchange(PartitionSizes partitions) throws IOException {
        if (!copyExchangeCheckBox.isSelected()) {
            return true;
        }
        // check if the target stick actually has an exchange partition
        if (partitions.getExchangeMB() == 0) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_No_Exchange_At_Target"),
                    STRINGS.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // check that target partition is large enough
        long sourceExchangeSize = source.getExchangePartition()
                .getUsedSpace(false);
        long targetExchangeSize
                = (long) partitions.getExchangeMB() * (long) DLCopy.MEGA;
        if (sourceExchangeSize > targetExchangeSize) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_Target_Exchange_Too_Small"),
                    STRINGS.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean isUnmountedPersistenceAvailable()
            throws IOException, DBusException {

        // check that a persistence partition is available
        Partition dataPartition = source.getDataPartition();
        if (dataPartition == null) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_No_Persistence"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // ensure that the persistence partition is not mounted read-write
        String dataPartitionDevice
                = "/dev/" + dataPartition.getDeviceAndNumber();
        boolean mountedReadWrite = false;
        List<String> mounts = LernstickFileTools.readFile(
                new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            String mountedPartition = tokens[0];
            if (mountedPartition.equals(dataPartitionDevice)) {
                // check mount options
                String mountOptions = tokens[3];
                if (mountOptions.startsWith("rw")) {
                    mountedReadWrite = true;
                    break;
                }
            }
        }

        if (mountedReadWrite) {
            if (persistenceBoot) {
                // error and hint
                String message = STRINGS.getString(
                        "Warning_Persistence_Mounted") + "\n"
                        + STRINGS.getString("Hint_Nonpersistent_Boot");
                JOptionPane.showMessageDialog(this, message,
                        STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
                return false;

            } else {
                // persistence partition was manually mounted
                // warning and offer umount
                String message = STRINGS.getString(
                        "Warning_Persistence_Mounted") + "\n"
                        + STRINGS.getString("Umount_Question");
                int returnValue = JOptionPane.showConfirmDialog(this, message,
                        STRINGS.getString("Warning"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (returnValue == JOptionPane.YES_OPTION) {
                    source.getDataPartition().umount();
                    return isUnmountedPersistenceAvailable();
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean checkPersistence(PartitionSizes partitions)
            throws IOException, DBusException {

        if (!copyPersistenceCheckBox.isSelected()) {
            return true;
        }

        if (!isUnmountedPersistenceAvailable()) {
            return false;
        }

        // check if the target medium actually has a persistence partition
        if (partitions.getPersistenceMB() == 0) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_No_Persistence_At_Target"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // check that target partition is large enough
        long persistenceSize = source.getDataPartition()
                .getUsedSpace(false);
        long targetPersistenceSize
                = (long) partitions.getPersistenceMB() * (long) DLCopy.MEGA;
        if (persistenceSize > targetPersistenceSize) {
            String errorMessage
                    = STRINGS.getString("Error_Target_Persistence_Too_Small");
            errorMessage = MessageFormat.format(errorMessage,
                    LernstickFileTools.getDataVolumeString(persistenceSize, 1),
                    LernstickFileTools.getDataVolumeString(
                            targetPersistenceSize, 1));
            JOptionPane.showMessageDialog(this, errorMessage,
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private void switchToISOInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        showCard(cardPanel, "toISOInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.ISO_INFORMATION;
    }

    private void switchToUSBInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        executionLabel.setText(STRINGS.getString("Installation_Label"));
        showCard(cardPanel, "installInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.INSTALL_INFORMATION;
    }

    private void switchToUpgradeInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        executionLabel.setText(STRINGS.getString("Upgrade_Label"));
        showCard(cardPanel, "upgradeInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.UPGRADE_INFORMATION;
    }

    private void switchToRepairInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        executionLabel.setText(STRINGS.getString("Repair_Label"));
        showCard(cardPanel, "repairInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.REPAIR_INFORMATION;
    }

    private void switchToRepairSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.REPAIR_SELECTION;
        showCard(cardPanel, "repairSelectionPanel");
        enableNextButton();
    }

    private void globalShow(String componentName) {
        Container contentPane = getContentPane();
        CardLayout globalCardLayout = (CardLayout) contentPane.getLayout();
        globalCardLayout.show(contentPane, componentName);
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
        } else if (document == tmpDirTextField.getDocument()) {
            checkFreeSpaceTextField();
        }
    }

    private void checkFreeSpaceTextField() {
        File tmpDir = new File(tmpDirTextField.getText());
        if (tmpDir.exists()) {
            long freeSpace = tmpDir.getFreeSpace();
            freeSpaceTextField.setText(
                    LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                writableTextField.setText(STRINGS.getString("Yes"));
                writableTextField.setForeground(Color.BLACK);
                enableNextButton();
            } else {
                writableTextField.setText(STRINGS.getString("No"));
                writableTextField.setForeground(Color.RED);
                disableNextButton();
            }
        } else {
            writableTextField.setText(
                    STRINGS.getString("Directory_Does_Not_Exist"));
            writableTextField.setForeground(Color.RED);
            disableNextButton();
        }
    }

    private void storageDeviceListChanged(DefaultListModel<StorageDevice> model,
            JPanel panel, String noMediaPanelName, String selectionPanelName,
            StorageDeviceRenderer renderer, JList list) {

        int deviceCount = model.size();
        if (deviceCount == 0) {
            showCard(panel, noMediaPanelName);
            disableNextButton();
        } else {
            renderer.setMaxSize(getMaxStorageDeviceSize(model));
            showCard(panel, selectionPanelName);
            // auto-select single entry
            if (deviceCount == 1) {
                list.setSelectedIndex(0);
            }
            list.repaint();
        }
    }

    private class UdisksMonitorThread extends Thread {

        // use local ProcessExecutor because the udisks process is blocking and
        // long-running
        private final ProcessExecutor executor = new ProcessExecutor();

        @Override
        public void run() {
            String binaryName;
            String parameter;
            if (DbusTools.DBUS_VERSION == DbusTools.DBUS_VERSION.V1) {
                binaryName = "udisks";
                parameter = "--monitor";
            } else {
                binaryName = "udisksctl";
                parameter = "monitor";
            }
            executor.addPropertyChangeListener(DLCopySwingGUI.this);
            executor.executeProcess(binaryName, parameter);
        }

        public void stopMonitoring() {
            executor.destroy();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel autoNumberIncrementLabel;
    private javax.swing.JSpinner autoNumberIncrementSpinner;
    private javax.swing.JPanel autoNumberPanel;
    private javax.swing.JLabel autoNumberPatternLabel;
    private javax.swing.JTextField autoNumberPatternTextField;
    private javax.swing.JLabel autoNumberStartLabel;
    private javax.swing.JSpinner autoNumberStartSpinner;
    private javax.swing.JCheckBox autoStartInstallerCheckBox;
    private javax.swing.JButton automaticBackupButton;
    private javax.swing.JCheckBox automaticBackupCheckBox;
    private javax.swing.JLabel automaticBackupLabel;
    private javax.swing.JCheckBox automaticBackupRemoveCheckBox;
    private javax.swing.JTextField automaticBackupTextField;
    private javax.swing.JLabel bootDefinitionLabel;
    private javax.swing.JPanel bootMediumPanel;
    private javax.swing.JRadioButton bootMediumRadioButton;
    private javax.swing.JPanel buttonGridPanel;
    private javax.swing.JPanel cardPanel;
    private javax.swing.JLabel choiceLabel;
    private javax.swing.JPanel choicePanel;
    private javax.swing.JCheckBox copyExchangeCheckBox;
    private javax.swing.JCheckBox copyPersistenceCheckBox;
    private javax.swing.JLabel cpFilenameLabel;
    private javax.swing.JPanel cpPanel;
    private javax.swing.JProgressBar cpPogressBar;
    private javax.swing.JLabel cpTimeLabel;
    private javax.swing.JLabel currentlyInstalledDeviceLabel;
    private javax.swing.JLabel currentlyRepairedDeviceLabel;
    private javax.swing.JLabel currentlyUpgradedDeviceLabel;
    private javax.swing.JLabel dataDefinitionLabel;
    private javax.swing.JLabel dataPartitionFileSystemLabel;
    private javax.swing.JComboBox dataPartitionFilesystemComboBox;
    private javax.swing.JComboBox dataPartitionModeComboBox;
    private javax.swing.JLabel dataPartitionModeLabel;
    private javax.swing.JPanel dataPartitionPanel;
    private javax.swing.JLabel doneLabel;
    private javax.swing.JPanel donePanel;
    private javax.swing.ButtonGroup exchangeButtonGroup;
    private javax.swing.JLabel exchangeDefinitionLabel;
    private javax.swing.JPanel exchangePartitionBottomPanel;
    private javax.swing.JComboBox exchangePartitionFileSystemComboBox;
    private javax.swing.JLabel exchangePartitionFileSystemLabel;
    private javax.swing.JPanel exchangePartitionFileSystemPanel;
    private javax.swing.JLabel exchangePartitionLabel;
    private javax.swing.JPanel exchangePartitionLabelPanel;
    private javax.swing.JPanel exchangePartitionPanel;
    private javax.swing.JLabel exchangePartitionSizeLabel;
    private javax.swing.JSlider exchangePartitionSizeSlider;
    private javax.swing.JTextField exchangePartitionSizeTextField;
    private javax.swing.JLabel exchangePartitionSizeUnitLabel;
    private javax.swing.JTextField exchangePartitionTextField;
    private javax.swing.JLabel executionLabel;
    private javax.swing.JPanel executionPanel;
    private javax.swing.JRadioButton formatDataPartitionRadioButton;
    private javax.swing.JLabel freeSpaceLabel;
    private javax.swing.JTextField freeSpaceTextField;
    private javax.swing.JCheckBox homeDirectoryCheckBox;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JLabel infoStepLabel;
    private javax.swing.JButton installButton;
    private javax.swing.JPanel installCardPanel;
    private javax.swing.JLabel installCopyLabel;
    private javax.swing.JPanel installCopyPanel;
    private javax.swing.JPanel installCurrentPanel;
    private ch.fhnw.filecopier.FileCopierPanel installFileCopierPanel;
    private javax.swing.JProgressBar installIndeterminateProgressBar;
    private javax.swing.JPanel installIndeterminateProgressPanel;
    private javax.swing.JPanel installInfoPanel;
    private javax.swing.JLabel installLabel;
    private javax.swing.JPanel installListPanel;
    private javax.swing.JTabbedPane installListTabbedPane;
    private javax.swing.JLabel installNoMediaLabel;
    private javax.swing.JPanel installNoMediaPanel;
    private javax.swing.JPanel installReportPanel;
    private javax.swing.JPanel installSelectionCardPanel;
    private javax.swing.JLabel installSelectionCountLabel;
    private javax.swing.JLabel installSelectionHeaderLabel;
    private javax.swing.JPanel installSelectionPanel;
    private javax.swing.JCheckBox installShowHarddisksCheckBox;
    private javax.swing.ButtonGroup installSourceButtonGroup;
    private javax.swing.JPanel installSourcePanel;
    private javax.swing.JList installStorageDeviceList;
    private javax.swing.JScrollPane installStorageDeviceListScrollPane;
    private javax.swing.JTabbedPane installTabbedPane;
    private javax.swing.JPanel installTargetPanel;
    private javax.swing.JScrollPane installationResultsScrollPane;
    private javax.swing.JTable installationResultsTable;
    private javax.swing.ButtonGroup isoButtonGroup;
    private javax.swing.JComboBox isoDataPartitionModeComboBox;
    private javax.swing.JLabel isoDataPartitionModeLabel;
    private javax.swing.JLabel isoDoneLabel;
    private javax.swing.JLabel isoLabelLabel;
    private javax.swing.JTextField isoLabelTextField;
    private javax.swing.JPanel isoOptionsCardPanel;
    private javax.swing.JPanel isoOptionsPanel;
    private javax.swing.JButton isoSourceFileChooserButton;
    private javax.swing.JRadioButton isoSourceRadioButton;
    private javax.swing.JTextField isoSourceTextField;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JCheckBox keepPrinterSettingsCheckBox;
    private javax.swing.JButton nextButton;
    private javax.swing.JPanel northEastPanel;
    private javax.swing.JPanel northWestPanel;
    private javax.swing.JRadioButton originalExchangeRadioButton;
    private javax.swing.JButton previousButton;
    private javax.swing.JPanel radioButtonPanel;
    private javax.swing.JCheckBox reactivateWelcomeCheckBox;
    private javax.swing.JRadioButton removeExchangeRadioButton;
    private javax.swing.JRadioButton removeFilesRadioButton;
    private javax.swing.JCheckBox removeHiddenFilesCheckBox;
    private javax.swing.JButton repairButton;
    private javax.swing.ButtonGroup repairButtonGroup;
    private javax.swing.JLabel repairDataDefinitionLabel;
    private javax.swing.JLabel repairExchangeDefinitionLabel;
    private javax.swing.JLabel repairInfoLabel;
    private javax.swing.JPanel repairInfoPanel;
    private javax.swing.JLabel repairNoMediaLabel;
    private javax.swing.JPanel repairNoMediaPanel;
    private javax.swing.JLabel repairOsDefinitionLabel;
    private javax.swing.JPanel repairPanel;
    private javax.swing.JProgressBar repairProgressBar;
    private javax.swing.JPanel repairSelectionCardPanel;
    private javax.swing.JLabel repairSelectionCountLabel;
    private javax.swing.JPanel repairSelectionDeviceListPanel;
    private javax.swing.JLabel repairSelectionHeaderLabel;
    private javax.swing.JPanel repairSelectionPanel;
    private javax.swing.JCheckBox repairShowHarddisksCheckBox;
    private javax.swing.JList repairStorageDeviceList;
    private javax.swing.JScrollPane repairStorageDeviceListScrollPane;
    private javax.swing.JPanel repartitionExchangeOptionsPanel;
    private javax.swing.JLabel resizeExchangeLabel;
    private javax.swing.JRadioButton resizeExchangeRadioButton;
    private javax.swing.JTextField resizeExchangeTextField;
    private javax.swing.JLabel resultsInfoLabel;
    private javax.swing.JPanel resultsPanel;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JPanel resultsTitledPanel;
    private javax.swing.JPanel rsyncPanel;
    private javax.swing.JProgressBar rsyncPogressBar;
    private javax.swing.JLabel rsyncTimeLabel;
    private javax.swing.JRadioButton runningSystemSourceRadioButton;
    private javax.swing.JLabel selectionLabel;
    private javax.swing.JCheckBox showNotUsedDialogCheckBox;
    private javax.swing.JButton sortAscendingButton;
    private javax.swing.JButton sortDescendingButton;
    private javax.swing.JLabel stepsLabel;
    private javax.swing.JPanel stepsPanel;
    private javax.swing.JLabel systemDefinitionLabel;
    private javax.swing.JCheckBox systemFilesCheckBox;
    private javax.swing.JPanel systemMediumPanel;
    private javax.swing.JRadioButton systemMediumRadioButton;
    private javax.swing.JLabel tmpDirLabel;
    private javax.swing.JButton tmpDirSelectButton;
    private javax.swing.JTextField tmpDirTextField;
    private javax.swing.JLabel tmpDriveInfoLabel;
    private javax.swing.JButton toISOButton;
    private javax.swing.JPanel toISODonePanel;
    private javax.swing.JLabel toISOInfoLabel;
    private javax.swing.JPanel toISOInfoPanel;
    private javax.swing.JProgressBar toISOProgressBar;
    private javax.swing.JPanel toISOProgressPanel;
    private javax.swing.JPanel toISOSelectionPanel;
    private javax.swing.JPanel toIsoGridBagPanel;
    private javax.swing.JLabel upgradeBackupDurationLabel;
    private javax.swing.JLabel upgradeBackupFilenameLabel;
    private javax.swing.JLabel upgradeBackupLabel;
    private javax.swing.JPanel upgradeBackupPanel;
    private javax.swing.JProgressBar upgradeBackupProgressBar;
    private javax.swing.JLabel upgradeBackupProgressLabel;
    private javax.swing.JLabel upgradeBootDefinitionLabel;
    private javax.swing.JButton upgradeButton;
    private javax.swing.JPanel upgradeCardPanel;
    private javax.swing.JLabel upgradeCopyLabel;
    private javax.swing.JPanel upgradeCopyPanel;
    private javax.swing.JLabel upgradeDataDefinitionLabel;
    private javax.swing.JTabbedPane upgradeDetailsTabbedPane;
    private javax.swing.JLabel upgradeExchangeDefinitionLabel;
    private ch.fhnw.filecopier.FileCopierPanel upgradeFileCopierPanel;
    private javax.swing.JProgressBar upgradeIndeterminateProgressBar;
    private javax.swing.JPanel upgradeIndeterminateProgressPanel;
    private javax.swing.JLabel upgradeInfoLabel;
    private javax.swing.JPanel upgradeInfoPanel;
    private javax.swing.JLabel upgradeLabel;
    private javax.swing.JButton upgradeMoveDownButton;
    private javax.swing.JButton upgradeMoveUpButton;
    private javax.swing.JLabel upgradeNoMediaLabel;
    private javax.swing.JPanel upgradeNoMediaPanel;
    private javax.swing.JPanel upgradeOptionsPanel;
    private javax.swing.JLabel upgradeOsDefinitionLabel;
    private javax.swing.JButton upgradeOverwriteAddButton;
    private javax.swing.JButton upgradeOverwriteEditButton;
    private javax.swing.JButton upgradeOverwriteExportButton;
    private javax.swing.JButton upgradeOverwriteImportButton;
    private javax.swing.JList upgradeOverwriteList;
    private javax.swing.JPanel upgradeOverwritePanel;
    private javax.swing.JButton upgradeOverwriteRemoveButton;
    private javax.swing.JScrollPane upgradeOverwriteScrollPane;
    private javax.swing.JPanel upgradePanel;
    private javax.swing.JPanel upgradeReportPanel;
    private javax.swing.JScrollPane upgradeResultsScrollPane;
    private javax.swing.JTable upgradeResultsTable;
    private javax.swing.JPanel upgradeSelectionCardPanel;
    private javax.swing.JLabel upgradeSelectionCountLabel;
    private javax.swing.JPanel upgradeSelectionDeviceListPanel;
    private javax.swing.JLabel upgradeSelectionHeaderLabel;
    private javax.swing.JPanel upgradeSelectionPanel;
    private javax.swing.JTabbedPane upgradeSelectionTabbedPane;
    private javax.swing.JCheckBox upgradeShowHarddisksCheckBox;
    private javax.swing.JList upgradeStorageDeviceList;
    private javax.swing.JScrollPane upgradeStorageDeviceListScrollPane;
    private javax.swing.JCheckBox upgradeSystemPartitionCheckBox;
    private javax.swing.JTabbedPane upgradeTabbedPane;
    private javax.swing.JLabel writableLabel;
    private javax.swing.JTextField writableTextField;
    // End of variables declaration//GEN-END:variables
}
