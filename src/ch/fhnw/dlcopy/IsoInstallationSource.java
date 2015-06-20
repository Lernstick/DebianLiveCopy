package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installation source from a loop mounted ISO file
 *
 */
public class IsoInstallationSource implements InstallationSource {

    private final static Logger LOGGER
            = Logger.getLogger(IsoInstallationSource.class.getName());

    private final String mediaPath;
    private final ProcessExecutor processExecutor;
    private final DebianLiveVersion version;

    private String rootFsPath = null;

    public IsoInstallationSource(String mediaPath,
            ProcessExecutor processExecutor) {
        this.mediaPath = mediaPath;
        this.processExecutor = processExecutor;
        this.version = validateIsoImage();

        if (version == null) {
            throw new IllegalStateException(mediaPath
                    + " is not a valid LernStick distribution image");
        }

        LOGGER.log(Level.INFO, "Install from ISO image at '{0}'", mediaPath);
        LOGGER.log(Level.INFO, "ISO system version {0}", version);
    }

    @Override
    public String getDeviceName() {
        return "";
    }

    @Override
    public StorageDevice.Type getDeviceType() {
        return StorageDevice.Type.OpticalDisc;
    }

    @Override
    public boolean hasBootPartition() {
        return false;
    }

    @Override
    public boolean hasExchangePartition() {
        return false;
    }

    @Override
    public DataPartitionMode getDataPartitionMode() {
        return DataPartitionMode.NotUsed;
    }

    @Override
    public DebianLiveVersion getSystemVersion() {
        return version;
    }

    @Override
    public String getSystemPath() {
        return mediaPath;
    }

    @Override
    public Source getBootCopySource() {
        return new Source(mediaPath,
                InstallationSource.BOOT_COPY_PATTERN);
    }

    @Override
    public Source getExchangeBootCopySource() {
        return new Source(mediaPath,
                InstallationSource.EXCHANGE_BOOT_COPY_PATTERN);
    }

    @Override
    public Source getSystemCopySource() {
        return new Source(mediaPath,
                InstallationSource.SYSTEM_COPY_PATTERM);
    }

    @Override
    public Source getPersistentCopySource() {
        return null;
    }

    @Override
    public Source getExchangeCopySource() {
        return null;
    }

    @Override
    public Partition getBootPartition() {
        return null;
    }

    @Override
    public Partition getExchangePartition() {
        return null;
    }

    @Override
    public Partition getDataPartition() {
        return null;
    }

    @Override
    public String getMbrPath() {
        mountSystemImageIfNeeded();
        return rootFsPath + version.getMbrFilePath();
    }

    @Override
    public int installSyslinux(String bootDevice) throws IOException {
        mountSystemImageIfNeeded();
        processExecutor.executeProcess("sync");
        return processExecutor.executeProcess(true, true,
                "chroot", rootFsPath,
                "syslinux", "-d", "syslinux", bootDevice);
    }

    @Override
    public void unmountTmpPartitions() {
        if (rootFsPath != null) {
            try {
                processExecutor.executeScript(String.format(
                        "umount %s/dev\n"
                        + "umount %s/proc\n"
                        + "umount %s/sys\n"
                        + "umount %s/tmp\n"
                        + "umount %s\n"
                        + "rmdir %s\n",
                        rootFsPath, rootFsPath, rootFsPath,
                        rootFsPath, rootFsPath, rootFsPath));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            rootFsPath = null;
        }
    }

    private void mountSystemImageIfNeeded() {
        try {
            if (rootFsPath != null) {
                return;
            }
            rootFsPath = LernstickFileTools.createTempDirectory(new File("/tmp/"),
                    "DLCopy").getCanonicalPath();
            // Create sandbox environment from install system image.
            // Should be sufficient to run syslinux in chroot environment.
            processExecutor.executeScript(String.format(
                    "mount %s/live/filesystem.squashfs %s\n"
                    + "mount -o bind /proc %s/proc/\n"
                    + "mount -o bind /sys %s/sys\n"
                    + "mount -o bind /dev %s/dev\n"
                    + "mount -o size=10m -t tmpfs tmpfs %s/tmp\n",
                    mediaPath, rootFsPath, rootFsPath, rootFsPath,
                    rootFsPath, rootFsPath));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private DebianLiveVersion validateIsoImage() {
        try {
            File infoFile = new File(mediaPath + "/.disk/info");
            if (!infoFile.exists()) {
                LOGGER.severe("Can't find .disk/info file"
                        + "- not a valid Lernstick image");
                return null;
            }
            String signature = LernstickFileTools.readFile(infoFile).get(0);
            if (signature.contains("Wheezy")) {
                return DebianLiveVersion.DEBIAN_7;
            } else if (signature.contains("Jessie")) {
                return DebianLiveVersion.DEBIAN_8;
            }
            LOGGER.log(Level.SEVERE, "Invalid version signature:  {0}",
                    signature);
            return null;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not read .disk/info", ex);
            return null;
        }
    }

}
