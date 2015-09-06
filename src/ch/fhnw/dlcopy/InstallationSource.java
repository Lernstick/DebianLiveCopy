package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice.Type;
import java.io.IOException;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Encapsulate the source of an install or update operation
 */
public interface InstallationSource {

    /*
     * "Removable" USB flash drives must have their exchange partition before
     * the boot partition. Otherwise Windows can't read the exchange partition.
     * On the other hand, some firmware (e.g. in the Lenovo B590) only looks at
     * the very first FAT32 partition for boot files. But only when booting in
     * UEFI mode...
     * Therefore we copy the files for UEFI boot also to the exchange partition
     * when the exchange partition is FAT32 and the stick is "removable".
     * Here we define the patterns for the different copy operations:
     */
    public final static String EXCHANGE_BOOT_COPY_PATTERN
            = "boot.*|efi.*|live/initrd.*|live/vmlinuz.*"
            + "|lernstick.ico|autorun.inf|.VolumeIcon.icns|\\.disk.*";
    public final static String BOOT_COPY_PATTERN = EXCHANGE_BOOT_COPY_PATTERN
            + "|isolinux.*|syslinux.*|live/memtest";
    public final static String SYSTEM_COPY_PATTERM
            = "live/filesystem.*|md5sum.txt";

    public enum DataPartitionMode {

        ReadWrite, ReadOnly, NotUsed
    }

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
    public Source getBootCopySource() throws DBusException;

    public Source getExchangeBootCopySource() throws DBusException;

    public Source getSystemCopySource();

    public Source getPersistentCopySource();

    public Source getExchangeCopySource() throws DBusException;

    public Partition getBootPartition();

    public Partition getExchangePartition();

    public Partition getDataPartition();

    public String getMbrPath();

    public int installSyslinux(String bootDevice) throws IOException;

    // Unmount any partitions that were mounted by any get*CopySource()
    public void unmountTmpPartitions();
}
