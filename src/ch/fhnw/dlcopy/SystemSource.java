package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice.Type;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Encapsulate the source of an install or update operation
 */
public abstract class SystemSource {

    /**
     * the pattern of files that need to be copied to the EFI partition
     */
    public final static String EFI_COPY_PATTERN
            = "autorun.inf|boot.*|efi.*|EFI.*|isolinux.*|syslinux.*|"
            + "lernstick.ico|live/initrd.*|live/memtest|live/vmlinuz.*|"
            + "md5sum.txt|\\.disk.*|\\.VolumeIcon.icns";

    /**
     * the pattern of files that need to be copied to the EFI partition on
     * legacy (pre 2016-03) systems
     */
    public final static String LEGACY_EFI_COPY_PATTERN
            = EFI_COPY_PATTERN + "|boot.*|live/initrd2.img|live/vmlinuz2";

    /**
     * the pattern of files that need to be copied to the system partition (only
     * the files that are needed to boot the system, without the squashfs files)
     * on legacy (pre 2016-03) systems
     */
    public final static String LEGACY_SYSTEM_COPY_PATTERN_BOOT
            = "isolinux.*|syslinux.*|live/memtest|live/initrd.*|"
            + "live/vmlinuz.*|\\.disk/.*|md5sum.txt|lernstick.ico|autorun.inf";

    /**
     * the pattern of files that need to be copied to the system partition (only
     * the files that are needed to boot the system, without the squashfs files)
     */
    public final static String SYSTEM_COPY_PATTERN_BOOT = "";

    /**
     * the pattern of files that need to be copied to the system partition
     */
    public final static String SYSTEM_COPY_PATTERN_FULL
            = "live/filesystem.*";

    /**
     * the pattern of files that need to be copied to the system partition on
     * legacy (pre 2016-03) systems
     */
    public final static String LEGACY_SYSTEM_COPY_PATTERN_FULL
            = LEGACY_SYSTEM_COPY_PATTERN_BOOT + "|live/filesystem.*";

    /**
     * the MD5 sum of the legacy (pre 2016-03) GRUB that doesn't find it's
     * config files on the system partition but only on the boot/EFI partition
     */
    public static final String GRUB_LEGACY_MD5
            = "4293d2731afe94395ba62df10cc989ab";

    /**
     * the path to the GRUB EFI file
     */
    public static final String GRUB_EFI_PATH = "/boot/grubx64.efi";

    /**
     * Returns the device name (e.g. sda) of this system source. This is mostly
     * used for filtering out the system source in installation or upgrade
     * lists.
     *
     * @return the device name (e.g. sda) of this system source
     */
    public abstract String getDeviceName();

    /**
     * returns the device type if this system source
     *
     * @return the device type if this system source
     */
    public abstract Type getDeviceType();

    /**
     * returns <tt>true</tt> if the InstallationSource has a boot/EFI partition,
     * <tt>false</tt> otherwise
     *
     * @return <tt>true</tt> if the InstallationSource has a boot/EFI partition,
     * <tt>false</tt> otherwise
     */
    public abstract boolean hasEfiPartition();

    /**
     * returns <tt>true</tt> if the InstallationSource has an exchange
     * partition, <tt>false</tt> otherwise
     *
     * @return <tt>true</tt> if the InstallationSource has an exchange
     * partition, <tt>false</tt> otherwise
     */
    public abstract boolean hasExchangePartition();

    /**
     * returns the mode of the data partition
     *
     * @return the mode of the data partition
     */
    public abstract DataPartitionMode getDataPartitionMode();

    /**
     * returns the Debian Live version of the system
     *
     * @return the Debian Live version of the system
     */
    public abstract DebianLiveVersion getSystemVersion();

    /**
     * returns the path where the system is mounted
     *
     * @return the path where the system is mounted
     */
    public abstract String getSystemPath();

    /**
     * returns the size of the system in byte
     *
     * @return the size of the system in byte
     */
    public abstract long getSystemSize();

    /**
     * Returns Source definitions for copy jobs to the EFI partition. Partitions
     * may be mounted as needed if not already available.
     *
     * @return the Source definitions for copy jobs to the EFI partition
     * @throws DBusException
     */
    public abstract Source getEfiCopySource() throws DBusException, IOException;

    /**
     * returns a copy source for the system, without the squashfs files
     *
     * @return a copy source for the system, without the squashfs files
     */
    public abstract Source getSystemCopySourceBoot();

    /**
     * returns a copy source for the system, including the squashfs files
     *
     * @return a copy source for the system, including the squashfs files
     */
    public abstract Source getSystemCopySourceFull();

    public abstract Source getPersistentCopySource();

    public abstract Source getExchangeCopySource() 
            throws DBusException, IOException;

    public abstract Partition getEfiPartition();

    public abstract Partition getExchangePartition();

    public abstract Partition getDataPartition();

    public abstract String getMbrPath();

    /**
     * installs extlinux from this source to another partition
     *
     * @param partition the partition where to install extLinux
     * @throws java.io.IOException if installing extlinux on the bootPartition
     * fails
     */
    public abstract void installExtlinux(Partition partition) throws IOException;

    /**
     * Unmount any partitions that were mounted by any get*CopySource()
     */
    public abstract void unmountTmpPartitions();

    /**
     * creates a syslinux directory on a partition
     *
     * @param partition the partition where to create the syslinux directory
     * @return the path to the newly created syslinux directory
     * @throws IOException if creating the syslinux directory fails
     */
    protected static String createSyslinuxDir(Partition partition)
            throws IOException {
        try {
            MountInfo mountInfo = partition.mount();
            File syslinuxDir = new File(mountInfo.getMountPath() + "/syslinux");
            if (!syslinuxDir.exists() && !syslinuxDir.mkdirs()) {
                String errorMessage = DLCopy.STRINGS.getString(
                        "Error_Creating_Directory");
                errorMessage = MessageFormat.format(errorMessage, syslinuxDir);
                throw new IOException(errorMessage);
            }
            return syslinuxDir.getPath();
        } catch (DBusException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Depending on the Debian Live version the EFI directory is lowercase or
     * uppercase.This function tries to find the file in either case.
     *
     * @param base the base directory where the directory "efi" or "EFI" is
     * located
     * @return the location of the grubx64.efi file
     * @throws java.io.IOException if the grubx64.efi can't be found
     */
    protected String findGrubEfiFile(String base) throws IOException {
        String filePath = base + "/efi" + GRUB_EFI_PATH;
        if (!Files.exists(Paths.get(filePath))) {
            filePath = base + "/EFI" + GRUB_EFI_PATH;
        }
        if (!Files.exists(Paths.get(filePath))) {
            throw new IOException(filePath + " not found");
        }
        return filePath;
    }
}
