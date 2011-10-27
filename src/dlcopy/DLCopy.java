/*
 * DLCopy.java
 *
 * Created on 16. April 2008, 09:14
 */
package dlcopy;

import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import dlcopy.tools.FileTools;
import dlcopy.tools.ModalDialogHandler;
import dlcopy.tools.ProcessExecutor;
import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Installs Debian Live to a USB flash drive
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DLCopy extends JFrame
        implements DocumentListener, PropertyChangeListener {

    /**
     * 1024 * 1024
     */
    public static final int MEGA = 1048576;
    /**
     * all the translateable STRINGS of the program
     */
    public static final ResourceBundle STRINGS =
            ResourceBundle.getBundle("dlcopy/Strings");
    /**
     * the minimal size for a data partition (200 MByte)
     */
    public final static long MINIMUM_PARTITION_SIZE = 200 * MEGA;

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
         * the system is so small that only a system and persistent partition
         * can be created
         */
        PERSISTENT,
        /**
         * the system is large enough to create all partition scenarios
         */
        EXCHANGE
    }
    private final static ProcessExecutor processExecutor =
            new ProcessExecutor();
    private final static FileFilter NO_HIDDEN_FILES_FILTER =
            NoHiddenFilesSwingFileFilter.getInstance();
    private final static NumberFormat numberFormat = NumberFormat.getInstance();
    private final static Logger LOGGER =
            Logger.getLogger(DLCopy.class.getName());
    private final static long MINIMUM_FREE_MEMORY = 300 * MEGA;
    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";
    private final DefaultListModel installStorageDeviceListModel =
            new DefaultListModel();
    private final DefaultListModel upgradeStorageDeviceListModel =
            new DefaultListModel();
    private final InstallStorageDeviceRenderer installStorageDeviceRenderer;
    private final UpgradeStorageDeviceRenderer upgradeStorageDeviceRenderer;
    private long systemSize = -1;
    private long systemSizeEnlarged = -1;
    // some things to change when debugging...
    // SIZE_FACTOR is >1 so that we leave some space for updates, etc...
    private final float SIZE_FACTOR = 1.1f;
    private final String MOUNT_POINT = "/mnt/usbstick";
    private final String DEBIAN_LIVE_SYSTEM_PATH = "/live/image";
    private final String SYSLINUX_MBR_PATH = "/usr/lib/syslinux/mbr.bin";

    private enum State {

        INSTALL_INFORMATION, INSTALL_SELECTION, INSTALLATION,
        UPGRADE_INFORMATION, UPGRADE_SELECTION, UPGRADE,
        ISO_INFORMATION, ISO_SELECTION, ISO_INSTALLATION
    }
    private State state = State.INSTALL_INFORMATION;

    private enum IsoStep {

        MKSQUASHFS, GENISOIMAGE
    }
    private String bootDevice;
    private boolean bootDeviceIsUSB;
    private boolean textFieldTriggeredSliderChange;

    private enum DebianLiveDistribution {

        Default, lernstick
    }
    private DebianLiveDistribution debianLiveDistribution;
    private final String systemPartitionLabel;
    private List<UsbStorageDevice> debugUsbStorageDevices;
    private String persistencyUDI;
    private String persistencyDevice;
    private boolean persistencyBoot;
    private long persistencySize;
    private final Pattern rsyncPattern =
            Pattern.compile(".*to-check=(.*)/(.*)\\)");
    private final Pattern mksquashfsPattern =
            Pattern.compile("\\[.* (.*)/(.*) .*");
    private final Pattern genisoimagePattern =
            Pattern.compile("(.*)\\..*%.*");
    private String exchangeSourcePartition;
    private String exchangeSourcePartitionUDI;
    private DBusConnection dbusSystemConnection;
    private UdisksMonitorThread udisksMonitorThread;
    private DefaultListModel separateFileSystemsListModel;
    private DefaultListModel upgradeOverwriteListModel;

    /** Creates new form DLCopy
     * @param arguments the command line arguments
     */
    public DLCopy(String[] arguments) {
        /**
         * set up logging
         */
        Logger globalLogger = Logger.getLogger("dlcopy");
        globalLogger.setLevel(Level.ALL);
        Logger fileCopierLogger = Logger.getLogger("ch.fhnw.filecopier");
        fileCopierLogger.setLevel(Level.ALL);
        // log to console
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.ALL);
        globalLogger.addHandler(consoleHandler);
        fileCopierLogger.addHandler(consoleHandler);
        // also log into a rotating temporaty file of max 5 MB
        try {
            FileHandler fileHandler =
                    new FileHandler("%t/DebianLiveCopy", 5000000, 2, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            globalLogger.addHandler(fileHandler);
            fileCopierLogger.addHandler(fileHandler);

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "can not create log file", ex);
        } catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE, "can not create log file", ex);
        }
        // prevent double logs in console
        globalLogger.setUseParentHandlers(false);
        fileCopierLogger.setUseParentHandlers(false);

        LOGGER.info("*********** Starting dlcopy ***********");

//        try {
//            UIManager.setLookAndFeel(
//                    "com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel");
//        } catch (ClassNotFoundException ex) {
//            logger.log(Level.SEVERE, null, ex);
//        } catch (InstantiationException ex) {
//            logger.log(Level.SEVERE, null, ex);
//        } catch (IllegalAccessException ex) {
//            logger.log(Level.SEVERE, null, ex);
//        } catch (UnsupportedLookAndFeelException ex) {
//            logger.log(Level.SEVERE, null, ex);
//        }

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
        for (int i = 0, length = arguments.length; i < length; i++) {

            if (arguments[i].equals("--variant")
                    && (i != length - 1)
                    && (arguments[i + 1].equals("lernstick"))) {
                debianLiveDistribution = DebianLiveDistribution.lernstick;
            }

            if (arguments[i].equals("--boot") && (i != length - 1)) {
                bootDevice = arguments[i + 1];
            }

            if (arguments[i].equals("--systemsize") && (i != length - 1)) {
                try {
                    systemSizeEnlarged = Long.parseLong(arguments[i + 1]);
                    LOGGER.log(Level.INFO, "systemSize = {0}", systemSizeEnlarged);
                } catch (NumberFormatException numberFormatException) {
                    LOGGER.log(Level.SEVERE, "can not parse system size",
                            numberFormatException);
                }
            }
        }
        if (LOGGER.isLoggable(Level.INFO)) {
            if (debianLiveDistribution == DebianLiveDistribution.Default) {
                LOGGER.info("using default variant");
            } else {
                LOGGER.info("using lernstick variant");
            }
        }
        systemPartitionLabel =
                debianLiveDistribution == DebianLiveDistribution.lernstick
                ? "lernstick"
                : "DEBIAN_LIVE";

        initComponents();
        tmpDirTextField.getDocument().addDocumentListener(this);

        try {
            // only try determining boot device if not already given on command
            // line
            if (bootDevice == null) {
                bootDevice = getBootDevice(DEBIAN_LIVE_SYSTEM_PATH);
                if (bootDevice == null) {
                    String errorMessage =
                            STRINGS.getString("Error_Boot_Device");
                    LOGGER.severe(errorMessage);
                    showErrorMessage(errorMessage);
                    System.exit(-1);
                }
            }

            LOGGER.log(Level.FINEST, "bootDevice:\"{0}\"", bootDevice);
            bootDeviceIsUSB = isUSBFlashDrive(bootDevice);
            LOGGER.log(Level.FINEST, "bootDeviceIsUSB = {0}", bootDeviceIsUSB);
            if (bootDeviceIsUSB) {
                Icon usb2usbIcon = new ImageIcon(
                        getClass().getResource("/dlcopy/icons/usb2usb.png"));
                infoLabel.setIcon(usb2usbIcon);
                installButton.setIcon(usb2usbIcon);
            }
            getRootPane().setDefaultButton(installButton);
            installButton.requestFocusInWindow();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "can not determine boot device", ex);
            System.exit(-1);
        }
        URL imageURL = getClass().getResource(
                "/dlcopy/icons/usbpendrive_unmount.png");
        setIconImage(new ImageIcon(imageURL).getImage());

        // determine system size
        File system = new File(DEBIAN_LIVE_SYSTEM_PATH);
        if (systemSizeEnlarged == -1) {
            systemSize = system.getTotalSpace() - system.getFreeSpace();
            LOGGER.log(Level.FINEST, "systemSpace: {0}", systemSize);
            systemSizeEnlarged = (long) (systemSize * SIZE_FACTOR);
            LOGGER.log(Level.FINEST, "systemSize: {0}", systemSizeEnlarged);
        }
        String sizeString = getDataVolumeString(systemSizeEnlarged, 1);
        String text = STRINGS.getString("Select_Install_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        installSelectionHeaderLabel.setText(text);

        sizeString = getDataVolumeString(systemSize, 1);
        text = STRINGS.getString("Select_Upgrade_Target_Storage_Media");
        text = MessageFormat.format(text, sizeString);
        upgradeSelectionHeaderLabel.setText(text);

        // detect if system has an exchange partition
        // - boot device must have more than one partition
        // - first partition of boot device must have volume.fstype "vfat"
        String bootDeviceUDI = getHalUDI("block.device", bootDevice);
        String bootDeviceParent = getHalProperty(bootDeviceUDI, "info.parent");
        List<String> partitionUDIs =
                getHalUDIs("info.parent", bootDeviceParent);
        if ((partitionUDIs != null) && (partitionUDIs.size() > 1)) {
            // the boot device has more than one partition
            // find the first partition
            for (String partitionUDI : partitionUDIs) {
                String partitionNumber =
                        getHalProperty(partitionUDI, "volume.partition.number");
                if (partitionNumber.equals("1")) {
                    // this is the first partition
                    // check file system type
                    String fstype =
                            getHalProperty(partitionUDI, "volume.fstype");
                    if (fstype.equals("vfat")) {
                        // ok, we found the exchange partition
                        exchangeSourcePartitionUDI = partitionUDI;
                        exchangeSourcePartition =
                                getHalProperty(partitionUDI, "block.device");
                        break; // for
                    }
                }
            }
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "\n\texchangePartitionUDI: {0}\n\texchangePartition: {1}",
                    new Object[]{
                        exchangeSourcePartitionUDI,
                        exchangeSourcePartition});
        }
        if (exchangeSourcePartition == null) {
            copyExchangeCheckBox.setEnabled(false);
            copyExchangeCheckBox.setToolTipText(
                    STRINGS.getString("No_Exchange_Partition"));
        }

        // detect if system has a data partition (persistency layer)
        List<String> persistencyUDIs =
                getHalUDIs("volume.label", "live-rw");
        if (persistencyUDIs != null) {
            for (String tmpPersistencyUDI : persistencyUDIs) {
                String tmpPersistencyDevice =
                        getHalProperty(tmpPersistencyUDI, "block.device");
                // check, if this corresponds with the boot partition
                // all devices are something like "/dev/sdb1"
                String bootPrefix = bootDevice.substring(0, 8);
                String tmpPrefix = tmpPersistencyDevice.substring(0, 8);
                if (bootPrefix.equalsIgnoreCase(tmpPrefix)) {
                    // ok, we found it
                    persistencyUDI = tmpPersistencyUDI;
                    persistencyDevice = tmpPersistencyDevice;
                    break;
                }
            }
        }
        LOGGER.log(Level.FINEST, "persistencyUDI: {0}", persistencyUDI);
        if (persistencyUDI != null) {
            final String CMD_LINE_FILENAME = "/proc/cmdline";
            try {
                String cmdLine = readOneLineFile(new File(CMD_LINE_FILENAME));
                persistencyBoot = cmdLine.contains(" persistent ");
                LOGGER.log(Level.FINEST,
                        "persistencyBoot: {0}", persistencyBoot);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                        "could not read \"" + CMD_LINE_FILENAME + '\"', ex);
            }
        }

        if (persistencyUDI != null) {
            copyPersistencyCheckBox.setEnabled(true);

            // try determining the size of the persistency layer
            try {
                final String persistencySourcePath = mountPersistencySource();
                if (persistencySourcePath != null) {
                    File persistency = new File(persistencySourcePath);
                    persistencySize = persistency.getTotalSpace()
                            - persistency.getFreeSpace();
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "persistencySize: {0}",
                                getDataVolumeString(persistencySize, 1));
                    }

                    String checkBoxText =
                            STRINGS.getString("Copy_Data_Partition") + " ("
                            + getDataVolumeString(persistencySize, 1) + ')';
                    copyPersistencyCheckBox.setText(checkBoxText);
                    // umount persistencySourcePath after 1 sec
                    // (direct umounting results very often in "device is busy"
                    // error messages)
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                    umount(persistencySourcePath);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "can not determine ", ex);
            }

        } else {
            copyPersistencyCheckBox.setEnabled(false);
            copyPersistencyCheckBox.setToolTipText(
                    STRINGS.getString("No_Data_Partition"));
        }

        installStorageDeviceList.setModel(installStorageDeviceListModel);
        installStorageDeviceRenderer =
                new InstallStorageDeviceRenderer(this, systemSizeEnlarged);
        installStorageDeviceList.setCellRenderer(installStorageDeviceRenderer);

        upgradeStorageDeviceList.setModel(upgradeStorageDeviceListModel);
        upgradeStorageDeviceRenderer = new UpgradeStorageDeviceRenderer();
        upgradeStorageDeviceList.setCellRenderer(upgradeStorageDeviceRenderer);

        pack();
        setLocationRelativeTo(null);

        AbstractDocument exchangePartitionDocument =
                (AbstractDocument) exchangePartitionTextField.getDocument();
        exchangePartitionDocument.setDocumentFilter(new DocumentSizeFilter());
        exchangePartitionSizeTextField.getDocument().addDocumentListener(this);

        if (debianLiveDistribution == DebianLiveDistribution.lernstick) {
            isoLabelTextField.setText("lernstick");
        }

        try {
            dbusSystemConnection = DBusConnection.getConnection(
                    DBusConnection.SYSTEM);
        } catch (DBusException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        // monitor udisks changes
        udisksMonitorThread = new UdisksMonitorThread();
        udisksMonitorThread.start();

        separateFileSystemsListModel = new DefaultListModel();
        separateFileSystemsList.setModel(separateFileSystemsListModel);

        upgradeOverwriteListModel = new DefaultListModel();
        upgradeOverwriteList.setModel(upgradeOverwriteListModel);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {

        // Take great care when calling Swing functions,
        // because here we are on the UdisksMonitorThread!

        // only handle line changes
        if (!"line".equals(evt.getPropertyName())) {
            return;
        }

        String line = (String) evt.getNewValue();
        switch (state) {
            case INSTALL_SELECTION:
                if (line.startsWith(UDISKS_ADDED)) {
                    new InstallStorageDeviceAdder(line).execute();
                } else if (line.startsWith(UDISKS_REMOVED)) {
                    removeStorageDevice(line);
                }
                break;

            case UPGRADE_SELECTION:
                if (line.startsWith(UDISKS_ADDED)) {
                    new UpgradeStorageDeviceAdder(line).execute();
                } else if (line.startsWith(UDISKS_REMOVED)) {
                    removeStorageDevice(line);
                }
                break;

            default:
                LOGGER.log(Level.INFO,
                        "device change not handled in state {0}", state);
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
     * sets the debug list of USB storage devices
     * @param debugUsbStorageDevices the debug list of USB storage devices
     */
    public void setDebugUsbStorageDevices(
            List<UsbStorageDevice> debugUsbStorageDevices) {
        this.debugUsbStorageDevices = debugUsbStorageDevices;
    }

    /**
     * returns the PartitionState for a given storage and system size
     * @param storageSize the storage size
     * @param systemSize the system size
     * @return the PartitionState for a given storage and system size
     */
    public static PartitionState getPartitionState(
            long storageSize, long systemSize) {
        if (storageSize > (systemSize + (2 * MINIMUM_PARTITION_SIZE))) {
            return PartitionState.EXCHANGE;
        } else if (storageSize > (systemSize + MINIMUM_PARTITION_SIZE)) {
            return PartitionState.PERSISTENT;
        } else if (storageSize > systemSize) {
            return PartitionState.ONLY_SYSTEM;
        } else {
            return PartitionState.TOO_SMALL;
        }
    }

    /**
     * returns the exchange partition size slider
     * @return the exchange partition size slider
     */
    public JSlider getExchangePartitionSizeSlider() {
        return exchangePartitionSizeSlider;
    }

    /**
     * returns true, if the given device is a USB flash drive, false otherwise
     * @param device the device
     * @return true, if the given device is a USB flash drive, false otherwise
     * @throws IOException if an I/O error occurs
     */
    public static boolean isUSBFlashDrive(String device) throws IOException {
        String halUDI = getHalUDI("block.device", device);
        if (halUDI == null) {
            // maybe device is a symlink?
            File deviceFile = new File(device);
            String canonicalPath = deviceFile.getCanonicalPath();
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO,
                        "WARNING: HAL UDI for \"{0}\" not found!\n"
                        + "         Retrying with canonical path \"{1}\"",
                        new Object[]{device, canonicalPath});
            }
            if (!canonicalPath.equals(device)) {
                // recursive retry...
                return isUSBFlashDrive(canonicalPath);
            }
        }

        String storageDeviceUDI =
                getHalProperty(halUDI, "block.storage_device");
        if (storageDeviceUDI == null) {
            LOGGER.log(Level.SEVERE,
                    "ERROR: storage device for \"{0}\" not found!", halUDI);
            return false;
        }

        String bus = getHalProperty(storageDeviceUDI, "storage.bus");
        if (!bus.equals("usb")) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "{0} is no USB flash drive because the "
                        + "storage bus is not \"usb\" but \"{1}\"",
                        new Object[]{device, bus});
            }
            return false;
        }
        String driveType =
                getHalProperty(storageDeviceUDI, "storage.drive_type");
        if (!driveType.equals("disk") && !driveType.equals("compact_flash")) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "{0} is no USB flash drive because the "
                        + "drive type is not \"disk\" or \"compact_flash\" but "
                        + "\"{1}\"",
                        new Object[]{device, driveType});
            }
            return false;
        }
        String hotpluggable =
                getHalProperty(storageDeviceUDI, "storage.hotpluggable");
        if (!hotpluggable.equals("true")) {
            LOGGER.log(Level.INFO,
                    "{0} is no USB flash drive because it is not hotpluggable",
                    device);
            return false;
        }
        String removable =
                getHalProperty(storageDeviceUDI, "storage.removable");
        if (!removable.equals("true")) {
            LOGGER.log(Level.INFO,
                    "{0} is no USB flash drive because it is not removable",
                    device);
            return false;
        }

        return true;
    }

    /**
     * returns a hal property for a given device
     * @param udi the universal device identifier of the device
     * @param key the property key
     * @return the requested hal property or <code>null</code>, if the property
     * is not fount
     */
    public static String getHalProperty(String udi, String key) {
        // hal seems to be very unreliable these days, try three times...
        for (int i = 0; i < 3; i++) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST,
                        "executing \"hal-get-property --udi {0} --key {1}\"",
                        new Object[]{udi, key});
            }
            int returnValue = processExecutor.executeProcess(true, true,
                    "hal-get-property", "--udi", udi, "--key", key);
            LOGGER.log(Level.FINEST, "returnValue = {0}", returnValue);
            if (returnValue == 0) {
                List<String> stdOut = processExecutor.getStdOutList();
                if (LOGGER.isLoggable(Level.FINEST)) {
                    StringBuilder stringBuilder = new StringBuilder("stdOut:");
                    for (String string : stdOut) {
                        stringBuilder.append("\n\t\"");
                        stringBuilder.append(string);
                        stringBuilder.append('\"');
                    }
                    LOGGER.finest(stringBuilder.toString());
                }
                if (stdOut.size() > 0) {
                    return stdOut.get(0);
                }
            }
            try {
                // wait a short time before retrying...
                LOGGER.warning("retrying...");
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "could not sleep", ex);
            }
        }
        return null;
    }

    /**
     * returns the HAL UDI for a given property
     * @param key the key of the property
     * @param value the value of the property
     * @return
     */
    public static List<String> getHalUDIs(String key, String value) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "executing \"hal-find-by-property --key {0} --string {1}\"",
                    new Object[]{key, value});
        }
        int returnValue = processExecutor.executeProcess(true, true,
                "hal-find-by-property", "--key", key, "--string", value);
        LOGGER.log(Level.FINEST, "returnValue = {0}", returnValue);
        if (returnValue == 0) {
            List<String> stdOut = processExecutor.getStdOutList();
            if (LOGGER.isLoggable(Level.FINEST)) {
                StringBuilder stringBuilder = new StringBuilder("stdOut:");
                for (String string : stdOut) {
                    stringBuilder.append("\n\t\"");
                    stringBuilder.append(string);
                    stringBuilder.append('\"');
                }
                LOGGER.finest(stringBuilder.toString());
            }
            if (stdOut.size() > 0) {
                return stdOut;
            }
        }
        return null;
    }

    /**
     * returns the HAL UDI for a given property
     * @param key the key of the property
     * @param value the value of the property
     * @return
     */
    public static String getHalUDI(String key, String value) {
        List<String> halUDIs = getHalUDIs(key, value);
        if ((halUDIs != null) && (halUDIs.size() > 0)) {
            return halUDIs.get(0);
        }
        return null;
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
     * returns the string representation of a given data volume
     * @param bytes the datavolume given in Byte
     * @param fractionDigits the number of fraction digits to display
     * @return the string representation of a given data volume
     */
    public static String getDataVolumeString(long bytes, int fractionDigits) {
        if (bytes >= 1024) {
            numberFormat.setMaximumFractionDigits(fractionDigits);
            float kbytes = (float) bytes / 1024;
            if (kbytes >= 1024) {
                float mbytes = (float) bytes / 1048576;
                if (mbytes >= 1024) {
                    float gbytes = (float) bytes / 1073741824;
                    return numberFormat.format(gbytes) + " GiB";
                }

                return numberFormat.format(mbytes) + " MiB";
            }

            return numberFormat.format(kbytes) + " KiB";
        }

        return numberFormat.format(bytes) + " Byte";
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        choicePanel = new javax.swing.JPanel();
        choiceLabel = new javax.swing.JLabel();
        installButton = new javax.swing.JButton();
        upgradeButton = new javax.swing.JButton();
        imageButton = new javax.swing.JButton();
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
        installShowHarddiskCheckBox = new javax.swing.JCheckBox();
        installSelectionCardPanel = new javax.swing.JPanel();
        installListPanel = new javax.swing.JPanel();
        storageDeviceListScrollPane = new javax.swing.JScrollPane();
        installStorageDeviceList = new javax.swing.JList();
        createPartitionPanel = new javax.swing.JPanel();
        exchangePartitionSizeLabel = new javax.swing.JLabel();
        exchangePartitionSizeSlider = new javax.swing.JSlider();
        exchangePartitionSizeTextField = new javax.swing.JTextField();
        exchangePartitionSizeUnitLabel = new javax.swing.JLabel();
        exchangePartitionLabel = new javax.swing.JLabel();
        exchangePartitionTextField = new javax.swing.JTextField();
        copyPartitionPanel = new javax.swing.JPanel();
        copyExchangeCheckBox = new javax.swing.JCheckBox();
        copyPersistencyCheckBox = new javax.swing.JCheckBox();
        exchangeDefinitionLabel = new javax.swing.JLabel();
        dataDefinitionLabel = new javax.swing.JLabel();
        osDefinitionLabel = new javax.swing.JLabel();
        installNoMediaPanel = new javax.swing.JPanel();
        installNoMediaLabel = new javax.swing.JLabel();
        installPanel = new javax.swing.JPanel();
        currentlyInstalledDeviceLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        installCardPanel = new javax.swing.JPanel();
        installIndeterminateProgressPanel = new javax.swing.JPanel();
        installIndeterminateProgressBar = new javax.swing.JProgressBar();
        installCopyPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        installFileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
        rsyncPanel = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        rsyncPogressBar = new javax.swing.JProgressBar();
        donePanel = new javax.swing.JPanel();
        doneLabel = new javax.swing.JLabel();
        upgradeInfoPanel = new javax.swing.JPanel();
        upgradeInfoLabel = new javax.swing.JLabel();
        upgradeSelectionPanel = new javax.swing.JPanel();
        upgradeSelectionHeaderLabel = new javax.swing.JLabel();
        upgradeShowHarddiskCheckBox = new javax.swing.JCheckBox();
        upgradeSelectionCardPanel = new javax.swing.JPanel();
        upgradeNoMediaPanel = new javax.swing.JPanel();
        upgradeNoMediaLabel = new javax.swing.JLabel();
        upgradeTabbedPane = new javax.swing.JTabbedPane();
        upgradeSelectionDeviceListPanel = new javax.swing.JPanel();
        upgradeStorageDeviceListScrollPane = new javax.swing.JScrollPane();
        upgradeStorageDeviceList = new javax.swing.JList();
        upgradeExchangeDefinitionLabel = new javax.swing.JLabel();
        upgradeDataDefinitionLabel = new javax.swing.JLabel();
        upgradeOsDefinitionLabel = new javax.swing.JLabel();
        upgradeSelectionConfigPanel = new javax.swing.JPanel();
        upgradeSystemPartitionCheckBox = new javax.swing.JCheckBox();
        reactivateWelcomeCheckBox = new javax.swing.JCheckBox();
        keepPrinterSettingsCheckBox = new javax.swing.JCheckBox();
        upgradeOverwritePanel = new javax.swing.JPanel();
        upgradeOverwriteScrollPane = new javax.swing.JScrollPane();
        upgradeOverwriteList = new javax.swing.JList();
        upgradeOverwriteAddButton = new javax.swing.JButton();
        upgradeOverwriteEditButton = new javax.swing.JButton();
        upgradeOverwriteRemoveButton = new javax.swing.JButton();
        upgradePanel = new javax.swing.JPanel();
        currentlyUpgradedDeviceLabel = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        upgradeCardPanel = new javax.swing.JPanel();
        upgradeIndeterminateProgressPanel = new javax.swing.JPanel();
        upgradeIndeterminateProgressBar = new javax.swing.JProgressBar();
        upgradeCopyPanel = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        upgradeFileCopierPanel = new ch.fhnw.filecopier.FileCopierPanel();
        toISOInfoPanel = new javax.swing.JPanel();
        toISOInfoLabel = new javax.swing.JLabel();
        toISOSelectionPanel = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
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
        autoStartCheckBox = new javax.swing.JCheckBox();
        separateFileSystemsPanel = new javax.swing.JPanel();
        separateFileSystemsScrollpane = new javax.swing.JScrollPane();
        separateFileSystemsList = new javax.swing.JList();
        separateFileSystemsAddButton = new javax.swing.JButton();
        separateFileSystemsEditButton = new javax.swing.JButton();
        separateFileSystemsRemoveButton = new javax.swing.JButton();
        toISOProgressPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        toISOProgressBar = new javax.swing.JProgressBar();
        toISODonePanel = new javax.swing.JPanel();
        isoDoneLabel = new javax.swing.JLabel();
        previousButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("dlcopy/Strings"); // NOI18N
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
        gridBagConstraints.insets = new java.awt.Insets(30, 0, 0, 0);
        choicePanel.add(choiceLabel, gridBagConstraints);

        installButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/dvd2usb.png"))); // NOI18N
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
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        choicePanel.add(installButton, gridBagConstraints);

        upgradeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usbupgrade.png"))); // NOI18N
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
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        choicePanel.add(upgradeButton, gridBagConstraints);

        imageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usb2dvd.png"))); // NOI18N
        imageButton.setText(bundle.getString("DLCopy.imageButton.text")); // NOI18N
        imageButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        imageButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        imageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imageButtonActionPerformed(evt);
            }
        });
        imageButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                imageButtonFocusGained(evt);
            }
        });
        imageButton.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                imageButtonKeyPressed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        choicePanel.add(imageButton, gridBagConstraints);

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
                .addContainerGap(430, Short.MAX_VALUE))
        );

        cardPanel.setLayout(new java.awt.CardLayout());

        installInfoPanel.setLayout(new java.awt.GridBagLayout());

        infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/dvd2usb.png"))); // NOI18N
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

        installShowHarddiskCheckBox.setFont(installShowHarddiskCheckBox.getFont().deriveFont(installShowHarddiskCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, installShowHarddiskCheckBox.getFont().getSize()-1));
        installShowHarddiskCheckBox.setText(bundle.getString("DLCopy.installShowHarddiskCheckBox.text")); // NOI18N
        installShowHarddiskCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                installShowHarddiskCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        installSelectionPanel.add(installShowHarddiskCheckBox, gridBagConstraints);

        installSelectionCardPanel.setLayout(new java.awt.CardLayout());

        installStorageDeviceList.setName("installStorageDeviceList"); // NOI18N
        installStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                installStorageDeviceListValueChanged(evt);
            }
        });
        storageDeviceListScrollPane.setViewportView(installStorageDeviceList);

        createPartitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.createPartitionPanel.border.title"))); // NOI18N

        exchangePartitionSizeLabel.setText(bundle.getString("Size")); // NOI18N
        exchangePartitionSizeLabel.setEnabled(false);

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

        exchangePartitionSizeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        exchangePartitionSizeTextField.setEnabled(false);
        exchangePartitionSizeTextField.setName("exchangePartitionSizeTextField"); // NOI18N

        exchangePartitionSizeUnitLabel.setText(bundle.getString("DLCopy.exchangePartitionSizeUnitLabel.text")); // NOI18N
        exchangePartitionSizeUnitLabel.setEnabled(false);

        exchangePartitionLabel.setText(bundle.getString("Label")); // NOI18N
        exchangePartitionLabel.setEnabled(false);

        exchangePartitionTextField.setColumns(11);
        exchangePartitionTextField.setText(bundle.getString("Exchange")); // NOI18N
        exchangePartitionTextField.setEnabled(false);

        javax.swing.GroupLayout createPartitionPanelLayout = new javax.swing.GroupLayout(createPartitionPanel);
        createPartitionPanel.setLayout(createPartitionPanelLayout);
        createPartitionPanelLayout.setHorizontalGroup(
            createPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(createPartitionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(createPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(createPartitionPanelLayout.createSequentialGroup()
                        .addComponent(exchangePartitionSizeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exchangePartitionSizeSlider, javax.swing.GroupLayout.DEFAULT_SIZE, 486, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exchangePartitionSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exchangePartitionSizeUnitLabel))
                    .addGroup(createPartitionPanelLayout.createSequentialGroup()
                        .addComponent(exchangePartitionLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exchangePartitionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );

        createPartitionPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {exchangePartitionLabel, exchangePartitionSizeLabel});

        createPartitionPanelLayout.setVerticalGroup(
            createPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(createPartitionPanelLayout.createSequentialGroup()
                .addGap(1, 1, 1)
                .addGroup(createPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(exchangePartitionSizeLabel)
                    .addComponent(exchangePartitionSizeSlider, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchangePartitionSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(exchangePartitionSizeUnitLabel))
                .addGap(18, 18, 18)
                .addGroup(createPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exchangePartitionLabel)
                    .addComponent(exchangePartitionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        copyPartitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.copyPartitionPanel.border.title"))); // NOI18N

        copyExchangeCheckBox.setText(bundle.getString("DLCopy.copyExchangeCheckBox.text")); // NOI18N

        copyPersistencyCheckBox.setText(bundle.getString("Copy_Data_Partition")); // NOI18N

        javax.swing.GroupLayout copyPartitionPanelLayout = new javax.swing.GroupLayout(copyPartitionPanel);
        copyPartitionPanel.setLayout(copyPartitionPanelLayout);
        copyPartitionPanelLayout.setHorizontalGroup(
            copyPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(copyPartitionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(copyExchangeCheckBox)
                .addGap(18, 18, 18)
                .addComponent(copyPersistencyCheckBox)
                .addContainerGap(322, Short.MAX_VALUE))
        );
        copyPartitionPanelLayout.setVerticalGroup(
            copyPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(copyPartitionPanelLayout.createSequentialGroup()
                .addGroup(copyPartitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(copyExchangeCheckBox)
                    .addComponent(copyPersistencyCheckBox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        exchangeDefinitionLabel.setFont(exchangeDefinitionLabel.getFont().deriveFont(exchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, exchangeDefinitionLabel.getFont().getSize()-1));
        exchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/yellow_box.png"))); // NOI18N
        exchangeDefinitionLabel.setText(bundle.getString("DLCopy.exchangeDefinitionLabel.text")); // NOI18N

        dataDefinitionLabel.setFont(dataDefinitionLabel.getFont().deriveFont(dataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, dataDefinitionLabel.getFont().getSize()-1));
        dataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/green_box.png"))); // NOI18N
        dataDefinitionLabel.setText(bundle.getString("DLCopy.dataDefinitionLabel.text")); // NOI18N

        osDefinitionLabel.setFont(osDefinitionLabel.getFont().deriveFont(osDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, osDefinitionLabel.getFont().getSize()-1));
        osDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/blue_box.png"))); // NOI18N
        osDefinitionLabel.setText(bundle.getString("DLCopy.osDefinitionLabel.text")); // NOI18N

        javax.swing.GroupLayout installListPanelLayout = new javax.swing.GroupLayout(installListPanel);
        installListPanel.setLayout(installListPanelLayout);
        installListPanelLayout.setHorizontalGroup(
            installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 748, Short.MAX_VALUE)
            .addGroup(installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(installListPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(copyPartitionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(createPartitionPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(installListPanelLayout.createSequentialGroup()
                            .addComponent(exchangeDefinitionLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 398, Short.MAX_VALUE))
                        .addGroup(installListPanelLayout.createSequentialGroup()
                            .addComponent(dataDefinitionLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 127, Short.MAX_VALUE))
                        .addGroup(installListPanelLayout.createSequentialGroup()
                            .addComponent(osDefinitionLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 623, Short.MAX_VALUE))
                        .addComponent(storageDeviceListScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                    .addContainerGap()))
        );
        installListPanelLayout.setVerticalGroup(
            installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 478, Short.MAX_VALUE)
            .addGroup(installListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(installListPanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(storageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 206, Short.MAX_VALUE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(exchangeDefinitionLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(dataDefinitionLabel)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(osDefinitionLabel)
                    .addGap(18, 18, 18)
                    .addComponent(createPartitionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(copyPartitionPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap()))
        );

        installSelectionCardPanel.add(installListPanel, "installListPanel");

        installNoMediaPanel.setLayout(new java.awt.GridBagLayout());

        installNoMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/messagebox_info.png"))); // NOI18N
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

        currentlyInstalledDeviceLabel.setText(bundle.getString("Install_Device_Info")); // NOI18N

        installCardPanel.setLayout(new java.awt.CardLayout());

        installIndeterminateProgressPanel.setLayout(new java.awt.GridBagLayout());

        installIndeterminateProgressBar.setIndeterminate(true);
        installIndeterminateProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        installIndeterminateProgressBar.setString(bundle.getString("DLCopy.installIndeterminateProgressBar.string")); // NOI18N
        installIndeterminateProgressBar.setStringPainted(true);
        installIndeterminateProgressPanel.add(installIndeterminateProgressBar, new java.awt.GridBagConstraints());

        installCardPanel.add(installIndeterminateProgressPanel, "installIndeterminateProgressPanel");

        installCopyPanel.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText(bundle.getString("DLCopy.jLabel1.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        installCopyPanel.add(jLabel1, gridBagConstraints);
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
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        rsyncPanel.add(rsyncPogressBar, gridBagConstraints);

        installCardPanel.add(rsyncPanel, "rsyncPanel");

        javax.swing.GroupLayout installPanelLayout = new javax.swing.GroupLayout(installPanel);
        installPanel.setLayout(installPanelLayout);
        installPanelLayout.setHorizontalGroup(
            installPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(installPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(currentlyInstalledDeviceLabel)
                .addContainerGap(483, Short.MAX_VALUE))
            .addComponent(installCardPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 748, Short.MAX_VALUE)
            .addComponent(jSeparator3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 748, Short.MAX_VALUE)
        );
        installPanelLayout.setVerticalGroup(
            installPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(installPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(currentlyInstalledDeviceLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(1, 1, 1)
                .addComponent(installCardPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 479, Short.MAX_VALUE))
        );

        cardPanel.add(installPanel, "installPanel");

        doneLabel.setFont(doneLabel.getFont().deriveFont(doneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        doneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        doneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usbpendrive_unmount_tux.png"))); // NOI18N
        doneLabel.setText(bundle.getString("Done_Message_From_USB")); // NOI18N
        doneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        doneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout donePanelLayout = new javax.swing.GroupLayout(donePanel);
        donePanel.setLayout(donePanelLayout);
        donePanelLayout.setHorizontalGroup(
            donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 748, Short.MAX_VALUE)
            .addGroup(donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(donePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(doneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 724, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        donePanelLayout.setVerticalGroup(
            donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 523, Short.MAX_VALUE)
            .addGroup(donePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(donePanelLayout.createSequentialGroup()
                    .addGap(83, 83, 83)
                    .addComponent(doneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(274, Short.MAX_VALUE)))
        );

        cardPanel.add(donePanel, "donePanel");

        upgradeInfoPanel.setLayout(new java.awt.GridBagLayout());

        upgradeInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usbupgrade.png"))); // NOI18N
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

        upgradeShowHarddiskCheckBox.setFont(upgradeShowHarddiskCheckBox.getFont().deriveFont(upgradeShowHarddiskCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeShowHarddiskCheckBox.getFont().getSize()-1));
        upgradeShowHarddiskCheckBox.setText(bundle.getString("DLCopy.upgradeShowHarddiskCheckBox.text")); // NOI18N
        upgradeShowHarddiskCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                upgradeShowHarddiskCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        upgradeSelectionPanel.add(upgradeShowHarddiskCheckBox, gridBagConstraints);

        upgradeSelectionCardPanel.setLayout(new java.awt.CardLayout());

        upgradeNoMediaPanel.setLayout(new java.awt.GridBagLayout());

        upgradeNoMediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/messagebox_info.png"))); // NOI18N
        upgradeNoMediaLabel.setText(bundle.getString("Insert_Media")); // NOI18N
        upgradeNoMediaLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        upgradeNoMediaLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        upgradeNoMediaPanel.add(upgradeNoMediaLabel, new java.awt.GridBagConstraints());

        upgradeSelectionCardPanel.add(upgradeNoMediaPanel, "upgradeNoMediaPanel");

        upgradeStorageDeviceList.setName("storageDeviceList"); // NOI18N
        upgradeStorageDeviceList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                upgradeStorageDeviceListValueChanged(evt);
            }
        });
        upgradeStorageDeviceListScrollPane.setViewportView(upgradeStorageDeviceList);

        upgradeExchangeDefinitionLabel.setFont(upgradeExchangeDefinitionLabel.getFont().deriveFont(upgradeExchangeDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeExchangeDefinitionLabel.getFont().getSize()-1));
        upgradeExchangeDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/yellow_box.png"))); // NOI18N
        upgradeExchangeDefinitionLabel.setText(bundle.getString("DLCopy.upgradeExchangeDefinitionLabel.text")); // NOI18N

        upgradeDataDefinitionLabel.setFont(upgradeDataDefinitionLabel.getFont().deriveFont(upgradeDataDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeDataDefinitionLabel.getFont().getSize()-1));
        upgradeDataDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/green_box.png"))); // NOI18N
        upgradeDataDefinitionLabel.setText(bundle.getString("DLCopy.upgradeDataDefinitionLabel.text")); // NOI18N

        upgradeOsDefinitionLabel.setFont(upgradeOsDefinitionLabel.getFont().deriveFont(upgradeOsDefinitionLabel.getFont().getStyle() & ~java.awt.Font.BOLD, upgradeOsDefinitionLabel.getFont().getSize()-1));
        upgradeOsDefinitionLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/blue_box.png"))); // NOI18N
        upgradeOsDefinitionLabel.setText(bundle.getString("DLCopy.upgradeOsDefinitionLabel.text")); // NOI18N

        javax.swing.GroupLayout upgradeSelectionDeviceListPanelLayout = new javax.swing.GroupLayout(upgradeSelectionDeviceListPanel);
        upgradeSelectionDeviceListPanel.setLayout(upgradeSelectionDeviceListPanelLayout);
        upgradeSelectionDeviceListPanelLayout.setHorizontalGroup(
            upgradeSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeSelectionDeviceListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(upgradeSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(upgradeStorageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 719, Short.MAX_VALUE)
                    .addComponent(upgradeOsDefinitionLabel)
                    .addComponent(upgradeDataDefinitionLabel)
                    .addComponent(upgradeExchangeDefinitionLabel))
                .addContainerGap())
        );
        upgradeSelectionDeviceListPanelLayout.setVerticalGroup(
            upgradeSelectionDeviceListPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, upgradeSelectionDeviceListPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(upgradeStorageDeviceListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 361, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upgradeExchangeDefinitionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upgradeDataDefinitionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upgradeOsDefinitionLabel)
                .addContainerGap())
        );

        upgradeTabbedPane.addTab(bundle.getString("DLCopy.upgradeSelectionDeviceListPanel.TabConstraints.tabTitle"), upgradeSelectionDeviceListPanel); // NOI18N

        upgradeSystemPartitionCheckBox.setSelected(true);
        upgradeSystemPartitionCheckBox.setText(bundle.getString("DLCopy.upgradeSystemPartitionCheckBox.text")); // NOI18N

        reactivateWelcomeCheckBox.setSelected(true);
        reactivateWelcomeCheckBox.setText(bundle.getString("DLCopy.reactivateWelcomeCheckBox.text")); // NOI18N

        keepPrinterSettingsCheckBox.setSelected(true);
        keepPrinterSettingsCheckBox.setText(bundle.getString("DLCopy.keepPrinterSettingsCheckBox.text")); // NOI18N

        upgradeOverwritePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.upgradeOverwritePanel.border.title"))); // NOI18N

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

        upgradeOverwriteAddButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/16x16/list-add.png"))); // NOI18N
        upgradeOverwriteAddButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteAddButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteEditButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/16x16/document-edit.png"))); // NOI18N
        upgradeOverwriteEditButton.setEnabled(false);
        upgradeOverwriteEditButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteEditButtonActionPerformed(evt);
            }
        });

        upgradeOverwriteRemoveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/16x16/list-remove.png"))); // NOI18N
        upgradeOverwriteRemoveButton.setEnabled(false);
        upgradeOverwriteRemoveButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upgradeOverwriteRemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upgradeOverwriteRemoveButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout upgradeOverwritePanelLayout = new javax.swing.GroupLayout(upgradeOverwritePanel);
        upgradeOverwritePanel.setLayout(upgradeOverwritePanelLayout);
        upgradeOverwritePanelLayout.setHorizontalGroup(
            upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, upgradeOverwritePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(upgradeOverwriteScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 653, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(upgradeOverwriteAddButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteEditButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteRemoveButton, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        upgradeOverwritePanelLayout.setVerticalGroup(
            upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeOverwritePanelLayout.createSequentialGroup()
                .addGroup(upgradeOverwritePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(upgradeOverwriteScrollPane, javax.swing.GroupLayout.Alignment.LEADING, 0, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, upgradeOverwritePanelLayout.createSequentialGroup()
                        .addComponent(upgradeOverwriteAddButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upgradeOverwriteEditButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upgradeOverwriteRemoveButton)))
                .addContainerGap())
        );

        javax.swing.GroupLayout upgradeSelectionConfigPanelLayout = new javax.swing.GroupLayout(upgradeSelectionConfigPanel);
        upgradeSelectionConfigPanel.setLayout(upgradeSelectionConfigPanelLayout);
        upgradeSelectionConfigPanelLayout.setHorizontalGroup(
            upgradeSelectionConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeSelectionConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(upgradeSelectionConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(upgradeOverwritePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(keepPrinterSettingsCheckBox)
                    .addComponent(reactivateWelcomeCheckBox)
                    .addComponent(upgradeSystemPartitionCheckBox))
                .addContainerGap())
        );
        upgradeSelectionConfigPanelLayout.setVerticalGroup(
            upgradeSelectionConfigPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(upgradeSelectionConfigPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(upgradeSystemPartitionCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(reactivateWelcomeCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(keepPrinterSettingsCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(upgradeOverwritePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        upgradeTabbedPane.addTab(bundle.getString("DLCopy.upgradeSelectionConfigPanel.TabConstraints.tabTitle"), upgradeSelectionConfigPanel); // NOI18N

        upgradeSelectionCardPanel.add(upgradeTabbedPane, "upgradeTabbedPane");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        upgradeSelectionPanel.add(upgradeSelectionCardPanel, gridBagConstraints);

        cardPanel.add(upgradeSelectionPanel, "upgradeSelectionPanel");

        upgradePanel.setLayout(new java.awt.GridBagLayout());

        currentlyUpgradedDeviceLabel.setFont(currentlyUpgradedDeviceLabel.getFont().deriveFont(currentlyUpgradedDeviceLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        currentlyUpgradedDeviceLabel.setText(bundle.getString("DLCopy.currentlyUpgradedDeviceLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
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

        jLabel2.setText(bundle.getString("DLCopy.jLabel2.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        upgradeCopyPanel.add(jLabel2, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        upgradeCopyPanel.add(upgradeFileCopierPanel, gridBagConstraints);

        upgradeCardPanel.add(upgradeCopyPanel, "upgradeCopyPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        upgradePanel.add(upgradeCardPanel, gridBagConstraints);

        cardPanel.add(upgradePanel, "upgradePanel");

        toISOInfoPanel.setLayout(new java.awt.GridBagLayout());

        toISOInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usb2dvd.png"))); // NOI18N
        toISOInfoLabel.setText(bundle.getString("DLCopy.toISOInfoLabel.text")); // NOI18N
        toISOInfoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        toISOInfoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        toISOInfoPanel.add(toISOInfoLabel, new java.awt.GridBagConstraints());

        cardPanel.add(toISOInfoPanel, "toISOInfoPanel");

        toISOSelectionPanel.setLayout(new java.awt.GridBagLayout());

        tmpDriveInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tmpDriveInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/file_temporary.png"))); // NOI18N
        tmpDriveInfoLabel.setText(bundle.getString("DLCopy.tmpDriveInfoLabel.text")); // NOI18N
        tmpDriveInfoLabel.setIconTextGap(15);

        tmpDirLabel.setText(bundle.getString("DLCopy.tmpDirLabel.text")); // NOI18N

        tmpDirTextField.setColumns(20);
        tmpDirTextField.setText(bundle.getString("DLCopy.tmpDirTextField.text")); // NOI18N

        tmpDirSelectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/fileopen.png"))); // NOI18N
        tmpDirSelectButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        tmpDirSelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tmpDirSelectButtonActionPerformed(evt);
            }
        });

        freeSpaceLabel.setText(bundle.getString("DLCopy.freeSpaceLabel.text")); // NOI18N

        freeSpaceTextField.setColumns(20);
        freeSpaceTextField.setEditable(false);

        writableLabel.setText(bundle.getString("DLCopy.writableLabel.text")); // NOI18N

        writableTextField.setColumns(20);
        writableTextField.setEditable(false);

        isoLabelLabel.setText(bundle.getString("DLCopy.isoLabelLabel.text")); // NOI18N

        autoStartCheckBox.setText(bundle.getString("DLCopy.autoStartCheckBox.text")); // NOI18N

        separateFileSystemsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopy.separateFileSystemsPanel.border.title"))); // NOI18N

        separateFileSystemsList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                separateFileSystemsListMouseClicked(evt);
            }
        });
        separateFileSystemsList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                separateFileSystemsListValueChanged(evt);
            }
        });
        separateFileSystemsScrollpane.setViewportView(separateFileSystemsList);

        separateFileSystemsAddButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/16x16/list-add.png"))); // NOI18N
        separateFileSystemsAddButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        separateFileSystemsAddButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                separateFileSystemsAddButtonActionPerformed(evt);
            }
        });

        separateFileSystemsEditButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/16x16/document-edit.png"))); // NOI18N
        separateFileSystemsEditButton.setEnabled(false);
        separateFileSystemsEditButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        separateFileSystemsEditButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                separateFileSystemsEditButtonActionPerformed(evt);
            }
        });

        separateFileSystemsRemoveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/16x16/list-remove.png"))); // NOI18N
        separateFileSystemsRemoveButton.setEnabled(false);
        separateFileSystemsRemoveButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        separateFileSystemsRemoveButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                separateFileSystemsRemoveButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout separateFileSystemsPanelLayout = new javax.swing.GroupLayout(separateFileSystemsPanel);
        separateFileSystemsPanel.setLayout(separateFileSystemsPanelLayout);
        separateFileSystemsPanelLayout.setHorizontalGroup(
            separateFileSystemsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, separateFileSystemsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(separateFileSystemsScrollpane, javax.swing.GroupLayout.DEFAULT_SIZE, 658, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(separateFileSystemsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(separateFileSystemsAddButton)
                    .addComponent(separateFileSystemsEditButton)
                    .addComponent(separateFileSystemsRemoveButton))
                .addContainerGap())
        );
        separateFileSystemsPanelLayout.setVerticalGroup(
            separateFileSystemsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(separateFileSystemsPanelLayout.createSequentialGroup()
                .addGroup(separateFileSystemsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(separateFileSystemsPanelLayout.createSequentialGroup()
                        .addComponent(separateFileSystemsAddButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(separateFileSystemsEditButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(separateFileSystemsRemoveButton))
                    .addComponent(separateFileSystemsScrollpane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 129, Short.MAX_VALUE))
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(separateFileSystemsPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tmpDriveInfoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 724, Short.MAX_VALUE)
                    .addComponent(autoStartCheckBox)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(writableLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(isoLabelLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(writableTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 531, Short.MAX_VALUE)
                                    .addComponent(isoLabelTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 531, Short.MAX_VALUE)))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(freeSpaceLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(freeSpaceTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 531, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(tmpDirLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tmpDirTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 531, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tmpDirSelectButton)))
                .addContainerGap())
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {freeSpaceLabel, tmpDirLabel, writableLabel});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tmpDriveInfoLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(tmpDirLabel)
                    .addComponent(tmpDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tmpDirSelectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(freeSpaceLabel)
                    .addComponent(freeSpaceTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(writableLabel)
                    .addComponent(writableTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(isoLabelTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(isoLabelLabel))
                .addGap(18, 18, 18)
                .addComponent(autoStartCheckBox)
                .addGap(18, 18, 18)
                .addComponent(separateFileSystemsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        toISOSelectionPanel.add(jPanel1, gridBagConstraints);

        cardPanel.add(toISOSelectionPanel, "toISOSelectionPanel");

        toISOProgressPanel.setLayout(new java.awt.GridBagLayout());

        jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usb2dvd.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        toISOProgressPanel.add(jLabel6, gridBagConstraints);

        toISOProgressBar.setIndeterminate(true);
        toISOProgressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        toISOProgressBar.setString(bundle.getString("DLCopy.toISOProgressBar.string")); // NOI18N
        toISOProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(30, 0, 0, 0);
        toISOProgressPanel.add(toISOProgressBar, gridBagConstraints);

        cardPanel.add(toISOProgressPanel, "toISOProgressPanel");

        isoDoneLabel.setFont(isoDoneLabel.getFont().deriveFont(isoDoneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        isoDoneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/usb2dvd.png"))); // NOI18N
        isoDoneLabel.setText(bundle.getString("DLCopy.isoDoneLabel.text")); // NOI18N
        isoDoneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        isoDoneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        javax.swing.GroupLayout toISODonePanelLayout = new javax.swing.GroupLayout(toISODonePanel);
        toISODonePanel.setLayout(toISODonePanelLayout);
        toISODonePanelLayout.setHorizontalGroup(
            toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 748, Short.MAX_VALUE)
            .addGroup(toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(toISODonePanelLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(isoDoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 724, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        toISODonePanelLayout.setVerticalGroup(
            toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 523, Short.MAX_VALUE)
            .addGroup(toISODonePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(toISODonePanelLayout.createSequentialGroup()
                    .addGap(83, 83, 83)
                    .addComponent(isoDoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addContainerGap(199, Short.MAX_VALUE)))
        );

        cardPanel.add(toISODonePanel, "toISODonePanel");

        previousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/previous.png"))); // NOI18N
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

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/next.png"))); // NOI18N
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
                        .addComponent(cardPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 748, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, executionPanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(previousButton)
                        .addGap(18, 18, 18)
                        .addComponent(nextButton)))
                .addContainerGap())
            .addComponent(jSeparator2, javax.swing.GroupLayout.DEFAULT_SIZE, 898, Short.MAX_VALUE)
        );
        executionPanelLayout.setVerticalGroup(
            executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, executionPanelLayout.createSequentialGroup()
                .addGroup(executionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(executionPanelLayout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addComponent(cardPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 523, Short.MAX_VALUE))
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
                setLabelHighlighted(infoStepLabel, false);
                setLabelHighlighted(selectionLabel, true);
                setLabelHighlighted(executionLabel, false);
                showCard(cardPanel, "upgradeSelectionPanel");
                enableNextButton();
                state = State.UPGRADE_SELECTION;
                break;

            case INSTALL_SELECTION:
                try {
                    checkInstallSelection();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE,
                            "checking the selected usb flash drive failed", ex);
                }
                break;

            case UPGRADE_SELECTION:
                upgrade();
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

            case INSTALLATION:
                exitProgram();
                break;

            case UPGRADE:
                exitProgram();
                break;

            case ISO_INSTALLATION:
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

            case ISO_INSTALLATION:
                nextButton.setIcon(new ImageIcon(
                        getClass().getResource("/dlcopy/icons/next.png")));
                nextButton.setText(
                        STRINGS.getString("DLCopy.nextButton.text"));
                getRootPane().setDefaultButton(installButton);
                installButton.requestFocusInWindow();
                globalShow("choicePanel");
                break;

            case INSTALLATION:
                switchToInstallSelection();
                nextButton.setIcon(new ImageIcon(
                        getClass().getResource("/dlcopy/icons/next.png")));
                nextButton.setText(
                        STRINGS.getString("DLCopy.nextButton.text"));
                break;

            case UPGRADE:
                switchToUpgradeInformation();
                nextButton.setIcon(new ImageIcon(
                        getClass().getResource("/dlcopy/icons/next.png")));
                nextButton.setText(
                        STRINGS.getString("DLCopy.nextButton.text"));
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

    private void imageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imageButtonActionPerformed
        try {
            if (isUnmountedPersistencyAvailable()) {
                globalShow("executionPanel");
                switchToISOInformation();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_imageButtonActionPerformed

    private void tmpDirSelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tmpDirSelectButtonActionPerformed
        selectDirectory(tmpDirTextField);
}//GEN-LAST:event_tmpDirSelectButtonActionPerformed

    private void installButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_installButtonFocusGained
        getRootPane().setDefaultButton(installButton);
    }//GEN-LAST:event_installButtonFocusGained

    private void imageButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_imageButtonFocusGained
        getRootPane().setDefaultButton(imageButton);
    }//GEN-LAST:event_imageButtonFocusGained

    private void installShowHarddiskCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_installShowHarddiskCheckBoxItemStateChanged
        new InstallStorageDeviceListUpdater().execute();
    }//GEN-LAST:event_installShowHarddiskCheckBoxItemStateChanged

    private void upgradeButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_upgradeButtonFocusGained
        getRootPane().setDefaultButton(upgradeButton);
    }//GEN-LAST:event_upgradeButtonFocusGained

    private void installButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_installButtonKeyPressed
        if (KeyEvent.VK_DOWN == evt.getKeyCode()) {
            upgradeButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_installButtonKeyPressed

    private void upgradeButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_upgradeButtonKeyPressed
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
                installButton.requestFocusInWindow();
                break;
            case KeyEvent.VK_DOWN:
                imageButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_upgradeButtonKeyPressed

    private void choicePanelComponentShown(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_choicePanelComponentShown
        installButton.requestFocusInWindow();
    }//GEN-LAST:event_choicePanelComponentShown

    private void imageButtonKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_imageButtonKeyPressed
        if (KeyEvent.VK_UP == evt.getKeyCode()) {
            upgradeButton.requestFocusInWindow();
        }
    }//GEN-LAST:event_imageButtonKeyPressed

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

private void upgradeShowHarddiskCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_upgradeShowHarddiskCheckBoxItemStateChanged
    new UpgradeStorageDeviceListUpdater().execute();
}//GEN-LAST:event_upgradeShowHarddiskCheckBoxItemStateChanged

    private void separateFileSystemsAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_separateFileSystemsAddButtonActionPerformed
        addPathToList(JFileChooser.DIRECTORIES_ONLY,
                separateFileSystemsListModel);
    }//GEN-LAST:event_separateFileSystemsAddButtonActionPerformed

    private void separateFileSystemsEditButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_separateFileSystemsEditButtonActionPerformed
        editPathListEntry(separateFileSystemsList,
                JFileChooser.DIRECTORIES_ONLY);
    }//GEN-LAST:event_separateFileSystemsEditButtonActionPerformed

    private void separateFileSystemsRemoveButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_separateFileSystemsRemoveButtonActionPerformed
        removeSelectedListEntries(separateFileSystemsList);
    }//GEN-LAST:event_separateFileSystemsRemoveButtonActionPerformed

    private void separateFileSystemsListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_separateFileSystemsListValueChanged
        int[] selectedIndices = separateFileSystemsList.getSelectedIndices();
        separateFileSystemsEditButton.setEnabled(selectedIndices.length == 1);
        separateFileSystemsRemoveButton.setEnabled(selectedIndices.length > 0);
    }//GEN-LAST:event_separateFileSystemsListValueChanged

    private void separateFileSystemsListMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_separateFileSystemsListMouseClicked
        if (evt.getClickCount() == 2) {
            editPathListEntry(separateFileSystemsList,
                    JFileChooser.DIRECTORIES_ONLY);
        }
    }//GEN-LAST:event_separateFileSystemsListMouseClicked

    private void upgradeOverwriteAddButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upgradeOverwriteAddButtonActionPerformed
        addPathToList(JFileChooser.FILES_AND_DIRECTORIES,
                upgradeOverwriteListModel);
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
        upgradeOverwriteEditButton.setEnabled(selectedIndices.length == 1);
        upgradeOverwriteRemoveButton.setEnabled(selectedIndices.length > 0);
    }//GEN-LAST:event_upgradeOverwriteListValueChanged

    private void removeSelectedListEntries(JList list) {
        int[] selectedIndices = list.getSelectedIndices();
        DefaultListModel model = (DefaultListModel) list.getModel();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            model.remove(i);
        }
    }

    private void addPathToList(int selectionMode, DefaultListModel listModel) {
        JFileChooser fileChooser = new JFileChooser("/");
        fileChooser.setFileSelectionMode(selectionMode);
        addHiddenFilesFilter(fileChooser);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fileChooser.getSelectedFile().getPath();
            listModel.addElement(selectedPath);
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
                DefaultListModel listModel = null;
                switch (state) {
                    case INSTALL_SELECTION:
                        listModel = installStorageDeviceListModel;
                        break;
                    case UPGRADE_SELECTION:
                        listModel = upgradeStorageDeviceListModel;
                }

                for (int i = 0, size = listModel.getSize(); i < size; i++) {
                    StorageDevice storageDevice = (StorageDevice) listModel.get(i);
                    if (storageDevice.getDevice().endsWith(device)) {
                        listModel.remove(i);
                        LOGGER.log(Level.INFO, "removed from storage device list: {0}", device);
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

            showCard(upgradeSelectionCardPanel, "upgradeTabbedPane");
            // auto-select single entry
            if (deviceCount == 1) {
                upgradeStorageDeviceList.setSelectedIndex(0);
            }
        }
    }

    private long getMaxStorageDeviceSize(ListModel listModel) {
        long maxSize = 0;
        for (int i = 0, size = listModel.getSize(); i < size; i++) {
            StorageDevice storageDevice =
                    (StorageDevice) listModel.getElementAt(i);
            long deviceSize = storageDevice.getSize();
            if (deviceSize > maxSize) {
                maxSize = deviceSize;
            }
        }
        LOGGER.log(Level.INFO, "maxSize = {0}", maxSize);
        return maxSize;
    }

    private void editPathListEntry(JList list, int selectionMode) {
        String oldPath = (String) list.getSelectedValue();
        File oldDirectory = new File(oldPath);
        JFileChooser fileChooser =
                new JFileChooser(oldDirectory.getParentFile());
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

    private String getBootDevice(String path) throws IOException {
        String tmpBootDevice = null;
        // try HAL first
        String halUDI = getHalUDI("volume.mount_point", path);
        if (halUDI == null) {
            // sometimes HAL fails...
            LOGGER.warning(
                    "HAL did not find a UDI, we must parse /proc/mounts");
            // ok, lets parse /proc/mounts now...
            List<String> mounts = readFile(new File("/proc/mounts"));
            for (String mount : mounts) {
                String[] tokens = mount.split(" ");
                if (tokens[0].startsWith("/dev/") && tokens[1].equals(path)) {
                    tmpBootDevice = tokens[0];
                    break;
                }
            }
        } else {
            tmpBootDevice = getHalProperty(halUDI, "block.device");
        }
        return tmpBootDevice;
    }

    private static List<String> readFile(File file) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new FileReader(file));
        for (String line = reader.readLine(); line != null;
                line = reader.readLine()) {
            lines.add(line);
        }
        reader.close();
        return lines;
    }

    private static void writeFile(File file, List<String> lines)
            throws IOException {
        // delete old version of file
        if (file.exists()) {
            file.delete();
        }
        // write new version of file
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            String lineSeparator = System.getProperty("line.separator");
            for (String line : lines) {
                outputStream.write((line + lineSeparator).getBytes());
            }
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }

    private void exitProgram() {
        udisksMonitorThread.stopMonitoring();
        System.exit(0);
    }

    private long getExchangePartitionSize() throws IOException {
        String exchangeSourcePath = null;
        boolean exchangeSourceTempMounted = false;
        String mountPoint = getHalProperty(
                exchangeSourcePartitionUDI, "volume.mount_point");
        if (mountPoint.length() > 0) {
            // the exchange partition is mounted
            exchangeSourcePath = mountPoint;
        } else {
            exchangeSourcePath = createTempDir("exchange_source").getPath();
            if (!mount(exchangeSourcePartition, exchangeSourcePath)) {
                throw new IOException("can not umount " + exchangeSourcePath);
            }
            exchangeSourceTempMounted = true;
        }
        File exchangePartition = new File(exchangeSourcePath);
        long exchangeSize = exchangePartition.getTotalSpace()
                - exchangePartition.getFreeSpace();
        if (exchangeSourceTempMounted) {
            umount(exchangeSourcePath);
        }
        return exchangeSize;
    }

    private void switchToInstallSelection() {
        // update label highlights
        setLabelHighlighted(infoStepLabel, false);
        setLabelHighlighted(selectionLabel, true);
        setLabelHighlighted(executionLabel, false);

        // update storage device list
        new InstallStorageDeviceListUpdater().execute();

        // update copyExchangeCheckBox
        try {
            if (exchangeSourcePartitionUDI != null) {
                long exchangeSize = getExchangePartitionSize();
                copyExchangeCheckBox.setText(
                        STRINGS.getString("DLCopy.copyExchangeCheckBox.text")
                        + " (" + getDataVolumeString(exchangeSize, 1) + ')');
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "could not update copyExchangeCheckBox", ex);
        }

        previousButton.setEnabled(true);
        // state must be updated before calling
        // installStorageDeviceListSelectionChanged()!
        state = State.INSTALL_SELECTION;
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
                StorageDevice device =
                        (StorageDevice) installStorageDeviceListModel.get(
                        selectedIndices[i]);
                long overhead = device.getSize() - systemSizeEnlarged;
                minOverhead = Math.min(minOverhead, overhead);
                PartitionState partitionState =
                        getPartitionState(device.getSize(), systemSizeEnlarged);
                if (partitionState != PartitionState.EXCHANGE) {
                    exchange = false;
                    break; // for
                }
            }
        }

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

        // enable nextButton?
        updateInstallNextButton();
    }

    private void upgradeStorageDeviceListSelectionChanged() {

        // early return
        if (state != State.UPGRADE_SELECTION) {
            return;
        }

        // check all selected storage devices
        boolean canUpgrade = true;
        int[] selectedIndices = upgradeStorageDeviceList.getSelectedIndices();
        for (int i : selectedIndices) {
            StorageDevice storageDevice =
                    (StorageDevice) upgradeStorageDeviceListModel.get(i);
            try {
                if (!storageDevice.canBeUpgraded()) {
                    canUpgrade = false;
                    break;
                }
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }

        // update nextButton state
        if ((selectedIndices.length > 0) && canUpgrade) {
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
        Dictionary<Integer, JComponent> labels =
                new Hashtable<Integer, JComponent>();
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

    private List<StorageDevice> getStorageDevices(boolean includeHarddisks)
            throws IOException, DBusException {
        List<StorageDevice> storageDevices = new ArrayList<StorageDevice>();

        if (debugUsbStorageDevices == null) {
            // using libdbus-java here fails on Debian Live
            // therefore we parse the command line output
            int returnValue = processExecutor.executeProcess(
                    true, true, "udisks", "--enumerate");
            if (returnValue != 0) {
                throw new IOException("calling \"udisks --enumerate\" failed "
                        + "with the following output: "
                        + processExecutor.getOutput());
            }
            List<String> udisksObjectPaths = processExecutor.getStdOutList();
            for (String path : udisksObjectPaths) {
                // do not add the boot device (something like "/dev/sdb1")
                String bootDeviceTrail = bootDevice.substring(
                        bootDevice.lastIndexOf("/") + 1);
                String pathTrail = path.substring(path.lastIndexOf("/") + 1);
                if (bootDeviceTrail.startsWith(pathTrail)) {
                    // this is the boot device, skip it
                    LOGGER.log(Level.INFO,
                            "skipping {0}, it''s the boot device", path);
                    continue;
                }

                StorageDevice storageDevice = getStorageDevice(
                        path, includeHarddisks);
                if (storageDevice != null) {
                    LOGGER.log(Level.INFO, "adding {0}", path);
                    storageDevices.add(storageDevice);
                }
            }

        } else {
            storageDevices.addAll(debugUsbStorageDevices);
        }

        return storageDevices;
    }

    private StorageDevice getStorageDevice(String path,
            boolean includeHarddisks) throws DBusException {

        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", path, DBus.Properties.class);

        Boolean isDrive = deviceProperties.Get(
                "org.freedesktop.UDisks", "DeviceIsDrive");
        Boolean isLoop = deviceProperties.Get(
                "org.freedesktop.UDisks", "DeviceIsLinuxLoop");
        UInt64 size64 = deviceProperties.Get(
                "org.freedesktop.UDisks", "DeviceSize");
        long size = size64.longValue();

        // early return for non-drives
        if ((!isDrive) || isLoop || (size <= 0)) {
            return null;
        }

        String deviceFile = deviceProperties.Get(
                "org.freedesktop.UDisks", "DeviceFile");
        String vendor = deviceProperties.Get(
                "org.freedesktop.UDisks", "DriveVendor");
        String model = deviceProperties.Get(
                "org.freedesktop.UDisks", "DriveModel");
        String revision = deviceProperties.Get(
                "org.freedesktop.UDisks", "DriveRevision");
        String serial = deviceProperties.Get(
                "org.freedesktop.UDisks", "DriveSerial");
        Boolean isSystemInternal = deviceProperties.Get(
                "org.freedesktop.UDisks", "DeviceIsSystemInternal");
        Boolean removable = deviceProperties.Get(
                "org.freedesktop.UDisks", "DeviceIsRemovable");
        String media = deviceProperties.Get(
                "org.freedesktop.UDisks", "DriveMedia");
        if (deviceFile.startsWith("/dev/mmcblk")) {
            // an SD card
            return new SDStorageDevice(vendor, model, revision, serial,
                    deviceFile, size, systemPartitionLabel, systemSize);
        } else {
            if (removable) {
                // probably a USB flash drive
                return new UsbStorageDevice(vendor, model, revision, serial,
                        deviceFile, size, systemPartitionLabel, systemSize);
            } else {
                // probably a hard drive
                if (includeHarddisks) {
                    return new Harddisk(vendor, model, revision, serial,
                            deviceFile, size, systemPartitionLabel, systemSize);
                } else {
                    return null;
                }
            }
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
            StorageDevice device =
                    (StorageDevice) installStorageDeviceListModel.get(i);
            PartitionState partitionState =
                    getPartitionState(device.getSize(), systemSizeEnlarged);
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
        List<String> mounts = readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String mountedPartition = mount.split(" ")[0];
            if (mountedPartition.startsWith(device)) {
                umount(mountedPartition);
            }
        }
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
            warningMessage = MessageFormat.format(warningMessage,
                    swapFile, device, getDataVolumeString(remainingFreeMem, 0));
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
            String warningMessage =
                    STRINGS.getString("Warning_Swapoff_Partition");
            warningMessage = MessageFormat.format(warningMessage,
                    swapFile, device, getDataVolumeString(remainingFreeMem, 0));
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
                String errorMessage =
                        STRINGS.getString("Error_Swapoff_Partition");
                errorMessage = MessageFormat.format(errorMessage, swapFile);
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
            }
        }
    }

    private void umount(String partition) throws IOException {
        // check if a swapfile is in use on this partition
        List<String> mounts = readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            String device = tokens[0];
            String mountPoint = tokens[1];
            if (device.equals(partition) || mountPoint.equals(partition)) {
                List<String> swapLines = readFile(new File("/proc/swaps"));
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

    private String mountPersistencySource() throws IOException {
        String persistencySourcePath =
                createTempDir("persistency_source").getPath();
        String persistencyPartition =
                getHalProperty(persistencyUDI, "block.device");
        if (!mount(persistencyPartition, persistencySourcePath)) {
            switchToInstallSelection();
            return null;
        }
        return persistencySourcePath;
    }

    private boolean mount(String device, String mountPoint) {
        return mount(device, mountPoint, null);
    }

    private boolean mount(String device, String mountPoint, String options) {
        int exitValue = 0;
        if (options == null) {
            exitValue = processExecutor.executeProcess(
                    "mount", device, mountPoint);
        } else {
            exitValue = processExecutor.executeProcess(
                    "mount", "-o", options, device, mountPoint);
        }

        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Error_Mount");
            errorMessage = MessageFormat.format(
                    errorMessage, device, mountPoint);
            LOGGER.severe(errorMessage);
            showErrorMessage(errorMessage);
            return false;
        }
        return true;
    }

    private static class Partitions {

        private final int exchangeMB;
        private final int persistencyMB;

        public Partitions(int exchangeMB, int persistencyMB) {
            this.exchangeMB = exchangeMB;
            this.persistencyMB = persistencyMB;
        }

        /**
         * returns the size of the exchange partition (in MiB)
         * @return the size of the exchange partition (in MiB)
         */
        public int getExchangeMB() {
            return exchangeMB;
        }

        /**
         * returns the size of the persistency partition (in MiB)
         * @return the size of the persistency partition (in MiB)
         */
        public int getPersistencyMB() {
            return persistencyMB;
        }
    }

    private Partitions getPartitions(StorageDevice storageDevice) {
        long size = storageDevice.getSize();
        long overhead = size - systemSizeEnlarged;
        int overheadMB = (int) (overhead / MEGA);
        PartitionState partitionState = getPartitionState(size, systemSizeEnlarged);
        switch (partitionState) {
            case TOO_SMALL:
                return null;

            case ONLY_SYSTEM:
                return new Partitions(0, 0);

            case EXCHANGE:
                int exchangeMB = exchangePartitionSizeSlider.getValue();
                int persistentMB = overheadMB - exchangeMB;
                return new Partitions(exchangeMB, persistentMB);

            case PERSISTENT:
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

    private class Installer extends Thread implements PropertyChangeListener {

        private FileCopier fileCopier;
        private int currentDevice;
        private int selectionCount;
        private int rsyncProgress;

        @Override
        public void run() {
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(cardPanel, "installPanel");
                }
            });

            int[] selectedIndices = installStorageDeviceList.getSelectedIndices();
            selectionCount = selectedIndices.length;
            fileCopier = new FileCopier();
            try {
                // main loop over all target storage devices
                boolean noError = true;
                for (int i = 0; i < selectionCount; i++) {
                    currentDevice = i + 1;
                    StorageDevice storageDevice =
                            (StorageDevice) installStorageDeviceListModel.getElementAt(
                            selectedIndices[i]);
                    // update overall progress message
                    String pattern = STRINGS.getString("Install_Device_Info");
                    final String message = MessageFormat.format(pattern,
                            storageDevice.getVendor() + " "
                            + storageDevice.getModel() + " "
                            + getDataVolumeString(
                            storageDevice.getSize(), 1),
                            storageDevice.getDevice(),
                            currentDevice, selectionCount);
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            currentlyInstalledDeviceLabel.setText(message);
                        }
                    });

                    if (!copyToStorageDevice(storageDevice)) {
                        noError = false;
                        switchToInstallSelection();
                        break;
                    }
                }

                if (noError) {
                    // done
                    SwingUtilities.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            setTitle(STRINGS.getString("DLCopy.title"));
                            if (bootDeviceIsUSB) {
                                doneLabel.setText(STRINGS.getString(
                                        "Done_Message_From_USB"));
                            } else {
                                doneLabel.setText(STRINGS.getString(
                                        "Done_Message_From_DVD"));
                            }
                            showCard(cardPanel, "donePanel");
                            previousButton.setEnabled(true);
                            nextButton.setText(STRINGS.getString("Done"));
                            nextButton.setIcon(new ImageIcon(
                                    getClass().getResource(
                                    "/dlcopy/icons/exit.png")));
                            nextButton.setEnabled(true);
                            previousButton.requestFocusInWindow();
                            getRootPane().setDefaultButton(previousButton);
                            //Toolkit.getDefaultToolkit().beep();
                            playNotifySound();
                            toFront();
                        }
                    });
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "Installation failed", ex);
                String errorMessage = STRINGS.getString(
                        "Installation_Failed_With_Exception");
                errorMessage = MessageFormat.format(
                        errorMessage, ex.getMessage());
                showErrorMessage(errorMessage);
                switchToInstallSelection();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Installation failed", ex);
                String errorMessage = STRINGS.getString(
                        "Installation_Failed_With_Exception");
                errorMessage = MessageFormat.format(
                        errorMessage, ex.getMessage());
                showErrorMessage(errorMessage);
                switchToInstallSelection();
            }
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

            } else if ("line".equals(propertyName)) {
                // rsync updates
                // parse lines that end with "... to-check=100/800)"
                String line = (String) evt.getNewValue();
                Matcher matcher = rsyncPattern.matcher(line);
                if (matcher.matches()) {
                    String toCheckString = matcher.group(1);
                    String fileCountString = matcher.group(2);
                    try {
                        int filesToCheck = Integer.parseInt(toCheckString);
                        int fileCount = Integer.parseInt(fileCountString);
                        int progress =
                                (fileCount - filesToCheck) * 100 / fileCount;
                        // Because of the rsync algorithm it can happen that the
                        // progress value temporarily decreases. This is very
                        // confusing for users. We hide this ugly details by not
                        // updating the value if it decreases...
                        if (progress > rsyncProgress) {
                            rsyncProgress = progress;
                            SwingUtilities.invokeLater(new Runnable() {

                                @Override
                                public void run() {
                                    rsyncPogressBar.setValue(rsyncProgress);
                                    setTitle(rsyncProgress + "% "
                                            + STRINGS.getString("Copied") + " ("
                                            + currentDevice + '/'
                                            + selectionCount + ')');
                                }
                            });
                        }

                    } catch (NumberFormatException ex) {
                        LOGGER.log(Level.WARNING,
                                "could not parse rsync output", ex);
                    }
                }
            }
        }

        private boolean copyToStorageDevice(StorageDevice storageDevice)
                throws InterruptedException, IOException {

            // determine size and state
            String device = storageDevice.getDevice();
            long size = storageDevice.getSize();
            Partitions partitions = getPartitions(storageDevice);
            int exchangeMB = partitions.getExchangeMB();
            PartitionState partitionState =
                    getPartitionState(size, systemSizeEnlarged);

            boolean sdDevice = storageDevice instanceof SDStorageDevice;

            // determine devices
            String exchangeDevice = device + (sdDevice ? "p1" : '1');
            String persistentDevice = null;
            String systemDevice = null;
            switch (partitionState) {
                case ONLY_SYSTEM:
                    systemDevice = device + (sdDevice ? "p1" : '1');
                    break;

                case PERSISTENT:
                    persistentDevice = device + (sdDevice ? "p1" : '1');
                    systemDevice = device + (sdDevice ? "p2" : '2');
                    break;

                case EXCHANGE:
                    if (exchangeMB == 0) {
                        // create two partitions:
                        // persistent, system
                        persistentDevice = device + (sdDevice ? "p1" : '1');
                        systemDevice = device + (sdDevice ? "p2" : '2');
                    } else {
                        if (partitions.getPersistencyMB() == 0) {
                            // create two partitions:
                            // exchange, system
                            systemDevice = device + (sdDevice ? "p2" : '2');
                        } else {
                            // create three partitions:
                            // exchange, persistent, system
                            persistentDevice = device + (sdDevice ? "p2" : '2');
                            systemDevice = device + (sdDevice ? "p3" : '3');
                        }
                    }
                    break;

                default:
                    String errorMessage = "unsupported partitionState \""
                            + partitionState + '\"';
                    LOGGER.severe(errorMessage);
                    showErrorMessage(errorMessage);
                    return false;
            }

            // create all necessary partitions
            if (!createPartitions(storageDevice, partitions, size, exchangeMB,
                    partitionState, exchangeDevice, systemDevice,
                    persistentDevice, false)) {
                // On some Corsari Flash Voyager GT drives the first sfdisk try
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
                if (!createPartitions(storageDevice, partitions, size,
                        exchangeMB, partitionState, exchangeDevice,
                        systemDevice, persistentDevice, true)) {
                    return false;
                }
            }

            // copy operating system files
            if (!copyExchangeAndSystem(exchangeDevice, systemDevice)) {
                return false;
            }

            // copy persistency layer
            if (!copyPersistency(persistentDevice)) {
                return false;
            }

            // make usb flash drive bootable
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(installCardPanel,
                            "installIndeterminateProgressPanel");
                    installIndeterminateProgressBar.setString(
                            STRINGS.getString("Writing_Boot_Sector"));
                }
            });
            return makeBootable(device, systemDevice);
        }

        private boolean createPartitions(StorageDevice storageDevice,
                Partitions partitions, long size, int exchangeMB,
                final PartitionState partitionState, String exchangeDevice,
                String systemDevice, String persistentDevice,
                boolean showErrorMessages)
                throws InterruptedException, IOException {

            // update GUI
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(installCardPanel,
                            "installIndeterminateProgressPanel");
                    boolean severalPartitions =
                            (partitionState == PartitionState.PERSISTENT)
                            || (partitionState == PartitionState.EXCHANGE);
                    installIndeterminateProgressBar.setString(
                            STRINGS.getString(severalPartitions
                            ? "Creating_File_Systems"
                            : "Creating_File_System"));
                }
            });

            // determine exact partition sizes
            String device = storageDevice.getDevice();
            long overhead = size - systemSizeEnlarged;
            int persistentMB = partitions.getPersistencyMB();
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "size of {0} = {1} Byte\n"
                        + "overhead = {2} Byte\n"
                        + "exchangeMB = {3} MiB\n"
                        + "persistentMB = {4} MiB",
                        new Object[]{
                            device, size, overhead, exchangeMB, persistentMB
                        });
            }

            // assemble partition command
            List<String> partedCommandList = new ArrayList<String>();
            partedCommandList.add("parted");
            partedCommandList.add("-s");
            partedCommandList.add("-a");
            partedCommandList.add("optimal");
            partedCommandList.add(device);
            partedCommandList.add("mklabel");
            partedCommandList.add("msdos");

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
                    mkpart(partedCommandList, "0%", "100%");
                    setFlag(partedCommandList, "1", "boot", "on");
                    setFlag(partedCommandList, "1", "lba", "on");
                    break;

                case PERSISTENT:
                    String persistentBorder = persistentMB + "M";
                    mkpart(partedCommandList, "0%", persistentBorder);
                    mkpart(partedCommandList, persistentBorder, "100%");
                    setFlag(partedCommandList, "2", "boot", "on");
                    setFlag(partedCommandList, "2", "lba", "on");
                    break;

                case EXCHANGE:
                    if (exchangeMB == 0) {
                        // create two partitions:
                        // persistent, system
                        persistentBorder = persistentMB + "M";
                        mkpart(partedCommandList, "0%", persistentBorder);
                        mkpart(partedCommandList, persistentBorder, "100%");
                        setFlag(partedCommandList, "2", "boot", "on");
                        setFlag(partedCommandList, "2", "lba", "on");

                    } else {
                        String exchangeBorder = exchangeMB + "M";
                        if (persistentMB == 0) {
                            // create two partitions:
                            // exchange, system
                            mkpart(partedCommandList, "0%", exchangeBorder);
                            mkpart(partedCommandList, exchangeBorder, "100%");
                            setFlag(partedCommandList, "2", "boot", "on");
                            setFlag(partedCommandList, "1", "lba", "on");
                            setFlag(partedCommandList, "2", "lba", "on");
                        } else {
                            // create three partitions:
                            // exchange, persistent, system
                            persistentBorder = (exchangeMB + persistentMB) + "M";
                            mkpart(partedCommandList, "0%", exchangeBorder);
                            mkpart(partedCommandList, exchangeBorder, persistentBorder);
                            mkpart(partedCommandList, persistentBorder, "100%");
                            setFlag(partedCommandList, "3", "boot", "on");
                            setFlag(partedCommandList, "1", "lba", "on");
                            setFlag(partedCommandList, "3", "lba", "on");
                        }
                    }
                    break;

                default:
                    String errorMessage = "unsupported partitionState \""
                            + partitionState + '\"';
                    LOGGER.severe(errorMessage);
                    if (showErrorMessages) {
                        showErrorMessage(errorMessage);
                    }
                    return false;
            }

            // safety wait in case of device scanning
            // 5 seconds were not enough...
            Thread.sleep(7000);

            // check if a swap partition is active on this device
            // if so, switch it off
            List<String> swaps = readFile(new File("/proc/swaps"));
            for (String swapLine : swaps) {
                if (swapLine.startsWith(device)) {
                    swapoffPartition(device, swapLine);
                }
            }

            // umount all mounted partitions of device
            umountPartitions(device);

            // repartition device
            String[] commandArray = partedCommandList.toArray(
                    new String[partedCommandList.size()]);
            int exitValue = processExecutor.executeProcess(commandArray);
            if (exitValue != 0) {
                String errorMessage = STRINGS.getString("Error_Repartitioning");
                errorMessage = MessageFormat.format(errorMessage, device);
                LOGGER.severe(errorMessage);
                if (showErrorMessages) {
                    showErrorMessage(errorMessage);
                }
                return false;
            }

            // safety wait so that new partitions are known to the system
            Thread.sleep(5000);

            // create file systems
            switch (partitionState) {
                case ONLY_SYSTEM:
                    return formatSystemPartition(
                            systemDevice, showErrorMessages);

                case PERSISTENT:
                    return formatPersistentPartition(
                            persistentDevice, showErrorMessages)
                            && formatSystemPartition(
                            systemDevice, showErrorMessages);

                case EXCHANGE:
                    if (exchangeMB != 0) {
                        // create file system for exchange partition
                        String exchangePartitionLabel =
                                exchangePartitionTextField.getText();
                        exitValue = processExecutor.executeProcess(
                                "mkfs.vfat", "-n", exchangePartitionLabel,
                                exchangeDevice);
                        if (exitValue != 0) {
                            String errorMessage =
                                    "Can not create exchange partition!";
                            LOGGER.severe(errorMessage);
                            if (showErrorMessages) {
                                showErrorMessage(errorMessage);
                            }
                            return false;
                        }
                    }
                    if ((persistentDevice != null)
                            && (!formatPersistentPartition(
                            persistentDevice, showErrorMessages))) {
                        return false;
                    }
                    return formatSystemPartition(
                            systemDevice, showErrorMessages);

                default:
                    LOGGER.log(Level.SEVERE,
                            "unsupported partitionState \"{0}\"",
                            partitionState);
                    return false;
            }
        }

        private void mkpart(List<String> commandList,
                String start, String end) {
            commandList.add("mkpart");
            commandList.add("primary");
            commandList.add(start);
            commandList.add(end);
        }

        private void setFlag(List<String> commandList, String partition, String flag, String value) {
            commandList.add("set");
            commandList.add(partition);
            commandList.add(flag);
            commandList.add(value);
        }

        private boolean copyExchangeAndSystem(
                String exchangeDevice, String systemDevice)
                throws InterruptedException, IOException {
            File mountPoint = new File(MOUNT_POINT);
            if (mountPoint.exists()) {
                String mountedVolume =
                        getHalUDI("volume.mount_point", mountPoint.getPath());
                if ((mountedVolume != null) && (mountedVolume.length() > 0)) {
                    umount(MOUNT_POINT);
                }
                Thread.sleep(1000);
            } else {
                int returnValue =
                        processExecutor.executeProcess("mkdir", MOUNT_POINT);
                if (returnValue != 0) {
                    String errorMessage = "Could not create mount "
                            + "point \"" + MOUNT_POINT + '\"';
                    LOGGER.severe(errorMessage);
                    showErrorMessage(errorMessage);
                    return false;
                }
            }

            if (!mount(systemDevice, MOUNT_POINT, "umask=0")) {
                return false;
            }
            Thread.sleep(1000);
            installFileCopierPanel.setFileCopier(fileCopier);
            fileCopier.addPropertyChangeListener(
                    FileCopier.BYTE_COUNTER_PROPERTY, this);

            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(installCardPanel, "installCopyPanel");
                }
            });

            copyDebianFiles(fileCopier, exchangeDevice);

            umount(MOUNT_POINT);
            return true;
        }

        private boolean copyDebianFiles(FileCopier fileCopier,
                String exchangeDestination) throws IOException {
            String exchangeSourcePath = null;
            boolean exchangeSourceTempMounted = false;
            String exchangeDestinationPath = null;
            CopyJob exchangeCopyJob = null;
            if (copyExchangeCheckBox.isSelected()) {
                // check that source and destination partitions are mounted
                String mountPoint = getHalProperty(
                        exchangeSourcePartitionUDI, "volume.mount_point");
                if (mountPoint.length() > 0) {
                    exchangeSourcePath = mountPoint;
                } else {
                    exchangeSourcePath =
                            createTempDir("exchange_source").getPath();
                    if (!mount(exchangeSourcePartition, exchangeSourcePath)) {
                        return false;
                    }
                    exchangeSourceTempMounted = true;
                }
                exchangeDestinationPath =
                        createTempDir("exchange_destination").getPath();
                if (!mount(exchangeDestination, exchangeDestinationPath,
                        "umask=0")) {
                    return false;
                }
                exchangeCopyJob = new CopyJob(
                        new Source[]{new Source(exchangeSourcePath, ".*")},
                        new String[]{exchangeDestinationPath});
            }

            CopyJob systemCopyJob = new CopyJob(
                    new Source[]{new Source(DEBIAN_LIVE_SYSTEM_PATH, ".*")},
                    new String[]{MOUNT_POINT});
            fileCopier.copy(systemCopyJob, exchangeCopyJob);

            /**
             * When we want to copy the data partition, we have to run the
             * system in test mode. But the copies should run per default in
             * normal (persistent) mode. Therefore we have to enforce
             * persistency here.
             */
            Pattern pattern = Pattern.compile("PERSISTENT=\".*\"");
            replaceText(MOUNT_POINT + "/live/live.cfg",
                    pattern, "PERSISTENT=\"Yes\"");
            pattern = Pattern.compile("NOPERSISTENT=\".*\"");
            replaceText(MOUNT_POINT + "/live/live.cfg",
                    pattern, "NOPERSISTENT=\"\"");

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

            // umount temporarily mounted partitions
            if (exchangeSourceTempMounted) {
                umount(exchangeSourcePath);
            }
            if (exchangeDestinationPath != null) {
                umount(exchangeDestinationPath);
            }

            // isolinux -> syslinux renaming
            if (!bootDeviceIsUSB) {
                isolinuxToSyslinux(MOUNT_POINT);
            }

            return true;
        }

        private boolean copyPersistency(String persistentDevice)
                throws IOException, InterruptedException {
            // copy persistency partition
            if (copyPersistencyCheckBox.isSelected()) {

                // mount persistency source
                String persistencySourcePath = mountPersistencySource();
                if (persistencySourcePath == null) {
                    // there was an error in mountPersistencySource()
                    // error handling is done there
                    // just exit here...
                    return false;
                }

                // mount persistency destination
                String persistencyDestinationPath =
                        createTempDir("persistency_destination").getPath();
                if (!mount(persistentDevice, persistencyDestinationPath)) {
                    return false;
                }

                // TODO: use filecopier as soon as it supports symlinks etc.
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        showCard(installCardPanel, "rsyncPanel");
                    }
                });
                Thread.sleep(1000);
                rsyncProgress = 0;
                processExecutor.addPropertyChangeListener(this);
                int exitValue = processExecutor.executeProcess("rsync", "-av",
                        "--no-inc-recursive", "--progress",
                        persistencySourcePath + '/',
                        persistencyDestinationPath + '/');
                processExecutor.removePropertyChangeListener(this);
                if (exitValue != 0) {
                    String errorMessage =
                            "Could not copy persistency layer!";
                    LOGGER.severe(errorMessage);
                    showErrorMessage(errorMessage);
                    return false;
                }

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

                // umount persistency partitions
                umount(persistencySourcePath);
                umount(persistencyDestinationPath);
            }

            return true;
        }

        private boolean formatPersistentPartition(
                String device, boolean showErrorMessage) {
            int exitValue = processExecutor.executeProcess(
                    "mkfs.ext2", "-L", "live-rw", device);
            if (exitValue != 0) {
                LOGGER.severe(processExecutor.getOutput());
                String errorMessage =
                        STRINGS.getString("Error_Create_Data_Partition");
                LOGGER.severe(errorMessage);
                if (showErrorMessage) {
                    showErrorMessage(errorMessage);
                }
                return false;
            }
            exitValue = processExecutor.executeProcess(
                    "tune2fs", "-m", "0", "-c", "0", "-i", "0", device);
            if (exitValue != 0) {
                LOGGER.severe(processExecutor.getOutput());
                String errorMessage =
                        STRINGS.getString("Error_Tune_Data_Partition");
                LOGGER.severe(errorMessage);
                if (showErrorMessage) {
                    showErrorMessage(errorMessage);
                }
                return false;
            }
            return true;
        }
    }

    private boolean formatSystemPartition(
            String device, boolean showErrorMessage) {
        // hint: the partition label can be only 11 characters long!
        int exitValue = processExecutor.executeProcess(
                "mkfs.vfat", "-n", systemPartitionLabel, device);
        if (exitValue != 0) {
            LOGGER.severe(processExecutor.getOutput());
            String errorMessage =
                    STRINGS.getString("Error_Create_System_Partition");
            LOGGER.severe(errorMessage);
            if (showErrorMessage) {
                showErrorMessage(errorMessage);
            }
            return false;
        }
        return true;
    }

    private void isolinuxToSyslinux(String mountPoint) throws IOException {
        final String isolinuxPath = mountPoint + "/isolinux";
        if (new File(isolinuxPath).exists()) {
            LOGGER.info("replacing isolinux with syslinux");
            final String syslinuxPath = mountPoint + "/syslinux";
            moveFile(isolinuxPath, syslinuxPath);
            moveFile(syslinuxPath + "/isolinux.bin",
                    syslinuxPath + "/syslinux.bin");
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
        } else {
            // boot device is probably a hard disk
            LOGGER.info(
                    "isolinux directory does not exist -> no renaming");
        }
    }

    private boolean makeBootable(String device, String systemDevice)
            throws IOException {

        int exitValue = processExecutor.executeProcess(true, true,
                "syslinux", "-d", "syslinux", systemDevice);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Make_Bootable_Failed");
            errorMessage = MessageFormat.format(errorMessage,
                    systemDevice, processExecutor.getOutput());
            LOGGER.severe(errorMessage);
            showErrorMessage(errorMessage);
            return false;
        }
        exitValue = processExecutor.executeScript(
                "cat " + SYSLINUX_MBR_PATH + " > " + device + '\n'
                + "sync");
        if (exitValue != 0) {
            String errorMessage = "could not copy syslinux Master Boot Record "
                    + "to device \"" + device + '\"';
            LOGGER.severe(errorMessage);
            showErrorMessage(errorMessage);
            return false;
        }

        return true;
    }

    private void playNotifySound() {
        URL url = getClass().getResource("/dlcopy/KDE_Notify.wav");
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
            List<String> lines = readFile(file);
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
            String errorMessage =
                    STRINGS.getString("Error_File_Does_Not_Exist");
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

    private void checkInstallSelection() throws IOException {

        // check all selected target USB storage devices
        int[] selectedIndices = installStorageDeviceList.getSelectedIndices();
        boolean harddiskSelected = false;
        for (int i : selectedIndices) {
            StorageDevice storageDevice =
                    (StorageDevice) installStorageDeviceListModel.getElementAt(i);
            if (storageDevice instanceof Harddisk) {
                harddiskSelected = true;
            }
            Partitions partitions = getPartitions(storageDevice);
            if (!checkPersistency(partitions)) {
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
            String input = JOptionPane.showInputDialog(this, message,
                    STRINGS.getString("Warning"), JOptionPane.WARNING_MESSAGE);
            if (!expectedInput.equals(input)) {
                return;
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
        long sourceExchangeSize = getExchangePartitionSize();
        long targetExchangeSize =
                (long) partitions.getExchangeMB() * (long) MEGA;
        if (sourceExchangeSize > targetExchangeSize) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_Target_Exchange_Too_Small"),
                    STRINGS.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        return true;
    }

    private boolean isUnmountedPersistencyAvailable() throws IOException {
        // check that persistency is available and make sure it is not mounted
        if (persistencyUDI == null) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_No_Persistency"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String persistencyMountPoint =
                getHalProperty(persistencyUDI, "volume.mount_point");
        if ((persistencyMountPoint != null)
                && persistencyMountPoint.length() > 0) {
            if (persistencyBoot) {
                // error and hint
                String message = STRINGS.getString(
                        "Warning_Persistency_Mounted") + "\n"
                        + STRINGS.getString("Hint_Nonpersistent_Boot");
                JOptionPane.showMessageDialog(this, message,
                        STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
                return false;

            } else {
                // warning and offer umount
                String message = STRINGS.getString(
                        "Warning_Persistency_Mounted") + "\n"
                        + STRINGS.getString("Umount_Question");
                int returnValue = JOptionPane.showConfirmDialog(this, message,
                        STRINGS.getString("Warning"), JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (returnValue == JOptionPane.YES_OPTION) {
                    umount(persistencyMountPoint);
                    return isUnmountedPersistencyAvailable();
                }
            }
        }
        return true;
    }

    private boolean checkPersistency(Partitions partitions) throws IOException {

        if (!copyPersistencyCheckBox.isSelected()) {
            return true;
        }

        if (!isUnmountedPersistencyAvailable()) {
            return false;
        }

        // check if the target stick actually has a persistency partition
        if (partitions.getPersistencyMB() == 0) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_No_Persistency_At_Target"),
                    STRINGS.getString("Error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // check that target partition is large enough
        long targetPersistencySize =
                (long) partitions.getPersistencyMB() * (long) MEGA;
        if (persistencySize > targetPersistencySize) {
            JOptionPane.showMessageDialog(this,
                    STRINGS.getString("Error_Target_Persistency_Too_Small"),
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
        state = State.ISO_INFORMATION;
    }

    private void switchToUSBInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        executionLabel.setText(STRINGS.getString("Installation_Label"));
        showCard(cardPanel, "installInfoPanel");
        enableNextButton();
        state = State.INSTALL_INFORMATION;
    }

    private void switchToUpgradeInformation() {
        setLabelHighlighted(infoStepLabel, true);
        setLabelHighlighted(selectionLabel, false);
        executionLabel.setText(STRINGS.getString("Upgrade_Label"));
        showCard(cardPanel, "upgradeInfoPanel");
        enableNextButton();
        state = State.UPGRADE_INFORMATION;
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
            freeSpaceTextField.setText(getDataVolumeString(freeSpace, 1));
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

    private class Upgrader
            extends SwingWorker<Void, Void>
            implements PropertyChangeListener {

        private final FileCopier fileCopier = new FileCopier();
        private int selectionCount;
        private int currentDevice;

        @Override
        protected Void doInBackground() throws Exception {

            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(cardPanel, "upgradePanel");
                }
            });

            // upgrade all selected storage devices
            int[] selectedIndices =
                    upgradeStorageDeviceList.getSelectedIndices();
            selectionCount = selectedIndices.length;
            for (int i : selectedIndices) {

                StorageDevice storageDevice =
                        (StorageDevice) upgradeStorageDeviceListModel.get(i);

                // update overall progress message
                currentDevice++;
                String pattern = STRINGS.getString("Upgrade_Device_Info");
                final String message = MessageFormat.format(pattern,
                        currentDevice, selectionCount,
                        storageDevice.getVendor() + " "
                        + storageDevice.getModel(),
                        " (" + DLCopy.STRINGS.getString("Size") + ": "
                        + getDataVolumeString(storageDevice.getSize(), 1) + ", "
                        + DLCopy.STRINGS.getString("Revision") + ": "
                        + storageDevice.getRevision() + ", "
                        + DLCopy.STRINGS.getString("Serial") + ": "
                        + storageDevice.getSerial() + ", "
                        + storageDevice.getDevice().replace("/", "&#47;")
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

                try {
                    upgradeDataPartition(storageDevice);

                    if (upgradeSystemPartitionCheckBox.isSelected()) {
                        upgradeSystemPartition(storageDevice);
                    }
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "", ex);
                }
                LOGGER.log(Level.INFO, "upgrading of storage device finished: "
                        + "{0} of {1} ({2})", new Object[]{
                            currentDevice, selectionCount, storageDevice
                        });
            }

            return null;
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
            setTitle(STRINGS.getString("DLCopy.title"));
            if (bootDeviceIsUSB) {
                doneLabel.setText(STRINGS.getString(
                        "Upgrade_Done_From_USB_Message"));
            } else {
                doneLabel.setText(STRINGS.getString(
                        "Upgrade_Done_From_DVD_Message"));
            }
            showCard(cardPanel, "donePanel");
            previousButton.setEnabled(true);
            nextButton.setText(STRINGS.getString("Done"));
            nextButton.setIcon(new ImageIcon(getClass().getResource(
                    "/dlcopy/icons/exit.png")));
            nextButton.setEnabled(true);
            previousButton.requestFocusInWindow();
            getRootPane().setDefaultButton(previousButton);
            playNotifySound();
            toFront();
        }

        private void upgradeDataPartition(StorageDevice storageDevice)
                throws DBusException, IOException {

            // reset data partition
            Partition dataPartition = storageDevice.getDataPartition();
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Resetting_Data_Partition"));
                }
            });
            String dataMountPoint = dataPartition.mount();
            ProcessExecutor processExecutor = new ProcessExecutor();
            if (keepPrinterSettingsCheckBox.isSelected()) {
                processExecutor.executeProcess("find", dataMountPoint,
                        "!", "-regex", dataMountPoint,
                        "!", "-regex", dataMountPoint + "/lost\\+found",
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
                        "!", "-regex", dataMountPoint + "/home.*",
                        "-exec", "rm", "-rf", "{}", ";");
            }
            if (reactivateWelcomeCheckBox.isSelected()) {
                try {
                    File propertiesFile = new File(dataMountPoint
                            + "/home/user/.config/lernstickWelcome");
                    FileReader reader = new FileReader(propertiesFile);
                    Properties lernstickWelcomeProperties = new Properties();
                    lernstickWelcomeProperties.load(reader);
                    lernstickWelcomeProperties.setProperty(
                            "ShowAtStartup", "true");
                    FileWriter writer = new FileWriter(propertiesFile);
                    lernstickWelcomeProperties.store(
                            writer, "lernstick Welcome dialog properties");
                    reader.close();
                    writer.close();
                } catch (IOException iOException) {
                    LOGGER.log(Level.WARNING, "", iOException);
                }
            }
            // process list of files to overwrite
            for (int i = 0, size = upgradeOverwriteListModel.size();
                    i < size; i++) {
                // remove old version
                String path = (String) upgradeOverwriteListModel.get(i);
                File fileToDelete = new File(dataMountPoint, path);
                FileTools.recursiveDelete(fileToDelete, true);

                // create target directory
                File parentFile = fileToDelete.getParentFile();
                parentFile.mkdirs();

                // recursive copy
                processExecutor.executeProcess("cp", "-a",
                        path, parentFile.getPath());
            }

            /**
             * TODO: becaus of the previous copy operation this call very often
             * results in the following exception:
             * org.freedesktop.DBus$Error$NoReply: No reply within specified time
             * at org.freedesktop.dbus.RemoteInvocationHandler.executeRemoteMethod(RemoteInvocationHandler.java:133)
             * at org.freedesktop.dbus.RemoteInvocationHandler.invoke(RemoteInvocationHandler.java:188)
             * at $Proxy2.FilesystemUnmount(Unknown Source)
             */
            dataPartition.umount();
        }

        private void upgradeSystemPartition(StorageDevice storageDevice)
                throws DBusException, IOException {
            Partition dataPartition = storageDevice.getDataPartition();
            Partition systemPartition = storageDevice.getSystemPartition();

            if (storageDevice.needsRepartitioning()) {
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
                String dataDevPath = "/dev/" + dataPartition.getDevice();
                processExecutor.executeProcess(true, true,
                        "e2fsck", "-f", "-y", "-v", dataDevPath);
                processExecutor.executeProcess(true, true,
                        "resize2fs", "-M", "-p", dataDevPath);
                long dataPartitionOffset = dataPartition.getOffset();
                long newSystemPartitionOffset = systemPartition.getOffset()
                        + systemPartition.getSize()
                        - (long) (systemSize * 1.01);
                // align newSystemPartitionOffset
                newSystemPartitionOffset /= MEGA;
                String start = String.valueOf(dataPartitionOffset) + "B";
                String border =
                        String.valueOf(newSystemPartitionOffset) + "MB";
                String systemPartitionString =
                        String.valueOf(systemPartition.getNumber());
                processExecutor.executeProcess(true, true, "parted",
                        "-a", "optimal", "-s", storageDevice.getDevice(),
                        "rm", String.valueOf(dataPartition.getNumber()),
                        "rm", systemPartitionString,
                        "mkpart", "primary", start, border,
                        "mkpart", "primary", border, "100%",
                        "set", systemPartitionString, "boot", "on");
                processExecutor.executeProcess(true, true,
                        "resize2fs", dataDevPath);
                formatSystemPartition(
                        "/dev/" + systemPartition.getDevice(), true);
            }

            // upgrade system partition
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
            LOGGER.log(Level.INFO,
                    "mounting {0}", systemPartition.getDevice());
            String systemMountPoint = systemPartition.mount();
            File systemMountPointFile = new File(systemMountPoint);
            LOGGER.log(Level.INFO,
                    "recursively deleting {0}", systemMountPointFile);
            FileTools.recursiveDelete(systemMountPointFile, false);
            LOGGER.info("starting copy job");
            upgradeFileCopierPanel.setFileCopier(fileCopier);
            fileCopier.addPropertyChangeListener(
                    FileCopier.BYTE_COUNTER_PROPERTY, this);
            CopyJob systemCopyJob = new CopyJob(
                    new Source[]{new Source(DEBIAN_LIVE_SYSTEM_PATH, ".*")},
                    new String[]{systemMountPoint});
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(upgradeCardPanel, "upgradeCopyPanel");
                }
            });
            fileCopier.copy(systemCopyJob);
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    showCard(upgradeCardPanel,
                            "upgradeIndeterminateProgressPanel");
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Unmounting_File_Systems"));
                }
            });
            isolinuxToSyslinux(systemMountPoint);
            systemPartition.umount();
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    upgradeIndeterminateProgressBar.setString(
                            STRINGS.getString("Writing_Boot_Sector"));
                }
            });
            makeBootable(storageDevice.getDevice(),
                    "/dev/" + systemPartition.getDevice());
        }
    }

    private class ISOCreator
            extends SwingWorker<Boolean, String>
            implements PropertyChangeListener {

        private IsoStep step;
        private String isoPath;
        private int fileSystem;
        private int fileSystems;

        @Override
        protected Boolean doInBackground() throws Exception {
            try {

                // copy base image files
                publish(STRINGS.getString("Copying_Files"));
                String targetDirectory = tmpDirTextField.getText();
                String copyScript = "rm -rf " + targetDirectory + '\n'
                        + "mkdir " + targetDirectory + '\n'
                        + "cd /live/image\n"
                        + "find -not -name filesystem.squashfs | cpio -pvdum "
                        + targetDirectory;
                processExecutor.executeScript(copyScript);

                publish(STRINGS.getString("Mounting_Partitions"));
                File tmpDir = createTempDir("usb2iso");

                // mount base image
                File roDir = new File(tmpDir, "ro");
                roDir.mkdirs();
                String roPath = roDir.getPath();
                processExecutor.executeProcess("mount", "-t", "squashfs", "-o",
                        "loop,ro", "/live/image/live/filesystem.squashfs",
                        roPath);

                // mount persistency on top
                File rwDir = new File(tmpDir, "rw");
                rwDir.mkdirs();
                String rwPath = rwDir.getPath();
                processExecutor.executeProcess(
                        "mount", persistencyDevice, rwPath);

                // union base image with persistency
                File cowDir = new File(tmpDir, "cow");
                cowDir.mkdirs();
                String cowPath = cowDir.getPath();
                processExecutor.executeProcess("mount", "-t", "aufs", "-o",
                        "br=" + rwPath + "=rw:" + roPath + "=ro", "none",
                        cowPath);

                // move lernstick autostart script temporarily away
                boolean moveAutostart = !autoStartCheckBox.isSelected();
                String lernstickAutostart = cowDir
                        + "/etc/xdg/autostart/lernstick-autostart.desktop";
                String tmpFile = "/tmp/lernstick-autostart.desktop";
                if (moveAutostart) {
                    processExecutor.executeProcess(
                            "mv", lernstickAutostart, tmpFile);
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
                List<String> separateFileSystems = getSeparateFileSystems();
                if (separateFileSystems.isEmpty()) {
                    // build just one single squash file system
                    fileSystem = 1;
                    fileSystems = 1;
                    processExecutor.executeProcess("mksquashfs", cowPath,
                            targetDirectory + "/live/filesystem.squashfs");

                } else {
                    // build several squash file systems
                    fileSystems = 1 + separateFileSystems.size();

                    // first file system (excludes all separate file systems)
                    fileSystem = 1;
                    List<String> commandList = new ArrayList<String>();
                    commandList.add("mksquashfs");
                    commandList.add(cowPath);
                    commandList.add(
                            targetDirectory + "/live/filesystem1.squashfs");
                    for (String separateFileSystem : separateFileSystems) {
                        commandList.add("-e");
                        commandList.add(separateFileSystem);
                    }
                    String[] commandArray = new String[commandList.size()];
                    commandArray = commandList.toArray(commandArray);
                    processExecutor.executeProcess(commandArray);

                    // separate file systems (excludes everything but itself)
                    for (String separateFileSystem : separateFileSystems) {
                        processExecutor.executeProcess("mksquashfs", cowPath,
                                targetDirectory + "/live/filesystem"
                                + (++fileSystem) + ".squashfs", "-wildcards",
                                "-e", "!(" + separateFileSystem + ")");
                    }
                }
                processExecutor.removePropertyChangeListener(this);
                SwingUtilities.invokeLater(new Runnable() {

                    @Override
                    public void run() {
                        toISOProgressBar.setIndeterminate(true);
                    }
                });

                // bring back lernstick autostart script
                if (moveAutostart) {
                    processExecutor.executeProcess(
                            "mv", tmpFile, lernstickAutostart);
                }

                // umount all partitions
                umount(cowPath);
                umount(rwPath);
                umount(roPath);

                // syslinux -> isolinux
                final String ISOLINUX_DIR = targetDirectory + "/isolinux";
                moveFile(targetDirectory + "/syslinux", ISOLINUX_DIR);
                moveFile(ISOLINUX_DIR + "/syslinux.bin",
                        ISOLINUX_DIR + "/isolinux.bin");
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
                FileWriter fileWriter =
                        new FileWriter(targetDirectory + "/md5sum.txt");
                fileWriter.write(md5header);
                fileWriter.close();
                String md5Script = "cd " + targetDirectory + "\n"
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
                isoPath = targetDirectory + "/lernstick.iso";
                step = IsoStep.GENISOIMAGE;
                publish(STRINGS.getString("Creating_Image"));
                processExecutor.addPropertyChangeListener(this);
                String isoLabel = isoLabelTextField.getText();
                int returnValue = 0;
                if (isoLabel.isEmpty()) {
                    returnValue = processExecutor.executeProcess("genisoimage",
                            "-J", "-l", "-cache-inodes", "-allow-multidot",
                            "-no-emul-boot", "-boot-load-size", "4",
                            "-boot-info-table", "-r", "-b",
                            "isolinux/isolinux.bin", "-c", "isolinux/boot.cat",
                            "-o", isoPath, targetDirectory);
                } else {
                    returnValue = processExecutor.executeProcess("genisoimage",
                            "-J", "-V", isoLabel, "-l", "-cache-inodes",
                            "-allow-multidot", "-no-emul-boot",
                            "-boot-load-size", "4", "-boot-info-table", "-r",
                            "-b", "isolinux/isolinux.bin", "-c",
                            "isolinux/boot.cat", "-o", isoPath,
                            targetDirectory);
                }
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
            String message = null;
            try {
                if (get()) {
                    message = STRINGS.getString("DLCopy.isoDoneLabel.text");
                    message = MessageFormat.format(message, isoPath);
                } else {
                    message = STRINGS.getString("Error_ISO_Creation");
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            isoDoneLabel.setText(message);
            showCard(cardPanel, "toISODonePanel");
            previousButton.setEnabled(true);
            nextButton.setText(STRINGS.getString("Done"));
            nextButton.setIcon(new ImageIcon(getClass().getResource(
                    "/dlcopy/icons/exit.png")));
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
            if ("line".equals(propertyName)) {
                String line = (String) evt.getNewValue();
                switch (step) {
                    case GENISOIMAGE:
                        // genisoimage output looks like this:
                        // 89.33% done, estimate finish Wed Dec  2 17:08:41 2009
                        Matcher matcher = genisoimagePattern.matcher(line);
                        if (matcher.matches()) {
                            String progressString = matcher.group(1).trim();
                            try {
                                final int progress =
                                        Integer.parseInt(progressString);
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
                                        "could not parse genisoimage progress",
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
                                message = MessageFormat.format(message,
                                        fileSystem, fileSystems,
                                        progress + "%");
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

        private List<String> getSeparateFileSystems() {
            List<String> separateFileSystems = new ArrayList<String>();
            for (int i = 0, size = separateFileSystemsListModel.size(); i < size; i++) {
                String separateFileSystem = (String) separateFileSystemsListModel.get(i);
                // cut off the leading slash
                separateFileSystem = separateFileSystem.substring(1);
                separateFileSystems.add(separateFileSystem);
            }
            return separateFileSystems;
        }
    }

    private class SwapInfo {

        private String file;
        private long remainingFreeMemory;

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
                String warningMessage =
                        "Could not parse swaps line:\n" + swapLine;
                LOGGER.warning(warningMessage);
                throw new IOException(warningMessage);
            }

            long memFree = 0;
            pattern = Pattern.compile("\\p{Graph}+\\p{Blank}+(\\p{Graph}+).*");
            List<String> meminfo = readFile(new File("/proc/meminfo"));
            for (String meminfoLine : meminfo) {
                if (meminfoLine.startsWith("MemFree:")
                        || meminfoLine.startsWith("Buffers:")
                        || meminfoLine.startsWith("Cached:")
                        || meminfoLine.startsWith("SwapFree:")) {
                    matcher = pattern.matcher(meminfoLine);
                    if (matcher.matches()) {
                        memFree += Long.valueOf(matcher.group(1)) * 1024;
                    } else {
                        String warningMessage =
                                "Could not parse meminfo line:\n" + meminfoLine;
                        LOGGER.warning(warningMessage);
                        throw new IOException(warningMessage);
                    }
                }
            }
            remainingFreeMemory = memFree - swapSize;
        }

        /**
         * returns the swap file/partition
         * @return the swap file/partition
         */
        public String getFile() {
            return file;
        }

        /**
         * returns the remaining free memory when this swap file/partition would
         * be switched off
         * @return the remaining free memory when this swap file/partition would
         * be switched off
         */
        public long getRemainingFreeMemory() {
            return remainingFreeMemory;
        }
    }

    private class UdisksMonitorThread extends Thread {

        private ProcessExecutor executor = new ProcessExecutor();

        @Override
        public void run() {
            executor.addPropertyChangeListener(DLCopy.this);
            executor.executeProcess("udisks", "--monitor");
        }

        public void stopMonitoring() {
            executor.destroy();
        }
    }

    private class InstallStorageDeviceAdder extends SwingWorker<Void, Void> {

        private ModalDialogHandler dialogHandler;
        private Object[] selectedValues;
        private StorageDevice addedDevice;
        private boolean parsed;
        private final Lock lock = new ReentrantLock();
        private final Condition parsedCondition = lock.newCondition();

        public InstallStorageDeviceAdder(String outputLine) {
            final String addedPath = outputLine.substring(
                    UDISKS_ADDED.length()).trim();
            LOGGER.log(Level.INFO, "added path: \"{0}\"", addedPath);
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    lock.lock();
                    try {
                        addedDevice = getStorageDevice(addedPath,
                                installShowHarddiskCheckBox.isSelected());
                        LOGGER.log(Level.INFO,
                                "storage device of path {0}: {1}",
                                new Object[]{addedPath, addedDevice});
                        if (addedDevice != null) {
                            // remember selected values so that we can restore the selection
                            selectedValues = installStorageDeviceList.getSelectedValues();
                            StorageDeviceListUpdateDialog dialog =
                                    new StorageDeviceListUpdateDialog(DLCopy.this);
                            dialogHandler = new ModalDialogHandler(dialog);
                            dialogHandler.show();
                        }
                        parsed = true;
                        parsedCondition.signalAll();
                    } catch (DBusException ex) {
                        LOGGER.log(Level.SEVERE, "", ex);
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }

        @Override
        protected Void doInBackground() throws Exception {
            lock.lock();
            try {
                while (!parsed) {
                    parsedCondition.await();
                }
                if (addedDevice != null) {
                    // nothing to do right now...
                }
            } finally {
                lock.unlock();
            }
            return null;
        }

        @Override
        protected void done() {
            if (addedDevice != null) {
                List<StorageDevice> deviceList = new ArrayList<StorageDevice>();
                Object[] entries = installStorageDeviceListModel.toArray();
                for (Object entry : entries) {
                    deviceList.add((StorageDevice) entry);
                }
                deviceList.add(addedDevice);
                Collections.sort(deviceList);
                synchronized (installStorageDeviceListModel) {
                    installStorageDeviceListModel.clear();
                    for (StorageDevice device : deviceList) {
                        installStorageDeviceListModel.addElement(device);
                    }
                }
                // try to restore the previous selection
                for (Object selectedValue : selectedValues) {
                    int index = deviceList.indexOf(selectedValue);
                    if (index != -1) {
                        installStorageDeviceList.addSelectionInterval(
                                index, index);
                    }
                }
                installStorageDeviceListChanged();
                installStorageDeviceListSelectionChanged();
                dialogHandler.hide();
            }
        }
    }

    private class UpgradeStorageDeviceAdder extends SwingWorker<Void, Void> {

        private ModalDialogHandler dialogHandler;
        private Object[] selectedValues;
        private StorageDevice addedDevice;
        private boolean parsed;
        private final Lock lock = new ReentrantLock();
        private final Condition parsedCondition = lock.newCondition();

        public UpgradeStorageDeviceAdder(String outputLine) {
            final String addedPath = outputLine.substring(
                    UDISKS_ADDED.length()).trim();
            LOGGER.log(Level.INFO, "added path: \"{0}\"", addedPath);
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    lock.lock();
                    try {
                        addedDevice = getStorageDevice(addedPath,
                                upgradeShowHarddiskCheckBox.isSelected());
                        LOGGER.log(Level.INFO,
                                "storage device of path {0}: {1}",
                                new Object[]{addedPath, addedDevice});
                        if (addedDevice != null) {
                            // remember selected values so that we can restore the selection
                            selectedValues = upgradeStorageDeviceList.getSelectedValues();
                            StorageDeviceListUpdateDialog dialog =
                                    new StorageDeviceListUpdateDialog(DLCopy.this);
                            dialogHandler = new ModalDialogHandler(dialog);
                            dialogHandler.show();
                        }
                        parsed = true;
                        parsedCondition.signalAll();
                    } catch (DBusException ex) {
                        LOGGER.log(Level.SEVERE, "", ex);
                    } finally {
                        lock.unlock();
                    }
                }
            });
        }

        @Override
        protected Void doInBackground() throws Exception {
            lock.lock();
            try {
                while (!parsed) {
                    parsedCondition.await();
                }
                if (addedDevice != null) {
                    // init all infos so that later rendering does not block
                    // in Swing Event Thread
                    addedDevice.canBeUpgraded();
                    for (Partition partition : addedDevice.getPartitions()) {
                        try {
                            partition.getUsableSpace();
                        } catch (Exception ignored) {
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
            return null;
        }

        @Override
        protected void done() {
            if (addedDevice != null) {
                List<StorageDevice> deviceList = new ArrayList<StorageDevice>();
                Object[] entries = upgradeStorageDeviceListModel.toArray();
                for (Object entry : entries) {
                    deviceList.add((StorageDevice) entry);
                }
                deviceList.add(addedDevice);
                Collections.sort(deviceList);
                synchronized (upgradeStorageDeviceListModel) {
                    upgradeStorageDeviceListModel.clear();
                    for (StorageDevice device : deviceList) {
                        upgradeStorageDeviceListModel.addElement(device);
                    }
                }
                // try to restore the previous selection
                for (Object selectedValue : selectedValues) {
                    int index = deviceList.indexOf(selectedValue);
                    if (index != -1) {
                        upgradeStorageDeviceList.addSelectionInterval(
                                index, index);
                    }
                }
                upgradeStorageDeviceListChanged();
                upgradeStorageDeviceListSelectionChanged();
                dialogHandler.hide();
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
            StorageDeviceListUpdateDialog dialog =
                    new StorageDeviceListUpdateDialog(DLCopy.this);
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
            // remember selected values so that we can restore the selection
            selectedValues = installStorageDeviceList.getSelectedValues();
            showHardDisks = installShowHarddiskCheckBox.isSelected();
        }

        @Override
        protected Void doInBackground() throws Exception {
            synchronized (installStorageDeviceListModel) {
                installStorageDeviceListModel.clear();
                try {
                    storageDevices = getStorageDevices(showHardDisks);
                    Collections.sort(storageDevices);
                    for (StorageDevice device : storageDevices) {
                        installStorageDeviceListModel.addElement(device);
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                } catch (DBusException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
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

    private class UpgradeStorageDeviceListUpdater
            extends SwingWorker<Void, Void> {

        private final ModalDialogHandler dialogHandler;
        private final Object[] selectedValues;
        private final boolean showHardDisks;
        private List<StorageDevice> storageDevices;

        public UpgradeStorageDeviceListUpdater() {
            StorageDeviceListUpdateDialog dialog =
                    new StorageDeviceListUpdateDialog(DLCopy.this);
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
            // remember selected values so that we can restore the selection
            selectedValues = upgradeStorageDeviceList.getSelectedValues();
            showHardDisks = upgradeShowHarddiskCheckBox.isSelected();
            upgradeStorageDeviceListModel.clear();
        }

        @Override
        protected Void doInBackground() throws Exception {
            try {
                storageDevices = getStorageDevices(showHardDisks);
                Collections.sort(storageDevices);
                // init all infos so that later rendering does not block
                // in Swing Event Thread
                for (StorageDevice device : storageDevices) {
                    device.canBeUpgraded();
                    for (Partition partition : device.getPartitions()) {
                        try {
                            partition.getUsableSpace();
                        } catch (Exception ignored) {
                        }
                    }
                }

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
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
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox autoStartCheckBox;
    private javax.swing.JPanel cardPanel;
    private javax.swing.JLabel choiceLabel;
    private javax.swing.JPanel choicePanel;
    private javax.swing.JCheckBox copyExchangeCheckBox;
    private javax.swing.JPanel copyPartitionPanel;
    private javax.swing.JCheckBox copyPersistencyCheckBox;
    private javax.swing.JPanel createPartitionPanel;
    private javax.swing.JLabel currentlyInstalledDeviceLabel;
    private javax.swing.JLabel currentlyUpgradedDeviceLabel;
    private javax.swing.JLabel dataDefinitionLabel;
    private javax.swing.JLabel doneLabel;
    private javax.swing.JPanel donePanel;
    private javax.swing.JLabel exchangeDefinitionLabel;
    private javax.swing.JLabel exchangePartitionLabel;
    private javax.swing.JLabel exchangePartitionSizeLabel;
    private javax.swing.JSlider exchangePartitionSizeSlider;
    private javax.swing.JTextField exchangePartitionSizeTextField;
    private javax.swing.JLabel exchangePartitionSizeUnitLabel;
    private javax.swing.JTextField exchangePartitionTextField;
    private javax.swing.JLabel executionLabel;
    private javax.swing.JPanel executionPanel;
    private javax.swing.JLabel freeSpaceLabel;
    private javax.swing.JTextField freeSpaceTextField;
    private javax.swing.JButton imageButton;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JLabel infoStepLabel;
    private javax.swing.JButton installButton;
    private javax.swing.JPanel installCardPanel;
    private javax.swing.JPanel installCopyPanel;
    private ch.fhnw.filecopier.FileCopierPanel installFileCopierPanel;
    private javax.swing.JProgressBar installIndeterminateProgressBar;
    private javax.swing.JPanel installIndeterminateProgressPanel;
    private javax.swing.JPanel installInfoPanel;
    private javax.swing.JPanel installListPanel;
    private javax.swing.JLabel installNoMediaLabel;
    private javax.swing.JPanel installNoMediaPanel;
    private javax.swing.JPanel installPanel;
    private javax.swing.JPanel installSelectionCardPanel;
    private javax.swing.JLabel installSelectionHeaderLabel;
    private javax.swing.JPanel installSelectionPanel;
    private javax.swing.JCheckBox installShowHarddiskCheckBox;
    private javax.swing.JList installStorageDeviceList;
    private javax.swing.JLabel isoDoneLabel;
    private javax.swing.JLabel isoLabelLabel;
    private javax.swing.JTextField isoLabelTextField;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JCheckBox keepPrinterSettingsCheckBox;
    private javax.swing.JButton nextButton;
    private javax.swing.JLabel osDefinitionLabel;
    private javax.swing.JButton previousButton;
    private javax.swing.JCheckBox reactivateWelcomeCheckBox;
    private javax.swing.JPanel rsyncPanel;
    private javax.swing.JProgressBar rsyncPogressBar;
    private javax.swing.JLabel selectionLabel;
    private javax.swing.JButton separateFileSystemsAddButton;
    private javax.swing.JButton separateFileSystemsEditButton;
    private javax.swing.JList separateFileSystemsList;
    private javax.swing.JPanel separateFileSystemsPanel;
    private javax.swing.JButton separateFileSystemsRemoveButton;
    private javax.swing.JScrollPane separateFileSystemsScrollpane;
    private javax.swing.JLabel stepsLabel;
    private javax.swing.JPanel stepsPanel;
    private javax.swing.JScrollPane storageDeviceListScrollPane;
    private javax.swing.JLabel tmpDirLabel;
    private javax.swing.JButton tmpDirSelectButton;
    private javax.swing.JTextField tmpDirTextField;
    private javax.swing.JLabel tmpDriveInfoLabel;
    private javax.swing.JPanel toISODonePanel;
    private javax.swing.JLabel toISOInfoLabel;
    private javax.swing.JPanel toISOInfoPanel;
    private javax.swing.JProgressBar toISOProgressBar;
    private javax.swing.JPanel toISOProgressPanel;
    private javax.swing.JPanel toISOSelectionPanel;
    private javax.swing.JButton upgradeButton;
    private javax.swing.JPanel upgradeCardPanel;
    private javax.swing.JPanel upgradeCopyPanel;
    private javax.swing.JLabel upgradeDataDefinitionLabel;
    private javax.swing.JLabel upgradeExchangeDefinitionLabel;
    private ch.fhnw.filecopier.FileCopierPanel upgradeFileCopierPanel;
    private javax.swing.JProgressBar upgradeIndeterminateProgressBar;
    private javax.swing.JPanel upgradeIndeterminateProgressPanel;
    private javax.swing.JLabel upgradeInfoLabel;
    private javax.swing.JPanel upgradeInfoPanel;
    private javax.swing.JLabel upgradeNoMediaLabel;
    private javax.swing.JPanel upgradeNoMediaPanel;
    private javax.swing.JLabel upgradeOsDefinitionLabel;
    private javax.swing.JButton upgradeOverwriteAddButton;
    private javax.swing.JButton upgradeOverwriteEditButton;
    private javax.swing.JList upgradeOverwriteList;
    private javax.swing.JPanel upgradeOverwritePanel;
    private javax.swing.JButton upgradeOverwriteRemoveButton;
    private javax.swing.JScrollPane upgradeOverwriteScrollPane;
    private javax.swing.JPanel upgradePanel;
    private javax.swing.JPanel upgradeSelectionCardPanel;
    private javax.swing.JPanel upgradeSelectionConfigPanel;
    private javax.swing.JPanel upgradeSelectionDeviceListPanel;
    private javax.swing.JLabel upgradeSelectionHeaderLabel;
    private javax.swing.JPanel upgradeSelectionPanel;
    private javax.swing.JCheckBox upgradeShowHarddiskCheckBox;
    private javax.swing.JList upgradeStorageDeviceList;
    private javax.swing.JScrollPane upgradeStorageDeviceListScrollPane;
    private javax.swing.JCheckBox upgradeSystemPartitionCheckBox;
    private javax.swing.JTabbedPane upgradeTabbedPane;
    private javax.swing.JLabel writableLabel;
    private javax.swing.JTextField writableTextField;
    // End of variables declaration//GEN-END:variables
}
