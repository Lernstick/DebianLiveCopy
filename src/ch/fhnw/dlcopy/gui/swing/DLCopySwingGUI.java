/*
 * DLCopySwingGUI.java
 *
 * Created on 16. April 2008, 09:14
 */
package ch.fhnw.dlcopy.gui.swing;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.DebianLiveDistribution;
import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.IsoCreator;
import ch.fhnw.dlcopy.IsoSystemSource;
import ch.fhnw.dlcopy.PartitionSizes;
import ch.fhnw.dlcopy.PartitionState;
import ch.fhnw.dlcopy.RepartitionStrategy;
import ch.fhnw.dlcopy.Resetter;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SquashFSCreator;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.Subdirectory;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.Upgrader;
import ch.fhnw.dlcopy.exceptions.NoExecutableExtLinuxException;
import ch.fhnw.dlcopy.exceptions.NoExtLinuxException;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.swing.preferences.DLCopySwingGUIPreferencesHandler;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationDestinationDetailsPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationDestinationSelectionPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationDestinationTransferPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.InstallationSourcePreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.MainMenuPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetBackupPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetDeletePreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetPrintPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetRestorePreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.ResetSelectionPreferences;
import ch.fhnw.dlcopy.gui.swing.preferences.UpgradePreferences;
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
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
        ListDataListener, ListSelectionListener {

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
    private final DefaultListModel<StorageDevice> installTransferStorageDeviceListModel
            = new DefaultListModel<>();
    private final DefaultListModel<StorageDevice> upgradeStorageDeviceListModel
            = new DefaultListModel<>();
    private final DefaultListModel<StorageDevice> resetStorageDeviceListModel
            = new DefaultListModel<>();
    private InstallStorageDeviceRenderer installStorageDeviceRenderer;
    private final InstallTransferStorageDeviceRenderer installTransferStorageDeviceRenderer;
    private final UpgradeStorageDeviceRenderer upgradeStorageDeviceRenderer;
    private final ResetStorageDeviceRenderer resetStorageDeviceRenderer;
    private final DateFormat timeFormat;

    private enum State {

        INSTALL_INFORMATION, INSTALL_SELECTION, INSTALLATION,
        UPGRADE_INFORMATION, UPGRADE_SELECTION, UPGRADE,
        RESET_INFORMATION, RESET_SELECTION, RESET,
        ISO_INFORMATION, ISO_SELECTION, ISO_INSTALLATION
    }
    private State state = State.INSTALL_INFORMATION;

    private SystemSource runningSystemSource;
    private SystemSource isoSystemSource;
    private SystemSource systemSource = null;

    private boolean persistenceBoot;
    private boolean textFieldTriggeredSliderChange;
    private boolean listSelectionTriggeredSliderChange;
    private int explicitExchangeSize;

    private DebianLiveDistribution debianLiveDistribution;

    private final UdisksMonitorThread udisksMonitorThread;
    private final DefaultListModel<String> upgradeOverwriteListModel;
    private JFileChooser addFileChooser;
    private RdiffBackupRestore rdiffBackupRestore;

    private final ResultsTableModel installationResultsTableModel;
    private final ResultsTableModel upgradeResultsTableModel;
    private final ResultsTableModel resultsTableModel;
    private final SubdirectoryTableModel subdirectoryTableModel;
    private final List<Subdirectory> orderedSubdirectoriesEntries;
    private CpActionListener cpActionListener;
    private UpdateChangingDurationsTableActionListener updateTableActionListener;
    private Timer tableUpdateTimer;

    private final static Pattern ADDED_PATTERN = Pattern.compile(
            ".*: Added (/org/freedesktop/UDisks2/block_devices/.*)");
    private final static Pattern REMOVED_PATTERN = Pattern.compile(
            ".*: Removed (/org/freedesktop/UDisks2/block_devices/.*)");

    private final StorageDeviceListUpdateDialogHandler storageDeviceListUpdateDialogHandler
            = new StorageDeviceListUpdateDialogHandler(this);
    private final Color DARK_GREEN = new Color(0, 190, 0);

    private int batchCounter;
    private List<StorageDeviceResult> resultsList;

    private Integer commandLineExchangePartitionSize;
    private String commandLineExchangePartitionFileSystem;
    private Boolean commandLineCopyDataPartition;
    private Boolean commandLineReactivateWelcome;
    private boolean instantInstallation;
    private boolean instantUpgrade;
    private boolean autoUpgrade;
    private boolean isolatedAutoUpgrade;

    // some locks to synchronize the Installer, Upgrader and Resetter with their
    // corresponding StorageDeviceAdder
    private Lock installLock = new ReentrantLock();
    private Lock upgradeLock = new ReentrantLock();
    private Lock resetLock = new ReentrantLock();

    // global cache for file digests to speed up repeated file copy checks
    private final HashMap<String, byte[]> digestCache = new HashMap<>();

    private final InstallationDestinationSelectionPreferences installationDestinationSelectionPreferences;
    private final ResetPrintPreferences resetPrintPreferences;
    private final DLCopySwingGUIPreferencesHandler preferencesHandler;

    /**
     * Creates new form DLCopy
     *
     * @param arguments the command line arguments
     */
    public DLCopySwingGUI(String[] arguments) {
        /**
         * set up logging
         */
        List<Logger> loggers = new ArrayList<>();
        Logger globalLogger = Logger.getLogger("ch.fhnw");
        globalLogger.setLevel(Level.ALL);
        loggers.add(globalLogger);
        Logger dbusLogger = Logger.getLogger("ch.fhnw.util.DbusTools");
        dbusLogger.setLevel(Level.WARNING);
        loggers.add(dbusLogger);
        Logger fileCopierLibrary = Logger.getLogger("ch.fhnw.filecopier");
        fileCopierLibrary.setLevel(Level.INFO);
        loggers.add(fileCopierLibrary);

        // log to console
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.ALL);
        loggers.forEach(logger -> {
            logger.addHandler(consoleHandler);
        });
        // also log into a rotating temporaty file of max 50 MB
        try {
            FileHandler fileHandler = new FileHandler(""
                    + "%t/DebianLiveCopy", 50 * DLCopy.MEGA, 2, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            loggers.forEach(logger -> {
                logger.addHandler(fileHandler);
            });

        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.SEVERE, "can not create log file", ex);
        }
        // prevent double logs in console
        loggers.forEach(logger -> {
            logger.setUseParentHandlers(false);
        });
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
        parseCommandLineArguments(arguments);

        if (LOGGER.isLoggable(Level.INFO)) {
            switch (debianLiveDistribution) {
                case LERNSTICK:
                    LOGGER.info("using lernstick distribution");
                    break;
                case LERNSTICK_EXAM:
                    LOGGER.info(
                            "using lernstick exam environment distribution");
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

        initComponents();

        upgradeOverwriteListModel = new DefaultListModel<>();

        // init jump targets
        String[] jumpTargets = new String[]{
            STRINGS.getString("Main_Menu"),
            installButton.getText(),
            upgradeButton.getText(),
            toISOButton.getText(),
            resetButton.getText()
        };
        jumpComboBox.setModel(new DefaultComboBoxModel<>(jumpTargets));

        String[] dataPartitionModes = new String[]{
            STRINGS.getString("Read_Write"),
            STRINGS.getString("Read_Only"),
            STRINGS.getString("Not_Used")
        };
        dataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel<>(dataPartitionModes));
        isoDataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel<>(dataPartitionModes));

        String[] exchangePartitionFileSystemItems;
        if (debianLiveDistribution == DebianLiveDistribution.LERNSTICK_EXAM) {
            // default to NTFS for exchange partition
            // exFAT: rdiff-backup simply aborts with an AssertionError in
            //        set_case_sensitive_readwrite
            // FAT32: is case insensitive and therefore makes mirroring files
            //        like .config/geogebra (GeoGebra v5) and .config/GeoGebra
            //        (GeoGebra v6) at the same time impossible
            exchangePartitionFileSystemItems
                    = new String[]{"NTFS", "exFAT", "FAT32"};
        } else {
            // default to exFAT for exchange partition
            // (best compatibility with other OSes and feature set)
            exchangePartitionFileSystemItems
                    = new String[]{"exFAT", "NTFS", "FAT32"};
        }

        ComboBoxModel<String> exchangePartitionFileSystemsModel
                = new DefaultComboBoxModel<>(exchangePartitionFileSystemItems);
        exchangePartitionFileSystemComboBox.setModel(
                exchangePartitionFileSystemsModel);
        resetFormatExchangePartitionFileSystemComboBox.setModel(
                exchangePartitionFileSystemsModel);

        preferencesHandler = new DLCopySwingGUIPreferencesHandler();

        preferencesHandler.addPreference(new MainMenuPreferences(jumpComboBox));

        preferencesHandler.addPreference(new InstallationSourcePreferences(
                isoSourceRadioButton, isoSourceTextField));

        installationDestinationSelectionPreferences
                = new InstallationDestinationSelectionPreferences(
                        copyExchangePartitionCheckBox,
                        exchangePartitionSizeSlider, copyDataPartitionCheckBox,
                        dataPartitionModeComboBox);
        preferencesHandler.addPreference(
                installationDestinationSelectionPreferences);

        preferencesHandler.addPreference(
                new InstallationDestinationDetailsPreferences(
                        exchangePartitionFileSystemComboBox,
                        exchangePartitionTextField, autoNumberPatternTextField,
                        autoNumberStartSpinner, autoNumberIncrementSpinner,
                        autoNumberMinDigitsSpinner,
                        dataPartitionFileSystemComboBox, checkCopiesCheckBox));

        preferencesHandler.addPreference(
                new InstallationDestinationTransferPreferences(
                        transferExchangeCheckBox, transferHomeCheckBox,
                        transferNetworkCheckBox, transferPrinterCheckBox,
                        transferFirewallCheckBox, transferUserSettingsCheckBox));

        preferencesHandler.addPreference(new UpgradePreferences(
                upgradeSystemPartitionCheckBox, resetDataPartitionCheckBox,
                keepPrinterSettingsCheckBox, keepNetworkSettingsCheckBox,
                keepFirewallSettingsCheckBox, keepUserSettingsCheckBox,
                reactivateWelcomeCheckBox, removeHiddenFilesCheckBox,
                automaticBackupCheckBox, automaticBackupTextField,
                automaticBackupRemoveCheckBox, upgradeOverwriteListModel));

        preferencesHandler.addPreference(new ResetSelectionPreferences(
                resetAutomaticModeRadioButton, resetListModeRadioButton));

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
                = new ResetBackupPreferences(resetBackupCheckBox,
                        resetBackupSourceTextField,
                        resetBackupDestinationTextField);
        preferencesHandler.addPreference(resetBackupPreferences);
        orderedSubdirectoriesEntries = resetBackupPreferences.
                getOrderedSubdirectoriesEntries();

        preferencesHandler.addPreference(new ResetDeletePreferences(
                resetFormatExchangePartitionCheckBox,
                resetFormatExchangePartitionFileSystemComboBox,
                resetFormatExchangePartitionKeepLabelRadioButton,
                resetFormatExchangePartitionNewLabelRadioButton,
                resetFormatExchangePartitionNewLabelTextField,
                deleteOnDataPartitionCheckBox,
                formatDataPartitionRadioButton, removeFilesRadioButton,
                systemFilesCheckBox, homeDirectoryCheckBox));

        preferencesHandler.addPreference(new ResetRestorePreferences(
                resetRestoreDataCheckBox, resetRestoreConfigurationPanel));

        preferencesHandler.load();

        // do not show initial "{0}" placeholder
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString, 0, 0);
        installSelectionCountLabel.setText(countString);
        upgradeSelectionCountLabel.setText(countString);
        resetSelectionCountLabel.setText(countString);

        String text = STRINGS.getString("Boot_Definition");
        String bootSize = LernstickFileTools.getDataVolumeString(
                DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA, 1);
        text = MessageFormat.format(text, bootSize);
        bootDefinitionLabel.setText(text);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        systemSource = isoSourceRadioButton.isSelected()
                ? isoSystemSource
                : runningSystemSource;

        // because of its HTML content, the transfer label tends to resize
        // therefore we fix its size here
        Dimension preferredSize = transferLabel.getPreferredSize();
        transferLabel.setMinimumSize(preferredSize);
        transferLabel.setMaximumSize(preferredSize);

        installTransferStorageDeviceList.setModel(
                installTransferStorageDeviceListModel);
        installTransferStorageDeviceRenderer
                = new InstallTransferStorageDeviceRenderer(systemSource);
        installTransferStorageDeviceList.setCellRenderer(
                installTransferStorageDeviceRenderer);

        upgradeStorageDeviceRenderer
                = new UpgradeStorageDeviceRenderer(runningSystemSource);
        upgradeStorageDeviceList.setCellRenderer(upgradeStorageDeviceRenderer);

        resetStorageDeviceRenderer = new ResetStorageDeviceRenderer();
        resetStorageDeviceList.setCellRenderer(resetStorageDeviceRenderer);

        setSpinnerColums(autoNumberStartSpinner, 2);
        setSpinnerColums(autoNumberIncrementSpinner, 2);

        isoLabelTextField.setText("lernstick");

        subdirectoryTableModel = new SubdirectoryTableModel(
                resetBackupSubdirectoryTable, orderedSubdirectoriesEntries);

        // set renderer that respects the "enabled" state of the table
        setEnabledRespectingDefaultRenderer(
                resetBackupSubdirectoryTable, Boolean.class);
        setEnabledRespectingDefaultRenderer(
                resetBackupSubdirectoryTable, String.class);

        if (commandLineExchangePartitionFileSystem != null) {
            exchangePartitionFileSystemComboBox.setSelectedItem(
                    commandLineExchangePartitionFileSystem);
        }
        if (commandLineCopyDataPartition != null) {
            copyDataPartitionCheckBox.setSelected(commandLineCopyDataPartition);
        }

        // monitor udisks changes
        udisksMonitorThread = new UdisksMonitorThread();

        explicitExchangeSize = exchangePartitionSizeSlider.getValue();
        if (commandLineExchangePartitionSize != null) {
            explicitExchangeSize = commandLineExchangePartitionSize;
        }
        if (commandLineReactivateWelcome != null) {
            reactivateWelcomeCheckBox.setSelected(commandLineReactivateWelcome);
        }

        installationResultsTableModel = new ResultsTableModel(
                installationResultsTable);
        installationResultsTable.setModel(installationResultsTableModel);
        TableColumn sizeColumn
                = installationResultsTable.getColumnModel().getColumn(
                        ResultsTableModel.SIZE_COLUMN);
        sizeColumn.setCellRenderer(new SizeTableCellRenderer());
        installationResultsTable.setRowSorter(
                new ResultsTableRowSorter(installationResultsTableModel));

        upgradeResultsTableModel = new ResultsTableModel(
                upgradeResultsTable);
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

        // The preferred heigth of our device lists is much too small. Therefore
        // we hardcode it here.
        preferredSize = installStorageDeviceListScrollPane.getPreferredSize();
        preferredSize.height = 200;
        installStorageDeviceListScrollPane.setPreferredSize(preferredSize);

        switch (jumpComboBox.getSelectedIndex()) {
            case 1:
                globalShow("executionPanel");
                switchToInstallSelection();
                break;
            case 2:
                globalShow("executionPanel");
                switchToUpgradeSelection();
                break;
            case 3:
                globalShow("executionPanel");
                switchToISOSelection();
                break;
            case 4:
                globalShow("executionPanel");
                switchToResetSelection();
        }
    }

    // post-constructor initialization
    public void init() {
        upgradeOverwriteListModel.addListDataListener(this);
        upgradeOverwriteList.setModel(upgradeOverwriteListModel);

        tmpDirTextField.getDocument().addDocumentListener(this);

        getRootPane().setDefaultButton(installButton);
        installButton.requestFocusInWindow();

        URL imageURL = getClass().getResource(
                "/ch/fhnw/dlcopy/icons/usbpendrive_unmount.png");
        setIconImage(new ImageIcon(imageURL).getImage());

        installStorageDeviceList.setModel(installStorageDeviceListModel);
        installStorageDeviceRenderer = new InstallStorageDeviceRenderer(this);
        installStorageDeviceList.setCellRenderer(installStorageDeviceRenderer);

        upgradeStorageDeviceListModel.addListDataListener(this);
        upgradeStorageDeviceList.setModel(upgradeStorageDeviceListModel);

        resetStorageDeviceListModel.addListDataListener(this);
        resetStorageDeviceList.setModel(resetStorageDeviceListModel);

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
                = (AbstractDocument) exchangePartitionTextField.getDocument();
        exchangePartitionDocument.setDocumentFilter(new DocumentSizeFilter());
        exchangePartitionSizeTextField.getDocument().addDocumentListener(this);

        resetBackupSubdirectoryTable.setModel(subdirectoryTableModel);
        resetBackupSubdirectoryTable.setSelectionMode(
                ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resetBackupSubdirectoryTable.getSelectionModel().
                addListSelectionListener(this);

        if (autoUpgrade) {
            globalShow("executionPanel");
            switchToUpgradeSelection();
            upgradeAutomaticRadioButton.doClick();
        }

        if (isolatedAutoUpgrade) {
            // maximize frame
            setExtendedState(Frame.MAXIMIZED_BOTH);

            // only leave the upgrade parts visible
            stepsPanel.setVisible(false);
            executionPanelSeparator.setVisible(false);
            prevNextButtonPanel.setVisible(false);

            // reduce the upgrade parts
            upgradeSelectionHeaderLabel.setVisible(false);
            upgradeShowHarddisksCheckBox.setVisible(false);
            upgradeListModeRadioButton.setVisible(false);
            upgradeAutomaticRadioButton.setVisible(false);
            upgradeSelectionTabbedPane.remove(upgradeDetailsTabbedPane);
            upgradeTabbedPane.remove(upgradeReportPanel);

            // change colors
            upgradeNoMediaPanel.setBackground(Color.YELLOW);
            upgradePanel.setBackground(Color.RED);
            upgradeIndeterminateProgressPanel.setBackground(Color.RED);
            upgradeFileCopierPanel.setBackground(Color.RED);
            upgradeCopyPanel.setBackground(Color.RED);

            // change texts
            upgradeNoMediaLabel.setText(
                    STRINGS.getString("Insert_Media_Isolated"));

            // change insets
            GridBagLayout layout = (GridBagLayout) executionPanel.getLayout();
            GridBagConstraints constraints = layout.getConstraints(cardPanel);
            constraints.insets = new Insets(0, 0, 0, 0);
            layout.setConstraints(cardPanel, constraints);
        }

        if (instantInstallation) {
            globalShow("executionPanel");
            switchToInstallSelection();
        } else if (instantUpgrade) {
            globalShow("executionPanel");
            switchToUpgradeSelection();
        }

        pack();

        // The preferred width of the labels with HTML text is much too wide.
        // Therefore we reset the width to a sane size.
        Dimension size = getSize();
        size.width = 1060;
        setSize(size);

        // center on screen
        setLocationRelativeTo(null);

        udisksMonitorThread.start();
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
                            installStorageDeviceList, this, installLock)
                            .execute();
                    new InstallTransferStorageDeviceAdder(addedPath,
                            installShowHarddisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            installTransferStorageDeviceListModel,
                            installTransferStorageDeviceList, this, installLock)
                            .execute();
                    break;

                case UPGRADE_SELECTION:
                    new UpgradeStorageDeviceAdder(runningSystemSource,
                            addedPath,
                            upgradeShowHarddisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            upgradeStorageDeviceListModel,
                            upgradeStorageDeviceList, this, upgradeLock)
                            .execute();
                    break;

                case RESET_SELECTION:
                    new ResetStorageDeviceAdder(addedPath,
                            resetShowHarddisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            resetStorageDeviceListModel,
                            resetStorageDeviceList, this, resetLock,
                            resetListModeRadioButton.isSelected()).execute();
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
    public void valueChanged(ListSelectionEvent e) {
        if (e.getSource() == resetBackupSubdirectoryTable.getSelectionModel()) {
            int[] selectedRows = resetBackupSubdirectoryTable.getSelectedRows();
            if (selectedRows.length == 0) {
                resetMoveUpButton.setEnabled(false);
                resetMoveDownButton.setEnabled(false);
            } else {
                resetMoveUpButton.setEnabled(selectedRows[0] != 0);
                resetMoveDownButton.setEnabled(
                        selectedRows[selectedRows.length - 1]
                        != subdirectoryTableModel.getRowCount() - 1);
            }
        }
    }

    @Override
    public void showInstallProgress() {
        SwingUtilities.invokeLater(
                () -> showCard(cardPanel, "installTabbedPane"));
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
        Timer cpTimer = new Timer(1000, cpActionListener);
        cpTimer.setInitialDelay(0);
        cpTimer.start();
        SwingUtilities.invokeLater(() -> {
            cpFilenameLabel.setText(" ");
            cpPogressBar.setValue(0);
            cpTimeLabel.setText(timeFormat.format(new Date(0)));
            showCard(installCardPanel, "cpPanel");
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
        if (instantInstallation) {
            instantInstallation = false;
        }
        tableUpdateTimer.stop();
    }

    @Override
    public void upgradingDeviceStarted(StorageDevice storageDevice) {
        deviceStarted(storageDevice);

        int selectionCount = upgradeListModeRadioButton.isSelected()
                ? upgradeStorageDeviceList.getSelectedIndices().length
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
        setLabelTextonEDT(currentlyUpgradedDeviceLabel, deviceInfo);

        // add "in progress" entry to results table
        upgradeResultsTableModel.setList(resultsList);
    }

    @Override
    public void showUpgradeBackup() {
        SwingUtilities.invokeLater(() -> {
            upgradeBackupLabel.setText(
                    STRINGS.getString("Backing_Up_User_Data"));
            upgradeBackupDurationLabel.setText(
                    timeFormat.format(new Date(0)));
            showCard(upgradeCardPanel, "upgradeBackupPanel");
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
        SwingUtilities.invokeLater(() -> {
            upgradeCopyLabel.setText(STRINGS.getString(
                    "Backing_Up_Exchange_Partition"));
            showCard(upgradeCardPanel, "upgradeCopyPanel");
        });
        upgradeFileCopierPanel.setFileCopier(fileCopier);
    }

    @Override
    public void showUpgradeRestoreInit() {
        SwingUtilities.invokeLater(() -> {
            upgradeIndeterminateProgressBar.setString(
                    STRINGS.getString("Reading_Backup"));
            showCard(upgradeCardPanel, "upgradeIndeterminateProgressPanel");
        });
    }

    @Override
    public void showUpgradeRestoreRunning() {
        SwingUtilities.invokeLater(() -> {
            upgradeBackupLabel.setText(
                    STRINGS.getString("Restoring_User_Data"));
            upgradeBackupDurationLabel.setText(
                    timeFormat.format(new Date(0)));
            showCard(upgradeCardPanel, "upgradeBackupPanel");
        });
    }

    @Override
    public void showUpgradeRestoreExchangePartition(FileCopier fileCopier) {
        SwingUtilities.invokeLater(() -> {
            upgradeCopyLabel.setText(STRINGS.getString(
                    "Restoring_Exchange_Partition"));
            showCard(upgradeCardPanel, "upgradeCopyPanel");
        });
        upgradeFileCopierPanel.setFileCopier(fileCopier);
    }

    @Override
    public void showUpgradeChangingPartitionSizes() {
        SwingUtilities.invokeLater(() -> {
            showCard(upgradeCardPanel, "upgradeIndeterminateProgressPanel");
        });
        setProgressBarStringOnEDT(upgradeIndeterminateProgressBar,
                STRINGS.getString("Changing_Partition_Sizes"));
    }

    @Override
    public void showUpgradeDataPartitionReset() {
        SwingUtilities.invokeLater(() -> {
            showCard(upgradeCardPanel, "upgradeIndeterminateProgressPanel");
        });
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
        SwingUtilities.invokeLater(() -> {
            showCard(upgradeCardPanel, "upgradeIndeterminateProgressPanel");
            upgradeIndeterminateProgressBar.setString(
                    STRINGS.getString("Resetting_System_Partition"));
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
        if (instantUpgrade) {
            instantUpgrade = false;
        }
        if (upgradeListModeRadioButton.isSelected()) {
            batchFinished(
                    "Upgrade_Done_From_Non_Removable_Device",
                    "Upgrade_Done_From_Removable_Device",
                    "Upgrade_Report");
        } else {
            if (isolatedAutoUpgrade) {
                upgradeNoMediaPanel.setBackground(DARK_GREEN);
                upgradeNoMediaLabel.setText(
                        STRINGS.getString("Upgrade_Done_Isolated"));
            }
            showCard(cardPanel, "upgradeSelectionTabbedPane");
            previousButton.setEnabled(true);
            previousButton.requestFocusInWindow();
            getRootPane().setDefaultButton(previousButton);
            toFront();
            playNotifySound();
            state = State.UPGRADE_SELECTION;
        }
        tableUpdateTimer.stop();
    }

    @Override
    public void showIsoProgressMessage(final String message) {
        SwingUtilities.invokeLater(() -> {
            toISOProgressBar.setIndeterminate(true);
            toISOProgressBar.setString(message);
        });
    }

    @Override
    public void showIsoProgressMessage(final String message, final int value) {
        SwingUtilities.invokeLater(() -> {
            toISOProgressBar.setIndeterminate(false);
            toISOProgressBar.setString(message);
            toISOProgressBar.setValue(value);
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
    public void showResetProgress() {
        // DON'T (!) use invokeLater here or we might run into a timing issue so
        // that after a very quick reset the resetProgressPanel is still shown
        // because the code below code gets executed much later.
        try {
            SwingUtilities.invokeAndWait(() -> {
                showCard(cardPanel, "resetPanel");
                showCard(resetCardPanel, "resetProgressPanel");
            });
        } catch (InterruptedException | InvocationTargetException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    @Override
    public void resettingDeviceStarted(StorageDevice storageDevice) {
        deviceStarted(storageDevice);

        String pattern = STRINGS.getString("Reset_Device_Info");
        String message = MessageFormat.format(pattern, batchCounter,
                resetStorageDeviceList.getSelectedIndices().length,
                storageDevice.getVendor() + " " + storageDevice.getModel(),
                " (" + STRINGS.getString("Size") + ": "
                + LernstickFileTools.getDataVolumeString(
                        storageDevice.getSize(), 1) + ", "
                + STRINGS.getString("Revision") + ": "
                + storageDevice.getRevision() + ", "
                + STRINGS.getString("Serial") + ": "
                + storageDevice.getSerial() + ", " + "&#47;dev&#47;"
                + storageDevice.getDevice() + ")");
        setLabelTextonEDT(currentlyResettingDeviceLabel, message);
    }

    @Override
    public void showPrintingDocuments() {
        setProgressBarStringOnEDT(resetProgressBar,
                STRINGS.getString("Printing_Documents"));
    }

    @Override
    public List<Path> selectDocumentsToPrint(
            final String type, final String mountPath, final List<Path> documents) {

        final List<Path> selectedDocuments = new ArrayList<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                PrintSelectionDialog dialog = new PrintSelectionDialog(
                        DLCopySwingGUI.this, type, mountPath, documents);
                dialog.setVisible(true);
                if (dialog.okPressed()) {
                    selectedDocuments.addAll(dialog.getSelectedDocuments());
                }
            });
        } catch (InterruptedException | InvocationTargetException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        return selectedDocuments;
    }

    @Override
    public void showResetBackup(FileCopier fileCopier) {
        SwingUtilities.invokeLater(
                () -> showCard(resetCardPanel, "resetBackupPanel"));
        resetBackupFileCopierPanel.setFileCopier(fileCopier);
    }

    @Override
    public void showResetRestore(FileCopier fileCopier) {
        // TODO: use a different panel to show this file copy progress?
        showResetBackup(fileCopier);
    }

    @Override
    public void showResetFormattingExchangePartition() {
        SwingUtilities.invokeLater(
                () -> showCard(resetCardPanel, "resetProgressPanel"));
        setProgressBarStringOnEDT(resetProgressBar,
                STRINGS.getString("Formatting_Exchange_Partition"));
    }

    @Override
    public void showResetFormattingDataPartition() {
        SwingUtilities.invokeLater(
                () -> showCard(resetCardPanel, "resetProgressPanel"));
        setProgressBarStringOnEDT(resetProgressBar,
                STRINGS.getString("Formatting_Data_Partition"));
    }

    @Override
    public void showResetRemovingFiles() {
        SwingUtilities.invokeLater(
                () -> showCard(resetCardPanel, "resetProgressPanel"));
        setProgressBarStringOnEDT(resetProgressBar,
                STRINGS.getString("Removing_Selected_Files"));
    }

    @Override
    public void resettingFinished(boolean success) {
        setTitle(STRINGS.getString("DLCopySwingGUI.title"));
        if (resetListModeRadioButton.isSelected()) {
            if (success) {
                doneLabel.setText(STRINGS.getString("Reset_Done"));
                showCard(cardPanel, "donePanel");
                processDone();
            } else {
                switchToResetSelection();
            }
        } else {
            showCard(resetSelectionCardPanel, "resetNoMediaPanel");
            showCard(cardPanel, "resetSelectionTabbedPane");
            previousButton.setEnabled(true);
            previousButton.requestFocusInWindow();
            getRootPane().setDefaultButton(previousButton);
            toFront();
            playNotifySound();
            state = State.RESET_SELECTION;
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
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                DLCopySwingGUI.this, errorMessage,
                STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE)
        );
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
            stringBuilder.append(storageDevice.getRaidLevel());
            stringBuilder.append(", ");
            stringBuilder.append(storageDevice.getRaidDeviceCount());
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
        stringBuilder.append("</b>");
        if (!stringBuilder.toString().equals("<html><b></b>")) {
            stringBuilder.append(", ");
        }
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
        // GUI changes
        storageDeviceListChanged(
                installStorageDeviceListModel, installSelectionCardPanel,
                "installNoMediaPanel", "installSelectionTabbedPane",
                installStorageDeviceRenderer, installStorageDeviceList);
        updateInstallSelectionCountAndExchangeInfo();

        // run instant installation if needed
        if (instantInstallation) {
            installStorageDeviceList.setSelectionInterval(
                    0, installStorageDeviceListModel.size() - 1);
            try {
                checkAndInstallSelection(false);
            } catch (IOException | DBusException ex) {
                LOGGER.log(Level.SEVERE,
                        "checking the selected usb flash drive failed", ex);
            }
        }
    }

    public void installTransferStorageDeviceListChanged() {
        if (installTransferStorageDeviceListModel.size() == 0) {
            return;
        }
        installTransferStorageDeviceRenderer.setMaxSize(
                getMaxStorageDeviceSize(installTransferStorageDeviceListModel));
        installTransferStorageDeviceList.repaint();
    }

    /**
     * must be called whenever the upgrade storage device list changes to
     * execute some updates (e.g. maximum storage device size) and some sanity
     * checks
     */
    public void upgradeStorageDeviceListChanged() {
        if (upgradeListModeRadioButton.isSelected()) {
            storageDeviceListChanged(
                    upgradeStorageDeviceListModel, upgradeSelectionCardPanel,
                    "upgradeNoMediaPanel", "upgradeSelectionDeviceListPanel",
                    upgradeStorageDeviceRenderer, upgradeStorageDeviceList);
        }
        updateUpgradeSelectionCountAndNextButton();

        // run instant upgrade if needed
        if (instantUpgrade) {
            upgradeStorageDeviceList.setSelectionInterval(
                    0, upgradeStorageDeviceListModel.size() - 1);
            upgradeSelectedStorageDevices();
        }
    }

    /**
     * must be called whenever the reset storage device list changes to execute
     * some updates
     */
    public void resetStorageDeviceListChanged() {
        if (resetListModeRadioButton.isSelected()) {
            storageDeviceListChanged(
                    resetStorageDeviceListModel, resetSelectionCardPanel,
                    "resetNoMediaPanel", "resetSelectionDeviceListPanel",
                    resetStorageDeviceRenderer, resetStorageDeviceList);
        }
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
                        = installStorageDeviceListModel.get(selectedIndices[i]);
                long enlargedSystemSize = DLCopy.getEnlargedSystemSize(
                        systemSource.getSystemSize());
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

        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, installStorageDeviceListModel.size());
        installSelectionCountLabel.setText(countString);

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
        exchangePartitionTextField.setEnabled(exchange);
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
        copyExchangePartitionCheckBox.setEnabled(exchange && (systemSource != null)
                && systemSource.hasExchangePartition());

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
            StorageDevice storageDevice = upgradeStorageDeviceListModel.get(i);
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
                        LOGGER.log(Level.WARNING,
                                "unsupported systemUpgradeVariant: {0}",
                                systemUpgradeVariant);
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
        if ((selectionCount > 0) && canUpgrade
                && upgradeListModeRadioButton.isSelected()) {
            enableNextButton();
        } else {
            disableNextButton();
        }
    }

    /**
     * must be called whenever the selection count and next button for the
     * resetter needs an update
     */
    public void updateResetSelectionCountAndNextButton() {

        // early return
        if (state != State.RESET_SELECTION) {
            return;
        }

        // check all selected storage devices
        boolean canReset = true;
        List<StorageDevice> selectedStorageDevices
                = resetStorageDeviceList.getSelectedValuesList();
        for (StorageDevice storageDevice : selectedStorageDevices) {
            Partition dataPartition = storageDevice.getDataPartition();
            try {
                if ((dataPartition != null)
                        && dataPartition.isActivePersistencePartition()) {
                    canReset = false;
                    break;
                }
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                canReset = false;
                break;
            }
        }

        int selectionCount = selectedStorageDevices.size();
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString,
                selectionCount, resetStorageDeviceListModel.size());
        resetSelectionCountLabel.setText(countString);

        // update nextButton state
        if ((selectionCount > 0) && canReset) {
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

        resetExchangePartitionButtonGroup = new javax.swing.ButtonGroup();
        resetDataPartitionButtonGroup = new javax.swing.ButtonGroup();
        isoButtonGroup = new javax.swing.ButtonGroup();
        exchangeButtonGroup = new javax.swing.ButtonGroup();
        installSourceButtonGroup = new javax.swing.ButtonGroup();
        upgradeSelectionModeButtonGroup = new javax.swing.ButtonGroup();
        resetSelectionModeButtonGroup = new javax.swing.ButtonGroup();
        automaticPrintingButtonGroup = new javax.swing.ButtonGroup();
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
        resetButton = new javax.swing.JButton();
        jumpLabel1 = new javax.swing.JLabel();
        jumpComboBox = new javax.swing.JComboBox<>();
        jumpLabel2 = new javax.swing.JLabel();
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
        installTargetCardPanel = new javax.swing.JPanel();
        installTargetPanel = new javax.swing.JPanel();
        installSelectionHeaderLabel = new javax.swing.JLabel();
        installShowHarddisksCheckBox = new javax.swing.JCheckBox();
        installSelectionCardPanel = new javax.swing.JPanel();
        installSelectionTabbedPane = new javax.swing.JTabbedPane();
        installBasicsPanel = new javax.swing.JPanel();
        installSelectionCountLabel = new javax.swing.JLabel();
        installStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        installStorageDeviceList = new javax.swing.JList<>();
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
        installDetailsPanel = new javax.swing.JPanel();
        exchangePartitionDetailsPanel = new javax.swing.JPanel();
        exchangePartitionFileSystemPanel = new javax.swing.JPanel();
        exchangePartitionFileSystemLabel = new javax.swing.JLabel();
        exchangePartitionFileSystemComboBox = new javax.swing.JComboBox<>();
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
        autoNumberMinDigitsLabel = new javax.swing.JLabel();
        autoNumberMinDigitsSpinner = new javax.swing.JSpinner();
        dataPartitionDetailsPanel = new javax.swing.JPanel();
        dataPartitionFileSystemLabel = new javax.swing.JLabel();
        dataPartitionFileSystemComboBox = new javax.swing.JComboBox<>();
        checkCopiesCheckBox = new javax.swing.JCheckBox();
        installTransferPanel = new javax.swing.JPanel();
        transferLabel = new javax.swing.JLabel();
        transferCheckboxPanel = new javax.swing.JPanel();
        transferExchangeCheckBox = new javax.swing.JCheckBox();
        transferHomeCheckBox = new javax.swing.JCheckBox();
        transferNetworkCheckBox = new javax.swing.JCheckBox();
        transferPrinterCheckBox = new javax.swing.JCheckBox();
        transferFirewallCheckBox = new javax.swing.JCheckBox();
        transferUserSettingsCheckBox = new javax.swing.JCheckBox();
        installTransferScrollPane = new javax.swing.JScrollPane();
        installTransferStorageDeviceList = new javax.swing.JList<>();
        installNoMediaPanel = new javax.swing.JPanel();
        installNoMediaLabel = new javax.swing.JLabel();
        installNoSourcePanel = new javax.swing.JPanel();
        installNoSouceLabel = new javax.swing.JLabel();
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
        upgradeSelectionTabbedPane = new javax.swing.JTabbedPane();
        upgradeSelectionPanel = new javax.swing.JPanel();
        upgradeListModeRadioButton = new javax.swing.JRadioButton();
        upgradeShowHarddisksCheckBox = new javax.swing.JCheckBox();
        upgradeAutomaticRadioButton = new javax.swing.JRadioButton();
        upgradeSelectionSeparator = new javax.swing.JSeparator();
        upgradeSelectionCardPanel = new javax.swing.JPanel();
        upgradeSelectionDeviceListPanel = new javax.swing.JPanel();
        upgradeSelectionInfoPanel = new javax.swing.JPanel();
        upgradeSelectionHeaderLabel = new javax.swing.JLabel();
        upgradeSelectionCountLabel = new javax.swing.JLabel();
        upgradeStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        upgradeStorageDeviceList = new javax.swing.JList<>();
        upgradeExchangeDefinitionLabel = new javax.swing.JLabel();
        upgradeDataDefinitionLabel = new javax.swing.JLabel();
        upgradeBootDefinitionLabel = new javax.swing.JLabel();
        upgradeOsDefinitionLabel = new javax.swing.JLabel();
        upgradeNoMediaPanel = new javax.swing.JPanel();
        upgradeNoMediaLabel = new javax.swing.JLabel();
        upgradeDetailsTabbedPane = new javax.swing.JTabbedPane();
        upgradeOptionsPanel = new javax.swing.JPanel();
        upgradeSystemPartitionCheckBox = new javax.swing.JCheckBox();
        resetDataPartitionCheckBox = new javax.swing.JCheckBox();
        reactivateWelcomeCheckBox = new javax.swing.JCheckBox();
        keepPrinterSettingsCheckBox = new javax.swing.JCheckBox();
        keepNetworkSettingsCheckBox = new javax.swing.JCheckBox();
        keepFirewallSettingsCheckBox = new javax.swing.JCheckBox();
        keepUserSettingsCheckBox = new javax.swing.JCheckBox();
        removeHiddenFilesCheckBox = new javax.swing.JCheckBox();
        automaticBackupCheckBox = new javax.swing.JCheckBox();
        backupDestinationPanel = new javax.swing.JPanel();
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
        upgradeOverwriteList = new javax.swing.JList<>();
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
        resetInfoPanel = new javax.swing.JPanel();
        resetInfoLabel = new javax.swing.JLabel();
        resetSelectionTabbedPane = new javax.swing.JTabbedPane();
        resetSelectionPanel = new javax.swing.JPanel();
        resetListModeRadioButton = new javax.swing.JRadioButton();
        resetShowHarddisksCheckBox = new javax.swing.JCheckBox();
        resetAutomaticModeRadioButton = new javax.swing.JRadioButton();
        jSeparator2 = new javax.swing.JSeparator();
        resetSelectionCardPanel = new javax.swing.JPanel();
        resetNoMediaPanel = new javax.swing.JPanel();
        resetNoMediaLabel = new javax.swing.JLabel();
        resetSelectionDeviceListPanel = new javax.swing.JPanel();
        resetSelectionInfoPanel = new javax.swing.JPanel();
        resetSelectionHeaderLabel = new javax.swing.JLabel();
        resetSelectionCountLabel = new javax.swing.JLabel();
        resetStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        resetStorageDeviceList = new javax.swing.JList<>();
        resetExchangeDefinitionLabel = new javax.swing.JLabel();
        resetDataDefinitionLabel = new javax.swing.JLabel();
        resetOsDefinitionLabel = new javax.swing.JLabel();
        resetPrintingDetailsPanel = new javax.swing.JPanel();
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
        resetBackupDetailsPanel = new javax.swing.JPanel();
        resetBackupCheckBox = new javax.swing.JCheckBox();
        resetBackupSourcePanel = new javax.swing.JPanel();
        resetBackupSourceTextField = new javax.swing.JTextField();
        resetBackupDestinationPanel = new javax.swing.JPanel();
        resetBackupDestinationTextField = new javax.swing.JTextField();
        resetBackupDestinationButton = new javax.swing.JButton();
        resetBackupSubdirectoryPanel = new javax.swing.JPanel();
        resetMoveUpButton = new javax.swing.JButton();
        resetMoveDownButton = new javax.swing.JButton();
        resetBackupSubdirectoryScrollPane = new javax.swing.JScrollPane();
        resetBackupSubdirectoryTable = new javax.swing.JTable();
        resetBackupSpacerPanel = new javax.swing.JPanel();
        resetDeletePanel = new javax.swing.JPanel();
        resetExchangePartitionDetailsPanel = new javax.swing.JPanel();
        resetFormatExchangePartitionCheckBox = new javax.swing.JCheckBox();
        resetFormatExchangePartitionFileSystemLabel = new javax.swing.JLabel();
        resetFormatExchangePartitionFileSystemComboBox = new javax.swing.JComboBox<>();
        resetFormatExchangePartitionKeepLabelRadioButton = new javax.swing.JRadioButton();
        resetFormatExchangePartitionNewLabelRadioButton = new javax.swing.JRadioButton();
        resetFormatExchangePartitionNewLabelTextField = new javax.swing.JTextField();
        resetExchangespacer = new javax.swing.JPanel();
        resetDataPartitionDetailsPanel = new javax.swing.JPanel();
        deleteOnDataPartitionCheckBox = new javax.swing.JCheckBox();
        formatDataPartitionRadioButton = new javax.swing.JRadioButton();
        removeFilesRadioButton = new javax.swing.JRadioButton();
        systemFilesCheckBox = new javax.swing.JCheckBox();
        homeDirectoryCheckBox = new javax.swing.JCheckBox();
        resetRestorePanel = new javax.swing.JPanel();
        resetRestoreDataCheckBox = new javax.swing.JCheckBox();
        resetRestoreConfigurationPanel = new ch.fhnw.dlcopy.gui.swing.OverwriteConfigurationPanel();
        resetPanel = new javax.swing.JPanel();
        currentlyResettingDeviceLabel = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        resetCardPanel = new javax.swing.JPanel();
        resetProgressPanel = new javax.swing.JPanel();
        resetProgressBar = new javax.swing.JProgressBar();
        resetBackupPanel = new javax.swing.JPanel();
        resetBackupCopyLabel = new javax.swing.JLabel();
        resetBackupFileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
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
        dataPartitionRadioButton = new javax.swing.JRadioButton();
        systemMediumRadioButton = new javax.swing.JRadioButton();
        isoOptionsPanel = new javax.swing.JPanel();
        isoDataPartitionModeLabel = new javax.swing.JLabel();
        isoDataPartitionModeComboBox = new javax.swing.JComboBox<>();
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
        executionPanelSeparator = new javax.swing.JSeparator();
        prevNextButtonPanel = new javax.swing.JPanel();
        previousButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();

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
        installButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                installButtonFocusGained(evt);
            }
        });
        installButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                installButtonActionPerformed(evt);
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

        resetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/lernstick_reset.png"))); // NOI18N
        resetButton.setText(bundle.getString("DLCopySwingGUI.resetButton.text")); // NOI18N
        resetButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        resetButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        resetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
            }
        });
        resetButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                resetButtonFocusGained(evt);
            }
        });
        resetButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                resetButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 0);
        buttonGridPanel.add(resetButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        choicePanel.add(buttonGridPanel, gridBagConstraints);

        jumpLabel1.setText(bundle.getString("DLCopySwingGUI.jumpLabel1.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        choicePanel.add(jumpLabel1, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 5, 0, 0);
        choicePanel.add(jumpComboBox, gridBagConstraints);

        jumpLabel2.setText(bundle.getString("DLCopySwingGUI.jumpLabel2.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 5, 0, 0);
        choicePanel.add(jumpLabel2, gridBagConstraints);

        getContentPane().add(choicePanel, "choicePanel");

        executionPanel.setLayout(new java.awt.GridBagLayout());

        stepsPanel.setBackground(java.awt.Color.white);
        stepsPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        stepsPanel.setLayout(new java.awt.GridBagLayout());

        stepsLabel.setText(bundle.getString("DLCopySwingGUI.stepsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        stepsPanel.add(stepsLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        stepsPanel.add(jSeparator1, gridBagConstraints);

        infoStepLabel.setFont(infoStepLabel.getFont().deriveFont(infoStepLabel.getFont().getStyle() | java.awt.Font.BOLD));
        infoStepLabel.setText(bundle.getString("DLCopySwingGUI.infoStepLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(15, 10, 0, 10);
        stepsPanel.add(infoStepLabel, gridBagConstraints);

        selectionLabel.setFont(selectionLabel.getFont().deriveFont(selectionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        selectionLabel.setForeground(java.awt.Color.darkGray);
        selectionLabel.setText(bundle.getString("DLCopySwingGUI.selectionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        stepsPanel.add(selectionLabel, gridBagConstraints);

        executionLabel.setFont(executionLabel.getFont().deriveFont(executionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        executionLabel.setForeground(java.awt.Color.darkGray);
        executionLabel.setText(bundle.getString("Installation_Label")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 10);
        stepsPanel.add(executionLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        executionPanel.add(stepsPanel, gridBagConstraints);

        cardPanel.setName("cardPanel"); // NOI18N
        cardPanel.setLayout(new java.awt.CardLayout());

        installInfoPanel.setLayout(new java.awt.GridBagLayout());

        infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dvd2usb.png"))); // NOI18N
        infoLabel.setText(bundle.getString("DLCopySwingGUI.infoLabel.text")); // NOI18N
        infoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        infoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        installInfoPanel.add(infoLabel, gridBagConstraints);

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

        isoSourceTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        installSourcePanel.add(isoSourceTextField, gridBagConstraints);

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
        installSourcePanel.add(isoSourceFileChooserButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        installSelectionPanel.add(installSourcePanel, gridBagConstraints);

        installTargetCardPanel.setName("installTargetCardPanel"); // NOI18N
        installTargetCardPanel.setLayout(new java.awt.CardLayout());

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

        installSelectionCardPanel.setName("installSelectionCardPanel"); // NOI18N
        installSelectionCardPanel.setLayout(new java.awt.CardLayout());

        installBasicsPanel.setLayout(new java.awt.GridBagLayout());

        installSelectionCountLabel.setText(bundle.getString("Selection_Count")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        installBasicsPanel.add(installSelectionCountLabel, gridBagConstraints);

        installStorageDeviceList.setName("installStorageDeviceList"); // NOI18N
        installStorageDeviceList.setVisibleRowCount(3);
        installStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                installStorageDeviceListValueChanged(evt);
            }
        });
        installStorageDeviceListScrollPane.setViewportView(installStorageDeviceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        installBasicsPanel.add(installStorageDeviceListScrollPane, gridBagConstraints);

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("ExchangePartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        installBasicsPanel.add(exchangeDefinitionLabel, gridBagConstraints);

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DataPartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        installBasicsPanel.add(dataDefinitionLabel, gridBagConstraints);

        bootDefinitionLabel.setFont(bootDefinitionLabel.getFont().deriveFont(bootDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, bootDefinitionLabel.getFont().getSize()-1));
        bootDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/dark_blue_box.png"))); // NOI18N
        bootDefinitionLabel.setText(bundle.getString("Boot_Definition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        installBasicsPanel.add(bootDefinitionLabel, gridBagConstraints);

        systemDefinitionLabel.setFont(systemDefinitionLabel.getFont().deriveFont(systemDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, systemDefinitionLabel.getFont().getSize()-1));
        systemDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        systemDefinitionLabel.setText(bundle.getString("System_Definition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 0);
        installBasicsPanel.add(systemDefinitionLabel, gridBagConstraints);

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
        installBasicsPanel.add(basicExchangePartitionPanel, gridBagConstraints);

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
        installBasicsPanel.add(basicDataPartitionPanel, gridBagConstraints);

        installSelectionTabbedPane.addTab(bundle.getString("Selection"), installBasicsPanel); // NOI18N

        installDetailsPanel.setLayout(new java.awt.GridBagLayout());

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
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(17, 3, 0, 0);
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

        autoNumberPatternTextField.setColumns(11);
        autoNumberPatternTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
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
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 0);
        exchangePartitionDetailsPanel.add(autoNumberPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        installDetailsPanel.add(exchangePartitionDetailsPanel, gridBagConstraints);

        dataPartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Data_Partition"))); // NOI18N
        dataPartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        dataPartitionFileSystemLabel.setText(bundle.getString("DLCopySwingGUI.dataPartitionFileSystemLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        dataPartitionDetailsPanel.add(dataPartitionFileSystemLabel, gridBagConstraints);

        dataPartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "ext2", "ext3", "ext4" }));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        dataPartitionDetailsPanel.add(dataPartitionFileSystemComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        installDetailsPanel.add(dataPartitionDetailsPanel, gridBagConstraints);

        checkCopiesCheckBox.setText(bundle.getString("DLCopySwingGUI.checkCopiesCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        installDetailsPanel.add(checkCopiesCheckBox, gridBagConstraints);

        installSelectionTabbedPane.addTab(bundle.getString("Details"), installDetailsPanel); // NOI18N

        installTransferPanel.setLayout(new java.awt.GridBagLayout());

        transferLabel.setText(bundle.getString("DLCopySwingGUI.transferLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        installTransferPanel.add(transferLabel, gridBagConstraints);

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

        transferUserSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.transferUserSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        transferCheckboxPanel.add(transferUserSettingsCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        installTransferPanel.add(transferCheckboxPanel, gridBagConstraints);

        installTransferStorageDeviceList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        installTransferScrollPane.setViewportView(installTransferStorageDeviceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        installTransferPanel.add(installTransferScrollPane, gridBagConstraints);

        installSelectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.installTransferPanel.TabConstraints.tabTitle"), installTransferPanel); // NOI18N

        installSelectionCardPanel.add(installSelectionTabbedPane, "installSelectionTabbedPane");

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

        installTargetCardPanel.add(installTargetPanel, "installTargetPanel");

        installNoSourcePanel.setLayout(new java.awt.GridBagLayout());

        installNoSouceLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        installNoSouceLabel.setText(bundle.getString("DLCopySwingGUI.installNoSouceLabel.text")); // NOI18N
        installNoSouceLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        installNoSouceLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        installNoSourcePanel.add(installNoSouceLabel, new java.awt.GridBagConstraints());

        installTargetCardPanel.add(installNoSourcePanel, "installNoSourcePanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        installSelectionPanel.add(installTargetCardPanel, gridBagConstraints);

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

        installCardPanel.setName("installCardPanel"); // NOI18N
        installCardPanel.setLayout(new java.awt.CardLayout());

        installIndeterminateProgressPanel.setLayout(new java.awt.GridBagLayout());

        installIndeterminateProgressBar.setIndeterminate(true);
        installIndeterminateProgressBar.setPreferredSize(new java.awt.Dimension(300, 25));
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

        donePanel.setLayout(new java.awt.GridBagLayout());

        doneLabel.setFont(doneLabel.getFont().deriveFont(doneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        doneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        doneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbpendrive_unmount_tux.png"))); // NOI18N
        doneLabel.setText(bundle.getString("Installation_Done_Message_From_Removable_Boot_Device")); // NOI18N
        doneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        doneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        donePanel.add(doneLabel, new java.awt.GridBagConstraints());

        cardPanel.add(donePanel, "donePanel");

        upgradeInfoPanel.setLayout(new java.awt.GridBagLayout());

        upgradeInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbupgrade.png"))); // NOI18N
        upgradeInfoLabel.setText(bundle.getString("DLCopySwingGUI.upgradeInfoLabel.text")); // NOI18N
        upgradeInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        upgradeInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        upgradeInfoPanel.add(upgradeInfoLabel, gridBagConstraints);

        cardPanel.add(upgradeInfoPanel, "upgradeInfoPanel");

        upgradeSelectionTabbedPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                upgradeSelectionTabbedPaneComponentShown(evt);
            }
        });

        upgradeSelectionPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSelectionModeButtonGroup.add(upgradeListModeRadioButton);
        upgradeListModeRadioButton.setSelected(true);
        upgradeListModeRadioButton.setText(bundle.getString("Select_Storage_Media_From_List")); // NOI18N
        upgradeListModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeListModeRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        upgradeSelectionPanel.add(upgradeListModeRadioButton, gridBagConstraints);

        upgradeShowHarddisksCheckBox.setFont(upgradeShowHarddisksCheckBox.getFont().deriveFont(upgradeShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeShowHarddisksCheckBox.getFont().getSize()-1));
        upgradeShowHarddisksCheckBox.setText(bundle.getString("DLCopySwingGUI.upgradeShowHarddisksCheckBox.text")); // NOI18N
        upgradeShowHarddisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                upgradeShowHarddisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 30, 0, 0);
        upgradeSelectionPanel.add(upgradeShowHarddisksCheckBox, gridBagConstraints);

        upgradeSelectionModeButtonGroup.add(upgradeAutomaticRadioButton);
        upgradeAutomaticRadioButton.setText(bundle.getString("DLCopySwingGUI.upgradeAutomaticRadioButton.text")); // NOI18N
        upgradeAutomaticRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeAutomaticRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        upgradeSelectionPanel.add(upgradeAutomaticRadioButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        upgradeSelectionPanel.add(upgradeSelectionSeparator, gridBagConstraints);

        upgradeSelectionCardPanel.setName("upgradeSelectionCardPanel"); // NOI18N
        upgradeSelectionCardPanel.setLayout(new java.awt.CardLayout());

        upgradeSelectionDeviceListPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSelectionInfoPanel.setLayout(new java.awt.GridBagLayout());

        upgradeSelectionHeaderLabel.setFont(upgradeSelectionHeaderLabel.getFont().deriveFont(upgradeSelectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        upgradeSelectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        upgradeSelectionHeaderLabel.setText(bundle.getString("Select_Upgrade_Target_Storage_Media")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        upgradeSelectionInfoPanel.add(upgradeSelectionHeaderLabel, gridBagConstraints);

        upgradeSelectionCountLabel.setText(bundle.getString("Selection_Count")); // NOI18N
        upgradeSelectionInfoPanel.add(upgradeSelectionCountLabel, new java.awt.GridBagConstraints());

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeSelectionInfoPanel, gridBagConstraints);

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
        upgradeExchangeDefinitionLabel.setText(bundle.getString("ExchangePartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeSelectionDeviceListPanel.add(upgradeExchangeDefinitionLabel, gridBagConstraints);

        upgradeDataDefinitionLabel.setFont(upgradeDataDefinitionLabel.getFont().deriveFont(upgradeDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeDataDefinitionLabel.getFont().getSize()-1));
        upgradeDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        upgradeDataDefinitionLabel.setText(bundle.getString("DataPartitionDefinition")); // NOI18N
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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        upgradeSelectionPanel.add(upgradeSelectionCardPanel, gridBagConstraints);

        upgradeSelectionTabbedPane.addTab(bundle.getString("Selection"), upgradeSelectionPanel); // NOI18N

        upgradeOptionsPanel.setLayout(new java.awt.GridBagLayout());

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
        upgradeOptionsPanel.add(upgradeSystemPartitionCheckBox, gridBagConstraints);

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
        upgradeOptionsPanel.add(resetDataPartitionCheckBox, gridBagConstraints);

        reactivateWelcomeCheckBox.setSelected(true);
        reactivateWelcomeCheckBox.setText(bundle.getString("DLCopySwingGUI.reactivateWelcomeCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeOptionsPanel.add(reactivateWelcomeCheckBox, gridBagConstraints);

        keepPrinterSettingsCheckBox.setSelected(true);
        keepPrinterSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepPrinterSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        upgradeOptionsPanel.add(keepPrinterSettingsCheckBox, gridBagConstraints);

        keepNetworkSettingsCheckBox.setSelected(true);
        keepNetworkSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepNetworkSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        upgradeOptionsPanel.add(keepNetworkSettingsCheckBox, gridBagConstraints);

        keepFirewallSettingsCheckBox.setSelected(true);
        keepFirewallSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepFirewallSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        upgradeOptionsPanel.add(keepFirewallSettingsCheckBox, gridBagConstraints);

        keepUserSettingsCheckBox.setSelected(true);
        keepUserSettingsCheckBox.setText(bundle.getString("DLCopySwingGUI.keepUserSettingsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        upgradeOptionsPanel.add(keepUserSettingsCheckBox, gridBagConstraints);

        removeHiddenFilesCheckBox.setText(bundle.getString("DLCopySwingGUI.removeHiddenFilesCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeOptionsPanel.add(removeHiddenFilesCheckBox, gridBagConstraints);

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
        upgradeOptionsPanel.add(automaticBackupCheckBox, gridBagConstraints);

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

        automaticBackupButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-open-folder.png"))); // NOI18N
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
        upgradeOptionsPanel.add(backupDestinationPanel, gridBagConstraints);

        automaticBackupRemoveCheckBox.setText(bundle.getString("DLCopySwingGUI.automaticBackupRemoveCheckBox.text")); // NOI18N
        automaticBackupRemoveCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 28, 0, 10);
        upgradeOptionsPanel.add(automaticBackupRemoveCheckBox, gridBagConstraints);

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
        upgradeOptionsPanel.add(repartitionExchangeOptionsPanel, gridBagConstraints);

        upgradeDetailsTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeOptionsPanel.TabConstraints.tabTitle"), upgradeOptionsPanel); // NOI18N

        upgradeOverwritePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        upgradeOverwritePanel.setLayout(new java.awt.GridBagLayout());

        upgradeMoveUpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-up.png"))); // NOI18N
        upgradeMoveUpButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeMoveUpButton.toolTipText")); // NOI18N
        upgradeMoveUpButton.setEnabled(false);
        upgradeMoveUpButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeMoveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeMoveUpButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        upgradeOverwritePanel.add(upgradeMoveUpButton, gridBagConstraints);

        upgradeMoveDownButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-down.png"))); // NOI18N
        upgradeMoveDownButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeMoveDownButton.toolTipText")); // NOI18N
        upgradeMoveDownButton.setEnabled(false);
        upgradeMoveDownButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeMoveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeMoveDownButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        upgradeOverwritePanel.add(upgradeMoveDownButton, gridBagConstraints);

        sortAscendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/view-sort-ascending.png"))); // NOI18N
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
        upgradeOverwritePanel.add(sortAscendingButton, gridBagConstraints);

        sortDescendingButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/view-sort-descending.png"))); // NOI18N
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
        upgradeOverwritePanel.add(sortDescendingButton, gridBagConstraints);

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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 0);
        upgradeOverwritePanel.add(upgradeOverwriteScrollPane, gridBagConstraints);

        upgradeOverwriteAddButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/list-add.png"))); // NOI18N
        upgradeOverwriteAddButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteAddButton.toolTipText")); // NOI18N
        upgradeOverwriteAddButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteAddButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        upgradeOverwritePanel.add(upgradeOverwriteAddButton, gridBagConstraints);

        upgradeOverwriteEditButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-edit.png"))); // NOI18N
        upgradeOverwriteEditButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteEditButton.toolTipText")); // NOI18N
        upgradeOverwriteEditButton.setEnabled(false);
        upgradeOverwriteEditButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteEditButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        upgradeOverwritePanel.add(upgradeOverwriteEditButton, gridBagConstraints);

        upgradeOverwriteRemoveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/list-remove.png"))); // NOI18N
        upgradeOverwriteRemoveButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteRemoveButton.toolTipText")); // NOI18N
        upgradeOverwriteRemoveButton.setEnabled(false);
        upgradeOverwriteRemoveButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteRemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteRemoveButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        upgradeOverwritePanel.add(upgradeOverwriteRemoveButton, gridBagConstraints);

        upgradeOverwriteExportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-export.png"))); // NOI18N
        upgradeOverwriteExportButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteExportButton.toolTipText")); // NOI18N
        upgradeOverwriteExportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteExportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteExportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        upgradeOverwritePanel.add(upgradeOverwriteExportButton, gridBagConstraints);

        upgradeOverwriteImportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/document-import.png"))); // NOI18N
        upgradeOverwriteImportButton.setToolTipText(bundle.getString("DLCopySwingGUI.upgradeOverwriteImportButton.toolTipText")); // NOI18N
        upgradeOverwriteImportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteImportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteImportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        upgradeOverwritePanel.add(upgradeOverwriteImportButton, gridBagConstraints);

        upgradeDetailsTabbedPane.addTab(bundle.getString("DLCopySwingGUI.upgradeOverwritePanel.TabConstraints.tabTitle"), upgradeOverwritePanel); // NOI18N

        upgradeSelectionTabbedPane.addTab(bundle.getString("Details"), upgradeDetailsTabbedPane); // NOI18N

        cardPanel.add(upgradeSelectionTabbedPane, "upgradeSelectionTabbedPane");

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

        upgradeCardPanel.setName("upgradeCardPanel"); // NOI18N
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

        resetInfoPanel.setLayout(new java.awt.GridBagLayout());

        resetInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/lernstick_reset.png"))); // NOI18N
        resetInfoLabel.setText(bundle.getString("DLCopySwingGUI.resetInfoLabel.text")); // NOI18N
        resetInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        resetInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        resetInfoPanel.add(resetInfoLabel, gridBagConstraints);

        cardPanel.add(resetInfoPanel, "resetInfoPanel");

        resetSelectionTabbedPane.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentShown(java.awt.event.ComponentEvent evt) {
                resetSelectionTabbedPaneComponentShown(evt);
            }
        });

        resetSelectionPanel.setLayout(new java.awt.GridBagLayout());

        resetSelectionModeButtonGroup.add(resetListModeRadioButton);
        resetListModeRadioButton.setSelected(true);
        resetListModeRadioButton.setText(bundle.getString("Select_Storage_Media_From_List")); // NOI18N
        resetListModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetListModeRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        resetSelectionPanel.add(resetListModeRadioButton, gridBagConstraints);

        resetShowHarddisksCheckBox.setFont(resetShowHarddisksCheckBox.getFont().deriveFont(resetShowHarddisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, resetShowHarddisksCheckBox.getFont().getSize()-1));
        resetShowHarddisksCheckBox.setText(bundle.getString("DLCopySwingGUI.resetShowHarddisksCheckBox.text")); // NOI18N
        resetShowHarddisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                resetShowHarddisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 30, 0, 0);
        resetSelectionPanel.add(resetShowHarddisksCheckBox, gridBagConstraints);

        resetSelectionModeButtonGroup.add(resetAutomaticModeRadioButton);
        resetAutomaticModeRadioButton.setText(bundle.getString("DLCopySwingGUI.resetAutomaticModeRadioButton.text")); // NOI18N
        resetAutomaticModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetAutomaticModeRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        resetSelectionPanel.add(resetAutomaticModeRadioButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        resetSelectionPanel.add(jSeparator2, gridBagConstraints);

        resetSelectionCardPanel.setName("resetSelectionCardPanel"); // NOI18N
        resetSelectionCardPanel.setLayout(new java.awt.CardLayout());

        resetNoMediaPanel.setLayout(new java.awt.GridBagLayout());

        resetNoMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/messagebox_info.png"))); // NOI18N
        resetNoMediaLabel.setText(bundle.getString("DLCopySwingGUI.resetNoMediaLabel.text")); // NOI18N
        resetNoMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        resetNoMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        resetNoMediaPanel.add(resetNoMediaLabel, new java.awt.GridBagConstraints());

        resetSelectionCardPanel.add(resetNoMediaPanel, "resetNoMediaPanel");

        resetSelectionDeviceListPanel.setLayout(new java.awt.GridBagLayout());

        resetSelectionInfoPanel.setLayout(new java.awt.GridBagLayout());

        resetSelectionHeaderLabel.setFont(resetSelectionHeaderLabel.getFont().deriveFont(resetSelectionHeaderLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        resetSelectionHeaderLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        resetSelectionHeaderLabel.setText(bundle.getString("DLCopySwingGUI.resetSelectionHeaderLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        resetSelectionInfoPanel.add(resetSelectionHeaderLabel, gridBagConstraints);

        resetSelectionCountLabel.setText(bundle.getString("DLCopySwingGUI.resetSelectionCountLabel.text")); // NOI18N
        resetSelectionInfoPanel.add(resetSelectionCountLabel, new java.awt.GridBagConstraints());

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        resetSelectionDeviceListPanel.add(resetSelectionInfoPanel, gridBagConstraints);

        resetStorageDeviceList.setName("storageDeviceList"); // NOI18N
        resetStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                resetStorageDeviceListValueChanged(evt);
            }
        });
        resetStorageDeviceListScrollPane.setViewportView(resetStorageDeviceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        resetSelectionDeviceListPanel.add(resetStorageDeviceListScrollPane, gridBagConstraints);

        resetExchangeDefinitionLabel.setFont(resetExchangeDefinitionLabel.getFont().deriveFont(resetExchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, resetExchangeDefinitionLabel.getFont().getSize()-1));
        resetExchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/yellow_box.png"))); // NOI18N
        resetExchangeDefinitionLabel.setText(bundle.getString("ExchangePartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        resetSelectionDeviceListPanel.add(resetExchangeDefinitionLabel, gridBagConstraints);

        resetDataDefinitionLabel.setFont(resetDataDefinitionLabel.getFont().deriveFont(resetDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, resetDataDefinitionLabel.getFont().getSize()-1));
        resetDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/green_box.png"))); // NOI18N
        resetDataDefinitionLabel.setText(bundle.getString("DataPartitionDefinition")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        resetSelectionDeviceListPanel.add(resetDataDefinitionLabel, gridBagConstraints);

        resetOsDefinitionLabel.setFont(resetOsDefinitionLabel.getFont().deriveFont(resetOsDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, resetOsDefinitionLabel.getFont().getSize()-1));
        resetOsDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/blue_box.png"))); // NOI18N
        resetOsDefinitionLabel.setText(bundle.getString("DLCopySwingGUI.resetOsDefinitionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 10, 10);
        resetSelectionDeviceListPanel.add(resetOsDefinitionLabel, gridBagConstraints);

        resetSelectionCardPanel.add(resetSelectionDeviceListPanel, "resetSelectionDeviceListPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resetSelectionPanel.add(resetSelectionCardPanel, gridBagConstraints);

        resetSelectionTabbedPane.addTab(bundle.getString("Selection"), resetSelectionPanel); // NOI18N

        resetPrintingDetailsPanel.setLayout(new java.awt.GridBagLayout());

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
        resetPrintingDetailsPanel.add(printDocumentsCheckBox, gridBagConstraints);

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
        resetPrintingDetailsPanel.add(printingDirectoryPanel, gridBagConstraints);

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
        resetPrintingDetailsPanel.add(printFileFormatsPanel, gridBagConstraints);

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
        resetPrintingDetailsPanel.add(rightPrintingPanel, gridBagConstraints);

        resetSelectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.resetPrintingDetailsPanel.TabConstraints.tabTitle"), resetPrintingDetailsPanel); // NOI18N

        resetBackupDetailsPanel.setLayout(new java.awt.GridBagLayout());

        resetBackupCheckBox.setText(bundle.getString("DLCopySwingGUI.resetBackupCheckBox.text")); // NOI18N
        resetBackupCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                resetBackupCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        resetBackupDetailsPanel.add(resetBackupCheckBox, gridBagConstraints);

        resetBackupSourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.resetBackupSourcePanel.border.title"))); // NOI18N
        resetBackupSourcePanel.setLayout(new java.awt.GridBagLayout());

        resetBackupSourceTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        resetBackupSourcePanel.add(resetBackupSourceTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 5);
        resetBackupDetailsPanel.add(resetBackupSourcePanel, gridBagConstraints);

        resetBackupDestinationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.resetBackupDestinationPanel.border.title"))); // NOI18N
        resetBackupDestinationPanel.setLayout(new java.awt.GridBagLayout());

        resetBackupDestinationTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        resetBackupDestinationPanel.add(resetBackupDestinationTextField, gridBagConstraints);

        resetBackupDestinationButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/fileopen.png"))); // NOI18N
        resetBackupDestinationButton.setEnabled(false);
        resetBackupDestinationButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        resetBackupDestinationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetBackupDestinationButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        resetBackupDestinationPanel.add(resetBackupDestinationButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 5);
        resetBackupDetailsPanel.add(resetBackupDestinationPanel, gridBagConstraints);

        resetBackupSubdirectoryPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.resetBackupSubdirectoryPanel.border.title"))); // NOI18N
        resetBackupSubdirectoryPanel.setLayout(new java.awt.GridBagLayout());

        resetMoveUpButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-up.png"))); // NOI18N
        resetMoveUpButton.setToolTipText(bundle.getString("DLCopySwingGUI.resetMoveUpButton.toolTipText")); // NOI18N
        resetMoveUpButton.setEnabled(false);
        resetMoveUpButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        resetMoveUpButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMoveUpButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        resetBackupSubdirectoryPanel.add(resetMoveUpButton, gridBagConstraints);

        resetMoveDownButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/16x16/arrow-down.png"))); // NOI18N
        resetMoveDownButton.setToolTipText(bundle.getString("DLCopySwingGUI.resetMoveDownButton.toolTipText")); // NOI18N
        resetMoveDownButton.setEnabled(false);
        resetMoveDownButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        resetMoveDownButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMoveDownButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 5, 0);
        resetBackupSubdirectoryPanel.add(resetMoveDownButton, gridBagConstraints);

        resetBackupSubdirectoryTable.setEnabled(false);
        resetBackupSubdirectoryScrollPane.setViewportView(resetBackupSubdirectoryTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resetBackupSubdirectoryPanel.add(resetBackupSubdirectoryScrollPane, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        resetBackupDetailsPanel.add(resetBackupSubdirectoryPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resetBackupDetailsPanel.add(resetBackupSpacerPanel, gridBagConstraints);

        resetSelectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.resetBackupDetailsPanel.TabConstraints.tabTitle"), resetBackupDetailsPanel); // NOI18N

        resetDeletePanel.setLayout(new java.awt.GridBagLayout());

        resetExchangePartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), bundle.getString("Exchange_Partition"))); // NOI18N
        resetExchangePartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        resetFormatExchangePartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionCheckBox.text")); // NOI18N
        resetFormatExchangePartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                resetFormatExchangePartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 10, 0);
        resetExchangePartitionDetailsPanel.add(resetFormatExchangePartitionCheckBox, gridBagConstraints);

        resetFormatExchangePartitionFileSystemLabel.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionFileSystemLabel.text")); // NOI18N
        resetFormatExchangePartitionFileSystemLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 25, 10, 0);
        resetExchangePartitionDetailsPanel.add(resetFormatExchangePartitionFileSystemLabel, gridBagConstraints);

        resetFormatExchangePartitionFileSystemComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        resetFormatExchangePartitionFileSystemComboBox.setToolTipText(bundle.getString("ExchangePartitionFileSystemComboBoxToolTipText")); // NOI18N
        resetFormatExchangePartitionFileSystemComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 10, 10);
        resetExchangePartitionDetailsPanel.add(resetFormatExchangePartitionFileSystemComboBox, gridBagConstraints);

        resetExchangePartitionButtonGroup.add(resetFormatExchangePartitionKeepLabelRadioButton);
        resetFormatExchangePartitionKeepLabelRadioButton.setSelected(true);
        resetFormatExchangePartitionKeepLabelRadioButton.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionKeepLabelRadioButton.text")); // NOI18N
        resetFormatExchangePartitionKeepLabelRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 0, 0);
        resetExchangePartitionDetailsPanel.add(resetFormatExchangePartitionKeepLabelRadioButton, gridBagConstraints);

        resetExchangePartitionButtonGroup.add(resetFormatExchangePartitionNewLabelRadioButton);
        resetFormatExchangePartitionNewLabelRadioButton.setText(bundle.getString("DLCopySwingGUI.resetFormatExchangePartitionNewLabelRadioButton.text")); // NOI18N
        resetFormatExchangePartitionNewLabelRadioButton.setEnabled(false);
        resetFormatExchangePartitionNewLabelRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                resetFormatExchangePartitionNewLabelRadioButtonItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 22, 5, 0);
        resetExchangePartitionDetailsPanel.add(resetFormatExchangePartitionNewLabelRadioButton, gridBagConstraints);

        resetFormatExchangePartitionNewLabelTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 10);
        resetExchangePartitionDetailsPanel.add(resetFormatExchangePartitionNewLabelTextField, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resetExchangePartitionDetailsPanel.add(resetExchangespacer, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        resetDeletePanel.add(resetExchangePartitionDetailsPanel, gridBagConstraints);

        resetDataPartitionDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED), bundle.getString("Data_Partition"))); // NOI18N
        resetDataPartitionDetailsPanel.setLayout(new java.awt.GridBagLayout());

        deleteOnDataPartitionCheckBox.setText(bundle.getString("DLCopySwingGUI.deleteOnDataPartitionCheckBox.text")); // NOI18N
        deleteOnDataPartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                deleteOnDataPartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        resetDataPartitionDetailsPanel.add(deleteOnDataPartitionCheckBox, gridBagConstraints);

        resetDataPartitionButtonGroup.add(formatDataPartitionRadioButton);
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
        resetDataPartitionDetailsPanel.add(formatDataPartitionRadioButton, gridBagConstraints);

        resetDataPartitionButtonGroup.add(removeFilesRadioButton);
        removeFilesRadioButton.setSelected(true);
        removeFilesRadioButton.setText(bundle.getString("DLCopySwingGUI.removeFilesRadioButton.text")); // NOI18N
        removeFilesRadioButton.setEnabled(false);
        removeFilesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeFilesRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 20, 0, 10);
        resetDataPartitionDetailsPanel.add(removeFilesRadioButton, gridBagConstraints);

        systemFilesCheckBox.setText(bundle.getString("DLCopySwingGUI.systemFilesCheckBox.text")); // NOI18N
        systemFilesCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 40, 0, 0);
        resetDataPartitionDetailsPanel.add(systemFilesCheckBox, gridBagConstraints);

        homeDirectoryCheckBox.setText(bundle.getString("DLCopySwingGUI.homeDirectoryCheckBox.text")); // NOI18N
        homeDirectoryCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 40, 10, 10);
        resetDataPartitionDetailsPanel.add(homeDirectoryCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        resetDeletePanel.add(resetDataPartitionDetailsPanel, gridBagConstraints);

        resetSelectionTabbedPane.addTab(bundle.getString("Delete_Data"), resetDeletePanel); // NOI18N

        resetRestorePanel.setLayout(new java.awt.GridBagLayout());

        resetRestoreDataCheckBox.setText(bundle.getString("DLCopySwingGUI.resetRestoreDataCheckBox.text")); // NOI18N
        resetRestoreDataCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                resetRestoreDataCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        resetRestorePanel.add(resetRestoreDataCheckBox, gridBagConstraints);

        resetRestoreConfigurationPanel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        resetRestorePanel.add(resetRestoreConfigurationPanel, gridBagConstraints);

        resetSelectionTabbedPane.addTab(bundle.getString("DLCopySwingGUI.resetRestorePanel.TabConstraints.tabTitle"), resetRestorePanel); // NOI18N

        cardPanel.add(resetSelectionTabbedPane, "resetSelectionTabbedPane");

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

        resetProgressPanel.setLayout(new java.awt.GridBagLayout());

        resetProgressBar.setIndeterminate(true);
        resetProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        resetProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weighty = 1.0;
        resetProgressPanel.add(resetProgressBar, gridBagConstraints);

        resetCardPanel.add(resetProgressPanel, "resetProgressPanel");

        resetBackupPanel.setLayout(new java.awt.GridBagLayout());

        resetBackupCopyLabel.setText(bundle.getString("DLCopySwingGUI.resetBackupCopyLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        resetBackupPanel.add(resetBackupCopyLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        resetBackupPanel.add(resetBackupFileCopierPanel, gridBagConstraints);

        resetCardPanel.add(resetBackupPanel, "resetBackupPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        resetPanel.add(resetCardPanel, gridBagConstraints);

        cardPanel.add(resetPanel, "resetPanel");

        toISOInfoPanel.setLayout(new java.awt.GridBagLayout());

        toISOInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        toISOInfoLabel.setText(bundle.getString("DLCopySwingGUI.toISOInfoLabel.text")); // NOI18N
        toISOInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        toISOInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        toISOInfoPanel.add(toISOInfoLabel, gridBagConstraints);

        cardPanel.add(toISOInfoPanel, "toISOInfoPanel");

        toISOSelectionPanel.setLayout(new java.awt.GridBagLayout());

        tmpDriveInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tmpDriveInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/file_temporary.png"))); // NOI18N
        tmpDriveInfoLabel.setText(bundle.getString("DLCopySwingGUI.tmpDriveInfoLabel.text")); // NOI18N
        tmpDriveInfoLabel.setIconTextGap(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        toISOSelectionPanel.add(tmpDriveInfoLabel, gridBagConstraints);

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

        isoButtonGroup.add(dataPartitionRadioButton);
        dataPartitionRadioButton.setText(bundle.getString("DLCopySwingGUI.dataPartitionRadioButton.text")); // NOI18N
        dataPartitionRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataPartitionRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        radioButtonPanel.add(dataPartitionRadioButton, gridBagConstraints);

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

        isoOptionsCardPanel.setName("isoOptionsCardPanel"); // NOI18N
        isoOptionsCardPanel.setLayout(new java.awt.CardLayout());

        systemMediumPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.systemMediumPanel.border.title"))); // NOI18N
        systemMediumPanel.setLayout(new java.awt.GridBagLayout());

        showNotUsedDialogCheckBox.setText(bundle.getString("DLCopySwingGUI.showNotUsedDialogCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        systemMediumPanel.add(showNotUsedDialogCheckBox, gridBagConstraints);

        autoStartInstallerCheckBox.setText(bundle.getString("DLCopySwingGUI.autoStartInstallerCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        systemMediumPanel.add(autoStartInstallerCheckBox, gridBagConstraints);

        isoOptionsCardPanel.add(systemMediumPanel, "systemMediumPanel");

        bootMediumPanel.setLayout(new java.awt.GridBagLayout());
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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(30, 10, 10, 10);
        toISOSelectionPanel.add(toIsoGridBagPanel, gridBagConstraints);

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

        toISODonePanel.setLayout(new java.awt.GridBagLayout());

        isoDoneLabel.setFont(isoDoneLabel.getFont().deriveFont(isoDoneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        isoDoneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        isoDoneLabel.setText(bundle.getString("DLCopySwingGUI.isoDoneLabel.text")); // NOI18N
        isoDoneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toISODonePanel.add(isoDoneLabel, new java.awt.GridBagConstraints());

        cardPanel.add(toISODonePanel, "toISODonePanel");

        resultsPanel.setLayout(new java.awt.GridBagLayout());

        resultsInfoLabel.setText(bundle.getString("Installation_Done_Message_From_Removable_Boot_Device")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 15, 0, 10);
        resultsPanel.add(resultsInfoLabel, gridBagConstraints);

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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
        resultsPanel.add(resultsTitledPanel, gridBagConstraints);

        cardPanel.add(resultsPanel, "resultsPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 10);
        executionPanel.add(cardPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        executionPanel.add(executionPanelSeparator, gridBagConstraints);

        prevNextButtonPanel.setLayout(new java.awt.GridBagLayout());

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
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        prevNextButtonPanel.add(previousButton, gridBagConstraints);

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/next.png"))); // NOI18N
        nextButton.setText(bundle.getString("DLCopySwingGUI.nextButton.text")); // NOI18N
        nextButton.setName("nextButton"); // NOI18N
        nextButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                nextButtonFocusGained(evt);
            }
        });
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });
        nextButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                nextButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        prevNextButtonPanel.add(nextButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 10, 10);
        executionPanel.add(prevNextButtonPanel, gridBagConstraints);

        getContentPane().add(executionPanel, "executionPanel");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        switch (state) {

            case INSTALL_INFORMATION:
                switchToInstallSelection();
                break;

            case ISO_INFORMATION:
                switchToISOSelection();
                break;

            case UPGRADE_INFORMATION:
                switchToUpgradeSelection();
                break;

            case RESET_INFORMATION:
                switchToResetSelection();
                break;

            case INSTALL_SELECTION:
                try {
                checkAndInstallSelection(true);
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

            if (dataPartitionRadioButton.isSelected()) {
                new SquashFSCreator(this, runningSystemSource,
                        tmpDirTextField.getText(),
                        showNotUsedDialogCheckBox.isSelected(),
                        autoStartInstallerCheckBox.isSelected()).execute();
            } else {
                DataPartitionMode dataPartitionMode = getDataPartitionMode(
                        isoDataPartitionModeComboBox);
                new IsoCreator(this, runningSystemSource,
                        bootMediumRadioButton.isSelected(),
                        tmpDirTextField.getText(), dataPartitionMode,
                        showNotUsedDialogCheckBox.isSelected(),
                        autoStartInstallerCheckBox.isSelected(),
                        isoLabelTextField.getText()).execute();
            }

            break;

            case UPGRADE_SELECTION:
                upgradeSelectedStorageDevices();
                break;

            case RESET_SELECTION:
                reset();
                break;

            case INSTALLATION:
            case UPGRADE:
            case ISO_INSTALLATION:
            case RESET:
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
            case RESET_INFORMATION:
            case ISO_INFORMATION:
                getRootPane().setDefaultButton(installButton);
                installButton.requestFocusInWindow();
                globalShow("choicePanel");
                break;

            case INSTALL_SELECTION:
                switchToInstallInformation();
                break;

            case UPGRADE_SELECTION:
                switchToUpgradeInformation();
                break;

            case ISO_SELECTION:
                switchToISOInformation();
                break;

            case RESET_SELECTION:
                switchToResetInformation();
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

            case RESET:
                switchToResetSelection();
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

        if (!listSelectionTriggeredSliderChange) {
            explicitExchangeSize = exchangePartitionSizeSlider.getValue();
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
        switchToInstallInformation();
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
                installShowHarddisksCheckBox.isSelected(),
                runningSystemSource.getDeviceName()).execute();
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
                resetButton.requestFocusInWindow();
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
                resetButton.requestFocusInWindow();
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

private void upgradeShowHarddisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeShowHarddisksCheckBoxItemStateChanged
    new UpgradeStorageDeviceListUpdater(runningSystemSource, this,
            upgradeStorageDeviceList, upgradeStorageDeviceListModel,
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
        selectBackupDestination(automaticBackupTextField);
    }//GEN-LAST:event_automaticBackupButtonActionPerformed

    private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed
        globalShow("executionPanel");
        switchToResetInformation();
    }//GEN-LAST:event_resetButtonActionPerformed

    private void resetButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_resetButtonFocusGained
        getRootPane().setDefaultButton(resetButton);
    }//GEN-LAST:event_resetButtonFocusGained

    private void resetButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_resetButtonKeyPressed
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
                upgradeButton.requestFocusInWindow();
                break;
            case KeyEvent.VK_LEFT:
                toISOButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_resetButtonKeyPressed

    private void resetStorageDeviceListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_resetStorageDeviceListValueChanged
        updateResetSelectionCountAndNextButton();
    }//GEN-LAST:event_resetStorageDeviceListValueChanged

    private void formatDataPartitionRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_formatDataPartitionRadioButtonActionPerformed
        updateResetDataPartitionButtonState();
    }//GEN-LAST:event_formatDataPartitionRadioButtonActionPerformed

    private void removeFilesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeFilesRadioButtonActionPerformed
        updateResetDataPartitionButtonState();
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
            upgradeOverwriteList.addSelectionInterval(
                    previousIndex, previousIndex);
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

    private void resetShowHarddisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_resetShowHarddisksCheckBoxItemStateChanged
        new ResetStorageDeviceListUpdater(this, resetStorageDeviceList,
                resetStorageDeviceListModel,
                resetShowHarddisksCheckBox.isSelected(),
                runningSystemSource.getDeviceName()).execute();
    }//GEN-LAST:event_resetShowHarddisksCheckBoxItemStateChanged

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
        updateMediumPanel();
        setISOElementsEnabled(true);
    }//GEN-LAST:event_bootMediumRadioButtonActionPerformed

    private void systemMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemMediumRadioButtonActionPerformed
        updateMediumPanel();
        setISOElementsEnabled(true);
    }//GEN-LAST:event_systemMediumRadioButtonActionPerformed

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

    private void runningSystemSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_runningSystemSourceRadioButtonActionPerformed
        updateInstallSourceGUI();
    }//GEN-LAST:event_runningSystemSourceRadioButtonActionPerformed

    private void isoSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_isoSourceRadioButtonActionPerformed
        updateInstallSourceGUI();
    }//GEN-LAST:event_isoSourceRadioButtonActionPerformed

    private void dataPartitionRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataPartitionRadioButtonActionPerformed
        updateMediumPanel();
        setISOElementsEnabled(false);
    }//GEN-LAST:event_dataPartitionRadioButtonActionPerformed

    private void resetFormatExchangePartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_resetFormatExchangePartitionCheckBoxItemStateChanged
        boolean enabled = resetFormatExchangePartitionCheckBox.isSelected();
        resetFormatExchangePartitionFileSystemLabel.setEnabled(enabled);
        resetFormatExchangePartitionFileSystemComboBox.setEnabled(enabled);
        resetFormatExchangePartitionKeepLabelRadioButton.setEnabled(enabled);
        resetFormatExchangePartitionNewLabelRadioButton.setEnabled(enabled);
        resetFormatExchangePartitionNewLabelTextField.setEnabled(enabled
                && resetFormatExchangePartitionNewLabelRadioButton.isSelected());
    }//GEN-LAST:event_resetFormatExchangePartitionCheckBoxItemStateChanged

    private void resetFormatExchangePartitionNewLabelRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_resetFormatExchangePartitionNewLabelRadioButtonItemStateChanged
        resetFormatExchangePartitionNewLabelTextField.setEnabled(
                resetFormatExchangePartitionCheckBox.isSelected()
                && resetFormatExchangePartitionNewLabelRadioButton.isSelected());
    }//GEN-LAST:event_resetFormatExchangePartitionNewLabelRadioButtonItemStateChanged

    private void upgradeListModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeListModeRadioButtonActionPerformed
        showCard(upgradeSelectionCardPanel, "upgradeSelectionDeviceListPanel");
        new UpgradeStorageDeviceListUpdater(runningSystemSource, this,
                upgradeStorageDeviceList, upgradeStorageDeviceListModel,
                upgradeShowHarddisksCheckBox.isSelected()).execute();
        upgradeShowHarddisksCheckBox.setEnabled(
                upgradeListModeRadioButton.isSelected());
    }//GEN-LAST:event_upgradeListModeRadioButtonActionPerformed

    private void upgradeAutomaticRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeAutomaticRadioButtonActionPerformed
        showCard(upgradeSelectionCardPanel, "upgradeNoMediaPanel");
        nextButton.setEnabled(false);
        upgradeShowHarddisksCheckBox.setEnabled(
                !upgradeAutomaticRadioButton.isSelected());
    }//GEN-LAST:event_upgradeAutomaticRadioButtonActionPerformed

    private void upgradeSelectionTabbedPaneComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_upgradeSelectionTabbedPaneComponentShown
        if (upgradeListModeRadioButton.isSelected()) {
            new UpgradeStorageDeviceListUpdater(runningSystemSource, this,
                    upgradeStorageDeviceList, upgradeStorageDeviceListModel,
                    upgradeShowHarddisksCheckBox.isSelected()).execute();
        }
    }//GEN-LAST:event_upgradeSelectionTabbedPaneComponentShown

    private void resetSelectionTabbedPaneComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_resetSelectionTabbedPaneComponentShown
        if (resetListModeRadioButton.isSelected()) {
            new ResetStorageDeviceListUpdater(this, resetStorageDeviceList,
                    resetStorageDeviceListModel,
                    resetShowHarddisksCheckBox.isSelected(),
                    runningSystemSource.getDeviceName()).execute();
        }
    }//GEN-LAST:event_resetSelectionTabbedPaneComponentShown

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

    private void resetAutomaticModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAutomaticModeRadioButtonActionPerformed
        showCard(resetSelectionCardPanel, "resetNoMediaPanel");
        nextButton.setEnabled(false);
        resetShowHarddisksCheckBox.setEnabled(
                !resetAutomaticModeRadioButton.isSelected());
    }//GEN-LAST:event_resetAutomaticModeRadioButtonActionPerformed

    private void resetListModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetListModeRadioButtonActionPerformed
        boolean selected = resetListModeRadioButton.isSelected();
        resetShowHarddisksCheckBox.setEnabled(selected);
        if (selected) {
            showCard(resetSelectionCardPanel, "resetSelectionDeviceListPanel");
            new ResetStorageDeviceListUpdater(this, resetStorageDeviceList,
                    resetStorageDeviceListModel,
                    resetShowHarddisksCheckBox.isSelected(),
                    runningSystemSource.getDeviceName()).execute();
        }
    }//GEN-LAST:event_resetListModeRadioButtonActionPerformed

    private void resetBackupCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_resetBackupCheckBoxItemStateChanged
        boolean enabled = resetBackupCheckBox.isSelected();
        resetBackupSourceTextField.setEnabled(enabled);
        resetBackupDestinationButton.setEnabled(enabled);
        resetBackupSubdirectoryTable.setEnabled(enabled);
        resetMoveUpButton.setEnabled(enabled);
        resetMoveDownButton.setEnabled(enabled);
    }//GEN-LAST:event_resetBackupCheckBoxItemStateChanged

    private void resetBackupDestinationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetBackupDestinationButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(STRINGS.getString("Destination_Directory"));
        fileChooser.setApproveButtonText(STRINGS.getString("Select"));
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File directory = new File(resetBackupDestinationTextField.getText());
        fileChooser.setSelectedFile(directory);
        // TODO: still broken? (https://bugs.openjdk.java.net/browse/JDK-6572365)
        fileChooser.ensureFileIsVisible(directory);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            resetBackupDestinationTextField.setText(selectedFile.toString());
        }
    }//GEN-LAST:event_resetBackupDestinationButtonActionPerformed

    private void resetMoveUpButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMoveUpButtonActionPerformed
        int selectedRows[] = resetBackupSubdirectoryTable.getSelectedRows();
        subdirectoryTableModel.moveUp(selectedRows);
        resetBackupSubdirectoryTable.clearSelection();
        for (int selectedRow : selectedRows) {
            int previousRow = selectedRow - 1;
            resetBackupSubdirectoryTable.addRowSelectionInterval(
                    previousRow, previousRow);
        }
    }//GEN-LAST:event_resetMoveUpButtonActionPerformed

    private void resetMoveDownButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMoveDownButtonActionPerformed
        int selectedRows[] = resetBackupSubdirectoryTable.getSelectedRows();
        subdirectoryTableModel.moveDown(selectedRows);
        resetBackupSubdirectoryTable.clearSelection();
        for (int selectedRow : selectedRows) {
            int nextRow = selectedRow + 1;
            resetBackupSubdirectoryTable.addRowSelectionInterval(
                    nextRow, nextRow);
        }
    }//GEN-LAST:event_resetMoveDownButtonActionPerformed

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

    private void upgradeSystemPartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeSystemPartitionCheckBoxItemStateChanged
        resetDataPartitionCheckBox.setSelected(true);
    }//GEN-LAST:event_upgradeSystemPartitionCheckBoxItemStateChanged

    private void deleteOnDataPartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_deleteOnDataPartitionCheckBoxItemStateChanged
        boolean enabled = deleteOnDataPartitionCheckBox.isSelected();
        formatDataPartitionRadioButton.setEnabled(enabled);
        removeFilesRadioButton.setEnabled(enabled);
        systemFilesCheckBox.setEnabled(enabled);
        homeDirectoryCheckBox.setEnabled(enabled);
    }//GEN-LAST:event_deleteOnDataPartitionCheckBoxItemStateChanged

    private void resetRestoreDataCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_resetRestoreDataCheckBoxItemStateChanged
        resetRestoreConfigurationPanel.setEnabled(
                resetRestoreDataCheckBox.isSelected());
    }//GEN-LAST:event_resetRestoreDataCheckBoxItemStateChanged

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

    private void selectBackupDestination(JTextField textField) {
        String selectedPath = textField.getText();
        SelectBackupDirectoryDialog dialog = new SelectBackupDirectoryDialog(
                this, null, selectedPath, false);
        if (dialog.showDialog() == JOptionPane.OK_OPTION) {
            textField.setText(dialog.getSelectedPath());
        }
    }

    private void parseCommandLineArguments(String[] arguments) {
        for (int i = 0, length = arguments.length; i < length; i++) {

            // lernstick variant
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

            // exchange partition size
            if (arguments[i].equals("--exchangePartitionSize")
                    && (i != length - 1)) {
                try {
                    commandLineExchangePartitionSize
                            = Integer.parseInt(arguments[i + 1]);
                } catch (NumberFormatException numberFormatException) {
                    LOGGER.log(Level.WARNING, "", numberFormatException);
                }
            }

            // exchange partition file system
            if (arguments[i].equals("--exchangePartitionFileSystem")
                    && (i != length - 1)) {
                commandLineExchangePartitionFileSystem = arguments[i + 1];
            }

            // if the data partition should be copied
            if (arguments[i].equals("--copyDataPartition")
                    && (i != length - 1)) {
                commandLineCopyDataPartition
                        = "true".equalsIgnoreCase(arguments[i + 1]);
            }

            // if the welcome application should be reactivated during upgrade
            if (arguments[i].equals("--reactivateWelcome")
                    && (i != length - 1)) {
                commandLineReactivateWelcome
                        = "true".equalsIgnoreCase(arguments[i + 1]);
            }

            if (arguments[i].equals("--autoUpgrade")) {
                autoUpgrade = true;
            }

            if (arguments[i].equals("--isolatedAutoUpgrade")) {
                isolatedAutoUpgrade = true;
            }

            // only allow one instant* command
            if (arguments[i].equals("--instantInstallation")) {
                instantInstallation = true;
            } else if (arguments[i].equals("--instantUpgrade")) {
                instantUpgrade = true;
            }
        }
    }

    private void setISOInstallationSourcePath(String path) {
        try {
            SystemSource newIsoSystemSource
                    = new IsoSystemSource(path, PROCESS_EXECUTOR);
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
            showErrorMessage(errorMessage);
        } catch (NoExecutableExtLinuxException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String errorMessage
                    = STRINGS.getString("Error_No_Executable_Extlinux");
            errorMessage = MessageFormat.format(errorMessage, path);
            showErrorMessage(errorMessage);
        } catch (NoExtLinuxException ex) {
            LOGGER.log(Level.INFO, "", ex);
            String errorMessage = STRINGS.getString("Error_Deprecyted_ISO");
            errorMessage = MessageFormat.format(errorMessage, path);
            showErrorMessage(errorMessage);
        }
    }

    private void setISOElementsEnabled(boolean enabled) {
        isoLabelLabel.setEnabled(enabled);
        isoLabelTextField.setEnabled(enabled);
        isoDataPartitionModeLabel.setEnabled(enabled);
        isoDataPartitionModeComboBox.setEnabled(enabled);
    }

    private void setSystemSource(SystemSource systemSource) {

        // early return
        if (systemSource == null) {
            return;
        }

        // update system source itself
        this.systemSource = systemSource;

        // update source dependend strings and states
        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(systemSource.getSystemSize());
        String sizeString
                = LernstickFileTools.getDataVolumeString(enlargedSystemSize, 1);

        installStorageDeviceRenderer.setSystemSize(enlargedSystemSize);
        installStorageDeviceList.repaint();

        String text = STRINGS.getString("Select_Install_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        installSelectionHeaderLabel.setText(text);

        text = STRINGS.getString("System_Definition");
        text = MessageFormat.format(text, sizeString);
        systemDefinitionLabel.setText(text);

        sizeString = LernstickFileTools.getDataVolumeString(
                systemSource.getSystemSize(), 1);
        text = STRINGS.getString("Select_Upgrade_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        upgradeSelectionHeaderLabel.setText(text);

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
            copyDataPartitionCheckBox.setText(
                    STRINGS.getString("Copy"));
            copyDataPartitionCheckBox.setToolTipText(
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
            copyDataPartitionCheckBox.setEnabled(true);

            String checkBoxText = STRINGS.getString("Copy")
                    + " (" + LernstickFileTools.getDataVolumeString(
                            dataPartition.getUsedSpace(false), 1) + ')';
            copyDataPartitionCheckBox.setText(checkBoxText);
        }
        syncWidth(copyExchangePartitionCheckBox, copyDataPartitionCheckBox);

        DataPartitionMode sourceDataPartitionMode
                = systemSource.getDataPartitionMode();
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
            isoDataPartitionModeComboBox.setSelectedItem(selectedItem);
        }

        if (StorageDevice.Type.USBFlashDrive == systemSource.getDeviceType()) {
            Icon usb2usbIcon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/usb2usb.png"));
            infoLabel.setIcon(usb2usbIcon);
            installButton.setIcon(usb2usbIcon);
        }
    }

    private void syncWidth(JComponent component1, JComponent component2) {
        int preferredWidth = Math.max(component1.getPreferredSize().width,
                component2.getPreferredSize().width);
        setPreferredWidth(preferredWidth, component1, component2);
    }

    private void setPreferredWidth(int preferredWidth, JComponent... components) {
        for (JComponent component : components) {
            Dimension preferredSize = component.getPreferredSize();
            preferredSize.width = preferredWidth;
            component.setPreferredSize(preferredSize);
        }
    }

    private void setSpinnerColums(JSpinner spinner, int columns) {
        JComponent editor = spinner.getEditor();
        JFormattedTextField tf
                = ((JSpinner.DefaultEditor) editor).getTextField();
        tf.setColumns(columns);
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

    private void updateInstallationSource() {
        if (isoSourceRadioButton.isSelected()
                && isoSourceTextField.getText().isEmpty()) {
            showCard(installTargetCardPanel, "installNoSourcePanel");
        } else {
            showCard(installTargetCardPanel, "installTargetPanel");
        }
        updateInstallNextButton();
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
        showCard(cardPanel, "upgradeSelectionTabbedPane");
        enableNextButton();
    }

    private void updateMediumPanel() {
        CardLayout cardLayout = (CardLayout) isoOptionsCardPanel.getLayout();
        if (bootMediumRadioButton.isSelected()) {
            cardLayout.show(isoOptionsCardPanel, "bootMediumPanel");
        } else {
            cardLayout.show(isoOptionsCardPanel, "systemMediumPanel");
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

    private void reset() {
        // final warning
        int result = JOptionPane.showConfirmDialog(this,
                STRINGS.getString("Final_Reset_Warning"),
                STRINGS.getString("Warning"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        int[] selectedIndices = resetStorageDeviceList.getSelectedIndices();
        List<StorageDevice> deviceList = new ArrayList<>();
        for (int i : selectedIndices) {
            deviceList.add(resetStorageDeviceListModel.get(i));
        }

        resetStorageDevices(deviceList);
    }

    private void resetStorageDevices(List<StorageDevice> deviceList) {
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        state = State.RESET;

        batchCounter = 0;
        resultsList = new ArrayList<>();

        String exchangePartitionFileSystem
                = resetFormatExchangePartitionFileSystemComboBox.getSelectedItem().toString();
        // TODO: using dataPartitionFileSystemComboBox.getSelectedItem() here is
        // ugly because the input field it is not visible when upgrading
        String dataPartitionFileSystem
                = dataPartitionFileSystemComboBox.getSelectedItem().toString();

        new Resetter(this, deviceList, runningSystemSource.getDeviceName(),
                printDocumentsCheckBox.isSelected(),
                printingDirectoriesTextArea.getText(),
                scanDirectoriesRecursivelyCheckBox.isSelected(),
                printOdtCheckBox.isSelected(), printOdsCheckBox.isSelected(),
                printOdpCheckBox.isSelected(), printPdfCheckBox.isSelected(),
                printDocCheckBox.isSelected(), printDocxCheckBox.isSelected(),
                printXlsCheckBox.isSelected(), printXlsxCheckBox.isSelected(),
                printPptCheckBox.isSelected(), printPptxCheckBox.isSelected(),
                resetPrintPreferences.getAutoPrintMode(),
                ((Number) printCopiesSpinner.getValue()).intValue(),
                printDuplexCheckBox.isSelected(),
                resetBackupCheckBox.isSelected(),
                resetBackupSourceTextField.getText(),
                resetBackupDestinationTextField.getText(),
                orderedSubdirectoriesEntries,
                resetFormatExchangePartitionCheckBox.isSelected(),
                exchangePartitionFileSystem,
                resetFormatExchangePartitionKeepLabelRadioButton.isSelected(),
                resetFormatExchangePartitionNewLabelTextField.getText(),
                deleteOnDataPartitionCheckBox.isSelected(),
                formatDataPartitionRadioButton.isSelected(),
                dataPartitionFileSystem, homeDirectoryCheckBox.isSelected(),
                systemFilesCheckBox.isSelected(),
                resetRestoreDataCheckBox.isSelected(),
                resetRestoreConfigurationPanel.getEntries(), resetLock)
                .execute();
    }

    private void sortList(boolean ascending) {
        // remember selection before sorting
        List<String> selectedValues
                = upgradeOverwriteList.getSelectedValuesList();

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
        list.forEach((string) -> {
            upgradeOverwriteListModel.addElement(string);
        });

        // restore original selection
        for (String selectedValue : selectedValues) {
            int selectedIndex
                    = upgradeOverwriteListModel.indexOf(selectedValue);
            upgradeOverwriteList.addSelectionInterval(
                    selectedIndex, selectedIndex);
        }
    }

    private void handleListDataEvent(ListDataEvent e) {
        LOGGER.info(e.toString());
        Object source = e.getSource();

        if (source == upgradeOverwriteListModel) {
            LOGGER.info("source == upgradeOverwriteListModel");
            boolean sortable = upgradeOverwriteListModel.getSize() > 1;
            sortAscendingButton.setEnabled(sortable);
            sortDescendingButton.setEnabled(sortable);

        } else if (source == upgradeStorageDeviceListModel) {
            LOGGER.info("source == upgradeStorageDeviceListModel");
            if ((e.getType() == ListDataEvent.INTERVAL_ADDED)
                    && upgradeAutomaticRadioButton.isSelected()) {

                List<StorageDevice> deviceList = new ArrayList<>();
                for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
                    LOGGER.log(Level.INFO,
                            "adding index {0} to device list", i);
                    deviceList.add(upgradeStorageDeviceListModel.get(i));
                }

                upgradeStorageDevices(deviceList);
            }

        } else if (source == resetStorageDeviceListModel) {
            LOGGER.info("source == resetStorageDeviceListModel");
            if ((e.getType() == ListDataEvent.INTERVAL_ADDED)
                    && resetAutomaticModeRadioButton.isSelected()) {

                List<StorageDevice> deviceList = new ArrayList<>();
                for (int i = e.getIndex0(); i <= e.getIndex1(); i++) {
                    LOGGER.log(Level.INFO,
                            "adding index {0} to device list", i);
                    deviceList.add(resetStorageDeviceListModel.get(i));
                }

                resetStorageDevices(deviceList);
            }

        } else {
            LOGGER.log(Level.WARNING, "unknown source: {0}", source);
        }
    }

    private void updateResetDataPartitionButtonState() {
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
                    fileChooser.getFileFilter() == NO_HIDDEN_FILES_FILTER);
            fileChooser.rescanCurrentDirectory();
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

        // the list of listmodels where the device must be removed
        List<DefaultListModel<StorageDevice>> listModels = new ArrayList<>();

        SwingUtilities.invokeLater(() -> {

            switch (state) {
                case INSTALL_SELECTION:
                    listModels.add(installStorageDeviceListModel);
                    listModels.add(installTransferStorageDeviceListModel);
                    break;

                case UPGRADE_SELECTION:
                    if (isolatedAutoUpgrade) {
                        upgradeNoMediaPanel.setBackground(Color.YELLOW);
                        upgradeNoMediaLabel.setText(
                                STRINGS.getString("Insert_Media_Isolated"));
                    }
                    listModels.add(upgradeStorageDeviceListModel);
                    break;

                case RESET_SELECTION:
                    listModels.add(resetStorageDeviceListModel);
                    break;

                default:
                    LOGGER.log(Level.WARNING,
                            "Unsupported state: {0}", state);
                    return;
            }

            listModels.forEach(listModel -> {
                for (int i = 0, size = listModel.getSize(); i < size; i++) {
                    StorageDevice storageDevice = listModel.get(i);
                    if (storageDevice.getDevice().equals(device)) {

                        listModel.remove(i);
                        LOGGER.log(Level.INFO,
                                "removed from storage device list: {0}",
                                device);

                        switch (state) {
                            case INSTALL_SELECTION:
                                installStorageDeviceListChanged();
                                installTransferStorageDeviceListChanged();
                                break;

                            case UPGRADE_SELECTION:
                                upgradeStorageDeviceListChanged();
                                break;

                            case RESET_SELECTION:
                                resetStorageDeviceListChanged();
                        }

                        break; // for
                    }
                }
            });
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
        installationDestinationSelectionPreferences.
                saveExplicitExchangeSize(explicitExchangeSize);
        preferencesHandler.save();

        runningSystemSource.unmountTmpPartitions();
        if (isoSystemSource != null) {
            isoSystemSource.unmountTmpPartitions();
        }

        // stop monitoring thread
        udisksMonitorThread.stopMonitoring();

        // everything is done, disappear now
        System.exit(0);
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
                installShowHarddisksCheckBox.isSelected(),
                runningSystemSource.getDeviceName()).execute();

        // update transfer storage device list
        new InstallTransferStorageDeviceListUpdater(this,
                installTransferStorageDeviceList,
                installTransferStorageDeviceListModel,
                installShowHarddisksCheckBox.isSelected(),
                runningSystemSource.getDeviceName()).execute();

        previousButton.setEnabled(true);
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

        // no valid source selected
        if (isoSourceRadioButton.isSelected()
                && isoSourceTextField.getText().isEmpty()) {
            disableNextButton();
            return;
        }

        int[] selectedIndices = installStorageDeviceList.getSelectedIndices();

        // no storage device selected
        if (selectedIndices.length == 0) {
            disableNextButton();
            return;
        }

        // check selection
        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(systemSource.getSystemSize());
        for (int i : selectedIndices) {
            StorageDevice device = installStorageDeviceListModel.get(i);
            PartitionState partitionState = DLCopy.getPartitionState(
                    device.getSize(), enlargedSystemSize);
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
        SwingUtilities.invokeLater(() -> {
            showCard(container, cardName);
            progressBar.setString(STRINGS.getString(text));
        });
    }

    private void deviceStarted(StorageDevice storageDevice) {
        batchCounter++;
        resultsList.add(new StorageDeviceResult(storageDevice));
    }

    private void deviceFinished(String errorMessage) {
        // update "in progress" entry
        StorageDeviceResult result = resultsList.get(resultsList.size() - 1);
        result.finish();
        result.setErrorMessage(errorMessage);

        // update final report
        resultsTableModel.setList(resultsList);
    }

    private void batchFinished(String nonRemovableKey,
            String removableKey, String reportKey) {
        setTitle(STRINGS.getString("DLCopySwingGUI.title"));
        String key;
        switch (systemSource.getDeviceType()) {
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
        LOGGER.log(Level.FINEST, "\n"
                + "    thread: {0}\n"
                + "    container : {1}\n"
                + "    card: {2}",
                new Object[]{Thread.currentThread().getName(),
                    container.getName(), cardName});
        CardLayout cardLayout = (CardLayout) container.getLayout();
        cardLayout.show(container, cardName);
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

    private static void setLabelTextonEDT(
            final JLabel label, final String text) {
        SwingUtilities.invokeLater(() -> label.setText(text));
    }

    private static void setProgressBarStringOnEDT(
            final JProgressBar progressBar, final String string) {
        SwingUtilities.invokeLater(() -> progressBar.setString(string));
    }

    private void checkAndInstallSelection(boolean interactive)
            throws IOException, DBusException {

        // check all selected target USB storage devices
        int[] selectedIndices = installStorageDeviceList.getSelectedIndices();
        boolean harddiskSelected = false;
        for (int i : selectedIndices) {
            StorageDevice storageDevice
                    = installStorageDeviceListModel.getElementAt(i);
            if (storageDevice.getType() == StorageDevice.Type.HardDrive) {
                harddiskSelected = true;
            }
            PartitionSizes partitionSizes = DLCopy.getInstallPartitionSizes(
                    systemSource, storageDevice,
                    exchangePartitionSizeSlider.getValue());
            if (!checkPersistence(partitionSizes)) {
                return;
            }
            if (!checkExchange(partitionSizes)) {
                return;
            }
            if (!checkTransfer(storageDevice, partitionSizes)) {
                return;
            }
        }

        // exchange copy and exchange transfer are mutually exclusive
        if (!installTransferStorageDeviceList.isSelectionEmpty()
                && copyExchangePartitionCheckBox.isSelected()
                && transferExchangeCheckBox.isSelected()) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_Exchange_Copy_And_Transfer"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return;
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
        } else if (interactive) {
            int result = JOptionPane.showConfirmDialog(this,
                    STRINGS.getString("Final_Installation_Warning"),
                    STRINGS.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        installationDestinationSelectionPreferences.
                saveExplicitExchangeSize(explicitExchangeSize);
        preferencesHandler.save();

        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        state = State.INSTALLATION;

        // let's start...
        Number autoNumberStart = (Number) autoNumberStartSpinner.getValue();
        Number autoIncrementNumber
                = (Number) autoNumberIncrementSpinner.getValue();
        Number autoMinDigitsNumber
                = (Number) autoNumberMinDigitsSpinner.getValue();
        int autoNumber = autoNumberStart.intValue();
        int autoIncrement = autoIncrementNumber.intValue();
        int autoMinDigits = autoMinDigitsNumber.intValue();
        List<StorageDevice> deviceList = new ArrayList<>();
        for (int i : selectedIndices) {
            deviceList.add(installStorageDeviceListModel.get(i));
        }
        Object selectedFileSystem
                = exchangePartitionFileSystemComboBox.getSelectedItem();
        String exchangePartitionFileSystem = selectedFileSystem.toString();
        String dataPartitionFileSystem
                = dataPartitionFileSystemComboBox.getSelectedItem().toString();
        DataPartitionMode dataPartitionMode
                = getDataPartitionMode(dataPartitionModeComboBox);
        boolean copyExchange = copyExchangePartitionCheckBox.isEnabled()
                && copyExchangePartitionCheckBox.isSelected();
        boolean copyData = copyDataPartitionCheckBox.isEnabled()
                & copyDataPartitionCheckBox.isSelected();
        resultsList = new ArrayList<>();
        batchCounter = 0;

        new Installer(systemSource, deviceList,
                exchangePartitionTextField.getText(),
                exchangePartitionFileSystem, dataPartitionFileSystem,
                digestCache, this, exchangePartitionSizeSlider.getValue(),
                copyExchange, autoNumberPatternTextField.getText(), autoNumber,
                autoIncrement, autoMinDigits, copyData, dataPartitionMode,
                installTransferStorageDeviceList.getSelectedValue(),
                transferExchangeCheckBox.isSelected(),
                transferHomeCheckBox.isSelected(),
                transferNetworkCheckBox.isSelected(),
                transferPrinterCheckBox.isSelected(),
                transferFirewallCheckBox.isSelected(),
                transferUserSettingsCheckBox.isSelected(),
                checkCopiesCheckBox.isSelected(),
                installLock).execute();

        updateTableActionListener
                = new UpdateChangingDurationsTableActionListener(
                        installationResultsTableModel);
        tableUpdateTimer = new Timer(1000, updateTableActionListener);
        tableUpdateTimer.setInitialDelay(0);
        tableUpdateTimer.start();
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
                upgradeSelectionTabbedPane.setSelectedComponent(
                        upgradeDetailsTabbedPane);
                upgradeDetailsTabbedPane.setSelectedComponent(
                        upgradeOptionsPanel);
                textField.requestFocusInWindow();
                textField.selectAll();
                showErrorMessage(errorMessage);
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
                upgradeSelectionTabbedPane.setSelectedComponent(
                        upgradeDetailsTabbedPane);
                upgradeDetailsTabbedPane.setSelectedComponent(
                        upgradeOptionsPanel);
                showErrorMessage(errorMessage);
                resizeExchangeTextField.requestFocusInWindow();
                resizeExchangeTextField.selectAll();
                return false;
            }
        }
        return true;
    }

    private void upgradeStorageDevices(List<StorageDevice> deviceList) {
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
        int exchangeMB = 0;
        if (resizeExchangeRadioButton.isSelected()) {
            exchangeMB = Integer.parseInt(resizeExchangeTextField.getText());
        }

        Object selectedItem
                = exchangePartitionFileSystemComboBox.getSelectedItem();
        String exchangePartitionFileSystem = selectedItem.toString();
        // TODO: using dataPartitionFileSystemComboBox.getSelectedItem() here is
        // ugly because the input field it is not visible when upgrading
        String dataPartitionFileSystem
                = dataPartitionFileSystemComboBox.getSelectedItem().toString();

        // TODO: using exchangePartitionTextField.getText() here is ugly
        // because the input field it is not visible when upgrading
        new Upgrader(runningSystemSource, deviceList,
                exchangePartitionTextField.getText(),
                exchangePartitionFileSystem, dataPartitionFileSystem,
                digestCache, this, this, repartitionStrategy, exchangeMB,
                automaticBackupCheckBox.isSelected(),
                automaticBackupTextField.getText(), removeBackup,
                upgradeSystemPartitionCheckBox.isSelected(),
                resetDataPartitionCheckBox.isSelected(),
                keepPrinterSettingsCheckBox.isSelected(),
                keepNetworkSettingsCheckBox.isSelected(),
                keepFirewallSettingsCheckBox.isSelected(),
                keepUserSettingsCheckBox.isSelected(),
                reactivateWelcomeCheckBox.isSelected(),
                removeHiddenFilesCheckBox.isSelected(), overWriteList,
                DLCopy.getEnlargedSystemSize(
                        runningSystemSource.getSystemSize()), upgradeLock)
                .execute();

        updateTableActionListener
                = new UpdateChangingDurationsTableActionListener(
                        upgradeResultsTableModel);
        tableUpdateTimer = new Timer(1000, updateTableActionListener);
        tableUpdateTimer.setInitialDelay(0);
        tableUpdateTimer.start();
    }

    private void upgradeSelectedStorageDevices() {
        // some backup related sanity checks
        if (!upgradeSanityChecks()) {
            return;
        }

        List<StorageDevice> selectedDevices
                = upgradeStorageDeviceList.getSelectedValuesList();
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
        for (int i : upgradeStorageDeviceList.getSelectedIndices()) {
            deviceList.add(upgradeStorageDeviceListModel.get(i));
        }
        upgradeStorageDevices(deviceList);
    }

    private boolean checkExchange(PartitionSizes partitionSizes)
            throws IOException {

        // early return
        if (!copyExchangePartitionCheckBox.isEnabled()
                || !copyExchangePartitionCheckBox.isSelected()) {
            return true;
        }

        // check if the target storage device actually has an exchange partition
        return checkExchangePartition(systemSource.getExchangePartition(),
                partitionSizes, "Error_No_Exchange_At_Target",
                "Error_Target_Exchange_Too_Small");
    }

    private boolean checkTransfer(StorageDevice storageDevice,
            PartitionSizes partitionSizes) {

        StorageDevice transferSourceDevice
                = installTransferStorageDeviceList.getSelectedValue();

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

    private boolean isUnmountedPersistenceAvailable()
            throws IOException, DBusException {

        // check that a persistence partition is available
        Partition dataPartition = systemSource.getDataPartition();
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
                    systemSource.getDataPartition().umount();
                    return isUnmountedPersistenceAvailable();
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean checkPersistence(PartitionSizes partitionSizes)
            throws IOException, DBusException {

        if (!(copyDataPartitionCheckBox.isEnabled()
                && copyDataPartitionCheckBox.isSelected())) {
            return true;
        }

        if (!isUnmountedPersistenceAvailable()) {
            return false;
        }

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

    private void switchToISOInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        showCard(cardPanel, "toISOInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.ISO_INFORMATION;
    }

    private void switchToISOSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        showCard(cardPanel, "toISOSelectionPanel");
        checkFreeSpaceTextField();
        enableNextButton();
        state = State.ISO_SELECTION;
    }

    private void switchToInstallInformation() {
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

    private void switchToResetInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        executionLabel.setText(STRINGS.getString("Reset_Label"));
        showCard(cardPanel, "resetInfoPanel");
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.RESET_INFORMATION;
    }

    private void switchToResetSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.RESET_SELECTION;
        showCard(cardPanel, "resetSelectionTabbedPane");
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
            StorageDeviceRenderer renderer, JList<StorageDevice> list) {

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
        private final ProcessExecutor executor = new ProcessExecutor(false);

        @Override
        public void run() {
            String binaryName;
            String parameter;
            if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
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
    private javax.swing.JLabel autoNumberMinDigitsLabel;
    private javax.swing.JSpinner autoNumberMinDigitsSpinner;
    private javax.swing.JPanel autoNumberPanel;
    private javax.swing.JLabel autoNumberPatternLabel;
    private javax.swing.JTextField autoNumberPatternTextField;
    private javax.swing.JLabel autoNumberStartLabel;
    private javax.swing.JSpinner autoNumberStartSpinner;
    private javax.swing.JRadioButton autoPrintAllDocumentsRadioButton;
    private javax.swing.JRadioButton autoPrintNoneRadioButton;
    private javax.swing.JRadioButton autoPrintSingleDocumentsRadioButton;
    private javax.swing.JCheckBox autoStartInstallerCheckBox;
    private javax.swing.JButton automaticBackupButton;
    private javax.swing.JCheckBox automaticBackupCheckBox;
    private javax.swing.JLabel automaticBackupLabel;
    private javax.swing.JCheckBox automaticBackupRemoveCheckBox;
    private javax.swing.JTextField automaticBackupTextField;
    private javax.swing.ButtonGroup automaticPrintingButtonGroup;
    private javax.swing.JPanel automaticPrintingPanel;
    private javax.swing.JPanel backupDestinationPanel;
    private javax.swing.JPanel basicDataPartitionPanel;
    private javax.swing.JPanel basicExchangePartitionPanel;
    private javax.swing.JLabel bootDefinitionLabel;
    private javax.swing.JPanel bootMediumPanel;
    private javax.swing.JRadioButton bootMediumRadioButton;
    private javax.swing.JPanel buttonGridPanel;
    private javax.swing.JPanel cardPanel;
    private javax.swing.JCheckBox checkCopiesCheckBox;
    private javax.swing.JLabel choiceLabel;
    private javax.swing.JPanel choicePanel;
    private javax.swing.JCheckBox copyDataPartitionCheckBox;
    private javax.swing.JCheckBox copyExchangePartitionCheckBox;
    private javax.swing.JLabel cpFilenameLabel;
    private javax.swing.JPanel cpPanel;
    private javax.swing.JProgressBar cpPogressBar;
    private javax.swing.JLabel cpTimeLabel;
    private javax.swing.JLabel currentlyInstalledDeviceLabel;
    private javax.swing.JLabel currentlyResettingDeviceLabel;
    private javax.swing.JLabel currentlyUpgradedDeviceLabel;
    private javax.swing.JLabel dataDefinitionLabel;
    private javax.swing.JPanel dataPartitionDetailsPanel;
    private javax.swing.JComboBox<String> dataPartitionFileSystemComboBox;
    private javax.swing.JLabel dataPartitionFileSystemLabel;
    private javax.swing.JComboBox<String> dataPartitionModeComboBox;
    private javax.swing.JLabel dataPartitionModeLabel;
    private javax.swing.JRadioButton dataPartitionRadioButton;
    private javax.swing.JSeparator dataPartitionSeparator;
    private javax.swing.JCheckBox deleteOnDataPartitionCheckBox;
    private javax.swing.JLabel doneLabel;
    private javax.swing.JPanel donePanel;
    private javax.swing.ButtonGroup exchangeButtonGroup;
    private javax.swing.JLabel exchangeDefinitionLabel;
    private javax.swing.JPanel exchangePartitionDetailsPanel;
    private javax.swing.JComboBox<String> exchangePartitionFileSystemComboBox;
    private javax.swing.JLabel exchangePartitionFileSystemLabel;
    private javax.swing.JPanel exchangePartitionFileSystemPanel;
    private javax.swing.JLabel exchangePartitionLabel;
    private javax.swing.JPanel exchangePartitionLabelPanel;
    private javax.swing.JSeparator exchangePartitionSeparator;
    private javax.swing.JLabel exchangePartitionSizeLabel;
    private javax.swing.JSlider exchangePartitionSizeSlider;
    private javax.swing.JTextField exchangePartitionSizeTextField;
    private javax.swing.JLabel exchangePartitionSizeUnitLabel;
    private javax.swing.JTextField exchangePartitionTextField;
    private javax.swing.JLabel executionLabel;
    private javax.swing.JPanel executionPanel;
    private javax.swing.JSeparator executionPanelSeparator;
    private javax.swing.JRadioButton formatDataPartitionRadioButton;
    private javax.swing.JLabel freeSpaceLabel;
    private javax.swing.JTextField freeSpaceTextField;
    private javax.swing.JCheckBox homeDirectoryCheckBox;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JLabel infoStepLabel;
    private javax.swing.JPanel installBasicsPanel;
    private javax.swing.JButton installButton;
    private javax.swing.JPanel installCardPanel;
    private javax.swing.JLabel installCopyLabel;
    private javax.swing.JPanel installCopyPanel;
    private javax.swing.JPanel installCurrentPanel;
    private javax.swing.JPanel installDetailsPanel;
    private ch.fhnw.filecopier.FileCopierPanel installFileCopierPanel;
    private javax.swing.JProgressBar installIndeterminateProgressBar;
    private javax.swing.JPanel installIndeterminateProgressPanel;
    private javax.swing.JPanel installInfoPanel;
    private javax.swing.JLabel installLabel;
    private javax.swing.JLabel installNoMediaLabel;
    private javax.swing.JPanel installNoMediaPanel;
    private javax.swing.JLabel installNoSouceLabel;
    private javax.swing.JPanel installNoSourcePanel;
    private javax.swing.JPanel installReportPanel;
    private javax.swing.JPanel installSelectionCardPanel;
    private javax.swing.JLabel installSelectionCountLabel;
    private javax.swing.JLabel installSelectionHeaderLabel;
    private javax.swing.JPanel installSelectionPanel;
    private javax.swing.JTabbedPane installSelectionTabbedPane;
    private javax.swing.JCheckBox installShowHarddisksCheckBox;
    private javax.swing.ButtonGroup installSourceButtonGroup;
    private javax.swing.JPanel installSourcePanel;
    private javax.swing.JList<StorageDevice> installStorageDeviceList;
    private javax.swing.JScrollPane installStorageDeviceListScrollPane;
    private javax.swing.JTabbedPane installTabbedPane;
    private javax.swing.JPanel installTargetCardPanel;
    private javax.swing.JPanel installTargetPanel;
    private javax.swing.JPanel installTransferPanel;
    private javax.swing.JScrollPane installTransferScrollPane;
    private javax.swing.JList<StorageDevice> installTransferStorageDeviceList;
    private javax.swing.JScrollPane installationResultsScrollPane;
    private javax.swing.JTable installationResultsTable;
    private javax.swing.ButtonGroup isoButtonGroup;
    private javax.swing.JComboBox<String> isoDataPartitionModeComboBox;
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
    private javax.swing.JComboBox<String> jumpComboBox;
    private javax.swing.JLabel jumpLabel1;
    private javax.swing.JLabel jumpLabel2;
    private javax.swing.JCheckBox keepFirewallSettingsCheckBox;
    private javax.swing.JCheckBox keepNetworkSettingsCheckBox;
    private javax.swing.JCheckBox keepPrinterSettingsCheckBox;
    private javax.swing.JCheckBox keepUserSettingsCheckBox;
    private javax.swing.JButton nextButton;
    private javax.swing.JPanel northEastPanel;
    private javax.swing.JPanel northWestPanel;
    private javax.swing.JRadioButton originalExchangeRadioButton;
    private javax.swing.JPanel prevNextButtonPanel;
    private javax.swing.JButton previousButton;
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
    private javax.swing.JScrollPane printingDirectoriesScrollPane;
    private javax.swing.JTextArea printingDirectoriesTextArea;
    private javax.swing.JPanel printingDirectoryPanel;
    private javax.swing.JPanel radioButtonPanel;
    private javax.swing.JCheckBox reactivateWelcomeCheckBox;
    private javax.swing.JRadioButton removeExchangeRadioButton;
    private javax.swing.JRadioButton removeFilesRadioButton;
    private javax.swing.JCheckBox removeHiddenFilesCheckBox;
    private javax.swing.JPanel repartitionExchangeOptionsPanel;
    private javax.swing.JRadioButton resetAutomaticModeRadioButton;
    private javax.swing.JCheckBox resetBackupCheckBox;
    private javax.swing.JLabel resetBackupCopyLabel;
    private javax.swing.JButton resetBackupDestinationButton;
    private javax.swing.JPanel resetBackupDestinationPanel;
    private javax.swing.JTextField resetBackupDestinationTextField;
    private javax.swing.JPanel resetBackupDetailsPanel;
    private ch.fhnw.filecopier.FileCopierPanel resetBackupFileCopierPanel;
    private javax.swing.JPanel resetBackupPanel;
    private javax.swing.JPanel resetBackupSourcePanel;
    private javax.swing.JTextField resetBackupSourceTextField;
    private javax.swing.JPanel resetBackupSpacerPanel;
    private javax.swing.JPanel resetBackupSubdirectoryPanel;
    private javax.swing.JScrollPane resetBackupSubdirectoryScrollPane;
    private javax.swing.JTable resetBackupSubdirectoryTable;
    private javax.swing.JButton resetButton;
    private javax.swing.JPanel resetCardPanel;
    private javax.swing.JLabel resetDataDefinitionLabel;
    private javax.swing.ButtonGroup resetDataPartitionButtonGroup;
    private javax.swing.JCheckBox resetDataPartitionCheckBox;
    private javax.swing.JPanel resetDataPartitionDetailsPanel;
    private javax.swing.JPanel resetDeletePanel;
    private javax.swing.JLabel resetExchangeDefinitionLabel;
    private javax.swing.ButtonGroup resetExchangePartitionButtonGroup;
    private javax.swing.JPanel resetExchangePartitionDetailsPanel;
    private javax.swing.JPanel resetExchangespacer;
    private javax.swing.JCheckBox resetFormatExchangePartitionCheckBox;
    private javax.swing.JComboBox<String> resetFormatExchangePartitionFileSystemComboBox;
    private javax.swing.JLabel resetFormatExchangePartitionFileSystemLabel;
    private javax.swing.JRadioButton resetFormatExchangePartitionKeepLabelRadioButton;
    private javax.swing.JRadioButton resetFormatExchangePartitionNewLabelRadioButton;
    private javax.swing.JTextField resetFormatExchangePartitionNewLabelTextField;
    private javax.swing.JLabel resetInfoLabel;
    private javax.swing.JPanel resetInfoPanel;
    private javax.swing.JRadioButton resetListModeRadioButton;
    private javax.swing.JButton resetMoveDownButton;
    private javax.swing.JButton resetMoveUpButton;
    private javax.swing.JLabel resetNoMediaLabel;
    private javax.swing.JPanel resetNoMediaPanel;
    private javax.swing.JLabel resetOsDefinitionLabel;
    private javax.swing.JPanel resetPanel;
    private javax.swing.JPanel resetPrintingDetailsPanel;
    private javax.swing.JProgressBar resetProgressBar;
    private javax.swing.JPanel resetProgressPanel;
    private ch.fhnw.dlcopy.gui.swing.OverwriteConfigurationPanel resetRestoreConfigurationPanel;
    private javax.swing.JCheckBox resetRestoreDataCheckBox;
    private javax.swing.JPanel resetRestorePanel;
    private javax.swing.JPanel resetSelectionCardPanel;
    private javax.swing.JLabel resetSelectionCountLabel;
    private javax.swing.JPanel resetSelectionDeviceListPanel;
    private javax.swing.JLabel resetSelectionHeaderLabel;
    private javax.swing.JPanel resetSelectionInfoPanel;
    private javax.swing.ButtonGroup resetSelectionModeButtonGroup;
    private javax.swing.JPanel resetSelectionPanel;
    private javax.swing.JTabbedPane resetSelectionTabbedPane;
    private javax.swing.JCheckBox resetShowHarddisksCheckBox;
    private javax.swing.JList<StorageDevice> resetStorageDeviceList;
    private javax.swing.JScrollPane resetStorageDeviceListScrollPane;
    private javax.swing.JLabel resizeExchangeLabel;
    private javax.swing.JRadioButton resizeExchangeRadioButton;
    private javax.swing.JTextField resizeExchangeTextField;
    private javax.swing.JLabel resultsInfoLabel;
    private javax.swing.JPanel resultsPanel;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JPanel resultsTitledPanel;
    private javax.swing.JPanel rightPrintingPanel;
    private javax.swing.JPanel rsyncPanel;
    private javax.swing.JProgressBar rsyncPogressBar;
    private javax.swing.JLabel rsyncTimeLabel;
    private javax.swing.JRadioButton runningSystemSourceRadioButton;
    private javax.swing.JCheckBox scanDirectoriesRecursivelyCheckBox;
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
    private javax.swing.JPanel transferCheckboxPanel;
    private javax.swing.JCheckBox transferExchangeCheckBox;
    private javax.swing.JCheckBox transferFirewallCheckBox;
    private javax.swing.JCheckBox transferHomeCheckBox;
    private javax.swing.JLabel transferLabel;
    private javax.swing.JCheckBox transferNetworkCheckBox;
    private javax.swing.JCheckBox transferPrinterCheckBox;
    private javax.swing.JCheckBox transferUserSettingsCheckBox;
    private javax.swing.JRadioButton upgradeAutomaticRadioButton;
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
    private javax.swing.JRadioButton upgradeListModeRadioButton;
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
    private javax.swing.JList<String> upgradeOverwriteList;
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
    private javax.swing.JPanel upgradeSelectionInfoPanel;
    private javax.swing.ButtonGroup upgradeSelectionModeButtonGroup;
    private javax.swing.JPanel upgradeSelectionPanel;
    private javax.swing.JSeparator upgradeSelectionSeparator;
    private javax.swing.JTabbedPane upgradeSelectionTabbedPane;
    private javax.swing.JCheckBox upgradeShowHarddisksCheckBox;
    private javax.swing.JList<StorageDevice> upgradeStorageDeviceList;
    private javax.swing.JScrollPane upgradeStorageDeviceListScrollPane;
    private javax.swing.JCheckBox upgradeSystemPartitionCheckBox;
    private javax.swing.JTabbedPane upgradeTabbedPane;
    private javax.swing.JLabel writableLabel;
    private javax.swing.JTextField writableTextField;
    // End of variables declaration//GEN-END:variables
}
