package ch.fhnw.dlcopy;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import static ch.fhnw.util.StorageDevice.EfiUpgradeVariant;
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
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
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
    private final boolean deleteBackup;
    private final boolean upgradeSystemPartition;
    private final boolean resetDataPartition;
    private final boolean keepPrinterSettings;
    private final boolean keepNetworkSettings;
    private final boolean keepFirewallSettings;
    private final boolean keepUserSettings;
    private final boolean reactivateWelcome;
    private final boolean deleteHiddenFiles;
    private final List<String> filesToOverwrite;
    private final long systemSizeEnlarged;

    UserConfiguration userConfiguration;

    /**
     * Creates a new Upgrader
     *
     * @param source the system source
     * @param deviceList the list of StorageDevices to upgrade
     * @param exchangePartitionLabel the label of the exchange partition
     * @param exchangePartitionFileSystem the file system of the exchange
     * partition
     * @param dataPartitionFileSystem the file system of the data partition
     * @param digestCache a global digest cache for speeding up repeated file
     * checks
     * @param dlCopy the main DLCopy instance
     * @param dlCopyGUI the DLCopy GUI
     * @param repartitionStrategy the repartition strategie for the exchange
     * partition
     * @param resizedExchangePartitionSize the new size of the exchange
     * partition if we want to resize it during upgrade
     * @param automaticBackup if an automatic backup should be run before
     * upgrading
     * @param automaticBackupDestination the destination for automatic backups
     * @param deleteBackup if temporary backups should be deleted
     * @param upgradeSystemPartition if the system partition should be upgraded
     * @param resetDataPartition if the data partition should be reset
     * @param keepPrinterSettings if the printer settings should be kept when
     * upgrading
     * @param keepNetworkSettings if the network settings should be kept when
     * upgrading
     * @param keepFirewallSettings if the firewall settings should be kept when
     * upgrading
     * @param keepUserSettings if the user name, password and groups should be
     * kept when upgrading
     * @param reactivateWelcome if the welcome program should be reactivated
     * @param deleteHiddenFiles if hidden files in the user's home of the
     * storage device should be deleted
     * @param filesToOverwrite the list of files to copy from the currently
     * running system to the upgraded storage device
     * @param systemSizeEnlarged the "enlarged" system size (multiplied with a
     * small file system overhead factor)
     * @param lock the lock to aquire before executing in background
     */
    public Upgrader(SystemSource source, List<StorageDevice> deviceList,
            String exchangePartitionLabel, String exchangePartitionFileSystem,
            String dataPartitionFileSystem, HashMap<String, byte[]> digestCache,
            DLCopySwingGUI dlCopy, DLCopyGUI dlCopyGUI,
            RepartitionStrategy repartitionStrategy,
            int resizedExchangePartitionSize, boolean automaticBackup,
            String automaticBackupDestination, boolean deleteBackup,
            boolean upgradeSystemPartition, boolean resetDataPartition,
            boolean keepPrinterSettings, boolean keepNetworkSettings,
            boolean keepFirewallSettings, boolean keepUserSettings,
            boolean reactivateWelcome, boolean deleteHiddenFiles,
            List<String> filesToOverwrite, long systemSizeEnlarged, Lock lock) {

        super(source, deviceList, exchangePartitionLabel,
                exchangePartitionFileSystem, dataPartitionFileSystem,
                digestCache, dlCopyGUI, lock);

        this.repartitionStrategy = repartitionStrategy;
        this.resizedExchangePartitionSize = resizedExchangePartitionSize;
        this.automaticBackup = automaticBackup;
        this.automaticBackupDestination = automaticBackupDestination;
        this.deleteBackup = deleteBackup;
        this.upgradeSystemPartition = upgradeSystemPartition;
        this.resetDataPartition = resetDataPartition;
        this.keepPrinterSettings = keepPrinterSettings;
        this.keepNetworkSettings = keepNetworkSettings;
        this.keepFirewallSettings = keepFirewallSettings;
        this.keepUserSettings = keepUserSettings;
        this.reactivateWelcome = reactivateWelcome;
        this.deleteHiddenFiles = deleteHiddenFiles;
        this.filesToOverwrite = filesToOverwrite;
        this.systemSizeEnlarged = systemSizeEnlarged;
    }

    @Override
    protected Void doInBackground() throws Exception {

        Thread.currentThread().setName(getClass().getName());

        lock.lock();
        try {
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

                File dataDestination = new File(
                        getBackupDestination(storageDevice),
                        STRINGS.getString("Data_Partition"));
                dataDestination.mkdirs();

                String errorMessage = null;
                try {
                    StorageDevice.SystemUpgradeVariant upgradeVariant
                            = storageDevice.getSystemUpgradeVariant(
                                    DLCopy.getEnlargedSystemSize(
                                            source.getSystemSize()));
                    switch (upgradeVariant) {
                        case REGULAR:
                        case REPARTITION:
                            if (upgradeDataPartition(
                                    storageDevice, dataDestination)
                                    & upgradeSystemPartition) {
                                upgradeEfiAndSystemPartition(storageDevice);
                            }
                            break;

                        case BACKUP:
                            backupInstallRestore(storageDevice);
                            break;

                        case INSTALLATION:
                            // TODO: support encryption and checking of copies
                            DLCopy.copyToStorageDevice(source, fileCopier,
                                    storageDevice, exchangePartitionLabel,
                                    this, false, null, false, null, false,
                                    false, dlCopyGUI);
                            break;

                        default:
                            LOGGER.log(Level.WARNING,
                                    "Unsupported variant {0}", upgradeVariant);
                    }

                    // automatic removal of (temporary) backup
                    if (deleteBackup) {
                        LernstickFileTools.recursiveDelete(
                                dataDestination, true);
                    }

                } catch (Exception ex) {
                    // We really want to catch *ALL* exceptions here, including
                    // all possible unchecked runtime exceptions. Otherwise they
                    // just get lost as we dont catch them in the done() method.
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

        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void done() {
        if (inhibit != null) {
            inhibit.delete();
        }

        // the following try-catch block is needed to log otherwise invisible
        // runtime exceptions
        try {
            get();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "", ex);
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
            SQLException, NoSuchAlgorithmException {

        //TODO: union old squashfs and data partition!
        // prepare backup destination directories
        File dataDestination = new File(getBackupDestination(storageDevice),
                STRINGS.getString("Data_Partition"));
        dataDestination.mkdirs();
        File exchangeDestination = new File(getBackupDestination(storageDevice),
                STRINGS.getString("Exchange_Partition"));
        exchangeDestination.mkdirs();

        // backup
        Partition dataPartition = storageDevice.getDataPartition();
        String dataMountPoint = dataPartition.mount().getMountPath();
        backupUserData(dataMountPoint, dataDestination);
        dataPartition.umount();
        backupExchangePartition(storageDevice, exchangeDestination);

        // installation
        // TODO: support encryption and checking of copies
        DLCopy.copyToStorageDevice(source, fileCopier, storageDevice,
                exchangePartitionLabel, this, false, null, false, null, false,
                false, dlCopyGUI);

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
        if (keepNetworkSettings) {
            includes += '\n' + mountPoint + "/etc/NetworkManager/";
        }
        if (keepFirewallSettings) {
            includes += '\n' + mountPoint + "/etc/lernstick-firewall/";
        }

        // run the actual backup process
        rdiffBackupRestore.backupViaFileSystem(backupSource,
                backupDestination, null, mountPoint, includes, true, null,
                null, false, false, false, false, false);

        // cleanup
        backupTimer.stop();
    }

    private void backupExchangePartition(StorageDevice storageDevice,
            File exchangeDestination)
            throws DBusException, IOException, NoSuchAlgorithmException {

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

        File restoreDestinationDir;
        if (DLCopy.getMajorDebianVersion() > 8) {
            restoreDestinationDir = new File(mountPath, "rw");
        } else {
            restoreDestinationDir = new File(mountPath);
        }

        rdiffBackupRestore.restore("now", rdiffRoot,
                restoreSourceDir, restoreDestinationDir, null, false);

        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // !!! This must happen *after* restoring the files above.       !!!
        // !!! otherwise the changes would be overwritten by the restore !!!
        // !!! process (rdiff-backup)!                                   !!!
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // reactivate welcome, overwrite files...
        finalizeDataPartition(restoreDestinationDir.getPath());
        DLCopy.writePersistenceConf(mountPath);

        // cleanup
        dataPartition.umount();
        restoreTimer.stop();
    }

    private void restoreExchangePartition(
            StorageDevice storageDevice, File restoreSourceDir)
            throws DBusException, IOException,
            SQLException, NoSuchAlgorithmException {

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

        // union old squashfs with data partition
        Partition systemPartition = storageDevice.getSystemPartition();
        MountInfo systemMountInfo = systemPartition.mount();
        List<String> readOnlyMountPoints = LernstickFileTools.mountAllSquashFS(
                systemMountInfo.getMountPath());

        String cowPath;
        boolean mountAufs = false;
        boolean upgradeFromAufsToOverlay = false;
        int majorDebianVersion = DLCopy.getMajorDebianVersion();
        if (majorDebianVersion > 8) {
            // Until Debian 8 we used aufs for the data partition and the base
            // directory was just "/".
            // Starting with Debian 9 we use overlay for the data partition and
            // the base directory is "/rw/".
            // Therefore, when upgrading from Debian 8 to later versions we have
            // to move the directories "/home/" and "/etc/" to the new base.

            if (Files.exists(Paths.get(dataMountPoint, "rw"))
                    && Files.exists(Paths.get(dataMountPoint, "work"))) {
                // Debian 9 to Debian 9 or newer

                // There was a bug in DLCopy.formatPersistencePartition()
                // (introduced in git commit
                // 0e3a6249cd5177fe0f1f0fb1de7ce826f51629e3 and fixed in git
                // commit 6ce8559d864d79f1bee10669897a3c7aab90083e) where we
                // created the rw and work directories also in Debian 8.
                // Therefore we have to doublecheck here with some heuristic
                // if the medium is still a Debian 8 system.
                if (!Files.exists(Paths.get(dataMountPoint, "rw", "home"))
                        && Files.exists(Paths.get(dataMountPoint, "home"))) {
                    // OK, this is a Debian 8 system with our spurious and empty
                    // home and rw directories...
                    upgradeFromAufsToOverlay = true;
                    mountAufs = true;
                }
            } else {
                // Debian 8 to Debian 9 or newer
                upgradeFromAufsToOverlay = true;
                mountAufs = true;
            }
        } else {
            // Debian 8 to Debian 8
            mountAufs = true;
        }

        /**
         * DON'T use a temporary upper dir here. Otherwise we will most probably
         * run out of memory during the copyup operation when upgrading the
         * system partition. The finalizeDataPartition() later also needs
         * persistent write operations.
         */
        cowPath = mountDataPartition(
                dataMountPoint, readOnlyMountPoints, mountAufs);

        // backup
        if (automaticBackup) {
            backupUserData(cowPath, backupDestination);
            dlCopyGUI.showUpgradeDataPartitionReset();
        }

        // remember user settings before reset
        if (keepUserSettings) {
            userConfiguration = new UserConfiguration(cowPath);
        }

        // reset data partition
        if (resetDataPartition) {
            resetDataPartition(cowPath, dataMountPoint,
                    majorDebianVersion, upgradeFromAufsToOverlay);
            // the data partition gets unmounted by resetDataPartition
            // therefore we have to remount it here
            cowPath = mountDataPartition(
                    dataMountPoint, readOnlyMountPoints, mountAufs);
        }

        if (upgradeSystemPartition) {
            copyUp(cowPath);
        }

        // upgrading from aufs to overlay has to happen before calling
        // finalizeDataPartition() below!
        if (upgradeFromAufsToOverlay) {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            DLCopy.umount(cowPath, dlCopyGUI);

            // create new overlay directories "/work/" and "/rw/"
            Path workPath = Paths.get(dataMountPoint, "work");
            if (!Files.exists(workPath)) {
                Files.createDirectory(workPath);
            }
            Path rwPath = Paths.get(dataMountPoint, "rw");
            if (!Files.exists(rwPath)) {
                Files.createDirectory(rwPath);
            }

            // move "/home/" to "/rw/home/" and
            // move "/etc/" to "/rw/etc/"
            Path homeDir = Paths.get(dataMountPoint, "home");
            Path etcDir = Paths.get(dataMountPoint, "etc");
            Files.move(homeDir, rwPath.resolve(homeDir.getFileName()));
            Files.move(etcDir, rwPath.resolve(etcDir.getFileName()));

            // The "upgrade" to overlayfs is done now. In the next step we want
            // to mount our data partition as an overlayfs, therefore we set
            // mountAufs to false.
            mountAufs = false;
            // We also set temporaryUpperDir to false because we want to
            // finalize the data partition in the next step and therefore want
            // all write operations to be persistent.
            cowPath = mountDataPartition(
                    dataMountPoint, readOnlyMountPoints, mountAufs);
        }

        finalizeDataPartition(cowPath);
        DLCopy.writePersistenceConf(dataMountPoint);

        // disassemble union
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        DLCopy.umount(cowPath, dlCopyGUI);
        for (String readOnlyMountPoint : readOnlyMountPoints) {
            DLCopy.umount(readOnlyMountPoint, dlCopyGUI);
        }

        // umount
        if ((!dataMountInfo.alreadyMounted())
                && (!DLCopy.umount(dataPartition, dlCopyGUI))) {
            return false;
        }
        if ((!systemMountInfo.alreadyMounted())
                && (!DLCopy.umount(systemPartition, dlCopyGUI))) {
            return false;
        }

        // upgrade label (if necessary)
        if (!(dataPartition.getIdLabel().equals(Partition.PERSISTENCE_LABEL))) {
            ProcessExecutor processExecutor = new ProcessExecutor();
            processExecutor.executeProcess("e2label",
                    dataPartition.getFullDeviceAndNumber(),
                    Partition.PERSISTENCE_LABEL);
        }

        return true;
    }

    private void resetDataPartition(String cowPath, String dataMountPoint,
            int majorDebianVersion, boolean upgradeFromAufsToOverlay)
            throws IOException {

        // first umount the filesystem union (aufs or overlay)
        // (otherwise we would wreak havoc on the filesystem metadata)
        try {
            // Because we just mounted the cowPath, we have to wait here a
            // little bit. Otherwise umounting fails with error messages similar
            // to this one:
            // umount: /run/rw1/cow: target is busy
            TimeUnit.SECONDS.sleep(7);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        DLCopy.umount(cowPath, dlCopyGUI);

        List<String> excludes = new ArrayList<>();
        excludes.add("/home.*");
        String cleanupRoot = dataMountPoint;
        if (majorDebianVersion > 8 && !upgradeFromAufsToOverlay) {
            cleanupRoot = dataMountPoint + "/rw";
        } else {
            excludes.addAll(Arrays.asList(
                    "/lost\\+found", "/persistence.conf"));
        }
        if (keepPrinterSettings || keepNetworkSettings
                || keepFirewallSettings) {
            excludes.add("/etc.*");
        }
        cleanup(cleanupRoot, excludes);

        cleanupRoot += "/etc";
        excludes.clear();
        if (keepPrinterSettings || keepNetworkSettings
                || keepFirewallSettings) {

            if (keepPrinterSettings) {
                excludes.add("/cups.*");
            }
            if (keepNetworkSettings) {
                excludes.add("/NetworkManager.*");
            }
            if (keepFirewallSettings) {
                excludes.add("/lernstick-firewall.*");
            }
        }
        cleanup(cleanupRoot, excludes);
    }

    private String mountDataPartition(String dataMountPoint,
            List<String> readOnlyMountPoints, boolean mountAufs)
            throws IOException {

        if (mountAufs) {
            return LernstickFileTools.mountAufs(
                    dataMountPoint, readOnlyMountPoints).getPath();
        } else {
            // !!! DON'T use a temporary upper dir here !!!
            // Otherwise we will most probably run out of memory during
            // the copyup operation below.
            File overlayDir = LernstickFileTools.mountOverlay(dataMountPoint,
                    readOnlyMountPoints, false /*temporaryUpperDir*/);
            return new File(overlayDir, "merged").getPath();
        }
    }

    private int cleanup(String root, List<String> excludes) {
        List<String> commandList = new ArrayList<>();
        commandList.add("find");
        commandList.add(root);
        addExclude(commandList, root);
        excludes.forEach((exclude) -> {
            addExclude(commandList, root + exclude);
        });
        commandList.add("-delete");
        String[] command = commandList.toArray(new String[commandList.size()]);
        ProcessExecutor processExecutor = new ProcessExecutor();
        return processExecutor.executeProcess(true, true, command);
    }

    private void addExclude(List<String> commandList, String exclude) {
        commandList.addAll(Arrays.asList("!", "-regex", exclude));
    }

    private void copyUp(String cowPath) throws IOException {
        // Copy-up all personal data from old squashfs to data partition.
        // For aufs and overlayfs it is enough to change the access file stamp
        // of files and directories to trigger a copy-up action. Symbolic links
        // have to be recreated to be "copied up" to the data partition.
        final FileTime fileTime = FileTime.fromMillis(
                System.currentTimeMillis());
        FileVisitor<Path> copyUpFileVisitor = new SimpleFileVisitor<Path>() {
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
                            + "named pipe or unix domain socket, both are "
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

        copyUp(cowPath, "home", copyUpFileVisitor);

        if (keepPrinterSettings) {
            copyUp(cowPath, "etc/cups", copyUpFileVisitor);
        }
        if (keepNetworkSettings) {
            copyUp(cowPath, "etc/NetworkManager", copyUpFileVisitor);
        }
        if (keepFirewallSettings) {
            copyUp(cowPath, "etc/lernstick-firewall", copyUpFileVisitor);
        }
    }

    private void copyUp(String cowPath, String directory,
            FileVisitor<Path> fileVisitor) throws IOException {

        Path path = Paths.get(cowPath, directory);
        if (Files.exists(path)) {
            Files.walkFileTree(path, fileVisitor);
        }
    }

    private void finalizeDataPartition(String persistenceRoot)
            throws IOException {

        LOGGER.log(Level.INFO,
                "finalizing data partition, persistenceRoot: {0}",
                persistenceRoot);

        // welcome application reactivation
        if (reactivateWelcome) {
            File propertiesFile = new File(
                    persistenceRoot + "/etc/lernstickWelcome");
            LOGGER.log(Level.INFO,
                    "reactivating Welcome application, propertiesFile: {0}",
                    propertiesFile);
            Properties lernstickWelcomeProperties = new Properties();
            if (propertiesFile.exists()) {
                LOGGER.info("properties file already exists");
                try (FileReader reader = new FileReader(propertiesFile)) {
                    lernstickWelcomeProperties.load(reader);
                } catch (IOException iOException) {
                    LOGGER.log(Level.WARNING, "", iOException);
                }
            } else {
                LOGGER.info("creating new properties file");
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
        if (deleteHiddenFiles) {
            LOGGER.info("removing hidden files");
            File userDir = new File(persistenceRoot + "/home/user/");
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
            File destinationFile = new File(persistenceRoot, file);
            LernstickFileTools.recursiveDelete(destinationFile, true);

            // recursive copy
            processExecutor.executeProcess(true, true,
                    "cp", "-a", "--parents", file, persistenceRoot);
        }
    }

    private boolean upgradeEfiAndSystemPartition(StorageDevice storageDevice)
            throws DBusException, IOException,
            InterruptedException, NoSuchAlgorithmException, SQLException {

        String device = storageDevice.getDevice();
        String devicePath = "/dev/" + device;
        Partition efiPartition = storageDevice.getEfiPartition();
        Partition exchangePartition = storageDevice.getExchangePartition();
        Partition dataPartition = storageDevice.getDataPartition();
        Partition systemPartition = storageDevice.getSystemPartition();
        int systemPartitionNumber = systemPartition.getNumber();

        ProcessExecutor processExecutor = new ProcessExecutor();

        // (the list returned by getPartitions() is 0-based and getNumber()
        // is 1-based, so we really get the next partition...)
        Partition nextPartition = storageDevice.getPartitions().get(
                efiPartition.getNumber());
        File exchangeDestination = null;

        long neededEfiPartitionSize = DLCopy.EFI_PARTITION_SIZE * DLCopy.MEGA;
        EfiUpgradeVariant efiUpgradeVariant
                = storageDevice.getEfiUpgradeVariant(neededEfiPartitionSize);

        if (efiUpgradeVariant == EfiUpgradeVariant.ENLARGE_REPARTITION) {

            long offset = neededEfiPartitionSize - efiPartition.getSize();
            DLCopy.moveExtPartitionOffsetForward(
                    efiPartition, nextPartition, offset);
            DLCopy.formatEfiPartition(efiPartition.getFullDeviceAndNumber());
        } else if (efiUpgradeVariant == EfiUpgradeVariant.ENLARGE_BACKUP) {

            // backup
            exchangeDestination = new File(getBackupDestination(storageDevice),
                    STRINGS.getString("Exchange_Partition"));
            exchangeDestination.mkdirs();
            backupExchangePartition(storageDevice, exchangeDestination);

            dlCopyGUI.showUpgradeChangingPartitionSizes();

            // remove old EFI and exchange partitions
            efiPartition.remove();
            nextPartition.remove();

            // It might happen that the parted command returns but the device
            // nodes aren't created yet. See here for details:
            // https://www.gnu.org/software/parted/manual/parted.html#quit
            // Therefore we have to call DLCopy.waitForDeviceNodes() here for
            // the device nodes of both partitions to reappear. Otherwise
            // subsequent operations (like formatting the partitions) might
            // fail.
            //
            // create and format new EFI partition
            long nextOffset = efiPartition.getOffset() + neededEfiPartitionSize;
            storageDevice.createPrimaryPartition(efiPartition.getIdType(),
                    efiPartition.getOffset(), nextOffset - 1);
            String efiPartitionDevicePath
                    = efiPartition.getFullDeviceAndNumber();
            DLCopy.waitForDeviceNodes(Paths.get(efiPartitionDevicePath));
            DLCopy.formatEfiPartition(efiPartitionDevicePath);

            // create new exchange partition
            long nextEnd = nextPartition.getOffset()
                    + nextPartition.getSize() - 1;
            storageDevice.createPrimaryPartition(
                    nextPartition.getIdType(), nextOffset, nextEnd);
            String exchangePartitionDevicePath
                    = exchangePartition.getFullDeviceAndNumber();
            DLCopy.waitForDeviceNodes(Paths.get(exchangePartitionDevicePath));
            DLCopy.formatExchangePartition(exchangePartitionDevicePath,
                    exchangePartition.getIdLabel(),
                    exchangePartition.getIdType(), dlCopyGUI);
        }

        if (efiUpgradeVariant != EfiUpgradeVariant.REGULAR) {
            // must update partition info because of changes above
            TimeUnit.SECONDS.sleep(7);
            storageDevice = new StorageDevice(storageDevice.getDevice());
            efiPartition = storageDevice.getEfiPartition();
            exchangePartition = storageDevice.getExchangePartition();
            dataPartition = storageDevice.getDataPartition();
            systemPartition = storageDevice.getSystemPartition();
            systemPartitionNumber = systemPartition.getNumber();
        }

        if (efiUpgradeVariant == EfiUpgradeVariant.ENLARGE_BACKUP) {
            restoreExchangePartition(storageDevice, exchangeDestination);
            dlCopyGUI.showUpgradeChangingPartitionSizes();
        }

        // make sure that systemPartition is unmounted
        if (!DLCopy.umount(systemPartition, dlCopyGUI)) {
            return false;
        }

        long enlargedSystemSize
                = DLCopy.getEnlargedSystemSize(source.getSystemSize());
        if (storageDevice.getSystemUpgradeVariant(enlargedSystemSize)
                == StorageDevice.SystemUpgradeVariant.REPARTITION) {

            dlCopyGUI.showUpgradeChangingPartitionSizes();

            // TODO: search partition that needs to be shrinked
            // (for now we simply assume it's the data partition)
            if (!DLCopy.umount(dataPartition, dlCopyGUI)) {
                return false;
            }
            String dataDevPath = dataPartition.getFullDeviceAndNumber();
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
                    efiPartition.getFullDeviceAndNumber(),
                    systemPartition.getFullDeviceAndNumber());

            // update boot flag
            efiPartition.setBootFlag(true);
            systemPartition.setBootFlag(false);
            // we have to wait for d-bus to settle after changing the boot flag
            TimeUnit.SECONDS.sleep(7);
        }

        // upgrade EFI and system partition
        dlCopyGUI.showUpgradeSystemPartitionReset();

        if (!efiPartition.getIdLabel().equals(Partition.EFI_LABEL)) {
            // The EFI partition is a pre 2016-02 boot partition with the label
            // "boot".
            // We need to upgrade that to the current partitioning schema where
            // the EFI partition has the label "EFI", has a boot flag but the
            // system partition has no boot flag.
            DLCopy.formatEfiAndSystemPartition(
                    efiPartition.getFullDeviceAndNumber(),
                    systemPartition.getFullDeviceAndNumber());

            efiPartition.setBootFlag(true);
            systemPartition.setBootFlag(false);
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

        // clean up EFI and system partition
        cleanupPartition(new File(copyJobsInfo.getDestinationEfiPath()));
        cleanupPartition(new File(copyJobsInfo.getDestinationSystemPath()));

        LOGGER.info("starting copy job");
        dlCopyGUI.showUpgradeFileCopy(fileCopier);

        CopyJob bootFilesCopyJob = copyJobsInfo.getExchangeEfiCopyJob();
        fileCopier.copy(copyJobsInfo.getEfiCopyJob(),
                bootFilesCopyJob, copyJobsInfo.getSystemCopyJob());

        dlCopyGUI.showUpgradeUnmounting();
        DLCopy.isolinuxToSyslinux(
                copyJobsInfo.getDestinationEfiPath(), dlCopyGUI);

        // make storage device bootable
        dlCopyGUI.showUpgradeWritingBootSector();
        DLCopy.makeBootable(source, devicePath, efiPartition);

        if (keepUserSettings && (userConfiguration.getPasswdLine() != null
                || userConfiguration.getShadowLine() != null
                || userConfiguration.getGroupLine() != null
                || userConfiguration.getGroups() != null
                || userConfiguration.getGdmAutoLogin() != null)) {

            // restore user settings after upgrading
            dataPartition = storageDevice.getDataPartition();
            MountInfo dataMountInfo = dataPartition.mount();
            String dataMountPoint = dataMountInfo.getMountPath();

            MountInfo systemMountInfo = systemPartition.mount();
            List<String> readOnlyMountPoints
                    = LernstickFileTools.mountAllSquashFS(
                            systemMountInfo.getMountPath());
            String cowPath = mountDataPartition(
                    dataMountPoint, readOnlyMountPoints, false);

            userConfiguration.apply(cowPath);

            // umount
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            DLCopy.umount(cowPath, dlCopyGUI);
            for (String readOnlyMountPoint : readOnlyMountPoints) {
                DLCopy.umount(readOnlyMountPoint, dlCopyGUI);
            }
            if ((!dataMountInfo.alreadyMounted())
                    && (!DLCopy.umount(dataPartition, dlCopyGUI))) {
                return false;
            }
        }

        // cleanup
        source.unmountTmpPartitions();
        if (!DLCopy.umount(efiPartition, dlCopyGUI)) {
            return false;
        }
        return DLCopy.umount(systemPartition, dlCopyGUI);
    }

    private void cleanupPartition(File moutPoint) throws IOException {
        LOGGER.log(Level.INFO, "recursively deleting {0}", moutPoint);
        // The file syslinux/ldlinux.sys has the immutable flag set. To be able
        // to remove this file, we first have to remove the immutable flag.
        String ldLinuxPath = moutPoint + "/syslinux/ldlinux.sys";
        if (new File(ldLinuxPath).exists()) {
            ProcessExecutor processExecutor = new ProcessExecutor();
            processExecutor.executeProcess("chattr", "-i", ldLinuxPath);
        }
        LernstickFileTools.recursiveDelete(moutPoint, false);

    }

    @Override
    public PartitionSizes getPartitionSizes(StorageDevice storageDevice) {
        return DLCopy.getUpgradePartitionSizes(source, storageDevice,
                repartitionStrategy, resizedExchangePartitionSize);
    }
}
