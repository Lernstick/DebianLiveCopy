package ch.fhnw.dlcopy;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.swing.DLCopySwingGUI;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import ch.fhnw.jbackpack.RdiffBackupRestore;
import ch.fhnw.jbackpack.chooser.Increment;
import ch.fhnw.jbackpack.chooser.RdiffFile;
import ch.fhnw.jbackpack.chooser.RdiffFileDatabase;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Upgrades a list of StorageDevices from an InstallationSource
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Upgrader extends InstallerOrUpgrader {

    private static final Logger LOGGER
            = Logger.getLogger(Upgrader.class.getName());

    private final RepartitionStrategy repartitionStrategy;
    private final int resizedExchangePartitionSize;
    private final boolean automaticBackup;
    private final String automaticBackupDestination;
    private final boolean removeBackup;
    private final boolean upgradeSystemPartition;
    private final boolean keepPrinterSettings;
    private final boolean reactivateWelcome;
    private final boolean removeHiddenFiles;
    private final List<String> filesToOverwrite;
    private final long systemSizeEnlarged;

    /**
     * Creates a new Upgrader
     *
     * @param source the system source
     * @param deviceList the list of StorageDevices to upgrade
     * @param exchangePartitionLabel the label of the exchange partition
     * @param exchangePartitionFileSystem the file system of the exchange
     * partition
     * @param dataPartitionFileSystem the file system of the data partition
     * @param dlCopy the main DLCopy instance
     * @param dlCopyGUI the DLCopy GUI
     * @param repartitionStrategy the repartition strategie for the exchange
     * partition
     * @param resizedExchangePartitionSize the new size of the exchange
     * partition if we want to resize it during upgrade
     * @param automaticBackup if an automatic backup should be run before
     * upgrading
     * @param automaticBackupDestination the destination for automatic backups
     * @param removeBackup if temporary backups should be removed
     * @param upgradeSystemPartition if the system partition should be upgraded
     * @param keepPrinterSettings if the printer settings should be kept when
     * upgrading
     * @param reactivateWelcome if the welcome program should be reactivated
     * @param removeHiddenFiles if hidden files in the user's home of the
     * storage device should be removed
     * @param filesToOverwrite the list of files to copy from the currently
     * running system to the upgraded storage device
     * @param systemSizeEnlarged the "enlarged" system size (multiplied with a
     * small file system overhead factor)
     */
    public Upgrader(SystemSource source, List<StorageDevice> deviceList,
            String exchangePartitionLabel, String exchangePartitionFileSystem,
            String dataPartitionFileSystem, DLCopySwingGUI dlCopy,
            DLCopyGUI dlCopyGUI, RepartitionStrategy repartitionStrategy,
            int resizedExchangePartitionSize, boolean automaticBackup,
            String automaticBackupDestination, boolean removeBackup,
            boolean upgradeSystemPartition, boolean keepPrinterSettings,
            boolean reactivateWelcome, boolean removeHiddenFiles,
            List<String> filesToOverwrite, long systemSizeEnlarged) {
        super(source, deviceList, exchangePartitionLabel,
                exchangePartitionFileSystem, dataPartitionFileSystem,
                dlCopyGUI);
        this.repartitionStrategy = repartitionStrategy;
        this.resizedExchangePartitionSize = resizedExchangePartitionSize;
        this.automaticBackup = automaticBackup;
        this.automaticBackupDestination = automaticBackupDestination;
        this.removeBackup = removeBackup;
        this.upgradeSystemPartition = upgradeSystemPartition;
        this.keepPrinterSettings = keepPrinterSettings;
        this.reactivateWelcome = reactivateWelcome;
        this.removeHiddenFiles = removeHiddenFiles;
        this.filesToOverwrite = filesToOverwrite;
        this.systemSizeEnlarged = systemSizeEnlarged;
    }

    @Override
    protected Void doInBackground() throws Exception {
        inhibit = new LogindInhibit("Upgrading");

        // upgrade all selected storage devices
        int batchCounter = 0;
        for (StorageDevice storageDevice : deviceList) {

            // update overall progress message
            batchCounter++;
            dlCopyGUI.upgradingDeviceStarted(storageDevice);
            LOGGER.log(Level.INFO,
                    "upgrading storage device: {0} of {1} ({2})",
                    new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

            File backupDestination = getBackupDestination(storageDevice);

            String errorMessage = null;
            try {
                StorageDevice.UpgradeVariant upgradeVariant
                        = storageDevice.getUpgradeVariant(
                                DLCopy.getEnlargedSystemSize(
                                        source.getSystemSize()));
                switch (upgradeVariant) {
                    case REGULAR:
                    case REPARTITION:
                        if (upgradeDataPartition(
                                storageDevice, backupDestination)
                                & upgradeSystemPartition) {
                            upgradeSystemPartition(storageDevice);
                        }
                        break;

                    case BACKUP:
                        backupInstallRestore(storageDevice);
                        break;

                    case INSTALLATION:
                        DLCopy.copyToStorageDevice(source, fileCopier,
                                storageDevice, exchangePartitionLabel,
                                this, dlCopyGUI);
                        break;

                    default:
                        LOGGER.log(Level.WARNING,
                                "Unsupported variant {0}", upgradeVariant);
                }

                // automatic removal of (temporary) backup
                if (removeBackup) {
                    LernstickFileTools.recursiveDelete(backupDestination, true);
                }
            } catch (DBusException | IOException | InterruptedException ex) {
                LOGGER.log(Level.WARNING, "", ex);
                errorMessage = ex.getMessage();
            }

            dlCopyGUI.upgradingDeviceFinished(errorMessage);

            LOGGER.log(Level.INFO, "upgrading of storage device finished: "
                    + "{0} of {1} ({2})", new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });
        }

        return null;
    }

    @Override
    protected void done() {
        if (inhibit != null) {
            inhibit.delete();
        }
        dlCopyGUI.upgradingListFinished();
    }

    @Override
    public void showCreatingFileSystems() {
        dlCopyGUI.showUpgradeCreatingFileSystems();
    }

    @Override
    public void showCopyingFiles(FileCopier fileCopier) {
        dlCopyGUI.showUpgradeFileCopy(fileCopier);
    }

    @Override
    public void showUnmounting() {
        dlCopyGUI.showUpgradeUnmounting();
    }

    @Override
    public void showWritingBootSector() {
        dlCopyGUI.showUpgradeWritingBootSector();
    }

    private File getBackupDestination(StorageDevice storageDevice) {
        // use the device serial number as unique identifier for backups
        // (but replace all slashes because they are not allowed in
        //  directory names)
        String backupUID = storageDevice.getSerial().replaceAll("/", "-");
        return new File(automaticBackupDestination, backupUID);
    }

    private void backupInstallRestore(StorageDevice storageDevice)
            throws InterruptedException, IOException, DBusException,
            SQLException {

        //TODO: union old squashfs and data partition!
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
        DLCopy.copyToStorageDevice(source, fileCopier, storageDevice,
                exchangePartitionLabel, this, dlCopyGUI);

        // !!! update reference to storage device !!!
        // copyToStorageDevice() may change the storage device completely
        storageDevice = new StorageDevice(storageDevice.getDevice());
        restoreDataPartition(storageDevice, dataDestination);
        restoreExchangePartition(storageDevice, exchangeDestination);
    }

    private void backupUserData(String mountPoint, File backupDestination)
            throws IOException {

        // prepare backup run
        File backupSource = new File(mountPoint);
        RdiffBackupRestore rdiffBackupRestore = new RdiffBackupRestore();
        Timer backupTimer = new Timer(1000, new BackupActionListener(
                true, rdiffBackupRestore, dlCopyGUI));
        backupTimer.setInitialDelay(0);
        backupTimer.start();
        dlCopyGUI.showUpgradeBackup();

        String includes = mountPoint + "/home/user/";
        if (keepPrinterSettings) {
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
        dlCopyGUI.showUpgradeBackupExchangePartition(fileCopier);

        // Unfortunately, rdiffbackup does not work with exFAT or NTFS.
        // Both filesystems are possible on the exchange partition.
        // Therefore we just make a simple copy.
        LernstickFileTools.recursiveDelete(exchangeDestination, false);
        Source[] sources = new Source[]{new Source(mountPath, ".*")};
        String[] destinations = new String[]{exchangeDestination.getPath()};
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
        dlCopyGUI.showUpgradeRestoreInit();

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
        RdiffBackupRestore rdiffBackupRestore = new RdiffBackupRestore();
        Timer restoreTimer = new Timer(1000, new BackupActionListener(
                false, rdiffBackupRestore, dlCopyGUI));
        restoreTimer.setInitialDelay(0);
        restoreTimer.start();

        dlCopyGUI.showUpgradeRestoreRunning();

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

        dlCopyGUI.showUpgradeRestoreExchangePartition(fileCopier);

        Source[] sources = new Source[]{
            new Source(restoreSourceDir.getPath(), ".*")};
        String[] destinations = new String[]{
            exchangePartition.mount().getMountPath()};
        fileCopier.copy(new CopyJob(sources, destinations));

        exchangePartition.umount();
    }

    private boolean upgradeDataPartition(StorageDevice storageDevice,
            File backupDestination) throws DBusException, IOException {

        dlCopyGUI.showUpgradeDataPartitionReset();

        Partition dataPartition = storageDevice.getDataPartition();
        if (dataPartition == null) {
            LOGGER.log(Level.WARNING,
                    "skipping /dev/{0} because it has no data partition",
                    storageDevice.getDevice());
            return true;
        }

        MountInfo dataMountInfo = dataPartition.mount();
        String dataMountPoint = dataMountInfo.getMountPath();

        // union old squashfs with data partitin
        MountInfo systemMountInfo
                = storageDevice.getSystemPartition().mount();
        List<String> readOnlyMountPoints = LernstickFileTools.mountAllSquashFS(
                systemMountInfo.getMountPath());
        String branchDefinition = LernstickFileTools.getBranchDefinition(
                dataMountPoint, readOnlyMountPoints);
        File cowDir = LernstickFileTools.mountAufs(branchDefinition);
        String cowPath = cowDir.getPath();

        // backup
        if (automaticBackup) {
            backupUserData(cowPath, backupDestination);
        }

        // reset data partition
        // first umount the aufs
        // (otherwise we would wreak havoc on the aufs metadata)
        DLCopy.umount(cowPath, dlCopyGUI);
        ProcessExecutor processExecutor = new ProcessExecutor();
        if (keepPrinterSettings) {
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
        // re-mount aufs
        cowDir = LernstickFileTools.mountAufs(branchDefinition);
        cowPath = cowDir.getPath();

        // Copy-up all personal data from old squashfs to data partition.
        // For aufs it is enough to change the access file stamp of files and
        // directories to trigger a copy-up action. Symbolic links have to be
        // recreated to be "copied up" to the data partition.
        final FileTime fileTime = FileTime.fromMillis(
                System.currentTimeMillis());
        FileVisitor copyUpFileVisitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    // recreate symbolic link
                    Path target = Files.readSymbolicLink(file);
                    LOGGER.log(Level.INFO,
                            "recreating symbolic link {0} > {1}",
                            new Object[]{file, target});
                    Files.delete(file);
                    Files.createSymbolicLink(file, target);
                } else if (attributes.isOther()) {
                    LOGGER.log(Level.WARNING, "skipping {0} (probably a "
                            + "named pipe or unix domain socket, bot are "
                            + "not supported!)", file);
                } else {
                    changeAccessTime(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attrs) throws IOException {
                changeAccessTime(dir);
                return FileVisitResult.CONTINUE;
            }

            private void changeAccessTime(Path path) throws IOException {
                // change file access time
                LOGGER.log(Level.INFO, "updating access time of {0}", path);
                BasicFileAttributeView attributeView
                        = Files.getFileAttributeView(
                                path, BasicFileAttributeView.class);
                attributeView.setTimes(null, fileTime, null);
            }
        };
        Files.walkFileTree(Paths.get(cowPath, "home"), copyUpFileVisitor);
        if (keepPrinterSettings) {
            Files.walkFileTree(Paths.get(
                    cowPath, "etc/cups"), copyUpFileVisitor);
        }

        finalizeDataPartition(cowPath);

        // disassemble union
        DLCopy.umount(cowPath, dlCopyGUI);
        for (String readOnlyMountPoint : readOnlyMountPoints) {
            DLCopy.umount(readOnlyMountPoint, dlCopyGUI);
        }

        // umount
        if ((!dataMountInfo.alreadyMounted())
                && (!DLCopy.umount(dataPartition, dlCopyGUI))) {
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
        if (reactivateWelcome) {
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
        if (removeHiddenFiles) {
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
        ProcessExecutor processExecutor = new ProcessExecutor();
        for (String file : filesToOverwrite) {
            File destinationFile = new File(dataMountPoint, file);
            LernstickFileTools.recursiveDelete(destinationFile, true);

            // recursive copy
            processExecutor.executeProcess(true, true,
                    "cp", "-a", "--parents", file, dataMountPoint);
        }

        // when upgrading from very old versions, the persistence.conf file
        // is still missing and we have to add it now
        File persistenceConf = new File(dataMountPoint, "persistence.conf");
        if (!persistenceConf.exists()) {
            DLCopy.writePersistenceConf(dataMountPoint);
        }
    }

    private boolean upgradeSystemPartition(StorageDevice storageDevice)
            throws DBusException, IOException, InterruptedException {

        String device = storageDevice.getDevice();
        String devicePath = "/dev/" + device;
        Partition efiPartition = storageDevice.getEfiPartition();
        Partition exchangePartition = storageDevice.getExchangePartition();
        Partition dataPartition = storageDevice.getDataPartition();
        Partition systemPartition = storageDevice.getSystemPartition();
        int systemPartitionNumber = systemPartition.getNumber();

        ProcessExecutor processExecutor = new ProcessExecutor();

        // make sure that systemPartition is unmounted
        if (!DLCopy.umount(systemPartition, dlCopyGUI)) {
            return false;
        }

        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(source.getSystemSize());
        if (storageDevice.getUpgradeVariant(enlargedSystemSize)
                == StorageDevice.UpgradeVariant.REPARTITION) {

            dlCopyGUI.showUpgradeChangingPartitionSizes();

            // TODO: search partition that needs to be shrinked
            // (for now we simply assume it's the data partition)
            if (!DLCopy.umount(dataPartition, dlCopyGUI)) {
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
                        dlCopyGUI.showErrorMessage(errorMessage);
                        return false;
                    }

                    // let's wait some time before retrying
                    try {
                        LOGGER.info(
                                "waiting for 10 seconds before continuing...");
                        TimeUnit.SECONDS.sleep(10);
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
                    dlCopyGUI.showErrorMessage(errorMessage);
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
                dlCopyGUI.showErrorMessage(errorMessage);
                return false;
            }

            long dataPartitionOffset = dataPartition.getOffset();
            long newSystemPartitionOffset = systemPartition.getOffset()
                    + systemPartition.getSize() - systemSizeEnlarged;
            // align newSystemPartitionOffset on a MiB boundary
            newSystemPartitionOffset /= DLCopy.MEGA;
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
                dlCopyGUI.showErrorMessage(errorMessage);
                return false;
            }
            // refresh storage device and partition info
            processExecutor.executeProcess(true, true, "/sbin/partprobe");
            // safety wait so that new partitions are known to the system
            // (7 seconds were NOT enough!)
            TimeUnit.SECONDS.sleep(7);

            returnValue = processExecutor.executeProcess(true, true,
                    "resize2fs", dataDevPath);
            if (returnValue != 0) {
                String errorMessage
                        = STRINGS.getString("Error_File_System_Resize");
                errorMessage
                        = MessageFormat.format(errorMessage, dataDevPath);
                dlCopyGUI.showErrorMessage(errorMessage);
                return false;
            }

            storageDevice = new StorageDevice(device);
            // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            // ! We can't use storageDevice.getSystemPartition() here      !
            // ! because the partitions dont have the necessary properties,!
            // ! yet. We will set them in the next steps.                  !
            // ! !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
            List<Partition> partitions = storageDevice.getPartitions();
            systemPartition = partitions.get(systemPartitionNumber - 1);

            DLCopy.formatEfiAndSystemPartition(
                    "/dev/" + efiPartition.getDeviceAndNumber(),
                    "/dev/" + systemPartition.getDeviceAndNumber());

            // update boot flag
            efiPartition.setBootFlag(false);
            systemPartition.setBootFlag(true);
            // we have to wait for d-bus to settle after changing the boot flag
            TimeUnit.SECONDS.sleep(7);
        }

        // upgrade boot and system partition
        dlCopyGUI.showUpgradeSystemPartitionReset();

        if (!efiPartition.getIdLabel().equals(Partition.EFI_LABEL)) {
            // The EFI partition is a pre 2016-02 boot partition with the label
            // "boot", a boot flag and the system partition has no boot flag.
            // We need to upgrade that to the current partitioning schema where
            // the EFI partition has the label "EFI" and has no boot flag but
            // the system partition has one.
            DLCopy.formatEfiAndSystemPartition(
                    "/dev/" + efiPartition.getDeviceAndNumber(),
                    "/dev/" + systemPartition.getDeviceAndNumber());

            efiPartition.setBootFlag(false);
            systemPartition.setBootFlag(true);
            // we have to wait for d-bus to settle after changing the boot flag
            TimeUnit.SECONDS.sleep(7);
        }

        // define CopyJobs for boot and system parititions
        String exchangePartitionFS = null;
        if ((exchangePartition != null)
                && "vfat".equals(exchangePartition.getIdType())) {
            exchangePartitionFS = "fat32";
        }
        // TODO: mapping of other file systems

        CopyJobsInfo copyJobsInfo = DLCopy.prepareEfiAndSystemCopyJobs(source,
                storageDevice, efiPartition, exchangePartition,
                systemPartition, exchangePartitionFS);
        File bootMountPointFile = new File(
                copyJobsInfo.getDestinationEfiPath());
        LOGGER.log(Level.INFO, "recursively deleting {0}",
                bootMountPointFile);
        LernstickFileTools.recursiveDelete(bootMountPointFile, false);
        String destinationSystemPath = copyJobsInfo.getDestinationSystemPath();
        File systemMountPointFile = new File(destinationSystemPath);
        LOGGER.log(Level.INFO, "recursively deleting {0}",
                systemMountPointFile);
        // The file syslinux/ldlinux.sys has the immutable flag set. To be able
        // to remove this file, we first have to remove the immutable flag.
        String ldLinuxPath = destinationSystemPath + "/syslinux/ldlinux.sys";
        if (new File(ldLinuxPath).exists()) {
            processExecutor.executeProcess("chattr", "-i", ldLinuxPath);
        }
        LernstickFileTools.recursiveDelete(systemMountPointFile, false);

        LOGGER.info("starting copy job");
        dlCopyGUI.showUpgradeFileCopy(fileCopier);

        CopyJob bootFilesCopyJob = copyJobsInfo.getExchangeEfiCopyJob();
        fileCopier.copy(copyJobsInfo.getEfiCopyJob(),
                bootFilesCopyJob, copyJobsInfo.getSystemCopyJob());

        // hide boot files in exchange partition
        // (only necessary with FAT32 on removable media...)
        if (bootFilesCopyJob != null) {
            String exchangePath = exchangePartition.getMountPath();
            DLCopy.hideBootFiles(bootFilesCopyJob, exchangePath);
            DLCopy.umount(exchangePartition, dlCopyGUI);
        }

        dlCopyGUI.showUpgradeUnmounting();
        DLCopy.isolinuxToSyslinux(
                copyJobsInfo.getDestinationSystemPath(), dlCopyGUI);

        // make storage device bootable
        dlCopyGUI.showUpgradeWritingBootSector();
        DLCopy.makeBootable(source, devicePath, systemPartition);

        // cleanup
        source.unmountTmpPartitions();
        if (!DLCopy.umount(efiPartition, dlCopyGUI)) {
            return false;
        }
        return DLCopy.umount(systemPartition, dlCopyGUI);
    }

    @Override
    public PartitionSizes getPartitionSizes(StorageDevice storageDevice) {
        return DLCopy.getUpgradePartitionSizes(source, storageDevice,
                repartitionStrategy, resizedExchangePartitionSize);
    }
}
