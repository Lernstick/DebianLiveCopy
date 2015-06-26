/*
 * DLCopy.java
 *
 * Created on 16. April 2008, 09:14
 */
package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.InstallationSource.DataPartitionMode;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.FileCopierPanel;
import ch.fhnw.filecopier.Source;
import ch.fhnw.jbackpack.JSqueezedLabel;
import ch.fhnw.jbackpack.RdiffBackupRestore;
import ch.fhnw.jbackpack.chooser.Increment;
import ch.fhnw.jbackpack.chooser.RdiffFile;
import ch.fhnw.jbackpack.chooser.RdiffFileDatabase;
import ch.fhnw.jbackpack.chooser.SelectBackupDirectoryDialog;
import ch.fhnw.util.DbusTools;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import ch.fhnw.util.StorageTools;
import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.xml.parsers.ParserConfigurationException;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;
import org.xml.sax.SAXException;

/**
 * Installs Debian Live to a USB flash drive
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DLCopy extends JFrame
        implements DocumentListener, PropertyChangeListener, ListDataListener {

    /**
     * 1024 * 1024
     */
    public static final int MEGA = 1048576;
    /**
     * all the translateable STRINGS of the program
     */
    public static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings");
    /**
     * the minimal size for a data partition (200 MByte)
     */
    public final static long MINIMUM_PARTITION_SIZE = 200 * MEGA;
    /**
     * the size of the boot partition (given in MiB)
     */
    public final static long BOOT_PARTITION_SIZE = 100;

    /**
     * the known partition states for a drive
     */
    public enum PartitionState {

        /**
         * the drive is too small
         */
        TOO_SMALL,
        /**
         * the drive is so small that only a system partition can be created
         */
        ONLY_SYSTEM,
        /**
         * the system is so small that only a system and persistence partition
         * can be created
         */
        PERSISTENCE,
        /**
         * the system is large enough to create all partition scenarios
         */
        EXCHANGE
    }

    private final static ProcessExecutor processExecutor
            = new ProcessExecutor();
    private final static FileFilter NO_HIDDEN_FILES_FILTER
            = NoHiddenFilesSwingFileFilter.getInstance();
    private final static NumberFormat numberFormat = NumberFormat.getInstance();
    private final static Logger LOGGER
            = Logger.getLogger(DLCopy.class.getName());
    private final static long MINIMUM_FREE_MEMORY = 300 * MEGA;
    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";
    private final DefaultListModel installStorageDeviceListModel
            = new DefaultListModel();
    private final DefaultListModel upgradeStorageDeviceListModel
            = new DefaultListModel();
    private final DefaultListModel repairStorageDeviceListModel
            = new DefaultListModel();
    private final InstallStorageDeviceRenderer installStorageDeviceRenderer;
    private final UpgradeStorageDeviceRenderer upgradeStorageDeviceRenderer;
    private final RepairStorageDeviceRenderer repairStorageDeviceRenderer;
    private long systemSize = -1;
    private long systemSizeEnlarged = -1;
    private final DateFormat timeFormat;

    private final XmlBootConfigUtil xmlBootConfigUtil
            = new XmlBootConfigUtil(processExecutor);

    private enum State {

        INSTALL_INFORMATION, INSTALL_SELECTION, INSTALLATION,
        UPGRADE_INFORMATION, UPGRADE_SELECTION, UPGRADE,
        REPAIR_INFORMATION, REPAIR_SELECTION, REPAIR,
        ISO_INFORMATION, ISO_SELECTION, ISO_INSTALLATION
    }
    private State state = State.INSTALL_INFORMATION;

    private enum IsoStep {

        MKSQUASHFS, GENISOIMAGE
    }

    // Information about currently running system image
    private InstallationSource systemSource;
    private InstallationSource source = null;

    private boolean persistenceBoot;
    private boolean textFieldTriggeredSliderChange;

    private String isoImagePath;

    private enum DebianLiveDistribution {

        Default, lernstick, lernstick_pu
    }
    private DebianLiveDistribution debianLiveDistribution;
    private final String systemPartitionLabel;
    private final Pattern mksquashfsPattern
            = Pattern.compile("\\[.* (.*)/(.*) .*");
    // genisoimage output looks like this:
    // 89.33% done, estimate finish Wed Dec  2 17:08:41 2009
    // private final Pattern genisoimagePattern
    //      = Pattern.compile("(.*)\\..*%.*");

    // xorriso output looks like this:
    // xorriso : UPDATE : Writing:    234234s  31.5%   fifo 23% buf 50% 12.7xD
    //
    // The first 31.5% is the progress value we are looking for. We are only
    // interested in the integer value (31 in the example above).
    private final Pattern xorrisoPattern
            = Pattern.compile(".* (.*)\\..*% .*%.*%.*");
    private DBusConnection dbusSystemConnection;
    private UdisksMonitorThread udisksMonitorThread;
    private DefaultListModel upgradeOverwriteListModel;
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

    private DataPartitionMode sourceDataPartitionMode;
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

    /**
     * Creates new form DLCopy
     *
     * @param arguments the command line arguments
     */
    public DLCopy(String[] arguments) {
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
        processExecutor.setEnvironment(environment);

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
        debianLiveDistribution = DebianLiveDistribution.Default;
        isoImagePath = null;
        for (int i = 0, length = arguments.length; i < length; i++) {

            if (arguments[i].equals("--variant")
                    && (i != length - 1)
                    && (arguments[i + 1].equals("lernstick"))) {
                debianLiveDistribution = DebianLiveDistribution.lernstick;
            }
            if (arguments[i].equals("--variant")
                    && (i != length - 1)
                    && (arguments[i + 1].equals("lernstick-pu"))) {
                debianLiveDistribution = DebianLiveDistribution.lernstick_pu;
            }
            if (arguments[i].equals("--iso") && (i != length - 1)) {
                isoImagePath = arguments[i + 1];
            }
        }
        if (LOGGER.isLoggable(Level.INFO)) {
            switch (debianLiveDistribution) {
                case lernstick:
                    LOGGER.info("using lernstick distribution");
                    break;
                case lernstick_pu:
                    LOGGER.info("using lernstick exam environment distribution");
                    break;
                case Default:
                    LOGGER.info("using Debian Live distribution");
                    break;
                default:
                    LOGGER.log(Level.WARNING, "unsupported distribution: {0}",
                            debianLiveDistribution);
            }
        }

        switch (debianLiveDistribution) {
            case lernstick:
            case lernstick_pu:
                systemPartitionLabel = "system";
                break;
            default:
                systemPartitionLabel = "DEBIAN_LIVE";
        }

        try {
            dbusSystemConnection = DBusConnection.getConnection(
                    DBusConnection.SYSTEM);
        } catch (DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        try {
            systemSource = new SystemInstallationSource(processExecutor,
                    xmlBootConfigUtil);
            source = systemSource;
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);

        }
        if (isoImagePath != null) {
            source = new IsoInstallationSource(isoImagePath, processExecutor);
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

        systemSize = StorageTools.getSystemSize();
        systemSizeEnlarged = StorageTools.getEnlargedSystemSize();
        String sizeString = LernstickFileTools.getDataVolumeString(
                systemSizeEnlarged, 1);

        String text = STRINGS.getString("Select_Install_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        installSelectionHeaderLabel.setText(text);

        text = STRINGS.getString("Boot_Definition");
        String bootSize = LernstickFileTools.getDataVolumeString(
                BOOT_PARTITION_SIZE * MEGA, 1);
        text = MessageFormat.format(text, bootSize);
        bootDefinitionLabel.setText(text);

        text = STRINGS.getString("System_Definition");
        text = MessageFormat.format(text, sizeString);
        systemDefinitionLabel.setText(text);

        sizeString = LernstickFileTools.getDataVolumeString(systemSize, 1);
        text = STRINGS.getString("Select_Upgrade_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        upgradeSelectionHeaderLabel.setText(text);

        // detect if system has an exchange partition
        if (!source.hasExchangePartition()) {
            copyExchangeCheckBox.setEnabled(false);
            copyExchangeCheckBox.setToolTipText(
                    STRINGS.getString("No_Exchange_Partition"));
        }

        if (source.getDataPartitionMode() != DataPartitionMode.NotUsed) {
            final String CMD_LINE_FILENAME = "/proc/cmdline";
            try {
                String cmdLine = readOneLineFile(new File(CMD_LINE_FILENAME));
                persistenceBoot = cmdLine.contains(" persistence ");
                LOGGER.log(Level.FINEST,
                        "persistenceBoot: {0}", persistenceBoot);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                        "could not read \"" + CMD_LINE_FILENAME + '\"', ex);
            }

            copyPersistenceCheckBox.setEnabled(true);

            Partition dataPartition = source.getDataPartition();
            if (dataPartition != null) {
                String checkBoxText = STRINGS.getString("Copy_Data_Partition")
                        + " (" + LernstickFileTools.getDataVolumeString(
                                dataPartition.getUsedSpace(false), 1) + ')';
                copyPersistenceCheckBox.setText(checkBoxText);
            }

        } else {
            copyPersistenceCheckBox.setEnabled(false);
            copyPersistenceCheckBox.setToolTipText(
                    STRINGS.getString("No_Data_Partition"));
        }

        installStorageDeviceList.setModel(installStorageDeviceListModel);
        installStorageDeviceRenderer
                = new InstallStorageDeviceRenderer(this, systemSizeEnlarged);
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
            case lernstick:
                isoLabelTextField.setText("lernstick");
                // default to exFAT for exchange partition
                ComboBoxModel model = new DefaultComboBoxModel(
                        new String[]{"exFAT", "FAT32", "NTFS"});
                exchangePartitionFileSystemComboBox.setModel(model);
                exchangePartitionFileSystemComboBox.setSelectedItem("exFAT");
                break;
            case lernstick_pu:
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

        preferences = Preferences.userNodeForPackage(DLCopy.class);
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

        if (sourceDataPartitionMode != null) {
            String selectedItem = null;
            switch (sourceDataPartitionMode) {
                case NotUsed:
                    selectedItem = STRINGS.getString("Not_Used");
                    break;

                case ReadOnly:
                    selectedItem = STRINGS.getString("Read_Only");
                    break;

                case ReadWrite:
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

    private void setSpinnerColums(JSpinner spinner, int columns) {
        JComponent editor = spinner.getEditor();
        JFormattedTextField tf
                = ((JSpinner.DefaultEditor) editor).getTextField();
        tf.setColumns(columns);
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
                    new InstallStorageDeviceAdder(addedPath).execute();
                    break;

                case UPGRADE_SELECTION:
                    new UpgradeStorageDeviceAdder(addedPath).execute();
                    break;

                case REPAIR_SELECTION:
                    new RepairStorageDeviceAdder(addedPath).execute();
                    break;

                default:
                    LOGGER.log(Level.INFO,
                            "device change not handled in state {0}",
                            state);
            }

        } else {
            // check, if a device was removed
            boolean deviceRemoved = false;
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

    /**
     * returns the PartitionState for a given storage and system size
     *
     * @param storageSize the storage size
     * @param systemSize the system size
     * @return the PartitionState for a given storage and system size
     */
    public static PartitionState getPartitionState(
            long storageSize, long systemSize) {
        if (storageSize > (systemSize + (2 * MINIMUM_PARTITION_SIZE))) {
            return PartitionState.EXCHANGE;
        } else if (storageSize > (systemSize + MINIMUM_PARTITION_SIZE)) {
            return PartitionState.PERSISTENCE;
        } else if (storageSize > systemSize) {
            return PartitionState.ONLY_SYSTEM;
        } else {
            return PartitionState.TOO_SMALL;
        }
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
     * @param args the command line arguments
     */
    public static void main(final String args[]) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new DLCopy(args).setVisible(true);
            }
        });
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
        installSelectionHeaderLabel = new javax.swing.JLabel();
        installShowHarddisksCheckBox = new javax.swing.JCheckBox();
        installSelectionCardPanel = new javax.swing.JPanel();
        installListPanel = new javax.swing.JPanel();
        storageDeviceListScrollPane = new javax.swing.JScrollPane();
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
        quickFormatCheckBox = new javax.swing.JCheckBox();
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
        removeExchangeRadioButton = new javax.swing.JRadioButton();
        originalExchangeRadioButton = new javax.swing.JRadioButton();
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
        upgradeBackupTimeLabel = new javax.swing.JLabel();
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
        tmpDirLabel = new javax.swing.JLabel();
        tmpDirTextField = new javax.swing.JTextField();
        tmpDirSelectButton = new javax.swing.JButton();
        freeSpaceLabel = new javax.swing.JLabel();
        freeSpaceTextField = new javax.swing.JTextField();
        writableLabel = new javax.swing.JLabel();
        writableTextField = new javax.swing.JTextField();
        isoLabelLabel = new javax.swing.JLabel();
        isoLabelTextField = new javax.swing.JTextField();
        bootMediumRadioButton = new javax.swing.JRadioButton();
        systemMediumRadioButton = new javax.swing.JRadioButton();
        isoOptionsCardPanel = new javax.swing.JPanel();
        systemMediumPanel = new javax.swing.JPanel();
        isoDataPartitionModeLabel = new javax.swing.JLabel();
        isoDataPartitionModeComboBox = new javax.swing.JComboBox();
        systemMediumPanelx = new javax.swing.JPanel();
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
        setTitle(bundle.getString("DLCopy.title")); // NOI18N
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
        choiceLabel.setText(bundle.getString("DLCopy.choiceLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        choicePanel.add(choiceLabel, gridBagConstraints);

        buttonGridPanel.setLayout(new java.awt.GridBagLayout());

        northWestPanel.setLayout(new java.awt.GridBagLayout());

        installButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dvd2usb.png"))); // NOI18N
        installButton.setText(bundle.getString("DLCopy.installButton.text")); // NOI18N
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
        installLabel.setText(bundle.getString("DLCopy.installLabel.text")); // NOI18N
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
        upgradeButton.setText(bundle.getString("DLCopy.upgradeButton.text")); // NOI18N
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
        upgradeLabel.setText(bundle.getString("DLCopy.upgradeLabel.text")); // NOI18N
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
        toISOButton.setText(bundle.getString("DLCopy.toISOButton.text")); // NOI18N
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
        repairButton.setText(bundle.getString("DLCopy.repairButton.text")); // NOI18N
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

        stepsLabel.setText(bundle.getString("DLCopy.stepsLabel.text")); // NOI18N

        infoStepLabel.setFont(infoStepLabel.getFont().deriveFont(infoStepLabel.getFont().getStyle() | java.awt.Font.BOLD));
        infoStepLabel.setText(bundle.getString("DLCopy.infoStepLabel.text")); // NOI18N

        selectionLabel.setFont(selectionLabel.getFont().deriveFont(selectionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        selectionLabel.setForeground(java.awt.Color.darkGray);
        selectionLabel.setText(bundle.getString("DLCopy.selectionLabel.text")); // NOI18N

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
        infoLabel.setText(bundle.getString("DLCopy.infoLabel.text")); // NOI18N
        infoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        infoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        installInfoPanel.add(infoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(installInfoPanel, "installInfoPanel");

        installSelectionPanel.setLayout(new java.awt.GridBagLayout());

        installSelectionHeaderLabel.setFont(installSelectionHeaderLabel.getFont().deriveFont(installSelectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        installSelectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        installSelectionHeaderLabel.setText(bundle.getString("Select_Install_Target_Storage_Media")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        installSelectionPanel.add(installSelectionHeaderLabel, gridBagConstraints);

        installShowHarddisksCheckBox.setFont(installShowHarddisksCheckBox.getFont().deriveFont(installShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, installShowHarddisksCheckBox.getFont().getSize()-1));
        installShowHarddisksCheckBox.setText(bundle.getString("DLCopy.installShowHarddisksCheckBox.text")); // NOI18N
        installShowHarddisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                installShowHarddisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        installSelectionPanel.add(installShowHarddisksCheckBox, gridBagConstraints);

        installSelectionCardPanel.setLayout(new java.awt.CardLayout());

        installStorageDeviceList.setName("installStorageDeviceList"); // NOI18N
        installStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                installStorageDeviceListValueChanged(evt);
            }
        });
        storageDeviceListScrollPane.setViewportView(installStorageDeviceList);

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("DLCopy.exchangeDefinitionLabel.text")); // NOI18N

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DLCopy.dataDefinitionLabel.text")); // NOI18N

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

        exchangePartitionSizeUnitLabel.setText(bundle.getString("DLCopy.exchangePartitionSizeUnitLabel.text")); // NOI18N
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

        autoNumberPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.autoNumberPanel.border.title"))); // NOI18N
        autoNumberPanel.setLayout(new java.awt.GridBagLayout());

        autoNumberPatternLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberPatternLabel.setText(bundle.getString("DLCopy.autoNumberPatternLabel.text")); // NOI18N
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
        autoNumberStartLabel.setText(bundle.getString("DLCopy.autoNumberStartLabel.text")); // NOI18N
        autoNumberStartLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 2, 0);
        autoNumberPanel.add(autoNumberStartLabel, gridBagConstraints);

        autoNumberStartSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), null, null, Integer.valueOf(1)));
        autoNumberStartSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 0);
        autoNumberPanel.add(autoNumberStartSpinner, gridBagConstraints);

        autoNumberIncrementLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        autoNumberIncrementLabel.setText(bundle.getString("DLCopy.autoNumberIncrementLabel.text")); // NOI18N
        autoNumberIncrementLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 2, 0);
        autoNumberPanel.add(autoNumberIncrementLabel, gridBagConstraints);

        autoNumberIncrementSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
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

        exchangePartitionFileSystemLabel.setText(bundle.getString("DLCopy.exchangePartitionFileSystemLabel.text")); // NOI18N
        exchangePartitionFileSystemLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        exchangePartitionFileSystemPanel.add(exchangePartitionFileSystemLabel, gridBagConstraints);

        exchangePartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "exFAT", "FAT32", "NTFS" }));
        exchangePartitionFileSystemComboBox.setToolTipText(bundle.getString("DLCopy.exchangePartitionFileSystemComboBox.toolTipText")); // NOI18N
        exchangePartitionFileSystemComboBox.setEnabled(false);
        exchangePartitionFileSystemComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exchangePartitionFileSystemComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        exchangePartitionFileSystemPanel.add(exchangePartitionFileSystemComboBox, gridBagConstraints);

        quickFormatCheckBox.setText(bundle.getString("DLCopy.quickFormatCheckBox.text")); // NOI18N
        quickFormatCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        exchangePartitionFileSystemPanel.add(quickFormatCheckBox, gridBagConstraints);

        copyExchangeCheckBox.setText(bundle.getString("DLCopy.copyExchangeCheckBox.text")); // NOI18N
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

        installListTabbedPane.addTab(bundle.getString("DLCopy.exchangePartitionPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png")), exchangePartitionPanel); // NOI18N

        dataPartitionPanel.setLayout(new java.awt.GridBagLayout());

        dataPartitionFileSystemLabel.setText(bundle.getString("DLCopy.dataPartitionFileSystemLabel.text")); // NOI18N
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

        dataPartitionModeLabel.setText(bundle.getString("DLCopy.dataPartitionModeLabel.text")); // NOI18N
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

        installListTabbedPane.addTab(bundle.getString("DLCopy.dataPartitionPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png")), dataPartitionPanel); // NOI18N

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
                    .addComponent(storageDeviceListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
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
                .addComponent(storageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 95, Short.MAX_VALUE)
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
        installSelectionPanel.add(installSelectionCardPanel, gridBagConstraints);

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
        installIndeterminateProgressBar.setString(bundle.getString("DLCopy.installIndeterminateProgressBar.string")); // NOI18N
        installIndeterminateProgressBar.setStringPainted(true);
        installIndeterminateProgressPanel.add(installIndeterminateProgressBar, new java.awt.GridBagConstraints());

        installCardPanel.add(installIndeterminateProgressPanel, "installIndeterminateProgressPanel");

        installCopyPanel.setLayout(new java.awt.GridBagLayout());

        installCopyLabel.setText(bundle.getString("DLCopy.installCopyLabel.text")); // NOI18N
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

        jLabel3.setText(bundle.getString("DLCopy.jLabel3.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        rsyncPanel.add(jLabel3, gridBagConstraints);

        rsyncPogressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        rsyncPogressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        rsyncPanel.add(rsyncPogressBar, gridBagConstraints);

        rsyncTimeLabel.setText(bundle.getString("DLCopy.rsyncTimeLabel.text")); // NOI18N
        rsyncTimeLabel.setName("upgradeBackupTimeLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        rsyncPanel.add(rsyncTimeLabel, gridBagConstraints);

        installCardPanel.add(rsyncPanel, "rsyncPanel");

        cpPanel.setLayout(new java.awt.GridBagLayout());

        jLabel4.setText(bundle.getString("DLCopy.jLabel4.text")); // NOI18N
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
        cpFilenameLabel.setText(bundle.getString("DLCopy.cpFilenameLabel.text")); // NOI18N
        cpFilenameLabel.setName("cpFilenameLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        cpPanel.add(cpFilenameLabel, gridBagConstraints);

        cpTimeLabel.setText(bundle.getString("DLCopy.cpTimeLabel.text")); // NOI18N
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

        installTabbedPane.addTab(bundle.getString("DLCopy.installCurrentPanel.TabConstraints.tabTitle"), installCurrentPanel); // NOI18N

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
        doneLabel.setText(bundle.getString("Done_Message_From_Removable_Boot_Device")); // NOI18N
        doneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        doneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout donePanelLayout = new javax.swing.GroupLayout(donePanel);
        donePanel.setLayout(donePanelLayout);
        donePanelLayout.setHorizontalGroup(
            donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 575, Short.MAX_VALUE)
            .addGroup(donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(donePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(doneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 551, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        donePanelLayout.setVerticalGroup(
            donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 395, Short.MAX_VALUE)
            .addGroup(donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(donePanelLayout.createSequentialGroup()
                    .addGap(83, 83, 83)
                    .addComponent(doneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(146, Short.MAX_VALUE)))
        );

        cardPanel.add(donePanel, "donePanel");

        upgradeInfoPanel.setLayout(new java.awt.GridBagLayout());

        upgradeInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbupgrade.png"))); // NOI18N
        upgradeInfoLabel.setText(bundle.getString("DLCopy.upgradeInfoLabel.text")); // NOI18N
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
        upgradeShowHarddisksCheckBox.setText(bundle.getString("DLCopy.upgradeShowHarddisksCheckBox.text")); // NOI18N
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
        upgradeExchangeDefinitionLabel.setText(bundle.getString("DLCopy.upgradeExchangeDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeExchangeDefinitionLabel, gridBagConstraints);

        upgradeDataDefinitionLabel.setFont(upgradeDataDefinitionLabel.getFont().deriveFont(upgradeDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeDataDefinitionLabel.getFont().getSize()-1));
        upgradeDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        upgradeDataDefinitionLabel.setText(bundle.getString("DLCopy.upgradeDataDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeDataDefinitionLabel, gridBagConstraints);

        upgradeBootDefinitionLabel.setFont(upgradeBootDefinitionLabel.getFont().deriveFont(upgradeBootDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeBootDefinitionLabel.getFont().getSize()-1));
        upgradeBootDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dark_blue_box.png"))); // NOI18N
        upgradeBootDefinitionLabel.setText(bundle.getString("DLCopy.upgradeBootDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 0);
        upgradeSelectionDeviceListPanel.add(upgradeBootDefinitionLabel, gridBagConstraints);

        upgradeOsDefinitionLabel.setFont(upgradeOsDefinitionLabel.getFont().deriveFont(upgradeOsDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeOsDefinitionLabel.getFont().getSize()-1));
        upgradeOsDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        upgradeOsDefinitionLabel.setText(bundle.getString("DLCopy.upgradeOsDefinitionLabel.text")); // NOI18N
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

        upgradeSelectionTabbedPane.addTab(bundle.getString("DLCopy.upgradeSelectionCardPanel.TabConstraints.tabTitle"), upgradeSelectionCardPanel); // NOI18N

        upgradeSystemPartitionCheckBox.setSelected(true);
        upgradeSystemPartitionCheckBox.setText(bundle.getString("DLCopy.upgradeSystemPartitionCheckBox.text")); // NOI18N

        reactivateWelcomeCheckBox.setSelected(true);
        reactivateWelcomeCheckBox.setText(bundle.getString("DLCopy.reactivateWelcomeCheckBox.text")); // NOI18N

        keepPrinterSettingsCheckBox.setSelected(true);
        keepPrinterSettingsCheckBox.setText(bundle.getString("DLCopy.keepPrinterSettingsCheckBox.text")); // NOI18N

        removeHiddenFilesCheckBox.setText(bundle.getString("DLCopy.removeHiddenFilesCheckBox.text")); // NOI18N

        automaticBackupCheckBox.setText(bundle.getString("DLCopy.automaticBackupCheckBox.text")); // NOI18N
        automaticBackupCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                automaticBackupCheckBoxItemStateChanged(evt);
            }
        });

        automaticBackupLabel.setText(bundle.getString("DLCopy.automaticBackupLabel.text")); // NOI18N
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

        automaticBackupRemoveCheckBox.setText(bundle.getString("DLCopy.automaticBackupRemoveCheckBox.text")); // NOI18N
        automaticBackupRemoveCheckBox.setEnabled(false);

        repartitionExchangeOptionsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.repartitionExchangeOptionsPanel.border.title"))); // NOI18N

        exchangeButtonGroup.add(removeExchangeRadioButton);
        removeExchangeRadioButton.setText(bundle.getString("DLCopy.removeExchangeRadioButton.text")); // NOI18N

        exchangeButtonGroup.add(originalExchangeRadioButton);
        originalExchangeRadioButton.setSelected(true);
        originalExchangeRadioButton.setText(bundle.getString("DLCopy.originalExchangeRadioButton.text")); // NOI18N

        exchangeButtonGroup.add(resizeExchangeRadioButton);
        resizeExchangeRadioButton.setText(bundle.getString("DLCopy.resizeExchangeRadioButton.text")); // NOI18N

        resizeExchangeTextField.setColumns(4);
        resizeExchangeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);

        resizeExchangeLabel.setText(bundle.getString("DLCopy.resizeExchangeLabel.text")); // NOI18N

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
                        .addGap(0, 70, Short.MAX_VALUE)))
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

        upgradeDetailsTabbedPane.addTab(bundle.getString("DLCopy.upgradeOptionsPanel.TabConstraints.tabTitle"), upgradeOptionsPanel); // NOI18N

        upgradeOverwritePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        upgradeMoveUpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-up.png"))); // NOI18N
        upgradeMoveUpButton.setToolTipText(bundle.getString("DLCopy.upgradeMoveUpButton.toolTipText")); // NOI18N
        upgradeMoveUpButton.setEnabled(false);
        upgradeMoveUpButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeMoveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeMoveUpButtonActionPerformed(evt);
            }
        });

        upgradeMoveDownButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-down.png"))); // NOI18N
        upgradeMoveDownButton.setToolTipText(bundle.getString("DLCopy.upgradeMoveDownButton.toolTipText")); // NOI18N
        upgradeMoveDownButton.setEnabled(false);
        upgradeMoveDownButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeMoveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeMoveDownButtonActionPerformed(evt);
            }
        });

        sortAscendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/view-sort-ascending.png"))); // NOI18N
        sortAscendingButton.setToolTipText(bundle.getString("DLCopy.sortAscendingButton.toolTipText")); // NOI18N
        sortAscendingButton.setEnabled(false);
        sortAscendingButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        sortAscendingButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sortAscendingButtonActionPerformed(evt);
            }
        });

        sortDescendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/view-sort-descending.png"))); // NOI18N
        sortDescendingButton.setToolTipText(bundle.getString("DLCopy.sortDescendingButton.toolTipText")); // NOI18N
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
        upgradeOverwriteAddButton.setToolTipText(bundle.getString("DLCopy.upgradeOverwriteAddButton.toolTipText")); // NOI18N
        upgradeOverwriteAddButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteAddButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteEditButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-edit.png"))); // NOI18N
        upgradeOverwriteEditButton.setToolTipText(bundle.getString("DLCopy.upgradeOverwriteEditButton.toolTipText")); // NOI18N
        upgradeOverwriteEditButton.setEnabled(false);
        upgradeOverwriteEditButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteEditButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteRemoveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/list-remove.png"))); // NOI18N
        upgradeOverwriteRemoveButton.setToolTipText(bundle.getString("DLCopy.upgradeOverwriteRemoveButton.toolTipText")); // NOI18N
        upgradeOverwriteRemoveButton.setEnabled(false);
        upgradeOverwriteRemoveButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteRemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteRemoveButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteExportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-export.png"))); // NOI18N
        upgradeOverwriteExportButton.setToolTipText(bundle.getString("DLCopy.upgradeOverwriteExportButton.toolTipText")); // NOI18N
        upgradeOverwriteExportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteExportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteExportButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteImportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-import.png"))); // NOI18N
        upgradeOverwriteImportButton.setToolTipText(bundle.getString("DLCopy.upgradeOverwriteImportButton.toolTipText")); // NOI18N
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
                .addComponent(upgradeOverwriteScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 475, Short.MAX_VALUE)
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
                        .addGap(0, 128, Short.MAX_VALUE))
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

        upgradeDetailsTabbedPane.addTab(bundle.getString("DLCopy.upgradeOverwritePanel.TabConstraints.tabTitle"), upgradeOverwritePanel); // NOI18N

        upgradeSelectionTabbedPane.addTab(bundle.getString("DLCopy.upgradeDetailsTabbedPane.TabConstraints.tabTitle"), upgradeDetailsTabbedPane); // NOI18N

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
        upgradeIndeterminateProgressBar.setString(bundle.getString("DLCopy.upgradeIndeterminateProgressBar.string")); // NOI18N
        upgradeIndeterminateProgressBar.setStringPainted(true);
        upgradeIndeterminateProgressPanel.add(upgradeIndeterminateProgressBar, new java.awt.GridBagConstraints());

        upgradeCardPanel.add(upgradeIndeterminateProgressPanel, "upgradeIndeterminateProgressPanel");

        upgradeCopyPanel.setLayout(new java.awt.GridBagLayout());

        upgradeCopyLabel.setText(bundle.getString("DLCopy.upgradeCopyLabel.text")); // NOI18N
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

        upgradeBackupLabel.setText(bundle.getString("DLCopy.upgradeBackupLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        upgradeBackupPanel.add(upgradeBackupLabel, gridBagConstraints);

        upgradeBackupProgressLabel.setText(bundle.getString("DLCopy.upgradeBackupProgressLabel.text")); // NOI18N
        upgradeBackupProgressLabel.setName("upgradeBackupProgressLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        upgradeBackupPanel.add(upgradeBackupProgressLabel, gridBagConstraints);

        upgradeBackupFilenameLabel.setFont(upgradeBackupFilenameLabel.getFont().deriveFont(upgradeBackupFilenameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeBackupFilenameLabel.getFont().getSize()-1));
        upgradeBackupFilenameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        upgradeBackupFilenameLabel.setText(bundle.getString("DLCopy.upgradeBackupFilenameLabel.text")); // NOI18N
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

        upgradeBackupTimeLabel.setText(bundle.getString("DLCopy.upgradeBackupTimeLabel.text")); // NOI18N
        upgradeBackupTimeLabel.setName("upgradeBackupTimeLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        upgradeBackupPanel.add(upgradeBackupTimeLabel, gridBagConstraints);

        upgradeCardPanel.add(upgradeBackupPanel, "upgradeBackupPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        upgradePanel.add(upgradeCardPanel, gridBagConstraints);

        upgradeTabbedPane.addTab(bundle.getString("DLCopy.upgradePanel.TabConstraints.tabTitle"), upgradePanel); // NOI18N

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
        repairInfoLabel.setText(bundle.getString("DLCopy.repairInfoLabel.text")); // NOI18N
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
        repairSelectionHeaderLabel.setText(bundle.getString("DLCopy.repairSelectionHeaderLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        repairSelectionPanel.add(repairSelectionHeaderLabel, gridBagConstraints);

        repairShowHarddisksCheckBox.setFont(repairShowHarddisksCheckBox.getFont().deriveFont(repairShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, repairShowHarddisksCheckBox.getFont().getSize()-1));
        repairShowHarddisksCheckBox.setText(bundle.getString("DLCopy.repairShowHarddisksCheckBox.text")); // NOI18N
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
        repairNoMediaLabel.setText(bundle.getString("DLCopy.repairNoMediaLabel.text")); // NOI18N
        repairNoMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        repairNoMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        repairNoMediaPanel.add(repairNoMediaLabel, new java.awt.GridBagConstraints());

        repairSelectionCardPanel.add(repairNoMediaPanel, "repairNoMediaPanel");

        repairSelectionCountLabel.setText(bundle.getString("DLCopy.repairSelectionCountLabel.text")); // NOI18N

        repairStorageDeviceList.setName("storageDeviceList"); // NOI18N
        repairStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                repairStorageDeviceListValueChanged(evt);
            }
        });
        repairStorageDeviceListScrollPane.setViewportView(repairStorageDeviceList);

        repairExchangeDefinitionLabel.setFont(repairExchangeDefinitionLabel.getFont().deriveFont(repairExchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, repairExchangeDefinitionLabel.getFont().getSize()-1));
        repairExchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        repairExchangeDefinitionLabel.setText(bundle.getString("DLCopy.repairExchangeDefinitionLabel.text")); // NOI18N

        repairDataDefinitionLabel.setFont(repairDataDefinitionLabel.getFont().deriveFont(repairDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, repairDataDefinitionLabel.getFont().getSize()-1));
        repairDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        repairDataDefinitionLabel.setText(bundle.getString("DLCopy.repairDataDefinitionLabel.text")); // NOI18N

        repairOsDefinitionLabel.setFont(repairOsDefinitionLabel.getFont().deriveFont(repairOsDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, repairOsDefinitionLabel.getFont().getSize()-1));
        repairOsDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        repairOsDefinitionLabel.setText(bundle.getString("DLCopy.repairOsDefinitionLabel.text")); // NOI18N

        repairButtonGroup.add(formatDataPartitionRadioButton);
        formatDataPartitionRadioButton.setText(bundle.getString("DLCopy.formatDataPartitionRadioButton.text")); // NOI18N
        formatDataPartitionRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                formatDataPartitionRadioButtonActionPerformed(evt);
            }
        });

        repairButtonGroup.add(removeFilesRadioButton);
        removeFilesRadioButton.setSelected(true);
        removeFilesRadioButton.setText(bundle.getString("DLCopy.removeFilesRadioButton.text")); // NOI18N
        removeFilesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeFilesRadioButtonActionPerformed(evt);
            }
        });

        systemFilesCheckBox.setSelected(true);
        systemFilesCheckBox.setText(bundle.getString("DLCopy.systemFilesCheckBox.text")); // NOI18N

        homeDirectoryCheckBox.setText(bundle.getString("DLCopy.homeDirectoryCheckBox.text")); // NOI18N

        javax.swing.GroupLayout repairSelectionDeviceListPanelLayout = new javax.swing.GroupLayout(repairSelectionDeviceListPanel);
        repairSelectionDeviceListPanel.setLayout(repairSelectionDeviceListPanelLayout);
        repairSelectionDeviceListPanelLayout.setHorizontalGroup(
            repairSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(repairSelectionDeviceListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(repairSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(repairStorageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 551, Short.MAX_VALUE)
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
                .addComponent(repairStorageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 159, Short.MAX_VALUE)
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
        toISOInfoLabel.setText(bundle.getString("DLCopy.toISOInfoLabel.text")); // NOI18N
        toISOInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        toISOInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toISOInfoPanel.add(toISOInfoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(toISOInfoPanel, "toISOInfoPanel");

        tmpDriveInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tmpDriveInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/file_temporary.png"))); // NOI18N
        tmpDriveInfoLabel.setText(bundle.getString("DLCopy.tmpDriveInfoLabel.text")); // NOI18N
        tmpDriveInfoLabel.setIconTextGap(15);

        tmpDirLabel.setText(bundle.getString("DLCopy.tmpDirLabel.text")); // NOI18N

        tmpDirTextField.setColumns(20);
        tmpDirTextField.setText("/media/");

        tmpDirSelectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/fileopen.png"))); // NOI18N
        tmpDirSelectButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        tmpDirSelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tmpDirSelectButtonActionPerformed(evt);
            }
        });

        freeSpaceLabel.setText(bundle.getString("DLCopy.freeSpaceLabel.text")); // NOI18N

        freeSpaceTextField.setEditable(false);
        freeSpaceTextField.setColumns(20);

        writableLabel.setText(bundle.getString("DLCopy.writableLabel.text")); // NOI18N

        writableTextField.setEditable(false);
        writableTextField.setColumns(20);

        isoLabelLabel.setText(bundle.getString("DLCopy.isoLabelLabel.text")); // NOI18N

        isoButtonGroup.add(bootMediumRadioButton);
        bootMediumRadioButton.setText(bundle.getString("DLCopy.bootMediumRadioButton.text")); // NOI18N
        bootMediumRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bootMediumRadioButtonActionPerformed(evt);
            }
        });

        isoButtonGroup.add(systemMediumRadioButton);
        systemMediumRadioButton.setSelected(true);
        systemMediumRadioButton.setText(bundle.getString("DLCopy.systemMediumRadioButton.text")); // NOI18N
        systemMediumRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                systemMediumRadioButtonActionPerformed(evt);
            }
        });

        isoOptionsCardPanel.setLayout(new java.awt.CardLayout());

        systemMediumPanel.setLayout(new java.awt.GridBagLayout());

        isoDataPartitionModeLabel.setText(bundle.getString("DLCopy.isoDataPartitionModeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        systemMediumPanel.add(isoDataPartitionModeLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        systemMediumPanel.add(isoDataPartitionModeComboBox, gridBagConstraints);

        systemMediumPanelx.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.systemMediumPanelx.border.title"))); // NOI18N

        showNotUsedDialogCheckBox.setText(bundle.getString("DLCopy.showNotUsedDialogCheckBox.text")); // NOI18N

        autoStartInstallerCheckBox.setText(bundle.getString("DLCopy.autoStartInstallerCheckBox.text")); // NOI18N

        javax.swing.GroupLayout systemMediumPanelxLayout = new javax.swing.GroupLayout(systemMediumPanelx);
        systemMediumPanelx.setLayout(systemMediumPanelxLayout);
        systemMediumPanelxLayout.setHorizontalGroup(
            systemMediumPanelxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(showNotUsedDialogCheckBox)
            .addComponent(autoStartInstallerCheckBox)
        );
        systemMediumPanelxLayout.setVerticalGroup(
            systemMediumPanelxLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(systemMediumPanelxLayout.createSequentialGroup()
                .addComponent(showNotUsedDialogCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(autoStartInstallerCheckBox))
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        systemMediumPanel.add(systemMediumPanelx, gridBagConstraints);

        isoOptionsCardPanel.add(systemMediumPanel, "systemMediumPanel");

        javax.swing.GroupLayout bootMediumPanelLayout = new javax.swing.GroupLayout(bootMediumPanel);
        bootMediumPanel.setLayout(bootMediumPanelLayout);
        bootMediumPanelLayout.setHorizontalGroup(
            bootMediumPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 366, Short.MAX_VALUE)
        );
        bootMediumPanelLayout.setVerticalGroup(
            bootMediumPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 111, Short.MAX_VALUE)
        );

        isoOptionsCardPanel.add(bootMediumPanel, "bootMediumPanel");

        javax.swing.GroupLayout toISOSelectionPanelLayout = new javax.swing.GroupLayout(toISOSelectionPanel);
        toISOSelectionPanel.setLayout(toISOSelectionPanelLayout);
        toISOSelectionPanelLayout.setHorizontalGroup(
            toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tmpDriveInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, toISOSelectionPanelLayout.createSequentialGroup()
                        .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(writableLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(isoLabelLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(writableTextField)
                                    .addComponent(isoLabelTextField)))
                            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                                .addComponent(freeSpaceLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(freeSpaceTextField))
                            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                                .addComponent(tmpDirLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tmpDirTextField))
                            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(systemMediumRadioButton)
                                    .addComponent(bootMediumRadioButton))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(isoOptionsCardPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tmpDirSelectButton)))
                .addContainerGap())
        );

        toISOSelectionPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {freeSpaceLabel, tmpDirLabel, writableLabel});

        toISOSelectionPanelLayout.setVerticalGroup(
            toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tmpDriveInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(tmpDirLabel)
                    .addComponent(tmpDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tmpDirSelectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(freeSpaceLabel)
                    .addComponent(freeSpaceTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(writableLabel)
                    .addComponent(writableTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(isoLabelTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(isoLabelLabel))
                .addGap(18, 18, 18)
                .addGroup(toISOSelectionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(toISOSelectionPanelLayout.createSequentialGroup()
                        .addComponent(bootMediumRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(systemMediumRadioButton))
                    .addComponent(isoOptionsCardPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        cardPanel.add(toISOSelectionPanel, "toISOSelectionPanel");

        toISOProgressPanel.setLayout(new java.awt.GridBagLayout());

        jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        toISOProgressPanel.add(jLabel6, gridBagConstraints);

        toISOProgressBar.setIndeterminate(true);
        toISOProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        toISOProgressBar.setString(bundle.getString("DLCopy.toISOProgressBar.string")); // NOI18N
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
        isoDoneLabel.setText(bundle.getString("DLCopy.isoDoneLabel.text")); // NOI18N
        isoDoneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout toISODonePanelLayout = new javax.swing.GroupLayout(toISODonePanel);
        toISODonePanel.setLayout(toISODonePanelLayout);
        toISODonePanelLayout.setHorizontalGroup(
            toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 575, Short.MAX_VALUE)
            .addGroup(toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(toISODonePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(isoDoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 551, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        toISODonePanelLayout.setVerticalGroup(
            toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 395, Short.MAX_VALUE)
            .addGroup(toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(toISODonePanelLayout.createSequentialGroup()
                    .addGap(83, 83, 83)
                    .addComponent(isoDoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(116, Short.MAX_VALUE)))
        );

        cardPanel.add(toISODonePanel, "toISODonePanel");

        resultsInfoLabel.setText(bundle.getString("Done_Message_From_Non_Removable_Boot_Device")); // NOI18N

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
            .addComponent(resultsTitledPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 575, Short.MAX_VALUE)
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
                .addComponent(resultsTitledPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 302, Short.MAX_VALUE))
        );

        cardPanel.add(resultsPanel, "resultsPanel");

        previousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/previous.png"))); // NOI18N
        previousButton.setText(bundle.getString("DLCopy.previousButton.text")); // NOI18N
        previousButton.setName("previousButton"); // NOI18N
        previousButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previousButtonActionPerformed(evt);
            }
        });
        previousButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                previousButtonFocusGained(evt);
            }
        });
        previousButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                previousButtonKeyPressed(evt);
            }
        });

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/next.png"))); // NOI18N
        nextButton.setText(bundle.getString("DLCopy.nextButton.text")); // NOI18N
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
                state = State.ISO_INSTALLATION;
                setLabelHighlighted(infoStepLabel, false);
                setLabelHighlighted(selectionLabel, false);
                setLabelHighlighted(executionLabel, true);
                showCard(cardPanel, "toISOProgressPanel");
                previousButton.setEnabled(false);
                nextButton.setEnabled(false);
                ISOCreator isoCreator = new ISOCreator();
                isoCreator.execute();
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
        installStorageDeviceListSelectionChanged();
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
        installStorageDeviceListSelectionChanged();
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
        try {
            if (isUnmountedPersistenceAvailable()) {
                globalShow("executionPanel");
                switchToISOInformation();
            }
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
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
        new InstallStorageDeviceListUpdater().execute();
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
        upgradeStorageDeviceListSelectionChanged();
    }//GEN-LAST:event_upgradeStorageDeviceListValueChanged

    private void upgradeSelectionPanelComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_upgradeSelectionPanelComponentShown
        new UpgradeStorageDeviceListUpdater().execute();
    }//GEN-LAST:event_upgradeSelectionPanelComponentShown

private void upgradeShowHarddisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeShowHarddisksCheckBoxItemStateChanged
    new UpgradeStorageDeviceListUpdater().execute();
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
        updateUpgradeNextButton();
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
        repairStorageDeviceListSelectionChanged();
    }//GEN-LAST:event_repairStorageDeviceListValueChanged

    private void repairSelectionPanelComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_repairSelectionPanelComponentShown
        new RepairStorageDeviceListUpdater().execute();
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
            Object previousValue = upgradeOverwriteListModel.get(previousIndex);
            Object value = upgradeOverwriteListModel.get(selectedIndex);
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
            Object nextValue = upgradeOverwriteListModel.get(nextIndex);
            Object value = upgradeOverwriteListModel.get(selectedIndex);
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
        new RepairStorageDeviceListUpdater().execute();
    }//GEN-LAST:event_repairShowHarddisksCheckBoxItemStateChanged

    private void upgradeOverwriteExportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteExportButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(selectedFile);
                String listString = getUpgradeOverwriteListString();
                fileWriter.write(listString);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            } finally {
                if (fileWriter != null) {
                    try {
                        fileWriter.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "", ex);
                    }
                }
            }
        }
    }//GEN-LAST:event_upgradeOverwriteExportButtonActionPerformed

    private void upgradeOverwriteImportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteImportButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            upgradeOverwriteListModel.clear();
            FileReader fileReader = null;
            BufferedReader bufferedReader = null;
            try {
                File selectedFile = fileChooser.getSelectedFile();
                fileReader = new FileReader(selectedFile);
                bufferedReader = new BufferedReader(fileReader);
                for (String line = bufferedReader.readLine(); line != null;
                        line = bufferedReader.readLine()) {
                    upgradeOverwriteListModel.addElement(line);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            } finally {
                if (fileReader != null) {
                    try {
                        fileReader.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "", ex);
                    }
                }
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "", ex);
                    }
                }
            }
        }
    }//GEN-LAST:event_upgradeOverwriteImportButtonActionPerformed

    private void exchangePartitionFileSystemComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exchangePartitionFileSystemComboBoxActionPerformed
        String exFS
                = exchangePartitionFileSystemComboBox.getSelectedItem().toString();
        quickFormatCheckBox.setEnabled(exFS.equalsIgnoreCase("ntfs"));
    }//GEN-LAST:event_exchangePartitionFileSystemComboBoxActionPerformed

    private void bootMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bootMediumRadioButtonActionPerformed
        showMediumPanel();
    }//GEN-LAST:event_bootMediumRadioButtonActionPerformed

    private void systemMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemMediumRadioButtonActionPerformed
        showMediumPanel();
    }//GEN-LAST:event_systemMediumRadioButtonActionPerformed

    private void showMediumPanel() {
        CardLayout cardLayout = (CardLayout) isoOptionsCardPanel.getLayout();
        if (systemMediumRadioButton.isSelected()) {
            cardLayout.show(isoOptionsCardPanel, "systemMediumPanel");
        } else {
            cardLayout.show(isoOptionsCardPanel, "bootMediumPanel");
        }
    }

    private void setDataPartitionMode(JComboBox comboBox, String imagePath) {
        DataPartitionMode destinationDataPartitionMode = null;
        String dstString = (String) comboBox.getSelectedItem();
        if (dstString.equals(STRINGS.getString("Not_Used"))) {
            destinationDataPartitionMode = DataPartitionMode.NotUsed;
        } else if (dstString.equals(STRINGS.getString("Read_Only"))) {
            destinationDataPartitionMode = DataPartitionMode.ReadOnly;
        } else if (dstString.equals(STRINGS.getString("Read_Write"))) {
            destinationDataPartitionMode = DataPartitionMode.ReadWrite;
        } else {
            LOGGER.log(Level.WARNING,
                    "unsupported data partition mode: {0}", dstString);
            return;
        }
        if (sourceDataPartitionMode == destinationDataPartitionMode) {
            // nothing to do here...
            return;
        }
        xmlBootConfigUtil.setDataPartitionMode(destinationDataPartitionMode,
                imagePath);
    }

    private void resetNextButton() {
        nextButton.setIcon(new ImageIcon(
                getClass().getResource("/ch/fhnw/dlcopy/icons/next.png")));
        nextButton.setText(
                STRINGS.getString("DLCopy.nextButton.text"));
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
        new Repairer().execute();
    }

    private void sortList(boolean ascending) {
        // remember selection before sorting
        Object[] selectedValues = upgradeOverwriteList.getSelectedValues();

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
        for (Object object : list) {
            upgradeOverwriteListModel.addElement(object);
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

    /**
     * must be called whenever the install storage device list changes to
     * execute some updates (e.g. maximum storage device size) and some sanity
     * checks
     */
    private void installStorageDeviceListChanged() {
        int deviceCount = installStorageDeviceListModel.size();
        if (deviceCount == 0) {
            showCard(installSelectionCardPanel, "installNoMediaPanel");
            disableNextButton();
        } else {
            installStorageDeviceRenderer.setMaxSize(
                    getMaxStorageDeviceSize(installStorageDeviceListModel));

            showCard(installSelectionCardPanel, "installListPanel");
            // auto-select single entry
            if (deviceCount == 1) {
                installStorageDeviceList.setSelectedIndex(0);
            }
            installStorageDeviceList.repaint();
            updateInstallNextButton();
        }
    }

    /**
     * must be called whenever the upgrade storage device list changes to
     * execute some updates (e.g. maximum storage device size) and some sanity
     * checks
     */
    private void upgradeStorageDeviceListChanged() {
        int deviceCount = upgradeStorageDeviceListModel.size();
        if (deviceCount == 0) {
            showCard(upgradeSelectionCardPanel, "upgradeNoMediaPanel");
            disableNextButton();
        } else {
            upgradeStorageDeviceRenderer.setMaxSize(
                    getMaxStorageDeviceSize(upgradeStorageDeviceListModel));

            showCard(upgradeSelectionCardPanel,
                    "upgradeSelectionDeviceListPanel");
            // auto-select single entry
            if (deviceCount == 1) {
                upgradeStorageDeviceList.setSelectedIndex(0);
            }
        }
    }

    private void repairStorageDeviceListChanged() {
        int deviceCount = repairStorageDeviceListModel.size();
        if (deviceCount == 0) {
            showCard(repairSelectionCardPanel, "repairNoMediaPanel");
            disableNextButton();
        } else {
            repairStorageDeviceRenderer.setMaxSize(
                    getMaxStorageDeviceSize(repairStorageDeviceListModel));

            showCard(repairSelectionCardPanel,
                    "repairSelectionDeviceListPanel");
            // auto-select single entry
            if (deviceCount == 1) {
                repairStorageDeviceList.setSelectedIndex(0);
            }
        }
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

    private static void writeFile(File file, List<String> lines)
            throws IOException {
        // delete old version of file
        if (file.exists()) {
            file.delete();
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            String lineSeparator = System.getProperty("line.separator");
            for (String line : lines) {
                outputStream.write((line + lineSeparator).getBytes());
            }
            outputStream.flush();
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
        new InstallStorageDeviceListUpdater().execute();

        // update copyExchangeCheckBox
        if (source.hasExchangePartition()) {
            long exchangeSize = source.getExchangePartition()
                    .getUsedSpace(false);
            String dataVolumeString
                    = LernstickFileTools.getDataVolumeString(exchangeSize, 1);
            copyExchangeCheckBox.setText(
                    STRINGS.getString("DLCopy.copyExchangeCheckBox.text")
                    + " (" + dataVolumeString + ')');
        }

        previousButton.setEnabled(true);
        installStorageDeviceListSelectionChanged();
        showCard(cardPanel, "installSelectionPanel");
    }

    private void installStorageDeviceListSelectionChanged() {

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
                        - (BOOT_PARTITION_SIZE * MEGA) - systemSizeEnlarged;
                minOverhead = Math.min(minOverhead, overhead);
                PartitionState partitionState = getPartitionState(
                        device.getSize(),
                        (BOOT_PARTITION_SIZE * MEGA) + systemSizeEnlarged);
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
            int overheadMega = (int) (minOverhead / MEGA);
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

    private void upgradeStorageDeviceListSelectionChanged() {
        updateUpgradeNextButton();
    }

    private void updateUpgradeNextButton() {
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
            } catch (DBusException ex) {
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

    private void repairStorageDeviceListSelectionChanged() {

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

    private List<StorageDevice> getStorageDevices(
            boolean includeHarddisks, boolean includeBootDevice)
            throws IOException, DBusException {

        List<String> partitions = DbusTools.getPartitions();
        List<StorageDevice> storageDevices = new ArrayList<>();

        for (String partition : partitions) {
            LOGGER.log(Level.FINE, "checking partition \"{0}\"", partition);

            if (!includeBootDevice) {
                if (partition.equals(systemSource.getDeviceName())) {
                    // this is the boot device, skip it
                    LOGGER.log(Level.INFO,
                            "skipping {0}, it''s the boot device", partition);
                    continue;
                }
            }

            String pathPrefix = "/org/freedesktop/";
            if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
                pathPrefix += "UDisks/devices/";
            } else {
                pathPrefix += "UDisks2/block_devices/";
            }
            StorageDevice storageDevice = getStorageDevice(
                    pathPrefix + partition, includeHarddisks);
            if (storageDevice != null) {
                if (storageDevice.getType() == StorageDevice.Type.OpticalDisc) {
                    LOGGER.log(Level.INFO,
                            "skipping optical disk {0}", storageDevice);
                } else {
                    LOGGER.log(Level.INFO, "adding {0}", partition);
                    storageDevices.add(storageDevice);
                }
            }
        }

        return storageDevices;
    }

    private StorageDevice getStorageDevice(String path,
            boolean includeHarddisks) throws DBusException {

        LOGGER.log(Level.FINE, "path: {0}", path);

        String busName;
        Boolean isDrive = null;
        Boolean isLoop = null;
        long size;
        String deviceFile;
        if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
            busName = "org.freedesktop.UDisks";
            String interfaceName = "org.freedesktop.UDisks";
            DBus.Properties deviceProperties
                    = dbusSystemConnection.getRemoteObject(
                            busName, path, DBus.Properties.class);
            isDrive = deviceProperties.Get(interfaceName, "DeviceIsDrive");
            isLoop = deviceProperties.Get(interfaceName, "DeviceIsLinuxLoop");
            UInt64 size64 = deviceProperties.Get(interfaceName, "DeviceSize");
            size = size64.longValue();
            deviceFile = deviceProperties.Get(interfaceName, "DeviceFile");
        } else {
            String prefix = "org.freedesktop.UDisks2.";
            try {
                List<String> interfaceNames = DbusTools.getInterfaceNames(path);
                isDrive = !interfaceNames.contains(prefix + "Partition");
                isLoop = interfaceNames.contains(prefix + "Loop");
            } catch (IOException | SAXException |
                    ParserConfigurationException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            size = DbusTools.getLongProperty(path, prefix + "Block", "Size");
            // the device is a char array terminated with a 0 byte
            // we need to remove the 0 byte!
            byte[] array = DbusTools.getByteArrayProperty(path,
                    prefix + "Block", "Device");
            deviceFile = new String(DbusTools.removeNullByte(array));
        }

        LOGGER.log(Level.FINE, "{0} isDrive: {1}", new Object[]{path, isDrive});
        LOGGER.log(Level.FINE, "{0} isLoop: {1}", new Object[]{path, isLoop});
        LOGGER.log(Level.FINE, "{0} size: {1}", new Object[]{path, size});
        LOGGER.log(Level.FINE, "{0} deviceFile: {1}",
                new Object[]{path, deviceFile});

        // early return for non-drives
        // (partitions, loop devices, empty optical drives, ...)
        if ((!isDrive) || isLoop || (size <= 0)) {
            return null;
        }

        StorageDevice storageDevice
                = new StorageDevice(deviceFile.substring(5), systemSize);

        if ((storageDevice.getType() == StorageDevice.Type.HardDrive)
                && !includeHarddisks) {
            return null;
        } else {
            return storageDevice;
        }
    }

    private static String readOneLineFile(File file) throws IOException {
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader(file);
            bufferedReader = new BufferedReader(fileReader);
            String string = bufferedReader.readLine();
            if (string != null) {
                string = string.trim();
            }
            bufferedReader.close();
            return string;
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
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
            PartitionState partitionState
                    = getPartitionState(device.getSize(), systemSizeEnlarged);
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

    private void umountPartitions(String device) throws IOException {
        LOGGER.log(Level.FINEST, "umountPartitions({0})", device);
        List<String> mounts
                = LernstickFileTools.readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String mountedPartition = mount.split(" ")[0];
            if (mountedPartition.startsWith(device)) {
                umount(mountedPartition);
            }
        }
    }

    private boolean isMounted(String device) throws IOException {
        List<String> mounts
                = LernstickFileTools.readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String mountedPartition = mount.split(" ")[0];
            if (mountedPartition.startsWith(device)) {
                return true;
            }
        }
        return false;
    }

    private void swapoffFile(String device, String swapLine)
            throws IOException {

        SwapInfo swapInfo = new SwapInfo(swapLine);
        String swapFile = swapInfo.getFile();
        long remainingFreeMem = swapInfo.getRemainingFreeMemory();

        boolean disableSwap = true;
        if (remainingFreeMem < MINIMUM_FREE_MEMORY) {
            // deactivating the swap file is dangerous
            // show a warning dialog and let the user decide
            String warningMessage = STRINGS.getString("Warning_Swapoff_File");
            String freeMem = LernstickFileTools.getDataVolumeString(
                    remainingFreeMem, 0);
            warningMessage = MessageFormat.format(
                    warningMessage, swapFile, device, freeMem);
            int selection = JOptionPane.showConfirmDialog(this,
                    warningMessage, STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (selection != JOptionPane.YES_OPTION) {
                disableSwap = false;
            }
        }

        if (disableSwap) {
            int exitValue = processExecutor.executeProcess("swapoff", swapFile);
            if (exitValue != 0) {
                String errorMessage = STRINGS.getString("Error_Swapoff_File");
                errorMessage = MessageFormat.format(errorMessage, swapFile);
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
            }
        }
    }

    private void swapoffPartition(String device, String swapLine)
            throws IOException {

        SwapInfo swapInfo = new SwapInfo(swapLine);
        String swapFile = swapInfo.getFile();
        long remainingFreeMem = swapInfo.getRemainingFreeMemory();

        boolean disableSwap = true;
        if (remainingFreeMem < MINIMUM_FREE_MEMORY) {
            // deactivating the swap file is dangerous
            // show a warning dialog and let the user decide
            String warningMessage
                    = STRINGS.getString("Warning_Swapoff_Partition");
            String freeMem = LernstickFileTools.getDataVolumeString(
                    remainingFreeMem, 0);
            warningMessage = MessageFormat.format(
                    warningMessage, swapFile, device, freeMem);
            int selection = JOptionPane.showConfirmDialog(this,
                    warningMessage, STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (selection != JOptionPane.YES_OPTION) {
                disableSwap = false;
            }
        }

        if (disableSwap) {
            int exitValue = processExecutor.executeProcess("swapoff", swapFile);
            if (exitValue != 0) {
                String errorMessage
                        = STRINGS.getString("Error_Swapoff_Partition");
                errorMessage = MessageFormat.format(errorMessage, swapFile);
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
            }
        }
    }

    private boolean umount(Partition partition) throws DBusException {
        // early return
        if (!partition.isMounted()) {
            LOGGER.log(Level.INFO,
                    "{0} was NOT mounted...", partition.getDeviceAndNumber());
            return true;
        }

        if (partition.umount()) {
            LOGGER.log(Level.INFO, "{0} was successfully umounted",
                    partition.getDeviceAndNumber());
            return true;
        } else {
            String errorMessage = STRINGS.getString("Error_Umount");
            errorMessage = MessageFormat.format(errorMessage,
                    "/dev/" + partition.getDeviceAndNumber());
            showErrorMessage(errorMessage);
            return false;
        }
    }

    private void umount(String partition) throws IOException {
        // check if a swapfile is in use on this partition
        List<String> mounts
                = LernstickFileTools.readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            String device = tokens[0];
            String mountPoint = tokens[1];
            if (device.equals(partition) || mountPoint.equals(partition)) {
                List<String> swapLines = LernstickFileTools.readFile(
                        new File("/proc/swaps"));
                for (String swapLine : swapLines) {
                    if (swapLine.startsWith(mountPoint)) {
                        // deactivate swapfile
                        swapoffFile(device, swapLine);
                    }
                }
            }
        }

        int exitValue = processExecutor.executeProcess("umount", partition);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Error_Umount");
            errorMessage = MessageFormat.format(errorMessage, partition);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private static File createTempDir(String prefix) throws IOException {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File tmpFile = File.createTempFile(prefix, "", tmpDir);
        tmpFile.delete();
        tmpFile.mkdir();
        tmpFile.deleteOnExit();
        return tmpFile;
    }

    private static class Partitions {

        private final int exchangeMB;
        private final int persistenceMB;

        public Partitions(int exchangeMB, int persistenceMB) {
            this.exchangeMB = exchangeMB;
            this.persistenceMB = persistenceMB;
        }

        /**
         * returns the size of the exchange partition (in MiB)
         *
         * @return the size of the exchange partition (in MiB)
         */
        public int getExchangeMB() {
            return exchangeMB;
        }

        /**
         * returns the size of the persistence partition (in MiB)
         *
         * @return the size of the persistence partition (in MiB)
         */
        public int getPersistenceMB() {
            return persistenceMB;
        }
    }

    private Partitions getPartitions(StorageDevice storageDevice) {
        long size = storageDevice.getSize();
        long overhead
                = size - (BOOT_PARTITION_SIZE * MEGA) - systemSizeEnlarged;
        int overheadMB = (int) (overhead / MEGA);
        PartitionState partitionState
                = getPartitionState(size, systemSizeEnlarged);
        switch (partitionState) {
            case TOO_SMALL:
                return null;

            case ONLY_SYSTEM:
                return new Partitions(0, 0);

            case EXCHANGE:
                int exchangeMB = 0;
                if (state == State.UPGRADE) {
                    if (originalExchangeRadioButton.isSelected()) {
                        Partition exchangePartition
                                = storageDevice.getExchangePartition();
                        if (exchangePartition != null) {
                            LOGGER.log(Level.INFO, "exchangePartition: {0}",
                                    exchangePartition);
                            exchangeMB = (int) (exchangePartition.getSize()
                                    / MEGA);
                        }
                    } else if (resizeExchangeRadioButton.isSelected()) {
                        String newSizeText = resizeExchangeTextField.getText();
                        try {
                            exchangeMB = Integer.parseInt(newSizeText);
                        } catch (NumberFormatException ex) {
                            LOGGER.log(Level.WARNING, "", ex);
                        }
                    }
                } else {
                    exchangeMB = exchangePartitionSizeSlider.getValue();
                }
                LOGGER.log(Level.INFO, "exchangeMB = {0}", exchangeMB);
                int persistenceMB = overheadMB - exchangeMB;
                return new Partitions(exchangeMB, persistenceMB);

            case PERSISTENCE:
                return new Partitions(0, overheadMB);

            default:
                LOGGER.log(Level.SEVERE,
                        "unsupported partitionState \"{0}\"", partitionState);
                return null;
        }
    }

    private void showCard(Container container, String cardName) {
        CardLayout cardLayout = (CardLayout) container.getLayout();
        cardLayout.show(container, cardName);
    }

    private void formatBootAndSystemPartition(
            String bootDevice, String systemDevice) throws IOException {
        int exitValue = processExecutor.executeProcess(
                "/sbin/mkfs.vfat", "-n", Partition.BOOT_LABEL, bootDevice);
        if (exitValue != 0) {
            LOGGER.severe(processExecutor.getOutput());
            String errorMessage
                    = STRINGS.getString("Error_Create_Boot_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
        exitValue = processExecutor.executeProcess(
                "/sbin/mkfs.ext4", "-L", systemPartitionLabel, systemDevice);
        if (exitValue != 0) {
            LOGGER.severe(processExecutor.getOutput());
            String errorMessage
                    = STRINGS.getString("Error_Create_System_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private void isolinuxToSyslinux(String mountPoint) throws IOException {
        final String isolinuxPath = mountPoint + "/isolinux";
        if (new File(isolinuxPath).exists()) {
            LOGGER.info("replacing isolinux with syslinux");
            final String syslinuxPath = mountPoint + "/syslinux";
            moveFile(isolinuxPath, syslinuxPath);
            moveFile(syslinuxPath + "/isolinux.cfg",
                    syslinuxPath + "/syslinux.cfg");

            // replace "isolinux" with "syslinux" in some files
            Pattern pattern = Pattern.compile("isolinux");
            replaceText(syslinuxPath + "/exithelp.cfg", pattern, "syslinux");
            replaceText(syslinuxPath + "/stdmenu.cfg", pattern, "syslinux");
            replaceText(syslinuxPath + "/syslinux.cfg", pattern, "syslinux");

            // remove  boot.cat
            String bootCatFileName = syslinuxPath + "/boot.cat";
            File bootCatFile = new File(bootCatFileName);
            if (!bootCatFile.delete()) {
                showErrorMessage("Could not delete " + bootCatFileName);
            }

            // update md5sum.txt
            String md5sumFileName = mountPoint + "/md5sum.txt";
            replaceText(md5sumFileName, pattern, "syslinux");
            File md5sumFile = new File(md5sumFileName);
            if (md5sumFile.exists()) {
                List<String> lines = LernstickFileTools.readFile(md5sumFile);
                for (int i = lines.size() - 1; i >= 0; i--) {
                    String line = lines.get(i);
                    if (line.contains("xmlboot.config")
                            || line.contains("grub.cfg")) {
                        lines.remove(i);
                    }
                }
                writeFile(md5sumFile, lines);
                processExecutor.executeProcess("sync");
            } else {
                LOGGER.log(Level.WARNING,
                        "file \"{0}\" does not exist!", md5sumFileName);
            }

        } else {
            // boot device is probably a hard disk
            LOGGER.info(
                    "isolinux directory does not exist -> no renaming");
        }
    }

    private void makeBootable(String device, Partition destinationBootPartition)
            throws IOException, DBusException {

        String bootDevice
                = "/dev/" + destinationBootPartition.getDeviceAndNumber();
        int exitValue = source.installSyslinux(bootDevice);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Make_Bootable_Failed");
            errorMessage = MessageFormat.format(
                    errorMessage, bootDevice, processExecutor.getOutput());
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
        exitValue = processExecutor.executeScript("cat " + source.getMbrPath() + " > " + device + '\n'
                + "sync");
        if (exitValue != 0) {
            String errorMessage = "could not copy syslinux Master Boot Record "
                    + "to device \"" + device + '\"';
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private void playNotifySound() {
        URL url = getClass().getResource("/ch/fhnw/dlcopy/KDE_Notify.wav");
        AudioClip clip = Applet.newAudioClip(url);
        clip.play();
    }

    private void showErrorMessage(String errorMessage) {
        JOptionPane.showMessageDialog(this, errorMessage,
                STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
    }

    private void replaceText(String fileName, Pattern pattern,
            String replacement) throws IOException {
        File file = new File(fileName);
        if (file.exists()) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                        "replacing pattern \"{0}\" with \"{1}\" in file \"{2}\"",
                        new Object[]{pattern.pattern(), replacement, fileName});
            }
            List<String> lines = LernstickFileTools.readFile(file);
            boolean changed = false;
            for (int i = 0, size = lines.size(); i < size; i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    LOGGER.log(Level.INFO, "line \"{0}\" matches", line);
                    lines.set(i, matcher.replaceAll(replacement));
                    changed = true;
                } else {
                    LOGGER.log(Level.INFO, "line \"{0}\" does NOT match", line);
                }
            }
            if (changed) {
                writeFile(file, lines);
            }
        } else {
            LOGGER.log(Level.WARNING, "file \"{0}\" does not exist!", fileName);
        }
    }

    private void moveFile(String source, String destination)
            throws IOException {
        File sourceFile = new File(source);
        if (!sourceFile.exists()) {
            String errorMessage
                    = STRINGS.getString("Error_File_Does_Not_Exist");
            errorMessage = MessageFormat.format(errorMessage, source);
            throw new IOException(errorMessage);
        }
        if (!sourceFile.renameTo(new File(destination))) {
            String errorMessage = STRINGS.getString("Error_File_Move");
            errorMessage = MessageFormat.format(
                    errorMessage, source, destination);
            throw new IOException(errorMessage);
        }
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
            Partitions partitions = getPartitions(storageDevice);
            if (!checkPersistence(partitions)) {
                return;
            }
            if (!checkExchange(partitions)) {
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
        new Installer().start();
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

        Object[] selectedDevices = upgradeStorageDeviceList.getSelectedValues();
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

        state = State.UPGRADE;
        // let's start...
        new Upgrader().execute();
    }

    private boolean checkExchange(Partitions partitions) throws IOException {
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
                = (long) partitions.getExchangeMB() * (long) MEGA;
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
        // check that persistence is available
        if (DataPartitionMode.NotUsed == source.getDataPartitionMode()) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_No_Persistence"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // ensure that the persistence partition is not mounted read-write
        String dataPartitionDevice
                = "/dev/" + source.getDataPartition().getDeviceAndNumber();
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

    private boolean checkPersistence(Partitions partitions)
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
                = (long) partitions.getPersistenceMB() * (long) MEGA;
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
        showCard(cardPanel, "toISOInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.ISO_INFORMATION;
    }

    private void switchToUSBInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        executionLabel.setText(STRINGS.getString("Installation_Label"));
        showCard(cardPanel, "installInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.INSTALL_INFORMATION;
    }

    private void switchToUpgradeInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        executionLabel.setText(STRINGS.getString("Upgrade_Label"));
        showCard(cardPanel, "upgradeInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.UPGRADE_INFORMATION;
    }

    private void switchToRepairInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        executionLabel.setText(STRINGS.getString("Repair_Label"));
        showCard(cardPanel, "repairInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.REPAIR_INFORMATION;
    }

    private void switchToUpgradeSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.UPGRADE_SELECTION;
        showCard(cardPanel, "upgradeSelectionPanel");
        enableNextButton();
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

    private void formatPersistencePartition(String device)
            throws DBusException, IOException {

        // make sure that the partition is unmounted
        if (isMounted(device)) {
            umount(device);
        }

        // formatting
        String fileSystem
                = dataPartitionFilesystemComboBox.getSelectedItem().toString();
        int exitValue = processExecutor.executeProcess(
                "/sbin/mkfs." + fileSystem,
                "-L", Partition.PERSISTENCE_LABEL, device);
        if (exitValue != 0) {
            LOGGER.severe(processExecutor.getOutput());
            String errorMessage
                    = STRINGS.getString("Error_Create_Data_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // tuning
        exitValue = processExecutor.executeProcess(
                "/sbin/tune2fs", "-m", "0", "-c", "0", "-i", "0", device);
        if (exitValue != 0) {
            LOGGER.severe(processExecutor.getOutput());
            String errorMessage
                    = STRINGS.getString("Error_Tune_Data_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // We have to wait a little for dbus to get to know the new filesystem.
        // Otherwise we will sometimes get the following exception in the calls
        // below:
        // org.freedesktop.dbus.exceptions.DBusExecutionException:
        // No such interface 'org.freedesktop.UDisks2.Filesystem'
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        // create default persistence configuration file
        Partition persistencePartition
                = Partition.getPartitionFromDeviceAndNumber(
                        device.substring(5), systemSize);
        String mountPath = persistencePartition.mount().getMountPath();
        if (mountPath == null) {
            throw new IOException("could not mount persistence partition");
        }
        writePersistenceConf(mountPath);
        persistencePartition.umount();
    }

    private void writePersistenceConf(String mountPath) throws IOException {
        try (FileWriter writer
                = new FileWriter(mountPath + "/persistence.conf")) {
            writer.write("/ union,source=.\n");
            writer.flush();
        }
    }

    private void addDeviceToList(JList list, StorageDevice newDevice) {
        DefaultListModel listModel = (DefaultListModel) list.getModel();

        // put new device into a "sorted" position
        List<StorageDevice> deviceList = new ArrayList<>();
        Object[] entries = listModel.toArray();
        for (Object entry : entries) {
            deviceList.add((StorageDevice) entry);
        }
        deviceList.add(newDevice);
        Collections.sort(deviceList);
        Object[] selectedValues = list.getSelectedValues();
        listModel.clear();
        for (StorageDevice device : deviceList) {
            listModel.addElement(device);
        }

        // try to restore the previous selection
        for (Object selectedValue : selectedValues) {
            int index = deviceList.indexOf(selectedValue);
            if (index != -1) {
                list.addSelectionInterval(index, index);
            }
        }
    }

    private CopyJobsInfo prepareBootAndSystemCopyJobs(
            StorageDevice storageDevice,
            Partition destinationBootPartition,
            Partition destinationExchangePartition,
            Partition destinationSystemPartition,
            String destinationExchangePartitionFileSystem)
            throws DBusException {

        String destinationBootPath
                = destinationBootPartition.mount().getMountPath();
        String destinationSystemPath
                = destinationSystemPartition.mount().getMountPath();
        boolean sourceBootTempMounted = false;

        Source bootCopyJobSource = source.getBootCopySource();
        Source systemCopyJobSource = source.getSystemCopySource();

        CopyJob bootCopyJob = new CopyJob(
                new Source[]{bootCopyJobSource},
                new String[]{destinationBootPath});

        // Only if we have an FAT32 exchange partition on a removable media we
        // have to copy the boot files also to the exchange partition.
        CopyJob bootFilesCopyJob = null;
        if ((destinationExchangePartition != null)
                && (storageDevice.isRemovable())) {
            if ("fat32".equalsIgnoreCase(
                    destinationExchangePartitionFileSystem)) {
                String destinationExchangePath
                        = destinationExchangePartition.mount().getMountPath();
                bootFilesCopyJob = new CopyJob(
                        new Source[]{source.getExchangeBootCopySource()},
                        new String[]{destinationExchangePath});
            }
        }

        CopyJob systemCopyJob = new CopyJob(
                new Source[]{systemCopyJobSource},
                new String[]{destinationSystemPath});

        return new CopyJobsInfo(sourceBootTempMounted, destinationBootPath,
                destinationSystemPath, bootCopyJob, bootFilesCopyJob,
                systemCopyJob);
    }

    private void hideBootFiles(CopyJob bootFilesCopyJob,
            String destinationExchangePath) {

        Source bootFilesSource = bootFilesCopyJob.getSources()[0];
        String[] bootFiles = bootFilesSource.getBaseDirectory().list();

        if (bootFiles != null) {
            // use FAT attributes to hide boot files in Windows
            for (String bootFile : bootFiles) {
                processExecutor.executeProcess("fatattr", "+h",
                        destinationExchangePath + '/' + bootFile);
            }

            // use ".hidden" file to hide boot files in OS X
            String osxHiddenFilePath = destinationExchangePath + "/.hidden";
            try (FileWriter fileWriter = new FileWriter(osxHiddenFilePath)) {
                String lineSeperator = System.lineSeparator();
                for (String bootFile : bootFiles) {
                    fileWriter.write(bootFile + lineSeperator);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "", ex);
            }

            // use FAT attributes again to hide OS X ".hidden" file in Windows
            processExecutor.executeProcess("fatattr", "+h", osxHiddenFilePath);
        }
    }

    private void copyToStorageDevice(FileCopier fileCopier,
            StorageDevice storageDevice, String exchangePartitionLabel,
            final Container cardPanel, String copyPanelName, JLabel copyLabel,
            FileCopierPanel fileCopierPanel,
            final String indeterminatePanelName,
            final JProgressBar indeterminateProgressBar)
            throws InterruptedException, IOException, DBusException {

        // determine size and state
        String device = "/dev/" + storageDevice.getDevice();
        long size = storageDevice.getSize();
        Partitions partitions = getPartitions(storageDevice);
        int exchangeMB = partitions.getExchangeMB();
        PartitionState partitionState
                = getPartitionState(size, systemSizeEnlarged);

        boolean sdDevice = (storageDevice.getType()
                == StorageDevice.Type.SDMemoryCard);

        // determine devices
        String destinationBootDevice = null;
        String destinationExchangeDevice = null;
        String destinationDataDevice = null;
        String destinationSystemDevice;
        switch (partitionState) {
            case ONLY_SYSTEM:
                destinationBootDevice = device + (sdDevice ? "p1" : '1');
                destinationSystemDevice = device + (sdDevice ? "p2" : '2');
                break;

            case PERSISTENCE:
                destinationBootDevice = device + (sdDevice ? "p1" : '1');
                destinationDataDevice = device + (sdDevice ? "p2" : '2');
                destinationSystemDevice = device + (sdDevice ? "p3" : '3');
                break;

            case EXCHANGE:
                if (exchangeMB == 0) {
                    destinationBootDevice = device + (sdDevice ? "p1" : '1');
                    destinationDataDevice = device + (sdDevice ? "p2" : '2');
                    destinationSystemDevice = device + (sdDevice ? "p3" : '3');
                } else {
                    if (storageDevice.isRemovable()) {
                        destinationExchangeDevice
                                = device + (sdDevice ? "p1" : '1');
                        destinationBootDevice
                                = device + (sdDevice ? "p2" : '2');
                    } else {
                        destinationBootDevice
                                = device + (sdDevice ? "p1" : '1');
                        destinationExchangeDevice
                                = device + (sdDevice ? "p2" : '2');
                    }
                    if (partitions.getPersistenceMB() == 0) {
                        destinationSystemDevice
                                = device + (sdDevice ? "p3" : '3');
                    } else {
                        destinationDataDevice
                                = device + (sdDevice ? "p3" : '3');
                        destinationSystemDevice
                                = device + (sdDevice ? "p4" : '4');
                    }
                }
                break;

            default:
                String errorMessage = "unsupported partitionState \""
                        + partitionState + '\"';
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
        }

        // create all necessary partitions
        try {
            createPartitions(storageDevice, partitions, size, exchangeMB,
                    partitionState, destinationExchangeDevice,
                    exchangePartitionLabel, destinationDataDevice,
                    destinationBootDevice, destinationSystemDevice, cardPanel,
                    indeterminatePanelName, indeterminateProgressBar);
        } catch (IOException iOException) {
            // On some Corsair Flash Voyager GT drives the first sfdisk try
            // failes with the following output:
            // ---------------
            // Checking that no-one is using this disk right now ...
            // OK
            // Warning: The partition table looks like it was made
            //       for C/H/S=*/78/14 (instead of 15272/64/32).
            //
            // For this listing I'll assume that geometry.
            //
            // Disk /dev/sdc: 15272 cylinders, 64 heads, 32 sectors/track
            // Old situation:
            // Units = mebibytes of 1048576 bytes, blocks of 1024 bytes, counting from 0
            //
            //    Device Boot Start   End    MiB    #blocks   Id  System
            // /dev/sdc1         3+ 15271  15269-  15634496    c  W95 FAT32 (LBA)
            //                 start: (c,h,s) expected (7,30,1) found (1,0,1)
            //                 end: (c,h,s) expected (1023,77,14) found (805,77,14)
            // /dev/sdc2         0      -      0          0    0  Empty
            // /dev/sdc3         0      -      0          0    0  Empty
            // /dev/sdc4         0      -      0          0    0  Empty
            // New situation:
            // Units = mebibytes of 1048576 bytes, blocks of 1024 bytes, counting from 0
            //
            //    Device Boot Start   End    MiB    #blocks   Id  System
            // /dev/sdc1         0+  1023   1024-   1048575+   c  W95 FAT32 (LBA)
            // /dev/sdc2      1024  11008   9985   10224640   83  Linux
            // /dev/sdc3   * 11009  15271   4263    4365312    c  W95 FAT32 (LBA)
            // /dev/sdc4         0      -      0          0    0  Empty
            // BLKRRPART: Das Gerät oder die Ressource ist belegt
            // The command to re-read the partition table failed.
            // Run partprobe(8), kpartx(8) or reboot your system now,
            // before using mkfs
            // If you created or changed a DOS partition, /dev/foo7, say, then use dd(1)
            // to zero the first 512 bytes:  dd if=/dev/zero of=/dev/foo7 bs=512 count=1
            // (See fdisk(8).)
            // Successfully wrote the new partition table
            //
            // Re-reading the partition table ...
            // ---------------
            // Strangely, even though sfdisk exits with zero (success) the
            // partitions are *NOT* correctly created the first time. Even
            // more strangely, it always works the second time. Therefore
            // we automatically retry once more in case of an error.
            createPartitions(storageDevice, partitions, size, exchangeMB,
                    partitionState, destinationExchangeDevice,
                    exchangePartitionLabel, destinationDataDevice,
                    destinationBootDevice, destinationSystemDevice, cardPanel,
                    indeterminatePanelName, indeterminateProgressBar);
        }

        // Here have to trigger a rescan of the device partitions. Otherwise
        // udisks sometimes just doesn't know about the new partitions and we
        // will later get exceptions similar to this one:
        // org.freedesktop.dbus.exceptions.DBusExecutionException:
        // No such interface 'org.freedesktop.UDisks2.Filesystem'
        processExecutor.executeProcess("partprobe", device);
        // Sigh... even after partprobe exits, we have to give udisks even more
        // time to get its act together and finally know about the new
        // partitions.
        try {
            // 5 seconds were not enough!
            TimeUnit.SECONDS.sleep(7);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        // the partitions now really exist
        // -> instantiate them as objects
        Partition destinationExchangePartition
                = (destinationExchangeDevice == null) ? null
                        : Partition.getPartitionFromDeviceAndNumber(
                                destinationExchangeDevice.substring(5),
                                systemSize);

        Partition destinationDataPartition
                = (destinationDataDevice == null) ? null
                        : Partition.getPartitionFromDeviceAndNumber(
                                destinationDataDevice.substring(5), systemSize);

        Partition destinationBootPartition
                = Partition.getPartitionFromDeviceAndNumber(
                        destinationBootDevice.substring(5), systemSize);

        Partition destinationSystemPartition
                = Partition.getPartitionFromDeviceAndNumber(
                        destinationSystemDevice.substring(5), systemSize);

        // copy operating system files
        copyExchangeBootAndSystem(fileCopier, storageDevice,
                destinationExchangePartition, destinationBootPartition,
                destinationSystemPartition, cardPanel, copyPanelName, copyLabel,
                fileCopierPanel, indeterminatePanelName,
                indeterminateProgressBar);

        // copy persistence layer
        if ((state != State.UPGRADE) && (destinationDataPartition != null)) {
            copyPersistence(destinationDataPartition);
        }

        // make storage device bootable
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(cardPanel, indeterminatePanelName);
                indeterminateProgressBar.setString(
                        STRINGS.getString("Writing_Boot_Sector"));
            }
        });
        makeBootable(device, destinationBootPartition);

        if (!umount(destinationBootPartition)) {
            String errorMessage = "could not umount destination boot partition";
            throw new IOException(errorMessage);
        }

        if (!umount(destinationSystemPartition)) {
            String errorMessage
                    = "could not umount destination system partition";
            throw new IOException(errorMessage);
        }
        source.unmountTmpPartitions();
    }

    private void createPartitions(StorageDevice storageDevice,
            Partitions partitions, long storageDeviceSize, int exchangeMB,
            final PartitionState partitionState, String exchangeDevice,
            String exchangePartitionLabel, String persistenceDevice,
            String bootDevice, String systemDevice, final Container cardPanel,
            final String cardName, final JProgressBar progressBar)
            throws InterruptedException, IOException, DBusException {

        // update GUI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(cardPanel, cardName);
                boolean severalPartitions
                        = (partitionState == PartitionState.PERSISTENCE)
                        || (partitionState == PartitionState.EXCHANGE);
                progressBar.setString(STRINGS.getString(severalPartitions
                        ? "Creating_File_Systems" : "Creating_File_System"));
            }
        });

        String device = "/dev/" + storageDevice.getDevice();

        // determine exact partition sizes
        long overhead = storageDeviceSize - systemSizeEnlarged;
        int persistenceMB = partitions.getPersistenceMB();
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "size of {0} = {1} Byte\n"
                    + "overhead = {2} Byte\n"
                    + "exchangeMB = {3} MiB\n"
                    + "persistenceMB = {4} MiB",
                    new Object[]{device, storageDeviceSize, overhead,
                        exchangeMB, persistenceMB
                    });
        }

        // assemble partition command
        List<String> partedCommandList = new ArrayList<>();
        partedCommandList.add("/sbin/parted");
        partedCommandList.add("-s");
        partedCommandList.add("-a");
        partedCommandList.add("optimal");
        partedCommandList.add(device);

//            // list of "rm" commands must be inversely sorted, otherwise
//            // removal of already existing partitions will fail when storage
//            // device has logical partitions in extended partitions (the logical
//            // partitions are no longer found when the extended partition is
//            // already removed)
//            List<String> partitionNumbers = new ArrayList<String>();
//            for (Partition partition : storageDevice.getPartitions()) {
//                partitionNumbers.add(String.valueOf(partition.getNumber()));
//            }
//            Collections.sort(partitionNumbers);
//            for (int i = partitionNumbers.size() - 1; i >=0; i--) {
//                partedCommandList.add("rm");
//                partedCommandList.add(partitionNumbers.get(i));
//            }
        switch (partitionState) {
            case ONLY_SYSTEM:
                // create two partitions: boot, system
                String bootBorder = BOOT_PARTITION_SIZE + "MiB";
                mkpart(partedCommandList, "0%", bootBorder);
                mkpart(partedCommandList, bootBorder, "100%");
                setFlag(partedCommandList, "1", "boot", "on");
                setFlag(partedCommandList, "1", "lba", "on");
                break;

            case PERSISTENCE:
                // create three partitions: boot, persistence, system
                bootBorder = BOOT_PARTITION_SIZE + "MiB";
                String persistenceBorder
                        = (BOOT_PARTITION_SIZE + persistenceMB) + "MiB";
                mkpart(partedCommandList, "0%", bootBorder);
                mkpart(partedCommandList, bootBorder, persistenceBorder);
                mkpart(partedCommandList, persistenceBorder, "100%");
                setFlag(partedCommandList, "1", "boot", "on");
                setFlag(partedCommandList, "1", "lba", "on");
                break;

            case EXCHANGE:
                if (exchangeMB == 0) {
                    // create three partitions: boot, persistence, system
                    bootBorder = BOOT_PARTITION_SIZE + "MiB";
                    persistenceBorder
                            = (BOOT_PARTITION_SIZE + persistenceMB) + "MiB";
                    mkpart(partedCommandList, "0%", bootBorder);
                    mkpart(partedCommandList, bootBorder, persistenceBorder);
                    mkpart(partedCommandList, persistenceBorder, "100%");
                    setFlag(partedCommandList, "1", "boot", "on");
                    setFlag(partedCommandList, "1", "lba", "on");

                } else {
                    String secondBorder;
                    if (storageDevice.isRemovable()) {
                        String exchangeBorder = exchangeMB + "MiB";
                        bootBorder = (exchangeMB + BOOT_PARTITION_SIZE) + "MiB";
                        secondBorder = bootBorder;
                        mkpart(partedCommandList, "0%", exchangeBorder);
                        mkpart(partedCommandList, exchangeBorder, bootBorder);
                        setFlag(partedCommandList, "2", "boot", "on");
                    } else {
                        bootBorder = BOOT_PARTITION_SIZE + "MiB";
                        String exchangeBorder
                                = (BOOT_PARTITION_SIZE + exchangeMB) + "MiB";
                        secondBorder = exchangeBorder;
                        mkpart(partedCommandList, "0%", bootBorder);
                        mkpart(partedCommandList, bootBorder, exchangeBorder);
                        setFlag(partedCommandList, "1", "boot", "on");
                    }
                    if (persistenceMB == 0) {
                        mkpart(partedCommandList, secondBorder, "100%");
                    } else {
                        persistenceBorder = (BOOT_PARTITION_SIZE + exchangeMB
                                + persistenceMB) + "MiB";
                        mkpart(partedCommandList,
                                secondBorder, persistenceBorder);
                        mkpart(partedCommandList, persistenceBorder, "100%");
                    }
                    setFlag(partedCommandList, "1", "lba", "on");
                    setFlag(partedCommandList, "2", "lba", "on");
                }
                break;

            default:
                String errorMessage = "unsupported partitionState \""
                        + partitionState + '\"';
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
        }

        // safety wait in case of device scanning
        // 5 seconds were not enough...
        Thread.sleep(7000);

        // check if a swap partition is active on this device
        // if so, switch it off
        List<String> swaps
                = LernstickFileTools.readFile(new File("/proc/swaps"));
        for (String swapLine : swaps) {
            if (swapLine.startsWith(device)) {
                swapoffPartition(device, swapLine);
            }
        }

        // umount all mounted partitions of device
        umountPartitions(device);

        // Create a new partition table before creating the partitions,
        // otherwise USB flash drives previously written with a dd'ed ISO
        // will NOT work!
        //
        // NOTE 1:
        // "parted <device> mklabel msdos" did NOT work correctly here!
        // (the partition table type was still unknown and booting failed)
        //
        // NOTE 2:
        // "--print-reply" is needed in the call to dbus-send below to make
        // the call synchronous
        int exitValue;
        if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
            exitValue = processExecutor.executeProcess("dbus-send",
                    "--system", "--print-reply",
                    "--dest=org.freedesktop.UDisks",
                    "/org/freedesktop/UDisks/devices/" + device.substring(5),
                    "org.freedesktop.UDisks.Device.PartitionTableCreate",
                    "string:mbr", "array:string:");

        } else {
            // Even more fun with udisks2! :-)
            // 
            // Now whe have to call org.freedesktop.UDisks2.Block.Format
            // This function has the signature 'sa{sv}'.
            // dbus-send is unable to send messages with this signature.
            // To quote the dbus-send manpage:
            // ****************************
            //  D-Bus supports more types than these, but dbus-send currently
            //  does not. Also, dbus-send does not permit empty containers or
            //  nested containers (e.g. arrays of variants).
            // ****************************
            // 
            // creating a Java interface also fails, see here:
            // https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=777241
            //
            // So we have to create a script that calls python.
            // This utterly sucks but our options are limited...
            // exitValue = processExecutor.executeScript(true, true,
            //         "python -c 'import dbus; "
            //         + "dbus.SystemBus().call_blocking("
            //         + "\"org.freedesktop.UDisks2\", "
            //         + "\"/org/freedesktop/UDisks2/block_devices/"
            //         + device.substring(5) + "\", "
            //         + "\"org.freedesktop.UDisks2.Block\", "
            //         + "\"Format\", \"sa{sv}\", (\"dos\", {}))'");
            //
            // It gets even better. The call above very often just fails with
            // the following error message:
            // Traceback (most recent call last):
            // File "<string>", line 1, in <module>
            // File "/usr/lib/python2.7/dist-packages/dbus/connection.py", line 651, in call_blocking message, timeout)
            // dbus.exceptions.DBusException: org.freedesktop.UDisks2.Error.Failed: Error synchronizing after initial wipe: Timed out waiting for object
            //
            // So, for Debian 8 we retry with good old parted and hope for the
            // best...
            exitValue = processExecutor.executeProcess(true, true,
                    "parted", "-s", device, "mklabel", "msdos");
        }
        if (exitValue != 0) {
            String errorMessage
                    = STRINGS.getString("Error_Creating_Partition_Table");
            errorMessage = MessageFormat.format(errorMessage, device);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // another safety wait...
        Thread.sleep(3000);

        // repartition device
        String[] commandArray = partedCommandList.toArray(
                new String[partedCommandList.size()]);
        exitValue = processExecutor.executeProcess(commandArray);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Error_Repartitioning");
            errorMessage = MessageFormat.format(errorMessage, device);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // safety wait so that new partitions are known to the system
        Thread.sleep(7000);

        // The partition types assigned by parted are mosty garbage.
        // We must fix them here...
        // The boot partition is actually formatted with FAT32, but "hidden"
        // by using the EFI partition type.
        switch (partitionState) {
            case ONLY_SYSTEM:
                // create two partitions:
                //  1) boot (EFI)
                //  2) system (Linux)
                processExecutor.executeProcess("/sbin/sfdisk",
                        "--id", device, "1", "ef");
                processExecutor.executeProcess("/sbin/sfdisk",
                        "--id", device, "2", "83");
                break;

            case PERSISTENCE:
                // create three partitions:
                //  1) boot (EFI)
                //  2) persistence (Linux)
                //  3) system (Linux)
                processExecutor.executeProcess("/sbin/sfdisk",
                        "--id", device, "1", "ef");
                processExecutor.executeProcess("/sbin/sfdisk",
                        "--id", device, "2", "83");
                processExecutor.executeProcess("/sbin/sfdisk",
                        "--id", device, "3", "83");
                break;

            case EXCHANGE:
                if (exchangeMB == 0) {
                    // create three partitions:
                    //  1) boot (EFI)
                    //  2) persistence (Linux)
                    //  3) system (Linux)
                    processExecutor.executeProcess("/sbin/sfdisk",
                            "--id", device, "1", "ef");
                    processExecutor.executeProcess("/sbin/sfdisk",
                            "--id", device, "2", "83");
                    processExecutor.executeProcess("/sbin/sfdisk",
                            "--id", device, "3", "83");
                } else {
                    // determine ID for exchange partition
                    String exchangePartitionID;
                    Object selectedItem
                            = exchangePartitionFileSystemComboBox.getSelectedItem();
                    String fileSystem = selectedItem.toString();
                    if (fileSystem.equalsIgnoreCase("fat32")) {
                        exchangePartitionID = "c";
                    } else {
                        // exFAT & NTFS
                        exchangePartitionID = "7";
                    }

                    if (storageDevice.isRemovable()) {
                        //  1) exchange (exFAT, FAT32 or NTFS)
                        //  2) boot (EFI)
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "1", exchangePartitionID);
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "2", "ef");
                    } else {
                        //  1) boot (EFI)
                        //  2) exchange (exFAT, FAT32 or NTFS)
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "1", "ef");
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "2", exchangePartitionID);
                    }

                    if (persistenceMB == 0) {
                        //  3) system (Linux)
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "3", "83");
                    } else {
                        //  3) persistence (Linux)
                        //  4) system (Linux)
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "3", "83");
                        processExecutor.executeProcess("/sbin/sfdisk",
                                "--id", device, "4", "83");
                    }
                }
                break;

            default:
                String errorMessage = "unsupported partitionState \""
                        + partitionState + '\"';
                LOGGER.log(Level.SEVERE, errorMessage);
                throw new IOException(errorMessage);
        }

        // create file systems
        switch (partitionState) {
            case ONLY_SYSTEM:
                formatBootAndSystemPartition(bootDevice, systemDevice);
                return;

            case PERSISTENCE:
                formatPersistencePartition(persistenceDevice);
                formatBootAndSystemPartition(bootDevice, systemDevice);
                return;

            case EXCHANGE:
                if (exchangeMB != 0) {
                    // create file system for exchange partition
                    formatExchangePartition(
                            exchangeDevice, exchangePartitionLabel);
                }
                if (persistenceDevice != null) {
                    formatPersistencePartition(persistenceDevice);
                }
                formatBootAndSystemPartition(bootDevice, systemDevice);
                return;

            default:
                String errorMessage = "unsupported partitionState \""
                        + partitionState + '\"';
                LOGGER.log(Level.SEVERE, errorMessage);
                throw new IOException(errorMessage);
        }
    }

    private void copyExchangeBootAndSystem(FileCopier fileCopier,
            StorageDevice storageDevice, Partition destinationExchangePartition,
            Partition destinationBootPartition,
            Partition destinationSystemPartition,
            final Container cardPanel, final String copyCardName,
            final JLabel copyLabel, FileCopierPanel fileCopyPanel,
            final String umountCardName, final JProgressBar progressBar)
            throws InterruptedException, IOException, DBusException {

        // define CopyJob for exchange paritition
        boolean sourceExchangeTempMounted = false;
        String destinationExchangePath = null;
        CopyJob exchangeCopyJob = null;
        if (copyExchangeCheckBox.isSelected()) {
            destinationExchangePath
                    = destinationExchangePartition.mount().getMountPath();

            exchangeCopyJob = new CopyJob(
                    new Source[]{source.getExchangeCopySource()},
                    new String[]{destinationExchangePath});
        }

        // define CopyJobs for boot and system parititions
        Object selectedFileSystem
                = exchangePartitionFileSystemComboBox.getSelectedItem();
        String destinationExchangePartitionFileSystem
                = selectedFileSystem.toString();
        CopyJobsInfo copyJobsInfo = prepareBootAndSystemCopyJobs(
                storageDevice, destinationBootPartition,
                destinationExchangePartition, destinationSystemPartition,
                destinationExchangePartitionFileSystem);

        // copy all files
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                copyLabel.setText(STRINGS.getString("Copying_Files"));
                showCard(cardPanel, copyCardName);
            }
        });
        fileCopyPanel.setFileCopier(fileCopier);

        CopyJob bootFilesCopyJob = copyJobsInfo.getBootFilesCopyJob();
        fileCopier.copy(exchangeCopyJob, bootFilesCopyJob,
                copyJobsInfo.getBootCopyJob(), copyJobsInfo.getSystemCopyJob());

        if (bootFilesCopyJob != null) {
            // The exchange partition is FAT32 on a removable media and
            // therefore we copied the boot files not only to the boot partition
            // but also to the exchange partition.

            if (destinationExchangePath == null) {
                // The user did not select to copy the exchange partition but it
                // was already mounted in prepareBootAndSystemCopyJobs().
                // Just get the reference here...
                destinationExchangePath
                        = destinationExchangePartition.getMountPath();
            }

            // hide boot files
            hideBootFiles(bootFilesCopyJob, destinationExchangePath);

            // change data partition mode (if needed)
            setDataPartitionMode(dataPartitionModeComboBox,
                    destinationExchangePath);
        }

        // update GUI
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                showCard(cardPanel, umountCardName);
                progressBar.setString(
                        STRINGS.getString("Unmounting_File_Systems"));
            }
        });

        source.unmountTmpPartitions();
        if (destinationExchangePath != null) {
            destinationExchangePartition.umount();
        }

        String destinationBootPath = copyJobsInfo.getDestinationBootPath();
        // isolinux -> syslinux renaming
        // !!! don't check here for boot storage device type !!!
        // (usb flash drives with an isohybrid image also contain the
        //  isolinux directory)
        isolinuxToSyslinux(destinationBootPath);

        // change data partition mode on target (if needed)
        setDataPartitionMode(dataPartitionModeComboBox, destinationBootPath);
    }

    private void copyPersistence(Partition destinationDataPartition)
            throws IOException, InterruptedException, DBusException {
        // copy persistence partition
        if (copyPersistenceCheckBox.isSelected()) {

            // mount persistence source
            MountInfo sourceDataMountInfo
                    = source.getDataPartition().mount();
            String sourceDataPath = sourceDataMountInfo.getMountPath();
            if (sourceDataPath == null) {
                String errorMessage = "could not mount source data partition";
                throw new IOException(errorMessage);
            }

            // mount persistence destination
            MountInfo destinationDataMountInfo
                    = destinationDataPartition.mount();
            String destinationDataPath
                    = destinationDataMountInfo.getMountPath();
            if (destinationDataPath == null) {
                String errorMessage
                        = "could not mount destination data partition";
                throw new IOException(errorMessage);
            }

            // TODO: use filecopier as soon as it supports symlinks etc.
            copyPersistenceCp(sourceDataPath, destinationDataPath);

            // update GUI
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(installCardPanel,
                            "installIndeterminateProgressPanel");
                    installIndeterminateProgressBar.setString(
                            STRINGS.getString("Unmounting_File_Systems"));
                }
            });

            // umount both source and destination persistence partitions
            //  (only if there were not mounted before)
            if (!sourceDataMountInfo.alreadyMounted()) {
                source.getDataPartition().umount();
            }
            if (!destinationDataMountInfo.alreadyMounted()) {
                destinationDataPartition.umount();
            }
        }
    }

    private void mkpart(List<String> commandList, String start, String end) {
        commandList.add("mkpart");
        commandList.add("primary");
        commandList.add(start);
        commandList.add(end);
    }

    private void setFlag(List<String> commandList,
            String partition, String flag, String value) {

        commandList.add("set");
        commandList.add(partition);
        commandList.add(flag);
        commandList.add(value);
    }

    private void formatExchangePartition(String exchangeDevice,
            String exchangePartitionLabel) throws IOException {

        // create file system for exchange partition
        Object selectedFileSystem
                = exchangePartitionFileSystemComboBox.getSelectedItem();
        String exFS = selectedFileSystem.toString();
        String mkfsBuilder;
        String mkfsLabelSwitch;
        String quickSwitch = null;
        if (exFS.equalsIgnoreCase("fat32")) {
            mkfsBuilder = "vfat";
            mkfsLabelSwitch = "-n";
        } else if (exFS.equalsIgnoreCase("exfat")) {
            mkfsBuilder = "exfat";
            mkfsLabelSwitch = "-n";
        } else {
            mkfsBuilder = "ntfs";
            if (quickFormatCheckBox.isSelected()) {
                quickSwitch = "-f";
            }
            mkfsLabelSwitch = "-L";
        }

        int exitValue;
        if (quickSwitch == null) {
            exitValue = processExecutor.executeProcess(
                    "/sbin/mkfs." + mkfsBuilder, mkfsLabelSwitch,
                    exchangePartitionLabel, exchangeDevice);
        } else {
            exitValue = processExecutor.executeProcess(
                    "/sbin/mkfs." + mkfsBuilder, quickSwitch, mkfsLabelSwitch,
                    exchangePartitionLabel, exchangeDevice);
        }

        if (exitValue != 0) {
            String errorMessage
                    = STRINGS.getString("Error_Create_Exchange_Partition");
            errorMessage = MessageFormat.format(errorMessage, exchangeDevice);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private void copyPersistenceCp(String persistenceSourcePath,
            String persistenceDestinationPath)
            throws InterruptedException, IOException {

        cpActionListener = new CpActionListener();
        cpActionListener.setSourceMountPoint(persistenceSourcePath);
        final Timer cpTimer = new Timer(1000, cpActionListener);
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
        Thread.sleep(1000);
        processExecutor.addPropertyChangeListener(this);
        // this needs to be a script because of the shell globbing
        String copyScript = "#!/bin/bash\n"
                + "cp -av \"" + persistenceSourcePath + "/\"* \""
                + persistenceDestinationPath + "/\"";
        int exitValue = processExecutor.executeScript(true, true, copyScript);
        processExecutor.removePropertyChangeListener(this);
        cpTimer.stop();
        if (exitValue != 0) {
            String errorMessage = "Could not copy persistence layer!";
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    private class Installer extends Thread implements PropertyChangeListener {

        private FileCopier fileCopier;
        private int currentDevice;
        private int selectionCount;

        @Override
        public void run() {
            LogindInhibit inhibit = new LogindInhibit("Installing");

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(cardPanel, "installTabbedPane");
                }
            });

            int[] selectedIndices
                    = installStorageDeviceList.getSelectedIndices();
            selectionCount = selectedIndices.length;
            fileCopier = new FileCopier();
            Number autoNumberStart = (Number) autoNumberStartSpinner.getValue();
            Number autoIncrementNumber
                    = (Number) autoNumberIncrementSpinner.getValue();
            int autoNumber = autoNumberStart.intValue();
            int autoIncrement = autoIncrementNumber.intValue();
            final List<StorageDeviceResult> resultsList = new ArrayList<>();
            for (int i = 0; i < selectionCount; i++) {
                long startTime = System.currentTimeMillis();
                currentDevice = i + 1;
                StorageDevice storageDevice
                        = (StorageDevice) installStorageDeviceListModel.
                        getElementAt(selectedIndices[i]);

                // update overall progress message
                String pattern = STRINGS.getString("Install_Device_Info");
                final String message = MessageFormat.format(pattern,
                        storageDevice.getVendor() + " "
                        + storageDevice.getModel() + " "
                        + LernstickFileTools.getDataVolumeString(
                                storageDevice.getSize(), 1),
                        "/dev/" + storageDevice.getDevice(),
                        currentDevice, selectionCount);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        currentlyInstalledDeviceLabel.setText(message);
                    }
                });

                // add "in progress" entry to results table
                resultsList.add(
                        new StorageDeviceResult(storageDevice, -1, null));
                installationResultsTableModel.setList(resultsList);

                // auto numbering
                String exchangePartitionLabel
                        = exchangePartitionTextField.getText();
                String autoNumberPattern = autoNumberPatternTextField.getText();
                if (!autoNumberPattern.isEmpty()) {
                    exchangePartitionLabel = exchangePartitionLabel.replace(
                            autoNumberPattern, String.valueOf(autoNumber));
                    autoNumber += autoIncrement;
                }

                String errorMessage = null;
                try {
                    copyToStorageDevice(fileCopier, storageDevice,
                            exchangePartitionLabel, installCardPanel,
                            "installCopyPanel", installCopyLabel,
                            installFileCopierPanel,
                            "installIndeterminateProgressPanel",
                            installIndeterminateProgressBar);
                } catch (InterruptedException | IOException |
                        DBusException exception) {
                    LOGGER.log(Level.WARNING, "", exception);
                    errorMessage = exception.getMessage();
                }

                long stopTime = System.currentTimeMillis();
                long duration = stopTime - startTime;

                // remove "in progress" entry
                resultsList.remove(resultsList.size() - 1);
                // add current result
                resultsList.add(new StorageDeviceResult(
                        storageDevice, duration, errorMessage));
                installationResultsTableModel.setList(resultsList);
                resultsTableModel.setList(resultsList);

                // auto-increment autoNumberStartSpinner
                final int newStartNumber = autoNumber;
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        autoNumberStartSpinner.setValue(newStartNumber);
                    }
                });
            }

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setTitle(STRINGS.getString("DLCopy.title"));
                    String key;
                    switch (source.getDeviceType()) {
                        case HardDrive:
                        case OpticalDisc:
                            key = "Done_Message_From_Non_Removable_Boot_Device";
                            break;
                        default:
                            key = "Done_Message_From_Removable_Boot_Device";
                    }
                    resultsInfoLabel.setText(STRINGS.getString(key));
                    resultsTitledPanel.setBorder(
                            BorderFactory.createTitledBorder(
                                    STRINGS.getString("Installation_Report")));
                    showCard(cardPanel, "resultsPanel");
                    previousButton.setEnabled(true);
                    nextButton.setText(STRINGS.getString("Done"));
                    nextButton.setIcon(new ImageIcon(getClass().getResource(
                            "/ch/fhnw/dlcopy/icons/exit.png")));
                    nextButton.setEnabled(true);
                    previousButton.requestFocusInWindow();
                    getRootPane().setDefaultButton(previousButton);
                    //Toolkit.getDefaultToolkit().beep();
                    playNotifySound();
                    toFront();
                }
            });
            inhibit.delete();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();
            switch (propertyName) {
                case FileCopier.BYTE_COUNTER_PROPERTY:
                    long byteCount = fileCopier.getByteCount();
                    long copiedBytes = fileCopier.getCopiedBytes();
                    final int progress
                            = (int) ((100 * copiedBytes) / byteCount);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            setTitle(progress + "% "
                                    + STRINGS.getString("Copied") + " ("
                                    + currentDevice + '/' + selectionCount
                                    + ')');
                        }
                    });
                    break;

                case ProcessExecutor.LINE:
                    // store current cp progress line
                    // (will be pattern matched later when needed)
                    String line = (String) evt.getNewValue();
                    cpActionListener.setCurrentLine(line);

//                // rsync updates
//                // parse lines that end with "... to-check=100/800)"
//                String line = (String) evt.getNewValue();
//                Matcher matcher = rsyncPattern.matcher(line);
//                if (matcher.matches()) {
//                    String toCheckString = matcher.group(1);
//                    String fileCountString = matcher.group(2);
//                    try {
//                        int filesToCheck = Integer.parseInt(toCheckString);
//                        int fileCount = Integer.parseInt(fileCountString);
//                        int progress =
//                                (fileCount - filesToCheck) * 100 / fileCount;
//                        // Because of the rsync algorithm it can happen that the
//                        // progress value temporarily decreases. This is very
//                        // confusing for users. We hide this ugly details by not
//                        // updating the value if it decreases...
//                        if (progress > rsyncProgress) {
//                            rsyncProgress = progress;
//                            SwingUtilities.invokeLater(new Runnable() {
//
//                                @Override
//                                public void run() {
//                                    rsyncPogressBar.setValue(rsyncProgress);
//                                    setTitle(rsyncProgress + "% "
//                                            + STRINGS.getString("Copied") + " ("
//                                            + currentDevice + '/'
//                                            + selectionCount + ')');
//                                }
//                            });
//                        }
//
//                    } catch (NumberFormatException ex) {
//                        LOGGER.log(Level.WARNING,
//                                "could not parse rsync output", ex);
//                    }
//                }
                    break;
            }
        }
    }

    private class Upgrader
            extends SwingWorker<Boolean, Void>
            implements PropertyChangeListener {

        private final FileCopier fileCopier = new FileCopier();
        private LogindInhibit inhibit = null;
        private int selectionCount;
        private int currentDevice;

        @Override
        protected Boolean doInBackground() throws Exception {
            inhibit = new LogindInhibit("Upgrading");

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(cardPanel, "upgradeTabbedPane");
                }
            });

            // upgrade all selected storage devices
            int[] selectedIndices
                    = upgradeStorageDeviceList.getSelectedIndices();
            selectionCount = selectedIndices.length;
            final List<StorageDeviceResult> resultsList = new ArrayList<>();
            for (int i : selectedIndices) {
                long startTime = System.currentTimeMillis();

                StorageDevice storageDevice
                        = (StorageDevice) upgradeStorageDeviceListModel.get(i);

                // update overall progress message
                currentDevice++;
                String pattern = STRINGS.getString("Upgrade_Device_Info");
                final String message = MessageFormat.format(pattern,
                        currentDevice, selectionCount,
                        storageDevice.getVendor() + " "
                        + storageDevice.getModel(),
                        " (" + DLCopy.STRINGS.getString("Size") + ": "
                        + LernstickFileTools.getDataVolumeString(
                                storageDevice.getSize(), 1)
                        + ", "
                        + DLCopy.STRINGS.getString("Revision") + ": "
                        + storageDevice.getRevision() + ", "
                        + DLCopy.STRINGS.getString("Serial") + ": "
                        + storageDevice.getSerial() + ", "
                        + "&#47;dev&#47;" + storageDevice.getDevice()
                        + ")");
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        currentlyUpgradedDeviceLabel.setText(message);
                    }
                });
                LOGGER.log(Level.INFO,
                        "upgrading storage device: {0} of {1} ({2})",
                        new Object[]{
                            currentDevice, selectionCount, storageDevice
                        });

                // add "in progress" entry to results table
                resultsList.add(
                        new StorageDeviceResult(storageDevice, -1, null));
                upgradeResultsTableModel.setList(resultsList);

                File backupDestination = getBackupDestination(storageDevice);

                String errorMessage = null;
                try {
                    StorageDevice.UpgradeVariant upgradeVariant
                            = storageDevice.getUpgradeVariant();
                    switch (upgradeVariant) {
                        case REGULAR:
                        case REPARTITION:
                            if (upgradeDataPartition(
                                    storageDevice, backupDestination)) {
                                if (upgradeSystemPartitionCheckBox.isSelected()
                                        && !upgradeSystemPartition(storageDevice)) {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                            break;

                        case BACKUP:
                            backupInstallRestore(storageDevice);
                            break;

                        case INSTALLATION:
                            copyToStorageDevice(fileCopier, storageDevice,
                                    exchangePartitionTextField.getText(),
                                    upgradeCardPanel, "upgradeCopyPanel",
                                    upgradeCopyLabel, upgradeFileCopierPanel,
                                    "upgradeIndeterminateProgressPanel",
                                    upgradeIndeterminateProgressBar);
                            break;

                        default:
                            LOGGER.log(Level.WARNING,
                                    "Unsupported variant {0}", upgradeVariant);
                    }

                    // automatic removal of (temporary) backup
                    if (automaticBackupCheckBox.isSelected()
                            && automaticBackupRemoveCheckBox.isSelected()) {
                        LernstickFileTools.recursiveDelete(
                                backupDestination, true);
                    }
                } catch (DBusException | IOException |
                        InterruptedException ex) {
                    LOGGER.log(Level.WARNING, "", ex);
                    errorMessage = ex.getMessage();
                }

                LOGGER.log(Level.INFO, "upgrading of storage device finished: "
                        + "{0} of {1} ({2})", new Object[]{
                            currentDevice, selectionCount, storageDevice
                        });

                long stopTime = System.currentTimeMillis();
                long duration = stopTime - startTime;

                // remove "in progress" entry
                resultsList.remove(resultsList.size() - 1);
                // add current result
                resultsList.add(new StorageDeviceResult(
                        storageDevice, duration, errorMessage));
                installationResultsTableModel.setList(resultsList);
                resultsTableModel.setList(resultsList);
            }

            return true;
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();
            if (FileCopier.BYTE_COUNTER_PROPERTY.equals(propertyName)) {
                long byteCount = fileCopier.getByteCount();
                long copiedBytes = fileCopier.getCopiedBytes();
                final int progress = (int) ((100 * copiedBytes) / byteCount);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        setTitle(progress + "% " + STRINGS.getString("Copied")
                                + " (" + currentDevice + '/' + selectionCount
                                + ')');
                    }
                });
            }
        }

        @Override
        protected void done() {
            if (inhibit != null) {
                inhibit.delete();
            }
            setTitle(STRINGS.getString("DLCopy.title"));
            try {
                if (get()) {
                    String key;
                    switch (source.getDeviceType()) {
                        case HardDrive:
                        case OpticalDisc:
                            key = "Upgrade_Done_From_Non_Removable_Device";
                            break;
                        default:
                            key = "Upgrade_Done_From_Removable_Device";
                    }
                    resultsInfoLabel.setText(STRINGS.getString(key));
                    resultsTitledPanel.setBorder(
                            BorderFactory.createTitledBorder(
                                    STRINGS.getString("Upgrade_Report")));
                    showCard(cardPanel, "resultsPanel");
                    previousButton.setEnabled(true);
                    nextButton.setText(STRINGS.getString("Done"));
                    nextButton.setIcon(new ImageIcon(getClass().getResource(
                            "/ch/fhnw/dlcopy/icons/exit.png")));
                    nextButton.setEnabled(true);
                    previousButton.requestFocusInWindow();
                    getRootPane().setDefaultButton(previousButton);
                    playNotifySound();
                    toFront();
                } else {
                    switchToUpgradeSelection();
                }
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }

        private File getBackupDestination(StorageDevice storageDevice) {
            // use the device serial number as unique identifier for backups
            // (but replace all slashes because they are not allowed in
            //  directory names)
            String backupUID = storageDevice.getSerial().replaceAll("/", "-");
            return new File(automaticBackupTextField.getText(), backupUID);
        }

        private void backupInstallRestore(StorageDevice storageDevice)
                throws InterruptedException, IOException, DBusException,
                SQLException {

            // prepare backup destination directories
            File dataDestination
                    = new File(getBackupDestination(storageDevice), "data");
            dataDestination.mkdirs();
            File exchangeDestination
                    = new File(getBackupDestination(storageDevice), "exchange");
            exchangeDestination.mkdirs();

            // backup
            Partition dataPartition = storageDevice.getDataPartition();
            String dataMountPoint = dataPartition.mount().getMountPath();
            backupUserData(dataMountPoint, dataDestination);
            dataPartition.umount();
            backupExchangeParitition(storageDevice, exchangeDestination);

            // installation
            copyToStorageDevice(fileCopier, storageDevice,
                    exchangePartitionTextField.getText(), upgradeCardPanel,
                    "upgradeCopyPanel", upgradeCopyLabel,
                    upgradeFileCopierPanel, "upgradeIndeterminateProgressPanel",
                    upgradeIndeterminateProgressBar);

            // !!! update reference to storage device !!!
            // copyToStorageDevice() may change the storage device completely
            storageDevice = new StorageDevice(
                    storageDevice.getDevice(), systemSize);
            restoreDataPartition(storageDevice, dataDestination);
            restoreExchangePartition(storageDevice, exchangeDestination);
        }

        private void backupUserData(String mountPoint, File backupDestination)
                throws IOException {

            // prepare backup run
            File backupSource = new File(mountPoint);
            rdiffBackupRestore = new RdiffBackupRestore();
            Timer backupTimer = new Timer(1000, new BackupActionListener(true));
            backupTimer.setInitialDelay(0);
            backupTimer.start();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    upgradeBackupLabel.setText(
                            STRINGS.getString("Backing_Up_User_Data"));
                    upgradeBackupTimeLabel.setText(
                            timeFormat.format(new Date(0)));
                    showCard(upgradeCardPanel, "upgradeBackupPanel");
                }
            });

            String includes = mountPoint + "/home/user/";
            if (keepPrinterSettingsCheckBox.isSelected()) {
                includes += '\n' + mountPoint + "/etc/cups/";
            }

            // run the actual backup process
            rdiffBackupRestore.backupViaFileSystem(backupSource,
                    backupDestination, null, mountPoint, includes, true, null,
                    null, false, false, false, false, false);

            // cleanup
            backupTimer.stop();
        }

        private void backupExchangeParitition(StorageDevice storageDevice,
                File exchangeDestination) throws DBusException, IOException {

            Partition exchangePartition = storageDevice.getExchangePartition();
            if (exchangePartition == null) {
                LOGGER.warning("there is no exchange partition!");
                return;
            }
            String mountPath = exchangePartition.mount().getMountPath();

            // GUI update
            upgradeCopyLabel.setText(STRINGS.getString(
                    "Backing_Up_Exchange_Partition"));
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(upgradeCardPanel, "upgradeCopyPanel");
                }
            });

            // Unfortunately, rdiffbackup does not work with exFAT or NTFS.
            // Both filesystems are possible on the exchange partition.
            // Therefore we just make a simple copy.
            LernstickFileTools.recursiveDelete(exchangeDestination, false);
            Source[] sources = new Source[]{new Source(mountPath, ".*")};
            String[] destinations = new String[]{exchangeDestination.getPath()};
            upgradeFileCopierPanel.setFileCopier(fileCopier);
            fileCopier.copy(new CopyJob(sources, destinations));
        }

        private void restoreDataPartition(
                StorageDevice storageDevice, File restoreSourceDir)
                throws DBusException, IOException, SQLException {

            Partition dataPartition = storageDevice.getDataPartition();
            if (dataPartition == null) {
                LOGGER.warning("there is no data partition!");
                return;
            }

            String mountPath = dataPartition.mount().getMountPath();

            // restore data
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(upgradeCardPanel,
                            "upgradeIndeterminateProgressPanel");
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Reading_Backup"));
                }
            });
            RdiffFileDatabase rdiffFileDatabase
                    = RdiffFileDatabase.getInstance(restoreSourceDir);
            rdiffFileDatabase.sync();
            List<Increment> increments = rdiffFileDatabase.getIncrements();
            if ((increments == null) || increments.isEmpty()) {
                throw new IOException(
                        "could not restore user data, no backup found");
            }
            Increment increment = increments.get(0);
            RdiffFile[] rdiffRoot = new RdiffFile[]{increment.getRdiffRoot()};

            // create a new RdiffBackupRestore instance to reset its counters
            rdiffBackupRestore = new RdiffBackupRestore();
            Timer restoreTimer = new Timer(
                    1000, new BackupActionListener(false));
            restoreTimer.setInitialDelay(0);
            restoreTimer.start();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    upgradeBackupLabel.setText(
                            STRINGS.getString("Restoring_User_Data"));
                    upgradeBackupTimeLabel.setText(
                            timeFormat.format(new Date(0)));
                    showCard(upgradeCardPanel, "upgradeBackupPanel");
                }
            });
            rdiffBackupRestore.restore("now", rdiffRoot,
                    restoreSourceDir, new File(mountPath), null, false);

            // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            // !!! This must happen *after* restoring the files above.       !!!
            // !!! otherwise the changes would be overwritten by the restore !!!
            // !!! process (rdiff-backup)!                                   !!!
            // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            // reactivate welcome, overwrite files...
            finalizeDataPartition(mountPath);

            // cleanup
            dataPartition.umount();
            restoreTimer.stop();
        }

        private void restoreExchangePartition(
                StorageDevice storageDevice, File restoreSourceDir)
                throws DBusException, IOException, SQLException {

            Partition exchangePartition = storageDevice.getExchangePartition();
            if (exchangePartition == null) {
                LOGGER.warning("there is no exchange partition!");
                return;
            }

            upgradeCopyLabel.setText(STRINGS.getString(
                    "Restoring_Exchange_Partition"));
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(upgradeCardPanel, "upgradeCopyPanel");
                }
            });

            Source[] sources = new Source[]{
                new Source(restoreSourceDir.getPath(), ".*")};
            String[] destinations = new String[]{
                exchangePartition.mount().getMountPath()};
            upgradeFileCopierPanel.setFileCopier(fileCopier);
            fileCopier.copy(new CopyJob(sources, destinations));

            exchangePartition.umount();
        }

        private boolean upgradeDataPartition(StorageDevice storageDevice,
                File backupDestination) throws DBusException, IOException {

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Resetting_Data_Partition"));
                }
            });
            Partition dataPartition = storageDevice.getDataPartition();
            if (dataPartition == null) {
                LOGGER.log(Level.WARNING,
                        "skipping /dev/{0} because it has no data partition",
                        storageDevice.getDevice());
                return true;
            }
            MountInfo mountInfo = dataPartition.mount();
            String dataMountPoint = mountInfo.getMountPath();

            // backup
            if (automaticBackupCheckBox.isSelected()) {
                backupUserData(dataMountPoint, backupDestination);
            }

            // reset data partition
            if (keepPrinterSettingsCheckBox.isSelected()) {
                processExecutor.executeProcess("find", dataMountPoint,
                        "!", "-regex", dataMountPoint,
                        "!", "-regex", dataMountPoint + "/lost\\+found",
                        "!", "-regex", dataMountPoint + "/persistence.conf",
                        "!", "-regex", dataMountPoint + "/home.*",
                        "!", "-regex", dataMountPoint + "/etc.*",
                        "-exec", "rm", "-rf", "{}", ";");
                String etcPath = dataMountPoint + "/etc";
                processExecutor.executeProcess("find", etcPath,
                        "!", "-regex", etcPath,
                        "!", "-regex", etcPath + "/cups.*",
                        "-exec", "rm", "-rf", "{}", ";");
            } else {
                processExecutor.executeProcess("find", dataMountPoint,
                        "!", "-regex", dataMountPoint,
                        "!", "-regex", dataMountPoint + "/lost\\+found",
                        "!", "-regex", dataMountPoint + "/persistence.conf",
                        "!", "-regex", dataMountPoint + "/home.*",
                        "-exec", "rm", "-rf", "{}", ";");
            }

            finalizeDataPartition(dataMountPoint);

            // umount
            if ((!mountInfo.alreadyMounted()) && (!umount(dataPartition))) {
                return false;
            }

            // upgrade label (if necessary)
            if (!(dataPartition.getIdLabel().equals(
                    Partition.PERSISTENCE_LABEL))) {
                processExecutor.executeProcess("e2label",
                        "/dev/" + dataPartition.getDeviceAndNumber(),
                        Partition.PERSISTENCE_LABEL);
            }

            return true;
        }

        private void finalizeDataPartition(String dataMountPoint)
                throws IOException {

            // welcome application reactivation
            if (reactivateWelcomeCheckBox.isSelected()) {
                File propertiesFile = new File(
                        dataMountPoint + "/etc/lernstickWelcome");
                Properties lernstickWelcomeProperties = new Properties();
                if (propertiesFile.exists()) {
                    try (FileReader reader = new FileReader(propertiesFile)) {
                        lernstickWelcomeProperties.load(reader);
                    } catch (IOException iOException) {
                        LOGGER.log(Level.WARNING, "", iOException);
                    }
                } else {
                    propertiesFile.getParentFile().mkdirs();
                    propertiesFile.createNewFile();
                }
                lernstickWelcomeProperties.setProperty("ShowWelcome", "true");
                try (FileWriter writer = new FileWriter(propertiesFile)) {
                    lernstickWelcomeProperties.store(
                            writer, "lernstick Welcome properties");
                } catch (IOException iOException) {
                    LOGGER.log(Level.WARNING, "", iOException);
                }
            }

            // remove hidden files from user directory
            if (removeHiddenFilesCheckBox.isSelected()) {
                File userDir = new File(dataMountPoint + "/home/user/");
                File[] files = userDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().startsWith(".")) {
                            LernstickFileTools.recursiveDelete(file, true);
                        }
                    }
                }
            }

            // process list of files (or directories) to overwrite
            for (int i = 0, size = upgradeOverwriteListModel.size();
                    i < size; i++) {
                // remove the old destination file (or directory)
                String sourcePath = (String) upgradeOverwriteListModel.get(i);
                File destinationFile = new File(dataMountPoint, sourcePath);
                LernstickFileTools.recursiveDelete(destinationFile, true);

                // recursive copy
                processExecutor.executeProcess(true, true,
                        "cp", "-a", "--parents", sourcePath, dataMountPoint);
            }

            // when upgrading from very old versions, the persistence.conf file
            // is still missing and we have to add it now
            File persistenceConf = new File(dataMountPoint, "persistence.conf");
            if (!persistenceConf.exists()) {
                writePersistenceConf(dataMountPoint);
            }
        }

        private boolean upgradeSystemPartition(StorageDevice storageDevice)
                throws DBusException, IOException, InterruptedException {
            String device = storageDevice.getDevice();
            String devicePath = "/dev/" + device;
            Partition bootPartition = storageDevice.getBootPartition();
            Partition exchangePartition = storageDevice.getExchangePartition();
            Partition dataPartition = storageDevice.getDataPartition();
            Partition systemPartition = storageDevice.getSystemPartition();
            int systemPartitionNumber = systemPartition.getNumber();

            // make sure that systemPartition is unmounted
            if (!umount(systemPartition)) {
                return false;
            }

            if (storageDevice.getUpgradeVariant()
                    == StorageDevice.UpgradeVariant.REPARTITION) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        upgradeIndeterminateProgressBar.setString(
                                STRINGS.getString(
                                        "Changing_Partition_Sizes"));
                    }
                });

                // TODO: search partition that needs to be shrinked
                // (for now we simply assume it's the data partition)
                if (!umount(dataPartition)) {
                    return false;
                }
                String dataDevPath
                        = "/dev/" + dataPartition.getDeviceAndNumber();
                int returnValue = processExecutor.executeProcess(true, true,
                        "e2fsck", "-f", "-y", "-v", dataDevPath);
                // e2fsck return values:
                // 0    - No errors
                // 1    - File system errors corrected
                // 2    - File system errors corrected, system should be rebooted
                // 4    - File system errors left uncorrected
                // 8    - Operational error
                // 16   - Usage or syntax error
                // 32   - E2fsck canceled by user request
                // 128  - Shared library error
                //
                // -> only continue if there were no errors or the errors were
                // corrected
                int busyCounter = 0;
                while ((returnValue != 0) && (returnValue != 1)) {
                    if (returnValue == 8) {
                        // Unfortunately, "8" is returned in two situations:
                        // either the device is still busy or the partition
                        // table is damaged.
                        busyCounter++;

                        if (busyCounter >= 10) {
                            // This has been going on too long. A device should
                            // not be busy for such a long time. Most probably
                            // the partition table is damaged...
                            String errorMessage = STRINGS.getString(
                                    "Error_File_System_Check");
                            errorMessage = MessageFormat.format(
                                    errorMessage, dataDevPath);
                            showErrorMessage(errorMessage);
                            return false;
                        }

                        // let's wait some time before retrying
                        try {
                            LOGGER.info("waiting for 10 seconds before continuing...");
                            Thread.sleep(10000);
                        } catch (InterruptedException ex) {
                            LOGGER.log(Level.SEVERE, null, ex);
                        }
                        returnValue = processExecutor.executeProcess(true, true,
                                "e2fsck", "-f", "-y", "-v", dataDevPath);
                    } else {
                        String errorMessage
                                = STRINGS.getString("Error_File_System_Check");
                        errorMessage = MessageFormat.format(
                                errorMessage, dataDevPath);
                        showErrorMessage(errorMessage);
                        return false;
                    }
                }
                returnValue = processExecutor.executeProcess(true, true,
                        "resize2fs", "-M", "-p", dataDevPath);
                if (returnValue != 0) {
                    String errorMessage
                            = STRINGS.getString("Error_File_System_Resize");
                    errorMessage
                            = MessageFormat.format(errorMessage, dataDevPath);
                    showErrorMessage(errorMessage);
                    return false;
                }

                long dataPartitionOffset = dataPartition.getOffset();
                long newSystemPartitionOffset = systemPartition.getOffset()
                        + systemPartition.getSize() - systemSizeEnlarged;
                // align newSystemPartitionOffset on a MiB boundary
                newSystemPartitionOffset /= MEGA;
                String dataPartitionStart
                        = String.valueOf(dataPartitionOffset) + "B";
                String systemPartitionStart
                        = String.valueOf(newSystemPartitionOffset) + "MiB";
                List<String> partedCommand = new ArrayList<>();
                partedCommand.add("/sbin/parted");
                partedCommand.add("-a");
                partedCommand.add("optimal");
                partedCommand.add("-s");
                partedCommand.add(devicePath);
                // remove old partitions
                partedCommand.add("rm");
                partedCommand.add(String.valueOf(dataPartition.getNumber()));
                partedCommand.add("rm");
                partedCommand.add(String.valueOf(systemPartitionNumber));
                // create new partitions
                partedCommand.add("mkpart");
                partedCommand.add("primary");
                partedCommand.add(dataPartitionStart);
                partedCommand.add(systemPartitionStart);
                partedCommand.add("mkpart");
                partedCommand.add("primary");
                partedCommand.add(systemPartitionStart);
                partedCommand.add("100%");
                String[] command = partedCommand.toArray(
                        new String[partedCommand.size()]);

                returnValue = processExecutor.executeProcess(
                        true, true, command);
                if (returnValue != 0) {
                    String errorMessage = STRINGS.getString(
                            "Error_Changing_Partition_Sizes");
                    errorMessage
                            = MessageFormat.format(errorMessage, dataDevPath);
                    showErrorMessage(errorMessage);
                    return false;
                }
                // refresh storage device and partition info
                processExecutor.executeProcess(true, true, "/sbin/partprobe");
                // safety wait so that new partitions are known to the system
                // (5000ms were NOT enough!)
                Thread.sleep(7000);

                returnValue = processExecutor.executeProcess(true, true,
                        "resize2fs", dataDevPath);
                if (returnValue != 0) {
                    String errorMessage
                            = STRINGS.getString("Error_File_System_Resize");
                    errorMessage
                            = MessageFormat.format(errorMessage, dataDevPath);
                    showErrorMessage(errorMessage);
                    return false;
                }

                storageDevice = new StorageDevice(device, systemSize);
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // ! We can't use storageDevice.getSystemPartition() here      !
                // ! because the partitions dont have the necessary properties,!
                // ! yet. We will set them in the next steps.                  !
                // ! !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                List<Partition> partitions = storageDevice.getPartitions();
                systemPartition = partitions.get(systemPartitionNumber - 1);

                formatBootAndSystemPartition(
                        "/dev/" + bootPartition.getDeviceAndNumber(),
                        "/dev/" + systemPartition.getDeviceAndNumber());
            }

            // upgrade boot and system partition
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(upgradeCardPanel,
                            "upgradeIndeterminateProgressPanel");
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString(
                                    "Resetting_System_Partition"));
                }
            });

            // define CopyJobs for boot and system parititions
            String exchangePartitionFS = null;
            if ((exchangePartition != null)
                    && "vfat".equals(exchangePartition.getIdType())) {
                exchangePartitionFS = "fat32";
            }
            // TODO: mapping of other file systems

            CopyJobsInfo copyJobsInfo = prepareBootAndSystemCopyJobs(
                    storageDevice, bootPartition, exchangePartition,
                    systemPartition, exchangePartitionFS);
            File bootMountPointFile = new File(
                    copyJobsInfo.getDestinationBootPath());
            LOGGER.log(Level.INFO, "recursively deleting {0}",
                    bootMountPointFile);
            LernstickFileTools.recursiveDelete(bootMountPointFile, false);
            File systemMountPointFile = new File(
                    copyJobsInfo.getDestinationSystemPath());
            LOGGER.log(Level.INFO, "recursively deleting {0}",
                    systemMountPointFile);
            LernstickFileTools.recursiveDelete(systemMountPointFile, false);

            LOGGER.info("starting copy job");
            upgradeCopyLabel.setText(STRINGS.getString("Copying_Files"));
            upgradeFileCopierPanel.setFileCopier(fileCopier);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(upgradeCardPanel, "upgradeCopyPanel");
                }
            });
            CopyJob bootFilesCopyJob = copyJobsInfo.getBootFilesCopyJob();
            fileCopier.copy(copyJobsInfo.getBootCopyJob(),
                    bootFilesCopyJob, copyJobsInfo.getSystemCopyJob());

            // hide boot files in exchange partition
            // (only necessary with FAT32 on removable media...)
            if (bootFilesCopyJob != null) {
                String exchangePath = exchangePartition.getMountPath();
                hideBootFiles(bootFilesCopyJob, exchangePath);
                umount(exchangePartition);
            }

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(upgradeCardPanel,
                            "upgradeIndeterminateProgressPanel");
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Unmounting_File_Systems"));
                }
            });
            isolinuxToSyslinux(copyJobsInfo.getDestinationBootPath());

            // make storage device bootable
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Writing_Boot_Sector"));
                }
            });
            makeBootable(devicePath, bootPartition);

            // cleanup
            source.unmountTmpPartitions();
            if (!umount(bootPartition)) {
                return false;
            }
            return umount(systemPartition);
        }
    }

    private class ISOCreator
            extends SwingWorker<Boolean, String>
            implements PropertyChangeListener {

        private IsoStep step;
        private String isoPath;
        private LogindInhibit inhibit = null;

        @Override
        protected Boolean doInBackground() throws Exception {
            inhibit = new LogindInhibit("Creating ISO");

            try {
                publish(STRINGS.getString("Copying_Files"));
                String tmpDirectory = tmpDirTextField.getText();

                // create new temporary directory in selected directory
                File tmpDirFile = LernstickFileTools.createTempDirectory(
                        new File(tmpDirectory), "Lernstick-ISO");
                String targetRootDirectory = tmpDirFile.getPath();
                String targetDirectory = targetRootDirectory + "/build";
                new File(targetDirectory).mkdirs();

                // copy boot files
                if (!source.hasBootPartition()) {
                    // legacy system without separate boot partition
                    String copyScript = "#!/bin/sh" + '\n'
                            + "cd " + source.getSystemPath() + '\n'
                            + "find -not -name filesystem*.squashfs | cpio -pvdum \""
                            + targetDirectory + "\"";
                    processExecutor.executeScript(copyScript);

                } else {
                    // system with a separate boot partition
                    CopyJob bootCopyJob = new CopyJob(
                            new Source[]{source.getBootCopySource()},
                            new String[]{targetDirectory});
                    FileCopier fileCopier = new FileCopier();
                    fileCopier.copy(bootCopyJob);
                    source.unmountTmpPartitions();
                }

                if (systemMediumRadioButton.isSelected()) {
                    createSquashFS(targetDirectory);
                }

                setDataPartitionMode(
                        isoDataPartitionModeComboBox, targetDirectory);

                // syslinux -> isolinux
                final String ISOLINUX_DIR = targetDirectory + "/isolinux";
                moveFile(targetDirectory + "/syslinux", ISOLINUX_DIR);
                final String syslinuxBinPath = ISOLINUX_DIR + "/syslinux.bin";
                File syslinuxBinFile = new File(syslinuxBinPath);
                if (syslinuxBinFile.exists()) {
                    moveFile(syslinuxBinPath, ISOLINUX_DIR + "/isolinux.bin");
                }
                moveFile(ISOLINUX_DIR + "/syslinux.cfg",
                        ISOLINUX_DIR + "/isolinux.cfg");

                // replace "syslinux" with "isolinux" in some files
                Pattern pattern = Pattern.compile("syslinux");
                replaceText(ISOLINUX_DIR + "/exithelp.cfg",
                        pattern, "isolinux");
                replaceText(ISOLINUX_DIR + "/stdmenu.cfg",
                        pattern, "isolinux");
                replaceText(ISOLINUX_DIR + "/isolinux.cfg",
                        pattern, "isolinux");

                // update md5sum
                publish(STRINGS.getString("Updating_Checksums"));
                String md5header = "This file contains the list of md5 "
                        + "checksums of all files on this medium.\n"
                        + "\n"
                        + "You can verify them automatically with the "
                        + "'integrity-check' boot parameter,\n"
                        + "or, manually with: 'md5sum -c md5sum.txt'.";
                try (FileWriter fileWriter
                        = new FileWriter(targetDirectory + "/md5sum.txt")) {
                    fileWriter.write(md5header);
                }
                String md5Script = "cd \"" + targetDirectory + "\"\n"
                        + "find . -type f \\! -path './isolinux/isolinux.bin' "
                        + "\\! -path './boot/grub/stage2_eltorito' -print0 | "
                        + "sort -z | xargs -0 md5sum >> md5sum.txt";
                processExecutor.executeScript(md5Script);

                // create new iso image
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        toISOProgressBar.setIndeterminate(false);
                    }
                });
                isoPath = targetRootDirectory + "/Lernstick.iso";
                step = IsoStep.GENISOIMAGE;
                publish(STRINGS.getString("Creating_Image"));
                processExecutor.addPropertyChangeListener(this);
                String isoLabel = isoLabelTextField.getText();

                String xorrisoScript = "#!/bin/sh\n"
                        + "cd \"" + targetDirectory + "\"\n"
                        + "xorriso -dev \"" + isoPath + "\" -volid LERNSTICK";
                if (!(isoLabel.isEmpty())) {
                    xorrisoScript += " -application_id \"" + isoLabel + "\"";
                }
                xorrisoScript += " -boot_image isolinux dir=isolinux"
                        + " -joliet on"
                        + " -compliance iso_9660_level=3"
                        + " -add .";

                int returnValue = processExecutor.executeScript(
                        true, true, xorrisoScript);

                processExecutor.removePropertyChangeListener(this);
                if (returnValue != 0) {
                    return false;
                }

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            return true;
        }

        @Override
        protected void process(List<String> chunks) {
            toISOProgressBar.setString(chunks.get(0));
        }

        @Override
        protected void done() {
            if (inhibit != null) {
                inhibit.delete();
            }

            String message = null;
            try {
                if (get()) {
                    message = STRINGS.getString("DLCopy.isoDoneLabel.text");
                    message = MessageFormat.format(message, isoPath);
                } else {
                    message = STRINGS.getString("Error_ISO_Creation");
                }
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            isoDoneLabel.setText(message);
            showCard(cardPanel, "toISODonePanel");
            previousButton.setEnabled(true);
            nextButton.setText(STRINGS.getString("Done"));
            nextButton.setIcon(new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/exit.png")));
            nextButton.setEnabled(true);
            previousButton.requestFocusInWindow();
            getRootPane().setDefaultButton(previousButton);
            //Toolkit.getDefaultToolkit().beep();
            playNotifySound();
            toFront();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String propertyName = evt.getPropertyName();
            if (ProcessExecutor.LINE.equals(propertyName)) {
                String line = (String) evt.getNewValue();
                switch (step) {
                    case GENISOIMAGE:
                        Matcher matcher = xorrisoPattern.matcher(line);
                        if (matcher.matches()) {
                            String progressString = matcher.group(1).trim();
                            try {
                                final int progress
                                        = Integer.parseInt(progressString);
                                String message = STRINGS.getString(
                                        "Creating_Image_Progress");
                                message = MessageFormat.format(
                                        message, progress + "%");
                                publish(message);
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        toISOProgressBar.setValue(progress);
                                    }
                                });
                            } catch (NumberFormatException ex) {
                                LOGGER.log(Level.WARNING,
                                        "could not parse xorriso progress",
                                        ex);
                            }
                        }

                        break;

                    case MKSQUASHFS:
                        // mksquashfs output looks like this:
                        // [==========           ]  43333/230033  18%
                        matcher = mksquashfsPattern.matcher(line);
                        if (matcher.matches()) {
                            String doneString = matcher.group(1).trim();
                            String maxString = matcher.group(2).trim();
                            try {
                                int doneInt = Integer.parseInt(doneString);
                                int maxInt = Integer.parseInt(maxString);
                                final int progress = (doneInt * 100) / maxInt;
                                String message = STRINGS.getString(
                                        "Compressing_Filesystem_Progress");
                                message = MessageFormat.format(
                                        message, progress + "%");
                                publish(message);
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        toISOProgressBar.setValue(progress);
                                    }
                                });
                            } catch (NumberFormatException ex) {
                                LOGGER.log(Level.WARNING,
                                        "could not parse mksquashfs progress",
                                        ex);
                            }
                        }
                        break;

                    default:
                        LOGGER.log(Level.WARNING, "unsupported step {0}", step);
                }
            }
        }

        private void createSquashFS(String targetDirectory)
                throws IOException, DBusException {
            publish(STRINGS.getString("Mounting_Partitions"));
            File tmpDir = createTempDir("usb2iso");

            // get a list of all available squashfs
            FilenameFilter squashFsFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".squashfs");
                }
            };
            File liveDir = new File(source.getSystemPath(), "live");
            File[] squashFileSystems = liveDir.listFiles(squashFsFilter);

            // mount all squashfs read-only in temporary directories
            List<String> readOnlyMountPoints = new ArrayList<>();
            for (int i = 0; i < squashFileSystems.length; i++) {
                File roDir = new File(tmpDir, "ro" + (i + 1));
                roDir.mkdirs();
                String roPath = roDir.getPath();
                readOnlyMountPoints.add(roPath);
                String filePath = squashFileSystems[i].getPath();
                processExecutor.executeProcess(
                        "mount", "-o", "loop", filePath, roPath);
            }

            // mount persistence
            MountInfo dataMountInfo = source.getDataPartition().mount();
            String dataPartitionPath = dataMountInfo.getMountPath();

            // union base image with persistence
            // ---------------------------------
            // We need an rwDir so that we can change some settings in the
            // lernstickWelcome properties below without affecting the current
            // data partition.
            // rwDir and cowDir are placed in /run/ because it is one of
            // the few directories that are not aufs itself.
            // Nested aufs is not (yet) supported...
            File runDir = new File("/run/");
            File rwDir = LernstickFileTools.createTempDirectory(runDir, "rw");
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("br=");
            stringBuilder.append(rwDir.getPath());
            stringBuilder.append(':');
            stringBuilder.append(dataPartitionPath);
            // The additional option "=ro+wh" for the data partition is
            // absolutely neccessary! Otherwise the whiteouts (info about
            // deleted files) in the data partition are not applied!!!
            stringBuilder.append("=ro+wh");
            for (String readOnlyMountPoint : readOnlyMountPoints) {
                stringBuilder.append(':');
                stringBuilder.append(readOnlyMountPoint);
            }
            String branchDefinition = stringBuilder.toString();

            // To create the file system union, we need a temporary and
            // writable xino file that must not reside in an aufs. Therefore
            // we use a file in the /run directory, which is a writable
            // tmpfs.
            File xinoTmpFile = File.createTempFile(
                    ".aufs.xino", "", new File("/run/"));
            xinoTmpFile.delete();
            File cowDir = LernstickFileTools.createTempDirectory(runDir, "cow");
            String cowPath = cowDir.getPath();
            processExecutor.executeProcess("mount", "-t", "aufs",
                    "-o", "xino=" + xinoTmpFile.getPath(),
                    "-o", branchDefinition, "none", cowPath);

            // apply settings in cow directory
            Properties lernstickWelcomeProperties = new Properties();
            File propertiesFile = new File(cowDir, "/etc/lernstickWelcome");
            try (FileReader reader = new FileReader(propertiesFile)) {
                lernstickWelcomeProperties.load(reader);
            } catch (IOException iOException) {
                LOGGER.log(Level.WARNING, "", iOException);
            }
            lernstickWelcomeProperties.setProperty("ShowNotUsedInfo",
                    showNotUsedDialogCheckBox.isSelected()
                            ? "true" : "false");
            lernstickWelcomeProperties.setProperty("AutoStartInstaller",
                    autoStartInstallerCheckBox.isSelected()
                            ? "true" : "false");
            try (FileWriter writer = new FileWriter(propertiesFile)) {
                lernstickWelcomeProperties.store(
                        writer, "lernstick Welcome properties");
            } catch (IOException iOException) {
                LOGGER.log(Level.WARNING, "", iOException);
            }

            // create new squashfs image
            step = IsoStep.MKSQUASHFS;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    toISOProgressBar.setIndeterminate(false);
                }
            });
            publish(STRINGS.getString("Compressing_Filesystem"));
            processExecutor.addPropertyChangeListener(this);
            int exitValue = processExecutor.executeProcess("mksquashfs",
                    cowPath, targetDirectory + "/live/filesystem.squashfs",
                    "-comp", "xz");
            if (exitValue != 0) {
                throw new IOException(
                        STRINGS.getString("Error_Creating_Squashfs"));
            }
            processExecutor.removePropertyChangeListener(this);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    toISOProgressBar.setIndeterminate(true);
                }
            });

            // umount all partitions
            umount(cowPath);
            if (!dataMountInfo.alreadyMounted()) {
                source.getDataPartition().umount();
            }
            for (String readOnlyMountPoint : readOnlyMountPoints) {
                umount(readOnlyMountPoint);
            }
        }
    }

    private class Repairer extends SwingWorker<Boolean, Void> {

        private int selectionCount;
        private int currentDevice;

        @Override
        protected Boolean doInBackground() throws Exception {

            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showCard(cardPanel, "repairPanel");
                }
            });

            // repair all selected storage devices
            int[] selectedIndices
                    = repairStorageDeviceList.getSelectedIndices();
            selectionCount = selectedIndices.length;
            for (int i : selectedIndices) {

                StorageDevice storageDevice
                        = (StorageDevice) repairStorageDeviceListModel.get(i);

                // update overall progress message
                currentDevice++;
                String pattern = STRINGS.getString("Repair_Device_Info");
                final String message = MessageFormat.format(pattern,
                        currentDevice, selectionCount,
                        storageDevice.getVendor() + " "
                        + storageDevice.getModel(),
                        " (" + DLCopy.STRINGS.getString("Size") + ": "
                        + LernstickFileTools.getDataVolumeString(
                                storageDevice.getSize(), 1)
                        + ", "
                        + DLCopy.STRINGS.getString("Revision") + ": "
                        + storageDevice.getRevision() + ", "
                        + DLCopy.STRINGS.getString("Serial") + ": "
                        + storageDevice.getSerial() + ", "
                        + "&#47;dev&#47;" + storageDevice.getDevice()
                        + ")");
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        currentlyRepairedDeviceLabel.setText(message);
                    }
                });
                LOGGER.log(Level.INFO,
                        "repairing storage device: {0} of {1} ({2})",
                        new Object[]{
                            currentDevice, selectionCount, storageDevice
                        });

                // repair
                Partition dataPartition = storageDevice.getDataPartition();
                if (formatDataPartitionRadioButton.isSelected()) {
                    // format data partition
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            repairProgressBar.setString(STRINGS.getString(
                                    "Formatting_Data_Partition"));
                        }
                    });

                    formatPersistencePartition(
                            "/dev/" + dataPartition.getDeviceAndNumber());
                } else {
                    // remove files from data partition
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            repairProgressBar.setString(STRINGS.getString(
                                    "Removing_Selected_Files"));
                        }
                    });

                    MountInfo mountInfo = dataPartition.mount();
                    String mountPoint = mountInfo.getMountPath();
                    boolean resetHome = homeDirectoryCheckBox.isSelected();
                    if (systemFilesCheckBox.isSelected() && resetHome) {
                        // remove all files
                        // but keep "/lost+found/" and "persistence.conf"
                        processExecutor.executeProcess("find", mountPoint,
                                "!", "-regex", mountPoint,
                                "!", "-regex", mountPoint + "/lost\\+found",
                                "!", "-regex", mountPoint + "/persistence.conf",
                                "-exec", "rm", "-rf", "{}", ";");
                    } else {
                        if (systemFilesCheckBox.isSelected()) {
                            // remove all files but keep
                            // "/lost+found/", "persistence.conf" and "/home/"
                            processExecutor.executeProcess("find", mountPoint,
                                    "!", "-regex", mountPoint,
                                    "!", "-regex", mountPoint + "/lost\\+found",
                                    "!", "-regex", mountPoint + "/persistence.conf",
                                    "!", "-regex", mountPoint + "/home.*",
                                    "-exec", "rm", "-rf", "{}", ";");
                        }
                        if (resetHome) {
                            // only remove "/home/user/"
                            processExecutor.executeProcess(
                                    "rm", "-rf", mountPoint + "/home/user/");
                        }
                    }
                    if (resetHome) {
                        // restore "/home/user/" from "/etc/skel/"
                        processExecutor.executeProcess("mkdir",
                                mountPoint + "/home/");
                        processExecutor.executeProcess("cp", "-a",
                                "/etc/skel/", mountPoint + "/home/user/");
                        processExecutor.executeProcess("chown", "-R",
                                "user.user", mountPoint + "/home/user/");
                    }
                    if (!mountInfo.alreadyMounted()) {
                        umount(dataPartition);
                    }
                }

                LOGGER.log(Level.INFO, "repairing of storage device finished: "
                        + "{0} of {1} ({2})", new Object[]{
                            currentDevice, selectionCount, storageDevice
                        });
            }

            return true;
        }

        @Override
        protected void done() {
            setTitle(STRINGS.getString("DLCopy.title"));
            try {
                if (get()) {
                    doneLabel.setText(STRINGS.getString("Repair_Done"));
                    showCard(cardPanel, "donePanel");
                    previousButton.setEnabled(true);
                    nextButton.setText(STRINGS.getString("Done"));
                    nextButton.setIcon(new ImageIcon(getClass().getResource(
                            "/ch/fhnw/dlcopy/icons/exit.png")));
                    nextButton.setEnabled(true);
                    previousButton.requestFocusInWindow();
                    getRootPane().setDefaultButton(previousButton);
                    playNotifySound();
                    toFront();
                } else {
                    switchToRepairSelection();
                }
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }
    }

    private class SwapInfo {

        private String file;
        private final long remainingFreeMemory;

        public SwapInfo(String swapLine) throws IOException {
            long swapSize = 0;
            // the swaps line has the following syntax
            // <filename> <type> <size> <used> <priority>
            // e.g.:
            // /media/live-rw/live.swp file 1048568 0 -1
            // (separation with spaces and TABs is slightly caotic, therefore we
            // use regular expressions to parse the line)
            Pattern pattern = Pattern.compile("(\\p{Graph}+)\\p{Blank}+"
                    + "\\p{Graph}+\\p{Blank}+(\\p{Graph}+).*");
            Matcher matcher = pattern.matcher(swapLine);
            if (matcher.matches()) {
                file = matcher.group(1);
                swapSize = Long.valueOf(matcher.group(2)) * 1024;
            } else {
                String warningMessage
                        = "Could not parse swaps line:\n" + swapLine;
                LOGGER.warning(warningMessage);
                throw new IOException(warningMessage);
            }

            long memFree = 0;
            pattern = Pattern.compile("\\p{Graph}+\\p{Blank}+(\\p{Graph}+).*");
            List<String> meminfo = LernstickFileTools.readFile(
                    new File("/proc/meminfo"));
            for (String meminfoLine : meminfo) {
                if (meminfoLine.startsWith("MemFree:")
                        || meminfoLine.startsWith("Buffers:")
                        || meminfoLine.startsWith("Cached:")
                        || meminfoLine.startsWith("SwapFree:")) {
                    matcher = pattern.matcher(meminfoLine);
                    if (matcher.matches()) {
                        memFree += Long.valueOf(matcher.group(1)) * 1024;
                    } else {
                        String warningMessage = "Could not parse meminfo line:\n"
                                + meminfoLine;
                        LOGGER.warning(warningMessage);
                        throw new IOException(warningMessage);
                    }
                }
            }
            remainingFreeMemory = memFree - swapSize;
        }

        /**
         * returns the swap file/partition
         *
         * @return the swap file/partition
         */
        public String getFile() {
            return file;
        }

        /**
         * returns the remaining free memory when this swap file/partition would
         * be switched off
         *
         * @return the remaining free memory when this swap file/partition would
         * be switched off
         */
        public long getRemainingFreeMemory() {
            return remainingFreeMemory;
        }
    }

    private class UdisksMonitorThread extends Thread {

        // use local ProcessExecutor because the udisks process is blocking and
        // long-running
        private final ProcessExecutor executor = new ProcessExecutor();

        @Override
        public void run() {
            String binaryName = null;
            String parameter = null;
            if (DbusTools.DBUS_VERSION == DbusTools.DBUS_VERSION.V1) {
                binaryName = "udisks";
                parameter = "--monitor";
            } else {
                binaryName = "udisksctl";
                parameter = "monitor";
            }
            executor.addPropertyChangeListener(DLCopy.this);
            executor.executeProcess(binaryName, parameter);
        }

        public void stopMonitoring() {
            executor.destroy();
        }
    }

    private StorageDevice getStorageDeviceAfterTimeout(
            String path, boolean includeHardDisks) throws DBusException {
        // It has happened that "udisks --enumerate" returns a valid storage
        // device but not yet its partitions. Therefore we give the system
        // a little break after storage devices have been added.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        StorageDevice storageDevice = getStorageDevice(path, includeHardDisks);
        LOGGER.log(Level.INFO,
                "storage device of path {0}: {1}",
                new Object[]{path, storageDevice});
        return storageDevice;
    }

    private class InstallStorageDeviceAdder extends SwingWorker<Void, Void> {

        private final String addedPath;
        private StorageDevice addedDevice;

        public InstallStorageDeviceAdder(String addedPath) {
            this.addedPath = addedPath;
            storageDeviceListUpdateDialogHandler.addPath(addedPath);
        }

        @Override
        protected Void doInBackground() throws Exception {
            addedDevice = getStorageDeviceAfterTimeout(addedPath,
                    installShowHarddisksCheckBox.isSelected());
            return null;
        }

        @Override
        protected void done() {
            storageDeviceListUpdateDialogHandler.removePath(addedPath);

            if (addedDevice == null) {
                return;
            }
            synchronized (installStorageDeviceListModel) {
                // do nothing, if device was added in the meantime
                // e.g. via InstallStorageDeviceListUpdater
                if (!installStorageDeviceListModel.contains(addedDevice)) {
                    addDeviceToList(installStorageDeviceList, addedDevice);
                    installStorageDeviceListChanged();
                    installStorageDeviceListSelectionChanged();
                }
            }
        }
    }

    private class InstallStorageDeviceListUpdater
            extends SwingWorker<Void, Void> {

        private final ModalDialogHandler dialogHandler;
        private final Object[] selectedValues;
        private final boolean showHardDisks;
        private List<StorageDevice> storageDevices;

        public InstallStorageDeviceListUpdater() {
            StorageDeviceListUpdateDialog dialog
                    = new StorageDeviceListUpdateDialog(DLCopy.this);
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
            // remember selected values so that we can restore the selection
            selectedValues = installStorageDeviceList.getSelectedValues();
            showHardDisks = installShowHarddisksCheckBox.isSelected();
        }

        @Override
        protected Void doInBackground() throws Exception {
            synchronized (installStorageDeviceListModel) {
                installStorageDeviceListModel.clear();
                try {
                    storageDevices = getStorageDevices(showHardDisks, false);
                    Collections.sort(storageDevices);
                    for (StorageDevice device : storageDevices) {
                        installStorageDeviceListModel.addElement(device);
                    }
                } catch (IOException | DBusException ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                    throw ex;
                }
            }
            return null;
        }

        @Override
        protected void done() {
            // try to restore the previous selection
            for (Object selectedValue : selectedValues) {
                int index = storageDevices.indexOf(selectedValue);
                if (index != -1) {
                    installStorageDeviceList.addSelectionInterval(index, index);
                }
            }

            installStorageDeviceListChanged();
            installStorageDeviceListSelectionChanged();
            dialogHandler.hide();
        }
    }

    private class UpgradeStorageDeviceAdder extends SwingWorker<Void, Void> {

        private final String addedPath;
        private StorageDevice addedDevice;

        public UpgradeStorageDeviceAdder(String addedPath) {
            this.addedPath = addedPath;
            storageDeviceListUpdateDialogHandler.addPath(addedPath);
        }

        @Override
        protected Void doInBackground() throws Exception {

            addedDevice = getStorageDeviceAfterTimeout(addedPath,
                    upgradeShowHarddisksCheckBox.isSelected());

            if (addedDevice != null) {
                // init all infos so that later rendering does not block
                // in Swing Event Thread
                addedDevice.getUpgradeVariant();
                // safety wait, otherwise the call below to getPartitions()
                // fails very often
                Thread.sleep(7000);
                for (Partition partition : addedDevice.getPartitions()) {
                    try {
                        if (partition.isPersistencePartition()) {
                            partition.getUsedSpace(true);
                        } else {
                            partition.getUsedSpace(false);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        }

        @Override
        protected void done() {
            storageDeviceListUpdateDialogHandler.removePath(addedPath);

            if (addedDevice == null) {
                return;
            }
            synchronized (upgradeStorageDeviceListModel) {
                // do nothing, if device was added in the meantime
                // e.g. via UpgradeStorageDeviceListUpdater
                if (!upgradeStorageDeviceListModel.contains(addedDevice)) {
                    addDeviceToList(upgradeStorageDeviceList, addedDevice);
                    upgradeStorageDeviceListChanged();
                    upgradeStorageDeviceListSelectionChanged();
                }
            }
        }
    }

    private class UpgradeStorageDeviceListUpdater
            extends SwingWorker<Void, Void> {

        private final ModalDialogHandler dialogHandler;
        private final Object[] selectedValues;
        private final boolean showHardDisks;
        private List<StorageDevice> storageDevices;

        public UpgradeStorageDeviceListUpdater() {
            StorageDeviceListUpdateDialog dialog
                    = new StorageDeviceListUpdateDialog(DLCopy.this);
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
            // remember selected values so that we can restore the selection
            selectedValues = upgradeStorageDeviceList.getSelectedValues();
            showHardDisks = upgradeShowHarddisksCheckBox.isSelected();
            upgradeStorageDeviceListModel.clear();
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                storageDevices = getStorageDevices(showHardDisks, false);
                Collections.sort(storageDevices);
                // init all infos so that later rendering does not block
                // in Swing Event Thread
                for (StorageDevice device : storageDevices) {
                    device.getUpgradeVariant();
                    for (Partition partition : device.getPartitions()) {
                        try {
                            if (partition.isPersistencePartition()) {
                                partition.getUsedSpace(true);
                            } else {
                                partition.getUsedSpace(false);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

            } catch (IOException | DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                throw ex;
            }
            return null;
        }

        @Override
        protected void done() {
            // manipulate list model on Swing Event Thread
            for (StorageDevice device : storageDevices) {
                upgradeStorageDeviceListModel.addElement(device);
            }

            // try to restore the previous selection
            for (Object selectedValue : selectedValues) {
                int index = storageDevices.indexOf(selectedValue);
                if (index != -1) {
                    upgradeStorageDeviceList.addSelectionInterval(index, index);
                }
            }

            upgradeStorageDeviceListChanged();
            upgradeStorageDeviceListSelectionChanged();
            dialogHandler.hide();
        }
    }

    private class RepairStorageDeviceAdder extends SwingWorker<Void, Void> {

        private final String addedPath;
        private StorageDevice addedDevice;

        public RepairStorageDeviceAdder(final String addedPath) {
            this.addedPath = addedPath;
            storageDeviceListUpdateDialogHandler.addPath(addedPath);
        }

        @Override
        protected Void doInBackground() throws Exception {

            addedDevice = getStorageDeviceAfterTimeout(addedPath,
                    repairShowHarddisksCheckBox.isSelected());

            if (addedDevice != null) {
                // init all infos so that later rendering does not block
                // in Swing Event Thread
                // safety wait, otherwise the call below to getPartitions()
                // fails very often
                Thread.sleep(7000);
                addedDevice.getUpgradeVariant();
                for (Partition partition : addedDevice.getPartitions()) {
                    try {
                        partition.getUsedSpace(false);
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        }

        @Override
        protected void done() {
            storageDeviceListUpdateDialogHandler.removePath(addedPath);

            if (addedDevice == null) {
                return;
            }
            synchronized (repairStorageDeviceListModel) {
                // do nothing, if device was added in the meantime
                // e.g. via RepairStorageDeviceListUpdater
                if (!repairStorageDeviceListModel.contains(addedDevice)) {
                    addDeviceToList(repairStorageDeviceList, addedDevice);
                    repairStorageDeviceListChanged();
                    repairStorageDeviceListSelectionChanged();
                }
            }
        }
    }

    private class RepairStorageDeviceListUpdater
            extends SwingWorker<Void, Void> {

        private final ModalDialogHandler dialogHandler;
        private final Object[] selectedValues;
        private final boolean showHardDisks;
        private List<StorageDevice> storageDevices;

        public RepairStorageDeviceListUpdater() {
            StorageDeviceListUpdateDialog dialog
                    = new StorageDeviceListUpdateDialog(DLCopy.this);
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
            // remember selected values so that we can restore the selection
            selectedValues = repairStorageDeviceList.getSelectedValues();
            showHardDisks = repairShowHarddisksCheckBox.isSelected();
            repairStorageDeviceListModel.clear();
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                storageDevices = getStorageDevices(showHardDisks, true);
                Collections.sort(storageDevices);
                // init all infos so that later rendering does not block
                // in Swing Event Thread
                for (StorageDevice device : storageDevices) {
                    //device.canBeUpgraded();
                    for (Partition partition : device.getPartitions()) {
                        try {
                            partition.getUsedSpace(false);
                        } catch (Exception ignored) {
                        }
                    }
                }

            } catch (IOException | DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                throw ex;
            }
            return null;
        }

        @Override
        protected void done() {
            // manipulate list model on Swing Event Thread
            for (StorageDevice device : storageDevices) {
                repairStorageDeviceListModel.addElement(device);
            }

            // try to restore the previous selection
            for (Object selectedValue : selectedValues) {
                int index = storageDevices.indexOf(selectedValue);
                if (index != -1) {
                    repairStorageDeviceList.addSelectionInterval(index, index);
                }
            }

            repairStorageDeviceListChanged();
            repairStorageDeviceListSelectionChanged();
            dialogHandler.hide();
        }
    }

    private class BackupActionListener implements ActionListener {

        private final boolean backup;
        private final long start;
        private final ResourceBundle BUNDLE = ResourceBundle.getBundle(
                "ch/fhnw/jbackpack/Strings");

        public BackupActionListener(boolean backup) {
            this.backup = backup;
            start = System.currentTimeMillis();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            long fileCounter = rdiffBackupRestore.getFileCounter();
            if (fileCounter == 0) {
                // preparation is still running
                upgradeBackupProgressLabel.setText(" ");
                upgradeBackupFilenameLabel.setText(" ");
            } else {
                // files are processed
                String string = BUNDLE.getString(
                        backup
                                ? "Backing_Up_File"
                                : "Restoring_File_Not_Counted");
                string = MessageFormat.format(string, fileCounter);
                upgradeBackupProgressLabel.setText(string);
                String currentFile = rdiffBackupRestore.getCurrentFile();
                upgradeBackupFilenameLabel.setText(currentFile);
            }
            // update time information
            long time = System.currentTimeMillis() - start;
            String timeString = timeFormat.format(new Date(time));
            upgradeBackupTimeLabel.setText(timeString);
        }
    }

    private class RsyncActionListener implements ActionListener {

        private final long start;

        public RsyncActionListener() {
            start = System.currentTimeMillis();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            long time = System.currentTimeMillis() - start;
            String timeString = timeFormat.format(new Date(time));
            rsyncTimeLabel.setText(timeString);
        }
    }

    private class CpActionListener implements ActionListener {

        private final long start = System.currentTimeMillis();
        private final Pattern cpPattern = Pattern.compile("`(.*)' -> .*");
        private int pathIndex;
        private String currentLine = "";

        @Override
        public void actionPerformed(ActionEvent e) {
            // update file name
            Matcher matcher = cpPattern.matcher(currentLine);
            if (matcher.matches()) {
                cpFilenameLabel.setText(matcher.group(1).substring(pathIndex));
            }

            // update time
            long time = System.currentTimeMillis() - start;
            String timeString = timeFormat.format(new Date(time));
            cpTimeLabel.setText(timeString);
        }

        public void setSourceMountPoint(String sourceMountPoint) {
            pathIndex = sourceMountPoint.length();
        }

        public void setCurrentLine(String currentLine) {
            this.currentLine = currentLine;
        }
    }

    private class CopyJobsInfo {

        private final boolean bootTempMount;
        private final String destinationBootPath;
        private final String destinationSystemPath;
        private final CopyJob bootCopyJob;
        private final CopyJob bootFilesCopyJob;
        private final CopyJob systemCopyJob;

        public CopyJobsInfo(boolean bootTempMount,
                String destinationBootPath, String destinationSystemPath,
                CopyJob bootCopyJob, CopyJob bootFilesCopyJob,
                CopyJob systemCopyJob) {
            this.bootTempMount = bootTempMount;
            this.destinationBootPath = destinationBootPath;
            this.destinationSystemPath = destinationSystemPath;
            this.bootCopyJob = bootCopyJob;
            this.bootFilesCopyJob = bootFilesCopyJob;
            this.systemCopyJob = systemCopyJob;
        }

        public String getDestinationBootPath() {
            return destinationBootPath;
        }

        public String getDestinationSystemPath() {
            return destinationSystemPath;
        }

        public boolean isBootTempMounted() {
            return bootTempMount;
        }

        public CopyJob getBootCopyJob() {
            return bootCopyJob;
        }

        public CopyJob getBootFilesCopyJob() {
            return bootFilesCopyJob;
        }

        public CopyJob getSystemCopyJob() {
            return systemCopyJob;
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
    private javax.swing.JList installStorageDeviceList;
    private javax.swing.JTabbedPane installTabbedPane;
    private javax.swing.JScrollPane installationResultsScrollPane;
    private javax.swing.JTable installationResultsTable;
    private javax.swing.ButtonGroup isoButtonGroup;
    private javax.swing.JComboBox isoDataPartitionModeComboBox;
    private javax.swing.JLabel isoDataPartitionModeLabel;
    private javax.swing.JLabel isoDoneLabel;
    private javax.swing.JLabel isoLabelLabel;
    private javax.swing.JTextField isoLabelTextField;
    private javax.swing.JPanel isoOptionsCardPanel;
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
    private javax.swing.JCheckBox quickFormatCheckBox;
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
    private javax.swing.JLabel selectionLabel;
    private javax.swing.JCheckBox showNotUsedDialogCheckBox;
    private javax.swing.JButton sortAscendingButton;
    private javax.swing.JButton sortDescendingButton;
    private javax.swing.JLabel stepsLabel;
    private javax.swing.JPanel stepsPanel;
    private javax.swing.JScrollPane storageDeviceListScrollPane;
    private javax.swing.JLabel systemDefinitionLabel;
    private javax.swing.JCheckBox systemFilesCheckBox;
    private javax.swing.JPanel systemMediumPanel;
    private javax.swing.JPanel systemMediumPanelx;
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
    private javax.swing.JLabel upgradeBackupFilenameLabel;
    private javax.swing.JLabel upgradeBackupLabel;
    private javax.swing.JPanel upgradeBackupPanel;
    private javax.swing.JProgressBar upgradeBackupProgressBar;
    private javax.swing.JLabel upgradeBackupProgressLabel;
    private javax.swing.JLabel upgradeBackupTimeLabel;
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
