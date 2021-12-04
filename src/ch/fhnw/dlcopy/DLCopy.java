package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import ch.fhnw.util.DbusTools;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;
import org.xml.sax.SAXException;

/**
 * The core class of the program
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class DLCopy {

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
     * the size of the EFI partition (given in MiB)
     */
    public static final long EFI_PARTITION_SIZE = 200;

    /**
     * the known and supported data partition modes
     */
    public static final String[] DATA_PARTITION_MODES = new String[]{
        STRINGS.getString("Read_Write"),
        STRINGS.getString("Read_Only"),
        STRINGS.getString("Not_Used")
    };

    /**
     * the label to use for the system partition
     */
    public static String systemPartitionLabel;

    private static final Logger LOGGER
            = Logger.getLogger(DLCopy.class.getName());
    private static final ProcessExecutor PROCESS_EXECUTOR
            = new ProcessExecutor();
    private static final long MINIMUM_PARTITION_SIZE = 200 * MEGA;
    private static final long MINIMUM_FREE_MEMORY = 300 * MEGA;
    private static DBusConnection dbusSystemConnection;

    static {
        try {
            dbusSystemConnection = DBusConnection.getConnection(
                    DBusConnection.SYSTEM);
        } catch (DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
            System.exit(-1);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String args[]) {
        java.awt.EventQueue.invokeLater(() -> {
            DLCopySwingGUI gui = new DLCopySwingGUI(args);
            gui.init();
            gui.setVisible(true);
        });
    }

    /**
     * returns the enlarged system size (safe size for partition creation)
     *
     * @param systemSize the original system size
     * @return the enlarged system size (safe size for partition creation)
     */
    public static long getEnlargedSystemSize(long systemSize) {
        return 100 * MEGA + (long) (systemSize * 1.1);
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
        LOGGER.log(Level.FINE, "storageSize = {0}, systemSize = {1}",
                new Object[]{storageSize, systemSize});
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
     * moves a file
     *
     * @param source the source path
     * @param destination the destination path
     * @throws IOException if moving the file fails
     */
    public static void moveFile(String source, String destination)
            throws IOException {
        Path sourcePath = Paths.get(source);
        if (Files.notExists(sourcePath)) {
            String errorMessage
                    = STRINGS.getString("Error_File_Does_Not_Exist");
            errorMessage = MessageFormat.format(errorMessage, source);
            throw new IOException(errorMessage);
        }
        try {
            Files.move(sourcePath, Paths.get(destination));
        } catch (IOException exception) {
            String errorMessage = STRINGS.getString("Error_File_Move");
            errorMessage = MessageFormat.format(
                    errorMessage, source, destination);
            throw new IOException(errorMessage, exception);
        }
    }

    /**
     * installs syslinux from an InstallationSource to a target device
     *
     * @param source the system source
     * @param device the device where the MBR should be installed
     * @param bootPartition the boot partition of the device, where syslinux is
     * installed
     * @throws IOException when an IOException occurs
     */
    public static void makeBootable(SystemSource source, String device,
            Partition bootPartition) throws IOException {

        // install syslinux
        try {
            source.installExtlinux(bootPartition);
        } catch (IOException iOException) {
            String errorMessage = STRINGS.getString("Make_Bootable_Failed");
            errorMessage = MessageFormat.format(errorMessage,
                    bootPartition, iOException.getMessage());
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // install MBR
        int exitValue = PROCESS_EXECUTOR.executeScript(
                "cat " + source.getMbrPath() + " > " + device + '\n'
                + "sync");
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Copying_MBR_Failed");
            errorMessage = MessageFormat.format(errorMessage, device);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    /**
     * Installs the installation source to a target storage device
     *
     * @param source the system source
     * @param fileCopier the Filecopier used for copying the system partition
     * @param storageDevice the target storage device
     * @param exchangePartitionLabel the label of the exchange partition
     * @param installerOrUpgrader the Installer or Upgrader that is calling this
     * method
     * @param personalDataPartitionEncryption if the persistence partition
     * should be encrypted with a personal password
     * @param personalEncryptionPassword the personal encryption password
     * @param secondaryDataPartitionEncryption if the persistence partition
     * should be encrypted with a secondary password
     * @param secondaryEncryptionPassword the secondary encryption password
     * @param randomFillDataPartition if the data partition should be filled
     * with random data before formatting
     * @param checkCopies if copies should be checked for errors
     * @param dlCopyGUI the program GUI
     * @throws InterruptedException when the installation was interrupted
     * @throws IOException when an I/O exception occurs
     * @throws DBusException when there was a problem with DBus
     * @throws java.security.NoSuchAlgorithmException when the file checking
     * algorithm can't be found
     */
    public static void copyToStorageDevice(SystemSource source,
            FileCopier fileCopier, StorageDevice storageDevice,
            String exchangePartitionLabel,
            InstallerOrUpgrader installerOrUpgrader,
            boolean personalDataPartitionEncryption,
            String personalEncryptionPassword,
            boolean secondaryDataPartitionEncryption,
            String secondaryEncryptionPassword, boolean randomFillDataPartition,
            boolean checkCopies, DLCopyGUI dlCopyGUI)
            throws InterruptedException, IOException,
            DBusException, NoSuchAlgorithmException {

        // determine size and state
        String device = "/dev/" + storageDevice.getDevice();
        long storageDeviceSize = storageDevice.getSize();
        PartitionSizes partitionSizes
                = installerOrUpgrader.getPartitionSizes(storageDevice);
        int exchangeMB = partitionSizes.getExchangeMB();
        PartitionState partitionState = getPartitionState(storageDeviceSize,
                DLCopy.getEnlargedSystemSize(source.getSystemSize()));

        StorageDevice.Type deviceType = storageDevice.getType();
        boolean pPartition
                = deviceType == StorageDevice.Type.SDMemoryCard
                || deviceType == StorageDevice.Type.NVMe;

        // determine devices
        String destinationEfiDevice = null;
        String destinationExchangeDevice = null;
        String destinationDataDevice = null;
        String destinationSystemDevice;
        switch (partitionState) {
            case ONLY_SYSTEM:
                destinationEfiDevice = device + (pPartition ? "p1" : '1');
                destinationSystemDevice = device + (pPartition ? "p2" : '2');
                break;

            case PERSISTENCE:
                destinationEfiDevice = device + (pPartition ? "p1" : '1');
                destinationDataDevice = device + (pPartition ? "p2" : '2');
                destinationSystemDevice = device + (pPartition ? "p3" : '3');
                break;

            case EXCHANGE:
                if (exchangeMB == 0) {
                    destinationEfiDevice = device + (pPartition ? "p1" : '1');
                    destinationDataDevice = device + (pPartition ? "p2" : '2');
                    destinationSystemDevice = device + (pPartition ? "p3" : '3');
                } else {
                    destinationEfiDevice
                            = device + (pPartition ? "p1" : '1');
                    destinationExchangeDevice
                            = device + (pPartition ? "p2" : '2');
                    if (partitionSizes.getPersistenceMB() == 0) {
                        destinationSystemDevice
                                = device + (pPartition ? "p3" : '3');
                    } else {
                        destinationDataDevice
                                = device + (pPartition ? "p3" : '3');
                        destinationSystemDevice
                                = device + (pPartition ? "p4" : '4');
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
            createPartitions(storageDevice, partitionSizes, storageDeviceSize,
                    partitionState, destinationExchangeDevice,
                    exchangePartitionLabel, destinationDataDevice,
                    personalDataPartitionEncryption, personalEncryptionPassword,
                    secondaryDataPartitionEncryption,
                    secondaryEncryptionPassword, randomFillDataPartition,
                    destinationEfiDevice, destinationSystemDevice,
                    installerOrUpgrader, dlCopyGUI);
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
            createPartitions(storageDevice, partitionSizes, storageDeviceSize,
                    partitionState, destinationExchangeDevice,
                    exchangePartitionLabel, destinationDataDevice,
                    personalDataPartitionEncryption, personalEncryptionPassword,
                    secondaryDataPartitionEncryption,
                    secondaryEncryptionPassword, randomFillDataPartition,
                    destinationEfiDevice, destinationSystemDevice,
                    installerOrUpgrader, dlCopyGUI);
        }

        // Here have to trigger a rescan of the device partitions. Otherwise
        // udisks sometimes just doesn't know about the new partitions and we
        // will later get exceptions similar to this one:
        // org.freedesktop.dbus.exceptions.DBusExecutionException:
        // No such interface 'org.freedesktop.UDisks2.Filesystem'
        PROCESS_EXECUTOR.executeProcess("partprobe", device);
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
                                destinationExchangeDevice.substring(5));

        Partition destinationDataPartition
                = (destinationDataDevice == null) ? null
                        : Partition.getPartitionFromDeviceAndNumber(
                                destinationDataDevice.substring(5));

        Partition destinationBootPartition
                = Partition.getPartitionFromDeviceAndNumber(
                        destinationEfiDevice.substring(5));

        Partition destinationSystemPartition
                = Partition.getPartitionFromDeviceAndNumber(
                        destinationSystemDevice.substring(5));

        // copy operating system files
        copyExchangeEfiAndSystem(source, fileCopier, storageDevice,
                destinationExchangePartition, destinationBootPartition,
                destinationSystemPartition, installerOrUpgrader, checkCopies,
                dlCopyGUI);

        // copy persistence layer
        copyPersistence(source, installerOrUpgrader,
                destinationDataPartition, dlCopyGUI);

        // make storage device bootable
        installerOrUpgrader.showWritingBootSector();
        makeBootable(source, device, destinationBootPartition);

        if (!umount(destinationBootPartition, dlCopyGUI)) {
            String errorMessage = "could not umount destination boot partition";
            throw new IOException(errorMessage);
        }

        if (!umount(destinationSystemPartition, dlCopyGUI)) {
            String errorMessage
                    = "could not umount destination system partition";
            throw new IOException(errorMessage);
        }
        source.unmountTmpPartitions();
    }

    /**
     * returns the partitions sizes for a StorageDevice when installing
     *
     * @param source the system source
     * @param storageDevice the StorageDevice to check
     * @param exchangePartitionSize the planned size of the exchange partition
     * @return the partitions sizes for a StorageDevice when installing
     */
    public static PartitionSizes getInstallPartitionSizes(
            SystemSource source, StorageDevice storageDevice,
            int exchangePartitionSize) {
        return getPartitionSizes(source, storageDevice,
                false, null, 0, exchangePartitionSize);
    }

    /**
     * returns the partitions sizes for a StorageDevice when upgrading
     *
     * @param source the system source
     * @param storageDevice the StorageDevice to check
     * @param exchangeRepartitionStrategy the repartitioning strategy for the
     * exchange partition
     * @param resizedExchangePartitionSize the new size of the exchange
     * partition if we want to resize it
     * @return the partitions sizes for a StorageDevice when upgrading
     */
    public static PartitionSizes getUpgradePartitionSizes(
            SystemSource source, StorageDevice storageDevice,
            RepartitionStrategy exchangeRepartitionStrategy,
            int resizedExchangePartitionSize) {
        return getPartitionSizes(source, storageDevice, true,
                exchangeRepartitionStrategy, resizedExchangePartitionSize, 0);
    }

    /**
     * unmounts a device or mountpoint
     *
     * @param deviceOrMountpoint the device or mountpoint to unmount
     * @param dlCopyGUI the currently used GUI for DLCopy
     * @throws IOException
     */
    public static void umount(String deviceOrMountpoint, DLCopyGUI dlCopyGUI)
            throws IOException {
        // check if a swapfile is in use on this partition
        List<String> mounts = LernstickFileTools.readFile(
                new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            String device = tokens[0];
            String mountPoint = tokens[1];
            if (device.equals(deviceOrMountpoint)
                    || mountPoint.equals(deviceOrMountpoint)) {
                List<String> swapLines = LernstickFileTools.readFile(
                        new File("/proc/swaps"));
                for (String swapLine : swapLines) {
                    if (swapLine.startsWith(mountPoint)) {
                        // deactivate swapfile
                        swapoffFile(device, swapLine, dlCopyGUI);
                    }
                }
            }
        }

        int exitValue = PROCESS_EXECUTOR.executeProcess(
                "umount", deviceOrMountpoint);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Error_Umount");
            errorMessage = MessageFormat.format(
                    errorMessage, deviceOrMountpoint);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    /**
     * unmounts a given partition
     *
     * @param partition the given partition
     * @param dlCopyGUI the GUI to show error messages
     * @return <code>true</code>, if unmounting was successfull,
     * <code>false</code> otherwise
     * @throws DBusException
     */
    public static boolean umount(Partition partition, DLCopyGUI dlCopyGUI)
            throws DBusException, IOException {
        // early return
        if (!partition.isMounted()) {
            LOGGER.log(Level.INFO, "{0} was NOT mounted...",
                    partition.getDeviceAndNumber());
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
            dlCopyGUI.showErrorMessage(errorMessage);
            return false;
        }
    }

    /**
     * writes the currently used version of persistence.conf into a given path
     *
     * @param mountPath the given path
     * @throws IOException
     */
    public static void writePersistenceConf(String mountPath)
            throws IOException {

        Path configFilePath = Paths.get(mountPath, "persistence.conf");
        if (!Files.exists(configFilePath)) {
            Files.write(configFilePath, "/ union,source=.\n".getBytes());
        }
    }

    /**
     * formats and tunes the persistence partition of a given device
     * (e.g."/dev/sdb1") and creates the default persistence configuration file
     * on the partition file system
     *
     * @param device the given device (e.g. "/dev/sdb1")
     * @param personalDataPartitionEncryption if the persistence partition
     * should be encrypted with a personal password
     * @param personalEncryptionPassword the personal encryption password
     * @param secondaryDataPartitionEncryption if the persistence partition
     * should be encrypted with a secondary password
     * @param secondaryEncryptionPassword the secondary encryption password
     * @param randomFillDataPartition if the data partition should be filled
     * with random data before formatting
     * @param fileSystem the file system to use
     * @param dlCopyGUI the program GUI to show error messages
     * @throws DBusException if a DBusException occurs
     * @throws IOException if an IOException occurs
     */
    public static void formatPersistencePartition(String device,
            boolean personalDataPartitionEncryption,
            String personalEncryptionPassword,
            boolean secondaryDataPartitionEncryption,
            String secondaryEncryptionPassword, boolean randomFillDataPartition,
            String fileSystem, DLCopyGUI dlCopyGUI)
            throws DBusException, IOException {

        // make sure that the partition is unmounted
        if (isMounted(device)) {
            umount(device, dlCopyGUI);
        }

        String mapperDevice = null;
        if (personalDataPartitionEncryption) {

            Partition persistencePartition
                    = Partition.getPartitionFromDeviceAndNumber(
                            device.substring(5));

            if (randomFillDataPartition) {

                long persistenceSize = persistencePartition.getSize();

                LOGGER.info("filling data partition with random data...");
                try (FileChannel source = FileChannel.open(
                        Paths.get("/dev/urandom"), StandardOpenOption.READ);
                        FileChannel destination = FileChannel.open(
                                Paths.get(device), StandardOpenOption.WRITE)) {
                    ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MEGA);
                    long written = 0;
                    while (true) {
                        byteBuffer.clear();
                        source.read(byteBuffer);
                        byteBuffer.flip();
                        written += destination.write(byteBuffer);
                        dlCopyGUI.showInstallOverwritingDataPartitionWithRandomData(
                                written, persistenceSize);
                    }
                } catch (IOException e) {
                    // this exception is thrown when the filling process is done
                    // just ignore it...
                    LOGGER.log(Level.INFO, "", e);
                }
                dlCopyGUI.showInstallCreatingFileSystems();
            }

            persistencePartition.luksFormat(personalEncryptionPassword);

            mapperDevice
                    = persistencePartition.luksOpen(personalEncryptionPassword);

            if (secondaryDataPartitionEncryption) {
                persistencePartition.addSecondaryLuksPassword(
                        personalEncryptionPassword,
                        secondaryEncryptionPassword);
            }
        }

        // If we want to create a partition at the exact same location of
        // another type of partition mkfs becomes interactive.
        // For instance if we first install with an exchange partition and later
        // without one, mkfs asks the following question:
        // ------------
        // /dev/sda2 contains a exfat file system labelled 'Austausch'
        // Proceed anyway? (y,n)
        // ------------
        // To make a long story short, this is the reason we have to use the
        // force flag "-F" here.
        int exitValue = PROCESS_EXECUTOR.executeProcess("/sbin/mkfs."
                + fileSystem, "-F", "-L", Partition.PERSISTENCE_LABEL,
                personalDataPartitionEncryption ? mapperDevice : device);
        if (exitValue != 0) {
            LOGGER.severe(PROCESS_EXECUTOR.getOutput());
            String errorMessage = STRINGS.getString(
                    "Error_Create_Data_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // tuning
        exitValue = PROCESS_EXECUTOR.executeProcess(
                "/sbin/tune2fs", "-m", "0", "-c", "0", "-i", "0",
                personalDataPartitionEncryption ? mapperDevice : device);
        if (exitValue != 0) {
            LOGGER.severe(PROCESS_EXECUTOR.getOutput());
            String errorMessage = STRINGS.getString(
                    "Error_Tune_Data_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // We have to wait a little for dbus to get to know the new filesystem.
        // Otherwise we will sometimes get the following exception in the calls
        // below:
        // org.freedesktop.dbus.exceptions.DBusExecutionException:
        // No such interface 'org.freedesktop.UDisks2.Filesystem'
        // 5 seconds were too short!
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        settleUdev();

        // create default persistence configuration file
        Partition persistencePartition
                = Partition.getPartitionFromDeviceAndNumber(
                        device.substring(5));
        String mountPath = persistencePartition.mount().getMountPath();
        if (mountPath == null) {
            throw new IOException("could not mount persistence partition");
        }
        writePersistenceConf(mountPath);

        if (getMajorDebianVersion() >= 9) {
            // Starting with Debian 9 we use overlayfs for the data partition.
            // Therefore we create here the empty directories "rw" and "work"
            // for overlayfs so that read-only mode works out-of-the-box.
            // Otherwise the system would just crash when using read-only mode
            // at the first system startup.
            Files.createDirectory(Paths.get(mountPath, "rw"));
            Files.createDirectory(Paths.get(mountPath, "work"));
        }

        persistencePartition.umount();
    }

    /**
     * creates a CopyJobsInfo for a given source / destination combination
     *
     * @param source the system source
     * @param storageDevice the destination StorageDevice
     * @param destinationEfiPartition the destination boot partition
     * @param destinationExchangePartition the destination exchange partition
     * @param destinationSystemPartition the destination system partition
     * @param destinationExchangePartitionFileSystem the file system of the
     * destination exchange partition
     * @return the CopyJobsInfo for the given source / destination combination
     * @throws DBusException if a D-BUS exception occurs
     * @throws java.io.IOException if an I/O exception occurs
     */
    public static CopyJobsInfo prepareEfiAndSystemCopyJobs(SystemSource source,
            StorageDevice storageDevice, Partition destinationEfiPartition,
            Partition destinationExchangePartition,
            Partition destinationSystemPartition,
            String destinationExchangePartitionFileSystem)
            throws DBusException, IOException {

        String destinationEfiPath
                = destinationEfiPartition.mount().getMountPath();
        String destinationSystemPath
                = destinationSystemPartition.mount().getMountPath();

        Source efiCopyJobSource = source.getEfiCopySource();
        Source systemCopyJobSource = source.getSystemCopySourceFull();

        CopyJob efiCopyJob = new CopyJob(
                new Source[]{efiCopyJobSource},
                new String[]{destinationEfiPath});

        CopyJob systemCopyJob = new CopyJob(
                new Source[]{systemCopyJobSource},
                new String[]{destinationSystemPath});

        return new CopyJobsInfo(destinationEfiPath, destinationSystemPath,
                efiCopyJob, null, systemCopyJob);
    }

    /**
     * tries to hide the boot files on the exchange partition with FAT
     * attributes (works on Windows) and a .hidden file (works on macOS)
     *
     * @param bootFilesCopyJob the CopyJob for the boot file, used to get the
     * list of files to hide
     * @param destinationExchangePath
     */
    public static void hideBootFiles(
            CopyJob bootFilesCopyJob, String destinationExchangePath) {

        Source bootFilesSource = bootFilesCopyJob.getSources()[0];
        String[] bootFiles = bootFilesSource.getBaseDirectory().list();

        if (bootFiles == null) {
            return;
        }

        // use FAT attributes to hide boot files in Windows
        for (String bootFile : bootFiles) {
            Path destinationPath = Paths.get(destinationExchangePath, bootFile);
            if (Files.exists(destinationPath)) {
                PROCESS_EXECUTOR.executeProcess(
                        "fatattr", "+h", destinationPath.toString());
            }
        }

        // use ".hidden" file to hide boot files in macOS
        String osxHiddenFilePath = destinationExchangePath + "/.hidden";
        try (FileWriter fileWriter = new FileWriter(osxHiddenFilePath)) {
            String lineSeperator = System.lineSeparator();
            for (String bootFile : bootFiles) {
                Path destinationPath = Paths.get(
                        destinationExchangePath, bootFile);
                if (Files.exists(destinationPath)) {
                    fileWriter.write(bootFile + lineSeperator);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "", ex);
        }

        // use FAT attributes again to hide macOS ".hidden" file in Windows
        PROCESS_EXECUTOR.executeProcess("fatattr", "+h", osxHiddenFilePath);
    }

    /**
     * converts an isolinux directory structure to a syslinux directory
     * structure
     *
     * @param mountPoint the mountpoint of the isolinux directory
     * @param dlCopyGUI the GUI to show error messages
     * @throws IOException
     */
    public static void isolinuxToSyslinux(String mountPoint,
            DLCopyGUI dlCopyGUI) throws IOException {

        String isolinuxPath = mountPoint + "/isolinux";

        if (new File(isolinuxPath).exists()) {
            LOGGER.info("replacing isolinux with syslinux");
            final String syslinuxPath = mountPoint + "/syslinux";
            moveFile(isolinuxPath, syslinuxPath);
            moveFile(syslinuxPath + "/isolinux.cfg",
                    syslinuxPath + "/syslinux.cfg");

            // replace "isolinux" with "syslinux" in some files
            Pattern pattern = Pattern.compile("isolinux");
            LernstickFileTools.replaceText(
                    syslinuxPath + "/exithelp.cfg", pattern, "syslinux");
            LernstickFileTools.replaceText(
                    syslinuxPath + "/stdmenu.cfg", pattern, "syslinux");
            LernstickFileTools.replaceText(
                    syslinuxPath + "/syslinux.cfg", pattern, "syslinux");

            // remove boot.cat
            String bootCatFileName = syslinuxPath + "/boot.cat";
            File bootCatFile = new File(bootCatFileName);
            if (!bootCatFile.delete()) {
                dlCopyGUI.showErrorMessage(
                        "Could not delete " + bootCatFileName);
            }

            // update md5sum.txt
            String md5sumFileName = mountPoint + "/md5sum.txt";
            LernstickFileTools.replaceText(md5sumFileName, pattern, "syslinux");
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
                LernstickFileTools.writeFile(md5sumFile, lines);
                PROCESS_EXECUTOR.executeProcess("sync");
            } else {
                LOGGER.log(Level.WARNING,
                        "file \"{0}\" does not exist!", md5sumFileName);
            }

        } else {
            // boot device is probably a hard disk
            LOGGER.info("isolinux directory does not exist -> no renaming");
        }
    }

    /**
     * formats the efi and system partition
     *
     * @param efiDevice the efi device
     * @param systemDevice the system device
     * @throws IOException
     */
    public static void formatEfiAndSystemPartition(
            String efiDevice, String systemDevice) throws IOException {

        formatEfiPartition(efiDevice);

        int exitValue = PROCESS_EXECUTOR.executeProcess(
                "/sbin/mkfs.ext3", "-L", systemPartitionLabel, systemDevice);
        if (exitValue != 0) {
            LOGGER.severe(PROCESS_EXECUTOR.getOutput());
            String errorMessage
                    = STRINGS.getString("Error_Create_System_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    /**
     * formats the efi partition
     *
     * @param efiDevice the efi device
     * @throws IOException
     */
    public static void formatEfiPartition(String efiDevice) throws IOException {

        int exitValue = PROCESS_EXECUTOR.executeProcess(
                "/sbin/mkfs.vfat", "-n", Partition.EFI_LABEL, efiDevice);
        if (exitValue != 0) {
            LOGGER.severe(PROCESS_EXECUTOR.getOutput());
            String errorMessage
                    = STRINGS.getString("Error_Create_EFI_Partition");
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    /**
     * sets the data partition mode on a target system
     *
     * @param source the system source
     * @param dataPartitionMode the data partition mode to set
     * @param imagePath the path where the target image is mounted
     */
    public static void setDataPartitionMode(SystemSource source,
            DataPartitionMode dataPartitionMode, String imagePath) {
        LOGGER.log(Level.INFO,
                "data partition mode of installation source: {0}",
                source.getDataPartitionMode());
        LOGGER.log(Level.INFO,
                "selected data partition mode for destination: {0}",
                dataPartitionMode);
        if (source.getDataPartitionMode() != dataPartitionMode) {
            BootConfigUtil.setDataPartitionMode(dataPartitionMode, imagePath);
        }
    }

    /**
     * returns a list of all storage devices in the system
     *
     * @param includeHardDisks if hard disks should be included in the list
     * @param includeBootDevice if the boot device should be included in the
     * list
     * @param bootDeviceName the name of the boot device
     * @return
     * @throws IOException
     * @throws DBusException
     */
    public static List<StorageDevice> getStorageDevices(
            boolean includeHardDisks, boolean includeBootDevice,
            String bootDeviceName) throws IOException, DBusException {

        List<String> partitions = DbusTools.getPartitions();
        List<StorageDevice> storageDevices = new ArrayList<>();

        for (String partition : partitions) {
            LOGGER.log(Level.FINE, "checking partition \"{0}\"", partition);

            if (!includeBootDevice) {
                if (partition.equals(bootDeviceName)) {
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
                    pathPrefix + partition, includeHardDisks);
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

    /**
     * returns the StorageDevice for a given dbus path after a timeout
     *
     * @param path the dbus path
     * @param includeHardDisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @return the StorageDevice for a given dbus path after a timeout
     * @throws DBusException
     */
    public static StorageDevice getStorageDeviceAfterTimeout(
            String path, boolean includeHardDisks) throws DBusException {
        // It has happened that "udisks --enumerate" returns a valid storage
        // device but not yet its partitions. Therefore we give the system
        // a little break after storage devices have been added.
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        StorageDevice storageDevice = getStorageDevice(path, includeHardDisks);
        LOGGER.log(Level.INFO, "storage device of path {0}: {1}",
                new Object[]{path, storageDevice});
        return storageDevice;
    }

    /**
     * reads a one-line file
     *
     * @param file the file to read
     * @return the trimmed line of the file
     * @throws IOException if an I/O exception occurs
     */
    public static String readOneLineFile(File file) throws IOException {
        try (FileReader fileReader = new FileReader(file)) {
            try (BufferedReader bufferedReader
                    = new BufferedReader(fileReader)) {
                String string = bufferedReader.readLine();
                if (string != null) {
                    string = string.trim();
                }
                return string;
            }
        }
    }

    /**
     * returns the textual representation of the MD5 sum of a file
     *
     * @param filePath the path of the file to digest
     * @return the textual representation of the MD5 sum of a file
     * @throws NoSuchAlgorithmException if MD5 is not available
     * @throws IOException if reading from the file fails
     */
    public static String getMd5String(String filePath)
            throws NoSuchAlgorithmException, IOException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        try (DigestInputStream digestInputStream = new DigestInputStream(
                Files.newInputStream(Paths.get(filePath)), md5)) {
            byte[] byteArray = new byte[1024];
            for (int i = digestInputStream.read(byteArray); i > 0;) {
                i = digestInputStream.read(byteArray);
            }
            byte[] digest = md5.digest();
            StringBuilder stringBuilder = new StringBuilder(32);
            for (byte b : digest) {
                stringBuilder.append(String.format("%02x", b));
            }
            return stringBuilder.toString();
        }
    }

    /**
     * To make sure that every machine has its unique ssh host key we have to
     * remove the ssh configuration created by live-config in the system copies.
     *
     * @param root the root file system of the system copy
     * @throws IOException if an I/O exception occurs
     */
    public static void removeSshConfig(String root) throws IOException {

        // remove ssh host keys
        Path sshPath = Paths.get(root, "/etc/ssh");
        // There are installations without the openssh-server package. In this
        // case the SSH host keys are not generated and there is no /etc/ssh
        // directory in the persistence partition. Therefore the following check
        // for the existence of sshPath is necessary.
        if (Files.exists(sshPath)) {
            try (DirectoryStream<Path> stream
                    = Files.newDirectoryStream(sshPath, "ssh_host_*")) {
                for (Path path : stream) {
                    Files.deleteIfExists(path);
                }
            }
        } else {
            LOGGER.fine("no SSH keys found to remove");
        }

        // remove state file of /lib/live/config/1160-openssh-server so that new
        // keys are generated for this new image
        Files.deleteIfExists(Paths.get(root,
                "/var/lib/live/config/openssh-server"));
    }

    /**
     * Checks if a device is mounted in read-write mode.
     *
     * @param device the device to check, e.g. /dev/sda1
     * @return <code>true</code>, if the given device is mounted in read-write
     * mode, <code>false</code> otherwise
     * @throws IOException if an I/O error occurs while reading
     * <code>/proc/mounts</code>
     */
    public static boolean isMountedReadWrite(String device) throws IOException {
        List<String> mounts = LernstickFileTools.readFile(
                new File("/proc/mounts"));
        for (String mount : mounts) {
            String[] tokens = mount.split(" ");
            String mountedPartition = tokens[0];
            if (mountedPartition.equals(device)) {
                // check mount options
                String mountOptions = tokens[3];
                if (mountOptions.startsWith("rw")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks <code>/proc/cmdline</code>, if the system was booted in
     * persistence mode.
     *
     * @return <code>true</code>, if the system was booted in persistence mode,
     * <code>false</code> otherwise
     * @throws IOException if an I/O error occurs while reading
     * <code>/proc/cmdline</code>
     */
    public static boolean isBootPersistent() throws IOException {
        String cmdLineFileName = "/proc/cmdline";
        boolean persistenceBoot = false;
        try {
            String cmdLine = DLCopy.readOneLineFile(new File(cmdLineFileName));
            persistenceBoot = cmdLine.contains(" persistence ");
            LOGGER.log(Level.FINEST,
                    "persistenceBoot: {0}", persistenceBoot);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    "could not read \"" + cmdLineFileName + '\"', ex);
            throw ex;
        }
        return persistenceBoot;
    }

    private static PartitionSizes getPartitionSizes(SystemSource source,
            StorageDevice storageDevice, boolean upgrading,
            RepartitionStrategy upgradeRepartitionStrategy,
            int upgradeResizedExchangePartitionSize,
            int installExchangePartitionSize) {

        long storageDeviceSize = storageDevice.getSize();
        long enlargedSystemSize = getEnlargedSystemSize(source.getSystemSize());
        long overhead = storageDeviceSize
                - (EFI_PARTITION_SIZE * MEGA) - enlargedSystemSize;
        int overheadMB = (int) (overhead / MEGA);
        PartitionState partitionState
                = getPartitionState(storageDeviceSize, enlargedSystemSize);
        switch (partitionState) {
            case TOO_SMALL:
                return null;

            case ONLY_SYSTEM:
                return new PartitionSizes(0, 0);

            case EXCHANGE:
                int exchangeMB = 0;
                if (upgrading) {
                    switch (upgradeRepartitionStrategy) {
                        case KEEP:
                            Partition exchangePartition
                                    = storageDevice.getExchangePartition();
                            if (exchangePartition != null) {
                                LOGGER.log(Level.INFO, "exchangePartition: {0}",
                                        exchangePartition);
                                exchangeMB = (int) (exchangePartition.getSize()
                                        / MEGA);
                            }
                            break;
                        case RESIZE:
                            exchangeMB = upgradeResizedExchangePartitionSize;
                        // stays at 0 MB in all other cases...
                    }
                } else {
                    exchangeMB = installExchangePartitionSize;
                }
                LOGGER.log(Level.INFO, "exchangeMB = {0}", exchangeMB);
                int persistenceMB = overheadMB - exchangeMB;
                return new PartitionSizes(exchangeMB, persistenceMB);

            case PERSISTENCE:
                return new PartitionSizes(0, overheadMB);

            default:
                LOGGER.log(Level.SEVERE,
                        "unsupported partitionState \"{0}\"", partitionState);
                return null;
        }
    }

    private static void createPartitions(StorageDevice storageDevice,
            PartitionSizes partitionSizes, long storageDeviceSize,
            final PartitionState partitionState, String exchangeDevice,
            String exchangePartitionLabel,
            String persistenceDevice, boolean personalDataPartitionEncryption,
            String personalEncryptionPassword,
            boolean secondaryDataPartitionEncryption,
            String secondaryEncryptionPassword, boolean randomFillDataPartition,
            String efiDevice, String systemDevice,
            InstallerOrUpgrader installerOrUpgrader, DLCopyGUI dlCopyGUI)
            throws InterruptedException, IOException, DBusException {

        // update GUI
        installerOrUpgrader.showCreatingFileSystems();

        String device = "/dev/" + storageDevice.getDevice();

        // determine exact partition sizes
        long overhead = storageDeviceSize - getEnlargedSystemSize(
                installerOrUpgrader.getSourceSystemSize());
        int exchangeMB = partitionSizes.getExchangeMB();
        int persistenceMB = partitionSizes.getPersistenceMB();
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
                // create two partitions: efi, system
                String efiBorder = EFI_PARTITION_SIZE + "MiB";
                mkpart(partedCommandList, "0%", efiBorder);
                mkpart(partedCommandList, efiBorder, "100%");
                setFlag(partedCommandList, "1", "boot", "on");
                setFlag(partedCommandList, "1", "lba", "on");
                break;

            case PERSISTENCE:
                // create three partitions: efi, persistence, system
                efiBorder = EFI_PARTITION_SIZE + "MiB";
                String persistenceBorder
                        = (EFI_PARTITION_SIZE + persistenceMB) + "MiB";
                mkpart(partedCommandList, "0%", efiBorder);
                mkpart(partedCommandList, efiBorder, persistenceBorder);
                mkpart(partedCommandList, persistenceBorder, "100%");
                setFlag(partedCommandList, "1", "boot", "on");
                setFlag(partedCommandList, "1", "lba", "on");
                break;

            case EXCHANGE:
                if (exchangeMB == 0) {
                    // create three partitions: efi, persistence, system
                    efiBorder = EFI_PARTITION_SIZE + "MiB";
                    persistenceBorder
                            = (EFI_PARTITION_SIZE + persistenceMB) + "MiB";
                    mkpart(partedCommandList, "0%", efiBorder);
                    mkpart(partedCommandList, efiBorder, persistenceBorder);
                    mkpart(partedCommandList, persistenceBorder, "100%");
                    setFlag(partedCommandList, "1", "boot", "on");
                    setFlag(partedCommandList, "1", "lba", "on");

                } else {
                    // first two partitions: efi, exchange
                    efiBorder = EFI_PARTITION_SIZE + "MiB";
                    String exchangeBorder
                            = (EFI_PARTITION_SIZE + exchangeMB) + "MiB";
                    String secondBorder = exchangeBorder;
                    mkpart(partedCommandList, "0%", efiBorder);
                    mkpart(partedCommandList, efiBorder, exchangeBorder);
                    setFlag(partedCommandList, "1", "boot", "on");
                    if (persistenceMB == 0) {
                        // third partition: system
                        mkpart(partedCommandList, secondBorder, "100%");
                    } else {
                        // last two partitions: persistence, system
                        persistenceBorder = (EFI_PARTITION_SIZE
                                + exchangeMB + persistenceMB) + "MiB";
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
        TimeUnit.SECONDS.sleep(7);

        // check if a swap partition is active on this device
        // if so, switch it off
        List<String> swaps
                = LernstickFileTools.readFile(new File("/proc/swaps"));
        for (String swapLine : swaps) {
            if (swapLine.startsWith(device)) {
                swapoffPartition(device, swapLine, dlCopyGUI);
            }
        }

        // umount all mounted partitions of device
        umountPartitions(device, dlCopyGUI);

        // We must wipe the whole storage device before creating the partitions,
        // otherwise USB flash drives previously written with a dd'ed ISO
        // will NOT work!
        if (PROCESS_EXECUTOR.executeProcess(
                true, true, "wipefs", "-a", device) != 0) {
            String errorMessage = STRINGS.getString("Error_Wiping_File_System");
            errorMessage = MessageFormat.format(errorMessage, device);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // "parted <device> mklabel msdos" did NOT work correctly here!
        // (the partition table type was still unknown and booting failed)
        int exitValue;
        if (DbusTools.DBUS_VERSION == DbusTools.DbusVersion.V1) {
            // "--print-reply" is needed in the call to dbus-send below to make
            // the call synchronous
            exitValue = PROCESS_EXECUTOR.executeProcess("dbus-send",
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
            exitValue = PROCESS_EXECUTOR.executeProcess(true, true,
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
        TimeUnit.SECONDS.sleep(3);

        // repartition device
        String[] commandArray = partedCommandList.toArray(
                new String[partedCommandList.size()]);
        exitValue = PROCESS_EXECUTOR.executeProcess(commandArray);
        if (exitValue != 0) {
            String errorMessage = STRINGS.getString("Error_Repartitioning");
            errorMessage = MessageFormat.format(errorMessage, device);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }

        // safety wait so that new partitions are known to the system
        TimeUnit.SECONDS.sleep(7);

        // The partition types assigned by parted are mostly garbage.
        // We must fix them here...
        // The boot partition is actually formatted with FAT32, but "hidden"
        // by using the EFI partition type.
        switch (partitionState) {
            case ONLY_SYSTEM:
                // create two partitions:
                //  1) efi (EFI)
                //  2) system (Linux)
                PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                        "--part-type", device, "1", "ef");
                PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                        "--part-type", device, "2", "83");
                break;

            case PERSISTENCE:
                // create three partitions:
                //  1) efi (EFI)
                //  2) persistence (Linux)
                //  3) system (Linux)
                PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                        "--part-type", device, "1", "ef");
                PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                        "--part-type", device, "2", "83");
                PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                        "--part-type", device, "3", "83");
                break;

            case EXCHANGE:
                if (exchangeMB == 0) {
                    // create three partitions:
                    //  1) efi (EFI)
                    //  2) persistence (Linux)
                    //  3) system (Linux)
                    PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                            "--part-type", device, "1", "ef");
                    PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                            "--part-type", device, "2", "83");
                    PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                            "--part-type", device, "3", "83");
                } else {
                    // determine ID for exchange partition
                    String exchangePartitionID;
                    String fileSystem = installerOrUpgrader.
                            getExchangePartitionFileSystem();
                    if (fileSystem.equalsIgnoreCase("fat32")) {
                        exchangePartitionID = "c";
                    } else {
                        // exFAT & NTFS
                        exchangePartitionID = "7";
                    }

                    //  1) efi (EFI)
                    //  2) exchange (exFAT, FAT32 or NTFS)
                    PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                            "--part-type", device, "1", "ef");
                    PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                            "--part-type", device, "2", exchangePartitionID);

                    if (persistenceMB == 0) {
                        //  3) system (Linux)
                        PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                                "--part-type", device, "3", "83");
                    } else {
                        //  3) persistence (Linux)
                        //  4) system (Linux)
                        PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                                "--part-type", device, "3", "83");
                        PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk",
                                "--part-type", device, "4", "83");
                    }
                }
                break;

            default:
                String errorMessage = "unsupported partitionState \""
                        + partitionState + '\"';
                LOGGER.log(Level.SEVERE, errorMessage);
                throw new IOException(errorMessage);
        }

        // Partition.getPartitionFromDeviceAndNumber() in
        // formatPersistencePartition() below failed without waiting here for
        // a little bit
        TimeUnit.SECONDS.sleep(3);
        settleUdev();

        // create file systems
        switch (partitionState) {
            case ONLY_SYSTEM:
                formatEfiAndSystemPartition(efiDevice, systemDevice);
                return;

            case PERSISTENCE:
                formatPersistencePartition(persistenceDevice,
                        personalDataPartitionEncryption,
                        personalEncryptionPassword,
                        secondaryDataPartitionEncryption,
                        secondaryEncryptionPassword, randomFillDataPartition,
                        installerOrUpgrader.getDataPartitionFileSystem(),
                        dlCopyGUI);
                formatEfiAndSystemPartition(efiDevice, systemDevice);
                return;

            case EXCHANGE:
                if (exchangeMB != 0) {
                    // create file system for exchange partition
                    formatExchangePartition(exchangeDevice,
                            exchangePartitionLabel,
                            installerOrUpgrader.getExchangePartitionFileSystem(),
                            dlCopyGUI);
                }
                if (persistenceDevice != null) {
                    formatPersistencePartition(persistenceDevice,
                            personalDataPartitionEncryption,
                            personalEncryptionPassword,
                            secondaryDataPartitionEncryption,
                            secondaryEncryptionPassword,
                            randomFillDataPartition,
                            installerOrUpgrader.getDataPartitionFileSystem(),
                            dlCopyGUI);
                }
                formatEfiAndSystemPartition(efiDevice, systemDevice);
                return;

            default:
                String errorMessage = "unsupported partitionState \""
                        + partitionState + '\"';
                LOGGER.log(Level.SEVERE, errorMessage);
                throw new IOException(errorMessage);
        }
    }

    private static void copyExchangeEfiAndSystem(SystemSource source,
            FileCopier fileCopier, StorageDevice storageDevice,
            Partition destinationExchangePartition,
            Partition destinationEfiPartition,
            Partition destinationSystemPartition,
            InstallerOrUpgrader installerOrUpgrader, boolean checkCopies,
            DLCopyGUI dlCopyGUI)
            throws InterruptedException, IOException,
            DBusException, NoSuchAlgorithmException {

        // define CopyJob for exchange paritition
        String destinationExchangePath = null;
        CopyJob exchangeCopyJob = null;
        if (installerOrUpgrader instanceof Installer) {
            Installer installer = (Installer) installerOrUpgrader;
            if (installer.isCopyExchangePartitionSelected()) {
                destinationExchangePath
                        = destinationExchangePartition.mount().getMountPath();
                exchangeCopyJob = new CopyJob(
                        new Source[]{source.getExchangeCopySource()},
                        new String[]{destinationExchangePath});
            }
        }

        // define CopyJobs for efi and system parititions
        CopyJobsInfo copyJobsInfo = prepareEfiAndSystemCopyJobs(source,
                storageDevice, destinationEfiPartition,
                destinationExchangePartition, destinationSystemPartition,
                installerOrUpgrader.getExchangePartitionFileSystem());

        // copy all files
        installerOrUpgrader.showCopyingFiles(fileCopier);

        CopyJob efiFilesCopyJob = copyJobsInfo.getExchangeEfiCopyJob();
        fileCopier.copy(checkCopies, exchangeCopyJob, efiFilesCopyJob,
                copyJobsInfo.getEfiCopyJob(), copyJobsInfo.getSystemCopyJob());

        // update GUI
        installerOrUpgrader.showUnmounting();

        source.unmountTmpPartitions();
        if (destinationExchangePath != null) {
            destinationExchangePartition.umount();
        }

        String destinationEfiPath = copyJobsInfo.getDestinationEfiPath();
        // isolinux -> syslinux renaming
        // !!! don't check here for boot storage device type !!!
        // (usb flash drives with an isohybrid image also contain the
        //  isolinux directory)
        isolinuxToSyslinux(destinationEfiPath, dlCopyGUI);

        // change data partition mode on target (if needed)
        if (installerOrUpgrader instanceof Installer) {
            Installer installer = (Installer) installerOrUpgrader;
            DataPartitionMode dataPartitionMode
                    = installer.getDataPartitionMode();
            setDataPartitionMode(source, dataPartitionMode,
                    destinationEfiPath);
        }
    }

    private static void copyPersistence(SystemSource source,
            InstallerOrUpgrader installerOrUpgrader,
            Partition destinationDataPartition, DLCopyGUI dlCopyGUI)
            throws IOException, InterruptedException, DBusException {

        // some early checks and returns...
        if (!(installerOrUpgrader instanceof Installer)) {
            return;
        }
        Installer installer = (Installer) installerOrUpgrader;
        if (!installer.isCopyDataPartitionSelected()) {
            return;
        }
        if (destinationDataPartition == null) {
            return;
        }

        // mount persistence source
        MountInfo sourceDataMountInfo = source.getDataPartition().mount();
        String sourceDataPath = sourceDataMountInfo.getMountPath();
        if (sourceDataPath == null) {
            String errorMessage = "could not mount source data partition";
            throw new IOException(errorMessage);
        }

        // mount persistence destination
        MountInfo destinationDataMountInfo = destinationDataPartition.mount();
        String destinationDataPath = destinationDataMountInfo.getMountPath();
        if (destinationDataPath == null) {
            String errorMessage = "could not mount destination data partition";
            throw new IOException(errorMessage);
        }

        // TODO: use filecopier as soon as it supports symlinks etc.
        copyPersistenceCp(installer, sourceDataPath,
                destinationDataPath, dlCopyGUI);

        // remove original ssh config to make it unique for every system
        removeSshConfig(destinationDataPath);

        // update GUI
        dlCopyGUI.showInstallUnmounting();

        // umount both source and destination persistence partitions
        //  (only if there were not mounted before)
        if (!sourceDataMountInfo.alreadyMounted()) {
            source.getDataPartition().umount();
        }
        if (!destinationDataMountInfo.alreadyMounted()) {
            destinationDataPartition.umount();
        }
    }

    private static void mkpart(List<String> commandList,
            String start, String end) {
        commandList.add("mkpart");
        commandList.add("primary");
        commandList.add(start);
        commandList.add(end);
    }

    private static void setFlag(List<String> commandList,
            String partition, String flag, String value) {
        commandList.add("set");
        commandList.add(partition);
        commandList.add(flag);
        commandList.add(value);
    }

    /**
     * formats the exchange partition
     *
     * @param device the given device (e.g. "/dev/sdb1")
     * @param label
     * @param fileSystem the file system to use
     * @param dlCopyGUI the current DLCopy GUI in use
     * @throws IOException
     */
    public static void formatExchangePartition(String device,
            String label, String fileSystem, DLCopyGUI dlCopyGUI)
            throws IOException {

        // create file system for exchange partition
        String exchangePartitionID;
        String mkfsBuilder;
        String mkfsLabelSwitch;
        String quickSwitch = null;
        if (fileSystem.equalsIgnoreCase("fat32")) {
            exchangePartitionID = "c";
            mkfsBuilder = "vfat";
            mkfsLabelSwitch = "-n";
        } else if (fileSystem.equalsIgnoreCase("exfat")) {
            exchangePartitionID = "7";
            mkfsBuilder = "exfat";
            mkfsLabelSwitch = "-n";
        } else {
            exchangePartitionID = "7";
            mkfsBuilder = "ntfs";
            quickSwitch = "-f";
            mkfsLabelSwitch = "-L";
        }

        // try unmounting the device before touching it
        // (just in case it is mounted)
        try {
            umount(device, dlCopyGUI);
        } catch (IOException ex) {
            // ignored
        }

        // If there was a LUKS partition at the very same location, the LUKS
        // header would be still there without wiping.
        PROCESS_EXECUTOR.executeProcess("/usr/sbin/wipefs", "-a", device);

        // So that we continue to reliably detect exchange partitions even after
        // reformatting them with a different file system we have to adopt the
        // partition type according to the file system we use.
        Pattern pattern = Pattern.compile("(.*)(\\p{Digit}+)");
        Matcher matcher = pattern.matcher(device);
        if (matcher.matches()) {
            PROCESS_EXECUTOR.executeProcess("/sbin/sfdisk", "--part-type",
                    matcher.group(1), matcher.group(2), exchangePartitionID);
            try {
                TimeUnit.SECONDS.sleep(7);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }

            // It happened that after the timeout above, the device was
            // automatically mounted.
            // This made the the mkfs call below fail with the error message:
            // mkfs.vfat: /dev/sda2 contains a mounted filesystem
            // Therefore we try here *again* to unmount the device...
            try {
                umount(device, dlCopyGUI);
            } catch (IOException ex) {
                // ignored
            }
        }

        int exitValue;
        if (quickSwitch == null) {
            exitValue = PROCESS_EXECUTOR.executeProcess(
                    "/sbin/mkfs." + mkfsBuilder, mkfsLabelSwitch,
                    label, device);
        } else {
            exitValue = PROCESS_EXECUTOR.executeProcess(
                    "/sbin/mkfs." + mkfsBuilder, quickSwitch, mkfsLabelSwitch,
                    label, device);
        }

        if (exitValue != 0) {
            String errorMessage
                    = STRINGS.getString("Error_Create_Exchange_Partition");
            errorMessage = MessageFormat.format(errorMessage, device);
            LOGGER.severe(errorMessage);
            throw new IOException(errorMessage);
        }
    }

    /**
     * returns the major Debian version
     *
     * @return the major Debian version
     * @throws IOException if reading or parsing /etc/debian_version fails
     */
    public static int getMajorDebianVersion() throws IOException {

        String debianVersionPath = "/etc/debian_version";
        List<String> debianVersionFile = LernstickFileTools.readFile(
                new File(debianVersionPath));
        String versionString = debianVersionFile.get(0);

        // first try pattern <major>.<minor>
        Pattern versionPattern = Pattern.compile("(\\p{Digit}*)\\..*");
        Matcher matcher = versionPattern.matcher(versionString);
        if (matcher.matches()) {
            int majorDebianVersion = Integer.parseInt(matcher.group(1));
            LOGGER.log(Level.INFO, "majorDebianVersion: {0}",
                    majorDebianVersion);
            return majorDebianVersion;
        }

        // retry with a simple number pattern
        versionPattern = Pattern.compile("(\\p{Digit}*)");
        matcher = versionPattern.matcher(versionString);
        if (matcher.matches()) {
            int majorDebianVersion = Integer.parseInt(matcher.group(1));
            LOGGER.log(Level.INFO, "majorDebianVersion: {0}",
                    majorDebianVersion);
            return majorDebianVersion;
        }

        // maybe a testing version with codenames?
        if (versionString.startsWith("bullseye")) {
            return 11;
        }

        throw new IOException("could not parse " + debianVersionPath);
    }

    /**
     * moves the offset of an ext[234] partition forward
     *
     * @param previousPartition the partition previous to the one we move
     * @param partition the partition to change
     * @param delta how far the start of the partition should be moved forward
     * @throws java.io.IOException if an I/O exception occurs
     * @throws org.freedesktop.dbus.exceptions.DBusException if a DBus exception
     * occurs
     */
    public static void moveExtPartitionOffsetForward(
            Partition previousPartition, Partition partition, long delta)
            throws IOException, DBusException {

        LOGGER.log(Level.INFO,
                "\nprevious partition: {0}\npartition: {1}\ndelta: {2}",
                new Object[]{previousPartition, partition, delta});

        String partitionDeviceFile = "/dev/" + partition.getDeviceAndNumber();
        ProcessExecutor processExecutor = new ProcessExecutor(true);

        // run initial filesystem check
        int returnValue = processExecutor.executeProcess(true, true, "e2fsck",
                "-f", "-y", "-v", partitionDeviceFile);
        if ((returnValue != 0) && (returnValue != 1)) {
            throw new IOException(
                    "filesystem check on " + partitionDeviceFile + " failed");
        }

        // shrink filesystem
        long newSize = (partition.getSize() - delta) / 1024;
        returnValue = processExecutor.executeProcess(true, true, "resize2fs",
                partitionDeviceFile, newSize + "K");
        if (returnValue != 0) {
            throw new IOException("shrinking filesystem on "
                    + partitionDeviceFile + " failed");
        }

        // move filesystem
        returnValue = processExecutor.executeProcess(true, true, "e2image",
                "-ra", "-O", String.valueOf(delta), partitionDeviceFile);
        if (returnValue != 0) {
            throw new IOException(
                    "moving filesystem on " + partitionDeviceFile + " failed");
        }

        String deviceFile = "/dev/" + partition.getStorageDevice().getDevice();

        // remove partition
        partition.remove();

        // re-create partition on new offset
        long oldOffset = partition.getOffset();
        long newOffset = oldOffset + delta;
        long end = oldOffset + partition.getSize() - 1;
        partition.getStorageDevice().createPrimaryPartition(
                partition.getIdType(), newOffset, end);

        // resize previous partition to new boundary
        returnValue = processExecutor.executeProcess(true, true, "parted",
                deviceFile, "resizepart",
                String.valueOf(previousPartition.getNumber()),
                String.valueOf(newOffset - 1) + "B");
        if (returnValue != 0) {
            throw new IOException(
                    "resizing partition " + previousPartition + " failed");
        }

        // It might happen that the parted command returns but the device nodes
        // aren't created yet. See here for details:
        // https://www.gnu.org/software/parted/manual/parted.html#quit
        // Therefore we have to wait here for the device nodes of both
        // partitions to reappear. Otherwise subsequent operations (like
        // formatting the EFI partition) might fail.
        waitForDeviceNodes(
                Paths.get("/dev/" + previousPartition.getDeviceAndNumber()),
                Paths.get("/dev/" + partition.getDeviceAndNumber()));
    }

    /**
     * Wait for device nodes to appear.
     *
     * @param deviceNodes a list of device nodes (e.g. /dev/sda1) to wait for
     * @return <code>true</code>, if all device nodes appeared,
     * <code>false</code> otherwise
     */
    public static boolean waitForDeviceNodes(Path... deviceNodes) {

        settleUdev();

        // With this version:
        // List<Path> deviceNodeList = Arrays.asList(deviceNodes);
        // iterator.remove() below just throws
        // java.lang.UnsupportedOperationException: remove
        // Therefore we have to make a copy of the deviceNodes varargs array.
        List<Path> deviceNodeList = new ArrayList<>();
        Collections.addAll(deviceNodeList, deviceNodes);

        for (int i = 0, MAX = 30; i < MAX; i++) {

            Iterator<Path> iterator = deviceNodeList.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                boolean reappeared = Files.exists(path);
                LOGGER.log(Level.INFO, "{0} {1}.", new Object[]{
                    path, reappeared ? "reappeared" : "is still missing"
                });
                if (reappeared) {
                    iterator.remove();
                }
            }

            if (deviceNodeList.isEmpty()) {
                return true;
            }

            if (i < (MAX - 1)) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                }
            } else {
                LOGGER.warning("Timeout reached, "
                        + "no longer waiting for device nodes to reappear.");
            }
        }

        return false;
    }

    /**
     * Transfers certain data from a source storage device to a destination
     * storage device.
     *
     * @param sourceDevice the source storage device
     * @param destinationDevice the destination storage device
     * @param transferExchange if the exchange partition should be transferred
     * @param transferHome if the directory /home/ should be transferred
     * @param transferNetwork if the NetworkManager settings should be
     * transferred
     * @param transferPrinter if the printer settings should be transferred
     * @param transferFirewall if the firewall settings should be transferred
     * @param checkCopies if the copies should be verified
     * @param installer the Installer used during this transfer
     * @param gui the GUI used during this transfer
     * @throws IOException if an I/O error occurs
     * @throws DBusException if a D-Bus exception occurs
     * @throws NoSuchAlgorithmException if FileCopier can't load its message
     * digest algorithm used for verification of the copies
     */
    public static void transfer(StorageDevice sourceDevice,
            StorageDevice destinationDevice, boolean transferExchange,
            boolean transferHome, boolean transferNetwork,
            boolean transferPrinter, boolean transferFirewall,
            boolean checkCopies, Installer installer, DLCopyGUI gui)
            throws IOException, DBusException, NoSuchAlgorithmException {

        if (transferExchange) {

            ExchangeTransferrer transferrer = new ExchangeTransferrer(gui,
                    sourceDevice.getExchangePartition(),
                    destinationDevice.getExchangePartition());

            transferrer.transfer(checkCopies);
        }

        if (transferHome || transferNetwork || transferPrinter
                || transferFirewall) {

            FileTransferrer transferrer = new FileTransferrer(gui, installer,
                    sourceDevice, destinationDevice.getDataPartition());

            transferrer.transfer(transferHome, transferNetwork,
                    transferPrinter, transferFirewall);
        }
    }

    /**
     * Calls <code>udevadm settle</code> to wait for all current events of the
     * udev event queue to be handled.
     *
     */
    public static void settleUdev() {
        ProcessExecutor processExecutor = new ProcessExecutor(true);
        processExecutor.executeProcess(true, true, "udevadm", "settle");
    }

    private static void copyPersistenceCp(Installer installer,
            String persistenceSourcePath, String persistenceDestinationPath,
            DLCopyGUI dlCopyGUI)
            throws InterruptedException, IOException {
        // this needs to be a script because of the shell globbing
        String copyScript = "#!/bin/bash\n"
                + "cp -av \"" + persistenceSourcePath + "/\"* \""
                + persistenceDestinationPath + "/\"";
        dlCopyGUI.showInstallPersistencyCopy(
                installer, copyScript, persistenceSourcePath);
    }

    private static void umountPartitions(String device, DLCopyGUI dlCopyGUI)
            throws IOException {
        LOGGER.log(Level.FINEST, "umountPartitions({0})", device);
        List<String> mounts
                = LernstickFileTools.readFile(new File("/proc/mounts"));
        for (String mount : mounts) {
            String mountedPartition = mount.split(" ")[0];
            if (mountedPartition.startsWith(device)) {
                umount(mountedPartition, dlCopyGUI);
            }
        }
    }

    private static boolean isMounted(String device) throws IOException {
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

    private static void swapoffFile(String device, String swapLine,
            DLCopyGUI dlCopyGUI) throws IOException {

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
            disableSwap = dlCopyGUI.showConfirmDialog(
                    STRINGS.getString("Warning"), warningMessage);
        }

        if (disableSwap) {
            int exitValue = PROCESS_EXECUTOR.executeProcess(
                    "swapoff", swapFile);
            if (exitValue != 0) {
                String errorMessage = STRINGS.getString("Error_Swapoff_File");
                errorMessage = MessageFormat.format(errorMessage, swapFile);
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
            }
        }
    }

    private static void swapoffPartition(String device, String swapLine,
            DLCopyGUI dlCopyGUI) throws IOException {

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
            disableSwap = dlCopyGUI.showConfirmDialog(
                    STRINGS.getString("Warning"), warningMessage);
        }

        if (disableSwap) {
            int exitValue = PROCESS_EXECUTOR.executeProcess(
                    "swapoff", swapFile);
            if (exitValue != 0) {
                String errorMessage
                        = STRINGS.getString("Error_Swapoff_Partition");
                errorMessage = MessageFormat.format(errorMessage, swapFile);
                LOGGER.severe(errorMessage);
                throw new IOException(errorMessage);
            }
        }
    }

    /**
     * returns the StorageDevice for a given dbus path
     *
     * @param path the dbus path
     * @param includeHardDisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @return the StorageDevice for a given dbus path
     * @throws DBusException if a dbus exception occurs
     */
    private static StorageDevice getStorageDevice(
            String path, boolean includeHardDisks) throws DBusException {

        LOGGER.log(Level.FINE, "\n"
                + "    thread: {0}\n"
                + "    path: {1}",
                new Object[]{Thread.currentThread().getName(), path});

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
            } catch (IOException | SAXException
                    | ParserConfigurationException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            size = DbusTools.getLongProperty(path, prefix + "Block", "Size");
            // the device is a char array terminated with a 0 byte
            // we need to remove the 0 byte!
            byte[] array = DbusTools.getByteArrayProperty(path,
                    prefix + "Block", "Device");
            deviceFile = new String(DbusTools.removeNullByte(array));
        }

        // early return for non-drives
        // (partitions, loop devices, empty optical drives, ...)
        if ((!isDrive) || isLoop || (size <= 0)) {
            logPath(path, isDrive, isLoop, size, deviceFile, false/*accepted*/);
            return null;
        }

        logPath(path, isDrive, isLoop, size, deviceFile, true/*accepted*/);

        StorageDevice storageDevice
                = new StorageDevice(deviceFile.substring(5));

        StorageDevice.Type deviceType = storageDevice.getType();
        if ((deviceType == StorageDevice.Type.HardDrive
                || deviceType == StorageDevice.Type.NVMe)
                && !includeHardDisks) {
            return null;
        } else {
            return storageDevice;
        }
    }

    private static void logPath(String path, boolean isDrive, boolean isLoop,
            long size, String deviceFile, boolean accepted) {

        LOGGER.log(Level.FINE,
                "\npath={0}\n"
                + "    isDrive: {1}\n"
                + "    isLoop: {2}\n"
                + "    size: {3}\n"
                + "    deviceFile: {4}\n"
                + "    {5}",
                new Object[]{path, isDrive, isLoop, size, deviceFile,
                    "--> " + (accepted
                            ? "accepted (detected as real drive)"
                            : "ignored (no drive)")});
    }
}
