package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import ch.fhnw.util.StorageTools;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The currently running live system.
 */
public final class RunningSystemSource extends SystemSource {

    private final static Logger LOGGER
            = Logger.getLogger(RunningSystemSource.class.getName());

    private final ProcessExecutor processExecutor;
    private final DebianLiveVersion runningVersion;
    private final DataPartitionMode dataPartitionMode;
    private final StorageDevice storageDevice;
    private final Partition efiPartition;
    private final Partition exchangePartition;
    private final Partition dataPartition;
    private final boolean hasLegacyGrub;

    private String efiPath = null;
    private boolean isEfiTmpMounted = false;

    private String exchangePath = null;
    private boolean isExchangeTmpMounted = false;

    /**
     * creates a new SystemInstallationSource
     *
     * @param processExecutor the ProcessExecutor to use
     * @throws DBusException
     * @throws IOException
     */
    public RunningSystemSource(ProcessExecutor processExecutor)
            throws DBusException, IOException {
        this.processExecutor = processExecutor;
        runningVersion = DebianLiveVersion.getRunningVersion();
        if (runningVersion == null) {
            throw new IllegalArgumentException(
                    "Unable to detect running system version");
        }
        storageDevice = StorageTools.getSystemStorageDevice();

        LOGGER.log(Level.INFO,
                "system storage device: {0}", storageDevice);

        exchangePartition = storageDevice.getExchangePartition();
        LOGGER.log(Level.INFO,
                "system exchange partition: {0}", exchangePartition);

        dataPartition = storageDevice.getDataPartition();
        LOGGER.log(Level.INFO,
                "system data partition: {0}", dataPartition);

        efiPartition = storageDevice.getEfiPartition();
        LOGGER.log(Level.INFO,
                "system EFI partition: {0}", efiPartition);

        // determine mode of data partition
        // The boot config files were on the system partition on legacy systems
        // and are there again after 2016-03 when we changed the partition/file
        // structure once more to support kernel updates.
        DataPartitionMode tmpMode
                = BootConfigUtil.getDataPartitionMode(getSystemPath());
        if (tmpMode == null) {
            // There was a longer timeframe before 2016-03 when we had the boot
            // config files on the boot/EFI partition.
            if (hasEfiPartition()) {
                MountInfo efiMountInfo = efiPartition.mount();
                dataPartitionMode = BootConfigUtil.getDataPartitionMode(
                        efiMountInfo.getMountPath());
                if (!efiMountInfo.alreadyMounted()) {
                    efiPartition.umount();
                }
            } else {
                dataPartitionMode = null;
            }
        } else {
            dataPartitionMode = tmpMode;
        }

        if (dataPartition == null) {
            LOGGER.log(Level.WARNING,
                    "unable to determine data partition mode");
        } else {
            LOGGER.log(Level.INFO, "data partition mode: {0}",
                    dataPartitionMode);
        }

        hasLegacyGrub = hasLegacyGrub();
        LOGGER.log(Level.INFO, "system GRUB is a {0} version",
                (hasLegacyGrub ? "legacy" : "current"));
    }

    @Override
    public String getDeviceName() {
        return storageDevice.getDevice();
    }

    @Override
    public StorageDevice.Type getDeviceType() {
        return storageDevice.getType();
    }

    @Override
    public boolean hasEfiPartition() {
        return efiPartition != null;
    }

    @Override
    public boolean hasExchangePartition() {
        return exchangePartition != null;
    }

    @Override
    public DataPartitionMode getDataPartitionMode() {
        return dataPartitionMode;
    }

    @Override
    public DebianLiveVersion getSystemVersion() {
        return runningVersion;
    }

    @Override
    public String getSystemPath() {
        return runningVersion.getLiveSystemPath();
    }

    @Override
    public long getSystemSize() {
        return StorageTools.getSystemSize();
    }

    @Override
    public Source getEfiCopySource() throws DBusException, IOException {
        return new Source(getBasePath(), hasLegacyGrub
                ? SystemSource.LEGACY_EFI_COPY_PATTERN
                : SystemSource.EFI_COPY_PATTERN);
    }

    @Override
    public Source getSystemCopySourceBoot() {
        return new Source(getSystemPath(), hasLegacyGrub
                ? SystemSource.LEGACY_SYSTEM_COPY_PATTERN_BOOT
                : SystemSource.SYSTEM_COPY_PATTERN_BOOT);
    }

    @Override
    public Source getSystemCopySourceFull() {
        return new Source(getSystemPath(), hasLegacyGrub
                ? SystemSource.LEGACY_SYSTEM_COPY_PATTERN_FULL
                : SystemSource.SYSTEM_COPY_PATTERN_FULL);
    }

    @Override
    public Source getPersistentCopySource() {
        // not yet support for copy job
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Source getExchangeCopySource() throws DBusException, IOException {
        if (hasExchangePartition()) {
            mountExchangeIfNeeded();
            return new Source(exchangePath, ".*");
        }
        return null;
    }

    @Override
    public Partition getEfiPartition() {
        return efiPartition;
    }

    @Override
    public Partition getExchangePartition() {
        return exchangePartition;
    }

    @Override
    public Partition getDataPartition() {
        return dataPartition;
    }

    @Override
    public String getMbrPath() {
        return runningVersion.getMbrFilePath();
    }

    @Override
    public void installExtlinux(Partition bootPartition) throws IOException {
        String syslinuxDir = createSyslinuxDir(bootPartition);
        int returnValue = processExecutor.executeProcess(true, true,
                "extlinux", "-i", syslinuxDir);
        if (returnValue != 0) {
            throw new IOException(
                    "extlinux failed with the following output: "
                    + processExecutor.getOutput());
        }
    }

    @Override
    public void unmountTmpPartitions() {
        if (isEfiTmpMounted && efiPath != null) {
            try {
                efiPartition.umount();
            } catch (DBusException | IOException ex) {
                LOGGER.log(Level.SEVERE, "unmount boot", ex);
            }
            efiPath = null;
        }
        if (isExchangeTmpMounted && exchangePath != null) {
            try {
                exchangePartition.umount();
            } catch (DBusException | IOException ex) {
                LOGGER.log(Level.SEVERE, "unmount exchange", ex);
            }
            exchangePath = null;
        }

    }

    private void mountEfiIfNeeded() throws DBusException, IOException {
        if (efiPath == null) {
            MountInfo efiMountInfo = efiPartition.mount();
            efiPath = efiMountInfo.getMountPath();
            isEfiTmpMounted = !efiMountInfo.alreadyMounted();
        }
    }

    private void mountExchangeIfNeeded() throws DBusException, IOException {
        if (exchangePath == null) {
            MountInfo bootMountInfo = exchangePartition.mount();
            exchangePath = bootMountInfo.getMountPath();
            isExchangeTmpMounted = !bootMountInfo.alreadyMounted();
        }
    }

    private boolean hasLegacyGrub() throws IOException, DBusException {
        try {
            String filePath = findGrubEfiFile(getBasePath());
            return GRUB_LEGACY_MD5.equals(DLCopy.getMd5String(filePath));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        } finally {
            unmountTmpPartitions();
        }
    }

    private String getBasePath() throws DBusException, IOException {
        String basePath;
        if (hasEfiPartition()) {
            mountEfiIfNeeded();
            basePath = efiPath;
        } else {
            basePath = getSystemPath();
        }
        return basePath;
    }
}
