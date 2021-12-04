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
import ch.fhnw.dlcopy.RepartitionStrategy;
import ch.fhnw.dlcopy.Resetter;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.Upgrader;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.swing.preferences.DLCopySwingGUIPreferencesHandler;
import ch.fhnw.dlcopy.gui.swing.preferences.MainMenuPreferences;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.jbackpack.RdiffBackupRestore;
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
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.table.TableColumn;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Installs Debian Live to a USB flash drive
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DLCopySwingGUI extends JFrame
        implements DLCopyGUI, PropertyChangeListener {

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
    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";

    private final DateFormat timeFormat;

    private SystemSource runningSystemSource;
    private SystemSource systemSource = null;

    private boolean persistenceBoot;

    private DebianLiveDistribution debianLiveDistribution;

    private final UdisksMonitorThread udisksMonitorThread;
    private RdiffBackupRestore rdiffBackupRestore;

    private final ResultsTableModel resultsTableModel;
    private UpdateChangingDurationsTableActionListener updateTableActionListener;
    private Timer tableUpdateTimer;

    private final static Pattern ADDED_PATTERN = Pattern.compile(
            ".*: Added (/org/freedesktop/UDisks2/block_devices/.*)");
    private final static Pattern REMOVED_PATTERN = Pattern.compile(
            ".*: Removed (/org/freedesktop/UDisks2/block_devices/.*)");

    private final StorageDeviceListUpdateDialogHandler storageDeviceListUpdateDialogHandler
            = new StorageDeviceListUpdateDialogHandler(this);

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

        try {
            persistenceBoot = DLCopy.isBootPersistent();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "can't determine persistence mode", ex);
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
        upgraderPanels.init(this, runningSystemSource, preferencesHandler);
        resetterPanels.init(this, runningSystemSource.getDeviceName(),
                preferencesHandler, exchangePartitionFileSystemsModel);

        preferencesHandler.load();

        systemSource = installerPanels.getSystemSource();

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
            upgraderPanels.setReactivateWelcomeCheckBoxSelected(
                    commandLineReactivateWelcome);
        }

        // monitor udisks changes
        udisksMonitorThread = new UdisksMonitorThread();

        resultsTableModel = new ResultsTableModel(resultsTable);
        resultsTable.setModel(resultsTableModel);
        TableColumn sizeColumn = resultsTable.getColumnModel().getColumn(
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

        getRootPane().setDefaultButton(installButton);
        installButton.requestFocusInWindow();

        URL imageURL = getClass().getResource(
                "/ch/fhnw/dlcopy/icons/usbpendrive_unmount.png");
        setIconImage(new ImageIcon(imageURL).getImage());

        isoCreatorPanels.init(this);

        if (autoUpgrade) {
            globalShow("executionPanel");
            switchToUpgradeSelection();
            upgraderPanels.clickAutomaticRadioButton();
        }

        if (isolatedAutoUpgrade) {
            // maximize frame
            setExtendedState(Frame.MAXIMIZED_BOTH);

            // only leave the upgrade parts visible
            stepsPanel.setVisible(false);
            executionPanelSeparator.setVisible(false);
            prevNextButtonPanel.setVisible(false);

            upgraderPanels.setGuiToIsolatedAutoUpgrade();

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
            addStorageDevice(addedPath);

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
        installerPanels.showIndeterminateProgressBarText(
                "Creating_File_Systems");
    }

    @Override
    public void showInstallOverwritingDataPartitionWithRandomData(
            long done, long size) {
        installerPanels.showOverwriteRandomProgressBar(done, size);
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
        installerPanels.showIndeterminateProgressBarText(
                "Unmounting_File_Systems");
    }

    @Override
    public void showInstallWritingBootSector() {
        installerPanels.showIndeterminateProgressBarText(
                "Writing_Boot_Sector");
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

        upgraderPanels.startedUpgradeOnDevice(
                storageDevice, batchCounter, resultsList);
    }

    @Override
    public void showUpgradeBackup() {
        upgraderPanels.showUpgradeBackup();
    }

    @Override
    public void setUpgradeBackupProgress(String progressInfo) {
        upgraderPanels.setUpgradeBackupProgress(progressInfo);
    }

    @Override
    public void setUpgradeBackupFilename(String filename) {
        upgraderPanels.setUpgradeBackupFilename(filename);
    }

    @Override
    public void setUpgradeBackupDuration(long time) {
        upgraderPanels.setUpgradeBackupDuration(time);
    }

    @Override
    public void showUpgradeBackupExchangePartition(FileCopier fileCopier) {
        upgraderPanels.showUpgradeBackupExchangePartition(fileCopier);
    }

    @Override
    public void showUpgradeRestoreInit() {
        upgraderPanels.showUpgradeRestoreInit();
    }

    @Override
    public void showUpgradeRestoreRunning() {
        upgraderPanels.showUpgradeRestoreRunning();
    }

    @Override
    public void showUpgradeRestoreExchangePartition(FileCopier fileCopier) {
        upgraderPanels.showUpgradeRestoreExchangePartition(fileCopier);
    }

    @Override
    public void showUpgradeChangingPartitionSizes() {
        upgraderPanels.showUpgradeChangingPartitionSizes();
    }

    @Override
    public void showUpgradeDataPartitionReset() {
        upgraderPanels.showUpgradeDataPartitionReset();
    }

    @Override
    public void showUpgradeCreatingFileSystems() {
        upgraderPanels.showUpgradeIndeterminateProgressBarText(
                "Creating_File_Systems");
    }

    @Override
    public void showUpgradeFileCopy(FileCopier fileCopier) {
        upgraderPanels.showUpgradeFileCopy(fileCopier);
    }

    @Override
    public void showUpgradeUnmounting() {
        upgraderPanels.showUpgradeIndeterminateProgressBarText(
                "Unmounting_File_Systems");
    }

    @Override
    public void showUpgradeSystemPartitionReset() {
        upgraderPanels.showUpgradeSystemPartitionReset();
    }

    @Override
    public void showUpgradeWritingBootSector() {
        upgraderPanels.showUpgradeIndeterminateProgressBarText(
                "Writing_Boot_Sector");
    }

    @Override
    public void upgradingDeviceFinished(String errorMessage) {
        // upgrade final report
        deviceFinished(errorMessage);

        // update current report
        upgraderPanels.finishedUpgradeOnDevice(resultsList);
    }

    @Override
    public void upgradingListFinished() {
        if (instantUpgrade) {
            instantUpgrade = false;
        }
        if (upgraderPanels.isListModeSelected()) {
            batchFinished(
                    "Upgrade_Done_From_Non_Removable_Device",
                    "Upgrade_Done_From_Removable_Device",
                    "Upgrade_Report");
        } else {
            if (isolatedAutoUpgrade) {
                upgraderPanels.isolatedAutoUpgradeDone();
            }
            showCard(cardPanel, "upgraderPanels");
            upgraderPanels.showSelection();
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

        // update panels
        upgraderPanels.setSystemSource(systemSource);
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

    public static void setLabelTextonEDT(JLabel label, String text) {
        SwingUtilities.invokeLater(() -> label.setText(text));
    }

    public static void setProgressBarStringOnEDT(
            JProgressBar progressBar, String key) {

        SwingUtilities.invokeLater(
                () -> progressBar.setString(STRINGS.getString(key)));
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

        upgraderPanels.upgradeStorageDeviceListChanged(instantUpgrade);

        // run instant upgrade if needed
        if (instantUpgrade) {
            upgraderPanels.upgradeSelectedStorageDevices(instantUpgrade);
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
        upgraderPanels.updateUpgradeSelectionCountAndNextButton();
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
        if (DLCopy.isMountedReadWrite(
                "/dev/" + dataPartition.getDeviceAndNumber())) {
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

    public void upgradeStorageDevices(List<StorageDevice> deviceList) {
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, true);
        previousButton.setEnabled(false);
        nextButton.setEnabled(false);

        // let's start...
        state = State.UPGRADE;
        showCard(cardPanel, "upgraderPanels");
        upgraderPanels.showProgress();
        resultsList = new ArrayList<>();
        batchCounter = 0;

        boolean deleteBackup = upgraderPanels.isAutomaticUpgradeSelected()
                && upgraderPanels.isAutomaticBackupDeleteSelected();

        List<String> overWriteList = new ArrayList<>();
        DefaultListModel<String> listModel
                = upgraderPanels.getUpgradeOverwriteListModel();
        for (int i = 0, size = listModel.size(); i < size; i++) {
            overWriteList.add(listModel.get(i));
        }

        RepartitionStrategy repartitionStrategy
                = upgraderPanels.getRepartitionStrategy();

        int exchangeMB = upgraderPanels.getRepartitionExchangeSize();

        // TODO: make data partition file system configurable in Upgrader GUI
        String dataPartitionFileSystem = "ext4";

        // TODO: don't use exchange partition settings from installerPanels
        // but make it configurable in Upgrader GUI
        new Upgrader(runningSystemSource, deviceList,
                installerPanels.getExchangePartitionLabel(),
                installerPanels.getExchangePartitionFileSystem(),
                dataPartitionFileSystem, digestCache, this, this,
                repartitionStrategy, exchangeMB,
                upgraderPanels.isAutomaticBackupSelected(),
                upgraderPanels.getBackupDestination(), deleteBackup,
                upgraderPanels.isUpgradeSystemPartitionSelected(),
                upgraderPanels.isResetDataPartitionSelected(),
                upgraderPanels.isKeepPrinterSettingsSelected(),
                upgraderPanels.isKeepNetworkSettingsSelected(),
                upgraderPanels.isKeepFirewallSettingsSelected(),
                upgraderPanels.isKeepUserSettingsSelected(),
                upgraderPanels.isReactivateWelcomeSelected(),
                upgraderPanels.isDeleteHiddenFilesSelected(), overWriteList,
                DLCopy.getEnlargedSystemSize(
                        runningSystemSource.getSystemSize()), upgradeLock)
                .execute();

        updateTableActionListener
                = new UpdateChangingDurationsTableActionListener(
                        upgraderPanels.getResultsTableModel());
        tableUpdateTimer = new Timer(1000, updateTableActionListener);
        tableUpdateTimer.setInitialDelay(0);
        tableUpdateTimer.start();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

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
        upgraderPanels = new ch.fhnw.dlcopy.gui.swing.UpgraderPanels();
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
        cardPanel.add(upgraderPanels, "upgraderPanels");

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

            case INSTALL_SELECTION:
                install();
                break;

            case UPGRADE_INFORMATION:
                switchToUpgradeSelection();
                break;

            case UPGRADE_SELECTION:
                upgraderPanels.upgradeSelectedStorageDevices(instantUpgrade);
                break;

            case ISO_INFORMATION:
                switchToISOSelection();
                break;

            case ISO_SELECTION:
                createISO();
                break;

            case RESET_INFORMATION:
                switchToResetSelection();
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

    private void addStorageDevice(String addedPath) {
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
                        upgraderPanels.isShowHardDiskSelected(),
                        storageDeviceListUpdateDialogHandler,
                        upgraderPanels.getDeviceListModel(),
                        upgraderPanels.getDeviceList(), this, upgradeLock)
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
                        upgraderPanels.promptForNextIsolatedAutoUpgradeMedium();
                    }
                    listModels.add(upgraderPanels.getDeviceListModel());
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

    private void resetNextButton() {
        nextButton.setIcon(new ImageIcon(
                getClass().getResource("/ch/fhnw/dlcopy/icons/next.png")));
        nextButton.setText(
                STRINGS.getString("DLCopySwingGUI.nextButton.text"));
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
                installerPanels.isPersonalEncryptionSelected(),
                installerPanels.getPersonalEncryptionPassword(),
                installerPanels.isSecondaryEncryptionSelected(),
                installerPanels.getSecondaryEncryptionPassword(),
                installerPanels.isOverwriteDataPartitionWithRandomDataSelected(),
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

    private void install() {
        try {
            checkAndInstallSelection(true);
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.SEVERE,
                    "checking the selected usb flash drive failed", ex);
        }
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

    private void createISO() {
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
            new SquashFSCreatorSwingWorker(this, runningSystemSource,
                    isoCreatorPanels.getTemporaryDirectory(),
                    isoCreatorPanels.isShowNotUsedDialogSelected(),
                    isoCreatorPanels.isAutoStartInstallerSelected())
                    .execute();
        } else {
            new IsoCreatorSwingWorker(this, runningSystemSource,
                    isoCreatorPanels.isBootMediumSelected(),
                    isoCreatorPanels.getTemporaryDirectory(),
                    isoCreatorPanels.getDataPartitionMode(),
                    isoCreatorPanels.isShowNotUsedDialogSelected(),
                    isoCreatorPanels.isAutoStartInstallerSelected(),
                    isoCreatorPanels.getIsoLabel())
                    .execute();
        }
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

    private void switchToUpgradeInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        setLabelHighlighted(executionLabel, false);
        executionLabel.setText(STRINGS.getString("Upgrade_Label"));
        showCard(cardPanel, "upgraderPanels");
        upgraderPanels.showInfo();
        enableNextButton();
        nextButton.requestFocusInWindow();
        state = State.UPGRADE_INFORMATION;
    }

    private void switchToUpgradeSelection() {
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);
        state = State.UPGRADE_SELECTION;
        showCard(cardPanel, "upgraderPanels");
        upgraderPanels.showSelection();
        enableNextButton();
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
    private javax.swing.JPanel buttonGridPanel;
    private javax.swing.JPanel cardPanel;
    private javax.swing.JLabel choiceLabel;
    private javax.swing.JPanel choicePanel;
    private javax.swing.JLabel doneLabel;
    private javax.swing.JPanel donePanel;
    private javax.swing.JLabel executionLabel;
    private javax.swing.JPanel executionPanel;
    private javax.swing.JSeparator executionPanelSeparator;
    private javax.swing.JLabel infoStepLabel;
    private javax.swing.JButton installButton;
    private javax.swing.JLabel installLabel;
    private ch.fhnw.dlcopy.gui.swing.InstallerPanels installerPanels;
    private ch.fhnw.dlcopy.gui.swing.IsoCreatorPanels isoCreatorPanels;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JComboBox<String> jumpComboBox;
    private javax.swing.JLabel jumpLabel1;
    private javax.swing.JLabel jumpLabel2;
    private javax.swing.JButton nextButton;
    private javax.swing.JPanel northEastPanel;
    private javax.swing.JPanel northWestPanel;
    private javax.swing.JPanel prevNextButtonPanel;
    private javax.swing.JButton previousButton;
    private javax.swing.JButton resetButton;
    private ch.fhnw.dlcopy.gui.swing.ResetterPanels resetterPanels;
    private javax.swing.JLabel resultsInfoLabel;
    private javax.swing.JPanel resultsPanel;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JPanel resultsTitledPanel;
    private javax.swing.JLabel selectionLabel;
    private javax.swing.JLabel stepsLabel;
    private javax.swing.JPanel stepsPanel;
    private javax.swing.JButton toISOButton;
    private javax.swing.JButton upgradeButton;
    private javax.swing.JLabel upgradeLabel;
    private ch.fhnw.dlcopy.gui.swing.UpgraderPanels upgraderPanels;
    // End of variables declaration//GEN-END:variables
}
