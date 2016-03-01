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
    public final static String EFI_COPY_PATTERN = "efi.*|\\.VolumeIcon.icns|"
            + "\\.disk_label.*|boot.*|live/initrd2.img|live/vmlinuz2";

    /**
     * the pattern of files that need to be copied to the system partition (only
     * the files that are needed to boot the system, without the squashfs files)
     */
    public final static String SYSTEM_COPY_PATTERN_BOOT
            = "isolinux.*|syslinux.*|live/memtest|live/initrd.*|"
            + "live/vmlinuz.*|\\.disk/.*|md5sum.txt|lernstick.ico|autorun.inf";

    /**
     * the pattern of files that need to be copied to the system partition
     */
    public final static String SYSTEM_COPY_PATTERN_FULL
            = SYSTEM_COPY_PATTERN_BOOT + "|live/filesystem.*";

    public String getDeviceName();

    public Type getDeviceType();

    public boolean hasBootPartition();

    public boolean hasExchangePartition();

    public DataPartitionMode getDataPartitionMode();

    public DebianLiveVersion getSystemVersion();

    public String getSystemPath();

    public long getSystemSize();

    // Source definitions for copy jobs
    // Partitions may be mounted as needed if not already available
    public Source getEfiCopySource() throws DBusException;

    public Source getExchangeEfiCopySource() throws DBusException;

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

    // Unmount any partitions that were mounted by any get*CopySource()
    public void unmountTmpPartitions();
}
