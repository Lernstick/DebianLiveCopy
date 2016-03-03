package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice.Type;
import java.io.IOException;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Encapsulate the source of an install or update operation TODO: rename class
 * so that upgrading is also reflected in its name
 */
public interface InstallationSource {

    /**
     * the pattern of files that need to be copied to the EFI partition
     */
    public final static String EFI_COPY_PATTERN
            = "efi.*|\\.VolumeIcon.icns|\\.disk_label.*";

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
    public final static String SYSTEM_COPY_PATTERN_BOOT
            = LEGACY_SYSTEM_COPY_PATTERN_BOOT + "|boot.*";

    /**
     * the pattern of files that need to be copied to the system partition
     */
    public final static String SYSTEM_COPY_PATTERN_FULL
            = SYSTEM_COPY_PATTERN_BOOT + "|live/filesystem.*";

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
    public static final String GRUB_EFI_PATH = "/efi/boot/grubx64.efi";

    public String getDeviceName();

    public Type getDeviceType();

    /**
     * returns <tt>true</tt> if the InstallationSource has a boot/EFI partition,
     * <tt>false</tt> otherwise
     *
     * @return <tt>true</tt> if the InstallationSource has a boot/EFI partition,
     * <tt>false</tt> otherwise
     */
    public boolean hasEfiPartition();

    /**
     * returns <tt>true</tt> if the InstallationSource has an exchange
     * partition, <tt>false</tt> otherwise
     *
     * @return <tt>true</tt> if the InstallationSource has an exchange
     * partition, <tt>false</tt> otherwise
     */
    public boolean hasExchangePartition();

    /**
     * returns the mode of the data partition
     *
     * @return the mode of the data partition
     */
    public DataPartitionMode getDataPartitionMode();

    /**
     * returns the Debian Live version of the system
     *
     * @return the Debian Live version of the system
     */
    public DebianLiveVersion getSystemVersion();

    public String getSystemPath();

    public long getSystemSize();

    /**
     * Returns Source definitions for copy jobs to the EFI partition. Partitions
     * may be mounted as needed if not already available.
     *
     * @return the Source definitions for copy jobs to the EFI partition
     * @throws DBusException
     */
    public Source getEfiCopySource() throws DBusException;

    /**
     * returns a copy source for the system, without the squashfs files
     *
     * @return a copy source for the system, without the squashfs files
     */
    public Source getSystemCopySourceBoot();

    /**
     * returns a copy source for the system, including the squashfs files
     *
     * @return a copy source for the system, including the squashfs files
     */
    public Source getSystemCopySourceFull();

    public Source getPersistentCopySource();

    public Source getExchangeCopySource() throws DBusException;

    public Partition getEfiPartition();

    public Partition getExchangePartition();

    public Partition getDataPartition();

    public String getMbrPath();

    /**
     * installs extlinux from the source to the target boot partition
     *
     * @param systemPartition the target system partition
     * @throws java.io.IOException if installing extlinux on the bootPartition
     * fails
     */
    public void installExtlinux(Partition systemPartition) throws IOException;

    /**
     * Unmount any partitions that were mounted by any get*CopySource()
     */
    public void unmountTmpPartitions();
}
