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
import ch.fhnw.dlcopy.RepartitionStrategy;
import ch.fhnw.dlcopy.Resetter;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SquashFSCreator;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.Upgrader;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.swing.preferences.DLCopySwingGUIPreferencesHandler;
import ch.fhnw.dlcopy.gui.swing.preferences.MainMenuPreferences;
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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Installs Debian Live to a USB flash drive
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DLCopySwingGUI extends JFrame
        implements DLCopyGUI, PropertyChangeListener, ListDataListener {

    public enum State {

        INSTALL_INFORMATION, INSTALL_SELECTION, INSTALLATION,
        UPGRADE_INFORMATION, UPGRADE_SELECTION, UPGRADE,
        RESET_INFORMATION, RESET_SELECTION, RESET,
        ISO_INFORMATION, ISO_SELECTION, ISO_INSTALLATION
    }
    public State state = State.INSTALL_INFORMATION;

    private final static Logger LOGGER
            = Logger.getLogger(DLCopySwingGUI.class.getName());
    private final static ProcessExecutor PROCESS_EXECUTOR
            = new ProcessExecutor();
    private final static FileFilter NO_HIDDEN_FILES_FILTER
            = NoHiddenFilesSwingFileFilter.getInstance();
    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";
    private final DefaultListModel<StorageDevice> upgradeStorageDeviceListModel
            = new DefaultListModel<>();
    private final UpgradeStorageDeviceRenderer upgradeStorageDeviceRenderer;

    private final DateFormat timeFormat;

    private SystemSource runningSystemSource;
    private SystemSource systemSource = null;

    private boolean persistenceBoot;

    private DebianLiveDistribution debianLiveDistribution;

    private final UdisksMonitorThread udisksMonitorThread;
    private final DefaultListModel<String> upgradeOverwriteListModel;
    private JFileChooser addFileChooser;
    private RdiffBackupRestore rdiffBackupRestore;

    private final ResultsTableModel upgradeResultsTableModel;
    private final ResultsTableModel resultsTableModel;
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

        String cmdLineFileName = "/proc/cmdline";
        try {
            String cmdLine = DLCopy.readOneLineFile(new File(cmdLineFileName));
            persistenceBoot = cmdLine.contains(" persistence ");
            LOGGER.log(Level.FINEST,
                    "persistenceBoot: {0}", persistenceBoot);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    "could not read \"" + cmdLineFileName + '\"', ex);
        }

        initComponents();

        // init jump targets
        String[] jumpTargets = new String[]{
            STRINGS.getString("Main_Menu"),
            installButton.getText(),
            upgradeButton.getText(),
            toISOButton.getText(),
            resetButton.getText()
        };
        jumpComboBox.setModel(new DefaultComboBoxModel<>(jumpTargets));

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
        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        preferencesHandler = new DLCopySwingGUIPreferencesHandler();

        preferencesHandler.addPreference(new MainMenuPreferences(jumpComboBox));

        installerPanels.init(this, runningSystemSource, preferencesHandler,
                exchangePartitionFileSystemsModel);

        upgradeOverwriteListModel = new DefaultListModel<>();
        preferencesHandler.addPreference(new UpgradePreferences(
                upgradeSystemPartitionCheckBox, resetDataPartitionCheckBox,
                keepPrinterSettingsCheckBox, keepNetworkSettingsCheckBox,
                keepFirewallSettingsCheckBox, keepUserSettingsCheckBox,
                reactivateWelcomeCheckBox, removeHiddenFilesCheckBox,
                automaticBackupCheckBox, automaticBackupTextField,
                automaticBackupRemoveCheckBox, upgradeOverwriteListModel));

        resetterPanels.init(this, runningSystemSource.getDeviceName(),
                preferencesHandler, exchangePartitionFileSystemsModel);

        preferencesHandler.load();

        // do not show initial "{0}" placeholder
        String countString = STRINGS.getString("Selection_Count");
        countString = MessageFormat.format(countString, 0, 0);
        upgradeSelectionCountLabel.setText(countString);

        systemSource = installerPanels.getSystemSource();

        upgradeStorageDeviceRenderer
                = new UpgradeStorageDeviceRenderer(runningSystemSource);
        upgradeStorageDeviceList.setCellRenderer(upgradeStorageDeviceRenderer);

        if (commandLineExchangePartitionFileSystem != null) {
            installerPanels.setExchangePartitionFileSystem(
                    commandLineExchangePartitionFileSystem);
        }

        if (commandLineCopyDataPartition != null) {
            installerPanels.setCopyDataPartition(commandLineCopyDataPartition);
        }

        if (commandLineExchangePartitionSize != null) {
            installerPanels.setExplicitExchangeSize(
                    commandLineExchangePartitionSize);
        }

        if (commandLineReactivateWelcome != null) {
            reactivateWelcomeCheckBox.setSelected(commandLineReactivateWelcome);
        }

        // monitor udisks changes
        udisksMonitorThread = new UdisksMonitorThread();

        upgradeResultsTableModel = new ResultsTableModel(
                upgradeResultsTable);
        upgradeResultsTable.setModel(upgradeResultsTableModel);
        TableColumn sizeColumn = upgradeResultsTable.getColumnModel().getColumn(
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

        int jumpIndex = jumpComboBox.getSelectedIndex();
        if (jumpIndex != 0) {
            globalShow("executionPanel");
            switch (jumpIndex) {
                case 1:
                    switchToInstallSelection();
                    break;
                case 2:
                    switchToUpgradeSelection();
                    break;
                case 3:
                    switchToISOSelection();
                    break;
                case 4:
                    switchToResetSelection();
            }
        }
    }

    // post-constructor initialization
    public void init() {
        upgradeOverwriteListModel.addListDataListener(this);
        upgradeOverwriteList.setModel(upgradeOverwriteListModel);

        getRootPane().setDefaultButton(installButton);
        installButton.requestFocusInWindow();

        URL imageURL = getClass().getResource(
                "/ch/fhnw/dlcopy/icons/usbpendrive_unmount.png");
        setIconImage(new ImageIcon(imageURL).getImage());

        upgradeStorageDeviceListModel.addListDataListener(this);
        upgradeStorageDeviceList.setModel(upgradeStorageDeviceListModel);

        isoCreatorPanels.init(this);

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
            upgradeShowHardDisksCheckBox.setVisible(false);
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
                            installerPanels.isShowHardDisksSelected(),
                            storageDeviceListUpdateDialogHandler,
                            installerPanels.getDeviceListModel(),
                            installerPanels.getDeviceList(), this, installLock)
                            .execute();
                    new InstallTransferStorageDeviceAdder(addedPath,
                            installerPanels.isShowHardDisksSelected(),
                            storageDeviceListUpdateDialogHandler,
                            installerPanels.getTransferDeviceListModel(),
                            installerPanels.getTransferDeviceList(), this,
                            installLock).execute();
                    break;

                case UPGRADE_SELECTION:
                    new UpgradeStorageDeviceAdder(runningSystemSource,
                            addedPath,
                            upgradeShowHardDisksCheckBox.isSelected(),
                            storageDeviceListUpdateDialogHandler,
                            upgradeStorageDeviceListModel,
                            upgradeStorageDeviceList, this, upgradeLock)
                            .execute();
                    break;

                case RESET_SELECTION:
                    new ResetStorageDeviceAdder(addedPath,
                            resetterPanels.isShowHardDiskSelected(),
                            storageDeviceListUpdateDialogHandler,
                            resetterPanels.getDeviceListModel(),
                            resetterPanels.getDeviceList(), this, resetLock,
                            resetterPanels.isListSelectionSelected()).execute();
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
    public void showInstallProgress() {
        SwingUtilities.invokeLater(() -> {
            showCard(cardPanel, "installerPanels");
            installerPanels.showProgress();
        });
    }

    @Override
    public void installingDeviceStarted(StorageDevice storageDevice) {

        deviceStarted(storageDevice);

        installerPanels.startedInstallationOnDevice(
                storageDevice, batchCounter, resultsList);
    }

    @Override
    public void showInstallCreatingFileSystems() {
        showInstallIndeterminateProgressBarText("Creating_File_Systems");
    }

    @Override
    public void showInstallFileCopy(FileCopier fileCopier) {
        installerPanels.showFileCopierPanel(fileCopier);
    }

    @Override
    public void showInstallPersistencyCopy(
            Installer installer, String copyScript, String sourcePath) {

        installerPanels.showInstallPersistencyCopy(
                installer, copyScript, sourcePath);
    }

    @Override
    public void setInstallCopyLine(String line) {
        installerPanels.setInstallCopyLine(line);
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
        installerPanels.finishedInstallationOnDevice(
                autoNumberStart, resultsList);
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
    public void showIsoProgressMessage(String message) {
        isoCreatorPanels.showProgressMessage(message);
    }

    @Override
    public void showIsoProgressMessage(String message, int value) {
        isoCreatorPanels.showProgressMessage(message, value);
    }

    @Override
    public void isoCreationFinished(String isoPath, boolean success) {
        isoCreatorPanels.isoCreationFinished(isoPath, success);
        processDone();
    }

    @Override
    public void showResetProgress() {
        // DON'T (!) use invokeLater here or we might run into a timing issue so
        // that after a very quick reset the reset progress panel is still shown
        // because the code below code gets executed much later.
        try {
            SwingUtilities.invokeAndWait(() -> {
                showCard(cardPanel, "resetterPanels");
                resetterPanels.showProgressPanel();
            });
        } catch (InterruptedException | InvocationTargetException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    @Override
    public void resettingDeviceStarted(StorageDevice storageDevice) {
        deviceStarted(storageDevice);
        resetterPanels.startedResetOnDevice(batchCounter, storageDevice);
    }

    @Override
    public void showPrintingDocuments() {
        resetterPanels.showPrintingDocuments();
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
        resetterPanels.showBackup(fileCopier);
    }

    @Override
    public void showResetRestore(FileCopier fileCopier) {
        // TODO: use a different panel to show this file copy progress?
        showResetBackup(fileCopier);
    }

    @Override
    public void showResetFormattingExchangePartition() {
        resetterPanels.showFormattingExchangePartition();
    }

    @Override
    public void showResetFormattingDataPartition() {
        resetterPanels.showResetFormattingDataPartition();
    }

    @Override
    public void showResetRemovingFiles() {
        resetterPanels.showResetRemovingFiles();
    }

    @Override
    public void resettingFinished(boolean success) {

        setTitle(STRINGS.getString("DLCopySwingGUI.title"));

        if (resetterPanels.isListSelectionSelected()) {
            if (success) {
                doneLabel.setText(STRINGS.getString("Reset_Done"));
                showCard(cardPanel, "donePanel");
                processDone();
            } else {
                previousButton.setEnabled(true);
                switchToResetSelection();
            }

        } else {
            resetterPanels.showNoMediaPanel();
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

    public SystemSource getSystemSource() {
        return systemSource;
    }

    public void setSystemSource(SystemSource systemSource) {

        this.systemSource = systemSource;

        // update main menu button
        if (StorageDevice.Type.USBFlashDrive == systemSource.getDeviceType()) {
            Icon usb2usbIcon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/dlcopy/icons/usb2usb.png"));
            installButton.setIcon(usb2usbIcon);
        }

        // update upgrader GUI
        String sizeString = LernstickFileTools.getDataVolumeString(
                systemSource.getSystemSize(), 1);
        String text = STRINGS.getString("Select_Upgrade_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        upgradeSelectionHeaderLabel.setText(text);

        // update ISO creator GUI
        isoCreatorPanels.setSystemSource(systemSource);
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

    public static void showCard(Container container, String cardName) {
        LOGGER.log(Level.FINEST, "\n"
                + "    thread: {0}\n"
                + "    container : {1}\n"
                + "    card: {2}",
                new Object[]{Thread.currentThread().getName(),
                    container.getName(), cardName});
        CardLayout cardLayout = (CardLayout) container.getLayout();
        cardLayout.show(container, cardName);
    }

    public static DataPartitionMode getDataPartitionMode(JComboBox comboBox) {
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

    public static long getMaxStorageDeviceSize(ListModel listModel) {
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

    /**
     * returns the exchange partition size slider
     *
     * @return the exchange partition size slider
     */
    public JSlider getExchangePartitionSizeSlider() {
        return installerPanels.getExchangePartitionSizeSlider();
    }

    /**
     * must be called whenever the install storage device list changes to
     * execute some updates (e.g. maximum storage device size) and some sanity
     * checks
     */
    public void installStorageDeviceListChanged() {
        installerPanels.installStorageDeviceListChanged(instantInstallation);
    }

    public void updateInstallSelectionCountAndExchangeInfo() {
        installerPanels.updateInstallSelectionCountAndExchangeInfo();
    }

    public void installTransferStorageDeviceListChanged() {
        installerPanels.installTransferStorageDeviceListChanged();
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
        resetterPanels.storageDeviceListChanged();
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

        resetterPanels.updateSelectionCountAndNextButton();
    }

    public void enableNextButton() {
        if (nextButton.isShowing()) {
            nextButton.setEnabled(true);
            getRootPane().setDefaultButton(nextButton);
            if (previousButton.hasFocus()) {
                nextButton.requestFocusInWindow();
            }
        }
    }

    public void disableNextButton() {
        if (nextButton.hasFocus()) {
            previousButton.requestFocusInWindow();
        }
        getRootPane().setDefaultButton(previousButton);
        nextButton.setEnabled(false);
    }

    public void resetStorageDevices(List<StorageDevice> deviceList) {
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        state = State.RESET;

        batchCounter = 0;
        resultsList = new ArrayList<>();

        String exchangePartitionFileSystem
                = resetterPanels.getExchangePartitionFileSystem();

        // TODO: make dataPartitionFileSystem configurable in resetter GUI
        String dataPartitionFileSystem = "ext4";

        new Resetter(this, deviceList, runningSystemSource.getDeviceName(),
                resetterPanels.isPrintDocumentsSelected(),
                resetterPanels.getPrintingDirectories(),
                resetterPanels.isRecursiveDirectoryScanningEnabled(),
                resetterPanels.isPrintOdtSelected(),
                resetterPanels.isPrintOdsSelected(),
                resetterPanels.isPrintOdpSelected(),
                resetterPanels.isPrintPdfSelected(),
                resetterPanels.isPrintDocSelected(),
                resetterPanels.isPrintDocxSelected(),
                resetterPanels.isPrintXlsSelected(),
                resetterPanels.isPrintXlsxSelected(),
                resetterPanels.isPrintPptSelected(),
                resetterPanels.isPrintPptxSelected(),
                resetterPanels.getAutoPrintMode(),
                resetterPanels.getNumberOfPrintCopies(),
                resetterPanels.isDuplexPrintingSelected(),
                resetterPanels.isBackupSelected(),
                resetterPanels.getBackupSource(),
                resetterPanels.getBackupDestination(),
                resetterPanels.getBackupDestinationSubdirectoryEntries(),
                resetterPanels.isFormatExchangePartitionSelected(),
                exchangePartitionFileSystem,
                resetterPanels.isKeepExchangePartitionLabelSelected(),
                resetterPanels.getNewExchangePartitionLabel(),
                resetterPanels.isDeleteFromDataPartitionSelected(),
                resetterPanels.isFormatDataPartitionSelected(),
                dataPartitionFileSystem,
                resetterPanels.isDeleteHomeDirectorySelected(),
                resetterPanels.isDeleteSystemFilesSelected(),
                resetterPanels.isRestoreDataSelected(),
                resetterPanels.getRestoreEntries(), resetLock)
                .execute();
    }

    public void storageDeviceListChanged(DefaultListModel<StorageDevice> model,
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

    public boolean isUnmountedPersistenceAvailable()
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

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        exchangeButtonGroup = new javax.swing.ButtonGroup();
        upgradeSelectionModeButtonGroup = new javax.swing.ButtonGroup();
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
        installerPanels = new ch.fhnw.dlcopy.gui.swing.InstallerPanels();
        resetterPanels = new ch.fhnw.dlcopy.gui.swing.ResetterPanels();
        isoCreatorPanels = new ch.fhnw.dlcopy.gui.swing.IsoCreatorPanels();
        upgradeInfoPanel = new javax.swing.JPanel();
        upgradeInfoLabel = new javax.swing.JLabel();
        upgradeSelectionTabbedPane = new javax.swing.JTabbedPane();
        upgradeSelectionPanel = new javax.swing.JPanel();
        upgradeListModeRadioButton = new javax.swing.JRadioButton();
        upgradeShowHardDisksCheckBox = new javax.swing.JCheckBox();
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
        donePanel = new javax.swing.JPanel();
        doneLabel = new javax.swing.JLabel();
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
        resetButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                resetButtonFocusGained(evt);
            }
        });
        resetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetButtonActionPerformed(evt);
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
        cardPanel.add(installerPanels, "installerPanels");
        cardPanel.add(resetterPanels, "resetterPanels");
        cardPanel.add(isoCreatorPanels, "isoCreatorPanels");

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

        upgradeShowHardDisksCheckBox.setFont(upgradeShowHardDisksCheckBox.getFont().deriveFont(upgradeShowHardDisksCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeShowHardDisksCheckBox.getFont().getSize()-1));
        upgradeShowHardDisksCheckBox.setText(bundle.getString("DLCopySwingGUI.upgradeShowHardDisksCheckBox.text")); // NOI18N
        upgradeShowHardDisksCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                upgradeShowHardDisksCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 30, 0, 0);
        upgradeSelectionPanel.add(upgradeShowHardDisksCheckBox, gridBagConstraints);

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

        donePanel.setLayout(new java.awt.GridBagLayout());

        doneLabel.setFont(doneLabel.getFont().deriveFont(doneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        doneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        doneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usbpendrive_unmount_tux.png"))); // NOI18N
        doneLabel.setText(bundle.getString("Installation_Done_Message_From_Removable_Boot_Device")); // NOI18N
        doneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        doneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        donePanel.add(doneLabel, new java.awt.GridBagConstraints());

        cardPanel.add(donePanel, "donePanel");

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
                if (isoCreatorPanels.isSystemMediumSelected()
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
            showCard(cardPanel, "isoCreatorPanels");
            isoCreatorPanels.showProgressPanel();
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);

            if (isoCreatorPanels.isDataPartitionSelected()) {
                new SquashFSCreator(this, runningSystemSource,
                        isoCreatorPanels.getTemporaryDirectory(),
                        isoCreatorPanels.isShowNotUsedDialogSelected(),
                        isoCreatorPanels.isAutoStartInstallerSelected())
                        .execute();
            } else {
                new IsoCreator(this, runningSystemSource,
                        isoCreatorPanels.isBootMediumSelected(),
                        isoCreatorPanels.getTemporaryDirectory(),
                        isoCreatorPanels.getDataPartitionMode(),
                        isoCreatorPanels.isShowNotUsedDialogSelected(),
                        isoCreatorPanels.isAutoStartInstallerSelected(),
                        isoCreatorPanels.getIsoLabel())
                        .execute();
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

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        exitProgram();
    }//GEN-LAST:event_formWindowClosing

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

    private void installButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_installButtonFocusGained
        getRootPane().setDefaultButton(installButton);
    }//GEN-LAST:event_installButtonFocusGained

    private void toISOButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_toISOButtonFocusGained
        getRootPane().setDefaultButton(toISOButton);
    }//GEN-LAST:event_toISOButtonFocusGained

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

private void upgradeShowHardDisksCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeShowHardDisksCheckBoxItemStateChanged
    new UpgradeStorageDeviceListUpdater(runningSystemSource, this,
            upgradeStorageDeviceList, upgradeStorageDeviceListModel,
            upgradeShowHardDisksCheckBox.isSelected()).execute();
}//GEN-LAST:event_upgradeShowHardDisksCheckBoxItemStateChanged

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

    private void upgradeListModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeListModeRadioButtonActionPerformed
        showCard(upgradeSelectionCardPanel, "upgradeSelectionDeviceListPanel");
        new UpgradeStorageDeviceListUpdater(runningSystemSource, this,
                upgradeStorageDeviceList, upgradeStorageDeviceListModel,
                upgradeShowHardDisksCheckBox.isSelected()).execute();
        upgradeShowHardDisksCheckBox.setEnabled(
                upgradeListModeRadioButton.isSelected());
    }//GEN-LAST:event_upgradeListModeRadioButtonActionPerformed

    private void upgradeAutomaticRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeAutomaticRadioButtonActionPerformed
        showCard(upgradeSelectionCardPanel, "upgradeNoMediaPanel");
        nextButton.setEnabled(false);
        upgradeShowHardDisksCheckBox.setEnabled(
                !upgradeAutomaticRadioButton.isSelected());
    }//GEN-LAST:event_upgradeAutomaticRadioButtonActionPerformed

    private void upgradeSelectionTabbedPaneComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_upgradeSelectionTabbedPaneComponentShown
        if (upgradeListModeRadioButton.isSelected()) {
            new UpgradeStorageDeviceListUpdater(runningSystemSource, this,
                    upgradeStorageDeviceList, upgradeStorageDeviceListModel,
                    upgradeShowHardDisksCheckBox.isSelected()).execute();
        }
    }//GEN-LAST:event_upgradeSelectionTabbedPaneComponentShown

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

    private void playNotifySound() {
        try {
            Clip clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.open(AudioSystem.getAudioInputStream(
                    getClass().getResource("/ch/fhnw/dlcopy/KDE_Notify.wav")));
            clip.start();
        } catch (UnsupportedAudioFileException | IOException
                | LineUnavailableException ex) {
            LOGGER.log(Level.INFO, "", ex);
        }
    }

    private void switchToUpgradeSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.UPGRADE_SELECTION;
        showCard(cardPanel, "upgradeSelectionTabbedPane");
        enableNextButton();
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

        int[] selectedIndices
                = resetterPanels.getDeviceList().getSelectedIndices();
        List<StorageDevice> deviceList = new ArrayList<>();
        for (int i : selectedIndices) {
            deviceList.add(resetterPanels.getDeviceListModel().get(i));
        }

        resetStorageDevices(deviceList);
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

        } else {
            LOGGER.log(Level.WARNING, "unknown source: {0}", source);
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
                    listModels.add(installerPanels.getDeviceListModel());
                    listModels.add(
                            installerPanels.getTransferDeviceListModel());
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
                    listModels.add(resetterPanels.getDeviceListModel());
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

    private void exitProgram() {
        installerPanels.saveExplicitExchangeSize();
        preferencesHandler.save();

        runningSystemSource.unmountTmpPartitions();
        installerPanels.unmountIsoSystemSource();

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
        new InstallStorageDeviceListUpdater(this,
                installerPanels.getDeviceList(),
                installerPanels.getDeviceListModel(),
                installerPanels.isShowHardDisksSelected(),
                runningSystemSource.getDeviceName()).execute();

        // update transfer storage device list
        new InstallTransferStorageDeviceListUpdater(this,
                installerPanels.getTransferDeviceList(),
                installerPanels.getTransferDeviceListModel(),
                installerPanels.isShowHardDisksSelected(),
                runningSystemSource.getDeviceName()).execute();

        previousButton.setEnabled(true);
        showCard(cardPanel, "installerPanels");
        installerPanels.showSelectionPanel();
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

    private void showInstallIndeterminateProgressBarText(final String text) {
        installerPanels.showIndeterminateProgressBarText(text);
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
        // add "in progress" entry to results table
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

    public void checkAndInstallSelection(boolean interactive)
            throws IOException, DBusException {

        if (!installerPanels.checkSelection(interactive)) {
            return;
        }

        // save settings so that they don't get lost when the installer crashes
        installerPanels.saveExplicitExchangeSize();
        preferencesHandler.save();

        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);
        state = State.INSTALLATION;

        // let's start...
        resultsList = new ArrayList<>();
        batchCounter = 0;

        new Installer(systemSource, installerPanels.getSelectedDevices(),
                installerPanels.getExchangePartitionLabel(),
                installerPanels.getExchangePartitionFileSystem(),
                installerPanels.getDataPartitionFileSystem(),
                digestCache, this, installerPanels.getExchangePartitionSize(),
                installerPanels.isCopyExchangeSelected(),
                installerPanels.getAutoNumberPattern(),
                installerPanels.getAutoNumber(),
                installerPanels.getAutoIncrement(),
                installerPanels.getAutoMinDigits(),
                installerPanels.isCopyDataSelected(),
                installerPanels.getDataPartitionMode(),
                installerPanels.getTransferDevice(),
                installerPanels.isTransferExchangeSelected(),
                installerPanels.isTransferHomeSelected(),
                installerPanels.isTransferNetworkSelected(),
                installerPanels.isTransferPrinterSelected(),
                installerPanels.isTransferFirewallSelected(),
                installerPanels.isCheckCopiesSelected(),
                installLock).execute();

        updateTableActionListener
                = new UpdateChangingDurationsTableActionListener(
                        installerPanels.getResultsTableModel());
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

        // TODO: make data partition file system configurable in Upgrader GUI
        String dataPartitionFileSystem = "ext4";

        // TODO: don't use exchange partition settings from installerPanels
        // but make it configurable in Upgrader GUI
        new Upgrader(runningSystemSource, deviceList,
                installerPanels.getExchangePartitionLabel(),
                installerPanels.getExchangePartitionFileSystem(),
                dataPartitionFileSystem, digestCache, this, this,
                repartitionStrategy, exchangeMB,
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

    private void switchToISOInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        showCard(cardPanel, "isoCreatorPanels");
        isoCreatorPanels.showInfoPanel();
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.ISO_INFORMATION;
    }

    private void switchToISOSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        showCard(cardPanel, "isoCreatorPanels");
        isoCreatorPanels.showSelectionPanel();
        isoCreatorPanels.checkFreeSpace();
        enableNextButton();
        state = State.ISO_SELECTION;
    }

    private void switchToInstallInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        executionLabel.setText(STRINGS.getString("Installation_Label"));
        showCard(cardPanel, "installerPanels");
        installerPanels.showInfoPanel();
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
        showCard(cardPanel, "resetterPanels");
        resetterPanels.showInfo();
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.RESET_INFORMATION;
    }

    private void switchToResetSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.RESET_SELECTION;
        showCard(cardPanel, "resetterPanels");
        resetterPanels.showSelection();
        enableNextButton();
    }

    private void globalShow(String componentName) {
        Container contentPane = getContentPane();
        CardLayout globalCardLayout = (CardLayout) contentPane.getLayout();
        globalCardLayout.show(contentPane, componentName);
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
    private javax.swing.JButton automaticBackupButton;
    private javax.swing.JCheckBox automaticBackupCheckBox;
    private javax.swing.JLabel automaticBackupLabel;
    private javax.swing.JCheckBox automaticBackupRemoveCheckBox;
    private javax.swing.JTextField automaticBackupTextField;
    private javax.swing.JPanel backupDestinationPanel;
    private javax.swing.JPanel buttonGridPanel;
    private javax.swing.JPanel cardPanel;
    private javax.swing.JLabel choiceLabel;
    private javax.swing.JPanel choicePanel;
    private javax.swing.JLabel currentlyUpgradedDeviceLabel;
    private javax.swing.JLabel doneLabel;
    private javax.swing.JPanel donePanel;
    private javax.swing.ButtonGroup exchangeButtonGroup;
    private javax.swing.JLabel executionLabel;
    private javax.swing.JPanel executionPanel;
    private javax.swing.JSeparator executionPanelSeparator;
    private javax.swing.JLabel infoStepLabel;
    private javax.swing.JButton installButton;
    private javax.swing.JLabel installLabel;
    private ch.fhnw.dlcopy.gui.swing.InstallerPanels installerPanels;
    private ch.fhnw.dlcopy.gui.swing.IsoCreatorPanels isoCreatorPanels;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator4;
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
    private javax.swing.JCheckBox reactivateWelcomeCheckBox;
    private javax.swing.JRadioButton removeExchangeRadioButton;
    private javax.swing.JCheckBox removeHiddenFilesCheckBox;
    private javax.swing.JPanel repartitionExchangeOptionsPanel;
    private javax.swing.JButton resetButton;
    private javax.swing.JCheckBox resetDataPartitionCheckBox;
    private ch.fhnw.dlcopy.gui.swing.ResetterPanels resetterPanels;
    private javax.swing.JLabel resizeExchangeLabel;
    private javax.swing.JRadioButton resizeExchangeRadioButton;
    private javax.swing.JTextField resizeExchangeTextField;
    private javax.swing.JLabel resultsInfoLabel;
    private javax.swing.JPanel resultsPanel;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JPanel resultsTitledPanel;
    private javax.swing.JLabel selectionLabel;
    private javax.swing.JButton sortAscendingButton;
    private javax.swing.JButton sortDescendingButton;
    private javax.swing.JLabel stepsLabel;
    private javax.swing.JPanel stepsPanel;
    private javax.swing.JButton toISOButton;
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
    private javax.swing.JCheckBox upgradeShowHardDisksCheckBox;
    private javax.swing.JList<StorageDevice> upgradeStorageDeviceList;
    private javax.swing.JScrollPane upgradeStorageDeviceListScrollPane;
    private javax.swing.JCheckBox upgradeSystemPartitionCheckBox;
    private javax.swing.JTabbedPane upgradeTabbedPane;
    // End of variables declaration//GEN-END:variables
}
