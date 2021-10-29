package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.exceptions.NoExecutableExtLinuxException;
import ch.fhnw.dlcopy.exceptions.NoExtLinuxException;
import ch.fhnw.filecopier.Source;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A system source from a loop mounted ISO file.
 *
 */
public class IsoSystemSource extends SystemSource {

    private final static Logger LOGGER
            = Logger.getLogger(IsoSystemSource.class.getName());

    private final String imagePath;
    private final ProcessExecutor processExecutor;
    private final DebianLiveVersion version;
    private final boolean hasLegacyGrub;

    private String mediaPath;
    private String rootFsPath = null;

    /**
     * Creates a new IsoInstallationSource
     *
     * @param imagePath the path to the ISO image
     * @param processExecutor the ProcessExecutor to use
     * @throws java.io.IOException if an error with extlinux or grub on the ISO
     * occured
     * @throws ch.fhnw.dlcopy.exceptions.NoExtLinuxException if extlinux
     * couldn't be found on the ISO
     * @throws ch.fhnw.dlcopy.exceptions.NoExecutableExtLinuxException if
     * executing extlinux of the ISO in a chroot failed
     */
    public IsoSystemSource(String imagePath, ProcessExecutor processExecutor)
            throws IOException, NoExtLinuxException,
            NoExecutableExtLinuxException {

        this.imagePath = imagePath;
        this.processExecutor = processExecutor;
        this.version = getDebianLiveVersion();

        if (version == null) {
            throw new IllegalStateException(imagePath
                    + " is not a valid LernStick distribution image");
        }

        checkForExtlinux();

        hasLegacyGrub = hasLegacyGrub();

        LOGGER.log(Level.INFO, "Install from ISO image at '{0}'", mediaPath);
        LOGGER.log(Level.INFO, "ISO system version {0}", version);
        LOGGER.log(Level.INFO, "ISO GRUB is a {0} version",
                (hasLegacyGrub ? "legacy" : "current"));
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
    public boolean hasEfiPartition() {
        return false;
    }

    @Override
    public boolean hasExchangePartition() {
        return false;
    }

    @Override
    public DataPartitionMode getDataPartitionMode() {
        return DataPartitionMode.NOT_USED;
    }

    @Override
    public DebianLiveVersion getSystemVersion() {
        return version;
    }

    @Override
    public String getSystemPath() {
        mountIsoImageIfNeeded();
        return mediaPath;
    }

    @Override
    public long getSystemSize() {
        File system = new File(getSystemPath());
        return system.getTotalSpace() - system.getFreeSpace();
    }

    @Override
    public Source getEfiCopySource() {
        return new Source(getSystemPath(), hasLegacyGrub
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
        return null;
    }

    @Override
    public Source getExchangeCopySource() {
        return null;
    }

    @Override
    public Partition getEfiPartition() {
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
    public void installExtlinux(Partition partition) throws IOException {
        mountSystemImageIfNeeded();
        processExecutor.executeProcess("sync");
        String syslinuxDir = createSyslinuxDir(partition);
        String rootFsSyslinuxDir = LernstickFileTools.createTempDirectory(
                new File(rootFsPath + "/tmp"), "syslinux").getPath();
        processExecutor.executeProcess(
                "mount", "-o", "bind", syslinuxDir, rootFsSyslinuxDir);
        String chrootSyslinuxDir
                = rootFsSyslinuxDir.substring(rootFsPath.length());
        int returnValue = processExecutor.executeProcess(true, true,
                "chroot", rootFsPath, "extlinux", "-i", chrootSyslinuxDir);
        processExecutor.executeProcess("umount", rootFsSyslinuxDir);
        if (returnValue != 0) {
            throw new IOException(
                    "extlinux failed with the following output: "
                    + processExecutor.getOutput());
        }
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
                LOGGER.log(Level.SEVERE, "", ex);
            }
            rootFsPath = null;
        }
        if (mediaPath != null) {
            try {
                processExecutor.executeScript(String.format("umount %s\n",
                        mediaPath));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            mediaPath = null;
        }
    }

    private void mountIsoImageIfNeeded() {
        if (mediaPath != null) {
            return;
        }
        try {
            mediaPath = LernstickFileTools.createTempDirectory(
                    new File("/tmp/"), "DLCopy").getCanonicalPath();
            processExecutor.executeScript(String.format(
                    "mount -o ro \"%s\" \"%s\"\n", imagePath, mediaPath));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            mediaPath = null;
        }
    }

    private void mountSystemImageIfNeeded() {
        mountIsoImageIfNeeded();
        if (rootFsPath != null) {
            return;
        }
        try {
            rootFsPath = LernstickFileTools.createTempDirectory(
                    new File("/tmp/"), "DLCopy").getCanonicalPath();
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
            rootFsPath = null;
        }
    }

    private DebianLiveVersion getDebianLiveVersion() {
        mountIsoImageIfNeeded();
        if (mediaPath == null) {
            unmountTmpPartitions();
            return null;
        }
        try {
            File infoFile = new File(mediaPath + "/.disk/info");
            if (!infoFile.exists()) {
                LOGGER.severe("Can't find .disk/info file"
                        + "- not a valid Lernstick image");
                unmountTmpPartitions();
                return null;
            }
            String signature = LernstickFileTools.readFile(infoFile).get(0);
            if (signature.contains("Wheezy")) {
                return DebianLiveVersion.DEBIAN_7;
            } else if (signature.contains("Jessie")
                    || signature.contains("Stretch")) {
                return DebianLiveVersion.DEBIAN_8_to_9;
            } else if (signature.contains("Buster")
                    || signature.contains("Bullseye")) {
                return DebianLiveVersion.DEBIAN_10_to_11;
            }
            LOGGER.log(Level.SEVERE, "Invalid version signature:  {0}",
                    signature);
            unmountTmpPartitions();
            return null;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not read .disk/info", ex);
            unmountTmpPartitions();
            return null;
        }
    }

    private boolean hasLegacyGrub() throws IOException {
        mountIsoImageIfNeeded();
        try {
            String filePath = findGrubEfiFile(mediaPath);
            return GRUB_LEGACY_MD5.equals(DLCopy.getMd5String(filePath));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    private void checkForExtlinux() throws NoExtLinuxException,
            NoExecutableExtLinuxException, IOException {

        // check that extlinux is available
        mountSystemImageIfNeeded();
        int returnValue = processExecutor.executeProcess(true, true,
                "chroot", rootFsPath, "extlinux", "--version");
        if (returnValue != 0) {
            unmountTmpPartitions();
            switch (returnValue) {
                case 127:
                    throw new NoExtLinuxException();
                case 126:
                    throw new NoExecutableExtLinuxException();
                default:
                    throw new IOException("Can't execute extlinux on ISO");
            }
        }
    }
}
