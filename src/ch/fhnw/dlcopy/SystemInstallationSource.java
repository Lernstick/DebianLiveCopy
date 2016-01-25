package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import ch.fhnw.util.StorageTools;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Install from the currently running live system
 */
public final class SystemInstallationSource implements InstallationSource {

    private final static Logger LOGGER
            = Logger.getLogger(SystemInstallationSource.class.getName());

    private final ProcessExecutor processExecutor;
    private final DebianLiveVersion runningVersion;
    private final DataPartitionMode dataPartitionMode;
    private final StorageDevice storageDevice;
    private final Partition exchangePartition;
    private final Partition dataPartition;
    private final Partition bootPartition;

    private String bootPath = null;
    private boolean isBootTmpMounted = false;

    private String exchangePath = null;
    private boolean isExchangeTmpMounted = false;

    public SystemInstallationSource(ProcessExecutor processExecutor)
            throws DBusException, IOException {
        this.processExecutor = processExecutor;
        runningVersion = DebianLiveVersion.getRunningVersion();
        if (runningVersion == null) {
            throw new IllegalArgumentException(
                    "Unable to detect running system version");
        }
        storageDevice = StorageTools.getSystemStorageDevice();

        LOGGER.log(Level.INFO,
                "boot storage device: {0}", storageDevice);

        exchangePartition = storageDevice.getExchangePartition();
        LOGGER.log(Level.INFO,
                "boot exchange partition: {0}", exchangePartition);

        dataPartition = storageDevice.getDataPartition();
        LOGGER.log(Level.INFO,
                "boot data partition: {0}", dataPartition);

        bootPartition = storageDevice.getEfiPartition();
        LOGGER.log(Level.INFO,
                "boot boot partition: {0}", bootPartition);

        // determine mode of data partition
        if (hasBootPartition()) {
            MountInfo bootMountInfo = bootPartition.mount();
            dataPartitionMode
                    = BootConfigUtil.getDataPartitionMode(
                            bootMountInfo.getMountPath());
            if (!bootMountInfo.alreadyMounted()) {
                bootPartition.umount();
            }
        } else {
            dataPartitionMode
                    = BootConfigUtil.getDataPartitionMode(getSystemPath());
        }
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
    public boolean hasBootPartition() {
        return bootPartition != null;
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
    public Source getEfiCopySource() throws DBusException {
        if (hasBootPartition()) {
            mountBootIfNeeded();
            return new Source(bootPath, InstallationSource.EFI_COPY_PATTERN);
        } else {
            return new Source(getSystemPath(),
                    InstallationSource.EFI_COPY_PATTERN);
        }
    }

    @Override
    public Source getExchangeEfiCopySource() throws DBusException {
        if (hasBootPartition()) {
            mountBootIfNeeded();
            return new Source(bootPath,
                    InstallationSource.EFI_COPY_PATTERN);
        } else {
            return new Source(getSystemPath(),
                    InstallationSource.EFI_COPY_PATTERN);
        }
    }

    @Override
    public Source getSystemCopySource() {
        return new Source(getSystemPath(),
                InstallationSource.SYSTEM_COPY_PATTERM);
    }

    @Override
    public Source getPersistentCopySource() {
        // not yet support for copy job
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Source getExchangeCopySource() throws DBusException {
        if (hasExchangePartition()) {
            mountExchangeIfNeeded();
            return new Source(exchangePath, ".*");
        }
        return null;
    }

    @Override
    public Partition getEfiPartition() {
        return bootPartition;
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
        String syslinuxDir = DLCopy.createSyslinuxDir(bootPartition);
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
        if (isBootTmpMounted && bootPath != null) {
            try {
                bootPartition.umount();
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "unmount boot", ex);
            }
            bootPath = null;
        }
        if (isExchangeTmpMounted && exchangePath != null) {
            try {
                exchangePartition.umount();
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "unmount exchange", ex);
            }
            exchangePath = null;
        }

    }

    private void mountBootIfNeeded() throws DBusException {
        if (bootPath == null) {
            MountInfo bootMountInfo = bootPartition.mount();
            bootPath = bootMountInfo.getMountPath();
            isBootTmpMounted = bootMountInfo.alreadyMounted();
        }
    }

    private void mountExchangeIfNeeded() throws DBusException {
        if (exchangePath == null) {
            MountInfo bootMountInfo = exchangePartition.mount();
            exchangePath = bootMountInfo.getMountPath();
            isExchangeTmpMounted = bootMountInfo.alreadyMounted();
        }
    }
}
